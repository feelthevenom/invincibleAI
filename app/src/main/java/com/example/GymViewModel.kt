package com.example

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ExerciseItem
import com.example.data.ExerciseSet
import com.example.data.WorkoutExerciseKind
import com.example.data.CustomExercise
import com.example.data.WorkoutExerciseGroup
import com.example.data.WorkoutSplitGenerator
import com.example.data.DietDateUtils
import com.example.data.WorkoutRoutine
import com.example.data.RoutineExercise
import com.example.data.WorkoutCalorieEstimator
import com.example.data.WaterGoalCalculator
import com.example.data.BodyMeasurementTypes
import com.example.data.ExerciseWeightDefaults
import com.example.data.FitnessCalculator
import com.example.data.WorkoutGrouping
import com.example.data.AiAnalysisResult
import com.example.data.AiProviderConfig
import com.example.data.OpenRouterModelStore
import com.example.data.AiRouteResolver
import com.example.data.FoodItem
import android.graphics.Bitmap
import com.example.data.FoodNutritionCalculator
import com.example.data.GymRepository
import com.example.data.LocalFoodRepository
import com.example.data.LocalExerciseRepository
import com.example.data.CustomFoodItem
import com.example.data.MealEntry
import com.example.data.UserProfile
import com.example.data.WorkoutDashboardStats
import com.example.data.WorkoutStatsCalculator
import com.example.data.api.OpenFoodFactsRepository
import android.net.Uri
import com.example.data.AiStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FoodSearchUiState(
    val isLoading: Boolean = false,
    val query: String = "",
    val localResults: List<FoodItem> = emptyList(),
    val apiResults: List<FoodItem> = emptyList(),
    val isApiLoading: Boolean = false,
    val suggestions: List<FoodItem> = emptyList(),
    val error: String? = null
)

data class AiSettingsUiState(
    val progress2B: Float = 0f,
    val progress4B: Float = 0f,
    val isDownloading2B: Boolean = false,
    val isDownloading4B: Boolean = false,
    val openRouterModelsLoading: Boolean = false,
    val error: String? = null
)

data class ExerciseSearchUiState(
    val query: String = "",
    val localResults: List<ExerciseItem> = emptyList(),
    val suggestions: List<ExerciseItem> = emptyList(),
    val aiSuggestions: List<ExerciseItem> = emptyList(),
    val isLoading: Boolean = false,
    val aiLoading: Boolean = false,
    val error: String? = null,
    val aiError: String? = null
)

data class ActiveWorkoutSession(
    val routineId: Int?,
    val routineName: String,
    val dayLabel: String,
    val dayStart: Long,
    val startedAtMs: Long = System.currentTimeMillis()
)

data class WorkoutFinishSummary(
    val durationMs: Long,
    val totalVolumeKg: Float,
    val setsCompleted: Int,
    val totalSets: Int,
    val motivationalMessage: String
)

data class SuggestedRoutine(
    val routine: WorkoutRoutine,
    val reason: String,
    val exerciseCount: Int = 0,
    val isAiSuggested: Boolean = false
)

data class DayWorkoutLog(
    val dayStart: Long,
    val routineName: String,
    val routineId: Int?,
    val sets: List<ExerciseSet>,
    val exerciseGroups: List<WorkoutExerciseGroup>,
    val completedSets: Int,
    val totalSets: Int,
    val isFullyCompleted: Boolean
)

object ExerciseWeightUtils {
    fun lastLoggedWeight(allSets: List<ExerciseSet>, exerciseName: String): Float =
        allSets
            .filter { it.exerciseName.equals(exerciseName, ignoreCase = true) && it.weight > 0f }
            .maxByOrNull { it.timestamp }
            ?.weight ?: 0f

    fun maxWeight(allSets: List<ExerciseSet>, exerciseName: String): Float =
        allSets
            .filter { it.exerciseName.equals(exerciseName, ignoreCase = true) && it.weight > 0f }
            .maxOfOrNull { it.weight } ?: 0f

    fun lastWeightForSet(
        allSets: List<ExerciseSet>,
        exerciseName: String,
        routineLabel: String,
        setNumber: Int
    ): Float =
        allSets
            .filter {
                it.exerciseName.equals(exerciseName, ignoreCase = true) &&
                    it.workoutDayLabel == routineLabel &&
                    it.setNumber == setNumber &&
                    it.weight > 0f
            }
            .maxByOrNull { it.timestamp }
            ?.weight ?: 0f
}

