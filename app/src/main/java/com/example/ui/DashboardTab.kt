package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.CoachInsight
import com.example.GymViewModel
import com.example.SuggestedRoutine
import com.example.data.BodyMeasurementTypes
import com.example.data.DietDateUtils
import com.example.data.ExerciseSet
import com.example.data.MealEntry
import com.example.data.UserProfile
import com.example.data.WaterGoalCalculator
import com.example.data.WorkoutCalorieEstimator
import com.example.data.WorkoutDashboardStats
import com.example.ui.theme.*

private enum class HomeSection { Diet, Exercise }

@Composable
fun DashboardTab(
    viewModel: GymViewModel,
    onOpenWaterTracking: () -> Unit = {},
    onNavigateToDiet: () -> Unit = {},
    onNavigateToWorkout: () -> Unit = {},
    onOpenWorkoutReminder: () -> Unit = {}
) {
    val profile by viewModel.userProfile.collectAsState()
    val meals by viewModel.allMeals.collectAsState()
    val sets by viewModel.allSets.collectAsState()
    val routines by viewModel.allRoutines.collectAsState()
    val waterLogs by viewModel.allWaterLogs.collectAsState()
    val weightLogs by viewModel.allWeightLogs.collectAsState()
    val bodyMeasurements by viewModel.allBodyMeasurements.collectAsState()
    val goalSnapshots by viewModel.allGoalSnapshots.collectAsState()
    val dayProfile = viewModel.profileForDay(DietDateUtils.startOfTodayMillis(), goalSnapshots)
    val dietInsight by viewModel.dietCoachInsight.collectAsState()
    val exerciseInsight by viewModel.exerciseCoachInsight.collectAsState()
    val suggestedRoutine by viewModel.suggestedRoutine.collectAsState()
    val modelsRevision by viewModel.modelsRevision.collectAsState()
    val workoutStats: WorkoutDashboardStats = remember(sets, profile) { viewModel.workoutDashboardStats() }

    LaunchedEffect(profile, meals, sets, waterLogs, profile.aiProvider, profile.offlineModelId, modelsRevision) {
        viewModel.refreshCoachInsights()
    }
    LaunchedEffect(sets.size, routines.size, profile.workoutDaysPerWeek, modelsRevision, profile.aiProvider, profile.offlineModelId) {
        viewModel.refreshRoutineSuggestion(DietDateUtils.startOfTodayMillis())
    }

    DashboardTabContent(
        profile = profile,
        dayProfile = dayProfile,
        meals = meals,
        sets = sets,
        waterLogs = waterLogs,
        weightLogs = weightLogs,
        bodyMeasurements = bodyMeasurements,
        waterGoalGlasses = WaterGoalCalculator.effectiveGoalGlasses(profile),
        todayWaterGlasses = viewModel.todayWaterGlasses(waterLogs),
        waterProgress = viewModel.waterProgress(waterLogs),
        dietInsight = dietInsight,
        exerciseInsight = exerciseInsight,
        suggestedRoutine = suggestedRoutine,
        workoutStats = workoutStats,
        aiEnabled = viewModel.isAiConfigured(),
        onOpenWaterTracking = onOpenWaterTracking,
        onNavigateToDiet = onNavigateToDiet,
        onNavigateToWorkout = onNavigateToWorkout,
        onOpenWorkoutReminder = onOpenWorkoutReminder,
        onStartSuggestedWorkout = { suggestion ->
            viewModel.startRoutineWorkout(suggestion.routine, DietDateUtils.startOfTodayMillis())
            onNavigateToWorkout()
        },
        chartPointsForType = { type ->
            viewModel.chartPointsForType(type, weightLogs, bodyMeasurements)
        },
        latestValueForType = { type ->
            viewModel.latestValueForType(type, weightLogs, bodyMeasurements)
        },
        onLogWeight = { viewModel.logWeight(it) },
        onLogMeasurement = { type, valueCm -> viewModel.logBodyMeasurement(type, valueCm) },
        parseMeasurementInput = { input, useMetric -> viewModel.parseMeasurementInput(input, useMetric) }
    )
}

