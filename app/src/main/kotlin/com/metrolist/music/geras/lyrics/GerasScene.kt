package com.metrolist.music.geras.lyrics

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.metrolist.music.R
import com.metrolist.music.geras.audio.GerasPulse
import com.metrolist.music.geras.theme.GerasFonts
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Backdrop of the lyrics scene: the cover, heavily blurred and slowly drifting, under a dark
 * veil so the text stays readable. Breathes a little with the loudness of the track.
 */
@Composable
fun GerasSceneBackdrop(thumbnailUrl: String?, base: Color, modifier: Modifier = Modifier) {
    val drift = rememberInfiniteTransition(label = "sceneDrift")
    val phase by drift.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(46_000, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    Box(modifier.fillMaxSize().background(base)) {
        if (thumbnailUrl != null) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val s = 1.5f + 0.06f * GerasPulse.level
                        scaleX = s
                        scaleY = s
                        translationX = size.width * 0.07f * sin(phase)
                        translationY = size.height * 0.05f * cos(phase * 1.3f)
                    }
                    // Before Android 12 there is no cheap blur: the veil below simply gets denser.
                    .blur(72.dp),
            )
        }
        val veil = if (android.os.Build.VERSION.SDK_INT >= 31) 0.5f else 0.74f
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color.Black.copy(alpha = veil - 0.08f), 0.5f to Color.Black.copy(alpha = veil), 1f to Color.Black.copy(alpha = veil + 0.18f)),
            ),
        )
    }
}

private class Mote(var x: Float, var y: Float, val r: Float, val phase: Float, var vx: Float = 0f, var vy: Float = 0f)

/**
 * Dust in the light: slow drifting specks. They twitch on kick drums and scatter from the
 * centre when the track drops.
 */
@Composable
fun GerasSceneDust(tint: Color, modifier: Modifier = Modifier) {
    val motes = remember { List(54) { Mote(Random.nextFloat(), Random.nextFloat(), 0.7f + Random.nextFloat() * 1.7f, Random.nextFloat() * 6.28f) } }
    val time = remember { mutableFloatStateOf(0f) }
    val kick = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        var seenDrop = GerasPulse.dropAtNanos
        var seenKick = GerasPulse.kickAtNanos
        var kickShown = 0L
        while (isActive) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.05f)
                last = now
                val clock = System.nanoTime()
                val d = GerasPulse.dropAtNanos
                if (d != seenDrop && clock >= d + GerasPulse.OUTPUT_LATENCY_NS) {
                    seenDrop = d
                    motes.forEach { m ->
                        val dx = m.x - 0.5f
                        val dy = m.y - 0.5f
                        val len = hypot(dx, dy) + 0.05f
                        m.vx = dx / len * 1.1f
                        m.vy = dy / len * 1.1f
                    }
                }
                val k = GerasPulse.kickAtNanos
                if (k != seenKick && clock >= k + GerasPulse.OUTPUT_LATENCY_NS) {
                    seenKick = k
                    kickShown = clock
                }
                kick.floatValue = if (kickShown == 0L) 0f else exp(-(clock - kickShown) / 1e9f * 7f)
                val t = time.floatValue + dt
                val energy = GerasPulse.level
                motes.forEach { m ->
                    m.x += (0.012f * cos(t * 0.6f + m.phase) * (1f + energy) + m.vx) * dt
                    m.y += (0.012f * sin(t * 0.5f + m.phase) * (1f + energy) - 0.006f + m.vy) * dt
                    m.vx *= 0.93f
                    m.vy *= 0.93f
                    if (m.x > 1.03f) m.x = -0.03f
                    if (m.x < -0.03f) m.x = 1.03f
                    if (m.y > 1.03f) m.y = -0.03f
                    if (m.y < -0.03f) m.y = 1.03f
                }
                time.floatValue = t
            }
        }
    }
    Canvas(modifier.fillMaxSize()) {
        val t = time.floatValue
        val k = kick.floatValue
        val unit = 1.dp.toPx()
        motes.forEach { m ->
            val twinkle = 0.5f + 0.5f * sin(t * 2f + m.phase)
            val center = Offset(m.x * size.width, m.y * size.height)
            val r = m.r * unit * (1f + 0.5f * k)
            drawCircle(tint.copy(alpha = 0.07f * (0.4f + 0.6f * twinkle)), r * 3.2f, center)
            drawCircle(Color.White.copy(alpha = 0.34f * (0.35f + 0.65f * twinkle)), r, center)
        }
    }
}

