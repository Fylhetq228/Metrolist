package com.metrolist.music.geras.source.soundcloud

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.source.CompositeMediaSource
import androidx.media3.exoplayer.source.MediaPeriod
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.Allocator
import com.metrolist.music.extensions.metadata
import com.metrolist.music.geras.source.ItemKind
import com.metrolist.music.geras.transfer.MatchCandidate
import com.metrolist.music.geras.transfer.SourceTrack
import com.metrolist.music.geras.transfer.TransferMatcher
import com.metrolist.music.models.MediaMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * "SoundCloud first": YouTube tracks are played from SoundCloud when the same recording is
 * there in full. Matching is strict (title, artist, duration within 4 s, no 30-second Go+
 * previews); anything else silently stays on YouTube. Matches are cached on disk and the next
 * tracks in the queue are matched ahead of time, so playback starts without waiting.
 */
object GerasScFirst {
    private const val NONE = ""
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var prefs: SharedPreferences? = null
    private val matches = ConcurrentHashMap<String, String>()
    private val inFlight = ConcurrentHashMap<String, Deferred<String?>>()
    /** YouTube ids currently playing from SoundCloud, so an error can fall back to YouTube. */
    private val substituted = ConcurrentHashMap.newKeySet<String>()

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences("geras_sc_first", Context.MODE_PRIVATE)
        // Key and connection ready before the first tap.
        if (enabled) scope.launch { SoundCloudFast.warmUp() }
    }

    /** Set by the player service: true when the YouTube audio of this id is already on disk. */
    @Volatile var hasLocalCopy: (String) -> Boolean = { false }

    var enabled: Boolean
        get() = prefs?.getBoolean("enabled", true) ?: true
        set(v) {
            prefs?.edit()?.putBoolean("enabled", v)?.apply()
        }

    private fun cached(ytId: String): String? = matches[ytId] ?: prefs?.getString("m2_$ytId", null)?.also { matches[ytId] = it }

    private fun remember(ytId: String, scId: String) {
        matches[ytId] = scId
        prefs?.edit()?.putString("m2_$ytId", scId)?.apply()
    }

    /** Called on a playback error: that track plays from YouTube for the rest of this session. */
    fun onError(ytId: String?) {
        // Only until the app restarts: the failure may be an expired link or a bad connection,
        // not a reason to give up on the SoundCloud copy for good.
        if (ytId != null && substituted.remove(ytId)) matches[ytId] = NONE
    }

    fun isEligible(id: String) = !SoundCloudSource.isScId(id) && !id.startsWith("http") && !id.startsWith("content") && !id.startsWith("file")

    /** Starts matching in the background (used for the next tracks in the queue). */
    fun prefetch(meta: MediaMetadata?) {
        if (meta == null || !enabled || !isEligible(meta.id) || meta.isEpisode || meta.uploadEntityId != null) return
        cached(meta.id)?.let { id ->
            // Matched before: resolve the stream link now, so the track starts without a lookup.
            if (id.isNotEmpty()) scope.launch { runCatching { SoundCloudSource.streamUrl(id) } }
            return
        }
        match(meta)
    }

    private fun match(meta: MediaMetadata): Deferred<String?> = inFlight.getOrPut(meta.id) {
        scope.async {
            try {
                val found = runCatching { findOnSoundCloud(meta) }
                // Only a clean answer is remembered; after an error the next play asks again.
                found.onSuccess { remember(meta.id, it ?: NONE) }
                found.getOrNull()
            } finally {
                inFlight.remove(meta.id)
            }
        }
    }

    /** How long a never-seen track waits for SoundCloud before starting from YouTube. */
    private const val FIRST_PLAY_BUDGET_MS = 1500L

    /** SoundCloud track id for [meta], waiting at most [budgetMs]; null means "play from YouTube". */
    fun resolveBlocking(meta: MediaMetadata, budgetMs: Long): String? {
        cached(meta.id)?.let { return it.ifEmpty { null } }
        val job = match(meta)
        return runBlocking { withTimeoutOrNull(budgetMs) { job.await() } }
    }

    /**
     * The SoundCloud id of [meta]'s full, playable copy, or null when there is none. Network
     * errors are thrown, not turned into "none": a track must not be written off for good
     * because the connection hiccuped once.
     */
    private fun findOnSoundCloud(meta: MediaMetadata): String? {
        val duration = meta.duration.takeIf { it > 0 } ?: return null
        val artist = meta.artists.firstOrNull()?.name.orEmpty()
        val src = SourceTrack(meta.title, meta.artists.map { it.name }, duration)
        val best = SoundCloudFast.search("${meta.title} $artist".trim())
            // Playable in full and not DRM-only (Go+ tracks give a preview or nothing).
            .filter { it.transcoding != null && abs(it.durationSec - duration) <= MAX_DURATION_DIFF_S }
            .map { it to TransferMatcher.score(src, MatchCandidate(it.id, it.title, listOf(it.uploader), it.durationSec, it.artwork)) }
            .filter { it.second >= 0.8f }
            .maxWithOrNull(compareBy<Pair<SoundCloudFast.Track, Float>> { it.second }.thenBy { -abs(it.first.durationSec - duration) })
            ?.first ?: return null
        // Resolve the stream right away: proves it plays and the player finds it ready.
        SoundCloudFast.streamUrl(best.id) ?: return null
        return best.id
    }

    private const val MAX_DURATION_DIFF_S = 3

    /** Wraps the app's media source factory: each item decides at prepare time where it plays from. */
    @UnstableApi
    class Factory(private val delegate: MediaSource.Factory) : MediaSource.Factory by delegate {
        override fun createMediaSource(mediaItem: MediaItem): MediaSource {
            val meta = mediaItem.metadata
            // A downloaded or fully cached YouTube copy plays instantly and offline: keep it.
            if (!enabled || meta == null || !isEligible(mediaItem.mediaId) || meta.isEpisode || meta.uploadEntityId != null ||
                runCatching { hasLocalCopy(mediaItem.mediaId) }.getOrDefault(false)
            ) {
                return delegate.createMediaSource(mediaItem)
            }
            return DeferredSource(mediaItem, meta, delegate)
        }
    }

    @UnstableApi
    private class DeferredSource(
        private val item: MediaItem,
        private val meta: MediaMetadata,
        private val delegate: MediaSource.Factory,
    ) : CompositeMediaSource<Int>() {
        @Volatile private var released = false
        private var child: MediaSource? = null

        override fun getMediaItem(): MediaItem = item

        override fun prepareSourceInternal(mediaTransferListener: TransferListener?) {
            super.prepareSourceInternal(mediaTransferListener)
            released = false
            val handler: Handler = Util.createHandlerForCurrentLooper()
            Thread {
                // Already matched: instant. Otherwise give SoundCloud a moment, then use YouTube.
                val scId = runCatching { resolveBlocking(meta, FIRST_PLAY_BUDGET_MS) }.getOrNull()
                val source = if (scId != null) {
                    substituted += item.mediaId
                    delegate.createMediaSource(
                        item.buildUpon()
                            .setUri(SoundCloudSource.ID_PREFIX + scId)
                            .setCustomCacheKey(SoundCloudSource.ID_PREFIX + scId)
                            .setMimeType(MimeTypes.APPLICATION_M3U8)
                            .build(),
                    )
                } else {
                    substituted -= item.mediaId
                    delegate.createMediaSource(item)
                }
                handler.post {
                    if (released) return@post
                    child = source
                    prepareChildSource(0, source)
                }
            }.apply { name = "geras-sc-first" }.start()
        }

        override fun onChildSourceInfoRefreshed(childSourceId: Int, mediaSource: MediaSource, newTimeline: Timeline) {
            refreshSourceInfo(newTimeline)
        }

        override fun createPeriod(id: MediaSource.MediaPeriodId, allocator: Allocator, startPositionUs: Long): MediaPeriod =
            child!!.createPeriod(id, allocator, startPositionUs)

        override fun releasePeriod(mediaPeriod: MediaPeriod) {
            child?.releasePeriod(mediaPeriod)
        }

        override fun releaseSourceInternal() {
            released = true
            child = null
            super.releaseSourceInternal()
        }
    }
}
