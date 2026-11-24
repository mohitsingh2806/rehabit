package com.mohit.rehabit.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** How an exercise felt, chosen at the end of a session. Stored by name: append-only. */
enum class Feedback(val label: String) {
    TOO_EASY("Too easy"),
    RIGHT("About right"),
    TOO_HARD("Too hard"),
    HURT("Hurt"),
}

/** One run of the player (a routine, a plan phase or a single exercise). */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long,
    val title: String,
    val planId: String? = null,
    val phaseId: String? = null,
    /** 0-10, asked after rehab sessions. */
    val painDuring: Int? = null,
)

/** What was actually done for each exercise, so later plan edits never rewrite history. */
@Entity(
    tableName = "session_exercises",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index("exerciseId")],
)
data class SessionExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long = 0,
    val position: Int,
    val exerciseId: String,
    /** Work steps completed (each set and side counts once). */
    val stepsDone: Int,
    val stepsPlanned: Int,
    val feedback: Feedback? = null,
)

data class SessionSummary(
    @Embedded val session: SessionEntity,
    val exercisesDone: Int,
    val exercisesTotal: Int,
)

@Dao
abstract class SessionDao {
    @Insert
    abstract suspend fun insertSession(session: SessionEntity): Long

    @Insert
    abstract suspend fun insertExercises(exercises: List<SessionExerciseEntity>)

    @Transaction
    open suspend fun save(session: SessionEntity, exercises: List<SessionExerciseEntity>): Long {
        val id = insertSession(session)
        insertExercises(exercises.map { it.copy(sessionId = id) })
        return id
    }

    @Query(
        """
        SELECT s.*,
          (SELECT COUNT(*) FROM session_exercises e WHERE e.sessionId = s.id AND e.stepsDone > 0) AS exercisesDone,
          (SELECT COUNT(*) FROM session_exercises e WHERE e.sessionId = s.id) AS exercisesTotal
        FROM sessions s
        ORDER BY s.startedAt DESC
        LIMIT :limit
        """,
    )
    abstract fun observeRecent(limit: Int): Flow<List<SessionSummary>>
}

// ---- Following plans (v2) ----
// Enum values are stored by name: append-only.

enum class FollowStatus { ACTIVE, PAUSED, ENDED }

/** SLOTS: one reminder per time slot. DAILY: one reminder a day at [EnrollmentEntity.dailyReminderMin]. */
enum class ReminderMode { SLOTS, DAILY, OFF }

/** Which side the "sore side" exercises are for. BOTH = do both sides. */
enum class SideChoice { LEFT, RIGHT, BOTH }

enum class TaskStatus { DONE, PARTIAL, SKIPPED, SKIPPED_PAIN }

/** A plan the user follows daily. Items are a snapshot, so content updates never change it silently. */
@Entity(tableName = "enrollments")
data class EnrollmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: String,
    val phaseId: String,
    /** JSON list of the phase's PlanItems when followed (or when the phase last changed). */
    val itemsJson: String,
    val startEpochDay: Long,
    val side: SideChoice? = null,
    /** Chooser answers, "questionId:optionId" joined with ";". */
    val answers: String = "",
    val reminderMode: ReminderMode,
    val dailyReminderMin: Int,
    val status: FollowStatus = FollowStatus.ACTIVE,
    val endedEpochDay: Long? = null,
    val createdAt: Long,
)

/** One tick in the daily checklist: (day, plan, item, occurrence) → status. */
@Entity(
    tableName = "task_logs",
    indices = [Index(value = ["epochDay", "enrollmentId", "itemIndex", "occurrence"], unique = true)],
)
data class TaskLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val enrollmentId: Long,
    val itemIndex: Int,
    val occurrence: Int,
    val status: TaskStatus,
    val timestamp: Long,
)

/** Days the user ran (heavy tendon work moves to the next day). */
@Entity(tableName = "run_days")
data class RunDayEntity(@PrimaryKey val epochDay: Long)

@Dao
interface EnrollmentDao {
    @Query("SELECT * FROM enrollments WHERE status != 'ENDED' ORDER BY id")
    fun observeCurrent(): Flow<List<EnrollmentEntity>>

    @Query("SELECT * FROM enrollments WHERE status != 'ENDED' ORDER BY id")
    suspend fun getCurrent(): List<EnrollmentEntity>

    @Query("SELECT * FROM enrollments WHERE id = :id")
    suspend fun get(id: Long): EnrollmentEntity?

    @Insert
    suspend fun insert(e: EnrollmentEntity): Long

    @Update
    suspend fun update(e: EnrollmentEntity)
}

@Dao
interface TaskLogDao {
    @Query("SELECT * FROM task_logs WHERE epochDay = :day")
    fun observeDay(day: Long): Flow<List<TaskLogEntity>>

    @Query("SELECT * FROM task_logs WHERE epochDay = :day")
    suspend fun getDay(day: Long): List<TaskLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(logs: List<TaskLogEntity>)

    @Query(
        "DELETE FROM task_logs WHERE epochDay = :day AND enrollmentId = :enrollmentId " +
            "AND itemIndex = :itemIndex AND occurrence = :occurrence",
    )
    suspend fun delete(day: Long, enrollmentId: Long, itemIndex: Int, occurrence: Int)
}

@Dao
interface RunDayDao {
    @Query("SELECT epochDay FROM run_days WHERE epochDay BETWEEN :from AND :to")
    fun observeRange(from: Long, to: Long): Flow<List<Long>>

    @Query("SELECT epochDay FROM run_days WHERE epochDay BETWEEN :from AND :to")
    suspend fun getRange(from: Long, to: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(day: RunDayEntity)

    @Query("DELETE FROM run_days WHERE epochDay = :day")
    suspend fun delete(day: Long)
}

@Database(
    entities = [
        SessionEntity::class,
        SessionExerciseEntity::class,
        EnrollmentEntity::class,
        TaskLogEntity::class,
        RunDayEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class RehabitDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun enrollmentDao(): EnrollmentDao
    abstract fun taskLogDao(): TaskLogDao
    abstract fun runDayDao(): RunDayDao
}

/** v2: following plans (enrollments, daily ticks, run days). Session history is untouched. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `enrollments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`planId` TEXT NOT NULL, `phaseId` TEXT NOT NULL, `itemsJson` TEXT NOT NULL, " +
                "`startEpochDay` INTEGER NOT NULL, `side` TEXT, `answers` TEXT NOT NULL, " +
                "`reminderMode` TEXT NOT NULL, `dailyReminderMin` INTEGER NOT NULL, `status` TEXT NOT NULL, " +
                "`endedEpochDay` INTEGER, `createdAt` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `task_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`epochDay` INTEGER NOT NULL, `enrollmentId` INTEGER NOT NULL, `itemIndex` INTEGER NOT NULL, " +
                "`occurrence` INTEGER NOT NULL, `status` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_task_logs_epochDay_enrollmentId_itemIndex_occurrence` " +
                "ON `task_logs` (`epochDay`, `enrollmentId`, `itemIndex`, `occurrence`)",
        )
        db.execSQL("CREATE TABLE IF NOT EXISTS `run_days` (`epochDay` INTEGER NOT NULL, PRIMARY KEY(`epochDay`))")
    }
}
