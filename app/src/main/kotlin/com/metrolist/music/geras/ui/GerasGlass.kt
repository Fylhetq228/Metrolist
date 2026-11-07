package com.metrolist.music.geras.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.geras.GerasGlassBlurKey
import com.metrolist.music.geras.GerasGlassEnabledKey
import com.metrolist.music.geras.GerasGlassOpacityKey
import com.metrolist.music.ui.screens.Screens
import com.metrolist.music.utils.rememberPreference
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Shared backdrop for every glass surface in the activity. */
object GerasGlass {
    val state = HazeState()
}

/** Marks the content that glass surfaces blur. Applied to the main content in MainActivity. */
fun Modifier.gerasGlassSource(): Modifier = hazeSource(GerasGlass.state)

/**
 * "Liquid glass": a soft drop shadow lifts the surface, the backdrop is blurred and tinted,
 * a specular sheen sits on the top-left, the lower part is slightly shaded and the rim
 * catches light at the top and, more faintly, at the bottom like a thick glass lens.
 */
@Composable
fun Modifier.gerasGlass(shape: Shape, tint: Color = MaterialTheme.colorScheme.surfaceContainer): Modifier {
    val enabled by rememberPreference(GerasGlassEnabledKey, true)
    val opacity by rememberPreference(GerasGlassOpacityKey, 0.55f)
    val blur by rememberPreference(GerasGlassBlurKey, 22f)
    return this
        .shadow(elevation = 22.dp, shape = shape, ambientColor = Color.Black.copy(alpha = 0.5f), spotColor = Color.Black.copy(alpha = 0.6f))
        .clip(shape)
        .then(
            if (enabled) {
                Modifier.hazeEffect(
                    state = GerasGlass.state,
                    style = HazeStyle(
                        backgroundColor = tint,
                        tints = listOf(HazeTint(tint.copy(alpha = opacity))),
                        blurRadius = blur.dp,
                        noiseFactor = 0.05f,
                    ),
                )
            } else {
                Modifier.background(tint.copy(alpha = 0.96f))
            },
        )
        .glassLighting(shape)
}

/** Opaque raised surface with the same lighting, for cards and buttons that sit on a background. */
@Composable
fun Modifier.gerasRaised(shape: Shape, color: Color, elevation: Dp = 10.dp): Modifier =
    this
        .shadow(elevation = elevation, shape = shape, ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.45f))
        .clip(shape)
        .background(color)
        .glassLighting(shape, strength = 0.7f)

private fun Modifier.glassLighting(shape: Shape, strength: Float = 1f): Modifier =
    this
        .drawWithContent {
            drawContent()
            // Specular bloom near the top-left corner.
            drawRect(
                Brush.radialGradient(
                    0f to Color.White.copy(alpha = 0.13f * strength),
                    1f to Color.Transparent,
                    center = Offset(size.width * 0.18f, 0f),
                    radius = size.maxDimension * 0.55f,
                ),
            )
            // Top sheen and a slightly darker belly give the surface thickness.
            drawRect(
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.09f * strength),
                    0.42f to Color.Transparent,
                    0.7f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.16f * strength),
                ),
            )
        }
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.32f * strength),
                0.35f to Color.White.copy(alpha = 0.06f * strength),
                0.75f to Color.White.copy(alpha = 0.03f * strength),
                1f to Color.White.copy(alpha = 0.14f * strength),
            ),
            shape = shape,
        )

class GerasNavTab(
    val label: String,
    val icon: Int,
    val iconActive: Int,
    val selected: Boolean,
    val onClick: () -> Unit,
    val onLongClick: () -> Unit = {},
)

