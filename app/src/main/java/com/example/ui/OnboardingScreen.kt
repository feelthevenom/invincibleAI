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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.GymViewModel
import com.example.data.CuisineTypes
import com.example.data.FitnessCalculator
import com.example.data.ProfileValidation
import com.example.data.UserProfile
import com.example.ui.theme.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Restore
import androidx.compose.ui.platform.LocalContext
import com.example.data.backup.AppRelaunch
import com.example.data.backup.BackupRestoreManager
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun OnboardingScreen(
    viewModel: GymViewModel,
    onComplete: () -> Unit
) {
    OnboardingScreenContent(
        onComplete = { profile ->
            viewModel.completeOnboarding(profile)
            onComplete()
        }
    )
}

@Composable
fun OnboardingScreenContent(onComplete: (UserProfile) -> Unit) {
    val totalSteps = 6
    var step by remember { mutableIntStateOf(1) }
    var progress by remember { mutableFloatStateOf(1f / totalSteps) }

    var ageIndex by remember { mutableIntStateOf(25 - ProfileValidation.MIN_AGE) }
    var gender by remember { mutableStateOf("Male") }
    var useMetricHeight by remember { mutableStateOf(true) }
    var heightCmIndex by remember { mutableIntStateOf(170 - ProfileValidation.MIN_HEIGHT_CM) }
    var feetIndex by remember { mutableIntStateOf(2) }
    var inchIndex by remember { mutableIntStateOf(7) }

    var activityLevel by remember { mutableStateOf("Moderately Active") }
    var goal by remember { mutableStateOf("General Fitness") }

    fun heightCm(): Int = if (useMetricHeight) {
        ProfileValidation.MIN_HEIGHT_CM + heightCmIndex
    } else {
        ProfileValidation.cmFromFeetInches(3 + feetIndex, inchIndex)
    }

    val weightRange = remember(heightCm()) {
        ProfileValidation.minReasonableWeight(heightCm()) to ProfileValidation.maxReasonableWeight(heightCm())
    }
    val weightItems = remember(weightRange) { weightKgItems(weightRange.first, weightRange.second) }
    var currentWeightIndex by remember(weightItems) { mutableIntStateOf(weightItems.indexOfFirst { it.startsWith("70 ") }.coerceAtLeast(0)) }
    var targetWeightIndex by remember(weightItems) { mutableIntStateOf(currentWeightIndex) }

    var dailyCalories by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var fiber by remember { mutableStateOf("") }
    var workoutDays by remember { mutableStateOf("3") }

    var targetChangePerWeek by remember { mutableFloatStateOf(0.5f) }
    var weeklyChangeIndex by remember { mutableIntStateOf(0) }
    var weeklyWarning by remember { mutableStateOf<String?>(null) }
    var weeksToGoal by remember { mutableIntStateOf(0) }
    var maintenanceCalories by remember { mutableIntStateOf(0) }
    var calorieAdjustmentDaily by remember { mutableIntStateOf(0) }
    var calorieAdjustmentWeekly by remember { mutableIntStateOf(0) }

    var selectedCuisines by remember { mutableStateOf(setOf("South Indian")) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var restoreMessage by remember { mutableStateOf<String?>(null) }
    var restoreLoading by remember { mutableStateOf(false) }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        restoreLoading = true
        restoreMessage = null
        coroutineScope.launch {
            when (val validation = BackupRestoreManager.validateBackup(context, uri)) {
                BackupRestoreManager.BackupResult.Success -> {
                    when (val import = BackupRestoreManager.importBackup(context, uri)) {
                        BackupRestoreManager.BackupResult.Success -> AppRelaunch.afterRestore(context)
                        is BackupRestoreManager.BackupResult.Error -> {
                            restoreLoading = false
                            restoreMessage = import.message
                        }
                    }
                }
                is BackupRestoreManager.BackupResult.Error -> {
                    restoreLoading = false
                    restoreMessage = validation.message
                }
            }
        }
    }

    fun currentWeightKg(): Float = weightItems.getOrNull(currentWeightIndex)?.substringBefore(" ")?.toFloatOrNull()
        ?: ProfileValidation.MIN_WEIGHT_KG
    fun targetWeightKg(): Float = weightItems.getOrNull(targetWeightIndex)?.substringBefore(" ")?.toFloatOrNull()
        ?: currentWeightKg()

    fun recalculateStep5Metrics() {
        val cw = currentWeightKg()
        val tw = targetWeightKg()
        val a = ProfileValidation.MIN_AGE + ageIndex
        val h = heightCm()
        val cal = dailyCalories.toIntOrNull() ?: 0
        val weeklyItems = weeklyChangeItems(ProfileValidation.minWeeklyChangeKg(goal), ProfileValidation.maxWeeklyChangeKg(goal, cw))
        val change = weeklyItems.getOrNull(weeklyChangeIndex)?.substringBefore(" ")?.toFloatOrNull() ?: targetChangePerWeek
        targetChangePerWeek = change
        val validation = ProfileValidation.validateWeeklyChange(goal, cw, change)
        weeklyWarning = validation.message
        weeksToGoal = FitnessCalculator.weeksToReachGoal(cw, tw, if (validation.isValid) change else 0f)
        maintenanceCalories = FitnessCalculator.tdee(gender, cw, h, a, activityLevel)
        calorieAdjustmentDaily = FitnessCalculator.calorieAdjustmentFromWeeklyChange(goal, change)
        calorieAdjustmentWeekly = calorieAdjustmentDaily * 7
        dailyCalories = FitnessCalculator.dailyCaloriesFromWeeklyChange(maintenanceCalories, goal, change).toString()
        val macros = FitnessCalculator.calculateMacros(cw, dailyCalories.toIntOrNull() ?: 0, goal)
        protein = macros.protein.toString()
        carbs = macros.carbs.toString()
        fat = macros.fat.toString()
        fiber = macros.fiber.toString()
    }

    LaunchedEffect(step, goal, currentWeightIndex, targetWeightIndex, weeklyChangeIndex, dailyCalories) {
        progress = step / totalSteps.toFloat()
        when (step) {
            3 -> {
                val cw = currentWeightKg()
                val calculated = FitnessCalculator.calculateTargetWeight(gender, heightCm(), cw, goal)
                val idx = weightItems.indexOfFirst { it.startsWith("${calculated.toInt()} ") }
                if (idx >= 0) targetWeightIndex = idx
            }
            4 -> {
                val cw = currentWeightKg()
                val a = ProfileValidation.MIN_AGE + ageIndex
                val h = heightCm()
                val cal = FitnessCalculator.dailyCalories(gender, cw, h, a, activityLevel, goal)
                val macros = FitnessCalculator.calculateMacros(cw, cal, goal)
                if (dailyCalories.isBlank()) dailyCalories = cal.toString()
                if (protein.isBlank()) protein = macros.protein.toString()
                if (carbs.isBlank()) carbs = macros.carbs.toString()
                if (fat.isBlank()) fat = macros.fat.toString()
                if (fiber.isBlank()) fiber = macros.fiber.toString()
            }
            5 -> {
                val cw = currentWeightKg()
                val items = weeklyChangeItems(ProfileValidation.minWeeklyChangeKg(goal), ProfileValidation.maxWeeklyChangeKg(goal, cw))
                if (weeklyChangeIndex >= items.size) weeklyChangeIndex = 0
                if (goal == "Maintain Weight") weeklyChangeIndex = 0
                recalculateStep5Metrics()
            }
        }
    }

    fun recalculateTargetWeight() {
        val cw = currentWeightKg()
        val calculated = FitnessCalculator.calculateTargetWeight(gender, heightCm(), cw, goal)
        val idx = weightItems.indexOfFirst { it.startsWith("${calculated.toInt()} ") }
        if (idx >= 0) targetWeightIndex = idx
    }

    fun rebalanceMacro(field: FitnessCalculator.MacroField, newValue: Int) {
        val cal = dailyCalories.toIntOrNull() ?: return
        val result = FitnessCalculator.rebalanceMacros(
            totalCalories = cal,
            protein = protein.toIntOrNull() ?: 0,
            carbs = carbs.toIntOrNull() ?: 0,
            fat = fat.toIntOrNull() ?: 0,
            fiber = fiber.toIntOrNull() ?: 0,
            changedField = field,
            newValue = newValue
        )
        protein = result.protein.toString()
        carbs = result.carbs.toString()
        fat = result.fat.toString()
        fiber = result.fiber.toString()
    }

    fun canProceed(): Boolean = when (step) {
        1 -> ProfileValidation.isValidAge(ProfileValidation.MIN_AGE + ageIndex) &&
            ProfileValidation.isValidHeightCm(heightCm())
        2 -> activityLevel.isNotBlank() && goal.isNotBlank()
        3 -> ProfileValidation.isValidWeight(currentWeightKg(), heightCm()) &&
            ProfileValidation.isValidTargetWeight(targetWeightKg(), currentWeightKg(), heightCm(), ProfileValidation.MIN_AGE + ageIndex)
        4 -> dailyCalories.toIntOrNull()?.let { it in 800..6000 } == true
        5 -> workoutDays.toIntOrNull()?.let { it in 1..7 } == true &&
            ProfileValidation.validateWeeklyChange(goal, currentWeightKg(), targetChangePerWeek).isValid
        6 -> selectedCuisines.isNotEmpty()
        else -> false
    }

    fun finishOnboarding() {
        recalculateStep5Metrics()
        onComplete(
            UserProfile(
                age = ProfileValidation.MIN_AGE + ageIndex,
                gender = gender,
                height = heightCm(),
                activityLevel = activityLevel,
                goal = goal,
                currentWeight = currentWeightKg(),
                targetWeight = targetWeightKg(),
                dailyCalories = dailyCalories.toIntOrNull() ?: 0,
                protein = protein.toIntOrNull() ?: 0,
                carbs = carbs.toIntOrNull() ?: 0,
                fat = fat.toIntOrNull() ?: 0,
                fiber = fiber.toIntOrNull() ?: 0,
                workoutDaysPerWeek = workoutDays.toIntOrNull() ?: 3,
                targetWeightChangePerWeek = targetChangePerWeek,
                weeksToGoal = weeksToGoal,
                maintenanceCalories = maintenanceCalories,
                calorieAdjustmentDaily = calorieAdjustmentDaily,
                calorieAdjustmentWeekly = calorieAdjustmentWeekly,
                cuisinePreferences = CuisineTypes.serializePreferences(selectedCuisines.toList()),
                onboardingComplete = true
            )
        )
    }

    val animatedProgress by animateFloatAsState(targetValue = progress, animationSpec = tween(700), label = "prog")

    Box(modifier = Modifier.fillMaxSize().background(Background)) {
        AsyncImage(
            model = "https://lh3.googleusercontent.com/aida-public/AB6AXuBp6GjmmBl_imRCmTf5ECwQt6wFdFOHYNCCwSVpTo7osLw_5V8IGDPGYz3cRm8Y-sfBkvUy6YMM1N3-BofW2j3Z9AUNRn9jHRR0SCb-or8AbA4xx_LF0gWmajiqczmeXgYsORQqZ1Olt9B0X0MOAifCZRVd7NcMvainC-R7HXsBzZAih7Wy2qOlqe6VDhYRQfzvpYCKIC9grKw52m86I9XnJ_oYVByl3Syv-_zBNCDEN6NRZSHXXCKKc6ynhIVjHSevCveVyEr7gC32",
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.align(Alignment.TopEnd).size(400.dp).offset(x = 100.dp, y = (-100).dp),
            alpha = 0.1f
        )

        Column(modifier = Modifier.fillMaxSize()) {
            Column {
                Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(SurfaceContainerHighest)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(animatedProgress).background(Primary))
                }
                Row(
                    modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { if (step > 1) step-- }, enabled = step > 1) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = if (step > 1) OnSurface else OnSurface.copy(0.3f))
                    }
                    Text("STEP $step OF $totalSteps", style = Typography.labelMedium, color = OnSurfaceVariant)
                    Spacer(modifier = Modifier.width(48.dp))
                }
            }

            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                when (step) {
                    1 -> {
                        Step1Pickers(ageIndex, { ageIndex = it }, gender, { gender = it }, useMetricHeight, { useMetricHeight = it }, heightCmIndex, { heightCmIndex = it }, feetIndex, { feetIndex = it }, inchIndex, { inchIndex = it })
                        Spacer(modifier = Modifier.height(24.dp))
                        HorizontalDivider(color = OutlineVariant.copy(0.3f))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Already have a backup?",
                            style = Typography.titleMedium,
                            color = OnSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Restore gymai_backup.zip to skip setup and open the app with your saved data.",
                            style = Typography.bodySmall,
                            color = OnSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                restoreLauncher.launch(
                                    arrayOf(BackupRestoreManager.BACKUP_MIME, "application/octet-stream")
                                )
                            },
                            enabled = !restoreLoading,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Secondary)
                        ) {
                            if (restoreLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Secondary
                                )
                                Spacer(Modifier.width(12.dp))
                                Text("Restoring…")
                            } else {
                                Icon(Icons.Default.Restore, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Restore from backup")
                            }
                        }
                        restoreMessage?.let { msg ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(msg, color = Error, style = Typography.bodySmall)
                        }
                    }
                    2 -> Step2Content(activityLevel, { activityLevel = it }, goal, { goal = it; recalculateTargetWeight() })
                    3 -> Step3Pickers(weightItems, currentWeightIndex, { currentWeightIndex = it; recalculateTargetWeight() }, targetWeightIndex, { targetWeightIndex = it }, heightCm())
                    4 -> Step4Content(dailyCalories, { v -> dailyCalories = v; val cal = v.toIntOrNull(); val cw = currentWeightKg(); if (cal != null) { val m = FitnessCalculator.calculateMacros(cw, cal, goal); protein = m.protein.toString(); carbs = m.carbs.toString(); fat = m.fat.toString(); fiber = m.fiber.toString() } }, protein, { v -> rebalanceMacro(FitnessCalculator.MacroField.PROTEIN, v.toIntOrNull() ?: 0) }, carbs, { v -> rebalanceMacro(FitnessCalculator.MacroField.CARBS, v.toIntOrNull() ?: 0) }, fat, { v -> rebalanceMacro(FitnessCalculator.MacroField.FAT, v.toIntOrNull() ?: 0) }, fiber, { v -> rebalanceMacro(FitnessCalculator.MacroField.FIBER, v.toIntOrNull() ?: 0) })
                    5 -> Step5Pickers(workoutDays, { workoutDays = it }, goal, currentWeightKg(), weeklyChangeIndex, { weeklyChangeIndex = it; recalculateStep5Metrics() }, weeklyWarning, weeksToGoal, maintenanceCalories, calorieAdjustmentDaily, calorieAdjustmentWeekly, targetChangePerWeek)
                    6 -> Step6Cuisine(selectedCuisines, { selectedCuisines = it })
                }
            }

            Box(Modifier.fillMaxWidth().background(Background.copy(0.95f)).border(1.dp, OutlineVariant.copy(0.2f)).padding(20.dp)) {
                Button(
                    onClick = { if (step < totalSteps) step++ else finishOnboarding() },
                    enabled = canProceed(),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = OnPrimary)
                ) {
                    Text(if (step < totalSteps) "Continue" else "Complete Setup", style = Typography.headlineMedium.copy(fontSize = 18.sp))
                }
            }
        }
    }
}

