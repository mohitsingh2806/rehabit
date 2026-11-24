package com.mohit.rehabit.content

import android.content.res.AssetManager
import com.mohit.rehabit.data.RehabitDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

sealed interface ContentState {
    data object Loading : ContentState

    /** [problems] are shown in Settings (and flagged in the Library) so nothing fails silently. */
    data class Ready(val bundle: ContentBundle, val problems: List<String>) : ContentState
}

/**
 * Bundled exercises and plans (loaded once from assets, validated) merged with the user's own
 * content from the database (live). User ids start with [USER_ID_PREFIX], so they never clash.
 */
class ContentRepository(private val assets: AssetManager, db: RehabitDatabase, scope: CoroutineScope) {

    private val bundled = MutableStateFlow<ContentState.Ready?>(null)

    val state: StateFlow<ContentState> = combine(
        bundled.filterNotNull(),
        db.userContentDao().observeExercises(),
        db.userContentDao().observePlans(),
    ) { base, userExercises, userPlans ->
        val problems = base.problems.toMutableList()
        val exercises = userExercises.mapNotNull { e ->
            runCatching { ContentLoader.lenientJson.decodeFromString(Exercise.serializer(), e.json) }
                .onFailure { problems += "Your exercise '${e.id}' can't be read" }.getOrNull()
        }
        val plans = userPlans.mapNotNull { p ->
            runCatching { ContentLoader.lenientJson.decodeFromString(Plan.serializer(), p.json) }
                .onFailure { problems += "Your plan '${p.id}' can't be read" }.getOrNull()
        }
        val b = base.bundle
        ContentState.Ready(ContentBundle(b.version, b.exercises + exercises, b.plans + plans), problems)
    }.stateIn(scope, SharingStarted.Eagerly, ContentState.Loading)

    init {
        scope.launch { bundled.value = load() }
    }

    private fun load(): ContentState.Ready = try {
        val files = DIRS.flatMap { dir ->
            (assets.list("$ROOT/$dir") ?: emptyArray())
                .filter { it.endsWith(".json") }
                .map { name -> ContentFile("$dir/$name", read("$ROOT/$dir/$name")) }
        }
        val version = runCatching { read("$ROOT/content_version") }.getOrNull()
        val result = ContentLoader.load(version, files, ContentLoader.lenientJson)
        val invalid = ContentValidator.validate(result.bundle, ::assetExists)
        ContentState.Ready(result.bundle, result.problems + invalid)
    } catch (e: IOException) {
        ContentState.Ready(ContentBundle.EMPTY, listOf("Content couldn't be loaded: ${e.message}"))
    }

    private fun read(path: String): String = assets.open(path).bufferedReader().use { it.readText() }

    private fun assetExists(path: String): Boolean =
        runCatching { assets.open("$ROOT/$path").close() }.isSuccess

    private companion object {
        const val ROOT = "content"
        val DIRS = listOf("exercises", "plans")
    }
}
