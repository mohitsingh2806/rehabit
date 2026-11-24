package com.mohit.rehabit.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mohit.rehabit.container
import com.mohit.rehabit.content.ContentState
import com.mohit.rehabit.content.PlanKind
import com.mohit.rehabit.ui.common.NoInsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val state by context.container.content.state.collectAsStateWithLifecycle()
    val appVersion = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }

    Scaffold(contentWindowInsets = NoInsets, topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Content", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            when (val s = state) {
                ContentState.Loading -> Text("Loading…")
                is ContentState.Ready -> {
                    val b = s.bundle
                    InfoRow("Content version", b.version.toString())
                    InfoRow("Exercises", b.exercises.size.toString())
                    InfoRow("Routines", b.plans.count { it.kind == PlanKind.ON_DEMAND }.toString())
                    InfoRow("Plans", b.plans.count { it.kind == PlanKind.FOCUSED }.toString())
                    InfoRow(
                        "Not reviewed yet",
                        (b.exercises.count { !it.reviewed } + b.plans.count { !it.reviewed }).toString(),
                    )
                    if (s.problems.isEmpty()) {
                        Text("No content problems found ✓", color = MaterialTheme.colorScheme.primary)
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Content problems (${s.problems.size})", fontWeight = FontWeight.Bold)
                                s.problems.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                }
            }

            Text(
                "About",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp),
            )
            InfoRow("App version", appVersion)
            Text(
                "Guidance, not diagnosis. If something hurts more, or you have warning signs listed on a plan, " +
                    "stop and see a doctor or physiotherapist.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}
