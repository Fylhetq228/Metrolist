package com.metrolist.music.geras.search

import com.metrolist.music.geras.source.ItemKind
import com.metrolist.music.geras.source.SourceItem
import com.metrolist.music.geras.source.YouTubeMusicSource
import com.metrolist.music.geras.transfer.TransferMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** "What's the song that goes…": finds tracks by a line of lyrics through LRCLIB, then on YTM. */
object LyricsLineSearch {
    /** Lyrics lines are long; short queries are titles or names. */
    fun looksLikeLine(q: String) = q.trim().split(Regex("\\s+")).size >= 4

    suspend fun search(line: String): List<SourceItem> = withContext(Dispatchers.IO) {
        val raw = fetch("https://lrclib.net/api/search?q=" + URLEncoder.encode(line.trim(), "UTF-8")) ?: return@withContext emptyList()
        val arr = runCatching { Json.parseToJsonElement(raw) as? JsonArray }.getOrNull() ?: return@withContext emptyList()
        val needle = TransferMatcher.normalize(line)
        data class Hit(val title: String, val artist: String, val snippet: String?, val exact: Boolean)
        val hits = arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val title = (o["trackName"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val artist = (o["artistName"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            val lyrics = (o["plainLyrics"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            val lineHit = lyrics.lineSequence().firstOrNull { l ->
                val n = TransferMatcher.normalize(l)
                n.isNotEmpty() && (n.contains(needle) || needle.contains(n) && n.length > 12)
            }
            Hit(title, artist, lineHit?.trim(), lineHit != null)
        }.sortedByDescending { it.exact }
            .distinctBy { TransferMatcher.normalize(it.title) + "|" + TransferMatcher.normalize(it.artist) }
            .take(3)
        coroutineScope {
            hits.map { h ->
                async {
                    YouTubeMusicSource.search("${h.title} ${h.artist}", ItemKind.TRACK).getOrNull()
                        ?.sections?.firstOrNull()?.items?.firstOrNull()
                        ?.let { item -> h.snippet?.let { item.copy(subtitle = "«${it.take(60)}» · ${item.subtitle}") } ?: item }
                }
            }.awaitAll().filterNotNull().distinctBy { it.id }
        }
    }

    private fun fetch(url: String): String? = runCatching {
        (URL(url).openConnection() as HttpURLConnection).run {
            connectTimeout = 8000
            readTimeout = 10000
            setRequestProperty("User-Agent", "Geras (https://github.com/Fylhetq228/metrolist)")
            try {
                if (responseCode in 200..299) inputStream.bufferedReader().readText() else null
            } finally {
                disconnect()
            }
        }
    }.getOrNull()
}
