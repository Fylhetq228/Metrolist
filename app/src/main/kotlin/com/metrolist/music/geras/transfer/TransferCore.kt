package com.metrolist.music.geras.transfer

import android.net.Uri
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.geras.search.QueryTools
import com.metrolist.music.geras.source.ItemKind
import com.metrolist.music.geras.source.soundcloud.SoundCloudSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** A track as the source service describes it, before matching. */
data class SourceTrack(
    val title: String,
    val artists: List<String>,
    val durationSec: Int? = null,
    val album: String? = null,
    /** Already playable here (YTM video id or "sc:<id>"), so no matching is needed. */
    val playableId: String? = null,
    val thumbnail: String? = null,
)

data class ImportedPlaylist(
    val name: String,
    val source: TransferSource,
    val cover: String?,
    val tracks: List<SourceTrack>,
    /** True when the source page only exposes part of the list (Spotify embed). */
    val truncated: Boolean = false,
)

enum class TransferSource(val label: String) { SPOTIFY("Spotify"), YOUTUBE("YouTube Music"), SOUNDCLOUD("SoundCloud"), FILE("Файл") }

/** A playable match on our side. */
data class MatchCandidate(
    val id: String,
    val title: String,
    val artists: List<String>,
    val durationSec: Int?,
    val thumbnail: String?,
    val album: String? = null,
    val artistIds: List<String?> = emptyList(),
    val albumId: String? = null,
    val score: Float = 1f,
    /** YTM video type: ATV (official audio), OMV (official video), UGC (user upload). */
    val videoType: String? = null,
)

enum class MatchStatus { PENDING, FOUND, DOUBTFUL, NOT_FOUND }

data class TransferRow(
    val index: Int,
    val source: SourceTrack,
    val status: MatchStatus = MatchStatus.PENDING,
    val match: MatchCandidate? = null,
    val candidates: List<MatchCandidate> = emptyList(),
    val removed: Boolean = false,
)

class TransferException(val kind: Kind, cause: Throwable? = null) : Exception(kind.name, cause) {
    enum class Kind { UNSUPPORTED_LINK, NETWORK, EMPTY }
}

object TransferReaders {
    private val spotifyRe = Regex("""(?:open\.spotify\.com/(?:intl-[a-z]+/)?|spotify:)(playlist|album)[/:]([A-Za-z0-9]{22})""")
    private val ytListRe = Regex("""[?&]list=([A-Za-z0-9_-]+)""")
    private val ytBrowseRe = Regex("""music\.youtube\.com/browse/(VL[A-Za-z0-9_-]+|MPREb_[A-Za-z0-9_-]+)""")

    fun canRead(text: String): Boolean {
        val t = text.trim()
        return spotifyRe.containsMatchIn(t) || ytListRe.containsMatchIn(t) || ytBrowseRe.containsMatchIn(t) ||
            ("soundcloud.com" in t && "/sets/" in t)
    }

    suspend fun read(link: String): ImportedPlaylist = withContext(Dispatchers.IO) {
        val url = link.trim()
        try {
            spotifyRe.find(url)?.let { m -> return@withContext spotify(m.groupValues[1], m.groupValues[2]) }
            if ("soundcloud.com" in url) return@withContext soundCloud(url)
            val ytId = ytListRe.find(url)?.groupValues?.get(1) ?: ytBrowseRe.find(url)?.groupValues?.get(1)?.removePrefix("VL")
            if (ytId != null) return@withContext youtube(ytId)
        } catch (e: TransferException) {
            throw e
        } catch (e: Exception) {
            throw TransferException(TransferException.Kind.NETWORK, e)
        }
        throw TransferException(TransferException.Kind.UNSUPPORTED_LINK)
    }

