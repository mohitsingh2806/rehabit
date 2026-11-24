package com.mohit.rehabit.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.mohit.rehabit.content.ContentLoader
import com.mohit.rehabit.content.Exercise
import com.mohit.rehabit.content.Plan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

/** The user's own exercises, routines/plans and exercise photos. */
class UserContentRepository(private val context: Context, private val db: RehabitDatabase) {

    private val dao = db.userContentDao()
    val photosDir: File get() = File(context.filesDir, "photos").apply { mkdirs() }

    suspend fun saveExercise(e: Exercise) {
        dao.upsertExercise(UserExerciseEntity(e.id, ContentLoader.lenientJson.encodeToString(Exercise.serializer(), e), System.currentTimeMillis()))
    }

    suspend fun deleteExercise(id: String) {
        dao.deleteExercise(id)
        removePhoto(id)
    }

    suspend fun savePlan(p: Plan) {
        dao.upsertPlan(UserPlanEntity(p.id, ContentLoader.lenientJson.encodeToString(Plan.serializer(), p), System.currentTimeMillis()))
    }

    /** Deleting a plan you follow stops following it; history is kept. */
    suspend fun deletePlan(id: String, agenda: AgendaRepository) {
        db.enrollmentDao().getCurrent().filter { it.planId == id }.forEach { agenda.stop(it.id) }
        dao.deletePlan(id)
    }

    /** exerciseId → photo file, for every exercise that has one. */
    fun photos(): Flow<Map<String, File>> = dao.observePhotos().map { list ->
        list.associate { it.exerciseId to File(photosDir, it.fileName) }.filterValues { it.exists() }
    }

    /**
     * Copies a picked photo into app storage: upright (EXIF rotation applied), at most 1600 px on
     * the long side, JPEG. Returns false if the image can't be read.
     */
    suspend fun setPhoto(exerciseId: String, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val readable = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds); true } ?: false
        if (!readable) return@withContext false
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext false
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return@withContext false
        val rotation = runCatching {
            resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees() } ?: 0
        }.getOrDefault(0)
        val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(decoded.width, decoded.height))
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(rotation.toFloat())
        }
        val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

        val old = dao.getPhoto(exerciseId)
        val name = "$exerciseId-${System.currentTimeMillis()}.jpg"
        File(photosDir, name).outputStream().use { upright.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        dao.upsertPhoto(UserPhotoEntity(exerciseId, name))
        old?.let { File(photosDir, it.fileName).delete() }
        true
    }

    suspend fun removePhoto(exerciseId: String) {
        dao.getPhoto(exerciseId)?.let { File(photosDir, it.fileName).delete() }
        dao.deletePhoto(exerciseId)
    }

    private fun ExifInterface.rotationDegrees(): Int = when (getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }

    private companion object {
        const val MAX_SIDE = 1600
    }
}
