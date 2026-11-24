package com.mohit.rehabit.ui.follow

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mohit.rehabit.container
import com.mohit.rehabit.content.ContentBundle
import com.mohit.rehabit.content.ContentState
import com.mohit.rehabit.content.Plan
import com.mohit.rehabit.content.PlanCategory
import com.mohit.rehabit.content.Sides
import com.mohit.rehabit.data.ReminderMode
import com.mohit.rehabit.data.SideChoice
import com.mohit.rehabit.domain.combinationWarnings
import com.mohit.rehabit.ui.common.NoInsets
import com.mohit.rehabit.ui.library.CombinationCard
import com.mohit.rehabit.ui.common.TimeRow
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowPlanScreen(planId: String, onBack: () -> Unit, onFollowed: () -> Unit) {
    val context = LocalContext.current
    val state by context.container.content.state.collectAsStateWithLifecycle()
    val bundle = (state as? ContentState.Ready)?.bundle
    val plan = bundle?.plansById?.get(planId)

    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = {
            TopAppBar(
                title = { Text("Follow ${plan?.name ?: "plan"}") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (plan == null || bundle == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                FollowForm(plan, bundle, onFollowed)
            }
        }
    }
}

@Composable
private fun FollowForm(plan: Plan, bundle: ContentBundle, onFollowed: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()

    val rehab = plan.category == PlanCategory.REHAB
    // Ask for the sore side only if some exercise in the plan is done on one side.
    val needsSide = plan.phases.any { ph ->
        ph.items.any { item -> (item.dose ?: bundle.exercisesById[item.exerciseId]?.defaultDose)?.sides == Sides.AFFECTED }
    }

    var noRedFlags by remember { mutableStateOf(!rehab || plan.redFlags.isEmpty()) }
    val answers = remember { mutableStateMapOf<String, String>() }
    var side by remember { mutableStateOf<SideChoice?>(null) }
    var phaseId by remember { mutableStateOf(plan.phases.first().id) }
    var startTomorrow by remember { mutableStateOf(false) }
    var reminder by remember { mutableStateOf(ReminderMode.SLOTS) }
    var dailyMin by remember { mutableStateOf(19 * 60) }
    var saving by remember { mutableStateOf(false) }

    val ready = noRedFlags && plan.chooser.all { it.id in answers } && (!needsSide || side != null) && !saving

    fun follow() {
        saving = true
        scope.launch {
            val today = LocalDate.now().toEpochDay()
            c.agenda.follow(
                planId = plan.id,
                phaseId = phaseId,
                startDay = if (startTomorrow) today + 1 else today,
                side = side,
                answers = answers.toMap(),
                reminderMode = reminder,
                dailyReminderMin = dailyMin,
            )
            onFollowed()
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { follow() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Is this plan for you?
        if (rehab) {
            Title("Is this plan for you?")
            Text(plan.whoFor, style = MaterialTheme.typography.bodyLarge)
            if (plan.notFor.isNotEmpty()) {
                Column {
                    Text("Not for:", fontWeight = FontWeight.SemiBold)
                    plan.notFor.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                }
            }
            if (plan.redFlags.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Warning, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("See a doctor first if you have", fontWeight = FontWeight.Bold)
                        }
                        plan.redFlags.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().selectable(selected = noRedFlags, role = Role.Checkbox) { noRedFlags = !noRedFlags },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = noRedFlags, onCheckedChange = null)
                    Spacer(Modifier.width(8.dp))
                    Text("None of these apply to me", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        val enrollmentsFlow = remember { c.agenda.currentEnrollments() }
        val enrollments by enrollmentsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
        val warnings = remember(enrollments) { combinationWarnings(plan, c.agenda.followed(enrollments, bundle), bundle.exercisesById) }
        if (warnings.isNotEmpty()) CombinationCard(warnings)

        // Chooser questions
        plan.chooser.forEach { q ->
            Title(q.text)
            Column {
                q.options.forEach { o ->
                    Choice(
                        selected = answers[q.id] == o.id,
                        title = o.label,
                        subtitle = o.hint,
                    ) { answers[q.id] = o.id }
                }
            }
        }

        // Side
        if (needsSide) {
            Title("Which side is sore?")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(SideChoice.LEFT to "Left", SideChoice.RIGHT to "Right", SideChoice.BOTH to "Both").forEach { (s, label) ->
                    FilterChip(selected = side == s, onClick = { side = s }, label = { Text(label) })
                }
            }
        }

        // Phase
        if (plan.phases.size > 1) {
            Title("Where are you now?")
            Column {
                plan.phases.forEachIndexed { i, ph ->
                    Choice(
                        selected = phaseId == ph.id,
                        title = "Phase ${i + 1}: ${ph.name}",
                        subtitle = ph.entryCriteria,
                    ) { phaseId = ph.id }
                }
            }
        }

        // Start
        Title("Start")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !startTomorrow, onClick = { startTomorrow = false }, label = { Text("Today") })
            FilterChip(selected = startTomorrow, onClick = { startTomorrow = true }, label = { Text("Tomorrow") })
        }

        // Reminders
        Title("Reminders")
        Column {
            Choice(
                selected = reminder == ReminderMode.SLOTS,
                title = "At each time of day",
                subtitle = "One reminder per slot with something to do (e.g. morning and night). Never repeated.",
            ) { reminder = ReminderMode.SLOTS }
            Choice(
                selected = reminder == ReminderMode.DAILY,
                title = "Once a day",
                subtitle = "A single reminder if anything is left.",
            ) { reminder = ReminderMode.DAILY }
            if (reminder == ReminderMode.DAILY) {
                TimeRow("Reminder time", dailyMin) { dailyMin = it }
            }
            Choice(selected = reminder == ReminderMode.OFF, title = "No reminders", subtitle = null) { reminder = ReminderMode.OFF }
        }

        Button(
            onClick = {
                val needsPermission = reminder != ReminderMode.OFF &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                if (needsPermission) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else follow()
            },
            enabled = ready,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("Start following") }
        if (!ready && !saving) {
            Text(
                "Answer the questions above to continue.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun Choice(selected: Boolean, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
