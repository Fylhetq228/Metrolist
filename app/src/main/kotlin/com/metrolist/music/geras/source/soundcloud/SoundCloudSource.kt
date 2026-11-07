package com.metrolist.music.geras.source.soundcloud

import android.net.Uri
import com.metrolist.music.geras.source.ItemKind
import com.metrolist.music.geras.source.MusicSource
import com.metrolist.music.geras.source.OpenContext
import com.metrolist.music.geras.source.SourceItem
import com.metrolist.music.geras.source.SourcePage
import com.metrolist.music.geras.source.SourceSection
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.playback.queues.ListQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** SoundCloud through NewPipeExtractor: search, links, tracks, playlists and artists. */
object SoundCloudSource : MusicSource {
    override val id = "sc"
    override val name = "SoundCloud"
    override val badge = "SC"
    override val badgeColor = 0xFFFF7A1AL

    /** Media ids of SoundCloud tracks in the queue and the database. */
    const val ID_PREFIX = "sc:"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val service: StreamingService get() = NewPipeDownloader.ensureInit().let { ServiceList.SoundCloud }
    private val pages = ConcurrentHashMap<String, Triple<String, List<String>, Page>>()

    fun isScId(mediaId: String?) = mediaId?.startsWith(ID_PREFIX) == true

    private fun bigArtwork(url: String?) = url
        ?.replace("-mini.", "-t500x500.")
        ?.replace("-large.", "-t500x500.")
        ?.replace("-small.", "-t500x500.")
        ?.replace("-badge.", "-t500x500.")

    private fun trackIdFromUrl(url: String): String? =
        runCatching { service.streamLHFactory.fromUrl(url).id }.getOrNull()

    fun map(item: InfoItem): SourceItem? = when (item) {
        is StreamInfoItem -> trackIdFromUrl(item.url)?.let { tid ->
            SourceItem(
                sourceId = id,
                kind = ItemKind.TRACK,
                id = ID_PREFIX + tid,
                title = item.name,
                subtitle = item.uploaderName.orEmpty(),
                thumbnail = bigArtwork(item.thumbnails.maxByOrNull { it.height }?.url),
                durationSec = item.duration.takeIf { it > 0 }?.toInt(),
                raw = item.url,
            )
        }
        is PlaylistInfoItem -> SourceItem(
            sourceId = id,
            kind = ItemKind.PLAYLIST,
            id = item.url,
            title = item.name,
            subtitle = listOfNotNull(item.uploaderName, item.streamCount.takeIf { it > 0 }?.toString()).joinToString(" · "),
            thumbnail = bigArtwork(item.thumbnails.maxByOrNull { it.height }?.url),
            raw = item.url,
        )
        is ChannelInfoItem -> SourceItem(
            sourceId = id,
            kind = ItemKind.ARTIST,
            id = item.url,
            title = item.name,
            subtitle = "",
            thumbnail = bigArtwork(item.thumbnails.maxByOrNull { it.height }?.url),
            raw = item.url,
        )
        else -> null
    }

    private fun filterFor(kind: ItemKind?): List<String>? = when (kind) {
        null -> emptyList()
        ItemKind.TRACK -> listOf("tracks")
        ItemKind.ARTIST -> listOf("users")
        ItemKind.PLAYLIST -> listOf("playlists")
        ItemKind.ALBUM -> null
    }

    private fun toPage(items: List<InfoItem>, query: String, filter: List<String>, next: Page?, mixed: Boolean): SourcePage {
        val mapped = items.mapNotNull(::map)
        val token = next?.let { UUID.randomUUID().toString().also { t -> pages[t] = Triple(query, filter, it) } }
        return if (mixed) {
            val top = mapped.firstOrNull { it.kind == ItemKind.TRACK }
            val rest = mapped.filterNot { it === top }
            SourcePage(top, ItemKind.entries.map { k -> SourceSection(k, rest.filter { it.kind == k }) }.filter { it.items.isNotEmpty() }, token)
        } else {
            SourcePage(null, listOf(SourceSection(null, mapped)), token)
        }
    }

    override suspend fun search(query: String, kind: ItemKind?): Result<SourcePage> = withContext(Dispatchers.IO) {
        runCatching {
            val filter = filterFor(kind) ?: return@runCatching SourcePage(null, emptyList())
            val info = SearchInfo.getInfo(service, service.searchQHFactory.fromQuery(query, filter, ""))
            toPage(info.relatedItems, query, filter, info.nextPage, kind == null)
        }
    }

    override suspend fun searchMore(continuation: String): Result<SourcePage> = withContext(Dispatchers.IO) {
        runCatching {
            val (query, filter, page) = pages.remove(continuation) ?: error("Expired page")
            val more = SearchInfo.getMoreItems(service, service.searchQHFactory.fromQuery(query, filter, ""), page)
            toPage(more.items, query, filter, more.nextPage, false)
        }
    }

