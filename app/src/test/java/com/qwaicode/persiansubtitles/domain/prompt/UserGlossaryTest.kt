package com.qwaicode.persiansubtitles.domain.prompt

import com.qwaicode.persiansubtitles.data.prefs.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserGlossaryTest {

    @Test
    fun `every usual separator is understood`() {
        val entries = UserGlossary.parse(
            """
            Winterfell = وینترفل
            the Watch => نگهبانان شب
            Hodor → هودور
            Maester: استاد
            """.trimIndent()
        )
        assertEquals(listOf("Winterfell", "the Watch", "Hodor", "Maester"), entries.map { it.source })
        assertEquals("نگهبانان شب", entries[1].target)
    }

    @Test
    fun `comments, blank lines and half entries are ignored`() {
        val entries = UserGlossary.parse("# names\n\nJon =\n= جان\nArya = آریا")
        assertEquals(1, entries.size)
        assertEquals("Arya", entries.single().source)
    }

    @Test
    fun `the first definition of a term wins`() {
        val entries = UserGlossary.parse("Stark = استارک\nstark = استارك")
        assertEquals("استارک", entries.single().target)
    }

    @Test
    fun `an equals sign is preferred over a colon inside the term`() {
        val entry = UserGlossary.parse("Dr. Who: The Movie = دکتر هو").single()
        assertEquals("Dr. Who: The Movie", entry.source)
    }

    @Test
    fun `the glossary goes into every batch prompt before the brief`() {
        val settings = AppSettings(userGlossary = "Winterfell = وینترفل")
        val prompt = PromptBuilder.systemInstruction(settings, reviewMode = false)
        assertTrue(prompt.contains("واژه‌نامهٔ اختصاصی کاربر"))
        assertTrue(prompt.contains("- Winterfell = وینترفل"))
        // …and into the proofreading pass, so the editor does not undo it.
        assertTrue(PromptBuilder.polishInstruction(settings).contains("- Winterfell = وینترفل"))
    }

    @Test
    fun `an empty glossary adds nothing to the prompt`() {
        val prompt = PromptBuilder.systemInstruction(AppSettings(), reviewMode = false)
        assertFalse(prompt.contains("واژه‌نامهٔ اختصاصی کاربر"))
    }
}
