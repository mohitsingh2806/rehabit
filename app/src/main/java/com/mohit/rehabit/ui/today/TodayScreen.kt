package com.mohit.rehabit.ui.today

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohit.rehabit.container
import com.mohit.rehabit.content.ContentState
import com.mohit.rehabit.content.Equipment
import com.mohit.rehabit.content.Exercise
import com.mohit.rehabit.domain.Suggestions
import com.mohit.rehabit.ui.common.label
import com.mohit.rehabit.content.Plan
import com.mohit.rehabit.content.PlanKind
import com.mohit.rehabit.data.AgendaRepository
import com.mohit.rehabit.data.CheckInPrompt
import com.mohit.rehabit.data.ExcuseReason
import com.mohit.rehabit.data.HistoryRepository
import com.mohit.rehabit.data.ProgressState
import com.mohit.rehabit.domain.InsightKind
import com.mohit.rehabit.ui.progress.InsightCard
import com.mohit.rehabit.data.ReminderMode
import com.mohit.rehabit.data.TaskStatus
import com.mohit.rehabit.data.TodayState
import com.mohit.rehabit.domain.SlotGroup
import com.mohit.rehabit.domain.Task
import com.mohit.rehabit.domain.doseText
import com.mohit.rehabit.domain.label
import com.mohit.rehabit.domain.minutesFor
import com.mohit.rehabit.ui.common.NoInsets
import com.mohit.rehabit.ui.common.minuteText
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt
import java.time.format.DateTimeFormatter

class TodayViewModel(private val repo: AgendaRepository, private val history: HistoryRepository) : ViewModel() {
    val state: StateFlow<TodayState?> =
        repo.todayFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Check-in prompts, pain-rule warnings and today's excuse (from the progress data). */
    val progress: StateFlow<ProgressState?> =
        history.progressFlow(YearMonth.now()).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun saveCheckIns(values: Map<String, Int>) {
        val day = day()
        viewModelScope.launch { history.saveCheckIns(day, values) }
    }

    fun setExcused(reason: ExcuseReason?) {
        val day = day()
        viewModelScope.launch { history.setExcused(day, reason) }
    }

    /** Use [exerciseId] instead of the task's exercise in every plan it came from, from today. */
    fun swap(task: Task, to: Exercise) {
        viewModelScope.launch {
            task.refs.distinctBy { it.enrollmentId to it.itemIndex }.forEach { ref ->
                repo.replaceItem(ref.enrollmentId, ref.itemIndex) { item ->
                    item.copy(exerciseId = to.id, dose = Suggestions.doseForSwap(item.dose, to))
                }
            }
        }
    }

    private fun day(): Long = state.value?.agenda?.day ?: LocalDate.now().toEpochDay()

    fun setStatus(tasks: List<Task>, status: TaskStatus?) {
        val day = day()
        viewModelScope.launch { repo.setStatus(day, tasks, status) }
    }

    fun setRunDay(ran: Boolean) {
        val day = day()
        viewModelScope.launch { repo.setRunDay(day, ran) }
    }

    fun setCoreOnly(on: Boolean) {
        val day = day()
        viewModelScope.launch { repo.setCoreOnly(day, on) }
    }
}

private fun statusLabel(s: TaskStatus): String = when (s) {
    TaskStatus.DONE -> "Done"
    TaskStatus.PARTIAL -> "Partly done"
    TaskStatus.SKIPPED -> "Skipped"
    TaskStatus.SKIPPED_PAIN -> "Skipped: pain"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    onStartSlot: (day: Long, slotId: String) -> Unit,
    onTryExercise: (String) -> Unit,
    onOpenPlan: (String) -> Unit,
    onStartRoutine: (planId: String, phaseId: String) -> Unit,
    onBrowseLibrary: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProgress: () -> Unit,
) {
    val context = LocalContext.current
    val vm: TodayViewModel = viewModel { TodayViewModel(context.container.agenda, context.container.history) }
    val state by vm.state.collectAsStateWithLifecycle()
    val content by context.container.content.state.collectAsStateWithLifecycle()
    val allPlans = (content as? ContentState.Ready)?.bundle?.plans.orEmpty()
    val routines = allPlans.filter { it.kind == PlanKind.ON_DEMAND }.sortedBy { it.name }

    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Rehabit", fontWeight = FontWeight.Bold)
                        Text(
                            LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val s = state
            when {
                s == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                s.plans.isEmpty() -> NoPlans(allPlans, routines, onOpenPlan, onStartRoutine, onBrowseLibrary)
                else -> Checklist(s, vm, routines, onStartSlot, onTryExercise, onOpenPlan, onStartRoutine, onOpenSettings, onOpenProgress)
            }
        }
    }
}

