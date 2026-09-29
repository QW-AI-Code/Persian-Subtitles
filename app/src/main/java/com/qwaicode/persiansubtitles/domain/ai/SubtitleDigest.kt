package com.qwaicode.persiansubtitles.domain.ai

/**
 * Turns a whole subtitle into one readable excerpt that fits in a single request.
 *
 * A feature film is 1500 cues and 60–90 thousand characters. Sending all of it to
 * ask "what is this film about" would be one very large, slow and — on the free
 * tier — rate-limit-triggering request, and most of it adds nothing: the plot, the
 * characters and the register are visible from a fraction of the dialogue.
 *
 * Two properties matter for the result to be useful, and both are tested:
 *
 *  * **Consecutive windows, not scattered lines.** Isolated sentences from all over
 *    the film tell you nothing about who is speaking to whom; a stretch of twenty
 *    consecutive lines does. The excerpt is therefore a fixed number of windows
 *    spread evenly over the running time.
 *  * **Deterministic.** The same file always produces the same excerpt, so a
 *    resumed project keeps the brief it started with.
 */
object SubtitleDigest {

    /** Roughly 4–5k tokens: large enough to judge a film, small enough for a free key. */
    const val DEFAULT_BUDGET = 12_000

    /** Beginning, end, and ten windows in between. */
    const val WINDOWS = 12

    /** Marks a jump in the excerpt, so the model does not read across the gap. */
    const val GAP = "[…]"

    fun build(lines: List<String>, budget: Int = DEFAULT_BUDGET): String {
        val usable = lines.map { it.replace('\n', ' ').trim() }.filter { it.isNotEmpty() }
        if (usable.isEmpty()) return ""

        val whole = usable.joinToString("\n")
        if (whole.length <= budget) return whole

        val perWindow = (budget / WINDOWS).coerceAtLeast(200)
        val step = usable.size.toDouble() / WINDOWS

        val out = StringBuilder(budget + 64)
        var index = 0
        var lastTaken = -1

        while (index < WINDOWS) {
            var cursor = (index * step).toInt().coerceIn(0, usable.size - 1)
            // The last window ends at the end of the film, not somewhere before it.
            if (index == WINDOWS - 1) {
                var start = usable.size - 1
                var length = 0
                while (start > 0 && length + usable[start].length + 1 <= perWindow) {
                    length += usable[start].length + 1
                    start--
                }
                cursor = (start + 1).coerceAtLeast(lastTaken + 1)
            }
            if (cursor <= lastTaken) cursor = lastTaken + 1
            if (cursor >= usable.size) break

            if (out.isNotEmpty()) out.append('\n').append(GAP).append('\n')

            var used = 0
            while (cursor < usable.size && used + usable[cursor].length + 1 <= perWindow) {
                out.append(usable[cursor]).append('\n')
                used += usable[cursor].length + 1
                lastTaken = cursor
                cursor++
            }
            index++
        }

        return out.toString().trim()
    }
}
