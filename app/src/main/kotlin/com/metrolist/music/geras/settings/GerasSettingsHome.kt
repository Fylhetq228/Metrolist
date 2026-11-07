package com.metrolist.music.geras.settings

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.metrolist.innertube.utils.parseCookieString
import com.metrolist.music.BuildConfig
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.constants.AccountEmailKey
import com.metrolist.music.constants.AccountNameKey
import com.metrolist.music.constants.InnerTubeCookieKey
import com.metrolist.music.geras.audio.GerasEqualizerSheet
import com.metrolist.music.geras.audio.GerasSpeedSheet
import com.metrolist.music.geras.player.GerasPlayerColors
import com.metrolist.music.geras.ui.gerasRaised
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.viewmodels.AccountSettingsViewModel
import kotlinx.coroutines.launch
import com.metrolist.music.geras.ui.gerasAppear
import com.metrolist.music.geras.ui.gerasBounce

const val GerasSourceUrl = "https://github.com/Fylhetq228/metrolist"
private const val UpstreamUrl = "https://github.com/MetrolistGroup/Metrolist"

/**
 * The Settings tab: account on top, then only what Geras itself adds. Metrolist's own settings
 * screens are still in the code and routes, just not linked from here.
 */
@Composable
fun GerasSettingsHome(navController: NavController) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val accountViewModel: AccountSettingsViewModel = hiltViewModel()

    val (cookie, onCookieChange) = rememberPreference(InnerTubeCookieKey, "")
    val accountName by rememberPreference(AccountNameKey, "")
    val accountEmail by rememberPreference(AccountEmailKey, "")
    val loggedIn = remember(cookie) { "SAPISID" in parseCookieString(cookie) }

    val (crossfade, onCrossfadeChange) = rememberPreference(com.metrolist.music.constants.CrossfadeEnabledKey, false)
    val (crossfadeLen, onCrossfadeLenChange) = rememberPreference(com.metrolist.music.constants.CrossfadeDurationKey, 5f)
    val (smartShuffle, onSmartShuffleChange) = rememberPreference(com.metrolist.music.geras.audio.GerasSmartShuffleKey, true)
    var showEq by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    var confirmClearSearch by remember { mutableStateOf(false) }

    val bottom = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(bottom)
            .padding(horizontal = 16.dp),
    ) {
        Text(
            stringResource(R.string.settings),
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 32.sp),
            color = scheme.onSurface,
            modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 18.dp),
        )

        AccountCard(
            loggedIn = loggedIn,
            name = accountName,
            email = accountEmail,
            onLogin = { navController.navigate("login") },
            onLogout = { confirmLogout = true },
        )

        Spacer(Modifier.height(10.dp))
        GerasSettingsGroup(
            title = stringResource(R.string.geras_section_look),
            modifier = Modifier.gerasAppear(1),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.palette),
                    title = { Text(stringResource(R.string.geras_appearance)) },
                    description = { Text(stringResource(R.string.geras_appearance_hint)) },
                    onClick = { navController.navigate(GerasSettingsRoute) },
                ),
            ),
        )

        Spacer(Modifier.height(10.dp))
        GerasSettingsGroup(
            title = stringResource(R.string.geras_section_sound),
            modifier = Modifier.gerasAppear(2),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.equalizer),
                    title = { Text(stringResource(R.string.geras_eq_title)) },
                    description = { Text(stringResource(R.string.geras_settings_eq_hint)) },
                    onClick = { showEq = true },
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.speed),
                    title = { Text(stringResource(R.string.geras_settings_speed)) },
                    description = { Text(stringResource(R.string.geras_settings_speed_hint)) },
                    onClick = { showSpeed = true },
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.graphic_eq),
                    title = { Text(stringResource(R.string.geras_crossfade)) },
                    description = { Text(stringResource(R.string.geras_crossfade_hint)) },
                    trailingContent = { androidx.compose.material3.Switch(checked = crossfade, onCheckedChange = onCrossfadeChange) },
                    onClick = { onCrossfadeChange(!crossfade) },
                ),
            ) + if (crossfade) listOf(
                Material3SettingsItem(
                    title = {
                        Column {
                            Row {
                                Text(stringResource(R.string.geras_crossfade_len), modifier = Modifier.weight(1f))
                                Text("${crossfadeLen.toInt()} s", color = scheme.onSurfaceVariant)
                            }
                            androidx.compose.material3.Slider(value = crossfadeLen, onValueChange = onCrossfadeLenChange, valueRange = 1f..15f, steps = 13)
                        }
                    },
                ),
            ) else emptyList(),
        )

        Spacer(Modifier.height(10.dp))
        GerasSettingsGroup(
            title = stringResource(R.string.geras_section_music),
            modifier = Modifier.gerasAppear(3),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.geras_transfer),
                    title = { Text(stringResource(R.string.geras_transfer_title)) },
                    description = { Text(stringResource(R.string.geras_transfer_menu_hint)) },
                    onClick = { navController.navigate(com.metrolist.music.geras.transfer.transferRoute()) },
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.history),
                    title = { Text(stringResource(R.string.history)) },
                    onClick = { navController.navigate("history") },
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.stats),
                    title = { Text(stringResource(R.string.stats)) },
                    onClick = { navController.navigate("stats") },
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.shuffle),
                    title = { Text(stringResource(R.string.geras_smart_shuffle)) },
                    description = { Text(stringResource(R.string.geras_smart_shuffle_hint)) },
                    trailingContent = { androidx.compose.material3.Switch(checked = smartShuffle, onCheckedChange = onSmartShuffleChange) },
                    onClick = { onSmartShuffleChange(!smartShuffle) },
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.trending_up),
                    title = { Text(stringResource(R.string.geras_recap)) },
                    description = { Text(stringResource(R.string.geras_recap_hint)) },
                    onClick = { navController.navigate("wrapped") },
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.mic),
                    title = { Text(stringResource(R.string.geras_settings_recognize)) },
                    description = { Text(stringResource(R.string.geras_settings_recognize_hint)) },
                    onClick = { navController.navigate("recognition") },
                ),
            ),
        )

        Spacer(Modifier.height(10.dp))
        GerasSettingsGroup(
            title = stringResource(R.string.geras_section_search),
            modifier = Modifier.gerasAppear(4),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.delete_history),
                    title = { Text(stringResource(R.string.geras_settings_clear_search)) },
                    description = { Text(stringResource(R.string.geras_settings_clear_search_hint)) },
                    trailingContent = {},
                    onClick = { confirmClearSearch = true },
                ),
            ),
        )

        Spacer(Modifier.height(10.dp))
        GerasSettingsGroup(
            title = stringResource(R.string.geras_section_about),
            modifier = Modifier.gerasAppear(5),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.info),
                    title = { Text("Geras") },
                    description = { Text(stringResource(R.string.geras_settings_version, BuildConfig.VERSION_NAME)) },
                    trailingContent = {},
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.github),
                    title = { Text(stringResource(R.string.geras_settings_source)) },
                    description = { Text(stringResource(R.string.geras_settings_based_on)) },
                    onClick = { openUrl(context, GerasSourceUrl) },
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.music_note),
                    title = { Text("Metrolist") },
                    description = { Text("GPL-3.0") },
                    onClick = { openUrl(context, UpstreamUrl) },
                ),
            ),
        )
        Spacer(Modifier.height(24.dp))
    }

    val sheetColors = GerasPlayerColors(
        backgroundTop = scheme.surfaceContainerHigh,
        background = scheme.surfaceContainer,
        surface = scheme.onSurface.copy(alpha = 0.08f),
        content = scheme.onSurface,
        contentMuted = scheme.onSurfaceVariant,
        accent = scheme.primary,
        onAccent = scheme.onPrimary,
    )
    if (showEq) {
        GerasEqualizerSheet(
            colors = sheetColors,
            onDismiss = { showEq = false },
            onOpenAutoEq = {
                showEq = false
                navController.navigate("equalizer")
            },
        )
    }
    if (showSpeed) GerasSpeedSheet(colors = sheetColors, onDismiss = { showSpeed = false })

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text(stringResource(R.string.geras_settings_logout_title)) },
            text = { Text(stringResource(R.string.geras_settings_logout_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    scope.launch { accountViewModel.logoutKeepData(context, onCookieChange) }
                }) { Text(stringResource(R.string.action_logout)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }

    if (confirmClearSearch) {
        AlertDialog(
            onDismissRequest = { confirmClearSearch = false },
            title = { Text(stringResource(R.string.geras_settings_clear_search)) },
            text = { Text(stringResource(R.string.geras_settings_clear_search_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearSearch = false
                    database.query { clearSearchHistory() }
                    context.getSharedPreferences("geras_search", Context.MODE_PRIVATE).edit().remove("recent").apply()
                    Toast.makeText(context, R.string.geras_settings_cleared, Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(R.string.geras_settings_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearSearch = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun AccountCard(loggedIn: Boolean, name: String, email: String, onLogin: () -> Unit, onLogout: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .gerasRaised(RoundedCornerShape(24.dp), scheme.surfaceContainerHigh, elevation = 10.dp)
            .padding(16.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(54.dp)
                .gerasRaised(RoundedCornerShape(50), scheme.primary, elevation = 6.dp)
                .background(Brush.verticalGradient(listOf(scheme.primary, scheme.primaryContainer))),
        ) {
            if (loggedIn && name.isNotBlank()) {
                Text(name.trim().take(1).uppercase(), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onPrimary)
            } else {
                Icon(painterResource(R.drawable.account), null, tint = scheme.onPrimary, modifier = Modifier.size(26.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (loggedIn) name.ifBlank { "YouTube Music" } else stringResource(R.string.geras_settings_guest),
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (loggedIn) email.ifBlank { "YouTube Music" } else stringResource(R.string.geras_settings_login_hint),
                fontSize = 13.sp,
                color = scheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        val primary = !loggedIn
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.gerasBounce(0.92f)
                .height(38.dp)
                .gerasRaised(RoundedCornerShape(50), if (primary) scheme.onSurface else scheme.surfaceContainerHighest, elevation = 4.dp)
                .clickable(onClick = if (loggedIn) onLogout else onLogin)
                .padding(horizontal = 16.dp),
        ) {
            Text(
                stringResource(if (loggedIn) R.string.action_logout else R.string.geras_settings_login),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (primary) scheme.surface else scheme.onSurface,
            )
        }
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
}
