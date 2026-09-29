package com.qwaicode.persiansubtitles.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qwaicode.persiansubtitles.R
import com.qwaicode.persiansubtitles.data.db.ATTEMPTS_SKIPPED
import com.qwaicode.persiansubtitles.data.db.CueEntity
import com.qwaicode.persiansubtitles.data.db.DatabaseProvider
import com.qwaicode.persiansubtitles.data.db.ProjectEntity
import com.qwaicode.persiansubtitles.data.db.ProjectStatus
import com.qwaicode.persiansubtitles.data.prefs.AppSettings
import com.qwaicode.persiansubtitles.data.prefs.SettingsRepository
import com.qwaicode.persiansubtitles.data.repo.SubtitleRepository
import com.qwaicode.persiansubtitles.domain.subtitle.CopyrightCleaner
import com.qwaicode.persiansubtitles.domain.subtitle.ExportFormat
import com.qwaicode.persiansubtitles.domain.ai.ContextBrief
import com.qwaicode.persiansubtitles.domain.lang.TargetLanguages
import com.qwaicode.persiansubtitles.domain.prompt.PromptMode
import com.qwaicode.persiansubtitles.domain.text.BidiMode
import com.qwaicode.persiansubtitles.network.FreeModelCatalog
import com.qwaicode.persiansubtitles.network.GeminiClient
import com.qwaicode.persiansubtitles.network.GeminiErrorKind
import com.qwaicode.persiansubtitles.network.GeminiException
import com.qwaicode.persiansubtitles.network.LimitKind
import com.qwaicode.persiansubtitles.data.usage.TokenUsageRepository
import com.qwaicode.persiansubtitles.work.TranslationScheduler
import com.qwaicode.persiansubtitles.work.TranslationEngine
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.qwaicode.persiansubtitles.domain.progress.EtaEstimator

/** Counters shown on the translate tab. */
data class Stats(
    val total: Int = 0,
    val translated: Int = 0,
    val remaining: Int = 0,
    val flagged: Int = 0,
    val ads: Int = 0,
    val edited: Int = 0,
) {
    val progress: Float
        get() = if (total == 0) 0f else (translated.toFloat() / total).coerceIn(0f, 1f)
}

enum class EditorFilter { ALL, UNTRANSLATED, EDITED, FLAGGED, ADS }
sealed interface ApiTestState {
    data object Idle : ApiTestState
    data object Running : ApiTestState
    data class Success(val modelCount: Int) : ApiTestState
    data class Failure(val messageRes: Int, val detail: String?) : ApiTestState
}

/** A one-shot snackbar message. */
data class UiMessage(val res: Int, val arg: Any? = null)

/**
 * Shown in the "translation finished" dialog. The counters are taken from the
 * database at the moment the run ends, not from the live UI state, so the numbers
 * in the dialog are the numbers of the finished file.
 */
data class CompletionInfo(
    val total: Int,
    val translated: Int,
    val flagged: Int,
    val skipped: Int,
    val ads: Int,
)

/**
 * The key reached a Gemini limit. Shown as a dialog, because a snackbar is gone
 * before anybody reads it and the user has a decision to make.
 */
data class QuotaAlert(
    val daily: Boolean,
    /** The model that hit the limit. */
    val modelId: String,
    val modelLabel: String,
    /** Another free model with its own quota, or null when there is none left to try. */
    val suggestedModelId: String?,
    val suggestedModelLabel: String?,
    /** Parallel requests at the time; a per-minute limit is hit sooner with more of them. */
    val concurrency: Int,
    /** Milliseconds until the daily free quota resets (midnight Pacific time). */
    val resetInMs: Long,
    /** True when a run was stopped by it and can be resumed. */
    val canResume: Boolean,
)

/**
 * The "AI is reading the subtitle" dialog.
 *
 * The analysis is one long request, so there is no real percentage to report. The
 * dialog therefore shows what is known for sure — the stage, the elapsed time — and
 * an estimate that approaches but never reaches the end until the answer is in.
 */
