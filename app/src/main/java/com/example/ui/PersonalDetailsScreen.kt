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
    val modelsRevision by viewModel.modelsRevision.collectAsState()
    val secretsRevision by viewModel.secretsRevision.collectAsState()

    var showGeminiKeyInput by remember { mutableStateOf(false) }
    var geminiKeyInput by remember { mutableStateOf("") }
    var isGeminiKeyVisible by remember { mutableStateOf(false) }
    var showGroqKeyInput by remember { mutableStateOf(false) }
    var groqKeyInput by remember { mutableStateOf("") }
    var isGroqKeyVisible by remember { mutableStateOf(false) }

    val installedModels = remember(modelsRevision) { viewModel.modelDownloadManager.listInstalledModels() }
    val geminiKey = remember(secretsRevision) { viewModel.getGeminiApiKey() }
    val groqKey = remember(secretsRevision) { viewModel.getGroqApiKey() }

    val aiStatus = viewModel.aiStatus()
    val ramGb = remember { viewModel.modelDownloadManager.getTotalRamGb() }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importAnyModel(uri)
    }

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
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (aiStatus.isReady) Secondary.copy(0.12f) else Error.copy(0.08f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("AI STATUS", style = Typography.labelMedium, color = Primary)
                        Text(aiStatus.label, style = Typography.titleMedium, color = if (aiStatus.isReady) Secondary else Error)
                        Text(aiStatus.detail, style = Typography.bodySmall, color = OnSurfaceVariant)
                        Text("Device RAM: ${String.format("%.1f", ramGb)} GB", style = Typography.bodySmall, color = OnSurfaceVariant)
                    }
                }
            }

            item {
                Text("AI PROVIDER", style = Typography.labelMedium, color = Primary)
                Spacer(Modifier.height(8.dp))
                val providerLabels = AiProviderConfig.PROVIDERS.map { AiProviderConfig.displayNameFor(it) }
                val providerIndex = AiProviderConfig.PROVIDERS.indexOf(profile.aiProvider).coerceAtLeast(0)
                WheelPickerField(
                    label = "Choose provider",
                    items = providerLabels,
                    selectedIndex = providerIndex,
                    onConfirm = { idx -> viewModel.updateAiProvider(AiProviderConfig.PROVIDERS[idx]) }
                )
            }

            if (profile.aiProvider == "gemini" || profile.aiProvider == "groq") {
                item {
                    Text("MODEL", style = Typography.labelMedium, color = Primary)
                    Spacer(Modifier.height(8.dp))
                    val models = AiProviderConfig.modelsFor(profile.aiProvider)
                    val modelIndex = models.indexOfFirst { it.id == profile.aiModelId }.coerceAtLeast(0)
                    val selectedModel = models.getOrNull(modelIndex)
                    WheelPickerField(
                        label = "${AiProviderConfig.displayNameFor(profile.aiProvider)} model",
                        items = models.map { AiProviderConfig.modelWheelLabel(profile.aiProvider, it) },
                        selectedIndex = modelIndex,
                        onConfirm = { idx -> viewModel.updateAiModel(models[idx].id) }
                    )
                    Spacer(Modifier.height(8.dp))
                    selectedModel?.let { model ->
                        if (!model.supportsVision) {
                            Text(
                                "Image analysis not available — ${model.displayName} is text-only. Use Gemini or Offline for food photos.",
                                style = Typography.bodySmall,
                                color = Error
                            )
                        } else {
                            Text(
                                "Supports text autofill and food photo analysis.",
                                style = Typography.bodySmall,
                                color = Secondary
                            )
                        }
                    }
                }
            }

            if (profile.aiProvider == "offline") {
                item {
                    Text("OFFLINE MODEL", style = Typography.labelMedium, color = Primary)
                    Spacer(Modifier.height(8.dp))
                    if (installedModels.isEmpty()) {
                        Text(
                            "No on-device model installed. Download or import a model below.",
                            style = Typography.bodySmall,
                            color = OnSurfaceVariant
                        )
                    } else {
                        val offlineIds = installedModels.map { it.id }
                        val offlineIndex = offlineIds.indexOf(profile.offlineModelId).coerceAtLeast(0)
                        val selected = installedModels.getOrNull(offlineIndex)
                        WheelPickerField(
                            label = "On-device model",
                            items = installedModels.map { model ->
                                val sizeLabel = if (model.isBuiltIn) {
                                    if (model.id == "offline_2b") "~2.4 GB" else "~3.4 GB"
                                } else "Imported"
                                "${model.displayName} ($sizeLabel)"
                            },
                            selectedIndex = offlineIndex,
                            onConfirm = { idx -> viewModel.updateOfflineModelId(offlineIds[idx]) }
                        )
                        Spacer(Modifier.height(8.dp))
                        selected?.let {
                            Text(
                                "Ready for text autofill and food photo analysis on-device.",
                                style = Typography.bodySmall,
                                color = Secondary
                            )
                        }
                    }
                }
            }

            if (profile.aiProvider == "gemini") {
                item {
                    Text("GEMINI API KEY", style = Typography.labelMedium, color = Primary)
                    Spacer(Modifier.height(8.dp))
                    ApiKeyCard(
                        label = "Gemini API Key",
                        currentKey = geminiKey,
                        showInput = showGeminiKeyInput,
                        keyInput = geminiKeyInput,
                        isKeyVisible = isGeminiKeyVisible,
                        onKeyInputChange = { geminiKeyInput = it },
                        onShowInput = { showGeminiKeyInput = true },
                        onSave = {
                            viewModel.setGeminiApiKey(geminiKeyInput)
                            showGeminiKeyInput = false
                            geminiKeyInput = ""
                        },
                        onToggleVisibility = { isGeminiKeyVisible = !isGeminiKeyVisible },
                        onEdit = {
                            geminiKeyInput = viewModel.getGeminiApiKey() ?: ""
                            showGeminiKeyInput = true
                        },
                        onClear = { viewModel.clearGeminiApiKey() },
                        authenticate = authenticate
                    )
                }
            }

            if (profile.aiProvider == "groq") {
                item {
                    Text("GROQ API KEY", style = Typography.labelMedium, color = Primary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Free tier at console.groq.com — used only for text autofill (no vision).",
                        style = Typography.bodySmall,
                        color = OnSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    ApiKeyCard(
                        label = "Groq API Key",
                        currentKey = groqKey,
                        showInput = showGroqKeyInput,
                        keyInput = groqKeyInput,
                        isKeyVisible = isGroqKeyVisible,
                        onKeyInputChange = { groqKeyInput = it },
                        onShowInput = { showGroqKeyInput = true },
                        onSave = {
                            viewModel.setGroqApiKey(groqKeyInput)
                            showGroqKeyInput = false
                            groqKeyInput = ""
                        },
                        onToggleVisibility = { isGroqKeyVisible = !isGroqKeyVisible },
                        onEdit = {
                            groqKeyInput = viewModel.getGroqApiKey() ?: ""
                            showGroqKeyInput = true
                        },
                        onClear = { viewModel.clearGroqApiKey() },
                        authenticate = authenticate
                    )
                }
            }

            item {
                Text("OFFLINE MODELS", style = Typography.labelMedium, color = Primary)
                aiSettings.error?.let { err ->
                    Spacer(Modifier.height(8.dp))
                    Text(err, color = Error, style = Typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val downloadState = remember(modelsRevision) {
                        mapOf(
                            "offline_2b" to viewModel.modelDownloadManager.isModelDownloaded("offline_2b"),
                            "offline_4b" to viewModel.modelDownloadManager.isModelDownloaded("offline_4b")
                        )
                    }
                    listOf("offline_2b", "offline_4b").forEach { modelId ->
                        val isDownloaded = downloadState[modelId] == true
                        val isCompatible = viewModel.modelDownloadManager.isSystemCompatible(modelId)
                        val isDownloading = if (modelId == "offline_2b") aiSettings.isDownloading2B else aiSettings.isDownloading4B
                        val progress = if (modelId == "offline_2b") aiSettings.progress2B else aiSettings.progress4B

                        ModelCard(
                            title = if (modelId == "offline_2b") "Gemma 4 E2B-it (~2.4 GB)" else "Gemma 4 E4B-it (~3.4 GB)",
                            isDownloaded = isDownloaded,
                            isDownloading = isDownloading,
                            progress = progress,
                            isCompatible = isCompatible,
                            isSelected = profile.offlineModelId == modelId,
                            onSelect = { if (isDownloaded) viewModel.updateOfflineModelId(modelId) },
                            onDownload = { viewModel.startModelDownload(modelId) },
                            onCancel = { viewModel.cancelModelDownload(modelId) },
                            onDelete = { viewModel.deleteModel(modelId) }
                        )
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceContainerHighest.copy(0.3f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Import Model", style = Typography.bodyLarge, color = Primary)
                            Text(
                                "Import a .litertlm model file if you already have one on this device. Imported models appear in the on-device model list.",
                                style = Typography.bodySmall,
                                color = OnSurfaceVariant
                            )
                            Button(
                                onClick = { importLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Choose .litertlm file")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ApiKeyCard(
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
