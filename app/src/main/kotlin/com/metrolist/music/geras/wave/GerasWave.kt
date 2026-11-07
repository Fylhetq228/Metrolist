package com.metrolist.music.geras.wave

import androidx.media3.common.MediaItem
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
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

enum class WaveMode { USUAL, DISCOVER }

/**
 * "My Wave": an endless queue mixed from three streams, then spaced out by artist.
 *  - style: YTM radio of the seed tracks (same sound and mood);
 *  - neighbours: top songs of artists similar to the seeds' artists;
 *  - related: YTM "related" songs of the seeds.
 * In USUAL mode a few of your own favourites by the same artists are mixed in; DISCOVER skips
 * everything you already know. Each next batch is seeded from what the wave itself played,
 * so it drifts the way a radio host would.
 */
object GerasWave {
    @Volatile var database: MusicDatabase? = null

    private data class Taste(
        val heardIds: Set<String>,
        val libraryIds: Set<String>,
        val topArtistIds: Set<String>,
        val topArtistNames: Set<String>,
        val favourites: List<MediaMetadata>,
        val seedPool: List<String>,
    )

    private suspend fun loadTaste(): Taste = withContext(Dispatchers.IO) {
        val db = database ?: return@withContext Taste(emptySet(), emptySet(), emptySet(), emptySet(), emptyList(), emptyList())
        withTimeoutOrNull(3000) {
            val recent = db.mostPlayedSongs(LocalDateTime.now().minusDays(4), limit = 80).first()
            val month = db.mostPlayedSongs(LocalDateTime.now().minusDays(45), limit = 60).first()
            val artists = db.mostPlayedArtists(LocalDateTime.now().minusDays(60), limit = 12).first()
            val liked = db.likedSongs(SongSortType.CREATE_DATE, true).first().take(300)
            Taste(
                heardIds = recent.map { it.id }.toSet(),
                libraryIds = (liked + month).map { it.id }.toSet(),
                topArtistIds = artists.map { it.id }.toSet(),
                topArtistNames = artists.map { it.artist.name.lowercase() }.toSet(),
                favourites = (liked.take(120) + month).distinctBy { it.id }.map { it.toMediaMetadata() },
                seedPool = (month.take(25) + liked.take(25)).map { it.id }.distinct(),
            )
        } ?: Taste(emptySet(), emptySet(), emptySet(), emptySet(), emptyList(), emptyList())
    }

    fun queue(seed: MediaMetadata?, mode: WaveMode = WaveMode.USUAL, title: String? = null, fallbackSeed: MediaMetadata? = null): Queue =
        WaveQueue(seed, mode, title, fallbackSeed)

