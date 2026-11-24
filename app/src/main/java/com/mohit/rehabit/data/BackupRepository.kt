package com.mohit.rehabit.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class BackupPhoto(val exerciseId: String, val fileName: String, val base64: String)

/** Everything the user created or logged, in one file. */
@Serializable
data class BackupFile(
    val app: String = APP,
    val format: Int = FORMAT,
    val exportedAt: Long,
    val settings: BackupSettings,
    val sessions: List<SessionEntity> = emptyList(),
    val sessionExercises: List<SessionExerciseEntity> = emptyList(),
    val enrollments: List<EnrollmentEntity> = emptyList(),
    val taskLogs: List<TaskLogEntity> = emptyList(),
    val runDays: List<RunDayEntity> = emptyList(),
    val checkIns: List<CheckInEntity> = emptyList(),
    val excusedDays: List<ExcusedDayEntity> = emptyList(),
    val userExercises: List<UserExerciseEntity> = emptyList(),
    val userPlans: List<UserPlanEntity> = emptyList(),
    val photos: List<BackupPhoto> = emptyList(),
) {
    companion object {
        const val APP = "rehabit"
        const val FORMAT = 1
    }
}

data class BackupSummary(val sessions: Int, val plansFollowed: Int, val ownExercises: Int, val ownPlans: Int, val photos: Int)

/** Export to / restore from a JSON file the user picks (Drive, email, Downloads…). */
class BackupRepository(private val context: Context, private val db: RehabitDatabase, private val settings: SettingsRepository) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val photosDir: File get() = File(context.filesDir, "photos").apply { mkdirs() }

    suspend fun export(uri: Uri): BackupSummary = withContext(Dispatchers.IO) {
        val d = db.backupDao()
        val photos = d.userPhotos().mapNotNull { p ->
            val f = File(photosDir, p.fileName)
            if (f.exists()) BackupPhoto(p.exerciseId, p.fileName, Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)) else null
        }
        val file = BackupFile(
            exportedAt = System.currentTimeMillis(),
            settings = settings.exportSettings(),
            sessions = d.sessions(),
            sessionExercises = d.sessionExercises(),
            enrollments = d.enrollments(),
            taskLogs = d.taskLogs(),
            runDays = d.runDays(),
            checkIns = d.checkIns(),
            excusedDays = d.excusedDays(),
            userExercises = d.userExercises(),
            userPlans = d.userPlans(),
            photos = photos,
        )
        val out = context.contentResolver.openOutputStream(uri, "wt") ?: error("Can't write to that file")
        out.bufferedWriter().use { it.write(json.encodeToString(BackupFile.serializer(), file)) }
        file.summary()
    }

    /** Reads and checks a backup without changing anything. */
    suspend fun read(uri: Uri): BackupFile = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("Can't open that file")
        val file = runCatching { json.decodeFromString(BackupFile.serializer(), text) }
            .getOrElse { error("This isn't a Rehabit backup file") }
        require(file.app == BackupFile.APP) { "This isn't a Rehabit backup file" }
        require(file.format <= BackupFile.FORMAT) { "This backup is from a newer version of Rehabit: update the app first" }
        file
    }

    /** Replaces all current data with the backup's. */
    suspend fun restore(file: BackupFile) = withContext(Dispatchers.IO) {
        val d = db.backupDao()
        db.withTransaction {
            d.clearSessionExercises(); d.clearSessions(); d.clearEnrollments(); d.clearTaskLogs(); d.clearRunDays()
            d.clearCheckIns(); d.clearExcusedDays(); d.clearUserExercises(); d.clearUserPlans(); d.clearUserPhotos()
            d.insertSessions(file.sessions); d.insertSessionExercises(file.sessionExercises)
            d.insertEnrollments(file.enrollments); d.insertTaskLogs(file.taskLogs); d.insertRunDays(file.runDays)
            d.insertCheckIns(file.checkIns); d.insertExcusedDays(file.excusedDays)
            d.insertUserExercises(file.userExercises); d.insertUserPlans(file.userPlans)
            d.insertUserPhotos(file.photos.map { UserPhotoEntity(it.exerciseId, it.fileName) })
        }
        photosDir.listFiles()?.forEach { it.delete() }
        file.photos.forEach { p -> File(photosDir, p.fileName).writeBytes(Base64.decode(p.base64, Base64.NO_WRAP)) }
        settings.importSettings(file.settings)
    }

    fun BackupFile.summary() = BackupSummary(
        sessions = sessions.size,
        plansFollowed = enrollments.count { it.status != FollowStatus.ENDED },
        ownExercises = userExercises.size,
        ownPlans = userPlans.size,
        photos = photos.size,
    )
}
