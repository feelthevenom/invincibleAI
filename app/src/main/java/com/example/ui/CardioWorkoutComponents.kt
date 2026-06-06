package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ExerciseSet
import com.example.data.UserProfile
import com.example.data.WorkoutExerciseGroup
import com.example.data.WorkoutExerciseKind
import com.example.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun CardioExerciseCard(
    group: WorkoutExerciseGroup,
    profile: UserProfile,
    aiEnabled: Boolean,
    onToggleSet: (ExerciseSet) -> Unit,
    onUpdateSet: (ExerciseSet) -> Unit,
    onRemoveExercise: () -> Unit,
    onEstimateCalories: (ExerciseSet) -> Unit,
    onExerciseNameClick: () -> Unit,
    onSwitchToWeights: () -> Unit = {}
) {
    val set = group.sets.firstOrNull() ?: return
    val isCompleted = set.isCompleted
    var isEditing by remember(set.id) { mutableStateOf(!isCompleted) }
    var showRemoveConfirm by remember { mutableStateOf(false) }
    var showSwitchConfirm by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var timerRunning by remember { mutableStateOf(false) }
    var elapsed by remember(set.id, set.durationSeconds) { mutableIntStateOf(set.durationSeconds) }
    var durationInput by remember(set.id, set.durationSeconds) {
        mutableStateOf(WorkoutExerciseKind.formatDuration(set.durationSeconds))
    }
    var caloriesInput by remember(set.id, set.caloriesBurned) {
        mutableStateOf(if (set.caloriesBurned > 0) set.caloriesBurned.toString() else "")
    }

    LaunchedEffect(isCompleted) {
        if (isCompleted) timerRunning = false
    }

    LaunchedEffect(set.durationSeconds, set.caloriesBurned) {
        if (!timerRunning) {
            elapsed = set.durationSeconds
            durationInput = WorkoutExerciseKind.formatDuration(set.durationSeconds)
            caloriesInput = if (set.caloriesBurned > 0) set.caloriesBurned.toString() else ""
        }
    }

    LaunchedEffect(timerRunning, isEditing) {
        if (!timerRunning || !isEditing) return@LaunchedEffect
        while (timerRunning) {
            delay(1000)
            elapsed++
            durationInput = WorkoutExerciseKind.formatDuration(elapsed)
            onUpdateSet(set.copy(durationSeconds = elapsed, isCompleted = false))
        }
    }

    val showControls = isEditing || !isCompleted
    val showSummary = isCompleted && !isEditing
    val borderColor = if (isCompleted) Secondary else OutlineVariant.copy(0.3f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceContainerHighest.copy(0.3f), RoundedCornerShape(12.dp))
            .border(if (isCompleted) 2.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    group.exerciseName,
                    style = Typography.headlineMedium.copy(fontSize = 18.sp),
                    color = Primary,
                    modifier = Modifier.clickable(onClick = onExerciseNameClick)
                )
                Surface(shape = RoundedCornerShape(4.dp), color = Primary.copy(0.12f)) {
                    Text(
                        if (isCompleted) "CARDIO · COMPLETE" else "CARDIO",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = Typography.labelMedium.copy(fontSize = 10.sp),
                        color = if (isCompleted) Secondary else Primary
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isCompleted && !isEditing) {
                    IconButton(onClick = { isEditing = true }) {
                        Icon(Icons.Default.Edit, "Edit", tint = Primary)
                    }
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreHoriz, null, tint = OnSurfaceVariant.copy(0.4f))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Cardio", color = Primary) },
                            onClick = { showMenu = false },
                            leadingIcon = { Icon(Icons.Default.CheckCircle, null, tint = Primary) },
                            enabled = false
                        )
                        DropdownMenuItem(
                            text = { Text("Weights") },
                            onClick = {
                                showMenu = false
                                showSwitchConfirm = true
                            },
                            leadingIcon = { Icon(Icons.Default.FitnessCenter, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Remove", color = Error) },
                            onClick = { showMenu = false; showRemoveConfirm = true },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = Error) }
                        )
                    }
                }
            }
        }

        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                WorkoutExerciseKind.formatDuration(elapsed),
                style = Typography.displayMedium.copy(fontSize = 36.sp),
                color = if (isCompleted) Secondary else OnSurface,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            if (showControls) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { timerRunning = !timerRunning },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            if (timerRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                            null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (timerRunning) "Stop" else "Start")
                    }
                    OutlinedButton(
                        onClick = {
                            timerRunning = false
                            elapsed = 0
                            durationInput = "0:00"
                            onUpdateSet(set.copy(durationSeconds = 0, caloriesBurned = 0, isCompleted = false))
                            caloriesInput = ""
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Reset")
                    }
                }

                OutlinedTextField(
                    value = durationInput,
                    onValueChange = { v ->
                        durationInput = v
                        val seconds = WorkoutExerciseKind.parseDurationInput(v)
                        elapsed = seconds
                        onUpdateSet(set.copy(durationSeconds = seconds, isCompleted = false))
                    },
                    label = { Text("Duration (mm:ss or minutes)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = caloriesInput,
                        onValueChange = { v ->
                            if (v.isEmpty() || v.all { it.isDigit() }) {
                                caloriesInput = v
                                onUpdateSet(set.copy(caloriesBurned = v.toIntOrNull() ?: 0, isCompleted = false))
                            }
                        },
                        label = { Text("Calories burned") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedButton(
                        onClick = { onEstimateCalories(set.copy(durationSeconds = elapsed)) },
                        enabled = elapsed > 0,
                        modifier = Modifier.align(Alignment.CenterVertically),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (aiEnabled) "AI" else "Est.")
                    }
                }

                if (profile.currentWeight <= 0f || profile.age <= 0) {
                    Text(
                        "Add age and weight in profile for better calorie estimates.",
                        style = Typography.labelSmall,
                        color = OnSurfaceVariant
                    )
                }
            } else if (showSummary) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Duration", style = Typography.labelSmall, color = OnSurfaceVariant)
                        Text(durationInput, style = Typography.titleMedium, color = OnSurface)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Calories", style = Typography.labelSmall, color = OnSurfaceVariant)
                        Text(
                            caloriesInput.ifBlank { "—" },
                            style = Typography.titleMedium,
                            color = OnSurface
                        )
                    }
                }
            }

            when {
                showControls && !isCompleted -> {
                    Button(
                        onClick = {
                            timerRunning = false
                            onUpdateSet(
                                set.copy(
                                    durationSeconds = elapsed,
                                    caloriesBurned = caloriesInput.toIntOrNull() ?: set.caloriesBurned,
                                    isCompleted = true
                                )
                            )
                            isEditing = false
                        },
                        enabled = elapsed > 0,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Mark as Finish")
                    }
                }
                showControls && isCompleted -> {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                isEditing = false
                                timerRunning = false
                                elapsed = set.durationSeconds
                                durationInput = WorkoutExerciseKind.formatDuration(set.durationSeconds)
                                caloriesInput = if (set.caloriesBurned > 0) set.caloriesBurned.toString() else ""
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                timerRunning = false
                                onUpdateSet(
                                    set.copy(
                                        durationSeconds = elapsed,
                                        caloriesBurned = caloriesInput.toIntOrNull() ?: 0,
                                        isCompleted = true
                                    )
                                )
                                isEditing = false
                            },
                            enabled = elapsed > 0,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = { Text("Remove Exercise?") },
            text = { Text("Remove ${group.exerciseName} from this workout?") },
            confirmButton = {
                TextButton(onClick = {
                    showRemoveConfirm = false
                    onRemoveExercise()
                }) { Text("Remove", color = Error) }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showSwitchConfirm) {
        TrackingModeSwitchDialog(
            targetMode = "weight tracking",
            onConfirm = {
                showSwitchConfirm = false
                onSwitchToWeights()
            },
            onDismiss = { showSwitchConfirm = false }
        )
    }
}

@Composable
fun TrackingModeSwitchDialog(
    targetMode: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Switch tracking mode?") },
        text = {
            Text(
                "Switching to $targetMode will erase the tracked data from the current mode " +
                    "(timer, duration, calories, sets, or weights). This cannot be undone."
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Yes, switch")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
