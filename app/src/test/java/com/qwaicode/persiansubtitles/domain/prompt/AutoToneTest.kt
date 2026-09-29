package com.qwaicode.persiansubtitles.domain.prompt

import com.qwaicode.persiansubtitles.domain.ai.ContextBrief
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoToneTest {

    @Test
    fun `presets are found by id, case and quotes do not matter`() {
        assertEquals("comedy", StylePresets.find("comedy")?.id)
        assertEquals("anime", StylePresets.find(" \"Anime\" ")?.id)
        assertEquals("crime", StylePresets.find("crime (جنایی)")?.id)
    }

    @Test
    fun `presets are found by their Persian title as well`() {
        assertEquals("cinematic", StylePresets.find("سینمایی و ادبی")?.id)
        assertEquals("documentary", StylePresets.find("مستند")?.id)
    }

    @Test
    fun `an unknown or empty answer is not guessed`() {
        assertNull(StylePresets.find("noir"))
        assertNull(StylePresets.find(""))
        assertNull(StylePresets.find(null))
    }

    @Test
    fun `the brief keeps a valid tone and drops an invented one`() {
        val valid = ContextBrief.parse("""{"summary":"s","tone_preset":"colloquial","tone_reason":"دوستانه"}""")
        assertEquals("colloquial", valid?.tonePreset)
        assertEquals("دوستانه", valid?.toneReason)

        val invented = ContextBrief.parse("""{"summary":"s","tone_preset":"space-opera"}""")
        assertEquals("", invented?.tonePreset)
    }

    @Test
    fun `alternative key names are accepted`() {
        val brief = ContextBrief.parse("""{"summary":"s","preset":"formal","preset_reason":"سخنرانی"}""")
        assertEquals("formal", brief?.tonePreset)
        assertEquals("سخنرانی", brief?.toneReason)
    }

    @Test
    fun `an old stored brief without a tone still loads`() {
        val brief = ContextBrief.fromStorage("""{"title":"t","summary":"s"}""")
        assertEquals("", brief?.tonePreset)
    }

    @Test
    fun `the analysis prompt offers every preset id`() {
        val instruction = PromptBuilder.analysisInstruction()
        StylePresets.all.forEach { assertTrue(it.id, instruction.contains("- ${it.id}:")) }
        assertTrue(instruction.contains("tone_preset"))
    }
}
