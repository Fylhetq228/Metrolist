package com.metrolist.music.geras.audio

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.pow

@Serializable
data class SpeedFx(
    val speed: Float = 1f,
    val pitchSemitones: Float = 0f,
    /** Speed and pitch move together like a turntable (nightcore / slowed). */
    val linked: Boolean = false,
    val reverb: Float = 0f,
    val eightD: Float = 0f,
    val bass: Float = 0f,
) {
    val pitchRatio: Float get() = if (linked) speed else 2f.pow(pitchSemitones / 12f)
}

@Serializable
data class EqProfile(
    val enabled: Boolean = true,
    val presetId: String = EqPresets.NEUTRAL,
    val gains: List<Float> = List(GerasEq.BANDS.size) { 0f },
    val preamp: Float = 0f,
    val surround: Float = 0f,
)

@Serializable
data class CustomPreset(val id: String, val name: String, val gains: List<Float>)

enum class OutputKind { SPEAKER, HEADPHONES }

@Serializable
private data class EqStore(
    val speaker: EqProfile = EqProfile(),
    val headphones: EqProfile = EqProfile(),
    val autoSwitch: Boolean = true,
    val custom: List<CustomPreset> = emptyList(),
)

object EqPresets {
    const val NEUTRAL = "neutral"
    const val CUSTOM = "custom"
    // 31 62 125 250 500 1k 2k 4k 8k 16k
    val builtIn: List<Pair<String, List<Float>>> = listOf(
        NEUTRAL to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        "bass" to listOf(6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f),
        "treble" to listOf(0f, 0f, 0f, 0f, 0f, 1f, 2.5f, 4f, 5f, 5.5f),
        "vocal" to listOf(-2f, -2f, -1f, 0f, 2f, 3.5f, 3.5f, 2f, 0f, -1f),
        "rock" to listOf(4.5f, 3.5f, 2f, 0f, -1f, -0.5f, 1.5f, 3f, 4f, 4f),
        "electronic" to listOf(5f, 4f, 1.5f, 0f, -1.5f, 1f, 0.5f, 1.5f, 4f, 5f),
        "acoustic" to listOf(3f, 2.5f, 1.5f, 1f, 1.5f, 1.5f, 2f, 2.5f, 2f, 1f),
        "night" to listOf(3f, 2f, 1f, 0f, -1f, -1f, 0f, 1f, 1.5f, 2f),
    )
}

object SpeedPresets {
    val all: List<Pair<String, SpeedFx>> = listOf(
        "normal" to SpeedFx(),
        "slowed_reverb" to SpeedFx(speed = 0.85f, linked = true, reverb = 0.45f),
        "sped_up" to SpeedFx(speed = 1.2f, linked = true),
        "nightcore" to SpeedFx(speed = 1.3f, linked = true, bass = 0.15f),
        "daycore" to SpeedFx(speed = 0.82f, linked = true, reverb = 0.25f),
    )
}

/**
 * Owns speed/pitch, effects and the Geras EQ for every ExoPlayer MusicService creates.
 * State lives in its own SharedPreferences so Metrolist's settings stay untouched.
 */
@UnstableApi
object GerasAudio {
    /**
     * Effects run before the AudioTrack, so its buffer is the delay between moving a slider
     * and hearing it. Media3's default keeps 250-750 ms; 100-200 ms is still safe on phones.
     */
    val audioTrackBuffers: androidx.media3.exoplayer.audio.DefaultAudioTrackBufferSizeProvider =
        androidx.media3.exoplayer.audio.DefaultAudioTrackBufferSizeProvider.Builder()
            .setMinPcmBufferDurationUs(100_000)
            .setMaxPcmBufferDurationUs(200_000)
            .setPcmBufferMultiplicationFactor(2)
            .build()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var prefs: SharedPreferences
    private lateinit var appContext: Context
    private val processors = CopyOnWriteArrayList<WeakReference<GerasAudioProcessor>>()
    private val players = CopyOnWriteArrayList<WeakReference<Player>>()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _speedFx = MutableStateFlow(SpeedFx())
    val speedFx: StateFlow<SpeedFx> = _speedFx.asStateFlow()

    private val _rememberPerTrack = MutableStateFlow(false)
    /** True when the current track has its own saved speed/effects. */
    val rememberPerTrack: StateFlow<Boolean> = _rememberPerTrack.asStateFlow()

