package com.example.ui

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
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import android.os.Build
import android.app.ActivityManager
import android.content.Context
import com.example.GymViewModel
import com.example.data.CuisineTypes
import com.example.data.FitnessCalculator
import com.example.data.ProfileValidation
import com.example.data.UserProfile
import com.example.ui.theme.*

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
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalDetailsScreenContent(
    profile: UserProfile,
    onBack: () -> Unit,
    onSave: (Float, Int, Int, Int, Int, Int, Float?, String?) -> Unit
) {
    var isEditMode by remember { mutableStateOf(false) }

    var targetWeight by remember(profile, isEditMode) { mutableStateOf(String.format("%.1f", profile.targetWeight)) }
    var dailyCalories by remember(profile, isEditMode) { mutableStateOf(profile.dailyCalories.toString()) }
    var protein by remember(profile, isEditMode) { mutableStateOf(profile.protein.toString()) }
    var carbs by remember(profile, isEditMode) { mutableStateOf(profile.carbs.toString()) }
    var fat by remember(profile, isEditMode) { mutableStateOf(profile.fat.toString()) }
    var fiber by remember(profile, isEditMode) { mutableStateOf(profile.fiber.toString()) }
    var selectedCuisines by remember(profile, isEditMode) { mutableStateOf(CuisineTypes.parsePreferences(profile.cuisinePreferences).toSet()) }
    var weeklyWarning by remember { mutableStateOf<String?>(null) }

    val weeklyItems = remember(profile.goal, profile.currentWeight) {
        weeklyChangeItems(ProfileValidation.minWeeklyChangeKg(profile.goal), ProfileValidation.maxWeeklyChangeKg(profile.goal, profile.currentWeight))
    }
    var weeklyIndex by remember(profile, weeklyItems, isEditMode) {
        mutableIntStateOf(
            weeklyItems.indexOfFirst { it.startsWith(String.format("%.1f", profile.targetWeightChangePerWeek)) }.coerceAtLeast(0)
        )
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
                actions = {
                    if (!isEditMode) {
                        TextButton(onClick = { isEditMode = true }) {
                            Text("Edit", color = Primary)
                        }
                    } else {
                        TextButton(onClick = { isEditMode = false }) {
                            Text("Cancel", color = Error)
                        }
                        TextButton(
                            onClick = {
                                val change = weeklyItems.getOrNull(weeklyIndex)?.substringBefore(" ")?.toFloatOrNull()
                                val validation = change?.let { ProfileValidation.validateWeeklyChange(profile.goal, profile.currentWeight, it) }
                                if (validation != null && !validation.isValid) return@TextButton
                                onSave(
                                    targetWeight.toFloatOrNull() ?: profile.targetWeight,
                                    dailyCalories.toIntOrNull() ?: profile.dailyCalories,
                                    protein.toIntOrNull() ?: profile.protein,
                                    carbs.toIntOrNull() ?: profile.carbs,
                                    fat.toIntOrNull() ?: profile.fat,
                                    fiber.toIntOrNull() ?: profile.fiber,
                                    change,
                                    CuisineTypes.serializePreferences(selectedCuisines.toList())
                                )
                                isEditMode = false
                            }
                        ) {
                            Text("Confirm", color = Primary)
                        }
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
            ReadOnlySection("Step 1 — Physical Metrics", profile)
            Spacer(modifier = Modifier.height(24.dp))
            ReadOnlySection("Step 2 — Activity & Goal", profile)
            Spacer(modifier = Modifier.height(24.dp))

            Text("Step 3 — Weight Goals", style = Typography.labelMedium, color = OnSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            ReadOnlyField("Current Weight", "${profile.currentWeight} kg")
            Spacer(modifier = Modifier.height(12.dp))
            if (isEditMode) {
                OnboardingTextField("Target Weight", targetWeight, { targetWeight = it }, suffix = "kg")
            } else {
                ReadOnlyField("Target Weight", "${profile.targetWeight} kg")
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Step 4 — Nutrition", style = Typography.labelMedium, color = OnSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            if (isEditMode) {
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
            ReadOnlySection("Step 5 — Workout Plan", profile)
            Spacer(modifier = Modifier.height(24.dp))
            Text("Step 6 — Food Preferences", style = Typography.labelMedium, color = OnSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            if (isEditMode) {
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
                Text("Target Weight Change / Week", style = Typography.labelMedium, color = OnSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                if (isEditMode) {
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
private fun ReadOnlySection(title: String, profile: UserProfile) {
    Text(title, style = Typography.labelMedium, color = OnSurfaceVariant)
    Spacer(modifier = Modifier.height(8.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(0.05f), RoundedCornerShape(12.dp))
            .border(1.dp, OutlineVariant.copy(0.3f), RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when {
            title.contains("Step 1") -> {
                ReadOnlyField("Age", "${profile.age} years")
                ReadOnlyField("Gender", profile.gender)
                ReadOnlyField("Height", "${profile.height} cm")
            }
            title.contains("Step 2") -> {
                ReadOnlyField("Activity Level", profile.activityLevel)
                ReadOnlyField("Primary Goal", profile.goal)
            }
            title.contains("Step 5") -> {
                ReadOnlyField("Workout Days / Week", "${profile.workoutDaysPerWeek} days")
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
                ReadOnlyField("Cuisine Preferences", CuisineTypes.parsePreferences(profile.cuisinePreferences).joinToString(", ").ifBlank { "None" })
            }
        }
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
    val context = LocalContext.current
    val profile by viewModel.userProfile.collectAsState()
    val aiSettings by viewModel.aiSettingsState.collectAsState()
    
    var showApiKeyInput by remember { mutableStateOf(false) }
    var apiKeyInput by remember { mutableStateOf("") }
    var isApiKeyVisible by remember { mutableStateOf(false) }

    val isCompatible = remember { viewModel.modelDownloadManager.isSystemCompatible() }

    val authenticate = { onSuccess: () -> Unit ->
        val executor = ContextCompat.getMainExecutor(context)
        val biometricPrompt = BiometricPrompt(
            context as FragmentActivity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("AI Settings Security")
            .setSubtitle("Authenticate to manage API keys")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Settings", style = Typography.headlineMedium, color = Primary) },
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
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                Text("AI MODE SELECTION", style = Typography.labelMedium, color = Primary)
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AiModeRow("Online (Gemini)", "online", profile.aiMode, viewModel::updateAiMode)
                    AiModeRow("Offline (Gemma-4 E2B-it)", "offline_2b", profile.aiMode, viewModel::updateAiMode, enabled = viewModel.modelDownloadManager.isModelDownloaded("offline_2b"))
                    AiModeRow("Offline (Gemma-4 E4B-it)", "offline_4b", profile.aiMode, viewModel::updateAiMode, enabled = viewModel.modelDownloadManager.isModelDownloaded("offline_4b"))
                    AiModeRow("None", "none", profile.aiMode, viewModel::updateAiMode)
                }
            }

            item {
                Text("GEMINI API CONFIGURATION", style = Typography.labelMedium, color = Primary)
                Spacer(Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerHighest.copy(0.3f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        val currentKey = viewModel.getGeminiApiKey()
                        val hasKey = !currentKey.isNullOrBlank()

                        if (showApiKeyInput) {
                            OutlinedTextField(
                                value = apiKeyInput,
                                onValueChange = { apiKeyInput = it },
                                label = { Text("Gemini API Key") },
                                modifier = Modifier.fillMaxWidth(),
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { 
                                        viewModel.setGeminiApiKey(apiKeyInput)
                                        showApiKeyInput = false 
                                        apiKeyInput = ""
                                    }) {
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
                                    Text(if (isApiKeyVisible) currentKey!! else "••••••••••••", color = OnSurface, modifier = Modifier.weight(1f))
                                } else {
                                    Text("No API Key Set", color = OnSurfaceVariant, modifier = Modifier.weight(1f))
                                }
                                
                                Row {
                                    if (hasKey) {
                                        IconButton(onClick = { 
                                            authenticate { isApiKeyVisible = !isApiKeyVisible }
                                        }) {
                                            Icon(if (isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = Primary)
                                        }
                                        IconButton(onClick = { 
                                            authenticate { 
                                                apiKeyInput = viewModel.getGeminiApiKey() ?: ""
                                                showApiKeyInput = true 
                                            }
                                        }) {
                                            Icon(Icons.Default.Edit, null, tint = Primary)
                                        }
                                        IconButton(onClick = { 
                                            authenticate { viewModel.clearGeminiApiKey() }
                                        }) {
                                            Icon(Icons.Default.Delete, null, tint = Error)
                                        }
                                    } else {
                                        Button(onClick = { showApiKeyInput = true }) {
                                            Text("Add Key")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text("OFFLINE MODELS", style = Typography.labelMedium, color = Primary)
                if (!isCompatible) {
                    Spacer(Modifier.height(4.dp))
                    Text("System is not compatible with high-end Gemma models.", color = Error, style = Typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ModelCard(
                        "Gemma-4 E2B-it",
                        "offline_2b",
                        viewModel.modelDownloadManager.isModelDownloaded("offline_2b"),
                        aiSettings.isDownloading2B,
                        aiSettings.progress2B,
                        isCompatible,
                        onDownload = { viewModel.startModelDownload("offline_2b") },
                        onCancel = { viewModel.cancelModelDownload("offline_2b") },
                        onDelete = { viewModel.deleteModel("offline_2b") }
                    )
                    ModelCard(
                        "Gemma-4 E4B-it",
                        "offline_4b",
                        viewModel.modelDownloadManager.isModelDownloaded("offline_4b"),
                        aiSettings.isDownloading4B,
                        aiSettings.progress4B,
                        isCompatible,
                        onDownload = { viewModel.startModelDownload("offline_4b") },
                        onCancel = { viewModel.cancelModelDownload("offline_4b") },
                        onDelete = { viewModel.deleteModel("offline_4b") }
                    )
                }
            }
        }
    }
}

@Composable
fun AiModeRow(label: String, mode: String, currentMode: String, onSelect: (String) -> Unit, enabled: Boolean = true) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (currentMode == mode) Primary.copy(0.1f) else SurfaceContainerHighest.copy(0.1f),
                RoundedCornerShape(8.dp)
            )
            .border(
                1.dp,
                if (currentMode == mode) Primary else OutlineVariant.copy(0.2f),
                RoundedCornerShape(8.dp)
            )
            .clickable(enabled = enabled) { onSelect(mode) }
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = if (enabled) OnSurface else OnSurface.copy(0.4f))
        RadioButton(selected = currentMode == mode, onClick = { if (enabled) onSelect(mode) }, enabled = enabled)
    }
}

@Composable
fun ModelCard(
    title: String,
    mode: String,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    progress: Float,
    isCompatible: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerHighest.copy(0.3f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = Typography.bodyLarge, color = OnSurface)
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
                    progress = progress,
                    modifier = Modifier.fillMaxWidth(),
                    color = Primary,
                    trackColor = SurfaceContainerHighest
                )
                Text("${(progress * 100).toInt()}%", style = Typography.labelSmall, modifier = Modifier.align(Alignment.End))
            } else {
                Text(
                    if (isDownloaded) "Downloaded & Ready" else if (isCompatible) "Available for download" else "Not supported",
                    style = Typography.bodySmall,
                    color = if (isDownloaded) Secondary else OnSurfaceVariant
                )
            }
        }
    }
}
