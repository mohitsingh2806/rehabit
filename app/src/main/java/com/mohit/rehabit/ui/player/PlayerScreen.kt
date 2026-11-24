package com.mohit.rehabit.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohit.rehabit.container
import com.mohit.rehabit.data.Feedback
import com.mohit.rehabit.domain.Step
import com.mohit.rehabit.domain.StepKind
import com.mohit.rehabit.domain.doseText
import kotlin.math.ceil
import kotlin.math.roundToInt

@Composable
fun PlayerScreen(request: PlayerRequest, onClose: () -> Unit) {
    val context = LocalContext.current
    val vm: PlayerViewModel = viewModel(key = request.toString()) {
        val c = context.container
        PlayerViewModel(context.applicationContext, c.content, c.db, c.appScope, request)
    }
    val s by vm.state.collectAsStateWithLifecycle()
    var confirmExit by remember { mutableStateOf(false) }

    KeepScreenOn()
    BackHandler(enabled = !s.finished && !s.loading && s.error == null) {
        vm.setPaused(true)
        confirmExit = true
    }

    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            when {
                s.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                s.error != null -> Column(
                    Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(s.error.orEmpty(), textAlign = TextAlign.Center)
                    TextButton(onClick = onClose) { Text("Close") }
                }
                s.finished -> Summary(
                    s = s,
                    onSave = { feedback, pain ->
                        vm.save(feedback, pain)
                        onClose()
                    },
                    onDiscard = onClose,
                )
                else -> Active(
                    s = s,
                    vm = vm,
                    onCloseRequest = {
                        vm.setPaused(true)
                        confirmExit = true
                    },
                )
            }
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = {
                confirmExit = false
                vm.setPaused(false)
            },
            title = { Text("End session?") },
            text = { Text("You can still save what you've done so far.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmExit = false
                    vm.endEarly()
                }) { Text("End session") }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmExit = false
                    vm.setPaused(false)
                }) { Text("Keep going") }
            },
        )
    }
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
private fun Active(s: PlayerState, vm: PlayerViewModel, onCloseRequest: () -> Unit) {
    val step = s.step ?: return
    val e = step.exercise
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCloseRequest) { Icon(Icons.Filled.Close, contentDescription = "End session") }
            Column(Modifier.weight(1f)) {
                Text(s.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    "Exercise ${step.itemIndex + 1} of ${s.items.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = vm::toggleVoice) { Text(if (s.voiceOn) "Voice on" else "Voice off") }
        }
        LinearProgressIndicator(
            progress = { (s.index + 1f) / s.steps.size },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                step.label.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = when (step.kind) {
                    StepKind.WORK -> MaterialTheme.colorScheme.primary
                    StepKind.REST, StepKind.SWITCH_SIDE -> MaterialTheme.colorScheme.secondary
                    StepKind.PREPARE -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                e.name,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                stepSubtitle(step),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))

            val duration = step.durationMs
            if (duration != null) {
                TimerRing(step, s.elapsedMs, duration)
            } else {
                Text(step.target.orEmpty(), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                Text("Tap Done when you finish", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (s.paused) {
                Text("Paused", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
            }

            if (step.kind == StepKind.PREPARE || step.kind == StepKind.WORK) {
                Spacer(Modifier.height(20.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        e.cues.forEach { Text("• $it", style = MaterialTheme.typography.titleMedium) }
                        if (step.kind == StepKind.PREPARE) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Stop if: ${e.stopIf}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
            val next = s.nextExercise
            if (next != null && step.kind != StepKind.PREPARE) {
                Spacer(Modifier.height(12.dp))
                Text("Next: ${next.name}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Main action
        when {
            step.kind == StepKind.PREPARE -> Button(onClick = vm::skip, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                Text("Start now", style = MaterialTheme.typography.titleMedium)
            }
            step.kind == StepKind.WORK && step.durationMs == null ->
                Button(onClick = vm::done, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                    Text("Done", style = MaterialTheme.typography.titleMedium)
                }
            step.kind == StepKind.WORK -> FilledTonalButton(onClick = vm::done, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("Finished early")
            }
            else -> FilledTonalButton(onClick = vm::skip, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(if (step.kind == StepKind.REST) "Skip rest" else "Ready")
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = vm::back, modifier = Modifier.weight(1f).height(56.dp)) { Text("Back") }
            Button(onClick = vm::togglePause, modifier = Modifier.weight(1f).height(56.dp)) {
                Text(if (s.paused) "Resume" else "Pause")
            }
            OutlinedButton(onClick = vm::skipExercise, modifier = Modifier.weight(1f).height(56.dp)) { Text("Skip") }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun stepSubtitle(step: Step): String {
    val dose = doseText(step.dose, step.exercise.measure, step.exercise.laterality)
    return when (step.kind) {
        StepKind.PREPARE -> dose
        StepKind.WORK, StepKind.SWITCH_SIDE -> if (step.sets > 1) "Set ${step.set} of ${step.sets} · $dose" else dose
        StepKind.REST -> "Next: set ${step.set + 1} of ${step.sets}"
    }
}

@Composable
private fun TimerRing(step: Step, elapsedMs: Long, durationMs: Long) {
    val progress = (elapsedMs.toFloat() / durationMs).coerceIn(0f, 1f)
    val remainingSec = ceil((durationMs - elapsedMs).coerceAtLeast(0) / 1000.0).toInt()
    Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { 1f - progress },
            modifier = Modifier.fillMaxSize(),
            strokeWidth = 14.dp,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val counter = step.repCounter
            if (counter != null) {
                val rep = counter.currentRep(elapsedMs)
                Text(
                    if (rep == 0) "Get set" else "Rep $rep of ${counter.reps}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(timeText(remainingSec), style = MaterialTheme.typography.titleMedium)
            } else {
                Text(timeText(remainingSec), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun timeText(sec: Int): String = if (sec >= 60) "%d:%02d".format(sec / 60, sec % 60) else "$sec"

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Summary(s: PlayerState, onSave: (Map<Int, Feedback>, Int?) -> Unit, onDiscard: () -> Unit) {
    val feedback = remember { mutableStateMapOf<Int, Feedback>() }
    var pain by remember { mutableStateOf<Float?>(null) }
    val minutes = ((s.endedAt - s.startedAt) / 60_000.0).let { if (it < 1) 1 else it.roundToInt() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Session complete", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "${s.title}\n$minutes min · ${s.exercisesDone} of ${s.items.size} exercises done",
            style = MaterialTheme.typography.bodyLarge,
        )

        if (s.isRehab) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Pain during the session", style = MaterialTheme.typography.titleMedium)
                    Text(
                        pain?.let { "${it.roundToInt()} out of 10" } ?: "Not rated: move the slider",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Slider(value = pain ?: 0f, onValueChange = { pain = it }, valueRange = 0f..10f, steps = 9)
                }
            }
        }

        Text("How did each exercise feel? (optional)", style = MaterialTheme.typography.titleMedium)
        s.items.forEachIndexed { i, item ->
            if (s.workDone(i) == 0) return@forEachIndexed
            Column {
                Text(item.exercise.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Feedback.entries.forEach { f ->
                        FilterChip(
                            selected = feedback[i] == f,
                            onClick = { if (feedback[i] == f) feedback.remove(i) else feedback[i] = f },
                            label = { Text(f.label) },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { onSave(feedback.toMap(), pain?.roundToInt()) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("Save session") }
        TextButton(onClick = onDiscard, modifier = Modifier.fillMaxWidth()) { Text("Discard") }
    }
}
