package com.metrolist.music.geras

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

val GerasPlayerEnabledKey = booleanPreferencesKey("geras_player_enabled")
val GerasLyricsEnabledKey = booleanPreferencesKey("geras_lyrics_enabled")

/** Name of a GerasThemePreset, or "MATERIAL" for Metrolist's original Material You theme. */
val GerasThemeKey = stringPreferencesKey("geras_theme")
