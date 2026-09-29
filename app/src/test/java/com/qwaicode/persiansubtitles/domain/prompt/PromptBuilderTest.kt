package com.qwaicode.persiansubtitles.domain.prompt

import com.qwaicode.persiansubtitles.data.db.CueEntity
import com.qwaicode.persiansubtitles.data.prefs.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The answer parser is the app's weak point in production: models wrap JSON in code
 * fences, rename keys or fall back to plain text. Every shape here was seen in the wild.
 */
class PromptBuilderTest {

    @Test
    fun `parses a plain json array`() {
        val out = PromptBuilder.parseResponse("""[{"id":1,"fa":"سلام"},{"id":2,"fa":"خداحافظ"}]""")

        assertEquals(2, out.size)
        assertEquals("سلام", out[1])
        assertEquals("خداحافظ", out[2])
    }

    @Test
    fun `parses json wrapped in a code fence`() {
        val raw = "```json\n[{\"id\":7,\"fa\":\"متن\"}]\n```"
        assertEquals("متن", PromptBuilder.parseResponse(raw)[7])
    }

    @Test
    fun `parses an object wrapper around the array`() {
        val raw = """{"translations":[{"id":3,"fa":"یک"},{"id":4,"fa":"دو"}]}"""
        val out = PromptBuilder.parseResponse(raw)

        assertEquals(2, out.size)
        assertEquals("یک", out[3])
    }

    @Test
    fun `accepts alternative key names`() {
        val raw = """[{"index":1,"translation":"الف"},{"n":2,"text":"ب"}]"""
        val out = PromptBuilder.parseResponse(raw)

        assertEquals("الف", out[1])
        assertEquals("ب", out[2])
    }

    @Test
    fun `falls back to pipe separated lines`() {
        val raw = "12| خط اول\n13: خط دوم\nnot a line at all"
        val out = PromptBuilder.parseResponse(raw)

        assertEquals(2, out.size)
        assertEquals("خط اول", out[12])
        assertEquals("خط دوم", out[13])
    }

    @Test
    fun `skips entries with a missing id or empty text`() {
        val raw = """[{"fa":"بی‌شناسه"},{"id":5,"fa":""},{"id":6,"fa":"درست"}]"""
        val out = PromptBuilder.parseResponse(raw)

        assertEquals(1, out.size)
        assertEquals("درست", out[6])
    }

    @Test
    fun `garbage yields an empty map instead of an exception`() {
        assertTrue(PromptBuilder.parseResponse("I cannot help with that.").isEmpty())
        assertTrue(PromptBuilder.parseResponse("").isEmpty())
        assertTrue(PromptBuilder.parseResponse("{").isEmpty())
    }

    @Test
    fun `keeps line breaks inside a cue`() {
        val raw = """[{"id":1,"fa":"خط اول\nخط دوم"}]"""
        assertEquals("خط اول\nخط دوم", PromptBuilder.parseResponse(raw)[1])
    }

    @Test
    fun `batch payload contains every cue id and the context`() {
        val cues = listOf(
            CueEntity(id = 10, startMs = 0, endMs = 1, source = "Hello"),
            CueEntity(id = 11, startMs = 1, endMs = 2, source = "World"),
        )
        val context = listOf(
            CueEntity(id = 9, startMs = 0, endMs = 1, source = "Before", translated = "قبلی")
        )

        val payload = PromptBuilder.batchPayload(cues, context)

        assertTrue(payload.contains("\"lines_to_translate\""))
        assertTrue(payload.contains("\"context_already_translated\""))
        assertTrue(payload.contains("\"id\":10"))
        assertTrue(payload.contains("\"id\":11"))
        assertTrue(payload.contains("قبلی"))
    }

    @Test
    fun `batch payload omits the context key when there is none`() {
        val payload = PromptBuilder.batchPayload(
            listOf(CueEntity(id = 1, startMs = 0, endMs = 1, source = "x")),
            emptyList(),
        )
        assertTrue(!payload.contains("context_already_translated"))
    }

    @Test
    fun `system instruction carries the preset, the custom prompt and the review hint`() {
        val settings = AppSettings(presetId = "colloquial", customPrompt = "لحن جوان‌پسند باشد")

        val normal = PromptBuilder.systemInstruction(settings, reviewMode = false)
        val review = PromptBuilder.systemInstruction(settings, reviewMode = true)

        assertTrue(normal.contains(StylePresets.byId("colloquial").instruction))
        assertTrue(normal.contains("لحن جوان‌پسند باشد"))
        assertTrue(normal.contains("JSON"))
        assertTrue(!normal.contains("حالت بازبینی"))
        assertTrue(review.contains("حالت بازبینی"))
    }

    @Test
    fun `proper names rule flips with the setting`() {
        val keep = PromptBuilder.systemInstruction(AppSettings(keepProperNames = true), false)
        val translate = PromptBuilder.systemInstruction(AppSettings(keepProperNames = false), false)

        assertTrue(keep.contains("ترجمه نکن"))
        assertTrue(translate.contains("معادل رایج فارسی"))
    }

    @Test
    fun `every preset has a unique id and non empty texts`() {
        val ids = StylePresets.all.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        assertTrue(StylePresets.all.size >= 10)
        StylePresets.all.forEach {
            assertTrue(it.title.isNotBlank())
            assertTrue(it.description.isNotBlank())
            assertTrue(it.instruction.isNotBlank())
        }
        // an unknown id must never crash the engine
        assertNotNull(StylePresets.byId("does-not-exist"))
        assertEquals(StylePresets.default.id, StylePresets.byId("does-not-exist").id)
    }
}
