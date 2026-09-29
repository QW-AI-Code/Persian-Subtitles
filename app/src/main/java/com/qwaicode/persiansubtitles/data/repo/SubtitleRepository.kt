package com.qwaicode.persiansubtitles.data.repo

import android.content.Context
import android.net.Uri
import com.qwaicode.persiansubtitles.data.db.CueEntity
import com.qwaicode.persiansubtitles.data.db.ProjectEntity
import com.qwaicode.persiansubtitles.data.db.ProjectStatus
import com.qwaicode.persiansubtitles.data.db.SubtitleDao
import com.qwaicode.persiansubtitles.data.prefs.SettingsRepository
import com.qwaicode.persiansubtitles.domain.subtitle.CopyrightCleaner
import com.qwaicode.persiansubtitles.domain.subtitle.ExportFormat
import com.qwaicode.persiansubtitles.domain.subtitle.SubtitleExporter
import com.qwaicode.persiansubtitles.domain.subtitle.SubtitleIO
import com.qwaicode.persiansubtitles.util.FileUtils
import com.qwaicode.persiansubtitles.work.TranslationScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Everything the UI needs to do with a subtitle project. */
class SubtitleRepository(
    private val context: Context,
    private val dao: SubtitleDao,
    private val settingsRepository: SettingsRepository,
) {

    /** Reads the file, marks credit lines and replaces the workspace. */
    suspend fun import(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            // A run that is still going belongs to the old file. Left alive it kept
            // translating the new cues with the previous film's brief in memory,
            // while the project had already been reset to "idle".
            TranslationScheduler.stop(context)
            // A new file starts from zero: the tone the AI chose for the previous
            // film and its sync offset must not carry over.
            settingsRepository.resetForNewSubtitle()
            val name = FileUtils.displayName(context, uri)
            val raw = FileUtils.readText(context, uri)
            val parsed = SubtitleIO.parse(raw)
            require(parsed.isNotEmpty()) { "no cues found" }

            val settings = settingsRepository.settings.first()
            val regexes = CopyrightCleaner.compile(settings.adPatterns)
            val total = parsed.size

            val cues = parsed.map { cue ->
                val text = if (settings.stripTags) CopyrightCleaner.stripTags(cue.text) else cue.text
                CueEntity(
                    id = cue.index,
                    startMs = cue.startMs,
                    endMs = cue.endMs,
                    source = text,
                    isAd = settings.removeAds && CopyrightCleaner.isAd(
                        text = text,
                        regexes = regexes,
                        position = cue.index,
                        total = total,
                        edgesOnly = settings.adEdgesOnly,
                    ),
                )
            }

            dao.clearCues()
            dao.insertAll(cues)
            dao.upsertProject(
                ProjectEntity(
                    fileName = name,
                    format = SubtitleIO.detectFormat(name, raw),
                    totalCues = cues.size,
                    status = ProjectStatus.IDLE,
                    phase = null,
                    lastError = null,
                    // Explicitly empty: even the same file opened again is read
                    // (analysed) again by the AI on the next start.
                    contextBrief = null,
                )
            )
            cues.size
        }
    }

    suspend fun start() {
        val settings = settingsRepository.settings.first()
        dao.setStatus(ProjectStatus.RUNNING, null, null, System.currentTimeMillis())
        TranslationScheduler.start(context, settings.wifiOnly)
    }

    suspend fun pause() {
        dao.setStatus(ProjectStatus.PAUSED, null, null, System.currentTimeMillis())
        TranslationScheduler.stop(context)
    }

    /**
     * Manual review pass: the worker re-translates the suspicious lines. The review is
     * forced, because otherwise the button did nothing at all whenever "automatic
     * review" was switched off in the settings.
     */
    suspend fun review() {
        dao.setStatus(ProjectStatus.REVIEW, null, null, System.currentTimeMillis())
        val settings = settingsRepository.settings.first()
        TranslationScheduler.start(context, settings.wifiOnly, forceReview = true)
    }

    /**
     * Switches the target language.
     *
     * If the workspace already holds translations, they are in the old language and
     * would end up mixed into the new file, so they are cleared — the cues, timings
     * and ad flags stay. The film brief goes as well: it spells every name in the old
     * language. Returns true when existing translations were cleared.
     */
    suspend fun changeTargetLanguage(code: String): Boolean = withContext(Dispatchers.IO) {
        val current = settingsRepository.settings.first().targetLanguage
        settingsRepository.setTargetLanguage(code)
        if (current == code) return@withContext false
        val hadTranslations = dao.countTranslated() > 0
        if (dao.project() != null) {
            TranslationScheduler.stop(context)
            dao.resetAllTranslations()
            dao.setContextBrief(null, System.currentTimeMillis())
            dao.setStatus(ProjectStatus.IDLE, null, null, System.currentTimeMillis())
        }
        hadTranslations
    }

    /**
     * Manual editorial pass. Runs the same scan the end of a translation runs, on a
     * file that is already finished — which is what the user wants after reading
     * through the result and finding a few rough lines.
     */
    suspend fun polish() {
        dao.setStatus(ProjectStatus.RUNNING, null, null, System.currentTimeMillis())
        val settings = settingsRepository.settings.first()
        TranslationScheduler.start(context, settings.wifiOnly, forcePolish = true)
    }

    suspend fun retranslate(id: Int) {
        dao.resetTranslation(id)
        val project = dao.project() ?: return
        if (project.status == ProjectStatus.DONE || project.status == ProjectStatus.IDLE) {
            dao.setStatus(ProjectStatus.IDLE, null, null, System.currentTimeMillis())
        }
    }

    suspend fun saveEdit(id: Int, text: String) {
        dao.setTranslation(id, text.trim(), edited = true, flagged = false)
    }

    suspend fun setAd(id: Int, isAd: Boolean) = dao.setAd(id, isAd)

    /** Re-runs the credit detection with the current patterns. */
    suspend fun rescanAds(): Int = withContext(Dispatchers.IO) {
        val settings = settingsRepository.settings.first()
        val cues = dao.allCues()
        if (cues.isEmpty()) return@withContext 0

        val regexes = CopyrightCleaner.compile(settings.adPatterns)
        val total = cues.size
        var detected = 0
        dao.clearAdFlags()
        if (!settings.removeAds) return@withContext 0

        cues.forEach { cue ->
            val isAd = CopyrightCleaner.isAd(
                text = cue.source,
                regexes = regexes,
                position = cue.id,
                total = total,
                edgesOnly = settings.adEdgesOnly,
            )
            if (isAd) {
                dao.setAd(cue.id, true)
                detected++
            }
        }
        detected
    }

    suspend fun buildExport(format: ExportFormat): String = withContext(Dispatchers.IO) {
        val settings = settingsRepository.settings.first()
        SubtitleExporter.build(
            cues = dao.allCues(),
            format = format,
            dropAds = settings.removeAds,
            signature = settings.signatureText.takeIf { settings.signatureEnabled && it.isNotBlank() },
            signatureCount = settings.signatureCount,
            bidiMode = settings.bidiMode,
            // `،` `؛` `؟` belong to Arabic-script languages only.
            persianPunctuation = settings.persianPunctuation && settings.language.arabicPunctuation,
            // The RTL STYLE block of a VTT file is wrong for a left-to-right language.
            rtlTarget = settings.language.rtl,
            shiftMs = settings.timeShiftMs.toLong(),
        )
    }

    suspend fun export(uri: Uri, format: ExportFormat): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { FileUtils.writeText(context, uri, buildExport(format)) }
    }

    suspend fun suggestedFileName(format: ExportFormat): String {
        val base = dao.project()?.fileName ?: "subtitle.srt"
        return SubtitleExporter.suggestedFileName(base, format, settingsRepository.settings.first().targetLanguage)
    }

    /**
     * Resets the app to "no subtitle loaded". Besides the cues and the project (with
     * the AI's film brief), this also drops what the AI chose and stored in the
     * settings for that file — see [SettingsRepository.resetForNewSubtitle].
     */
    suspend fun clearWorkspace() = withContext(Dispatchers.IO) {
        TranslationScheduler.stop(context)
        dao.clearCues()
        dao.clearProject()
        settingsRepository.resetForNewSubtitle()
    }

    /**
     * Forgets what the app learned about this film, so the next run reads the
     * subtitle again. Useful after replacing the model or the translation style.
     */
    suspend fun clearContextBrief() {
        dao.setContextBrief(null, System.currentTimeMillis())
    }
}
