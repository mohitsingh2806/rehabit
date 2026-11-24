package com.mohit.rehabit.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohit.rehabit.container
import com.mohit.rehabit.data.ExcuseReason
import com.mohit.rehabit.data.HistoryRepository
import com.mohit.rehabit.data.PlanProgress
import com.mohit.rehabit.data.ProgressState
import com.mohit.rehabit.data.SessionSummary
import com.mohit.rehabit.data.TaskStatus
import com.mohit.rehabit.domain.DayMark
import com.mohit.rehabit.domain.History
import com.mohit.rehabit.domain.Insight
import com.mohit.rehabit.domain.InsightKind
import com.mohit.rehabit.domain.label
import com.mohit.rehabit.ui.common.NoInsets
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val WhenFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM · h:mm a")
private val DayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM")
private val ShortDay: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")

class ProgressViewModel(private val history: HistoryRepository) : ViewModel() {
    val month = MutableStateFlow(YearMonth.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ProgressState?> = month
        .flatMapLatest { history.progressFlow(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun shiftMonth(by: Long) {
        val next = month.value.plusMonths(by)
        if (next <= YearMonth.now()) month.value = next
    }

    fun saveCheckIn(day: Long, planId: String, value: Int) {
        viewModelScope.launch { history.saveCheckIns(day, mapOf(planId to value)) }
    }

    fun setExcused(day: Long, reason: ExcuseReason?) {
        viewModelScope.launch { history.setExcused(day, reason) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen() {
    val context = LocalContext.current
    val c = context.container
    val vm: ProgressViewModel = viewModel { ProgressViewModel(c.history) }
    val state by vm.state.collectAsStateWithLifecycle()
    val recentFlow = remember { c.db.sessionDao().observeRecent(10) }
    val sessions by recentFlow.collectAsStateWithLifecycle<List<SessionSummary>?>(initialValue = null)
    var openDay by remember { mutableStateOf<Long?>(null) }
    var checkInFor by remember { mutableStateOf<PlanProgress?>(null) }

    Scaffold(contentWindowInsets = NoInsets, topBar = { TopAppBar(title = { Text("Progress") }) }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val s = state
            val recent = sessions.orEmpty()
            when {
                s == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                !s.hasPlans && recent.isEmpty() -> Text(
                    "Nothing here yet. Follow a plan or do a routine, and your days, weekly progress and " +
                        "symptom trends show up here.",
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(s.insights, key = { it.title }) { InsightCard(it) }

                    if (s.plans.isNotEmpty()) {
                        item(key = "adherence-h") { Heading("Weekly progress", "Core items done on finished days. Excused days don't count.") }
                        items(s.plans, key = { "a-${it.planId}" }) { p ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(p.planName + if (!p.following) " (no longer followed)" else "", style = MaterialTheme.typography.titleSmall)
                                    Spacer(Modifier.height(8.dp))
                                    WeekBars(p.weeks, History.weekStart(s.today))
                                }
                            }
                        }
                    }

                    item(key = "calendar") {
                        Card(Modifier.fillMaxWidth()) {
                            Box(Modifier.padding(8.dp)) {
                                MonthCalendar(
                                    month = s.month,
                                    days = s.days,
                                    today = s.today,
                                    onPrev = { vm.shiftMonth(-1) },
                                    onNext = { vm.shiftMonth(1) },
                                    onDay = { openDay = it },
                                )
                            }
                        }
                    }

                    val tracked = s.plans.filter { it.metric != null }
                    if (tracked.isNotEmpty()) {
                        item(key = "trends-h") { Heading("Symptom trends", "Lower is better. Check-ins come up on Today on your check-in days.") }
                        items(tracked, key = { "t-${it.planId}" }) { p -> TrendCard(p, onAdd = { checkInFor = p }) }
                    }

                    if (recent.isNotEmpty()) {
                        item(key = "sessions-h") { Heading("Recent sessions", null) }
                        items(recent, key = { "s-${it.session.id}" }) { SessionRow(it) }
                    }
                }
            }
        }
    }

    val s = state
    val day = openDay
    if (s != null && day != null) {
        DayDialog(s, day, onDismiss = { openDay = null }, onExcuse = { reason -> vm.setExcused(day, reason) })
    }
    checkInFor?.let { p ->
        CheckInDialog(p, onDismiss = { checkInFor = null }) { value ->
            vm.saveCheckIn(LocalDate.now().toEpochDay(), p.planId, value)
            checkInFor = null
        }
    }
}

@Composable
private fun Heading(title: String, subtitle: String?) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun InsightCard(insight: Insight) {
    val warning = insight.kind == InsightKind.WARNING
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (warning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
            contentColor = if (warning) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Row(Modifier.padding(16.dp)) {
            Icon(if (warning) Icons.Filled.Warning else Icons.Filled.Check, contentDescription = if (warning) "Warning" else "Good news")
            Spacer(Modifier.width(12.dp))
            Column {
                Text(insight.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(insight.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun TrendCard(p: PlanProgress, onAdd: () -> Unit) {
    var showValues by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(p.metric.orEmpty(), style = MaterialTheme.typography.titleSmall)
            Text(p.planName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            if (p.checkIns.isEmpty()) {
                Text("No check-ins yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                TrendChart(p.checkIns.takeLast(30))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onAdd) { Text("Add check-in") }
                Spacer(Modifier.width(8.dp))
                if (p.checkIns.isNotEmpty()) {
                    TextButton(onClick = { showValues = !showValues }) { Text(if (showValues) "Hide values" else "Show values") }
                }
            }
            if (showValues) {
                p.checkIns.takeLast(30).reversed().forEach {
                    Text(
                        "${LocalDate.ofEpochDay(it.day).format(ShortDay)}: ${it.value}/10",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun CheckInDialog(p: PlanProgress, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var value by remember { mutableStateOf<Float?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Check-in: ${p.planName}") },
        text = {
            Column {
                Text(p.metric.orEmpty())
                Text(
                    value?.let { "${it.roundToInt()} out of 10" } ?: "Move the slider",
                    style = MaterialTheme.typography.titleMedium,
                )
                Slider(value = value ?: 0f, onValueChange = { value = it }, valueRange = 0f..10f, steps = 9)
            }
        },
        confirmButton = {
            TextButton(onClick = { value?.let { onSave(it.roundToInt()) } }, enabled = value != null) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayDialog(s: ProgressState, day: Long, onDismiss: () -> Unit, onExcuse: (ExcuseReason?) -> Unit) {
    val date = LocalDate.ofEpochDay(day)
    val score = s.days[day]
    val agenda = s.agendas[day]
    val excuse = s.excused[day]
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(date.format(DayFormat)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val done = (score?.done ?: 0.0).formatDone()
                val planned = score?.planned ?: 0
                val summary = when (score?.mark) {
                    DayMark.ALL_DONE -> "All core items done ✓"
                    DayMark.PARTIAL -> "Partly done: $done of $planned"
                    DayMark.MISSED -> "Nothing done ($planned planned)"
                    DayMark.EXCUSED -> "Excused: ${excuse?.label.orEmpty()}"
                    DayMark.IN_PROGRESS -> "In progress: $done of $planned so far"
                    else -> "Nothing was planned"
                }
                Text(summary, fontWeight = FontWeight.SemiBold)
                agenda?.groups?.forEach { g ->
                    Text(g.key.label(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    g.tasks.forEach { t ->
                        val status = when (t.status) {
                            TaskStatus.DONE -> "done"
                            TaskStatus.PARTIAL -> "partly done"
                            TaskStatus.SKIPPED -> "skipped"
                            TaskStatus.SKIPPED_PAIN -> "skipped (pain)"
                            null -> "not done"
                        }
                        Text("• ${t.exercise.name}: $status" + if (!t.core) " (optional)" else "", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                s.checkInsByDay[day]?.let { values ->
                    Text("Check-ins", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    values.forEach { (planId, v) ->
                        val name = s.plans.firstOrNull { it.planId == planId }?.planName ?: planId
                        Text("• $name: $v/10", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("Doesn't count against you:", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExcuseReason.entries.forEach { r ->
                        FilterChip(
                            selected = excuse == r,
                            onClick = { onExcuse(if (excuse == r) null else r) },
                            label = { Text(r.label) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private fun Double.formatDone(): String = if (this % 1.0 == 0.0) toInt().toString() else "%.1f".format(this)

@Composable
private fun SessionRow(summary: SessionSummary) {
    val s = summary.session
    val time = Instant.ofEpochMilli(s.startedAt).atZone(ZoneId.systemDefault()).format(WhenFormat)
    val minutes = ((s.endedAt - s.startedAt) / 60_000.0).let { if (it < 1) 1 else it.roundToInt() }
    val pain = s.painDuring?.let { " · pain $it/10" } ?: ""
    ListItem(
        overlineContent = { Text(time) },
        headlineContent = { Text(s.title) },
        supportingContent = { Text("$minutes min · ${summary.exercisesDone} of ${summary.exercisesTotal} exercises$pain") },
    )
}