/** Two glass cards under the lyrics, both read live from the sound: energy and tempo. */
@Composable
fun GerasSceneCards(accent: Color, modifier: Modifier = Modifier) {
    val level = remember { mutableFloatStateOf(0f) }
    val bpm = remember { mutableIntStateOf(0) }
    val beat = remember { mutableFloatStateOf(0f) }
    val time = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        var seenKick = GerasPulse.kickAtNanos
        var kickShown = 0L
        while (isActive) {
            withFrameNanos {
                val clock = System.nanoTime()
                level.floatValue = GerasPulse.level
                bpm.intValue = GerasPulse.bpm
                val k = GerasPulse.kickAtNanos
                if (k != seenKick && clock >= k + GerasPulse.OUTPUT_LATENCY_NS) {
                    seenKick = k
                    kickShown = clock
                }
                beat.floatValue = if (kickShown == 0L) 0f else exp(-(clock - kickShown) / 1e9f * 6f)
                time.longValue = clock
            }
        }
    }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SceneCard(stringResource(R.string.geras_scene_energy), accent, Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(width = 44.dp, height = 30.dp)) {
                    val t = time.longValue / 1e9f
                    val bar = size.width / 11f
                    repeat(6) { i ->
                        val h = size.height * (0.14f + 0.86f * (level.floatValue * (0.55f + 0.45f * sin(t * (5f + i) + i))).coerceIn(0f, 1f))
                        drawRoundRect(accent, Offset(i * bar * 2f, size.height - h), Size(bar, h), CornerRadius(bar / 2f))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    val pct = (level.floatValue * 100).toInt()
                    Text("$pct%", color = Color.White, fontFamily = GerasFonts.display, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text(
                        stringResource(
                            when {
                                pct < 35 -> R.string.geras_scene_calm
                                pct < 68 -> R.string.geras_scene_steady
                                else -> R.string.geras_scene_drive
                            },
                        ),
                        color = Color.White.copy(alpha = 0.62f),
                        fontSize = 11.sp,
                    )
                }
            }
        }
        SceneCard(stringResource(R.string.geras_scene_tempo), accent, Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(if (bpm.intValue > 0) bpm.intValue.toString() else "—", color = Color.White, fontFamily = GerasFonts.display, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.geras_scene_bpm), color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, modifier = Modifier.padding(bottom = 3.dp))
            }
            Text(stringResource(R.string.geras_scene_tempo_hint), color = Color.White.copy(alpha = 0.62f), fontSize = 11.sp)
            Spacer(Modifier.height(7.dp))
            Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                drawRoundRect(Color.White.copy(alpha = 0.14f), cornerRadius = CornerRadius(size.height / 2))
                drawRoundRect(accent, size = Size(size.width * beat.floatValue, size.height), cornerRadius = CornerRadius(size.height / 2))
            }
        }
    }
}

@Composable
private fun SceneCard(title: String, accent: Color, modifier: Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.09f))
            .border(
                1.dp,
                Brush.verticalGradient(0f to Color.White.copy(alpha = 0.34f), 0.4f to Color.White.copy(alpha = 0.08f), 1f to Color.White.copy(alpha = 0.12f)),
                shape,
            )
            .padding(horizontal = 13.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(accent))
            Spacer(Modifier.width(6.dp))
            Text(title, color = Color.White.copy(alpha = 0.78f), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(7.dp))
        content()
    }
}

private val stopWords = setOf(
    "этот", "этих", "этой", "этого", "через", "сквозь", "когда", "если", "чтобы", "потому", "только", "снова", "опять", "тогда", "сейчас",
    "тебя", "меня", "тебе", "себя", "твой", "твоя", "твои", "мной", "тобой", "всегда", "никогда", "очень", "просто", "может", "будет", "было", "были",
    "about", "there", "their", "would", "could", "should", "through", "because", "where", "which", "while", "never", "always", "gonna", "wanna", "don't", "can't", "every",
)

/**
 * The word of a line to paint in the accent color: the longest one that is not a filler word,
 * at least five letters. Returns its character range, or null when the line has no such word.
 */
internal fun keyWordRange(text: String): IntRange? =
    Regex("""[\p{L}\p{M}'’-]+""").findAll(text)
        .filter { it.value.length >= 5 && it.value.lowercase() !in stopWords }
        .maxByOrNull { it.value.length }
        ?.range
