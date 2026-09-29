package com.qwaicode.persiansubtitles.work

import com.qwaicode.persiansubtitles.data.db.ATTEMPTS_SKIPPED
import com.qwaicode.persiansubtitles.data.db.CueEntity
import com.qwaicode.persiansubtitles.data.db.SubtitleDao
import com.qwaicode.persiansubtitles.data.prefs.AppSettings
import com.qwaicode.persiansubtitles.domain.ai.ContextAnalyzer
import com.qwaicode.persiansubtitles.domain.ai.ContextBrief
import com.qwaicode.persiansubtitles.domain.lang.TargetLanguage
import com.qwaicode.persiansubtitles.domain.prompt.PromptBuilder
import com.qwaicode.persiansubtitles.domain.prompt.StylePresets
import com.qwaicode.persiansubtitles.domain.quality.QualityScanner
import com.qwaicode.persiansubtitles.domain.text.BidiShaper
import com.qwaicode.persiansubtitles.network.GeminiClient
import com.qwaicode.persiansubtitles.network.GeminiErrorKind
import com.qwaicode.persiansubtitles.network.GeminiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The translation loop.
 *
 * Speed: several batches are sent to Gemini AT THE SAME TIME (see [AppSettings.concurrency]),
 * which is what makes a 1500-line subtitle finish in minutes instead of half an hour.
 * Because the free tier has a requests-per-minute limit, the number of parallel
 * requests is adaptive: it shrinks when the API answers "quota exceeded" and grows
 * back after a streak of successful batches.
 *
 * Refusals: film dialogue regularly trips Gemini's content filters. The filters are
 * therefore switched off in the request, and if a batch is still refused it is halved
 * until the single responsible line is found. That one line keeps its original text
 * and is flagged for review, so a refusal costs one line instead of stopping the run.
 *
 * Safety: progress is never kept in memory only. Every finished batch is written to
 * the database immediately, so a killed process, a closed app or a lost connection
 * simply continues with the next untranslated cue.
 */
