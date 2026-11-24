package com.mohit.rehabit.ui.library

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mohit.rehabit.container
import com.mohit.rehabit.content.ContentBundle
import com.mohit.rehabit.content.ContentState
import com.mohit.rehabit.content.Exercise
import com.mohit.rehabit.content.MediaType
import com.mohit.rehabit.ui.common.NoInsets
import com.mohit.rehabit.ui.common.NotReviewedBadge
import com.mohit.rehabit.ui.common.defaultDoseText
import com.mohit.rehabit.ui.common.label

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(
    id: String,
    onBack: () -> Unit,
    onOpenExercise: (String) -> Unit,
    onTry: (String) -> Unit,
) {
    val context = LocalContext.current
    val state by context.container.content.state.collectAsStateWithLifecycle()
    val ready = state as? ContentState.Ready
    val exercise = ready?.bundle?.exercisesById?.get(id)

    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = {
            TopAppBar(
                title = { Text(exercise?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                ready == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                exercise == null -> Text(
                    "This exercise isn't in the library any more.",
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
                else -> ExerciseDetail(exercise, ready.bundle, onOpenExercise, onTry)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseDetail(
    e: Exercise,
    bundle: ContentBundle,
    onOpenExercise: (String) -> Unit,
    onTry: (String) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Dose + tags
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        e.kind.label.uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    if (!e.reviewed) NotReviewedBadge()
                }
                Text(e.defaultDoseText(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    e.bodyAreas.forEach { Tag(it.label) }
                    Tag(e.position.label)
                    if (e.equipment.isEmpty()) Tag("No equipment") else e.equipment.forEach { Tag(it.label) }
                    if (e.deskFriendly) Tag("Desk-friendly")
                    if (e.trailFriendly) Tag("Trail-friendly")
                }
                Spacer(Modifier.height(16.dp))
                Button(onClick = { onTry(e.id) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Try it now (guided)")
                }
            }
        }

        Section("Why it helps") { Body(e.why) }

        Section("Set up") {
            e.setup.forEachIndexed { i, step -> Body("${i + 1}. $step") }
        }

        Section("Key cues") {
            e.cues.forEach { cue ->
                Text("• $cue", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }

        Callout("You should feel", e.feel, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        Callout("Stop if", e.stopIf, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, warning = true)

        if (e.mistakes.isNotEmpty()) Section("Common mistakes") { e.mistakes.forEach { Body("• $it") } }
        if (e.cautions.isNotEmpty()) Section("Cautions") { e.cautions.forEach { Body("• $it") } }

        LinkSection("Easier version", e.regressions, bundle, onOpenExercise)
        LinkSection("Harder version", e.progressions, bundle, onOpenExercise)
        LinkSection("Alternatives", e.substitutes, bundle, onOpenExercise)

        val videos = e.media.filter { it.type == MediaType.VIDEO_LINK }
        if (videos.isNotEmpty()) {
            Section("Videos") {
                videos.forEach { v ->
                    Text(
                        v.credit ?: v.url,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.clickable { uriHandler.openUri(v.url) }.padding(vertical = 4.dp),
                    )
                }
            }
        }

        if (e.references.isNotEmpty()) {
            Section("References") {
                e.references.forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
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
private fun Callout(title: String, text: String, container: Color, content: Color, warning: Boolean = false) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Row(Modifier.padding(16.dp)) {
            if (warning) {
                Icon(Icons.Filled.Warning, contentDescription = null)
                Spacer(Modifier.width(12.dp))
            }
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(text, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun LinkSection(title: String, ids: List<String>, bundle: ContentBundle, onOpen: (String) -> Unit) {
    val linked = ids.mapNotNull { bundle.exercisesById[it] }
    if (linked.isEmpty()) return
    Section(title) {
        linked.forEach { other ->
            Surface(
                onClick = { onOpen(other.id) },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(other.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(other.defaultDoseText(), style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun Tag(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}
