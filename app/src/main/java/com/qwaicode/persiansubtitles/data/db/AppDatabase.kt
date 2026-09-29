package com.qwaicode.persiansubtitles.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** One subtitle cue. [id] is the 1-based cue number of the source file. */
@Entity(
    tableName = "cues",
    // Indices keep the "what is still pending?" query instant even on 3000-line files.
    indices = [Index("translated"), Index("isAd"), Index("flagged")],
)
data class CueEntity(
    @PrimaryKey val id: Int,
    @ColumnInfo(name = "startMs") val startMs: Long,
    @ColumnInfo(name = "endMs") val endMs: Long,
    @ColumnInfo(name = "source") val source: String,
    @ColumnInfo(name = "translated") val translated: String? = null,
    @ColumnInfo(name = "edited") val edited: Boolean = false,
    @ColumnInfo(name = "flagged") val flagged: Boolean = false,
    @ColumnInfo(name = "isAd") val isAd: Boolean = false,
    @ColumnInfo(name = "attempts") val attempts: Int = 0,
) {
    val isTranslated: Boolean get() = !translated.isNullOrBlank()
}

/** Single-row table describing the workspace, so a run survives process death. */
@Entity(tableName = "project")
data class ProjectEntity(
    @PrimaryKey val id: Int = SINGLE_ID,
    val fileName: String,
    val format: String,
    val totalCues: Int,
    val status: String,
    val phase: String? = null,
    val lastError: String? = null,
    /**
     * The result of reading the whole subtitle once before translating, as JSON.
     * Stored with the project, not in the settings: it belongs to this one file,
     * survives process death so a resumed run keeps translating with the same
     * understanding, and disappears when the workspace is cleared.
     */
    val contextBrief: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val SINGLE_ID = 1
    }
}

object ProjectStatus {
    const val IDLE = "idle"
    const val RUNNING = "running"
    const val PAUSED = "paused"
    const val REVIEW = "review"

    /** The editorial scan at the end of a run, or started by hand. */
    const val POLISH = "polish"
    const val DONE = "done"
    const val ERROR = "error"
}

/**
 * All counters of the workspace in one row.
 *
 * This exists for speed. The counters used to be produced by observing the whole
 * cue table and counting in Kotlin, so every finished batch shipped up to 3000
 * entities into the UI layer just to turn them into six numbers — during a run that
 * is a few times per second, and it is what made switching tabs stutter. SQLite
 * counts these in one pass without materialising a single row.
 */
data class StatsRow(
    val total: Int,
    val translated: Int,
    val remaining: Int,
    val flagged: Int,
    val ads: Int,
    val edited: Int,
)

/**
 * Sentinel written into [CueEntity.attempts] for a line we deliberately gave up on
 * (Gemini refuses to translate it even alone). It keeps the resume logic and the
 * review pass from trying that same line over and over.
 */
const val ATTEMPTS_SKIPPED = 99

@Dao
interface SubtitleDao {

    // ---- project -------------------------------------------------------
    @Query("SELECT * FROM project WHERE id = 1")
    fun observeProject(): Flow<ProjectEntity?>

    @Query("SELECT * FROM project WHERE id = 1")
    suspend fun project(): ProjectEntity?

    @Upsert
    suspend fun upsertProject(project: ProjectEntity)

    @Query("UPDATE project SET status = :status, phase = :phase, lastError = :error, updatedAt = :now WHERE id = 1")
    suspend fun setStatus(status: String, phase: String?, error: String?, now: Long)

    @Query("DELETE FROM project")
    suspend fun clearProject()

    @Query("UPDATE project SET contextBrief = :json, updatedAt = :now WHERE id = 1")
    suspend fun setContextBrief(json: String?, now: Long)

    // ---- cues ----------------------------------------------------------
    @Query("SELECT * FROM cues ORDER BY id ASC")
    fun observeCues(): Flow<List<CueEntity>>

    @Query("SELECT * FROM cues ORDER BY id ASC")
    suspend fun allCues(): List<CueEntity>

    @Query("SELECT * FROM cues WHERE id = :id")
    suspend fun cue(id: Int): CueEntity?

    @Query(
        """
        SELECT * FROM cues
        WHERE (translated IS NULL OR translated = '')
          AND (isAd = 0 OR :includeAds = 1)
        ORDER BY id ASC LIMIT :limit
        """
    )
    suspend fun pendingCues(limit: Int, includeAds: Boolean): List<CueEntity>

    @Query(
        """
        SELECT * FROM cues
        WHERE id < :beforeId AND translated IS NOT NULL AND translated != ''
        ORDER BY id DESC LIMIT :limit
        """
    )
    suspend fun contextBefore(beforeId: Int, limit: Int): List<CueEntity>

