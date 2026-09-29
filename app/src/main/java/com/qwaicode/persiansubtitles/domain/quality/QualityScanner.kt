package com.qwaicode.persiansubtitles.domain.quality

import com.qwaicode.persiansubtitles.domain.lang.Script
import com.qwaicode.persiansubtitles.domain.lang.TargetLanguage
import com.qwaicode.persiansubtitles.domain.lang.TargetLanguages
import com.qwaicode.persiansubtitles.domain.text.PersianNormalizer

/**
 * Finds what is wrong with a finished translation.
 *
 * The complaint this exists for: after a long run some lines are still not right —
 * an English word was left standing, a single Latin letter sits between Persian
 * ones, a sentence is cut in half, a word is written twice, the text uses Arabic
 * letters. Those defects are scattered over a 1500-line file, so looking for them by
 * hand is hopeless.
 *
 * The scanner splits them into two groups, because they need two different cures:
 *
 *  * **Mechanical** ([Issue.NORMALIZABLE]) — wrong letter shapes, missing
 *    zero-width non-joiner, spacing. [PersianNormalizer] repairs those on the
 *    device, for free, on every line.
 *  * **Editorial** (everything else) — an untranslated word or a truncated sentence
 *    cannot be repaired by a rule. Those lines, and only those, are sent back to
 *    Gemini in the polish pass.
 *
 * Pure Kotlin, so every rule is unit tested and none of it costs a request.
 */
object QualityScanner {

    enum class Issue(
        /** True when only the model can fix it; false when a local rule can. */
        val needsModel: Boolean,
        /** Short Persian description, sent to the model so it knows what to look at. */
        val label: String,
    ) {
        EMPTY(true, "بدون ترجمه"),
        SAME_AS_SOURCE(true, "متن اصلی به‌جای ترجمه"),
        UNTRANSLATED_WORD(true, "کلمهٔ انگلیسی ترجمه‌نشده"),
        STRAY_LATIN(true, "حرف لاتین چسبیده به حروف فارسی"),
        TRUNCATED(true, "ترجمهٔ ناقص یا بریده"),
        REPEATED_WORD(true, "کلمهٔ تکراری"),

        /**
         * The line is written in the wrong script — above all Persian, when the target
         * is another language. The instruction is Persian, and a model occasionally
         * answers in the language of the instruction instead of the target.
         */
        WRONG_SCRIPT(true, "نوشته‌شده به زبان یا خط اشتباه"),
        NORMALIZABLE(false, "املا و فاصله‌گذاری"),
    }

    private val TAG = Regex("""</?[A-Za-z][^>]*>|\{\\[^}]*\}""")

    /** A Latin word: three letters or more, so an initial or a roman numeral is ignored. */
    private val LATIN_WORD = Regex("""[A-Za-z][A-Za-z'’\-]{2,}""")

    /** A single lowercase Latin letter standing alone between Persian words. */
    private val LONE_LATIN = Regex("""(?<![A-Za-z])[a-z](?![A-Za-z])""")

    /** Persian and Latin letters written without a space between them. */
    private val GLUED_SCRIPTS = Regex("""[\u0600-\u06FF][A-Za-z]|[A-Za-z][\u0600-\u06FF]""")

    private val REPEATED = Regex("""(?<![\u0600-\u06FF])([\u0600-\u06FF]{2,}) \1(?![\u0600-\u06FF])""")

    /** Tokens that are not words: URLs, handles, file names, times. */
    private val NOT_A_WORD = Regex("""\S*[./@:\\]\S*""")

    private val PERSIAN_LETTER = Regex("""[\u0600-\u06FF]""")

    /** Any letter of another word: used to spot a doubled word in every script. */
    private val REPEATED_ANY = Regex("""(?<!\p{L})(\p{L}{2,}) \1(?!\p{L})""", RegexOption.IGNORE_CASE)

    fun issues(
        source: String,
        translated: String?,
        language: TargetLanguage = TargetLanguages.persian,
    ): Set<Issue> =
        if (language.isPersian) persianIssues(source, translated) else genericIssues(source, translated, language)

