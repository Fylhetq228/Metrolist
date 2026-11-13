/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.metrolist.music.constants.ExperimentalLyricsKey
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.viewmodels.LyricsViewModel

@Composable
fun Lyrics(
    sliderPositionProvider: () -> Long?,
    modifier: Modifier = Modifier,
    showLyrics: Boolean,
    lyricsViewModel: LyricsViewModel = hiltViewModel()
) {
    val (experimentalLyrics, _) = rememberPreference(key = ExperimentalLyricsKey, defaultValue = true)
    val gerasLyrics = true

    if (gerasLyrics) {
        val sceneCards by rememberPreference(com.metrolist.music.geras.GerasSceneCardsKey, true)
        androidx.compose.foundation.layout.Column(modifier) {
            com.metrolist.music.geras.lyrics.GerasLyrics(
                sliderPositionProvider = sliderPositionProvider,
                showLyrics = showLyrics,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                lyricsViewModel = lyricsViewModel,
            )
            if (sceneCards && showLyrics) {
                com.metrolist.music.geras.lyrics.GerasSceneCards(
                    accent = com.metrolist.music.geras.lyrics.LocalGerasLyricsColors.current?.accent ?: androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    } else if (experimentalLyrics) {
        ExperimentalLyrics(
            sliderPositionProvider = sliderPositionProvider,
            modifier = modifier,
            showLyrics = showLyrics,
            lyricsViewModel = lyricsViewModel
        )
    } else {
        OriginalLyrics(
            sliderPositionProvider = sliderPositionProvider,
            modifier = modifier,
            showLyrics = showLyrics
        )
    }
}
