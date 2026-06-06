@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun m3OutlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
)

@Composable
fun m3TimePickerColors() = TimePickerDefaults.colors(
    clockDialColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    clockDialSelectedContentColor = MaterialTheme.colorScheme.onPrimary,
    clockDialUnselectedContentColor = MaterialTheme.colorScheme.onSurface,
    selectorColor = MaterialTheme.colorScheme.primary,
    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    periodSelectorBorderColor = MaterialTheme.colorScheme.outline,
    periodSelectorSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    periodSelectorUnselectedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    periodSelectorSelectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    periodSelectorUnselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    timeSelectorSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    timeSelectorUnselectedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    timeSelectorSelectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    timeSelectorUnselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
)

/** Wavy circular indicator — use only for loading/wait states, not content progress bars. */
@Composable
fun GymLoadingIndicator(
    modifier: Modifier = Modifier,
    message: String? = null,
    progress: Float? = null
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (progress != null) {
            CircularWavyProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(48.dp),
                color = cs.primary,
                trackColor = cs.surfaceContainerHighest
            )
        } else {
            CircularWavyProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = cs.primary,
                trackColor = cs.surfaceContainerHighest
            )
        }
        message?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun GymLoadingIndicatorSmall(
    modifier: Modifier = Modifier.size(20.dp),
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary
) {
    CircularWavyProgressIndicator(
        modifier = modifier,
        color = color,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    )
}

/** Wavy linear progress for model download only (not diet/water stats bars). */
@Composable
fun GymModelDownloadProgress(
    progress: Float,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    val cs = MaterialTheme.colorScheme
    LinearWavyProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier,
        color = cs.primary,
        trackColor = cs.surfaceContainerHighest
    )
}

@Composable
fun DateNavigationTabs(
    yesterdaySelected: Boolean,
    todaySelected: Boolean,
    calendarSelected: Boolean,
    onYesterday: () -> Unit,
    onToday: () -> Unit,
    onCalendar: () -> Unit,
    modifier: Modifier = Modifier
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = yesterdaySelected,
            onClick = onYesterday,
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
            label = { Text("Yesterday") }
        )
        SegmentedButton(
            selected = todaySelected,
            onClick = onToday,
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
            label = { Text("Today") }
        )
        SegmentedButton(
            selected = calendarSelected,
            onClick = onCalendar,
            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
            icon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
            label = { Text("Log") }
        )
    }
}

/** Material 3 time picker dialog with dial ↔ text input toggle (official M3 pattern). */
@Composable
fun GymTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    is24Hour: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = is24Hour
    )
    var showDial by remember { mutableStateOf(true) }
    val colors = m3TimePickerColors()

    TimePickerDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (showDial) "Select time" else "Enter time",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        modeToggleButton = {
            IconButton(onClick = { showDial = !showDial }) {
                Icon(
                    imageVector = if (showDial) Icons.Default.Edit else Icons.Default.Schedule,
                    contentDescription = if (showDial) "Switch to text input" else "Switch to clock dial"
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(state.hour, state.minute) },
                enabled = state.isInputValid
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        if (showDial) {
            TimePicker(state = state, colors = colors)
        } else {
            TimeInput(state = state, colors = colors)
        }
    }
}

@Composable
fun GymTimePickerDialog(
    minuteOfDay: Int,
    onDismiss: () -> Unit,
    onConfirm: (minuteOfDay: Int) -> Unit
) {
    GymTimePickerDialog(
        initialHour = (minuteOfDay / 60).coerceIn(0, 23),
        initialMinute = (minuteOfDay % 60).coerceIn(0, 59),
        is24Hour = false,
        onDismiss = onDismiss,
        onConfirm = { hour, minute -> onConfirm(hour * 60 + minute) }
    )
}

/** M3-themed wheel picker inside an AlertDialog (AI Settings, model pickers, etc.). */
@Composable
fun WheelPickerDialog(
    title: String,
    items: List<String>,
    selectedIndex: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    emptyMessage: String? = null
) {
    val cs = MaterialTheme.colorScheme
    if (items.isEmpty() && emptyMessage != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title, color = cs.onSurface) },
            text = { Text(emptyMessage, color = cs.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("OK") }
            },
            containerColor = cs.surfaceContainerHigh
        )
        return
    }
    if (items.isEmpty()) return

    var draftIndex by remember(selectedIndex) { mutableIntStateOf(selectedIndex.coerceIn(0, items.lastIndex)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = cs.onSurface, style = MaterialTheme.typography.titleLarge) },
        text = {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = cs.surfaceContainerLow,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                WheelPicker(
                    items = items,
                    selectedIndex = draftIndex,
                    onSelected = { draftIndex = it },
                    label = null,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(draftIndex.coerceIn(0, items.lastIndex))
            }) {
                Text("Select", color = cs.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        containerColor = cs.surfaceContainerHigh
    )
}
