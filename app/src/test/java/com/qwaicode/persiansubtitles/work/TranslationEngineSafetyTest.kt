package com.qwaicode.persiansubtitles.work

import com.qwaicode.persiansubtitles.data.db.ATTEMPTS_SKIPPED
import com.qwaicode.persiansubtitles.data.db.CueEntity
import com.qwaicode.persiansubtitles.data.db.ProjectEntity
import com.qwaicode.persiansubtitles.data.db.StatsRow
import com.qwaicode.persiansubtitles.data.db.SubtitleDao
import com.qwaicode.persiansubtitles.data.prefs.AppSettings
import com.qwaicode.persiansubtitles.network.GeminiClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
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
 * What happens when Gemini refuses a line.
 *
 * Before the fix a refusal killed the whole run: the batch stayed untranslated, so
 * pressing "continue" sent exactly the same lines and got exactly the same refusal,
 * forever. These tests pin down the new behaviour — narrow the refusal down to the
 * single responsible line, skip that one, translate everything else, and never ask
 * about the skipped line again.
 */
class TranslationEngineSafetyTest {

    /** The line the fake Gemini refuses, whatever else is in the batch. */
    private val forbidden = "REFUSED"

    private val payloads = mutableListOf<String>()

    private fun fakeGemini(): GeminiClient {
        val http = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val request = chain.request()
                val buffer = Buffer()
                request.body?.writeTo(buffer)
                val body = buffer.readUtf8()
                payloads += body

                val lines = body.substringAfter("lines_to_translate")
                val refuses = lines.contains(forbidden)
                val json = if (refuses) {
                    """{"candidates":[{"finishReason":"PROHIBITED_CONTENT","content":{"parts":[]}}]}"""
                } else {
                    val ids = ID_IN_PAYLOAD.findAll(lines).map { it.groupValues[1] }.toList()
                    val items = ids.joinToString(",") { """{\"id\":$it,\"fa\":\"ترجمه $it\"}""" }
                    """{"candidates":[{"content":{"parts":[{"text":"[$items]"}]},"finishReason":"STOP"}]}"""
                }
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

    private fun engineOver(dao: SubtitleDao): TranslationEngine = TranslationEngine(
        dao = dao,
        settings = flowOf(
            AppSettings(
                apiKey = "test-key",
                batchSize = 4,
                concurrency = 1,
                autoReview = true,
                readWholeSubtitle = false,
            )
        ),
        api = fakeGemini(),
    )

    private fun cues(): List<CueEntity> = listOf(
        CueEntity(id = 1, startMs = 0, endMs = 900, source = "Good morning."),
        CueEntity(id = 2, startMs = 1000, endMs = 1900, source = "How are you?"),
        CueEntity(id = 3, startMs = 2000, endMs = 2900, source = "A $forbidden line of dialogue."),
        CueEntity(id = 4, startMs = 3000, endMs = 3900, source = "See you tomorrow."),
    )

    @Test
    fun `a refused line is skipped and everything around it is translated`() = runBlocking {
        val dao = FakeSubtitleDao(cues())

        val outcome = engineOver(dao).run(onProgress = { _, _, _ -> }, isStopped = { false })

        assertEquals(TranslationEngine.Outcome.Completed, outcome)

        // The three harmless lines came back translated.
        listOf(1, 2, 4).forEach { id ->
            assertEquals("ترجمه $id", dao.cue(id)?.translated)
        }

        // The refused line keeps its source text, so the exported file has no hole,
        // and it is flagged so the user finds it under "needs review".
        val skipped = dao.cue(3)
        assertNotNull(skipped)
        assertEquals("A $forbidden line of dialogue.", skipped!!.translated)
        assertTrue("the skipped line must be flagged for review", skipped.flagged)
        assertEquals(ATTEMPTS_SKIPPED, skipped.attempts)

        // Nothing is left pending, which is what used to keep the run stuck.
        assertEquals(0, dao.countPending())
    }

    @Test
    fun `the refusal is narrowed down instead of failing the batch`() = runBlocking {
        val dao = FakeSubtitleDao(cues())

        engineOver(dao).run(onProgress = { _, _, _ -> }, isStopped = { false })

        // 4 lines in one batch: the full batch, then the two halves, then the two
        // single lines of the refused half — five requests, no endless retrying.
        assertEquals(5, payloads.size)

        val lastRefused = payloads.last { it.substringAfter("lines_to_translate").contains(forbidden) }
        val idsInLastRefusal = ID_IN_PAYLOAD
            .findAll(lastRefused.substringAfter("lines_to_translate"))
            .map { it.groupValues[1] }
            .toList()
        assertEquals("the refusal must be narrowed down to one line", listOf("3"), idsInLastRefusal)
    }

    @Test
    fun `continuing the run does not ask about the skipped line again`() = runBlocking {
        val dao = FakeSubtitleDao(cues())
        engineOver(dao).run(onProgress = { _, _, _ -> }, isStopped = { false })

        // This is the bug the user hit: pressing "continue" repeated the same
        // refusal. A second run must not send a single request any more.
        payloads.clear()
        val second = engineOver(dao).run(onProgress = { _, _, _ -> }, isStopped = { false })

        assertEquals(TranslationEngine.Outcome.Completed, second)
        assertEquals("nothing left to ask Gemini about", 0, payloads.size)
    }

    @Test
    fun `the editorial pass repairs a line the translation left wrong`() = runBlocking {
        // A finished file with two defects the user reported: Arabic letter shapes
        // (fixable on the device) and an English word left standing (needs the model).
        val dao = FakeSubtitleDao(
            listOf(
                CueEntity(id = 1, startMs = 0, endMs = 900, source = "The book is here.", translated = "كتاب اينجاست."),
                CueEntity(id = 2, startMs = 1000, endMs = 1900, source = "Never forget.", translated = "هرگز forget"),
                CueEntity(id = 3, startMs = 2000, endMs = 2900, source = "Good morning.", translated = "صبح بخیر."),
                // A line the user corrected by hand must never be touched again.
                CueEntity(id = 4, startMs = 3000, endMs = 3900, source = "Hello.", translated = "سلام كن.", edited = true),
            )
        )

        engineOver(dao).run(onProgress = { _, _, _ -> }, isStopped = { false })

        // Stage 1, no request needed: the Arabic kaf and yeh are now Persian.
        assertEquals("کتاب اینجاست.", dao.cue(1)?.translated)

        // Stage 2: the leftover English word was sent back and replaced.
        assertEquals("ترجمه 2", dao.cue(2)?.translated)

        // A correct line is not sent anywhere and stays exactly as it was.
        assertEquals("صبح بخیر.", dao.cue(3)?.translated)

        // The hand-edited line keeps the user's wording, defects and all.
        assertEquals("سلام كن.", dao.cue(4)?.translated)
        assertTrue(dao.cue(4)!!.edited)
    }

    @Test
    fun `a clean file costs no polish request at all`() = runBlocking {
        val dao = FakeSubtitleDao(
            listOf(
                CueEntity(id = 1, startMs = 0, endMs = 900, source = "Good morning.", translated = "صبح بخیر."),
                CueEntity(id = 2, startMs = 1000, endMs = 1900, source = "See you.", translated = "بعداً می‌بینمت."),
            )
        )

        payloads.clear()
        engineOver(dao).run(onProgress = { _, _, _ -> }, isStopped = { false })

        assertEquals("nothing to translate, review or polish", 0, payloads.size)
    }
}

/**
 * The user payload travels as a JSON string inside the request, so its own quotes
 * arrive escaped: \"id\":7. The pattern therefore tolerates the backslashes.
 */
private val ID_IN_PAYLOAD = Regex("""\\?"id\\?"\s*:\s*(\d+)""")

/** In-memory stand-in for the Room DAO. Only the queries the engine uses matter. Shared with [AutoToneEngineTest]. */
internal class FakeSubtitleDao(initial: List<CueEntity>) : SubtitleDao {

