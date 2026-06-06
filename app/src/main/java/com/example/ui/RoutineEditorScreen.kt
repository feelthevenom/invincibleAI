package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.GymViewModel
import com.example.data.RoutineExercise
import com.example.data.WorkoutRoutine
import com.example.ui.theme.*

@Composable
fun RoutineEditorScreen(
    routine: WorkoutRoutine,
    viewModel: GymViewModel,
    onBack: () -> Unit
) {
    var title by remember(routine.id) { mutableStateOf(routine.name) }
    var exercises by remember { mutableStateOf<List<RoutineExercise>>(emptyList()) }
    var showAddExercise by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(routine.id) {
        exercises = viewModel.loadRoutineExercisesOnce(routine.id)
    }

    BackHandler { onBack() }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Primary)
            }
            Text("Edit Routine", style = Typography.titleLarge, color = OnSurface)
        }

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Routine title") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Exercises", style = Typography.titleMedium, color = OnSurfaceVariant)
            TextButton(onClick = { showAddExercise = true }) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add")
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(exercises, key = { _, ex -> "${ex.id}_${ex.exerciseName}_${ex.sortOrder}" }) { index, ex ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(SurfaceContainerHighest.copy(0.3f), RoundedCornerShape(12.dp))
                        .border(1.dp, OutlineVariant.copy(0.2f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(ex.exerciseName, style = Typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                        Text(
                            "${ex.exerciseType} · ${ex.defaultSets}×${ex.defaultReps}",
                            style = Typography.bodySmall,
                            color = OnSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = {
                            if (index > 0) exercises = viewModel.moveRoutineExercise(exercises, index, index - 1)
                        },
                        enabled = index > 0
                    ) { Icon(Icons.Default.KeyboardArrowUp, null) }
                    IconButton(
                        onClick = {
                            if (index < exercises.lastIndex) {
                                exercises = viewModel.moveRoutineExercise(exercises, index, index + 1)
                            }
                        },
                        enabled = index < exercises.lastIndex
                    ) { Icon(Icons.Default.KeyboardArrowDown, null) }
                    IconButton(onClick = { exercises = exercises.filterIndexed { i, _ -> i != index } }) {
                        Icon(Icons.Default.Delete, null, tint = Error)
                    }
                }
            }
        }

        Button(
            onClick = {
                if (title.isNotBlank()) {
                    viewModel.renameRoutine(routine, title)
                    viewModel.saveRoutineExercises(routine.id, exercises)
                    onBack()
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp).padding(vertical = 8.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Save Routine")
        }

        if (!routine.isBuiltIn) {
            OutlinedButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Error)
            ) {
                Icon(Icons.Default.Delete, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Delete Routine")
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Routine?") },
            text = { Text("Remove \"${routine.name}\" permanently? Workout history for this routine name is kept.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteRoutineAndReturn(routine, onBack)
                }) { Text("Delete", color = Error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showAddExercise) {
        ExerciseSearchOverlay(
            dayLabel = routine.name,
            routine = routine.name,
            logDayStart = com.example.data.DietDateUtils.startOfTodayMillis(),
            viewModel = viewModel,
            onDismiss = { showAddExercise = false },
            pickOnly = true,
            onExercisePicked = { item ->
                exercises = exercises + RoutineExercise(
                    routineId = routine.id,
                    exerciseName = item.name,
                    exerciseType = item.exerciseType,
                    equipment = item.equipment,
                    sortOrder = exercises.size,
                    defaultSets = item.defaultSets,
                    defaultReps = item.defaultReps,
                    isCardio = item.isCardio
                )
                showAddExercise = false
            }
        )
    }
}

@Composable
fun CreateRoutineDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Routine") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Routine title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onCreate(name.trim()) },
                enabled = name.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
