package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.GymViewModel
import com.example.notifications.WorkoutNotificationHelper
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutReminderScreen(
    viewModel: GymViewModel,
    onBack: () -> Unit
) {
    val profile by viewModel.userProfile.collectAsState()
    val context = LocalContext.current

    val initialMinute = profile.workoutReminderTimeMinute.coerceIn(0, 23 * 60 + 59)
    var hour12 by remember(profile.workoutReminderTimeMinute) {
        mutableIntStateOf(((initialMinute / 60) % 12).let { if (it == 0) 12 else it })
    }
    var minute by remember(profile.workoutReminderTimeMinute) {
        mutableIntStateOf(initialMinute % 60)
    }
    var isAm by remember(profile.workoutReminderTimeMinute) {
        mutableStateOf(initialMinute / 60 < 12)
    }
    var repeatDaily by remember(profile.workoutReminderRepeat) {
        mutableStateOf(profile.workoutReminderRepeat)
    }

    fun resolvedMinuteOfDay(): Int {
        var h24 = hour12 % 12
        if (!isAm) h24 += 12
        if (isAm && hour12 == 12) h24 = 0
        if (!isAm && hour12 == 12) h24 = 12
        return h24 * 60 + minute
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("SET REMINDER", style = Typography.titleMedium, color = Primary, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = OnSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)
            )
        },
        containerColor = Background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceContainer, RoundedCornerShape(16.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.AccessTime, null, tint = Primary, modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(8.dp))
                Text("CONSISTENCY IS KEY", style = Typography.labelMedium, color = Primary)
            }

            Text("SELECT TIME", style = Typography.labelMedium, color = OnSurfaceVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceContainer, RoundedCornerShape(16.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                InlineTimeWheelPicker(
                    hour12 = hour12,
                    minute = minute,
                    onHourChange = { hour12 = it },
                    onMinuteChange = { minute = it },
                    modifier = Modifier.weight(1f)
                )
                Column(
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .background(SurfaceContainerHigh, RoundedCornerShape(10.dp))
                        .padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(true to "AM", false to "PM").forEach { (am, label) ->
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isAm == am) Primary else androidx.compose.ui.graphics.Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { isAm = am }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                color = if (isAm == am) OnPrimary else OnSurfaceVariant,
                                style = Typography.labelMedium
                            )
                        }
                    }
                }
            }

            Text("OCCURRENCE", style = Typography.labelMedium, color = OnSurfaceVariant)
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

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val minuteOfDay = resolvedMinuteOfDay()
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
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = OnPrimary)
            ) {
                Text("Save Reminder", fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel", color = OnSurfaceVariant)
            }
        }
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
    Column(
        modifier = modifier
            .background(SurfaceContainer, RoundedCornerShape(14.dp))
            .border(
                1.dp,
                if (selected) Primary else OutlineVariant.copy(0.3f),
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, null, tint = if (selected) Primary else OnSurfaceVariant)
        Text(label, style = Typography.labelMedium, color = if (selected) Primary else OnSurfaceVariant)
    }
}