@Composable
fun GerasNavBar(tabs: List<GerasNavTab>, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val density = androidx.compose.ui.platform.LocalDensity.current
    val selectedIndex = tabs.indexOfFirst { it.selected }
    val scope = rememberCoroutineScope()
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        androidx.compose.foundation.layout.BoxWithConstraints(
            Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth()
                .height(62.dp)
                .gerasGlass(RoundedCornerShape(50))
                .padding(4.dp),
        ) {
            val widthPx = with(density) { maxWidth.toPx() }
            val tabW = widthPx / tabs.size.coerceAtLeast(1)
            // Two edges on different springs: the leading edge runs ahead, the trailing one
            // catches up, so the drop stretches while it travels and settles with a wobble.
            val left = remember { Animatable(tabW * selectedIndex.coerceAtLeast(0)) }
            val right = remember { Animatable(tabW * (selectedIndex.coerceAtLeast(0) + 1)) }
            val visible by animateFloatAsState(if (selectedIndex >= 0) 1f else 0f, label = "dropVisible")
            var dragging by remember { mutableStateOf(false) }
            val lift by animateFloatAsState(if (dragging) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "dropLift")

            fun moveTo(index: Int) {
                if (index < 0) return
                val l = tabW * index
                val r = l + tabW
                val goingRight = l > left.value
                val fast = spring<Float>(dampingRatio = 0.72f, stiffness = 900f)
                val slow = spring<Float>(dampingRatio = 0.68f, stiffness = 320f)
                scope.launch { left.animateTo(l, if (goingRight) slow else fast) }
                scope.launch { right.animateTo(r, if (goingRight) fast else slow) }
            }
            androidx.compose.runtime.LaunchedEffect(selectedIndex, tabW) { if (!dragging) moveTo(selectedIndex) }

            val dropModifier = Modifier
                .graphicsLayer {
                    val w = (right.value - left.value).coerceAtLeast(1f)
                    val stretch = ((w - tabW) / tabW).coerceIn(0f, 1.5f)
                    translationX = left.value
                    alpha = visible
                    scaleY = (1f - stretch * 0.12f) * (1f + lift * 0.1f)
                    scaleX = 1f + lift * 0.1f
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
                }
                .layout { measurable, constraints ->
                    val w = (right.value - left.value).coerceAtLeast(1f).toInt()
                    val placeable = measurable.measure(constraints.copy(minWidth = w, maxWidth = w))
                    layout(constraints.maxWidth, placeable.height) { placeable.place(0, 0) }
                }
            Box(
                dropModifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.verticalGradient(
                            listOf(scheme.onSurface.copy(alpha = 0.20f + 0.06f * lift), scheme.onSurface.copy(alpha = 0.08f)),
                        ),
                    )
                    .glassLighting(RoundedCornerShape(50), strength = 0.9f + lift * 0.4f),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(tabs.size, tabW) {
                        detectHorizontalDragGestures(
                            onDragStart = { dragging = true },
                            onDragEnd = {
                                dragging = false
                                val index = (((left.value + right.value) / 2f) / tabW).toInt().coerceIn(0, tabs.lastIndex)
                                moveTo(index)
                                if (index != selectedIndex) tabs[index].onClick()
                            },
                            onDragCancel = {
                                dragging = false
                                moveTo(selectedIndex)
                            },
                        ) { change, _ ->
                            change.consume()
                            val center = change.position.x.coerceIn(tabW / 2f, widthPx - tabW / 2f)
                            scope.launch { left.animateTo(center - tabW / 2f, spring(dampingRatio = 0.8f, stiffness = 1200f)) }
                            scope.launch { right.animateTo(center + tabW / 2f, spring(dampingRatio = 0.8f, stiffness = 900f)) }
                        }
                    },
            ) {
                tabs.forEach { tab ->
                    val sel by animateFloatAsState(if (tab.selected) 1f else 0f, spring(), label = "navSel")
                    val pop = remember { Animatable(1f) }
                    androidx.compose.runtime.LaunchedEffect(tab.selected) {
                        if (tab.selected) {
                            pop.snapTo(0.78f)
                            pop.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = 520f))
                        }
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .gerasBounce(0.86f, dim = false)
                            .pointerInput(tab.label) {
                                detectTapGestures(onTap = { tab.onClick() }, onLongPress = { tab.onLongClick() })
                            },
                    ) {
                        Icon(
                            painter = painterResource(if (tab.selected) tab.iconActive else tab.icon),
                            contentDescription = tab.label,
                            tint = scheme.onSurface.copy(alpha = 0.6f + 0.4f * sel),
                            modifier = Modifier
                                .size(22.dp)
                                .graphicsLayer {
                                    scaleX = pop.value
                                    scaleY = pop.value
                                },
                        )
                        Text(
                            text = tab.label,
                            fontSize = 10.sp,
                            fontWeight = if (tab.selected) FontWeight.Bold else FontWeight.Medium,
                            color = scheme.onSurface.copy(alpha = 0.6f + 0.4f * sel),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GerasMiniPlayer(
    positionState: MutableLongState,
    durationState: MutableLongState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val metadata by playerConnection.mediaMetadata.collectAsState()
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val currentSong by playerConnection.currentSong.collectAsStateWithLifecycle(initialValue = null)
    val canNext by playerConnection.canSkipNext.collectAsStateWithLifecycle()
    val canPrev by playerConnection.canSkipPrevious.collectAsStateWithLifecycle()
    val liked = currentSong?.song?.liked == true
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val scheme = MaterialTheme.colorScheme

    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.gerasBounce(0.96f)
                .padding(horizontal = 12.dp)
                .padding(top = 4.dp)
                .fillMaxWidth()
                .height(58.dp)
                .gerasGlass(RoundedCornerShape(50), tint = scheme.surfaceContainerHigh)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .pointerInput(canNext, canPrev) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            scope.launch { offset.snapTo(offset.value + amount * 0.6f) }
                        },
                        onDragEnd = {
                            val v = offset.value
                            when {
                                v < -size.width * 0.18f && canNext -> playerConnection.seekToNext()
                                v > size.width * 0.18f && canPrev -> playerConnection.seekToPrevious()
                            }
                            scope.launch { offset.animateTo(0f, spring()) }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f, spring()) } },
                    )
                }
                .padding(start = 7.dp, end = 6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .graphicsLayer { translationX = offset.value * 0.4f }
                    .clip(RoundedCornerShape(50))
                    .background(scheme.surfaceVariant),
            ) {
                AsyncImage(
                    model = metadata?.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                ProgressRing(positionState, durationState, scheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(
                Modifier
                    .weight(1f)
                    .graphicsLayer {
                        translationX = offset.value
                        alpha = 1f - (abs(offset.value) / 400f).coerceIn(0f, 0.7f)
                    },
            ) {
                Text(
                    text = metadata?.title.orEmpty(),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = metadata?.artists?.joinToString { it.name }.orEmpty(),
                    fontSize = 13.sp,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(Modifier.gerasPop(liked)) {
                MiniButton(if (liked) R.drawable.favorite else R.drawable.favorite_border, if (liked) scheme.error else scheme.onSurface) {
                    playerConnection.toggleLike()
                }
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.gerasBounce(0.96f)
                    .size(44.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable { playerConnection.togglePlayPause() },
            ) {
                AnimatedContent(
                    targetState = isPlaying,
                    transitionSpec = { (scaleIn(initialScale = 0.6f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.6f) + fadeOut()) },
                    label = "miniPlay",
                ) { playing ->
                    Icon(
                        painter = painterResource(if (playing) R.drawable.pause else R.drawable.play),
                        contentDescription = null,
                        tint = scheme.onSurface,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressRing(position: MutableLongState, duration: MutableLongState, color: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val d = duration.longValue
        val p = if (d > 0) (position.longValue.toFloat() / d).coerceIn(0f, 1f) else 0f
        val stroke = 2.5.dp.toPx()
        drawArc(Color.Black.copy(alpha = 0.35f), 0f, 360f, false, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke), topLeft = Offset(stroke / 2, stroke / 2), size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke))
        drawArc(color, -90f, 360f * p, false, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round), topLeft = Offset(stroke / 2, stroke / 2), size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke))
    }
}

@Composable
private fun MiniButton(icon: Int, tint: Color, size: Dp = 44.dp, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.gerasBounce(0.96f)
            .size(size)
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick),
    ) {
        Icon(painterResource(icon), null, tint = tint, modifier = Modifier.size(22.dp))
    }
}
