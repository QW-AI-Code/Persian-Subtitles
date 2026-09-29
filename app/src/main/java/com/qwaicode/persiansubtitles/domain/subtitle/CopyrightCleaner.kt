package com.qwaicode.persiansubtitles.domain.subtitle

/**
 * Detects the credit / advertisement cues that fan-subbers put between the real
 * dialogue (channel names, Telegram handles, website ads, "synced by ...").
 *
 * Patterns are plain regular expressions, one per line, and fully editable by the
 * user in the Copyright tab.
 */
object CopyrightCleaner {

    /** Number of cues scanned at the start and at the end when edge-only mode is on. */
    const val EDGE_WINDOW = 15

    val DEFAULT_PATTERNS: List<String> = listOf(
        // links and social handles
        "(?i)(https?://|www\\.)\\S+",
        "(?i)\\bt\\.me/\\S+",
        "(?i)@[A-Za-z0-9_]{4,}",
        "(?i)\\b(telegram|instagram|twitter|facebook|youtube|tiktok)\\b",
        // release / subtitle scene
        "(?i)\\b(subscene|opensubtitles|addic7ed|yify|yts|rarbg|psa|galaxyrg)\\b",
        "(?i)\\b(sync(ed)?|encoded|ripped|corrected|resync(ed)?|translated)\\s+by\\b",
        "(?i)\\bsubs?(titles)?\\s+by\\b",
        "(?i)\\b(copyright|all rights reserved)\\b",
        "©",
        // Persian credit lines
        "زیرنویس|زیر ?نویس",
        "مترجم(ین|ان)?",
        "ترجمه[ ‌]?(و[ ‌]?(تنظیم|زیرنویس))?[ ‌]?(اختصاصی|از|توسط)",
        "تنظیم|هماهنگ[ ‌]?سازی|سینک",
        "کانال|تلگرام|اینستاگرام|آی[ ‌]?دی",
        "تیم[ ‌]?ترجمه|گروه[ ‌]?ترجمه",
        "(فیلم|مووی|سریال)[ ‌]?(بین|باز|لند|کیو|یاب)",
        "دانلود[ ‌]?(فیلم|سریال|رایگان)",
    )

    val DEFAULT_PATTERNS_TEXT: String = DEFAULT_PATTERNS.joinToString("\n")

    private val TAG_REGEX = Regex("""</?[a-zA-Z][^>]*>|\{\\[^}]*\}""")
    private val MUSIC_NOTE = Regex("""^[\s♪♫#*\-–—]+$""")

    fun compile(patternsText: String): List<Regex> =
        patternsText.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { runCatching { Regex(it) }.getOrNull() }
            .toList()

    fun stripTags(text: String): String =
        TAG_REGEX.replace(text, "").replace(Regex(" {2,}"), " ").trim()

    /**
     * True when the cue looks like a credit / advertisement.
     *
     * [position] and [total] are used for the edge-only mode, which protects real
     * dialogue in the middle of a movie from being removed by mistake.
     */
    fun isAd(
        text: String,
        regexes: List<Regex>,
        position: Int,
        total: Int,
        edgesOnly: Boolean,
    ): Boolean {
        if (regexes.isEmpty()) return false
        if (edgesOnly && total > EDGE_WINDOW * 2) {
            val inEdge = position <= EDGE_WINDOW || position > total - EDGE_WINDOW
            if (!inEdge) return false
        }
        val clean = stripTags(text)
        if (clean.isBlank() || MUSIC_NOTE.matches(clean)) return false
        val matched = regexes.firstOrNull { it.containsMatchIn(clean) } ?: return false

        // A cue is an advertisement when the match makes up most of the cue,
        // otherwise it is probably a normal line that only mentions the word.
        val matchLength = matched.findAll(clean).sumOf { it.value.length }
        val ratio = matchLength.toDouble() / clean.length.coerceAtLeast(1)
        val short = clean.length <= 60
        return ratio >= 0.25 || short
    }
}
