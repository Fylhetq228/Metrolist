package com.metrolist.music.geras.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.Album
import com.metrolist.music.db.entities.Artist
import com.metrolist.music.db.entities.Playlist
import com.metrolist.music.db.entities.SearchHistory
import com.metrolist.music.db.entities.Song
import com.metrolist.music.geras.source.ItemKind
import com.metrolist.music.geras.source.MusicSources
import com.metrolist.music.geras.source.SourceItem
import com.metrolist.music.geras.source.SourcePage
import com.metrolist.music.geras.source.SourceSection
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject

enum class SearchTab { ONLINE, LIBRARY }

enum class Correction { LAYOUT, TRANSLIT }

data class OnlineState(
    val loading: Boolean = false,
    val query: String = "",
    /** Query the results are really for, when it differs from what was typed. */
    val corrected: String? = null,
    val correction: Correction? = null,
    val top: SourceItem? = null,
    val sections: List<SourceSection> = emptyList(),
    val continuation: Pair<String, String>? = null,
    val error: String? = null,
)

data class LibraryState(
    val songs: List<Song> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
)

@Serializable
private data class StoredItem(
    val sourceId: String,
    val kind: ItemKind,
    val id: String,
    val title: String,
    val subtitle: String,
    val thumbnail: String?,
)

