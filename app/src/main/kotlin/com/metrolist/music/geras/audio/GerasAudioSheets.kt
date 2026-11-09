package com.metrolist.music.geras.audio

import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import com.metrolist.music.R
import com.metrolist.music.geras.player.GerasPlayerColors
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import com.metrolist.music.geras.ui.gerasBounce

@Composable
private fun sliderColors(c: GerasPlayerColors) = SliderDefaults.colors(
    thumbColor = c.accent,
    activeTrackColor = c.accent,
    inactiveTrackColor = c.content.copy(alpha = 0.15f),
    activeTickColor = Color.Transparent,
    inactiveTickColor = Color.Transparent,
)

@Composable
private fun Chip(text: String, selected: Boolean, c: GerasPlayerColors, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    @kotlin.OptIn(ExperimentalFoundationApi::class)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.gerasBounce(0.92f)
            .height(34.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) c.accent else c.content.copy(alpha = 0.08f))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp),
    ) {
        Text(text, color = if (selected) c.onAccent else c.content, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SectionTitle(text: String, c: GerasPlayerColors) {
    Text(text, color = c.contentMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    valueText: String,
    c: GerasPlayerColors,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    enabled: Boolean = true,
    origin: Float? = null,
    onChange: (Float) -> Unit,
) {
    com.metrolist.music.geras.ui.GerasSlider(
        value = value,
        onValueChange = onChange,
        valueRange = range,
        label = label,
        valueText = valueText,
        enabled = enabled,
        origin = origin,
        accent = c.accent,
        track = c.content.copy(alpha = 0.09f),
        content = c.content,
        onAccent = c.onAccent,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GerasSpeedSheet(colors: GerasPlayerColors, onDismiss: () -> Unit) {
    val fx by GerasAudio.speedFx.collectAsState()
    val perTrack by GerasAudio.rememberPerTrack.collectAsState()
    val c = colors
    val presetNames = mapOf(
        "normal" to stringResource(R.string.geras_speed_normal),
        "slowed_reverb" to "Slowed + Reverb",
        "sped_up" to "Sped Up",
        "nightcore" to "Nightcore",
        "daycore" to "Daycore",
        "melancore" to "Melancore",
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.background,
        contentColor = c.content,
        dragHandle = { SheetHandle(c) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.geras_speed_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.content, modifier = Modifier.weight(1f))
                Text(String.format(Locale.US, "%.2fx", fx.speed), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = c.accent)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                listOf(
                    Triple(0.75f, "Slowed", true),
                    Triple(1f, "Default", false),
                    Triple(1.25f, "Speedup", true),
                ).forEach { (speed, label, linked) ->
                    val selected = abs(fx.speed - speed) < 0.005f
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.gerasBounce(0.92f)
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selected) c.accent else c.content.copy(alpha = 0.08f))
                            .clickable { GerasAudio.setSpeedFx(fx.copy(speed = speed, linked = linked)) }
                            .padding(vertical = 12.dp),
                    ) {
                        Text(String.format(Locale.US, "%sx", if (speed == 1f) "1.0" else speed.toString()), color = if (selected) c.onAccent else c.content, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(label, color = if (selected) c.onAccent.copy(alpha = 0.8f) else c.contentMuted, fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            LabeledSlider(
                label = stringResource(R.string.geras_speed_title),
                value = fx.speed,
                valueText = String.format(Locale.US, "%.2fx", fx.speed),
                c = c,
                range = 0.5f..2f,
                origin = 1f,
            ) { GerasAudio.setSpeedFx(fx.copy(speed = it)) }

            SectionTitle(stringResource(R.string.geras_presets), c)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                SpeedPresets.all.forEach { (id, preset) ->
                    val selected = abs(fx.speed - preset.speed) < 0.005f && fx.linked == preset.linked &&
                        abs(fx.reverb - preset.reverb) < 0.01f && (id != "normal" || (fx.eightD < 0.01f && fx.bass < 0.01f && abs(fx.pitchSemitones) < 0.05f))
                    Chip(presetNames[id] ?: id, selected, c, onClick = { GerasAudio.setSpeedFx(preset) })
                }
            }

            Spacer(Modifier.height(6.dp))
            com.metrolist.music.geras.ui.GerasExpandable(
                title = stringResource(R.string.geras_pitch_mode),
                summary = if (fx.linked) stringResource(R.string.geras_pitch_linked) else String.format(Locale.US, "%+.1f", fx.pitchSemitones),
                content = c.content,
                muted = c.contentMuted,
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(c.content.copy(alpha = 0.08f))
                        .padding(4.dp),
                ) {
                    Segment(stringResource(R.string.geras_pitch_linked), fx.linked, c, Modifier.weight(1f)) {
                        GerasAudio.setSpeedFx(fx.copy(linked = true))
                    }
                    Segment(stringResource(R.string.geras_pitch_keep), !fx.linked, c, Modifier.weight(1f)) {
                        GerasAudio.setSpeedFx(fx.copy(linked = false))
                    }
                }
                Spacer(Modifier.height(10.dp))
                val pitchShown = if (fx.linked) 12f * kotlin.math.log2(fx.speed) else fx.pitchSemitones
                LabeledSlider(
                    label = stringResource(R.string.geras_pitch),
                    value = pitchShown.coerceIn(-12f, 12f),
                    valueText = String.format(Locale.US, "%+.1f", pitchShown),
                    c = c,
                    range = -12f..12f,
                    enabled = !fx.linked,
                    origin = 0f,
                ) { GerasAudio.setSpeedFx(fx.copy(pitchSemitones = Math.round(it * 10) / 10f)) }


            }
            com.metrolist.music.geras.ui.GerasExpandable(
                title = stringResource(R.string.geras_effects),
                summary = listOf(stringResource(R.string.geras_reverb) to fx.reverb, "8D" to fx.eightD, stringResource(R.string.geras_bass_boost) to fx.bass, stringResource(R.string.geras_muffle) to fx.muffle)
                    .filter { it.second > 0.005f }.joinToString(" · ") { it.first + " " + percent(it.second) }.ifEmpty { stringResource(R.string.geras_effects_off) },
                content = c.content,
                muted = c.contentMuted,
            ) {
                LabeledSlider(stringResource(R.string.geras_reverb), fx.reverb, percent(fx.reverb), c) { GerasAudio.setSpeedFx(fx.copy(reverb = it)) }
                LabeledSlider("8D", fx.eightD, percent(fx.eightD), c) { GerasAudio.setSpeedFx(fx.copy(eightD = it)) }
                LabeledSlider(stringResource(R.string.geras_bass_boost), fx.bass, percent(fx.bass), c) { GerasAudio.setSpeedFx(fx.copy(bass = it)) }
                LabeledSlider(stringResource(R.string.geras_muffle), fx.muffle, percent(fx.muffle), c) { GerasAudio.setSpeedFx(fx.copy(muffle = it)) }


            }
            com.metrolist.music.geras.ui.GerasExpandable(
                title = stringResource(R.string.geras_loop_title),
                summary = stringResource(R.string.geras_loop_hint),
                content = c.content,
                muted = c.contentMuted,
            ) {
                LoopControls(c)


            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.gerasBounce(0.92f)
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .height(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (perTrack) c.content.copy(alpha = 0.1f) else c.accent)
                    .clickable { GerasAudio.setRememberPerTrack(!perTrack) },
            ) {
                Text(
                    stringResource(if (perTrack) R.string.geras_remembered else R.string.geras_remember),
                    color = if (perTrack) c.content else c.onAccent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                stringResource(R.string.geras_remember_track_hint),
                color = c.contentMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp).align(Alignment.CenterHorizontally),
            )
        }
    }
}

private fun percent(v: Float) = "${(v * 100).toInt()}%"

@Composable
private fun switchColors(c: GerasPlayerColors) = SwitchDefaults.colors(
    checkedThumbColor = c.onAccent,
    checkedTrackColor = c.accent,
    uncheckedThumbColor = c.contentMuted,
    uncheckedTrackColor = c.content.copy(alpha = 0.1f),
    uncheckedBorderColor = Color.Transparent,
)

@Composable
private fun SheetHandle(c: GerasPlayerColors) {
    Box(
        Modifier
            .padding(vertical = 10.dp)
            .size(width = 36.dp, height = 4.dp)
            .clip(RoundedCornerShape(50))
            .background(c.content.copy(alpha = 0.3f)),
    )
}

@Composable
private fun StepButton(text: String, c: GerasPlayerColors, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.gerasBounce(0.92f)
            .size(40.dp)
            .clip(RoundedCornerShape(50))
            .background(c.content.copy(alpha = 0.08f))
            .clickable(onClick = onClick),
    ) { Text(text, color = c.content, fontSize = 20.sp, fontWeight = FontWeight.Medium) }
}

@Composable
private fun Segment(text: String, selected: Boolean, c: GerasPlayerColors, modifier: Modifier, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.gerasBounce(0.92f)
            .height(38.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) c.accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
    ) {
        Text(text, color = if (selected) c.onAccent else c.content, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GerasEqualizerSheet(colors: GerasPlayerColors, onDismiss: () -> Unit, onOpenAutoEq: () -> Unit) {
    val c = colors
    val profile by GerasAudio.eqProfile.collectAsState()
    val custom by GerasAudio.customPresets.collectAsState()
    val editing by GerasAudio.editing.collectAsState()
    val output by GerasAudio.output.collectAsState()
    val auto by GerasAudio.autoSwitch.collectAsState()
    val bypass by GerasAudio.abBypass.collectAsState()
    var naming by remember { mutableStateOf(false) }
    val names = mapOf(
        EqPresets.NEUTRAL to stringResource(R.string.geras_eq_neutral),
        "bass" to stringResource(R.string.geras_eq_bass),
        "treble" to stringResource(R.string.geras_eq_treble),
        "vocal" to stringResource(R.string.geras_eq_vocal),
        "rock" to stringResource(R.string.geras_eq_rock),
        "electronic" to stringResource(R.string.geras_eq_electronic),
        "acoustic" to stringResource(R.string.geras_eq_acoustic),
        "night" to stringResource(R.string.geras_eq_night),
    )

    ModalBottomSheet(
        onDismissRequest = {
            GerasAudio.setAbBypass(false)
            onDismiss()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.background,
        contentColor = c.content,
        dragHandle = { SheetHandle(c) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.geras_eq_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.content, modifier = Modifier.weight(1f))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.gerasBounce(0.92f)
                        .padding(end = 10.dp)
                        .height(32.dp)
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, c.accent, RoundedCornerShape(50))
                        .background(if (bypass) c.accent else Color.Transparent)
                        .clickable { GerasAudio.setAbBypass(!bypass) }
                        .padding(horizontal = 12.dp),
                ) {
                    Text(if (bypass) "B" else "A", color = if (bypass) c.onAccent else c.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                com.metrolist.music.geras.ui.GerasSwitch(
                    checked = profile.enabled,
                    onCheckedChange = { on -> GerasAudio.updateEq { it.copy(enabled = on) } },
                    accent = c.accent,
                    track = c.content.copy(alpha = 0.15f),
                )
            }
            androidx.compose.animation.AnimatedVisibility(bypass) {
                Text(stringResource(R.string.geras_ab_b), color = c.contentMuted, fontSize = 12.sp)
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .padding(top = 14.dp)
                    .horizontalScroll(rememberScrollState()),
            ) {
                Chip("+", false, c, onClick = { naming = true })
                EqPresets.builtIn.forEach { (id, _) ->
                    Chip(names[id] ?: id, profile.presetId == id, c, onClick = { GerasAudio.applyPreset(id) })
                }
                custom.forEach { preset ->
                    Chip(preset.name, profile.presetId == preset.id, c, onClick = { GerasAudio.applyPreset(preset.id) }, onLongClick = { GerasAudio.deleteCustomPreset(preset.id) })
                }
            }

            EqGraph(
                gains = profile.gains,
                colors = c,
                enabled = profile.enabled && !bypass,
                onDragEnd = { GerasAudio.commitEq() },
                onGain = { band, db ->
                    GerasAudio.updateEq(persist = false) { p ->
                        p.copy(gains = p.gains.toMutableList().also { it[band] = db }, presetId = EqPresets.CUSTOM, enabled = true)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .padding(top = 16.dp),
            )

            LabeledSlider(
                label = stringResource(R.string.geras_surround),
                value = profile.surround,
                valueText = percent(profile.surround),
                c = c,
            ) { v -> GerasAudio.updateEq { it.copy(surround = v) } }
            com.metrolist.music.geras.ui.GerasExpandable(
                title = stringResource(R.string.geras_eq_more),
                summary = stringResource(if (output == OutputKind.HEADPHONES) R.string.geras_eq_headphones else R.string.geras_eq_speaker) + if (auto) " · " + stringResource(R.string.geras_eq_auto) else "",
                content = c.content,
                muted = c.contentMuted,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 14.dp),
                ) {
                    OutputKind.entries.forEach { kind ->
                        val label = stringResource(if (kind == OutputKind.HEADPHONES) R.string.geras_eq_headphones else R.string.geras_eq_speaker)
                        Chip(if (kind == output) "$label •" else label, editing == kind, c, onClick = {
                            if (auto) GerasAudio.setAutoSwitch(false)
                            GerasAudio.selectEditing(kind)
                        })
                    }
                    Spacer(Modifier.weight(1f))
                    Text(stringResource(R.string.geras_eq_auto), color = c.contentMuted, fontSize = 13.sp)
                    Spacer(Modifier.width(6.dp))
                    com.metrolist.music.geras.ui.GerasSwitch(checked = auto, onCheckedChange = { GerasAudio.setAutoSwitch(it) }, accent = c.accent, track = c.content.copy(alpha = 0.15f))
                }


                LabeledSlider(
                    label = stringResource(R.string.geras_preamp),
                    value = profile.preamp,
                    valueText = String.format(Locale.US, "%+.1f dB", profile.preamp),
                    c = c,
                    range = -12f..6f,
                    origin = 0f,
                ) { v -> GerasAudio.updateEq { it.copy(preamp = Math.round(v * 2) / 2f) } }


                TextButton(onClick = onOpenAutoEq) {
                    Text(stringResource(R.string.geras_eq_autoeq), color = c.accent)
                }
            }
        }
    }

    if (naming) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text(stringResource(R.string.geras_eq_save_preset)) },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    GerasAudio.saveCustomPreset(name)
                    naming = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { naming = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}

/** Monotone cubic (Fritsch–Carlson) through the band points, in log-frequency space. */
private fun monotoneSpline(xs: FloatArray, ys: FloatArray): (Float) -> Float {
    val n = xs.size
    val d = FloatArray(n - 1) { (ys[it + 1] - ys[it]) / (xs[it + 1] - xs[it]) }
    val m = FloatArray(n)
    m[0] = d[0]
    m[n - 1] = d[n - 2]
    for (i in 1 until n - 1) m[i] = if (d[i - 1] * d[i] <= 0f) 0f else (d[i - 1] + d[i]) / 2f
    for (i in 0 until n - 1) {
        if (d[i] == 0f) {
            m[i] = 0f
            m[i + 1] = 0f
        } else {
            val a = m[i] / d[i]
            val b = m[i + 1] / d[i]
            val h = a * a + b * b
            if (h > 9f) {
                val t = 3f / sqrt(h)
                m[i] = t * a * d[i]
                m[i + 1] = t * b * d[i]
            }
        }
    }
    return { x ->
        when {
            x <= xs[0] -> ys[0]
            x >= xs[n - 1] -> ys[n - 1]
            else -> {
                var i = 0
                while (x > xs[i + 1]) i++
                val h = xs[i + 1] - xs[i]
                val t = (x - xs[i]) / h
                val t2 = t * t
                val t3 = t2 * t
                (2 * t3 - 3 * t2 + 1) * ys[i] + (t3 - 2 * t2 + t) * h * m[i] +
                    (-2 * t3 + 3 * t2) * ys[i + 1] + (t3 - t2) * h * m[i + 1]
            }
        }
    }
}

@Composable
internal fun EqGraph(
    gains: List<Float>,
    colors: GerasPlayerColors,
    enabled: Boolean,
    onGain: (Int, Float) -> Unit,
    modifier: Modifier = Modifier,
    onDragEnd: () -> Unit = {},
) {
    val measurer = rememberTextMeasurer()
    val labelFamily = MaterialTheme.typography.labelSmall.fontFamily
    val latestGains by rememberUpdatedState(gains)
    val latestOnGain by rememberUpdatedState(onGain)
    val latestOnDragEnd by rememberUpdatedState(onDragEnd)
    var active by remember { mutableIntStateOf(-1) }
    val labelSpace = 34.dp
    val c = colors
    val bands = GerasEq.BANDS
    val edgePad = 22.dp

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            val padPx = edgePad.toPx()
            fun xOfBand(i: Int) = padPx + i * (size.width - 2 * padPx) / (bands.size - 1)
            fun dbAt(y: Float): Float {
                val plotH = size.height - labelSpace.toPx()
                val pad = 12.dp.toPx()
                val v = GerasEq.MAX_DB - ((y - pad) / (plotH - 2 * pad)) * (GerasEq.MAX_DB - GerasEq.MIN_DB)
                return (Math.round(v.coerceIn(GerasEq.MIN_DB, GerasEq.MAX_DB) * 2) / 2f)
            }
            var lastTap = 0L
            awaitEachGesture {
                // Take the gesture immediately so the bottom sheet never starts dragging.
                val down = awaitFirstDown(requireUnconsumed = false)
                down.consume()
                val band = bands.indices.minBy { abs(xOfBand(it) - down.position.x) }
                val now = System.currentTimeMillis()
                if (now - lastTap < 300) {
                    latestOnGain(band, 0f)
                    latestOnDragEnd()
                    lastTap = 0L
                    return@awaitEachGesture
                }
                lastTap = now
                active = band
                val startY = down.position.y
                val startDb = latestGains.getOrElse(band) { 0f }
                val startDbAtFinger = dbAt(startY)
                var moved = false
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    change.consume()
                    if (!moved && abs(change.position.y - startY) < viewConfiguration.touchSlop / 2) continue
                    moved = true
                    // Relative drag: the point follows the finger without jumping to it.
                    val db = (startDb + (dbAt(change.position.y) - startDbAtFinger)).coerceIn(GerasEq.MIN_DB, GerasEq.MAX_DB)
                    val rounded = Math.round(db * 2) / 2f
                    if (rounded != latestGains.getOrElse(band) { 0f }) latestOnGain(band, rounded)
                }
                active = -1
                if (moved) latestOnDragEnd()
            }
        },
    ) {
        val w = size.width
        val plotH = size.height - labelSpace.toPx()
        val pad = 12.dp.toPx()
        val padX = edgePad.toPx()
        fun xOfBand(i: Int) = padX + i * (w - 2 * padX) / (bands.size - 1)
        fun yOf(db: Float) = pad + (GerasEq.MAX_DB - db) / (GerasEq.MAX_DB - GerasEq.MIN_DB) * (plotH - 2 * pad)
        val alpha = if (enabled) 1f else 0.4f

        listOf(-12f, -6f, 0f, 6f, 12f).forEach { db ->
            drawLine(
                c.content.copy(alpha = if (db == 0f) 0.22f else 0.07f),
                Offset(0f, yOf(db)),
                Offset(w, yOf(db)),
                strokeWidth = 1.dp.toPx(),
                pathEffect = if (db == 0f) null else PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
            )
        }
        bands.indices.forEach { i ->
            drawLine(c.content.copy(alpha = 0.04f), Offset(xOfBand(i), pad), Offset(xOfBand(i), plotH - pad), strokeWidth = 1.dp.toPx())
        }

        val xs = FloatArray(bands.size + 2) { i ->
            when (i) {
                0 -> 0f
                bands.size + 1 -> w
                else -> xOfBand(i - 1)
            }
        }
        val ys = FloatArray(bands.size + 2) { i ->
            when (i) {
                0 -> gains.getOrElse(0) { 0f } * 0.85f
                bands.size + 1 -> gains.getOrElse(bands.size - 1) { 0f } * 0.85f
                else -> gains.getOrElse(i - 1) { 0f }
            }
        }
        val spline = monotoneSpline(xs, ys)
        val curve = Path()
        val steps = 120
        for (sIdx in 0..steps) {
            val x = w * sIdx / steps
            val y = yOf(spline(x))
            if (sIdx == 0) curve.moveTo(x, y) else curve.lineTo(x, y)
        }
        val fill = Path().apply {
            addPath(curve)
            lineTo(w, plotH)
            lineTo(0f, plotH)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(c.accent.copy(alpha = 0.42f * alpha), c.accent.copy(alpha = 0f)), startY = 0f, endY = plotH))
        drawPath(curve, c.accent.copy(alpha = alpha), style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))

        bands.forEachIndexed { i, f0 ->
            val g = gains.getOrElse(i) { 0f }
            val x = xOfBand(i)
            val y = yOf(g)
            val r = if (i == active) 11.dp.toPx() else 7.dp.toPx()
            if (i == active) drawCircle(c.accent.copy(alpha = 0.22f), r * 2f, Offset(x, y))
            drawCircle(c.background, r + 2.dp.toPx(), Offset(x, y))
            drawCircle(c.accent.copy(alpha = alpha), r, Offset(x, y))
            val freq = if (f0 >= 1000) "${(f0 / 1000).let { if (it % 1f == 0f) it.toInt().toString() else it.toString() }}k" else f0.toInt().toString()
            val db = if (abs(g) < 0.05f) "0" else String.format(Locale.US, "%+.1f", g)
            val l1 = measurer.measure(freq, TextStyle(color = c.contentMuted, fontSize = 10.sp, fontFamily = labelFamily))
            val l2 = measurer.measure(db, TextStyle(color = if (i == active) c.accent else c.content, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, fontFamily = labelFamily))
            val lx = (x - l1.size.width / 2f).coerceIn(0f, w - l1.size.width)
            val dx = (x - l2.size.width / 2f).coerceIn(0f, w - l2.size.width)
            drawText(l2, topLeft = Offset(dx, plotH + 2.dp.toPx()))
            drawText(l1, topLeft = Offset(lx, plotH + 2.dp.toPx() + l2.size.height))
        }
    }
}


