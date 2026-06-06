package com.example.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.DayWorkoutLog
import com.example.GymViewModel
import com.example.SuggestedRoutine
import com.example.WorkoutFinishSummary
import com.example.data.DietDateUtils
import com.example.data.ExerciseSet
import com.example.data.UserProfile
import com.example.data.WorkoutExerciseGroup
import com.example.data.WorkoutExerciseKind
import com.example.data.WorkoutGrouping
import com.example.data.WorkoutRoutine
import com.example.ui.theme.*

private enum class WorkoutDateTab { Yesterday, Today, Calendar }

@Composable
fun WorkoutTab(
    viewModel: GymViewModel,
    onOpenWorkoutReminder: () -> Unit = {},
    onNavigateToCoach: () -> Unit = {},
    aiEnabled: Boolean = false
) {
    val sets by viewModel.allSets.collectAsState()
    val profile by viewModel.userProfile.collectAsState()
    val routines by viewModel.allRoutines.collectAsState()
    val activeSession by viewModel.activeSession.collectAsState()
    val pendingSummary by viewModel.pendingFinishSummary.collectAsState()
    val suggestedRoutine by viewModel.suggestedRoutine.collectAsState()
    val modelsRevision by viewModel.modelsRevision.collectAsState()

    WorkoutTabContent(
        sets = sets,
        profile = profile,
        routines = routines,
        activeSession = activeSession,
        pendingSummary = pendingSummary,
        suggestedRoutine = suggestedRoutine,
        modelsRevision = modelsRevision,
        onOpenWorkoutReminder = onOpenWorkoutReminder,
        viewModel = viewModel,
        onNavigateToCoach = onNavigateToCoach,
        aiEnabled = aiEnabled
    )
}

