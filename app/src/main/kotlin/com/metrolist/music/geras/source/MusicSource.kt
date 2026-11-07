package com.metrolist.music.geras.source

import androidx.compose.runtime.Immutable
import androidx.navigation.NavController
import com.metrolist.music.playback.PlayerConnection

enum class ItemKind { TRACK, ALBUM, ARTIST, PLAYLIST }

/** One search result or link target, independent of where it came from. */
@Immutable
data class SourceItem(
    val sourceId: String,
    val kind: ItemKind,
    val id: String,
    val title: String,
    val subtitle: String,
    val thumbnail: String?,
    val durationSec: Int? = null,
    val explicit: Boolean = false,
    /** Source-specific object (e.g. YTItem) used when opening the item. */
    val raw: Any? = null,
)

data class SourceSection(val kind: ItemKind?, val items: List<SourceItem>)

data class SourcePage(
    /** Best match shown as a large card on the "All" tab. */
    val top: SourceItem?,
    val sections: List<SourceSection>,
    val continuation: String? = null,
)

class OpenContext(
    val navController: NavController,
    val playerConnection: PlayerConnection,
)

/**
 * A place music comes from. Adding a source is one class implementing this and one line in
 * [MusicSources]; search, links and badges pick it up automatically.
 */
interface MusicSource {
    val id: String
    val name: String
    /** Two or three letters for the badge on results. */
    val badge: String
    val badgeColor: Long

    /** [kind] null means mixed results (top result plus sections). */
    suspend fun search(query: String, kind: ItemKind?): Result<SourcePage>

    suspend fun searchMore(continuation: String): Result<SourcePage> = Result.failure(UnsupportedOperationException())

    suspend fun suggestions(query: String): List<String> = emptyList()

    fun canOpen(url: String): Boolean = false

    suspend fun resolve(url: String): SourceItem? = null

    fun open(item: SourceItem, context: OpenContext)
}

object MusicSources {
    val all: List<MusicSource> by lazy { listOf(YouTubeMusicSource, com.metrolist.music.geras.source.soundcloud.SoundCloudSource) }

    fun byId(id: String): MusicSource? = all.firstOrNull { it.id == id }

    fun forUrl(url: String): MusicSource? = all.firstOrNull { it.canOpen(url) }
}
