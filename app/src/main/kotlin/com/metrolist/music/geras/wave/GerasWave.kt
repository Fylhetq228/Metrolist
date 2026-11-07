package com.metrolist.music.geras.wave

import android.content.Context
import android.content.SharedPreferences
import androidx.media3.common.MediaItem
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_ATV
import com.metrolist.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_UGC
import com.metrolist.music.constants.SongSortType
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.geras.audio.GerasShuffle
import com.metrolist.music.geras.transfer.TransferMatcher
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.queues.Queue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

enum class WaveMode { USUAL, DISCOVER }

/**
 * My Wave, second version. Instead of mixing random streams it ranks every candidate:
 *
 *  score = 0.42 · how early it appears in a seed's YTM radio (radio is ordered by similarity)
 *        + 0.33 · artist affinity (seed artists → "fans also like" in their order → related)
 *        + 0.15 · same script/language as the seeds (Russian seeds keep Russian music)
 *        + 0.10 · popularity inside its artist's top songs
 *        − penalties: skips and dislikes of that artist, uploads, junk, known tracks in Discover
 *
 * Then it takes the best ones with at most two per artist and never the same artist twice in a
 * row. Next batches continue each seed's radio (no random re-seeding), and the artist
 * neighbourhood grows with what you like and shrinks with what you skip.
 */