    /**
     * The same checks for every other target language. There is no local normaliser
     * for them, so nothing is ever [Issue.NORMALIZABLE]; everything found here is a
     * job for the model.
     */
    private fun genericIssues(source: String, translated: String?, language: TargetLanguage): Set<Issue> {
        val found = linkedSetOf<Issue>()
        if (translated.isNullOrBlank()) {
            found += Issue.EMPTY
            return found
        }
        val cleanSource = strip(source)
        val clean = strip(translated)
        if (clean.isBlank()) {
            found += Issue.EMPTY
            return found
        }

        val sourceHasLatin = LATIN_WORD.containsMatchIn(cleanSource)
        val hasOwn = language.hasOwnLetters(clean)

        // Answered in Persian (or Arabic) although the target uses another script.
        if (language.script != Script.ARABIC &&
            PERSIAN_LETTER.containsMatchIn(clean) &&
            !PERSIAN_LETTER.containsMatchIn(cleanSource)
        ) {
            found += Issue.WRONG_SCRIPT
        }

        if (language.script != Script.LATIN) {
            if (sourceHasLatin && !hasOwn && cleanSource.length > 3) {
                found += Issue.UNTRANSLATED_WORD
                if (equalIgnoringSpace(clean, cleanSource)) found += Issue.SAME_AS_SOURCE
                return found
            }
            if (hasOwn) {
                val leftover = LATIN_WORD.findAll(withoutNonWords(clean))
                    .map { it.value }
                    .any { it.first().isLowerCase() }
                if (leftover) found += Issue.UNTRANSLATED_WORD
            }
        }

        // A Latin target cannot be told apart from an English source by its letters,
        // but an unchanged sentence is still an untranslated one.
        if (cleanSource.length > 3 && LATIN_WORD.containsMatchIn(cleanSource) &&
            equalIgnoringSpace(clean, cleanSource)
        ) {
            found += Issue.SAME_AS_SOURCE
        }

        val ratio = if (language.script.compact) 0.08 else 0.25
        if (cleanSource.length > 24 && clean.length < cleanSource.length * ratio) {
            found += Issue.TRUNCATED
        }

        if (REPEATED_ANY.containsMatchIn(clean)) found += Issue.REPEATED_WORD
        return found
    }

    private fun persianIssues(source: String, translated: String?): Set<Issue> {
        val found = linkedSetOf<Issue>()

        if (translated.isNullOrBlank()) {
            found += Issue.EMPTY
            return found
        }

        if (!PersianNormalizer.isClean(translated)) found += Issue.NORMALIZABLE

        val cleanSource = strip(source)
        val clean = strip(translated)
        if (clean.isBlank()) {
            found += Issue.EMPTY
            return found
        }

        val sourceHasLatin = LATIN_WORD.containsMatchIn(cleanSource)
        val hasPersian = PERSIAN_LETTER.containsMatchIn(clean)

        // Nothing Persian at all, although the source was a real English sentence.
        if (sourceHasLatin && !hasPersian && cleanSource.length > 3) {
            found += Issue.UNTRANSLATED_WORD
            if (equalIgnoringSpace(clean, cleanSource)) found += Issue.SAME_AS_SOURCE
            return found
        }

        if (sourceHasLatin && equalIgnoringSpace(clean, cleanSource)) found += Issue.SAME_AS_SOURCE

        if (hasPersian) {
            // A leftover English word. Capitalised words are left alone: those are the
            // proper names the user asked to keep in Latin.
            val leftover = LATIN_WORD.findAll(withoutNonWords(clean))
                .map { it.value }
                .any { it.first().isLowerCase() }
            if (leftover) found += Issue.UNTRANSLATED_WORD

            if (GLUED_SCRIPTS.containsMatchIn(clean) ||
                LONE_LATIN.containsMatchIn(withoutNonWords(clean))
            ) {
                found += Issue.STRAY_LATIN
            }
        }

        if (cleanSource.length > 24 && clean.length < cleanSource.length * 0.25) {
            found += Issue.TRUNCATED
        }

        if (REPEATED.containsMatchIn(clean)) found += Issue.REPEATED_WORD

        return found
    }

    /** True when the line has a defect that a local rule cannot repair. */
    fun needsModel(
        source: String,
        translated: String?,
        language: TargetLanguage = TargetLanguages.persian,
    ): Boolean = issues(source, translated, language).any { it.needsModel }

    /**
     * The mechanical repair: safe, instant, no request. Only Persian has one — the
     * Persian rules (`ي` → `ی`, `ة` → `ه`) would damage Arabic, Urdu or Pashto — so
     * every other language only gets its spacing tidied.
     */
    fun autoFix(translated: String, language: TargetLanguage = TargetLanguages.persian): String =
        if (language.isPersian) PersianNormalizer.normalize(translated) else tidy(translated)

    private val MULTI_SPACE = Regex("""[ \t]{2,}""")

    /** Trims each displayed line and collapses runs of spaces. Safe in every language. */
    fun tidy(text: String): String =
        text.split('\n').joinToString("\n") { it.replace(MULTI_SPACE, " ").trim() }

    /** "کلمهٔ انگلیسی ترجمه‌نشده، ترجمهٔ ناقص یا بریده" — put into the polish prompt. */
    fun describe(issues: Set<Issue>): String =
        issues.filter { it.needsModel }.joinToString("، ") { it.label }

    private fun strip(text: String): String = TAG.replace(text, " ").trim()

    private fun withoutNonWords(text: String): String = NOT_A_WORD.replace(text, " ")

    private fun equalIgnoringSpace(a: String, b: String): Boolean =
        a.replace(Regex("""\s+"""), " ").equals(b.replace(Regex("""\s+"""), " "), ignoreCase = true)
}
