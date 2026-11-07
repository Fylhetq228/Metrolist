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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
            .background(scheme.surfaceContainerHigh)
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
