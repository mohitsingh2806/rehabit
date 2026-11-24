package com.mohit.rehabit.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
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

@Database(
    entities = [SessionEntity::class, SessionExerciseEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class RehabitDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
}
