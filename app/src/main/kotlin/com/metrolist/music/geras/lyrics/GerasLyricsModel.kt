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
private const val MS_PER_CHAR = 80L
private const val MIN_LINE_MS = 1_200L
private const val MAX_HELD_WORD_MS = 4_000L
private const val DEFAULT_MS_PER_SYLLABLE = 200f
private const val PLAIN_BASE_MS = 1_000_000L

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
    // Plain text gets placeholder times 1_000_000, 1_000_001, … from the view model: not timing.
    if (times.first() >= PLAIN_BASE_MS && times.zipWithNext().all { (a, b) -> b - a <= 2 }) return false
    val distinct = times.distinct().size
    return distinct >= minOf(3, times.size) && times.zipWithNext().count { (a, b) -> b >= a } >= (times.size - 1) * 0.8
}

/**
 * Plain (untimed) lyrics: spread the lines over the track so the text still flows with the
 * music. Approximate by nature, so the screen marks it as such and does not seek on tap.
 */
fun estimateTiming(entries: List<LyricsEntry>, durationMs: Long): List<LyricsEntry> {
    val lines = entries.filter { it.text.isNotBlank() && !isInstrumentalMarker(it.text) }
    if (lines.isEmpty() || durationMs < 30_000) return entries
    // Blank lines and "[Chorus]"-style headers split the text into verses; the music between
    // verses gets its share of the time and is marked with a blank entry, so it shows as a break.
    data class Slot(val entry: LyricsEntry?, val weight: Float)
    val slots = mutableListOf<Slot>()
    for (e in entries) {
        val brk = e.text.isBlank() || isInstrumentalMarker(e.text)
        if (brk) {
            if (slots.isNotEmpty() && slots.last().entry != null) slots += Slot(null, BREAK_WEIGHT)
        } else {
            slots += Slot(e, e.text.length + 12f)
        }
    }
    while (slots.isNotEmpty() && slots.last().entry == null) slots.removeAt(slots.lastIndex)
    val start = (durationMs * 0.07).toLong()
    val span = (durationMs * 0.86).toLong()
    val total = slots.sumOf { it.weight.toDouble() }.toFloat()
    var acc = 0f
    return slots.map { slot ->
        val t = start + (span * (acc / total)).toLong()
        acc += slot.weight
        slot.entry?.copy(time = t, words = null) ?: LyricsEntry(t, "")
    }
}

private const val BREAK_WEIGHT = 30f

