package com.metrolist.music.geras.transfer

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.music.R
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.db.entities.PlaylistSongMap
import com.metrolist.music.models.MediaMetadata
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import javax.inject.Inject

data class TransferState(
    val phase: Phase = Phase.INPUT,
    val playlist: ImportedPlaylist? = null,
    val rows: List<TransferRow> = emptyList(),
    val error: String? = null,
    val savedPlaylistId: String? = null,
    val busy: Boolean = false,
) {
    enum class Phase { INPUT, LOADING, MATCHING, DONE }

    val done get() = rows.count { it.status != MatchStatus.PENDING }
    fun count(status: MatchStatus) = rows.count { !it.removed && it.status == status }
}

@HiltViewModel
class TransferViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) : ViewModel() {
    private val _state = MutableStateFlow(TransferState())
    val state: StateFlow<TransferState> = _state.asStateFlow()
    private var job: Job? = null

    fun reset() {
        job?.cancel()
        _state.value = TransferState()
    }

    fun importLink(link: String) {
        job?.cancel()
        _state.value = TransferState(phase = TransferState.Phase.LOADING)
        job = viewModelScope.launch {
            runCatching { TransferReaders.read(link) }
                .onSuccess(::startMatching)
                .onFailure(::fail)
        }
    }

    fun importFile(uri: Uri) {
        job?.cancel()
        _state.value = TransferState(phase = TransferState.Phase.LOADING)
        job = viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "Импорт"
                    val text = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }.orEmpty()
                    TransferReaders.parseFile(name, text)
                }
            }.onSuccess(::startMatching).onFailure(::fail)
        }
    }

    private fun fail(e: Throwable) {
        val msg = when ((e as? TransferException)?.kind) {
            TransferException.Kind.UNSUPPORTED_LINK -> context.getString(R.string.geras_transfer_bad_link)
            TransferException.Kind.EMPTY -> context.getString(R.string.geras_transfer_empty)
            else -> context.getString(R.string.geras_search_network_error)
        }
        _state.value = TransferState(phase = TransferState.Phase.INPUT, error = msg)
    }

    private fun startMatching(playlist: ImportedPlaylist) {
        val rows = playlist.tracks.mapIndexed { i, t -> TransferRow(i, t) }
        _state.value = TransferState(phase = TransferState.Phase.MATCHING, playlist = playlist, rows = rows)
        job = viewModelScope.launch(Dispatchers.IO) {
            val gate = Semaphore(4)
            val networkFailures = java.util.concurrent.atomic.AtomicInteger()
            rows.map { row ->
                async {
                    gate.withPermit {
                        val direct = TransferMatcher.direct(row.source)
                        val updated = if (direct != null) {
                            row.copy(status = MatchStatus.FOUND, match = direct, candidates = listOf(direct))
                        } else {
                            val result = runCatching { TransferMatcher.match(row.source) }
                            if (result.isFailure) networkFailures.incrementAndGet()
                            val list = result.getOrDefault(emptyList())
                            val best = list.firstOrNull()
                            val status = TransferMatcher.statusFor(row.source, best)
                            row.copy(status = status, match = best.takeIf { status != MatchStatus.NOT_FOUND }, candidates = list)
                        }
                        _state.update { s -> s.copy(rows = s.rows.map { if (it.index == row.index) updated else it }) }
                    }
                }
            }.awaitAll()
            _state.update {
                it.copy(
                    phase = TransferState.Phase.DONE,
                    error = if (networkFailures.get() > rows.size / 2) context.getString(R.string.geras_search_network_error) else null,
                )
            }
        }
    }

    fun choose(index: Int, candidate: MatchCandidate) = _state.update { s ->
        s.copy(rows = s.rows.map { if (it.index == index) it.copy(match = candidate, status = MatchStatus.FOUND, removed = false) else it })
    }

    fun setRemoved(index: Int, removed: Boolean) = _state.update { s ->
        s.copy(rows = s.rows.map { if (it.index == index) it.copy(removed = removed) else it })
    }

    /** Accept every doubtful match as is. */
    fun acceptDoubtful() = _state.update { s ->
        s.copy(rows = s.rows.map { if (it.status == MatchStatus.DOUBTFUL && it.match != null) it.copy(status = MatchStatus.FOUND) else it })
    }

    suspend fun searchManual(query: String, row: TransferRow): List<MatchCandidate> =
        runCatching { TransferMatcher.searchCandidates(query) }.getOrDefault(emptyList())
            .map { it.copy(score = TransferMatcher.score(row.source, it)) }

    private fun selectedMatches(includeDoubtful: Boolean) = _state.value.rows
        .filter { !it.removed && it.match != null && (it.status == MatchStatus.FOUND || (includeDoubtful && it.status == MatchStatus.DOUBTFUL)) }
        .mapNotNull { it.match }

    /** Saves found (and, if asked, doubtful) tracks as a local playlist. */
    fun saveLocal(includeDoubtful: Boolean, onDone: (String?) -> Unit) {
        val s = _state.value
        val name = s.playlist?.name ?: return
        val matches = selectedMatches(includeDoubtful)
        if (matches.isEmpty()) return onDone(null)
        _state.update { it.copy(busy = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val playlist = PlaylistEntity(name = name, bookmarkedAt = LocalDateTime.now(), isEditable = true, thumbnailUrl = s.playlist.cover)
            database.transaction {
                insert(playlist)
                matches.forEachIndexed { pos, m ->
                    insert(
                        MediaMetadata(
                            id = m.id,
                            title = m.title,
                            artists = m.artists.mapIndexed { i, a -> MediaMetadata.Artist(m.artistIds.getOrNull(i), a) },
                            duration = m.durationSec ?: -1,
                            thumbnailUrl = m.thumbnail,
                            album = m.albumId?.let { MediaMetadata.Album(it, m.album.orEmpty()) },
                        ),
                    )
                    insert(PlaylistSongMap(songId = m.id, playlistId = playlist.id, position = pos))
                }
            }
            _state.update { it.copy(busy = false, savedPlaylistId = playlist.id) }
            withContext(Dispatchers.Main) { onDone(playlist.id) }
        }
    }

    /** Creates a playlist in the signed-in YouTube Music account. SoundCloud tracks are skipped. */
    fun saveToYouTube(includeDoubtful: Boolean, onDone: (Int, Int) -> Unit) {
        val s = _state.value
        val name = s.playlist?.name ?: return
        val ids = selectedMatches(includeDoubtful).map { it.id }.filterNot { it.startsWith("sc:") }
        _state.update { it.copy(busy = true) }
        viewModelScope.launch(Dispatchers.IO) {
            var added = 0
            runCatching {
                val playlistId = YouTube.createPlaylist(name)
                ids.forEach { id -> if (YouTube.addToPlaylist(playlistId, id).isSuccess) added++ }
            }
            _state.update { it.copy(busy = false) }
            withContext(Dispatchers.Main) { onDone(added, ids.size) }
        }
    }

    fun exportText(json: Boolean): String {
        val s = _state.value
        val name = s.playlist?.name.orEmpty()
        return if (json) TransferExport.json(name, s.rows) else TransferExport.csv(name, s.rows)
    }

    fun writeExport(uri: Uri, json: Boolean, onDone: (Boolean) -> Unit) {
        val text = exportText(json)
        viewModelScope.launch(Dispatchers.IO) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } != null
            }.getOrDefault(false)
            withContext(Dispatchers.Main) { onDone(ok) }
        }
    }
}
