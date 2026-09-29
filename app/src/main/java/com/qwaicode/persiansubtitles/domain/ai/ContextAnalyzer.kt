package com.qwaicode.persiansubtitles.domain.ai

import com.qwaicode.persiansubtitles.data.db.CueEntity
import com.qwaicode.persiansubtitles.data.prefs.AppSettings
import com.qwaicode.persiansubtitles.domain.prompt.PromptBuilder
import com.qwaicode.persiansubtitles.network.GeminiClient

/**
 * Reads the subtitle before the translation starts.
 *
 * One request, one answer, and from then on every batch knows the film: who the
 * characters are and how their names are spelled in Persian, whether they speak
 * formally or informally, what recurring term means what. That is what makes a
 * 1500-line translation read like one translator did it rather than seventy
 * independent ones.
 *
 * It is deliberately failure-tolerant: no key quota, no answer, unparsable JSON —
 * the caller gets `null` and translates without a brief, exactly as before.
 */
class ContextAnalyzer(
    private val api: GeminiClient = GeminiClient(),
) {

    suspend fun analyze(cues: List<CueEntity>, settings: AppSettings, fileName: String?): ContextBrief? {
        if (cues.isEmpty()) return null
        val digest = SubtitleDigest.build(cues.map { it.source })
        if (digest.isBlank()) return null

        val raw = api.generateAnalysis(
            apiKey = settings.apiKey,
            model = settings.model,
            systemInstruction = PromptBuilder.analysisInstruction(settings.language),
            userPayload = PromptBuilder.analysisPayload(
                fileName = fileName,
                totalCues = cues.size,
                digest = digest,
            ),
        )
        return ContextBrief.parse(raw)
    }
}
