package com.qwaicode.persiansubtitles.work

import com.qwaicode.persiansubtitles.data.db.CueEntity
import com.qwaicode.persiansubtitles.data.db.ProjectEntity
import com.qwaicode.persiansubtitles.data.db.ProjectStatus
import com.qwaicode.persiansubtitles.data.prefs.AppSettings
import com.qwaicode.persiansubtitles.domain.ai.ContextBrief
import com.qwaicode.persiansubtitles.network.GeminiClient
import com.qwaicode.persiansubtitles.network.GeminiErrorKind
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The analysis used to be dropped silently on the first error, so a key that was
 * briefly rate-limited translated the film without ever reading it. It is retried
 * now, and a used-up daily quota ends the run with a clear reason.
 */
class AnalysisQuotaEngineTest {

    private val idPattern = Regex(""""id"\s*:\s*(\d+)""")

    private fun quota(id: String) = """
        {"error":{"code":429,"message":"quota","status":"RESOURCE_EXHAUSTED","details":[
          {"@type":"type.googleapis.com/google.rpc.QuotaFailure","violations":[{"quotaId":"$id"}]},
          {"@type":"type.googleapis.com/google.rpc.RetryInfo","retryDelay":"1s"}]}}
    """.trimIndent()

    /** Answers the analysis with [analysisAnswers] in order, translations always succeed. */
    private fun gemini(vararg analysisAnswers: Pair<Int, String>): GeminiClient {
        val queue = ArrayDeque(analysisAnswers.toList())
        val http = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val request = chain.request()
                val buffer = Buffer()
                request.body?.writeTo(buffer)
                val body = buffer.readUtf8()
                val (code, json) = if (body.contains("excerpt") && queue.isNotEmpty()) {
                    queue.removeFirst()
                } else {
                    val lines = body.replace("\\\"", "\"").substringAfter("lines_to_translate", "")
                    val ids = idPattern.findAll(lines).map { it.groupValues[1] }.toList()
                    val answer = "[" + ids.joinToString(",") { """{"id":$it,"fa":"ترجمه $it"}""" } + "]"
                    200 to """{"candidates":[{"content":{"parts":[{"text":${JsonPrimitive(answer)}}]},"finishReason":"STOP"}]}"""
                }
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message(if (code == 200) "OK" else "Error")
                    .body(json.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()
        return GeminiClient(http)
    }

    private val briefAnswer: String = run {
        val brief = """{"title":"Elixir","genre":"درام","summary":"بحران در یک شرکت داروسازی."}"""
        """{"candidates":[{"content":{"parts":[{"text":${JsonPrimitive(brief)}}]},"finishReason":"STOP"}]}"""
    }

    private fun dao() = FakeSubtitleDao(
        listOf(
            CueEntity(id = 1, startMs = 0, endMs = 900, source = "The trial failed."),
            CueEntity(id = 2, startMs = 1000, endMs = 1900, source = "Then we lie."),
        )
    ).apply {
        runBlocking {
            upsertProject(ProjectEntity(fileName = "elixir.srt", format = "srt", totalCues = 2, status = ProjectStatus.RUNNING))
        }
    }

    private val settings = AppSettings(
        apiKey = "test-key",
        batchSize = 10,
        concurrency = 1,
        autoReview = false,
        autoPolish = false,
        readWholeSubtitle = true,
        autoTone = false,
    )

    @Test
    fun `a per-minute limit during the analysis is waited out instead of skipping it`() = runBlocking {
        val dao = dao()
        val phases = mutableListOf<String>()
        val engine = TranslationEngine(
            dao = dao,
            settings = flowOf(settings),
            api = gemini(429 to quota("GenerateRequestsPerMinutePerProjectPerModel-FreeTier"), 200 to briefAnswer),
        )

        val outcome = engine.run(onProgress = { phase, _, _ -> phases += phase }, isStopped = { false })

        assertEquals(TranslationEngine.Outcome.Completed, outcome)
        assertNotNull("the brief must be stored after the retry", ContextBrief.fromStorage(dao.project()?.contextBrief))
        assertTrue(phases.contains(TranslationEngine.PHASE_ANALYZE_WAIT))
        // The analysis phase is left right after the answer, not after the first wave.
        val analyzeEnd = phases.lastIndexOf(TranslationEngine.PHASE_ANALYZE)
        assertEquals(TranslationEngine.PHASE_TRANSLATE, phases[analyzeEnd + 1])
    }

    @Test
    fun `a used-up daily quota stops the run with its own reason`() = runBlocking {
        val engine = TranslationEngine(
            dao = dao(),
            settings = flowOf(settings),
            api = gemini(429 to quota("GenerateRequestsPerDayPerProjectPerModel-FreeTier")),
        )

        val outcome = engine.run(onProgress = { _, _, _ -> }, isStopped = { false })

        assertTrue(outcome is TranslationEngine.Outcome.Failed)
        assertEquals(GeminiErrorKind.QUOTA_DAILY, (outcome as TranslationEngine.Outcome.Failed).kind)
    }
}