data class AnalysisProgress(
    val startedAt: Long,
    val totalCues: Int,
    /** Rough duration of the request for a file of this size. */
    val expectedMs: Long,
    val waitingForQuota: Boolean = false,
    /** Set when the analysis is over; the dialog shows the result briefly. */
    val finished: Boolean = false,
    val succeeded: Boolean = false,
    val finishedAt: Long = 0L,
    val title: String? = null,
    val tonePresetId: String? = null,
)

/** One limit as shown on screen, and whether the user set it or Google reported it. */
data class LimitUi(val value: Long, val manual: Boolean)

/** The token and request usage of one model on the current quota day. */
data class ModelUsageUi(
    val id: String,
    val label: String,
    val selected: Boolean,
    val requestsToday: Long,
    val promptTokens: Long,
    val outputTokens: Long,
    val thoughtTokens: Long,
    val totalTokens: Long,
    val requestsLastMinute: Int,
    val inputTokensLastMinute: Long,
    val requestsPerDay: LimitUi?,
    val requestsPerMinute: LimitUi?,
    val inputTokensPerMinute: LimitUi?,
    val inputTokensPerDay: LimitUi?,
)

/** The "token usage" section of the settings tab. */
data class TokenUsageUi(
    val models: List<ModelUsageUi> = emptyList(),
    val resetInMs: Long = 0L,
)

