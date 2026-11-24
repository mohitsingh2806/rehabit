package com.mohit.rehabit.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mohit.rehabit.container
import com.mohit.rehabit.content.ContentBundle
import com.mohit.rehabit.content.ContentState
import com.mohit.rehabit.content.Phase
import com.mohit.rehabit.content.Plan
import com.mohit.rehabit.content.PlanItem
import com.mohit.rehabit.content.PlanKind
import com.mohit.rehabit.domain.SessionItem
import com.mohit.rehabit.domain.SessionScript
import com.mohit.rehabit.domain.doseText
import com.mohit.rehabit.domain.scheduleText
import com.mohit.rehabit.ui.common.NoInsets
import com.mohit.rehabit.ui.common.NotReviewedBadge
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import com.mohit.rehabit.data.EnrollmentEntity
import com.mohit.rehabit.data.FollowStatus
import com.mohit.rehabit.data.ReminderMode
import com.mohit.rehabit.ui.common.TimeRow
import com.mohit.rehabit.ui.common.minuteText
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanDetailScreen(
    id: String,
    onBack: () -> Unit,
    onOpenExercise: (String) -> Unit,
    onStart: (planId: String, phaseId: String) -> Unit,
    onFollow: (planId: String) -> Unit,
) {
    val context = LocalContext.current
    val state by context.container.content.state.collectAsStateWithLifecycle()
    val ready = state as? ContentState.Ready
    val plan = ready?.bundle?.plansById?.get(id)

    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = {
            TopAppBar(
                title = { Text(plan?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                ready == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                plan == null -> Text(
                    "This plan isn't in the library any more.",
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
                else -> PlanDetail(plan, ready.bundle, onOpenExercise, onStart, onFollow)
            }
        }
    }
}

@Composable
private fun PlanDetail(
    plan: Plan,
    bundle: ContentBundle,
    onOpenExercise: (String) -> Unit,
    onStart: (String, String) -> Unit,
    onFollow: (String) -> Unit,
) {
    val isPlan = plan.kind == PlanKind.FOCUSED
    val context = LocalContext.current
    val agenda = context.container.agenda
    val enrollmentsFlow = remember(agenda) { agenda.currentEnrollments() }
    val enrollments by enrollmentsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val enrollment = enrollments.firstOrNull { it.planId == plan.id }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        planMeta(plan).uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    if (!plan.reviewed) NotReviewedBadge()
                }
                Spacer(Modifier.height(4.dp))
                Text(plan.goal, style = MaterialTheme.typography.bodyLarge)
                if (isPlan && enrollment == null) {
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { onFollow(plan.id) }, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                        Text("Follow this plan daily", style = MaterialTheme.typography.titleMedium)
                    }
                }
                if (!isPlan) {
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { onStart(plan.id, plan.phases.first().id) },
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Start routine", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }

        if (enrollment != null) FollowingCard(plan, enrollment, bundle)

        Section("Who it's for") {
            Body(plan.whoFor)
            if (plan.notFor.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("Not for:", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                plan.notFor.forEach { Body("• $it") }
            }
        }

        if (plan.redFlags.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("See a doctor first if you have", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    plan.redFlags.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }

        plan.painRules?.let { rules ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("How much pain is OK", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(rules, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        plan.trackedMetric?.let { Section("What to track") { Body(it) } }

        plan.phases.forEachIndexed { i, phase ->
            PhaseCard(
                plan, phase, i, bundle, showHeader = isPlan || plan.phases.size > 1, onOpenExercise, onStart,
                current = enrollment?.phaseId == phase.id,
            )
        }

        if (plan.references.isNotEmpty()) {
            Section("References") {
                plan.references.forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PhaseCard(
    plan: Plan,
    phase: Phase,
    index: Int,
    bundle: ContentBundle,
    showHeader: Boolean,
    onOpenExercise: (String) -> Unit,
    onStart: (String, String) -> Unit,
    current: Boolean = false,
) {
    val minutes = phaseMinutes(phase, bundle)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showHeader) {
                Text(
                    (if (current) "YOUR PHASE · " else "") + "PHASE ${index + 1}" + (phase.durationHint?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(phase.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                phase.entryCriteria?.let { Labelled("Start here when", it) }
                phase.exitCriteria?.let { Labelled("Move on when", it) }
            }
            Text(
                "${phase.items.size} exercises · about $minutes min as one session",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
            phase.items.forEach { item -> ItemRow(item, bundle, onOpenExercise, chooserLabel(plan, item.showIf)) }
            if (plan.kind == PlanKind.FOCUSED) {
                OutlinedButton(onClick = { onStart(plan.id, phase.id) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Do this phase now")
                }
            }
        }
    }
}

private fun phaseMinutes(phase: Phase, bundle: ContentBundle): Int {
    val items = phase.items.mapNotNull { item ->
        bundle.exercisesById[item.exerciseId]?.let { SessionItem(it, item.dose ?: it.defaultDose) }
    }
    return (SessionScript.estimateMs(SessionScript.build(items)) / 60_000.0).roundToInt().coerceAtLeast(1)
}

@Composable
private fun ItemRow(item: PlanItem, bundle: ContentBundle, onOpenExercise: (String) -> Unit, onlyIf: String?) {
    val e = bundle.exercisesById[item.exerciseId] ?: return
    val dose = item.dose ?: e.defaultDose
    Surface(
        onClick = { onOpenExercise(e.id) },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        e.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (!item.core) {
                        Text(
                            "  optional",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(doseText(dose, e.measure, e.laterality), style = MaterialTheme.typography.bodyMedium)
                item.schedule?.let {
                    Text(scheduleText(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                onlyIf?.let {
                    Text("Only if: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
                item.note?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun Labelled(label: String, text: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** "eases:bending" → the option's label, e.g. "Bending forward or sitting eases it". */
private fun chooserLabel(plan: Plan, showIf: String?): String? {
    val (q, o) = showIf?.split(':', limit = 2)?.takeIf { it.size == 2 }?.let { it[0] to it[1] } ?: return null
    return plan.chooser.firstOrNull { it.id == q }?.options?.firstOrNull { it.id == o }?.label
}

@Composable
private fun FollowingCard(plan: Plan, e: EnrollmentEntity, bundle: ContentBundle) {
    val context = LocalContext.current
    val agenda = context.container.agenda
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<String?>(null) }
    val phaseIndex = plan.phases.indexOfFirst { it.id == e.phaseId }
    val phase = plan.phases.getOrNull(phaseIndex)
    val since = LocalDate.ofEpochDay(e.startEpochDay).format(DateTimeFormatter.ofPattern("d MMM"))
    val outdated = remember(e, bundle) { agenda.isOutdated(e, bundle) }

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                (if (e.status == FollowStatus.PAUSED) "PAUSED" else "FOLLOWING") + " · since $since",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            phase?.let { Text("Phase ${phaseIndex + 1}: ${it.name}", style = MaterialTheme.typography.titleMedium) }
            Text("Reminders: " + reminderText(e.reminderMode, e.dailyReminderMin), style = MaterialTheme.typography.bodyMedium)
            e.side?.let { Text("Sore side: " + it.name.lowercase().replaceFirstChar { c -> c.uppercase() }, style = MaterialTheme.typography.bodyMedium) }
            if (outdated) {
                Spacer(Modifier.height(4.dp))
                Text("This phase has been updated in the app.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = { scope.launch { agenda.setPhase(e.id, e.phaseId) } }) { Text("Use the new version") }
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (plan.phases.size > 1) OutlinedButton(onClick = { dialog = "phase" }) { Text("Change phase") }
                OutlinedButton(onClick = { dialog = "reminders" }) { Text("Reminders") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    scope.launch { if (e.status == FollowStatus.PAUSED) agenda.resume(e.id) else agenda.pause(e.id) }
                }) { Text(if (e.status == FollowStatus.PAUSED) "Resume" else "Pause") }
                TextButton(onClick = { dialog = "stop" }) { Text("Stop following") }
            }
        }
    }

    when (dialog) {
        "phase" -> {
            var selected by remember { mutableStateOf(e.phaseId) }
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text("Change phase") },
                text = {
                    Column {
                        plan.phases.forEachIndexed { i, ph ->
                            Row(
                                Modifier.fillMaxWidth().selectable(selected = selected == ph.id, role = Role.RadioButton) { selected = ph.id }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                RadioButton(selected = selected == ph.id, onClick = null)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text("Phase ${i + 1}: ${ph.name}")
                                    ph.entryCriteria?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                }
                            }
                        }
                        Text(
                            "Today's checklist starts fresh with the new phase.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        dialog = null
                        if (selected != e.phaseId) scope.launch { agenda.setPhase(e.id, selected) }
                    }) { Text("Change") }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
            )
        }
        "reminders" -> {
            var mode by remember { mutableStateOf(e.reminderMode) }
            var minute by remember { mutableStateOf(e.dailyReminderMin) }
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text("Reminders") },
                text = {
                    Column {
                        listOf(
                            ReminderMode.SLOTS to "At each time of day",
                            ReminderMode.DAILY to "Once a day",
                            ReminderMode.OFF to "No reminders",
                        ).forEach { (m, label) ->
                            Row(
                                Modifier.fillMaxWidth().selectable(selected = mode == m, role = Role.RadioButton) { mode = m }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = mode == m, onClick = null)
                                Spacer(Modifier.width(8.dp))
                                Text(label)
                            }
                        }
                        if (mode == ReminderMode.DAILY) TimeRow("Time", minute) { minute = it }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        dialog = null
                        scope.launch { agenda.updateEnrollment(e.id) { it.copy(reminderMode = mode, dailyReminderMin = minute) } }
                    }) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
            )
        }
        "stop" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Stop following ${plan.name}?") },
            text = { Text("It leaves your daily checklist and reminders. Your history is kept.") },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    scope.launch { agenda.stop(e.id) }
                }) { Text("Stop following") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
    }
}

private fun reminderText(mode: ReminderMode, minute: Int): String = when (mode) {
    ReminderMode.SLOTS -> "at each time of day"
    ReminderMode.DAILY -> "once a day at ${minuteText(minute)}"
    ReminderMode.OFF -> "off"
}
