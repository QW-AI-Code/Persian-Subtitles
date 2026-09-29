package com.qwaicode.persiansubtitles.domain.text

/**
 * The deterministic half of the post-translation clean-up.
 *
 * A model writing Persian produces a predictable set of small defects that have
 * nothing to do with the translation itself: the Arabic `ك` and `ي` instead of the
 * Persian `ک` and `ی`, Arabic-Indic digits, a stray tatweel, Arabic diacritics, a
 * space where a zero-width non-joiner belongs (`می رود` instead of `می‌رود`), a
 * space in front of a comma, two spaces in a row.
 *
 * None of that needs a second request to Gemini — it is mechanical, so it is fixed
 * here, on the device, for every line. Everything that needs judgement (a word left
 * in English, a truncated sentence) is left to [com.qwaicode.persiansubtitles.domain.quality.QualityScanner]
 * and the AI pass.
 *
 * Deliberately conservative: a rule is only applied where it cannot damage correct
 * text. Pure Kotlin, so every rule is unit tested.
 */
object PersianNormalizer {

    /** Zero-width non-joiner — a real letter-joining character in Persian, never removed. */
    const val ZWNJ = '\u200C'

    /** Letters that Persian writes differently from Arabic, plus presentation forms. */
    private val LETTERS: Map<Char, Char> = mapOf(
        // kaf
        'ك' to 'ک', '\uFED9' to 'ک', '\uFEDA' to 'ک', '\uFEDB' to 'ک', '\uFEDC' to 'ک',
        // yeh
        'ي' to 'ی', 'ى' to 'ی', '\uFEF1' to 'ی', '\uFEF2' to 'ی', '\uFBFC' to 'ی', '\uFBFD' to 'ی',
        // heh / teh marbuta
        'ة' to 'ه', '\uFEE9' to 'ه', '\uFEEA' to 'ه',
        // alef presentation forms
        '\uFE8D' to 'ا', '\uFE8E' to 'ا',
        // Arabic-Indic digits
        '٠' to '0', '١' to '1', '٢' to '2', '٣' to '3', '٤' to '4',
        '٥' to '5', '٦' to '6', '٧' to '7', '٨' to '8', '٩' to '9',
    )

    /**
     * Characters that carry no meaning in a subtitle line and are dropped.
     *
     * The tanwin `ً` (U+064B) is deliberately **not** in here. It looks like a
     * diacritic but it is part of the correct spelling of everyday Persian words —
     * «حتماً», «لطفاً», «معمولاً» — and stripping it would introduce spelling
     * mistakes instead of removing them. The vowel marks below, on the other hand,
     * are optional Arabic vocalisation that a model sometimes adds and that no Persian
     * subtitle ever shows.
     */
    private val REMOVED: Set<Char> = buildSet {
        add('\u0640') // tatweel
        addAll(('\u064C'..'\u0655').toList()) // dammatan … hamza marks, without fathatan
        add('\u0670')
        add('\u200B') // zero width space
        add('\u200D') // zero width joiner
        add('\uFEFF') // BOM in the middle of a line
    }

    /** `می` / `نمی` glued to the verb, which is how Persian writes it. */
    private val VERB_PREFIX = Regex("""(?<![\u0600-\u06FF])(ن?می)[ \t]+(?=[\u0600-\u06FF]{2,})""")

    /** A space in front of a Persian mark is always wrong. */
    private val SPACE_BEFORE_PERSIAN_MARK = Regex("""[ \t]+([،؛؟…])""")

    /**
     * A space in front of a full stop, an exclamation mark or a colon — but only at
     * the end of a word, so `e.g.`, a time like `12:30` and a decimal number keep
     * their own spacing.
     */
    private val SPACE_BEFORE_MARK = Regex("""[ \t]+([.!:])(?=\s|$)""")

    /** A comma or semicolon glued to the next word. */
    private val MARK_WITHOUT_SPACE = Regex("""([،؛])(?=[\p{L}\u0600-\u06FF])""")

    /** Spaces around a zero-width non-joiner: the ZWNJ replaces them. */
    private val ZWNJ_SPACES = Regex("""[ \t]*\u200C[ \t]*""")

    private val MULTI_SPACE = Regex("""[ \t]{2,}""")

    /** Every rule, applied line by line — each displayed line stands on its own. */
    fun normalize(text: String): String =
        text.split('\n').joinToString("\n") { normalizeLine(it) }

    fun normalizeLine(line: String): String {
        if (line.isBlank()) return line.trim()

        val mapped = buildString(line.length) {
            line.forEach { ch ->
                when {
                    ch in REMOVED -> Unit
                    ch == '\u00A0' -> append(' ')
                    else -> append(LETTERS[ch] ?: ch)
                }
            }
        }

        return mapped
            .replace(ZWNJ_SPACES, ZWNJ.toString())
            .replace(VERB_PREFIX, "$1$ZWNJ")
            .replace(SPACE_BEFORE_PERSIAN_MARK, "$1")
            .replace(SPACE_BEFORE_MARK, "$1")
            .replace(MARK_WITHOUT_SPACE, "$1 ")
            .replace(MULTI_SPACE, " ")
            .trim()
    }

    /** True when [text] is already clean, so nothing has to be written back. */
    fun isClean(text: String): Boolean = normalize(text) == text
}