class GymViewModel(
    private val repository: GymRepository,
    private val localFoodRepository: LocalFoodRepository,
    private val localExerciseRepository: LocalExerciseRepository,
    private val offRepository: OpenFoodFactsRepository = OpenFoodFactsRepository(),
    val aiManager: com.example.data.AiManager,
    val modelDownloadManager: com.example.data.ModelDownloadManager,
    val secureStorageManager: com.example.data.SecureStorageManager,
    private val exerciseGuideRepository: com.example.data.ExerciseGuideRepository,
    private val coachHistoryRepository: com.example.data.CoachHistoryRepository,
    private val openRouterApiClient: com.example.data.OpenRouterApiClient = com.example.data.OpenRouterApiClient()
) : ViewModel() {

    private val _aiConfigOverlay = MutableStateFlow<AiConfigOverlay?>(null)

    private val _coachHistorySessions = MutableStateFlow<List<com.example.data.CoachHistorySession>>(emptyList())
    val coachHistorySessions: StateFlow<List<com.example.data.CoachHistorySession>> = _coachHistorySessions.asStateFlow()

    val userProfile: StateFlow<UserProfile> = combine(
        repository.userProfile.map { it ?: UserProfile() },
        _aiConfigOverlay
    ) { db, overlay ->
        if (overlay == null) db else db.applyAiConfig(overlay)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserProfile())

    init {
        viewModelScope.launch {
            repository.userProfile.collect { stored ->
                val db = stored ?: return@collect
                _aiConfigOverlay.update { overlay ->
                    if (overlay != null && db.matchesAiConfig(overlay)) null else overlay
                }
            }
        }
        viewModelScope.launch {
            _coachHistorySessions.value = coachHistoryRepository.loadHistory()
        }
    }

    val allSets: StateFlow<List<ExerciseSet>> = repository.allSets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allMeals: StateFlow<List<MealEntry>> = repository.allMeals
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customFoods: StateFlow<List<CustomFoodItem>> = repository.customFoods
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _foodSearchState = MutableStateFlow(FoodSearchUiState())
    val foodSearchState: StateFlow<FoodSearchUiState> = _foodSearchState.asStateFlow()

    private val _aiSettingsState = MutableStateFlow(AiSettingsUiState())
    val aiSettingsState: StateFlow<AiSettingsUiState> = _aiSettingsState.asStateFlow()

    private val _modelsRevision = MutableStateFlow(0)
    val modelsRevision: StateFlow<Int> = _modelsRevision.asStateFlow()

    private val _secretsRevision = MutableStateFlow(0)
    val secretsRevision: StateFlow<Int> = _secretsRevision.asStateFlow()

    private val _aiConfigRevision = MutableStateFlow(0)
    val aiConfigRevision: StateFlow<Int> = _aiConfigRevision.asStateFlow()

    private fun bumpModelsRevision() = _modelsRevision.update { it + 1 }
    private fun bumpSecretsRevision() = _secretsRevision.update { it + 1 }
    private val _openRouterModelsRevision = MutableStateFlow(0)
    val openRouterModelsRevision: StateFlow<Int> = _openRouterModelsRevision.asStateFlow()

    private fun bumpOpenRouterModelsRevision() = _openRouterModelsRevision.update { it + 1 }
    private fun bumpAiConfigRevision() = _aiConfigRevision.update { it + 1 }

    private val _exerciseSearchState = MutableStateFlow(ExerciseSearchUiState())
    val exerciseSearchState: StateFlow<ExerciseSearchUiState> = _exerciseSearchState.asStateFlow()

    val customExercises: StateFlow<List<CustomExercise>> = repository.customExercises
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allRoutines: StateFlow<List<WorkoutRoutine>> = repository.allRoutines
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allWaterLogs: StateFlow<List<com.example.data.WaterLog>> = repository.allWaterLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allWeightLogs: StateFlow<List<com.example.data.WeightLog>> = repository.allWeightLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allBodyMeasurements: StateFlow<List<com.example.data.BodyMeasurementLog>> = repository.allBodyMeasurements
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allGoalSnapshots: StateFlow<List<com.example.data.DailyGoalSnapshot>> = repository.allGoalSnapshots
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allNotifications: StateFlow<List<com.example.data.AppNotification>> = repository.allNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeSession = MutableStateFlow<ActiveWorkoutSession?>(null)
    val activeSession: StateFlow<ActiveWorkoutSession?> = _activeSession.asStateFlow()

    private val _pendingFinishSummary = MutableStateFlow<WorkoutFinishSummary?>(null)
    val pendingFinishSummary: StateFlow<WorkoutFinishSummary?> = _pendingFinishSummary.asStateFlow()

    private val _suggestedRoutine = MutableStateFlow<SuggestedRoutine?>(null)
    val suggestedRoutine: StateFlow<SuggestedRoutine?> = _suggestedRoutine.asStateFlow()

    private val _workoutHandlesBack = MutableStateFlow(false)
    val workoutHandlesBack: StateFlow<Boolean> = _workoutHandlesBack.asStateFlow()

    private var suggestionDismissedForDay: Long? = null

    init {
        viewModelScope.launch { repository.seedBuiltInRoutinesIfEmpty() }
        viewModelScope.launch {
            userProfile.collectLatest { profile ->
                if (profile.currentWeight > 0f && allWeightLogs.value.isEmpty()) {
                    repository.upsertWeightLog(profile.currentWeight, System.currentTimeMillis())
                }
            }
        }
    }

    private var exerciseSearchJob: Job? = null
    private val seededWorkoutDays = mutableSetOf<String>()
    private var localSearchJob: Job? = null
    private var apiSearchJob: Job? = null
    private var downloadJob2B: Job? = null
    private var downloadJob4B: Job? = null

    fun saveProfile(profile: UserProfile) {
        _aiConfigOverlay.value = profile.toAiConfigOverlay()
        viewModelScope.launch { repository.saveProfile(profile) }
    }

    private fun applyAiProviderChange(current: UserProfile, provider: String): UserProfile {
        if (provider == "offline") {
            return current.copy(
                aiProvider = provider,
                aiTextProvider = provider,
                aiVisionProvider = provider
            )
        }
        val textModel = AiProviderConfig.defaultModelFor(provider, vision = false)
        val visionModel = AiProviderConfig.defaultModelFor(provider, vision = true)
        val unifiedModel = when (provider) {
            "groq" -> visionModel
            "openrouter" -> OpenRouterModelStore.defaultUnifiedModelId().ifBlank { visionModel.ifBlank { textModel } }
            else -> textModel
        }
        return current.copy(
            aiProvider = provider,
            aiModelId = unifiedModel,
            aiTextProvider = provider,
            aiTextModelId = textModel,
            aiVisionProvider = provider,
            aiVisionModelId = visionModel
        )
    }

    private fun applyAiTextProviderChange(current: UserProfile, provider: String): UserProfile {
        val model = if (provider == "offline") {
            current.aiTextModelId
        } else {
            AiProviderConfig.defaultModelFor(provider, vision = false)
        }
        return current.copy(aiTextProvider = provider, aiTextModelId = model)
    }

    private fun applyAiVisionProviderChange(current: UserProfile, provider: String): UserProfile {
        val model = if (provider == "offline") {
            current.aiVisionModelId
        } else {
            AiProviderConfig.defaultModelFor(provider, vision = true)
        }
        return current.copy(aiVisionProvider = provider, aiVisionModelId = model)
    }

    fun startModelDownload(modelType: String) {
        if (modelType == "offline_2b") {
            downloadJob2B?.cancel()
            downloadJob2B = viewModelScope.launch {
                _aiSettingsState.update { it.copy(isDownloading2B = true, progress2B = 0f, error = null) }
                modelDownloadManager.downloadModel(modelType).collect { status ->
                    when (status) {
                        is com.example.data.DownloadStatus.Downloading ->
                            _aiSettingsState.update { it.copy(progress2B = status.progress) }
                        com.example.data.DownloadStatus.Success -> {
                            viewModelScope.launch { aiManager.releaseOfflineEngine() }
                            bumpModelsRevision()
                            _aiSettingsState.update { it.copy(isDownloading2B = false, progress2B = 1f) }
                        }
                        is com.example.data.DownloadStatus.Error ->
                            _aiSettingsState.update { it.copy(isDownloading2B = false, error = status.message) }
                    }
                }
            }
        } else {
            downloadJob4B?.cancel()
            downloadJob4B = viewModelScope.launch {
                _aiSettingsState.update { it.copy(isDownloading4B = true, progress4B = 0f, error = null) }
                modelDownloadManager.downloadModel(modelType).collect { status ->
                    when (status) {
                        is com.example.data.DownloadStatus.Downloading ->
                            _aiSettingsState.update { it.copy(progress4B = status.progress) }
                        com.example.data.DownloadStatus.Success -> {
                            viewModelScope.launch { aiManager.releaseOfflineEngine() }
                            bumpModelsRevision()
                            _aiSettingsState.update { it.copy(isDownloading4B = false, progress4B = 1f) }
                        }
                        is com.example.data.DownloadStatus.Error ->
                            _aiSettingsState.update { it.copy(isDownloading4B = false, error = status.message) }
                    }
                }
            }
        }
    }

    fun cancelModelDownload(modelType: String) {
        if (modelType == "offline_2b") {
            downloadJob2B?.cancel()
            _aiSettingsState.update { it.copy(isDownloading2B = false, progress2B = 0f) }
        } else {
            downloadJob4B?.cancel()
            _aiSettingsState.update { it.copy(isDownloading4B = false, progress4B = 0f) }
        }
    }

    fun deleteModel(modelType: String) {
        cancelModelDownload(modelType)
        modelDownloadManager.deleteModel(modelType)
        viewModelScope.launch { aiManager.releaseOfflineEngine() }
        bumpModelsRevision()
        val current = userProfile.value
        if (current.offlineModelId == modelType) {
            val next = modelDownloadManager.listInstalledModels().firstOrNull()?.id
            if (next != null) updateOfflineModelId(next)
        }
    }

    fun importAnyModel(uri: Uri) {
        viewModelScope.launch {
            when (val result = modelDownloadManager.importAnyModel(uri)) {
                com.example.data.DownloadStatus.Success -> {
                    aiManager.releaseOfflineEngine()
                    bumpModelsRevision()
                    modelDownloadManager.listInstalledModels().lastOrNull()?.let { imported ->
                        updateOfflineModelId(imported.id)
                    }
                    _aiSettingsState.update { it.copy(error = null) }
                }
                is com.example.data.DownloadStatus.Error -> {
                    _aiSettingsState.update { it.copy(error = result.message) }
                }
                else -> Unit
            }
        }
    }

    fun importModel(modelType: String, uri: Uri) {
        viewModelScope.launch {
            when (val result = modelDownloadManager.importModelFromUri(modelType, uri)) {
                com.example.data.DownloadStatus.Success -> {
                    aiManager.releaseOfflineEngine()
                    bumpModelsRevision()
                    _aiSettingsState.update { it.copy(error = null) }
                }
                is com.example.data.DownloadStatus.Error -> {
                    _aiSettingsState.update { it.copy(error = result.message) }
                }
                else -> Unit
            }
        }
    }

    fun aiStatus(): AiStatus {
        val p = userProfile.value
        if (p.aiSplitModels) {
            val text = AiRouteResolver.textSlot(p)
            val vision = AiRouteResolver.visionSlot(p)
            val textStatus = aiManager.getStatus(text.provider, text.modelId, text.offlineModelId)
            val visionStatus = aiManager.getStatus(vision.provider, vision.modelId, vision.offlineModelId)
            val ready = textStatus.isReady && visionStatus.isReady
            return AiStatus(
                mode = "split",
                isReady = ready,
                label = if (ready) "Split models — ready" else "Split models — setup needed",
                detail = "Text: ${textStatus.label}. Vision: ${visionStatus.label}."
            )
        }
        return aiManager.getStatus(p.aiProvider, p.aiModelId, p.offlineModelId)
    }

    private fun slotReady(slot: com.example.data.AiSlot): Boolean {
        if (slot.provider == "offline") {
            return modelDownloadManager.isModelInstalled(slot.offlineModelId)
        }
        return aiManager.getStatus(slot.provider, slot.modelId, slot.offlineModelId).isReady
    }

    fun isAiConfigured(): Boolean = slotReady(AiRouteResolver.textSlot(userProfile.value))

    fun supportsVision(): Boolean {
        val slot = AiRouteResolver.visionSlot(userProfile.value)
        if (!slotReady(slot)) return false
        if (slot.provider == "offline") {
            return modelDownloadManager.isModelInstalled(slot.offlineModelId)
        }
        return AiProviderConfig.supportsVision(slot.provider, slot.modelId)
    }

    suspend fun generateFoodSuggestions(query: String): List<FoodItem> {
        val slot = AiRouteResolver.textSlot(userProfile.value)
        return aiManager.generateFoodSuggestions(query, slot.provider, slot.modelId, slot.offlineModelId)
    }

    suspend fun analyzeFoodImage(bitmap: Bitmap): AiAnalysisResult {
        val slot = AiRouteResolver.visionSlot(userProfile.value)
        return aiManager.analyzeFoodImage(bitmap, slot.provider, slot.modelId, slot.offlineModelId)
    }

    fun updateAiSplitModels(split: Boolean) {
        val current = userProfile.value
        saveProfile(current.copy(aiSplitModels = split))
        bumpAiConfigRevision()
        refreshCoachPromptChips()
    }

    fun updateAiTextProvider(provider: String) {
        val updated = applyAiTextProviderChange(userProfile.value, provider)
        saveProfile(updated)
        bumpAiConfigRevision()
        if (provider != "offline") {
            viewModelScope.launch { aiManager.releaseOfflineEngine() }
        }
        refreshCoachPromptChips()
        if (provider == "openrouter") refreshOpenRouterModels()
    }

    fun updateAiTextModel(modelId: String) {
        saveProfile(userProfile.value.copy(aiTextModelId = modelId))
        bumpAiConfigRevision()
    }

    fun updateAiVisionProvider(provider: String) {
        val updated = applyAiVisionProviderChange(userProfile.value, provider)
        saveProfile(updated)
        bumpAiConfigRevision()
        if (provider != "offline") {
            viewModelScope.launch { aiManager.releaseOfflineEngine() }
        }
        if (provider == "openrouter") refreshOpenRouterModels()
    }

    fun updateAiVisionModel(modelId: String) {
        saveProfile(userProfile.value.copy(aiVisionModelId = modelId))
        bumpAiConfigRevision()
    }

    fun updateAiProvider(provider: String) {
        val updated = applyAiProviderChange(userProfile.value, provider)
        saveProfile(updated)
        bumpAiConfigRevision()
        bumpModelsRevision()
        if (provider != "offline") {
            viewModelScope.launch { aiManager.releaseOfflineEngine() }
        }
        refreshCoachPromptChips()
        if (provider == "openrouter") refreshOpenRouterModels()
    }

    fun updateAiModel(modelId: String) {
        val current = userProfile.value
        saveProfile(
            current.copy(
                aiModelId = modelId,
                aiTextModelId = modelId,
                aiVisionModelId = modelId
            )
        )
        bumpAiConfigRevision()
    }

    fun updateOfflineModelId(offlineModelId: String) {
        val current = userProfile.value
        saveProfile(current.copy(offlineModelId = offlineModelId))
        bumpModelsRevision()
        bumpAiConfigRevision()
    }

    fun setGeminiApiKey(key: String) {
        secureStorageManager.saveGeminiApiKey(key)
        bumpSecretsRevision()
    }

    fun clearGeminiApiKey() {
        secureStorageManager.clearGeminiApiKey()
        bumpSecretsRevision()
    }

    fun getGeminiApiKey(): String? {
        return secureStorageManager.getGeminiApiKey()
    }

    private val _apiConnectionTestState = MutableStateFlow<ApiConnectionTestState>(ApiConnectionTestState.Idle)
    val apiConnectionTestState: StateFlow<ApiConnectionTestState> = _apiConnectionTestState.asStateFlow()

    fun testOnlineApiConnections() {
        viewModelScope.launch {
            _apiConnectionTestState.value = ApiConnectionTestState.Testing
            val profile = userProfile.value
            val checks = AiRouteResolver.onlineApiChecks(profile)
            if (checks.isEmpty()) {
                _apiConnectionTestState.value = ApiConnectionTestState.Success("Using offline models — no API to test.")
                return@launch
            }

            val failures = mutableListOf<String>()
            val successes = mutableListOf<String>()
            val tested = mutableSetOf<Pair<String, String>>()

            for (check in checks) {
                val providerName = AiProviderConfig.displayNameFor(check.provider)
                val keyMissing = when (check.provider) {
                    "gemini" -> secureStorageManager.getGeminiApiKey().isNullOrBlank()
                    "groq" -> secureStorageManager.getGroqApiKey().isNullOrBlank()
                    "openrouter" -> secureStorageManager.getOpenRouterApiKey().isNullOrBlank()
                    else -> true
                }
                if (keyMissing) {
                    failures.add("$providerName (${check.slotLabel}): No API key configured")
                    continue
                }
                if (check.modelId.isBlank()) {
                    failures.add("$providerName (${check.slotLabel}): No model selected")
                    continue
                }
                val dedupeKey = check.provider to check.modelId
                if (!tested.add(dedupeKey)) continue

                val result = when (check.provider) {
                    "gemini" -> aiManager.testGeminiConnection(check.modelId)
                    "groq" -> aiManager.testGroqConnection(check.modelId)
                    "openrouter" -> aiManager.testOpenRouterConnection(check.modelId)
                    else -> Result.failure(IllegalStateException("Unsupported provider"))
                }
                result.fold(
                    onSuccess = { msg ->
                        val slotNote = if (checks.count { it.provider == check.provider && it.modelId == check.modelId } > 1) {
                            " (${check.slotLabel})"
                        } else ""
                        successes.add("$providerName$slotNote: $msg")
                    },
                    onFailure = { err ->
                        failures.add("$providerName (${check.slotLabel}): ${err.message ?: "Connection failed"}")
                    }
                )
            }

            _apiConnectionTestState.value = when {
                failures.isEmpty() -> ApiConnectionTestState.Success(successes.joinToString("\n"))
                successes.isEmpty() -> ApiConnectionTestState.Error(failures.joinToString("\n"))
                else -> ApiConnectionTestState.Error(
                    failures.joinToString("\n") + "\n\n" + successes.joinToString("\n") { "✓ $it" }
                )
            }
        }
    }

    fun clearApiConnectionTestState() {
        _apiConnectionTestState.value = ApiConnectionTestState.Idle
    }

    // ── Coach AI chat ─────────────────────────────────────────────────

    private val _coachChatMessages = MutableStateFlow<List<CoachChatMessage>>(emptyList())
    val coachChatMessages: StateFlow<List<CoachChatMessage>> = _coachChatMessages.asStateFlow()

    private val _coachChatLoading = MutableStateFlow(false)
    val coachChatLoading: StateFlow<Boolean> = _coachChatLoading.asStateFlow()

    private var coachWelcomeSent = false

    private val _coachPromptChips = MutableStateFlow<List<CoachPromptChip>>(emptyList())
    val coachPromptChips: StateFlow<List<CoachPromptChip>> = _coachPromptChips.asStateFlow()

    private val _pendingExerciseSteps = MutableStateFlow<String?>(null)
    val pendingExerciseSteps: StateFlow<String?> = _pendingExerciseSteps.asStateFlow()

    private val _exerciseGuideState = MutableStateFlow<ExerciseGuideUiState>(ExerciseGuideUiState.Idle)
    val exerciseGuideState: StateFlow<ExerciseGuideUiState> = _exerciseGuideState.asStateFlow()

    fun loadExerciseGuide(exerciseName: String) {
        viewModelScope.launch {
            _exerciseGuideState.value = ExerciseGuideUiState.Loading
            when (val result = exerciseGuideRepository.loadGuide(exerciseName)) {
                is com.example.data.ExerciseGuideResult.Success ->
                    _exerciseGuideState.value = ExerciseGuideUiState.Ready(result.guide)
                com.example.data.ExerciseGuideResult.OfflineNoCache ->
                    _exerciseGuideState.value = ExerciseGuideUiState.NeedsInternet(exerciseName)
                com.example.data.ExerciseGuideResult.NoOnlineMatch ->
                    _exerciseGuideState.value = ExerciseGuideUiState.NoMatch(exerciseName)
                is com.example.data.ExerciseGuideResult.Error ->
                    _exerciseGuideState.value = ExerciseGuideUiState.Error(result.message)
            }
        }
    }

    fun fetchExerciseGuideOnline(exerciseName: String) {
        viewModelScope.launch {
            val previous = _exerciseGuideState.value
            _exerciseGuideState.value = ExerciseGuideUiState.Loading
            when (val result = exerciseGuideRepository.loadGuide(exerciseName, forceOnline = true)) {
                is com.example.data.ExerciseGuideResult.Success ->
                    _exerciseGuideState.value = ExerciseGuideUiState.Ready(result.guide)
                com.example.data.ExerciseGuideResult.OfflineNoCache ->
                    _exerciseGuideState.value = ExerciseGuideUiState.NeedsInternet(exerciseName)
                com.example.data.ExerciseGuideResult.NoOnlineMatch ->
                    _exerciseGuideState.value = ExerciseGuideUiState.NoMatch(exerciseName)
                is com.example.data.ExerciseGuideResult.Error ->
                    _exerciseGuideState.value = if (previous is ExerciseGuideUiState.Ready) previous
                    else ExerciseGuideUiState.Error(result.message)
            }
        }
    }

    fun fillExerciseGuideFromAi(exerciseName: String, replaceExisting: Boolean = true) {
        viewModelScope.launch {
            if (!isAiConfigured()) {
                _exerciseGuideState.value = ExerciseGuideUiState.Error(
                    "Configure AI in Settings to generate steps."
                )
                return@launch
            }
            val current = _exerciseGuideState.value
            if (current is ExerciseGuideUiState.Ready && replaceExisting) {
                _exerciseGuideState.value = current.copy(isGeneratingAi = true)
            } else if (current !is ExerciseGuideUiState.Ready) {
                _exerciseGuideState.value = ExerciseGuideUiState.Loading
            }

            val slot = AiRouteResolver.textSlot(userProfile.value)
            val reply = aiManager.generateExerciseSteps(
                exerciseName = exerciseName,
                provider = slot.provider,
                modelId = slot.modelId,
                offlineModelId = slot.offlineModelId
            )
            val steps = reply?.let { com.example.data.ExerciseStepFormatter.parseAiSteps(it) }.orEmpty()
            if (steps.isEmpty()) {
                _exerciseGuideState.value = when (current) {
                    is ExerciseGuideUiState.Ready -> current.copy(isGeneratingAi = false)
                    else -> ExerciseGuideUiState.Error("Couldn't generate steps. Try again or check AI Settings.")
                }
                return@launch
            }
            val guide = exerciseGuideRepository.saveAiGuide(exerciseName, steps)
            _exerciseGuideState.value = ExerciseGuideUiState.Ready(guide, isGeneratingAi = false)
        }
    }

    fun clearExerciseGuideState() {
        _exerciseGuideState.value = ExerciseGuideUiState.Idle
    }

    fun exerciseGuideIsOnline(): Boolean = exerciseGuideRepository.isOnline()

    fun requestExerciseStepsFromCoach(exerciseName: String) {
        _pendingExerciseSteps.value = exerciseName.trim()
    }

    fun clearPendingExerciseSteps() {
        _pendingExerciseSteps.value = null
    }

    fun sendExerciseStepsFromAi(exerciseName: String) {
        viewModelScope.launch {
            if (!isAiConfigured()) return@launch
            ensureCoachWelcomeMessage()
            _coachChatLoading.value = true
            val slot = AiRouteResolver.textSlot(userProfile.value)
            val reply = aiManager.generateExerciseSteps(
                exerciseName = exerciseName,
                provider = slot.provider,
                modelId = slot.modelId,
                offlineModelId = slot.offlineModelId
            )
            val body = reply?.trim()?.takeIf { it.isNotBlank() }
                ?: "Couldn't generate steps for **$exerciseName** right now. Check AI Settings and try again."
            _coachChatMessages.update {
                it + CoachChatMessage(
                    role = CoachChatRole.ASSISTANT,
                    text = "**$exerciseName — step-by-step form**\n\n$body"
                )
            }
            _coachChatLoading.value = false
        }
    }

    fun archiveCoachSessionOnBackground() {
        viewModelScope.launch {
            val messages = _coachChatMessages.value
            if (messages.any { it.role == CoachChatRole.USER }) {
                coachHistoryRepository.archiveSession(messages)
                _coachHistorySessions.value = coachHistoryRepository.loadHistory()
            }
            _coachChatMessages.value = emptyList()
            coachWelcomeSent = false
        }
    }

    suspend fun loadCoachHistorySession(id: String): com.example.data.CoachHistorySession? =
        coachHistoryRepository.getSession(id)

    fun refreshCoachHistory() {
        viewModelScope.launch {
            _coachHistorySessions.value = coachHistoryRepository.loadHistory()
        }
    }

    fun refreshCoachPromptChips() {
        viewModelScope.launch {
            _coachPromptChips.value = buildHeuristicCoachChips()
            if (!isAiConfigured()) return@launch
            val slot = AiRouteResolver.textSlot(userProfile.value)
            aiManager.generateCoachPromptSuggestions(
                userContext = buildCoachChatContext(),
                provider = slot.provider,
                modelId = slot.modelId,
                offlineModelId = slot.offlineModelId
            )?.takeIf { it.isNotEmpty() }?.let { _coachPromptChips.value = it }
        }
    }

    private fun buildHeuristicCoachChips(): List<CoachPromptChip> {
        val profile = userProfile.value
        val stats = workoutDashboardStats()
        val todayStart = DietDateUtils.startOfTodayMillis()
        val todayEnd = DietDateUtils.endOfDayMillis(todayStart)
        val todayMeals = allMeals.value.filter { it.timestamp in todayStart until todayEnd }
        val proteinGap = (profile.protein - todayMeals.sumOf { it.protein }).coerceAtLeast(0)
        val chips = mutableListOf<CoachPromptChip>()
        chips += CoachPromptChip(
            "Leg Day Tips",
            "What should I focus on for leg day based on my recent training and ${profile.fitnessLevel} level?"
        )
        chips += CoachPromptChip(
            "Protein Intake",
            if (proteinGap > 0) "I'm ${proteinGap}g short on protein today — what should I eat to hit ${profile.protein}g?"
            else "Am I hitting my protein target today? Review my logged meals."
        )
        chips += CoachPromptChip(
            "Workout Plan",
            "I've done ${stats.workoutsCompleted}/${stats.workoutsTarget} workouts this week. What should I train next?"
        )
        if (stats.prsHit > 0) {
            chips += CoachPromptChip(
                "Progressive Overload",
                "I hit ${stats.prsHit} PRs recently. How should I progress my main lifts safely?"
            )
        }
        chips += CoachPromptChip(
            "Recovery Check",
            "Based on my volume (${stats.totalVolumeKg.toInt()} kg this week), am I recovering well for my ${profile.goal.lowercase()} goal?"
        )
        return chips.take(6)
    }

    fun ensureCoachWelcomeMessage() {
        if (coachWelcomeSent && _coachChatMessages.value.isNotEmpty()) {
            refreshCoachPromptChips()
            return
        }
        if (_coachChatMessages.value.isNotEmpty()) return
        coachWelcomeSent = true
        val stats = workoutDashboardStats()
        val profile = userProfile.value
        val welcome = buildString {
            append("Ready to crush today's session? ")
            when {
                stats.workoutsCompleted >= stats.workoutsTarget ->
                    append("You've hit ${stats.workoutsCompleted}/${stats.workoutsTarget} workouts this week — great consistency.")
                stats.workoutsCompleted > 0 ->
                    append("You're at ${stats.workoutsCompleted}/${stats.workoutsTarget} workouts this week — keep the momentum going.")
                else ->
                    append("Your ${profile.goal.lowercase()} plan is set — ask me about diet, workouts, or recovery anytime.")
            }
        }
        _coachChatMessages.value = listOf(
            CoachChatMessage(role = CoachChatRole.ASSISTANT, text = welcome)
        )
        refreshCoachPromptChips()
    }

    fun sendCoachMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank() || _coachChatLoading.value) return
        val userMsg = CoachChatMessage(role = CoachChatRole.USER, text = trimmed)
        _coachChatMessages.update { it + userMsg }
        _coachChatLoading.value = true
        viewModelScope.launch {
            val profile = userProfile.value
            val history = _coachChatMessages.value
                .dropLast(1)
                .takeLast(12)
                .map { msg ->
                    (if (msg.role == CoachChatRole.USER) "user" else "assistant") to msg.text
                }
            val context = buildCoachChatContext()
            val textSlot = AiRouteResolver.textSlot(profile)
            val reply = if (isAiConfigured()) {
                aiManager.generateCoachChatReply(
                    userContext = context,
                    conversationHistory = history,
                    userMessage = trimmed,
                    provider = textSlot.provider,
                    modelId = textSlot.modelId,
                    offlineModelId = textSlot.offlineModelId
                )
            } else null
            val responseText = reply?.trim()?.takeIf { it.isNotBlank() }
                ?: buildHeuristicCoachReply(trimmed, textSlot.provider)
            _coachChatMessages.update { it + CoachChatMessage(role = CoachChatRole.ASSISTANT, text = responseText) }
            _coachChatLoading.value = false
        }
    }

    fun buildCoachChatContext(): String = buildString {
        append(buildDietCoachSummary())
        append("\n")
        append(buildExerciseCoachSummary())
        val routines = allRoutines.value
        if (routines.isNotEmpty()) {
            append("\nRoutines: ${routines.joinToString(", ") { it.name }}.")
        }
        suggestedRoutine.value?.let {
            append("\nSuggested today: ${it.routine.name} (${it.exerciseCount} exercises).")
        }
    }

    private fun buildHeuristicCoachReply(userMessage: String, activeProvider: String): String {
        val lower = userMessage.lowercase()
        val profile = userProfile.value
        val stats = workoutDashboardStats()
        val todayStart = DietDateUtils.startOfTodayMillis()
        val todayEnd = DietDateUtils.endOfDayMillis(todayStart)
        val todayMeals = allMeals.value.filter { it.timestamp in todayStart until todayEnd }
        val proteinGap = (profile.protein - todayMeals.sumOf { it.protein }).coerceAtLeast(0)

        if (!isAiConfigured()) {
            return "I'm in offline coach mode — configure Gemini, Groq, OpenRouter, or an offline model in **AI Settings** for personalized answers.\n\n" +
                when {
                    lower.contains("protein") ->
                        "Today you've logged ${todayMeals.sumOf { it.protein }}g of your ${profile.protein}g protein target (${proteinGap}g remaining)."
                    lower.contains("leg") ->
                        "For leg day, prioritize squats or leg press, RDLs or ham curls, and lunges or split squats — 3–4 sets of 8–12 reps based on your ${profile.fitnessLevel.lowercase()} level."
                    lower.contains("squat") || lower.contains("back") ->
                        "If your lower back feels tight, reduce spinal loading: swap heavy back squats for **Bulgarian split squats (3×10/leg)** and **leg press with slow eccentrics (4×12)**."
                    else ->
                        "Ask about protein, leg day, workout swaps, or your weekly volume (${stats.totalVolumeKg.toInt()} kg logged this week)."
                }
        }
        return when (activeProvider) {
            "offline" ->
                "On-device coach couldn't finish that reply. Try a shorter question, wait a moment for the model to load, or confirm your offline model is downloaded in **AI Settings**."
            "groq" ->
                "Groq didn't respond. Check your Groq API key in **AI Settings** and tap **Test API Connection**."
            "openrouter" ->
                "OpenRouter didn't respond. Check your OpenRouter API key in **AI Settings**, refresh models, and tap **Test API Connection**."
            "gemini" ->
                "Gemini didn't respond. Check your Gemini API key in **AI Settings** and tap **Test API Connection**."
            else ->
                "I couldn't reach the AI right now. Check your connection and API key in Settings, then try again."
        }
    }

    fun setGroqApiKey(key: String) {
        secureStorageManager.saveGroqApiKey(key)
        bumpSecretsRevision()
    }

    fun clearGroqApiKey() {
        secureStorageManager.clearGroqApiKey()
        bumpSecretsRevision()
    }

    fun getGroqApiKey(): String? {
        return secureStorageManager.getGroqApiKey()
    }

    fun setOpenRouterApiKey(key: String) {
        secureStorageManager.saveOpenRouterApiKey(key)
        bumpSecretsRevision()
        refreshOpenRouterModels()
    }

    fun clearOpenRouterApiKey() {
        secureStorageManager.clearOpenRouterApiKey()
        OpenRouterModelStore.clear()
        bumpSecretsRevision()
        bumpOpenRouterModelsRevision()
    }

    fun getOpenRouterApiKey(): String? = secureStorageManager.getOpenRouterApiKey()

    fun refreshOpenRouterModels() {
        viewModelScope.launch {
            _aiSettingsState.update { it.copy(openRouterModelsLoading = true, error = null) }
            val key = secureStorageManager.getOpenRouterApiKey()?.trim()
            if (key.isNullOrBlank()) {
                OpenRouterModelStore.clear()
                bumpOpenRouterModelsRevision()
                _aiSettingsState.update { it.copy(openRouterModelsLoading = false) }
                return@launch
            }
            try {
                val models = openRouterApiClient.fetchModels(key)
                OpenRouterModelStore.update(models)
                syncOpenRouterModelSelection()
                bumpOpenRouterModelsRevision()
                bumpAiConfigRevision()
            } catch (e: Exception) {
                _aiSettingsState.update { it.copy(error = e.message ?: "Failed to load OpenRouter models.") }
            }
            _aiSettingsState.update { it.copy(openRouterModelsLoading = false) }
        }
    }

    private fun syncOpenRouterModelSelection() {
        val current = userProfile.value
        fun resolved(provider: String, modelId: String, vision: Boolean): String {
            if (provider != "openrouter") return modelId
            if (OpenRouterModelStore.findModel(modelId) != null) return modelId
            return if (vision) OpenRouterModelStore.defaultVisionModelId() else OpenRouterModelStore.defaultTextModelId()
        }
        val updated = current.copy(
            aiModelId = resolved(current.aiProvider, current.aiModelId, vision = true),
            aiTextModelId = resolved(current.aiTextProvider, current.aiTextModelId, vision = false),
            aiVisionModelId = resolved(current.aiVisionProvider, current.aiVisionModelId, vision = true)
        )
        if (updated != current) saveProfile(updated)
    }

    fun setHuggingFaceToken(token: String) {
        secureStorageManager.saveHuggingFaceToken(token.trim())
    }

    fun getHuggingFaceToken(): String? = secureStorageManager.getHuggingFaceToken()

    fun completeOnboarding(profile: UserProfile) {
        viewModelScope.launch {
            repository.saveProfile(
                profile.copy(
                    onboardingComplete = true,
                    workoutSetupComplete = false
                )
            )
            if (profile.currentWeight > 0f) {
                repository.upsertWeightLog(profile.currentWeight, System.currentTimeMillis())
            }
        }
    }

    fun completeWorkoutSetup(profile: UserProfile) {
        viewModelScope.launch {
            repository.saveProfile(
                profile.copy(
                    workoutSetupComplete = true,
                    notificationsEnabled = profile.notificationsEnabled || userProfile.value.notificationsEnabled
                )
            )
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            saveProfile(userProfile.value.copy(notificationsEnabled = enabled))
        }
    }

    private fun profileWithRecalculatedCalories(profile: UserProfile, newWeight: Float): UserProfile {
        val maintenance = FitnessCalculator.tdee(
            profile.gender, newWeight, profile.height, profile.age, profile.activityLevel
        )
        val dailyCal = if (profile.targetWeightChangePerWeek > 0f) {
            FitnessCalculator.dailyCaloriesFromWeeklyChange(
                maintenance, profile.goal, profile.targetWeightChangePerWeek
            )
        } else {
            FitnessCalculator.dailyCalories(
                profile.gender, newWeight, profile.height, profile.age, profile.activityLevel, profile.goal
            )
        }
        val macros = FitnessCalculator.calculateMacros(newWeight, dailyCal, profile.goal)
        val adjustment = FitnessCalculator.calorieAdjustmentDaily(maintenance, dailyCal)
        return profile.copy(
            currentWeight = newWeight,
            maintenanceCalories = maintenance,
            dailyCalories = dailyCal,
            protein = macros.protein,
            carbs = macros.carbs,
            fat = macros.fat,
            fiber = macros.fiber,
            calorieAdjustmentDaily = adjustment,
            calorieAdjustmentWeekly = adjustment * 7,
            weeksToGoal = FitnessCalculator.weeksToReachGoal(
                newWeight, profile.targetWeight, profile.targetWeightChangePerWeek
            )
        )
    }

    fun saveWorkoutPreferencesEdits(
        fitnessLevel: String,
        benchmarkSkipped: Boolean,
        squat1RmKg: Float,
        benchPress1RmKg: Float,
        deadlift1RmKg: Float,
        workoutDaysPerWeek: Int,
        weekStartDay: Int,
        equipmentSelection: String,
        gymLocation: String
    ) {
        val current = userProfile.value
        if (!current.onboardingComplete) return
        saveProfile(
            current.copy(
                fitnessLevel = fitnessLevel,
                benchmarkSkipped = benchmarkSkipped,
                squat1RmKg = squat1RmKg,
                benchPress1RmKg = benchPress1RmKg,
                deadlift1RmKg = deadlift1RmKg,
                workoutDaysPerWeek = workoutDaysPerWeek.coerceIn(3, 7),
                weekStartDay = weekStartDay,
                equipmentSelection = equipmentSelection,
                gymLocation = gymLocation
            )
        )
    }

    fun savePersonalDetailsEdits(
        targetWeight: Float,
        dailyCalories: Int,
        protein: Int,
        carbs: Int,
        fat: Int,
        fiber: Int,
        targetWeightChangePerWeek: Float? = null,
        cuisinePreferences: String? = null
    ) {
        val current = userProfile.value
        if (!current.onboardingComplete) return
        val weekly = targetWeightChangePerWeek ?: current.targetWeightChangePerWeek
        val base = current.copy(
            targetWeight = targetWeight,
            targetWeightChangePerWeek = weekly,
            cuisinePreferences = cuisinePreferences ?: current.cuisinePreferences
        )
        val updated = if (targetWeightChangePerWeek != null) {
            val plan = FitnessCalculator.nutritionPlanFromProfile(base)
            base.copy(
                dailyCalories = plan.dailyCalories,
                protein = plan.macros.protein,
                carbs = plan.macros.carbs,
                fat = plan.macros.fat,
                fiber = plan.macros.fiber,
                maintenanceCalories = plan.maintenanceCalories,
                calorieAdjustmentDaily = plan.calorieAdjustmentDaily,
                calorieAdjustmentWeekly = plan.calorieAdjustmentWeekly,
                weeksToGoal = FitnessCalculator.weeksToReachGoal(
                    current.currentWeight, targetWeight, weekly
                )
            )
        } else {
            val maintenance = FitnessCalculator.tdee(
                current.gender, current.currentWeight, current.height, current.age, current.activityLevel
            )
            val adjustment = FitnessCalculator.calorieAdjustmentDaily(maintenance, dailyCalories)
            base.copy(
                dailyCalories = dailyCalories,
                protein = protein,
                carbs = carbs,
                fat = fat,
                fiber = fiber,
                maintenanceCalories = maintenance,
                calorieAdjustmentDaily = adjustment,
                calorieAdjustmentWeekly = adjustment * 7,
                weeksToGoal = FitnessCalculator.weeksToReachGoal(
                    current.currentWeight, targetWeight, weekly
                )
            )
        }
        saveProfile(updated)
        viewModelScope.launch { repository.updateTodayGoalSnapshot(updated) }
    }

    fun savePhysicalMetricsEdits(age: Int, gender: String, heightCm: Int) {
        val current = userProfile.value
        if (!current.onboardingComplete) return
        val base = current.copy(age = age, gender = gender, height = heightCm)
        val plan = FitnessCalculator.nutritionPlanFromProfile(base)
        val updated = base.copy(
            dailyCalories = plan.dailyCalories,
            protein = plan.macros.protein,
            carbs = plan.macros.carbs,
            fat = plan.macros.fat,
            fiber = plan.macros.fiber,
            maintenanceCalories = plan.maintenanceCalories,
            calorieAdjustmentDaily = plan.calorieAdjustmentDaily,
            calorieAdjustmentWeekly = plan.calorieAdjustmentWeekly
        )
        saveProfile(updated)
        viewModelScope.launch { repository.updateTodayGoalSnapshot(updated) }
    }

    fun saveActivityGoalEdits(activityLevel: String, goal: String) {
        val current = userProfile.value
        if (!current.onboardingComplete) return
        val base = current.copy(activityLevel = activityLevel, goal = goal)
        val plan = FitnessCalculator.nutritionPlanFromProfile(base)
        val updated = base.copy(
            dailyCalories = plan.dailyCalories,
            protein = plan.macros.protein,
            carbs = plan.macros.carbs,
            fat = plan.macros.fat,
            fiber = plan.macros.fiber,
            maintenanceCalories = plan.maintenanceCalories,
            calorieAdjustmentDaily = plan.calorieAdjustmentDaily,
            calorieAdjustmentWeekly = plan.calorieAdjustmentWeekly
        )
        saveProfile(updated)
        viewModelScope.launch { repository.updateTodayGoalSnapshot(updated) }
    }

    fun dailyCaloriesForDay(
        dayStart: Long,
        snapshots: List<com.example.data.DailyGoalSnapshot> = allGoalSnapshots.value
    ): Int = snapshots.find { it.dayStart == dayStart }?.dailyCalories
        ?: userProfile.value.dailyCalories.coerceAtLeast(1)

    fun profileForDay(
        dayStart: Long,
        snapshots: List<com.example.data.DailyGoalSnapshot> = allGoalSnapshots.value
    ): UserProfile {
        val base = userProfile.value
        val snap = snapshots.find { it.dayStart == dayStart } ?: return base
        return base.copy(
            dailyCalories = snap.dailyCalories,
            protein = snap.protein,
            carbs = snap.carbs,
            fat = snap.fat,
            fiber = snap.fiber
        )
    }

    private suspend fun backfillMissingGoalSnapshots(profile: UserProfile) {
        val today = DietDateUtils.startOfTodayMillis()
        val existing = allGoalSnapshots.value.map { it.dayStart }.toSet()
        allMeals.value
            .map { DietDateUtils.startOfDayMillis(java.util.Calendar.getInstance().apply { timeInMillis = it.timestamp }) }
            .distinct()
            .filter { it < today && it !in existing }
            .forEach { day -> repository.ensureGoalSnapshot(day, profile) }
    }

    fun deleteNotification(id: Int) {
        viewModelScope.launch { repository.deleteNotification(id) }
    }

    fun recordNotification(title: String, body: String, category: String = "general") {
        viewModelScope.launch { repository.addNotification(title, body, category) }
    }

    suspend fun onBackupRestored() {
        repository.normalizeProfileAfterRestore()
    }

    fun addMeal(meal: MealEntry) {
        viewModelScope.launch { repository.addMeal(meal) }
    }

    fun updateMeal(meal: MealEntry) {
        viewModelScope.launch { repository.updateMeal(meal) }
    }

    fun deleteMeal(meal: MealEntry) {
        viewModelScope.launch { repository.deleteMeal(meal) }
    }

    fun addFoodToMeal(
        mealType: String,
        food: FoodItem,
        weightGrams: Int,
        logDayStart: Long = DietDateUtils.startOfTodayMillis()
    ) {
        if (DietDateUtils.isFuture(logDayStart)) return
        viewModelScope.launch {
            repository.ensureGoalSnapshot(logDayStart, userProfile.value)
        }
        val nutrition = FoodNutritionCalculator.nutritionForWeight(food, weightGrams)
        addMeal(
            MealEntry(
                mealType = mealType,
                foodName = food.name,
                weightGrams = weightGrams,
                calories = nutrition.calories,
                protein = nutrition.protein,
                carbs = nutrition.carbs,
                fat = nutrition.fat,
                fiber = nutrition.fiber,
                timestamp = DietDateUtils.timestampForDay(logDayStart)
            )
        )
    }

    fun addCustomFoodAndReturn(food: CustomFoodItem, onSaved: (FoodItem) -> Unit) {
        viewModelScope.launch {
            val id = repository.addCustomFood(food)
            onSaved(FoodNutritionCalculator.fromCustomEntity(food.copy(id = id.toInt())))
        }
    }

    fun deleteCustomFood(food: CustomFoodItem) {
        viewModelScope.launch { repository.deleteCustomFood(food) }
    }

    /** Load initial suggestions when the food list page opens. */
    fun loadFoodSuggestions() {
        val prefs = userProfile.value.cuisinePreferences
        viewModelScope.launch {
            _foodSearchState.update { it.copy(isLoading = true, error = null) }
            delay(50)
            val list = localFoodRepository.suggestions(prefs)
            _foodSearchState.update {
                it.copy(
                    isLoading = false,
                    suggestions = list,
                    localResults = list,
                    apiResults = emptyList()
                )
            }
        }
    }

    /**
     * Called on every keystroke. Performs:
     *  - Instant local search (no debounce)
     *  - Debounced API search (500ms) only if local results < 5
     */
    fun onFoodSearchQueryChanged(query: String) {
        _foodSearchState.update { it.copy(query = query) }

        // Cancel any pending jobs
        localSearchJob?.cancel()
        apiSearchJob?.cancel()

        if (query.isBlank()) {
            // Reset to suggestions
            _foodSearchState.update {
                it.copy(
                    localResults = it.suggestions,
                    apiResults = emptyList(),
                    isLoading = false,
                    isApiLoading = false,
                    error = null
                )
            }
            return
        }

        // Instant local search (very fast, in-memory)
        localSearchJob = viewModelScope.launch {
            delay(100) // Tiny debounce to batch rapid keystrokes
            val prefs = userProfile.value.cuisinePreferences
            val localResults = localFoodRepository.search(query, prefs)
            _foodSearchState.update {
                it.copy(localResults = localResults, isLoading = false, error = null)
            }

            // Only call API if local results are sparse and query is meaningful
            if (localResults.size < 5 && query.trim().length >= 3) {
                _foodSearchState.update { it.copy(isApiLoading = true) }
                apiSearchJob = launch {
                    delay(400) // Extra debounce for API calls
                    val apiResults = offRepository.search(query.trim())
                    _foodSearchState.update {
                        it.copy(apiResults = apiResults, isApiLoading = false)
                    }
                }
            } else {
                _foodSearchState.update { it.copy(apiResults = emptyList(), isApiLoading = false) }
            }
        }
    }

    fun clearFoodSearch() {
        localSearchJob?.cancel()
        apiSearchJob?.cancel()
        _foodSearchState.value = FoodSearchUiState()
    }

    fun toggleSetCompleted(set: ExerciseSet) {
        viewModelScope.launch {
            repository.updateSet(set.copy(isCompleted = !set.isCompleted))
        }
    }

    fun updateSet(set: ExerciseSet) {
        viewModelScope.launch { repository.updateSet(set) }
    }

    fun workoutPlanForDay(dayStart: Long): WorkoutSplitGenerator.WorkoutDayPlan {
        val p = userProfile.value
        val days = p.workoutDaysPerWeek.coerceAtLeast(1)
        val idx = WorkoutSplitGenerator.dayIndexForDate(dayStart, days)
        return WorkoutSplitGenerator.planForDayIndex(idx, days, p.goal.ifBlank { "General Fitness" })
    }

    fun setsForDay(sets: List<ExerciseSet>, dayStart: Long, dayLabel: String): List<ExerciseSet> {
        val end = DietDateUtils.endOfDayMillis(dayStart)
        return sets.filter {
            it.timestamp >= dayStart && it.timestamp < end && it.workoutDayLabel == dayLabel
        }
    }

    private fun lastWeightForExercise(exerciseName: String): Float =
        ExerciseWeightUtils.lastLoggedWeight(allSets.value, exerciseName)

    private fun lastWeightForSet(exerciseName: String, routineLabel: String, setNumber: Int): Float =
        ExerciseWeightUtils.lastWeightForSet(allSets.value, exerciseName, routineLabel, setNumber)

    private fun resolveSetWeightKg(
        exerciseName: String,
        exerciseType: String,
        routineLabel: String,
        setNumber: Int,
        templateWeight: Float = 0f,
        duplicateFromWeight: Float = 0f
    ): Float = ExerciseWeightDefaults.resolveWeightKg(
        exerciseName = exerciseName,
        exerciseType = exerciseType,
        profile = userProfile.value,
        setNumber = setNumber,
        lastSetWeight = duplicateFromWeight,
        lastRoutineWeight = lastWeightForSet(exerciseName, routineLabel, setNumber),
        lastGlobalWeight = lastWeightForExercise(exerciseName),
        templateWeight = templateWeight
    )

    fun maxWeightForExercise(exerciseName: String): Float =
        ExerciseWeightUtils.maxWeight(allSets.value, exerciseName)

    fun workoutLogsForDay(
        dayStart: Long,
        sets: List<ExerciseSet>,
        routines: List<WorkoutRoutine>
    ): List<DayWorkoutLog> {
        val end = DietDateUtils.endOfDayMillis(dayStart)
        return sets
            .filter { it.timestamp >= dayStart && it.timestamp < end && it.workoutDayLabel.isNotBlank() }
            .groupBy { it.workoutDayLabel }
            .map { (label, labelSets) ->
                val routine = routines.find { it.name.equals(label, ignoreCase = true) }
                DayWorkoutLog(
                    dayStart = dayStart,
                    routineName = label,
                    routineId = routine?.id,
                    sets = labelSets,
                    exerciseGroups = WorkoutGrouping.groupSets(labelSets),
                    completedSets = labelSets.count { it.isCompleted },
                    totalSets = labelSets.size,
                    isFullyCompleted = labelSets.isNotEmpty() && labelSets.all { it.isCompleted }
                )
            }
            .sortedByDescending { log -> log.sets.maxOfOrNull { it.timestamp } ?: 0L }
    }

    fun resumeDayWorkout(dayLabel: String, dayStart: Long, routineId: Int? = null) {
        if (DietDateUtils.isFuture(dayStart)) return
        _activeSession.value = ActiveWorkoutSession(
            routineId = routineId,
            routineName = dayLabel,
            dayLabel = dayLabel,
            dayStart = dayStart
        )
    }

    fun deleteDayWorkoutLog(log: DayWorkoutLog) {
        viewModelScope.launch {
            val end = DietDateUtils.endOfDayMillis(log.dayStart)
            repository.deleteWorkoutForDay(log.routineName, log.dayStart, end)
            val session = _activeSession.value
            if (session != null &&
                session.dayStart == log.dayStart &&
                session.dayLabel == log.routineName
            ) {
                _activeSession.value = null
                _pendingFinishSummary.value = null
            }
        }
    }

    fun addExerciseToDay(
        exercise: ExerciseItem,
        dayLabel: String,
        logDayStart: Long,
        weightKg: Float? = null,
        setCount: Int? = null,
        reps: Int? = null
    ) {
        if (DietDateUtils.isFuture(logDayStart)) return
        viewModelScope.launch {
            val isCardio = WorkoutExerciseKind.isCardioType(exercise.exerciseType, exercise.isCardio)
            if (isCardio) {
                repository.addSet(
                    ExerciseSet(
                        exerciseName = exercise.name,
                        exerciseType = "Cardio",
                        workoutDayLabel = dayLabel,
                        setNumber = 1,
                        weight = 0f,
                        reps = 0,
                        durationSeconds = 0,
                        caloriesBurned = 0,
                        timestamp = DietDateUtils.timestampForDay(logDayStart)
                    )
                )
                return@launch
            }
            val count = setCount ?: exercise.defaultSets
            val repCount = reps ?: exercise.defaultReps
            val weight = weightKg ?: resolveSetWeightKg(
                exercise.name, exercise.exerciseType, dayLabel, 1
            )
            val timestamp = DietDateUtils.timestampForDay(logDayStart)
            repeat(count) { index ->
                repository.addSet(
                    ExerciseSet(
                        exerciseName = exercise.name,
                        exerciseType = exercise.exerciseType,
                        workoutDayLabel = dayLabel,
                        setNumber = index + 1,
                        weight = if (index == 0) weight else resolveSetWeightKg(
                            exercise.name, exercise.exerciseType, dayLabel, index + 1,
                            duplicateFromWeight = weight
                        ),
                        reps = repCount,
                        timestamp = timestamp
                    )
                )
            }
        }
    }

    fun removeExerciseFromDay(group: WorkoutExerciseGroup, dayStart: Long) {
        viewModelScope.launch {
            repository.deleteExerciseForDay(
                group.exerciseName,
                group.workoutDayLabel,
                dayStart,
                DietDateUtils.endOfDayMillis(dayStart)
            )
        }
    }

    fun switchExerciseTrackingMode(group: WorkoutExerciseGroup, isCardio: Boolean, dayStart: Long) {
        viewModelScope.launch {
            val end = DietDateUtils.endOfDayMillis(dayStart)
            repository.deleteExerciseForDay(
                group.exerciseName,
                group.workoutDayLabel,
                dayStart,
                end
            )
            val timestamp = DietDateUtils.timestampForDay(dayStart)
            if (isCardio) {
                repository.addSet(
                    ExerciseSet(
                        exerciseName = group.exerciseName,
                        exerciseType = "Cardio",
                        workoutDayLabel = group.workoutDayLabel,
                        setNumber = 1,
                        weight = 0f,
                        reps = 0,
                        durationSeconds = group.sets.firstOrNull()?.durationSeconds ?: 0,
                        caloriesBurned = group.sets.firstOrNull()?.caloriesBurned ?: 0,
                        timestamp = timestamp
                    )
                )
            } else {
                val reps = 10
                val weight = resolveSetWeightKg(
                    group.exerciseName,
                    group.sets.firstOrNull()?.exerciseType ?: "Full Body",
                    group.workoutDayLabel,
                    1
                )
                repeat(3) { index ->
                    repository.addSet(
                        ExerciseSet(
                            exerciseName = group.exerciseName,
                            exerciseType = group.sets.firstOrNull()?.exerciseType?.takeIf {
                                !WorkoutExerciseKind.isCardioType(it)
                            } ?: "Full Body",
                            workoutDayLabel = group.workoutDayLabel,
                            setNumber = index + 1,
                            weight = if (index == 0) weight else resolveSetWeightKg(
                                group.exerciseName,
                                "Full Body",
                                group.workoutDayLabel,
                                index + 1,
                                duplicateFromWeight = weight
                            ),
                            reps = reps,
                            timestamp = timestamp
                        )
                    )
                }
            }
        }
    }

    fun updateExerciseSetCount(
        group: WorkoutExerciseGroup,
        newSetCount: Int,
        dayStart: Long
    ) {
        viewModelScope.launch {
            val end = DietDateUtils.endOfDayMillis(dayStart)
            val current = group.sets.sortedBy { it.setNumber }
            when {
                newSetCount <= 0 -> repository.deleteExerciseForDay(
                    group.exerciseName, group.workoutDayLabel, dayStart, end
                )
                newSetCount < current.size -> {
                    current.drop(newSetCount).forEach { repository.deleteSet(it) }
                }
                newSetCount > current.size -> {
                    val last = current.lastOrNull()
                    val reps = last?.reps ?: 10
                    val ts = last?.timestamp ?: DietDateUtils.timestampForDay(dayStart)
                    for (n in (current.size + 1)..newSetCount) {
                        val prevWeight = last?.weight?.takeIf { it > 0f } ?: 0f
                        repository.addSet(
                            ExerciseSet(
                                exerciseName = group.exerciseName,
                                exerciseType = group.exerciseType,
                                workoutDayLabel = group.workoutDayLabel,
                                setNumber = n,
                                weight = resolveSetWeightKg(
                                    group.exerciseName,
                                    group.exerciseType,
                                    group.workoutDayLabel,
                                    n,
                                    duplicateFromWeight = prevWeight
                                ),
                                reps = reps,
                                isCompleted = false,
                                timestamp = ts
                            )
                        )
                    }
                }
            }
        }
    }

    fun progressiveOverloadTip(exerciseName: String, currentWeight: Float, currentReps: Int): String? {
        val history = allSets.value.filter {
            it.exerciseName.equals(exerciseName, ignoreCase = true) && it.isCompleted
        }
        if (history.isEmpty()) return null
        val lastWeek = System.currentTimeMillis() - 7 * DietDateUtils.DAY_MS
        val recent = history.filter { it.timestamp >= lastWeek }
            .maxByOrNull { it.weight * it.reps }
        val previous = history.filter { it.timestamp < lastWeek }
            .maxByOrNull { it.weight * it.reps }
        val ref = previous ?: recent ?: return null
        if (currentWeight <= 0f) return null
        val suggested = ref.weight + 2.5f
        return "Last session: ${ref.weight}kg × ${ref.reps}. Try ${suggested}kg × $currentReps."
    }

    fun loadExerciseSuggestions(routine: String) {
        viewModelScope.launch {
            val list = localExerciseRepository.suggestions(routine)
            _exerciseSearchState.update {
                it.copy(suggestions = list, localResults = list, isLoading = false, aiSuggestions = emptyList())
            }
        }
    }

    fun generateAiExercisesForRoutine(routine: String) {
        viewModelScope.launch {
            if (!isAiConfigured()) {
                _exerciseSearchState.update {
                    it.copy(aiError = "Configure AI in Settings to generate exercises.")
                }
                return@launch
            }
            _exerciseSearchState.update { it.copy(aiLoading = true, aiError = null) }
            try {
                val custom = customExercises.value.map { it.name }
                val predefined = localExerciseRepository.all().map { it.name }
                val exclude = (custom + predefined).distinct()
                val slot = AiRouteResolver.textSlot(userProfile.value)
                val generated = aiManager.generateRoutineExerciseBatch(
                    routine = routine,
                    excludeNames = exclude,
                    provider = slot.provider,
                    modelId = slot.modelId,
                    offlineModelId = slot.offlineModelId
                )
                _exerciseSearchState.update {
                    it.copy(aiSuggestions = generated, aiLoading = false)
                }
            } catch (e: Exception) {
                _exerciseSearchState.update {
                    it.copy(aiLoading = false, aiError = e.message ?: "Failed to generate exercises.")
                }
            }
        }
    }

    fun clearAiExerciseSuggestions() {
        _exerciseSearchState.update { it.copy(aiSuggestions = emptyList(), aiError = null) }
    }

    fun onExerciseSearchQueryChanged(query: String, routine: String?) {
        _exerciseSearchState.update { it.copy(query = query) }
        exerciseSearchJob?.cancel()
        exerciseSearchJob = viewModelScope.launch {
            delay(100)
            val results = localExerciseRepository.search(query, routine)
            _exerciseSearchState.update {
                it.copy(localResults = results, isLoading = false)
            }
        }
    }

    fun clearExerciseSearch() {
        exerciseSearchJob?.cancel()
        _exerciseSearchState.value = ExerciseSearchUiState()
    }

    suspend fun generateExerciseSuggestions(query: String, routine: String): List<ExerciseItem> {
        val slot = AiRouteResolver.textSlot(userProfile.value)
        return aiManager.generateExerciseSuggestions(query, routine, slot.provider, slot.modelId, slot.offlineModelId)
    }

    fun addCustomExerciseAndReturn(
        exercise: CustomExercise,
        dayLabel: String,
        logDayStart: Long,
        addToWorkout: Boolean = true,
        onSaved: (ExerciseItem) -> Unit
    ) {
        viewModelScope.launch {
            val id = repository.addCustomExercise(exercise)
            val item = ExerciseItem(
                id = "custom_$id",
                name = exercise.name,
                exerciseType = if (exercise.isCardio) "Cardio" else exercise.exerciseType,
                routines = listOf(routineFromDayLabel(dayLabel)),
                defaultSets = if (exercise.isCardio) 1 else exercise.defaultSets,
                defaultReps = if (exercise.isCardio) 0 else exercise.defaultReps,
                isCustom = true,
                isCardio = exercise.isCardio
            )
            if (addToWorkout) {
                addExerciseToDay(item, dayLabel, logDayStart, exercise.defaultWeight, item.defaultSets, item.defaultReps)
            }
            onSaved(item)
        }
    }

    fun estimateCardioCalories(set: ExerciseSet) {
        viewModelScope.launch {
            val profile = userProfile.value
            if (set.durationSeconds <= 0) return@launch
            val calories = if (isAiConfigured()) {
                val slot = AiRouteResolver.textSlot(profile)
                aiManager.estimateCardioCalories(
                    exerciseName = set.exerciseName,
                    durationSeconds = set.durationSeconds,
                    age = profile.age,
                    weightKg = profile.currentWeight,
                    heightCm = profile.height,
                    gender = profile.gender,
                    provider = slot.provider,
                    modelId = slot.modelId,
                    offlineModelId = slot.offlineModelId
                ) ?: WorkoutCalorieEstimator.estimateCardioCalories(
                    set.durationSeconds, profile.currentWeight, set.exerciseName
                )
            } else {
                WorkoutCalorieEstimator.estimateCardioCalories(
                    set.durationSeconds, profile.currentWeight, set.exerciseName
                )
            }
            repository.updateSet(set.copy(caloriesBurned = calories))
        }
    }

    fun deleteCustomExercise(exercise: CustomExercise) {
        viewModelScope.launch { repository.deleteCustomExercise(exercise) }
    }

    private fun routineFromDayLabel(dayLabel: String): String {
        return when {
            dayLabel.contains("Push", ignoreCase = true) -> "Push"
            dayLabel.contains("Pull", ignoreCase = true) -> "Pull"
            dayLabel.contains("Leg", ignoreCase = true) -> "Legs"
            dayLabel.contains("Upper", ignoreCase = true) -> "Upper"
            dayLabel.contains("Lower", ignoreCase = true) -> "Lower"
            else -> "Full Body"
        }
    }

    fun deleteRoutineAndReturn(routine: WorkoutRoutine, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteRoutine(routine)
            onDeleted()
        }
    }

    fun editExerciseGroup(
        group: WorkoutExerciseGroup,
        newSetCount: Int,
        weight: Float,
        reps: Int,
        dayStart: Long
    ) {
        viewModelScope.launch {
            val resolvedWeight = weight.takeIf { it > 0f } ?: resolveSetWeightKg(
                group.exerciseName, group.exerciseType, group.workoutDayLabel, 1
            )
            group.sets.forEach { set ->
                repository.updateSet(set.copy(weight = resolvedWeight, reps = reps))
            }
            val end = DietDateUtils.endOfDayMillis(dayStart)
            val current = group.sets.sortedBy { it.setNumber }
            when {
                newSetCount <= 0 -> repository.deleteExerciseForDay(
                    group.exerciseName, group.workoutDayLabel, dayStart, end
                )
                newSetCount < current.size -> {
                    current.drop(newSetCount).forEach { repository.deleteSet(it) }
                }
                newSetCount > current.size -> {
                    val ts = current.lastOrNull()?.timestamp ?: DietDateUtils.timestampForDay(dayStart)
                    val lastWeight = current.lastOrNull()?.weight?.takeIf { it > 0f } ?: resolvedWeight
                    for (n in (current.size + 1)..newSetCount) {
                        repository.addSet(
                            ExerciseSet(
                                exerciseName = group.exerciseName,
                                exerciseType = group.exerciseType,
                                workoutDayLabel = group.workoutDayLabel,
                                setNumber = n,
                                weight = resolveSetWeightKg(
                                    group.exerciseName,
                                    group.exerciseType,
                                    group.workoutDayLabel,
                                    n,
                                    duplicateFromWeight = lastWeight
                                ),
                                reps = reps,
                                isCompleted = false,
                                timestamp = ts
                            )
                        )
                    }
                }
            }
        }
    }

    fun seedDefaultExercisesIfEmpty(dayLabel: String, routine: String, logDayStart: Long) {
        if (DietDateUtils.isFuture(logDayStart)) return
        viewModelScope.launch {
            val seedKey = "$logDayStart|$dayLabel"
            if (seedKey in seededWorkoutDays) return@launch
            val end = DietDateUtils.endOfDayMillis(logDayStart)
            val existing = allSets.value.filter {
                it.timestamp >= logDayStart && it.timestamp < end &&
                    it.workoutDayLabel == dayLabel
            }
            seededWorkoutDays.add(seedKey)
            if (existing.isNotEmpty()) return@launch
            val suggestions = localExerciseRepository.suggestions(routine, limit = 4)
            val timestamp = DietDateUtils.timestampForDay(logDayStart)
            suggestions.forEach { exercise ->
                repeat(exercise.defaultSets) { index ->
                    val setNum = index + 1
                    repository.addSet(
                        ExerciseSet(
                            exerciseName = exercise.name,
                            exerciseType = exercise.exerciseType,
                            workoutDayLabel = dayLabel,
                            setNumber = setNum,
                            weight = resolveSetWeightKg(
                                exercise.name, exercise.exerciseType, dayLabel, setNum
                            ),
                            reps = exercise.defaultReps,
                            timestamp = timestamp
                        )
                    )
                }
            }
        }
    }

    fun sessionNumberForDay(allSets: List<ExerciseSet>, dayStart: Long): Int {
        val daysWithData = allSets.map { set ->
            DietDateUtils.startOfDayMillis(
                java.util.Calendar.getInstance().apply { timeInMillis = set.timestamp }
            )
        }.distinct().sorted()
        val idx = daysWithData.indexOf(dayStart)
        return if (idx >= 0) idx + 1 else daysWithData.size + 1
    }

    fun completeWorkout(sets: List<ExerciseSet>) {
        viewModelScope.launch {
            sets.forEach { set ->
                if (!set.isCompleted) repository.updateSet(set.copy(isCompleted = true))
            }
        }
    }

    fun todayCalorieSummary(): Triple<Int, Int, Int> {
        val profile = userProfile.value
        val todayStart = DietDateUtils.startOfTodayMillis()
        val todayEnd = DietDateUtils.endOfDayMillis(todayStart)
        val goal = profile.dailyCalories.coerceAtLeast(1)
        val consumed = allMeals.value
            .filter { it.timestamp in todayStart until todayEnd }
            .sumOf { it.calories }
        val burned = WorkoutCalorieEstimator.estimateBurn(
            allSets.value.filter { it.timestamp in todayStart until todayEnd && it.isCompleted },
            profile
        )
        val remaining = (goal - consumed + burned).coerceAtLeast(0)
        return Triple(remaining, consumed, burned)
    }

    fun todayMacroTotals(): Triple<Int, Int, Int> {
        val todayStart = DietDateUtils.startOfTodayMillis()
        val todayEnd = DietDateUtils.endOfDayMillis(todayStart)
        val meals = allMeals.value.filter { it.timestamp in todayStart until todayEnd }
        return Triple(meals.sumOf { it.protein }, meals.sumOf { it.carbs }, meals.sumOf { it.fat })
    }

    fun todayFiberTotal(): Int {
        val todayStart = DietDateUtils.startOfTodayMillis()
        val todayEnd = DietDateUtils.endOfDayMillis(todayStart)
        return allMeals.value.filter { it.timestamp in todayStart until todayEnd }.sumOf { it.fiber }
    }

    fun deleteSet(set: ExerciseSet) {
        viewModelScope.launch { repository.deleteSet(set) }
    }

    fun routineExercisesFlow(routineId: Int) = repository.routineExercises(routineId)

    suspend fun loadRoutineExercisesOnce(routineId: Int): List<RoutineExercise> =
        repository.getRoutineExercisesOnce(routineId)

    fun createRoutine(name: String, onCreated: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val id = repository.insertRoutine(
                WorkoutRoutine(name = name.trim(), isBuiltIn = false, sortOrder = allRoutines.value.size)
            ).toInt()
            onCreated(id)
        }
    }

    fun renameRoutine(routine: WorkoutRoutine, newName: String) {
        viewModelScope.launch {
            repository.updateRoutine(routine.copy(name = newName.trim()))
        }
    }

    fun deleteRoutine(routine: WorkoutRoutine) {
        viewModelScope.launch { repository.deleteRoutine(routine) }
    }

    fun saveRoutineExercises(routineId: Int, exercises: List<RoutineExercise>) {
        viewModelScope.launch { repository.replaceRoutineExercises(routineId, exercises) }
    }

    fun moveRoutineExercise(exercises: List<RoutineExercise>, fromIndex: Int, toIndex: Int): List<RoutineExercise> {
        if (fromIndex == toIndex || fromIndex !in exercises.indices || toIndex !in exercises.indices) return exercises
        val mutable = exercises.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        return mutable.mapIndexed { index, ex -> ex.copy(sortOrder = index) }
    }

    fun startRoutineWorkout(routine: WorkoutRoutine, dayStart: Long = DietDateUtils.startOfTodayMillis()) {
        if (DietDateUtils.isFuture(dayStart)) return
        viewModelScope.launch {
            val plan = workoutPlanForDay(dayStart)
            val dayLabel = routine.name
            val existing = allSets.value.filter {
                it.timestamp >= dayStart && it.timestamp < DietDateUtils.endOfDayMillis(dayStart) &&
                    it.workoutDayLabel == dayLabel
            }
            if (existing.isEmpty()) {
                val exercises = repository.getRoutineExercisesOnce(routine.id)
                val timestamp = DietDateUtils.timestampForDay(dayStart)
                exercises.forEach { ex ->
                    if (ex.isCardio || WorkoutExerciseKind.isCardioType(ex.exerciseType)) {
                        repository.addSet(
                            ExerciseSet(
                                exerciseName = ex.exerciseName,
                                exerciseType = "Cardio",
                                workoutDayLabel = dayLabel,
                                setNumber = 1,
                                weight = 0f,
                                reps = 0,
                                durationSeconds = 0,
                                caloriesBurned = 0,
                                timestamp = timestamp
                            )
                        )
                    } else {
                        repeat(ex.defaultSets) { index ->
                            val setNum = index + 1
                            repository.addSet(
                                ExerciseSet(
                                    exerciseName = ex.exerciseName,
                                    exerciseType = ex.exerciseType,
                                    workoutDayLabel = dayLabel,
                                    setNumber = setNum,
                                    weight = resolveSetWeightKg(
                                        ex.exerciseName,
                                        ex.exerciseType,
                                        dayLabel,
                                        setNum,
                                        templateWeight = ex.defaultWeight
                                    ),
                                    reps = ex.defaultReps,
                                    timestamp = timestamp
                                )
                            )
                        }
                    }
                }
            }
            _activeSession.value = ActiveWorkoutSession(
                routineId = routine.id,
                routineName = routine.name,
                dayLabel = dayLabel,
                dayStart = dayStart
            )
        }
    }

    fun startEmptyWorkout(dayStart: Long = DietDateUtils.startOfTodayMillis()) {
        if (DietDateUtils.isFuture(dayStart)) return
        _activeSession.value = ActiveWorkoutSession(
            routineId = null,
            routineName = "Empty Workout",
            dayLabel = "Empty Workout",
            dayStart = dayStart
        )
    }

    fun setWorkoutHandlesBack(handles: Boolean) {
        _workoutHandlesBack.value = handles
    }

    fun dismissSuggestedRoutine(dayStart: Long) {
        suggestionDismissedForDay = dayStart
        _suggestedRoutine.value = null
    }

    fun refreshRoutineSuggestion(dayStart: Long) {
        if (DietDateUtils.isFuture(dayStart)) {
            _suggestedRoutine.value = null
            return
        }
        if (suggestionDismissedForDay == dayStart) return
        viewModelScope.launch {
            val routines = allRoutines.value
            if (routines.isEmpty()) return@launch

            val all = allSets.value
            val yesterdayStart = dayStart - DietDateUtils.DAY_MS
            val yesterdayEnd = DietDateUtils.endOfDayMillis(yesterdayStart)
            val yesterdaySets = all.filter { it.timestamp >= yesterdayStart && it.timestamp < yesterdayEnd }
            val yesterdayLabels = yesterdaySets.map { it.workoutDayLabel }.distinct().filter { it.isNotBlank() }
            val yesterdaySummary = if (yesterdayLabels.isEmpty()) {
                "No workout logged yesterday."
            } else {
                "Yesterday: ${yesterdayLabels.joinToString(", ")} (${yesterdaySets.count { it.isCompleted }} sets completed)."
            }

            val profile = userProfile.value
            val textSlot = AiRouteResolver.textSlot(profile)
            val aiConfigured = isAiConfigured()
            val aiSuggestion = if (aiConfigured) {
                aiManager.suggestRoutineName(
                    routineNames = routines.map { it.name },
                    yesterdaySummary = yesterdaySummary,
                    goal = profile.goal,
                    workoutDaysPerWeek = profile.workoutDaysPerWeek,
                    provider = textSlot.provider,
                    modelId = textSlot.modelId,
                    offlineModelId = textSlot.offlineModelId
                )
            } else null

            val picked = aiSuggestion?.let { name -> routines.find { it.name.equals(name, ignoreCase = true) } }
                ?: suggestRoutineHeuristic(routines, all, dayStart)

            if (picked != null) {
                val exerciseCount = repository.getRoutineExercisesOnce(picked.id).size
                val reason = if (aiSuggestion != null) {
                    "Today's scheduled routine based on your recent training."
                } else {
                    heuristicReason(all, dayStart, picked.name)
                }
                _suggestedRoutine.value = SuggestedRoutine(
                    routine = picked,
                    reason = reason,
                    exerciseCount = exerciseCount,
                    isAiSuggested = aiSuggestion != null
                )
            }
        }
    }

    private fun suggestRoutineHeuristic(
        routines: List<WorkoutRoutine>,
        allSets: List<ExerciseSet>,
        dayStart: Long
    ): WorkoutRoutine? {
        if (routines.isEmpty()) return null
        val completed = allSets.filter { it.isCompleted && it.timestamp < dayStart }
        if (completed.isEmpty()) return routines.firstOrNull()

        val lastTimestamp = completed.maxOf { it.timestamp }
        val lastDayStart = DietDateUtils.startOfDayMillis(
            java.util.Calendar.getInstance().apply { timeInMillis = lastTimestamp }
        )
        val lastLabels = completed
            .filter {
                val d = DietDateUtils.startOfDayMillis(
                    java.util.Calendar.getInstance().apply { timeInMillis = it.timestamp }
                )
                d == lastDayStart
            }
            .map { it.workoutDayLabel }
            .distinct()
            .filter { it.isNotBlank() }

        val last = lastLabels.lastOrNull()?.lowercase().orEmpty()
        val nextName = when {
            last.contains("push") -> "Pull"
            last.contains("pull") -> "Legs"
            last.contains("leg") -> "Push"
            last.contains("upper") -> "Lower Body"
            last.contains("lower") -> "Upper Body"
            last.contains("core") || last.contains("abs") -> "Full Body"
            last.contains("cardio") -> "Push"
            else -> null
        }
        return nextName?.let { name -> routines.find { it.name.equals(name, ignoreCase = true) } }
            ?: routines.firstOrNull()
    }

    private fun heuristicReason(allSets: List<ExerciseSet>, dayStart: Long, suggestedName: String): String {
        val completed = allSets.filter { it.isCompleted && it.timestamp < dayStart }
        if (completed.isEmpty()) return "Suggested $suggestedName to get started today."
        val lastTimestamp = completed.maxOf { it.timestamp }
        val lastDayStart = DietDateUtils.startOfDayMillis(
            java.util.Calendar.getInstance().apply { timeInMillis = lastTimestamp }
        )
        val lastLabels = completed.filter {
            DietDateUtils.startOfDayMillis(
                java.util.Calendar.getInstance().apply { timeInMillis = it.timestamp }
            ) == lastDayStart
        }.map { it.workoutDayLabel }.distinct().filter { it.isNotBlank() }
        return if (lastLabels.isEmpty()) {
            "Suggested $suggestedName based on your training plan."
        } else {
            "After ${lastLabels.last()}, $suggestedName is a smart follow-up."
        }
    }

    fun workoutDashboardStats(): WorkoutDashboardStats =
        WorkoutStatsCalculator.compute(allSets.value, userProfile.value)

    fun saveWorkoutReminder(enabled: Boolean, timeMinute: Int, repeat: Boolean) {
        viewModelScope.launch {
            saveProfile(
                userProfile.value.copy(
                    workoutReminderEnabled = enabled,
                    workoutReminderTimeMinute = timeMinute.coerceIn(0, 23 * 60 + 59),
                    workoutReminderRepeat = repeat
                )
            )
        }
    }

    fun finishWorkoutRequest(sets: List<ExerciseSet>) {
        val session = _activeSession.value ?: return
        val duration = System.currentTimeMillis() - session.startedAtMs
        val completed = sets.count { it.isCompleted }
        val volume = sets.filter { it.isCompleted }.sumOf { (it.weight * it.reps).toDouble() }.toFloat()
        val messages = listOf(
            "Great work — consistency builds strength!",
            "Another session in the books. Keep pushing!",
            "You showed up and put in the work. That's what counts.",
            "Strong effort today. Recovery is part of the process."
        )
        _pendingFinishSummary.value = WorkoutFinishSummary(
            durationMs = duration,
            totalVolumeKg = volume,
            setsCompleted = completed,
            totalSets = sets.size,
            motivationalMessage = messages.random()
        )
    }

    fun confirmFinishWorkout() {
        _pendingFinishSummary.value = null
        _activeSession.value = null
    }

    fun discardWorkout(sets: List<ExerciseSet>) {
        viewModelScope.launch {
            val session = _activeSession.value ?: return@launch
            val end = DietDateUtils.endOfDayMillis(session.dayStart)
            sets.filter {
                it.workoutDayLabel == session.dayLabel &&
                    it.timestamp >= session.dayStart && it.timestamp < end
            }.forEach { repository.deleteSet(it) }
            _pendingFinishSummary.value = null
            _activeSession.value = null
        }
    }

    fun dismissFinishSummary() {
        _pendingFinishSummary.value = null
    }

    fun endActiveSession() {
        _activeSession.value = null
    }

    fun effectiveWaterGoalMl(): Int = WaterGoalCalculator.effectiveGoalMl(userProfile.value)

    fun effectiveWaterGoalGlasses(): Int = WaterGoalCalculator.effectiveGoalGlasses(userProfile.value)

    fun suggestedWaterGoalGlasses(): Int = WaterGoalCalculator.suggestedGoalGlasses(userProfile.value)

    fun waterGlassesForDay(logs: List<com.example.data.WaterLog>, dayStart: Long): Int {
        val end = DietDateUtils.endOfDayMillis(dayStart)
        val totalMl = logs.filter { it.timestamp in dayStart until end }.sumOf { it.amountMl }
        return WaterGoalCalculator.glassesFromMl(totalMl)
    }

    fun todayWaterGlasses(logs: List<com.example.data.WaterLog>): Int =
        waterGlassesForDay(logs, DietDateUtils.startOfTodayMillis())

    fun todayWaterTotalMl(logs: List<com.example.data.WaterLog>, dayStart: Long = DietDateUtils.startOfTodayMillis()): Int {
        val end = DietDateUtils.endOfDayMillis(dayStart)
        return logs.filter { it.timestamp in dayStart until end }.sumOf { it.amountMl }
    }

    fun waterProgress(logs: List<com.example.data.WaterLog>, dayStart: Long = DietDateUtils.startOfTodayMillis()): Float {
        val goal = effectiveWaterGoalMl().coerceAtLeast(1)
        return todayWaterTotalMl(logs, dayStart).toFloat() / goal
    }

    fun setWaterGlassesForDay(dayStart: Long, glasses: Int) {
        if (DietDateUtils.isFuture(dayStart)) return
        viewModelScope.launch {
            repository.setWaterGlassesForDay(dayStart, glasses.coerceAtLeast(0))
        }
    }

    fun incrementWaterGlass(dayStart: Long = DietDateUtils.startOfTodayMillis()) {
        val current = waterGlassesForDay(allWaterLogs.value, dayStart)
        setWaterGlassesForDay(dayStart, current + 1)
    }

    fun decrementWaterGlass(dayStart: Long = DietDateUtils.startOfTodayMillis()) {
        val current = waterGlassesForDay(allWaterLogs.value, dayStart)
        if (current > 0) setWaterGlassesForDay(dayStart, current - 1)
    }

    fun addWater(amountMl: Int = WaterGoalCalculator.DEFAULT_QUICK_ADD_ML) {
        val dayStart = DietDateUtils.startOfTodayMillis()
        val glasses = waterGlassesForDay(allWaterLogs.value, dayStart) + 1
        setWaterGlassesForDay(dayStart, glasses)
    }

    fun validateWaterGoalMl(goalMl: Int): String? = WaterGoalCalculator.validateGoalMl(goalMl)

    fun validateWaterGoalGlasses(glasses: Int): String? = WaterGoalCalculator.validateGoalGlasses(glasses)

    fun updateDailyWaterGoalGlasses(glasses: Int, onResult: (String?) -> Unit) {
        val error = validateWaterGoalGlasses(glasses)
        if (error != null) {
            onResult(error)
            return
        }
        viewModelScope.launch {
            saveProfile(userProfile.value.copy(dailyWaterGoalMl = glasses * WaterGoalCalculator.ML_PER_GLASS))
            onResult(null)
        }
    }

    fun updateDailyWaterGoalMl(goalMl: Int, onResult: (String?) -> Unit) {
        val error = validateWaterGoalMl(goalMl)
        if (error != null) {
            onResult(error)
            return
        }
        viewModelScope.launch {
            saveProfile(userProfile.value.copy(dailyWaterGoalMl = goalMl))
            onResult(null)
        }
    }

    fun logWeight(weightKg: Float) {
        viewModelScope.launch {
            val oldProfile = userProfile.value
            backfillMissingGoalSnapshots(oldProfile)
            repository.upsertWeightLog(weightKg)
            val newProfile = profileWithRecalculatedCalories(oldProfile, weightKg)
            saveProfile(newProfile)
            repository.updateTodayGoalSnapshot(newProfile)
        }
    }

    fun logBodyMeasurement(type: String, valueCm: Float) {
        viewModelScope.launch {
            repository.addBodyMeasurement(type, valueCm)
        }
    }

    fun measurementDisplayValue(valueCm: Float, useMetric: Boolean): String =
        if (useMetric) String.format("%.1f cm", valueCm)
        else String.format("%.1f in", valueCm / 2.54f)

    fun parseMeasurementInput(input: String, useMetric: Boolean): Float? {
        val v = input.toFloatOrNull() ?: return null
        return if (useMetric) v else v * 2.54f
    }

    fun chartPointsForType(
        type: String,
        weightLogs: List<com.example.data.WeightLog>,
        bodyLogs: List<com.example.data.BodyMeasurementLog>
    ): List<ProgressChartPoint> {
        return if (type == BodyMeasurementTypes.WEIGHT) {
            weightLogsByDay(weightLogs).map {
                ProgressChartPoint(it.timestamp, it.weightKg, String.format("%.1f kg", it.weightKg))
            }
        } else {
            bodyLogsByDay(bodyLogs.filter { it.measurementType == type }).map {
                val display = measurementDisplayValue(it.valueCm, userProfile.value.measurementUseMetric)
                ProgressChartPoint(it.timestamp, it.valueCm, display)
            }
        }
    }

    private fun weightLogsByDay(logs: List<com.example.data.WeightLog>): List<com.example.data.WeightLog> =
        logs.groupBy { log ->
            DietDateUtils.startOfDayMillis(java.util.Calendar.getInstance().apply { timeInMillis = log.timestamp })
        }.values.map { dayLogs -> dayLogs.maxBy { it.timestamp } }
            .sortedBy { it.timestamp }

    private fun bodyLogsByDay(logs: List<com.example.data.BodyMeasurementLog>): List<com.example.data.BodyMeasurementLog> =
        logs.groupBy { log ->
            DietDateUtils.startOfDayMillis(java.util.Calendar.getInstance().apply { timeInMillis = log.timestamp })
        }.values.map { dayLogs -> dayLogs.maxBy { it.timestamp } }
            .sortedBy { it.timestamp }

    fun latestValueForType(
        type: String,
        weightLogs: List<com.example.data.WeightLog>,
        bodyLogs: List<com.example.data.BodyMeasurementLog>
    ): String? {
        return if (type == BodyMeasurementTypes.WEIGHT) {
            weightLogsByDay(weightLogs).lastOrNull()?.weightKg?.let { String.format("%.1f kg", it) }
                ?: userProfile.value.currentWeight.takeIf { it > 0f }?.let { String.format("%.1f kg", it) }
        } else {
            bodyLogsByDay(bodyLogs.filter { it.measurementType == type }).lastOrNull()?.let {
                measurementDisplayValue(it.valueCm, userProfile.value.measurementUseMetric)
            }
        }
    }

    fun weeklyWaterGlasses(logs: List<com.example.data.WaterLog>, endingDayStart: Long = DietDateUtils.startOfTodayMillis()): List<Pair<Long, Int>> =
        DietDateUtils.last7DayStarts(endingDayStart).map { day ->
            day to waterGlassesForDay(logs, day)
        }

    fun saveWaterReminderSettings(
        enabled: Boolean,
        alarmEnabled: Boolean,
        mode: String,
        intervalMinutes: Int,
        timesPerDay: Int,
        dailyTimeMinute: Int,
        weeklyDay: Int,
        windowStartMinute: Int,
        windowEndMinute: Int
    ) {
        viewModelScope.launch {
            saveProfile(
                userProfile.value.copy(
                    waterReminderEnabled = enabled,
                    waterAlarmRemindersEnabled = alarmEnabled,
                    waterReminderMode = mode,
                    waterReminderIntervalMinutes = intervalMinutes.coerceAtLeast(15),
                    waterReminderTimesPerDay = timesPerDay.coerceIn(1, 12),
                    waterReminderDailyTimeMinute = dailyTimeMinute,
                    waterReminderWeeklyDay = weeklyDay,
                    waterReminderWindowStartMinute = windowStartMinute,
                    waterReminderWindowEndMinute = windowEndMinute
                )
            )
        }
    }

    fun waterMotivationMessage(glasses: Int, goalGlasses: Int, aiEnabled: Boolean): String? {
        if (!aiEnabled) return null
        val progress = if (goalGlasses > 0) glasses.toFloat() / goalGlasses else 0f
        return when {
            glasses == 0 -> "Start with one glass — small steps add up."
            progress >= 1f -> "Goal reached! Awesome hydration today."
            progress >= 0.75f -> "Almost there — keep going!"
            progress >= 0.5f -> "Halfway there. Water is good!"
            progress >= 0.25f -> "Awesome! Keep that momentum going."
            else -> "Great start — stay fueled with water."
        }
    }

    fun setWaterReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            saveProfile(userProfile.value.copy(waterReminderEnabled = enabled))
        }
    }

    fun snoozeHydrationReminder(minutes: Int = 60) {
        viewModelScope.launch {
            saveProfile(
                userProfile.value.copy(waterSnoozeUntilMs = System.currentTimeMillis() + minutes * 60_000L)
            )
        }
    }

    fun hydrationProgressPercent(
        logs: List<com.example.data.WaterLog> = allWaterLogs.value,
        dayStart: Long = DietDateUtils.startOfTodayMillis()
    ): Int = (waterProgress(logs, dayStart) * 100).toInt().coerceIn(0, 100)

    private val _dietCoachInsight = MutableStateFlow(CoachInsight.placeholder("AI DIET INSIGHT"))
    val dietCoachInsight: StateFlow<CoachInsight> = _dietCoachInsight.asStateFlow()

    private val _exerciseCoachInsight = MutableStateFlow(CoachInsight.placeholder("AI COACH INSIGHT"))
    val exerciseCoachInsight: StateFlow<CoachInsight> = _exerciseCoachInsight.asStateFlow()

    fun refreshCoachInsights() {
        viewModelScope.launch {
            val dietHeuristic = buildDietCoachInsightHeuristic()
            val exerciseHeuristic = buildExerciseCoachInsightHeuristic()
            _dietCoachInsight.value = dietHeuristic
            _exerciseCoachInsight.value = exerciseHeuristic

            if (isAiConfigured()) {
                val profile = userProfile.value
                val textSlot = AiRouteResolver.textSlot(profile)
                val dietSummary = buildDietCoachSummary()
                val exerciseSummary = buildExerciseCoachSummary()
                aiManager.generateDietCoachInsight(
                    dietSummary, textSlot.provider, textSlot.modelId, textSlot.offlineModelId
                )?.let { _dietCoachInsight.value = it }
                aiManager.generateExerciseCoachInsight(
                    exerciseSummary, textSlot.provider, textSlot.modelId, textSlot.offlineModelId
                )?.let { _exerciseCoachInsight.value = it }
            }
        }
    }

    /** @deprecated use refreshCoachInsights */
    fun refreshCoachInsight() = refreshCoachInsights()

    private fun buildDietCoachSummary(): String {
        val profile = userProfile.value
        val todayStart = DietDateUtils.startOfTodayMillis()
        val todayEnd = DietDateUtils.endOfDayMillis(todayStart)
        val todayMeals = allMeals.value.filter { it.timestamp in todayStart until todayEnd }
        return buildString {
            append("Goal: ${profile.goal}. ")
            append("Daily calories: ${profile.dailyCalories}, eaten ${todayMeals.sumOf { it.calories }}. ")
            append("Protein goal: ${profile.protein}g, eaten ${todayMeals.sumOf { it.protein }}g. ")
            append("Carbs goal: ${profile.carbs}g, fat: ${profile.fat}g. ")
            append("Water: ${waterGlassesForDay(allWaterLogs.value, todayStart)}/${WaterGoalCalculator.effectiveGoalGlasses(profile)} glasses. ")
            append("Target weight change: ${profile.targetWeightChangePerWeek} kg/week.")
        }
    }

    private fun buildExerciseCoachSummary(): String {
        val profile = userProfile.value
        val stats = workoutDashboardStats()
        val weekAgo = DietDateUtils.startOfTodayMillis() - 7 * DietDateUtils.DAY_MS
        val recentSets = allSets.value.filter { it.timestamp >= weekAgo && it.isCompleted }
        val topExercise = recentSets.groupBy { it.exerciseName }.maxByOrNull { it.value.size }?.key
        return buildString {
            append("Goal: ${profile.goal}. Fitness: ${profile.fitnessLevel}. ")
            append("Workouts this week: ${stats.workoutsCompleted}/${stats.workoutsTarget}. ")
            append("Volume: ${stats.totalVolumeKg.toInt()} kg. PRs: ${stats.prsHit}. ")
            topExercise?.let { append("Top exercise: $it. ") }
            append("Squat 1RM: ${profile.squat1RmKg}, Bench: ${profile.benchPress1RmKg}, Deadlift: ${profile.deadlift1RmKg}.")
        }
    }

    private fun buildDietCoachInsightHeuristic(): CoachInsight {
        val profile = userProfile.value
        val todayStart = DietDateUtils.startOfTodayMillis()
        val todayEnd = DietDateUtils.endOfDayMillis(todayStart)
        val todayMeals = allMeals.value.filter { it.timestamp in todayStart until todayEnd }
        val proteinConsumed = todayMeals.sumOf { it.protein }
        val caloriesConsumed = todayMeals.sumOf { it.calories }
        val proteinGap = (profile.protein - proteinConsumed).coerceAtLeast(0)
        val waterGlasses = waterGlassesForDay(allWaterLogs.value, todayStart)
        val goalGlasses = WaterGoalCalculator.effectiveGoalGlasses(profile)
        val waterGap = (goalGlasses - waterGlasses).coerceAtLeast(0)
        val calGap = profile.dailyCalories - caloriesConsumed

        return when {
            proteinGap > 20 -> CoachInsight(
                headline = "AI DIET INSIGHT",
                body = "You're ${proteinGap}g short on protein with $caloriesConsumed / ${profile.dailyCalories} kcal logged. Add a protein-rich meal to support your ${profile.goal.lowercase()} goal.",
                highlight = "${proteinGap}g"
            )
            waterGap > 2 -> CoachInsight(
                headline = "AI DIET INSIGHT",
                body = "Hydration is at $waterGlasses of $goalGlasses glasses. Drink more water to support recovery and nutrient absorption.",
                highlight = "$waterGlasses glasses"
            )
            calGap > 400 -> CoachInsight(
                headline = "AI DIET INSIGHT",
                body = "~$calGap kcal remaining toward your ${profile.dailyCalories} kcal target. Plan your next meal to stay on pace.",
                highlight = "$calGap kcal"
            )
            else -> CoachInsight(
                headline = "AI DIET INSIGHT",
                body = "Nutrition is on track today for your ${profile.goal.lowercase()} plan. Keep logging meals for smarter recommendations.",
                highlight = null
            )
        }
    }

    private fun buildExerciseCoachInsightHeuristic(): CoachInsight {
        val profile = userProfile.value
        val weekAgo = DietDateUtils.startOfTodayMillis() - 7 * DietDateUtils.DAY_MS
        val recentSets = allSets.value.filter { it.timestamp >= weekAgo && it.isCompleted }
        val topExercise = recentSets.groupBy { it.exerciseName }.maxByOrNull { it.value.size }?.key
        val overloadTip = topExercise?.let { name ->
            val lastSet = recentSets.filter { it.exerciseName.equals(name, ignoreCase = true) }
                .maxByOrNull { it.timestamp }
            if (lastSet != null) progressiveOverloadTip(name, lastSet.weight, lastSet.reps) else null
        }
        val stats = workoutDashboardStats()

        return when {
            overloadTip != null -> CoachInsight(
                headline = "AI PROGRESSIVE OVERLOAD TIP",
                body = overloadTip,
                highlight = Regex("\\d+\\.?\\d*\\s*kg[^.]*").find(overloadTip)?.value?.trim()
            )
            stats.workoutsCompleted < stats.workoutsTarget -> CoachInsight(
                headline = "AI COACH INSIGHT",
                body = "You've completed ${stats.workoutsCompleted} of ${stats.workoutsTarget} planned workouts this week. Today's suggested session keeps you on schedule.",
                highlight = "${stats.workoutsCompleted} / ${stats.workoutsTarget}"
            )
            else -> CoachInsight(
                headline = "AI COACH INSIGHT",
                body = "Strong week — ${stats.totalVolumeKg.toInt()} kg total volume logged. Focus on progressive overload on your main lifts.",
                highlight = "${stats.totalVolumeKg.toInt()} kg"
            )
        }
    }
}