const val TRANSFER_SOURCE = "transfer"

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class GerasSearchViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) : ViewModel() {
    val query = MutableStateFlow("")
    val tab = MutableStateFlow(SearchTab.ONLINE)
    val kind = MutableStateFlow<ItemKind?>(null)
    val source = MutableStateFlow<String?>(null)
    val downloadedOnly = MutableStateFlow(false)

    private val _online = MutableStateFlow(OnlineState())
    val online: StateFlow<OnlineState> = _online.asStateFlow()

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    val history: StateFlow<List<SearchHistory>> =
        database.searchHistory().map { it.take(12) }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val prefs = context.getSharedPreferences("geras_search", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val _recent = MutableStateFlow(loadRecent())
    val recent: StateFlow<List<SourceItem>> = _recent.asStateFlow()

    val library: StateFlow<LibraryState> =
        combine(query.debounce(150), downloadedOnly) { q, d -> q.trim() to d }
            .flatMapLatest { (q, downloaded) ->
                if (q.isEmpty()) {
                    flowOf(LibraryState())
                } else {
                    combine(
                        database.searchSongsExtended(q, 60),
                        database.searchArtists(q, 10),
                        database.searchAlbums(q, 10),
                        database.searchPlaylists(q, 10),
                    ) { songs, artists, albums, playlists ->
                        LibraryState(
                            songs = if (downloaded) songs.filter { it.song.dateDownload != null || it.song.isDownloaded } else songs,
                            artists = if (downloaded) emptyList() else artists,
                            albums = if (downloaded) emptyList() else albums,
                            playlists = if (downloaded) emptyList() else playlists,
                        )
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.Lazily, LibraryState())

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            combine(query.debounce(280), kind, source) { q, k, s -> Triple(q.trim(), k, s) }
                .collect { (q, k, s) -> runSearch(q, k, s) }
        }
    }

    private fun runSearch(q: String, k: ItemKind?, s: String?) {
        searchJob?.cancel()
        if (q.isEmpty()) {
            _online.value = OnlineState()
            _suggestions.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            _online.value = _online.value.copy(loading = true, query = q, error = null)
            val sources = MusicSources.all.filter { s == null || it.id == s }
            val primary = sources.firstOrNull() ?: MusicSources.all.first()

            // Pick the layout the user meant by asking which version YTM can complete.
            val alt = QueryTools.switchLayout(q)
            val (rawSug, altSug) = coroutineScope {
                val a = async { runCatching { primary.suggestions(q) }.getOrDefault(emptyList()) }
                val b = async { alt?.let { runCatching { primary.suggestions(it) }.getOrDefault(emptyList()) } }
                a.await() to b.await()
            }
            var effective = q
            var correction: Correction? = null
            if (alt != null && altSug != null && QueryTools.prefixScore(alt, altSug) > QueryTools.prefixScore(q, rawSug)) {
                effective = alt
                correction = Correction.LAYOUT
            }
            _suggestions.value = (if (correction != null) altSug else rawSug).orEmpty().take(6)

            var pages = searchAll(sources, effective, k)
            if (pages.isEmptyResult() && correction == null) {
                QueryTools.transliterate(q)?.let { tr ->
                    val alternative = searchAll(sources, tr, k)
                    if (!alternative.isEmptyResult()) {
                        pages = alternative
                        effective = tr
                        correction = Correction.TRANSLIT
                    }
                }
            }
            _online.value = pages.toState(q, effective.takeIf { it != q }, correction)
        }
    }

    private class Pages(val results: List<Pair<String, Result<SourcePage>>>)

    private fun Pages.isEmptyResult() = results.all { (_, r) -> r.getOrNull()?.let { it.top == null && it.sections.all { s -> s.items.isEmpty() } } ?: true }

    private suspend fun searchAll(sources: List<com.metrolist.music.geras.source.MusicSource>, q: String, k: ItemKind?): Pages = coroutineScope {
        Pages(sources.map { src -> async { src.id to withContext(Dispatchers.IO) { runCatching { src.search(q, k).getOrThrow() } } } }.awaitAll())
    }

    private fun Pages.toState(q: String, corrected: String?, correction: Correction?): OnlineState {
        val ok = results.mapNotNull { (id, r) -> r.getOrNull()?.let { id to it } }
        if (ok.isEmpty()) {
            val err = results.firstOrNull()?.second?.exceptionOrNull()
            return OnlineState(query = q, error = errorText(err))
        }
        val top = ok.firstNotNullOfOrNull { it.second.top }
        val sections = ItemKind.entries.mapNotNull { k ->
            val items = ok.flatMap { (_, page) -> page.sections.filter { it.kind == k || it.kind == null }.flatMap { it.items } }.filter { it.kind == k }
            if (items.isEmpty()) null else SourceSection(k, items)
        }
        val cont = ok.firstNotNullOfOrNull { (id, page) -> page.continuation?.let { id to it } }
        return OnlineState(query = q, corrected = corrected, correction = correction, top = top, sections = sections, continuation = cont)
    }

    private fun errorText(e: Throwable?): String =
        when (e) {
            is IOException, is java.net.UnknownHostException, is java.net.SocketTimeoutException ->
                context.getString(com.metrolist.music.R.string.geras_search_network_error)
            null -> context.getString(com.metrolist.music.R.string.geras_search_nothing)
            else -> context.getString(com.metrolist.music.R.string.geras_search_network_error)
        }

    fun loadMore() {
        val state = _online.value
        val (sourceId, token) = state.continuation ?: return
        if (state.loading) return
        viewModelScope.launch {
            _online.value = state.copy(loading = true)
            val page = withContext(Dispatchers.IO) { MusicSources.byId(sourceId)?.searchMore(token)?.getOrNull() }
            val extra = page?.sections?.flatMap { it.items }.orEmpty()
            _online.value = _online.value.copy(
                loading = false,
                sections = _online.value.sections.map { sec -> sec.copy(items = (sec.items + extra.filter { it.kind == sec.kind }).distinctBy { it.id }) },
                continuation = page?.continuation?.let { sourceId to it },
            )
        }
    }

    fun useOriginalQuery() {
        val q = _online.value.query
        viewModelScope.launch {
            _online.value = _online.value.copy(loading = true)
            val pages = searchAll(MusicSources.all.filter { source.value == null || it.id == source.value }, q, kind.value)
            _online.value = pages.toState(q, null, null)
        }
    }

    fun commitHistory(text: String) {
        val q = text.trim()
        if (q.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            database.query {
                history.value.filter { it.query.equals(q, true) }.forEach { delete(it) }
                insert(SearchHistory(query = q))
            }
        }
    }

    fun deleteHistory(item: SearchHistory) {
        viewModelScope.launch(Dispatchers.IO) { database.query { delete(item) } }
    }

    fun rememberOpened(item: SourceItem) {
        val list = (listOf(item) + _recent.value.filterNot { it.id == item.id && it.sourceId == item.sourceId }).take(15)
        _recent.value = list
        val stored = list.map { StoredItem(it.sourceId, it.kind, it.id, it.title, it.subtitle, it.thumbnail) }
        prefs.edit().putString("recent", json.encodeToString(ListSerializer(StoredItem.serializer()), stored)).apply()
    }

    fun clearRecent() {
        _recent.value = emptyList()
        prefs.edit().remove("recent").apply()
    }

    private fun loadRecent(): List<SourceItem> =
        prefs.getString("recent", null)?.let { raw ->
            runCatching { json.decodeFromString(ListSerializer(StoredItem.serializer()), raw) }.getOrNull()
        }.orEmpty().map { SourceItem(it.sourceId, it.kind, it.id, it.title, it.subtitle, it.thumbnail) }

    // Links pasted or found in the clipboard.

    private val _link = MutableStateFlow<SourceItem?>(null)
    val link: StateFlow<SourceItem?> = _link.asStateFlow()
    private var lastLink: String? = null

    fun inspectLink(text: String?) {
        val url = text?.trim()?.takeIf { it.startsWith("http") && it.length < 500 } ?: return
        if (url == lastLink) return
        lastLink = url
        // Spotify playlists and albums can only be transferred, not opened directly.
        com.metrolist.music.geras.transfer.TransferReaders.spotifyKind(url)?.let {
            _link.value = SourceItem(
                sourceId = TRANSFER_SOURCE,
                kind = ItemKind.PLAYLIST,
                id = url,
                title = context.getString(com.metrolist.music.R.string.geras_transfer_link_title),
                subtitle = context.getString(com.metrolist.music.R.string.geras_transfer_link_sub),
                thumbnail = null,
                raw = url,
            )
            return
        }
        viewModelScope.launch {
            _link.value = withContext(Dispatchers.IO) {
                runCatching { MusicSources.forUrl(url)?.resolve(url) ?: SpotifyLinks.resolve(url) }.getOrNull()
            }
        }
    }

    fun dismissLink() {
        _link.value = null
    }

    @Suppress("unused")
    private suspend fun pause(ms: Long) = delay(ms)
}
