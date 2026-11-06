package com.metrolist.music.geras.source

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.utils.YouTubeUrlParser
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.queues.YouTubeQueue

object YouTubeMusicSource : MusicSource {
    override val id = "ytm"
    override val name = "YouTube Music"
    override val badge = "YT"
    override val badgeColor = 0xFFE5484DL

    private fun filterFor(kind: ItemKind) = when (kind) {
        ItemKind.TRACK -> YouTube.SearchFilter.FILTER_SONG
        ItemKind.ALBUM -> YouTube.SearchFilter.FILTER_ALBUM
        ItemKind.ARTIST -> YouTube.SearchFilter.FILTER_ARTIST
        ItemKind.PLAYLIST -> YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST
    }

    fun map(item: YTItem): SourceItem? = when (item) {
        is SongItem -> SourceItem(
            sourceId = id,
            kind = ItemKind.TRACK,
            id = item.id,
            title = item.title,
            subtitle = item.artists.joinToString { it.name },
            thumbnail = item.thumbnail,
            durationSec = item.duration,
            explicit = item.explicit,
            raw = item,
        )
        is AlbumItem -> SourceItem(id, ItemKind.ALBUM, item.browseId, item.title, listOfNotNull(item.artists?.joinToString { it.name }, item.year?.toString()).joinToString(" · "), item.thumbnail, explicit = item.explicit, raw = item)
        is ArtistItem -> SourceItem(id, ItemKind.ARTIST, item.id, item.title, "", item.thumbnail, raw = item)
        is PlaylistItem -> SourceItem(id, ItemKind.PLAYLIST, item.id, item.title, listOfNotNull(item.author?.name, item.songCountText).joinToString(" · "), item.thumbnail, raw = item)
        else -> null
    }

    override suspend fun search(query: String, kind: ItemKind?): Result<SourcePage> =
        if (kind == null) {
            YouTube.searchSummary(query).map { page ->
                val sections = page.summaries.map { summary -> summary.items.mapNotNull(::map) }
                val top = sections.firstOrNull()?.firstOrNull()
                val all = sections.flatten().drop(if (top != null) 1 else 0).distinctBy { it.kind to it.id }
                SourcePage(
                    top = top,
                    sections = ItemKind.entries.map { k -> SourceSection(k, all.filter { it.kind == k }) }.filter { it.items.isNotEmpty() },
                )
            }
        } else {
            YouTube.search(query, filterFor(kind)).map { result ->
                SourcePage(null, listOf(SourceSection(kind, result.items.mapNotNull(::map))), result.continuation)
            }
        }

    override suspend fun searchMore(continuation: String): Result<SourcePage> =
        YouTube.searchContinuation(continuation).map { result ->
            SourcePage(null, listOf(SourceSection(null, result.items.mapNotNull(::map))), result.continuation)
        }

    override suspend fun suggestions(query: String): List<String> =
        YouTube.searchSuggestions(query).getOrNull()?.queries.orEmpty()

    override fun canOpen(url: String): Boolean = YouTubeUrlParser.parse(url) != null

    override suspend fun resolve(url: String): SourceItem? {
        val parsed = YouTubeUrlParser.parse(url) ?: return null
        val item: YTItem? = when (parsed) {
            is YouTubeUrlParser.ParsedUrl.Video -> YouTube.next(WatchEndpoint(videoId = parsed.id)).getOrNull()?.items?.firstOrNull()
            is YouTubeUrlParser.ParsedUrl.Playlist -> YouTube.playlist(parsed.id).getOrNull()?.playlist
            is YouTubeUrlParser.ParsedUrl.Album -> YouTube.album("MPREb_${parsed.id}").getOrNull()?.album
                ?: YouTube.playlist(parsed.id).getOrNull()?.playlist
            is YouTubeUrlParser.ParsedUrl.Artist -> YouTube.artist(parsed.id).getOrNull()?.artist
        }
        return item?.let(::map)
    }

    override fun open(item: SourceItem, context: OpenContext) {
        when (item.kind) {
            ItemKind.TRACK -> {
                val song = item.raw as? SongItem
                val metadata = song?.toMediaMetadata()
                if (context.playerConnection.mediaMetadata.value?.id == item.id) {
                    context.playerConnection.togglePlayPause()
                } else {
                    context.playerConnection.playQueue(YouTubeQueue(WatchEndpoint(videoId = item.id), metadata))
                }
            }
            ItemKind.ALBUM -> context.navController.navigate("album/${item.id}")
            ItemKind.ARTIST -> context.navController.navigate("artist/${item.id}")
            ItemKind.PLAYLIST -> context.navController.navigate("online_playlist/${item.id}")
        }
    }
}
