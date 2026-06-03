package com.example.ui

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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.GymViewModel
import com.example.data.DietDateUtils
import com.example.data.ExerciseSet
import com.example.data.UserProfile
import com.example.data.WorkoutExerciseGroup
import com.example.data.WorkoutGrouping
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Locale

private enum class WorkoutDateTab { Yesterday, Today, Calendar }

@Composable
fun WorkoutTab(viewModel: GymViewModel) {
    val sets by viewModel.allSets.collectAsState()
    val profile by viewModel.userProfile.collectAsState()
    WorkoutTabContent(sets = sets, profile = profile, viewModel = viewModel)
}

@Composable
fun WorkoutTabContent(
    sets: List<ExerciseSet> = emptyList(),
    profile: UserProfile = UserProfile(),
    viewModel: GymViewModel? = null
) {
    var dateTab by remember { mutableStateOf(WorkoutDateTab.Today) }
    var selectedDayStart by remember { mutableLongStateOf(DietDateUtils.startOfTodayMillis()) }
    var showCalendar by remember { mutableStateOf(false) }
    var showExerciseSearch by remember { mutableStateOf(false) }
    var groupToEdit by remember { mutableStateOf<WorkoutExerciseGroup?>(null) }

    LaunchedEffect(dateTab) {
        selectedDayStart = when (dateTab) {
            WorkoutDateTab.Yesterday -> DietDateUtils.startOfYesterdayMillis()
            WorkoutDateTab.Today -> DietDateUtils.startOfTodayMillis()
            WorkoutDateTab.Calendar -> selectedDayStart
        }
    }

    val plan = remember(selectedDayStart, profile.workoutDaysPerWeek, profile.goal) {
        viewModel?.workoutPlanForDay(selectedDayStart)
            ?: com.example.data.WorkoutSplitGenerator.planForDayIndex(
                0,
                profile.workoutDaysPerWeek.coerceAtLeast(1),
                profile.goal.ifBlank { "General Fitness" }
            )
    }

    val daySets = remember(sets, selectedDayStart, plan.label) {
        val end = DietDateUtils.endOfDayMillis(selectedDayStart)
        sets.filter {
            it.timestamp >= selectedDayStart && it.timestamp < end &&
                it.workoutDayLabel == plan.label
        }
    }

    val exerciseGroups = remember(daySets) { WorkoutGrouping.groupSets(daySets) }

    LaunchedEffect(selectedDayStart, plan.label, plan.routine, daySets.isEmpty()) {
        if (viewModel != null && daySets.isEmpty()) {
            viewModel.seedDefaultExercisesIfEmpty(plan.label, plan.routine, selectedDayStart)
        }
    }

    val daySummaries = remember(sets) { DietDateUtils.summarizeWorkoutDays(sets) }
    val isToday = DietDateUtils.isToday(selectedDayStart)
    val sessionNum = remember(sets, selectedDayStart) {
        viewModel?.sessionNumberForDay(sets, selectedDayStart) ?: 1
    }
    val dateLabel = remember(selectedDayStart) {
        SimpleDateFormat("MMM d", Locale.getDefault()).format(selectedDayStart).uppercase()
    }

    val firstIncompleteGroup = exerciseGroups.firstOrNull { !it.allCompleted }
    val tipExercise = firstIncompleteGroup?.sets?.firstOrNull()
    val overloadTip = remember(tipExercise, sets) {
        tipExercise?.let { set ->
            viewModel?.progressiveOverloadTip(set.exerciseName, set.weight, set.reps)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .padding(bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Date tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceContainerLow, RoundedCornerShape(12.dp))
                .border(1.dp, OutlineVariant.copy(0.2f), RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabModifier = @Composable { tab: WorkoutDateTab, label: String ->
                val isSelected = dateTab == tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) PrimaryContainer else Color.Transparent)
                        .clickable { dateTab = tab }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        style = Typography.bodyMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = if (isSelected) OnPrimaryContainer else OnSurfaceVariant.copy(0.6f)
                    )
                }
            }
            tabModifier(WorkoutDateTab.Yesterday, "Yesterday")
            tabModifier(WorkoutDateTab.Today, "Today")
            IconButton(
                onClick = {
                    dateTab = WorkoutDateTab.Calendar
                    showCalendar = true
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (dateTab == WorkoutDateTab.Calendar) PrimaryContainer
                        else Color.Transparent
                    )
            ) {
                Icon(
                    Icons.Default.CalendarMonth,
                    contentDescription = "Calendar",
                    tint = if (dateTab == WorkoutDateTab.Calendar) OnPrimaryContainer
                    else OnSurfaceVariant.copy(0.6f)
                )
            }
        }

        if (dateTab == WorkoutDateTab.Calendar && !isToday) {
            Text(
                DietDateUtils.formatDisplayDate(selectedDayStart),
                style = Typography.bodySmall,
                color = Error.copy(0.85f),
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        // Header — clickable title
        Column {
            Text(
                plan.label,
                style = Typography.headlineLarge.copy(fontSize = 28.sp),
                color = OnSurface,
                modifier = Modifier.clickable { showExerciseSearch = true }
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CalendarToday,
                    null,
                    modifier = Modifier.size(14.dp),
                    tint = OnSurfaceVariant.copy(0.6f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "$dateLabel • SESSION #$sessionNum",
                    style = Typography.labelMedium,
                    color = OnSurfaceVariant.copy(0.6f)
                )
            }
            Text(
                plan.focus,
                style = Typography.bodySmall,
                color = OnSurfaceVariant.copy(0.5f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // AI tip
        if (overloadTip != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceContainerHighest.copy(0.3f), RoundedCornerShape(12.dp))
                    .border(2.dp, Primary, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.AutoAwesome, null, tint = Primary)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "AI PROGRESSIVE OVERLOAD TIP",
                            style = Typography.labelMedium,
                            color = Primary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(overloadTip, style = Typography.bodySmall, color = OnSurface)
                    }
                }
            }
        }

        // Exercise cards
        exerciseGroups.forEach { group ->
            WorkoutExerciseCard(
                group = group,
                onToggleSet = { set -> viewModel?.toggleSetCompleted(set) },
                onUpdateSet = { set -> viewModel?.updateSet(set) },
                onMenuEdit = { groupToEdit = group },
                onMenuRemove = { viewModel?.removeExerciseFromDay(group, selectedDayStart) },
                onAddSet = {
                    viewModel?.updateExerciseSetCount(
                        group,
                        group.sets.size + 1,
                        selectedDayStart
                    )
                }
            )
        }

        if (exerciseGroups.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Primary, modifier = Modifier.size(32.dp))
            }
        }

        Button(
            onClick = { showExerciseSearch = true },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SurfaceContainerHigh,
                contentColor = Primary
            )
        ) {
            Text("+ ADD EXERCISE", style = Typography.labelMedium)
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
            }
        )
    }

    if (showExerciseSearch && viewModel != null) {
        ExerciseSearchOverlay(
            dayLabel = plan.label,
            routine = plan.routine,
            logDayStart = selectedDayStart,
            viewModel = viewModel,
            onDismiss = { showExerciseSearch = false }
        )
    }

    groupToEdit?.let { group ->
        EditExerciseDialog(
            group = group,
            onDismiss = { groupToEdit = null },
            onSave = { setCount, weight, reps ->
                viewModel?.editExerciseGroup(group, setCount, weight, reps, selectedDayStart)
                groupToEdit = null
            },
            onRemove = {
                viewModel?.removeExerciseFromDay(group, selectedDayStart)
                groupToEdit = null
            }
        )
    }
}