sealed class ExerciseGuideUiState {
    data object Idle : ExerciseGuideUiState()
    data object Loading : ExerciseGuideUiState()
    data class Ready(
        val guide: com.example.data.ExerciseGuideDetail,
        val isGeneratingAi: Boolean = false
    ) : ExerciseGuideUiState()
    data class NoMatch(val exerciseName: String) : ExerciseGuideUiState()
    data class NeedsInternet(val exerciseName: String) : ExerciseGuideUiState()
    data class Error(val message: String) : ExerciseGuideUiState()
}

sealed class ApiConnectionTestState {
    data object Idle : ApiConnectionTestState()
    data object Testing : ApiConnectionTestState()
    data class Success(val message: String) : ApiConnectionTestState()
    data class Error(val message: String) : ApiConnectionTestState()
}

private data class AiConfigOverlay(
    val aiProvider: String,
    val aiModelId: String,
    val aiSplitModels: Boolean,
    val aiTextProvider: String,
    val aiTextModelId: String,
    val aiVisionProvider: String,
    val aiVisionModelId: String,
    val offlineModelId: String
)

private fun UserProfile.toAiConfigOverlay() = AiConfigOverlay(
    aiProvider = aiProvider,
    aiModelId = aiModelId,
    aiSplitModels = aiSplitModels,
    aiTextProvider = aiTextProvider,
    aiTextModelId = aiTextModelId,
    aiVisionProvider = aiVisionProvider,
    aiVisionModelId = aiVisionModelId,
    offlineModelId = offlineModelId
)

