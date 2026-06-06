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
    onDismiss: () -> Unit,
    pickOnly: Boolean = false,
    onExercisePicked: ((ExerciseItem) -> Unit)? = null
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
            color = MaterialTheme.colorScheme.surface
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            when (page) {
                                is ExerciseSheetPage.Custom -> "Create Exercise"
                                else -> "Add Exercise"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(dayLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(0.2f))

                when (val current = page) {
                    ExerciseSheetPage.Search -> ExerciseSearchPage(
                        searchState = searchState,
                        customExercises = customExercises,
                        routine = routine,
                        aiEnabled = viewModel.isAiConfigured(),
                        onQueryChange = { viewModel.onExerciseSearchQueryChanged(it, routine) },
                        onCreateCustom = {
                            page = ExerciseSheetPage.Custom(searchState.query.trim())
                        },
                        onGenerateAi = { viewModel.generateAiExercisesForRoutine(routine) },
                        onExerciseSelected = { exercise ->
                            if (pickOnly && onExercisePicked != null) {
                                onExercisePicked(exercise)
                            } else {
                                viewModel.addExerciseToDay(exercise, dayLabel, logDayStart)
                                onDismiss()
                            }
                        },
                        viewModel = viewModel
                    )
                    is ExerciseSheetPage.Custom -> CustomExerciseCreatePage(
                        viewModel = viewModel,
                        initialName = current.prefilledName,
                        dayLabel = dayLabel,
                        logDayStart = logDayStart,
                        pickOnly = pickOnly,
                        onPicked = onExercisePicked,
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
    aiEnabled: Boolean,
    onQueryChange: (String) -> Unit,
    onCreateCustom: () -> Unit,
    onGenerateAi: () -> Unit,
    onExerciseSelected: (ExerciseItem) -> Unit,
    viewModel: GymViewModel
) {
    var exerciseToDelete by remember { mutableStateOf<CustomExercise?>(null) }
    val isCardioRoutine = routine.equals("Cardio", ignoreCase = true)
    var aiDraftNames by remember(searchState.aiSuggestions) {
        mutableStateOf(searchState.aiSuggestions.associate { it.id to it.name })
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchState.query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            placeholder = { Text("Search exercises… e.g. bench press, curl") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors = m3OutlinedTextFieldColors()
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "55+ exercises · Push/Pull/Legs · Tap title to add",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f),
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(8.dp))

        if (searchState.isLoading) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                GymLoadingIndicator()
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "custom_create") {
                val query = searchState.query.trim()
                ExerciseListItem(
                    name = "Create Custom Exercise",
                    subtitle = if (query.isNotBlank()) {
                        "Add \"$query\" — name pre-filled"
                    } else {
                        "Add your own exercise with muscle type"
                    },
                    icon = Icons.Default.Create,
                    highlight = true,
                    onClick = onCreateCustom
                )
            }

            item(key = "ai_generate") {
                OutlinedButton(
                    onClick = onGenerateAi,
                    enabled = aiEnabled && !searchState.aiLoading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (searchState.aiLoading) {
                        GymLoadingIndicatorSmall(modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Generating…")
                    } else {
                        Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("AI Suggest Exercises (up to 5)")
                    }
                }
                searchState.aiError?.let { err ->
                    Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                }
            }

            if (isCardioRoutine && searchState.suggestions.isNotEmpty() && searchState.query.isBlank()) {
                item(key = "cardio_header") {
                    WorkoutSectionHeader("CARDIO EXERCISES")
                }
                items(
                    searchState.suggestions.filter { it.isCardio || it.exerciseType.equals("Cardio", true) },
                    key = { "cardio_${it.id}" }
                ) { exercise ->
                    ExerciseListItem(
                        name = exercise.name,
                        subtitle = "Cardio · timer & calories",
                        onClick = { onExerciseSelected(exercise.copy(isCardio = true)) }
                    )
                }
            }

            if (customExercises.isNotEmpty()) {
                item(key = "custom_header") {
                    WorkoutSectionHeader("YOUR CUSTOM EXERCISES")
                }
                items(customExercises, key = { "custom_${it.id}" }) { ce ->
                    ExerciseListItem(
                        name = ce.name,
                        subtitle = if (ce.isCardio) "Cardio · timer & calories" else "${ce.exerciseType} · ${ce.defaultSets} sets × ${ce.defaultReps} reps",
                        icon = Icons.Default.Delete,
                        onClick = {
                            onExerciseSelected(
                                ExerciseItem(
                                    id = "custom_${ce.id}",
                                    name = ce.name,
                                    exerciseType = if (ce.isCardio) "Cardio" else ce.exerciseType,
                                    defaultSets = if (ce.isCardio) 1 else ce.defaultSets,
                                    defaultReps = if (ce.isCardio) 0 else ce.defaultReps,
                                    isCustom = true,
                                    isCardio = ce.isCardio
                                )
                            )
                        },
                        onIconClick = { exerciseToDelete = ce }
                    )
                }
            }

            val localLabel = if (searchState.query.isBlank()) "SUGGESTED FOR $routine" else "SEARCH RESULTS"
            if (searchState.localResults.isNotEmpty() && !(isCardioRoutine && searchState.query.isBlank())) {
                item(key = "local_header") {
                    WorkoutSectionHeader(localLabel)
                }
                items(searchState.localResults, key = { it.id }) { exercise ->
                    val subtitle = if (exercise.isCardio || exercise.exerciseType.equals("Cardio", true)) {
                        "Cardio · timer & calories"
                    } else {
                        "${exercise.exerciseType} · ${exercise.defaultSets} sets × ${exercise.defaultReps} reps"
                    }
                    ExerciseListItem(
                        name = exercise.name,
                        subtitle = subtitle,
                        onClick = { onExerciseSelected(exercise) }
                    )
                }
            }

            if (searchState.aiSuggestions.isNotEmpty()) {
                item(key = "ai_header") {
                    WorkoutSectionHeader("AI SUGGESTIONS · tap Add or edit name")
                }
                items(searchState.aiSuggestions, key = { it.id }) { exercise ->
                    AiExerciseSuggestionCard(
                        exercise = exercise,
                        name = aiDraftNames[exercise.id] ?: exercise.name,
                        onNameChange = { aiDraftNames = aiDraftNames + (exercise.id to it) },
                        onAdd = {
                            val finalName = (aiDraftNames[exercise.id] ?: exercise.name).trim()
                            if (finalName.isBlank()) return@AiExerciseSuggestionCard
                            onExerciseSelected(exercise.copy(name = finalName))
                        }
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
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
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
    pickOnly: Boolean = false,
    onPicked: ((ExerciseItem) -> Unit)? = null,
    onSaved: () -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var isCardio by remember { mutableStateOf(false) }
    var exerciseType by remember { mutableStateOf("Chest") }
    var sets by remember { mutableStateOf("3") }
    var reps by remember { mutableStateOf("10") }
    var weight by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

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

        Text("Cardio exercise", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = isCardio, onClick = { isCardio = true })
                Text("On", color = MaterialTheme.colorScheme.onSurface)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = !isCardio, onClick = { isCardio = false })
                Text("Off", color = MaterialTheme.colorScheme.onSurface)
            }
        }
        if (isCardio) {
            Text(
                "Cardio uses a timer and calories — no sets, reps, or weight.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (!isCardio) {
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
                    EXERCISE_TYPES.filter { !it.equals("Cardio", ignoreCase = true) }.forEach { type ->
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
        }

        Button(
            onClick = {
                if (name.isBlank()) return@Button
                val entity = CustomExercise(
                    name = name.trim(),
                    exerciseType = if (isCardio) "Cardio" else exerciseType,
                    defaultSets = if (isCardio) 1 else sets.toIntOrNull()?.coerceIn(1, 20) ?: 3,
                    defaultReps = if (isCardio) 0 else reps.toIntOrNull()?.coerceIn(1, 100) ?: 10,
                    defaultWeight = if (isCardio) 0f else weight.toFloatOrNull() ?: 0f,
                    isCardio = isCardio
                )
                viewModel.addCustomExerciseAndReturn(
                    exercise = entity,
                    dayLabel = dayLabel,
                    logDayStart = logDayStart,
                    addToWorkout = !pickOnly
                ) { item ->
                    if (pickOnly && onPicked != null) {
                        onPicked(item)
                    }
                    onSaved()
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = name.isNotBlank()
        ) {
            Text(if (pickOnly) "Save & Add to Routine" else "Save & Add to Workout")
        }
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
                TextButton(onClick = onRemove) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
private fun AiExerciseSuggestionCard(
    exercise: ExerciseItem,
    name: String,
    onNameChange: (String) -> Unit,
    onAdd: () -> Unit
) {
    val isCardio = exercise.isCardio || exercise.exerciseType.equals("Cardio", ignoreCase = true)
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(cs.surfaceContainerHighest.copy(0.35f), RoundedCornerShape(12.dp))
            .border(1.dp, cs.primary.copy(0.25f), RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Exercise name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors = m3OutlinedTextFieldColors()
        )
        Text(
            if (isCardio) {
                "Cardio · timer & calories"
            } else {
                "${exercise.exerciseType} · ${exercise.defaultSets} sets × ${exercise.defaultReps} reps"
            },
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant
        )
        Button(
            onClick = onAdd,
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add to ${if (isCardio) "cardio" else "workout"}")
        }
    }
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
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (highlight) cs.primaryContainer.copy(0.35f) else cs.surfaceContainerHighest.copy(0.3f),
                RoundedCornerShape(12.dp)
            )
            .border(1.dp, cs.outlineVariant.copy(0.2f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = cs.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        if (icon != null && onIconClick != null) {
            IconButton(onClick = onIconClick) {
                Icon(icon, null, tint = cs.error.copy(0.8f))
            }
        }
    }
}

@Composable
private fun WorkoutSectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )
}

private fun formatWeight(w: Float): String =
    if (w == w.toLong().toFloat()) w.toLong().toString() else w.toString()
