package com.example

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ExerciseItem
import com.example.data.ExerciseSet
import com.example.data.CustomExercise
import com.example.data.WorkoutExerciseGroup
import com.example.data.WorkoutSplitGenerator
import com.example.data.DietDateUtils
import com.example.data.WorkoutGrouping
import com.example.data.FitnessCalculator
import com.example.data.AiAnalysisResult
import com.example.data.AiProviderConfig
import com.example.data.FoodItem
import android.graphics.Bitmap
import com.example.data.FoodNutritionCalculator
import com.example.data.GymRepository
import com.example.data.LocalFoodRepository
import com.example.data.LocalExerciseRepository
import com.example.data.CustomFoodItem
import com.example.data.MealEntry
import com.example.data.UserProfile
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
    val error: String? = null
)

data class ExerciseSearchUiState(
    val query: String = "",
    val localResults: List<ExerciseItem> = emptyList(),
    val suggestions: List<ExerciseItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class GymViewModel(
    private val repository: GymRepository,
    private val localFoodRepository: LocalFoodRepository,
    private val localExerciseRepository: LocalExerciseRepository,
    private val offRepository: OpenFoodFactsRepository = OpenFoodFactsRepository(),
    val aiManager: com.example.data.AiManager,
    val modelDownloadManager: com.example.data.ModelDownloadManager,
    val secureStorageManager: com.example.data.SecureStorageManager
) : ViewModel() {

    val userProfile: StateFlow<UserProfile> = repository.userProfile
        .map { it ?: UserProfile() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserProfile())

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

    private fun bumpModelsRevision() = _modelsRevision.update { it + 1 }
    private fun bumpSecretsRevision() = _secretsRevision.update { it + 1 }

    private val _exerciseSearchState = MutableStateFlow(ExerciseSearchUiState())
    val exerciseSearchState: StateFlow<ExerciseSearchUiState> = _exerciseSearchState.asStateFlow()

    val customExercises: StateFlow<List<CustomExercise>> = repository.customExercises
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var exerciseSearchJob: Job? = null
    private val seededWorkoutDays = mutableSetOf<String>()
    private var localSearchJob: Job? = null
    private var apiSearchJob: Job? = null
    private var downloadJob2B: Job? = null
    private var downloadJob4B: Job? = null

    fun saveProfile(profile: UserProfile) {
        viewModelScope.launch { repository.saveProfile(profile) }
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
                            aiManager.releaseOfflineEngine()
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
                            aiManager.releaseOfflineEngine()
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
        aiManager.releaseOfflineEngine()
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
        return aiManager.getStatus(p.aiProvider, p.aiModelId, p.offlineModelId)
    }

    fun isAiConfigured(): Boolean {
        val p = userProfile.value
        if (p.aiProvider == "offline") {
            return modelDownloadManager.isModelInstalled(p.offlineModelId)
        }
        return aiStatus().isReady
    }

    fun supportsVision(): Boolean {
        val p = userProfile.value
        if (!isAiConfigured()) return false
        if (p.aiProvider == "offline") {
            return modelDownloadManager.isModelInstalled(p.offlineModelId)
        }
        val modelKey = p.aiModelId
        return AiProviderConfig.supportsVision(p.aiProvider, modelKey)
    }

    suspend fun generateFoodSuggestions(query: String): List<FoodItem> {
        val p = userProfile.value
        return aiManager.generateFoodSuggestions(query, p.aiProvider, p.aiModelId, p.offlineModelId)
    }

    suspend fun analyzeFoodImage(bitmap: Bitmap): AiAnalysisResult {
        val p = userProfile.value
        return aiManager.analyzeFoodImage(bitmap, p.aiProvider, p.aiModelId, p.offlineModelId)
    }

    fun updateAiProvider(provider: String) {
        val current = userProfile.value
        val defaultModel = when (provider) {
            "gemini" -> AiProviderConfig.findModel("gemini", current.aiModelId)?.id
                ?: "gemini-2.0-flash"
            "groq" -> AiProviderConfig.findModel("groq", current.aiModelId)?.id
                ?: "llama-3.3-70b-versatile"
            else -> current.aiModelId
        }
        saveProfile(current.copy(aiProvider = provider, aiModelId = defaultModel))
    }

    fun updateAiModel(modelId: String) {
        val current = userProfile.value
        saveProfile(current.copy(aiModelId = modelId))
    }

    fun updateOfflineModelId(offlineModelId: String) {
        val current = userProfile.value
        saveProfile(current.copy(offlineModelId = offlineModelId))
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

    fun setHuggingFaceToken(token: String) {
        secureStorageManager.saveHuggingFaceToken(token.trim())
    }

    fun getHuggingFaceToken(): String? = secureStorageManager.getHuggingFaceToken()

    fun completeOnboarding(profile: UserProfile) {
        viewModelScope.launch {
            repository.saveProfile(profile.copy(onboardingComplete = true))
        }
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
        val maintenance = FitnessCalculator.tdee(
            current.gender, current.currentWeight, current.height, current.age, current.activityLevel
        )
        val adjustment = FitnessCalculator.calorieAdjustmentDaily(maintenance, dailyCalories)
        val updated = current.copy(
            targetWeight = targetWeight,
            dailyCalories = dailyCalories,
            protein = protein,
            carbs = carbs,
            fat = fat,
            fiber = fiber,
            targetWeightChangePerWeek = weekly,
            maintenanceCalories = maintenance,
            calorieAdjustmentDaily = adjustment,
            calorieAdjustmentWeekly = adjustment * 7,
            weeksToGoal = FitnessCalculator.weeksToReachGoal(
                current.currentWeight, targetWeight, weekly
            ),
            cuisinePreferences = cuisinePreferences ?: current.cuisinePreferences
        )
        saveProfile(updated)
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

    private fun lastWeightForExercise(exerciseName: String): Float {
        return allSets.value
            .filter { it.exerciseName.equals(exerciseName, ignoreCase = true) && it.weight > 0f }
            .maxByOrNull { it.timestamp }
            ?.weight ?: 0f
    }

    fun addExerciseToDay(
        exercise: ExerciseItem,
        dayLabel: String,
        logDayStart: Long,
        weightKg: Float? = null,
        setCount: Int? = null,
        reps: Int? = null
    ) {
        viewModelScope.launch {
            val count = setCount ?: exercise.defaultSets
            val repCount = reps ?: exercise.defaultReps
            val weight = weightKg ?: lastWeightForExercise(exercise.name)
            val timestamp = DietDateUtils.timestampForDay(logDayStart)
            repeat(count) { index ->
                repository.addSet(
                    ExerciseSet(
                        exerciseName = exercise.name,
                        exerciseType = exercise.exerciseType,
                        workoutDayLabel = dayLabel,
                        setNumber = index + 1,
                        weight = weight,
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
                    val weight = last?.weight ?: 0f
                    val reps = last?.reps ?: 10
                    val ts = last?.timestamp ?: DietDateUtils.timestampForDay(dayStart)
                    for (n in (current.size + 1)..newSetCount) {
                        repository.addSet(
                            ExerciseSet(
                                exerciseName = group.exerciseName,
                                exerciseType = group.exerciseType,
                                workoutDayLabel = group.workoutDayLabel,
                                setNumber = n,
                                weight = weight,
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
                it.copy(suggestions = list, localResults = list, isLoading = false)
            }
        }
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
        val p = userProfile.value
        return aiManager.generateExerciseSuggestions(query, routine, p.aiProvider, p.aiModelId, p.offlineModelId)
    }

    fun addCustomExerciseAndReturn(exercise: CustomExercise, dayLabel: String, logDayStart: Long, onSaved: (ExerciseItem) -> Unit) {
        viewModelScope.launch {
            val id = repository.addCustomExercise(exercise)
            val item = ExerciseItem(
                id = "custom_$id",
                name = exercise.name,
                exerciseType = exercise.exerciseType,
                routines = listOf(routineFromDayLabel(dayLabel)),
                defaultSets = exercise.defaultSets,
                defaultReps = exercise.defaultReps,
                isCustom = true
            )
            addExerciseToDay(item, dayLabel, logDayStart, exercise.defaultWeight, exercise.defaultSets, exercise.defaultReps)
            onSaved(item)
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

    fun editExerciseGroup(
        group: WorkoutExerciseGroup,
        newSetCount: Int,
        weight: Float,
        reps: Int,
        dayStart: Long
    ) {
        viewModelScope.launch {
            group.sets.forEach { set ->
                repository.updateSet(set.copy(weight = weight, reps = reps))
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
                    for (n in (current.size + 1)..newSetCount) {
                        repository.addSet(
                            ExerciseSet(
                                exerciseName = group.exerciseName,
                                exerciseType = group.exerciseType,
                                workoutDayLabel = group.workoutDayLabel,
                                setNumber = n,
                                weight = weight,
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
                    repository.addSet(
                        ExerciseSet(
                            exerciseName = exercise.name,
                            exerciseType = exercise.exerciseType,
                            workoutDayLabel = dayLabel,
                            setNumber = index + 1,
                            weight = lastWeightForExercise(exercise.name),
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
}

class GymViewModelFactory(
    private val repository: GymRepository,
    private val localFoodRepository: LocalFoodRepository,
    private val localExerciseRepository: LocalExerciseRepository,
    private val offRepository: OpenFoodFactsRepository,
    private val aiManager: com.example.data.AiManager,
    private val modelDownloadManager: com.example.data.ModelDownloadManager,
    private val secureStorageManager: com.example.data.SecureStorageManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GymViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GymViewModel(
                repository, localFoodRepository, localExerciseRepository,
                offRepository, aiManager, modelDownloadManager, secureStorageManager
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
