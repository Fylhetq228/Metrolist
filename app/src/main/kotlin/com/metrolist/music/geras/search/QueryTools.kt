package com.metrolist.music.geras.search

/** Keyboard-layout and transliteration helpers for "ащку" → "face" and "kino" → "кино". */
object QueryTools {
    private const val EN = "`qwertyuiop[]asdfghjkl;'zxcvbnm,./~QWERTYUIOP{}ASDFGHJKL:\"ZXCVBNM<>?"
    private const val RU = "ёйцукенгшщзхъфывапролджэячсмитьбю.ЁЙЦУКЕНГШЩЗХЪФЫВАПРОЛДЖЭЯЧСМИТЬБЮ,"

    private val enToRu = EN.zip(RU).toMap()
    private val ruToEn = RU.zip(EN).toMap()

    private fun isCyr(c: Char) = c in 'а'..'я' || c in 'А'..'Я' || c == 'ё' || c == 'Ё'
    private fun isLat(c: Char) = c in 'a'..'z' || c in 'A'..'Z'

    /** The query as if typed on the other layout, or null when it is mixed or has no letters. */
    fun switchLayout(query: String): String? {
        val letters = query.filter { it.isLetter() }
        if (letters.length < 2) return null
        return when {
            letters.all(::isCyr) -> query.map { ruToEn[it] ?: it }.joinToString("")
            letters.all(::isLat) -> query.map { enToRu[it] ?: it }.joinToString("")
            else -> null
        }
    }

    private val translitRu = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "yo", 'ж' to "zh",
        'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o",
        'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "kh", 'ц' to "ts",
        'ч' to "ch", 'ш' to "sh", 'щ' to "sch", 'ъ' to "", 'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya",
    )
    private val latToCyr = listOf(
        "sch" to "щ", "shch" to "щ", "zh" to "ж", "kh" to "х", "ts" to "ц", "ch" to "ч", "sh" to "ш",
        "yo" to "ё", "yu" to "ю", "ya" to "я", "ye" to "е",
        "a" to "а", "b" to "б", "v" to "в", "g" to "г", "d" to "д", "e" to "е", "z" to "з", "i" to "и",
        "y" to "й", "k" to "к", "l" to "л", "m" to "м", "n" to "н", "o" to "о", "p" to "п", "r" to "р",
        "s" to "с", "t" to "т", "u" to "у", "f" to "ф", "h" to "х", "c" to "к", "w" to "в", "x" to "кс", "j" to "дж", "q" to "к",
    )

    /** Cyrillic ↔ Latin transliteration, or null when the script is mixed. */
    fun transliterate(query: String): String? {
        val letters = query.filter { it.isLetter() }
        if (letters.length < 2) return null
        return when {
            letters.all(::isCyr) -> query.lowercase().map { translitRu[it] ?: it.toString() }.joinToString("")
            letters.all(::isLat) -> {
                var s = query.lowercase()
                val out = StringBuilder()
                var i = 0
                while (i < s.length) {
                    val hit = latToCyr.firstOrNull { s.startsWith(it.first, i) }
                    if (hit != null) {
                        out.append(hit.second)
                        i += hit.first.length
                    } else {
                        out.append(s[i])
                        i++
                    }
                }
                out.toString()
            }
            else -> null
        }
    }

    /** How many suggestions really continue what was typed; used to pick the intended layout. */
    fun prefixScore(query: String, suggestions: List<String>): Int {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return 0
        return suggestions.count { s ->
            val l = s.lowercase()
            l.startsWith(q) || l.split(' ').any { it.startsWith(q) }
        }
    }
}
