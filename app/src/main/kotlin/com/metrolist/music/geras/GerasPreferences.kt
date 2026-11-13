package com.metrolist.music.geras

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

val GerasPlayerEnabledKey = booleanPreferencesKey("geras_player_enabled")
val GerasLyricsEnabledKey = booleanPreferencesKey("geras_lyrics_enabled")

/** Name of a GerasThemePreset, or "MATERIAL" for Metrolist's original Material You theme. */
val GerasThemeKey = stringPreferencesKey("geras_theme")

val GerasGlassEnabledKey = booleanPreferencesKey("geras_glass_enabled")
/** Tint strength of glass surfaces, 0 = clear, 1 = solid. */
val GerasGlassOpacityKey = floatPreferencesKey("geras_glass_opacity_dark")
val GerasGlassBlurKey = floatPreferencesKey("geras_glass_blur_dark")
/** How lyrics react when the track kicks in: 0 off, 1 punch, 2 wave, 3 hype. */
val GerasDropStyleKey = androidx.datastore.preferences.core.intPreferencesKey("geras_drop_style")
/** Word animation of synced lyrics: 0 glow, 1 fill, 2 bounce, 3 calm, 4 neon. */
val GerasLyricsStyleKey = androidx.datastore.preferences.core.intPreferencesKey("geras_lyrics_style")
/** Lyrics text size: 0 small, 1 normal, 2 large. */
val GerasLyricsSizeKey = androidx.datastore.preferences.core.intPreferencesKey("geras_lyrics_size")
/** Blur lines far from the current one. */
val GerasLyricsBlurKey = androidx.datastore.preferences.core.booleanPreferencesKey("geras_lyrics_blur")
/** Light pulse of the current line on kick-drum hits. */
val GerasKickPulseKey = androidx.datastore.preferences.core.booleanPreferencesKey("geras_kick_pulse")
/** FLOATING (glass capsule) or STANDARD (Metrolist's bar). */
val GerasNavStyleKey = stringPreferencesKey("geras_nav_style")
val GerasHomeEnabledKey = booleanPreferencesKey("geras_home_enabled")
/** MANROPE or SYSTEM. */
val GerasFontKey = stringPreferencesKey("geras_font")
val GerasSearchEnabledKey = booleanPreferencesKey("geras_search_enabled")
