package com.qwaicode.persiansubtitles.domain.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BidiShaperTest {

    @Test
    fun `english is never forced into rtl`() {
        val line = "See you tomorrow."
        assertEquals(line, BidiShaper.shapeLine(line, BidiMode.SMART, persianPunctuation = true))
    }

    @Test
    fun `smart mode puts strong rtl marks around persian`() {
        val out = BidiShaper.shapeLine("قرار ملاقات دارم.", BidiMode.SMART, persianPunctuation = true)
        assertTrue(out.startsWith(BidiShaper.RLM.toString()))
        assertTrue(out.endsWith(BidiShaper.RLM.toString()))
        assertTrue(out.contains("قرار ملاقات دارم."))
        assertFalse(out.contains(BidiText.RLE))
    }

    @Test
    fun `strong mode additionally embeds the line`() {
        val out = BidiShaper.shapeLine("سلام دنیا", BidiMode.STRONG, persianPunctuation = true)
        assertTrue(out.startsWith("${BidiShaper.RLM}${BidiText.RLE}"))
        assertTrue(out.endsWith("${BidiText.PDF}${BidiShaper.RLM}"))
    }

    @Test
    fun `visual brackets are repaired and punctuation becomes persian`() {
        assertEquals("[زن ۱]", BidiShaper.repair("]زن ۱["))
        assertEquals("سلام؟", BidiShaper.shapeLine("سلام?", BidiMode.NONE, persianPunctuation = true))
    }

    @Test
    fun `latin punctuation inside a latin phrase stays latin`() {
        val out = BidiShaper.shapeLine("سلام Hello, world?", BidiMode.NONE, persianPunctuation = true)
        assertEquals("سلام Hello, world?", out)
    }

    @Test
    fun `direction controls are removed before shaping`() {
        val marked = "\u200Fسلام\u202B دنیا\u202C"
        assertEquals("سلام دنیا", BidiShaper.strip(marked))
    }
}