private fun clock(ms: Long): String {
    val t = (ms / 1000).coerceAtLeast(0)
    return if (t >= 3600) "%d:%02d:%02d".format(t / 3600, t / 60 % 60, t % 60) else "%d:%02d".format(t / 60, t % 60)
}

/** A-B repeat: mark A, mark B, and the fragment loops until cleared or the track changes. */
@Composable
private fun LoopControls(c: GerasPlayerColors) {
    val player = com.metrolist.music.LocalPlayerConnection.current?.player ?: return
    val loop by GerasLoop.state.collectAsState()
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        val label = when {
            loop.a == null -> stringResource(R.string.geras_loop_set_a)
            loop.b == null -> stringResource(R.string.geras_loop_set_b)
            else -> stringResource(R.string.geras_loop_clear)
        }
        val armed = loop.a != null
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .gerasBounce(0.92f)
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(androidx.compose.animation.animateColorAsState(if (armed) c.accent else c.content.copy(alpha = 0.08f), label = "loopBg").value)
                .clickable { GerasLoop.tap(player.currentPosition) },
        ) {
            androidx.compose.animation.AnimatedContent(label, transitionSpec = { com.metrolist.music.geras.ui.GerasTextSwap }, label = "loopLabel") { text ->
                Text(text, color = if (armed) c.onAccent else c.content, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(96.dp)) {
            Text("A  " + (loop.a?.let(::clock) ?: "–"), color = c.content, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text("B  " + (loop.b?.let(::clock) ?: "–"), color = c.contentMuted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Sleep timer with the fade-out Metrolist's timer already supports. */
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GerasSleepSheet(colors: GerasPlayerColors, onDismiss: () -> Unit) {
    val c = colors
    val timer = com.metrolist.music.LocalPlayerConnection.current?.service?.sleepTimer ?: return
    var fade by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(true) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    androidx.compose.runtime.LaunchedEffect(timer.isActive) {
        while (timer.isActive) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(500)
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.background,
        contentColor = c.content,
        dragHandle = { SheetHandle(c) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text(stringResource(R.string.geras_sleep_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.content)
            androidx.compose.animation.AnimatedVisibility(timer.isActive) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    Text(
                        if (timer.pauseWhenSongEnd) stringResource(R.string.geras_sleep_end_of_track) else clock(timer.triggerTime - now),
                        fontSize = if (timer.pauseWhenSongEnd) 24.sp else 56.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = c.accent,
                        style = androidx.compose.material3.LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 12.dp)) {
                        if (!timer.pauseWhenSongEnd) {
                            Segment("+5 " + stringResource(R.string.geras_sleep_min), false, c, Modifier.width(110.dp)) {
                                val left = ((timer.triggerTime - System.currentTimeMillis()) / 60_000L).toInt() + 5
                                timer.start(left.coerceAtLeast(1), false, timer.fadeOutEnabled)
                            }
                        }
                        Segment(stringResource(R.string.geras_sleep_off), true, c, Modifier.width(130.dp)) { timer.clear() }
                    }
                }
            }
            SectionTitle(stringResource(R.string.geras_sleep_in), c)
            val options = listOf(5, 10, 15, 30, 45, 60, 90, -1)
            options.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    row.forEach { m ->
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .gerasBounce(0.9f)
                                .weight(1f)
                                .height(54.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(c.content.copy(alpha = 0.08f))
                                .clickable {
                                    timer.start(m, false, fade)
                                    onDismiss()
                                },
                        ) {
                            if (m == -1) {
                                Text(stringResource(R.string.geras_sleep_track), color = c.content, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(m.toString(), color = c.content, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                                    Text(stringResource(R.string.geras_sleep_min), color = c.contentMuted, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.geras_sleep_fade), color = c.content, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.geras_sleep_fade_hint), color = c.contentMuted, fontSize = 12.sp)
                }
                com.metrolist.music.geras.ui.GerasSwitch(checked = fade, onCheckedChange = { fade = it }, accent = c.accent, track = c.content.copy(alpha = 0.15f))
            }
        }
    }
}
