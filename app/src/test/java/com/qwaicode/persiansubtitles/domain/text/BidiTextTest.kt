package com.qwaicode.persiansubtitles.domain.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules that keep mixed Persian/English text from scrambling — in the input
 * fields, in the editor list and in the exported file.
 */
class BidiTextTest {

    @Test
    fun `a persian line is right to left`() {
        assertTrue(BidiText.firstStrongIsRtl("سلام دنیا"))
    }

    @Test
    fun `an english line is left to right`() {
        assertFalse(BidiText.firstStrongIsRtl("Hello world"))
    }

    @Test
    fun `leading punctuation and digits do not decide the direction`() {
        // This is the case that used to break: typing "(" or a digit first made
        // the field jump around.
        assertFalse(BidiText.firstStrongIsRtl("«Hello» سلام"))
        assertTrue(BidiText.firstStrongIsRtl("«سلام» Hello"))
        assertFalse(BidiText.firstStrongIsRtl("... 1997 Hello"))
        assertTrue(BidiText.firstStrongIsRtl("... 1997 سلام"))
        assertFalse(BidiText.firstStrongIsRtl("<i>Hello</i> سلام"))
    }

    @Test
    fun `text without any letter keeps the persian default`() {
        assertTrue(BidiText.firstStrongIsRtl(""))
        assertTrue(BidiText.firstStrongIsRtl("   "))
        assertTrue(BidiText.firstStrongIsRtl("12:34"))
        assertTrue(BidiText.firstStrongIsRtl("!!! ..."))
        // …unless the caller wants an LTR default (file names, API keys).
        assertFalse(BidiText.firstStrongIsRtl("", default = false))
    }

    @Test
    fun `script detection`() {
        assertTrue(BidiText.containsRtl("سلام"))
        assertTrue(BidiText.containsRtl("Hello سلام"))
        assertFalse(BidiText.containsRtl("Hello 123 !"))
        assertTrue(BidiText.containsLatin("Hello سلام"))
        assertFalse(BidiText.containsLatin("سلام ۱۲۳"))
    }

    @Test
    fun `a persian line is wrapped and closed for the player`() {
        val out = BidiText.forPlayer("سلام دنیا.")

        assertEquals("\u202Bسلام دنیا.\u202C", out)
    }

    @Test
    fun `an english line is handed to the player untouched`() {
        val line = "See you tomorrow, John."

        assertEquals(line, BidiText.forPlayer(line))
    }

    @Test
    fun `a mixed line is wrapped once, keeping the latin part intact`() {
        val out = BidiText.forPlayer("به example.com سر بزنید.")

        assertTrue(out.startsWith(BidiText.RLE))
        assertTrue(out.endsWith(BidiText.PDF))
        assertTrue(out.contains("example.com"))
        assertEquals(1, out.count { it == BidiText.RLE })
        assertEquals(1, out.count { it == BidiText.PDF })
    }

    @Test
    fun `every line of a cue is treated on its own`() {
        val out = BidiText.forPlayer("سلام\nHello\nخداحافظ")

        val lines = out.split("\n")
        assertEquals(3, lines.size)
        assertEquals("\u202Bسلام\u202C", lines[0])
        assertEquals("Hello", lines[1])
        assertEquals("\u202Bخداحافظ\u202C", lines[2])
    }

    @Test
    fun `an empty line stays empty instead of collecting marks`() {
        assertEquals("سلام\n\nخداحافظ".let(BidiText::forPlayer).split("\n")[1], "")
    }

    @Test
    fun `marks can be removed again`() {
        val wrapped = BidiText.forPlayer("سلام دنیا")

        assertEquals("سلام دنیا", BidiText.stripMarks(wrapped))
    }
}
