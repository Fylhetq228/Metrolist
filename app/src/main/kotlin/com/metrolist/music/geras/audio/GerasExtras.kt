package com.metrolist.music.geras.audio

import android.os.Handler
import android.os.Looper
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

/**
 * A-B repeat. First tap marks A, second marks B (playback then loops A..B), third clears.
 * Runs on the service's player so the loop keeps going with the screen off.
 */
object GerasLoop {
    data class State(val a: Long? = null, val b: Long? = null) {
        val active get() = a != null && b != null
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var player: WeakReference<Player>? = null
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            val p = player?.get() ?: return
            val s = _state.value
            if (s.a != null && s.b != null) {
                if (p.currentPosition >= s.b || p.currentPosition < s.a - 1500) p.seekTo(s.a)
                handler.postDelayed(this, 40)
            }
        }
    }

    fun attach(p: Player) {
        player = WeakReference(p)
        p.addListener(
            object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) clear()
                }
            },
        )
    }

    /** Cycles A → B → off at [position]. */
    fun tap(position: Long) {
        val s = _state.value
        _state.value = when {
            s.a == null -> State(a = position)
            s.b == null -> if (position > s.a + 500) State(s.a, position) else State(a = position)
            else -> State()
        }
        handler.removeCallbacks(tick)
        if (_state.value.active) {
            player?.get()?.seekTo(_state.value.a!!)
            handler.post(tick)
        }
    }

    fun clear() {
        handler.removeCallbacks(tick)
        _state.value = State()
    }
}

val GerasSmartShuffleKey = booleanPreferencesKey("geras_smart_shuffle")

/**
 * Smart shuffle: still random, but never the same artist twice in a row and, where the queue
 * allows it, not within the last three tracks either.
 */
object GerasShuffle {
    fun spread(order: IntArray, artistOf: (Int) -> String?): IntArray {
        if (order.size < 4) return order
        val result = ArrayList<Int>(order.size)
        result += order[0]
        val pool = order.drop(1).toMutableList()
        val recent = ArrayDeque<String>()
        artistOf(order[0])?.let { recent.addLast(it) }
        while (pool.isNotEmpty()) {
            val window = minOf(pool.size, 60)
            var pick = -1
            // Prefer an artist not heard in the last three tracks, then one that just differs.
            for (gap in intArrayOf(3, 1)) {
                val blocked = recent.takeLast(gap).toSet()
                for (i in 0 until window) {
                    val a = artistOf(pool[i])
                    if (a == null || a !in blocked) {
                        pick = i
                        break
                    }
                }
                if (pick >= 0) break
            }
            if (pick < 0) pick = 0
            val idx = pool.removeAt(pick)
            result += idx
            artistOf(idx)?.let {
                recent.addLast(it)
                if (recent.size > 3) recent.removeFirst()
            }
        }
        return result.toIntArray()
    }

    fun artistKey(item: MediaItem): String? =
        item.mediaMetadata.artist?.toString()?.lowercase()?.substringBefore(',')?.substringBefore(" feat")?.trim()?.takeIf { it.isNotEmpty() }
}
