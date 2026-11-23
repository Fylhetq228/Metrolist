package com.metrolist.music.geras.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Filter/choice pill used across the app (replaces Material FilterChip in old screens). */
@Composable
fun GerasPill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (selected) scheme.onSurface else scheme.surfaceContainerHigh, spring(stiffness = 500f), label = "pill")
    val fg by animateColorAsState(if (selected) scheme.surface else scheme.onSurface, label = "pillText")
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .gerasBounce(0.93f)
            .height(36.dp)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = fg, maxLines = 1)
    }
}

/** Square action tile for menus (Play next, Add to playlist…). */
@Composable
fun GerasActionTile(
    icon: @Composable () -> Unit,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    background: Color = Color.Unspecified,
    content: Color = Color.Unspecified,
) {
    val scheme = MaterialTheme.colorScheme
    val bg = if (background == Color.Unspecified) scheme.surfaceContainerHigh else background
    val fg = if (content == Color.Unspecified) scheme.onSurface else content
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .gerasBounce(0.92f)
            .gerasRaised(RoundedCornerShape(20.dp), bg.copy(alpha = if (enabled) bg.alpha else bg.alpha * 0.5f), elevation = 3.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(50))
                .background(fg.copy(alpha = 0.08f)),
        ) {
            CompositionLocalProvider(LocalContentColor provides fg) { Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) { icon() } }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = fg.copy(alpha = if (enabled) 1f else 0.5f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.basicMarquee(),
        )
    }
}

class GerasMenuRow(
    val icon: (@Composable () -> Unit)?,
    val title: @Composable () -> Unit,
    val description: (@Composable () -> Unit)?,
    val trailing: (@Composable () -> Unit)?,
    val onClick: (() -> Unit)?,
    val tint: Color? = null,
)

/** One rounded card with hairline dividers and an iOS-like press highlight. */
@Composable
fun GerasMenuCard(rows: List<GerasMenuRow>, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .gerasPanelFill(scheme.surfaceContainerHigh)
            .animateContentSize(),
    ) {
        rows.forEachIndexed { i, row ->
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val hl by animateColorAsState(
                if (pressed) scheme.onSurface.copy(alpha = 0.08f) else scheme.onSurface.copy(alpha = 0f),
                tween(if (pressed) 60 else 300),
                label = "menuRow",
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(hl)
                    .clickable(interaction, indication = null, enabled = row.onClick != null) { row.onClick?.invoke() }
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            ) {
                row.icon?.let {
                    CompositionLocalProvider(LocalContentColor provides (row.tint ?: scheme.onSurface)) {
                        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { it() }
                    }
                    Spacer(Modifier.width(16.dp))
                }
                Column(Modifier.weight(1f)) {
                    ProvideTextStyle(MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, color = row.tint ?: scheme.onSurface)) { row.title() }
                    row.description?.let {
                        Spacer(Modifier.height(2.dp))
                        ProvideTextStyle(MaterialTheme.typography.bodySmall.copy(color = scheme.onSurfaceVariant)) { it() }
                    }
                }
                row.trailing?.let {
                    Spacer(Modifier.width(8.dp))
                    it()
                }
            }
            if (i != rows.lastIndex) {
                HorizontalDivider(color = scheme.onSurface.copy(alpha = 0.06f), modifier = Modifier.padding(start = if (row.icon != null) 58.dp else 18.dp))
            }
        }
    }
}

