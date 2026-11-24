package com.mohit.rehabit.ui.builder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mohit.rehabit.container
import com.mohit.rehabit.content.ContentBundle
import com.mohit.rehabit.content.ContentState
import com.mohit.rehabit.content.ContentValidator
import com.mohit.rehabit.content.DayRule
import com.mohit.rehabit.content.Dose
import com.mohit.rehabit.content.Exercise
import com.mohit.rehabit.content.Laterality
import com.mohit.rehabit.content.Measure
import com.mohit.rehabit.content.PlanItem
import com.mohit.rehabit.content.PlanKind
import com.mohit.rehabit.content.Schedule
import com.mohit.rehabit.content.Sides
import com.mohit.rehabit.content.SlotId
import com.mohit.rehabit.content.Weekday
import com.mohit.rehabit.data.EnrollmentEntity
import com.mohit.rehabit.domain.Builder
import com.mohit.rehabit.domain.doseText
import com.mohit.rehabit.domain.label
import com.mohit.rehabit.domain.scheduleText
import com.mohit.rehabit.ui.common.NoInsets
import com.mohit.rehabit.ui.common.NumberField
import kotlinx.coroutines.launch

/**
 * Build a routine or daily plan from library exercises. [id] edits one of your plans;
 * [combine] (plan ids) starts from those plans merged.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanEditorScreen(id: String?, combine: List<String>, onBack: () -> Unit, onSaved: (String) -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val state by c.content.state.collectAsStateWithLifecycle()
    val enrollmentsFlow = remember { c.agenda.currentEnrollments() }
    val enrollments by enrollmentsFlow.collectAsStateWithLifecycle(initialValue = null)
    val ready = state as? ContentState.Ready
    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = {
            TopAppBar(
                title = { Text(if (id != null) "Edit plan" else if (combine.isNotEmpty()) "Combine plans" else "New routine or plan") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val list = enrollments
            if (ready == null || list == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                PlanForm(ready.bundle, id, combine, list, onSaved)
            }
        }
    }
}

@Composable
private fun PlanForm(bundle: ContentBundle, id: String?, combine: List<String>, enrollments: List<EnrollmentEntity>, onSaved: (String) -> Unit) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val existing = id?.let { bundle.plansById[it] }
    val sources = remember {
        combine.mapNotNull { pid ->
            val plan = bundle.plansById[pid] ?: return@mapNotNull null
            val phaseId = enrollments.firstOrNull { it.planId == pid }?.phaseId
            plan to (plan.phases.firstOrNull { it.id == phaseId } ?: plan.phases.first())
        }
    }

    var name by remember { mutableStateOf(existing?.name ?: sources.joinToString(" + ") { it.first.name }) }
    var kind by remember {
        mutableStateOf(existing?.kind ?: if (sources.isNotEmpty() && sources.all { it.first.kind == PlanKind.ON_DEMAND }) PlanKind.ON_DEMAND else PlanKind.FOCUSED)
    }
    var goal by remember { mutableStateOf(existing?.goal ?: "") }
    var metric by remember { mutableStateOf(existing?.trackedMetric ?: "") }
    val planItems = remember {
        mutableStateListOf<PlanItem>().apply {
            addAll(existing?.phases?.firstOrNull()?.items ?: if (sources.isNotEmpty()) Builder.combine(sources, kind, bundle.exercisesById) else emptyList())
        }
    }
    var picking by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Int?>(null) }
    var errors by remember { mutableStateOf<List<String>>(emptyList()) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Title("Type")
                Chips(listOf(PlanKind.ON_DEMAND, PlanKind.FOCUSED), { it == kind }, { kind = it }) {
                    if (it == PlanKind.ON_DEMAND) "Routine (do it whenever)" else "Daily plan (checklist + reminders)"
                }
                OutlinedTextField(goal, { goal = it }, label = { Text("Notes, e.g. from your physio (optional)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                if (kind == PlanKind.FOCUSED) {
                    OutlinedTextField(
                        metric, { metric = it },
                        label = { Text("Symptom to track (optional), e.g. Knee pain on stairs (0-10)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                }
                Title("Exercises (${planItems.size})")
            }
        }
        items(planItems.indices.toList(), key = { "$it-${planItems[it].exerciseId}" }) { i ->
            val item = planItems[i]
            val e = bundle.exercisesById[item.exerciseId]
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable { editing = i }.padding(vertical = 10.dp)) {
                        Text(e?.name ?: item.exerciseId, fontWeight = FontWeight.SemiBold)
                        if (e != null) Text(doseText(item.dose ?: e.defaultDose, e.measure, e.laterality), style = MaterialTheme.typography.bodySmall)
                        if (kind == PlanKind.FOCUSED) {
                            Text(scheduleText(item.schedule ?: Builder.defaultSchedule), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                        if (!item.core) Text("optional", style = MaterialTheme.typography.labelSmall)
                    }
                    Column {
                        IconButton(onClick = { if (i > 0) planItems.add(i - 1, planItems.removeAt(i)) }, enabled = i > 0) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
                        }
                        IconButton(onClick = { if (i < planItems.lastIndex) planItems.add(i + 1, planItems.removeAt(i)) }, enabled = i < planItems.lastIndex) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
                        }
                    }
                    Column {
                        IconButton(onClick = { editing = i }) { Icon(Icons.Filled.Edit, contentDescription = "Edit") }
                        IconButton(onClick = { planItems.removeAt(i) }) { Icon(Icons.Filled.Delete, contentDescription = "Remove") }
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(" Add exercise")
                }
                errors.forEach { Text("• $it", color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = {
                        val planId = existing?.id ?: Builder.newId(name, bundle.plansById.keys, System.currentTimeMillis())
                        val plan = Builder.userPlan(planId, name, kind, goal, metric, planItems.toList(), bundle.exercisesById)
                        errors = buildList {
                            if (name.isBlank()) add("Give it a name.")
                            if (planItems.isEmpty()) add("Add at least one exercise.")
                            if (isEmpty()) addAll(ContentValidator.validate(bundle.copy(plans = listOf(plan))).filter { it.startsWith("Plan ") })
                        }
                        if (errors.isEmpty()) {
                            scope.launch {
                                c.userContent.savePlan(plan)
                                onSaved(plan.id)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text("Save") }
            }
        }
    }

    if (picking) {
        ExercisePicker(bundle, onDismiss = { picking = false }) { e ->
            picking = false
            planItems.add(PlanItem(exerciseId = e.id, schedule = if (kind == PlanKind.FOCUSED) Builder.defaultSchedule else null))
            editing = planItems.lastIndex
        }
    }
    editing?.let { i ->
        val item = planItems.getOrNull(i)
        val e = item?.let { bundle.exercisesById[it.exerciseId] }
        if (item == null || e == null) {
            editing = null
        } else {
            ItemDialog(item, e, daily = kind == PlanKind.FOCUSED, onDismiss = { editing = null }) { updated ->
                planItems[i] = updated
                editing = null
            }
        }
    }
}

@Composable
private fun ExercisePicker(bundle: ContentBundle, onDismiss: () -> Unit, onPick: (Exercise) -> Unit) {
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    val list = bundle.exercises
        .filter { q.isEmpty() || it.name.contains(q, true) || it.aliases.any { a -> a.contains(q, true) } }
        .sortedBy { it.name }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add exercise") },
        text = {
            Column {
                OutlinedTextField(query, { query = it }, label = { Text("Search") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(list, key = { it.id }) { e ->
                        ListItem(
                            modifier = Modifier.clickable { onPick(e) },
                            headlineContent = { Text(e.name) },
                            supportingContent = { Text(doseText(e.defaultDose, e.measure, e.laterality)) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private val SlotChoices = listOf(SlotId.WAKE, SlotId.MORNING, SlotId.DESK, SlotId.MIDDAY, SlotId.EVENING, SlotId.NIGHT, SlotId.ANYTIME)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemDialog(item: PlanItem, e: Exercise, daily: Boolean, onDismiss: () -> Unit, onSave: (PlanItem) -> Unit) {
    val d = item.dose ?: e.defaultDose
    var sets by remember { mutableStateOf(d.sets.toString()) }
    var reps by remember { mutableStateOf(d.reps?.toString() ?: "10") }
    var hold by remember { mutableStateOf(d.holdSec?.toString() ?: "30") }
    var duration by remember { mutableStateOf(d.durationSec?.toString() ?: "60") }
    var distance by remember { mutableStateOf(d.distanceM?.toString() ?: "20") }
    var rest by remember { mutableStateOf(d.restSec?.toString() ?: "") }
    var tempo by remember { mutableStateOf(d.tempo ?: "") }
    var soreSide by remember { mutableStateOf(d.sides == Sides.AFFECTED) }
    val s = item.schedule ?: Builder.defaultSchedule
    var slots by remember { mutableStateOf(s.slots.toSet()) }
    var days by remember { mutableStateOf(s.days) }
    var everyN by remember { mutableStateOf((s.everyNDays ?: 2).toString()) }
    var weekdays by remember { mutableStateOf(s.weekdays.toSet()) }
    var notOnRunDay by remember { mutableStateOf(s.notOnRunDay) }
    var optional by remember { mutableStateOf(!item.core) }
    var note by remember { mutableStateOf(item.note ?: "") }
    var errors by remember { mutableStateOf<List<String>>(emptyList()) }

    fun build(): PlanItem {
        val m = e.measure
        val dose = Dose(
            sets = sets.toIntOrNull() ?: 1,
            reps = reps.toIntOrNull().takeIf { m == Measure.REPS || m == Measure.REPS_WITH_HOLD },
            holdSec = hold.toIntOrNull().takeIf { m == Measure.HOLD || m == Measure.REPS_WITH_HOLD },
            durationSec = duration.toIntOrNull().takeIf { m == Measure.TIME },
            distanceM = distance.toIntOrNull().takeIf { m == Measure.DISTANCE },
            restSec = rest.toIntOrNull(),
            tempo = tempo.trim().ifEmpty { null },
            sides = if (e.laterality == Laterality.EACH_SIDE && soreSide) Sides.AFFECTED else null,
            load = d.load,
            effort = d.effort,
        )
        val ordered = SlotChoices.filter { it in slots }.ifEmpty { listOf(SlotId.NIGHT) }
        val schedule = Schedule(
            timesPerDay = ordered.size,
            days = days,
            everyNDays = if (days == DayRule.EVERY_N_DAYS) everyN.toIntOrNull() ?: 2 else null,
            weekdays = if (days == DayRule.WEEKDAYS) Weekday.entries.filter { it in weekdays } else emptyList(),
            slots = ordered,
            notOnRunDay = notOnRunDay,
        )
        return item.copy(
            dose = dose,
            schedule = if (daily) schedule else null,
            core = !optional,
            note = note.trim().ifEmpty { null },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(e.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Title("Dose")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val w = Modifier.fillMaxWidth(0.45f)
                    val m = e.measure
                    NumberField("Sets", sets, { sets = it }, w)
                    if (m == Measure.REPS || m == Measure.REPS_WITH_HOLD) NumberField(if (e.laterality == Laterality.BILATERAL) "Reps" else "Reps per side", reps, { reps = it }, w)
                    if (m == Measure.HOLD || m == Measure.REPS_WITH_HOLD) NumberField("Hold (s)", hold, { hold = it }, w)
                    if (m == Measure.TIME) NumberField("Time (s)", duration, { duration = it }, w)
                    if (m == Measure.DISTANCE) NumberField("Distance (m)", distance, { distance = it }, w)
                    NumberField("Rest (s)", rest, { rest = it }, w)
                    if (m == Measure.REPS) OutlinedTextField(tempo, { tempo = it }, label = { Text("Tempo 3-1-3") }, singleLine = true, modifier = w)
                }
                if (e.laterality == Laterality.EACH_SIDE) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Sore side only", modifier = Modifier.weight(1f))
                        Switch(checked = soreSide, onCheckedChange = { soreSide = it })
                    }
                }
                if (daily) {
                    Title("When in the day")
                    Chips(SlotChoices, { it in slots }, { sl -> slots = if (sl in slots) slots - sl else slots + sl }) { it.label }
                    Title("How often")
                    Chips(listOf(DayRule.DAILY, DayRule.EVERY_N_DAYS, DayRule.WEEKDAYS), { it == days }, { days = it }) {
                        when (it) {
                            DayRule.DAILY -> "Every day"
                            DayRule.EVERY_N_DAYS -> "Every few days"
                            DayRule.WEEKDAYS -> "Chosen days"
                        }
                    }
                    if (days == DayRule.EVERY_N_DAYS) NumberField("Every how many days (2+)", everyN, { everyN = it })
                    if (days == DayRule.WEEKDAYS) {
                        Chips(Weekday.entries, { it in weekdays }, { wd -> weekdays = if (wd in weekdays) weekdays - wd else weekdays + wd }) {
                            it.name.lowercase().replaceFirstChar { c -> c.uppercase() }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Not on days I run (moves to the next day)", modifier = Modifier.weight(1f))
                        Switch(checked = notOnRunDay, onCheckedChange = { notOnRunDay = it })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Optional (never counts against you)", modifier = Modifier.weight(1f))
                    Switch(checked = optional, onCheckedChange = { optional = it })
                }
                OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth())
                errors.forEach { Text("• $it", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val updated = build()
                errors = ContentValidator.checkDose(updated.dose ?: e.defaultDose, e, "Dose").map { it.removePrefix("Dose: ") } +
                    (updated.schedule?.let { sch ->
                        buildList {
                            if (sch.days == DayRule.EVERY_N_DAYS && (sch.everyNDays ?: 0) < 2) add("Every how many days: 2 or more.")
                            if (sch.days == DayRule.WEEKDAYS && sch.weekdays.isEmpty()) add("Pick at least one day.")
                        }
                    } ?: emptyList())
                if (errors.isEmpty()) onSave(updated)
            }) { Text("Done") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
