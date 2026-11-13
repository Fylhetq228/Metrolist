package com.metrolist.music.geras.lyrics

import android.content.Context
import android.content.SharedPreferences
import com.metrolist.music.geras.transfer.TransferMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Lyrics are only shown when we can tell they belong to THIS song, not just to a song with the
 * same title. Lyrics tied to the video itself (YouTube Music) are trusted. Anything found by a
 * title search must be confirmed by LRCLIB: an entry by the same artist with the same title and
 * length whose words overlap the candidate. No confirmation, no lyrics: "not found" is better
 * than a stranger's song.
 */
object GerasLyricsVerify {
    private val trusted = setOf("YouTube Music", "YouTube Subtitle")
    private var prefs: SharedPreferences? = null
    private val verdicts = ConcurrentHashMap<String, Boolean>()

    fun init(context: Context) {
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences("geras_lyrics_ok2", Context.MODE_PRIVATE)
    }

    fun isTrusted(provider: String?) = provider in trusted

    /**
     * "Not found" is stored for good by the app, but it is often only a timeout or a provider
     * that was down. Such tracks are searched again once with the current search, and then at
     * most every few days.
     */
    fun shouldRetryNotFound(id: String): Boolean {
        val p = prefs ?: return false
        val last = p.getLong("nf3_$id", 0L)
        if (System.currentTimeMillis() - last < NOT_FOUND_RETRY_MS) return false
        p.edit().putLong("nf3_$id", System.currentTimeMillis()).apply()
        return true
    }

    fun shouldUpgrade(id: String): Boolean {
        val p = prefs ?: return false
        if (p.getBoolean("up4_$id", false)) return false
        p.edit().putBoolean("up4_$id", true).apply()
        return true
    }

    private const val NOT_FOUND_RETRY_MS = 3L * 24 * 3600 * 1000

    /** Cached verdict for a stored lyrics row, or null when it has not been checked yet. */
    fun knownVerdict(id: String, lyricsHash: Int): Boolean? =
        verdicts["$id:$lyricsHash"] ?: prefs?.let { p -> if (p.contains("$id:$lyricsHash")) p.getBoolean("$id:$lyricsHash", false) else null }

    private fun remember(id: String, lyricsHash: Int, ok: Boolean) {
        verdicts["$id:$lyricsHash"] = ok
        prefs?.edit()?.putBoolean("$id:$lyricsHash", ok)?.apply()
    }

    suspend fun accept(id: String, lyrics: String?, provider: String?, title: String, artists: List<String>, durationSec: Int): Boolean {
        if (lyrics.isNullOrBlank() || lyrics == com.metrolist.music.db.entities.LyricsEntity.LYRICS_NOT_FOUND) return true
        if (isTrusted(provider)) return true
        if (!GerasLyricsCheck.plausible(lyrics, provider, durationSec).also { if (!it) remember(id, lyrics.hashCode(), false) }) return false
        knownVerdict(id, lyrics.hashCode())?.let { return it }
        val ok = confirm(lyrics, title, artists, durationSec)
        // Only remember answers we actually got; a network failure is retried later.
        ok?.let { remember(id, lyrics.hashCode(), it) }
        // Could not reach LRCLIB: show the lyrics rather than nothing; checked again next time.
        return ok ?: true
    }

    /**
     * true = fine to show, false = evidence it is another song, null = could not ask LRCLIB.
     * Lyrics are rejected only on evidence: LRCLIB knows this artist's song and the words differ,
     * or the words match a song with the same title by a different artist. Unknown songs pass.
     */
    private suspend fun confirm(lyrics: String, title: String, artists: List<String>, durationSec: Int): Boolean? = withContext(Dispatchers.IO) {
        val cleanTitle = com.metrolist.music.lyrics.LyricsUtils.cleanTitleForSearch(title)
        val artist = artists.firstOrNull().orEmpty()
        val mine = words(lyrics)
        if (mine.size < 6) return@withContext true

        fun overlap(e: JsonObject): Float {
            val theirs = words((e.str("plainLyrics") ?: e.str("syncedLyrics")).orEmpty())
            if (theirs.isEmpty()) return -1f
            return mine.intersect(theirs).size.toFloat() / minOf(mine.size, theirs.size)
        }
        fun sameArtist(e: JsonObject): Boolean {
            val eArtist = e.str("artistName").orEmpty()
            return artists.any { a ->
                eArtist.split(",", "&", " feat", " ft.", " x ", ";").any { part -> TransferMatcher.similarity(a, part.trim()) >= 0.72f }
            }
        }
        fun sameTitle(e: JsonObject) = TransferMatcher.similarity(cleanTitle, e.str("trackName").orEmpty()) >= 0.7f

        val byArtist = search("track_name=${enc(cleanTitle)}&artist_name=${enc(artist)}") ?: return@withContext null
        val mineEntries = byArtist.filter { sameArtist(it) && sameTitle(it) }
        if (mineEntries.isNotEmpty()) {
            val scored = mineEntries.map(::overlap).filter { it >= 0f }
            // LRCLIB knows this exact song: the words must match it.
            if (scored.isNotEmpty()) return@withContext scored.max() >= 0.3f
        }
        // Same title by someone else with these very words: that is the wrong song.
        val byTitle = search("track_name=${enc(cleanTitle)}") ?: return@withContext null
        val stranger = byTitle.any { e -> !sameArtist(e) && sameTitle(e) && overlap(e) >= 0.5f }
        !stranger
    }

    private fun search(query: String): List<JsonObject>? {
        val raw = fetch("https://lrclib.net/api/search?$query") ?: return null
        return (runCatching { Json.parseToJsonElement(raw) }.getOrNull() as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
    }

    private val stamp = Regex("""\[[^\]]*]|<[^>]*>""")

    private fun words(text: String): Set<String> =
        text.lineSequence()
            .map { it.replace(stamp, " ") }
            .flatMap { TransferMatcher.normalize(it).split(' ').asSequence() }
            .filter { it.length >= 3 }
            .take(400)
            .toSet()

    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun fetch(url: String): String? = runCatching {
        (URL(url).openConnection() as HttpURLConnection).run {
            connectTimeout = 7000
            readTimeout = 9000
            setRequestProperty("User-Agent", "Geras (https://github.com/Fylhetq228/metrolist)")
            try {
                if (responseCode in 200..299) inputStream.bufferedReader().readText() else null
            } finally {
                disconnect()
            }
        }
    }.getOrNull()
}
