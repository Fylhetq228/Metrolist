package com.metrolist.music.geras.lyrics

import android.content.ClipData
import android.content.Intent
import android.text.Layout
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalListenTogetherManager
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.AiProviderKey
import com.metrolist.music.constants.AiSystemPromptKey
import com.metrolist.music.constants.DeeplApiKey
import com.metrolist.music.constants.DeeplFormalityKey
import com.metrolist.music.constants.LyricsRomanizeCyrillicByLineKey
import com.metrolist.music.constants.LyricsRomanizeList
import com.metrolist.music.constants.OpenRouterApiKey
import com.metrolist.music.constants.OpenRouterBaseUrlKey
import com.metrolist.music.constants.OpenRouterDefaultBaseUrl
import com.metrolist.music.constants.OpenRouterDefaultModel
import com.metrolist.music.constants.OpenRouterModelKey
import com.metrolist.music.constants.RespectAgentPositioningKey
import com.metrolist.music.constants.TranslateLanguageKey
import com.metrolist.music.constants.TranslateModeKey
import com.metrolist.music.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.metrolist.music.lyrics.LyricsTranslationHelper
import com.metrolist.music.lyrics.lyricsTextLooksSynced
import com.metrolist.music.ui.component.LyricsBackgroundStyle
import com.metrolist.music.ui.component.LyricsColorPickerDialog
import com.metrolist.music.ui.component.LyricsShareDialog
import com.metrolist.music.ui.screens.settings.LyricsPosition
import com.metrolist.music.ui.screens.settings.defaultList
import com.metrolist.music.utils.ComposeToImage
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.viewmodels.LyricsViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor

@Immutable
data class GerasLyricsColors(
    val active: Color,
    val inactive: Color,
    val backing: Color,
    val accent: Color,
)

/** Set by the Geras player so lyrics take its artwork colours; falls back to the app theme. */
val LocalGerasLyricsColors = staticCompositionLocalOf<GerasLyricsColors?> { null }

private const val ANCHOR_RATIO = 0.3f
private const val USER_SCROLL_PAUSE_MS = 3_500L
private val LINE_TEXT_SIZE = 27.sp
private val LINE_HEIGHT = 34.sp

