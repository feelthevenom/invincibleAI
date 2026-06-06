@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.ApiConnectionTestState
import com.example.GymViewModel
import com.example.data.AiProviderConfig
import com.example.data.AiRouteResolver
import com.example.ui.theme.*

@Composable
fun AiSettingsScreen(viewModel: GymViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val profile by viewModel.userProfile.collectAsState()
    val aiSettings by viewModel.aiSettingsState.collectAsState()
    val modelsRevision by viewModel.modelsRevision.collectAsState()
    val secretsRevision by viewModel.secretsRevision.collectAsState()
    val aiConfigRevision by viewModel.aiConfigRevision.collectAsState()

    var showGeminiKeyInput by remember { mutableStateOf(false) }
    var geminiKeyInput by remember { mutableStateOf("") }
    var isGeminiKeyVisible by remember { mutableStateOf(false) }
    var showGroqKeyInput by remember { mutableStateOf(false) }
    var groqKeyInput by remember { mutableStateOf("") }
    var isGroqKeyVisible by remember { mutableStateOf(false) }
    var showOpenRouterKeyInput by remember { mutableStateOf(false) }
    var openRouterKeyInput by remember { mutableStateOf("") }
    var isOpenRouterKeyVisible by remember { mutableStateOf(false) }

    var pickerTarget by remember { mutableStateOf<PickerTarget?>(null) }
    var pickerDraftIndex by remember { mutableIntStateOf(0) }
    var pickerSelectedModelId by remember { mutableStateOf("") }

    val installedModels = remember(modelsRevision) { viewModel.modelDownloadManager.listInstalledModels() }
    val geminiKey = remember(secretsRevision) { viewModel.getGeminiApiKey() }
    val groqKey = remember(secretsRevision) { viewModel.getGroqApiKey() }
    val openRouterKey = remember(secretsRevision) { viewModel.getOpenRouterApiKey() }
    val openRouterModelsRevision by viewModel.openRouterModelsRevision.collectAsState()
    val aiStatus = remember(profile, secretsRevision, modelsRevision, aiConfigRevision) {
        viewModel.aiStatus()
    }
    val apiTestState by viewModel.apiConnectionTestState.collectAsState()
    val needsOnlineApi = remember(profile) { AiRouteResolver.needsOnlineApi(profile) }

    LaunchedEffect(
        profile.aiSplitModels,
        profile.aiProvider,
        profile.aiTextProvider,
        profile.aiVisionProvider,
        profile.aiModelId,
        profile.aiTextModelId,
        profile.aiVisionModelId,
        secretsRevision
    ) {
        viewModel.clearApiConnectionTestState()
        if (profileUsesOpenRouter(profile) && !openRouterKey.isNullOrBlank()) {
            viewModel.refreshOpenRouterModels()
        }
    }
    val ramGb = remember { viewModel.modelDownloadManager.getTotalRamGb() }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
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
        biometricPrompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("AI Settings Security")
                .setSubtitle("Authenticate to manage API keys")
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                .build()
        )
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
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (aiStatus.isReady) Secondary.copy(0.12f) else Error.copy(0.08f)
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("AI STATUS", style = Typography.labelMedium, color = Primary)
                        Text(
                            aiStatus.label,
                            style = Typography.titleMedium,
                            color = if (aiStatus.isReady) Secondary else Error,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(aiStatus.detail, style = Typography.bodySmall, color = OnSurfaceVariant)
                        Text(
                            "Device RAM: ${String.format("%.1f", ramGb)} GB",
                            style = Typography.bodySmall,
                            color = OnSurfaceVariant
                        )
                    }
                }
            }

            item {
                Text("MODEL CONFIGURATION MODE", style = Typography.labelMedium, color = OnSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                ModelModeToggle(
                    split = profile.aiSplitModels,
                    onUnified = { viewModel.updateAiSplitModels(false) },
                    onSplit = { viewModel.updateAiSplitModels(true) }
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (profile.aiSplitModels) {
                        "Distribute tasks across specialized models for maximum precision."
                    } else {
                        "Use one multimodal model for text, vision, and coaching."
                    },
                    style = Typography.bodySmall,
                    color = OnSurfaceVariant
                )
            }

            if (!profile.aiSplitModels) {
                item {
                    Text("PROVIDER", style = Typography.labelMedium, color = OnSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    ProviderCard(
                        provider = profile.aiProvider,
                        icon = providerIcon(profile.aiProvider),
                        onChange = {
                            pickerTarget = PickerTarget.UnifiedProvider
                            pickerDraftIndex = AiProviderConfig.PROVIDERS.indexOf(profile.aiProvider).coerceAtLeast(0)
                        }
                    )
                }
                if (profile.aiProvider != "offline") {
                    item {
                        val models = AiProviderConfig.modelsFor(profile.aiProvider)
                        val modelName = AiProviderConfig.modelDisplayName(profile.aiProvider, profile.aiModelId)
                        Text(
                            if (profile.aiProvider == "groq") "MULTIMODAL MODEL" else "MULTIMODAL MODEL",
                            style = Typography.labelMedium,
                            color = OnSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        ModelSlotCard(
                            title = modelName,
                            subtitle = if (AiProviderConfig.supportsVision(profile.aiProvider, profile.aiModelId)) {
                                "Supports text autofill and food photo analysis."
                            } else {
                                "Text-only model — switch for photo analysis."
                            },
                            icon = Icons.Default.AutoAwesome,
                            subtitleColor = if (AiProviderConfig.supportsVision(profile.aiProvider, profile.aiModelId)) Secondary else Error,
                            onChange = {
                                if (profile.aiProvider == "openrouter") viewModel.refreshOpenRouterModels()
                                pickerTarget = PickerTarget.UnifiedModel
                                pickerSelectedModelId = profile.aiModelId
                            }
                        )
                    }
                } else {
                    item { OfflineActivePicker(viewModel, profile, installedModels) }
                }
            } else {
                item {
                    Text("TEXT MODEL", style = Typography.labelMedium, color = OnSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    ProviderCard(
                        provider = profile.aiTextProvider,
                        icon = providerIcon(profile.aiTextProvider),
                        onChange = {
                            pickerTarget = PickerTarget.TextProvider
                            pickerDraftIndex = AiProviderConfig.PROVIDERS.indexOf(profile.aiTextProvider).coerceAtLeast(0)
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    if (profile.aiTextProvider == "offline") {
                        OfflineActivePicker(viewModel, profile, installedModels, forText = true)
                    } else {
                        ModelSlotCard(
                            title = AiProviderConfig.modelDisplayName(profile.aiTextProvider, profile.aiTextModelId),
                            subtitle = "Coaching, insights, and text-based food suggestions.",
                            icon = Icons.Default.Psychology,
                            subtitleColor = OnSurfaceVariant,
                            onChange = {
                                if (profile.aiTextProvider == "openrouter") viewModel.refreshOpenRouterModels()
                                pickerTarget = PickerTarget.TextModel
                                pickerSelectedModelId = profile.aiTextModelId
                            }
                        )
                    }
                    if (profile.aiTextProvider == "gemini") {
                        Spacer(Modifier.height(12.dp))
                        Text("TEXT MODEL API", style = Typography.labelMedium, color = OnSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        GeminiApiKeySection(
                            geminiKey = geminiKey,
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
                    if (profile.aiTextProvider == "groq") {
                        Spacer(Modifier.height(12.dp))
                        Text("TEXT MODEL API", style = Typography.labelMedium, color = OnSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Groq text models for coaching and insights.",
                            style = Typography.bodySmall,
                            color = OnSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        GroqApiKeySection(
                            groqKey = groqKey,
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
                    if (profile.aiTextProvider == "openrouter") {
                        Spacer(Modifier.height(12.dp))
                        OpenRouterApiSection(
                            openRouterKey = openRouterKey,
                            showInput = showOpenRouterKeyInput,
                            keyInput = openRouterKeyInput,
                            isKeyVisible = isOpenRouterKeyVisible,
                            isLoadingModels = aiSettings.openRouterModelsLoading,
                            modelCount = AiProviderConfig.textModelsFor("openrouter").size,
                            onKeyInputChange = { openRouterKeyInput = it },
                            onShowInput = { showOpenRouterKeyInput = true },
                            onSave = {
                                viewModel.setOpenRouterApiKey(openRouterKeyInput)
                                showOpenRouterKeyInput = false
                                openRouterKeyInput = ""
                            },
                            onToggleVisibility = { isOpenRouterKeyVisible = !isOpenRouterKeyVisible },
                            onEdit = {
                                openRouterKeyInput = viewModel.getOpenRouterApiKey() ?: ""
                                showOpenRouterKeyInput = true
                            },
                            onClear = { viewModel.clearOpenRouterApiKey() },
                            onRefreshModels = { viewModel.refreshOpenRouterModels() },
                            authenticate = authenticate
                        )
                    }
                }
                item {
                    Text("VISION MODEL", style = Typography.labelMedium, color = OnSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    ProviderCard(
                        provider = profile.aiVisionProvider,
                        icon = providerIcon(profile.aiVisionProvider),
                        onChange = {
                            pickerTarget = PickerTarget.VisionProvider
                            pickerDraftIndex = AiProviderConfig.PROVIDERS.indexOf(profile.aiVisionProvider).coerceAtLeast(0)
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    if (profile.aiVisionProvider == "offline") {
                        OfflineActivePicker(viewModel, profile, installedModels, forVision = true)
                    } else {
                        ModelSlotCard(
                            title = AiProviderConfig.modelDisplayName(profile.aiVisionProvider, profile.aiVisionModelId),
                            subtitle = "Food photo analysis only.",
                            icon = Icons.Default.Visibility,
                            subtitleColor = Secondary,
                            onChange = {
                                if (profile.aiVisionProvider == "openrouter") viewModel.refreshOpenRouterModels()
                                pickerTarget = PickerTarget.VisionModel
                                pickerSelectedModelId = profile.aiVisionModelId
                            }
                        )
                    }
                    if (profile.aiVisionProvider == "gemini") {
                        Spacer(Modifier.height(12.dp))
                        Text("VISION MODEL API", style = Typography.labelMedium, color = OnSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        GeminiApiKeySection(
                            geminiKey = geminiKey,
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
                    if (profile.aiVisionProvider == "groq") {
                        Spacer(Modifier.height(12.dp))
                        Text("VISION MODEL API", style = Typography.labelMedium, color = OnSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Groq vision uses Llama 4 Scout for food photo analysis.",
                            style = Typography.bodySmall,
                            color = OnSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        GroqApiKeySection(
                            groqKey = groqKey,
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
                    if (profile.aiVisionProvider == "openrouter") {
                        Spacer(Modifier.height(12.dp))
                        OpenRouterApiSection(
                            openRouterKey = openRouterKey,
                            showInput = showOpenRouterKeyInput,
                            keyInput = openRouterKeyInput,
                            isKeyVisible = isOpenRouterKeyVisible,
                            isLoadingModels = aiSettings.openRouterModelsLoading,
                            modelCount = AiProviderConfig.visionModelsFor("openrouter").size,
                            onKeyInputChange = { openRouterKeyInput = it },
                            onShowInput = { showOpenRouterKeyInput = true },
                            onSave = {
                                viewModel.setOpenRouterApiKey(openRouterKeyInput)
                                showOpenRouterKeyInput = false
                                openRouterKeyInput = ""
                            },
                            onToggleVisibility = { isOpenRouterKeyVisible = !isOpenRouterKeyVisible },
                            onEdit = {
                                openRouterKeyInput = viewModel.getOpenRouterApiKey() ?: ""
                                showOpenRouterKeyInput = true
                            },
                            onClear = { viewModel.clearOpenRouterApiKey() },
                            onRefreshModels = { viewModel.refreshOpenRouterModels() },
                            authenticate = authenticate
                        )
                    }
                    if (needsOnlineApi) {
                        Spacer(Modifier.height(12.dp))
                        CombinedApiTestSection(
                            apiTestState = apiTestState,
                            canTest = hasRequiredApiKeys(profile, geminiKey, groqKey, openRouterKey),
                            onTest = { viewModel.testOnlineApiConnections() }
                        )
                    }
                }
            }

            if (!profile.aiSplitModels && profile.aiProvider == "gemini") {
                item {
                    Text("API MANAGEMENT", style = Typography.labelMedium, color = OnSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    GeminiApiKeySection(
                        geminiKey = geminiKey,
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
                    Spacer(Modifier.height(10.dp))
                    CombinedApiTestSection(
                        apiTestState = apiTestState,
                        canTest = !geminiKey.isNullOrBlank(),
                        onTest = { viewModel.testOnlineApiConnections() }
                    )
                }
            }

            if (!profile.aiSplitModels && profile.aiProvider == "groq") {
                item {
                    Text("API MANAGEMENT", style = Typography.labelMedium, color = OnSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Groq vision uses Llama 4 Scout; text and vision share one API key.",
                        style = Typography.bodySmall,
                        color = OnSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    GroqApiKeySection(
                        groqKey = groqKey,
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
                    Spacer(Modifier.height(10.dp))
                    CombinedApiTestSection(
                        apiTestState = apiTestState,
                        canTest = !groqKey.isNullOrBlank(),
                        onTest = { viewModel.testOnlineApiConnections() }
                    )
                }
            }

            if (!profile.aiSplitModels && profile.aiProvider == "openrouter") {
                item {
                    Text("API MANAGEMENT", style = Typography.labelMedium, color = OnSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "OpenRouter loads available models from your account. Pick a vision-capable model for food photos.",
                        style = Typography.bodySmall,
                        color = OnSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OpenRouterApiSection(
                        openRouterKey = openRouterKey,
                        showInput = showOpenRouterKeyInput,
                        keyInput = openRouterKeyInput,
                        isKeyVisible = isOpenRouterKeyVisible,
                        isLoadingModels = aiSettings.openRouterModelsLoading,
                        modelCount = AiProviderConfig.modelsFor("openrouter").size,
                        onKeyInputChange = { openRouterKeyInput = it },
                        onShowInput = { showOpenRouterKeyInput = true },
                        onSave = {
                            viewModel.setOpenRouterApiKey(openRouterKeyInput)
                            showOpenRouterKeyInput = false
                            openRouterKeyInput = ""
                        },
                        onToggleVisibility = { isOpenRouterKeyVisible = !isOpenRouterKeyVisible },
                        onEdit = {
                            openRouterKeyInput = viewModel.getOpenRouterApiKey() ?: ""
                            showOpenRouterKeyInput = true
                        },
                        onClear = { viewModel.clearOpenRouterApiKey() },
                        onRefreshModels = { viewModel.refreshOpenRouterModels() },
                        authenticate = authenticate
                    )
                    Spacer(Modifier.height(10.dp))
                    CombinedApiTestSection(
                        apiTestState = apiTestState,
                        canTest = !openRouterKey.isNullOrBlank() && AiProviderConfig.modelsFor("openrouter").isNotEmpty(),
                        onTest = { viewModel.testOnlineApiConnections() }
                    )
                }
            }

            item {
                Text("OFFLINE MODELS", style = Typography.labelMedium, color = OnSurfaceVariant)
                aiSettings.error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = Error, style = Typography.bodySmall)
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
                        val isActive = profile.offlineModelId == modelId &&
                            (profile.aiProvider == "offline" || profile.aiTextProvider == "offline" || profile.aiVisionProvider == "offline")
                        ModelDownloadCard(
                            title = if (modelId == "offline_2b") "Gemma 4 E2B-it (~2.4 GB)" else "Gemma 4 E4B-it (~3.4 GB)",
                            isDownloaded = isDownloaded,
                            isDownloading = if (modelId == "offline_2b") aiSettings.isDownloading2B else aiSettings.isDownloading4B,
                            progress = if (modelId == "offline_2b") aiSettings.progress2B else aiSettings.progress4B,
                            isCompatible = viewModel.modelDownloadManager.isSystemCompatible(modelId),
                            onDownload = { viewModel.startModelDownload(modelId) },
                            onCancel = { viewModel.cancelModelDownload(modelId) },
                            onDelete = { viewModel.deleteModel(modelId) }
                        )
                    }
                    ImportModelCard(onImport = { importLauncher.launch(arrayOf("application/octet-stream", "*/*")) })
                }
            }
            item {
                AppUpdateSection(viewModel = viewModel)
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    pickerTarget?.let { target ->
        val openRouterRevision = openRouterModelsRevision
        if (isModelPickerTarget(target)) {
            val modelConfig = modelPickerConfig(target, profile, viewModel, openRouterRevision)
            if (modelConfig.models.isNotEmpty()) {
                ModelPickerDialog(
                    title = modelConfig.title,
                    provider = modelConfig.provider,
                    models = modelConfig.models,
                    selectedModelId = pickerSelectedModelId,
                    onDismiss = { pickerTarget = null },
                    onSelect = { model ->
                        modelConfig.onPick(model)
                        pickerTarget = null
                    }
                )
            } else if (modelConfig.emptyMessage != null) {
                AlertDialog(
                    onDismissRequest = { pickerTarget = null },
                    title = { Text(modelConfig.title, color = Primary) },
                    text = { Text(modelConfig.emptyMessage, color = OnSurfaceVariant) },
                    confirmButton = {
                        TextButton(onClick = { pickerTarget = null }) { Text("OK") }
                    }
                )
            }
        } else {
            val (title, items, onPick, emptyMessage) = pickerConfig(target, profile, viewModel, installedModels, openRouterRevision)
            if (items.isNotEmpty()) {
                AlertDialog(
                    onDismissRequest = { pickerTarget = null },
                    title = { Text(title, color = Primary) },
                    text = {
                        WheelPicker(
                            items = items,
                            selectedIndex = pickerDraftIndex.coerceIn(0, items.lastIndex),
                            onSelected = { pickerDraftIndex = it },
                            label = null
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            onPick(pickerDraftIndex.coerceIn(0, items.lastIndex))
                            pickerTarget = null
                        }) { Text("Select") }
                    },
                    dismissButton = {
                        TextButton(onClick = { pickerTarget = null }) { Text("Cancel") }
                    }
                )
            } else if (emptyMessage != null) {
                AlertDialog(
                    onDismissRequest = { pickerTarget = null },
                    title = { Text(title, color = Primary) },
                    text = { Text(emptyMessage, color = OnSurfaceVariant) },
                    confirmButton = {
                        TextButton(onClick = { pickerTarget = null }) { Text("OK") }
                    }
                )
            }
        }
    }
}

private enum class PickerTarget {
    UnifiedProvider, UnifiedModel, TextProvider, TextModel, VisionProvider, VisionModel, OfflineModel
}

@Composable
private fun ModelModeToggle(split: Boolean, onUnified: () -> Unit, onSplit: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceContainer, RoundedCornerShape(12.dp))
            .padding(4.dp)
    ) {
        listOf(false to "UNIFIED MODEL", true to "SPLIT MODELS").forEach { (isSplit, label) ->
            val selected = split == isSplit
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) Primary else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { if (isSplit) onSplit() else onUnified() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = Typography.labelSmall,
                    color = if (selected) OnPrimary else Primary,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ProviderCard(provider: String, icon: ImageVector, onChange: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Primary.copy(0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = Primary, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(AiProviderConfig.displayNameFor(provider), style = Typography.titleMedium, color = OnSurface)
                Text(
                    AiProviderConfig.providerSubtitle(provider),
                    style = Typography.bodySmall,
                    color = OnSurfaceVariant
                )
            }
            TextButton(onClick = onChange) {
                Text("CHANGE", style = Typography.labelMedium, color = Primary)
            }
        }
    }
}

@Composable
private fun ModelSlotCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    subtitleColor: androidx.compose.ui.graphics.Color,
    onChange: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = Primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = Typography.titleMedium, color = Primary, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = Typography.bodySmall, color = subtitleColor)
            }
            TextButton(onClick = onChange) {
                Text("CHANGE", style = Typography.labelMedium, color = Primary)
            }
        }
    }
}

@Composable
private fun OfflineActivePicker(
    viewModel: GymViewModel,
    profile: com.example.data.UserProfile,
    installedModels: List<com.example.data.ModelDownloadManager.InstalledOfflineModel>,
    forText: Boolean = false,
    forVision: Boolean = false
) {
    if (installedModels.isEmpty()) {
        Text("No on-device model installed. Download or import below.", style = Typography.bodySmall, color = OnSurfaceVariant)
        return
    }
    val ids = installedModels.map { it.id }
    val index = ids.indexOf(profile.offlineModelId).coerceAtLeast(0)
    WheelPickerField(
        label = when {
            forText -> "On-device text model"
            forVision -> "On-device vision model"
            else -> "On-device model"
        },
        items = installedModels.map { "${it.displayName}" },
        selectedIndex = index,
        onConfirm = { viewModel.updateOfflineModelId(ids[it]) }
    )
}

@Composable
private fun ImportModelCard(onImport: () -> Unit) {
    Card(
        onClick = onImport,
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer.copy(0.6f)),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, OutlineVariant.copy(0.35f))
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.FileDownload, null, tint = Primary, modifier = Modifier.size(26.dp))
            }
            Text("Download Local Model", style = Typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
            Text(
                "Import a .litertlm file for offline exercise and food analysis",
                style = Typography.bodySmall,
                color = OnSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun GeminiApiKeySection(
    geminiKey: String?,
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
    ApiKeyCard(
        label = "Gemini API Key",
        currentKey = geminiKey,
        showInput = showInput,
        keyInput = keyInput,
        isKeyVisible = isKeyVisible,
        onKeyInputChange = onKeyInputChange,
        onShowInput = onShowInput,
        onSave = onSave,
        onToggleVisibility = onToggleVisibility,
        onEdit = onEdit,
        onClear = onClear,
        authenticate = authenticate
    )
}

@Composable
private fun GroqApiKeySection(
    groqKey: String?,
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
    ApiKeyCard(
        label = "Groq API Key",
        currentKey = groqKey,
        showInput = showInput,
        keyInput = keyInput,
        isKeyVisible = isKeyVisible,
        onKeyInputChange = onKeyInputChange,
        onShowInput = onShowInput,
        onSave = onSave,
        onToggleVisibility = onToggleVisibility,
        onEdit = onEdit,
        onClear = onClear,
        authenticate = authenticate
    )
}

@Composable
private fun OpenRouterApiSection(
    openRouterKey: String?,
    showInput: Boolean,
    keyInput: String,
    isKeyVisible: Boolean,
    isLoadingModels: Boolean,
    modelCount: Int,
    onKeyInputChange: (String) -> Unit,
    onShowInput: () -> Unit,
    onSave: () -> Unit,
    onToggleVisibility: () -> Unit,
    onEdit: () -> Unit,
    onClear: () -> Unit,
    onRefreshModels: () -> Unit,
    authenticate: (() -> Unit) -> Unit
) {
    Text("OPENROUTER API", style = Typography.labelMedium, color = OnSurfaceVariant)
    Spacer(Modifier.height(4.dp))
    Text(
        "Models are loaded from your OpenRouter account. Only available models appear in the picker.",
        style = Typography.bodySmall,
        color = OnSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))
    ApiKeyCard(
        label = "OpenRouter API Key",
        currentKey = openRouterKey,
        showInput = showInput,
        keyInput = keyInput,
        isKeyVisible = isKeyVisible,
        onKeyInputChange = onKeyInputChange,
        onShowInput = onShowInput,
        onSave = onSave,
        onToggleVisibility = onToggleVisibility,
        onEdit = onEdit,
        onClear = onClear,
        authenticate = authenticate
    )
    Spacer(Modifier.height(8.dp))
    OutlinedButton(
        onClick = onRefreshModels,
        enabled = !openRouterKey.isNullOrBlank() && !isLoadingModels,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp), tint = Primary)
        Spacer(Modifier.width(8.dp))
        Text(
            if (isLoadingModels) "Loading models…" else "Refresh models",
            color = Primary
        )
    }
    Text(
        when {
            isLoadingModels -> "Fetching available models from OpenRouter…"
            modelCount > 0 -> "$modelCount models available for this slot."
            !openRouterKey.isNullOrBlank() -> "No models loaded yet. Tap Refresh models."
            else -> "Add your API key to load models."
        },
        style = Typography.bodySmall,
        color = OnSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp)
    )
}

@Composable
private fun CombinedApiTestSection(
    apiTestState: ApiConnectionTestState,
    canTest: Boolean,
    onTest: () -> Unit
) {
    OutlinedButton(
        onClick = onTest,
        enabled = canTest && apiTestState !is ApiConnectionTestState.Testing,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(Icons.Default.Bolt, null, tint = Primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            if (apiTestState is ApiConnectionTestState.Testing) "Testing…" else "Test API Connection",
            color = Primary
        )
    }
    when (apiTestState) {
        is ApiConnectionTestState.Success -> Text(
            apiTestState.message,
            style = Typography.bodySmall,
            color = Secondary,
            modifier = Modifier.padding(top = 6.dp)
        )
        is ApiConnectionTestState.Error -> Text(
            apiTestState.message,
            style = Typography.bodySmall,
            color = Error,
            modifier = Modifier.padding(top = 6.dp)
        )
        else -> Unit
    }
}

private fun hasRequiredApiKeys(
    profile: com.example.data.UserProfile,
    geminiKey: String?,
    groqKey: String?,
    openRouterKey: String?
): Boolean {
    val checks = AiRouteResolver.onlineApiChecks(profile)
    if (checks.isEmpty()) return false
    return checks.any { check ->
        when (check.provider) {
            "gemini" -> !geminiKey.isNullOrBlank()
            "groq" -> !groqKey.isNullOrBlank()
            "openrouter" -> !openRouterKey.isNullOrBlank() &&
                (AiProviderConfig.textModelsFor("openrouter").isNotEmpty() ||
                    AiProviderConfig.visionModelsFor("openrouter").isNotEmpty())
            else -> false
        }
    }
}

private fun profileUsesOpenRouter(profile: com.example.data.UserProfile): Boolean =
    profile.aiProvider == "openrouter" ||
        profile.aiTextProvider == "openrouter" ||
        profile.aiVisionProvider == "openrouter"

private fun providerIcon(provider: String): ImageVector = when (provider) {
    "gemini" -> Icons.Default.AutoAwesome
    "groq" -> Icons.Default.Memory
    "openrouter" -> Icons.Default.Hub
    else -> Icons.Default.PhoneAndroid
}

private fun isModelPickerTarget(target: PickerTarget): Boolean =
    target == PickerTarget.UnifiedModel ||
        target == PickerTarget.TextModel ||
        target == PickerTarget.VisionModel

private data class ModelPickerConfig(
    val title: String,
    val provider: String,
    val models: List<AiProviderConfig.AiModel>,
    val onPick: (AiProviderConfig.AiModel) -> Unit,
    val emptyMessage: String? = null
)

private fun modelPickerConfig(
    target: PickerTarget,
    profile: com.example.data.UserProfile,
    viewModel: GymViewModel,
    @Suppress("UNUSED_PARAMETER") openRouterRevision: Int
): ModelPickerConfig {
    return when (target) {
        PickerTarget.UnifiedModel -> {
            val provider = profile.aiProvider
            val models = AiProviderConfig.modelsFor(provider)
            if (provider == "openrouter" && models.isEmpty()) {
                ModelPickerConfig(
                    title = "Multimodal model",
                    provider = provider,
                    models = emptyList(),
                    onPick = {},
                    emptyMessage = openRouterEmptyMessage()
                )
            } else {
                ModelPickerConfig(
                    title = "Multimodal model",
                    provider = provider,
                    models = models,
                    onPick = { viewModel.updateAiModel(it.id) }
                )
            }
        }
        PickerTarget.TextModel -> {
            val provider = profile.aiTextProvider
            val models = AiProviderConfig.textModelsFor(provider)
            if (provider == "openrouter" && models.isEmpty()) {
                ModelPickerConfig(
                    title = "Text model",
                    provider = provider,
                    models = emptyList(),
                    onPick = {},
                    emptyMessage = openRouterEmptyMessage()
                )
            } else {
                ModelPickerConfig(
                    title = "Text model",
                    provider = provider,
                    models = models,
                    onPick = { viewModel.updateAiTextModel(it.id) }
                )
            }
        }
        PickerTarget.VisionModel -> {
            val provider = profile.aiVisionProvider
            val models = AiProviderConfig.visionModelsFor(provider)
            if (provider == "openrouter" && models.isEmpty()) {
                ModelPickerConfig(
                    title = "Vision model",
                    provider = provider,
                    models = emptyList(),
                    onPick = {},
                    emptyMessage = openRouterEmptyMessage()
                )
            } else {
                ModelPickerConfig(
                    title = "Vision model",
                    provider = provider,
                    models = models,
                    onPick = { viewModel.updateAiVisionModel(it.id) }
                )
            }
        }
        else -> ModelPickerConfig("", "", emptyList(), {})
    }
}

private data class PickerConfig(
    val title: String,
    val items: List<String>,
    val onPick: (Int) -> Unit,
    val emptyMessage: String? = null
)

private fun openRouterEmptyMessage(): String =
    "No OpenRouter models loaded. Add your API key, tap Refresh models, then try again."

private fun pickerConfig(
    target: PickerTarget,
    profile: com.example.data.UserProfile,
    viewModel: GymViewModel,
    installedModels: List<com.example.data.ModelDownloadManager.InstalledOfflineModel>,
    @Suppress("UNUSED_PARAMETER") openRouterRevision: Int
): PickerConfig {
    return when (target) {
        PickerTarget.UnifiedProvider -> PickerConfig(
            title = "Provider",
            items = AiProviderConfig.PROVIDERS.map { AiProviderConfig.displayNameFor(it) },
            onPick = { viewModel.updateAiProvider(AiProviderConfig.PROVIDERS[it]) }
        )
        PickerTarget.TextProvider -> PickerConfig(
            title = "Text provider",
            items = AiProviderConfig.PROVIDERS.map { AiProviderConfig.displayNameFor(it) },
            onPick = { viewModel.updateAiTextProvider(AiProviderConfig.PROVIDERS[it]) }
        )
        PickerTarget.VisionProvider -> PickerConfig(
            title = "Vision provider",
            items = AiProviderConfig.PROVIDERS.map { AiProviderConfig.displayNameFor(it) },
            onPick = { viewModel.updateAiVisionProvider(AiProviderConfig.PROVIDERS[it]) }
        )
        PickerTarget.UnifiedModel, PickerTarget.TextModel, PickerTarget.VisionModel, PickerTarget.OfflineModel ->
            PickerConfig(title = "", items = emptyList(), onPick = {})
    }
}
