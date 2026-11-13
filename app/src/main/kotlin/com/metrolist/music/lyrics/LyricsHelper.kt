/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.lyrics

import android.content.Context
import android.util.LruCache
import com.metrolist.music.constants.LyricsProviderOrderKey
import com.metrolist.music.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.utils.NetworkConnectivityObserver
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.reportException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject

private const val MAX_LYRICS_FETCH_MS = 20000L
private const val PER_PROVIDER_TIMEOUT_MS = 12000L
private const val PLAIN_GRACE_MS = 3000L
private const val SYNCED_GRACE_MS = 1500L
private const val PROVIDER_NONE = ""

class LyricsHelper
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val networkConnectivity: NetworkConnectivityObserver,
) {
    val preferred =
        context.dataStore.data
            .map { preferences ->
                resolveLyricsProviders(preferences)
            }.distinctUntilChanged()

    private val cache = LruCache<String, List<LyricsResult>>(MAX_CACHE_SIZE)
    private var currentLyricsJob: Job? = null

    suspend fun getLyrics(mediaMetadata: MediaMetadata): LyricsWithProvider {
        currentLyricsJob?.cancel()

        val cached = cache.get(mediaMetadata.id)?.firstOrNull()
            ?.takeIf {
                com.metrolist.music.geras.lyrics.GerasLyricsVerify.accept(
                    mediaMetadata.id, it.lyrics, it.providerName, mediaMetadata.title, mediaMetadata.artists.map { a -> a.name }, mediaMetadata.duration,
                )
            }
        if (cached != null) {
            return LyricsWithProvider(cached.lyrics, cached.providerName)
        }

        val orderedProviders = context.dataStore.data
            .map { preferences -> resolveLyricsProviders(preferences) }
            .first()

        val isNetworkAvailable = try {
            networkConnectivity.isCurrentlyConnected()
        } catch (e: Exception) {
            true
        }

        if (!isNetworkAvailable) {
            return LyricsWithProvider(LYRICS_NOT_FOUND, PROVIDER_NONE)
        }

        // Geras: all providers are asked at once (not one after another, where a slow or
        // blocked provider ate the whole time budget before YouTube was even tried), each with
        // a few title/artist variants. Results are then taken in the user's priority order;
        // synced lyrics win over plain ones that arrived first.
        val result = withTimeoutOrNull(MAX_LYRICS_FETCH_MS) {
            coroutineScope {
                val enabledProviders = orderedProviders.filter { it.isEnabled(context) }
                val queries = com.metrolist.music.geras.lyrics.GerasLyricsQuery.variants(
                    mediaMetadata.title, mediaMetadata.artists.joinToString { it.name },
                )
                val jobs = enabledProviders.map { provider ->
                    async {
                        val byId = provider.name.startsWith("YouTube")
                        withTimeoutOrNull(PER_PROVIDER_TIMEOUT_MS) {
                            for (q in if (byId) queries.take(1) else queries) {
                                val text = try {
                                    provider.getLyrics(context, mediaMetadata.id, q.title, q.artist, mediaMetadata.duration, mediaMetadata.album?.title).getOrNull()
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    Timber.tag("LyricsHelper").w("${provider.name} threw: ${e.message}")
                                    null
                                }
                                if (text.isNullOrBlank()) continue
                                val ok = com.metrolist.music.geras.lyrics.GerasLyricsVerify.accept(
                                    mediaMetadata.id, text, provider.name, q.title, q.artist.split(", ").filter { it.isNotBlank() }, mediaMetadata.duration,
                                )
                                if (ok) return@withTimeoutOrNull LyricsUtils.filterLyricsCreditLines(text)
                                Timber.tag("LyricsHelper").w("${provider.name} returned lyrics of another song, skipping")
                            }
                            null
                        }
                    }
                }
                // Take results as they arrive and keep the best kind: word-by-word timing beats
                // line timing beats plain text; within a kind the user's provider order decides.
                // Word-timed lyrics end the search at once; anything less waits a little for
                // something better.
                val done = kotlinx.coroutines.channels.Channel<Int>(enabledProviders.size)
                jobs.forEachIndexed { i, job -> job.invokeOnCompletion { done.trySend(i) } }
                var best: LyricsWithProvider? = null
                var bestTier = 0
                var bestIndex = Int.MAX_VALUE
                var deadline = Long.MAX_VALUE
                var left = jobs.size
                while (left > 0) {
                    val wait = deadline - System.currentTimeMillis()
                    if (wait <= 0) break
                    val i = (if (deadline == Long.MAX_VALUE) done.receive() else withTimeoutOrNull(wait) { done.receive() }) ?: break
                    left--
                    val text = runCatching { jobs[i].await() }.getOrNull() ?: continue
                    val tier = com.metrolist.music.geras.lyrics.GerasLyricsQuery.tier(text)
                    if (tier > bestTier || (tier == bestTier && i < bestIndex)) {
                        best = LyricsWithProvider(text, enabledProviders[i].name)
                        bestTier = tier
                        bestIndex = i
                    }
                    if (bestTier == 3) break
                    val grace = if (bestTier == 2) SYNCED_GRACE_MS else PLAIN_GRACE_MS
                    deadline = minOf(deadline, System.currentTimeMillis() + grace)
                }
                jobs.forEach { it.cancel() }
                best?.also { Timber.tag("LyricsHelper").i("Lyrics from ${it.provider}, tier $bestTier") }
                    ?: LyricsWithProvider(LYRICS_NOT_FOUND, PROVIDER_NONE)
            }
        }

        return result ?: LyricsWithProvider(LYRICS_NOT_FOUND, PROVIDER_NONE)
    }

    suspend fun getAllLyrics(
        mediaId: String,
        songTitle: String,
        songArtists: String,
        duration: Int,
        album: String? = null,
        callback: (LyricsResult) -> Unit,
    ) {
        currentLyricsJob?.cancel()

        val cacheKey = "$songArtists-$songTitle".replace(" ", "")
        cache.get(cacheKey)?.let { results ->
            results.forEach { callback(it) }
            return
        }

        val isNetworkAvailable = try {
            networkConnectivity.isCurrentlyConnected()
        } catch (e: Exception) {
            true
        }

        if (!isNetworkAvailable) return

        val allResult = mutableListOf<LyricsResult>()
        currentLyricsJob = CoroutineScope(SupervisorJob()).launch {
            val cleanedTitle = LyricsUtils.cleanTitleForSearch(songTitle)
            val allProviders = context.dataStore.data
                .map { preferences -> resolveLyricsProviders(preferences) }
                .first()
            val enabledProviders = allProviders.filter { it.isEnabled(context) }

            val otherProviders = enabledProviders.filter { it.name != "LyricsPlus" }
            val lyricsPlusProvider = enabledProviders.find { it.name == "LyricsPlus" }

            val callbackMutex = Any()

            val otherJobs = otherProviders.map { provider ->
                launch {
                    try {
                        provider.getAllLyrics(context, mediaId, cleanedTitle, songArtists, duration, album) { lyrics ->
                            val filteredLyrics = LyricsUtils.filterLyricsCreditLines(lyrics)
                            val result = LyricsResult(provider.name, filteredLyrics)
                            synchronized(callbackMutex) {
                                allResult += result
                                callback(result)
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        reportException(e)
                    }
                }
            }
            otherJobs.forEach { it.join() }

            val otherLyricsCount = allResult.count { it.providerName != "LyricsPlus" }
            if (lyricsPlusProvider != null && otherLyricsCount <= 2) {
                launch {
                    try {
                        lyricsPlusProvider.getAllLyrics(context, mediaId, cleanedTitle, songArtists, duration, album) { lyrics ->
                            val filteredLyrics = LyricsUtils.filterLyricsCreditLines(lyrics)
                            val result = LyricsResult(lyricsPlusProvider.name, filteredLyrics)
                            synchronized(callbackMutex) {
                                allResult += result
                                callback(result)
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        reportException(e)
                    }
                }.join()
            }

            cache.put(cacheKey, allResult)
        }

        currentLyricsJob?.join()
    }

    private fun resolveLyricsProviders(preferences: androidx.datastore.preferences.core.Preferences): List<LyricsProvider> {
        val providerOrder = preferences[LyricsProviderOrderKey].orEmpty()
        if (providerOrder.isNotBlank()) {
            return LyricsProviderRegistry.getOrderedProviders(providerOrder)
        }

        return LyricsProviderRegistry.getDefaultProviderOrder()
            .mapNotNull { LyricsProviderRegistry.getProviderByName(it) }
    }

    companion object {
        private const val MAX_CACHE_SIZE = 3
    }
}

data class LyricsResult(
    val providerName: String,
    val lyrics: String,
)

data class LyricsWithProvider(
    val lyrics: String,
    val provider: String,
)
