package com.qwaicode.persiansubtitles.domain.text

/**
 * How the exported file marks the direction of a Persian line.
 *
 * There is no single answer that works everywhere, because players differ in how
 * much of the Unicode BiDi algorithm they implement — that is why this is a
 * setting and not a constant.
 */
enum class BidiMode(val id: String) {

    /**
     * Default, and the only mode that is safe in every player.
     *
     * Two things happen, and only together do they fix the scrambling:
     *
     *  1. The line is marked right-to-left with a strong RLM (U+200F) at both ends,
     *     so the player lays it out RTL and the brackets and the final period at the
     *     two line boundaries are no longer handed to the surrounding left-to-right
     *     paragraph.
     *  2. Every embedded Latin run — an English name, a URL, a model number — is
     *     fenced in with an RLM on each side. That is what an RLM at the line ends
     *     alone cannot do: inside a Persian sentence the Latin run used to drag the
     *     neighbouring comma, colon or parenthesis with it. With a strong RTL
     *     character on both sides of the run, those neutral characters resolve to the
     *     Persian direction and stay where they belong.
     *
     * RLM is a zero-width character every player either applies or ignores — it can
     * never become a visible box, which is why the newer isolate characters are not
     * used here but offered separately as [ISOLATE].
     */
    SMART("smart"),

    /**
     * Like [SMART], but the embedded Latin runs are wrapped in a real isolate
     * (U+2068 FSI … U+2069 PDI) instead of being fenced with RLM.
     *
     * This is what UAX #9 recommends and what a modern engine (FriBidi, ICU, every
     * browser) handles best. It is **not** the default because a player that predates
     * Unicode 6.3, or simply has an incomplete font, draws these two characters as
     * empty boxes in the middle of the subtitle instead of applying them.
     */
    ISOLATE("isolate"),

    /**
     * RLM plus the legacy RLE … PDF embedding pair. What the app wrote before, kept
     * for old players that need the explicit embedding — but on its own it was not
     * enough: a player that ignores or strips the embedding controls put the
     * punctuation back on the wrong side.
     */
    STRONG("strong"),

    /**
     * No marks at all: clean logical text. The right choice for players with a
     * complete BiDi implementation (mpv/libass, modern VLC) and for feeding the
     * file into other software.
     */
    NONE("none");

    companion object {
        fun byId(id: String?): BidiMode = entries.firstOrNull { it.id == id } ?: SMART
    }
}

/**
 * Makes one subtitle line display correctly in a video player.
 *
 * Three different problems are handled here, and it is worth keeping them apart:
 *
 *  * **Direction** — the line is Persian, so the player must lay it out
 *    right-to-left. Handled by the marks described in [BidiMode].
 *  * **Mixed content** — a Latin run inside a Persian line has the opposite
 *    direction and pulls the punctuation next to it around. Handled by [isolate].
 *  * **Damage** — the text itself is in the wrong order, because the source file
 *    was written in visual order or the model returned it that way. A mark cannot
 *    repair that; the characters have to be moved. Handled by [repair].
 *
 * All of it is pure Kotlin so every rule is unit tested.
 */
object BidiShaper {

    /** Right-to-left mark: a zero-width strong right-to-left character. */
    const val RLM = '\u200F'

    /** Left-to-right mark. */
    const val LRM = '\u200E'

    /** First-strong isolate: the run inside it keeps its own direction… */
    const val FSI = '\u2068'

    /** …and this closes it again. Leaving it out corrupts the rest of the line. */
    const val PDI = '\u2069'

    /** Every direction control, so a file the app wrote can be re-read cleanly. */
    private val CONTROLS = setOf(
        '\u200E', '\u200F', // LRM, RLM
        '\u061C', // Arabic letter mark
        '\u202A', '\u202B', '\u202C', '\u202D', '\u202E', // LRE RLE PDF LRO RLO
        '\u2066', '\u2067', '\u2068', '\u2069', // LRI RLI FSI PDI
    )

    /** Punctuation that ends a sentence and therefore belongs at the end of a line. */
    private val SENTENCE_MARKS = setOf('.', '!', '?', '؟', '،', '؛', ':', '…')

    /** Mirrored pairs that a visually ordered line has the wrong way round. */
    private val MIRRORED = listOf(']' to '[', ')' to '(', '}' to '{', '»' to '«')

