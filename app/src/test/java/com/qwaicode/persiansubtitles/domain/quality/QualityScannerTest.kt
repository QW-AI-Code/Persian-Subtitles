package com.qwaicode.persiansubtitles.domain.quality

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which finished lines the editorial pass has to look at — and, just as important,
 * which ones it must leave alone. A scanner that flags correct lines would send the
 * whole file back to Gemini and burn the free quota for nothing.
 */
class QualityScannerTest {

    @Test
    fun `a clean translation has no issue at all`() {
        assertTrue(QualityScanner.issues("Good morning.", "صبح بخیر.").isEmpty())
        assertFalse(QualityScanner.needsModel("Good morning.", "صبح بخیر."))
    }

    @Test
    fun `a missing translation is reported`() {
        assertTrue(QualityScanner.issues("Hello", null).contains(QualityScanner.Issue.EMPTY))
        assertTrue(QualityScanner.issues("Hello", "   ").contains(QualityScanner.Issue.EMPTY))
    }

    @Test
    fun `the english source left in place is reported`() {
        val issues = QualityScanner.issues("What are you doing here?", "What are you doing here?")

        assertTrue(issues.contains(QualityScanner.Issue.SAME_AS_SOURCE))
        assertTrue(issues.contains(QualityScanner.Issue.UNTRANSLATED_WORD))
        assertTrue(QualityScanner.needsModel("What are you doing here?", "What are you doing here?"))
    }

    @Test
    fun `a single english word left inside a persian line is reported`() {
        val issues = QualityScanner.issues("I will never forget it.", "هرگز آن را forget نمی‌کنم.")

        assertTrue(issues.contains(QualityScanner.Issue.UNTRANSLATED_WORD))
    }

    @Test
    fun `a proper name in latin is not a leftover`() {
        // This is what the "keep proper names" setting produces, and it is correct.
        val issues = QualityScanner.issues("Jason went home.", "Jason به خانه رفت.")

        assertFalse(issues.contains(QualityScanner.Issue.UNTRANSLATED_WORD))
        assertFalse(QualityScanner.needsModel("Jason went home.", "Jason به خانه رفت."))
    }

    @Test
    fun `a url or a handle is not treated as an untranslated word`() {
        val issues = QualityScanner.issues("Visit example.com", "به example.com سر بزنید")

        assertFalse(issues.contains(QualityScanner.Issue.UNTRANSLATED_WORD))
    }

    @Test
    fun `a latin letter glued to persian letters is reported`() {
        val issues = QualityScanner.issues("Come here.", "بیا اینجاs")

        assertTrue(issues.contains(QualityScanner.Issue.STRAY_LATIN))
    }

    @Test
    fun `a truncated answer is reported`() {
        val source = "He told me the whole story about what happened that night."
        val issues = QualityScanner.issues(source, "او گفت")

        assertTrue(issues.contains(QualityScanner.Issue.TRUNCATED))
    }

    @Test
    fun `a doubled word is reported`() {
        val issues = QualityScanner.issues("I am going home.", "من به خانه خانه می‌روم")

        assertTrue(issues.contains(QualityScanner.Issue.REPEATED_WORD))
    }

    @Test
    fun `arabic letters are fixable without the model`() {
        val issues = QualityScanner.issues("The book", "كتاب")

        assertTrue(issues.contains(QualityScanner.Issue.NORMALIZABLE))
        assertFalse("a letter shape is not worth a request", QualityScanner.needsModel("The book", "كتاب"))
        assertEquals("کتاب", QualityScanner.autoFix("كتاب"))
    }

    @Test
    fun `formatting tags never count as a defect`() {
        assertFalse(QualityScanner.needsModel("<i>Good morning.</i>", "<i>صبح بخیر.</i>"))
        assertFalse(QualityScanner.needsModel("{\\an8}Hello", "{\\an8}سلام"))
    }

    @Test
    fun `the description names the problems in persian`() {
        val issues = QualityScanner.issues("What are you doing?", "What are you doing?")
        val text = QualityScanner.describe(issues)

        assertTrue(text.isNotBlank())
        assertFalse("only model-fixable problems belong in the prompt", text.contains("فاصله‌گذاری"))
    }
}
