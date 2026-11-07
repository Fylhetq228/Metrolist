package com.metrolist.music.geras.source.soundcloud

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.IOException

/**
 * Hook for MusicService's ResolvingDataSource. Returns a resolved spec for Geras-owned
 * media, or null to let Metrolist's YouTube resolver handle it.
 */
@UnstableApi
object GerasStreamResolver {
    fun resolve(dataSpec: DataSpec): DataSpec? {
        val uri = dataSpec.uri.toString()
        if (SoundCloudSource.isScId(uri)) {
            val trackId = uri.removePrefix(SoundCloudSource.ID_PREFIX)
            val url = try {
                runBlocking(Dispatchers.IO) { SoundCloudSource.streamUrl(trackId) }
            } catch (e: Exception) {
                throw IOException("SoundCloud is unavailable. Check the connection or turn on a VPN.", e)
            }
            // No cache key: the playlist URL is signed and expires, so it must not be cached by id.
            return dataSpec.buildUpon().setUri(Uri.parse(url)).setKey(null).build()
        }
        // HLS segments (absolute URLs from a SoundCloud playlist) pass through untouched.
        if (dataSpec.key == null && (uri.startsWith("https://") || uri.startsWith("http://"))) return dataSpec
        return null
    }
}
