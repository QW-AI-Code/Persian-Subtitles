package com.qwaicode.persiansubtitles.work

import com.qwaicode.persiansubtitles.data.db.CueEntity
import com.qwaicode.persiansubtitles.data.db.ProjectEntity
import com.qwaicode.persiansubtitles.data.db.ProjectStatus
import com.qwaicode.persiansubtitles.data.prefs.AppSettings
import com.qwaicode.persiansubtitles.domain.ai.ContextBrief
import com.qwaicode.persiansubtitles.domain.prompt.PromptMode
import com.qwaicode.persiansubtitles.domain.prompt.StylePresets
import com.qwaicode.persiansubtitles.network.GeminiClient
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The AI picks the translation tone after reading the whole subtitle.
 *
 * Pinned down here: the tone is applied only right after a fresh reading, it is
 * used by the batches of the very same run (not only from the next run on), and it
 * is left alone when the user switched the feature off, wrote their own prompt as
 * the only style instruction, or the model named a tone the app does not have.
 */
class AutoToneEngineTest {

    /** Every request body, with JSON escapes undone so Persian text is searchable. */
    private val requests = mutableListOf<String>()

    private fun fakeGemini(tonePreset: String): GeminiClient {
        val idPattern = Regex(""""id"\s*:\s*(\d+)""")
        val http = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val request = chain.request()
                val buffer = Buffer()
                request.body?.writeTo(buffer)
                val body = Json.parseToJsonElement(buffer.readUtf8()).toString()
                requests += body

                val answer = if (body.contains("excerpt")) {
                    """{"title":"Office Party","genre":"کمدی","summary":"چند همکار یک مهمانی به‌هم‌ریخته را می‌گذرانند.",""" +
                        """"tone_preset":"$tonePreset","tone_reason":"دیالوگ‌ها پر از شوخی‌اند."}"""
                } else {
                    // The payload is a JSON string inside the request: undo one level
                    // of escaping before looking for the ids.
                    val payload = body.replace("\\\"", "\"")
                    val lines = payload.substringAfter("lines_to_translate", payload.substringAfter("lines_to_fix", ""))
                    val ids = idPattern.findAll(lines).map { it.groupValues[1] }.toList()
                    "[" + ids.joinToString(",") { """{"id":$it,"fa":"ترجمه $it"}""" } + "]"
                }
                val json = """{"candidates":[{"content":{"parts":[{"text":${JsonPrimitive(answer)}}]},"finishReason":"STOP"}]}"""
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(json.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()
        return GeminiClient(http)
    }

    private fun dao(brief: ContextBrief? = null) = FakeSubtitleDao(
        listOf(
            CueEntity(id = 1, startMs = 0, endMs = 900, source = "You brought a cake? To a funeral?"),
            CueEntity(id = 2, startMs = 1000, endMs = 1900, source = "It was on sale."),
        )
    ).apply {
        runBlocking {
            upsertProject(
                ProjectEntity(
                    fileName = "office.party.srt",
                    format = "srt",
                    totalCues = 2,
                    status = ProjectStatus.RUNNING,
                    contextBrief = brief?.let { ContextBrief.toStorage(it) },
                )
            )
        }
    }

    private fun settings(
        autoTone: Boolean = true,
        mode: PromptMode = PromptMode.PRESET,
        customPrompt: String = "",
    ) = AppSettings(
        apiKey = "test-key",
        presetId = "fluent",
        batchSize = 10,
        concurrency = 1,
        autoReview = false,
        autoPolish = false,
        readWholeSubtitle = true,
        autoTone = autoTone,
        promptMode = mode,
        customPrompt = customPrompt,
    )

    private fun run(
        settings: AppSettings,
        dao: FakeSubtitleDao,
        tonePreset: String = "comedy",
    ): List<String> {
        val detected = mutableListOf<String>()
        val engine = TranslationEngine(
            dao = dao,
            settings = flowOf(settings),
            api = fakeGemini(tonePreset),
            onToneDetected = { detected += it },
        )
        runBlocking { engine.run(onProgress = { _, _, _ -> }, isStopped = { false }) }
        return detected
    }

    private val comedyInstruction = StylePresets.byId("comedy").instruction.take(40)

    @Test
    fun `the tone the model picked is stored and used by the same run`() {
        val dao = dao()
        val detected = run(settings(), dao)

        assertEquals(listOf("comedy"), detected)
        // The brief remembers the choice and the reason, for the translate tab.
        val stored = ContextBrief.fromStorage(runBlocking { dao.project() }?.contextBrief)
        assertEquals("comedy", stored?.tonePreset)
        assertEquals("دیالوگ‌ها پر از شوخی‌اند.", stored?.toneReason)

        // The translation batch after the reading already carries the new tone.
        val batch = requests.last { it.contains("lines_to_translate") }
        assertTrue("the comedy preset must be in the batch prompt", batch.contains(comedyInstruction))
        assertEquals("ترجمه 1", runBlocking { dao.cue(1) }?.translated)
    }

    @Test
    fun `nothing changes when the feature is switched off`() {
        val detected = run(settings(autoTone = false), dao())

        assertTrue(detected.isEmpty())
        val batch = requests.last { it.contains("lines_to_translate") }
        assertFalse(batch.contains(comedyInstruction))
    }

    @Test
    fun `the user's own prompt as the only instruction is never overruled`() {
        val detected = run(
            settings(mode = PromptMode.CUSTOM_ONLY, customPrompt = "خیلی رسمی ترجمه کن"),
            dao(),
        )
        assertTrue(detected.isEmpty())
    }

    @Test
    fun `an unknown tone name is ignored instead of guessed`() {
        val detected = run(settings(), dao(), tonePreset = "noir-thriller")
        assertTrue(detected.isEmpty())
    }

    @Test
    fun `a resumed run keeps the tone the user may have changed`() {
        // The brief is already stored: no new reading, so no new tone either.
        val detected = run(settings(), dao(ContextBrief(summary = "داستان", tonePreset = "comedy")))

        assertTrue(detected.isEmpty())
        assertFalse(requests.any { it.contains("excerpt") })
    }
}
