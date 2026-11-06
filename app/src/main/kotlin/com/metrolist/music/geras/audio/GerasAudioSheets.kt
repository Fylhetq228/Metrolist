package com.metrolist.music.geras.audio

import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
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
        modifier = Modifier
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
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = if (enabled) c.content else c.contentMuted, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(valueText, color = c.contentMuted, fontSize = 13.sp)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, enabled = enabled, colors = sliderColors(c))
    }
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
                        modifier = Modifier
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
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                StepButton("−", c) { GerasAudio.setSpeedFx(fx.copy(speed = fx.speed - 0.01f)) }
                Slider(
                    value = fx.speed,
                    onValueChange = { GerasAudio.setSpeedFx(fx.copy(speed = it)) },
                    valueRange = 0.5f..2f,
                    colors = sliderColors(c),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                StepButton("+", c) { GerasAudio.setSpeedFx(fx.copy(speed = fx.speed + 0.01f)) }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 52.dp)) {
                listOf("0.5x", "1.0x", "1.5x", "2.0x").forEachIndexed { i, t ->
                    Text(
                        t,
                        color = if (i == 1 && abs(fx.speed - 1f) < 0.005f) c.content else c.contentMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f),
                        textAlign = when (i) {
                            0 -> androidx.compose.ui.text.style.TextAlign.Start
                            3 -> androidx.compose.ui.text.style.TextAlign.End
                            else -> androidx.compose.ui.text.style.TextAlign.Center
                        },
                    )
                }
            }

            SectionTitle(stringResource(R.string.geras_presets), c)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                SpeedPresets.all.forEach { (id, preset) ->
                    val selected = abs(fx.speed - preset.speed) < 0.005f && fx.linked == preset.linked &&
                        abs(fx.reverb - preset.reverb) < 0.01f && (id != "normal" || (fx.eightD < 0.01f && fx.bass < 0.01f && abs(fx.pitchSemitones) < 0.05f))
                    Chip(presetNames[id] ?: id, selected, c, onClick = { GerasAudio.setSpeedFx(preset) })
                }
            }

            SectionTitle(stringResource(R.string.geras_pitch_mode), c)
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
            ) { GerasAudio.setSpeedFx(fx.copy(pitchSemitones = Math.round(it * 10) / 10f)) }

            SectionTitle(stringResource(R.string.geras_effects), c)
            LabeledSlider(stringResource(R.string.geras_reverb), fx.reverb, percent(fx.reverb), c) { GerasAudio.setSpeedFx(fx.copy(reverb = it)) }
            LabeledSlider("8D", fx.eightD, percent(fx.eightD), c) { GerasAudio.setSpeedFx(fx.copy(eightD = it)) }
            LabeledSlider(stringResource(R.string.geras_bass_boost), fx.bass, percent(fx.bass), c) { GerasAudio.setSpeedFx(fx.copy(bass = it)) }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
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
        modifier = Modifier
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
        modifier = modifier
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
                    modifier = Modifier
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
                Switch(checked = profile.enabled, onCheckedChange = { on -> GerasAudio.updateEq { it.copy(enabled = on) } }, colors = switchColors(c))
            }
            Text(
                stringResource(if (bypass) R.string.geras_ab_b else R.string.geras_ab_a),
                color = c.contentMuted,
                fontSize = 12.sp,
            )

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
                Switch(checked = auto, onCheckedChange = { GerasAudio.setAutoSwitch(it) }, colors = switchColors(c))
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
                onGain = { band, db ->
                    GerasAudio.updateEq { p ->
                        p.copy(gains = p.gains.toMutableList().also { it[band] = db }, presetId = EqPresets.CUSTOM, enabled = true)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .padding(top = 16.dp),
            )

            LabeledSlider(
                label = stringResource(R.string.geras_preamp),
                value = profile.preamp,
                valueText = String.format(Locale.US, "%+.1f dB", profile.preamp),
                c = c,
                range = -12f..6f,
            ) { v -> GerasAudio.updateEq { it.copy(preamp = Math.round(v * 2) / 2f) } }

            TextButton(onClick = onOpenAutoEq, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.geras_eq_autoeq), color = c.accent)
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

private const val GRAPH_FS = 48_000.0

private fun peakingDb(f: Double, f0: Double, gainDb: Double, q: Double): Double {
    if (abs(gainDb) < 0.01) return 0.0
    val a = 10.0.pow(gainDb / 40.0)
    val w0 = 2 * PI * f0 / GRAPH_FS
    val alpha = sin(w0) / (2 * q)
    val b0 = 1 + alpha * a
    val b1 = -2 * cos(w0)
    val b2 = 1 - alpha * a
    val a0 = 1 + alpha / a
    val a1 = -2 * cos(w0)
    val a2 = 1 - alpha / a
    val w = 2 * PI * f / GRAPH_FS
    fun mag(c0: Double, c1: Double, c2: Double): Double {
        val re = c0 + c1 * cos(w) + c2 * cos(2 * w)
        val im = -(c1 * sin(w) + c2 * sin(2 * w))
        return sqrt(re * re + im * im)
    }
    return 20 * log10(mag(b0, b1, b2) / mag(a0, a1, a2))
}

