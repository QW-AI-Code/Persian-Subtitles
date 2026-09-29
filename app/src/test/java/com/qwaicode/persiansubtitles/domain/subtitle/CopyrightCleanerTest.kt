package com.qwaicode.persiansubtitles.domain.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Credit detection must be aggressive on obvious ad lines and cautious on real
 * dialogue — a wrongly removed line is worse than a kept credit.
 */
class CopyrightCleanerTest {

    private val patterns = CopyrightCleaner.compile(CopyrightCleaner.DEFAULT_PATTERNS_TEXT)

    private fun isAd(text: String, position: Int = 1, total: Int = 100, edgesOnly: Boolean = false) =
        CopyrightCleaner.isAd(text, patterns, position, total, edgesOnly)

    @Test
    fun `default patterns all compile`() {
        assertEquals(CopyrightCleaner.DEFAULT_PATTERNS.size, patterns.size)
    }

    @Test
    fun `detects typical credit lines`() {
        assertTrue(isAd("زیرنویس اختصاصی از فیلم‌بین"))
        assertTrue(isAd("مترجم: علی رضایی"))
        assertTrue(isAd("کانال تلگرام ما @MovieChannel"))
        assertTrue(isAd("Subtitles by explosiveskull"))
        assertTrue(isAd("Synced by bozxphd"))
        assertTrue(isAd("www.opensubtitles.org"))
        assertTrue(isAd("© 2019 Netflix"))
        assertTrue(isAd("t.me/somechannel"))
    }

    @Test
    fun `keeps normal dialogue`() {
        assertFalse(isAd("I told you not to come here."))
        assertFalse(isAd("فردا صبح می‌بینمت."))
        assertFalse(isAd("چه ساعتی قطار حرکت می‌کند؟"))
        assertFalse(isAd("- بله؟\n- منم."))
    }

    @Test
    fun `music and dash only cues are never ads`() {
        assertFalse(isAd("♪♪"))
        assertFalse(isAd("- -"))
        assertFalse(isAd("   "))
    }

    @Test
    fun `edge only mode protects the middle of the film`() {
        val total = 1000
        // same text, once in the middle, once at the very end
        assertFalse(isAd("زیرنویس اختصاصی", position = 500, total = total, edgesOnly = true))
        assertTrue(isAd("زیرنویس اختصاصی", position = 3, total = total, edgesOnly = true))
        assertTrue(isAd("زیرنویس اختصاصی", position = 995, total = total, edgesOnly = true))
    }

    @Test
    fun `edge only mode is skipped on very short files`() {
        // With only 20 cues everything is an "edge", so the window must not apply.
        assertTrue(isAd("زیرنویس اختصاصی", position = 10, total = 20, edgesOnly = true))
    }

    @Test
    fun `long dialogue that merely mentions a keyword is kept`() {
        val line = "او تمام شب بیدار ماند و درباره ترجمه آن نامه قدیمی فکر کرد تا " +
            "سرانجام تصمیم گرفت صبح زود به دیدن استادش برود و همه چیز را برای او تعریف کند."
        assertFalse(isAd(line))
    }

    @Test
    fun `no patterns means nothing is detected`() {
        assertFalse(CopyrightCleaner.isAd("زیرنویس اختصاصی", emptyList(), 1, 100, false))
    }

    @Test
    fun `invalid regex lines are skipped instead of crashing`() {
        val compiled = CopyrightCleaner.compile("(unclosed\ntelegram\n# a comment\n\n")
        assertEquals(1, compiled.size)
        assertTrue(compiled.first().containsMatchIn("join our telegram"))
    }

    @Test
    fun `strips html and ass tags but keeps the text`() {
        assertEquals("سلام دنیا", CopyrightCleaner.stripTags("<i>سلام</i> {\\an8}دنیا"))
        assertEquals("hello", CopyrightCleaner.stripTags("<font color=\"#ffffff\">hello</font>"))
        assertEquals("a b", CopyrightCleaner.stripTags("a    b"))
    }
}