/**
 * Karaoke lyrics: each word fills left-to-right with a soft gradient edge in time with the
 * vocals, the current line is bright and full-size, others recede. Time comes straight from
 * the player every frame (already speed-aware) and is only read in the draw phase.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GerasLyrics(
    sliderPositionProvider: () -> Long?,
    showLyrics: Boolean,
    modifier: Modifier = Modifier,
    lyricsViewModel: LyricsViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val player = playerConnection.player
    val context = LocalContext.current
    val database = LocalDatabase.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val listenTogether = LocalListenTogetherManager.current
    val isGuest = listenTogether?.isInRoom == true && !listenTogether.isHost

    val colors = LocalGerasLyricsColors.current ?: GerasLyricsColors(
        active = MaterialTheme.colorScheme.onSurface,
        inactive = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f),
        backing = MaterialTheme.colorScheme.primary,
        accent = MaterialTheme.colorScheme.primary,
    )

    val romanizeList by rememberPreference(LyricsRomanizeList, "")
    val romanizeCyrillicByLine by rememberPreference(LyricsRomanizeCyrillicByLineKey, false)
    val respectAgents by rememberPreference(RespectAgentPositioningKey, true)
    val enabledLanguages = remember(romanizeList) {
        val pairs: List<Pair<String, Boolean>> =
            if (romanizeList.isEmpty()) defaultList
            else romanizeList.split(",").mapNotNull { entry ->
                val parts = entry.split(":")
                if (parts.size == 2) parts[0] to parts[1].toBoolean() else null
            }
        pairs.filter { it.second }.map { it.first }
    }

    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val currentSong by playerConnection.currentSong.collectAsStateWithLifecycle(initialValue = null)
    val lyricsEntity by playerConnection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)
    val rawLyrics = remember(lyricsEntity) { lyricsEntity?.lyrics?.trim() }
    val lines by lyricsViewModel.lines.collectAsStateWithLifecycle()

    LaunchedEffect(rawLyrics, enabledLanguages, romanizeCyrillicByLine) {
        lyricsViewModel.processLyrics(rawLyrics, enabledLanguages, romanizeCyrillicByLine, false)
    }
    // Timing is judged on the parsed lines (TTML has no LRC brackets); untimed lyrics get an
    // estimated flow over the track instead of sitting still.
    val timed = remember(lines) { linesAreTimed(lines) }
    val durationMs = (mediaMetadata?.duration ?: 0) * 1000L
    val estimated = !timed && lines.count { it.text.isNotBlank() } >= 4 && durationMs >= 30_000
    val timedLines = remember(lines, estimated, durationMs) { if (estimated) estimateTiming(lines, durationMs) else lines }
    val synced = timed || estimated
    val items = remember(timedLines, synced) { buildKaraoke(timedLines, synced) }

    TranslationEffects(lines = lines, showLyrics = showLyrics, songId = currentSong?.id)

    // Playback clock. ExoPlayer's currentPosition is interpolated with the playback speed, so
    // reading it every frame keeps word timing right at any tempo.
    val clock = remember { mutableLongStateOf(0L) }
    val lyricsOffset = currentSong?.song?.lyricsOffset ?: 0
    LaunchedEffect(player, lyricsOffset) {
        while (isActive) {
            withFrameMillis {
                // The host passes its coarse (100 ms) position; only trust it while it is far
                // from the player, i.e. while the seek bar is being dragged.
                val playerPos = player.currentPosition
                val hostPos = sliderPositionProvider()
                val pos = if (hostPos != null && abs(hostPos - playerPos) > 1_500L) hostPos else playerPos
                clock.longValue = pos + lyricsOffset
            }
        }
    }

    val activeIdx by remember(items) { derivedStateOf { activeIndex(items, clock.longValue) } }

    val scrollState = rememberScrollState()
    val positions = remember(items) { mutableStateMapOf<Int, Float>() }
    var lastUserTouch by remember { mutableLongStateOf(0L) }
    var touching by remember { mutableStateOf(false) }
    var programmatic by remember { mutableStateOf(false) }
    var viewportPx by remember { mutableStateOf(0) }
    var resumeTick by remember { mutableStateOf(0) }

    LaunchedEffect(scrollState.isScrollInProgress) {
        if (scrollState.isScrollInProgress && !programmatic) lastUserTouch = System.currentTimeMillis()
    }
    // Wake auto-scroll back up once the user has left the list alone for a while.
    LaunchedEffect(lastUserTouch) {
        if (lastUserTouch == 0L) return@LaunchedEffect
        kotlinx.coroutines.delay(USER_SCROLL_PAUSE_MS)
        if (!touching) resumeTick++
    }
    LaunchedEffect(activeIdx, resumeTick, viewportPx, items) {
        if (!synced || activeIdx < 0 || viewportPx == 0) return@LaunchedEffect
        val paused = touching || System.currentTimeMillis() - lastUserTouch < USER_SCROLL_PAUSE_MS
        if (paused) return@LaunchedEffect
        val y = positions[activeIdx] ?: return@LaunchedEffect
        val target = (y - viewportPx * ANCHOR_RATIO).toInt().coerceIn(0, scrollState.maxValue)
        programmatic = true
        try {
            scrollState.animateScrollTo(target, spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessLow))
        } finally {
            programmatic = false
        }
    }

    var menuFor by remember { mutableStateOf<KLine?>(null) }
    var shareText by remember { mutableStateOf<String?>(null) }
    var pickColorsFor by remember { mutableStateOf<String?>(null) }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        val heightPx = with(density) { viewportHeight.roundToPx() }
        LaunchedEffect(heightPx) { viewportPx = heightPx }

        when {
            rawLyrics == null -> Unit
            rawLyrics == LYRICS_NOT_FOUND || items.isEmpty() -> Text(
                text = stringResource(R.string.lyrics_not_found),
                color = colors.inactive,
                fontSize = 16.sp,
                modifier = Modifier.align(Alignment.Center),
            )
            else -> Column(
                verticalArrangement = Arrangement.spacedBy(22.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.1f to Color.Black,
                                0.82f to Color.Black,
                                1f to Color.Transparent,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            touching = true
                            lastUserTouch = System.currentTimeMillis()
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                            } while (event.changes.any { it.pressed })
                            touching = false
                            lastUserTouch = System.currentTimeMillis()
                        }
                    }
                    .verticalScroll(scrollState)
                    .padding(horizontal = 4.dp),
            ) {
                Spacer(Modifier.height(viewportHeight * ANCHOR_RATIO))
                items.forEachIndexed { index, item ->
                    Box(Modifier.onPlaced { positions[index] = it.positionInParent().y }) {
                        when (item) {
                            is KItem.Gap -> GapDots(
                                gap = item,
                                active = index == activeIdx,
                                clock = clock,
                                colors = colors,
                            )
                            is KItem.Line -> {
                                val line = item.line
                                val distance = if (activeIdx < 0) index + 1 else abs(index - activeIdx)
                                val isActive = index == activeIdx ||
                                    (line.isBackground && activeIdx >= 0 && distance <= 1)
                                LineView(
                                    line = line,
                                    isActive = isActive,
                                    isPast = synced && activeIdx >= 0 && index < activeIdx && !isActive,
                                    distance = distance,
                                    synced = synced,
                                    clock = clock,
                                    colors = colors,
                                    respectAgents = respectAgents,
                                    calm = !touching && System.currentTimeMillis() - lastUserTouch > USER_SCROLL_PAUSE_MS,
                                    onClick = {
                                        if (timed && !isGuest) {
                                            lastUserTouch = 0L
                                            playerConnection.seekTo((line.start - lyricsOffset).coerceAtLeast(0))
                                            resumeTick++
                                        }
                                    },
                                    onLongClick = { menuFor = line },
                                )
                                DropdownMenu(expanded = menuFor == line, onDismissRequest = { menuFor = null }) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.geras_lyrics_share_card)) },
                                        onClick = {
                                            shareText = line.text
                                            menuFor = null
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.geras_lyrics_translate)) },
                                        onClick = {
                                            LyricsTranslationHelper.triggerManualTranslation()
                                            menuFor = null
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.geras_lyrics_copy)) },
                                        onClick = {
                                            clipboard.setText(AnnotatedString(line.text))
                                            menuFor = null
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(viewportHeight * (1f - ANCHOR_RATIO)))
            }
        }
    }

    val title = mediaMetadata?.title.orEmpty()
    val artists = mediaMetadata?.artists?.joinToString { it.name }.orEmpty()
    shareText?.let { text ->
        LyricsShareDialog(
            txt = text,
            title = title,
            arts = artists,
            songId = mediaMetadata?.id.orEmpty(),
            onDismiss = { shareText = null },
            onShareAsImage = {
                pickColorsFor = text
                shareText = null
            },
        )
    }
    pickColorsFor?.let { text ->
        LyricsColorPickerDialog(
            txt = text,
            title = title,
            arts = artists,
            thumbnailUrl = mediaMetadata?.thumbnailUrl,
            lyricsTextPosition = LyricsPosition.LEFT,
            onDismiss = { pickColorsFor = null },
            onShare = { bg, fg, secondary, style ->
                pickColorsFor = null
                scope.launch {
                    try {
                        val image = ComposeToImage.createLyricsImage(
                            context, mediaMetadata?.thumbnailUrl, title, artists, text,
                            (configuration.screenWidthDp * density.density).toInt(),
                            (configuration.screenHeightDp * density.density).toInt(),
                            bg.toArgb(), style, fg.toArgb(), secondary.toArgb(),
                            Layout.Alignment.ALIGN_NORMAL,
                        )
                        val uri = ComposeToImage.saveBitmapAsFile(context, image, "lyrics_${System.currentTimeMillis()}")
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "image/png"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    clipData = ClipData.newRawUri(null, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                },
                                context.getString(R.string.share_lyrics),
                            ),
                        )
                    } catch (e: Exception) {
                        Toast.makeText(context, context.getString(R.string.failed_to_create_image, e.message), Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LineView(
    line: KLine,
    isActive: Boolean,
    isPast: Boolean,
    distance: Int,
    synced: Boolean,
    clock: MutableLongState,
    colors: GerasLyricsColors,
    respectAgents: Boolean,
    calm: Boolean = true,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    // A small overshoot makes the new line "arrive" instead of just fading in.
    val emphasis by animateFloatAsState(
        targetValue = if (isActive || !synced) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 260f),
        label = "lineEmphasis",
    )
    // Depth of field like Apple Music: lines further from the current one go soft.
    val blurTarget = if (!synced || isActive || !calm) 0f else (distance.coerceAtMost(4) * 1.1f)
    val blurDp by animateFloatAsState(blurTarget, tween(450), label = "lineBlur")
    val textAlign = when {
        respectAgents && line.agent == "v2" -> TextAlign.End
        respectAgents && line.agent == "v1000" -> TextAlign.Center
        else -> TextAlign.Start
    }
    val origin = when (textAlign) {
        TextAlign.End -> TransformOrigin(1f, 0.5f)
        TextAlign.Center -> TransformOrigin(0.5f, 0.5f)
        else -> TransformOrigin(0f, 0.5f)
    }
    val scaleBase = if (line.isBackground) 0.78f else 1f
    val far = (distance - 1).coerceIn(0, 4) / 4f
    val romanized by line.entry.romanizedTextFlow.collectAsState()
    val translated by line.entry.translatedTextFlow.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val s = scaleBase * (0.93f + 0.07f * emphasis)
                scaleX = s
                scaleY = s
                translationY = (1f - emphasis.coerceIn(0f, 1f)) * 6.dp.toPx() * if (isPast) -1f else 1f
                transformOrigin = origin
                alpha = if (!synced) 1f else (0.5f + 0.5f * emphasis.coerceIn(0f, 1f)) * (1f - far * 0.35f)
                if (android.os.Build.VERSION.SDK_INT >= 31 && blurDp > 0.2f) {
                    val px = blurDp.dp.toPx()
                    renderEffect = androidx.compose.ui.graphics.BlurEffect(px, px, androidx.compose.ui.graphics.TileMode.Decal)
                }
            }
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(vertical = 2.dp),
    ) {
        KaraokeText(line, isActive && synced, clock, colors, textAlign, past = isPast)
        val sub = listOfNotNull(romanized?.takeIf { it.isNotBlank() && it != line.text }, translated?.takeIf { it.isNotBlank() })
        sub.forEach { s ->
            Text(
                text = s,
                color = colors.inactive,
                fontSize = 15.sp,
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

/**
 * Lyrics style C ("glowing words"): the line is dim until a word is sung; that word lights up,
 * glows and pops up with a little overshoot, then stays bright. Each word is measured on its
 * own so its glow is never clipped by its neighbours.
 */