    private val cues = LinkedHashMap<Int, CueEntity>().apply {
        initial.forEach { put(it.id, it) }
    }
    private var project: ProjectEntity? = null

    override fun observeProject(): Flow<ProjectEntity?> = flowOf(project)
    override suspend fun project(): ProjectEntity? = project
    override suspend fun upsertProject(project: ProjectEntity) { this.project = project }

    override suspend fun setStatus(status: String, phase: String?, error: String?, now: Long) {
        project = project?.copy(status = status, phase = phase, lastError = error, updatedAt = now)
    }

    override suspend fun clearProject() { project = null }

    override suspend fun setContextBrief(json: String?, now: Long) {
        project = project?.copy(contextBrief = json, updatedAt = now)
    }

    override fun observeCues(): Flow<List<CueEntity>> = flowOf(cues.values.sortedBy { it.id })

    override fun observeStats(): Flow<StatsRow> = flowOf(
        StatsRow(
            total = cues.size,
            translated = cues.values.count { !it.translated.isNullOrEmpty() },
            remaining = cues.values.count { it.translated.isNullOrEmpty() && !it.isAd },
            flagged = cues.values.count { it.flagged },
            ads = cues.values.count { it.isAd },
            edited = cues.values.count { it.edited },
        )
    )
    override suspend fun allCues(): List<CueEntity> = cues.values.sortedBy { it.id }
    override suspend fun cue(id: Int): CueEntity? = cues[id]

