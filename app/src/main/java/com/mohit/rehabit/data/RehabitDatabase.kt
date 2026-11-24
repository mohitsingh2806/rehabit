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
import kotlinx.serialization.Serializable

/** How an exercise felt, chosen at the end of a session. Stored by name: append-only. */
enum class Feedback(val label: String) {
    TOO_EASY("Too easy"),
    RIGHT("About right"),
    TOO_HARD("Too hard"),
    HURT("Hurt"),
}

/** One run of the player (a routine, a plan phase or a single exercise). */
@Serializable
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
@Serializable
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

    @Query("SELECT * FROM sessions WHERE startedAt >= :fromMillis ORDER BY startedAt")
    abstract fun observeSince(fromMillis: Long): Flow<List<SessionEntity>>

    @Query(
        """
        SELECT s.planId AS planId, s.startedAt AS startedAt, e.exerciseId AS exerciseId
        FROM session_exercises e JOIN sessions s ON e.sessionId = s.id
        WHERE e.feedback = 'HURT' AND s.startedAt >= :fromMillis
        """,
    )
    abstract fun observeHurtSince(fromMillis: Long): Flow<List<HurtRow>>
}

/** An exercise rated "Hurt" at the end of a session. */
data class HurtRow(val planId: String?, val startedAt: Long, val exerciseId: String)

// ---- Following plans (v2) ----
// Enum values are stored by name: append-only.

enum class FollowStatus { ACTIVE, PAUSED, ENDED }

/** SLOTS: one reminder per time slot. DAILY: one reminder a day at [EnrollmentEntity.dailyReminderMin]. */
enum class ReminderMode { SLOTS, DAILY, OFF }

/** Which side the "sore side" exercises are for. BOTH = do both sides. */
enum class SideChoice { LEFT, RIGHT, BOTH }

enum class TaskStatus { DONE, PARTIAL, SKIPPED, SKIPPED_PAIN }

/** A plan the user follows daily. Items are a snapshot, so content updates never change it silently. */
@Serializable
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
@Serializable
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
@Serializable
@Entity(tableName = "run_days")
data class RunDayEntity(@PrimaryKey val epochDay: Long)

@Dao
interface EnrollmentDao {
    /** All follows, including ended ones (history needs them). */
    @Query("SELECT * FROM enrollments ORDER BY id")
    fun observeAll(): Flow<List<EnrollmentEntity>>

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

    @Query("SELECT * FROM task_logs WHERE epochDay BETWEEN :from AND :to")
    fun observeRange(from: Long, to: Long): Flow<List<TaskLogEntity>>

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

// ---- Progress (v3) ----

/** A symptom score (0-10) for a plan's tracked metric on a day. One per plan per day. */
@Serializable
@Entity(
    tableName = "check_ins",
    indices = [Index(value = ["epochDay", "planId"], unique = true)],
)
data class CheckInEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val planId: String,
    val value: Int,
    val timestamp: Long,
)

/** Why a day doesn't count against adherence. Stored by name: append-only. */
enum class ExcuseReason(val label: String) { SICK("Sick"), TRAVEL("Travelling"), FLARE("Flare-up"), OTHER("Other") }

@Serializable
@Entity(tableName = "excused_days")
data class ExcusedDayEntity(@PrimaryKey val epochDay: Long, val reason: ExcuseReason)

@Dao
interface CheckInDao {
    @Query("SELECT * FROM check_ins ORDER BY epochDay")
    fun observeAll(): Flow<List<CheckInEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CheckInEntity>)

    @Query("DELETE FROM check_ins WHERE epochDay = :day AND planId = :planId")
    suspend fun delete(day: Long, planId: String)
}

@Dao
interface ExcusedDayDao {
    @Query("SELECT * FROM excused_days WHERE epochDay BETWEEN :from AND :to")
    fun observeRange(from: Long, to: Long): Flow<List<ExcusedDayEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(day: ExcusedDayEntity)

    @Query("DELETE FROM excused_days WHERE epochDay = :day")
    suspend fun delete(day: Long)
}

// ---- Your own content (v4) ----

/** A user-made exercise, stored as the same JSON as bundled content. */
@Serializable
@Entity(tableName = "user_exercises")
data class UserExerciseEntity(@PrimaryKey val id: String, val json: String, val updatedAt: Long)

/** A user-made routine or plan, stored as the same JSON as bundled content. */
@Serializable
@Entity(tableName = "user_plans")
data class UserPlanEntity(@PrimaryKey val id: String, val json: String, val updatedAt: Long)

/** The user's own photo for any exercise (bundled or theirs); the file lives in app storage. */
@Serializable
@Entity(tableName = "user_photos")
data class UserPhotoEntity(@PrimaryKey val exerciseId: String, val fileName: String)

@Dao
interface UserContentDao {
    @Query("SELECT * FROM user_exercises ORDER BY id")
    fun observeExercises(): Flow<List<UserExerciseEntity>>

