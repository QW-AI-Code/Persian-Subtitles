package com.qwaicode.persiansubtitles.domain.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleDigestTest {

    @Test
    fun `short subtitle is kept in order without a gap`() {
        val lines = listOf("  اول  ", "", "دوم", "سوم")
        assertEquals("اول\nدوم\nسوم", SubtitleDigest.build(lines, budget = 1000))
    }

    @Test
    fun `long subtitle samples consecutive windows deterministically`() {
        val lines = (1..500).map { "line-$it" }
        val first = SubtitleDigest.build(lines, budget = 2400)
        val second = SubtitleDigest.build(lines, budget = 2400)

        assertEquals(first, second)
        assertTrue(first.contains("line-1"))
        assertTrue(first.contains("line-500"))
        assertTrue(first.contains(SubtitleDigest.GAP))
        // A sampled part is a consecutive window, not isolated random lines.
        assertTrue(first.contains("line-1\nline-2\nline-3"))
    }

    @Test
    fun `empty subtitle produces empty digest`() {
        assertEquals("", SubtitleDigest.build(listOf(" ", "\n")))
    }
}
