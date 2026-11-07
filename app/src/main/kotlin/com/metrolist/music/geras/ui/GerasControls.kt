package com.metrolist.music.geras.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.music.R
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * iOS 26 slider: a thin track with a white knob that turns into a liquid-glass lens while it is
 * held. The lens magnifies the track under it, swells with a spring, and the whole control
 * stretches like rubber past the ends. Drag is relative (precise), a tap jumps with a spring,
 * and light haptics mark the ends and the [origin] (0 dB, 1x, 0 semitones).
 */
@Composable
fun GerasSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    label: String? = null,
    valueText: String? = null,
    enabled: Boolean = true,
    origin: Float? = null,
    steps: Int = 0,
    accent: Color,
    track: Color,
    content: Color,
    onAccent: Color,
    @Suppress("UNUSED_PARAMETER") height: Dp = 46.dp,
    onValueChangeFinished: () -> Unit = {},
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
    fun frac(v: Float) = ((v - valueRange.start) / span).coerceIn(0f, 1f)
    fun snap(v: Float): Float {
        val c = v.coerceIn(valueRange.start, valueRange.endInclusive)
        if (steps <= 0) return c
        val step = span / (steps + 1)
        return valueRange.start + ((c - valueRange.start) / step).roundToInt() * step
    }
    val latestValue by rememberUpdatedState(value)
    val latestChange by rememberUpdatedState(onValueChange)
    val shown = remember { Animatable(frac(value)) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(value) { if (!dragging) shown.animateTo(frac(value), spring(dampingRatio = 0.75f, stiffness = 420f)) }
    var pressed by remember { mutableStateOf(false) }
    val lens by animateFloatAsState(if (pressed) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 420f), label = "lens")
    var stretch by remember { mutableFloatStateOf(0f) }
    val stretchAnim by animateFloatAsState(stretch, spring(dampingRatio = 0.45f, stiffness = 380f), label = "rubber")
    val alpha = if (enabled) 1f else 0.4f

    Column(modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha }) {
        if (label != null || valueText != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label.orEmpty(), color = content, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
                Text(valueText.orEmpty(), color = content.copy(alpha = 0.6f), fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .graphicsLayer {
                    val s = 1f + abs(stretchAnim) / size.width.coerceAtLeast(1f) * 0.35f
                    scaleX = s
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (stretchAnim > 0) 0f else 1f, 0.5f)
                }
                .pointerInput(enabled, valueRange, steps) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        pressed = true
                        val startValue = latestValue
                        val usable = size.width - 28.dp.toPx()
                        var moved = 0f
                        var lastSnap = snap(startValue)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            moved += change.positionChange().x
                            if (abs(moved) > viewConfiguration.touchSlop || dragging) {
                                dragging = true
                                change.consume()
                                val raw = startValue + moved / usable * span
                                val v = snap(raw)
                                stretch = when {
                                    raw > valueRange.endInclusive -> (raw - valueRange.endInclusive) / span * usable
                                    raw < valueRange.start -> (raw - valueRange.start) / span * usable
                                    else -> 0f
                                }
                                if (v != lastSnap) {
                                    val hitEnd = v == valueRange.start || v == valueRange.endInclusive
                                    val crossed = origin != null && (lastSnap - origin) * (v - origin) <= 0f
                                    if (steps > 0 || hitEnd || crossed) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    lastSnap = v
                                    scope.launch { shown.snapTo(frac(v)) }
                                    latestChange(v)
                                }
                            }
                        }
                        if (!dragging) {
                            val usable2 = size.width - 28.dp.toPx()
                            val v = snap(valueRange.start + ((down.position.x - 14.dp.toPx()) / usable2) * span)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            latestChange(v)
                        }
                        dragging = false
                        pressed = false
                        stretch = 0f
                        onValueChangeFinished()
                    }
                },
        ) {
            val edge = 14.dp.toPx()
            val cy = size.height / 2f
            val trackH = 6.dp.toPx()
            val usable = size.width - edge * 2
            val f = shown.value
            val o = origin?.let(::frac) ?: 0f
            val x = edge + usable * f
            val ox = edge + usable * o

            fun drawTrack(thickness: Float) {
                val r = thickness / 2f
                drawRoundRect(track, Offset(edge - r, cy - r), Size(usable + thickness, thickness), CornerRadius(r, r))
                val l = minOf(x, ox)
                val rr = maxOf(x, ox)
                val left = if (origin == null) edge - r else l
                drawRoundRect(accent, Offset(left, cy - r), Size((rr - left).coerceAtLeast(0f) + if (origin == null) 0f else 0f, thickness), CornerRadius(r, r))
                if (origin != null) drawCircle(content.copy(alpha = 0.45f), 2.dp.toPx(), Offset(ox, cy))
            }
            drawTrack(trackH)

            // Knob → lens.
            val w = 28.dp.toPx() + 22.dp.toPx() * lens
            val h = 28.dp.toPx() + 8.dp.toPx() * lens
            val tl = Offset(x - w / 2f, cy - h / 2f)
            val cr = CornerRadius(h / 2f, h / 2f)
            drawRoundRect(Color.Black.copy(alpha = 0.28f), tl + Offset(0f, 3.dp.toPx()), Size(w, h), cr)
            // Solid white knob fades out as the glass fades in.
            drawRoundRect(Color.White.copy(alpha = 1f - 0.9f * lens.coerceIn(0f, 1f)), tl, Size(w, h), cr)
            if (lens > 0.01f) {
                val path = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(tl.x, tl.y, tl.x + w, tl.y + h, cr)) }
                clipPath(path) {
                    // Magnified track seen through the glass.
                    drawRect(Color.White.copy(alpha = 0.06f * lens), tl, Size(w, h))
                    drawTrack(trackH * (1f + 1.3f * lens))
                    drawRect(
                        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f * lens), Color.Transparent, Color.Black.copy(alpha = 0.12f * lens)), startY = tl.y, endY = tl.y + h),
                        tl,
                        Size(w, h),
                    )
                }
                drawRoundRect(Color.White.copy(alpha = 0.55f * lens.coerceIn(0f, 1f)), tl, Size(w, h), cr, style = Stroke(1.dp.toPx()))
            }
        }
    }
}

