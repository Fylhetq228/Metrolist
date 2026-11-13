package com.metrolist.music.geras.lyrics

import android.content.Context
import com.metrolist.music.geras.transfer.TransferMatcher
import com.metrolist.music.lyrics.LyricsProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

/**
 * NetEase Cloud Music: a very large catalogue (plenty of Russian and niche releases) with
 * line-synced lyrics and, for popular tracks, word-by-word timing ("yrc"). The song is matched
 * by title, artist and length, so a same-named track by someone else is not taken.
 */
object NetEaseLyricsProvider : LyricsProvider {
    override val name = "NetEase"

    override fun isEnabled(context: Context) = true

    override suspend fun getLyrics(context: Context, id: String, title: String, artist: String, duration: Int, album: String?): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val songId = findSong(title, artist, duration) ?: error("NetEase: no matching song")
                val raw = request("https://music.163.com/api/song/lyric/v1?id=$songId&lv=0&kv=0&tv=0&yv=0&rv=0&cp=false", null)
                val json = Json.parseToJsonElement(raw) as JsonObject
                fun text(key: String) = ((json[key] as? JsonObject)?.get("lyric") as? JsonPrimitive)?.contentOrNull.orEmpty()
                yrcToRichSync(text("yrc")) ?: cleanLrc(text("lrc")) ?: error("NetEase: no lyrics")
            }
        }

    private fun findSong(title: String, artist: String, duration: Int): Long? {
        val raw = request(
            "https://music.163.com/api/search/get",
            "s=${URLEncoder.encode("$title $artist".trim(), "UTF-8")}&type=1&limit=10&offset=0",
        )
        val songs = (((Json.parseToJsonElement(raw) as? JsonObject)?.get("result") as? JsonObject)?.get("songs") as? JsonArray).orEmpty()
        val wantedArtists = artist.split(",", "&").map { it.trim() }.filter { it.isNotEmpty() }
        return songs.mapNotNull { it as? JsonObject }.mapNotNull { s ->
            val sid = (s["id"] as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null
            val name = (s["name"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            val artists = (s["artists"] as? JsonArray).orEmpty().mapNotNull { ((it as? JsonObject)?.get("name") as? JsonPrimitive)?.contentOrNull }
            val lengthSec = ((s["duration"] as? JsonPrimitive)?.longOrNull ?: 0L) / 1000
            val titleScore = TransferMatcher.similarity(title, name)
            val artistOk = wantedArtists.isEmpty() || wantedArtists.any { w -> artists.any { a -> TransferMatcher.similarity(w, a) >= 0.72f } }
            val lengthOk = duration <= 0 || lengthSec <= 0 || abs(lengthSec - duration) <= 3
            if (titleScore >= 0.8f && artistOk && lengthOk) Triple(sid, titleScore, abs(lengthSec - duration)) else null
        }.maxWithOrNull(compareBy<Triple<Long, Float, Long>> { it.second }.thenBy { -it.third })?.first
    }

    private fun request(url: String, form: String?): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 7000
        c.readTimeout = 9000
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36")
        c.setRequestProperty("Referer", "https://music.163.com/")
        try {
            if (form != null) {
                c.requestMethod = "POST"
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                c.outputStream.use { it.write(form.toByteArray()) }
            }
            if (c.responseCode !in 200..299) error("NetEase HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().readText()
        } finally {
            c.disconnect()
        }
    }

    private val yrcLine = Regex("""^\[(\d+),(\d+)](.*)$""")
    private val yrcWord = Regex("""\((\d+),(\d+),\d+\)([^(]*)""")

    private fun stamp(ms: Long): String = "%02d:%02d.%03d".format(ms / 60_000, ms / 1000 % 60, ms % 1000)

    /** NetEase word timing → "[mm:ss.mmm]<mm:ss.mmm>word <mm:ss.mmm>word <end>" used by the app. */
    internal fun yrcToRichSync(yrc: String): String? {
        val out = StringBuilder()
        for (line in yrc.lineSequence()) {
            val m = yrcLine.find(line.trim()) ?: continue
            val words = yrcWord.findAll(m.groupValues[3]).toList()
            if (words.isEmpty()) continue
            if (words.joinToString("") { it.groupValues[3] }.let(::isCredit)) continue
            out.append('[').append(stamp(m.groupValues[1].toLong())).append(']')
            var end = 0L
            for (w in words) {
                val start = w.groupValues[1].toLong()
                end = start + w.groupValues[2].toLong()
                out.append('<').append(stamp(start)).append('>').append(w.groupValues[3])
            }
            out.append('<').append(stamp(end)).append(">\n")
        }
        return out.toString().trimEnd().takeIf { it.lines().size >= 3 }
    }

    private val credit = Regex("""^\s*(作词|作曲|编曲|制作人|混音|母带|录音|监制|出品|发行|和声|吉他|贝斯|鼓|键盘|制作|OP|SP|Lyrics by|Composed by|Produced by)\s*[:：]""", RegexOption.IGNORE_CASE)

    private fun isCredit(text: String) = credit.containsMatchIn(text)

    /** Drops NetEase's JSON metadata rows and credit lines; null when nothing is left. */
    internal fun cleanLrc(lrc: String): String? {
        val kept = lrc.lineSequence()
            .filter { !it.trimStart().startsWith("{") }
            .filter { !isCredit(it.replace(Regex("""^\s*(\[[^\]]*])+"""), "")) }
            .joinToString("\n").trim()
        return kept.takeIf { it.lines().count { l -> l.isNotBlank() } >= 3 }
    }
}
