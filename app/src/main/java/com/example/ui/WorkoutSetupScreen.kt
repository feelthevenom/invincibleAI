package com.example.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.GymViewModel
import com.example.data.UserProfile
import com.example.data.WorkoutPreferences
import com.example.data.ProfileValidation
import com.example.data.WorkoutSetupProgress
import com.example.ui.theme.*
import java.util.Calendar
import kotlin.math.roundToInt

@Composable
fun WorkoutSetupScreen(
    viewModel: GymViewModel,
    profile: UserProfile,
    onComplete: () -> Unit
) {
    WorkoutSetupScreenContent(
        profile = profile,
        onComplete = { workoutProfile ->
            viewModel.completeWorkoutSetup(workoutProfile)
            onComplete()
        },
        onNotificationsGranted = { viewModel.setNotificationsEnabled(true) }
    )
}

@Composable
fun WorkoutSetupScreenContent(
    profile: UserProfile = UserProfile(),
    onComplete: (UserProfile) -> Unit = {},
    onNotificationsGranted: () -> Unit = {}
) {
    val totalSteps = 8
    var step by remember { mutableIntStateOf(WorkoutSetupProgress.firstIncompleteStep(profile).coerceIn(1, totalSteps)) }
    var progress by remember { mutableFloatStateOf(step / totalSteps.toFloat()) }

    var fitnessLevel by remember(profile) {
        mutableStateOf(profile.fitnessLevel.ifBlank { WorkoutPreferences.FITNESS_LEVELS.first() })
    }
    var benchmarkSkipped by remember(profile) { mutableStateOf(profile.benchmarkSkipped) }
    val benchmarkLifts = listOf("Squat", "Bench Press", "Deadlift")
    var selectedBenchmarkLift by remember(profile) {
        mutableStateOf(
            when {
                profile.squat1RmKg > 0f -> "Squat"
                profile.benchPress1RmKg > 0f -> "Bench Press"
                profile.deadlift1RmKg > 0f -> "Deadlift"
                else -> benchmarkLifts.first()
            }
        )
    }
    var benchmarkWeight by remember(profile) {
        mutableStateOf(
            when {
                profile.squat1RmKg > 0f -> profile.squat1RmKg.toInt().toString()
                profile.benchPress1RmKg > 0f -> profile.benchPress1RmKg.toInt().toString()
                profile.deadlift1RmKg > 0f -> profile.deadlift1RmKg.toInt().toString()
                else -> "50"
            }
        )
    }

    var workoutDays by remember(profile) {
        mutableIntStateOf(profile.workoutDaysPerWeek.coerceIn(3, 7).let { if (it < 3) 3 else it })
    }
    var weekStartDay by remember(profile) {
        mutableIntStateOf(
            profile.weekStartDay.takeIf { it in Calendar.SUNDAY..Calendar.SATURDAY } ?: Calendar.MONDAY
        )
    }
    var selectedEquipment by remember(profile) {
        mutableStateOf(WorkoutPreferences.parseEquipment(profile.equipmentSelection).ifEmpty { setOf("all") })
    }
    var gymLocation by remember(profile) {
        mutableStateOf(profile.gymLocation.ifBlank { WorkoutPreferences.GYM_LOCATIONS.first().id })
    }
    var notificationsEnabled by remember(profile) { mutableStateOf(profile.notificationsEnabled) }

    val rmWeightItems = remember { weightKgItems(20f, 250f) }

    LaunchedEffect(step) {
        progress = step / totalSteps.toFloat()
    }

    fun canProceed(): Boolean = when (step) {
        1 -> fitnessLevel.isNotBlank()
        2 -> benchmarkSkipped || benchmarkWeight.toFloatOrNull()?.let { it > 0 } == true
        3 -> workoutDays in 3..7
        4 -> weekStartDay in Calendar.SUNDAY..Calendar.SATURDAY
        5 -> selectedEquipment.isNotEmpty()
        6 -> gymLocation.isNotBlank()
        7 -> true
        8 -> true
        else -> false
    }

    fun buildProfile(): UserProfile {
        val weight = if (benchmarkSkipped) 0f else benchmarkWeight.toFloatOrNull() ?: 0f
        return profile.copy(
            fitnessLevel = fitnessLevel,
            benchmarkSkipped = benchmarkSkipped,
            squat1RmKg = if (!benchmarkSkipped && selectedBenchmarkLift == "Squat") weight else 0f,
            benchPress1RmKg = if (!benchmarkSkipped && selectedBenchmarkLift == "Bench Press") weight else 0f,
            deadlift1RmKg = if (!benchmarkSkipped && selectedBenchmarkLift == "Deadlift") weight else 0f,
            workoutDaysPerWeek = workoutDays,
            weekStartDay = weekStartDay,
            equipmentSelection = WorkoutPreferences.serializeEquipment(selectedEquipment),
            gymLocation = gymLocation,
            workoutSetupComplete = true,
            notificationsEnabled = notificationsEnabled
        )
    }

    val animatedProgress by animateFloatAsState(targetValue = progress, animationSpec = tween(700), label = "workoutSetupProg")
    val draft = remember(
        fitnessLevel, benchmarkSkipped, selectedBenchmarkLift, benchmarkWeight,
        workoutDays, weekStartDay, selectedEquipment, gymLocation
    ) { buildProfile() }

    Column(modifier = Modifier.fillMaxSize().background(Background)) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(SurfaceContainerHighest)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(animatedProgress).background(Primary))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 8.dp)
                    .height(56.dp)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { if (step > 1) step-- }, enabled = step > 1) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        "Back",
                        tint = if (step > 1) OnSurface else OnSurface.copy(0.3f)
                    )
                }
                Text("WORKOUT SETUP $step / $totalSteps", style = Typography.labelMedium, color = OnSurfaceVariant)
                Spacer(modifier = Modifier.width(48.dp))
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            when (step) {
                1 -> FitnessLevelStep(fitnessLevel) { fitnessLevel = it }
                2 -> BenchmarkStep(
                    skipped = benchmarkSkipped,
                    onSkip = {
                        benchmarkSkipped = true
                        benchmarkWeight = "50"
                    },
                    onEnableBenchmark = {
                        benchmarkSkipped = false
                        if (benchmarkWeight.isBlank()) benchmarkWeight = "50"
                    },
                    selectedLift = selectedBenchmarkLift,
                    onLiftSelected = { selectedBenchmarkLift = it },
                    weight = benchmarkWeight,
                    onWeight = { benchmarkWeight = it; benchmarkSkipped = false },
                    weightItems = rmWeightItems
                )
                3 -> FrequencyStep(workoutDays) { workoutDays = it }
                4 -> WeekStartStep(weekStartDay) { weekStartDay = it }
                5 -> EquipmentStep(selectedEquipment) {
                    selectedEquipment = WorkoutPreferences.toggleEquipmentSelection(selectedEquipment, it)
                }
                6 -> GymLocationStep(gymLocation) { gymLocation = it }
                7 -> SummaryStep(draft)
                8 -> NotificationsSetupStep(
                    onGranted = {
                        notificationsEnabled = true
                        onNotificationsGranted()
                    }
                )
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .background(Background.copy(0.95f))
                .border(1.dp, OutlineVariant.copy(0.2f))
                .padding(20.dp)
        ) {
            Button(
                onClick = {
                    if (step < totalSteps) step++ else onComplete(buildProfile())
                },
                enabled = canProceed(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = OnPrimary)
            ) {
                Text(
                    when (step) {
                        7 -> "Continue"
                        8 -> "Start Training"
                        else -> "Continue"
                    },
                    style = Typography.headlineMedium.copy(fontSize = 18.sp)
                )
            }
        }
    }
}