@Composable
internal fun EqGraph(
    gains: List<Float>,
    colors: GerasPlayerColors,
    enabled: Boolean,
    onGain: (Int, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val latestGains by rememberUpdatedState(gains)
    var active by remember { mutableIntStateOf(-1) }
    val minF = 20.0
    val maxF = 20_000.0
    val labelSpace = 34.dp
    val c = colors

    fun xOf(f: Double, w: Float) = (log10(f / minF) / log10(maxF / minF) * w).toFloat()

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                val plotH = { size.height - labelSpace.toPx() }
                fun dbAt(y: Float) = (GerasEq.MAX_DB - (y / plotH()) * (GerasEq.MAX_DB - GerasEq.MIN_DB))
                    .coerceIn(GerasEq.MIN_DB, GerasEq.MAX_DB)
                    .let { Math.round(it * 2) / 2f }
                fun nearest(x: Float) = GerasEq.BANDS.indices.minBy { abs(xOf(GerasEq.BANDS[it].toDouble(), size.width.toFloat()) - x) }
                detectDragGestures(
                    onDragStart = { pos -> active = nearest(pos.x) },
                    onDragEnd = { active = -1 },
                    onDragCancel = { active = -1 },
                    onDrag = { change, _ ->
                        change.consume()
                        if (active >= 0) {
                            val db = dbAt(change.position.y)
                            if (db != latestGains[active]) onGain(active, db)
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                val plotH = { size.height - labelSpace.toPx() }
                detectTapGestures(
                    onDoubleTap = { pos ->
                        val i = GerasEq.BANDS.indices.minBy { abs(xOf(GerasEq.BANDS[it].toDouble(), size.width.toFloat()) - pos.x) }
                        onGain(i, 0f)
                    },
                    onTap = { pos ->
                        if (pos.y < plotH()) {
                            val i = GerasEq.BANDS.indices.minBy { abs(xOf(GerasEq.BANDS[it].toDouble(), size.width.toFloat()) - pos.x) }
                            val db = (GerasEq.MAX_DB - (pos.y / plotH()) * (GerasEq.MAX_DB - GerasEq.MIN_DB)).coerceIn(GerasEq.MIN_DB, GerasEq.MAX_DB)
                            onGain(i, Math.round(db * 2) / 2f)
                        }
                    },
                )
            },
    ) {
        val w = size.width
        val plotH = size.height - labelSpace.toPx()
        val pad = 10.dp.toPx()
        fun yOf(db: Double) = (pad + (GerasEq.MAX_DB - db) / (GerasEq.MAX_DB - GerasEq.MIN_DB) * (plotH - 2 * pad)).toFloat()
        val alpha = if (enabled) 1f else 0.4f

        // Grid: 0 dB line and ±6/±12 guides.
        listOf(-12.0, -6.0, 0.0, 6.0, 12.0).forEach { db ->
            drawLine(
                c.content.copy(alpha = if (db == 0.0) 0.22f else 0.08f),
                Offset(0f, yOf(db)),
                Offset(w, yOf(db)),
                strokeWidth = 1.dp.toPx(),
                pathEffect = if (db == 0.0) null else PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
            )
        }

        val curve = Path()
        val steps = 160
        for (s in 0..steps) {
            val f = minF * (maxF / minF).pow(s.toDouble() / steps)
            var db = 0.0
            GerasEq.BANDS.forEachIndexed { i, f0 -> db += peakingDb(f, f0.toDouble(), gains.getOrElse(i) { 0f }.toDouble(), GerasEq.Q.toDouble()) }
            val x = xOf(f, w)
            val y = yOf(db.coerceIn(-18.0, 18.0))
            if (s == 0) curve.moveTo(x, y) else curve.lineTo(x, y)
        }
        val fill = Path().apply {
            addPath(curve)
            lineTo(w, plotH)
            lineTo(0f, plotH)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(c.accent.copy(alpha = 0.38f * alpha), c.accent.copy(alpha = 0f)), startY = 0f, endY = plotH))
        drawPath(curve, c.accent.copy(alpha = alpha), style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))

        GerasEq.BANDS.forEachIndexed { i, f0 ->
            val g = gains.getOrElse(i) { 0f }
            val x = xOf(f0.toDouble(), w)
            val y = yOf(g.toDouble())
            val r = if (i == active) 11.dp.toPx() else 7.dp.toPx()
            drawCircle(c.background, r + 2.dp.toPx(), Offset(x, y))
            drawCircle(c.accent.copy(alpha = alpha), r, Offset(x, y))
            val freq = if (f0 >= 1000) "${(f0 / 1000).let { if (it % 1f == 0f) it.toInt().toString() else it.toString() }}k" else f0.toInt().toString()
            val db = String.format(Locale.US, "%+.1f", g).replace("+0.0", "0")
            val l1 = measurer.measure(freq, TextStyle(color = c.contentMuted, fontSize = 10.sp))
            val l2 = measurer.measure(db, TextStyle(color = if (i == active) c.accent else c.content, fontSize = 10.sp, fontWeight = FontWeight.SemiBold))
            val lx = (x - l1.size.width / 2f).coerceIn(0f, w - l1.size.width)
            val dx = (x - l2.size.width / 2f).coerceIn(0f, w - l2.size.width)
            drawText(l2, topLeft = Offset(dx, plotH + 2.dp.toPx()))
            drawText(l1, topLeft = Offset(lx, plotH + 2.dp.toPx() + l2.size.height))
        }
    }
}
