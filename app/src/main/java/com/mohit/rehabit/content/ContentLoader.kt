package com.mohit.rehabit.content

import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** A content file's path inside `assets/content` (e.g. "exercises/neck.json") and its text. */
data class ContentFile(val path: String, val text: String)

data class LoadResult(val bundle: ContentBundle, val problems: List<String>)

/**
 * Parses bundled content. Pure Kotlin, so the CI test loads exactly what the app loads.
 *
 * Layout: `content_version` (an integer), `exercises/<group>.json` (a JSON array of exercises),
 * `plans/<plan-id>.json` (one plan object per file). A file that can't be parsed is skipped and
 * reported in [LoadResult.problems]; it never crashes the app or fails silently.
 */
object ContentLoader {

    /** CI: unknown keys are errors, so a typo in a field name fails the build. */
    val strictJson = Json { ignoreUnknownKeys = false }

    /** App: tolerate fields from newer content so an older build still opens. */
    val lenientJson = Json { ignoreUnknownKeys = true }

    private val exerciseList = ListSerializer(Exercise.serializer())

    fun load(versionText: String?, files: List<ContentFile>, json: Json): LoadResult {
        val problems = mutableListOf<String>()
        val version = versionText?.trim()?.toIntOrNull() ?: run {
            problems += "content_version is missing or not a number"
            0
        }
        val exercises = mutableListOf<Exercise>()
        val plans = mutableListOf<Plan>()
        for (file in files.sortedBy { it.path }) {
            try {
                when {
                    file.path.startsWith("exercises/") && file.path.endsWith(".json") ->
                        exercises += json.decodeFromString(exerciseList, file.text)
                    file.path.startsWith("plans/") && file.path.endsWith(".json") ->
                        plans += json.decodeFromString(Plan.serializer(), file.text)
                    else -> Unit // images, .gitkeep, etc.
                }
            } catch (e: SerializationException) {
                problems += "${file.path}: ${e.message?.lineSequence()?.firstOrNull() ?: "can't be read"}"
            } catch (e: IllegalArgumentException) {
                problems += "${file.path}: ${e.message?.lineSequence()?.firstOrNull() ?: "can't be read"}"
            }
        }
        return LoadResult(ContentBundle(version, exercises, plans), problems)
    }
}
