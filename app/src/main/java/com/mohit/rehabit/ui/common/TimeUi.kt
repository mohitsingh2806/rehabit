package com.mohit.rehabit.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val TimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

/** 1290 → "9:30 PM" */
fun minuteText(minute: Int): String {
    val m = Math.floorMod(minute, 24 * 60)
    return LocalTime.of(m / 60, m % 60).format(TimeFormat)
}

/** A list row showing a time; tapping it opens a time picker. */
@Composable
fun TimeRow(title: String, minute: Int, subtitle: String? = null, enabled: Boolean = true, onChange: (Int) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    ListItem(
        modifier = if (enabled) Modifier.clickable { editing = true } else Modifier,
        headlineContent = { Text(title) },
        supportingContent = if (subtitle != null) ({ Text(subtitle) }) else null,
        trailingContent = {
            Text(
                minuteText(minute),
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
    if (editing) {
        TimeDialog(title, minute, onDismiss = { editing = false }) {
            editing = false
            onChange(it)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(title: String, initialMinute: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val state = rememberTimePickerState(
        initialHour = initialMinute / 60 % 24,
        initialMinute = initialMinute % 60,
        is24Hour = false,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
