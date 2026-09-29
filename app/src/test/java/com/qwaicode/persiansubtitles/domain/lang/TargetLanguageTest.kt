package com.qwaicode.persiansubtitles.domain.lang

import com.qwaicode.persiansubtitles.data.prefs.AppSettings
import com.qwaicode.persiansubtitles.domain.prompt.PromptBuilder
import com.qwaicode.persiansubtitles.domain.quality.QualityScanner
import com.qwaicode.persiansubtitles.domain.subtitle.ExportFormat
import com.qwaicode.persiansubtitles.domain.subtitle.SubtitleExporter
import com.qwaicode.persiansubtitles.data.db.CueEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Translating into languages other than Persian. Persian must behave exactly as
 * before; every other language must never be run through the Persian-only rules.
 */
class TargetLanguageTest {

    private val german = TargetLanguages.byCode("de")
    private val arabic = TargetLanguages.byCode("ar")
    private val japanese = TargetLanguages.byCode("ja")

    @Test
    fun `catalog has persian first, unique codes and a flag for every language`() {
        assertEquals("fa", TargetLanguages.all.first().code)
        assertEquals(31, TargetLanguages.all.size)
        val codes = TargetLanguages.all.map { it.code }
        assertEquals(codes.size, codes.distinct().size)
        TargetLanguages.all.forEach {
            assertTrue(it.flag.isNotBlank())
            assertTrue(it.nameFa.isNotBlank())
            assertTrue(it.nameEn.isNotBlank())
            assertTrue(it.nativeName.isNotBlank())
        }
    }

    @Test
    fun `unknown code falls back to persian`() {
        assertEquals("fa", TargetLanguages.byCode("xx").code)
        assertEquals("fa", TargetLanguages.byCode(null).code)
        assertEquals("fa", AppSettings().language.code)
    }

    @Test
    fun `search matches persian, english and native names`() {
        assertTrue(TargetLanguages.search("آلمانی").any { it.code == "de" })
        assertTrue(TargetLanguages.search("german").any { it.code == "de" })
        assertTrue(TargetLanguages.search("Deutsch").any { it.code == "de" })
        assertEquals(TargetLanguages.all, TargetLanguages.search("  "))
    }

    @Test
    fun `direction and punctuation follow the script`() {
        assertTrue(arabic.rtl)
        assertTrue(arabic.arabicPunctuation)
        assertTrue(TargetLanguages.byCode("he").rtl)
        assertFalse(TargetLanguages.byCode("he").arabicPunctuation)
        assertFalse(german.rtl)
    }

    @Test
    fun `persian prompt is unchanged and other languages name the target explicitly`() {
        val fa = PromptBuilder.systemInstruction(AppSettings(), reviewMode = false)
        val de = PromptBuilder.systemInstruction(AppSettings(targetLanguage = "de"), reviewMode = false)

        assertTrue(fa.contains("به فارسی روان"))
        assertFalse(fa.contains("TARGET LANGUAGE"))
        assertTrue(de.contains("TARGET LANGUAGE: German"))
        assertTrue(de.contains("\"translation\""))
        assertFalse(de.contains("همه چیز باید فارسی باشد"))
    }

    @Test
    fun `polish instruction for another language is not the persian editor`() {
        val de = PromptBuilder.polishInstruction(AppSettings(targetLanguage = "de"))
        assertTrue(de.contains("German"))
        assertFalse(de.contains("نیم‌فاصله"))
        assertTrue(de.contains("بازنویسی سلیقه‌ای ممنوع است"))
    }

    @Test
    fun `persian letter rules never touch arabic text`() {
        // ي and ة are correct Arabic; the Persian normaliser would turn them into ی and ه.
        val line = "مدرسة جميلة"
        assertEquals(line, QualityScanner.autoFix(line, arabic))
        assertTrue(QualityScanner.issues("A beautiful school", line, arabic).isEmpty())
    }

    @Test
    fun `a persian answer to a german request is caught`() {
        val issues = QualityScanner.issues("Where are you going?", "کجا می‌روی؟", german)
        assertTrue(issues.contains(QualityScanner.Issue.WRONG_SCRIPT))
        assertTrue(QualityScanner.needsModel("Where are you going?", "کجا می‌روی؟", german))
    }

    @Test
    fun `a correct german line is clean and an unchanged one is not`() {
        assertTrue(QualityScanner.issues("Where are you going?", "Wohin gehst du?", german).isEmpty())
        assertTrue(
            QualityScanner.issues("Where are you going?", "Where are you going?", german)
                .contains(QualityScanner.Issue.SAME_AS_SOURCE)
        )
    }

    @Test
    fun `a short japanese line is not mistaken for a truncated one`() {
        val source = "I have been waiting for you all this time."
        assertFalse(
            QualityScanner.issues(source, "ずっと待っていた。", japanese)
                .contains(QualityScanner.Issue.TRUNCATED)
        )
    }

    @Test
    fun `export uses the language code and no rtl style for ltr languages`() {
        assertEquals("movie.de.srt", SubtitleExporter.suggestedFileName("movie.srt", ExportFormat.SRT, "de"))
        assertEquals("movie.de-en.srt", SubtitleExporter.suggestedFileName("movie.srt", ExportFormat.BILINGUAL_SRT, "de"))

        val cues = listOf(CueEntity(id = 1, startMs = 0, endMs = 1000, source = "Hello", translated = "Hallo"))
        val vtt = SubtitleExporter.build(cues, ExportFormat.VTT, dropAds = true, signature = null, rtlTarget = false)
        assertFalse(vtt.contains("direction: rtl"))
        assertTrue(vtt.contains("Hallo"))
    }
}
