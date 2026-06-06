package com.example.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.GymViewModel
import com.example.data.DietDateUtils
import com.example.data.UserProfile
import com.example.data.WaterGoalCalculator
import com.example.data.WaterReminderModes
import com.example.notifications.WaterNotificationHelper
import com.example.ui.theme.*
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaterReminderSettingsScreen(
    viewModel: GymViewModel,
    onBack: () -> Unit
) {
    val profile by viewModel.userProfile.collectAsState()
    val context = LocalContext.current
    val goalGlasses = viewModel.effectiveWaterGoalGlasses()

    var enabled by remember(profile) { mutableStateOf(profile.waterReminderEnabled) }
    var mode by remember(profile) { mutableStateOf(profile.waterReminderMode) }
    var intervalMinutes by remember(profile) { mutableIntStateOf(profile.waterReminderIntervalMinutes) }
    var timesPerDay by remember(profile) { mutableIntStateOf(profile.waterReminderTimesPerDay) }
    var dailyTimeMinute by remember(profile) { mutableIntStateOf(profile.waterReminderDailyTimeMinute) }
    var weeklyDay by remember(profile) { mutableIntStateOf(profile.waterReminderWeeklyDay) }
    var windowStart by remember(profile) { mutableIntStateOf(profile.waterReminderWindowStartMinute) }
    var windowEnd by remember(profile) { mutableIntStateOf(profile.waterReminderWindowEndMinute) }

    var showIntervalPicker by remember { mutableStateOf(false) }
    var showTimesPicker by remember { mutableStateOf(false) }
    var showDailyTimePicker by remember { mutableStateOf(false) }
    var showWeeklyPicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.setNotificationsEnabled(true)
    }

    fun requestNotificationIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.setNotificationsEnabled(true)
        }
    }

    fun requestExactAlarmIfNeeded(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !WaterNotificationHelper.canScheduleExactAlarms(context)
        ) {
            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
            })
            return false
        }
        return true
    }

    fun saveSettings() {
        val alarmEnabled = enabled && WaterNotificationHelper.canScheduleExactAlarms(context)
        val updatedProfile = profile.copy(
            waterReminderEnabled = enabled,
            waterAlarmRemindersEnabled = alarmEnabled,
            waterReminderMode = mode,
            waterReminderIntervalMinutes = intervalMinutes.coerceAtLeast(15),
            waterReminderTimesPerDay = timesPerDay.coerceIn(1, 12),
            waterReminderDailyTimeMinute = dailyTimeMinute,
            waterReminderWeeklyDay = weeklyDay,
            waterReminderWindowStartMinute = windowStart,
            waterReminderWindowEndMinute = windowEnd
        )
        viewModel.saveWaterReminderSettings(
            enabled = enabled,
            alarmEnabled = alarmEnabled,
            mode = mode,
            intervalMinutes = intervalMinutes,
            timesPerDay = timesPerDay,
            dailyTimeMinute = dailyTimeMinute,
            weeklyDay = weeklyDay,
            windowStartMinute = windowStart,
            windowEndMinute = windowEnd
        )
        if (enabled) {
            WaterNotificationHelper.scheduleReminders(context, updatedProfile)
        } else {
            WaterNotificationHelper.cancelReminders(context)
        }
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Drink Water Reminder", style = Typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    Switch(
                        checked = enabled,
                        onCheckedChange = { checked ->
                            enabled = checked
                            if (checked) {
                                requestNotificationIfNeeded()
                                requestExactAlarmIfNeeded()
                            }
                        }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)
            )
        },
        bottomBar = {
            Button(
                onClick = { saveSettings() },
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = OnSurface, contentColor = Background)
            ) { Text("SAVE", fontWeight = FontWeight.Bold) }
        },
        containerColor = Background
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Text("Get reminded to drink water", style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = OnSurface)
            Spacer(Modifier.height(8.dp))
            Text(
                "Water reminders help you meet your hydration goal of a minimum of $goalGlasses glasses (${WaterGoalCalculator.formatLiters(goalGlasses * WaterGoalCalculator.ML_PER_GLASS)}L) a day.",
                style = Typography.bodyMedium,
                color = OnSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))

            ReminderTimeRow(
                label = "From ${DietDateUtils.formatTimeFromMinute(windowStart)} to ${DietDateUtils.formatTimeFromMinute(windowEnd)}",
                enabled = enabled,
                onStartClick = { if (enabled) showStartTimePicker = true },
                onEndClick = { if (enabled) showEndTimePicker = true }
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = OutlineVariant.copy(0.3f))

            ReminderOptionRow(
                label = WaterReminderModes.label(WaterReminderModes.INTERVAL),
                value = formatInterval(intervalMinutes),
                selected = mode == WaterReminderModes.INTERVAL,
                enabled = enabled,
                onSelect = { mode = WaterReminderModes.INTERVAL },
                onValueClick = { showIntervalPicker = true }
            )
            ReminderOptionRow(
                label = WaterReminderModes.label(WaterReminderModes.TIMES),
                value = "$timesPerDay Times",
                selected = mode == WaterReminderModes.TIMES,
                enabled = enabled,
                onSelect = { mode = WaterReminderModes.TIMES },
                onValueClick = { showTimesPicker = true }
            )
            ReminderOptionRow(
                label = WaterReminderModes.label(WaterReminderModes.DAILY),
                value = DietDateUtils.formatTimeFromMinute(dailyTimeMinute),
                selected = mode == WaterReminderModes.DAILY,
                enabled = enabled,
                onSelect = { mode = WaterReminderModes.DAILY },
                onValueClick = { showDailyTimePicker = true }
            )
            ReminderOptionRow(
                label = WaterReminderModes.label(WaterReminderModes.WEEKLY),
                value = weekDayLabel(weeklyDay),
                selected = mode == WaterReminderModes.WEEKLY,
                enabled = enabled,
                onSelect = { mode = WaterReminderModes.WEEKLY },
                onValueClick = { showWeeklyPicker = true }
            )

            if (enabled && !WaterNotificationHelper.canScheduleExactAlarms(context)) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Allow Alarms & reminders for precise scheduling. Without it, basic notifications will be used.",
                    style = Typography.bodySmall,
                    color = Tertiary
                )
                TextButton(onClick = { requestExactAlarmIfNeeded() }) {
                    Text("Open alarm settings")
                }
            }
        }
    }

    if (showIntervalPicker) {
        NumberWheelDialog(
            title = "Set your time intervals",
            subtitle = "At what time intervals should we remind you to drink water to meet $goalGlasses glasses a day?",
            values = listOf(15, 30, 45, 60, 90, 120, 180, 240),
            selected = intervalMinutes,
            suffix = if (intervalMinutes == 60 || intervalMinutes == 120 || intervalMinutes == 180 || intervalMinutes == 240) "Hours" else "Minutes",
            formatValue = { formatInterval(it) },
            onDismiss = { showIntervalPicker = false },
            onSave = { intervalMinutes = it; showIntervalPicker = false }
        )
    }
    if (showTimesPicker) {
        NumberWheelDialog(
            title = "Set the number of reminders",
            subtitle = "How many times a day should we remind you to drink water to meet $goalGlasses glasses a day?",
            values = (1..12).toList(),
            selected = timesPerDay,
            suffix = "Times",
            formatValue = { "$it Times" },
            onDismiss = { showTimesPicker = false },
            onSave = { timesPerDay = it; showTimesPicker = false }
        )
    }
    if (showDailyTimePicker) {
        TimePickerDialog(
            minuteOfDay = dailyTimeMinute,
            onDismiss = { showDailyTimePicker = false },
            onSave = { dailyTimeMinute = it; showDailyTimePicker = false }
        )
    }
    if (showStartTimePicker) {
        TimePickerDialog(
            minuteOfDay = windowStart,
            onDismiss = { showStartTimePicker = false },
            onSave = { windowStart = it; showStartTimePicker = false }
        )
    }
    if (showEndTimePicker) {
        TimePickerDialog(
            minuteOfDay = windowEnd,
            onDismiss = { showEndTimePicker = false },
            onSave = { windowEnd = it; showEndTimePicker = false }
        )
    }
    if (showWeeklyPicker) {
        WeekDayPickerDialog(
            selectedDay = weeklyDay,
            onDismiss = { showWeeklyPicker = false },
            onSave = { weeklyDay = it; showWeeklyPicker = false }
        )
    }
}

