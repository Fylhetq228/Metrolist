package com.metrolist.music.geras.search

import com.metrolist.music.geras.source.ItemKind
import com.metrolist.music.geras.source.SourceItem
import com.metrolist.music.geras.source.YouTubeMusicSource
import java.net.HttpURLConnection
import java.net.URL

/**
 * Spotify track links: read title and artist from the public embed page (no account
 * needed) and match them to YouTube Music. Playlists and albums go through the importer.
 */
object SpotifyLinks {
    private val trackRe = Regex("""open\.spotify\.com/(?:intl-[a-z]+/)?track/([A-Za-z0-9]{22})""")

    fun isSpotify(url: String) = "spotify.com" in url || url.startsWith("spotify:")

    suspend fun resolve(url: String): SourceItem? {
        val id = trackRe.find(url)?.groupValues?.get(1) ?: return null
        val html = fetch("https://open.spotify.com/embed/track/$id") ?: return null
        val title = Regex(""""name":"((?:[^"\\]|\\.)*)","uri":"spotify:track:$id"""").find(html)?.groupValues?.get(1)
            ?: Regex(""""title":"((?:[^"\\]|\\.)*)"""").find(html)?.groupValues?.get(1)
            ?: return null
        val artist = Regex(""""artists":\[\{"name":"((?:[^"\\]|\\.)*)"""").find(html)?.groupValues?.get(1)
            ?: Regex(""""subtitle":"((?:[^"\\]|\\.)*)"""").find(html)?.groupValues?.get(1)
        val query = listOfNotNull(unescape(title), artist?.let(::unescape)).joinToString(" ")
        return YouTubeMusicSource.search(query, ItemKind.TRACK).getOrNull()
            ?.sections?.firstOrNull()?.items?.firstOrNull()
    }

    private fun unescape(s: String) = s.replace("\\\"", "\"").replace("\\/", "/").replace(Regex("""\\u([0-9a-fA-F]{4})""")) {
        it.groupValues[1].toInt(16).toChar().toString()
    }

    private fun fetch(url: String): String? = runCatching {
        (URL(url).openConnection() as HttpURLConnection).run {
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36")
            setRequestProperty("Accept-Language", "en")
            try {
                if (responseCode in 200..299) inputStream.bufferedReader().readText() else null
            } finally {
                disconnect()
            }
        }
    }.getOrNull()
}
