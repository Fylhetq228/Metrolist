package com.metrolist.music.geras.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/** Immutable snapshot the audio thread reads; swapped atomically by [GerasAudio]. */
data class DspParams(
    val eqEnabled: Boolean = false,
    val eqGainsDb: FloatArray = FloatArray(GerasEq.BANDS.size),
    val preampDb: Float = 0f,
    val bass: Float = 0f,
    val reverb: Float = 0f,
    val eightD: Float = 0f,
    /** Stereo widening + decorrelated ambience, 0..1. */
    val surround: Float = 0f,
) {
    val isNeutral: Boolean
        get() = (!eqEnabled || (eqGainsDb.all { abs(it) < 0.05f } && abs(preampDb) < 0.05f)) &&
            bass < 0.005f && reverb < 0.005f && eightD < 0.005f && surround < 0.005f
}

object GerasEq {
    val BANDS = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1_000f, 2_000f, 4_000f, 8_000f, 16_000f)
    const val Q = 1.4f
    const val MIN_DB = -12f
    const val MAX_DB = 12f
}

/**
 * Graphic EQ + bass boost + 8D auto-pan + reverb, after Metrolist's own processors.
 * Handles 16-bit and float PCM, mono or stereo; passes audio through untouched when neutral.
 */
@UnstableApi
class GerasAudioProcessor : BaseAudioProcessor() {
    @Volatile
    var params: DspParams = DspParams()

    private var sampleRate = 44_100
    private var channels = 2
    private var isFloat = false

    private var appliedParams: DspParams? = null
    private val eqL = Array(GerasEq.BANDS.size) { Biquad() }
    private val eqR = Array(GerasEq.BANDS.size) { Biquad() }
    private val bassL = Biquad()
    private val bassR = Biquad()
    private var preamp = 1.0
    private var reverb: Freeverb? = null
    private var panPhase = 0.0

    // Smoothed mix amounts so moving a slider never clicks.
    private var reverbMix = 0.0
    private var eightDMix = 0.0
    private var surroundMix = 0.0
    private var haas = DoubleArray(1)
    private var haasIdx = 0
    private val decorrelate = Biquad()

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val enc = inputAudioFormat.encoding
        if ((enc != C.ENCODING_PCM_16BIT && enc != C.ENCODING_PCM_FLOAT) || inputAudioFormat.channelCount > 2) {
            // Unsupported layout: stay inactive instead of failing playback.
            return AudioProcessor.AudioFormat.NOT_SET
        }
        sampleRate = inputAudioFormat.sampleRate
        channels = inputAudioFormat.channelCount
        isFloat = enc == C.ENCODING_PCM_FLOAT
        appliedParams = null
        reverb = Freeverb(sampleRate)
        haas = DoubleArray((sampleRate * 0.011).toInt().coerceAtLeast(1))
        haasIdx = 0
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val p = params
        val out = replaceOutputBuffer(remaining)
        if (p.isNeutral && reverbMix < 1e-4 && eightDMix < 1e-4 && surroundMix < 1e-4) {
            out.put(inputBuffer)
            out.flip()
            return
        }
        if (appliedParams !== p) updateFilters(p)

        val frameBytes = (if (isFloat) 4 else 2) * channels
        val frames = remaining / frameBytes
        val inBuf = inputBuffer.order(ByteOrder.nativeOrder())
        out.order(ByteOrder.nativeOrder())
        val rev = reverb
        val panStep = 2.0 * PI / (sampleRate * 7.5)
        val targetReverb = p.reverb.toDouble()
        val target8d = if (channels == 2) p.eightD.toDouble() else 0.0
        val targetSurround = if (channels == 2) p.surround.toDouble() else 0.0
        val smooth = 1.0 / (sampleRate * 0.05)