    @Query("SELECT COUNT(*) FROM cues")
    suspend fun countAll(): Int

    @Query("SELECT COUNT(*) FROM cues WHERE translated IS NOT NULL AND translated != ''")
    suspend fun countTranslated(): Int

    @Query("SELECT COUNT(*) FROM cues WHERE (translated IS NULL OR translated = '') AND isAd = 0")
    suspend fun countPending(): Int

    /** Every counter the UI shows, in a single query. See [StatsRow]. */
    @Query(
        """
        SELECT
            COUNT(*) AS total,
            IFNULL(SUM(CASE WHEN translated IS NOT NULL AND translated != '' THEN 1 ELSE 0 END), 0) AS translated,
            IFNULL(SUM(CASE WHEN (translated IS NULL OR translated = '') AND isAd = 0 THEN 1 ELSE 0 END), 0) AS remaining,
            IFNULL(SUM(CASE WHEN flagged THEN 1 ELSE 0 END), 0) AS flagged,
            IFNULL(SUM(CASE WHEN isAd THEN 1 ELSE 0 END), 0) AS ads,
            IFNULL(SUM(CASE WHEN edited THEN 1 ELSE 0 END), 0) AS edited
        FROM cues
        """
    )
    fun observeStats(): Flow<StatsRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cues: List<CueEntity>)

    @Query("UPDATE cues SET translated = :text, edited = :edited, flagged = :flagged WHERE id = :id")
    suspend fun setTranslation(id: Int, text: String, edited: Boolean, flagged: Boolean)

    @Query("UPDATE cues SET attempts = :attempts WHERE id = :id")
    suspend fun setAttempts(id: Int, attempts: Int)

    @Query("UPDATE cues SET attempts = attempts + 1 WHERE id IN (:ids)")
    suspend fun bumpAttempts(ids: List<Int>)

    /**
     * Writes the result of one finished batch in a single transaction — one disk
     * commit instead of one per line, which is what keeps the UI smooth while
     * several batches are being translated in parallel.
     */
    @Transaction
    suspend fun applyBatchResult(
        translated: List<Triple<Int, String, Boolean>>,
        retryIds: List<Int>,
    ) {
        translated.forEach { (id, text, flagged) ->
            setTranslation(id = id, text = text, edited = false, flagged = flagged)
        }
        if (retryIds.isNotEmpty()) bumpAttempts(retryIds)
    }

    @Query("UPDATE cues SET translated = NULL, flagged = 0, attempts = 0 WHERE id = :id")
    suspend fun resetTranslation(id: Int)

    /**
     * Writes the result of the editorial pass. Separate from [applyBatchResult]
     * because a polished line is not a fresh translation: the review flag is cleared
     * (the problem is fixed) and the attempt counter is left alone.
     */
    @Transaction
    suspend fun applyPolish(fixed: List<Pair<Int, String>>) {
        fixed.forEach { (id, text) ->
            setTranslation(id = id, text = text, edited = false, flagged = false)
        }
    }

    /**
     * Gives up on one line for good: the source text is kept so the export stays
     * complete, the line is flagged so the user finds it under "needs review", and
     * [ATTEMPTS_SKIPPED] stops both the resume loop and the review pass from
     * asking Gemini about it again.
     */
    @Transaction
    suspend fun markSkipped(id: Int, sourceText: String) {
        setTranslation(id = id, text = sourceText, edited = false, flagged = true)
        setAttempts(id = id, attempts = ATTEMPTS_SKIPPED)
    }

    /**
     * Forgets every translation but keeps the cues, their timing and the ad flags.
     * Used when the target language changes: lines in the old language must not be
     * mixed into a file in the new one, and hand edits in the old language are no
     * longer useful either.
     */
    @Query("UPDATE cues SET translated = NULL, edited = 0, flagged = 0, attempts = 0")
    suspend fun resetAllTranslations()

    @Query("UPDATE cues SET isAd = :isAd WHERE id = :id")
    suspend fun setAd(id: Int, isAd: Boolean)

    @Query("UPDATE cues SET isAd = 0")
    suspend fun clearAdFlags()

    @Query("DELETE FROM cues")
    suspend fun clearCues()
}

@Database(entities = [CueEntity::class, ProjectEntity::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): SubtitleDao
}

/**
 * Adds the stored film brief.
 *
 * A real migration and not a destructive fallback on purpose: a user who updates
 * the app in the middle of a 1500-line translation keeps the workspace and simply
 * continues, instead of losing every translated line to a dropped table.
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE project ADD COLUMN contextBrief TEXT")
    }
}
