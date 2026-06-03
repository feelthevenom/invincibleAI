package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ExerciseSearchUiState
import com.example.GymViewModel
import com.example.data.CustomExercise
import com.example.data.ExerciseItem
import com.example.data.WorkoutExerciseGroup
import com.example.ui.theme.*
import kotlinx.coroutines.launch

private val EXERCISE_TYPES = listOf(
    "Chest", "Back", "Shoulders", "Biceps", "Triceps",
    "Legs", "Glutes", "Core", "Full Body", "Cardio"
)

private sealed class ExerciseSheetPage {
    data object Search : ExerciseSheetPage()
    data class Custom(val prefilledName: String = "") : ExerciseSheetPage()
}

@Composable
fun ExerciseSearchOverlay(
    dayLabel: String,
    routine: String,
    logDayStart: Long,
    viewModel: GymViewModel,
    onDismiss: () -> Unit
) {
    var page by remember { mutableStateOf<ExerciseSheetPage>(ExerciseSheetPage.Search) }
    val searchState by viewModel.exerciseSearchState.collectAsState()
    val customExercises by viewModel.customExercises.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(routine) {
        viewModel.loadExerciseSuggestions(routine)
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.clearExerciseSearch() }
    }

    BackHandler {
        when (page) {
            is ExerciseSheetPage.Custom -> page = ExerciseSheetPage.Search
            else -> onDismiss()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 48.dp),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = Surface
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        when (page) {
                            is ExerciseSheetPage.Custom -> page = ExerciseSheetPage.Search
                            else -> onDismiss()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Primary)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            when (page) {
                                is ExerciseSheetPage.Custom -> "Create Exercise"
                                else -> "Add Exercise"
                            },
                            style = Typography.titleLarge,
                            color = OnSurface
                        )
                        Text(dayLabel, style = Typography.bodySmall, color = OnSurfaceVariant)
                    }
                }

                HorizontalDivider(color = OutlineVariant.copy(0.2f))

                when (val current = page) {
                    ExerciseSheetPage.Search -> ExerciseSearchPage(
                        searchState = searchState,
                        customExercises = customExercises,
                        routine = routine,
                        onQueryChange = { viewModel.onExerciseSearchQueryChanged(it, routine) },
                        onCreateCustom = {
                            page = ExerciseSheetPage.Custom(searchState.query.trim())
                        },
                        onExerciseSelected = { exercise ->
                            viewModel.addExerciseToDay(exercise, dayLabel, logDayStart)
                            onDismiss()
                        },
                        viewModel = viewModel
                    )
                    is ExerciseSheetPage.Custom -> CustomExerciseCreatePage(
                        viewModel = viewModel,
                        initialName = current.prefilledName,
                        dayLabel = dayLabel,
                        logDayStart = logDayStart,
                        onSaved = { onDismiss() }
                    )
                }
            }
        }
    }
}

