package com.metrolist.music.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.metrolist.music.R

// Geras: the recap uses our display face (Unbounded); Normal maps to its Black weight.
val bbh_bartle = FontFamily(
    Font(R.font.geras_unbounded_900, FontWeight.Normal),
    Font(R.font.geras_unbounded_700, FontWeight.Bold),
    Font(R.font.geras_unbounded_900, FontWeight.Black),
)
