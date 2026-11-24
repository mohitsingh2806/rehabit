package com.mohit.rehabit.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mohit.rehabit.container
import com.mohit.rehabit.content.BodyArea
import com.mohit.rehabit.content.ContentState
import com.mohit.rehabit.content.Exercise
import com.mohit.rehabit.content.Plan
import com.mohit.rehabit.content.PlanKind
import com.mohit.rehabit.content.isUser
import com.mohit.rehabit.ui.common.NoInsets
import com.mohit.rehabit.ui.common.NotReviewedBadge
import com.mohit.rehabit.ui.common.YoursBadge
import com.mohit.rehabit.ui.common.defaultDoseText
import com.mohit.rehabit.ui.common.label

private enum class LibraryTab(val title: String) { PLANS("Plans"), ROUTINES("Routines"), EXERCISES("Exercises") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenExercise: (String) -> Unit,
    onOpenPlan: (String) -> Unit,
    onShowProblems: () -> Unit,
    onNewExercise: () -> Unit,
    onNewPlan: () -> Unit,
    onCombine: () -> Unit,
) {
    val context = LocalContext.current
    val state by context.container.content.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(LibraryTab.PLANS) }
    var query by rememberSaveable { mutableStateOf("") }
    var area by rememberSaveable { mutableStateOf<BodyArea?>(null) }
    var noEquipment by rememberSaveable { mutableStateOf(false) }
    var deskFriendly by rememberSaveable { mutableStateOf(false) }
    var myEquipment by rememberSaveable { mutableStateOf(false) }
    var showMap by rememberSaveable { mutableStateOf(false) }
    val prefsFlow = remember { context.container.settings.flow }
    val prefs by prefsFlow.collectAsStateWithLifecycle(initialValue = null)
    val owned = prefs?.equipment.orEmpty()

    var createMenu by remember { mutableStateOf(false) }
    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = {
            TopAppBar(
                title = { Text("Library") },
                actions = {
                    Box {
                        IconButton(onClick = { createMenu = true }) { Icon(Icons.Filled.Add, contentDescription = "Create") }
                        DropdownMenu(expanded = createMenu, onDismissRequest = { createMenu = false }) {
                            DropdownMenuItem(text = { Text("New exercise") }, onClick = { createMenu = false; onNewExercise() })
                            DropdownMenuItem(text = { Text("New routine or plan") }, onClick = { createMenu = false; onNewPlan() })
                            DropdownMenuItem(text = { Text("Combine plans") }, onClick = { createMenu = false; onCombine() })
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab.ordinal) {
                LibraryTab.entries.forEach { t ->
                    Tab(selected = tab == t, onClick = { tab = t }, text = { Text(t.title) })
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search ${tab.title.lowercase()}") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, contentDescription = "Clear search") }
                    }
                },
                singleLine = true,
            )
            Box(Modifier.fillMaxSize()) {
                when (val s = state) {
                    ContentState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    is ContentState.Ready -> {
                        val q = query.trim()
                        when (tab) {
                            LibraryTab.PLANS, LibraryTab.ROUTINES -> {
                                val kind = if (tab == LibraryTab.PLANS) PlanKind.FOCUSED else PlanKind.ON_DEMAND
                                val plans = s.bundle.plans
                                    .filter { it.kind == kind && it.matches(q) }
                                    .sortedBy { it.name }
                                PlanList(plans, s.problems.size, onOpenPlan, onShowProblems, emptyText = when {
                                    q.isNotEmpty() -> "Nothing matches \"$q\"."
                                    kind == PlanKind.FOCUSED -> "No plans yet."
                                    else -> "No routines yet."
                                })
                            }
                            LibraryTab.EXERCISES -> {
                                val areas = s.bundle.exercises.flatMap { it.bodyAreas }.distinct().sorted()
                                val selectedArea = area
                                val exercises = s.bundle.exercises.filter { e ->
                                    e.matches(q) &&
                                        (selectedArea == null || selectedArea in e.bodyAreas) &&
                                        (!noEquipment || e.equipment.all { it.everyday }) &&
                                        (!deskFriendly || e.deskFriendly) &&
                                        (!myEquipment || e.equipment.all { it.everyday || it in owned })
                                }
                                Column {
                                    if (showMap) BodyMap(selectedArea, areas.toSet()) { area = it }
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        item {
                                            FilterChip(
                                                selected = showMap,
                                                onClick = { showMap = !showMap },
                                                label = { Text("Body map") },
                                            )
                                        }
                                        item {
                                            FilterChip(
                                                selected = myEquipment,
                                                onClick = { myEquipment = !myEquipment },
                                                label = { Text("My equipment") },
                                            )
                                        }
                                        item {
                                            FilterChip(
                                                selected = noEquipment,
                                                onClick = { noEquipment = !noEquipment },
                                                label = { Text("No equipment") },
                                            )
                                        }
                                        item {
                                            FilterChip(
                                                selected = deskFriendly,
                                                onClick = { deskFriendly = !deskFriendly },
                                                label = { Text("Desk-friendly") },
                                            )
                                        }
                                        items(areas) { a ->
                                            FilterChip(
                                                selected = area == a,
                                                onClick = { area = if (area == a) null else a },
                                                label = { Text(a.label) },
                                            )
                                        }
                                    }
                                    ExerciseList(exercises, s.problems.size, onOpenExercise, onShowProblems, groupBy = selectedArea)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Plan.matches(q: String): Boolean =
    q.isEmpty() || name.contains(q, ignoreCase = true) || goal.contains(q, ignoreCase = true)

private fun Exercise.matches(q: String): Boolean =
    q.isEmpty() || name.contains(q, ignoreCase = true) || aliases.any { it.contains(q, ignoreCase = true) }

@Composable
private fun PlanList(
    plans: List<Plan>,
    problemCount: Int,
    onOpenPlan: (String) -> Unit,
    onShowProblems: () -> Unit,
    emptyText: String,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        if (problemCount > 0) item(key = "problems") { ProblemsBanner(problemCount, onShowProblems) }
        if (plans.isEmpty()) item(key = "empty") { EmptyText(emptyText) }
        items(plans, key = { it.id }) { plan ->
            ListItem(
                modifier = Modifier.clickable { onOpenPlan(plan.id) },
                overlineContent = { Text(planMeta(plan)) },
                headlineContent = { Text(plan.name) },
                supportingContent = { Text(plan.goal, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                trailingContent = when {
                    plan.isUser -> ({ YoursBadge() })
                    !plan.reviewed -> ({ NotReviewedBadge() })
                    else -> null
                },
            )
        }
    }
}

fun planMeta(plan: Plan): String {
    val kind = if (plan.kind == PlanKind.FOCUSED) "Plan" else "Routine"
    val phases = if (plan.phases.size > 1) " · ${plan.phases.size} phases" else ""
    return "$kind$phases · about ${plan.estMinutes} min"
}

@Composable
private fun ExerciseList(
    exercises: List<Exercise>,
    problemCount: Int,
    onOpenExercise: (String) -> Unit,
    onShowProblems: () -> Unit,
    groupBy: BodyArea?,
) {
    // Grouped by each exercise's main (first) body area, or the selected area filter.
    val groups = exercises
        .groupBy { groupBy ?: it.bodyAreas.first() }
        .toSortedMap()
        .mapValues { (_, list) -> list.sortedBy { it.name } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        if (problemCount > 0) item(key = "problems") { ProblemsBanner(problemCount, onShowProblems) }
        if (exercises.isEmpty()) item(key = "empty") { EmptyText("No exercises match these filters.") }
        groups.forEach { (area, list) ->
            item(key = "h-${area.name}") {
                Text(
                    area.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            items(list, key = { "${area.name}-${it.id}" }) { e ->
                ListItem(
                    modifier = Modifier.clickable { onOpenExercise(e.id) },
                    headlineContent = { Text(e.name) },
                    supportingContent = { Text("${e.kind.label} · ${e.defaultDoseText()}") },
                    trailingContent = when {
                        e.isUser -> ({ YoursBadge() })
                        !e.reviewed -> ({ NotReviewedBadge() })
                        else -> null
                    },
                )
            }
        }
    }
}

@Composable
private fun EmptyText(text: String) {
    Text(text, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            supportingContent = { Text("Some content may be missing. Tap to see details.") },
        )
    }
}
