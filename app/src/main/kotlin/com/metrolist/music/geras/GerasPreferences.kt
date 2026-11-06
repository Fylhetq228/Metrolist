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
val GerasGlassOpacityKey = floatPreferencesKey("geras_glass_opacity")
val GerasGlassBlurKey = floatPreferencesKey("geras_glass_blur")
/** FLOATING (glass capsule) or STANDARD (Metrolist's bar). */
val GerasNavStyleKey = stringPreferencesKey("geras_nav_style")
val GerasHomeEnabledKey = booleanPreferencesKey("geras_home_enabled")