        repeat(frames) {
            var l = read(inBuf)
            var r = if (channels == 2) read(inBuf) else l

            if (p.eqEnabled || bassL.active) {
                l *= preamp
                r *= preamp
            }
            if (p.eqEnabled) {
                for (i in eqL.indices) {
                    if (eqL[i].active) {
                        l = eqL[i].process(l)
                        r = eqR[i].process(r)
                    }
                }
            }
            if (bassL.active) {
                l = bassL.process(l)
                r = bassR.process(r)
            }

            eightDMix += (target8d - eightDMix) * smooth
            if (eightDMix > 1e-4) {
                panPhase += panStep
                if (panPhase > 2 * PI) panPhase -= 2 * PI
                val pan = sin(panPhase)
                val angle = (pan + 1.0) * PI / 4.0
                val mid = (l + r) * 0.5
                // The far ear also loses a little level, which reads as distance.
                val pl = mid * cos(angle) * sqrt(2.0)
                val pr = mid * sin(angle) * sqrt(2.0)
                l = l * (1 - eightDMix) + pl * eightDMix
                r = r * (1 - eightDMix) + pr * eightDMix
            }

            surroundMix += (targetSurround - surroundMix) * smooth
            if (surroundMix > 1e-4) {
                val mid = (l + r) * 0.5
                val side = (l - r) * 0.5
                // A short, high-passed echo of the mid pushed out of phase between the ears
                // adds width even to near-mono mixes.
                val echo = decorrelate.process(haas[haasIdx])
                haas[haasIdx] = mid
                if (++haasIdx >= haas.size) haasIdx = 0
                val w = surroundMix
                val m = mid * (1 - 0.12 * w)
                val sd = side * (1 + 1.4 * w) + echo * 0.32 * w
                l = m + sd
                r = m - sd
            }

            reverbMix += (targetReverb - reverbMix) * smooth
            if (rev != null && reverbMix > 1e-4) {
                rev.process(l, r)
                val wet = reverbMix * 0.9
                val dry = 1.0 - reverbMix * 0.35
                l = l * dry + rev.outL * wet
                r = r * dry + rev.outR * wet
            }

            write(out, softClip(l))
            if (channels == 2) write(out, softClip(r))
        }
        out.flip()
    }

    private fun read(buf: ByteBuffer): Double =
        if (isFloat) buf.getFloat().toDouble() else buf.getShort() / 32768.0

    private fun write(buf: ByteBuffer, v: Double) {
        if (isFloat) {
            buf.putFloat(v.toFloat())
        } else {
            buf.putShort((v * 32767.0).coerceIn(-32768.0, 32767.0).toInt().toShort())
        }
    }

    private fun softClip(x: Double): Double {
        val a = abs(x)
        if (a <= 0.92) return x
        val sign = if (x < 0) -1.0 else 1.0
        return sign * (0.92 + 0.08 * tanh((a - 0.92) / 0.08))
    }

    private fun updateFilters(p: DspParams) {
        GerasEq.BANDS.forEachIndexed { i, f ->
            val g = p.eqGainsDb.getOrElse(i) { 0f }
            val active = abs(g) > 0.05f && f < sampleRate / 2f
            eqL[i].setPeaking(sampleRate, f.toDouble(), GerasEq.Q.toDouble(), g.toDouble(), active)
            eqR[i].setPeaking(sampleRate, f.toDouble(), GerasEq.Q.toDouble(), g.toDouble(), active)
        }
        // Automatic headroom: boosts are compensated so loud masters do not clip.
        val maxBoost = if (p.eqEnabled) (p.eqGainsDb.maxOrNull() ?: 0f).coerceAtLeast(0f) else 0f
        val headroom = maxBoost * 0.5f + p.bass * 3f
        val userPreamp = if (p.eqEnabled) p.preampDb else 0f
        preamp = 10.0.pow((userPreamp - headroom) / 20.0)
        decorrelate.setHighPass(sampleRate, 350.0)
        val bassDb = p.bass * 12.0
        bassL.setLowShelf(sampleRate, 95.0, bassDb, bassDb > 0.05)
        bassR.setLowShelf(sampleRate, 95.0, bassDb, bassDb > 0.05)
        appliedParams = p
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        eqL.forEach { it.clear() }
        eqR.forEach { it.clear() }
        bassL.clear()
        bassR.clear()
        reverb?.clear()
        haas.fill(0.0)
        decorrelate.clear()
    }

    override fun onReset() {
        appliedParams = null
        reverb = null
    }
}

/** Direct form I biquad; coefficients can change without clearing the state (no clicks). */
internal class Biquad {
    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0
    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0
    var active = false
        private set