    /** Formatting markup, which must stay outside any isolate to remain parsable. */
    private val TAG = Regex("""</?[A-Za-z][^>]*>|\{\\[^}]*\}""")

    /** Characters that may sit inside a Latin run without ending it. */
    private const val RUN_INNER = ".,'’-_&/:@#+%°"

    fun strip(text: String): String = text.filterNot { it in CONTROLS }

    /** Shapes a whole cue; every displayed line has its own direction. */
    fun shape(text: String, mode: BidiMode, persianPunctuation: Boolean): String =
        text.split('\n').joinToString("\n") { shapeLine(it, mode, persianPunctuation) }

    /**
     * Shapes one line.
     *
     * A line without any Persian — an English line, a URL, a signature like
     * "QW-AI-Code" — is returned untouched. Forcing right-to-left onto it is what
     * moved its full stop to the front.
     */
    fun shapeLine(text: String, mode: BidiMode, persianPunctuation: Boolean): String {
        val clean = strip(text)
        if (clean.isBlank()) return clean
        if (!BidiText.containsRtl(clean)) return clean

        var line = repair(clean)
        if (persianPunctuation) line = persianPunctuation(line)

        return when (mode) {
            BidiMode.NONE -> line
            BidiMode.SMART -> "$RLM${fence(line)}$RLM"
            BidiMode.ISOLATE -> "$RLM${isolate(line)}$RLM"
            BidiMode.STRONG -> "$RLM${BidiText.RLE}$line${BidiText.PDF}$RLM"
        }
    }

    /**
     * Shapes an inserted credit line.
     *
     * A credit is not dialogue: it is a short decorative line that mixes a Persian
     * word, a Latin name and ornaments, and it is the one place where a player with a
     * weak BiDi implementation is guaranteed to disagree with a correct one. The text
     * is therefore handed over exactly as it was written, with only the direction
     * controls stripped — the templates in
     * [com.qwaicode.persiansubtitles.domain.subtitle.SignatureTemplates] are ordered
     * so that reading them left to right already gives the intended result, which
     * makes them render identically whether the player implements the BiDi algorithm
     * or ignores it completely. Adding marks on top would only break that agreement.
     */
    fun shapeCredit(text: String): String =
        text.split('\n').joinToString("\n") { strip(it).trim() }

    /**
     * Fences every embedded Latin run with an RLM on each side.
     *
     * `به John, بگو` used to put the comma on the wrong side of the name, because the
     * comma sits between a left-to-right and a right-to-left run and the algorithm has
     * to guess which one it belongs to. With an RLM on both sides of the name the
     * guess is gone: the neutral characters next to the run now have a strong
     * right-to-left neighbour and resolve to the Persian direction.
     *
     * Markup (`<i>`, `{\an8}`) is copied through untouched, and a run without a single
     * Latin letter is left alone — a bare number is a weak character and already
     * behaves correctly.
     */
    fun fence(line: String): String = wrapLatinRuns(line, RLM.toString(), RLM.toString())

    /** Same as [fence], but with real isolates. See [BidiMode.ISOLATE]. */
    fun isolate(line: String): String = wrapLatinRuns(line, FSI.toString(), PDI.toString())

    private fun wrapLatinRuns(line: String, before: String, after: String): String {
        if (!BidiText.containsLatin(line)) return line

        val out = StringBuilder(line.length + 16)
        var index = 0
        while (index < line.length) {
            val tag = TAG.matchAt(line, index)
            if (tag != null) {
                out.append(tag.value)
                index = tag.range.last + 1
                continue
            }

            if (!isRunStart(line[index])) {
                out.append(line[index])
                index++
                continue
            }

            val end = endOfRun(line, index)
            val run = line.substring(index, end)
            if (run.any { it.isLatinLetter() }) {
                out.append(before).append(run).append(after)
            } else {
                out.append(run)
            }
            index = end
        }
        return out.toString()
    }

    private fun isRunStart(ch: Char): Boolean = ch.isLatinLetter() || ch in '0'..'9'