@Composable
fun WorkoutExerciseCard(
    group: WorkoutExerciseGroup,
    onToggleSet: (ExerciseSet) -> Unit,
    onUpdateSet: (ExerciseSet) -> Unit,
    onMenuEdit: () -> Unit,
    onMenuRemove: () -> Unit,
    onAddSet: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRemoveConfirm by remember { mutableStateOf(false) }
    val borderColor = if (group.allCompleted) Secondary else OutlineVariant.copy(0.3f)
    val borderWidth = if (group.allCompleted) 2.dp else 1.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceContainerHighest.copy(0.3f), RoundedCornerShape(12.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(12.dp))
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .border(1.dp, OutlineVariant.copy(0.2f))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    group.exerciseName,
                    style = Typography.headlineMedium.copy(fontSize = 18.sp),
                    color = Primary
                )
                Box(
                    Modifier
                        .background(SurfaceContainerHighest, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        group.exerciseType.uppercase(),
                        style = Typography.labelMedium.copy(fontSize = 10.sp),
                        color = OnSurfaceVariant
                    )
                }
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreHoriz, null, tint = OnSurfaceVariant.copy(0.4f))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = { showMenu = false; onMenuEdit() },
                        leadingIcon = { Icon(Icons.Default.Edit, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Remove", color = Error) },
                        onClick = {
                            showMenu = false
                            showRemoveConfirm = true
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = Error) }
                    )
                }
            }
        }

        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Text(
                    "SET",
                    style = Typography.labelMedium.copy(fontSize = 10.sp),
                    color = OnSurfaceVariant.copy(0.6f),
                    modifier = Modifier.weight(0.5f),
                    textAlign = TextAlign.Center
                )
                Text(
                    "WEIGHT (KG)",
                    style = Typography.labelMedium.copy(fontSize = 10.sp),
                    color = OnSurfaceVariant.copy(0.6f),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Text(
                    "REPS",
                    style = Typography.labelMedium.copy(fontSize = 10.sp),
                    color = OnSurfaceVariant.copy(0.6f),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Text(
                    "DONE",
                    style = Typography.labelMedium.copy(fontSize = 10.sp),
                    color = OnSurfaceVariant.copy(0.6f),
                    modifier = Modifier.weight(0.5f),
                    textAlign = TextAlign.Center
                )
            }

            group.sets.forEach { set ->
                WorkoutSetRow(
                    set = set,
                    onToggleDone = { onToggleSet(set) },
                    onWeightChange = { w -> onUpdateSet(set.copy(weight = w)) },
                    onRepsChange = { r -> onUpdateSet(set.copy(reps = r)) }
                )
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, OutlineVariant.copy(0.3f), RoundedCornerShape(8.dp))
                    .clickable(onClick = onAddSet)
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("+ ADD SET", style = Typography.labelMedium, color = OnSurfaceVariant.copy(0.6f))
            }
        }
    }

    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = { Text("Remove Exercise?") },
            text = { Text("Remove ${group.exerciseName} from this workout?") },
            confirmButton = {
                TextButton(onClick = {
                    showRemoveConfirm = false
                    onMenuRemove()
                }) { Text("Remove", color = Error) }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun WorkoutSetRow(
    set: ExerciseSet,
    onToggleDone: () -> Unit,
    onWeightChange: (Float) -> Unit,
    onRepsChange: (Int) -> Unit
) {
    var weightText by remember(set.id, set.weight) {
        mutableStateOf(formatWeightDisplay(set.weight))
    }
    var repsText by remember(set.id, set.reps) {
        mutableStateOf(set.reps.toString())
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${set.setNumber}",
            style = Typography.displayMedium.copy(fontSize = 14.sp),
            color = OnSurfaceVariant.copy(0.4f),
            modifier = Modifier.weight(0.5f),
            textAlign = TextAlign.Center
        )
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
        EditableSetField(
            value = repsText,
            onValueChange = { v ->
                if (v.all { it.isDigit() } && v.length <= 3) {
                    repsText = v
                    v.toIntOrNull()?.let { onRepsChange(it) }
                }
            },
            modifier = Modifier.weight(1f),
            keyboardType = KeyboardType.Number
        )
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
private fun EditableSetField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Decimal
) {
    Box(
        modifier
            .padding(horizontal = 4.dp)
            .height(40.dp)
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