@Composable
private fun FitnessLevelStep(selected: String, onSelect: (String) -> Unit) {
    StepHeader(
        title = "What's your fitness level?",
        subtitle = "We'll tailor volume and exercise difficulty to match your experience."
    )
    WorkoutPreferences.FITNESS_LEVELS.forEach { level ->
        GoalCard(
            title = level,
            subtitle = when (level) {
                "Beginner" -> "New to structured training"
                "Intermediate" -> "6+ months of consistent lifting"
                "Advanced" -> "Years of training experience"
                else -> "Competitive or high-performance athlete"
            },
            isSelected = selected == level
        ) { onSelect(level) }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun BenchmarkStep(
    skipped: Boolean,
    onSkip: () -> Unit,
    onEnableBenchmark: () -> Unit,
    selectedLift: String,
    onLiftSelected: (String) -> Unit,
    weight: String,
    onWeight: (String) -> Unit,
    weightItems: List<String>
) {
    val lifts = listOf("Squat", "Bench Press", "Deadlift")
    StepHeader(
        title = "Estimate your 1RM",
        subtitle = "Choose one lift you know and enter your best single-rep max, or skip if you're a beginner."
    )
    if (!skipped) {
        OnboardingDropdown("Exercise", selectedLift, lifts, onSelect = onLiftSelected)
        Spacer(Modifier.height(16.dp))
        val weightIndex = remember(weight, weightItems) {
            val kg = weight.toFloatOrNull()?.roundToInt() ?: 50
            weightItems.indexOfFirst { it.startsWith("$kg ") }.coerceAtLeast(0)
        }
        var pickerIndex by remember(weight) { mutableIntStateOf(weightIndex) }
        WheelPickerField(
            label = "1RM Weight (kg)",
            items = weightItems,
            selectedIndex = pickerIndex.coerceIn(0, weightItems.lastIndex),
            onConfirm = {
                pickerIndex = it
                onWeight(weightItems[it].substringBefore(" "))
            }
        )
        Spacer(Modifier.height(20.dp))
    } else {
        OnboardingInfoCard(
            title = "Benchmarks skipped",
            value = "We'll start with lighter weights and progress safely.",
            highlight = true
        )
        Spacer(Modifier.height(20.dp))
    }
    if (skipped) {
        OutlinedButton(
            onClick = onEnableBenchmark,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary)
        ) {
            Text("I know my lifts — add benchmarks", style = Typography.labelMedium)
        }
    } else {
        OutlinedButton(
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = OnSurfaceVariant)
        ) {
            Text("I'm a beginner — skip benchmarks", style = Typography.labelMedium)
        }
    }
}