/**
 * Switch with a liquid thumb: it widens while pressed and stretches as it travels, the track
 * fades between colours, and a light haptic confirms the change.
 */
@Composable
fun GerasSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color,
    track: Color,
    thumb: Color = Color.White,
) {
    val haptic = LocalHapticFeedback.current
    val pos by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.62f, stiffness = 420f), label = "switchPos")
    var pressed by remember { mutableStateOf(false) }
    val press by animateFloatAsState(if (pressed) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 600f), label = "switchPress")
    val trackColor by animateColorAsState(if (checked) accent else track, tween(220), label = "switchTrack")
    Canvas(
        modifier
            .size(width = 54.dp, height = 32.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .pointerInput(enabled, checked) {
                if (!enabled || onCheckedChange == null) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown()
                    pressed = true
                    val up = waitForUpOrCancellationCompat()
                    pressed = false
                    if (up) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onCheckedChange(!checked)
                    }
                }
            },
    ) {
        val r = size.height / 2f
        drawRoundRect(trackColor, cornerRadius = CornerRadius(r, r))
        val pad = 3.dp.toPx()
        val d = size.height - pad * 2
        // Travel stretch peaks mid-way; pressing widens the thumb like iOS.
        val travel = 1f - abs(pos - 0.5f) * 2f
        val w = d + d * 0.35f * travel.coerceIn(0f, 1f) + d * 0.28f * press
        val x = pad + (size.width - pad * 2 - w) * pos.coerceIn(0f, 1f)
        drawRoundRect(Color.Black.copy(alpha = 0.18f), topLeft = Offset(x, pad + 1.5.dp.toPx()), size = Size(w, d), cornerRadius = CornerRadius(d / 2, d / 2))
        drawRoundRect(thumb, topLeft = Offset(x, pad), size = Size(w, d), cornerRadius = CornerRadius(d / 2, d / 2))
    }
}

private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.waitForUpOrCancellationCompat(): Boolean =
    waitForUpOrCancellation() != null

/** Round checkbox: fills with a spring and draws its tick stroke by stroke. */
@Composable
fun GerasCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
    onAccent: Color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
    ring: Color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val fill by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "cbFill")
    val tick by animateFloatAsState(if (checked) 1f else 0f, tween(if (checked) 260 else 120, delayMillis = if (checked) 60 else 0), label = "cbTick")
    val haptic = LocalHapticFeedback.current
    Box(
        modifier
            .size(40.dp)
            .clip(RoundedCornerShape(50))
            .clickable(enabled = enabled && onCheckedChange != null, interactionSource = remember { MutableInteractionSource() }, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onCheckedChange?.invoke(!checked)
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(24.dp)) {
            val r = size.minDimension / 2f
            drawCircle(ring.copy(alpha = 0.6f * (1f - fill.coerceIn(0f, 1f))), r - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
            drawCircle(accent, r * fill.coerceIn(0f, 1.15f))
            if (tick > 0f) {
                val p = Path().apply {
                    moveTo(size.width * 0.28f, size.height * 0.53f)
                    lineTo(size.width * 0.44f, size.height * 0.68f)
                    lineTo(size.width * 0.73f, size.height * 0.36f)
                }
                val measure = PathMeasure().apply { setPath(p, false) }
                val part = Path()
                measure.getSegment(0f, measure.length * tick, part, true)
                drawPath(part, onAccent, style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

/** Collapsible section header used to keep sheets light: title, short summary, chevron. */
@Composable
fun GerasExpandable(
    title: String,
    summary: String?,
    content: Color,
    muted: Color,
    initiallyOpen: Boolean = false,
    body: @Composable () -> Unit,
) {
    var open by androidx.compose.runtime.saveable.rememberSaveable(title) { mutableStateOf(initiallyOpen) }
    val rot by animateFloatAsState(if (open) 180f else 0f, spring(dampingRatio = 0.7f, stiffness = 400f), label = "chev")
    Column(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable { open = !open }
                .padding(vertical = 10.dp, horizontal = 2.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = content, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (!summary.isNullOrBlank()) Text(summary, color = muted, fontSize = 12.sp, maxLines = 1)
            }
            Icon(painterResource(R.drawable.expand_more), null, tint = muted, modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = rot })
        }
        AnimatedVisibility(
            open,
            enter = expandVertically(spring(dampingRatio = 0.85f, stiffness = 380f)) + fadeIn(tween(200, delayMillis = 60)),
            exit = shrinkVertically(spring(dampingRatio = 1f, stiffness = 500f)) + fadeOut(tween(120)),
        ) {
            Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) { body() }
        }
    }
}