@Composable
fun WorkoutTabContent(
    sets: List<ExerciseSet> = emptyList(),
    profile: UserProfile = UserProfile(),
    routines: List<WorkoutRoutine> = emptyList(),
    activeSession: com.example.ActiveWorkoutSession? = null,
    pendingSummary: WorkoutFinishSummary? = null,
    suggestedRoutine: SuggestedRoutine? = null,
    modelsRevision: Int = 0,
    onOpenWorkoutReminder: () -> Unit = {},
    viewModel: GymViewModel? = null,
    onNavigateToCoach: () -> Unit = {},
    aiEnabled: Boolean = false
) {
    val context = LocalContext.current
    var dateTab by remember { mutableStateOf(WorkoutDateTab.Today) }
    var selectedDayStart by remember { mutableLongStateOf(DietDateUtils.startOfTodayMillis()) }
    var showCalendar by remember { mutableStateOf(false) }
    var showExerciseSearch by remember { mutableStateOf(false) }
    var showCreateRoutine by remember { mutableStateOf(false) }
    var editingRoutine by remember { mutableStateOf<WorkoutRoutine?>(null) }
    var selectedRoutine by remember { mutableStateOf<WorkoutRoutine?>(null) }

    LaunchedEffect(dateTab) {
        selectedDayStart = when (dateTab) {
            WorkoutDateTab.Yesterday -> DietDateUtils.startOfYesterdayMillis()
            WorkoutDateTab.Today -> DietDateUtils.startOfTodayMillis()
            WorkoutDateTab.Calendar -> selectedDayStart
        }
    }

    LaunchedEffect(selectedDayStart, routines.size, modelsRevision, viewModel) {
        viewModel?.refreshRoutineSuggestion(selectedDayStart)
    }

    val session = activeSession
    val isFutureDay = DietDateUtils.isFuture(selectedDayStart)
    val daySummaries = remember(sets) { DietDateUtils.summarizeWorkoutDays(sets) }
    val dayWorkoutLogs = remember(sets, selectedDayStart, routines) {
        viewModel?.workoutLogsForDay(selectedDayStart, sets, routines).orEmpty()
    }

    LaunchedEffect(selectedDayStart) {
        selectedRoutine = null
    }

    LaunchedEffect(selectedDayStart, dayWorkoutLogs, routines) {
        if (dayWorkoutLogs.size == 1) {
            val log = dayWorkoutLogs.first()
            selectedRoutine = log.routineId?.let { id -> routines.find { it.id == id } }
                ?: routines.find { it.name.equals(log.routineName, ignoreCase = true) }
        }
    }

    DisposableEffect(session, editingRoutine, pendingSummary) {
        val handlesBack = session != null || editingRoutine != null || pendingSummary != null
        viewModel?.setWorkoutHandlesBack(handlesBack)
        onDispose { viewModel?.setWorkoutHandlesBack(false) }
    }

    BackHandler(enabled = editingRoutine != null) {
        editingRoutine = null
    }

    BackHandler(enabled = pendingSummary != null) {
        viewModel?.dismissFinishSummary()
    }

    BackHandler(enabled = session != null && pendingSummary == null) {
        viewModel?.endActiveSession()
    }

    if (editingRoutine != null && viewModel != null) {
        RoutineEditorScreen(
            routine = editingRoutine!!,
            viewModel = viewModel,
            onBack = { editingRoutine = null }
        )
        return
    }

    if (session != null && viewModel != null) {
        var tutorialExercise by remember { mutableStateOf<String?>(null) }
        ActiveWorkoutScreen(
            session = session,
            sets = sets,
            viewModel = viewModel,
            pendingSummary = pendingSummary,
            onBack = { viewModel.endActiveSession() },
            onShowExerciseSearch = { showExerciseSearch = true },
            onExerciseNameClick = { tutorialExercise = it }
        )
        ActiveWorkoutExerciseTutorialHost(
            viewModel = viewModel,
            aiEnabled = aiEnabled,
            selectedExercise = tutorialExercise,
            onDismiss = { tutorialExercise = null }
        )
        BackHandler(enabled = tutorialExercise != null) {
            tutorialExercise = null
            viewModel.clearExerciseGuideState()
        }
        if (showExerciseSearch) {
            ExerciseSearchOverlay(
                dayLabel = session.dayLabel,
                routine = session.routineName,
                logDayStart = session.dayStart,
                viewModel = viewModel,
                onDismiss = { showExerciseSearch = false }
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .padding(bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DateTabsRow(
            dateTab = dateTab,
            onTab = { dateTab = it },
            onCalendar = {
                dateTab = WorkoutDateTab.Calendar
                showCalendar = true
            }
        )

        if (dateTab == WorkoutDateTab.Calendar && !DietDateUtils.isToday(selectedDayStart)) {
            Text(
                DietDateUtils.formatDisplayDate(selectedDayStart),
                style = Typography.bodySmall,
                color = if (isFutureDay) OnSurfaceVariant.copy(0.5f) else Error.copy(0.85f)
            )
        }

        if (isFutureDay) {
            Text(
                "You can't log entries for future dates.",
                style = Typography.bodyMedium,
                color = OnSurfaceVariant.copy(0.7f),
                modifier = Modifier.padding(vertical = 24.dp)
            )
        } else {
            suggestedRoutine?.let { suggestion ->
                SuggestedWorkoutCard(
                    suggestion = suggestion,
                    onStart = { viewModel?.startRoutineWorkout(suggestion.routine, selectedDayStart) },
                    onSetReminder = onOpenWorkoutReminder
                )
            }

            Spacer(Modifier.height(8.dp))
            Text("Routines", style = Typography.titleMedium, color = OnSurface)

            RoutineDropdown(
                routines = routines,
                selected = selectedRoutine,
                onSelected = { selectedRoutine = it },
                onEdit = { editingRoutine = it }
            )

            Button(
                onClick = { selectedRoutine?.let { viewModel?.startRoutineWorkout(it, selectedDayStart) } },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = selectedRoutine != null && viewModel != null
            ) {
                Text("Start Routine", style = Typography.labelMedium)
            }

            Button(
                onClick = { viewModel?.startEmptyWorkout(selectedDayStart) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SurfaceContainerHigh,
                    contentColor = Primary
                ),
                enabled = viewModel != null
            ) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Start Empty Workout", style = Typography.labelMedium)
            }

            OutlinedButton(
                onClick = { showCreateRoutine = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = viewModel != null
            ) {
                Icon(Icons.Default.Create, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("New Routine", style = Typography.labelMedium)
            }

            if (dayWorkoutLogs.isNotEmpty()) {
                Text(
                    if (DietDateUtils.isToday(selectedDayStart)) "Today's Workout" else "Workout Log",
                    style = Typography.titleMedium,
                    color = OnSurface,
                    modifier = Modifier.padding(top = 8.dp)
                )
                dayWorkoutLogs.forEach { log ->
                    DayWorkoutLogCard(
                        log = log,
                        routines = routines,
                        onContinue = {
                            viewModel?.resumeDayWorkout(log.routineName, log.dayStart, log.routineId)
                        },
                        onEditRoutine = { routine ->
                            editingRoutine = routine
                        },
                        onDelete = {
                            viewModel?.deleteDayWorkoutLog(log)
                        }
                    )
                }
            }
        }
    }

    if (showCalendar) {
        DietCalendarDialog(
            selectedDayStart = selectedDayStart,
            daySummaries = daySummaries,
            onDismiss = { showCalendar = false },
            onDaySelected = { day ->
                selectedDayStart = day
                dateTab = WorkoutDateTab.Calendar
                showCalendar = false
            },
            onFutureDayBlocked = {
                Toast.makeText(context, "You can't log entries for future dates.", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showCreateRoutine && viewModel != null) {
        CreateRoutineDialog(
            onDismiss = { showCreateRoutine = false },
            onCreate = { name ->
                viewModel.createRoutine(name) { id ->
                    editingRoutine = WorkoutRoutine(id = id, name = name)
                }
                showCreateRoutine = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutineDropdown(
    routines: List<WorkoutRoutine>,
    selected: WorkoutRoutine?,
    onSelected: (WorkoutRoutine) -> Unit,
    onEdit: (WorkoutRoutine) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.name ?: "Select a routine",
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Primary,
                unfocusedBorderColor = OutlineVariant.copy(0.4f)
            )
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (routines.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No routines yet", color = OnSurfaceVariant) },
                    onClick = { expanded = false },
                    enabled = false
                )
            } else {
                routines.forEach { routine ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(routine.name, fontWeight = FontWeight.Medium)
                                if (routine.isBuiltIn) {
                                    Text("Built-in", style = Typography.labelSmall, color = OnSurfaceVariant)
                                }
                            }
                        },
                        onClick = {
                            onSelected(routine)
                            expanded = false
                        },
                        trailingIcon = {
                            IconButton(onClick = { onEdit(routine); expanded = false }) {
                                Icon(Icons.Default.Edit, null, Modifier.size(18.dp), tint = OnSurfaceVariant)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AiRoutineSuggestionCard(
    suggestion: SuggestedRoutine,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
    onChange: (WorkoutRoutine) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(PrimaryContainer.copy(0.25f), RoundedCornerShape(12.dp))
            .border(1.dp, Primary.copy(0.35f), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, null, tint = Primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Suggested for you", style = Typography.labelMedium, color = Primary)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, "Dismiss", tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
            Text(suggestion.routine.name, style = Typography.titleLarge, color = OnSurface)
            Text(suggestion.reason, style = Typography.bodySmall, color = OnSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onStart, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                    Text("Start")
                }
                OutlinedButton(
                    onClick = {
                        onChange(suggestion.routine)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Change")
                }
            }
        }
    }
}

@Composable
private fun ActiveWorkoutScreen(
    session: com.example.ActiveWorkoutSession,
    sets: List<ExerciseSet>,
    viewModel: GymViewModel,
    pendingSummary: WorkoutFinishSummary?,
    onBack: () -> Unit,
    onShowExerciseSearch: () -> Unit,
    onExerciseNameClick: (String) -> Unit = {}
) {
    val daySets = remember(sets, session) {
        val end = DietDateUtils.endOfDayMillis(session.dayStart)
        sets.filter {
            it.timestamp >= session.dayStart && it.timestamp < end &&
                it.workoutDayLabel == session.dayLabel
        }
    }
    val exerciseGroups = remember(daySets) { WorkoutGrouping.groupSets(daySets) }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .padding(bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Primary)
                }
                Column {
                    Text(session.routineName, style = Typography.headlineMedium, color = OnSurface)
                    Text("Active workout", style = Typography.bodySmall, color = Primary)
                }
            }

            exerciseGroups.forEach { group ->
                val maxWeight = viewModel.maxWeightForExercise(group.exerciseName)
                when (WorkoutExerciseKind.kindFor(group)) {
                    WorkoutExerciseKind.Kind.CARDIO -> {
                        val profile by viewModel.userProfile.collectAsState()
                        CardioExerciseCard(
                            group = group,
                            profile = profile,
                            aiEnabled = viewModel.isAiConfigured(),
                            onToggleSet = { viewModel.toggleSetCompleted(it) },
                            onUpdateSet = { viewModel.updateSet(it) },
                            onRemoveExercise = { viewModel.removeExerciseFromDay(group, session.dayStart) },
                            onEstimateCalories = { viewModel.estimateCardioCalories(it) },
                            onExerciseNameClick = { onExerciseNameClick(group.exerciseName) },
                            onSwitchToWeights = {
                                viewModel.switchExerciseTrackingMode(group, isCardio = false, session.dayStart)
                            }
                        )
                    }
                    else -> WorkoutExerciseCard(
                        group = group,
                        maxWeightKg = maxWeight,
                        hideWeight = WorkoutExerciseKind.kindFor(group) == WorkoutExerciseKind.Kind.BODYWEIGHT,
                        canEdit = true,
                        onToggleSet = { viewModel.toggleSetCompleted(it) },
                        onUpdateSet = { viewModel.updateSet(it) },
                        onRemoveSet = { viewModel.deleteSet(it) },
                        onRemoveExercise = { viewModel.removeExerciseFromDay(group, session.dayStart) },
                        onAddSet = {
                            viewModel.updateExerciseSetCount(group, group.sets.size + 1, session.dayStart)
                        },
                        onSaveGroupEdit = { setCount, weight, reps ->
                            viewModel.editExerciseGroup(group, setCount, weight, reps, session.dayStart)
                        },
                        onExerciseNameClick = { onExerciseNameClick(group.exerciseName) },
                        onSwitchToCardio = {
                            viewModel.switchExerciseTrackingMode(group, isCardio = true, session.dayStart)
                        }
                    )
                }
            }

            if (exerciseGroups.isEmpty()) {
                Text(
                    "No exercises yet. Add your first exercise below.",
                    style = Typography.bodyMedium,
                    color = OnSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }

            Button(
                onClick = onShowExerciseSearch,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SurfaceContainerHigh,
                    contentColor = Primary
                )
            ) {
                Text("+ ADD EXERCISE", style = Typography.labelMedium)
            }
        }

        if (pendingSummary == null) {
            Button(
                onClick = { viewModel.finishWorkoutRequest(daySets) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Text("Finish Workout", style = Typography.labelLarge, color = OnPrimary)
            }
        }
    }

    pendingSummary?.let { summary ->
        WorkoutFinishSummaryDialog(
            summary = summary,
            onConfirm = { viewModel.confirmFinishWorkout() },
            onDiscard = { viewModel.discardWorkout(daySets) },
            onDismiss = { viewModel.dismissFinishSummary() }
        )
    }
}

@Composable
private fun DayWorkoutLogCard(
    log: DayWorkoutLog,
    routines: List<WorkoutRoutine>,
    onContinue: () -> Unit,
    onEditRoutine: (WorkoutRoutine) -> Unit,
    onDelete: () -> Unit
) {
    val matchedRoutine = log.routineId?.let { id -> routines.find { it.id == id } }
        ?: routines.find { it.name.equals(log.routineName, ignoreCase = true) }
    val exercisePreview = log.exerciseGroups.take(3).joinToString(", ") { it.exerciseName }
    val borderColor = if (log.isFullyCompleted) Secondary else OutlineVariant.copy(0.3f)
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Workout?") },
            text = {
                Text("Remove \"${log.routineName}\" and all logged sets for this day? This cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    }
                ) {
                    Text("Delete", color = Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(SurfaceContainerHighest.copy(0.3f), RoundedCornerShape(12.dp))
            .border(
                width = if (log.isFullyCompleted) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(log.routineName, style = Typography.titleMedium, color = OnSurface, modifier = Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (log.isFullyCompleted) {
                    Icon(Icons.Default.CheckCircle, null, tint = Secondary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                }
                IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, "Delete workout", tint = Error.copy(0.85f), modifier = Modifier.size(20.dp))
                }
            }
        }
        Text(
            "${log.exerciseGroups.size} exercises · ${log.completedSets}/${log.totalSets} sets",
            style = Typography.bodySmall,
            color = OnSurfaceVariant
        )
        if (exercisePreview.isNotBlank()) {
            Text(
                exercisePreview + if (log.exerciseGroups.size > 3) "…" else "",
                style = Typography.labelMedium,
                color = OnSurfaceVariant.copy(0.7f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onContinue,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(if (log.isFullyCompleted) "View Workout" else "Continue Workout")
            }
            if (matchedRoutine != null) {
                OutlinedButton(
                    onClick = { onEditRoutine(matchedRoutine) },
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Edit")
                }
            }
        }
    }
}

@Composable
private fun DateTabsRow(
    dateTab: WorkoutDateTab,
    onTab: (WorkoutDateTab) -> Unit,
    onCalendar: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceContainerLow, RoundedCornerShape(12.dp))
            .border(1.dp, OutlineVariant.copy(0.2f), RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(WorkoutDateTab.Yesterday to "Yesterday", WorkoutDateTab.Today to "Today").forEach { (tab, label) ->
            val selected = dateTab == tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) PrimaryContainer else Color.Transparent)
                    .clickable { onTab(tab) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = Typography.bodyMedium.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    ),
                    color = if (selected) OnPrimaryContainer else OnSurfaceVariant.copy(0.6f)
                )
            }
        }
        IconButton(
            onClick = onCalendar,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(if (dateTab == WorkoutDateTab.Calendar) PrimaryContainer else Color.Transparent)
        ) {
            Icon(
                Icons.Default.CalendarMonth,
                contentDescription = "Calendar",
                tint = if (dateTab == WorkoutDateTab.Calendar) OnPrimaryContainer else OnSurfaceVariant.copy(0.6f)
            )
        }
    }
}

@Composable
fun WorkoutExerciseCard(
    group: WorkoutExerciseGroup,
    maxWeightKg: Float = 0f,
    hideWeight: Boolean = false,
    canEdit: Boolean = true,
    onToggleSet: (ExerciseSet) -> Unit,
    onUpdateSet: (ExerciseSet) -> Unit,
    onRemoveSet: (ExerciseSet) -> Unit,
    onRemoveExercise: () -> Unit,
    onAddSet: () -> Unit,
    onSaveGroupEdit: (Int, Float, Int) -> Unit,
    onExerciseNameClick: () -> Unit = {},
    onSwitchToCardio: () -> Unit = {}
) {
    var isEditing by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showRemoveConfirm by remember { mutableStateOf(false) }
    var showSwitchConfirm by remember { mutableStateOf(false) }

    val borderColor = if (group.allCompleted) Secondary else OutlineVariant.copy(0.3f)
    val borderWidth = if (group.allCompleted) 2.dp else 1.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceContainerHighest.copy(0.3f), RoundedCornerShape(12.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(12.dp))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        group.exerciseName,
                        style = Typography.headlineMedium.copy(fontSize = 18.sp),
                        color = Primary,
                        modifier = Modifier.clickable(onClick = onExerciseNameClick)
                    )
                    if (maxWeightKg > 0f) {
                        Text(
                            "Max ${formatWeightDisplay(maxWeightKg)} kg",
                            style = Typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Secondary
                        )
                    }
                }
                Box(
                    Modifier.background(SurfaceContainerHighest, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(group.exerciseType.uppercase(), style = Typography.labelMedium.copy(fontSize = 10.sp), color = OnSurfaceVariant)
                }
            }
            Row {
                if (canEdit && !isEditing) {
                    TextButton(onClick = { isEditing = true }) { Text("Edit") }
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreHoriz, null, tint = OnSurfaceVariant.copy(0.4f))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Cardio") },
                            onClick = {
                                showMenu = false
                                showSwitchConfirm = true
                            },
                            leadingIcon = { Icon(Icons.Default.Timer, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Weights", color = Primary) },
                            onClick = { showMenu = false },
                            leadingIcon = { Icon(Icons.Default.CheckCircle, null, tint = Primary) },
                            enabled = false
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

        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                if (isEditing) Spacer(Modifier.width(28.dp))
                HeaderCell("SET", if (hideWeight) 0.7f else 0.5f)
                if (!hideWeight) HeaderCell("WEIGHT (KG)", 1f)
                HeaderCell(if (hideWeight) "REPS" else "REPS", if (hideWeight) 1.3f else 1f)
                HeaderCell("DONE", 0.5f)
            }

            group.sets.forEach { set ->
                WorkoutSetRow(
                    set = set,
                    isEditing = isEditing,
                    hideWeight = hideWeight,
                    onToggleDone = { onToggleSet(set) },
                    onWeightChange = { w -> onUpdateSet(set.copy(weight = w)) },
                    onRepsChange = { r -> onUpdateSet(set.copy(reps = r)) },
                    onRemoveSet = { onRemoveSet(set) }
                )
                Spacer(Modifier.height(8.dp))
            }

            if (isEditing) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onAddSet, modifier = Modifier.fillMaxWidth()) {
                    Text("+ Add Set")
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            onSaveGroupEdit(
                                group.sets.size,
                                group.sets.firstOrNull()?.weight ?: 0f,
                                group.sets.firstOrNull()?.reps ?: 10
                            )
                            isEditing = false
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Done") }
                    OutlinedButton(onClick = { isEditing = false }, modifier = Modifier.weight(1f)) {
                        Text("Cancel")
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
            targetMode = "cardio (timer & calories)",
            onConfirm = {
                showSwitchConfirm = false
                onSwitchToCardio()
            },
            onDismiss = { showSwitchConfirm = false }
        )
    }
}

@Composable
private fun RowScope.HeaderCell(text: String, columnWeight: Float) {
    Text(
        text,
        style = Typography.labelMedium.copy(fontSize = 10.sp),
        color = OnSurfaceVariant.copy(0.6f),
        modifier = Modifier.weight(columnWeight),
        textAlign = TextAlign.Center
    )
}

@Composable
fun WorkoutSetRow(
    set: ExerciseSet,
    isEditing: Boolean = false,
    hideWeight: Boolean = false,
    onToggleDone: () -> Unit,
    onWeightChange: (Float) -> Unit,
    onRepsChange: (Int) -> Unit,
    onRemoveSet: () -> Unit = {}
) {
    var weightText by remember(set.id, set.weight) { mutableStateOf(formatWeightDisplay(set.weight)) }
    var repsText by remember(set.id, set.reps) { mutableStateOf(set.reps.toString()) }
    val rowAlpha = if (set.isCompleted && !isEditing) 0.45f else 1f

    Row(
        Modifier.fillMaxWidth().alpha(rowAlpha),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isEditing) {
            IconButton(onClick = onRemoveSet, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, null, tint = Error, modifier = Modifier.size(16.dp))
            }
        }
        Text(
            "${set.setNumber}",
            style = Typography.displayMedium.copy(fontSize = 14.sp),
            color = OnSurfaceVariant.copy(0.4f),
            modifier = Modifier.weight(if (hideWeight) 0.7f else 0.5f),
            textAlign = TextAlign.Center
        )
        if (isEditing) {
            if (!hideWeight) {
                EditableSetField(
                    value = weightText,
                    onValueChange = { v ->
                        if (v.isEmpty() || v.matches(Regex("^\\d*\\.?\\d*$"))) {
                            weightText = v
                            v.toFloatOrNull()?.let { onWeightChange(it) }
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            EditableSetField(
                value = repsText,
                onValueChange = { v ->
                    if (v.all { it.isDigit() } && v.length <= 3) {
                        repsText = v
                        v.toIntOrNull()?.let { onRepsChange(it) }
                    }
                },
                modifier = Modifier.weight(if (hideWeight) 1.3f else 1f),
                keyboardType = KeyboardType.Number
            )
        } else {
            if (!hideWeight) {
                StaticSetCell(formatWeightDisplay(set.weight).ifEmpty { "—" }, Modifier.weight(1f), PrimaryFixed)
            }
            StaticSetCell(set.reps.toString(), Modifier.weight(if (hideWeight) 1.3f else 1f), OnSurface)
        }
        Box(Modifier.weight(0.5f), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(
                        if (set.isCompleted) Secondary else SurfaceContainerHighest,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable(onClick = onToggleDone),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    null,
                    tint = if (set.isCompleted) OnSecondary else OnSurfaceVariant.copy(0.2f)
                )
            }
        }
    }
}

@Composable
private fun StaticSetCell(text: String, modifier: Modifier, color: Color) {
    Box(modifier.padding(horizontal = 4.dp).height(40.dp), contentAlignment = Alignment.Center) {
        Text(text, style = Typography.displayMedium.copy(fontSize = 16.sp), color = color)
    }
}

@Composable
private fun EditableSetField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Decimal
) {
    Box(
        modifier.padding(horizontal = 4.dp).height(40.dp)
            .background(SurfaceContainerLow, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = Typography.displayMedium.copy(
                fontSize = 16.sp,
                color = PrimaryFixed,
                textAlign = TextAlign.Center
            ),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            cursorBrush = SolidColor(Primary),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
        )
    }
}

private fun formatWeightDisplay(w: Float): String =
    if (w == 0f) "" else if (w == w.toLong().toFloat()) w.toLong().toString() else w.toString()

@Preview(showBackground = true)
@Composable
fun WorkoutTabPreview() {
    MyApplicationTheme {
        WorkoutTabContent()
    }
}
