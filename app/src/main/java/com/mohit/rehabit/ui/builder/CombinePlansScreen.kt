package com.mohit.rehabit.ui.builder

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mohit.rehabit.container
import com.mohit.rehabit.content.ContentState
import com.mohit.rehabit.content.Plan
import com.mohit.rehabit.content.PlanKind
import com.mohit.rehabit.ui.common.NoInsets
import com.mohit.rehabit.ui.library.planMeta

/** Pick 2+ plans/routines to merge into one (duplicates removed), then edit it in the builder. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CombinePlansScreen(onBack: () -> Unit, onCombine: (List<String>) -> Unit) {
    val context = LocalContext.current
    val state by context.container.content.state.collectAsStateWithLifecycle()
    val plans = (state as? ContentState.Ready)?.bundle?.plans?.sortedWith(compareBy<Plan>({ it.kind != PlanKind.FOCUSED }, { it.name }))
    val selected = remember { mutableStateListOf<String>() }

    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = {
            TopAppBar(
                title = { Text("Combine plans") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (plans == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
                return@Box
            }
            Column(Modifier.fillMaxSize()) {
                Text(
                    "Pick two or more. The same exercise at the same time of day is kept once, with the bigger dose. " +
                        "Plans you follow use your current phase. You can edit the result before saving.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 8.dp)) {
                    items(plans, key = { it.id }) { p ->
                        val checked = p.id in selected
                        ListItem(
                            modifier = Modifier.toggleable(checked, role = Role.Checkbox) {
                                if (it) selected.add(p.id) else selected.remove(p.id)
                            },
                            leadingContent = { Checkbox(checked = checked, onCheckedChange = null) },
                            headlineContent = { Text(p.name) },
                            supportingContent = { Text(planMeta(p)) },
                        )
                    }
                }
                Button(
                    onClick = { onCombine(selected.toList()) },
                    enabled = selected.size >= 2,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp),
                ) { Text(if (selected.size < 2) "Pick at least 2" else "Combine ${selected.size}") }
            }
        }
    }
}