    fun setPeaking(fs: Int, f0: Double, q: Double, gainDb: Double, enabled: Boolean) {
        active = enabled
        if (!enabled) return
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2 * PI * f0 / fs
        val alpha = sin(w0) / (2 * q)
        val cw = cos(w0)
        val a0 = 1 + alpha / a
        b0 = (1 + alpha * a) / a0
        b1 = (-2 * cw) / a0
        b2 = (1 - alpha * a) / a0
        a1 = (-2 * cw) / a0
        a2 = (1 - alpha / a) / a0
    }

    fun setLowShelf(fs: Int, f0: Double, gainDb: Double, enabled: Boolean) {
        active = enabled
        if (!enabled) return
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2 * PI * f0 / fs
        val cw = cos(w0)
        val alpha = sin(w0) / 2 * sqrt(2.0)
        val sa = 2 * sqrt(a) * alpha
        val a0 = (a + 1) + (a - 1) * cw + sa
        b0 = a * ((a + 1) - (a - 1) * cw + sa) / a0
        b1 = 2 * a * ((a - 1) - (a + 1) * cw) / a0
        b2 = a * ((a + 1) - (a - 1) * cw - sa) / a0
        a1 = -2 * ((a - 1) + (a + 1) * cw) / a0
        a2 = ((a + 1) + (a - 1) * cw - sa) / a0
    }

    fun setHighPass(fs: Int, f0: Double) {
        active = true
        val w0 = 2 * PI * f0 / fs
        val alpha = sin(w0) / (2 * 0.707)
        val cw = cos(w0)
        val a0 = 1 + alpha
        b0 = (1 + cw) / 2 / a0
        b1 = -(1 + cw) / a0
        b2 = (1 + cw) / 2 / a0
        a1 = -2 * cw / a0
        a2 = (1 - alpha) / a0
    }

    fun process(x: Double): Double {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1
        x1 = x
        y2 = y1
        y1 = y
        return y
    }

    fun clear() {
        x1 = 0.0; x2 = 0.0; y1 = 0.0; y2 = 0.0
    }
}

/** Freeverb (Jezar's public-domain design), tuned for a soft "slowed + reverb" room. */
private class Freeverb(fs: Int) {
    private val scale = fs / 44_100.0
    private val combTunings = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
    private val allpassTunings = intArrayOf(556, 441, 341, 225)
    private val spread = 23
    private val combL = combTunings.map { Comb((it * scale).toInt()) }
    private val combR = combTunings.map { Comb(((it + spread) * scale).toInt()) }
    private val apL = allpassTunings.map { Allpass((it * scale).toInt()) }
    private val apR = allpassTunings.map { Allpass(((it + spread) * scale).toInt()) }
    var outL = 0.0
        private set
    var outR = 0.0
        private set

    fun process(l: Double, r: Double) {
        val input = (l + r) * 0.015
        var ol = 0.0
        var or = 0.0
        for (i in combL.indices) {
            ol += combL[i].process(input)
            or += combR[i].process(input)
        }
        for (i in apL.indices) {
            ol = apL[i].process(ol)
            or = apR[i].process(or)
        }
        outL = ol
        outR = or
    }

    fun clear() {
        combL.forEach { it.clear() }
        combR.forEach { it.clear() }
        apL.forEach { it.clear() }
        apR.forEach { it.clear() }
    }

    private class Comb(size: Int) {
        private val buf = DoubleArray(size.coerceAtLeast(1))
        private var idx = 0
        private var store = 0.0
        private val feedback = 0.86
        private val damp = 0.32

        fun process(x: Double): Double {
            val out = buf[idx]
            store = out * (1 - damp) + store * damp
            buf[idx] = x + store * feedback
            if (++idx >= buf.size) idx = 0
            return out
        }

        fun clear() {
            buf.fill(0.0)
            store = 0.0
        }
    }

    private class Allpass(size: Int) {
        private val buf = DoubleArray(size.coerceAtLeast(1))
        private var idx = 0

        fun process(x: Double): Double {
            val b = buf[idx]
            val out = -x + b
            buf[idx] = x + b * 0.5
            if (++idx >= buf.size) idx = 0
            return out
        }

        fun clear() = buf.fill(0.0)
    }
}