private const val ETA_TICK_MS = 5_000L
private const val USAGE_TICK_MS = 5_000L
private const val ANALYSIS_RESULT_VISIBLE_MS = 2_600L

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = DatabaseProvider.get(app).dao()
    private val settingsRepository = SettingsRepository(app)
    private val repository = SubtitleRepository(app, dao, settingsRepository)
    private val api = GeminiClient()
    private val usageRepository = TokenUsageRepository.get(app)

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val project: StateFlow<ProjectEntity?> = dao.observeProject()
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** What reading the whole subtitle produced, or null if it has not run yet. */
    val contextBrief: StateFlow<ContextBrief?> = project
        .map { ContextBrief.fromStorage(it?.contextBrief) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * The full cue list — only collected while the editor tab is actually on screen.
     *
     * This used to be `Eagerly`, which meant that during a run every finished batch
     * pushed all 3000 rows through the flow, whether or not anybody was looking at
     * them. That was the app's main source of stutter and of the freeze when
     * switching tabs. The counters no longer come from here at all (see [stats]), so
     * nothing outside the editor needs this list.
     */
    val cues: StateFlow<List<CueEntity>> = dao.observeCues()
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), emptyList())

    /**
     * Counted by SQLite in a single query instead of by mapping the whole cue list
     * in Kotlin — the difference between six numbers and 3000 objects per update.
     */
    val stats: StateFlow<Stats> = dao.observeStats()
        .map { row ->
            Stats(
                total = row.total,
                translated = row.translated,
                remaining = row.remaining,
                flagged = row.flagged,
                ads = row.ads,
                edited = row.edited,
            )
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, Stats())

    /**
     * Speed and time left of the running translation. A ticker drives it as well as
     * the counters, so a quota pause visibly slows the estimate down instead of
     * freezing it at the last good value.
     */
    val eta: StateFlow<EtaEstimator.Estimate?> = run {
        val estimator = EtaEstimator()
        val ticker = flow {
            while (true) {
                emit(System.currentTimeMillis())
                delay(ETA_TICK_MS)
            }
        }
        combine(stats, project.map { it?.status }.distinctUntilChanged(), ticker) { s, status, now ->
            if (status != ProjectStatus.RUNNING) {
                estimator.reset()
                null
            } else {
                estimator.update(now, s.translated, s.total)
            }
        }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    }

    // ---- editor state ---------------------------------------------------
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filter = MutableStateFlow(EditorFilter.ALL)
    val filter: StateFlow<EditorFilter> = _filter.asStateFlow()

    @OptIn(FlowPreview::class)
    val visibleCues: StateFlow<List<CueEntity>> =
        combine(cues, _query.debounce(120), _filter) { list, query, filter ->
            val needle = query.trim()
            list.asSequence()
                .filter { cue ->
                    when (filter) {
                        EditorFilter.ALL -> true
                        EditorFilter.UNTRANSLATED -> !cue.isTranslated
                        EditorFilter.EDITED -> cue.edited
                        EditorFilter.FLAGGED -> cue.flagged
                        EditorFilter.ADS -> cue.isAd
                    }
                }
                .filter { cue ->
                    needle.isEmpty() ||
                        cue.source.contains(needle, ignoreCase = true) ||
                        cue.translated?.contains(needle, ignoreCase = true) == true
                }
                .toList()
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---- api / models ---------------------------------------------------
    private val _testState = MutableStateFlow<ApiTestState>(ApiTestState.Idle)
    val testState: StateFlow<ApiTestState> = _testState.asStateFlow()

    private val _modelsLoading = MutableStateFlow(false)
    val modelsLoading: StateFlow<Boolean> = _modelsLoading.asStateFlow()

    val models: StateFlow<List<FreeModelCatalog.Entry>> = settings
        .map { s -> FreeModelCatalog.decode(s.cachedModels).ifEmpty { FreeModelCatalog.fallback } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, FreeModelCatalog.fallback)

    /**
     * Token usage per model for the settings tab: the selected model first, then
     * every other model that was used today. Refreshed by every request and by a
     * ticker, so the per-minute window and the reset countdown keep moving.
     */
    val tokenUsage: StateFlow<TokenUsageUi> = run {
        val ticker = flow {
            while (true) {
                emit(Unit)
                delay(USAGE_TICK_MS)
            }
        }
        combine(
            usageRepository.snapshot,
            usageRepository.version,
            settings.map { it.model }.distinctUntilChanged(),
            models,
            ticker,
        ) { snapshot, _, selected, offered, _ ->
            val now = System.currentTimeMillis()
            val today = com.qwaicode.persiansubtitles.data.usage.QuotaClock.dayKey(now)
            val usage = if (snapshot.day == today) snapshot.usage else emptyMap()
            val ids = buildList {
                add(selected)
                usage.entries
                    .sortedByDescending { it.value.lastUsedAt }
                    .forEach { if (it.key != selected) add(it.key) }
            }
            TokenUsageUi(
                models = ids.map { id ->
                    val u = usage[id] ?: com.qwaicode.persiansubtitles.data.usage.ModelUsage()
                    val limits = snapshot.limits[id] ?: com.qwaicode.persiansubtitles.data.usage.ModelLimits()
                    val (rpm, tpm) = usageRepository.lastMinute(id, now)
                    fun limit(kind: LimitKind) = limits.effective(kind)?.let { LimitUi(it, limits.isManual(kind)) }
                    ModelUsageUi(
                        id = id,
                        label = offered.firstOrNull { it.id == id }?.label ?: FreeModelCatalog.entryFor(id).label,
                        selected = id == selected,
                        requestsToday = u.requests,
                        promptTokens = u.promptTokens,
                        outputTokens = u.outputTokens,
                        thoughtTokens = u.thoughtTokens,
                        totalTokens = u.totalTokens,
                        requestsLastMinute = rpm,
                        inputTokensLastMinute = tpm,
                        requestsPerDay = limit(LimitKind.REQUESTS_PER_DAY),
                        requestsPerMinute = limit(LimitKind.REQUESTS_PER_MINUTE),
                        inputTokensPerMinute = limit(LimitKind.INPUT_TOKENS_PER_MINUTE),
                        inputTokensPerDay = limit(LimitKind.INPUT_TOKENS_PER_DAY),
                    )
                },
                resetInMs = com.qwaicode.persiansubtitles.data.usage.QuotaClock.millisUntilReset(now),
            )
        }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), TokenUsageUi())
    }

    fun resetTokenUsage() = viewModelScope.launch {
        usageRepository.resetUsage()
        send(UiMessage(R.string.usage_reset_done))
    }

    fun setManualLimits(model: String, requestsPerDay: Long?, tokensPerMinute: Long?) = viewModelScope.launch {
        usageRepository.setManualLimits(model, requestsPerDay, tokensPerMinute)
        send(UiMessage(R.string.usage_limits_saved))
    }

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private val _completion = MutableStateFlow<CompletionInfo?>(null)
    /** Non-null while the "translation finished" dialog should be on screen. */
    val completion: StateFlow<CompletionInfo?> = _completion.asStateFlow()

    fun dismissCompletion() { _completion.value = null }

    // ---- quota / analysis dialogs --------------------------------------
    private val _quotaAlert = MutableStateFlow<QuotaAlert?>(null)
    /** Non-null while the "limit reached" dialog should be on screen. */
    val quotaAlert: StateFlow<QuotaAlert?> = _quotaAlert.asStateFlow()

    /** Models that reported their daily quota as used up during this session. */
    private val exhaustedModels = mutableSetOf<String>()

    /** The per-minute dialog is shown once per run, not after every pause. */
    private var minuteAlertShown = false

    fun dismissQuotaAlert() { _quotaAlert.value = null }

    private val _analysis = MutableStateFlow<AnalysisProgress?>(null)
    /** Non-null while the analysis dialog should be on screen. */
    val analysis: StateFlow<AnalysisProgress?> = _analysis.asStateFlow()

    /** Hides the dialog; the analysis itself keeps running in the background. */
    fun hideAnalysis() { _analysis.value = null }

    init {
        // The run finishes in a background worker, so the UI has to notice it by
        // watching the stored status. Only a real transition into DONE reports
        // anything — reopening the app on an already finished project does not.
        viewModelScope.launch {
            var previous: String? = null
            var first = true
            dao.observeProject()
                .distinctUntilChanged { old, new -> old?.status == new?.status }
                .collect { project ->
                    val status = project?.status
                    if (!first && status == ProjectStatus.DONE && previous != ProjectStatus.DONE) {
                        // A manual correction scan gets one short message instead of
                        // the "translation ready" dialog: opening a dialog with an
                        // export button on top of the message the user just triggered
                        // is what made the two collide on screen.
                        if (project?.phase == TranslationEngine.PHASE_POLISH) {
                            send(UiMessage(R.string.msg_polish_done))
                        } else {
                            val cues = dao.allCues()
                            _completion.value = CompletionInfo(
                                total = cues.size,
                                translated = cues.count { it.isTranslated },
                                flagged = cues.count { it.flagged },
                                skipped = cues.count { it.attempts >= ATTEMPTS_SKIPPED },
                                ads = cues.count { it.isAd },
                            )
                        }
                    }
                    previous = status
                    first = false
                }
        }

        // Follows the run for the two dialogs: the analysis progress and the
        // "limit reached" alert.
        viewModelScope.launch {
            var analyzing = false
            var previousStatus: String? = null
            var previousPhase: String? = null
            // Straight from the database, not from [project]: that one starts with a
            // placeholder null, which would make the stored state look like a fresh
            // transition every time the app is opened.
            dao.observeProject().distinctUntilChanged().collect { p ->
                val status = p?.status
                val phase = p?.phase
                val nowAnalyzing = status == ProjectStatus.RUNNING &&
                    (phase == TranslationEngine.PHASE_ANALYZE || phase == TranslationEngine.PHASE_ANALYZE_WAIT)

                if (nowAnalyzing && p != null) {
                    val waiting = phase == TranslationEngine.PHASE_ANALYZE_WAIT
                    val current = _analysis.value
                    if (!analyzing) {
                        _analysis.value = AnalysisProgress(
                            startedAt = System.currentTimeMillis(),
                            totalCues = p.totalCues,
                            expectedMs = expectedAnalysisMs(p.totalCues),
                            waitingForQuota = waiting,
                        )
                    } else if (current != null && current.waitingForQuota != waiting) {
                        _analysis.value = current.copy(waitingForQuota = waiting)
                    }
                    analyzing = true
                } else if (analyzing) {
                    analyzing = false
                    val current = _analysis.value
                    val brief = ContextBrief.fromStorage(p?.contextBrief)
                    if (current == null || p == null || status == ProjectStatus.PAUSED ||
                        status == ProjectStatus.ERROR || status == ProjectStatus.IDLE
                    ) {
                        // Paused, cleared or failed: the dialog just goes away (a
                        // failure brings its own dialog).
                        _analysis.value = null
                    } else {
                        val finished = current.copy(
                            finished = true,
                            succeeded = brief != null,
                            finishedAt = System.currentTimeMillis(),
                            title = brief?.title?.trim()?.takeIf { it.isNotBlank() },
                            tonePresetId = brief?.tonePreset?.takeIf { it.isNotBlank() },
                        )
                        _analysis.value = finished
                        launch {
                            delay(if (finished.succeeded) ANALYSIS_RESULT_VISIBLE_MS else ANALYSIS_RESULT_VISIBLE_MS * 2)
                            if (_analysis.value === finished) _analysis.value = null
                        }
                    }
                }

                // The limit dialogs react to a transition only, never to the state
                // the app was reopened in.
                val entered = status != previousStatus || phase != previousPhase
                if (entered && previousStatus != null) {
                    if (status == ProjectStatus.ERROR && phase == GeminiErrorKind.QUOTA_DAILY.name) {
                        showQuotaAlert(daily = true, canResume = true)
                    } else if (status == ProjectStatus.RUNNING &&
                        phase == com.qwaicode.persiansubtitles.work.TranslationWorker.PHASE_WAITING_QUOTA &&
                        !minuteAlertShown
                    ) {
                        minuteAlertShown = true
                        showQuotaAlert(daily = false, canResume = false)
                    }
                }
                previousStatus = status ?: ""
                previousPhase = phase
            }
        }

        // Re-attach an interrupted run (process death, reboot, connection loss).
        viewModelScope.launch {
            val current = dao.project()
            if (current?.status == ProjectStatus.RUNNING ||
                current?.status == ProjectStatus.REVIEW ||
                current?.status == ProjectStatus.POLISH
            ) {
                TranslationScheduler.resume(getApplication(), settingsRepository.settings.first().wifiOnly)
            }
        }
    }

    // ---- project actions ------------------------------------------------
    fun import(uri: Uri) = viewModelScope.launch {
        repository.import(uri)
            .onSuccess { count ->
                resetTransientState()
                send(UiMessage(R.string.msg_import_ok, count))
            }
            .onFailure { send(UiMessage(R.string.error_import)) }
    }

    /** Everything the screen keeps about the current file outside the database. */
    private fun resetTransientState() {
        _completion.value = null
        _analysis.value = null
        _quotaAlert.value = null
        _query.value = ""
        _filter.value = EditorFilter.ALL
        minuteAlertShown = false
    }

    fun start() = viewModelScope.launch {
        if (settings.value.apiKey.isBlank()) {
            send(UiMessage(R.string.error_no_api_key)); return@launch
        }
        if (stats.value.total == 0) {
            send(UiMessage(R.string.error_no_project)); return@launch
        }
        minuteAlertShown = false
        repository.start()
    }

    fun pause() = viewModelScope.launch { repository.pause() }

    fun review() = viewModelScope.launch {
        if (settings.value.apiKey.isBlank()) {
            send(UiMessage(R.string.error_no_api_key)); return@launch
        }
        if (stats.value.total == 0) {
            send(UiMessage(R.string.error_no_project)); return@launch
        }
        repository.review()
    }

    /** Runs the editorial scan over the finished file on demand. */
    fun polish() = viewModelScope.launch {
        if (settings.value.apiKey.isBlank()) {
            send(UiMessage(R.string.error_no_api_key)); return@launch
        }
        if (stats.value.translated == 0) {
            send(UiMessage(R.string.error_no_project)); return@launch
        }
        repository.polish()
        send(UiMessage(R.string.msg_polish_started))
    }

    fun clearWorkspace() = viewModelScope.launch {
        repository.clearWorkspace()
        resetTransientState()
        send(UiMessage(R.string.msg_cleared))
    }

    fun export(uri: Uri, format: ExportFormat) = viewModelScope.launch {
        repository.export(uri, format)
            .onSuccess { send(UiMessage(R.string.msg_export_ok)) }
            .onFailure { send(UiMessage(R.string.error_export)) }
    }

    fun suggestedFileName(format: ExportFormat): String =
        com.qwaicode.persiansubtitles.domain.subtitle.SubtitleExporter.suggestedFileName(
            project.value?.fileName ?: "subtitle.srt",
            format,
            settings.value.targetLanguage,
        )

    // ---- editor actions -------------------------------------------------
    fun setQuery(value: String) { _query.value = value }

    fun setFilter(value: EditorFilter) { _filter.value = value }

    fun saveEdit(id: Int, text: String) = viewModelScope.launch {
        repository.saveEdit(id, text)
        send(UiMessage(R.string.msg_saved))
    }

    fun retranslateLine(id: Int) = viewModelScope.launch {
        repository.retranslate(id)
        send(UiMessage(R.string.msg_queued_line))
    }

    fun toggleAd(id: Int, isAd: Boolean) = viewModelScope.launch { repository.setAd(id, isAd) }

    fun rescanAds() = viewModelScope.launch {
        val detected = repository.rescanAds()
        send(UiMessage(R.string.copyright_detected, detected))
    }

    // ---- settings -------------------------------------------------------
    fun setApiKey(value: String) = viewModelScope.launch {
        settingsRepository.setApiKey(value)
        _testState.value = ApiTestState.Idle
    }

    /**
     * Picks the language the subtitle is translated into. Refused while a run is in
     * progress — the batches in flight are already in the old language.
     */
    fun setTargetLanguage(code: String) = viewModelScope.launch {
        val status = project.value?.status
        if (status == ProjectStatus.RUNNING || status == ProjectStatus.REVIEW || status == ProjectStatus.POLISH) {
            send(UiMessage(R.string.error_language_while_running)); return@launch
        }
        if (code == settings.value.targetLanguage) return@launch
        val cleared = repository.changeTargetLanguage(code)
        val name = TargetLanguages.byCode(code).let { "${it.flag} ${it.nameFa}" }
        send(UiMessage(if (cleared) R.string.msg_language_changed_reset else R.string.msg_language_changed, name))
    }

    fun setModel(value: String) = viewModelScope.launch { settingsRepository.setModel(value) }
    fun setPreset(value: String) = viewModelScope.launch { settingsRepository.setPreset(value) }
    fun setCustomPrompt(value: String) = viewModelScope.launch { settingsRepository.setCustomPrompt(value) }
    fun setPromptMode(value: PromptMode) = viewModelScope.launch { settingsRepository.setPromptMode(value) }
    fun setTemperature(value: Float) = viewModelScope.launch { settingsRepository.setTemperature(value) }
    fun setBatchSize(value: Int) = viewModelScope.launch { settingsRepository.setBatchSize(value) }
    fun setConcurrency(value: Int) = viewModelScope.launch { settingsRepository.setConcurrency(value) }
    fun setKeepProperNames(value: Boolean) = viewModelScope.launch { settingsRepository.setKeepProperNames(value) }
    fun setAutoReview(value: Boolean) = viewModelScope.launch { settingsRepository.setAutoReview(value) }
    fun setAutoPolish(value: Boolean) = viewModelScope.launch { settingsRepository.setAutoPolish(value) }
    fun setRemoveAds(value: Boolean) = viewModelScope.launch { settingsRepository.setRemoveAds(value) }
    fun setAdEdgesOnly(value: Boolean) = viewModelScope.launch { settingsRepository.setAdEdgesOnly(value) }
    fun setAdPatterns(value: String) = viewModelScope.launch { settingsRepository.setAdPatterns(value) }
    fun setStripTags(value: Boolean) = viewModelScope.launch { settingsRepository.setStripTags(value) }
    fun setSignatureEnabled(value: Boolean) = viewModelScope.launch { settingsRepository.setSignatureEnabled(value) }
    fun setSignatureText(value: String) = viewModelScope.launch { settingsRepository.setSignatureText(value) }
    fun setSignatureCount(value: Int) = viewModelScope.launch { settingsRepository.setSignatureCount(value) }
    fun setWifiOnly(value: Boolean) = viewModelScope.launch { settingsRepository.setWifiOnly(value) }
    fun setBidiMode(value: BidiMode) = viewModelScope.launch { settingsRepository.setBidiMode(value) }
    fun setPersianPunctuation(value: Boolean) = viewModelScope.launch { settingsRepository.setPersianPunctuation(value) }
    fun setReadWholeSubtitle(value: Boolean) = viewModelScope.launch { settingsRepository.setReadWholeSubtitle(value) }
    fun setAutoTone(value: Boolean) = viewModelScope.launch { settingsRepository.setAutoTone(value) }
    fun setUserGlossary(value: String) = viewModelScope.launch { settingsRepository.setUserGlossary(value) }

    /** Nudges the export sync offset; `0` as [deltaMs] with [reset] puts it back to zero. */
    fun shiftTiming(deltaMs: Int, reset: Boolean = false) = viewModelScope.launch {
        val next = if (reset) 0 else settings.value.timeShiftMs + deltaMs
        settingsRepository.setTimeShiftMs(next)
    }

    /** Drops the stored brief; the next run reads the subtitle again. */
    fun rereadSubtitle() = viewModelScope.launch {
        repository.clearContextBrief()
        send(UiMessage(R.string.msg_brief_cleared))
    }

    fun restoreDefaultPatterns() = viewModelScope.launch {
        settingsRepository.setAdPatterns(CopyrightCleaner.DEFAULT_PATTERNS_TEXT)
        send(UiMessage(R.string.msg_default_patterns_restored))
    }

    /** Verifies the key and caches the free models it grants access to. */
    fun testConnection() = viewModelScope.launch {
        val key = settings.value.apiKey
        if (key.isBlank()) {
            _testState.value = ApiTestState.Failure(R.string.error_no_api_key, null)
            return@launch
        }
        _testState.value = ApiTestState.Running
        _modelsLoading.value = true
        try {
            val free = FreeModelCatalog.filter(api.listModels(key))
            if (free.isNotEmpty()) {
                // A newly entered key starts on Gemini 3.1 Flash-Lite: it has by far
                // the largest free quota. A later refresh keeps the user's choice.
                settingsRepository.applyLoadedModels(
                    apiKey = key,
                    encodedModels = FreeModelCatalog.encode(free),
                    preferredModel = FreeModelCatalog.preferredFrom(free),
                    offered = free.map { it.id }.toSet(),
                )
            }
            _testState.value = ApiTestState.Success(free.size)
        } catch (e: GeminiException) {
            _testState.value = ApiTestState.Failure(errorRes(e.kind), e.message)
            if (e.kind.isQuota) showQuotaAlert(daily = e.kind == GeminiErrorKind.QUOTA_DAILY, canResume = false)
        } catch (e: Exception) {
            _testState.value = ApiTestState.Failure(R.string.error_unknown, e.message)
        } finally {
            _modelsLoading.value = false
        }
    }

    fun refreshModels() = testConnection()

    /**
     * Fills the "limit reached" dialog. For the daily limit another free model is
     * suggested — the free quota is counted per model, so the next one usually still
     * has plenty — preferring the recommended one and skipping models that already
     * ran out today.
     */
    private fun showQuotaAlert(daily: Boolean, canResume: Boolean) {
        val current = settings.value.model
        if (daily) exhaustedModels += current
        val offered = models.value
        val suggestion = if (daily) {
            offered.firstOrNull { it.id != current && it.id !in exhaustedModels && !it.preview }
                ?: offered.firstOrNull { it.id != current && it.id !in exhaustedModels }
        } else {
            offered.firstOrNull { it.id == FreeModelCatalog.RECOMMENDED && it.id != current }
        }
        _quotaAlert.value = QuotaAlert(
            daily = daily,
            modelId = current,
            modelLabel = offered.firstOrNull { it.id == current }?.label ?: FreeModelCatalog.entryFor(current).label,
            suggestedModelId = suggestion?.id,
            suggestedModelLabel = suggestion?.label,
            concurrency = settings.value.concurrency,
            resetInMs = millisUntilDailyReset(),
            canResume = canResume && stats.value.total > 0,
        )
    }

    /** Switches to the suggested model and, if a run was stopped by the limit, continues it. */
    fun useSuggestedModel() = viewModelScope.launch {
        val alert = _quotaAlert.value ?: return@launch
        _quotaAlert.value = null
        val target = alert.suggestedModelId ?: return@launch
        settingsRepository.setModel(target)
        send(UiMessage(R.string.msg_model_switched, alert.suggestedModelLabel ?: target))
        if (alert.canResume && project.value?.status == ProjectStatus.ERROR) {
            // Wait until the new model is really in the settings the worker reads.
            settings.first { it.model == target }
            minuteAlertShown = false
            repository.start()
        }
    }

    /** Fewer parallel requests = the per-minute limit is reached less often. */
    fun reduceConcurrencyForQuota() = viewModelScope.launch {
        _quotaAlert.value = null
        val next = (settings.value.concurrency / 2).coerceAtLeast(1)
        settingsRepository.setConcurrency(next)
        send(UiMessage(R.string.msg_concurrency_reduced, next))
    }

    /** The free daily quota resets at midnight Pacific time. */
    private fun millisUntilDailyReset(): Long =
        com.qwaicode.persiansubtitles.data.usage.QuotaClock.millisUntilReset()

    /** One request reads the whole file: a few seconds plus a little per line. */
    private fun expectedAnalysisMs(totalCues: Int): Long =
        (8_000L + totalCues.coerceAtLeast(0) * 12L).coerceIn(8_000L, 45_000L)

    private fun errorRes(kind: GeminiErrorKind): Int = when (kind) {
        GeminiErrorKind.NO_KEY -> R.string.error_no_api_key
        GeminiErrorKind.AUTH -> R.string.error_auth
        GeminiErrorKind.QUOTA -> R.string.error_quota
        GeminiErrorKind.QUOTA_DAILY -> R.string.error_quota_daily
        GeminiErrorKind.SERVER -> R.string.error_server
        GeminiErrorKind.NETWORK -> R.string.error_network
        GeminiErrorKind.PARSE -> R.string.error_parse
        GeminiErrorKind.SAFETY -> R.string.error_safety
        GeminiErrorKind.UNKNOWN -> R.string.error_unknown
    }

    /** Maps the worker's stored error key back to a readable message. */
    fun errorResFor(phase: String?): Int? {
        val kind = runCatching { phase?.let { GeminiErrorKind.valueOf(it) } }.getOrNull() ?: return null
        return errorRes(kind)
    }

    private suspend fun send(message: UiMessage) { _messages.send(message) }
}
