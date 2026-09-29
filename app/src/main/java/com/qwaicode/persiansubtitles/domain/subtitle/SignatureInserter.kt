package com.qwaicode.persiansubtitles.domain.subtitle

import com.qwaicode.persiansubtitles.data.db.CueEntity
import kotlin.math.max
import kotlin.random.Random

/**
 * Places the translator's signature inside the subtitle instead of dumping one
 * line at the very end.
 *
 * Two rules decide everything here:
 *
 *  1. **Never touch the dialogue.** A credit is only ever placed in a hole in
 *     the timeline where no cue is on screen, with a guard band on both sides.
 *     No existing cue is moved, stretched or shifted, so the timing of the whole
 *     file stays exactly as it was — only the cue numbering grows.
 *  2. **Spread it out.** The requested number of credits is distributed over the
 *     whole running time (one per equal-length window) rather than clustered in
 *     the quiet opening minutes.
 *
 * The placement is deterministic for a given file, text and count: exporting
 * twice produces byte-identical files, which also makes it testable.
 */
object SignatureInserter {

    /** Pre-filled signature text; the user is expected to replace it with theirs. */
    const val DEFAULT_TEXT = "QW-AI-Code"

    /** Default number of credits, and the range offered in the UI. */
    const val DEFAULT_COUNT = 3
    const val MIN_COUNT = 1
    const val MAX_COUNT = 15

    /** A hole in the dialogue must be at least this long to host a credit. */
    const val MIN_GAP_MS = 4_000L

    /** Distance kept from the neighbouring dialogue on each side. */
    const val GUARD_MS = 600L

    /** How long a credit stays on screen, and the shortest acceptable flash. */
    const val SHOW_MS = 3_000L
    const val MIN_SHOW_MS = 1_200L

    /** Room reserved after the last cue, where a credit always fits. */
    private const val TAIL_LEAD_MS = 1_500L
    private const val TAIL_LENGTH_MS = 30_000L

    /** A credit line to be merged into the export. */
    data class Credit(val startMs: Long, val endMs: Long, val text: String)

    private data class Gap(val startMs: Long, val endMs: Long) {
        val length: Long get() = endMs - startMs
    }

    /**
     * @param cues the cues that will be exported, in any order.
     * @param signature the user's text; blank means "no credits".
     * @param count how many credits the user asked for.
     */
    fun plan(cues: List<CueEntity>, signature: String, count: Int): List<Credit> {
        val text = signature.trim()
        if (text.isEmpty() || count <= 0) return emptyList()

        val sorted = cues.sortedBy { it.startMs }
        if (sorted.isEmpty()) {
            return listOf(Credit(TAIL_LEAD_MS, TAIL_LEAD_MS + SHOW_MS, text))
        }

        val wanted = count.coerceIn(MIN_COUNT, MAX_COUNT)
        val gaps = findGaps(sorted)
        if (gaps.isEmpty()) return emptyList()

        val random = Random(seedFor(text, wanted, sorted))
        val chosen = distribute(gaps, wanted, random)

        return chosen
            .mapNotNull { gap -> placeIn(gap, text, random) }
            .sortedBy { it.startMs }
    }

    /**
     * Holes in the timeline: before the first cue, between cues and after the
     * last one. A running maximum of the end times is used so overlapping cues
     * (they exist in hand-made files) can never produce a fake gap.
     */
    private fun findGaps(sorted: List<CueEntity>): List<Gap> {
        val gaps = mutableListOf<Gap>()
        val minimum = MIN_GAP_MS + 2 * GUARD_MS

        val opening = Gap(0L, sorted.first().startMs)
        if (opening.length >= minimum) gaps += opening

        var reached = sorted.first().endMs
        for (index in 1 until sorted.size) {
            val cue = sorted[index]
            val gap = Gap(reached, cue.startMs)
            if (gap.length >= minimum) gaps += gap
            reached = max(reached, cue.endMs)
        }

        // After the credits of the film itself there is always room: appending
        // here cannot collide with anything.
        gaps += Gap(reached + TAIL_LEAD_MS, reached + TAIL_LEAD_MS + TAIL_LENGTH_MS)
        return gaps
    }

    /**
     * One gap per equal-length window of the running time. Windows without a
     * usable gap give their slot back, and the leftovers are filled with the
     * longest unused gaps, so the user always gets as many credits as the file
     * can carry.
     */
    private fun distribute(gaps: List<Gap>, wanted: Int, random: Random): List<Gap> {
        if (gaps.size <= wanted) return gaps

        val span = gaps.last().endMs.coerceAtLeast(1L)
        val window = span / wanted
        val used = mutableSetOf<Int>()
        val picked = mutableListOf<Gap>()

        for (slot in 0 until wanted) {
            val from = slot * window
            val to = if (slot == wanted - 1) span + 1 else (slot + 1) * window
            val candidates = gaps.indices.filter { index ->
                index !in used && gaps[index].let { it.startMs + it.length / 2 } in from until to
            }
            if (candidates.isEmpty()) continue
            val index = candidates[random.nextInt(candidates.size)]
            used += index
            picked += gaps[index]
        }

        if (picked.size < wanted) {
            gaps.indices
                .filter { it !in used }
                .sortedByDescending { gaps[it].length }
                .take(wanted - picked.size)
                .forEach { picked += gaps[it] }
        }

        return picked.sortedBy { it.startMs }
    }

    /** Places the credit inside the gap, keeping clear of both neighbours. */
    private fun placeIn(gap: Gap, text: String, random: Random): Credit? {
        val from = gap.startMs + GUARD_MS
        val until = gap.endMs - GUARD_MS
        val room = until - from
        if (room < MIN_SHOW_MS) return null

        val duration = SHOW_MS.coerceAtMost(room)
        val slack = room - duration
        // A little jitter so the credits do not all sit at the same offset.
        val offset = if (slack > 0) random.nextLong(slack + 1) else 0L
        val start = from + offset
        return Credit(start, start + duration, text)
    }

    /** Same file + same text + same count → same placement on every export. */
    private fun seedFor(text: String, count: Int, sorted: List<CueEntity>): Long {
        var seed = text.hashCode().toLong() * 31 + count
        seed = seed * 31 + sorted.size
        seed = seed * 31 + sorted.last().endMs
        seed = seed * 31 + sorted.first().startMs
        return seed
    }
}
