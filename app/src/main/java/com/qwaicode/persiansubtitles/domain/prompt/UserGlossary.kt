package com.qwaicode.persiansubtitles.domain.prompt

/**
 * The user's own fixed terms.
 *
 * The brief the model builds by reading the film is a guess; a fansub team that has
 * translated seasons one to four already knows that "the Watch" is «نگهبانان شب»
 * and that "Winterfell" stays as it is. This list is how they say so once, and it is
 * sent with every batch *above* the brief, so the model's own guess never wins.
 *
 * The format is deliberately forgiving because it is typed on a phone keyboard:
 * `source = translation`, and `=>`, `→`, `←` or `:` work as well. Blank lines and
 * lines starting with `#` are ignored.
 */
object UserGlossary {

    data class Entry(val source: String, val target: String)

    /** A prompt block must stay small: it goes out again with every request. */
    const val MAX_ENTRIES = 60

    private val SEPARATORS = listOf("=>", "→", "←", "=", ":", "|")

    fun parse(text: String): List<Entry> {
        val seen = mutableSetOf<String>()
        return text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                val sep = SEPARATORS.firstOrNull { line.contains(it) } ?: return@mapNotNull null
                val source = line.substringBefore(sep).trim()
                val target = line.substringAfter(sep).trim()
                if (source.isEmpty() || target.isEmpty()) null else Entry(source, target)
            }
            // The first definition of a term wins; a later duplicate is a typo.
            .filter { seen.add(it.source.lowercase()) }
            .take(MAX_ENTRIES)
            .toList()
    }

    /** The block for the system instruction, or blank when there is nothing to say. */
    fun toPromptBlock(text: String, languageName: String): String {
        val entries = parse(text)
        if (entries.isEmpty()) return ""
        return buildString {
            appendLine("### واژه‌نامهٔ اختصاصی کاربر (الزامی، مقدم بر هر معادل دیگر)")
            appendLine("هر جا این واژه‌ها یا عبارت‌ها آمد، دقیقاً با همین معادل $languageName ترجمه کن:")
            entries.forEach { appendLine("- ${it.source} = ${it.target}") }
        }.trimEnd()
    }
}
