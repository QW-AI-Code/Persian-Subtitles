package com.qwaicode.persiansubtitles.domain.subtitle

import com.qwaicode.persiansubtitles.data.db.CueEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The export-time sync offset: a subtitle that runs early or late against the video. */
class TimeShiftTest {

    private val cues = listOf(
        CueEntity(id = 1, startMs = 500, endMs = 1_500, source = "First", translated = "اول"),
        CueEntity(id = 2, startMs = 3_000, endMs = 4_000, source = "Second", translated = "دوم"),
        CueEntity(id = 3, startMs = 10_000, endMs = 12_000, source = "Third", translated = "سوم"),
    )

    @Test
    fun `no shift leaves every timing exactly as it was`() {
        val out = SubtitleExporter.build(cues, ExportFormat.SRT, dropAds = true, signature = null, shiftMs = 0)
        assertTrue(out.contains("00:00:03,000 --> 00:00:04,000"))
    }

    @Test
    fun `a positive shift moves every cue later`() {
        val out = SubtitleExporter.build(cues, ExportFormat.SRT, dropAds = true, signature = null, shiftMs = 1_250)
        assertTrue(out.contains("00:00:01,750 --> 00:00:02,750"))
        assertTrue(out.contains("00:00:11,250 --> 00:00:13,250"))
    }

    @Test
    fun `a negative shift clips at zero and drops cues pushed before the start`() {
        val out = SubtitleExporter.build(cues, ExportFormat.SRT, dropAds = true, signature = null, shiftMs = -3_500)

        // Cue 1 ended before 0:00 and is gone; cue 2 starts before 0:00 and is clipped.
        assertFalse(out.contains("اول"))
        assertTrue(out.contains("00:00:00,000 --> 00:00:00,500"))
        assertTrue(out.contains("00:00:06,500 --> 00:00:08,500"))
        // Numbering stays sequential after the dropped cue.
        assertTrue(out.trim().startsWith("1\n"))
        assertEquals(2, out.trim().split("\n\n").size)
    }

    @Test
    fun `vtt is shifted the same way`() {
        val out = SubtitleExporter.build(cues, ExportFormat.VTT, dropAds = true, signature = null, shiftMs = -500)
        assertTrue(out.contains("00:00:00.000 --> 00:00:01.000"))
        assertTrue(out.contains("00:00:09.500 --> 00:00:11.500"))
    }
}