@Composable
private fun KaraokeText(
    line: KLine,
    filling: Boolean,
    clock: MutableLongState,
    colors: GerasLyricsColors,
    textAlign: TextAlign,
    past: Boolean = false,
) {
    val measurer = rememberTextMeasurer(cacheSize = 16)
    val density = LocalDensity.current
    // TextMeasurer does not inherit the theme, so the app font has to be passed explicitly.
    val family = MaterialTheme.typography.bodyLarge.fontFamily
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val widthPx = constraints.maxWidth
        val style = TextStyle(
            fontFamily = family,
            fontSize = LINE_TEXT_SIZE,
            lineHeight = LINE_HEIGHT,
            fontWeight = FontWeight.ExtraBold,
            fontStyle = if (line.isBackground) FontStyle.Italic else FontStyle.Normal,
            textAlign = textAlign,
        )
        fun annotated(main: Color, backing: Color) = buildAnnotatedString {
            append(line.text)
            addStyle(SpanStyle(color = if (line.isBackground) backing else main), 0, line.text.length)
            line.backing.forEach { r ->
                addStyle(SpanStyle(color = backing, fontStyle = FontStyle.Italic), r.first, (r.last + 1).coerceAtMost(line.text.length))
            }
        }
        val dim = remember(line, widthPx, colors) {
            measurer.measure(annotated(colors.inactive, colors.inactive.copy(alpha = colors.inactive.alpha * 0.75f)), style, constraints = Constraints(maxWidth = widthPx))
        }
        val bright = remember(line, widthPx, colors) {
            measurer.measure(annotated(colors.active, colors.backing), style, constraints = Constraints(maxWidth = widthPx))
        }
        // One layout per word, placed where the word sits in the full line.
        class Piece(val word: KWord, val layout: TextLayoutResult, val dimLayout: TextLayoutResult, val at: Offset)
        val pieces = remember(line, widthPx, colors) {
            line.words.mapNotNull { w ->
                val first = w.charStart.coerceIn(0, (line.text.length - 1).coerceAtLeast(0))
                if (line.text.isEmpty() || w.charEnd <= w.charStart) return@mapNotNull null
                val row = bright.getLineForOffset(first)
                val x = bright.getHorizontalPosition(first, true)
                val sub = annotated(colors.active, colors.backing).subSequence(w.charStart, w.charEnd.coerceAtMost(line.text.length))
                val subDim = annotated(colors.inactive, colors.inactive.copy(alpha = colors.inactive.alpha * 0.75f)).subSequence(w.charStart, w.charEnd.coerceAtMost(line.text.length))
                val oneLine = style.copy(textAlign = TextAlign.Start)
                Piece(w, measurer.measure(sub, oneLine, softWrap = false), measurer.measure(subDim, oneLine, softWrap = false), Offset(x, bright.getLineTop(row)))
            }
        }
        val heightDp = with(density) { dim.size.height.toDp() }
        val liftPx = with(density) { 4.dp.toPx() }
        val glowColor = colors.active

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(heightDp),
        ) {
            if (!filling) {
                drawText(if (past) bright else dim)
                return@Canvas
            }
            if (pieces.isEmpty()) {
                drawText(if (clock.longValue >= line.start) bright else dim)
                return@Canvas
            }
            val t = clock.longValue
            pieces.forEach { pc ->
                val w = pc.word
                when {
                    t < w.start -> drawText(pc.dimLayout, topLeft = pc.at)
                    else -> {
                        val since = (t - w.start).toFloat()
                        val after = (t - w.end).toFloat()
                        // Pop: ease-out-back on the way up, settle back once the word is over.
                        val up = easeOutBack((since / 180f).coerceIn(0f, 1f))
                        val down = if (after > 0f) (1f - (after / 320f)).coerceIn(0f, 1f) else 1f
                        val k = up * down
                        val glow = (if (after > 0f) (1f - after / 450f) else 1f).coerceIn(0f, 1f)
                        val scale = 1f + 0.08f * k
                        val cx = pc.at.x + pc.layout.size.width / 2f
                        val cy = pc.at.y + pc.layout.size.height / 2f
                        withTransform({
                            translate(0f, -liftPx * k)
                            scale(scale, scale, Offset(cx, cy))
                        }) {
                            if (glow > 0.02f) {
                                drawText(pc.layout, topLeft = pc.at, shadow = Shadow(glowColor.copy(alpha = 0.85f * glow), Offset.Zero, 26f))
                                drawText(pc.layout, topLeft = pc.at, shadow = Shadow(Color.White.copy(alpha = 0.6f * glow), Offset.Zero, 6f))
                            }
                            drawText(pc.layout, topLeft = pc.at)
                        }
                    }
                }
            }
        }
    }
}