@Composable
fun DashboardTabContent(
    profile: UserProfile = UserProfile(),
    dayProfile: UserProfile = profile,
    meals: List<MealEntry> = emptyList(),
    sets: List<ExerciseSet> = emptyList(),
    waterLogs: List<com.example.data.WaterLog> = emptyList(),
    weightLogs: List<com.example.data.WeightLog> = emptyList(),
    bodyMeasurements: List<com.example.data.BodyMeasurementLog> = emptyList(),
    waterGoalGlasses: Int = 12,
    todayWaterGlasses: Int = 0,
    waterProgress: Float = 0f,
    dietInsight: CoachInsight = CoachInsight.placeholder("AI DIET INSIGHT"),
    exerciseInsight: CoachInsight = CoachInsight.placeholder("AI COACH INSIGHT"),
    suggestedRoutine: SuggestedRoutine? = null,
    workoutStats: WorkoutDashboardStats = WorkoutDashboardStats(0f, null, 0, 3, 0, 0, emptyList()),
    aiEnabled: Boolean = false,
    onOpenWaterTracking: () -> Unit = {},
    onNavigateToDiet: () -> Unit = {},
    onNavigateToWorkout: () -> Unit = {},
    onOpenWorkoutReminder: () -> Unit = {},
    onStartSuggestedWorkout: (SuggestedRoutine) -> Unit = {},
    chartPointsForType: (String) -> List<com.example.ProgressChartPoint> = { emptyList() },
    latestValueForType: (String) -> String? = { null },
    onLogWeight: (Float) -> Unit = {},
    onLogMeasurement: (String, Float) -> Unit = { _, _ -> },
    parseMeasurementInput: (String, Boolean) -> Float? = { _, _ -> null }
) {
    val todayStart = remember { DietDateUtils.startOfTodayMillis() }
    val todayEnd = remember { DietDateUtils.endOfDayMillis(todayStart) }
    var homeSection by remember { mutableStateOf(HomeSection.Diet) }

    var showLogWeightDialog by remember { mutableStateOf(false) }
    var showLogMeasurementDialog by remember { mutableStateOf(false) }
    var selectedMeasurementType by remember { mutableStateOf(BodyMeasurementTypes.WEIGHT) }

    val todayMeals = remember(meals, todayStart) { DietDateUtils.mealsForDay(meals, todayStart) }
    val dailyGoal = dayProfile.dailyCalories.coerceAtLeast(1)
    val consumed = todayMeals.sumOf { it.calories }
    val burned = remember(sets, todayStart, profile) {
        WorkoutCalorieEstimator.estimateBurn(
            sets.filter { it.timestamp in todayStart until todayEnd && it.isCompleted },
            profile
        )
    }
    val rawRemaining = remember(consumed, burned, dailyGoal) { dailyGoal - consumed + burned }
    val dailyProgress = if (dailyGoal > 0) {
        (consumed.toFloat() / dailyGoal).coerceIn(0f, 1f)
    } else 0f

    val proteinGoal = dayProfile.protein.coerceAtLeast(1)
    val carbsGoal = dayProfile.carbs.coerceAtLeast(1)
    val fatGoal = dayProfile.fat.coerceAtLeast(1)
    val fiberGoal = dayProfile.fiber.coerceAtLeast(1)
    val proteinConsumed = todayMeals.sumOf { it.protein }
    val carbsConsumed = todayMeals.sumOf { it.carbs }
    val fatConsumed = todayMeals.sumOf { it.fat }
    val fiberConsumed = todayMeals.sumOf { it.fiber }

    val chartPoints = remember(selectedMeasurementType, weightLogs, bodyMeasurements) {
        chartPointsForType(selectedMeasurementType)
    }
    val latestValue = remember(selectedMeasurementType, weightLogs, bodyMeasurements) {
        latestValueForType(selectedMeasurementType)
    }

    if (showLogWeightDialog) {
        LogWeightDialog(
            currentKg = profile.currentWeight,
            onDismiss = { showLogWeightDialog = false },
            onConfirm = { kg -> onLogWeight(kg); showLogWeightDialog = false }
        )
    }
    if (showLogMeasurementDialog && !BodyMeasurementTypes.isWeight(selectedMeasurementType)) {
        LogMeasurementDialog(
            typeLabel = BodyMeasurementTypes.labelFor(selectedMeasurementType),
            useMetric = profile.measurementUseMetric,
            onDismiss = { showLogMeasurementDialog = false },
            onConfirm = { valueCm ->
                onLogMeasurement(selectedMeasurementType, valueCm)
                showLogMeasurementDialog = false
            },
            parseInput = { input -> parseMeasurementInput(input, profile.measurementUseMetric) }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HomeSectionToggle(
            selectedExercise = homeSection == HomeSection.Exercise,
            onDiet = { homeSection = HomeSection.Diet },
            onExercise = { homeSection = HomeSection.Exercise }
        )

        when (homeSection) {
            HomeSection.Diet -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNavigateToDiet)
                        .background(SurfaceContainerHighest.copy(0.3f), RoundedCornerShape(16.dp))
                        .border(1.dp, OutlineVariant.copy(0.2f), RoundedCornerShape(16.dp))
                        .padding(24.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("OVERALL INTAKE", style = Typography.labelMedium, color = OnSurfaceVariant.copy(0.7f))
                            Icon(Icons.Default.ChevronRight, null, tint = OnSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                "%,d".format(consumed),
                                style = Typography.displayMedium,
                                color = if (rawRemaining < 0) Error else Secondary
                            )
                            Text(
                                " / %,d kcal".format(dailyGoal),
                                style = Typography.titleMedium,
                                color = OnSurfaceVariant.copy(0.6f),
                                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                            )
                        }
                        Text(
                            if (rawRemaining >= 0) {
                                "%,d kcal remaining".format(rawRemaining)
                            } else {
                                "%,d kcal over goal".format(-rawRemaining)
                            },
                            style = Typography.bodySmall,
                            color = if (rawRemaining >= 0) OnSurfaceVariant.copy(0.6f) else Error
                        )
                        if (burned > 0) {
                            Text("+%d kcal from workouts".format(burned), style = Typography.labelMedium, color = Primary, modifier = Modifier.padding(top = 4.dp))
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { dailyProgress },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = Secondary,
                            trackColor = SurfaceContainerHighest
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            MacroCol("PROTEIN", "${proteinConsumed}g/${proteinGoal}g", Secondary, proteinConsumed.toFloat() / proteinGoal, Modifier.weight(1f))
                            MacroCol("CARBS", "${carbsConsumed}g/${carbsGoal}g", Primary, carbsConsumed.toFloat() / carbsGoal, Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            MacroCol("FAT", "${fatConsumed}g/${fatGoal}g", Tertiary, fatConsumed.toFloat() / fatGoal, Modifier.weight(1f))
                            MacroCol("FIBER", "${fiberConsumed}g/${fiberGoal}g", OnSurface, fiberConsumed.toFloat() / fiberGoal, Modifier.weight(1f))
                        }
                    }
                }

                if (!aiEnabled || dietInsight.body.isNotBlank()) {
                    AiCoachInsightCard(insight = dietInsight)
                }

                WaterCard(
                    todayGlasses = todayWaterGlasses,
                    goalGlasses = waterGoalGlasses,
                    progress = waterProgress.coerceIn(0f, 1f),
                    onClick = onOpenWaterTracking
                )

                BodyCompositionCard(
                    selectedType = selectedMeasurementType,
                    onTypeSelected = { selectedMeasurementType = it },
                    chartPoints = chartPoints,
                    latestValue = latestValue,
                    useMetric = profile.measurementUseMetric,
                    onLogClick = {
                        if (BodyMeasurementTypes.isWeight(selectedMeasurementType)) showLogWeightDialog = true
                        else showLogMeasurementDialog = true
                    }
                )
            }

            HomeSection.Exercise -> {
                suggestedRoutine?.let { suggestion ->
                    SuggestedWorkoutCard(
                        suggestion = suggestion,
                        onStart = { onStartSuggestedWorkout(suggestion) },
                        onSetReminder = onOpenWorkoutReminder
                    )
                } ?: run {
                    OnboardingInfoCard(
                        title = "No routine suggestion",
                        value = "Create a routine in the Workout tab",
                        subtitle = "Tap below to open workouts",
                        highlight = false
                    )
                    Button(onClick = onNavigateToWorkout, modifier = Modifier.fillMaxWidth()) {
                        Text("Open Workout Tab")
                    }
                }

                if (!aiEnabled || exerciseInsight.body.isNotBlank()) {
                    AiCoachInsightCard(insight = exerciseInsight)
                }

                WorkoutStatsRow(stats = workoutStats)
                WeeklyActivityWideCard(stats = workoutStats)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun WaterCard(
    todayGlasses: Int,
    goalGlasses: Int,
    progress: Float,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(SurfaceContainerHighest.copy(0.3f), RoundedCornerShape(16.dp))
            .border(1.dp, OutlineVariant.copy(0.2f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.WaterDrop, null, tint = Primary)
            Spacer(Modifier.width(8.dp))
            Text("Water", style = Typography.labelMedium, color = OnSurfaceVariant.copy(0.8f))
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, null, tint = OnSurfaceVariant)
        }
        Text("$todayGlasses of $goalGlasses Glasses", style = Typography.titleLarge, color = OnSurface)
        WaterSegmentBar(progress = progress)
        Text("${todayGlasses * WaterGoalCalculator.ML_PER_GLASS} ml logged today", style = Typography.bodySmall, color = OnSurfaceVariant)
    }
}

