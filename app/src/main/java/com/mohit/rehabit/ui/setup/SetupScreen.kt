package com.mohit.rehabit.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mohit.rehabit.container
import com.mohit.rehabit.content.Equipment
import com.mohit.rehabit.domain.DayPrefs
import com.mohit.rehabit.ui.common.NoInsets
import com.mohit.rehabit.ui.common.TimeRow
import com.mohit.rehabit.ui.common.label
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** "Your day": slot times, workdays, desk breaks and equipment. First-run setup and Settings. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SetupScreen(firstRun: Boolean, onBack: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    var prefs by remember { mutableStateOf<DayPrefs?>(null) }
    LaunchedEffect(Unit) { prefs = c.settings.get() }

    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = {
            TopAppBar(
                title = { Text(if (firstRun) "Set up your day" else "Your day") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        val p = prefs
        if (p == null) {
            Box(Modifier.padding(padding).fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
            return@Scaffold
        }
        fun update(change: DayPrefs.() -> DayPrefs) { prefs = p.change() }

        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            if (firstRun) {
                Text(
                    "Plans spread exercises across your day, at times like these. Reminders come at these times " +
                        "(once per slot, never repeated). You can change all of this later in Settings.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp),
                )
            }

            Section("Times of day")
            ListItem(
                headlineContent = { Text("On waking: follow my phone alarm") },
                supportingContent = { Text("Uses your alarm-clock time on days it's set") },
                trailingContent = {
                    Switch(checked = p.wakeFollowsAlarm, onCheckedChange = { on -> update { copy(wakeFollowsAlarm = on) } })
                },
            )
            TimeRow(
                if (p.wakeFollowsAlarm) "On waking, when no alarm is set" else "On waking",
                p.wakeMin,
            ) { m -> update { copy(wakeMin = m) } }
            TimeRow("Morning", p.morningMin, "Short block: holds and gentle movement") { m -> update { copy(morningMin = m) } }
            TimeRow("Midday", p.middayMin) { m -> update { copy(middayMin = m) } }
            TimeRow("Evening", p.eveningMin) { m -> update { copy(eveningMin = m) } }
            TimeRow("Night", p.nightMin, "Main session: stretching and strength") { m -> update { copy(nightMin = m) } }

            HorizontalDivider()
            Section("Workdays and desk breaks")
            FlowRow(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DayOfWeek.entries.forEach { d ->
                    FilterChip(
                        selected = d in p.workdays,
                        onClick = { update { copy(workdays = if (d in workdays) workdays - d else workdays + d) } },
                        label = { Text(d.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                    )
                }
            }
            ListItem(
                headlineContent = { Text("Desk-break reminders") },
                supportingContent = { Text("2 a day on workdays, quiet. Desk exercises still show on the checklist when off.") },
                trailingContent = {
                    Switch(checked = p.deskBreaks, onCheckedChange = { on -> update { copy(deskBreaks = on) } })
                },
            )
            TimeRow("Desk break 1", p.desk1Min, enabled = p.deskBreaks) { m -> update { copy(desk1Min = m) } }
            TimeRow("Desk break 2", p.desk2Min, enabled = p.deskBreaks) { m -> update { copy(desk2Min = m) } }

            HorizontalDivider()
            Section("Symptom check-ins")
            Text(
                "On these days Today asks for a quick 0-10 score for each rehab plan you follow (about 10 seconds).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            FlowRow(
                Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DayOfWeek.entries.forEach { d ->
                    FilterChip(
                        selected = d in p.checkInDays,
                        onClick = { update { copy(checkInDays = if (d in checkInDays) checkInDays - d else checkInDays + d) } },
                        label = { Text(d.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                    )
                }
            }

            HorizontalDivider()
            Section("Equipment you have")
            Text(
                "Used to suggest swaps later. Every exercise has a no-equipment alternative.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            FlowRow(
                Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Equipment.entries.filter { !it.everyday }.forEach { eq ->
                    FilterChip(
                        selected = eq in p.equipment,
                        onClick = { update { copy(equipment = if (eq in equipment) equipment - eq else equipment + eq) } },
                        label = { Text(eq.label) },
                    )
                }
            }

            Button(
                onClick = {
                    scope.launch {
                        c.settings.saveDay(p)
                        c.scheduler.rescheduleAll()
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp),
            ) { Text(if (firstRun) "Save and continue" else "Save") }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}
