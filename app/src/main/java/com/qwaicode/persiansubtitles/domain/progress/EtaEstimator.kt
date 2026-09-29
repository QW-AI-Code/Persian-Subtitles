package com.qwaicode.persiansubtitles.domain.progress

/**
 * Speed and time left of a running translation.
 *
 * The rate is measured over a sliding window of recent progress instead of since
 * the start, for two reasons that both come from how a real run behaves: the first
 * minute is slow on purpose (the engine starts with one parallel request and ramps
 * up), and a quota pause in the middle would otherwise drag the estimate for the
 * rest of the run. A window reacts within a minute to both.
 *
 * Pure Kotlin on purpose, so it is tested on the JVM with made-up clocks.
 */
class EtaEstimator(
    private val windowMs: Long = DEFAULT_WINDOW_MS,
) {
    data class Estimate(
        /** Lines per minute over the window. */
        val linesPerMinute: Int,
        /** Milliseconds until [total] is reached at that speed. */
        val remainingMs: Long,
    )

    private data class Sample(val atMs: Long, val done: Int)

    private val samples = ArrayDeque<Sample>()

    /** Forget everything — a new run, a new file or a pause starts from zero. */
    fun reset() = samples.clear()

    /**
     * Records the progress at [nowMs] and returns the estimate, or null while there
     * is not enough to go on (fewer than two samples, under [MIN_SPAN_MS] of data, or
     * no progress at all in the window).
     */
    fun update(nowMs: Long, done: Int, total: Int): Estimate? {
        // Progress that goes backwards is a new start (a retranslation, a language
        // switch): the old samples would produce a nonsense speed.
        if (samples.isNotEmpty() && done < samples.last().done) samples.clear()
        if (samples.isEmpty() || samples.last().done != done || nowMs - samples.last().atMs > windowMs / 4) {
            samples.addLast(Sample(nowMs, done))
        }
        while (samples.size > 2 && nowMs - samples.first().atMs > windowMs) samples.removeFirst()

        if (done >= total) return null
        val first = samples.first()
        val span = nowMs - first.atMs
        val gained = done - first.done
        if (samples.size < 2 || span < MIN_SPAN_MS || gained <= 0) return null

        val perMs = gained.toDouble() / span
        val remaining = ((total - done) / perMs).toLong()
        return Estimate(
            linesPerMinute = (perMs * 60_000).toInt().coerceAtLeast(1),
            remainingMs = remaining,
        )
    }

    companion object {
        const val DEFAULT_WINDOW_MS = 90_000L
        const val MIN_SPAN_MS = 8_000L
    }
}