@Composable
private fun Step1Pickers(
    ageIndex: Int, onAgeIndex: (Int) -> Unit,
    gender: String, onGender: (String) -> Unit,
    useMetric: Boolean, onMetricToggle: (Boolean) -> Unit,
    heightCmIndex: Int, onHeightCmIndex: (Int) -> Unit,
    feetIndex: Int, onFeetIndex: (Int) -> Unit,
    inchIndex: Int, onInchIndex: (Int) -> Unit
) {
    Text("Tell us about yourself", style = Typography.headlineLarge, color = Primary)
    Spacer(modifier = Modifier.height(8.dp))
    Text("Scroll to select your age and height.", style = Typography.bodyMedium, color = OnSurfaceVariant)
    Spacer(modifier = Modifier.height(24.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        WheelPicker(items = ageItems(), selectedIndex = ageIndex, onSelected = onAgeIndex, modifier = Modifier.weight(1f), label = "Age")
        OnboardingDropdown("Gender", gender, FitnessCalculator.genders, onGender, Modifier.weight(1f))
    }
    Spacer(modifier = Modifier.height(20.dp))
    HeightUnitToggle(useMetric, onMetricToggle)
    Spacer(modifier = Modifier.height(12.dp))
    if (useMetric) {
        WheelPicker(items = cmHeightItems(), selectedIndex = heightCmIndex, onSelected = onHeightCmIndex, label = "Height")
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WheelPicker(items = feetItems(), selectedIndex = feetIndex, onSelected = onFeetIndex, modifier = Modifier.weight(1f), label = "Feet")
            WheelPicker(items = inchItems(), selectedIndex = inchIndex, onSelected = onInchIndex, modifier = Modifier.weight(1f), label = "Inches")
        }
    }
}

