package com.example.ui

import android.app.Activity
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.GymViewModel
import com.example.notifications.WorkoutNotificationHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutReminderScreen(
    viewModel: GymViewModel,
    onBack: () -> Unit
) {
    val profile by viewModel.userProfile.collectAsState()
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme

    val initialMinute = profile.workoutReminderTimeMinute.coerceIn(0, 23 * 60 + 59)
    val timePickerState = rememberTimePickerState(
        initialHour = initialMinute / 60,
        initialMinute = initialMinute % 60,
        is24Hour = false
    )
    var showDial by remember { mutableStateOf(true) }

    var repeatDaily by remember(profile.workoutReminderRepeat) {
        mutableStateOf(profile.workoutReminderRepeat)
    }

    val alarmSoundLabel = remember(profile.workoutAlarmSoundUri) {
        resolveAlarmSoundLabel(context, profile.workoutAlarmSoundUri)
    }

    val ringtonePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        }
        viewModel.saveWorkoutAlarmSound(uri?.toString().orEmpty())
        WorkoutNotificationHelper.createChannel(context, uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Workout Reminder", style = MaterialTheme.typography.headlineSmall, color = cs.primary)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = cs.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        },
        containerColor = cs.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = cs.surfaceContainerHigh)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (showDial) "Select time" else "Enter time",
                            style = MaterialTheme.typography.labelMedium,
                            color = cs.onSurfaceVariant
                        )
                        IconButton(onClick = { showDial = !showDial }) {
                            Icon(
                                if (showDial) Icons.Default.Edit else Icons.Default.Schedule,
                                contentDescription = "Toggle time input mode"
                            )
                        }
                    }
                    if (showDial) {
                        TimePicker(state = timePickerState, colors = m3TimePickerColors())
                    } else {
                        TimeInput(state = timePickerState, colors = m3TimePickerColors())
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "OCCURRENCE",
                    style = MaterialTheme.typography.labelLarge,
                    color = cs.primary,
                    modifier = Modifier.padding(start = 4.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    OccurrenceCard(
                        label = "Once",
                        icon = Icons.Default.Event,
                        selected = !repeatDaily,
                        onClick = { repeatDaily = false },
                        modifier = Modifier.weight(1f)
                    )
                    OccurrenceCard(
                        label = "Repeat",
                        icon = Icons.Default.Repeat,
                        selected = repeatDaily,
                        onClick = { repeatDaily = true },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "ALARM SOUND",
                    style = MaterialTheme.typography.labelLarge,
                    color = cs.primary,
                    modifier = Modifier.padding(start = 4.dp)
                )
                Text(
                    "Workout reminders use an alarm with sound and vibration. Hydration uses quiet notifications only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Surface(
                    onClick = {
                        val intent = android.content.Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select workout alarm")
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            if (profile.workoutAlarmSoundUri.isNotBlank()) {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(profile.workoutAlarmSoundUri))
                            }
                        }
                        ringtonePicker.launch(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = cs.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.MusicNote, null, tint = cs.primary)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Alarm tone", style = MaterialTheme.typography.titleSmall, color = cs.onSurface)
                            Text(alarmSoundLabel, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val minuteOfDay = timePickerState.hour * 60 + timePickerState.minute
                    viewModel.saveWorkoutReminder(true, minuteOfDay, repeatDaily)
                    WorkoutNotificationHelper.scheduleReminder(
                        context,
                        profile.copy(
                            workoutReminderEnabled = true,
                            workoutReminderTimeMinute = minuteOfDay,
                            workoutReminderRepeat = repeatDaily
                        )
                    )
                    onBack()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.extraLarge,
                enabled = timePickerState.isInputValid
            ) {
                Text("Save Reminder", style = MaterialTheme.typography.titleMedium)
            }
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel")
            }
        }
    }
}

private fun resolveAlarmSoundLabel(context: android.content.Context, uriString: String): String {
    if (uriString.isBlank()) return "System default alarm"
    return try {
        RingtoneManager.getRingtone(context, Uri.parse(uriString))?.getTitle(context) ?: "Custom alarm"
    } catch (_: Exception) {
        "Custom alarm"
    }
}

@Composable
private fun OccurrenceCard(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.height(100.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) cs.primaryContainer else cs.surfaceContainerHigh,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, cs.outlineVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                null,
                tint = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}
