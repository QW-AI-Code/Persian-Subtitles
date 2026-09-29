package com.qwaicode.persiansubtitles.domain.subtitle

import com.qwaicode.persiansubtitles.data.db.CueEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleExporterTest {

    private fun cue(id: Int, start: Long, end: Long, source: String, fa: String? = null, ad: Boolean = false) =
        CueEntity(id = id, startMs = start, endMs = end, source = source, translated = fa, isAd = ad)

    private val cues = listOf(
        cue(1, 1_000, 2_000, "Hello", "سلام"),
        cue(2, 3_000, 4_000, "Visit example.com", "به example.com سر بزنید", ad = true),
        cue(3, 5_000, 6_500, "Goodbye", "خداحافظ"),
    )

    @Test
    fun `srt drops ads and renumbers sequentially`() {
        val out = SubtitleExporter.build(cues, ExportFormat.SRT, dropAds = true, signature = null)

        val blocks = out.trim().split("\n\n")
        assertEquals(2, blocks.size)
        // the removed ad must not leave a gap in the numbering
        assertTrue(blocks[0].startsWith("1\n"))
        assertTrue(blocks[1].startsWith("2\n"))
        assertTrue(out.contains("00:00:05,000 --> 00:00:06,500"))
        assertFalse(out.contains("example.com"))
    }

    @Test
    fun `srt keeps ads when the option is off`() {
        val out = SubtitleExporter.build(cues, ExportFormat.SRT, dropAds = false, signature = null)

        assertEquals(3, out.trim().split("\n\n").size)
        assertTrue(out.contains("example.com"))
    }

    @Test
    fun `falls back to the source text when a line is untranslated`() {
        val partial = listOf(cue(1, 0, 1_000, "Untranslated line", null))
        val out = SubtitleExporter.build(partial, ExportFormat.SRT, dropAds = true, signature = null)

        assertTrue(out.contains("Untranslated line"))
    }

    @Test
    fun `vtt starts with the header and uses dot separated timestamps`() {
        val out = SubtitleExporter.build(cues, ExportFormat.VTT, dropAds = true, signature = null)

        assertTrue(out.startsWith("WEBVTT\n\n"))
        assertTrue(out.contains("00:00:01.000 --> 00:00:02.000"))
        assertFalse(out.contains(",000 -->"))
    }

    @Test
    fun `bilingual srt contains persian and original`() {
        val out = SubtitleExporter.build(cues, ExportFormat.BILINGUAL_SRT, dropAds = true, signature = null)

        assertTrue(out.contains("سلام"))
        assertTrue(out.contains("Hello"))
    }

    @Test
    fun `txt is one line per cue without timings`() {
        val out = SubtitleExporter.build(cues, ExportFormat.TXT, dropAds = true, signature = null)

        assertEquals(2, out.trim().lines().size)
        assertFalse(out.contains("-->"))
    }

    @Test
    fun `the signature is spread over the file and renumbered with the dialogue`() {
        // A 10 minute film with a comfortable hole every 30 seconds.
        val film = (1..20).map { index ->
            val start = index * 30_000L
            cue(index, start, start + 2_000, "Line $index", "خط $index")
        }

        val out = SubtitleExporter.build(
            film,
            ExportFormat.SRT,
            dropAds = true,
            signature = "ترجمه از من",
            signatureCount = 4,
        )

        val blocks = out.trim().split("\n\n")
        assertEquals("20 cues + 4 credits", 24, blocks.size)
        assertEquals(4, blocks.count { it.contains("ترجمه از من") })
        // Numbering stays sequential across cues and credits.
        blocks.forEachIndexed { index, block ->
            assertEquals("${index + 1}", block.lineSequence().first())
        }
        // Not all credits at the end: at least one sits in the first half.
        val firstCreditBlock = blocks.indexOfFirst { it.contains("ترجمه از من") }
        assertTrue("credits must not all be at the end", firstCreditBlock < blocks.size / 2)
    }

    @Test
    fun `inserting the signature never changes a dialogue timing`() {
        val film = (1..20).map { index ->
            val start = index * 30_000L
            cue(index, start, start + 2_000, "Line $index", "خط $index")
        }

        val withCredits = SubtitleExporter.build(
            film, ExportFormat.SRT, dropAds = true, signature = "QW-AI-Code", signatureCount = 6,
        )

        // Every original timestamp must still be in the file, unshifted.
        film.forEach { cue ->
            val stamp = "${SubtitleIO.formatSrtTime(cue.startMs)} --> ${SubtitleIO.formatSrtTime(cue.endMs)}"
            assertTrue("timing of cue ${cue.id} was changed", withCredits.contains(stamp))
        }
    }

    @Test
    fun `a credit never overlaps a dialogue line`() {
        val film = (1..20).map { index ->
            val start = index * 30_000L
            cue(index, start, start + 2_000, "Line $index", "خط $index")
        }

        val credits = SignatureInserter.plan(film, "QW-AI-Code", 6)

        assertTrue(credits.isNotEmpty())
        credits.forEach { credit ->
            film.forEach { cue ->
                val overlaps = credit.startMs < cue.endMs && cue.startMs < credit.endMs
                assertFalse("credit at ${credit.startMs} covers cue ${cue.id}", overlaps)
            }
        }
    }

    @Test
    fun `a signature without any usable gap is still placed after the film`() {
        // Wall-to-wall dialogue: no hole big enough anywhere in between.
        val dense = (0 until 30).map { index ->
            val start = index * 1_000L
            cue(index + 1, start, start + 1_000, "Line", "خط")
        }

        val out = SubtitleExporter.build(
            dense, ExportFormat.SRT, dropAds = true, signature = "QW-AI-Code", signatureCount = 5,
        )

        assertEquals("only the trailing credit fits", 1, out.split("QW-AI-Code").size - 1)
        assertEquals(31, out.trim().split("\n\n").size)
    }

    @Test
    fun `blank signature is ignored`() {
        val out = SubtitleExporter.build(cues, ExportFormat.SRT, dropAds = true, signature = "   ")

        assertEquals(2, out.trim().split("\n\n").size)
    }

    @Test
    fun `a persian line is wrapped in a closed rtl embedding`() {
        val out = SubtitleExporter.build(cues, ExportFormat.SRT, dropAds = true, signature = null)

        // The default uses strong RLM marks at both ends of Persian text.
        assertTrue(out.contains("\u200Fسلام\u200F"))
    }

    @Test
    fun `a line without any persian is left alone`() {
        val english = listOf(cue(1, 0, 1_000, "See you tomorrow.", null))
        val out = SubtitleExporter.build(english, ExportFormat.SRT, dropAds = true, signature = null)

        // Forcing RTL on an English line is what moved its full stop to the front.
        assertFalse(out.contains("\u202B"))
        assertTrue(out.contains("See you tomorrow."))
    }

    @Test
    fun `a latin signature is not forced right to left`() {
        val out = SubtitleExporter.build(
            cues, ExportFormat.SRT, dropAds = true, signature = "QW-AI-Code", signatureCount = 1,
        )

        assertTrue(out.contains("QW-AI-Code"))
        assertFalse(out.contains("\u202BQW-AI-Code"))
    }

    @Test
    fun `each line of a multi line cue gets its own direction`() {
        val mixed = listOf(cue(1, 0, 2_000, "Two lines", "خط اول\nSecond line"))
        val out = SubtitleExporter.build(mixed, ExportFormat.SRT, dropAds = true, signature = null)

        assertTrue(out.contains("\u200Fخط اول\u200F"))
        assertTrue(out.contains("\nSecond line"))
        assertFalse(out.contains("\u202BSecond"))
    }

    @Test
    fun `suggested file name keeps the stem and swaps the extension`() {
        assertEquals("movie.fa.srt", SubtitleExporter.suggestedFileName("movie.srt", ExportFormat.SRT))
        assertEquals("movie.fa-en.srt", SubtitleExporter.suggestedFileName("movie.srt", ExportFormat.BILINGUAL_SRT))
        assertEquals("movie.fa.vtt", SubtitleExporter.suggestedFileName("movie.vtt", ExportFormat.VTT))
        // only the last extension is replaced, the rest of the name is kept
        assertEquals("movie.en.fa.vtt", SubtitleExporter.suggestedFileName("movie.en.vtt", ExportFormat.VTT))
        assertEquals("subtitle.fa.srt", SubtitleExporter.suggestedFileName("", ExportFormat.SRT))
    }

    @Test
    fun `export of an empty list does not crash`() {
        assertEquals("", SubtitleExporter.build(emptyList(), ExportFormat.SRT, dropAds = true, signature = null))
        assertTrue(SubtitleExporter.build(emptyList(), ExportFormat.VTT, dropAds = true, signature = null).startsWith("WEBVTT"))
    }
}
