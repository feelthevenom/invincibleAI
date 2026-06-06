package com.example.ui

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.GymViewModel
import com.example.data.AiProviderConfig
import com.example.data.CuisineTypes
import com.example.data.OfflineModelConfig
import com.example.data.FitnessCalculator
import com.example.data.ProfileValidation
import com.example.data.UserProfile
import com.example.data.WorkoutPreferences
import com.example.ui.theme.*

private enum class PersonalEditSection { Physical, ActivityGoal, Weight, Nutrition, Workout, Cuisines, Weekly }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalDetailsScreen(viewModel: GymViewModel, onBack: () -> Unit) {
    val profile by viewModel.userProfile.collectAsState()
    if (!profile.onboardingComplete) return

    PersonalDetailsScreenContent(
        profile = profile,
        onBack = onBack,
        onSave = { tw, dc, p, c, f, fi, weekly, cuisines ->
            viewModel.savePersonalDetailsEdits(tw, dc, p, c, f, fi, weekly, cuisines)
        },
        onSaveWorkout = { fl, skipped, squat, bench, dead, days, weekStart, equip, gym ->
            viewModel.saveWorkoutPreferencesEdits(fl, skipped, squat, bench, dead, days, weekStart, equip, gym)
        },
        onSavePhysical = { age, gender, height ->
            viewModel.savePhysicalMetricsEdits(age, gender, height)
        },
        onSaveActivityGoal = { activity, goal ->
            viewModel.saveActivityGoalEdits(activity, goal)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalDetailsScreenContent(
    profile: UserProfile,
    onBack: () -> Unit,
    onSave: (Float, Int, Int, Int, Int, Int, Float?, String?) -> Unit,
    onSaveWorkout: (
        fitnessLevel: String,
        benchmarkSkipped: Boolean,
        squat1RmKg: Float,
        benchPress1RmKg: Float,
        deadlift1RmKg: Float,
        workoutDaysPerWeek: Int,
        weekStartDay: Int,
        equipmentSelection: String,
        gymLocation: String
    ) -> Unit = { _, _, _, _, _, _, _, _, _ -> },
    onSavePhysical: (Int, String, Int) -> Unit = { _, _, _ -> },
    onSaveActivityGoal: (String, String) -> Unit = { _, _ -> }
) {
    var editingSection by remember { mutableStateOf<PersonalEditSection?>(null) }

    var ageIndex by remember(profile, editingSection) {
        mutableIntStateOf((profile.age - ProfileValidation.MIN_AGE).coerceIn(0, ProfileValidation.MAX_AGE - ProfileValidation.MIN_AGE))
    }
    var gender by remember(profile, editingSection) { mutableStateOf(profile.gender) }
    var useMetricHeight by remember { mutableStateOf(true) }
    var heightCmIndex by remember(profile, editingSection) {
        mutableIntStateOf((profile.height - ProfileValidation.MIN_HEIGHT_CM).coerceIn(0, ProfileValidation.MAX_HEIGHT_CM - ProfileValidation.MIN_HEIGHT_CM))
    }
    var feetIndex by remember { mutableIntStateOf(2) }
    var inchIndex by remember { mutableIntStateOf(7) }
    var activityLevel by remember(profile, editingSection) { mutableStateOf(profile.activityLevel) }
    var goal by remember(profile, editingSection) { mutableStateOf(profile.goal) }

    fun draftHeightCm(): Int = if (useMetricHeight) {
        ProfileValidation.MIN_HEIGHT_CM + heightCmIndex
    } else {
        ProfileValidation.cmFromFeetInches(3 + feetIndex, inchIndex)
    }

    var targetWeight by remember(profile, editingSection) { mutableStateOf(String.format("%.1f", profile.targetWeight)) }
    var dailyCalories by remember(profile, editingSection) { mutableStateOf(profile.dailyCalories.toString()) }
    var protein by remember(profile, editingSection) { mutableStateOf(profile.protein.toString()) }
    var carbs by remember(profile, editingSection) { mutableStateOf(profile.carbs.toString()) }
    var fat by remember(profile, editingSection) { mutableStateOf(profile.fat.toString()) }
    var fiber by remember(profile, editingSection) { mutableStateOf(profile.fiber.toString()) }
    var selectedCuisines by remember(profile, editingSection) { mutableStateOf(CuisineTypes.parsePreferences(profile.cuisinePreferences).toSet()) }
    var weeklyWarning by remember { mutableStateOf<String?>(null) }

    var fitnessLevel by remember(profile, editingSection) {
        mutableStateOf(profile.fitnessLevel.ifBlank { WorkoutPreferences.FITNESS_LEVELS.first() })
    }
    var benchmarkSkipped by remember(profile, editingSection) { mutableStateOf(profile.benchmarkSkipped) }
    var squat1Rm by remember(profile, editingSection) {
        mutableStateOf(if (profile.squat1RmKg > 0) profile.squat1RmKg.toInt().toString() else "")
    }
    var bench1Rm by remember(profile, editingSection) {
        mutableStateOf(if (profile.benchPress1RmKg > 0) profile.benchPress1RmKg.toInt().toString() else "")
    }
    var deadlift1Rm by remember(profile, editingSection) {
        mutableStateOf(if (profile.deadlift1RmKg > 0) profile.deadlift1RmKg.toInt().toString() else "")
    }
    var workoutDays by remember(profile, editingSection) {
        mutableIntStateOf(profile.workoutDaysPerWeek.coerceIn(3, 7).let { if (it < 3) 3 else it })
    }
    var weekStartDay by remember(profile, editingSection) {
        mutableIntStateOf(profile.weekStartDay)
    }
    var selectedEquipment by remember(profile, editingSection) {
        mutableStateOf(WorkoutPreferences.parseEquipment(profile.equipmentSelection).ifEmpty { setOf("all") })
    }
    var gymLocation by remember(profile, editingSection) {
        mutableStateOf(profile.gymLocation.ifBlank { WorkoutPreferences.GYM_LOCATIONS.first().id })
    }

    val weeklyItems = remember(profile.goal, profile.currentWeight) {
        weeklyChangeItems(ProfileValidation.minWeeklyChangeKg(profile.goal), ProfileValidation.maxWeeklyChangeKg(profile.goal, profile.currentWeight))
    }
    var weeklyIndex by remember(profile, weeklyItems, editingSection) {
        mutableIntStateOf(
            weeklyItems.indexOfFirst { it.startsWith(String.format("%.1f", profile.targetWeightChangePerWeek)) }.coerceAtLeast(0)
        )
    }

    fun resetSectionDrafts() {
        ageIndex = (profile.age - ProfileValidation.MIN_AGE).coerceIn(0, ProfileValidation.MAX_AGE - ProfileValidation.MIN_AGE)
        gender = profile.gender
        heightCmIndex = (profile.height - ProfileValidation.MIN_HEIGHT_CM).coerceIn(0, ProfileValidation.MAX_HEIGHT_CM - ProfileValidation.MIN_HEIGHT_CM)
        activityLevel = profile.activityLevel
        goal = profile.goal
        targetWeight = String.format("%.1f", profile.targetWeight)
        dailyCalories = profile.dailyCalories.toString()
        protein = profile.protein.toString()
        carbs = profile.carbs.toString()
        fat = profile.fat.toString()
        fiber = profile.fiber.toString()
        selectedCuisines = CuisineTypes.parsePreferences(profile.cuisinePreferences).toSet()
        fitnessLevel = profile.fitnessLevel.ifBlank { WorkoutPreferences.FITNESS_LEVELS.first() }
        benchmarkSkipped = profile.benchmarkSkipped
        squat1Rm = if (profile.squat1RmKg > 0) profile.squat1RmKg.toInt().toString() else ""
        bench1Rm = if (profile.benchPress1RmKg > 0) profile.benchPress1RmKg.toInt().toString() else ""
        deadlift1Rm = if (profile.deadlift1RmKg > 0) profile.deadlift1RmKg.toInt().toString() else ""
        workoutDays = profile.workoutDaysPerWeek.coerceIn(3, 7).let { if (it < 3) 3 else it }
        weekStartDay = profile.weekStartDay
        selectedEquipment = WorkoutPreferences.parseEquipment(profile.equipmentSelection).ifEmpty { setOf("all") }
        gymLocation = profile.gymLocation.ifBlank { WorkoutPreferences.GYM_LOCATIONS.first().id }
        weeklyIndex = weeklyItems.indexOfFirst { it.startsWith(String.format("%.1f", profile.targetWeightChangePerWeek)) }.coerceAtLeast(0)
    }

    fun rebalance(field: FitnessCalculator.MacroField, value: Int) {
        val cal = dailyCalories.toIntOrNull() ?: profile.dailyCalories
        val result = FitnessCalculator.rebalanceMacros(
            totalCalories = cal,
            protein = protein.toIntOrNull() ?: profile.protein,
            carbs = carbs.toIntOrNull() ?: profile.carbs,
            fat = fat.toIntOrNull() ?: profile.fat,
            fiber = fiber.toIntOrNull() ?: profile.fiber,
            changedField = field,
            newValue = value
        )
        protein = result.protein.toString()
        carbs = result.carbs.toString()
        fat = result.fat.toString()
        fiber = result.fiber.toString()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Personal Details", style = Typography.headlineMedium, color = Primary) },
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
                .padding(20.dp)
        ) {
            SectionHeader(
                title = "Step 1 — Physical Metrics",
                isEditing = editingSection == PersonalEditSection.Physical,
                onEdit = { editingSection = PersonalEditSection.Physical },
                onCancel = { resetSectionDrafts(); editingSection = null },
                onConfirm = {
                    onSavePhysical(ProfileValidation.MIN_AGE + ageIndex, gender, draftHeightCm())
                    editingSection = null
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (editingSection == PersonalEditSection.Physical) {
                WheelPickerField(
                    label = "Age",
                    items = ageItems(),
                    selectedIndex = ageIndex,
                    onConfirm = { ageIndex = it }
                )
                Spacer(modifier = Modifier.height(12.dp))
                OnboardingDropdown("Gender", gender, FitnessCalculator.genders, onSelect = { gender = it })
                Spacer(modifier = Modifier.height(12.dp))
                HeightUnitToggle(useMetricHeight) { useMetricHeight = it }
                Spacer(modifier = Modifier.height(12.dp))
                if (useMetricHeight) {
                    WheelPickerField(
                        label = "Height",
                        items = cmHeightItems(),
                        selectedIndex = heightCmIndex,
                        onConfirm = { heightCmIndex = it }
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        WheelPickerField(
                            label = "Feet",
                            items = feetItems(),
                            selectedIndex = feetIndex,
                            onConfirm = { feetIndex = it },
                            modifier = Modifier.weight(1f)
                        )
                        WheelPickerField(
                            label = "Inches",
                            items = inchItems(),
                            selectedIndex = inchIndex,
                            onConfirm = { inchIndex = it },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                PersonalMetricsCard(profile)
            }

            Spacer(modifier = Modifier.height(24.dp))
            SectionHeader(
                title = "Step 2 — Activity & Goal",
                isEditing = editingSection == PersonalEditSection.ActivityGoal,
                onEdit = { editingSection = PersonalEditSection.ActivityGoal },
                onCancel = { resetSectionDrafts(); editingSection = null },
                onConfirm = {
                    onSaveActivityGoal(activityLevel, goal)
                    editingSection = null
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (editingSection == PersonalEditSection.ActivityGoal) {
                OnboardingDropdown("Activity Level", activityLevel, FitnessCalculator.activityLevels, onSelect = { activityLevel = it })
                Spacer(modifier = Modifier.height(16.dp))
                Text("PRIMARY GOAL", style = Typography.labelMedium, color = OnSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                FitnessCalculator.goals.forEach { g ->
                    GoalCard(title = g, isSelected = goal == g, icon = GoalIcons.iconFor(g)) { goal = g }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            } else {
                ActivityGoalCard(profile)
            }

            Spacer(modifier = Modifier.height(24.dp))

            SectionHeader(
                title = "Step 3 — Weight Goals",
                isEditing = editingSection == PersonalEditSection.Weight,
                onEdit = { editingSection = PersonalEditSection.Weight },
                onCancel = { resetSectionDrafts(); editingSection = null },
                onConfirm = {
                    onSave(
                        targetWeight.toFloatOrNull() ?: profile.targetWeight,
                        profile.dailyCalories, profile.protein, profile.carbs, profile.fat, profile.fiber,
                        null, profile.cuisinePreferences
                    )
                    editingSection = null
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            ReadOnlyField("Current Weight", "${profile.currentWeight} kg")
            Spacer(modifier = Modifier.height(12.dp))
            if (editingSection == PersonalEditSection.Weight) {
                OnboardingTextField("Target Weight", targetWeight, { targetWeight = it }, suffix = "kg")
            } else {
                ReadOnlyField("Target Weight", "${profile.targetWeight} kg")
            }

            Spacer(modifier = Modifier.height(24.dp))
            SectionHeader(
                title = "Step 4 — Nutrition",
                isEditing = editingSection == PersonalEditSection.Nutrition,
                onEdit = { editingSection = PersonalEditSection.Nutrition },
                onCancel = { resetSectionDrafts(); editingSection = null },
                onConfirm = {
                    onSave(
                        profile.targetWeight,
                        dailyCalories.toIntOrNull() ?: profile.dailyCalories,
                        protein.toIntOrNull() ?: profile.protein,
                        carbs.toIntOrNull() ?: profile.carbs,
                        fat.toIntOrNull() ?: profile.fat,
                        fiber.toIntOrNull() ?: profile.fiber,
                        null, profile.cuisinePreferences
                    )
                    editingSection = null
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (editingSection == PersonalEditSection.Nutrition) {
                OnboardingTextField("Daily Calories", dailyCalories, {
                    dailyCalories = it
                    val cal = it.toIntOrNull()
                    val cw = profile.currentWeight
                    if (cal != null) {
                        val macros = FitnessCalculator.calculateMacros(cw, cal, profile.goal)
                        protein = macros.protein.toString()
                        carbs = macros.carbs.toString()
                        fat = macros.fat.toString()
                        fiber = macros.fiber.toString()
                    }
                }, suffix = "kcal")
                Spacer(modifier = Modifier.height(12.dp))
                MacroEditRow("Protein", protein, onValueChange = { v -> if (v.all { it.isDigit() }) rebalance(FitnessCalculator.MacroField.PROTEIN, v.toIntOrNull() ?: 0) })
                Spacer(modifier = Modifier.height(8.dp))
                MacroEditRow("Carbs", carbs, onValueChange = { v -> if (v.all { it.isDigit() }) rebalance(FitnessCalculator.MacroField.CARBS, v.toIntOrNull() ?: 0) })
                Spacer(modifier = Modifier.height(8.dp))
                MacroEditRow("Fat", fat, onValueChange = { v -> if (v.all { it.isDigit() }) rebalance(FitnessCalculator.MacroField.FAT, v.toIntOrNull() ?: 0) })
                Spacer(modifier = Modifier.height(8.dp))
                MacroEditRow("Fiber", fiber, onValueChange = { v -> if (v.all { it.isDigit() }) rebalance(FitnessCalculator.MacroField.FIBER, v.toIntOrNull() ?: 0) })
            } else {
                ReadOnlyField("Daily Calories", "${profile.dailyCalories} kcal")
                ReadOnlyField("Protein", "${profile.protein} g")
                ReadOnlyField("Carbs", "${profile.carbs} g")
                ReadOnlyField("Fat", "${profile.fat} g")
                ReadOnlyField("Fiber", "${profile.fiber} g")
            }

            Spacer(modifier = Modifier.height(24.dp))
            DietTimelineSection(profile)
            Spacer(modifier = Modifier.height(24.dp))
            WorkoutProfileSection(
                profile = profile,
                isEditMode = editingSection == PersonalEditSection.Workout,
                sectionHeader = {
                    SectionHeader(
                        title = "Workout Profile",
                        isEditing = editingSection == PersonalEditSection.Workout,
                        onEdit = { editingSection = PersonalEditSection.Workout },
                        onCancel = { resetSectionDrafts(); editingSection = null },
                        onConfirm = {
                            onSaveWorkout(
                                fitnessLevel, benchmarkSkipped,
                                if (benchmarkSkipped) 0f else squat1Rm.toFloatOrNull() ?: 0f,
                                if (benchmarkSkipped) 0f else bench1Rm.toFloatOrNull() ?: 0f,
                                if (benchmarkSkipped) 0f else deadlift1Rm.toFloatOrNull() ?: 0f,
                                workoutDays, weekStartDay,
                                WorkoutPreferences.serializeEquipment(selectedEquipment),
                                gymLocation
                            )
                            editingSection = null
                        }
                    )
                },
                fitnessLevel = fitnessLevel,
                onFitnessLevel = { fitnessLevel = it },
                benchmarkSkipped = benchmarkSkipped,
                onBenchmarkSkipped = { benchmarkSkipped = it },
                squat1Rm = squat1Rm,
                onSquat1Rm = { squat1Rm = it },
                bench1Rm = bench1Rm,
                onBench1Rm = { bench1Rm = it },
                deadlift1Rm = deadlift1Rm,
                onDeadlift1Rm = { deadlift1Rm = it },
                workoutDays = workoutDays,
                onWorkoutDays = { workoutDays = it },
                weekStartDay = weekStartDay,
                onWeekStartDay = { weekStartDay = it },
                selectedEquipment = selectedEquipment,
                onToggleEquipment = {
                    selectedEquipment = WorkoutPreferences.toggleEquipmentSelection(selectedEquipment, it)
                },
                gymLocation = gymLocation,
                onGymLocation = { gymLocation = it }
            )
            Spacer(modifier = Modifier.height(24.dp))
            SectionHeader(
                title = "Step 6 — Food Preferences",
                isEditing = editingSection == PersonalEditSection.Cuisines,
                onEdit = { editingSection = PersonalEditSection.Cuisines },
                onCancel = { resetSectionDrafts(); editingSection = null },
                onConfirm = {
                    onSave(
                        profile.targetWeight, profile.dailyCalories, profile.protein, profile.carbs, profile.fat, profile.fiber,
                        null, CuisineTypes.serializePreferences(selectedCuisines.toList())
                    )
                    editingSection = null
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (editingSection == PersonalEditSection.Cuisines) {
                CuisineTypes.ALL.forEach { cuisine ->
                    GoalCard(title = cuisine, isSelected = cuisine in selectedCuisines) {
                        selectedCuisines = if (cuisine in selectedCuisines) selectedCuisines - cuisine else selectedCuisines + cuisine
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            } else {
                ReadOnlyField("Preferred Cuisines", CuisineTypes.parsePreferences(profile.cuisinePreferences).joinToString(", ").ifBlank { "None" })
            }
            
            if (profile.goal != "Maintain Weight" && weeklyItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeader(
                    title = "Target Weight Change / Week",
                    isEditing = editingSection == PersonalEditSection.Weekly,
                    onEdit = { editingSection = PersonalEditSection.Weekly },
                    onCancel = { resetSectionDrafts(); editingSection = null },
                    onConfirm = {
                        val change = weeklyItems.getOrNull(weeklyIndex)?.substringBefore(" ")?.toFloatOrNull()
                        val validation = change?.let { ProfileValidation.validateWeeklyChange(profile.goal, profile.currentWeight, it) }
                        if (validation != null && !validation.isValid) {
                            weeklyWarning = validation.message
                        } else {
                            onSave(
                                profile.targetWeight, profile.dailyCalories, profile.protein, profile.carbs, profile.fat, profile.fiber,
                                change, profile.cuisinePreferences
                            )
                            editingSection = null
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (editingSection == PersonalEditSection.Weekly) {
                    WheelPicker(items = weeklyItems, selectedIndex = weeklyIndex.coerceIn(0, weeklyItems.lastIndex), onSelected = {
                        weeklyIndex = it
                        val change = weeklyItems[it].substringBefore(" ").toFloatOrNull() ?: 0f
                        weeklyWarning = ProfileValidation.validateWeeklyChange(profile.goal, profile.currentWeight, change).message
                    })
                    weeklyWarning?.let { Text(it, color = Error, style = Typography.bodySmall) }
                } else {
                    ReadOnlyField("Target Change", "${String.format("%.2f", profile.targetWeightChangePerWeek)} kg/wk")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PersonalDetailsScreenPreview() {
    val mockProfile = UserProfile(onboardingComplete = true)
    MyApplicationTheme {
        PersonalDetailsScreenContent(
            profile = mockProfile,
            onBack = {},
            onSave = { _, _, _, _, _, _, _, _ -> }
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    isEditing: Boolean,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = Typography.labelMedium, color = OnSurfaceVariant)
        if (isEditing) {
            Row {
                TextButton(onClick = onCancel) { Text("Cancel", color = Error) }
                TextButton(onClick = onConfirm) { Text("Confirm", color = Primary) }
            }
        } else {
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Edit, "Edit", tint = Primary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun DietTimelineSection(profile: UserProfile) {
    Text("Step 5 — Diet Timeline", style = Typography.labelMedium, color = OnSurfaceVariant)
    Spacer(modifier = Modifier.height(8.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(0.05f), RoundedCornerShape(12.dp))
            .border(1.dp, OutlineVariant.copy(0.3f), RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ReadOnlyField("Target Weight Change / Week", "${String.format("%.2f", profile.targetWeightChangePerWeek)} kg")
        ReadOnlyField("Weeks to Goal", if (profile.weeksToGoal > 0) "${profile.weeksToGoal} weeks" else "At goal")
        ReadOnlyField("Maintenance Calories", "${profile.maintenanceCalories} kcal/day")
        val adjLabel = when {
            profile.calorieAdjustmentDaily < 0 -> "Deficit"
            profile.calorieAdjustmentDaily > 0 -> "Surplus"
            else -> "Balance"
        }
        ReadOnlyField(
            "Calorie Adjustment",
            "$adjLabel: ${kotlin.math.abs(profile.calorieAdjustmentDaily)} kcal/day (${kotlin.math.abs(profile.calorieAdjustmentWeekly)} kcal/week)"
        )
    }
}

@Composable
private fun WorkoutProfileSection(
    profile: UserProfile,
    isEditMode: Boolean,
    sectionHeader: @Composable () -> Unit = {},
    fitnessLevel: String,
    onFitnessLevel: (String) -> Unit,
    benchmarkSkipped: Boolean,
    onBenchmarkSkipped: (Boolean) -> Unit,
    squat1Rm: String,
    onSquat1Rm: (String) -> Unit,
    bench1Rm: String,
    onBench1Rm: (String) -> Unit,
    deadlift1Rm: String,
    onDeadlift1Rm: (String) -> Unit,
    workoutDays: Int,
    onWorkoutDays: (Int) -> Unit,
    weekStartDay: Int,
    onWeekStartDay: (Int) -> Unit,
    selectedEquipment: Set<String>,
    onToggleEquipment: (String) -> Unit,
    gymLocation: String,
    onGymLocation: (String) -> Unit
) {
    sectionHeader()
    Spacer(modifier = Modifier.height(8.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(0.05f), RoundedCornerShape(12.dp))
            .border(1.dp, OutlineVariant.copy(0.3f), RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isEditMode) {
            Text("Fitness Level", style = Typography.bodySmall, color = OnSurfaceVariant)
            WorkoutPreferences.FITNESS_LEVELS.forEach { level ->
                GoalCard(title = level, isSelected = fitnessLevel == level) { onFitnessLevel(level) }
                Spacer(Modifier.height(4.dp))
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = benchmarkSkipped, onCheckedChange = onBenchmarkSkipped)
                Text("I'm a beginner — skip 1RM benchmarks", style = Typography.bodySmall, color = OnSurface)
            }
            if (!benchmarkSkipped) {
                OnboardingTextField("Squat 1RM", squat1Rm, onSquat1Rm, suffix = "kg")
                OnboardingTextField("Bench Press 1RM", bench1Rm, onBench1Rm, suffix = "kg")
                OnboardingTextField("Deadlift 1RM", deadlift1Rm, onDeadlift1Rm, suffix = "kg")
            }
            Text("Workout Frequency", style = Typography.bodySmall, color = OnSurfaceVariant)
            WorkoutPreferences.WORKOUT_FREQUENCY_OPTIONS.forEach { option ->
                GoalCard(title = option.label, isSelected = workoutDays == option.daysPerWeek) {
                    onWorkoutDays(option.daysPerWeek)
                }
                Spacer(Modifier.height(4.dp))
            }
            Text("Week Starts On", style = Typography.bodySmall, color = OnSurfaceVariant)
            WorkoutPreferences.WEEK_DAYS.forEach { day ->
                GoalCard(title = day.label, isSelected = weekStartDay == day.calendarDay) {
                    onWeekStartDay(day.calendarDay)
                }
                Spacer(Modifier.height(4.dp))
            }
            Text("Equipment", style = Typography.bodySmall, color = OnSurfaceVariant)
            WorkoutPreferences.EQUIPMENT_OPTIONS.forEach { eq ->
                GoalCard(title = eq.label, subtitle = eq.group, isSelected = eq.id in selectedEquipment) {
                    onToggleEquipment(eq.id)
                }
                Spacer(Modifier.height(4.dp))
            }
            Text("Training Location", style = Typography.bodySmall, color = OnSurfaceVariant)
            WorkoutPreferences.GYM_LOCATIONS.forEach { loc ->
                GoalCard(title = loc.label, subtitle = loc.description, isSelected = gymLocation == loc.id) {
                    onGymLocation(loc.id)
                }
                Spacer(Modifier.height(4.dp))
            }
        } else if (profile.workoutSetupComplete) {
            ReadOnlyField("Fitness Level", profile.fitnessLevel.ifBlank { "—" })
            ReadOnlyField("Strength Benchmark", WorkoutPreferences.strengthSummary(profile))
            ReadOnlyField("Workout Frequency", WorkoutPreferences.estimatedFrequencyLabel(profile.workoutDaysPerWeek))
            ReadOnlyField("Week Starts On", WorkoutPreferences.weekDayLabel(profile.weekStartDay))
            ReadOnlyField("Equipment", WorkoutPreferences.equipmentDisplay(profile.equipmentSelection))
            ReadOnlyField("Training Location", WorkoutPreferences.gymLocationLabel(profile.gymLocation))
            val total = WorkoutPreferences.totalStrengthKg(profile)
            if (!profile.benchmarkSkipped && total > 0) {
                ReadOnlyField("Combined 1RM Total", "${total.toInt()} kg")
            }
        } else {
            Text("Complete workout setup from the Workout tab flow on first launch.", style = Typography.bodySmall, color = OnSurfaceVariant)
        }
    }
}

@Composable
private fun PersonalMetricsCard(profile: UserProfile) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(0.05f), RoundedCornerShape(12.dp))
            .border(1.dp, OutlineVariant.copy(0.3f), RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ReadOnlyField("Age", "${profile.age} years")
        ReadOnlyField("Gender", profile.gender)
        ReadOnlyField("Height", "${profile.height} cm")
    }
}

@Composable
private fun ActivityGoalCard(profile: UserProfile) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(0.05f), RoundedCornerShape(12.dp))
            .border(1.dp, OutlineVariant.copy(0.3f), RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ReadOnlyField("Activity Level", profile.activityLevel)
        ReadOnlyField("Primary Goal", profile.goal)
    }
}

@Composable
private fun ReadOnlyField(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = Typography.bodyMedium, color = OnSurfaceVariant)
        Text(value, style = Typography.bodyMedium, color = OnSurface)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: GymViewModel, onBack: () -> Unit) {
    AiSettingsScreen(viewModel = viewModel, onBack = onBack)
}

@Composable
internal fun ApiKeyCard(
    label: String,
    currentKey: String?,
    showInput: Boolean,
    keyInput: String,
    isKeyVisible: Boolean,
    onKeyInputChange: (String) -> Unit,
    onShowInput: () -> Unit,
    onSave: () -> Unit,
    onToggleVisibility: () -> Unit,
    onEdit: () -> Unit,
    onClear: () -> Unit,
    authenticate: (() -> Unit) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerHighest.copy(0.3f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            val hasKey = !currentKey.isNullOrBlank()
            if (showInput) {
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = onKeyInputChange,
                    label = { Text(label) },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = onSave) {
                            Icon(Icons.Default.Save, null, tint = Primary)
                        }
                    }
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasKey) {
                        Text(
                            if (isKeyVisible) currentKey!! else "••••••••••••",
                            color = OnSurface,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Text("No API Key Set", color = OnSurfaceVariant, modifier = Modifier.weight(1f))
                    }
                    Row {
                        if (hasKey) {
                            IconButton(onClick = { authenticate(onToggleVisibility) }) {
                                Icon(
                                    if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    null,
                                    tint = Primary
                                )
                            }
                            IconButton(onClick = { authenticate(onEdit) }) {
                                Icon(Icons.Default.Edit, null, tint = Primary)
                            }
                            IconButton(onClick = { authenticate(onClear) }) {
                                Icon(Icons.Default.Delete, null, tint = Error)
                            }
                        } else {
                            Button(onClick = onShowInput) { Text("Add Key") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModelDownloadCard(
    title: String,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    progress: Float,
    isCompatible: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerHighest.copy(0.3f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = Typography.bodyLarge, color = OnSurface)
                    if (isDownloaded) Text("Downloaded — select from OFFLINE MODEL above", style = Typography.labelSmall, color = Secondary)
                }
                if (isDownloaded) {
                    IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = Error) }
                } else if (isDownloading) {
                    IconButton(onClick = onCancel) { Icon(Icons.Default.Close, null, tint = OnSurfaceVariant) }
                } else if (isCompatible) {
                    IconButton(onClick = onDownload) { Icon(Icons.Default.Download, null, tint = Primary) }
                }
            }
            if (isDownloading) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = Primary,
                    trackColor = SurfaceContainerHighest
                )
                Text("${(progress * 100).toInt()}%", style = Typography.labelSmall, modifier = Modifier.align(Alignment.End))
            } else {
                Text(
                    when {
                        isDownloaded -> "Ready — use the OFFLINE MODEL picker to activate"
                        isCompatible -> "Tap download to install"
                        else -> "Needs more RAM for this model"
                    },
                    style = Typography.bodySmall,
                    color = if (isDownloaded) Secondary else OnSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ModelCard(
    title: String,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    progress: Float,
    isCompatible: Boolean,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isDownloaded) { onSelect() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Primary.copy(0.1f) else SurfaceContainerHighest.copy(0.3f)
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, Primary) else null,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = Typography.bodyLarge, color = if (isSelected) Primary else OnSurface)
                    if (isSelected) Text("Currently Active", style = Typography.labelSmall, color = Primary)
                }
                if (isDownloaded) {
                    IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = Error) }
                } else if (isDownloading) {
                    IconButton(onClick = onCancel) { Icon(Icons.Default.Close, null, tint = OnSurfaceVariant) }
                } else if (isCompatible) {
                    IconButton(onClick = onDownload) { Icon(Icons.Default.Download, null, tint = Primary) }
                }
            }
            if (isDownloading) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = Primary,
                    trackColor = SurfaceContainerHighest
                )
                Text("${(progress * 100).toInt()}%", style = Typography.labelSmall, modifier = Modifier.align(Alignment.End))
            } else {
                Text(
                    when {
                        isDownloaded -> "Downloaded & ready for on-device AI"
                        isCompatible -> "Tap download to install"
                        else -> "Needs more RAM for this model"
                    },
                    style = Typography.bodySmall,
                    color = if (isDownloaded) Secondary else OnSurfaceVariant
                )
            }
        }
    }
}