    /**
     * The end of the Latin run that starts at [from]: letters, digits and the
     * punctuation *between* them (so `example.com` and `x-men 2` stay one run), but
     * never a trailing separator — that one belongs to the Persian sentence.
     */
    private fun endOfRun(line: String, from: Int): Int {
        var index = from
        var lastStrong = from
        while (index < line.length) {
            val ch = line[index]
            when {
                ch.isLatinLetter() || ch in '0'..'9' -> {
                    index++
                    lastStrong = index
                }

                ch == ' ' || ch in RUN_INNER -> {
                    // Only keep going when a Latin character follows; otherwise the
                    // separator is part of the surrounding Persian text.
                    val next = line.getOrNull(index + 1)
                    if (next != null && (next.isLatinLetter() || next in '0'..'9')) {
                        index++
                    } else {
                        break
                    }
                }

                else -> break
            }
        }
        return lastStrong.coerceAtLeast(from + 1)
    }

    private fun Char.isLatinLetter(): Boolean = this in 'A'..'Z' || this in 'a'..'z'

    /**
     * Repairs a line whose punctuation has physically ended up in the wrong place.
     *
     * This is deliberately conservative — it only touches the two patterns that
     * cannot occur in correctly written Persian text.
     */
    fun repair(text: String): String {
        var line = text.trim()
        line = unmirror(line)
        line = moveLeadingSentenceMark(line)
        line = tidySpaces(line)
        return line
    }

    /**
     * `]زن ۱[` → `[زن ۱]`. A line that *opens* with a closing bracket and *ends*
     * with an opening one was stored in visual order; the pair has to be swapped
     * back, otherwise no player can get it right. Angle brackets are left alone —
     * `<i>` is a formatting tag, not punctuation.
     */
    private fun unmirror(line: String): String {
        if (line.length < 2) return line
        val first = line.first()
        val last = line.last()
        val pair = MIRRORED.firstOrNull { it.first == first && it.second == last } ?: return line
        return pair.second + line.substring(1, line.length - 1) + pair.first
    }

    /**
     * `.قرار ملاقات دارم` → `قرار ملاقات دارم.`
     *
     * A Persian sentence never *starts* with a full stop, a comma or a question
     * mark, so finding one there means the line was saved in visual order. It is
     * moved to the end, where it belongs logically — the player then draws it on
     * the left, which is what the reader expects.
     *
     * A leading `…` or `...` is left alone: that is a legitimate continuation of a
     * sentence from the previous cue, and subtitles are full of it.
     */
    private fun moveLeadingSentenceMark(line: String): String {
        val cluster = line.takeWhile { it in SENTENCE_MARKS }
        if (cluster.isEmpty() || cluster.length > 2) return line
        if (cluster.any { it == '…' } || cluster == "..") return line
        if (cluster.any { it == '.' } && line.getOrNull(cluster.length)?.isDigit() == true) return line

        val rest = line.drop(cluster.length).trimStart()
        if (rest.isEmpty()) return line
        // Already punctuated at the end? Then the leading mark is intentional
        // (a stutter, a beat) and moving it would create a double.
        if (rest.last() in SENTENCE_MARKS) return line
        if (!BidiText.containsRtl(rest)) return line
        return rest + cluster
    }

    /** No space in front of a comma or a period, exactly one space after it. */
    private fun tidySpaces(line: String): String = line
        .replace(Regex("""[ \t]+([،؛؟!:.])(?=\s|$)"""), "$1")
        .replace(Regex("""([،؛])(?=\p{L})"""), "$1 ")
        .replace(Regex("""[ \t]{2,}"""), " ")
        .trim()

    /**
     * Persian punctuation for Persian text: `,` `;` `?` become `،` `؛` `؟`.
     *
     * Only where they actually punctuate Persian — a comma between two Latin
     * characters belongs to an English phrase inside the line ("Hello, world") and
     * stays as it is.
     */
    fun persianPunctuation(line: String): String = buildString(line.length) {
        line.forEachIndexed { index, ch ->
            val replacement = when (ch) {
                ',' -> '،'
                ';' -> '؛'
                '?' -> '؟'
                else -> null
            }
            if (replacement != null && !touchesLatin(line, index)) append(replacement) else append(ch)
        }
    }

    private fun touchesLatin(line: String, index: Int): Boolean {
        val before = line.getOrNull(index - 1)
        val after = line.getOrNull(index + 1)
        return isLatin(before) || isLatin(after)
    }

    private fun isLatin(ch: Char?): Boolean =
        ch != null && (ch in 'A'..'Z' || ch in 'a'..'z')
}