@Composable
private fun FrequencyStep(selectedDays: Int, onSelect: (Int) -> Unit) {
    StepHeader(
        title = "How often do you work out?",
        subtitle = "Choose a sustainable schedule — minimum 3 days per week."
    )
    WorkoutPreferences.WORKOUT_FREQUENCY_OPTIONS.forEach { option ->
        GoalCard(
            title = option.label,
            subtitle = when (option.daysPerWeek) {
                3 -> "Full-body or push/pull/legs"
                4 -> "Upper / lower split"
                5 -> "Bro split or PPL + extras"
                6 -> "High frequency training"
                else -> "Daily training"
            },
            isSelected = selectedDays == option.daysPerWeek
        ) { onSelect(option.daysPerWeek) }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun WeekStartStep(selectedDay: Int, onSelect: (Int) -> Unit) {
    StepHeader(
        title = "Pick your workout week's start day",
        subtitle = "Your weekly plan resets on this day."
    )
    WorkoutPreferences.WEEK_DAYS.forEach { day ->
        GoalCard(title = day.label, isSelected = selectedDay == day.calendarDay) {
            onSelect(day.calendarDay)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun EquipmentStep(selected: Set<String>, onToggle: (String) -> Unit) {
    StepHeader(
        title = "Which equipment do you have?",
        subtitle = "Select everything available where you train. Choose All or None if that fits best."
    )
    WorkoutPreferences.EQUIPMENT_OPTIONS.forEach { equipment ->
        GoalCard(
            title = equipment.label,
            subtitle = equipment.group,
            icon = EquipmentIcons.iconFor(equipment.id),
            isSelected = equipment.id in selected
        ) { onToggle(equipment.id) }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun GymLocationStep(selectedId: String, onSelect: (String) -> Unit) {
    StepHeader(
        title = "Where do you prefer to exercise?",
        subtitle = "This helps us recommend realistic exercises for your environment."
    )
    WorkoutPreferences.GYM_LOCATIONS.forEach { location ->
        GoalCard(
            title = location.label,
            subtitle = location.description,
            isSelected = selectedId == location.id
        ) { onSelect(location.id) }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun NotificationsSetupStep(onGranted: () -> Unit) {
    val context = LocalContext.current
    var permissionRequested by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onGranted()
        permissionRequested = true
    }

    StepHeader(
        title = "Stay on track with reminders",
        subtitle = "Allow notifications so Gym AI can remind you to drink water and keep up with your goals."
    )
    OnboardingInfoCard(
        title = "Hydration alerts",
        value = "Get nudges to log water throughout the day",
        subtitle = "You can customize reminder times later from the Water screen."
    )
    Spacer(Modifier.height(12.dp))
    OnboardingInfoCard(
        title = "Optional",
        value = "Skip for now if you prefer",
        subtitle = "You can enable notifications anytime from the home screen bell icon."
    )
    Spacer(Modifier.height(24.dp))
    OutlinedButton(
        onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                when {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED -> onGranted()
                    else -> launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                onGranted()
            }
        },
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text("Allow Notifications", color = Primary)
    }
    if (permissionRequested) {
        Spacer(Modifier.height(8.dp))
        Text(
            "You can change this later in system settings.",
            style = Typography.bodySmall,
            color = OnSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SummaryStep(profile: UserProfile) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        StepHeader(
            title = "Your workout profile",
            subtitle = "Review your setup. You can change these anytime in Personal Details."
        )
        Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = Primary, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))

        val totalStrength = WorkoutPreferences.totalStrengthKg(profile)
        val bmi = ProfileValidation.bmi(profile.currentWeight, profile.height)
        SummaryStatGrid(
            listOf(
                "Fitness Level" to profile.fitnessLevel,
                "Frequency" to WorkoutPreferences.estimatedFrequencyLabel(profile.workoutDaysPerWeek),
                "Week Starts" to WorkoutPreferences.weekDayLabel(profile.weekStartDay),
                "Location" to WorkoutPreferences.gymLocationLabel(profile.gymLocation),
                "Body Weight" to "${profile.currentWeight.toInt()} kg",
                "Height / BMI" to "${profile.height} cm · BMI ${String.format("%.1f", bmi)}"
            )
        )
        Spacer(Modifier.height(16.dp))
        OnboardingInfoCard(
            title = "Strength Benchmark",
            value = WorkoutPreferences.strengthSummary(profile),
            subtitle = if (!profile.benchmarkSkipped && totalStrength > 0) {
                "Combined total: ${totalStrength.toInt()} kg"
            } else null,
            highlight = !profile.benchmarkSkipped && totalStrength > 0
        )
        if (!profile.benchmarkSkipped && totalStrength > 0) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                if (profile.squat1RmKg > 0) {
                    Box(Modifier.weight(1f)) { OnboardingInfoCard("Squat 1RM", "${profile.squat1RmKg.toInt()} kg") }
                }
                if (profile.benchPress1RmKg > 0) {
                    Box(Modifier.weight(1f)) { OnboardingInfoCard("Bench 1RM", "${profile.benchPress1RmKg.toInt()} kg") }
                }
                if (profile.deadlift1RmKg > 0) {
                    Box(Modifier.weight(1f)) { OnboardingInfoCard("Deadlift 1RM", "${profile.deadlift1RmKg.toInt()} kg") }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        OnboardingInfoCard(
            title = "Equipment",
            value = WorkoutPreferences.equipmentDisplay(profile.equipmentSelection)
        )
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.CheckCircle, null, tint = Secondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Ready to start your personalized plan",
                style = Typography.bodyMedium,
                color = Secondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SummaryStatGrid(items: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { (label, value) ->
                    Box(Modifier.weight(1f)) {
                        OnboardingInfoCard(title = label, value = value)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StepHeader(title: String, subtitle: String) {
    Text(title, style = Typography.headlineLarge, color = Primary)
    Spacer(Modifier.height(8.dp))
    Text(subtitle, style = Typography.bodyMedium, color = OnSurfaceVariant)
    Spacer(Modifier.height(24.dp))
}

@Preview(showBackground = true)
@Composable
fun WorkoutSetupScreenPreview() {
    MyApplicationTheme {
        WorkoutSetupScreenContent()
    }
}
