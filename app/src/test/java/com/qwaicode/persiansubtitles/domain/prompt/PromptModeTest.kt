package com.qwaicode.persiansubtitles.domain.prompt

import com.qwaicode.persiansubtitles.data.prefs.AppSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which style instruction actually reaches Gemini.
 *
 * The complaint behind this: writing an own prompt did not switch the tone presets
 * off, so the request carried two competing style briefs and the preset kept
 * colouring a translation the user thought they had fully specified.
 */
class PromptModeTest {

    private val custom = "فقط با لحن رسمی و کوتاه ترجمه کن"

    @Test
    fun `preset mode sends the preset and the custom prompt on top`() {
        val settings = AppSettings(
            promptMode = PromptMode.PRESET,
            presetId = "colloquial",
            customPrompt = custom,
        )

        val instruction = PromptBuilder.systemInstruction(settings, reviewMode = false)

        assertTrue(instruction.contains(StylePresets.byId("colloquial").instruction))
        assertTrue(instruction.contains(custom))
        assertFalse(PromptBuilder.customOnly(settings))
    }

    @Test
    fun `custom only mode sends no preset at all`() {
        val settings = AppSettings(
            promptMode = PromptMode.CUSTOM_ONLY,
            presetId = "colloquial",
            customPrompt = custom,
        )

        val instruction = PromptBuilder.systemInstruction(settings, reviewMode = false)

        assertTrue(PromptBuilder.customOnly(settings))
        assertTrue(instruction.contains(custom))
        assertFalse(
            "the preset must not travel with the request any more",
            instruction.contains(StylePresets.byId("colloquial").instruction),
        )
        // …and none of the other presets sneaks in either.
        StylePresets.all.forEach { preset ->
            assertFalse(preset.id, instruction.contains(preset.instruction))
        }
    }

    @Test
    fun `an empty custom prompt falls back to the preset instead of no style`() {
        val settings = AppSettings(
            promptMode = PromptMode.CUSTOM_ONLY,
            presetId = "cinematic",
            customPrompt = "   ",
        )

        val instruction = PromptBuilder.systemInstruction(settings, reviewMode = false)

        assertFalse(PromptBuilder.customOnly(settings))
        assertTrue(instruction.contains(StylePresets.byId("cinematic").instruction))
    }

    @Test
    fun `the mode survives being stored as a string`() {
        PromptMode.entries.forEach { mode ->
            assertTrue(PromptMode.byId(mode.id) == mode)
        }
        assertTrue(PromptMode.byId(null) == PromptMode.PRESET)
        assertTrue(PromptMode.byId("nonsense") == PromptMode.PRESET)
    }

    @Test
    fun `the polish instruction forbids rewriting a correct line`() {
        val text = PromptBuilder.polishInstruction(AppSettings())

        assertTrue(text.contains("بازنویسی سلیقه‌ای ممنوع است"))
        assertTrue(text.contains("JSON"))
    }

    @Test
    fun `the polish payload carries the original, the translation and the problems`() {
        val payload = PromptBuilder.polishPayload(
            listOf(
                PromptBuilder.PolishItem(
                    id = 7,
                    source = "What are you doing?",
                    translated = "What are you doing?",
                    issues = "کلمهٔ انگلیسی ترجمه‌نشده",
                )
            )
        )

        assertTrue(payload.contains("\"lines_to_fix\""))
        assertTrue(payload.contains("\"id\":7"))
        assertTrue(payload.contains("What are you doing?"))
        assertTrue(payload.contains("problems"))
    }
}
