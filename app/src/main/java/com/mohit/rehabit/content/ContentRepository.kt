package com.mohit.rehabit.content

import android.content.res.AssetManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

sealed interface ContentState {
    data object Loading : ContentState

    /** [problems] are shown in Settings (and flagged in the Library) so nothing fails silently. */
    data class Ready(val bundle: ContentBundle, val problems: List<String>) : ContentState
}

/** Loads the bundled exercises and plans from assets once, off the main thread. */
class ContentRepository(private val assets: AssetManager, scope: CoroutineScope) {

    private val _state = MutableStateFlow<ContentState>(ContentState.Loading)
    val state: StateFlow<ContentState> = _state.asStateFlow()

    init {
        scope.launch { _state.value = load() }
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