    private var store = EqStore()
    private val _eqStore = MutableStateFlow(store)

    private val _output = MutableStateFlow(OutputKind.SPEAKER)
    val output: StateFlow<OutputKind> = _output.asStateFlow()

    /** Which profile the EQ sheet is editing (follows the output when auto-switch is on). */
    private val _editing = MutableStateFlow(OutputKind.SPEAKER)
    val editing: StateFlow<OutputKind> = _editing.asStateFlow()

    private val _abBypass = MutableStateFlow(false)
    val abBypass: StateFlow<Boolean> = _abBypass.asStateFlow()

    private var globalFx = SpeedFx()
    private var currentTrackId: String? = null

    val eqProfile: StateFlow<EqProfile> get() = _eqProfile
    private val _eqProfile = MutableStateFlow(EqProfile())
    val customPresets: StateFlow<List<CustomPreset>> get() = _custom
    private val _custom = MutableStateFlow<List<CustomPreset>>(emptyList())
    val autoSwitch: StateFlow<Boolean> get() = _auto
    private val _auto = MutableStateFlow(true)

    private fun ensureInit(context: Context) {
        if (::prefs.isInitialized) return
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences("geras_audio", Context.MODE_PRIVATE)
        globalFx = prefs.getString("global_fx", null)?.let { runCatching { json.decodeFromString<SpeedFx>(it) }.getOrNull() } ?: SpeedFx()
        _speedFx.value = globalFx
        store = prefs.getString("eq", null)?.let { runCatching { json.decodeFromString<EqStore>(it) }.getOrNull() } ?: EqStore()
        val am = appContext.getSystemService(AudioManager::class.java)
        detectOutput(am)
        am.registerAudioDeviceCallback(
            object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = detectOutput(am)
                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = detectOutput(am)
            },
            mainHandler,
        )
        publishEq()
    }

    /** Called from MusicService's audio sink builder; one processor per ExoPlayer. */
    fun newProcessor(context: Context): GerasAudioProcessor {
        ensureInit(context)
        processors.removeAll { it.get() == null }
        return GerasAudioProcessor().also {
            it.params = currentDsp()
            processors += WeakReference(it)
        }
    }

    /** Called right after MusicService builds an ExoPlayer. */
    fun attach(context: Context, player: Player) {
        ensureInit(context)
        players.removeAll { it.get() == null }
        players += WeakReference(player)
        player.addListener(
            object : Player.Listener {
                private var lastArtist: String? = null
                private var startedAt = System.currentTimeMillis()

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    // Wave feedback: a quick manual skip vs. a track played to the end.
                    val listened = System.currentTimeMillis() - startedAt
                    when (reason) {
                        Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> if (listened < 30_000) com.metrolist.music.geras.wave.GerasWave.skipped(lastArtist)
                        Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> com.metrolist.music.geras.wave.GerasWave.enjoyed(lastArtist)
                    }
                    lastArtist = mediaItem?.mediaMetadata?.artist?.toString()?.substringBefore(',')
                    startedAt = System.currentTimeMillis()
                    onTrackChanged(mediaItem?.mediaId)
                }
            },
        )
        onTrackChanged(player.currentMediaItem?.mediaId, force = true)
        GerasLoop.attach(player)
    }

    private fun onTrackChanged(id: String?, force: Boolean = false) {
        if (id == currentTrackId && !force) return
        currentTrackId = id
        val saved = id?.let { prefs.getString("track_$it", null) }
            ?.let { runCatching { json.decodeFromString<SpeedFx>(it) }.getOrNull() }
        _rememberPerTrack.value = saved != null
        applyFx(saved ?: globalFx)
    }

    fun setSpeedFx(fx: SpeedFx) {
        val clean = fx.copy(
            speed = (Math.round(fx.speed.coerceIn(0.5f, 2f) * 100) / 100f),
            pitchSemitones = fx.pitchSemitones.coerceIn(-12f, 12f),
            reverb = fx.reverb.coerceIn(0f, 1f),
            eightD = fx.eightD.coerceIn(0f, 1f),
            bass = fx.bass.coerceIn(0f, 1f),
        )
        if (_rememberPerTrack.value && currentTrackId != null) {
            prefs.edit().putString("track_$currentTrackId", json.encodeToString(SpeedFx.serializer(), clean)).apply()
        } else {
            globalFx = clean
            prefs.edit().putString("global_fx", json.encodeToString(SpeedFx.serializer(), clean)).apply()
        }
        applyFx(clean)
    }

    fun setRememberPerTrack(enabled: Boolean) {
        val id = currentTrackId ?: return
        _rememberPerTrack.value = enabled
        if (enabled) {
            prefs.edit().putString("track_$id", json.encodeToString(SpeedFx.serializer(), _speedFx.value)).apply()
        } else {
            prefs.edit().remove("track_$id").apply()
            applyFx(globalFx)
        }
    }

    private fun applyFx(fx: SpeedFx) {
        _speedFx.value = fx
        val params = PlaybackParameters(fx.speed, fx.pitchRatio)
        mainHandler.post {
            players.forEach { ref ->
                val p = ref.get() ?: return@forEach
                if (p.playbackParameters != params) p.playbackParameters = params
            }
        }
        pushDsp()
    }

    // EQ

    fun selectEditing(kind: OutputKind) {
        _editing.value = kind
        publishEq()
    }

    fun setAutoSwitch(enabled: Boolean) {
        store = store.copy(autoSwitch = enabled)
        if (enabled) _editing.value = _output.value
        saveEq()
    }

    /** [persist] = false while a finger is still dragging; call [commitEq] when it lifts. */
    fun updateEq(persist: Boolean = true, transform: (EqProfile) -> EqProfile) {
        val kind = _editing.value
        store = when (kind) {
            OutputKind.SPEAKER -> store.copy(speaker = transform(store.speaker))
            OutputKind.HEADPHONES -> store.copy(headphones = transform(store.headphones))
        }
        if (persist) saveEq() else publishEq()
    }

    fun commitEq() = saveEq()

    fun applyPreset(id: String) {
        val gains = EqPresets.builtIn.firstOrNull { it.first == id }?.second
            ?: store.custom.firstOrNull { it.id == id }?.gains
            ?: return
        updateEq { it.copy(presetId = id, gains = gains, enabled = true) }
    }

    fun saveCustomPreset(name: String) {
        val id = "c_${System.currentTimeMillis()}"
        val gains = currentEditingProfile().gains
        store = store.copy(custom = store.custom + CustomPreset(id, name.trim().ifEmpty { "Preset" }, gains))
        updateEq { it.copy(presetId = id) }
    }

    fun deleteCustomPreset(id: String) {
        store = store.copy(custom = store.custom.filterNot { it.id == id })
        saveEq()
    }

    fun setAbBypass(bypass: Boolean) {
        _abBypass.value = bypass
        pushDsp()
    }

    private fun currentEditingProfile() = if (_editing.value == OutputKind.HEADPHONES) store.headphones else store.speaker

    private fun saveEq() {
        prefs.edit().putString("eq", json.encodeToString(EqStore.serializer(), store)).apply()
        publishEq()
    }

    private fun publishEq() {
        _eqStore.value = store
        _eqProfile.value = currentEditingProfile()
        _custom.value = store.custom
        _auto.value = store.autoSwitch
        pushDsp()
    }

    private fun detectOutput(am: AudioManager) {
        val headphoneTypes = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_HEARING_AID,
        )
        val hasHeadphones = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in headphoneTypes }
        _output.value = if (hasHeadphones) OutputKind.HEADPHONES else OutputKind.SPEAKER
        if (store.autoSwitch) _editing.value = _output.value
        if (::prefs.isInitialized) publishEq()
    }

    private fun currentDsp(): DspParams {
        val kind = if (store.autoSwitch) _output.value else _editing.value
        val profile = if (kind == OutputKind.HEADPHONES) store.headphones else store.speaker
        val fx = _speedFx.value
        return DspParams(
            eqEnabled = profile.enabled && !_abBypass.value,
            eqGainsDb = profile.gains.toFloatArray(),
            preampDb = profile.preamp,
            bass = fx.bass,
            reverb = fx.reverb,
            eightD = fx.eightD,
            surround = profile.surround,
        )
    }

    private fun pushDsp() {
        val p = currentDsp()
        processors.forEach { it.get()?.params = p }
    }
}