@Composable
private fun Step3Pickers(
    weightItems: List<String>,
    currentIndex: Int, onCurrentIndex: (Int) -> Unit,
    targetIndex: Int, onTargetIndex: (Int) -> Unit,
    heightCm: Int
) {
    Text("Your weight goals", style = Typography.headlineLarge, color = Primary)
    Spacer(modifier = Modifier.height(8.dp))
    Text("Reasonable range for ${heightCm}cm: ${ProfileValidation.minReasonableWeight(heightCm).toInt()}–${ProfileValidation.maxReasonableWeight(heightCm).toInt()} kg", style = Typography.bodySmall, color = OnSurfaceVariant)
    Spacer(modifier = Modifier.height(24.dp))
    WheelPicker(items = weightItems, selectedIndex = currentIndex.coerceIn(0, weightItems.lastIndex), onSelected = onCurrentIndex, label = "Current Weight")
    Spacer(modifier = Modifier.height(16.dp))
    WheelPicker(items = weightItems, selectedIndex = targetIndex.coerceIn(0, weightItems.lastIndex), onSelected = onTargetIndex, label = "Target Weight")
}

@Composable
private fun Step5Pickers(
    workoutDays: String, onWorkoutDaysChange: (String) -> Unit,
    goal: String, currentWeight: Float,
    weeklyIndex: Int, onWeeklyIndex: (Int) -> Unit,
    weeklyWarning: String?,
    weeksToGoal: Int, maintenanceCalories: Int,
    calorieAdjustmentDaily: Int, calorieAdjustmentWeekly: Int,
    targetChangePerWeek: Float
) {
    Text("Workout & timeline", style = Typography.headlineLarge, color = Primary)
    Spacer(modifier = Modifier.height(8.dp))
    Text("Adjust your weekly target within safe limits.", style = Typography.bodyMedium, color = OnSurfaceVariant)
    Spacer(modifier = Modifier.height(24.dp))
    OnboardingTextField("Workout Days Per Week", workoutDays, { v -> if (v.all { it.isDigit() } && (v.isEmpty() || v.toIntOrNull()?.let { it <= 7 } == true)) onWorkoutDaysChange(v) }, suffix = "days")
    Spacer(modifier = Modifier.height(20.dp))
    val weeklyItems = weeklyChangeItems(ProfileValidation.minWeeklyChangeKg(goal), ProfileValidation.maxWeeklyChangeKg(goal, currentWeight))
    if (goal != "Maintain Weight" && weeklyItems.isNotEmpty()) {
        Text("TARGET WEIGHT CHANGE / WEEK", style = Typography.labelMedium, color = OnSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        WheelPicker(items = weeklyItems, selectedIndex = weeklyIndex.coerceIn(0, weeklyItems.lastIndex), onSelected = onWeeklyIndex)
        weeklyWarning?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, color = Error, style = Typography.bodySmall)
        }
        val estimatedDeficit = ProfileValidation.estimatedDailyDeficitForWeeklyChange(targetChangePerWeek)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Estimated daily deficit for this rate: ~$estimatedDeficit kcal (7700 kcal ≈ 1 kg)", style = Typography.bodySmall, color = OnSurfaceVariant)
    }
    Spacer(modifier = Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(1f)) { OnboardingInfoCard("Target / Week", "${String.format("%.1f", targetChangePerWeek)} kg") }
        Box(Modifier.weight(1f)) { OnboardingInfoCard("Weeks to Goal", if (weeksToGoal > 0) "$weeksToGoal weeks" else "At goal", highlight = true) }
    }
    Spacer(modifier = Modifier.height(12.dp))
    OnboardingInfoCard("Maintenance Calories", "$maintenanceCalories kcal/day", "To maintain current weight")
    Spacer(modifier = Modifier.height(12.dp))
    val adjLabel = if (calorieAdjustmentDaily < 0) "Calorie Deficit" else if (calorieAdjustmentDaily > 0) "Calorie Surplus" else "Calorie Balance"
    OnboardingInfoCard(adjLabel, "${abs(calorieAdjustmentDaily)} kcal/day", "Weekly: ${abs(calorieAdjustmentWeekly)} kcal", highlight = calorieAdjustmentDaily != 0)
}

