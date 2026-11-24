package com.mohit.rehabit.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mohit.rehabit.container
import com.mohit.rehabit.content.ContentState
import com.mohit.rehabit.content.PlanKind
import com.mohit.rehabit.ui.common.NoInsets
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    onOpenPlan: (String) -> Unit,
    onStartRoutine: (planId: String, phaseId: String) -> Unit,
    onBrowseLibrary: () -> Unit,
) {
    val context = LocalContext.current
    val state by context.container.content.state.collectAsStateWithLifecycle()
    val plans = (state as? ContentState.Ready)?.bundle?.plans.orEmpty()
    val routines = plans.filter { it.kind == PlanKind.ON_DEMAND }.sortedBy { it.name }
    val focused = plans.filter { it.kind == PlanKind.FOCUSED }.sortedBy { it.name }

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
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "NOTHING PLANNED YET",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("Your day starts here", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Plans you follow will show up here as a checklist for the day (coming in the next update). " +
                            "Until then, do any routine or plan phase as a guided session.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onBrowseLibrary, modifier = Modifier.fillMaxWidth()) { Text("Browse library") }
                }
            }

            if (routines.isNotEmpty()) {
                Text("Quick start", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                routines.forEach { r ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Row {
                            ListItem(
                                modifier = Modifier.weight(1f).clickable { onOpenPlan(r.id) },
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
                }
            }

            if (focused.isNotEmpty()) {
                Text("Plans", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                focused.forEach { p ->
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
        }
    }
}
