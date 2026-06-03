package com.example

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.CustomFoodItem
import com.example.data.ExerciseSet
import com.example.data.FitnessCalculator
import com.example.data.FoodItem
import com.example.data.FoodNutritionCalculator
import com.example.data.GymRepository
import com.example.data.LocalFoodRepository
import com.example.data.MealEntry
import com.example.data.UserProfile
import com.example.data.api.OpenFoodFactsRepository
import com.example.data.DownloadStatus
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
    val downloadId2B: Long? = null,
    val downloadId4B: Long? = null,
    val progress2B: Float = 0f,
    val progress4B: Float = 0f,
    val isDownloading2B: Boolean = false,
    val isDownloading4B: Boolean = false,
    val error: String? = null
)

class GymViewModel(
    private val repository: GymRepository,
    private val localFoodRepository: LocalFoodRepository,
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

    private var localSearchJob: Job? = null
    private var apiSearchJob: Job? = null
    private var downloadJob2B: Job? = null
    private var downloadJob4B: Job? = null

    fun saveProfile(profile: UserProfile) {
        viewModelScope.launch { repository.saveProfile(profile) }
    }

    fun startModelDownload(modelType: String) {
        val downloadId = modelDownloadManager.downloadModel(modelType)
        if (modelType == "offline_2b") {
            _aiSettingsState.update { it.copy(downloadId2B = downloadId, isDownloading2B = true) }
            downloadJob2B?.cancel()
            downloadJob2B = viewModelScope.launch {
                modelDownloadManager.getDownloadProgress(downloadId).collectLatest { status ->
                    when (status) {
                        is DownloadStatus.Downloading -> _aiSettingsState.update { it.copy(progress2B = status.progress) }
                        DownloadStatus.Success -> _aiSettingsState.update { it.copy(isDownloading2B = false, progress2B = 1f) }
                        is DownloadStatus.Error -> _aiSettingsState.update { it.copy(isDownloading2B = false, error = status.message) }
                    }
                }
            }
        } else {
            _aiSettingsState.update { it.copy(downloadId4B = downloadId, isDownloading4B = true) }
            downloadJob4B?.cancel()
            downloadJob4B = viewModelScope.launch {
                modelDownloadManager.getDownloadProgress(downloadId).collectLatest { status ->
                    when (status) {
                        is DownloadStatus.Downloading -> _aiSettingsState.update { it.copy(progress4B = status.progress) }
                        DownloadStatus.Success -> _aiSettingsState.update { it.copy(isDownloading4B = false, progress4B = 1f) }
                        is DownloadStatus.Error -> _aiSettingsState.update { it.copy(isDownloading4B = false, error = status.message) }
                    }
                }
            }
        }
    }

    fun cancelModelDownload(modelType: String) {
        val id = if (modelType == "offline_2b") _aiSettingsState.value.downloadId2B else _aiSettingsState.value.downloadId4B
        id?.let { modelDownloadManager.cancelDownload(it) }
        if (modelType == "offline_2b") {
            downloadJob2B?.cancel()
            _aiSettingsState.update { it.copy(isDownloading2B = false, progress2B = 0f) }
        } else {
            downloadJob4B?.cancel()
            _aiSettingsState.update { it.copy(isDownloading4B = false, progress4B = 0f) }
        }
    }

    fun deleteModel(modelType: String) {
        modelDownloadManager.deleteModel(modelType)
        // Profile update will happen automatically if we had a Room flow for download status, 
        // but since it's file based, we might need a manual refresh or just UI state update.
    }

    fun updateAiMode(mode: String) {
        val current = userProfile.value
        saveProfile(current.copy(aiMode = mode))
    }

    fun setGeminiApiKey(key: String) {
        secureStorageManager.saveGeminiApiKey(key)
    }

    fun clearGeminiApiKey() {
        secureStorageManager.clearGeminiApiKey()
    }

    fun getGeminiApiKey(): String? {
        return secureStorageManager.getGeminiApiKey()
    }

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

    fun addFoodToMeal(mealType: String, food: FoodItem, weightGrams: Int) {
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
                fiber = nutrition.fiber
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
    private val offRepository: OpenFoodFactsRepository,
    private val aiManager: com.example.data.AiManager,
    private val modelDownloadManager: com.example.data.ModelDownloadManager,
    private val secureStorageManager: com.example.data.SecureStorageManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GymViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GymViewModel(repository, localFoodRepository, offRepository, aiManager, modelDownloadManager, secureStorageManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