@Composable
private fun ReminderTimeRow(
    label: String,
    enabled: Boolean,
    onStartClick: () -> Unit,
    onEndClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = Typography.bodyLarge, color = if (enabled) OnSurface else OnSurfaceVariant.copy(0.5f))
    }
}

@Composable
private fun ReminderOptionRow(
    label: String,
    value: String,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    onValueClick: () -> Unit
) {
    val active = enabled && selected
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(label, style = Typography.bodyLarge, color = if (enabled) OnSurface else OnSurfaceVariant.copy(0.5f), modifier = Modifier.weight(1f))
        TextButton(onClick = onValueClick, enabled = active) {
            Text(value, fontWeight = FontWeight.Bold, color = if (active) Primary else OnSurfaceVariant)
        }
    }
}

@Composable
private fun NumberWheelDialog(
    title: String,
    subtitle: String,
    values: List<Int>,
    selected: Int,
    suffix: String,
    formatValue: (Int) -> String,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    var current by remember { mutableIntStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(subtitle, style = Typography.bodySmall, color = OnSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        values.forEach { v ->
                            Text(
                                formatValue(v),
                                style = if (v == current) Typography.titleLarge else Typography.bodyMedium,
                                color = if (v == current) OnSurface else OnSurfaceVariant.copy(0.4f),
                                modifier = Modifier
                                    .padding(vertical = 4.dp)
                                    .then(Modifier)
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(suffix, style = Typography.titleMedium, color = OnSurfaceVariant)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    values.forEach { v ->
                        TextButton(onClick = { current = v }) { Text(v.toString()) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(current) }) { Text("SAVE") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    minuteOfDay: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    GymTimePickerDialog(
        minuteOfDay = minuteOfDay,
        onDismiss = onDismiss,
        onConfirm = onSave
    )
}

@Composable
private fun WeekDayPickerDialog(
    selectedDay: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    val days = listOf(
        Calendar.SUNDAY to "Sunday",
        Calendar.MONDAY to "Monday",
        Calendar.TUESDAY to "Tuesday",
        Calendar.WEDNESDAY to "Wednesday",
        Calendar.THURSDAY to "Thursday",
        Calendar.FRIDAY to "Friday",
        Calendar.SATURDAY to "Saturday"
    )
    var current by remember { mutableIntStateOf(selectedDay) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remind me every week on") },
        text = {
            Column {
                days.forEach { (day, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = current == day, onClick = { current = day })
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = current == day, onClick = null)
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(current) }) { Text("SAVE") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } }
    )
}

private fun formatInterval(minutes: Int): String = when {
    minutes % 60 == 0 && minutes >= 60 -> "${minutes / 60} Hour${if (minutes / 60 > 1) "s" else ""}"
    else -> "$minutes Minutes"
}

private fun weekDayLabel(day: Int): String =
    java.text.SimpleDateFormat("EEEE", java.util.Locale.getDefault()).format(
        Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, day) }.time
    )
