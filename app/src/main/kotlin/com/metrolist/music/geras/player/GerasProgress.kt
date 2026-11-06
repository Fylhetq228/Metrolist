package com.metrolist.music.geras.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.metrolist.music.R
import com.metrolist.music.models.MediaMetadata

private fun formatTime(ms: Long): String {
    if (ms < 0) return "0:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/**
 * Thin seek bar; grows and shows a thumb while dragged. Position is read in the draw
 * phase so ticking playback only redraws the canvas.
 */
@Composable
fun GerasSeekBar(
    positionProvider: () -> Long,
    durationProvider: () -> Long,
    onSeek: (Long) -> Unit,
    enabled: Boolean,
    colors: GerasPlayerColors,
    onScrub: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val active by animateFloatAsState(if (dragFraction != null) 1f else 0f, label = "seekActive")

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { pos ->
                    val d = durationProvider()
                    if (d > 0) onSeek((pos.x / size.width).coerceIn(0f, 1f).times(d).toLong())
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { pos ->
                        dragFraction = (pos.x / size.width).coerceIn(0f, 1f)
                        onScrub((dragFraction!! * durationProvider()).toLong())
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        onScrub((dragFraction!! * durationProvider()).toLong())
                    },
                    onDragEnd = {
                        val d = durationProvider()
                        dragFraction?.let { if (d > 0) onSeek((it * d).toLong()) }
                        dragFraction = null
                        onScrub(null)
                    },
                    onDragCancel = {
                        dragFraction = null
                        onScrub(null)
                    },
                )
            },
    ) {
        val d = durationProvider()
        val fraction = dragFraction ?: if (d > 0) (positionProvider().toFloat() / d).coerceIn(0f, 1f) else 0f
        val stroke = (3.dp + 3.dp * active).toPx()
        val y = size.height / 2
        drawLine(colors.contentMuted.copy(alpha = 0.25f), Offset(0f, y), Offset(size.width, y), stroke, StrokeCap.Round)
        if (fraction > 0f) {
            drawLine(colors.accent, Offset(0f, y), Offset(size.width * fraction, y), stroke, StrokeCap.Round)
        }
        if (active > 0f) {
            drawCircle(colors.accent, radius = 7.dp.toPx() * active, center = Offset(size.width * fraction, y))
        }
    }
}

@Composable
fun GerasTimeRow(
    positionProvider: () -> Long,
    durationProvider: () -> Long,
    scrubPosition: Long?,
    next: MediaMetadata?,
    colors: GerasPlayerColors,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val timeStyle = Modifier.widthIn(min = 40.dp)
    val latestPosition by androidx.compose.runtime.rememberUpdatedState(positionProvider)
    val latestDuration by androidx.compose.runtime.rememberUpdatedState(durationProvider)
    val positionText by remember { androidx.compose.runtime.derivedStateOf { formatTime(latestPosition()) } }
    val durationText by remember { androidx.compose.runtime.derivedStateOf { formatTime(latestDuration()) } }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = scrubPosition?.let(::formatTime) ?: positionText,
            color = colors.contentMuted,
            fontSize = 12.sp,
            style = androidx.compose.material3.LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
            modifier = timeStyle,
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        ) {
            androidx.compose.animation.AnimatedVisibility(visible = next != null, enter = fadeIn(), exit = fadeOut()) {
                if (next != null) NextCapsule(next, colors, onNextClick)
            }
        }
        Text(
            text = durationText,
            color = colors.contentMuted,
            fontSize = 12.sp,
            style = androidx.compose.material3.LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = timeStyle,
        )
    }
}

@Composable
private fun NextCapsule(next: MediaMetadata, colors: GerasPlayerColors, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(colors.surface)
            .clickable(onClick = onClick)
            .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
    ) {
        AsyncImage(
            model = next.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(20.dp).clip(RoundedCornerShape(50)),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.geras_up_next, next.title),
            color = colors.content,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.basicMarquee(initialDelayMillis = 1500, velocity = 28.dp),
        )
    }
}
