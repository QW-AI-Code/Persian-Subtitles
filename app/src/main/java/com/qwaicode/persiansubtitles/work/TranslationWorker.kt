package com.qwaicode.persiansubtitles.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.qwaicode.persiansubtitles.R
import com.qwaicode.persiansubtitles.data.db.DatabaseProvider
import com.qwaicode.persiansubtitles.data.db.ProjectStatus
import com.qwaicode.persiansubtitles.data.prefs.SettingsRepository
import com.qwaicode.persiansubtitles.network.GeminiErrorKind

/**
 * Runs the translation as a foreground worker: it keeps going while the app is in
 * the background, is restarted by WorkManager after a crash or reboot, and waits
 * for the network instead of failing when the connection drops.
 */
class TranslationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    private val dao = DatabaseProvider.get(appContext).dao()
    private val settingsRepository = SettingsRepository(appContext)
    private val engine = TranslationEngine(
        dao = dao,
        settings = settingsRepository.settings,
        // The AI read the film and picked a tone: store it, so the Style tab shows
        // the choice and every later run (and the user) starts from it.
        onToneDetected = { presetId -> settingsRepository.applyAutoTone(presetId) },
    )

    /** Throttles the progress notification: it used to be rebuilt on every wave. */
    private var lastNotified = 0L

    /** True when this run was started by the "scan and correct" button. */
    private val polishOnly by lazy { inputData.getBoolean(KEY_FORCE_POLISH, false) }

    /** True when this run was started by the "review" button. */
    private val reviewRequested by lazy { inputData.getBoolean(KEY_FORCE_REVIEW, false) }

    override suspend fun doWork(): Result {
        val total = dao.countAll()
        if (total == 0) return Result.success()

        setStatus(ProjectStatus.RUNNING, TranslationEngine.PHASE_TRANSLATE, null)
        updateNotification(applicationContext.getString(R.string.notif_translating, dao.countTranslated(), total), dao.countTranslated(), total, force = true)

        val outcome = engine.run(
            onProgress = { phase, done, count ->
                setStatus(
                    when (phase) {
                        TranslationEngine.PHASE_REVIEW -> ProjectStatus.REVIEW
                        // Its own status, so the translate tab can say what is
                        // happening instead of still claiming "translating".
                        TranslationEngine.PHASE_POLISH -> ProjectStatus.POLISH
                        else -> ProjectStatus.RUNNING
                    },
                    phase,
                    null,
                )
                val text = when (phase) {
                    TranslationEngine.PHASE_REVIEW -> applicationContext.getString(R.string.notif_reviewing)
                    TranslationEngine.PHASE_ANALYZE,
                    TranslationEngine.PHASE_ANALYZE_WAIT -> applicationContext.getString(R.string.notif_analyzing)
                    TranslationEngine.PHASE_POLISH -> applicationContext.getString(R.string.notif_polishing)
                    else -> applicationContext.getString(R.string.notif_translating, done, count)
                }
                updateNotification(text, done, count)
                setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to count, KEY_PHASE to phase))
            },
            isStopped = { isStopped },
            forcePolish = polishOnly,
            forceReview = reviewRequested,
        )

        return when (outcome) {
            is TranslationEngine.Outcome.Completed -> {
                // The phase is kept on purpose: it is how the UI tells a finished
                // translation (which opens the "ready" dialog) apart from a manual
                // correction scan (which only reports itself in one short message).
                setStatus(
                    ProjectStatus.DONE,
                    if (polishOnly) TranslationEngine.PHASE_POLISH else null,
                    null,
                )
                Notifications.cancelProgress(applicationContext)
                Notifications.result(
                    applicationContext,
                    applicationContext.getString(
                        if (polishOnly) R.string.notif_polish_done else R.string.notif_done
                    ),
                )
                Result.success()
            }

            is TranslationEngine.Outcome.Stopped -> {
                // Paused by the user or stopped by the system: stay resumable.
                val current = dao.project()?.status
                if (current == ProjectStatus.RUNNING ||
                    current == ProjectStatus.REVIEW ||
                    current == ProjectStatus.POLISH
                ) {
                    setStatus(ProjectStatus.PAUSED, null, null)
                }
                Result.success()
            }

            is TranslationEngine.Outcome.Retry -> {
                // A per-minute limit gets its own phase: the app tells the user
                // in a dialog that the key hit its limit and the run is waiting.
                setStatus(
                    ProjectStatus.RUNNING,
                    if (outcome.kind == GeminiErrorKind.QUOTA) PHASE_WAITING_QUOTA else PHASE_WAITING,
                    outcome.message,
                )
                updateNotification(applicationContext.getString(R.string.notif_waiting), dao.countTranslated(), total, force = true)
                Result.retry()
            }

            is TranslationEngine.Outcome.Failed -> {
                setStatus(ProjectStatus.ERROR, errorKey(outcome.kind), outcome.message)
                Notifications.cancelProgress(applicationContext)
                Notifications.result(applicationContext, applicationContext.getString(R.string.notif_error))
                Result.failure()
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = buildForegroundInfo(
        applicationContext.getString(R.string.notif_translating, dao.countTranslated(), dao.countAll()),
        dao.countTranslated(),
        dao.countAll(),
    )

    private suspend fun updateNotification(text: String, done: Int, total: Int, force: Boolean = false) {
        // Rebuilding the foreground notification is a binder call to the system
        // server; doing it for every one of a few hundred waves competes with the UI
        // for the main thread. Twice a second is more than a human can read.
        val now = System.currentTimeMillis()
        if (!force && now - lastNotified < NOTIFY_INTERVAL_MS) return
        lastNotified = now
        runCatching { setForeground(buildForegroundInfo(text, done, total)) }
    }

    private fun buildForegroundInfo(text: String, done: Int, total: Int): ForegroundInfo {
        val notification = Notifications.progress(applicationContext, text, done, total)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(Notifications.PROGRESS_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(Notifications.PROGRESS_ID, notification)
        }
    }

    private suspend fun setStatus(status: String, phase: String?, error: String?) {
        dao.setStatus(status, phase, error, System.currentTimeMillis())
    }

    private fun errorKey(kind: GeminiErrorKind): String = kind.name

    companion object {
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_PHASE = "phase"
        const val PHASE_WAITING = "waiting"

        /** Waiting because the key reached its per-minute limit. */
        const val PHASE_WAITING_QUOTA = "waiting_quota"

        /** Input flag: run the editorial pass even if the setting is off. */
        const val KEY_FORCE_POLISH = "force_polish"

        /** Input flag: run the review pass even if auto-review is off. */
        const val KEY_FORCE_REVIEW = "force_review"

        private const val NOTIFY_INTERVAL_MS = 500L
    }
}