    override suspend fun suggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        runCatching { service.suggestionExtractor.suggestionList(query) }.getOrDefault(emptyList())
    }

    override fun canOpen(url: String): Boolean = "soundcloud.com" in url || "on.soundcloud.com" in url

    override suspend fun resolve(url: String): SourceItem? = withContext(Dispatchers.IO) {
        runCatching {
            when (service.getLinkTypeByUrl(url)) {
                StreamingService.LinkType.STREAM -> {
                    val info = StreamInfo.getInfo(service, url)
                    SourceItem(id, ItemKind.TRACK, ID_PREFIX + info.id, info.name, info.uploaderName.orEmpty(), bigArtwork(info.thumbnails.maxByOrNull { it.height }?.url), info.duration.toInt(), raw = info.url)
                }
                StreamingService.LinkType.PLAYLIST -> {
                    val info = PlaylistInfo.getInfo(service, url)
                    SourceItem(id, ItemKind.PLAYLIST, info.url, info.name, info.uploaderName.orEmpty(), bigArtwork(info.thumbnails.maxByOrNull { it.height }?.url), raw = info.url)
                }
                StreamingService.LinkType.CHANNEL -> {
                    val info = ChannelInfo.getInfo(service, url)
                    SourceItem(id, ItemKind.ARTIST, info.url, info.name, "", bigArtwork(info.avatars.maxByOrNull { it.height }?.url), raw = info.url)
                }
                else -> null
            }
        }.getOrNull()
    }

    fun SourceItem.toMetadata() = MediaMetadata(
        id = id,
        title = title,
        artists = listOf(MediaMetadata.Artist(id = null, name = subtitle.ifBlank { "SoundCloud" })),
        duration = durationSec ?: -1,
        thumbnailUrl = thumbnail,
    )

    override fun open(item: SourceItem, context: OpenContext) {
        when (item.kind) {
            ItemKind.TRACK -> {
                if (context.playerConnection.mediaMetadata.value?.id == item.id) {
                    context.playerConnection.togglePlayPause()
                    return
                }
                context.playerConnection.playQueue(ListQueue(title = item.title, items = listOf(item.toMetadata().toMediaItem())))
                // Keep the music going with SoundCloud's related tracks.
                val url = item.raw as? String ?: return
                scope.launch {
                    val related = runCatching { StreamInfo.getInfo(service, url).relatedItems }.getOrNull().orEmpty()
                        .mapNotNull(::map).filter { it.kind == ItemKind.TRACK }.take(25)
                    if (related.isEmpty()) return@launch
                    withContext(Dispatchers.Main) {
                        val player = context.playerConnection.player
                        if (player.currentMediaItem?.mediaId == item.id) {
                            player.addMediaItems(related.map { it.toMetadata().toMediaItem() })
                        }
                    }
                }
            }
            ItemKind.PLAYLIST, ItemKind.ARTIST, ItemKind.ALBUM ->
                context.navController.navigate(collectionRoute(item))
        }
    }

    // Collections (playlists and artists).

    const val ROUTE = "geras/sc/collection?kind={kind}&url={url}"

    fun collectionRoute(item: SourceItem) = "geras/sc/collection?kind=${item.kind.name}&url=${Uri.encode(item.raw as? String ?: item.id)}"

    data class Collection(
        val title: String,
        val subtitle: String,
        val thumbnail: String?,
        val tracks: List<SourceItem>,
    )

    suspend fun loadCollection(kind: ItemKind, url: String): Result<Collection> = withContext(Dispatchers.IO) {
        runCatching {
            if (kind == ItemKind.ARTIST) {
                val info = ChannelInfo.getInfo(service, url)
                val tab = info.tabs.firstOrNull { it.contentFilters.contains("tracks") } ?: info.tabs.first()
                val tracks = ChannelTabInfo.getInfo(service, tab).relatedItems.mapNotNull(::map).filter { it.kind == ItemKind.TRACK }
                Collection(info.name, info.description.orEmpty().lineSequence().firstOrNull().orEmpty(), bigArtwork(info.avatars.maxByOrNull { it.height }?.url), tracks)
            } else {
                val info = PlaylistInfo.getInfo(service, url)
                val tracks = info.relatedItems.mapNotNull(::map).toMutableList()
                var next = info.nextPage
                while (next != null && tracks.size < 200) {
                    val more = runCatching { PlaylistInfo.getMoreItems(service, url, next) }.getOrNull() ?: break
                    tracks += more.items.mapNotNull(::map)
                    next = more.nextPage
                }
                Collection(info.name, info.uploaderName.orEmpty(), bigArtwork(info.thumbnails.maxByOrNull { it.height }?.url), tracks)
            }
        }
    }

    // Playback: resolved from MusicService's data source when a media id starts with "sc:".

    private val streamCache = ConcurrentHashMap<String, Pair<String, Long>>()

    /** HLS playlist URL for a SoundCloud track id (without prefix); cached briefly as links expire. */
    fun streamUrl(trackId: String): String {
        streamCache[trackId]?.let { (u, t) -> if (System.currentTimeMillis() - t < 10 * 60_000) return u }
        val info = StreamInfo.getInfo(service, service.streamLHFactory.fromId(trackId).url)
        val streams = info.audioStreams
        val best = streams.filter { it.deliveryMethod == DeliveryMethod.HLS }
            .sortedWith(compareByDescending<org.schabi.newpipe.extractor.stream.AudioStream> { it.format?.name == "M4A" }.thenByDescending { it.averageBitrate })
            .firstOrNull()
            ?: error("No playable SoundCloud stream for $trackId")
        streamCache[trackId] = best.content to System.currentTimeMillis()
        return best.content
    }
}