private fun easeOutBack(x: Float): Float {
    val c1 = 2.2f
    val c3 = c1 + 1f
    val y = x - 1f
    return 1f + c3 * y * y * y + c1 * y * y
}

/** Fractional character offset that should be filled at time [t]. */
private fun cursorFor(line: KLine, t: Long): Float {
    if (line.words.isEmpty()) return if (t >= line.start) line.text.length.toFloat() else 0f
    var cursor = 0f
    for (w in line.words) {
        when {
            t >= w.end -> cursor = w.charEnd.toFloat()
            t >= w.start -> {
                val p = (t - w.start).toFloat() / (w.end - w.start).toFloat()
                return w.charStart + (w.charEnd - w.charStart) * easeOutSine(p.coerceIn(0f, 1f))
            }
            else -> return cursor
        }
    }
    return cursor
}

private fun easeOutSine(x: Float) = kotlin.math.sin(x * Math.PI / 2).toFloat()

private fun cursorPosition(layout: TextLayoutResult, cursor: Float, length: Int): Pair<Int, Float> {
    if (length == 0) return 0 to 0f
    if (cursor >= length) {
        val last = layout.lineCount - 1
        return last to layout.getLineRight(last) + 100f
    }
    val ci = floor(cursor).toInt().coerceIn(0, length - 1)
    val frac = cursor - ci
    val lineIdx = layout.getLineForOffset(ci)
    val x0 = layout.getHorizontalPosition(ci, true)
    val nextOnSameLine = ci + 1 <= length && layout.getLineForOffset((ci + 1).coerceAtMost(length - 1)) == lineIdx && ci + 1 < length
    val x1 = if (nextOnSameLine) layout.getHorizontalPosition(ci + 1, true) else layout.getLineRight(lineIdx)
    return lineIdx to (x0 + (x1 - x0) * frac)
}

