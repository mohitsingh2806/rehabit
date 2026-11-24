package com.mohit.rehabit.ui.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mohit.rehabit.container
import com.mohit.rehabit.content.BodyArea
import com.mohit.rehabit.content.ContentState
import com.mohit.rehabit.content.ContentValidator
import com.mohit.rehabit.content.Dose
import com.mohit.rehabit.content.Equipment
import com.mohit.rehabit.content.Exercise
import com.mohit.rehabit.content.ExerciseKind
import com.mohit.rehabit.content.Laterality
import com.mohit.rehabit.content.Measure
import com.mohit.rehabit.content.Media
import com.mohit.rehabit.content.MediaType
import com.mohit.rehabit.content.Position
import com.mohit.rehabit.content.Sides
import com.mohit.rehabit.domain.Builder
import com.mohit.rehabit.ui.common.NoInsets
import com.mohit.rehabit.ui.common.label
import kotlinx.coroutines.launch

/** Create or edit one of your own exercises (e.g. one your physio showed you). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseEditorScreen(id: String?, onBack: () -> Unit, onSaved: (String) -> Unit) {
    val context = LocalContext.current
    val state by context.container.content.state.collectAsStateWithLifecycle()
    val ready = state as? ContentState.Ready
    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = {
            TopAppBar(
                title = { Text(if (id == null) "New exercise" else "Edit exercise") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (ready == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                val existing = id?.let { ready.bundle.exercisesById[it] }
                EditorForm(existing, ready.bundle.exercisesById.keys, onSaved)
            }
        }
    }
}

private val MeasureLabels = linkedMapOf(
    Measure.REPS to "Reps",
    Measure.HOLD to "Hold",
    Measure.REPS_WITH_HOLD to "Reps with a hold",
    Measure.TIME to "Time",
    Measure.DISTANCE to "Distance",
)

private val LateralityLabels = linkedMapOf(
    Laterality.BILATERAL to "Both sides at once",
    Laterality.EACH_SIDE to "One side, then the other",
    Laterality.ALTERNATING to "Alternating sides",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorForm(existing: Exercise?, takenIds: Set<String>, onSaved: (String) -> Unit) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val d = existing?.defaultDose

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var areas by remember { mutableStateOf(existing?.bodyAreas?.toSet() ?: emptySet()) }
    var kind by remember { mutableStateOf(existing?.kind ?: ExerciseKind.STRENGTH) }
    var position by remember { mutableStateOf(existing?.position ?: Position.STANDING) }
    var equipment by remember { mutableStateOf(existing?.equipment?.toSet() ?: emptySet()) }
    var laterality by remember { mutableStateOf(existing?.laterality ?: Laterality.BILATERAL) }
    var measure by remember { mutableStateOf(existing?.measure ?: Measure.REPS) }
    var sets by remember { mutableStateOf((d?.sets ?: 3).toString()) }
    var reps by remember { mutableStateOf(d?.reps?.toString() ?: "10") }
    var hold by remember { mutableStateOf(d?.holdSec?.toString() ?: "30") }
    var duration by remember { mutableStateOf(d?.durationSec?.toString() ?: "60") }
    var distance by remember { mutableStateOf(d?.distanceM?.toString() ?: "20") }
    var rest by remember { mutableStateOf(d?.restSec?.toString() ?: "") }
    var tempo by remember { mutableStateOf(d?.tempo ?: "") }
    var soreSideOnly by remember { mutableStateOf(d?.sides == Sides.AFFECTED) }
    var setup by remember { mutableStateOf(existing?.setup?.joinToString("\n") ?: "") }
    var cue1 by remember { mutableStateOf(existing?.cues?.getOrNull(0) ?: "") }
    var cue2 by remember { mutableStateOf(existing?.cues?.getOrNull(1) ?: "") }
    var cue3 by remember { mutableStateOf(existing?.cues?.getOrNull(2) ?: "") }
    var why by remember { mutableStateOf(existing?.why ?: "") }
    var feel by remember { mutableStateOf(existing?.feel ?: "") }
    var stopIf by remember { mutableStateOf(existing?.stopIf ?: "") }
    var video by remember { mutableStateOf(existing?.media?.firstOrNull { it.type == MediaType.VIDEO_LINK }?.url ?: "") }
    var errors by remember { mutableStateOf<List<String>>(emptyList()) }

    fun build(): Exercise {
        val n = name.trim()
        val dose = Dose(
            sets = sets.toIntOrNull() ?: 1,
            reps = reps.toIntOrNull().takeIf { measure == Measure.REPS || measure == Measure.REPS_WITH_HOLD },
            holdSec = hold.toIntOrNull().takeIf { measure == Measure.HOLD || measure == Measure.REPS_WITH_HOLD },
            durationSec = duration.toIntOrNull().takeIf { measure == Measure.TIME },
            distanceM = distance.toIntOrNull().takeIf { measure == Measure.DISTANCE },
            restSec = rest.toIntOrNull(),
            tempo = tempo.trim().ifEmpty { null },
            sides = if (laterality == Laterality.EACH_SIDE && soreSideOnly) Sides.AFFECTED else null,
        )
        return Exercise(
            id = existing?.id ?: Builder.newId(n, takenIds, System.currentTimeMillis()),
            name = n,
            bodyAreas = BodyArea.entries.filter { it in areas },
            kind = kind,
            position = position,
            equipment = Equipment.entries.filter { it in equipment },
            laterality = laterality,
            measure = measure,
            defaultDose = dose,
            why = why.trim().ifEmpty { "Your own exercise." },
            setup = setup.lines().map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf("Set up as you were shown.") },
            cues = listOf(cue1, cue2, cue3).map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf("Move slowly and with control") },
            feel = feel.trim().ifEmpty { "Where your physio said you should feel it." },
            stopIf = stopIf.trim().ifEmpty { "Sharp pain, or pain that's worse the next day." },
            media = video.trim().takeIf { it.isNotEmpty() }?.let { listOf(Media(MediaType.VIDEO_LINK, it)) } ?: emptyList(),
            reviewed = true,
        )
    }

    fun validate(e: Exercise): List<String> {
        val out = mutableListOf<String>()
        if (e.name.isBlank()) out += "Give it a name."
        if (e.bodyAreas.isEmpty()) out += "Pick at least one body area."
        e.cues.filter { it.length > ContentValidator.MAX_CUE_LENGTH }.forEach { out += "Cue \"$it\" is too long (max ${ContentValidator.MAX_CUE_LENGTH} characters)." }
        if (e.media.any { !it.url.startsWith("https://") }) out += "Video link must start with https://"
        out += ContentValidator.checkDose(e.defaultDose, e, "Dose").map { it.removePrefix("Dose: ") }
        return out
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())

        Title("Body areas")
        Chips(BodyArea.entries, { it in areas }, { a -> areas = if (a in areas) areas - a else areas + a }) { it.label }
        Title("Type")
        Chips(ExerciseKind.entries, { it == kind }, { kind = it }) { it.label }
        Title("Position")
        Chips(Position.entries, { it == position }, { position = it }) { it.label }
        Title("Equipment (none = leave empty)")
        Chips(Equipment.entries, { it in equipment }, { eq -> equipment = if (eq in equipment) equipment - eq else equipment + eq }) { it.label }

        Title("Sides")
        Chips(LateralityLabels.keys.toList(), { it == laterality }, { laterality = it }) { LateralityLabels.getValue(it) }
        if (laterality == Laterality.EACH_SIDE) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sore side only (you pick the side when following a plan)", modifier = Modifier.weight(1f))
                Switch(checked = soreSideOnly, onCheckedChange = { soreSideOnly = it })
            }
        }

        Title("Measured in")
        Chips(MeasureLabels.keys.toList(), { it == measure }, { measure = it }) { MeasureLabels.getValue(it) }
        Title("Default dose")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val w = Modifier.fillMaxWidth(0.45f)
            com.mohit.rehabit.ui.common.NumberField("Sets", sets, { sets = it }, w)
            if (measure == Measure.REPS || measure == Measure.REPS_WITH_HOLD) com.mohit.rehabit.ui.common.NumberField("Reps", reps, { reps = it }, w)
            if (measure == Measure.HOLD || measure == Measure.REPS_WITH_HOLD) com.mohit.rehabit.ui.common.NumberField("Hold (seconds)", hold, { hold = it }, w)
            if (measure == Measure.TIME) com.mohit.rehabit.ui.common.NumberField("Time (seconds)", duration, { duration = it }, w)
            if (measure == Measure.DISTANCE) com.mohit.rehabit.ui.common.NumberField("Distance (metres)", distance, { distance = it }, w)
            com.mohit.rehabit.ui.common.NumberField("Rest between sets (s)", rest, { rest = it }, w)
            if (measure == Measure.REPS) {
                OutlinedTextField(tempo, { tempo = it }, label = { Text("Tempo, e.g. 3-1-3") }, singleLine = true, modifier = w)
            }
        }

        Title("How to do it")
        OutlinedTextField(setup, { setup = it }, label = { Text("Setup steps (one per line)") }, minLines = 3, modifier = Modifier.fillMaxWidth())
        Text("Key cues (spoken by the player, short)", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(cue1, { cue1 = it }, label = { Text("Cue 1") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(cue2, { cue2 = it }, label = { Text("Cue 2 (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(cue3, { cue3 = it }, label = { Text("Cue 3 (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(feel, { feel = it }, label = { Text("Where you should feel it (optional)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(stopIf, { stopIf = it }, label = { Text("Stop if (optional)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(why, { why = it }, label = { Text("Why / notes (optional)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(video, { video = it }, label = { Text("Video link (optional, https://…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())

        errors.forEach { Text("• $it", color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                val e = build()
                errors = validate(e)
                if (errors.isEmpty()) {
                    scope.launch {
                        c.userContent.saveExercise(e)
                        onSaved(e.id)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("Save exercise") }
    }
}

@Composable
internal fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> Chips(options: List<T>, selected: (T) -> Boolean, onClick: (T) -> Unit, label: (T) -> String) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o -> FilterChip(selected = selected(o), onClick = { onClick(o) }, label = { Text(label(o)) }) }
    }
}
