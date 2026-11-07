/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.wrapped

object WrappedConstants {
    // Geras: the recap is for the current year (January still shows the year that just ended).
    val YEAR: Int = java.time.LocalDate.now().let { if (it.monthValue == 1) it.year - 1 else it.year }
    val PLAYLIST_NAME = "Geras $YEAR"
}
