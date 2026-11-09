package com.metrolist.music.geras.lyrics

import com.metrolist.music.lyrics.LyricsUtils

/**
 * Search variants for lyrics providers. Many uploads carry the real artist in the title
 * ("Кузнецкий сквад - Первый" uploaded by some channel), plus "official video", "feat." and
 * similar noise that providers do not match on.
 */
object GerasLyricsQuery {
    data class Q(val title: String, val artist: String)

    private val junk = Regex(
        """(?i)\s*[|/]\s*(official|lyric|lyrics|audio|video|клип|премьера|текст).*$|\s+(official\s+(music\s+)?(video|audio)|lyric(s)?\s+video|премьера(\s+(клипа|трека|песни))?|клип|текст\s+песни)\s*$""",
    )
    private val feat = Regex("""(?i)\s+(\(?\s*(feat\.?|ft\.?|при уч\.?)\s+.*|prod\.?(\s*by)?\s+.*)$""")
    private val dash = Regex("""^(.+?)\s+[-–—]\s+(.+)$""")

    fun variants(title: String, artists: String): List<Q> {
        val base = LyricsUtils.cleanTitleForSearch(title).replace(junk, "").trim().ifEmpty { title.trim() }
        val plain = base.replace(feat, "").trim().ifEmpty { base }
        val out = LinkedHashSet<Q>()
        val split = dash.find(plain)?.let { m ->
            val a = m.groupValues[1].trim()
            val t = m.groupValues[2].trim()
            if (a.isNotEmpty() && t.isNotEmpty()) Q(t, a) else null
        }
        val firstArtist = artists.split(",", "&").firstOrNull()?.trim().orEmpty()
        // The uploader is not the artist when the title names someone else before the dash.
        val uploaderIsNotArtist = split != null && firstArtist.isNotEmpty() &&
            !split.artist.contains(firstArtist, true) && !firstArtist.contains(split.artist, true)
        if (split != null && uploaderIsNotArtist) out += split
        out += Q(base, artists)
        if (plain != base) out += Q(plain, artists)
        if (split != null) out += split
        return out.toList().take(3)
    }

    private val lrcStamp = Regex("""\[\d{1,2}:\d{2}""")

    fun isSynced(lyrics: String): Boolean = lrcStamp.containsMatchIn(lyrics) || lyrics.trimStart().startsWith("<")
}