    private class WaveQueue(
        private val seed: MediaMetadata?,
        private val mode: WaveMode,
        private val title: String?,
        private val fallbackSeed: MediaMetadata?,
    ) : Queue {
        override val preloadItem: MediaMetadata? = seed
        private val seen = HashSet<String>()
        private val seenTitles = HashSet<String>()
        private var taste: Taste? = null
        private var lastBatch: List<SongItem> = emptyList()
        private var failures = 0

        override suspend fun getInitialStatus(): Queue.Status {
            val t = loadTaste().also { taste = it }
            val seeds = buildList {
                seed?.id?.let(::add)
                if (seed == null) addAll(t.seedPool.shuffled().take(3))
            }.ifEmpty { listOfNotNull(seed?.id ?: fallbackSeed?.id) }
            seed?.let { markSeen(it.id, it.title, it.artists.firstOrNull()?.name) }
            val batch = buildBatch(seeds, t, first = true)
            val items = buildList {
                seed?.let { add(it.toMediaItem()) }
                addAll(batch.map { it.toMediaItem() })
            }
            if (items.isEmpty()) throw IllegalStateException("Empty wave")
            return Queue.Status(title = title, items = items, mediaItemIndex = 0)
        }

        override fun hasNextPage() = failures < 3

        override suspend fun nextPage(): List<MediaItem> {
            val t = taste ?: loadTaste().also { taste = it }
            // Drift: seed the next batch from what the wave just played, plus one of your own.
            val seeds = (lastBatch.shuffled().take(2).map { it.id } + t.seedPool.shuffled().take(if (mode == WaveMode.USUAL) 1 else 0))
                .ifEmpty { listOfNotNull(seed?.id) }
            val batch = runCatching { buildBatch(seeds, t, first = false) }.getOrDefault(emptyList())
            if (batch.isEmpty()) failures++ else failures = 0
            return batch.map { it.toMediaItem() }
        }

        private fun key(title: String, artist: String?) = TransferMatcher.normalize(title) + "|" + TransferMatcher.normalize(artist.orEmpty())

        private fun markSeen(id: String, title: String, artist: String?) {
            seen += id
            seenTitles += key(title, artist)
        }

        private val junk = listOf(
            "karaoke", "instrumental", "8d", "1 hour", "sped up", "slowed", "nursery", "kids", "for children", "compilation",
            "full album", "mix 20", "top 50", "top 100", "playlist", "reaction", "tutorial", "lyrics video",
        )

        private fun acceptable(s: SongItem, t: Taste, allowUploads: Boolean = false): Boolean {
            if (s.id in seen || s.isEpisode) return false
            if (s.musicVideoType == MUSIC_VIDEO_TYPE_UGC && (!allowUploads || s.artists.isEmpty())) return false
            if (key(s.title, s.artists.firstOrNull()?.name) in seenTitles) return false
            if (s.id in t.heardIds) return false
            if (mode == WaveMode.DISCOVER) {
                if (s.id in t.libraryIds) return false
                if (s.artists.any { it.id in t.topArtistIds || it.name.lowercase() in t.topArtistNames }) return false
            }
            val lower = s.title.lowercase()
            if (junk.any { it in lower }) return false
            if ((s.duration ?: 200) !in 60..900) return false
            return true
        }

        private suspend fun buildBatch(seeds: List<String>, t: Taste, first: Boolean): List<SongItem> = coroutineScope {
            // Stream 1 + 3: radio and related for each seed.
            val perSeed = seeds.take(3).map { id ->
                async(Dispatchers.IO) {
                    val next = YouTube.next(WatchEndpoint(videoId = id, playlistId = "RDAMVM$id")).getOrNull()
                    val related = next?.relatedEndpoint?.let { YouTube.related(it).getOrNull() }
                    // "Fans also like" on the seed artist's page: the most reliable similar-artist list.
                    val seedArtist = next?.items?.firstOrNull()?.artists?.firstOrNull()?.id
                    val fans = seedArtist?.let { aid ->
                        YouTube.artist(aid).getOrNull()?.sections.orEmpty().flatMap { it.items }.filterIsInstance<ArtistItem>()
                    }.orEmpty()
                    Triple(next?.items.orEmpty().drop(1), related?.songs.orEmpty(), (related?.artists.orEmpty() + fans))
                }
            }.awaitAll()
            val style = perSeed.flatMap { it.first }
            val related = perSeed.flatMap { it.second }
            // Stream 2: similar artists → their top songs.
            val similar: List<ArtistItem> = perSeed.flatMap { it.third }.distinctBy { it.id }
                .filter { mode == WaveMode.USUAL || (it.id !in t.topArtistIds && it.title.lowercase() !in t.topArtistNames) }
                .shuffled().take(if (first) 5 else 3)
            val neighbours = similar.map { a ->
                async(Dispatchers.IO) {
                    YouTube.artist(a.id).getOrNull()?.sections?.firstOrNull { sec -> sec.items.any { it is SongItem } }
                        ?.items?.filterIsInstance<SongItem>()?.shuffled()?.take(3).orEmpty()
                }
            }.awaitAll().flatten()

            val streams = listOf(style.shuffled().toMutableList(), neighbours.shuffled().toMutableList(), related.shuffled().toMutableList())
            val out = ArrayList<SongItem>()
            val perArtist = HashMap<String, Int>()
            val target = if (first) 30 else 25
            var turn = 0
            // Round-robin over the streams: style, neighbours, style, related…
            val pattern = intArrayOf(0, 1, 0, 2, 1)
            var guard = 0
            val rejected = ArrayList<SongItem>()
            while (out.size < target && streams.any { it.isNotEmpty() } && guard++ < 500) {
                val stream = streams[pattern[turn++ % pattern.size]]
                val s = stream.removeFirstOrNull() ?: continue
                if (!acceptable(s, t)) {
                    rejected += s
                    continue
                }
                val artist = s.artists.firstOrNull()?.name?.lowercase().orEmpty()
                if ((perArtist[artist] ?: 0) >= 2) continue
                perArtist[artist] = (perArtist[artist] ?: 0) + 1
                markSeen(s.id, s.title, s.artists.firstOrNull()?.name)
                out += s
            }
            // Where YTM has few official tracks (region), fall back to clean uploads.
            if (out.size < target / 2) {
                for (s in rejected) {
                    if (out.size >= target) break
                    if (!acceptable(s, t, allowUploads = true)) continue
                    val artist = s.artists.firstOrNull()?.name?.lowercase().orEmpty()
                    if ((perArtist[artist] ?: 0) >= 2) continue
                    perArtist[artist] = (perArtist[artist] ?: 0) + 1
                    markSeen(s.id, s.title, s.artists.firstOrNull()?.name)
                    out += s
                }
            }
            lastBatch = out
            var result: List<SongItem> = out
            // USUAL: about one in six is a favourite of yours by an artist already in this batch.
            if (mode == WaveMode.USUAL && t.favourites.isNotEmpty()) {
                val names = out.flatMap { s -> s.artists.map { it.name.lowercase() } }.toSet()
                val favs = t.favourites.filter { f -> f.id !in seen && f.artists.any { it.name.lowercase() in names } }.shuffled().take(out.size / 6)
                if (favs.isNotEmpty()) {
                    val mixed = out.toMutableList<Any>()
                    favs.forEachIndexed { i, f ->
                        seen += f.id
                        mixed.add(((i + 1) * 6).coerceAtMost(mixed.size), f)
                    }
                    return@coroutineScope mixed.mapNotNull { it as? SongItem ?: (it as? MediaMetadata)?.let(::asSong) }.let(::spaced)
                }
            }
            spaced(result)
        }

        private fun spaced(list: List<SongItem>): List<SongItem> {
            val order = GerasShuffle.spread(IntArray(list.size) { it }) { list[it].artists.firstOrNull()?.name?.lowercase() }
            return order.map { list[it] }
        }

        private fun asSong(m: MediaMetadata) = SongItem(
            id = m.id,
            title = m.title,
            artists = m.artists.map { com.metrolist.innertube.models.Artist(name = it.name, id = it.id) },
            album = m.album?.let { com.metrolist.innertube.models.Album(name = it.title, id = it.id) },
            duration = m.duration.takeIf { it > 0 },
            thumbnail = m.thumbnailUrl.orEmpty(),
            explicit = m.explicit,
            musicVideoType = m.musicVideoType,
        )
    }
}
