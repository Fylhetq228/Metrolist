package com.metrolist.music.geras.lyrics

import com.metrolist.music.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND

/**
 * Guards against lyrics of a different song with the same title. Most providers search by
 * title, so "Родной дом" can come back as a completely different "Родной дом". Synced lyrics
 * must fit the track's length; plain lyrics are only trusted from sources tied to the track
 * itself (YouTube Music) or matched by duration (LrcLib).
 */
object GerasLyricsCheck {
    private val trusted = setOf("YouTube Music", "YouTube Subtitle")
    private val plainOk = trusted + "LrcLib"
    private val lrcTime = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val ttmlTime = Regex("""(?:begin|end)="(?:(\d+):)?(\d+):(\d{2}(?:\.\d+)?)"""")
    private val ttmlSeconds = Regex("""(?:begin|end)="(\d+(?:\.\d+)?)s"""")

    fun lastTimestampSec(lyrics: String): Double? {
        var max = -1.0
        lrcTime.findAll(lyrics).forEach { m ->
            val t = m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt().toDouble()
            if (t > max) max = t
        }
        ttmlTime.findAll(lyrics).forEach { m ->
            val h = m.groupValues[1].toIntOrNull() ?: 0
            val t = h * 3600 + m.groupValues[2].toInt() * 60 + (m.groupValues[3].toDoubleOrNull() ?: 0.0)
            if (t > max) max = t
        }
        ttmlSeconds.findAll(lyrics).forEach { m -> m.groupValues[1].toDoubleOrNull()?.let { if (it > max) max = it } }
        return max.takeIf { it >= 0 }
    }

    fun plausible(lyrics: String?, provider: String?, durationSec: Int): Boolean {
        if (lyrics.isNullOrBlank() || lyrics == LYRICS_NOT_FOUND) return true
        if (provider in trusted) return true
        val last = lastTimestampSec(lyrics)
        if (last == null) return provider in plainOk
        if (durationSec <= 0) return true
        if (last > durationSec + 12) return false
        val lines = lyrics.lineSequence().count { it.isNotBlank() }
        if (lines >= 8 && last < durationSec * 0.3) return false
        return true
    }
}
