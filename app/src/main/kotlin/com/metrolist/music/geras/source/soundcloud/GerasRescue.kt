package com.metrolist.music.geras.source.soundcloud

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.media3.common.Player
import com.metrolist.music.R
import com.metrolist.music.extensions.metadata
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.geras.source.ItemKind
import com.metrolist.music.geras.source.soundcloud.SoundCloudSource.toMetadata
import com.metrolist.music.geras.transfer.SourceTrack
import com.metrolist.music.geras.transfer.TransferMatcher
import com.metrolist.music.geras.transfer.MatchCandidate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Collections

/**
 * When YouTube refuses a track (some VPN exits are blocked or flagged as bots), play the same
 * song from SoundCloud instead of skipping it. Matching uses the transfer matcher, so only a
 * confident match (title, artist, duration) replaces the track.
 */
object GerasRescue {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val tried = Collections.synchronizedSet(HashSet<String>())
    private val main = Handler(Looper.getMainLooper())

    /** Returns true if a rescue was started; [onGiveUp] runs if no match is found. */
    fun tryRescue(context: Context, player: Player, mediaId: String, onGiveUp: () -> Unit): Boolean {
        if (SoundCloudSource.isScId(mediaId) || !tried.add(mediaId)) return false
        val meta = player.currentMediaItem?.metadata ?: return false
        scope.launch {
            val src = SourceTrack(meta.title, meta.artists.map { it.name }, meta.duration.takeIf { it > 0 })
            val match = withContext(Dispatchers.IO) {
                withTimeoutOrNull(12_000) {
                    val page = SoundCloudSource.search("${meta.title} ${meta.artists.firstOrNull()?.name.orEmpty()}".trim(), ItemKind.TRACK).getOrNull()
                    page?.sections?.flatMap { it.items }?.take(10)
                        ?.map { it to TransferMatcher.score(src, MatchCandidate(it.id, it.title, listOf(it.subtitle), it.durationSec, it.thumbnail)) }
                        ?.filter { (it, score) ->
                            score >= 0.78f && (src.durationSec == null || it.durationSec == null || kotlin.math.abs(src.durationSec - it.durationSec) <= 10)
                        }
                        ?.maxByOrNull { it.second }?.first
                }
            }
            val index = (0 until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).mediaId == mediaId }
            if (match == null || index == null) {
                onGiveUp()
                return@launch
            }
            // Keep the YouTube metadata the user knows, only swap the audio source.
            val item = match.toMetadata().copy(title = meta.title, artists = meta.artists, album = meta.album, thumbnailUrl = meta.thumbnailUrl ?: match.thumbnail).toMediaItem()
            player.replaceMediaItem(index, item)
            if (player.currentMediaItemIndex != index) player.seekTo(index, 0)
            player.prepare()
            player.play()
            main.post { Toast.makeText(context, R.string.geras_rescue_toast, Toast.LENGTH_LONG).show() }
        }
        return true
    }
}
