package com.mohit.rehabit.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mohit.rehabit.container
import com.mohit.rehabit.content.ContentState
import com.mohit.rehabit.content.Exercise
import com.mohit.rehabit.ui.common.NoInsets
import com.mohit.rehabit.ui.common.NotReviewedBadge
import com.mohit.rehabit.ui.common.defaultDoseText
import com.mohit.rehabit.ui.common.label

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(onOpenExercise: (String) -> Unit, onShowProblems: () -> Unit) {
    val context = LocalContext.current
    val state by context.container.content.state.collectAsStateWithLifecycle()

    Scaffold(contentWindowInsets = NoInsets, topBar = { TopAppBar(title = { Text("Library") }) }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val s = state) {
                ContentState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is ContentState.Ready -> ExerciseList(s, onOpenExercise, onShowProblems)
            }
        }
    }
}

@Composable
private fun ExerciseList(s: ContentState.Ready, onOpenExercise: (String) -> Unit, onShowProblems: () -> Unit) {
    // Grouped by each exercise's main (first) body area, groups in body-area order.
    val groups = s.bundle.exercises
        .groupBy { it.bodyAreas.first() }
        .toSortedMap()
        .mapValues { (_, list) -> list.sortedBy { it.name } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        if (s.problems.isNotEmpty()) {
            item(key = "problems") { ProblemsBanner(s.problems.size, onShowProblems) }
        }
        if (s.bundle.exercises.isEmpty()) {
            item(key = "empty") {
                Text(
                    "No exercises found.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        groups.forEach { (area, exercises) ->
            item(key = "h-${area.name}") {
                Text(
                    area.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            items(exercises, key = { it.id }) { ExerciseRow(it) { onOpenExercise(it.id) } }
        }
    }
}

@Composable
private fun ExerciseRow(e: Exercise, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(e.name) },
        supportingContent = { Text("${e.kind.label} · ${e.defaultDoseText()}") },
        trailingContent = if (e.reviewed) null else ({ NotReviewedBadge() }),
    )
}

@Composable
private fun ProblemsBanner(count: Int, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        ListItem(
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                headlineColor = MaterialTheme.colorScheme.onErrorContainer,
                supportingColor = MaterialTheme.colorScheme.onErrorContainer,
                leadingIconColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
            leadingContent = { Icon(Icons.Filled.Warning, contentDescription = null) },
            headlineContent = { Text(if (count == 1) "1 content problem" else "$count content problems") },
            supportingContent = { Text("Some exercises may be missing. Tap to see details.") },
        )
    }
}
