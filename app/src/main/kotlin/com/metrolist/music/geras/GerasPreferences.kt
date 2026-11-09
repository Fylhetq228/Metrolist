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
val GerasGlassOpacityKey = floatPreferencesKey("geras_glass_opacity_lens")
val GerasGlassBlurKey = floatPreferencesKey("geras_glass_blur_lens")
/** How lyrics react when the track kicks in: 0 off, 1 punch, 2 wave, 3 hype. */
val GerasDropStyleKey = androidx.datastore.preferences.core.intPreferencesKey("geras_drop_style")
/** Light pulse of the current line on kick-drum hits. */
val GerasKickPulseKey = androidx.datastore.preferences.core.booleanPreferencesKey("geras_kick_pulse")
/** FLOATING (glass capsule) or STANDARD (Metrolist's bar). */
val GerasNavStyleKey = stringPreferencesKey("geras_nav_style")
val GerasHomeEnabledKey = booleanPreferencesKey("geras_home_enabled")
/** MANROPE or SYSTEM. */
val GerasFontKey = stringPreferencesKey("geras_font")
val GerasSearchEnabledKey = booleanPreferencesKey("geras_search_enabled")