@Composable
private fun Checklist(
    s: TodayState,
    vm: TodayViewModel,
    routines: List<Plan>,
    onStartSlot: (Long, String) -> Unit,
    onTryExercise: (String) -> Unit,
    onOpenPlan: (String) -> Unit,
    onStartRoutine: (String, String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProgress: () -> Unit,
) {
    val context = LocalContext.current
    val exercises = (context.container.content.state.collectAsStateWithLifecycle().value as? ContentState.Ready)?.bundle?.exercisesById.orEmpty()
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumeTick++
        onPauseOrDispose { }
    }
    val notificationsOff = remember(resumeTick) { !NotificationManagerCompat.from(context).areNotificationsEnabled() }
    val progress by vm.progress.collectAsStateWithLifecycle()
    val warnings = progress?.insights.orEmpty().filter { it.kind == InsightKind.WARNING }
    val prompts = progress?.checkInPrompts.orEmpty()
    val excusedToday = progress?.excused?.get(s.agenda.day)
    var excuseDialog by remember { mutableStateOf(false) }
    val wantsReminders = s.plans.any { it.active && it.reminderMode != ReminderMode.OFF }
    val agenda = s.agenda

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (notificationsOff && wantsReminders) {
            item(key = "notif") {
                Card(
                    onClick = onOpenSettings,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text("Notifications are off, so reminders can't show. Tap to fix.")
                    }
                }
            }
        }

        items(warnings, key = { "w-${it.title}" }) { InsightCard(it) }

        item(key = "hero") { Hero(s, vm, onStartSlot) }

        val suggestionCount = progress?.suggestions?.size ?: 0
        if (suggestionCount > 0) {
            item(key = "suggestions") {
                Card(onClick = onOpenProgress, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (suggestionCount == 1) "1 suggestion to adjust your plans" else "$suggestionCount suggestions to adjust your plans",
                            modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }
        }

        if (prompts.isNotEmpty()) {
            item(key = "checkin") { CheckInCard(prompts) { vm.saveCheckIns(it) } }
        }

        item(key = "toggles") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = agenda.runDay,
                    onClick = { vm.setRunDay(!agenda.runDay) },
                    label = { Text(if (agenda.runDay) "Ran today ✓" else "I ran today") },
                )
                FilterChip(
                    selected = excusedToday != null,
                    onClick = { if (excusedToday != null) vm.setExcused(null) else excuseDialog = true },
                    label = { Text(excusedToday?.let { "Excused: ${it.label}" } ?: "Rest day") },
                )
                FilterChip(
                    selected = agenda.coreOnly,
                    onClick = { vm.setCoreOnly(!agenda.coreOnly) },
                    label = {
                        Text(if (agenda.coreOnly && agenda.hiddenOptional > 0) "Core only (${agenda.hiddenOptional} hidden)" else "Core only")
                    },
                )
            }
        }

        if (excuseDialog) {
            item(key = "excuse-dialog") {
                AlertDialog(
                    onDismissRequest = { excuseDialog = false },
                    title = { Text("Take today off?") },
                    text = { Text("Today won't count against your weekly progress. Your checklist stays, in case you feel up to it.") },
                    confirmButton = {
                        Column {
                            ExcuseReason.entries.forEach { r ->
                                TextButton(onClick = { excuseDialog = false; vm.setExcused(r) }) { Text(r.label) }
                            }
                        }
                    },
                    dismissButton = { TextButton(onClick = { excuseDialog = false }) { Text("Cancel") } },
                )
            }
        }

        if (agenda.groups.isEmpty()) {
            item(key = "rest") {
                Text(
                    "Nothing scheduled today. A walk is always a good idea.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(agenda.groups, key = { it.key.id }) { g ->
            GroupCard(g, vm, exercises, s.prefs.equipment, onStart = { onStartSlot(agenda.day, g.key.id) }, onTryExercise = onTryExercise)
        }

        item(key = "plans-h") {
            Text("Your plans", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        items(s.plans, key = { "plan-${it.id}" }) { p ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                val phase = p.plan.phases.getOrNull(p.phaseIndex)
                ListItem(
                    modifier = Modifier.clickable { onOpenPlan(p.plan.id) },
                    headlineContent = { Text(p.plan.name, fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        val phaseText = phase?.let { "Phase ${p.phaseIndex + 1}: ${it.name}" } ?: ""
                        Text(if (p.active) phaseText else "Paused · $phaseText")
                    },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                )
            }
        }

        if (routines.isNotEmpty()) {
            item(key = "routines-h") {
                Text("Quick start", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            items(routines, key = { "r-${it.id}" }) { r -> RoutineCard(r, onOpenPlan, onStartRoutine) }
        }
    }
}

@Composable
private fun Hero(s: TodayState, vm: TodayViewModel, onStartSlot: (Long, String) -> Unit) {
    val agenda = s.agenda
    val hero = agenda.hero
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(20.dp)) {
            if (hero == null) {
                val anything = agenda.tasks.isNotEmpty()
                Text(
                    if (anything) "All done for today 🎉" else "Rest day",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (anything) "Nice work. See you tomorrow." else "Nothing from your plans today. Rest is part of the plan.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                return@Column
            }
            val pending = hero.pending
            val isNow = hero.minute <= s.nowMinute
            Text(
                when {
                    hero.key.slot == com.mohit.rehabit.content.SlotId.ANYTIME -> "ANY TIME TODAY"
                    isNow -> "NOW"
                    else -> "NEXT · ${minuteText(hero.minute)}"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(hero.key.label(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "${pending.size} exercise${if (pending.size == 1) "" else "s"} · about ${minutesFor(pending)} min",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(8.dp))
            pending.take(4).forEach {
                Text("• ${it.exercise.name}", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (pending.size > 4) Text("+ ${pending.size - 4} more", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { onStartSlot(agenda.day, hero.key.id) }, modifier = Modifier.fillMaxWidth().height(60.dp)) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Start", style = MaterialTheme.typography.titleMedium)
            }
            TextButton(onClick = { vm.setStatus(pending, TaskStatus.DONE) }, modifier = Modifier.align(Alignment.End)) {
                Text("Mark all done")
            }
        }
    }
}

@Composable
private fun GroupCard(
    g: SlotGroup,
    vm: TodayViewModel,
    exercises: Map<String, Exercise>,
    owned: Set<Equipment>,
    onStart: () -> Unit,
    onTryExercise: (String) -> Unit,
) {
    val allDone = g.pending.isEmpty()
    Card(Modifier.fillMaxWidth().alpha(if (allDone) 0.7f else 1f)) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(g.key.label(), style = MaterialTheme.typography.titleMedium)
                    val time = if (g.key.slot == com.mohit.rehabit.content.SlotId.ANYTIME) "Any time" else minuteText(g.minute)
                    Text(
                        "$time · ${g.done}/${g.tasks.size} done" +
                            if (!allDone) " · ${minutesFor(g.pending)} min left" else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!allDone) {
                    OutlinedButton(onClick = onStart) { Text("Start") }
                } else {
                    Text("Done ✓", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            g.tasks.forEach { TaskRow(it, vm, exercises, owned, onTryExercise) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskRow(t: Task, vm: TodayViewModel, exercises: Map<String, Exercise>, owned: Set<Equipment>, onTryExercise: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var swapTo by remember { mutableStateOf<Exercise?>(null) }
    val options = remember(t.exercise, exercises) {
        fun list(ids: List<String>, label: String) = ids.mapNotNull { exercises[it] }.map { label to it }
        list(t.exercise.regressions, "Easier") + list(t.exercise.progressions, "Harder") + list(t.exercise.substitutes, "Swap for")
    }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { if (t.pending) vm.setStatus(listOf(t), TaskStatus.DONE) else menu = true },
                    onLongClick = { menu = true },
                )
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    t.exercise.name,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (t.status == TaskStatus.DONE) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val extra = listOfNotNull(
                    doseText(t.dose, t.exercise.measure, t.exercise.laterality),
                    if (!t.core) "optional" else null,
                    if (t.moved) "moved from yesterday's run day" else null,
                ).joinToString(" · ")
                Text(extra, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    t.planNames.joinToString(" + "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val status = t.status
            if (status == null) {
                FilledTonalIconButton(onClick = { vm.setStatus(listOf(t), TaskStatus.DONE) }) {
                    Icon(Icons.Filled.Check, contentDescription = "Mark done")
                }
            } else {
                AssistChip(onClick = { vm.setStatus(listOf(t), null) }, label = { Text(statusLabel(status)) })
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Do it now (guided)") }, onClick = { menu = false; onTryExercise(t.exercise.id) })
            TaskStatus.entries.forEach { st ->
                DropdownMenuItem(text = { Text(statusLabel(st)) }, onClick = { menu = false; vm.setStatus(listOf(t), st) })
            }
            if (t.status != null) {
                DropdownMenuItem(text = { Text("Undo") }, onClick = { menu = false; vm.setStatus(listOf(t), null) })
            }
            options.forEach { (label, other) ->
                val missing = other.equipment.filter { !it.everyday && it !in owned }
                DropdownMenuItem(
                    text = { Text("$label: ${other.name}" + if (missing.isNotEmpty()) " (needs ${missing.joinToString { it.label.lowercase() }})" else "") },
                    onClick = { menu = false; swapTo = other },
                )
            }
        }
    }
    swapTo?.let { other ->
        AlertDialog(
            onDismissRequest = { swapTo = null },
            title = { Text("Use ${other.name}?") },
            text = { Text("Instead of ${t.exercise.name} in ${t.planNames.joinToString(" and ")}, from today on. Today's ticks are kept.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.swap(t, other)
                    swapTo = null
                }) { Text("Use it") }
            },
            dismissButton = { TextButton(onClick = { swapTo = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RoutineCard(r: Plan, onOpenPlan: (String) -> Unit, onStartRoutine: (String, String) -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        ListItem(
            modifier = Modifier.clickable { onOpenPlan(r.id) },
            headlineContent = { Text(r.name, fontWeight = FontWeight.SemiBold) },
            supportingContent = { Text("About ${r.estMinutes} min") },
            trailingContent = {
                FilledTonalIconButton(onClick = { onStartRoutine(r.id, r.phases.first().id) }) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Start ${r.name}")
                }
            },
        )
    }
}

@Composable
private fun NoPlans(
    allPlans: List<Plan>,
    routines: List<Plan>,
    onOpenPlan: (String) -> Unit,
    onStartRoutine: (String, String) -> Unit,
    onBrowseLibrary: () -> Unit,
) {
    val focused = allPlans.filter { it.kind == PlanKind.FOCUSED }.sortedBy { it.name }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "intro") {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("Your day starts here", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Follow a plan and it becomes a checklist here, spread over your day, with a gentle reminder " +
                            "for each time slot. Or just start a routine whenever you like.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onBrowseLibrary, modifier = Modifier.fillMaxWidth()) { Text("Browse library") }
                }
            }
        }
        if (focused.isNotEmpty()) {
            item(key = "plans-h") {
                Text("Plans you can follow", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            items(focused, key = { it.id }) { p ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    ListItem(
                        modifier = Modifier.clickable { onOpenPlan(p.id) },
                        headlineContent = { Text(p.name, fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("${p.phases.size} phases · about ${p.estMinutes} min a day") },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                    )
                }
            }
        }
        if (routines.isNotEmpty()) {
            item(key = "routines-h") {
                Text("Quick start", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            items(routines, key = { "r-${it.id}" }) { r -> RoutineCard(r, onOpenPlan, onStartRoutine) }
        }
    }
}

@Composable
private fun CheckInCard(prompts: List<CheckInPrompt>, onSave: (Map<String, Int>) -> Unit) {
    val values = remember(prompts) { mutableStateMapOf<String, Float>() }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Quick check-in", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "About 10 seconds. 0 = no pain, 10 = worst. It shows your trend over the weeks.",
                style = MaterialTheme.typography.bodySmall,
            )
            prompts.forEach { p ->
                Spacer(Modifier.height(8.dp))
                Text(p.metric, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                val v = values[p.planId]
                Text(
                    (if (prompts.size > 1) p.planName + " · " else "") + (v?.let { "${it.roundToInt()} out of 10" } ?: "Move the slider"),
                    style = MaterialTheme.typography.labelMedium,
                )
                Slider(value = v ?: 0f, onValueChange = { values[p.planId] = it }, valueRange = 0f..10f, steps = 9)
            }
            Button(
                onClick = { onSave(values.mapValues { it.value.roundToInt() }) },
                enabled = prompts.all { it.planId in values },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save check-in") }
        }
    }
}
