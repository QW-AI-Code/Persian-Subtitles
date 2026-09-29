package com.qwaicode.persiansubtitles.domain.text

/**
 * Mixed Persian/English text handling.
 *
 * A subtitle line is almost never purely one script: a Persian sentence with an
 * English name in it, an English line that was left untranslated, a credit line
 * with a URL. Without an explicit direction, the trailing period, question mark
 * or bracket of such a line jumps to the wrong side — the "scrambled" look.
 *
 * Everything here is pure Kotlin (no Android, no Compose) so the rules are unit
 * tested; the UI layer only maps the result onto Compose text styles.
 */
object BidiText {

    /** Right-to-left embedding: everything after it is laid out RTL. */
    const val RLE = '\u202B'

    /** Pops the last embedding — leaving this out is what corrupts the next line. */
    const val PDF = '\u202C'

    /** First-strong-isolate / pop-isolate: keeps an inserted run from bleeding out. */
    const val FSI = '\u2068'
    const val PDI = '\u2069'

    /**
     * The direction of a paragraph according to its first strong character, the
     * rule the Unicode BiDi algorithm itself uses. Neutral characters — spaces,
     * digits, punctuation, quotes, tags like `<i>` — are skipped, so "«Hello»"
     * is left-to-right and "«سلام»" is right-to-left.
     *
     * @param default used when the text carries no strong character at all
     *   (empty field, only digits). The app is Persian, so RTL is the default.
     */
    fun firstStrongIsRtl(text: String, default: Boolean = true): Boolean {
        text.forEach { ch ->
            when (Character.getDirectionality(ch)) {
                Character.DIRECTIONALITY_LEFT_TO_RIGHT,
                Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING,
                Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE,
                -> return false

                Character.DIRECTIONALITY_RIGHT_TO_LEFT,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE,
                -> return true

                else -> Unit // neutral or weak: keep looking
            }
        }
        return default
    }

    /** True when the text contains Persian/Arabic/Hebrew letters. */
    fun containsRtl(text: String): Boolean = text.any { ch ->
        when (Character.getDirectionality(ch)) {
            Character.DIRECTIONALITY_RIGHT_TO_LEFT,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
            -> true

            else -> false
        }
    }

    /** True when the text contains Latin letters. */
    fun containsLatin(text: String): Boolean = text.any { ch ->
        Character.getDirectionality(ch) == Character.DIRECTIONALITY_LEFT_TO_RIGHT
    }

    /**
     * Prepares one subtitle line for a media player.
     *
     * A line that actually contains Persian is wrapped in RLE … PDF, so the
     * player lays it out right-to-left and the punctuation stays where it
     * belongs. A line without any Persian (an English line, a URL, a signature
     * like "QW-AI-Code") is left completely untouched — forcing RTL on it, as
     * the app used to do for every single line, is exactly what pushed its full
     * stop to the front. Multi-line cues are handled line by line, because each
     * displayed line has its own direction.
     */
    fun forPlayer(text: String): String =
        text.split('\n').joinToString("\n") { line ->
            when {
                line.isBlank() -> line
                !containsRtl(line) -> line
                else -> buildString(line.length + 2) {
                    append(RLE).append(line).append(PDF)
                }
            }
        }

    /** Removes direction marks, e.g. before re-parsing a file the app wrote. */
    fun stripMarks(text: String): String =
        text.filterNot { it == RLE || it == PDF || it == FSI || it == PDI }
}
