package com.mohit.rehabit.ui.progress

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mohit.rehabit.container
import com.mohit.rehabit.data.SessionSummary
import com.mohit.rehabit.ui.common.NoInsets
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val WhenFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM · h:mm a")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen() {
    val context = LocalContext.current
    val dao = context.container.db.sessionDao()
    val recentFlow = remember(dao) { dao.observeRecent(50) }
    val sessions by recentFlow.collectAsStateWithLifecycle<List<SessionSummary>?>(initialValue = null)

    Scaffold(contentWindowInsets = NoInsets, topBar = { TopAppBar(title = { Text("Progress") }) }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val list = sessions
            when {
                list == null -> Unit
                list.isEmpty() -> Text(
                    "No sessions yet. Do a routine or a plan phase and it shows up here. " +
                        "Weekly adherence and symptom trends come once you follow a plan.",
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                    item {
                        Text(
                            "Recent sessions",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    items(list, key = { it.session.id }) { SessionRow(it) }
                }
            }
        }
    }
}

@Composable
private fun SessionRow(summary: SessionSummary) {
    val s = summary.session
    val time = Instant.ofEpochMilli(s.startedAt).atZone(ZoneId.systemDefault()).format(WhenFormat)
    val minutes = ((s.endedAt - s.startedAt) / 60_000.0).let { if (it < 1) 1 else it.roundToInt() }
    val pain = s.painDuring?.let { " · pain $it/10" } ?: ""
    ListItem(
        overlineContent = { Text(time) },
        headlineContent = { Text(s.title) },
        supportingContent = {
            Text("$minutes min · ${summary.exercisesDone} of ${summary.exercisesTotal} exercises$pain")
        },
    )
}
