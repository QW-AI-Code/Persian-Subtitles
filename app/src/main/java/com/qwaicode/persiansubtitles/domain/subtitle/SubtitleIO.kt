package com.qwaicode.persiansubtitles.domain.subtitle

/** A cue as it was read from the source file. */
data class ParsedCue(
    val index: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String,
)

enum class ExportFormat(val extension: String, val mime: String) {
    SRT("srt", "application/x-subrip"),
    VTT("vtt", "text/vtt"),
    BILINGUAL_SRT("srt", "application/x-subrip"),
    TXT("txt", "text/plain"),
}

/**
 * Reader/writer for the common subtitle formats. SRT and WebVTT are parsed by the
 * same block scanner, which tolerates missing cue numbers, CRLF line endings,
 * VTT cue settings and stray blank lines.
 */
object SubtitleIO {

    private val TIME_REGEX = Regex("""(?:(\d{1,3}):)?(\d{1,2}):(\d{1,2})[.,](\d{1,3})""")
    private val ARROW = "-->"

    fun detectFormat(fileName: String, raw: String): String = when {
        raw.trimStart().startsWith("WEBVTT") -> "vtt"
        fileName.endsWith(".vtt", ignoreCase = true) -> "vtt"
        else -> "srt"
    }

    /**
     * Line-based instead of splitting on blank lines, so a file that forgets the
     * empty line between two cues (common in hand-edited and converted files) no
     * longer merges them into one cue with the next cue's number in its text.
     *
     * Every timing line starts a cue. Its text runs until the first blank line; if
     * the next timing line follows without one, the SRT counter directly above it is
     * dropped. Anything after the blank line (a VTT cue id, a NOTE, a STYLE block)
     * is not dialogue.
     */
    fun parse(raw: String): List<ParsedCue> {
        val lines = raw
            .removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .split('\n')
            .map { it.trim() }

        val timings = ArrayList<Pair<Int, List<String>>>()
        lines.forEachIndexed { i, line ->
            if (!line.contains(ARROW)) return@forEachIndexed
            val times = TIME_REGEX.findAll(line).map { it.value }.take(2).toList()
            if (times.size == 2) timings += i to times
        }

        val cues = ArrayList<ParsedCue>(timings.size)
        var number = 0
        timings.forEachIndexed { t, (lineIndex, times) ->
            val next = if (t + 1 < timings.size) timings[t + 1].first else lines.size
            var segment = lines.subList(lineIndex + 1, next)
            val blank = segment.indexOfFirst { it.isEmpty() }
            if (blank >= 0) {
                segment = segment.subList(0, blank)
            } else if (t + 1 < timings.size && segment.isNotEmpty() && segment.last().all { it.isDigit() }) {
                // "…text\n2\n00:00:05,000 --> …" — the 2 is the next cue's counter.
                segment = segment.subList(0, segment.size - 1)
            }
            val text = segment.joinToString("\n").trim()
            if (text.isEmpty()) return@forEachIndexed

            number++
            cues += ParsedCue(
                index = number,
                startMs = parseTime(times[0]),
                endMs = parseTime(times[1]),
                text = text,
            )
        }
        return cues
    }

    fun parseTime(value: String): Long {
        val m = TIME_REGEX.find(value) ?: return 0L
        val (h, mm, ss, ms) = m.destructured
        val millis = ms.padEnd(3, '0').take(3).toLongOrNull() ?: 0L
        return (h.toLongOrNull() ?: 0L) * 3_600_000L +
            (mm.toLongOrNull() ?: 0L) * 60_000L +
            (ss.toLongOrNull() ?: 0L) * 1_000L +
            millis
    }

    fun formatSrtTime(ms: Long): String = formatTime(ms, ',')

    fun formatVttTime(ms: Long): String = formatTime(ms, '.')

    private fun formatTime(msTotal: Long, separator: Char): String {
        val ms = msTotal.coerceAtLeast(0L)
        val hours = ms / 3_600_000
        val minutes = (ms % 3_600_000) / 60_000
        val seconds = (ms % 60_000) / 1_000
        val millis = ms % 1_000
        // Locale.US on purpose: subtitle files must always use ASCII digits.
        return String.format(java.util.Locale.US, "%02d:%02d:%02d%s%03d", hours, minutes, seconds, separator, millis)
    }
}