@Composable
private fun ExerciseSearchPage(
    searchState: ExerciseSearchUiState,
    customExercises: List<CustomExercise>,
    routine: String,
    onQueryChange: (String) -> Unit,
    onCreateCustom: () -> Unit,
    onExerciseSelected: (ExerciseItem) -> Unit,
    viewModel: GymViewModel
) {
    var exerciseToDelete by remember { mutableStateOf<CustomExercise?>(null) }
    var aiResults by remember { mutableStateOf<List<ExerciseItem>>(emptyList()) }
    var isAiLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchState.query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            placeholder = { Text("Search exercises… e.g. bench press, curl", color = OnSurfaceVariant) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Primary) },
            trailingIcon = {
                if (searchState.query.isNotBlank() && viewModel.isAiConfigured()) {
                    IconButton(
                        onClick = {
                            isAiLoading = true
                            scope.launch {
                                aiResults = viewModel.generateExerciseSuggestions(
                                    searchState.query.trim(),
                                    routine
                                )
                                isAiLoading = false
                            }
                        },
                        enabled = !isAiLoading
                    ) {
                        if (isAiLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Primary
                            )
                        } else {
                            Icon(Icons.Default.AutoAwesome, null, tint = Primary)
                        }
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Primary,
                unfocusedBorderColor = OutlineVariant.copy(0.4f),
                focusedTextColor = OnSurface,
                unfocusedTextColor = OnSurface
            )
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "55+ exercises · Push/Pull/Legs · Tap title to add",
            style = Typography.labelMedium,
            color = OnSurfaceVariant.copy(0.6f),
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(8.dp))

        if (searchState.isLoading) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "custom_create") {
                val query = searchState.query.trim()
                ExerciseListItem(
                    name = "Create Custom Exercise",
                    subtitle = if (query.isNotBlank()) {
                        "Add \"$query\" — name pre-filled, edit or use AI"
                    } else {
                        "Add your own exercise with muscle type"
                    },
                    icon = Icons.Default.Create,
                    highlight = true,
                    onClick = onCreateCustom
                )
            }

            if (customExercises.isNotEmpty()) {
                item(key = "custom_header") {
                    WorkoutSectionHeader("YOUR CUSTOM EXERCISES")
                }
                items(customExercises, key = { "custom_${it.id}" }) { ce ->
                    ExerciseListItem(
                        name = ce.name,
                        subtitle = "${ce.exerciseType} · ${ce.defaultSets} sets × ${ce.defaultReps} reps",
                        icon = Icons.Default.Delete,
                        onClick = {
                            onExerciseSelected(
                                ExerciseItem(
                                    id = "custom_${ce.id}",
                                    name = ce.name,
                                    exerciseType = ce.exerciseType,
                                    defaultSets = ce.defaultSets,
                                    defaultReps = ce.defaultReps,
                                    isCustom = true
                                )
                            )
                        },
                        onIconClick = { exerciseToDelete = ce }
                    )
                }
            }

            if (aiResults.isNotEmpty()) {
                item(key = "ai_header") {
                    WorkoutSectionHeader("AI SUGGESTIONS")
                }
                items(aiResults, key = { it.id }) { exercise ->
                    ExerciseListItem(
                        name = exercise.name,
                        subtitle = "${exercise.exerciseType} · ${exercise.defaultSets}×${exercise.defaultReps} · AI",
                        onClick = { onExerciseSelected(exercise) }
                    )
                }
            }

            val localLabel = if (searchState.query.isBlank()) "SUGGESTED FOR $routine" else "SEARCH RESULTS"
            if (searchState.localResults.isNotEmpty()) {
                item(key = "local_header") {
                    WorkoutSectionHeader(localLabel)
                }
                items(searchState.localResults, key = { it.id }) { exercise ->
                    ExerciseListItem(
                        name = exercise.name,
                        subtitle = "${exercise.exerciseType} · ${exercise.defaultSets} sets × ${exercise.defaultReps} reps",
                        onClick = { onExerciseSelected(exercise) }
                    )
                }
            }
        }
    }

    if (exerciseToDelete != null) {
        AlertDialog(
            onDismissRequest = { exerciseToDelete = null },
            title = { Text("Delete Custom Exercise?") },
            text = { Text("Remove '${exerciseToDelete?.name}' from your custom exercises?") },
            confirmButton = {
                TextButton(onClick = {
                    exerciseToDelete?.let { viewModel.deleteCustomExercise(it) }
                    exerciseToDelete = null
                }) { Text("Delete", color = Error) }
            },
            dismissButton = {
                TextButton(onClick = { exerciseToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomExerciseCreatePage(
    viewModel: GymViewModel,
    initialName: String,
    dayLabel: String,
    logDayStart: Long,
    onSaved: () -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var exerciseType by remember { mutableStateOf("Chest") }
    var sets by remember { mutableStateOf("3") }
    var reps by remember { mutableStateOf("10") }
    var weight by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var isAiLoading by remember { mutableStateOf(false) }
    var aiSuggestions by remember { mutableStateOf<List<ExerciseItem>>(emptyList()) }
    var showAiPicker by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val routine = remember(dayLabel) {
        when {
            dayLabel.contains("Push", ignoreCase = true) -> "Push"
            dayLabel.contains("Pull", ignoreCase = true) -> "Pull"
            dayLabel.contains("Leg", ignoreCase = true) -> "Legs"
            dayLabel.contains("Upper", ignoreCase = true) -> "Upper"
            dayLabel.contains("Lower", ignoreCase = true) -> "Lower"
            else -> "Full Body"
        }
    }

    fun triggerAiAutofill() {
        if (name.isBlank() || !viewModel.isAiConfigured()) return
        isAiLoading = true
        scope.launch {
            val suggestions = viewModel.generateExerciseSuggestions(name.trim(), routine)
            if (suggestions.isNotEmpty()) {
                aiSuggestions = suggestions
                showAiPicker = true
            }
            isAiLoading = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Exercise Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = exerciseType,
                onValueChange = {},
                readOnly = true,
                label = { Text("Muscle Type") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                EXERCISE_TYPES.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(type) },
                        onClick = { exerciseType = type; expanded = false }
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = sets,
                onValueChange = { if (it.all { c -> c.isDigit() }) sets = it },
                label = { Text("Sets") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = reps,
                onValueChange = { if (it.all { c -> c.isDigit() }) reps = it },
                label = { Text("Reps") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
        }

        OutlinedTextField(
            value = weight,
            onValueChange = { v ->
                if (v.isEmpty() || v.matches(Regex("^\\d*\\.?\\d*$"))) weight = v
            },
            label = { Text("Default Weight (kg)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        if (viewModel.isAiConfigured()) {
            Button(
                onClick = { triggerAiAutofill() },
                enabled = name.isNotBlank() && !isAiLoading,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryContainer, contentColor = OnPrimaryContainer)
            ) {
                if (isAiLoading) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                } else {
                    Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text("AI Autofill")
            }
        }

        Button(
            onClick = {
                if (name.isBlank()) return@Button
                val entity = CustomExercise(
                    name = name.trim(),
                    exerciseType = exerciseType,
                    defaultSets = sets.toIntOrNull()?.coerceIn(1, 20) ?: 3,
                    defaultReps = reps.toIntOrNull()?.coerceIn(1, 100) ?: 10,
                    defaultWeight = weight.toFloatOrNull() ?: 0f
                )
                viewModel.addCustomExerciseAndReturn(entity, dayLabel, logDayStart) { onSaved() }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = name.isNotBlank()
        ) {
            Text("Save & Add to Workout")
        }
    }

    if (showAiPicker && aiSuggestions.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showAiPicker = false },
            title = { Text("AI Suggestions") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    aiSuggestions.forEach { item ->
                        TextButton(
                            onClick = {
                                name = item.name
                                exerciseType = item.exerciseType
                                sets = item.defaultSets.toString()
                                reps = item.defaultReps.toString()
                                showAiPicker = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "${item.name} (${item.exerciseType}) · ${item.defaultSets}×${item.defaultReps}",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAiPicker = false }) { Text("Close") }
            }
        )
    }
}

@Composable
fun EditExerciseDialog(
    group: WorkoutExerciseGroup,
    onDismiss: () -> Unit,
    onSave: (setCount: Int, weight: Float, reps: Int) -> Unit,
    onRemove: () -> Unit
) {
    var sets by remember { mutableStateOf(group.sets.size.toString()) }
    var weight by remember {
        mutableStateOf(group.sets.firstOrNull()?.weight?.let { formatWeight(it) } ?: "")
    }
    var reps by remember {
        mutableStateOf(group.sets.firstOrNull()?.reps?.toString() ?: "10")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${group.exerciseName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = sets,
                    onValueChange = { if (it.all { c -> c.isDigit() }) sets = it },
                    label = { Text("Number of Sets") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = weight,
                    onValueChange = { v ->
                        if (v.isEmpty() || v.matches(Regex("^\\d*\\.?\\d*$"))) weight = v
                    },
                    label = { Text("Weight (kg)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = reps,
                    onValueChange = { if (it.all { c -> c.isDigit() }) reps = it },
                    label = { Text("Reps") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    sets.toIntOrNull()?.coerceIn(1, 20) ?: group.sets.size,
                    weight.toFloatOrNull() ?: 0f,
                    reps.toIntOrNull()?.coerceIn(1, 100) ?: 10
                )
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onRemove) { Text("Remove", color = Error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
private fun ExerciseListItem(
    name: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    highlight: Boolean = false,
    onClick: () -> Unit,
    onIconClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (highlight) PrimaryContainer.copy(0.35f) else SurfaceContainerHighest.copy(0.3f),
                RoundedCornerShape(12.dp)
            )
            .border(1.dp, OutlineVariant.copy(0.2f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = Typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = OnSurface)
            Text(subtitle, style = Typography.bodySmall, color = OnSurfaceVariant)
        }
        if (icon != null && onIconClick != null) {
            IconButton(onClick = onIconClick) {
                Icon(icon, null, tint = Error.copy(0.8f))
            }
        }
    }
}

@Composable
private fun WorkoutSectionHeader(text: String) {
    Text(
        text,
        style = Typography.labelMedium,
        color = OnSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )
}

private fun formatWeight(w: Float): String =
    if (w == w.toLong().toFloat()) w.toLong().toString() else w.toString()
