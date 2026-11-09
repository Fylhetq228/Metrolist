package com.metrolist.music.geras.settings

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.graphics.Color
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
const val GerasSettingsSectionRoute = "geras/settings/section"

fun gerasSettingsSection(id: String) = "$GerasSettingsSectionRoute/$id"
private const val UpstreamUrl = "https://github.com/MetrolistGroup/Metrolist"

/**
 * The Settings tab: account on top, then sections with a line on what each holds. A section
 * opens as its own screen (same composable with [section] set). Metrolist's own settings
 * screens are still in the code and routes, just not linked from here.
 */
@Composable
fun GerasSettingsHome(navController: NavController, section: String? = null) {
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
    val (normalize, onNormalizeChange) = rememberPreference(com.metrolist.music.constants.AudioNormalizationKey, true)
    val (skipSilence, onSkipSilenceChange) = rememberPreference(com.metrolist.music.constants.SkipSilenceKey, false)
    var scFirst by remember {
        com.metrolist.music.geras.source.soundcloud.GerasScFirst.init(context)
        mutableStateOf(com.metrolist.music.geras.source.soundcloud.GerasScFirst.enabled)
    }
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
        if (section == null) {
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
                onOpen = { navController.navigate(com.metrolist.music.geras.profile.GerasProfileRoute) },
            )
            Spacer(Modifier.height(6.dp))
            CategoryGroup(stringResource(R.string.geras_grp_sound_play), 1, listOf(
                Category(R.drawable.equalizer, Color(0xFFE8642A), stringResource(R.string.geras_cat_sound), stringResource(R.string.geras_cat_sound_hint)) { navController.navigate(gerasSettingsSection("sound")) },
                Category(R.drawable.play, Color(0xFFC2452F), stringResource(R.string.geras_cat_playback), stringResource(R.string.geras_cat_playback_hint)) { navController.navigate(gerasSettingsSection("playback")) },
            ))
            CategoryGroup(stringResource(R.string.geras_grp_screen), 2, listOf(
                Category(R.drawable.palette, Color(0xFF7B52D6), stringResource(R.string.geras_appearance), stringResource(R.string.geras_cat_look_hint)) { navController.navigate(GerasSettingsRoute) },
            ))
            CategoryGroup(stringResource(R.string.geras_section_music), 3, listOf(
                Category(R.drawable.library_music, Color(0xFF2F8F6A), stringResource(R.string.geras_cat_library), stringResource(R.string.geras_cat_library_hint)) { navController.navigate(gerasSettingsSection("library")) },
            ))
            CategoryGroup(stringResource(R.string.geras_grp_network), 4, listOf(
                Category(R.drawable.wifi_proxy, Color(0xFF2C6FD6), stringResource(R.string.geras_cat_network), stringResource(R.string.geras_cat_network_hint)) { navController.navigate(gerasSettingsSection("network")) },
            ))
            CategoryGroup(stringResource(R.string.geras_grp_other), 5, listOf(
                Category(R.drawable.info, Color(0xFF6B645D), stringResource(R.string.geras_section_about), stringResource(R.string.geras_cat_about_hint)) { navController.navigate(gerasSettingsSection("about")) },
            ))
        } else {
            SectionHeader(
                when (section) {
                    "sound" -> stringResource(R.string.geras_cat_sound)
                    "playback" -> stringResource(R.string.geras_cat_playback)
                    "library" -> stringResource(R.string.geras_cat_library)
                    "network" -> stringResource(R.string.geras_cat_network)
                    else -> stringResource(R.string.geras_section_about)
                },
                onBack = { navController.navigateUp() },
            )
        }
        val accent = MaterialTheme.colorScheme.primary
        val off = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
        fun sw(on: Boolean, change: (Boolean) -> Unit): @Composable () -> Unit = {
            com.metrolist.music.geras.ui.GerasSwitch(checked = on, onCheckedChange = change, accent = accent, track = off)
        }
        when (section) {
            "sound" -> GerasSettingsGroup(
                title = stringResource(R.string.geras_section_sound),
                modifier = Modifier.gerasAppear(1),
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
                        icon = painterResource(R.drawable.volume_up),
                        title = { Text(stringResource(R.string.geras_normalize)) },
                        description = { Text(stringResource(R.string.geras_normalize_hint)) },
                        trailingContent = sw(normalize, onNormalizeChange),
                        onClick = { onNormalizeChange(!normalize) },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.fast_forward),
                        title = { Text(stringResource(R.string.geras_skip_silence)) },
                        description = { Text(stringResource(R.string.geras_skip_silence_hint)) },
                        trailingContent = sw(skipSilence, onSkipSilenceChange),
                        onClick = { onSkipSilenceChange(!skipSilence) },
                    ),
                ),
            )
            "playback" -> GerasSettingsGroup(
                title = stringResource(R.string.geras_cat_playback),
                modifier = Modifier.gerasAppear(1),
                items = listOf(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.cloud),
                        title = { Text(stringResource(R.string.geras_sc_first)) },
                        description = { Text(stringResource(R.string.geras_sc_first_hint)) },
                        trailingContent = sw(scFirst) { scFirst = it; com.metrolist.music.geras.source.soundcloud.GerasScFirst.enabled = it },
                        onClick = {
                            scFirst = !scFirst
                            com.metrolist.music.geras.source.soundcloud.GerasScFirst.enabled = scFirst
                        },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.shuffle),
                        title = { Text(stringResource(R.string.geras_smart_shuffle)) },
                        description = { Text(stringResource(R.string.geras_smart_shuffle_hint)) },
                        trailingContent = sw(smartShuffle, onSmartShuffleChange),
                        onClick = { onSmartShuffleChange(!smartShuffle) },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.graphic_eq),
                        title = { Text(stringResource(R.string.geras_crossfade)) },
                        description = { Text(stringResource(R.string.geras_crossfade_hint)) },
                        trailingContent = sw(crossfade, onCrossfadeChange),
                        onClick = { onCrossfadeChange(!crossfade) },
                    ),
                ) + if (crossfade) listOf(
                    Material3SettingsItem(
                        title = {
                            com.metrolist.music.geras.ui.GerasSlider(
                                value = crossfadeLen,
                                onValueChange = onCrossfadeLenChange,
                                valueRange = 1f..15f,
                                label = stringResource(R.string.geras_crossfade_len),
                                valueText = "${crossfadeLen.toInt()} s",
                                steps = 13,
                                accent = accent,
                                track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                content = MaterialTheme.colorScheme.onSurface,
                                onAccent = MaterialTheme.colorScheme.onPrimary,
                            )
                        },
                    ),
                ) else emptyList(),
            )
            "library" -> GerasSettingsGroup(
                title = stringResource(R.string.geras_cat_library),
                modifier = Modifier.gerasAppear(1),
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
                        title = { Text(stringResource(R.string.geras_profile_entry)) },
                        description = { Text(stringResource(R.string.geras_profile_entry_hint)) },
                        onClick = { navController.navigate(com.metrolist.music.geras.profile.GerasProfileRoute) },
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
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.delete_history),
                        title = { Text(stringResource(R.string.geras_settings_clear_search)) },
                        description = { Text(stringResource(R.string.geras_settings_clear_search_hint)) },
                        trailingContent = {},
                        onClick = { confirmClearSearch = true },
                    ),
                ),
            )
            "network" -> GerasProxySection()
            "about" -> GerasSettingsGroup(
                title = stringResource(R.string.geras_section_about),
                modifier = Modifier.gerasAppear(1),
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
        }
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
private fun AccountCard(loggedIn: Boolean, name: String, email: String, onLogin: () -> Unit, onLogout: () -> Unit, onOpen: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .gerasBounce(0.97f)
            .gerasRaised(RoundedCornerShape(24.dp), scheme.surfaceContainerHigh, elevation = 10.dp)
            .clickable(onClick = onOpen)
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

private class Category(val icon: Int, val color: Color, val title: String, val hint: String, val onClick: () -> Unit)

/** A titled card of sections: colored icon, name, a line on what is inside, chevron. */
@Composable
private fun CategoryGroup(title: String, index: Int, rows: List<Category>) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.gerasAppear(index)) {
        Text(
            title.uppercase(),
            fontSize = 12.sp,
            letterSpacing = 0.8.sp,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, top = 18.dp, bottom = 8.dp),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(scheme.surfaceContainer),
        ) {
            rows.forEachIndexed { i, c ->
                if (i > 0) Box(Modifier.padding(start = 62.dp).fillMaxWidth().height(1.dp).background(scheme.onSurface.copy(alpha = 0.07f)))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .gerasBounce(0.98f)
                        .clickable(onClick = c.onClick)
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Brush.verticalGradient(listOf(c.color, c.color.copy(alpha = 0.78f)))),
                    ) {
                        Icon(painterResource(c.icon), null, tint = Color.White, modifier = Modifier.size(19.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                        Text(c.hint, fontSize = 12.5.sp, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(painterResource(R.drawable.navigate_next), null, tint = scheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

/** "‹ Settings" back link and the section's large title, like a pushed iOS screen. */
@Composable
private fun SectionHeader(title: String, onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.padding(top = 10.dp, bottom = 12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .gerasBounce(0.94f)
                .clickable(onClick = onBack)
                .padding(end = 10.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Icon(painterResource(R.drawable.arrow_back), null, tint = scheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.geras_settings_back), color = scheme.primary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(
            title,
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 30.sp),
            color = scheme.onSurface,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
        )
    }
}
