package com.qwaicode.persiansubtitles.domain.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The mechanical half of the post-translation clean-up. Every case here is something
 * a model actually produces when it writes Persian.
 */
class PersianNormalizerTest {

    @Test
    fun `arabic letters become their persian shapes`() {
        assertEquals("کتاب", PersianNormalizer.normalize("كتاب"))
        assertEquals("ایران", PersianNormalizer.normalize("ايران"))
        assertEquals("زیبا", PersianNormalizer.normalize("زيبا"))
        // teh marbuta, which Persian writes as a plain heh
        assertEquals("مدرسه", PersianNormalizer.normalize("مدرسة"))
    }

    @Test
    fun `arabic indic digits become ascii`() {
        assertEquals("سال 1990", PersianNormalizer.normalize("سال ١٩٩٠"))
    }

    @Test
    fun `tatweel and diacritics are dropped`() {
        assertEquals("سلام", PersianNormalizer.normalize("سـ__لام".replace("__", "ـ")))
        assertEquals("کتاب", PersianNormalizer.normalize("كِتاب"))
    }

    @Test
    fun `the verb prefix gets its zero width non joiner`() {
        assertEquals("می‌روم", PersianNormalizer.normalize("می روم"))
        assertEquals("نمی‌دانم", PersianNormalizer.normalize("نمی دانم"))
        // A one-letter word after it is not a verb stem: «می و معشوق» must survive.
        assertEquals("می و معشوق", PersianNormalizer.normalize("می و معشوق"))
    }

    @Test
    fun `spaces around a zero width non joiner collapse into it`() {
        assertEquals("کتاب‌ها", PersianNormalizer.normalize("کتاب ‌ها"))
    }

    @Test
    fun `punctuation spacing is repaired`() {
        assertEquals("سلام، خوبی؟", PersianNormalizer.normalize("سلام ،خوبی ؟"))
        assertEquals("بله، حتماً", PersianNormalizer.normalize("بله،حتماً"))
        assertEquals("یک دو", PersianNormalizer.normalize("یک    دو"))
    }

    @Test
    fun `the tanwin of everyday persian words is kept`() {
        // «حتماً» and «لطفاً» are spelled with a tanwin. Treating it as a stray
        // diacritic and removing it would *create* the spelling mistake this class
        // exists to remove.
        listOf("حتماً", "لطفاً", "معمولاً", "اصلاً", "واقعاً").forEach { word ->
            assertEquals(word, PersianNormalizer.normalize(word))
            assertTrue(word, PersianNormalizer.isClean(word))
        }
        assertEquals("بله، حتماً", PersianNormalizer.normalize("بله،حتماً"))
    }

    @Test
    fun `already correct text is returned unchanged`() {
        val clean = listOf(
            "سلام دنیا",
            "می‌روم خانه.",
            "ترجمه 1",
            "See you tomorrow.",
            "به John گفتم که بیاید.",
        )
        clean.forEach { line ->
            assertEquals(line, PersianNormalizer.normalize(line))
            assertTrue("$line was reported dirty", PersianNormalizer.isClean(line))
        }
    }

    @Test
    fun `every line of a cue is normalized on its own`() {
        assertEquals(
            "کتاب\nمی‌روم",
            PersianNormalizer.normalize("كتاب\nمی روم"),
        )
    }

    @Test
    fun `dirty text is reported as such`() {
        assertFalse(PersianNormalizer.isClean("كتاب"))
        assertFalse(PersianNormalizer.isClean("سلام ،"))
    }

    @Test
    fun `an empty or blank line does not turn into junk`() {
        assertEquals("", PersianNormalizer.normalize(""))
        assertEquals("", PersianNormalizer.normalize("   "))
    }
}
