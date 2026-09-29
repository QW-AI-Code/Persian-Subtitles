package com.qwaicode.persiansubtitles.domain.subtitle

import com.qwaicode.persiansubtitles.data.db.CueEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the translator's credit is allowed to land.
 *
 * The hard requirement is that the dialogue is never touched: a credit may only
 * sit in a hole in the timeline, with a guard band on both sides, so no cue has
 * to be moved and the timing of the rest of the file cannot drift.
 */
class SignatureInserterTest {

    private fun cue(id: Int, start: Long, end: Long) =
        CueEntity(id = id, startMs = start, endMs = end, source = "Line $id", translated = "خط $id")

    /** A 10 minute film: two seconds of dialogue every 30 seconds. */
    private val film = (1..20).map { index -> cue(index, index * 30_000L, index * 30_000L + 2_000) }

    private fun assertClearOfDialogue(credits: List<SignatureInserter.Credit>, cues: List<CueEntity>) {
        credits.forEach { credit ->
            assertTrue("a credit must last at least a moment", credit.endMs > credit.startMs)
            cues.forEach { cue ->
                val overlaps = credit.startMs < cue.endMs && cue.startMs < credit.endMs
                assertFalse("credit ${credit.startMs}..${credit.endMs} hits cue ${cue.id}", overlaps)
            }
        }
    }

    @Test
    fun `it places exactly as many credits as the user asked for`() {
        listOf(1, 3, 5, 8).forEach { count ->
            val credits = SignatureInserter.plan(film, "QW-AI-Code", count)
            assertEquals("count $count", count, credits.size)
            assertTrue(credits.all { it.text == "QW-AI-Code" })
        }
    }

    @Test
    fun `no credit ever touches a dialogue line`() {
        assertClearOfDialogue(SignatureInserter.plan(film, "QW-AI-Code", 8), film)
    }

    @Test
    fun `credits keep a guard band away from the neighbouring dialogue`() {
        val credits = SignatureInserter.plan(film, "QW-AI-Code", 8)

        credits.forEach { credit ->
            val previousEnd = film.filter { it.endMs <= credit.startMs }.maxOfOrNull { it.endMs } ?: 0L
            val nextStart = film.filter { it.startMs >= credit.endMs }.minOfOrNull { it.startMs }
            assertTrue(
                "too close to the previous line",
                credit.startMs - previousEnd >= SignatureInserter.GUARD_MS,
            )
            if (nextStart != null) {
                assertTrue(
                    "too close to the next line",
                    nextStart - credit.endMs >= SignatureInserter.GUARD_MS,
                )
            }
        }
    }

    @Test
    fun `credits never overlap each other`() {
        val credits = SignatureInserter.plan(film, "QW-AI-Code", 10).sortedBy { it.startMs }

        credits.zipWithNext().forEach { (first, second) ->
            assertTrue("two credits on screen at once", first.endMs <= second.startMs)
        }
    }

    @Test
    fun `credits are spread over the whole film, not stacked at the start`() {
        val credits = SignatureInserter.plan(film, "QW-AI-Code", 4)
        val duration = film.maxOf { it.endMs }

        // One per quarter of the running time.
        val quarters = credits.map { (it.startMs * 4 / duration).coerceAtMost(3L) }.toSet()
        assertTrue("credits sit in only ${quarters.size} quarter(s)", quarters.size >= 3)
    }

    @Test
    fun `the same file and text always produce the same placement`() {
        val first = SignatureInserter.plan(film, "QW-AI-Code", 5)
        val second = SignatureInserter.plan(film, "QW-AI-Code", 5)

        // Exporting twice must give byte-identical files.
        assertEquals(first, second)
    }

    @Test
    fun `wall to wall dialogue only gets the credit after the film`() {
        val dense = (0 until 60).map { index -> cue(index + 1, index * 1_000L, index * 1_000L + 1_000) }

        val credits = SignatureInserter.plan(dense, "QW-AI-Code", 5)

        assertEquals(1, credits.size)
        assertTrue("must come after the last line", credits.single().startMs > dense.maxOf { it.endMs })
        assertClearOfDialogue(credits, dense)
    }

    @Test
    fun `a hole that is too short is not used`() {
        // 2 second holes only — shorter than MIN_GAP_MS.
        val tight = (0 until 20).map { index -> cue(index + 1, index * 4_000L, index * 4_000L + 2_000) }

        val credits = SignatureInserter.plan(tight, "QW-AI-Code", 6)

        assertEquals("only the tail is usable", 1, credits.size)
        assertClearOfDialogue(credits, tight)
    }

    @Test
    fun `the opening silence before the first line can host a credit`() {
        val late = listOf(cue(1, 60_000, 62_000), cue(2, 62_500, 64_000))

        val credits = SignatureInserter.plan(late, "QW-AI-Code", 2)

        assertTrue(credits.any { it.endMs < 60_000 })
        assertClearOfDialogue(credits, late)
    }

    @Test
    fun `overlapping cues cannot create a fake gap`() {
        // A long line with short ones inside it: naively comparing neighbours
        // would report a hole that is in fact covered.
        val overlapping = listOf(
            cue(1, 0, 60_000),
            cue(2, 10_000, 12_000),
            cue(3, 30_000, 32_000),
            cue(4, 70_000, 72_000),
        )

        val credits = SignatureInserter.plan(overlapping, "QW-AI-Code", 4)

        assertClearOfDialogue(credits, overlapping)
    }

    @Test
    fun `a blank signature or a count of zero inserts nothing`() {
        assertTrue(SignatureInserter.plan(film, "   ", 3).isEmpty())
        assertTrue(SignatureInserter.plan(film, "", 3).isEmpty())
        assertTrue(SignatureInserter.plan(film, "QW-AI-Code", 0).isEmpty())
    }

    @Test
    fun `the count is capped instead of flooding the file`() {
        val credits = SignatureInserter.plan(film, "QW-AI-Code", 500)

        assertTrue(credits.size <= SignatureInserter.MAX_COUNT)
        assertClearOfDialogue(credits, film)
    }

    @Test
    fun `an empty project still yields a usable credit`() {
        val credits = SignatureInserter.plan(emptyList(), "QW-AI-Code", 3)

        assertEquals(1, credits.size)
        assertTrue(credits.single().startMs > 0)
    }

    @Test
    fun `the signature text is trimmed`() {
        val credits = SignatureInserter.plan(film, "  QW-AI-Code  ", 2)

        assertTrue(credits.all { it.text == "QW-AI-Code" })
    }
}
