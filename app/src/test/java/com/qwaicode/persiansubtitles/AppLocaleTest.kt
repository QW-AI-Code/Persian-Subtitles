package com.qwaicode.persiansubtitles

import org.junit.Assert.assertEquals
import org.junit.Test

/** The UI shows Persian-Indic digits; exported subtitle files must not. */
class AppLocaleTest {

    @Test
    fun `digits are converted`() {
        assertEquals("۱۲۳۴۵۶۷۸۹۰", "1234567890".toPersianDigits())
        assertEquals("۴۲", 42.fa())
        assertEquals("۰", 0.fa())
    }

    @Test
    fun `non digit text is returned unchanged`() {
        assertEquals("سلام", "سلام".toPersianDigits())
        assertEquals("", "".toPersianDigits())
    }

    @Test
    fun `digits inside mixed text are converted, letters are kept`() {
        assertEquals("خط ۱۲ از ۳۴۰", "خط 12 از 340".toPersianDigits())
        assertEquals("۰۰:۰۱:۰۲", "00:01:02".toPersianDigits())
    }

    @Test
    fun `large counts get the persian thousands separator`() {
        assertEquals("۱٬۲۳۴", 1234.faGrouped())
        assertEquals("۱۲٬۳۴۵٬۶۷۸", 12345678.faGrouped())
        assertEquals("۹۹۹", 999.faGrouped())
    }

    @Test
    fun `the forced locale is persian`() {
        assertEquals("fa", AppLocale.PERSIAN.language)
    }
}