@Composable
private fun BodyCompositionCard(
    selectedType: String,
    onTypeSelected: (String) -> Unit,
    chartPoints: List<com.example.ProgressChartPoint>,
    latestValue: String?,
    useMetric: Boolean,
    onLogClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceContainerHighest.copy(0.3f), RoundedCornerShape(16.dp))
            .border(1.dp, OutlineVariant.copy(0.2f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.MonitorWeight, null, tint = Secondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("BODY COMPOSITION", style = Typography.labelMedium, color = OnSurfaceVariant.copy(0.8f))
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BodyMeasurementTypes.ALL.forEach { type ->
                val selected = type.id == selectedType
                FilterChip(
                    selected = selected,
                    onClick = { onTypeSelected(type.id) },
                    label = { Text(type.label, style = Typography.labelMedium) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Secondary.copy(0.2f),
                        selectedLabelColor = Secondary
                    )
                )
            }
        }
        ProgressSplineChart(points = chartPoints, lineColor = Secondary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Current ${BodyMeasurementTypes.labelFor(selectedType)}", style = Typography.labelMedium, color = OnSurfaceVariant)
                Text(latestValue ?: "—", style = Typography.titleMedium, color = OnSurface)
            }
            Button(onClick = onLogClick, shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = Primary.copy(0.25f))) {
                Text(
                    if (BodyMeasurementTypes.isWeight(selectedType)) "Log Today's Weight" else "Log Measurement",
                    style = Typography.labelMedium,
                    color = Primary
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DashboardTabPreview() {
    MyApplicationTheme { DashboardTabContent() }
}

@Composable
fun MacroCol(label: String, value: String, color: Color, progress: Float, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = Typography.labelMedium, color = OnSurfaceVariant.copy(0.8f))
            Text(value, style = Typography.labelMedium, color = color)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).background(SurfaceContainerHighest, RoundedCornerShape(50))) {
            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(color, RoundedCornerShape(50)))
        }
    }
}