    private fun spotify(type: String, id: String): ImportedPlaylist {
        val html = fetch("https://open.spotify.com/embed/$type/$id") ?: throw TransferException(TransferException.Kind.NETWORK)
        val raw = Regex("""<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1) ?: throw TransferException(TransferException.Kind.EMPTY)
        val entity = Json.parseToJsonElement(raw).jsonObject
            .obj("props")?.obj("pageProps")?.obj("state")?.obj("data")?.obj("entity")
            ?: throw TransferException(TransferException.Kind.EMPTY)
        val cover = (entity.obj("coverArt")?.get("sources") as? JsonArray)
            ?.mapNotNull { it.jsonObject.str("url") }?.lastOrNull()
        val tracks = (entity["trackList"] as? JsonArray).orEmpty().mapNotNull { el ->
            val o = el.jsonObject
            val title = o.str("title") ?: return@mapNotNull null
            SourceTrack(
                title = title,
                artists = o.str("subtitle").orEmpty().split(", ", ", ").map { it.trim() }.filter { it.isNotEmpty() },
                durationSec = o["duration"]?.jsonPrimitive?.longOrNull?.let { (it / 1000).toInt() },
                album = if (type == "album") entity.str("name") else null,
            )
        }
        if (tracks.isEmpty()) throw TransferException(TransferException.Kind.EMPTY)
        // The embed page lists at most 100 tracks; the real total is not exposed there.
        return ImportedPlaylist(entity.str("name") ?: "Spotify", TransferSource.SPOTIFY, cover, tracks, truncated = tracks.size >= 100)
    }

    private suspend fun youtube(id: String): ImportedPlaylist {
        if (id.startsWith("MPREb_")) {
            val album = YouTube.album(id).getOrThrow()
            return ImportedPlaylist(album.album.title, TransferSource.YOUTUBE, album.album.thumbnail, album.songs.map { it.toSource() })
        }
        val page = YouTube.playlist(id).getOrThrow()
        val songs = page.songs.toMutableList()
        var cont = page.songsContinuation ?: page.continuation
        var guard = 0
        while (cont != null && songs.size < 5000 && guard++ < 100) {
            val more = YouTube.playlistContinuation(cont).getOrNull() ?: break
            songs += more.songs
            cont = more.continuation
        }
        if (songs.isEmpty()) throw TransferException(TransferException.Kind.EMPTY)
        return ImportedPlaylist(page.playlist.title, TransferSource.YOUTUBE, page.playlist.thumbnail, songs.map { it.toSource() })
    }

    private suspend fun soundCloud(url: String): ImportedPlaylist {
        val c = SoundCloudSource.loadCollection(ItemKind.PLAYLIST, url).getOrThrow()
        if (c.tracks.isEmpty()) throw TransferException(TransferException.Kind.EMPTY)
        return ImportedPlaylist(
            c.title, TransferSource.SOUNDCLOUD, c.thumbnail,
            c.tracks.map { SourceTrack(it.title, listOf(it.subtitle), it.durationSec, playableId = it.id, thumbnail = it.thumbnail) },
        )
    }

    private fun SongItem.toSource() = SourceTrack(
        title = title,
        artists = artists.map { it.name },
        durationSec = duration,
        album = album?.name,
        playableId = id,
        thumbnail = thumbnail,
    )

    /** CSV (title, artist, duration, album; header optional) or Geras/other JSON exports. */
    fun parseFile(name: String, text: String): ImportedPlaylist {
        val trimmed = text.trimStart('﻿', ' ', '\n', '\r', '\t')
        val title = name.substringBeforeLast('.').ifBlank { "Импорт" }
        val tracks = if (trimmed.startsWith("{") || trimmed.startsWith("[")) parseJson(trimmed) else parseCsv(trimmed)
        if (tracks.isEmpty()) throw TransferException(TransferException.Kind.EMPTY)
        val jsonName = runCatching { Json.parseToJsonElement(trimmed).jsonObject.str("name") }.getOrNull()
        return ImportedPlaylist(jsonName ?: title, TransferSource.FILE, null, tracks)
    }

    private fun parseJson(text: String): List<SourceTrack> {
        val root = Json.parseToJsonElement(text)
        val arr = (root as? JsonArray) ?: (root.jsonObject["tracks"] as? JsonArray) ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val t = o.str("title") ?: o.str("name") ?: o.str("Track Name") ?: return@mapNotNull null
            val artists = (o["artists"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                ?: (o.str("artist") ?: o.str("Artist Name(s)")).orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val dur = o["duration"]?.jsonPrimitive?.intOrNull ?: o["durationSec"]?.jsonPrimitive?.intOrNull
                ?: o["duration_ms"]?.jsonPrimitive?.longOrNull?.let { (it / 1000).toInt() }
            SourceTrack(t, artists, dur, o.str("album"), playableId = o.str("id")?.takeIf { it.isNotBlank() })
        }
    }

    private fun parseCsv(text: String): List<SourceTrack> {
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        val sep = if (lines[0].count { it == ';' } > lines[0].count { it == ',' }) ';' else ','
        val first = splitCsv(lines[0], sep).map { it.lowercase().trim() }
        val hasHeader = first.any { it in setOf("title", "track name", "name", "artist", "artist name(s)", "название", "исполнитель") }
        fun col(vararg names: String) = if (hasHeader) first.indexOfFirst { it in names } else -1
        val ti = col("title", "track name", "name", "название").takeIf { it >= 0 } ?: 0
        val ai = col("artist", "artists", "artist name(s)", "исполнитель").takeIf { it >= 0 } ?: 1
        val di = col("duration", "duration (ms)", "duration_ms", "длительность")
        val ali = col("album", "album name", "альбом")
        val msDuration = hasHeader && di >= 0 && "ms" in first[di]
        return lines.drop(if (hasHeader) 1 else 0).mapNotNull { line ->
            val cells = splitCsv(line, sep)
            val t = cells.getOrNull(ti)?.trim().orEmpty()
            if (t.isEmpty()) return@mapNotNull null
            val dur = cells.getOrNull(if (di >= 0) di else 2)?.trim()?.let { parseDuration(it, msDuration) }
            SourceTrack(
                title = t,
                artists = cells.getOrNull(ai).orEmpty().split(",", ";", " & ").map { it.trim() }.filter { it.isNotEmpty() },
                durationSec = dur,
                album = cells.getOrNull(ali)?.takeIf { ali >= 0 && it.isNotBlank() },
            )
        }
    }

    private fun parseDuration(s: String, ms: Boolean): Int? {
        if (":" in s) {
            val parts = s.split(":").mapNotNull { it.trim().toIntOrNull() }
            return parts.fold(0) { acc, v -> acc * 60 + v }
        }
        val n = s.toLongOrNull() ?: return null
        return if (ms || n > 10_000) (n / 1000).toInt() else n.toInt()
    }

    private fun splitCsv(line: String, sep: Char): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> { cur.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == sep && !quoted -> { out += cur.toString(); cur.clear() }
                else -> cur.append(c)
            }
            i++
        }
        out += cur.toString()
        return out
    }

    private fun JsonObject.obj(key: String) = this[key] as? JsonObject
    private fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun fetch(url: String): String? = runCatching {
        (URL(url).openConnection() as HttpURLConnection).run {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36")
            setRequestProperty("Accept-Language", "en")
            try {
                if (responseCode in 200..299) inputStream.bufferedReader().readText() else null
            } finally {
                disconnect()
            }
        }
    }.getOrNull()

    fun spotifyKind(url: String): String? = spotifyRe.find(url)?.groupValues?.get(1)

    @Suppress("unused")
    fun isFileUri(uri: Uri) = uri.scheme == "content" || uri.scheme == "file"
}

/** Title/artist/duration matching against YouTube Music. */
object TransferMatcher {
    private val noiseInBrackets = Regex(
        """[(\[][^)\]]*(feat|ft\.|with|prod|remaster|official|video|audio|lyric|visuali[sz]er|explicit|clean|radio edit|version|mono|stereo|hd|hq)[^)\]]*[)\]]""",
        RegexOption.IGNORE_CASE,
    )
    private val featTail = Regex("""\s+(feat\.?|ft\.?|featuring)\s.+$""", RegexOption.IGNORE_CASE)
    private val dashTail = Regex("""\s-\s.*(remaster|version|edit|mix\)?$).*$""", RegexOption.IGNORE_CASE)

    fun normalize(s: String): String {
        var t = Normalizer.normalize(s, Normalizer.Form.NFKD).replace(Regex("""\p{Mn}+"""), "")
        t = t.replace(noiseInBrackets, " ").replace(featTail, "").replace(dashTail, "")
        t = t.lowercase().replace('ё', 'е').replace("&", " and ").replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()
        // Compare in one script: Cyrillic gets transliterated so "Кино" meets "Kino".
        if (t.any { it in 'а'..'я' }) QueryTools.transliterate(t)?.let { t = it }
        return t.replace(Regex("""\s+"""), " ").trim()
    }

    private fun levRatio(a: String, b: String): Float {
        if (a == b) return 1f
        if (a.isEmpty() || b.isEmpty()) return 0f
        val prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = min(min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            System.arraycopy(cur, 0, prev, 0, cur.size)
        }
        return 1f - prev[b.length].toFloat() / max(a.length, b.length)
    }

    private fun tokens(s: String) = s.split(' ').filter { it.isNotEmpty() }.toSet()

    fun similarity(a: String, b: String): Float {
        val na = normalize(a)
        val nb = normalize(b)
        if (na.isEmpty() || nb.isEmpty()) return 0f
        val ta = tokens(na)
        val tb = tokens(nb)
        val jaccard = ta.intersect(tb).size.toFloat() / ta.union(tb).size
        val contains = if (na.length >= 3 && nb.length >= 3 && (na in nb || nb in na)) 0.9f else 0f
        return maxOf(levRatio(na, nb), jaccard, contains)
    }

    private val variantWords = listOf(
        "karaoke", "instrumental", "cover", "8d", "slowed", "sped up", "speed up", "nightcore", "reverb", "live", "remix",
        "acoustic", "lirik", "terjemahan", "перевод", "reupload", "minus", "минус", "кавер", "ремикс", "concert", "концерт",
        "tutorial", "reaction", "1 hour", "loop", "bass boosted", "empty arena", "piano version", "arena", "арена", "лайв", "live version",
    )

    /** Uploads are often titled "Artist - Title (Lyrics) | channel junk"; keep only the title part. */
    private fun cleanCandidateTitle(src: SourceTrack, title: String): String {
        var t = title.substringBefore(" | ").substringBefore(" // ")
        val dash = t.indexOf(" - ")
        if (dash > 0) {
            val left = t.substring(0, dash)
            val right = t.substring(dash + 3)
            val leftIsArtist = src.artists.any { similarity(it, left) > 0.7f || normalize(left).contains(normalize(it)) }
            val rightIsArtist = src.artists.any { similarity(it, right) > 0.7f }
            t = when {
                leftIsArtist -> right
                rightIsArtist -> left
                else -> t
            }
        }
        return t
    }

    fun score(src: SourceTrack, c: MatchCandidate): Float {
        val cleaned = cleanCandidateTitle(src, c.title)
        val title = maxOf(similarity(src.title, c.title), similarity(src.title, cleaned))
        val artist = if (src.artists.isEmpty() || c.artists.isEmpty()) {
            0.6f
        } else {
            val byChannel = src.artists.maxOf { a -> c.artists.maxOf { b -> similarity(a, b) } }
            // A user upload names the artist in the title rather than the channel.
            val inTitle = if (src.artists.any { a -> normalize(a).length >= 2 && normalize(c.title).contains(normalize(a)) }) 0.9f else 0f
            maxOf(byChannel, inTitle)
        }
        var s = title * 0.62f + artist * 0.38f
        val srcLower = (src.title + " " + src.album.orEmpty()).lowercase()
        val candLower = c.title.lowercase()
        if (variantWords.any { it in candLower && it !in srcLower }) s -= 0.3f
        when (c.videoType) {
            "MUSIC_VIDEO_TYPE_ATV" -> s += 0.03f
            "MUSIC_VIDEO_TYPE_UGC" -> s -= 0.05f
        }
        val d1 = src.durationSec
        val d2 = c.durationSec
        if (d1 != null && d2 != null && d1 > 0 && d2 > 0) {
            val diff = abs(d1 - d2)
            s += when {
                diff <= 3 -> 0.06f
                diff <= 10 -> -0.08f
                diff <= 30 -> -0.2f
                else -> -0.35f
            }
        }
        return s.coerceIn(0f, 1f)
    }

    fun statusFor(src: SourceTrack, best: MatchCandidate?): MatchStatus {
        best ?: return MatchStatus.NOT_FOUND
        val durOk = src.durationSec == null || best.durationSec == null || abs(src.durationSec - best.durationSec) <= 3
        return when {
            best.score >= 0.82f && durOk -> MatchStatus.FOUND
            best.score >= 0.5f -> MatchStatus.DOUBTFUL
            else -> MatchStatus.NOT_FOUND
        }
    }

    private fun SongItem.toCandidate() = MatchCandidate(
        id = id,
        title = title,
        artists = artists.map { it.name },
        durationSec = duration,
        thumbnail = thumbnail,
        album = album?.name,
        artistIds = artists.map { it.id },
        albumId = album?.id,
        videoType = musicVideoType,
    )

    suspend fun searchCandidates(query: String, video: Boolean = false): List<MatchCandidate> =
        YouTube.search(query, if (video) YouTube.SearchFilter.FILTER_VIDEO else YouTube.SearchFilter.FILTER_SONG)
            .getOrThrow().items.filterIsInstance<SongItem>().take(8).map { it.toCandidate() }

    /** Best candidates for [src], highest score first. Throws on network errors. */
    suspend fun match(src: SourceTrack): List<MatchCandidate> {
        val artist = src.artists.firstOrNull().orEmpty()
        val songs = searchCandidates("${src.title} $artist".trim())
            .map { it.copy(score = score(src, it)) }
        var all = songs
        if ((songs.maxOfOrNull { it.score } ?: 0f) < 0.82f) {
            // Not on YTM as a "song": try uploads and music videos.
            val videos = runCatching { searchCandidates("$artist ${src.title}".trim(), video = true) }.getOrDefault(emptyList())
                .map { it.copy(score = score(src, it) - 0.04f) }
            all = songs + videos
        }
        return all.distinctBy { it.id }.sortedByDescending { it.score }.take(6)
    }

    fun direct(src: SourceTrack): MatchCandidate? = src.playableId?.let {
        MatchCandidate(it, src.title, src.artists, src.durationSec, src.thumbnail, src.album, score = 1f)
    }
}

object TransferExport {
    fun csv(name: String, rows: List<TransferRow>): String = buildString {
        appendLine("title,artist,duration,album,id")
        rows.filter { !it.removed }.forEach { r ->
            val t = r.match?.title ?: r.source.title
            val a = (r.match?.artists ?: r.source.artists).joinToString(", ")
            val d = r.match?.durationSec ?: r.source.durationSec
            val al = r.match?.album ?: r.source.album
            appendLine(listOf(t, a, d?.toString().orEmpty(), al.orEmpty(), r.match?.id.orEmpty()).joinToString(",") { cell(it) })
        }
    }.let { if (name.isEmpty()) it else it }

    fun json(name: String, rows: List<TransferRow>): String {
        val obj = buildJsonObject {
            put("name", name)
            put("exportedBy", "Geras")
            put(
                "tracks",
                buildJsonArray {
                    rows.filter { !it.removed }.forEach { r ->
                        add(
                            buildJsonObject {
                                put("title", r.match?.title ?: r.source.title)
                                put("artists", buildJsonArray { (r.match?.artists ?: r.source.artists).forEach { add(JsonPrimitive(it)) } })
                                (r.match?.durationSec ?: r.source.durationSec)?.let { put("duration", it) }
                                (r.match?.album ?: r.source.album)?.let { put("album", it) }
                                r.match?.id?.let { put("id", it) }
                            },
                        )
                    }
                },
            )
        }
        return Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), obj)
    }

    private fun cell(s: String) = if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
}