fun buildKaraoke(entries: List<LyricsEntry>, synced: Boolean, approximate: Boolean = false): List<KItem> {
    val source = entries.filter { (it.text.isNotBlank() && !isInstrumentalMarker(it.text)) || !it.words.isNullOrEmpty() }
    if (source.isEmpty()) return emptyList()
    // Blank timestamps and "♪"/"(instrumental)" lines mark where singing stops: they end the
    // previous line so the break shows as a gap instead of a line still being "sung".
    val boundaries = entries
        .filter { it.words.isNullOrEmpty() && (it.text.isBlank() || isInstrumentalMarker(it.text)) && it.time > 0 }
        .map { it.time }.distinct().sorted()
    if (!synced) {
        return source.map { e ->
            KItem.Line(KLine(e, e.text, Long.MAX_VALUE, Long.MAX_VALUE, emptyList(), parenRanges(e.text), e.isBackground, e.agent, true))
        }
    }

    // How fast this track is sung, read off the lyrics themselves: in a verse one line follows
    // another, so (time to the next line) / (syllables in the line) clusters around the real
    // pace. Rap comes out near 120 ms a syllable, a ballad near 350; one fixed guess fits neither.
    val pace = run {
        val lead = source.filter { !it.isBackground && it.words.isNullOrEmpty() }
        val samples = lead.zipWithNext().mapNotNull { (a, b) ->
            val gap = b.time - a.time
            val syl = syllables(a.text)
            if (gap in 600..9_000 && syl >= 3) gap.toFloat() / syl else null
        }.sorted()
        // The lower third: gaps are never shorter than the singing, breaks only make them longer.
        if (samples.size >= 4) samples[samples.size / 3].coerceIn(90f, 420f) else DEFAULT_MS_PER_SYLLABLE
    }

    val lines = source.mapIndexed { i, e ->
        val nextLine = source.drop(i + 1).firstOrNull { !it.isBackground }?.time // backing vocals overlap the lead, they do not end it
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
            val lastEnd = minOf(kw.last().end, kw.last().start + MAX_HELD_WORD_MS)
            val fixed = kw.dropLast(1) + kw.last().copy(end = lastEnd)
            KLine(e, text, e.time, lastEnd, fixed, parenRanges(text), e.isBackground, e.agent, false)
        } else {
            val text = e.text.trim()
            val gap = (nextStart ?: (e.time + 6_000L)) - e.time
            val estimate = (syllables(text) * pace + 250f).toLong().coerceAtLeast(MIN_LINE_MS)
            // A line takes about as long as it takes to sing it; a long pause after it is a break.
            // Estimated (plain) lyrics have no real pauses to show: the line simply runs until
            // the next one, otherwise every line would die early and leave "..." on screen.
            // Estimated (plain) lyrics run until the next line unless a verse break comes first.
            val breakFirst = nextMark != null && (nextLine == null || nextMark < nextLine)
            val duration = if (approximate && !breakFirst) (gap * 0.95f).toLong().coerceAtLeast(400L)
            else minOf((gap * 0.94f).toLong(), (estimate * (if (approximate) 1.4f else 1.3f)).toLong()).coerceAtLeast(400L)
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

private val instrumentalRe = Regex(
    """^[\s♪♫♬♩…\.\-–—*~]*$|^[\[(]?\s*((guitar |piano |sax )?solo|соло|instrumental( break)?|interlude|break|интро|intro|outro|аутро|проигрыш|инструментал|music|музыка|музыкальная пауза)\s*[\])]?$""",
    RegexOption.IGNORE_CASE,
)

/** "[Chorus]", "[Куплет 1: Артист]": section headers of plain lyrics, never sung. */
private val headerRe = Regex("""^\s*\[[^\]]{1,48}]\s*$""")

internal fun isInstrumentalMarker(text: String) = instrumentalRe.matches(text.trim()) || headerRe.matches(text)

private const val VOWELS = "аеёиоуыэюяАЕЁИОУЫЭЮЯaeiouyAEIOUYäöüÄÖÜéèêáàâíìîóòôúùûіїєІЇЄ"

/** Rough syllable count: vowels for alphabetic scripts, one per character for CJK/kana. */
internal fun syllables(text: String): Int {
    var n = 0
    var prevVowel = false
    for (c in text) {
        when {
            c in VOWELS -> {
                // English diphthongs ("ea", "ou") are one beat; Cyrillic vowels each count.
                if (!prevVowel || c.code > 0x3FF) n++
                prevVowel = true
            }
            c.code in 0x3040..0x30FF || c.code in 0x4E00..0x9FFF || c.code in 0xAC00..0xD7AF -> {
                n++
                prevVowel = false
            }
            else -> prevVowel = false
        }
    }
    return n.coerceAtLeast(if (text.any { it.isLetterOrDigit() }) 1 else 0)
}

/**
 * Spreads a line's time over its words by syllables (a three-syllable word is sung three times
 * longer than "я"), with a short breath after commas and dashes.
 */
private fun interpolateWords(text: String, start: Long, duration: Long): List<KWord> {
    val tokens = Regex("\\S+").findAll(text).toList()
    if (tokens.isEmpty()) return emptyList()
    val weights = tokens.map { m ->
        val pause = when (m.value.last()) {
            ',', ';', ':', '—', '–' -> 0.6f
            '.', '!', '?', '…' -> 0.9f
            else -> 0f
        }
        syllables(m.value).coerceAtLeast(1) + 0.35f to pause
    }
    val total = weights.sumOf { (it.first + it.second).toDouble() }.toFloat()
    var acc = 0f
    return tokens.mapIndexed { i, m ->
        val s = start + (duration * (acc / total)).toLong()
        val e = start + (duration * ((acc + weights[i].first) / total)).toLong()
        acc += weights[i].first + weights[i].second
        KWord(s, e.coerceAtLeast(s + 1), m.range.first, m.range.last + 1)
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