    @Query("SELECT * FROM user_plans ORDER BY id")
    fun observePlans(): Flow<List<UserPlanEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExercise(e: UserExerciseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlan(p: UserPlanEntity)

    @Query("DELETE FROM user_exercises WHERE id = :id")
    suspend fun deleteExercise(id: String)

    @Query("DELETE FROM user_plans WHERE id = :id")
    suspend fun deletePlan(id: String)

    @Query("SELECT * FROM user_photos")
    fun observePhotos(): Flow<List<UserPhotoEntity>>

    @Query("SELECT * FROM user_photos WHERE exerciseId = :exerciseId")
    suspend fun getPhoto(exerciseId: String): UserPhotoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPhoto(p: UserPhotoEntity)

    @Query("DELETE FROM user_photos WHERE exerciseId = :exerciseId")
    suspend fun deletePhoto(exerciseId: String)
}

/** Whole-database read / wipe / write for backup and restore. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM sessions") suspend fun sessions(): List<SessionEntity>
    @Query("SELECT * FROM session_exercises") suspend fun sessionExercises(): List<SessionExerciseEntity>
    @Query("SELECT * FROM enrollments") suspend fun enrollments(): List<EnrollmentEntity>
    @Query("SELECT * FROM task_logs") suspend fun taskLogs(): List<TaskLogEntity>
    @Query("SELECT * FROM run_days") suspend fun runDays(): List<RunDayEntity>
    @Query("SELECT * FROM check_ins") suspend fun checkIns(): List<CheckInEntity>
    @Query("SELECT * FROM excused_days") suspend fun excusedDays(): List<ExcusedDayEntity>
    @Query("SELECT * FROM user_exercises") suspend fun userExercises(): List<UserExerciseEntity>
    @Query("SELECT * FROM user_plans") suspend fun userPlans(): List<UserPlanEntity>
    @Query("SELECT * FROM user_photos") suspend fun userPhotos(): List<UserPhotoEntity>

    @Query("DELETE FROM session_exercises") suspend fun clearSessionExercises()
    @Query("DELETE FROM sessions") suspend fun clearSessions()
    @Query("DELETE FROM enrollments") suspend fun clearEnrollments()
    @Query("DELETE FROM task_logs") suspend fun clearTaskLogs()
    @Query("DELETE FROM run_days") suspend fun clearRunDays()
    @Query("DELETE FROM check_ins") suspend fun clearCheckIns()
    @Query("DELETE FROM excused_days") suspend fun clearExcusedDays()
    @Query("DELETE FROM user_exercises") suspend fun clearUserExercises()
    @Query("DELETE FROM user_plans") suspend fun clearUserPlans()
    @Query("DELETE FROM user_photos") suspend fun clearUserPhotos()

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSessions(l: List<SessionEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSessionExercises(l: List<SessionExerciseEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertEnrollments(l: List<EnrollmentEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTaskLogs(l: List<TaskLogEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertRunDays(l: List<RunDayEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCheckIns(l: List<CheckInEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertExcusedDays(l: List<ExcusedDayEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertUserExercises(l: List<UserExerciseEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertUserPlans(l: List<UserPlanEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertUserPhotos(l: List<UserPhotoEntity>)
}

@Database(
    entities = [
        SessionEntity::class,
        SessionExerciseEntity::class,
        EnrollmentEntity::class,
        TaskLogEntity::class,
        RunDayEntity::class,
        CheckInEntity::class,
        ExcusedDayEntity::class,
        UserExerciseEntity::class,
        UserPlanEntity::class,
        UserPhotoEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class RehabitDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun enrollmentDao(): EnrollmentDao
    abstract fun taskLogDao(): TaskLogDao
    abstract fun runDayDao(): RunDayDao
    abstract fun checkInDao(): CheckInDao
    abstract fun excusedDayDao(): ExcusedDayDao
    abstract fun userContentDao(): UserContentDao
    abstract fun backupDao(): BackupDao
}

/** v4: your own exercises, plans and photos. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `user_exercises` (`id` TEXT NOT NULL, `json` TEXT NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `user_plans` (`id` TEXT NOT NULL, `json` TEXT NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `user_photos` (`exerciseId` TEXT NOT NULL, `fileName` TEXT NOT NULL, " +
                "PRIMARY KEY(`exerciseId`))",
        )
    }
}

/** v3: symptom check-ins and excused days. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `check_ins` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`epochDay` INTEGER NOT NULL, `planId` TEXT NOT NULL, `value` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_check_ins_epochDay_planId` ON `check_ins` (`epochDay`, `planId`)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `excused_days` (`epochDay` INTEGER NOT NULL, `reason` TEXT NOT NULL, " +
                "PRIMARY KEY(`epochDay`))",
        )
    }
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
