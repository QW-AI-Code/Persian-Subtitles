package com.qwaicode.persiansubtitles.domain.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parser is the part that fails on real-world files: fan-subbed subtitles have
 * CRLF endings, missing cue numbers, BOMs and odd timestamps. These tests pin that
 * behaviour down.
 */
class SubtitleIOTest {

    @Test
    fun `parses plain srt`() {
        val src = """
            1
            00:00:01,000 --> 00:00:03,500
            Hello there.

            2
            00:00:04,000 --> 00:00:06,000
            General Kenobi!
        """.trimIndent()

        val cues = SubtitleIO.parse(src)

        assertEquals(2, cues.size)
        assertEquals(1, cues[0].index)
        assertEquals(1_000L, cues[0].startMs)
        assertEquals(3_500L, cues[0].endMs)
        assertEquals("Hello there.", cues[0].text)
        assertEquals("General Kenobi!", cues[1].text)
    }

    @Test
    fun `keeps multi-line cue text`() {
        val src = "1\n00:00:01,000 --> 00:00:02,000\nfirst line\nsecond line\n"
        val cues = SubtitleIO.parse(src)

        assertEquals(1, cues.size)
        assertEquals("first line\nsecond line", cues[0].text)
    }

    @Test
    fun `tolerates BOM, CRLF and missing cue numbers`() {
        val src = "\uFEFF00:00:01,000 --> 00:00:02,000\r\nno number here\r\n\r\n" +
            "00:00:03,000 --> 00:00:04,000\r\nsecond\r\n"

        val cues = SubtitleIO.parse(src)

        assertEquals(2, cues.size)
        // numbering is generated, so the export is always sequential
        assertEquals(listOf(1, 2), cues.map { it.index })
        assertEquals("no number here", cues[0].text)
    }

    @Test
    fun `parses webvtt with header, note and cue settings`() {
        val src = """
            WEBVTT

            NOTE this file was machine generated

            1
            00:00:01.000 --> 00:00:02.000 line:0 position:50%
            Salam

            00:01:00.500 --> 00:01:02.000
            Khodahafez
        """.trimIndent()

        val cues = SubtitleIO.parse(src)

        assertEquals(2, cues.size)
        assertEquals("Salam", cues[0].text)
        assertEquals(60_500L, cues[1].startMs)
        assertEquals("Khodahafez", cues[1].text)
    }

    @Test
    fun `skips blocks without timing or text`() {
        val src = "1\n00:00:01,000 --> 00:00:02,000\n\n\nrandom text without timing\n\n" +
            "2\n00:00:05,000 --> 00:00:06,000\nreal line\n"

        val cues = SubtitleIO.parse(src)

        assertEquals(1, cues.size)
        assertEquals("real line", cues[0].text)
    }

    @Test
    fun `parses timestamps without hours and with two-digit millis`() {
        assertEquals(62_500L, SubtitleIO.parseTime("01:02.5"))
        assertEquals(3_723_040L, SubtitleIO.parseTime("01:02:03,04"))
    }

    @Test
    fun `formats time with ascii digits`() {
        assertEquals("00:00:01,500", SubtitleIO.formatSrtTime(1_500L))
        assertEquals("01:02:03.004", SubtitleIO.formatVttTime(3_723_004L))
        // negative input must not produce a broken timestamp
        assertEquals("00:00:00,000", SubtitleIO.formatSrtTime(-5L))
    }

    @Test
    fun `round trip keeps timings`() {
        val src = "1\n00:01:02,345 --> 00:01:04,000\nline\n"
        val cue = SubtitleIO.parse(src).single()

        assertEquals("00:01:02,345", SubtitleIO.formatSrtTime(cue.startMs))
    }

    @Test
    fun `detects format from content and file name`() {
        assertEquals("vtt", SubtitleIO.detectFormat("movie.txt", "WEBVTT\n\n"))
        assertEquals("vtt", SubtitleIO.detectFormat("movie.VTT", "00:00:01,000 --> 00:00:02,000\nx"))
        assertEquals("srt", SubtitleIO.detectFormat("movie.srt", "1\n00:00:01,000 --> 00:00:02,000\nx"))
    }

    @Test
    fun `cues without a blank line between them are not merged`() {
        val src = "1\n00:00:01,000 --> 00:00:02,000\nHello\n2\n00:00:03,000 --> 00:00:04,000\nWorld\n"

        val cues = SubtitleIO.parse(src)

        assertEquals(2, cues.size)
        assertEquals("Hello", cues[0].text)
        assertEquals("World", cues[1].text)
        assertEquals(3_000L, cues[1].startMs)
    }

    @Test
    fun `empty input yields no cues`() {
        assertTrue(SubtitleIO.parse("").isEmpty())
        assertTrue(SubtitleIO.parse("   \n\n  ").isEmpty())
    }
}