object GerasWave {
    @Volatile var database: MusicDatabase? = null
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences("geras_wave", Context.MODE_PRIVATE)
    }

    // ---- feedback --------------------------------------------------------------------------

    private val artistPenalty = ConcurrentHashMap<String, Float>()
    private val artistBoost = ConcurrentHashMap<String, Float>()

    private fun artistKey(name: String) = name.lowercase().trim()

    fun dislike(id: String, artist: String?) {
        val p = prefs ?: return
        val set = p.getStringSet("disliked", emptySet()).orEmpty().toMutableSet().apply { add(id) }
        p.edit().putStringSet("disliked", set).apply()
        artist?.let { a -> bumpArtist(a, 0.35f) }
    }

    fun isDisliked(id: String) = prefs?.getStringSet("disliked", emptySet())?.contains(id) == true

    /** Skipped in the first half minute: that artist is a little less welcome in this session. */
    fun skipped(artist: String?) {
        artist?.let { a -> artistPenalty.merge(artistKey(a), 0.12f) { x, y -> (x + y).coerceAtMost(0.6f) } }
    }

    /** Liked or listened through: pull that artist closer. */
    fun enjoyed(artist: String?) {
        artist?.let { a -> artistBoost.merge(artistKey(a), 0.1f) { x, y -> (x + y).coerceAtMost(0.4f) } }
    }

    private fun bumpArtist(artist: String, by: Float) {
        val p = prefs ?: return
        val k = "pen_" + artistKey(artist)
        p.edit().putFloat(k, (p.getFloat(k, 0f) + by).coerceAtMost(1f)).apply()
    }

    private fun penaltyOf(artist: String): Float =
        (artistPenalty[artistKey(artist)] ?: 0f) + (prefs?.getFloat("pen_" + artistKey(artist), 0f) ?: 0f) - (artistBoost[artistKey(artist)] ?: 0f)

    // ---- taste -----------------------------------------------------------------------------

    private class Taste(
        val heardIds: Set<String>,
        val knownIds: Set<String>,
        val knownArtists: Set<String>,
        val favourites: List<MediaMetadata>,
        /** Seeds ordered by how much you play them, one per artist. */
        val seeds: List<MediaMetadata>,
    )

    private val emptyTaste = Taste(emptySet(), emptySet(), emptySet(), emptyList(), emptyList())

    private suspend fun loadTaste(): Taste = withContext(Dispatchers.IO) {
        val db = database ?: return@withContext emptyTaste
        withTimeoutOrNull(4000) {
            val now = LocalDateTime.now()
            val recent = db.mostPlayedSongs(now.minusDays(3), limit = 60).first()
            val month = db.mostPlayedSongs(now.minusDays(30), limit = 40).first()
            val longer = db.mostPlayedSongs(now.minusDays(180), limit = 80).first()
            val artists = db.mostPlayedArtists(now.minusDays(180), limit = 40).first()
            val liked = db.likedSongs(SongSortType.CREATE_DATE, true).first().take(400)
            val seeds = (month + liked.take(30) + longer)
                .distinctBy { it.artists.firstOrNull()?.name?.lowercase() ?: it.id }
                .map { it.toMediaMetadata() }
            Taste(
                heardIds = recent.map { it.id }.toSet(),
                knownIds = (liked + longer).map { it.id }.toSet(),
                knownArtists = artists.map { artistKey(it.artist.name) }.toSet(),
                favourites = (liked + month).distinctBy { it.id }.map { it.toMediaMetadata() },
                seeds = seeds,
            )
        } ?: emptyTaste
    }

    fun queue(seed: MediaMetadata?, mode: WaveMode = WaveMode.USUAL, title: String? = null, fallbackSeed: MediaMetadata? = null): Queue =
        WaveQueue(seed, mode, title, fallbackSeed)

    // ---- the queue ---------------------------------------------------------------------------

    private enum class Script { CYR, LAT, OTHER }

    private fun scriptOf(text: String): Script {
        var c = 0
        var l = 0
        var o = 0
        for (ch in text) {
            when {
                ch in 'а'..'я' || ch in 'А'..'Я' || ch == 'ё' || ch == 'Ё' -> c++
                ch in 'a'..'z' || ch in 'A'..'Z' -> l++
                ch.isLetter() -> o++
            }
        }
        return when {
            c >= l && c >= o && c > 0 -> Script.CYR
            o > l -> Script.OTHER
            else -> Script.LAT
        }
    }

    private class Stream(var endpoint: WatchEndpoint, var continuation: String?, val seedArtist: String?)

    private class WaveQueue(
        private val seed: MediaMetadata?,
        private val mode: WaveMode,
        private val title: String?,
        private val fallbackSeed: MediaMetadata?,
    ) : Queue {
        override val preloadItem: MediaMetadata? = seed
        private val seen = HashSet<String>()
        private val seenTitles = HashSet<String>()
        private var taste: Taste = emptyTaste
        private val streams = ArrayList<Stream>()
        /** artist name (lowercase) → affinity 0..1 */
        private val affinity = HashMap<String, Float>()
        private val neighbourIds = LinkedHashMap<String, Float>()
        private val usedNeighbours = HashSet<String>()
        private val seedScripts = HashMap<Script, Int>()
        private var failures = 0
        private var batchNo = 0

        private fun key(title: String, artist: String?) = TransferMatcher.normalize(title) + "|" + TransferMatcher.normalize(artist.orEmpty())

        private fun markSeen(id: String, title: String, artist: String?) {
            seen += id
            seenTitles += key(title, artist)
        }

        override suspend fun getInitialStatus(): Queue.Status {
            taste = loadTaste()
            val seeds: List<MediaMetadata> = when {
                seed != null -> listOf(seed)
                taste.seeds.isNotEmpty() -> pickSeeds(taste.seeds)
                fallbackSeed != null -> listOf(fallbackSeed)
                else -> emptyList()
            }
            if (seeds.isEmpty()) throw IllegalStateException("Nothing to start the wave from")
            seed?.let { markSeen(it.id, it.title, it.artists.firstOrNull()?.name) }
            seeds.forEach { s ->
                val a = s.artists.firstOrNull()?.name
                a?.let { affinity[artistKey(it)] = 1f }
                seedScripts.merge(scriptOf(s.title + " " + a.orEmpty()), 1, Int::plus)
                streams += Stream(WatchEndpoint(videoId = s.id, playlistId = "RDAMVM${s.id}"), null, a)
            }
            val batch = withContext(Dispatchers.IO) { buildBatch(first = true) }
            val items = buildList {
                seed?.let { add(it.toMediaItem()) }
                addAll(batch)
            }
            if (items.isEmpty()) throw IllegalStateException("Empty wave")
            return Queue.Status(title = title, items = items, mediaItemIndex = 0)
        }

        /** Mostly your heaviest rotation, plus one wildcard from further down, all different artists. */
        private fun pickSeeds(all: List<MediaMetadata>): List<MediaMetadata> {
            val head = all.take(8).shuffled().take(if (mode == WaveMode.DISCOVER) 3 else 2)
            val tail = all.drop(8).shuffled().take(1)
            return (head + tail).distinctBy { it.artists.firstOrNull()?.name?.lowercase() ?: it.id }
        }

        override fun hasNextPage() = failures < 3

        override suspend fun nextPage(): List<MediaItem> {
            val batch = runCatching { withContext(Dispatchers.IO) { buildBatch(first = false) } }.getOrDefault(emptyList())
            if (batch.isEmpty()) failures++ else failures = 0
            return batch
        }

        private data class Candidate(val song: SongItem, var radio: Float = 0f, var popularity: Float = 0f)

        private suspend fun buildBatch(first: Boolean): List<MediaItem> = coroutineScope {
            batchNo++
            val pool = LinkedHashMap<String, Candidate>()

            // 1. Radio of every seed stream, continuing where it stopped last time.
            val radios = streams.map { st ->
                async {
                    val res = YouTube.next(st.endpoint, st.continuation).getOrNull() ?: return@async Triple(st, emptyList<SongItem>(), null)
                    st.endpoint = res.endpoint
                    st.continuation = res.continuation
                    Triple(st, res.items, res.relatedEndpoint)
                }
            }.awaitAll()
            radios.forEach { (_, items, _) ->
                val list = if (first) items.drop(1) else items
                list.forEachIndexed { i, s ->
                    val c = pool.getOrPut(s.id) { Candidate(s) }
                    c.radio = maxOf(c.radio, 1f - i.toFloat() / (list.size + 5))
                }
            }

            // 2. On the first batch, learn the artist neighbourhood around each seed.
            if (first) {
                val graphs = radios.map { (st, items, related) ->
                    async {
                        val seedArtistId = items.firstOrNull()?.artists?.firstOrNull()?.id
                        val page = seedArtistId?.let { YouTube.artist(it).getOrNull() }
                        val fans = page?.sections.orEmpty().flatMap { it.items }.filterIsInstance<ArtistItem>()
                        val rel = related?.let { YouTube.related(it).getOrNull() }
                        val top = page?.sections?.firstOrNull { sec -> sec.items.any { it is SongItem } }?.items?.filterIsInstance<SongItem>().orEmpty()
                        Triple(fans, rel?.artists.orEmpty(), top to (rel?.songs.orEmpty()))
                    }
                }.awaitAll()
                graphs.forEach { (fans, relArtists, songs) ->
                    fans.forEachIndexed { i, a -> addNeighbour(a, 0.9f - i * 0.04f) }
                    relArtists.forEachIndexed { i, a -> addNeighbour(a, 0.62f - i * 0.03f) }
                    songs.first.forEachIndexed { i, s -> pool.getOrPut(s.id) { Candidate(s) }.popularity = 1f - i / 10f }
                    songs.second.forEach { s -> pool.getOrPut(s.id) { Candidate(s) }.radio = maxOf(pool[s.id]!!.radio, 0.35f) }
                }
            }

            // 3. Top songs of the closest neighbours not used yet (a few per batch).
            val next = neighbourIds.entries.filter { it.key !in usedNeighbours }.sortedByDescending { it.value }
                .take(if (first) 6 else 3)
            next.map { (id, _) ->
                usedNeighbours += id
                async {
                    YouTube.artist(id).getOrNull()?.sections?.firstOrNull { sec -> sec.items.any { it is SongItem } }
                        ?.items?.filterIsInstance<SongItem>()?.take(6).orEmpty()
                }
            }.awaitAll().forEach { list ->
                list.forEachIndexed { i, s -> pool.getOrPut(s.id) { Candidate(s) }.popularity = maxOf(pool[s.id]?.popularity ?: 0f, 1f - i / 6f) }
            }

            // 4. Rank.
            val totalSeedScripts = seedScripts.values.sum().coerceAtLeast(1)
            val ranked = pool.values.mapNotNull { c ->
                val s = c.song
                if (!acceptable(s)) return@mapNotNull null
                val artists = s.artists.map { artistKey(it.name) }
                val aff = artists.maxOfOrNull { affinity[it] ?: 0f } ?: 0f
                val script = (seedScripts[scriptOf(s.title + " " + s.artists.joinToString { it.name })] ?: 0).toFloat() / totalSeedScripts
                var score = 0.42f * c.radio + 0.33f * aff + 0.15f * script + 0.10f * c.popularity
                score -= s.artists.maxOfOrNull { penaltyOf(it.name) } ?: 0f
                when (s.musicVideoType) {
                    MUSIC_VIDEO_TYPE_ATV -> score += 0.04f
                    MUSIC_VIDEO_TYPE_UGC -> score -= 0.15f
                }
                if (mode == WaveMode.DISCOVER && artists.any { it in taste.knownArtists }) score -= 0.5f
                if (mode == WaveMode.USUAL && s.id in taste.knownIds) score += 0.04f
                score += Random.nextFloat() * 0.04f
                c to score
            }.sortedByDescending { it.second }

            val target = if (first) 24 else 18
            val out = ArrayList<SongItem>()
            val perArtist = HashMap<String, Int>()
            for ((c, _) in ranked) {
                if (out.size >= target) break
                val a = artistKey(c.song.artists.firstOrNull()?.name.orEmpty())
                if ((perArtist[a] ?: 0) >= 2) continue
                perArtist[a] = (perArtist[a] ?: 0) + 1
                markSeen(c.song.id, c.song.title, c.song.artists.firstOrNull()?.name)
                out += c.song
            }

            // Every fifth slot in the usual wave: one of your favourites by an artist close to this mood.
            val items = out.map { it.toMediaItem() }.toMutableList()
            if (mode == WaveMode.USUAL) {
                val favs = taste.favourites.filter { f ->
                    f.id !in seen && !isDisliked(f.id) && f.artists.any { (affinity[artistKey(it.name)] ?: 0f) >= 0.55f }
                }.shuffled().take(items.size / 5)
                favs.forEachIndexed { i, f ->
                    markSeen(f.id, f.title, f.artists.firstOrNull()?.name)
                    items.add(((i + 1) * 5).coerceAtMost(items.size), f.toMediaItem())
                }
            }
            // Keep the ranking but never the same artist back to back.
            val order = GerasShuffle.spread(IntArray(items.size) { it }) { items[it].mediaMetadata.artist?.toString()?.lowercase() }
            order.map { items[it] }
        }

        private fun addNeighbour(a: ArtistItem, weight: Float) {
            val k = artistKey(a.title)
            val w = weight.coerceIn(0.2f, 1f)
            if ((affinity[k] ?: 0f) < w) affinity[k] = w
            if (mode == WaveMode.DISCOVER && k in taste.knownArtists) return
            if ((neighbourIds[a.id] ?: 0f) < w) neighbourIds[a.id] = w
        }

        private val junk = listOf(
            "karaoke", "караоке", "instrumental", "минус", "8d", "1 hour", "1 час", "sped up", "speed up", "slowed", "nightcore",
            "nursery", "kids", "for children", "compilation", "full album", "mix 20", "top 50", "top 100", "playlist", "reaction",
            "tutorial", "cover by", "кавер", "remix 20", "bass boosted", "ремикс 20",
        )

        private fun acceptable(s: SongItem): Boolean {
            if (s.id in seen || s.isEpisode || isDisliked(s.id)) return false
            if (key(s.title, s.artists.firstOrNull()?.name) in seenTitles) return false
            if (s.id in taste.heardIds) return false
            if (mode == WaveMode.DISCOVER && s.id in taste.knownIds) return false
            val lower = s.title.lowercase()
            if (junk.any { it in lower }) return false
            if ((s.duration ?: 200) !in 70..720) return false
            return true
        }
    }
}
