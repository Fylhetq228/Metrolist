package com.metrolist.music.geras.audio

import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max

/**
 * Listens to the music for the lyrics screen: "the drop" (calm part, then the track kicks in
 * hard) and kick-drum hits. Fed from the audio processor in small hops; the UI reads the
 * timestamps. Built to stay quiet rather than fire on a single drum hit, a fade-in or a seek.
 */
object GerasPulse {
    /** Audio is processed this far ahead of what comes out of the speaker. */
    const val OUTPUT_LATENCY_NS = 200_000_000L

    /** nanoTime when the last drop was processed (0 = none) and its strength 0..1. */
    @Volatile var dropAtNanos = 0L
        private set
    @Volatile var dropStrength = 0f
        private set
    @Volatile var kickAtNanos = 0L
        private set
    @Volatile var kickStrength = 0f
        private set

    private var detector: DropDetector? = null
    private var acc = 0.0
    private var accLow = 0.0
    private var accFrames = 0
    private var low = 0.0
    private var lowCoef = 0.0
    private var hopFrames = 1024
    private var rate = 44_100

    fun configure(sampleRate: Int) {
        rate = sampleRate
        hopFrames = (sampleRate / 43).coerceAtLeast(256)
        lowCoef = 1.0 - exp(-2.0 * Math.PI * 120.0 / sampleRate)
        detector = DropDetector(hopFrames.toDouble() / sampleRate)
        acc = 0.0
        accLow = 0.0
        accFrames = 0
        low = 0.0
    }

    /** Seek or new stream: forget the past so it cannot make a "drop". */
    fun reset() {
        detector?.reset()
        acc = 0.0
        accLow = 0.0
        accFrames = 0
    }

    /** One mono sample in -1..1. Called on the audio thread. */
    fun sample(x: Double) {
        low += (x - low) * lowCoef
        acc += x * x
        accLow += low * low
        if (++accFrames >= hopFrames) {
            val d = detector ?: return
            val r = d.hop(acc / accFrames, accLow / accFrames)
            val now = System.nanoTime()
            if (r.drop > 0f) {
                dropStrength = r.drop
                dropAtNanos = now
            }
            if (r.kick > 0f) {
                kickStrength = r.kick
                kickAtNanos = now
            }
            acc = 0.0
            accLow = 0.0
            accFrames = 0
        }
    }
}

/**
 * Pure detector on per-hop power, so it can be tested without audio hardware.
 *
 * A drop fires when, held for a moment, the track is clearly louder AND bassier than anything
 * in the previous few seconds, and loud in absolute terms. Then it waits a long while before
 * it may fire again.
 */
class DropDetector(private val hopSec: Double) {
    class Result(val drop: Float, val kick: Float)

    private val historyLen = (HISTORY_SEC / hopSec).toInt()
    private val holdLen = (HOLD_SEC / hopSec).toInt().coerceAtLeast(2)
    private val fastDb = DoubleArray(historyLen) { SILENT }
    private val lowDb = DoubleArray(historyLen) { SILENT }
    private var pos = 0
    private var age = 0.0
    private var cooldown = 0.0
    private var held = 0
    private var fast = 0.0
    private var fastLow = 0.0
    private var slowLow = 0.0
    private var kickAvg = 0.0
    private var sinceKick = 1.0
    private val aFast = 1 - exp(-hopSec / 0.1)
    private val aSlow = 1 - exp(-hopSec / 4.0)
    private val aKick = 1 - exp(-hopSec / 0.6)

    fun reset() {
        fastDb.fill(SILENT)
        lowDb.fill(SILENT)
        age = 0.0
        held = 0
        fast = 0.0
        fastLow = 0.0
        slowLow = 0.0
        kickAvg = 0.0
    }

    fun hop(power: Double, lowPower: Double): Result {
        age += hopSec
        cooldown = max(0.0, cooldown - hopSec)
        sinceKick += hopSec
        fast += (power - fast) * aFast
        fastLow += (lowPower - fastLow) * aFast
        slowLow += (lowPower - slowLow) * aSlow
        val fDb = db(fast)
        val lDb = db(fastLow)

        // Loudest moment of the window that ended HOLD_SEC ago: "what it was like before".
        var prevMax = SILENT
        var prevLowMax = SILENT
        for (k in holdLen until historyLen) {
            val i = (pos - 1 - k + historyLen * 2) % historyLen
            prevMax = max(prevMax, fastDb[i])
            prevLowMax = max(prevLowMax, lowDb[i])
        }
        fastDb[pos] = fDb
        lowDb[pos] = lDb
        pos = (pos + 1) % historyLen

        // The hold is judged on each hop's own level, not the smoothed one, so the tail of a
        // single hit cannot carry it; a dip between kicks only sets it back a little.
        val jump = fDb - prevMax
        val instJump = db(power) - prevMax
        val bassJump = db(lowPower) - prevLowMax
        // The bass must grow more than the rest: a string swell gets louder, a drop gets heavier.
        val bassShare = (lDb - fDb) - (prevLowMax - prevMax)
        val candidate = age > HISTORY_SEC && fDb > MIN_LOUD_DB && instJump >= MIN_JUMP_DB &&
            bassJump >= MIN_BASS_JUMP_DB - 2 && bassShare >= MIN_BASS_SHARE_DB
        held = if (candidate) held + 1 else max(0, held - 2)
        var drop = 0f
        if (held >= holdLen && cooldown == 0.0) {
            drop = ((jump - MIN_JUMP_DB) / 10.0 + 0.5).coerceIn(0.4, 1.0).toFloat()
            cooldown = COOLDOWN_SEC
            held = 0
        }

        // Kick: a short bass burst well above the recent bass average, in a loud passage.
        var kick = 0f
        val inst = db(lowPower)
        if (age > 1.0 && sinceKick > 0.22 && inst > db(kickAvg) + 5.0 && inst > db(slowLow) + 2.0 && fDb > MIN_LOUD_DB - 4) {
            kick = ((inst - db(kickAvg) - 5.0) / 8.0 + 0.4).coerceIn(0.3, 1.0).toFloat()
            sinceKick = 0.0
        }
        kickAvg += (lowPower - kickAvg) * aKick
        return Result(drop, kick)
    }

    private fun db(p: Double) = if (p <= 1e-12) SILENT else 10 * log10(p)

    companion object {
        const val SILENT = -120.0
        const val HISTORY_SEC = 3.5
        const val HOLD_SEC = 0.35
        const val MIN_LOUD_DB = -24.0
        const val MIN_JUMP_DB = 5.0
        const val MIN_BASS_JUMP_DB = 6.0
        const val MIN_BASS_SHARE_DB = 3.0
        const val COOLDOWN_SEC = 15.0
    }
}