private fun UserProfile.applyAiConfig(overlay: AiConfigOverlay) = copy(
    aiProvider = overlay.aiProvider,
    aiModelId = overlay.aiModelId,
    aiSplitModels = overlay.aiSplitModels,
    aiTextProvider = overlay.aiTextProvider,
    aiTextModelId = overlay.aiTextModelId,
    aiVisionProvider = overlay.aiVisionProvider,
    aiVisionModelId = overlay.aiVisionModelId,
    offlineModelId = overlay.offlineModelId
)

private fun UserProfile.matchesAiConfig(overlay: AiConfigOverlay): Boolean =
    aiProvider == overlay.aiProvider &&
        aiModelId == overlay.aiModelId &&
        aiSplitModels == overlay.aiSplitModels &&
        aiTextProvider == overlay.aiTextProvider &&
        aiTextModelId == overlay.aiTextModelId &&
        aiVisionProvider == overlay.aiVisionProvider &&
        aiVisionModelId == overlay.aiVisionModelId &&
        offlineModelId == overlay.offlineModelId

enum class CoachChatRole { USER, ASSISTANT }

data class CoachPromptChip(val label: String, val prompt: String)

data class CoachChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: CoachChatRole,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class CoachInsight(
    val headline: String,
    val body: String,
    val highlight: String?
) {
    companion object {
        fun loading() = CoachInsight("AI COACH INSIGHT", "Analyzing your profile…", null)
        fun placeholder(headline: String) = CoachInsight(headline, "Gathering your latest data…", null)
    }
}

data class ProgressChartPoint(
    val timestamp: Long,
    val value: Float,
    val label: String
)

class GymViewModelFactory(
    private val repository: GymRepository,
    private val localFoodRepository: LocalFoodRepository,
    private val localExerciseRepository: LocalExerciseRepository,
    private val offRepository: OpenFoodFactsRepository,
    private val aiManager: com.example.data.AiManager,
    private val modelDownloadManager: com.example.data.ModelDownloadManager,
    private val secureStorageManager: com.example.data.SecureStorageManager,
    private val exerciseGuideRepository: com.example.data.ExerciseGuideRepository,
    private val coachHistoryRepository: com.example.data.CoachHistoryRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GymViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GymViewModel(
                repository, localFoodRepository, localExerciseRepository,
                offRepository, aiManager, modelDownloadManager, secureStorageManager,
                exerciseGuideRepository, coachHistoryRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
