package com.metrolist.music.geras.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.music.R
import com.metrolist.music.ui.component.Material3SettingsItem

/**
 * Settings group in the Geras style: one rounded card per group, quiet section title,
 * plain icons and a chevron on rows that open something.
 */
@Composable
fun GerasSettingsGroup(title: String?, items: List<Material3SettingsItem>, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth()) {
        title?.let {
            Text(
                text = it,
                fontSize = 13.sp,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 8.dp),
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(scheme.surfaceContainer),
        ) {
            items.forEachIndexed { index, item ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = item.enabled && item.onClick != null) { item.onClick?.invoke() }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    val alpha = if (item.enabled) 1f else 0.38f
                    if (item.leadingContent != null) {
                        item.leadingContent.invoke()
                        Spacer(Modifier.width(14.dp))
                    } else if (item.icon != null) {
                        Box {
                            Icon(item.icon, null, tint = scheme.onSurfaceVariant.copy(alpha = alpha), modifier = Modifier.size(22.dp))
                            if (item.showBadge) {
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .size(8.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(scheme.error),
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface.copy(alpha = alpha), fontSize = 16.sp)) {
                            item.title()
                        }
                        item.description?.let { desc ->
                            Spacer(Modifier.height(2.dp))
                            ProvideTextStyle(MaterialTheme.typography.bodySmall.copy(color = scheme.onSurfaceVariant.copy(alpha = alpha))) { desc() }
                        }
                    }
                    when {
                        item.trailingContent != null -> {
                            Spacer(Modifier.width(8.dp))
                            item.trailingContent.invoke()
                        }
                        item.onClick != null -> Icon(
                            painterResource(R.drawable.navigate_next),
                            null,
                            tint = scheme.onSurfaceVariant.copy(alpha = 0.6f * alpha),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                if (index != items.lastIndex) {
                    HorizontalDivider(
                        color = scheme.onSurface.copy(alpha = 0.05f),
                        modifier = Modifier.padding(start = if (item.icon != null) 52.dp else 16.dp),
                    )
                }
            }
        }
    }
}
