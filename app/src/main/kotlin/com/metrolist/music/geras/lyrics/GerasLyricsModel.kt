package com.metrolist.music.geras.lyrics

import androidx.compose.runtime.Immutable
import com.metrolist.music.lyrics.LyricsEntry

@Immutable
data class KWord(
    val start: Long,
    val end: Long,
    val charStart: Int,
    val charEnd: Int,
)

@Immutable
data class KLine(
    val entry: LyricsEntry,
    val text: String,
    val start: Long,
    val end: Long,
    val words: List<KWord>,
    /** Char ranges sung as backing vocals / ad-libs (text in parentheses). */
    val backing: List<IntRange>,
    val isBackground: Boolean,
    val agent: String?,
    val interpolated: Boolean,
)

sealed interface KItem {
    val start: Long

    data class Line(val line: KLine) : KItem {
        override val start: Long get() = line.start
    }

    data class Gap(override val start: Long, val end: Long) : KItem
}

private const val GAP_MIN_MS = 4_000L
private const val MS_PER_CHAR = 95L
private const val MIN_LINE_MS = 1_200L

/**
 * Turns parsed lyrics into karaoke lines: real word timings when the provider has them,
 * otherwise words spread over the line proportionally to their length. Long pauses become
 * [KItem.Gap] so the UI can show the "..." indicator.
 */
/**
 * True when the parsed lines really carry timing. Checked on the parsed result, not on the raw
 * text, so TTML/XML lyrics (word-synced, no LRC brackets) are recognised too.
 */
fun linesAreTimed(entries: List<LyricsEntry>): Boolean {
    val times = entries.filter { it.text.isNotBlank() }.map { it.time }
    if (times.size < 2) return false
    val distinct = times.distinct().size
    return distinct >= minOf(3, times.size) && times.zipWithNext().count { (a, b) -> b >= a } >= (times.size - 1) * 0.8
}

/**
 * Plain (untimed) lyrics: spread the lines over the track so the text still flows with the
 * music. Approximate by nature, so the screen marks it as such and does not seek on tap.
 */
fun estimateTiming(entries: List<LyricsEntry>, durationMs: Long): List<LyricsEntry> {
    val lines = entries.filter { it.text.isNotBlank() }
    if (lines.isEmpty() || durationMs < 30_000) return entries
    val start = (durationMs * 0.07).toLong()
    val span = (durationMs * 0.86).toLong()
    val weights = lines.map { it.text.length + 12f }
    val total = weights.sum()
    var acc = 0f
    return lines.mapIndexed { i, e ->
        val t = start + (span * (acc / total)).toLong()
        acc += weights[i]
        e.copy(time = t, words = null)
    }
}

fun buildKaraoke(entries: List<LyricsEntry>, synced: Boolean): List<KItem> {
    val source = entries.filter { (it.text.isNotBlank() && !isInstrumentalMarker(it.text)) || !it.words.isNullOrEmpty() }
    if (source.isEmpty()) return emptyList()
    // Blank timestamps and "♪"/"(instrumental)" lines mark where singing stops: they end the
    // previous line so the break shows as a gap instead of a line still being "sung".
    val boundaries = entries.map { it.time }.distinct().sorted()
    if (!synced) {
        return source.map { e ->
            KItem.Line(KLine(e, e.text, Long.MAX_VALUE, Long.MAX_VALUE, emptyList(), parenRanges(e.text), e.isBackground, e.agent, true))
        }
    }

    val lines = source.mapIndexed { i, e ->
        val nextLine = source.drop(i + 1).firstOrNull { !it.isBackground || !e.isBackground }?.time
        val nextMark = boundaries.firstOrNull { it > e.time }
        val nextStart = listOfNotNull(nextLine, nextMark).minOrNull()
        val words = e.words?.filter { it.text.isNotEmpty() }?.takeIf { ws ->
            // Some providers send word lists with zero or nonsense times: interpolate those instead.
            val first = (ws.first().startTime * 1000).toLong()
            val last = (ws.last().endTime * 1000).toLong()
            last > first && first >= e.time - 3_000 && (nextStart == null || first <= nextStart + 3_000) &&
                ws.zipWithNext().all { (a, b) -> b.startTime >= a.startTime - 0.05 }
        }
        if (!words.isNullOrEmpty()) {
            val sb = StringBuilder()
            val kw = words.mapIndexed { wi, w ->
                val cs = sb.length
                sb.append(w.text)
                val ce = sb.length
                if (w.hasTrailingSpace && wi != words.lastIndex) sb.append(' ')
                KWord((w.startTime * 1000).toLong(), (w.endTime * 1000).toLong().coerceAtLeast((w.startTime * 1000).toLong() + 1), cs, ce)
            }
            val text = sb.toString()
            KLine(e, text, e.time, kw.last().end, kw, parenRanges(text), e.isBackground, e.agent, false)
        } else {
            val text = e.text.trim()
            val gap = (nextStart ?: (e.time + 6_000L)) - e.time
            val estimate = (text.length * MS_PER_CHAR).coerceAtLeast(MIN_LINE_MS)
            // A line takes about as long as it takes to sing it; a long pause after it is a break.
            val duration = minOf((gap * 0.92f).toLong(), (estimate * 1.3f).toLong()).coerceAtLeast(400L)
            KLine(e, text, e.time, e.time + duration, interpolateWords(text, e.time, duration), parenRanges(text), e.isBackground, e.agent, true)
        }
    }

    val items = mutableListOf<KItem>()
    if (lines.first().start > GAP_MIN_MS) items += KItem.Gap(0L, lines.first().start)
    lines.forEachIndexed { i, line ->
        items += KItem.Line(line)
        val next = lines.getOrNull(i + 1) ?: return@forEachIndexed
        if (next.start - line.end > GAP_MIN_MS) items += KItem.Gap(line.end, next.start)
    }
    return items
}

private val instrumentalRe = Regex("""^[\s♪♫♬♩…\.\-–—*~]*$|^[\[(]?\s*(instrumental|интро|intro|outro|аутро|проигрыш|инструментал|music|музыка)\s*[\])]?$""", RegexOption.IGNORE_CASE)

private fun isInstrumentalMarker(text: String) = instrumentalRe.matches(text.trim())

private fun interpolateWords(text: String, start: Long, duration: Long): List<KWord> {
    val tokens = Regex("\\S+").findAll(text).toList()
    if (tokens.isEmpty()) return emptyList()
    // A little fixed weight per word so short words do not flash by.
    val weights = tokens.map { it.value.length + 2f }
    val total = weights.sum()
    var acc = 0f
    return tokens.mapIndexed { i, m ->
        val s = start + (duration * (acc / total)).toLong()
        acc += weights[i]
        val e = start + (duration * (acc / total)).toLong()
        KWord(s, e, m.range.first, m.range.last + 1)
    }
}

private fun parenRanges(text: String): List<IntRange> {
    val result = mutableListOf<IntRange>()
    var open = -1
    text.forEachIndexed { i, c ->
        when (c) {
            '(' -> if (open < 0) open = i
            ')' -> if (open >= 0) {
                result += open..i
                open = -1
            }
        }
    }
    if (open >= 0) result += open until text.length
    return result
}

/** Index in [items] of the line (or gap) that is current at [t]; -1 before the first one. */
fun activeIndex(items: List<KItem>, t: Long): Int {
    var idx = -1
    for (i in items.indices) {
        val item = items[i]
        if (item is KItem.Line && item.line.isBackground) continue
        if (item.start <= t) idx = i else break
    }
    return idx
}