class TranslationEngine(
    private val dao: SubtitleDao,
    /**
     * The settings as a flow, not the repository: that keeps the engine testable
     * on the JVM, where there is no DataStore to build a repository from.
     */
    private val settings: Flow<AppSettings>,
    private val api: GeminiClient = GeminiClient(),
    private val analyzer: ContextAnalyzer = ContextAnalyzer(api),
    /**
     * Called once when reading the whole subtitle produced a tone preset and
     * [AppSettings.autoTone] is on. The worker writes it into the settings, so the
     * Style tab shows the tone the AI chose — and the user can still overrule it.
     */
    private val onToneDetected: suspend (presetId: String) -> Unit = {},
) {

    sealed interface Outcome {
        /** Everything is translated (and reviewed). */
        data object Completed : Outcome

        /** The user paused or WorkManager stopped us — resume later, nothing lost. */
        data object Stopped : Outcome

        /** Temporary problem: WorkManager should retry with its own backoff. */
        data class Retry(val kind: GeminiErrorKind, val message: String) : Outcome

        /** Permanent problem the user has to fix (bad key, no cues). */
        data class Failed(val kind: GeminiErrorKind, val message: String) : Outcome
    }

    companion object {
        const val PHASE_ANALYZE = "analyze"

        /** Still analysing, but waiting for a per-minute limit to clear first. */
        const val PHASE_ANALYZE_WAIT = "analyze_wait"
        const val PHASE_TRANSLATE = "translate"
        const val PHASE_REVIEW = "review"
        const val PHASE_POLISH = "polish"

        private const val MAX_ATTEMPTS_PER_CUE = 3
        private const val MAX_CONSECUTIVE_FAILURES = 4
        private const val REVIEW_BATCH = 8

        /** Smaller than a translation batch: proofreading needs the model to be careful. */
        private const val POLISH_BATCH = 10

        private const val CONTEXT_CUES = 3

        /**
         * Above this share of finished lines a resumed run does not read the file
         * any more: the second half would suddenly follow a brief the first half
         * never saw, and a translation that changes its mind halfway is worse than
         * one without a brief at all.
         */
        private const val ANALYZE_UNTIL_PROGRESS = 0.5

        /** How often the analysis is retried after a per-minute limit or a network error. */
        private const val ANALYZE_RETRIES = 3

        /** Upper bound for one wait during the analysis, so the dialog never looks frozen. */
        private const val ANALYZE_MAX_WAIT_MS = 45_000L

        /** Successful waves needed before we dare to add a parallel slot again. */
        private const val GROW_AFTER_CLEAN_WAVES = 2
    }

    /** Guards the adaptive counters that the parallel batch jobs share. */
    private val stateLock = Mutex()
    private var activeSlots = 1
    private var targetSlots = 1
    private var cleanWaves = 0
    private var consecutiveFailures = 0
    private var lastRetryable: GeminiException? = null
    private var fatal: GeminiException? = null

    /** Lines Gemini refused even on their own; kept in the source language. */
    var safetySkipped: Int = 0
        private set

    /** Lines the editorial pass repaired, locally or with the model. */
    var polished: Int = 0
        private set

    /** What reading the whole subtitle produced, put into every batch prompt. */
    private var brief: ContextBrief? = null

    /** The target language of this run, read from the settings once at the start. */
    private var language: TargetLanguage = AppSettings().language

    /**
     * @param onProgress called after each wave: phase, translated cues, total cues.
     * @param isStopped checked between waves so a pause takes effect quickly.
     * @param forcePolish runs the editorial pass even when the setting is off — used
     *   by the button in the translate tab.
     * @param forceReview runs the review pass even when the setting is off — used by
     *   the "review" button, which used to do nothing at all while auto-review was
     *   switched off in the settings.
     */
    suspend fun run(
        onProgress: suspend (phase: String, done: Int, total: Int) -> Unit,
        isStopped: () -> Boolean,
        forcePolish: Boolean = false,
        forceReview: Boolean = false,
    ): Outcome {
        var settings = settings.first()
        language = settings.language
        if (settings.apiKey.isBlank()) {
            return Outcome.Failed(GeminiErrorKind.NO_KEY, "no api key")
        }

        val total = dao.countAll()
        if (total == 0) return Outcome.Failed(GeminiErrorKind.UNKNOWN, "no cues")

        targetSlots = settings.concurrency.coerceIn(1, 8)
        // Start careful and ramp up: the very first wave also verifies the key.
        activeSlots = 1
        cleanWaves = 0
        consecutiveFailures = 0
        safetySkipped = 0
        polished = 0
        fatal = null
        lastRetryable = null

        // ---- phase 0: read the whole subtitle once -------------------------
        val prepared = prepareBrief(settings, total, onProgress, isStopped)
        brief = prepared.brief
        if (isStopped()) return Outcome.Stopped
        // The key is used up or invalid: every batch would fail the same way, so
        // the run ends here and the user is told why.
        prepared.fatal?.let { return Outcome.Failed(it.kind, it.message ?: it.kind.name) }
        // Leave the "analysing" phase at once. The next progress report only comes
        // after the first translation wave, and until then the UI kept showing the
        // analysis as still running.
        if (prepared.analyzed) onProgress(PHASE_TRANSLATE, dao.countTranslated(), total)

        // ---- automatic tone ------------------------------------------------
        // Only right after a fresh reading: a resumed run keeps whatever tone is in
        // the settings now, because the user may have changed the AI's choice.
        if (prepared.fresh) {
            autoTonePreset(settings, prepared.brief)?.let { presetId ->
                settings = settings.copy(presetId = presetId, autoTonePreset = presetId)
                runCatching { onToneDetected(presetId) }
            }
        }

        // ---- phase 1: translate everything that is still missing ----------
        while (true) {
            if (isStopped()) return Outcome.Stopped

            val batchSize = settings.batchSize.coerceIn(1, 60)
            val slots = activeSlots.coerceIn(1, targetSlots)

            val wave = dao.pendingCues(
                limit = batchSize * slots,
                includeAds = !settings.removeAds,
            )
            if (wave.isEmpty()) break

            val chunks = wave.chunked(batchSize)
            runWave(chunks, settings, reviewMode = false)

            fatal?.let { return Outcome.Failed(it.kind, it.message ?: it.kind.name) }
            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                val error = lastRetryable
                return Outcome.Retry(
                    error?.kind ?: GeminiErrorKind.UNKNOWN,
                    error?.message ?: "too many failed batches",
                )
            }

            onProgress(PHASE_TRANSLATE, dao.countTranslated(), total)
        }

        // ---- phase 2: final review of incomplete or suspicious lines ------
        if (settings.autoReview || forceReview) {
            if (isStopped()) return Outcome.Stopped
            review(settings, total, onProgress, isStopped)?.let { return it }
        }

        // ---- phase 3: editorial scan of the finished Persian text ----------
        if (settings.autoPolish || forcePolish) {
            if (isStopped()) return Outcome.Stopped
            polish(settings, total, onProgress, isStopped)?.let { return it }
        }

        return Outcome.Completed
    }

    /**
     * Reads the subtitle before the first line is translated, so every batch knows
     * the film.
     *
     * Stored with the project, so this happens once and not again after a pause, a
     * crash or a lost connection. It is also strictly optional: if the request
     * fails the run continues without a brief instead of stopping — a missing
     * summary is worth far less than a translation that refuses to start.
     */
    private suspend fun prepareBrief(
        settings: AppSettings,
        total: Int,
        onProgress: suspend (phase: String, done: Int, total: Int) -> Unit,
        isStopped: () -> Boolean,
    ): PreparedBrief {
        val stored = ContextBrief.fromStorage(dao.project()?.contextBrief)
        if (stored != null) return PreparedBrief(stored, fresh = false)
        if (!settings.readWholeSubtitle) return PreparedBrief(null, fresh = false)
        if (isStopped()) return PreparedBrief(null, fresh = false)

        val done = dao.countTranslated()
        if (total > 0 && done.toDouble() / total > ANALYZE_UNTIL_PROGRESS) return PreparedBrief(null, fresh = false)

        onProgress(PHASE_ANALYZE, done, total)

        val fileName = dao.project()?.fileName
        val cues = dao.allCues()
        var attempt = 0
        while (true) {
            if (isStopped()) return PreparedBrief(null, fresh = false, analyzed = true)
            try {
                val result = analyzer.analyze(cues, settings, fileName)
                    ?: return PreparedBrief(null, fresh = false, analyzed = true)
                // A run that was stopped meanwhile (the workspace was cleared or a
                // new file was opened) must not write the old film's brief.
                if (isStopped()) return PreparedBrief(null, fresh = false, analyzed = true)
                withContext(Dispatchers.IO) {
                    dao.setContextBrief(ContextBrief.toStorage(result), System.currentTimeMillis())
                }
                return PreparedBrief(result, fresh = true, analyzed = true)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: GeminiException) {
                when {
                    // Nothing to gain from translating: every batch would hit the
                    // same wall. Reported to the user right away.
                    e.kind == GeminiErrorKind.QUOTA_DAILY ||
                        e.kind == GeminiErrorKind.AUTH ||
                        e.kind == GeminiErrorKind.NO_KEY ->
                        return PreparedBrief(null, fresh = false, analyzed = true, fatal = e)

                    // A per-minute limit or a hiccup: this used to silently drop the
                    // analysis, so the film was translated without being read — and
                    // it looked as if the AI had not analysed it at all. Wait and try
                    // again a few times first.
                    e.kind.retryable && attempt < ANALYZE_RETRIES -> {
                        attempt++
                        onProgress(PHASE_ANALYZE_WAIT, done, total)
                        delay(backoffMillis(attempt, e.retryAfterSeconds).coerceAtMost(ANALYZE_MAX_WAIT_MS))
                        onProgress(PHASE_ANALYZE, done, total)
                    }

                    else -> return PreparedBrief(null, fresh = false, analyzed = true)
                }
            } catch (e: Exception) {
                // Optional step: anything unexpected means "translate without it".
                return PreparedBrief(null, fresh = false, analyzed = true)
            }
        }
    }

    /** The brief plus whether it was produced by this run (and not loaded). */
    private data class PreparedBrief(
        val brief: ContextBrief?,
        val fresh: Boolean,
        /** True when this run entered the analysis phase (successfully or not). */
        val analyzed: Boolean = false,
        /** Set when the analysis proved that translating cannot work either. */
        val fatal: GeminiException? = null,
    )

    /**
     * The preset to switch to, or null when nothing should change: the feature is
     * off, the model named no known preset, the user's own prompt is the only style
     * instruction (the presets are not used at all then), or it is already set.
     */
    private fun autoTonePreset(settings: AppSettings, brief: ContextBrief?): String? {
        if (!settings.autoTone) return null
        if (PromptBuilder.customOnly(settings)) return null
        val preset = StylePresets.find(brief?.tonePreset) ?: return null
        return preset.id
    }

    /** Re-translates everything that looks wrong. Also used by the manual review button. */
    suspend fun review(
        settings: AppSettings,
        total: Int,
        onProgress: suspend (phase: String, done: Int, total: Int) -> Unit,
        isStopped: () -> Boolean,
    ): Outcome? {
        val suspicious = dao.allCues()
            .filter { !(settings.removeAds && it.isAd) }
            .filter { needsReview(it) }

        if (suspicious.isEmpty()) return null

        consecutiveFailures = 0
        val slots = activeSlots.coerceIn(1, targetSlots)

        suspicious.chunked(REVIEW_BATCH).chunked(slots).forEach { wave ->
            if (isStopped()) return Outcome.Stopped

            runWave(wave, settings, reviewMode = true)

            fatal?.let { return Outcome.Failed(it.kind, it.message ?: it.kind.name) }
            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                val error = lastRetryable
                return Outcome.Retry(
                    error?.kind ?: GeminiErrorKind.UNKNOWN,
                    error?.message ?: "review failed",
                )
            }
            onProgress(PHASE_REVIEW, dao.countTranslated(), total)
        }
        return null
    }

    /**
     * The editorial pass: what the user complained about after reading a finished
     * file — a word left in English, a Latin letter between Persian ones, a spelling
     * slip, a sentence that stops halfway.
     *
     * It runs in two stages, and the order matters because the first one is free:
     *
     *  1. **Locally**, [com.qwaicode.persiansubtitles.domain.text.PersianNormalizer] repairs every line of the file: Arabic
     *     `ك`/`ي`, Arabic digits, tatweel, diacritics, the missing zero-width
     *     non-joiner in `می رود`, a space in front of a comma. No request, no quota,
     *     no risk — these rules cannot change the meaning of a sentence.
     *  2. **With the model**, only for the lines [QualityScanner] still finds wrong
     *     afterwards, and the prompt tells it exactly what to look at.
     *
     * Two lines are never touched: one the user edited by hand (their wording wins)
     * and one that was deliberately skipped after a refusal.
     */
    suspend fun polish(
        settings: AppSettings,
        total: Int,
        onProgress: suspend (phase: String, done: Int, total: Int) -> Unit,
        isStopped: () -> Boolean,
    ): Outcome? {
        val candidates = dao.allCues()
            .filter { !(settings.removeAds && it.isAd) }
            .filter { !it.edited }
            .filter { it.attempts < ATTEMPTS_SKIPPED }
            .filter { it.isTranslated }

        if (candidates.isEmpty()) return null

        onProgress(PHASE_POLISH, dao.countTranslated(), total)

        // ---- stage 1: the mechanical repair, on the device ------------------
        val normalized = candidates.mapNotNull { cue ->
            val current = cue.translated ?: return@mapNotNull null
            val fixed = QualityScanner.autoFix(current, language)
            if (fixed == current || fixed.isBlank()) null else cue.id to fixed
        }
        if (normalized.isNotEmpty()) {
            withContext(Dispatchers.IO) { dao.applyPolish(normalized) }
            stateLock.withLock { polished += normalized.size }
        }

        // ---- stage 2: the lines a rule cannot repair ------------------------
        val fixedById = normalized.toMap()
        val suspicious = candidates.mapNotNull { cue ->
            val text = fixedById[cue.id] ?: cue.translated ?: return@mapNotNull null
            val issues = QualityScanner.issues(cue.source, text, language)
            if (issues.none { it.needsModel }) return@mapNotNull null
            PromptBuilder.PolishItem(
                id = cue.id,
                source = cue.source,
                translated = text,
                issues = QualityScanner.describe(issues),
            )
        }
        if (suspicious.isEmpty()) return null

        consecutiveFailures = 0
        for (batch in suspicious.chunked(POLISH_BATCH)) {
            if (isStopped()) return Outcome.Stopped

            try {
                polishBatch(batch, settings)
                onBatchSuccess()
            } catch (e: GeminiException) {
                // The file is already translated: a proofreading answer that cannot
                // be read or was refused only means these lines stay as they are.
                if (e.kind == GeminiErrorKind.PARSE || e.kind == GeminiErrorKind.SAFETY) {
                    onProgress(PHASE_POLISH, dao.countTranslated(), total)
                    continue
                }
                onBatchFailure(e)
                fatal?.let { return Outcome.Failed(it.kind, it.message ?: it.kind.name) }
                if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    // The file is already translated and locally cleaned up, so a
                    // failing polish pass must not turn the whole run into an error.
                    return null
                }
                delay(backoffMillis(consecutiveFailures, e.retryAfterSeconds))
            }
            onProgress(PHASE_POLISH, dao.countTranslated(), total)
        }
        return null
    }

    /** One proofreading request. Only lines that actually changed are written back. */
    private suspend fun polishBatch(batch: List<PromptBuilder.PolishItem>, settings: AppSettings) {
        val raw = api.generateJson(
            apiKey = settings.apiKey,
            model = settings.model,
            systemInstruction = PromptBuilder.polishInstruction(settings),
            userPayload = PromptBuilder.polishPayload(batch, language.jsonKey),
            temperature = 0.2f,
        )

        val answers = PromptBuilder.parseResponse(raw)
        if (answers.isEmpty()) throw GeminiException(GeminiErrorKind.PARSE, "no usable answer in polish")

        val fixed = batch.mapNotNull { item ->
            val answer = answers[item.id]?.let { normalize(it) } ?: return@mapNotNull null
            // A model that hands back an empty line, or one that suddenly dropped
            // most of the text, is refused: a wrong line is better than no line.
            if (answer.isBlank()) return@mapNotNull null
            if (answer.length < item.translated.length * 0.4 && item.translated.length > 12) {
                return@mapNotNull null
            }
            if (answer == item.translated) return@mapNotNull null
            item.id to answer
        }

        if (fixed.isNotEmpty()) {
            withContext(Dispatchers.IO) { dao.applyPolish(fixed) }
            stateLock.withLock { polished += fixed.size }
        }
    }

    /**
     * Sends one wave of batches concurrently. A single failing batch does not
     * abort its siblings — their results are already stored, and the failed cues
     * simply reappear in the next wave.
     */
    private suspend fun runWave(
        chunks: List<List<CueEntity>>,
        settings: AppSettings,
        reviewMode: Boolean,
    ) = coroutineScope {
        var waveHadFailure = false
        val waveLock = Mutex()

        chunks.mapIndexed { index, chunk ->
            async(Dispatchers.IO) {
                // Stagger the starts slightly: firing 8 requests in the same
                // millisecond is the fastest way to trigger a rate-limit answer.
                if (index > 0) delay(index * 220L)

                val context = if (reviewMode) emptyList() else dao.contextBefore(chunk.first().id, CONTEXT_CUES)
                try {
                    applyBatch(chunk, context, settings, reviewMode)
                    onBatchSuccess()
                } catch (e: GeminiException) {
                    waveLock.withLock { waveHadFailure = true }
                    onBatchFailure(e)
                }
            }
        }.awaitAll()

        if (waveHadFailure) {
            // One shared pause per wave, not one per failed batch. A wave whose only
            // failure was fatal does not wait: the run is about to end anyway.
            val (failures, error, stop) = stateLock.withLock { Triple(consecutiveFailures, lastRetryable, fatal != null) }
            if (!stop) delay(backoffMillis(failures.coerceAtLeast(1), error?.retryAfterSeconds))
        }
    }

    private suspend fun onBatchSuccess() = stateLock.withLock {
        consecutiveFailures = 0
        cleanWaves++
        if (cleanWaves >= GROW_AFTER_CLEAN_WAVES && activeSlots < targetSlots) {
            activeSlots++
            cleanWaves = 0
        }
    }

    private suspend fun onBatchFailure(e: GeminiException) = stateLock.withLock {
        // A safety refusal is never fatal and never worth retrying as a batch: the
        // bisection in applyBatch has already dealt with it line by line.
        if (e.kind == GeminiErrorKind.SAFETY) return@withLock
        if (!e.kind.retryable) {
            fatal = e
            return@withLock
        }
        lastRetryable = e
        consecutiveFailures++
        cleanWaves = 0
        if (e.kind == GeminiErrorKind.QUOTA || e.kind == GeminiErrorKind.SERVER) {
            // Too fast for the free tier: halve the parallelism instead of failing.
            activeSlots = (activeSlots / 2).coerceAtLeast(1)
        }
    }

    /** Sends one batch and stores whatever came back. */
    private suspend fun applyBatch(
        batch: List<CueEntity>,
        context: List<CueEntity>,
        settings: AppSettings,
        reviewMode: Boolean,
    ) {
        try {
            translateChunk(batch, context, settings, reviewMode)
            return
        } catch (e: GeminiException) {
            if (e.kind == GeminiErrorKind.PARSE) {
                handleUnreadable(batch, settings, reviewMode)
                return
            }
            if (e.kind != GeminiErrorKind.SAFETY) throw e
        }

        // Gemini refused this batch. One single line is responsible for that, and
        // the whole run used to die here: the batch stayed untranslated, so the
        // next "continue" picked exactly the same lines and hit exactly the same
        // refusal. Instead we narrow it down by halving the batch, so only the
        // offending line is affected and everything around it still gets
        // translated. The surrounding context is dropped on purpose — fewer
        // sentences in the prompt means fewer reasons to refuse.
        if (batch.size > 1) {
            val half = batch.size / 2
            applyBatch(batch.take(half), emptyList(), settings, reviewMode)
            applyBatch(batch.drop(half), emptyList(), settings, reviewMode)
            return
        }

        // A single line that Gemini refuses even on its own: skip it and move on.
        // The original text is kept so the exported file has no hole, and the line
        // is flagged so it shows up under "needs review" in the editor, where it
        // can be translated by hand or retried individually.
        val cue = batch.first()
        withContext(Dispatchers.IO) { dao.markSkipped(id = cue.id, sourceText = cue.source) }
        stateLock.withLock { safetySkipped++ }
    }

    /**
     * The model answered, but not with usable JSON — almost always because a long
     * batch ran into the output limit and the array was cut off (MAX_TOKENS).
     *
     * This used to be treated as fatal and stopped the whole run over one bad
     * answer. A smaller batch fits the budget, so the batch is halved; a single line
     * that still cannot be read counts an attempt and, after the last one, keeps its
     * source text and is flagged for review, exactly like a line the model skipped.
     */
    private suspend fun handleUnreadable(batch: List<CueEntity>, settings: AppSettings, reviewMode: Boolean) {
        if (batch.size > 1) {
            val half = batch.size / 2
            applyBatch(batch.take(half), emptyList(), settings, reviewMode)
            applyBatch(batch.drop(half), emptyList(), settings, reviewMode)
            return
        }
        val cue = batch.first()
        val attempts = cue.attempts + 1
        withContext(Dispatchers.IO) {
            if (attempts >= MAX_ATTEMPTS_PER_CUE) {
                dao.applyBatchResult(translated = listOf(Triple(cue.id, cue.source, true)), retryIds = emptyList())
            } else {
                dao.applyBatchResult(translated = emptyList(), retryIds = listOf(cue.id))
            }
        }
    }

    /** One request for one batch. Throws [GeminiException] on any problem. */
    private suspend fun translateChunk(
        batch: List<CueEntity>,
        context: List<CueEntity>,
        settings: AppSettings,
        reviewMode: Boolean,
    ) {
        val raw = api.generateJson(
            apiKey = settings.apiKey,
            model = settings.model,
            systemInstruction = PromptBuilder.systemInstruction(settings, reviewMode, brief),
            userPayload = PromptBuilder.batchPayload(batch, context, language.jsonKey),
            temperature = settings.temperature,
        )

        val translations = PromptBuilder.parseResponse(raw)
        if (translations.isEmpty()) {
            throw GeminiException(GeminiErrorKind.PARSE, "no usable translation in answer")
        }

        // One transaction per batch: far fewer disk writes than one per line.
        val done = mutableListOf<Triple<Int, String, Boolean>>()
        val retry = mutableListOf<CueEntity>()

        for (cue in batch) {
            val translated = translations[cue.id]?.trim()
            if (!translated.isNullOrEmpty()) {
                done += Triple(cue.id, normalize(translated), false)
            } else {
                val attempts = cue.attempts + 1
                if (attempts >= MAX_ATTEMPTS_PER_CUE) {
                    // Give up on this single line: keep the source text and flag it
                    // so the user finds it instantly in the editor.
                    done += Triple(cue.id, cue.source, true)
                } else {
                    retry += cue
                }
            }
        }

        withContext(Dispatchers.IO) {
            dao.applyBatchResult(
                translated = done,
                retryIds = retry.map { it.id },
            )
        }
    }

    /**
     * Cleans up what models sometimes add around the text.
     *
     * Direction marks are removed, not replaced by a space: the file is stored in
     * logical order and the exporter is what adds the marks the player needs. A
     * model-supplied RLM used to become a stray space in the middle of the line.
     *
     * [com.qwaicode.persiansubtitles.domain.text.PersianNormalizer] runs here as well, so a line is written in correct Persian
     * spelling the moment it arrives instead of being repaired later.
     */
    private fun normalize(text: String): String = QualityScanner.autoFix(
        BidiShaper.strip(text)
            .replace("\\n", "\n")
            .trim()
            .trim('"')
            .trim(),
        language,
    )

    private fun needsReview(cue: CueEntity): Boolean {
        val translated = cue.translated
        if (translated.isNullOrBlank()) return true
        if (cue.edited) return false // the user fixed this line by hand
        if (cue.attempts >= ATTEMPTS_SKIPPED) return false // deliberately skipped
        if (cue.flagged) return true

        val source = cue.source.trim()
        val lang = language
        // For a Latin-script target the letters prove nothing (the source is Latin
        // too), so only an unchanged line counts as untranslated there.
        val hasTarget = lang.script == com.qwaicode.persiansubtitles.domain.lang.Script.LATIN ||
            lang.hasOwnLetters(translated)
        val sourceHasLatin = source.any { it in 'A'..'Z' || it in 'a'..'z' }

        // untranslated leftovers
        if (sourceHasLatin && !hasTarget && source.length > 3) return true
        if (sourceHasLatin && translated.trim() == source &&
            (lang.script != com.qwaicode.persiansubtitles.domain.lang.Script.LATIN || source.length > 3)
        ) return true
        // answered in Persian although the target is another script
        if (QualityScanner.Issue.WRONG_SCRIPT in QualityScanner.issues(source, translated, lang)) return true
        // truncated answers
        val ratio = if (lang.script.compact) 0.06 else 0.2
        if (source.length > 24 && translated.length < source.length * ratio) return true
        return false
    }

    /** Exponential backoff, but honour the server's own retry hint when present. */
    private fun backoffMillis(attempt: Int, retryAfterSeconds: Int?): Long {
        retryAfterSeconds?.let { return (it.coerceIn(1, 120) * 1000).toLong() }
        val base = 4_000L * (1 shl (attempt - 1).coerceAtMost(4))
        return base.coerceAtMost(90_000L)
    }
}
