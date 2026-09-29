package com.qwaicode.persiansubtitles.domain.subtitle

import com.qwaicode.persiansubtitles.data.db.CueEntity
import com.qwaicode.persiansubtitles.domain.text.BidiMode
import com.qwaicode.persiansubtitles.domain.text.BidiShaper

/** Builds the final subtitle file from the translated cues. */
object SubtitleExporter {

    /** One line of the output: a translated cue or an inserted credit. */
    private data class Entry(
        val startMs: Long,
        val endMs: Long,
        val text: String,
        val source: String?,
        /** A credit is shaped differently from dialogue — see [BidiShaper.shapeCredit]. */
        val isCredit: Boolean = false,
    )

    fun build(
        cues: List<CueEntity>,
        format: ExportFormat,
        dropAds: Boolean,
        signature: String?,
        signatureCount: Int = 1,
        bidiMode: BidiMode = BidiMode.ISOLATE,
        persianPunctuation: Boolean = true,
        /** False for a left-to-right target language: no RTL `STYLE` block in VTT. */
        rtlTarget: Boolean = true,
        /**
         * Moves every cue (and every inserted credit) by this many milliseconds.
         * Negative = earlier. A cue pushed entirely before 0:00 is dropped; one that
         * only starts before it is clipped to begin at 0:00.
         */
        shiftMs: Long = 0L,
    ): String {
        val usable = cues
            .filter { !(dropAds && it.isAd) }
            .sortedBy { it.id }

        val credits = if (signature.isNullOrBlank()) {
            emptyList()
        } else {
            SignatureInserter.plan(usable, signature, signatureCount)
        }

        val shape: (String) -> String = { text -> BidiShaper.shape(text, bidiMode, persianPunctuation) }

        return when (format) {
            ExportFormat.SRT -> buildSrt(shift(merge(usable, credits), shiftMs), bilingual = false, shape = shape)
            ExportFormat.BILINGUAL_SRT -> buildSrt(shift(merge(usable, credits), shiftMs), bilingual = true, shape = shape)
            ExportFormat.VTT -> buildVtt(shift(merge(usable, credits), shiftMs), shape = shape, rtl = rtlTarget && bidiMode != BidiMode.NONE)
            // Plain text has no timeline to place anything on, so the signature
            // goes where it can only be a signature: the end.
            ExportFormat.TXT -> buildTxt(usable) +
                if (signature.isNullOrBlank()) "" else "\n" + signature.trim() + "\n"
        }
    }

    /**
     * Cues and credits in one ordered list. The credits sit in holes of the
     * timeline, so no cue's start or end time changes — only the numbering,
     * which subtitle files require to be sequential anyway.
     */
    private fun merge(cues: List<CueEntity>, credits: List<SignatureInserter.Credit>): List<Entry> {
        val entries = cues.map { cue ->
            Entry(cue.startMs, cue.endMs, textOf(cue), sanitize(cue.source))
        } + credits.map { credit ->
            Entry(credit.startMs, credit.endMs, credit.text, null, isCredit = true)
        }
        return entries.sortedWith(compareBy({ it.startMs }, { it.endMs }))
    }

    /** Applies the sync offset. Credits were planned on the original timeline, so they move with it. */
    private fun shift(entries: List<Entry>, shiftMs: Long): List<Entry> {
        if (shiftMs == 0L) return entries
        return entries.mapNotNull { entry ->
            val end = entry.endMs + shiftMs
            if (end <= 0L) return@mapNotNull null
            entry.copy(startMs = (entry.startMs + shiftMs).coerceAtLeast(0L), endMs = end)
        }
    }

    private fun textOf(cue: CueEntity): String =
        sanitize(cue.translated?.takeIf { it.isNotBlank() } ?: cue.source)

    private val BLANK_LINES = Regex("""\n[ \t]*\n+""")

    /**
     * A blank line ends a cue in both SRT and VTT, and `-->` starts a new one. A model
     * answer with an empty line in it used to split the cue in the exported file
     * (the second half showed up as garbage or vanished in the player).
     */
    private fun sanitize(text: String): String = text
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .trim()
        .replace(BLANK_LINES, "\n")
        .replace("-->", "→")

    private fun buildSrt(
        entries: List<Entry>,
        bilingual: Boolean,
        shape: (String) -> String,
    ): String = buildString {
        entries.forEachIndexed { position, entry ->
            append(position + 1).append('\n')
            append(SubtitleIO.formatSrtTime(entry.startMs))
            append(" --> ")
            append(SubtitleIO.formatSrtTime(entry.endMs)).append('\n')
            append(textFor(entry, shape)).append('\n')
            if (bilingual && entry.source != null) append(shape(entry.source)).append('\n')
            append('\n')
        }
    }

    /**
     * Dialogue is shaped for the player; a credit is written exactly as the user sees
     * it in the app. See [BidiShaper.shapeCredit] for why the two differ.
     */
    private fun textFor(entry: Entry, shape: (String) -> String): String =
        if (entry.isCredit) BidiShaper.shapeCredit(entry.text) else shape(entry.text)

    /**
     * WebVTT has a mechanism the Unicode marks cannot replace: a `STYLE` block. A
     * player or browser that understands it lays the cue out right-to-left because
     * the *file* says so, and `unicode-bidi: plaintext` makes it decide each cue's
     * direction from the cue's own first strong character — which is exactly the
     * behaviour a mixed Persian/English subtitle needs. Parsers that do not support
     * STYLE blocks skip them, so adding it costs nothing.
     */
    private fun buildVtt(
        entries: List<Entry>,
        shape: (String) -> String,
        rtl: Boolean,
    ): String = buildString {
        append("WEBVTT\n\n")
        if (rtl) {
            append("STYLE\n")
            append("::cue {\n")
            append("  direction: rtl;\n")
            append("  unicode-bidi: plaintext;\n")
            append("}\n\n")
        }
        entries.forEachIndexed { position, entry ->
            append(position + 1).append('\n')
            append(SubtitleIO.formatVttTime(entry.startMs))
            append(" --> ")
            append(SubtitleIO.formatVttTime(entry.endMs)).append('\n')
            append(textFor(entry, shape)).append("\n\n")
        }
    }

    private fun buildTxt(cues: List<CueEntity>): String = buildString {
        cues.forEach { cue ->
            append(textOf(cue).replace('\n', ' ')).append('\n')
        }
    }

    /** `movie.srt` → `movie.fa.srt`, `movie.de.srt`, … and `movie.fa-en.srt` for bilingual. */
    fun suggestedFileName(baseName: String, format: ExportFormat, languageCode: String = "fa"): String {
        val stem = baseName.substringBeforeLast('.', baseName).ifBlank { "subtitle" }
        val code = languageCode.ifBlank { "fa" }
        val suffix = when (format) {
            ExportFormat.BILINGUAL_SRT -> ".$code-en"
            else -> ".$code"
        }
        return "$stem$suffix.${format.extension}"
    }
}
