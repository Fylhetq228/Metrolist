package com.metrolist.music.geras.source.soundcloud

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.services.soundcloud.SoundcloudParsingHelper
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Straight calls to SoundCloud's own API for the two things playback needs: "find this track"
 * and "give me its stream". NewPipe's generic search took 5-7 s and its stream lookup 1.5 s per
 * track; these are one small request each (about 0.3 s and 0.15 s) over a kept-alive connection.
 * NewPipe stays as the fallback and still does browsing, playlists and links.
 */
object SoundCloudFast {
    class Track(
        val id: String,
        val title: String,
        val uploader: String,
        val durationSec: Int,
        /** Transcoding endpoint of a plain (not DRM) full-length AAC HLS stream, or null. */
        val transcoding: String?,
        val authorization: String?,
        val artwork: String?,
        val url: String?,
    )

    /** A user or playlist hit of a mixed search. */
    class Other(val isUser: Boolean, val url: String, val title: String, val subtitle: String, val image: String?)

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    private const val API = "https://api-v2.soundcloud.com"
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
    private val tracks = ConcurrentHashMap<String, Track>()
    private val streams = ConcurrentHashMap<String, Pair<String, Long>>()
    // Signed links live a limited time; keep them well under it.
    private const val STREAM_TTL_MS = 4 * 60_000L

    private fun clientId(): String {
        NewPipeDownloader.ensureInit()
        return SoundcloudParsingHelper.clientId()
    }

    /** Fetches the API key and opens the connection ahead of the first tap. */
    fun warmUp() {
        runCatching {
            val id = clientId()
            get("$API/search/tracks?q=a&client_id=$id&limit=1")
        }
    }

    private fun get(url: String): String {
        client.newCall(Request.Builder().url(url).header("User-Agent", UA).build()).execute().use { r ->
            if (!r.isSuccessful) error("SoundCloud API ${r.code}")
            return r.body?.string() ?: error("empty body")
        }
    }

    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.long(k: String) = (this[k] as? JsonPrimitive)?.longOrNull

    private fun parse(o: JsonObject): Track? {
        val id = o.long("id")?.toString() ?: return null
        val duration = o.long("full_duration") ?: o.long("duration") ?: return null
        val transcodings = ((o["media"] as? JsonObject)?.get("transcodings") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val playable = transcodings
            .filter { t ->
                val format = t["format"] as? JsonObject
                format?.str("protocol") == "hls" &&
                    format.str("mime_type").orEmpty().startsWith("audio/mp4") &&
                    (t["snipped"] as? JsonPrimitive)?.booleanOrNull != true
            }
            // aac_160k first, then whatever else is plain AAC.
            .sortedByDescending { it.str("preset") == "aac_160k" }
            .firstOrNull()
        return Track(
            id = id,
            title = o.str("title").orEmpty(),
            uploader = ((o["publisher_metadata"] as? JsonObject)?.str("artist")?.takeIf { it.isNotBlank() }
                ?: (o["user"] as? JsonObject)?.str("username")).orEmpty(),
            durationSec = (duration / 1000).toInt(),
            transcoding = playable?.str("url"),
            authorization = o.str("track_authorization"),
            artwork = o.str("artwork_url") ?: (o["user"] as? JsonObject)?.str("avatar_url"),
            url = o.str("permalink_url"),
        ).also { tracks[id] = it }
    }

    /** Track search. Throws on network errors, so "not found" and "could not ask" stay distinct. */
    fun search(query: String, limit: Int = 10): List<Track> {
        val raw = get("$API/search/tracks?q=${URLEncoder.encode(query, "UTF-8")}&client_id=${clientId()}&limit=$limit")
        val list = (Json.parseToJsonElement(raw) as JsonObject)["collection"] as? JsonArray ?: return emptyList()
        return list.mapNotNull { (it as? JsonObject)?.let(::parse) }
    }

    /**
     * Search of one kind ("tracks", "users", "playlists") or everything (null). Returns tracks
     * and the other hits in the API's own order.
     */
    fun searchAll(query: String, kind: String?, limit: Int = 30): Pair<List<Track>, List<Other>> {
        val path = if (kind == null) "search" else "search/$kind"
        val raw = get("$API/$path?q=${URLEncoder.encode(query, "UTF-8")}&client_id=${clientId()}&limit=$limit")
        val list = ((Json.parseToJsonElement(raw) as JsonObject)["collection"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val found = mutableListOf<Track>()
        val others = mutableListOf<Other>()
        for (o in list) {
            when (o.str("kind")) {
                "track" -> parse(o)?.let { found += it }
                "user" -> o.str("permalink_url")?.let { others += Other(true, it, o.str("username").orEmpty(), "", o.str("avatar_url")) }
                "playlist" -> o.str("permalink_url")?.let { u ->
                    val by = (o["user"] as? JsonObject)?.str("username")
                    val count = o.long("track_count")?.takeIf { it > 0 }?.toString()
                    others += Other(false, u, o.str("title").orEmpty(), listOfNotNull(by, count).joinToString(" · "), o.str("artwork_url"))
                }
            }
        }
        return found to others
    }

    private fun track(id: String): Track? =
        tracks[id] ?: (Json.parseToJsonElement(get("$API/tracks/$id?client_id=${clientId()}")) as? JsonObject)?.let(::parse)

    /** HLS playlist URL of a full, unencrypted stream; null when the track has none. */
    fun streamUrl(trackId: String): String? {
        streams[trackId]?.let { (u, t) -> if (System.currentTimeMillis() - t < STREAM_TTL_MS) return u }
        val t = track(trackId) ?: return null
        val endpoint = t.transcoding ?: return null
        val auth = t.authorization?.let { "&track_authorization=$it" }.orEmpty()
        val url = (Json.parseToJsonElement(get("$endpoint?client_id=${clientId()}$auth")) as? JsonObject)?.str("url") ?: return null
        streams[trackId] = url to System.currentTimeMillis()
        return url
    }
}
