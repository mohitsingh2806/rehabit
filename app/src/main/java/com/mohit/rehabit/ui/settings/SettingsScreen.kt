package com.mohit.rehabit.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mohit.rehabit.container
import com.mohit.rehabit.content.ContentState
import com.mohit.rehabit.content.PlanKind
import com.mohit.rehabit.data.BackupFile
import com.mohit.rehabit.ui.common.NoInsets
import com.mohit.rehabit.ui.common.minuteText
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onOpenDay: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val state by c.content.state.collectAsStateWithLifecycle()
    val prefsFlow = remember { c.settings.flow }
    val prefs by prefsFlow.collectAsStateWithLifecycle(initialValue = null)
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumeTick++
        onPauseOrDispose { }
    }
    val appVersion = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }

    Scaffold(contentWindowInsets = NoInsets, topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            SectionTitle("Your day")
            val p = prefs
            ListItem(
                modifier = Modifier.clickable(onClick = onOpenDay),
                headlineContent = { Text("Times of day, workdays, desk breaks") },
                supportingContent = {
                    Text(
                        if (p == null) "" else "Morning ${minuteText(p.morningMin)} · Night ${minuteText(p.nightMin)}" +
                            if (p.deskBreaks) " · Desk breaks on" else "",
                    )
                },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
            )

            HorizontalDivider()
            SectionTitle("Make reminders reliable")
            ReliabilitySection(context, resumeTick, c.scheduler.canScheduleExact())

            HorizontalDivider()
            SectionTitle("Backup")
            BackupSection()

            HorizontalDivider()
            SectionTitle("Content")
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (val s = state) {
                    ContentState.Loading -> Text("Loading…")
                    is ContentState.Ready -> {
                        val b = s.bundle
                        InfoRow("Content version", b.version.toString())
                        InfoRow("Exercises", b.exercises.size.toString())
                        InfoRow("Routines", b.plans.count { it.kind == PlanKind.ON_DEMAND }.toString())
                        InfoRow("Plans", b.plans.count { it.kind == PlanKind.FOCUSED }.toString())
                        InfoRow("Not reviewed yet", (b.exercises.count { !it.reviewed } + b.plans.count { !it.reviewed }).toString())
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
            }

            HorizontalDivider(Modifier.padding(top = 12.dp))
            SectionTitle("About")
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ReliabilitySection(context: Context, resumeTick: Int, exactAlarmsOk: Boolean) {
    val notificationsOk = remember(resumeTick) { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    val batteryOk = remember(resumeTick) {
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(context.packageName)
    }
    val pkgUri = Uri.parse("package:${context.packageName}")

    PermissionRow("Notifications", "Allowed", "Turned off: you won't see reminders", notificationsOk) {
        context.safeStart(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        )
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        PermissionRow("Exact alarms", "Allowed: reminders come on time", "Not allowed: reminders may be late", exactAlarmsOk) {
            context.safeStart(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkgUri))
        }
    }
    PermissionRow(
        "Battery optimisation",
        "Unrestricted: reminders can run in the background",
        "Restricted: some phones block reminders. Tap Fix.",
        batteryOk,
    ) {
        context.safeStart(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri))
    }
}

@Composable
private fun PermissionRow(title: String, okText: String, badText: String, ok: Boolean, onFix: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Text(
                if (ok) okText else badText,
                color = if (ok) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
        },
        trailingContent = {
            if (ok) {
                Icon(Icons.Filled.Check, contentDescription = "OK", tint = MaterialTheme.colorScheme.primary)
            } else {
                TextButton(onClick = onFix) { Text("Fix") }
            }
        },
    )
}

private fun Context.safeStart(intent: Intent) {
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
private fun BackupSection() {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<BackupFile?>(null) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            message = runCatching { c.backup.export(uri) }.fold(
                onSuccess = { s -> "Backup saved: ${s.sessions} sessions, ${s.plansFollowed} followed plans, ${s.ownExercises + s.ownPlans} of your own items, ${s.photos} photos." },
                onFailure = { "Couldn't save the backup: ${it.message}" },
            )
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { c.backup.read(uri) }
                .onSuccess { pending = it }
                .onFailure { message = it.message ?: "Couldn't read that file" }
        }
    }

    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Everything lives only on this phone. Save a backup file (to Drive, email, Downloads…) so you can restore " +
                "it after a reset or on a new phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { exporter.launch("rehabit-backup-${LocalDate.now()}.json") }) { Text("Export backup") }
            OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }) {
                Text("Restore")
            }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }

    pending?.let { file ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Restore this backup?") },
            text = {
                val date = Instant.ofEpochMilli(file.exportedAt).atZone(ZoneId.systemDefault()).toLocalDate()
                Text(
                    "From $date: ${file.sessions.size} sessions, ${file.enrollments.size} plan follows, " +
                        "${file.userExercises.size + file.userPlans.size} of your own items, ${file.photos.size} photos.\n\n" +
                        "This replaces everything currently in the app.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    scope.launch {
                        message = runCatching {
                            c.backup.restore(file)
                            c.scheduler.rescheduleAll()
                        }.fold(onSuccess = { "Restored." }, onFailure = { "Restore failed: ${it.message}" })
                    }
                }) { Text("Replace and restore") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } },
        )
    }
}