    override suspend fun pendingCues(limit: Int, includeAds: Boolean): List<CueEntity> =
        cues.values
            .sortedBy { it.id }
            .filter { it.translated.isNullOrEmpty() && (includeAds || !it.isAd) }
            .take(limit)

    override suspend fun contextBefore(beforeId: Int, limit: Int): List<CueEntity> =
        cues.values
            .sortedByDescending { it.id }
            .filter { it.id < beforeId && !it.translated.isNullOrEmpty() }
            .take(limit)

    override suspend fun countAll(): Int = cues.size
    override suspend fun countTranslated(): Int = cues.values.count { !it.translated.isNullOrEmpty() }
    override suspend fun countPending(): Int =
        cues.values.count { it.translated.isNullOrEmpty() && !it.isAd }

    override suspend fun insertAll(cues: List<CueEntity>) {
        cues.forEach { this.cues[it.id] = it }
    }

    override suspend fun setTranslation(id: Int, text: String, edited: Boolean, flagged: Boolean) {
        cues[id]?.let { cues[id] = it.copy(translated = text, edited = edited, flagged = flagged) }
    }

    override suspend fun setAttempts(id: Int, attempts: Int) {
        cues[id]?.let { cues[id] = it.copy(attempts = attempts) }
    }

    override suspend fun bumpAttempts(ids: List<Int>) {
        ids.forEach { id -> cues[id]?.let { cues[id] = it.copy(attempts = it.attempts + 1) } }
    }

    override suspend fun resetTranslation(id: Int) {
        cues[id]?.let { cues[id] = it.copy(translated = null, flagged = false, attempts = 0) }
    }

    override suspend fun setAd(id: Int, isAd: Boolean) {
        cues[id]?.let { cues[id] = it.copy(isAd = isAd) }
    }

    override suspend fun clearAdFlags() {
        cues.keys.toList().forEach { id -> cues[id]?.let { cues[id] = it.copy(isAd = false) } }
    }

    override suspend fun resetAllTranslations() {
        cues.keys.toList().forEach { id ->
            cues[id]?.let { cues[id] = it.copy(translated = null, edited = false, flagged = false, attempts = 0) }
        }
    }

    override suspend fun clearCues() = cues.clear()
}
