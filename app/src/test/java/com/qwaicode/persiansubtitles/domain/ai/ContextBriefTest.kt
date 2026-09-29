package com.qwaicode.persiansubtitles.domain.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextBriefTest {

    @Test
    fun `model json is parsed with aliases and code fences`() {
        val raw = """
            ```json
            {
              "movie": "The House",
              "type": "drama",
              "plot": "A family returns home.",
              "register": "محاوره‌ای",
              "people": [{"en":"Emma", "fa":"اِما", "role":"housekeeper"}],
              "terms": [{"term":"the estate", "translation":"عمارت"}]
            }
            ```
        """.trimIndent()

        val brief = ContextBrief.parse(raw)

        assertNotNull(brief)
        assertEquals("The House", brief!!.title)
        assertEquals("drama", brief.genre)
        assertEquals("A family returns home.", brief.summary)
        assertEquals("اِما", brief.characters.single().persian)
        assertEquals("عمارت", brief.glossary.single().persian)
    }

    @Test
    fun `brief survives project storage round trip`() {
        val original = ContextBrief(
            title = "Film",
            summary = "Story",
            characters = listOf(ContextBrief.Character("Emma", "اِما", "keeper")),
            glossary = listOf(ContextBrief.Term("estate", "عمارت")),
        )

        val restored = ContextBrief.fromStorage(ContextBrief.toStorage(original))

        assertEquals(original, restored)
    }

    @Test
    fun `prompt block is bounded and contains the important context first`() {
        val brief = ContextBrief(
            title = "Film",
            summary = "A short summary",
            notes = "x".repeat(5000),
        )

        val block = brief.toPromptBlock(maxChars = 300)

        assertTrue(block.length <= 300)
        assertTrue(block.contains("Film"))
        assertTrue(block.contains("A short summary"))
    }

    @Test
    fun `invalid or empty model answer is ignored`() {
        assertNull(ContextBrief.parse("not json"))
        assertNull(ContextBrief.parse("{}"))
    }
}