/**
 * Drop-in for Material's TopAppBar on the old Metrolist screens: background-coloured bar,
 * bold title and the back button in a soft circle. Caller colours are ignored on purpose.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun GerasTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    windowInsets: androidx.compose.foundation.layout.WindowInsets = androidx.compose.material3.TopAppBarDefaults.windowInsets,
    @Suppress("UNUSED_PARAMETER") colors: androidx.compose.material3.TopAppBarColors? = null,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior? = null,
    /** Room kept free at the right end, e.g. for the floating profile button. */
    actionsEndPadding: androidx.compose.ui.unit.Dp = 6.dp,
    /** True for bars outside the screen content (Library): the actions become real liquid glass. */
    liquidActions: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    // iOS 26 bar: no slab of colour; the page fades out under it, buttons float as glass.
    val fade = scheme.background
    androidx.compose.material3.TopAppBar(
        title = {
            ProvideTextStyle(MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, letterSpacing = (-0.2).sp)) { title() }
        },
        modifier = modifier.drawBehind {
            // Solid behind the bar itself, then a soft fade below it, so text scrolling up never
            // shows through the title (iOS "scroll edge").
            val fadeTail = 28.dp.toPx()
            drawRect(fade, size = size)
            drawRect(
                Brush.verticalGradient(0f to fade, 1f to fade.copy(alpha = 0f), startY = size.height, endY = size.height + fadeTail),
                topLeft = androidx.compose.ui.geometry.Offset(0f, size.height),
                size = androidx.compose.ui.geometry.Size(size.width, fadeTail),
            )
        },
        navigationIcon = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .androidxDrawCircleBehind(scheme.surfaceContainerHigh.copy(alpha = 0.92f)),
            ) { navigationIcon() }
        },
        actions = {
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(end = actionsEndPadding)
                    .then(if (liquidActions) Modifier.padding(vertical = 2.dp).gerasGlass(RoundedCornerShape(50)) else Modifier)
                    // Drawn only when there are actions: an empty bar shows no stray pill.
                    .drawBehind {
                        if (!liquidActions && size.width > 12.dp.toPx()) {
                            val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2f - 2.dp.toPx())
                            val inset = 2.dp.toPx()
                            drawRoundRect(scheme.surfaceContainerHigh.copy(alpha = 0.92f), topLeft = androidx.compose.ui.geometry.Offset(0f, inset), size = androidx.compose.ui.geometry.Size(size.width, size.height - inset * 2), cornerRadius = r)
                            drawRoundRect(
                                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.04f), Color.White.copy(alpha = 0.10f))),
                                topLeft = androidx.compose.ui.geometry.Offset(0f, inset),
                                size = androidx.compose.ui.geometry.Size(size.width, size.height - inset * 2),
                                cornerRadius = r,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
                            )
                        }
                    },
                content = actions,
            )
        },
        windowInsets = windowInsets,
        colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
            titleContentColor = scheme.onSurface,
            navigationIconContentColor = scheme.onSurface,
            actionIconContentColor = scheme.onSurface,
        ),
        scrollBehavior = scrollBehavior,
    )
}

private fun Modifier.androidxDrawCircleBehind(color: Color): Modifier = this.then(
    Modifier.drawBehind {
        if (size.width > 1f) {
            val r = minOf(size.width, size.height) / 2f - 2.dp.toPx()
            drawCircle(color, radius = r)
            drawCircle(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.04f), Color.White.copy(alpha = 0.10f))),
                radius = r,
                style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
            )
        }
    },
)

/**
 * Round liquid-glass button with the account picture (or a person icon): the way into the
 * profile and all settings, floating top-right over every tab.
 */
@androidx.compose.runtime.Composable
fun GerasProfileButton(imageUrl: String?, onClick: () -> Unit) {
    val shape = androidx.compose.foundation.shape.CircleShape
    androidx.compose.foundation.layout.Box(
        contentAlignment = androidx.compose.ui.Alignment.Center,
        modifier = androidx.compose.ui.Modifier
            .size(46.dp)
            .gerasBounce(0.88f)
            .gerasGlass(shape)
            .clickable(onClick = onClick),
    ) {
        if (imageUrl != null) {
            coil3.compose.AsyncImage(
                model = imageUrl,
                contentDescription = androidx.compose.ui.res.stringResource(com.metrolist.music.R.string.settings),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = androidx.compose.ui.Modifier.size(36.dp).clip(shape),
            )
        } else {
            androidx.compose.material3.Icon(
                androidx.compose.ui.res.painterResource(com.metrolist.music.R.drawable.person),
                contentDescription = androidx.compose.ui.res.stringResource(com.metrolist.music.R.string.settings),
                tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                modifier = androidx.compose.ui.Modifier.size(24.dp),
            )
        }
    }
}