@Composable
internal fun GapDots(gap: KItem.Gap, active: Boolean, clock: MutableLongState, colors: GerasLyricsColors) {
    AnimatedVisibility(
        visible = active,
        enter = fadeIn(tween(300)) + expandVertically(),
        exit = fadeOut(tween(250)) + shrinkVertically(),
    ) {
        Canvas(
            Modifier
                .padding(vertical = 6.dp)
                .size(width = 72.dp, height = 22.dp),
        ) {
            val t = clock.longValue
            val span = (gap.end - gap.start).coerceAtLeast(1)
            val progress = ((t - gap.start).toFloat() / span).coerceIn(0f, 1f)
            // Gentle breathing that settles just before the next line comes in.
            val breathe = 1f + 0.08f * kotlin.math.sin(t / 420.0).toFloat() * (1f - progress)
            val r = size.height / 2.6f * breathe
            for (i in 0 until 3) {
                val local = (progress * 3f - i).coerceIn(0f, 1f)
                val cx = r + i * (size.width - 2 * r) / 2f
                drawCircle(colors.inactive, r, Offset(cx, size.height / 2))
                drawCircle(colors.active.copy(alpha = local), r, Offset(cx, size.height / 2))
            }
        }
    }
}

@Composable
private fun TranslationEffects(
    lines: List<com.metrolist.music.lyrics.LyricsEntry>,
    showLyrics: Boolean,
    songId: String?,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val context = LocalContext.current
    val database = LocalDatabase.current
    val scope = rememberCoroutineScope()
    val lyricsEntity by playerConnection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)
    val openRouterApiKey by rememberPreference(OpenRouterApiKey, "")
    val deeplApiKey by rememberPreference(DeeplApiKey, "")
    val aiProvider by rememberPreference(AiProviderKey, "OpenRouter")
    val baseUrl by rememberPreference(OpenRouterBaseUrlKey, OpenRouterDefaultBaseUrl)
    val model by rememberPreference(OpenRouterModelKey, OpenRouterDefaultModel)
    val language by rememberPreference(TranslateLanguageKey, "en")
    val mode by rememberPreference(TranslateModeKey, "Literal")
    val formality by rememberPreference(DeeplFormalityKey, "default")
    val systemPrompt by rememberPreference(AiSystemPromptKey, "")

    DisposableEffect(Unit) {
        LyricsTranslationHelper.setCompositionActive(true)
        onDispose {
            LyricsTranslationHelper.setCompositionActive(false)
            LyricsTranslationHelper.cancelTranslation()
        }
    }
    LaunchedEffect(lines, lyricsEntity, language, mode) {
        val entity = lyricsEntity ?: return@LaunchedEffect
        if (lines.isNotEmpty()) {
            LyricsTranslationHelper.loadTranslationsFromDatabase(lyrics = lines, lyricsEntity = entity, targetLanguage = language, mode = mode)
        }
    }
    LaunchedEffect(showLyrics, lines, aiProvider, openRouterApiKey, deeplApiKey, baseUrl, model, language, mode, formality, systemPrompt, songId) {
        LyricsTranslationHelper.manualTrigger.collectLatest {
            val key = if (aiProvider == "DeepL") deeplApiKey else openRouterApiKey
            if (key.isBlank()) {
                Toast.makeText(context, context.getString(R.string.ai_api_key_required), Toast.LENGTH_SHORT).show()
            } else if (showLyrics && lines.isNotEmpty()) {
                LyricsTranslationHelper.translateLyrics(
                    lyrics = lines,
                    targetLanguage = language,
                    apiKey = openRouterApiKey,
                    baseUrl = baseUrl,
                    model = model,
                    mode = mode,
                    scope = scope,
                    context = context,
                    provider = aiProvider,
                    deeplApiKey = deeplApiKey,
                    deeplFormality = formality,
                    useStreaming = true,
                    songId = songId.orEmpty(),
                    database = database,
                    systemPrompt = systemPrompt,
                )
            }
        }
    }
    LaunchedEffect(lines) {
        LyricsTranslationHelper.clearTranslationsTrigger.collectLatest {
            lines.forEach { it.translatedTextFlow.value = null }
        }
    }
}
