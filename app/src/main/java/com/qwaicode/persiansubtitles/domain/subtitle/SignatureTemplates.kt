package com.qwaicode.persiansubtitles.domain.subtitle

/**
 * Ready-made credit lines, in the shapes professional Persian subtitles actually
 * use.
 *
 * ### Why the name comes first
 *
 * A credit line is the hardest thing in a subtitle file to lay out: it mixes a
 * Persian word, a Latin name and a few ornaments, and players disagree about what to
 * do with such a line. A player that implements the BiDi algorithm reads the line's
 * direction from its first strong character; a player that implements nothing simply
 * draws the tokens from left to right in the order they are stored.
 *
 * Every template below therefore puts the **Latin name first**. That single decision
 * makes both kinds of player produce the same picture:
 *
 * ```
 * stored:    ★ QW-AI-Code — زیرنویس اختصاصی ★
 * displayed: ★ QW-AI-Code — زیرنویس اختصاصی ★     (both, left to right)
 * ```
 *
 * The name ends up on the left, the Persian text on the right, and no direction
 * control character is needed to get there — which also means nothing can show up as
 * an empty box in a player that does not know those characters.
 *
 * Only ornaments that exist in every subtitle font are used, for the same reason.
 *
 * Pure Kotlin, so the rendering and the name detection are unit tested.
 */
object SignatureTemplates {

    /** The placeholder replaced by the user's name or channel. */
    const val NAME = "{نام}"

    data class Template(
        val id: String,
        /** What the user sees in the picker. */
        val title: String,
        /** The frame, containing [NAME]. */
        val pattern: String,
    )

    val all: List<Template> = listOf(
        Template(
            id = "plain",
            title = "ساده",
            pattern = "$NAME — ترجمه",
        ),
        Template(
            id = "dash",
            title = "با خط تیره",
            pattern = "$NAME - ترجمه و زیرنویس",
        ),
        Template(
            id = "stars",
            title = "ستاره‌دار",
            pattern = "★ $NAME ★ زیرنویس اختصاصی",
        ),
        Template(
            id = "rule",
            title = "خط تزئینی",
            pattern = "─── $NAME ─── ترجمه",
        ),
        Template(
            id = "bullets",
            title = "نقطه‌دار",
            pattern = "• $NAME • مترجم",
        ),
        Template(
            id = "quoted",
            title = "گیومه‌ای",
            pattern = "$NAME «زیرنویس اختصاصی»",
        ),
        Template(
            id = "brackets",
            title = "کروشه‌ای",
            pattern = "[ $NAME ] ترجمه و تنظیم",
        ),
        Template(
            id = "italic",
            title = "ایتالیک (تگ‌دار)",
            pattern = "<i>$NAME — ترجمه</i>",
        ),
        Template(
            id = "sync",
            title = "ترجمه و هماهنگ‌سازی",
            pattern = "$NAME — ترجمه و هماهنگ‌سازی",
        ),
        Template(
            id = "watch",
            title = "آرزوی تماشای خوب",
            pattern = "$NAME — تماشای خوبی داشته باشید",
        ),
        // The two-line frames keep each line to a single script, which is the most
        // robust arrangement of all: neither line can be reordered by anything.
        Template(
            id = "two_lines",
            title = "دو خطی",
            pattern = "$NAME\nترجمه و زیرنویس",
        ),
        Template(
            id = "two_lines_stars",
            title = "دو خطی ستاره‌دار",
            pattern = "★ $NAME ★\nزیرنویس اختصاصی",
        ),
        Template(
            id = "channel",
            title = "دو خطی با کانال",
            pattern = "$NAME\nبرای زیرنویس‌های بیشتر همراه ما باشید",
        ),
        Template(
            id = "presented",
            title = "تقدیم می‌کند",
            pattern = "$NAME\nتقدیم می‌کند",
        ),
    )

    fun byId(id: String): Template? = all.firstOrNull { it.id == id }

    /** Puts [name] into the frame; a blank name falls back to the project default. */
    fun render(pattern: String, name: String): String {
        val value = name.trim().ifBlank { SignatureInserter.DEFAULT_TEXT }
        return pattern.replace(NAME, value)
    }

    private val TAG = Regex("""</?[A-Za-z][^>]*>|\{\\[^}]*\}""")

    /** Words that belong to the frame, not to the name. */
    private val LABELS = listOf(
        "زیرنویس اختصاصی", "ترجمه و هماهنگ‌سازی", "ترجمه و زیرنویس", "ترجمه و تنظیم",
        "تقدیم می‌کند", "تماشای خوبی داشته باشید", "برای زیرنویس‌های بیشتر همراه ما باشید",
        "زیرنویس", "مترجم", "ترجمه", "تنظیم", "هماهنگ‌سازی", "کانال", "اختصاصی",
    )

    /** Characters a name (or the label in front of it) may contain; the rest is decoration. */
    private const val KEPT = " -_.@/:\u200C"

    /**
     * Pulls the name back out of an existing signature, so switching template does
     * not lose it: `★ QW-AI-Code ★ زیرنویس اختصاصی` → `QW-AI-Code`.
     */
    fun extractName(signature: String): String {
        val candidate = signature
            .lineSequence()
            .map { line -> clean(line) }
            .filter { it.isNotBlank() }
            .maxByOrNull { scoreOf(it) }
            ?: return SignatureInserter.DEFAULT_TEXT

        var value = candidate
        LABELS.forEach { label -> value = value.replace(label, " ") }
        // A label may be followed by its colon: «ترجمه: علی» leaves ": علی" behind.
        value = value.substringAfterLast(':').trim().ifBlank { value }
        value = value.replace(Regex("""\s{2,}"""), " ").trim(' ', '-', '—', '.', '،', ':')

        return value.ifBlank { SignatureInserter.DEFAULT_TEXT }
    }

    /** A line's chance of holding the name: letters and digits count, frames do not. */
    private fun scoreOf(line: String): Int {
        var score = line.count { it.isLetterOrDigit() }
        // A frame word counts double against the line: a line that is mostly frame
        // ("برای زیرنویس‌های بیشتر همراه ما باشید") must never win over the short line
        // that actually holds the name.
        LABELS.forEach { label -> if (line.contains(label)) score -= label.length * 2 }
        return score
    }

    private fun clean(line: String): String = TAG.replace(line, " ")
        .map { ch -> if (ch.isLetterOrDigit() || ch in KEPT) ch else ' ' }
        .joinToString("")
        .replace(Regex("""\s{2,}"""), " ")
        .trim()
}