@Composable
private fun Step6Cuisine(selected: Set<String>, onSelected: (Set<String>) -> Unit) {
    Text("Food preferences", style = Typography.headlineLarge, color = Primary)
    Spacer(modifier = Modifier.height(8.dp))
    Text("Select cuisines you eat often. We'll use this to suggest foods when logging meals.", style = Typography.bodyMedium, color = OnSurfaceVariant)
    Spacer(modifier = Modifier.height(24.dp))
    Text("SELECT CUISINE TYPES", style = Typography.labelMedium, color = OnSurfaceVariant)
    Spacer(modifier = Modifier.height(12.dp))
    CuisineTypes.ALL.forEach { cuisine ->
        GoalCard(title = cuisine, isSelected = cuisine in selected) {
            onSelected(if (cuisine in selected) selected - cuisine else selected + cuisine)
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Preview(showBackground = true)
@Composable
fun OnboardingScreenPreview() {
    MyApplicationTheme { OnboardingScreenContent(onComplete = {}) }
}

@Composable
private fun Step2Content(
    activityLevel: String, onActivityChange: (String) -> Unit,
    goal: String, onGoalChange: (String) -> Unit
) {
    Text("Your activity & goals", style = Typography.headlineLarge, color = Primary)
    Spacer(modifier = Modifier.height(8.dp))
    Text("This helps us calculate your calorie and training targets.", style = Typography.bodyMedium, color = OnSurfaceVariant)
    Spacer(modifier = Modifier.height(32.dp))
    OnboardingDropdown("Activity Level", activityLevel, FitnessCalculator.activityLevels, onActivityChange)
    Spacer(modifier = Modifier.height(32.dp))
    Text("SELECT PRIMARY GOAL", style = Typography.labelMedium, color = OnSurfaceVariant)
    Spacer(modifier = Modifier.height(12.dp))
    FitnessCalculator.goals.forEach { g ->
        GoalCard(title = g, isSelected = goal == g) { onGoalChange(g) }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun Step4Content(
    dailyCalories: String, onCaloriesChange: (String) -> Unit,
    protein: String, onProteinChange: (String) -> Unit,
    carbs: String, onCarbsChange: (String) -> Unit,
    fat: String, onFatChange: (String) -> Unit,
    fiber: String, onFiberChange: (String) -> Unit
) {
    Text("Daily nutrition", style = Typography.headlineLarge, color = Primary)
    Spacer(modifier = Modifier.height(8.dp))
    Text("Calories and macros are calculated from your profile. Edit any value and others will auto-balance.", style = Typography.bodyMedium, color = OnSurfaceVariant)
    Spacer(modifier = Modifier.height(32.dp))
    OnboardingTextField("Total Calories", dailyCalories, onCaloriesChange, suffix = "kcal")
    Spacer(modifier = Modifier.height(24.dp))
    Text("MACROS", style = Typography.labelMedium, color = OnSurfaceVariant)
    Spacer(modifier = Modifier.height(12.dp))
    MacroEditRow("Protein", protein, onValueChange = { v -> if (v.all { it.isDigit() }) onProteinChange(v) })
    Spacer(modifier = Modifier.height(8.dp))
    MacroEditRow("Carbs", carbs, onValueChange = { v -> if (v.all { it.isDigit() }) onCarbsChange(v) })
    Spacer(modifier = Modifier.height(8.dp))
    MacroEditRow("Fat", fat, onValueChange = { v -> if (v.all { it.isDigit() }) onFatChange(v) })
    Spacer(modifier = Modifier.height(8.dp))
    MacroEditRow("Fiber", fiber, onValueChange = { v -> if (v.all { it.isDigit() }) onFiberChange(v) })
}
