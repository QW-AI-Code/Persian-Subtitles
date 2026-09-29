package com.qwaicode.persiansubtitles.domain.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How a mixed Persian/English line is written into the exported file.
 *
 * The bug this exists for: a Persian line with an English name or a number in it came
 * out scrambled in players, because the punctuation next to the Latin run was pulled
 * to the wrong side. An RLM at the two ends of the line cannot fix that — the
 * conflict is in the middle of the line, so the run itself has to be fenced off.
 *
 * The second bug, reported from KMPlayer: the isolate characters U+2068/U+2069 were
 * drawn as two empty boxes around the name. Those characters are correct Unicode but
 * younger than many players, so the default mode uses the zero-width RLM instead,
 * which no player has ever shown as a glyph.
 */
class BidiIsolateTest {

    private fun smart(line: String) =
        BidiShaper.shapeLine(line, BidiMode.SMART, persianPunctuation = false)

    private fun isolate(line: String) =
        BidiShaper.shapeLine(line, BidiMode.ISOLATE, persianPunctuation = false)

    @Test
    fun `smart is the default mode`() {
        assertEquals(BidiMode.SMART, BidiMode.byId(null))
        assertEquals(BidiMode.SMART, BidiMode.byId("something else"))
        // …but a stored choice is still honoured.
        assertEquals(BidiMode.ISOLATE, BidiMode.byId("isolate"))
        assertEquals(BidiMode.STRONG, BidiMode.byId("strong"))
        assertEquals(BidiMode.NONE, BidiMode.byId("none"))
    }

    @Test
    fun `the default output never contains an isolate character`() {
        // This is what showed up as an empty box in the player.
        val lines = listOf(
            "به John گفتم که بیاید.",
            "فیلم Dark Matter را دیدم",
            "به example.com سر بزنید",
            "<i>سلام John</i>",
            "★ QW-AI-Code ★ زیرنویس اختصاصی",
        )
        lines.forEach { line ->
            val out = smart(line)
            assertFalse(line, out.contains(BidiShaper.FSI))
            assertFalse(line, out.contains(BidiShaper.PDI))
        }
    }

    @Test
    fun `a latin word inside a persian line is fenced with rtl marks`() {
        val out = smart("به John گفتم که بیاید.")

        assertTrue(out.startsWith(BidiShaper.RLM.toString()))
        assertTrue(out.endsWith(BidiShaper.RLM.toString()))
        assertTrue(out.contains("${BidiShaper.RLM}John${BidiShaper.RLM}"))
    }

    @Test
    fun `the punctuation after a fenced name stays with the persian sentence`() {
        val out = smart("سلام John، حالت چطوره")

        assertTrue(out.contains("${BidiShaper.RLM}John${BidiShaper.RLM}،"))
    }

    @Test
    fun `several latin words in a row are fenced once`() {
        val out = smart("فیلم Dark Matter را دیدم")

        assertTrue(out.contains("${BidiShaper.RLM}Dark Matter${BidiShaper.RLM}"))
        // Two marks for the run plus one at each end of the line.
        assertEquals(4, out.count { it == BidiShaper.RLM })
    }

    @Test
    fun `a url keeps its dots inside the fence`() {
        val out = smart("به example.com سر بزنید")

        assertTrue(out.contains("${BidiShaper.RLM}example.com${BidiShaper.RLM}"))
    }

    @Test
    fun `formatting tags stay outside the fence`() {
        val out = smart("<i>سلام John</i>")

        assertTrue(out.contains("<i>"))
        assertTrue(out.contains("</i>"))
        assertFalse("the tag itself must not be fenced", out.contains("${BidiShaper.RLM}i"))
    }

    @Test
    fun `a bare number is not fenced`() {
        val out = smart("ساعت 5 می‌بینمت")

        assertEquals("only the two marks at the ends", 2, out.count { it == BidiShaper.RLM })
    }

    @Test
    fun `a persian only line gets the marks and nothing else`() {
        assertEquals(
            "${BidiShaper.RLM}قرار ملاقات دارم.${BidiShaper.RLM}",
            smart("قرار ملاقات دارم."),
        )
    }

    @Test
    fun `an english line is never touched`() {
        val line = "See you tomorrow, John."

        assertEquals(line, smart(line))
        assertEquals(line, isolate(line))
    }

    @Test
    fun `the isolate mode is still available for players that support it`() {
        val out = isolate("به John گفتم که بیاید.")

        assertTrue(out.contains("${BidiShaper.FSI}John${BidiShaper.PDI}"))
    }

    @Test
    fun `every mode can be stripped back to plain text`() {
        val line = "به John گفتم."
        BidiMode.entries.forEach { mode ->
            val shaped = BidiShaper.shapeLine(line, mode, persianPunctuation = false)
            assertEquals("$mode was not reversible", line, BidiShaper.strip(shaped))
        }
    }

    @Test
    fun `each line of a cue is shaped on its own`() {
        val out = BidiShaper.shape("سلام John\nSecond line", BidiMode.SMART, persianPunctuation = false)
        val lines = out.split("\n")

        assertEquals(2, lines.size)
        assertTrue(lines[0].contains(BidiShaper.RLM))
        assertEquals("Second line", lines[1])
    }

    @Test
    fun `a credit line is written exactly as it was composed`() {
        // A credit mixes a Latin name, Persian words and ornaments. It is the one line
        // where a player with a weak BiDi implementation is guaranteed to disagree with
        // a correct one, so no mark is added at all and the template's own order
        // decides the picture.
        val credit = "★ QW-AI-Code ★ زیرنویس اختصاصی"

        assertEquals(credit, BidiShaper.shapeCredit(credit))
        assertEquals(credit, BidiShaper.shapeCredit("${BidiShaper.RLM}$credit${BidiShaper.PDI}"))
    }
}
