package com.mojrucek.app.domain.util

/**
 * Lets search queries typed on a Latin keyboard (e.g. "zacin") match
 * Cyrillic Macedonian text (e.g. "зачин") without diacritics or exact
 * transliteration.
 */
object MacedonianSearch {

    private val cyrillicToLatin = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'ѓ' to "gj", 'е' to "e",
        'ж' to "zh", 'з' to "z", 'ѕ' to "dz", 'и' to "i", 'ј' to "j", 'к' to "k", 'л' to "l",
        'љ' to "lj", 'м' to "m", 'н' to "n", 'њ' to "nj", 'о' to "o", 'п' to "p", 'р' to "r",
        'с' to "s", 'т' to "t", 'ќ' to "kj", 'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "c",
        'ч' to "ch", 'џ' to "dz", 'ш' to "sh"
    )

    /**
     * Converts [text] to a loose Latin key: Cyrillic text is romanized
     * through [cyrillicToLatin], then "ch"/"sh"/"zh" are collapsed to their
     * single-letter form so a casually-typed Latin query (no digraphs) and
     * the Cyrillic-derived romanization converge on the same key.
     */
    fun normalizeForSearch(text: String): String {
        val lower = text.lowercase()
        val hasCyrillic = lower.any { it in cyrillicToLatin }
        val latinized = if (hasCyrillic) {
            buildString { lower.forEach { c -> append(cyrillicToLatin[c] ?: c.toString()) } }
        } else {
            lower
        }
        val collapsed = latinized
            .replace("ch", "c")
            .replace("sh", "s")
            .replace("zh", "z")
        return collapsed.filter { it.isLetterOrDigit() }
    }
}
