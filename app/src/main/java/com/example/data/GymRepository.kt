package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class GymRepository(private val gymDao: GymDao) {
    val userProfile: Flow<UserProfile?> = gymDao.getUserProfile()
    val allSets: Flow<List<ExerciseSet>> = gymDao.getAllSets()
    val allMeals: Flow<List<MealEntry>> = gymDao.getAllMeals()
    val customFoods: Flow<List<CustomFoodItem>> = gymDao.getAllCustomFoods()
    val customExercises: Flow<List<CustomExercise>> = gymDao.getAllCustomExercises()
    val allRoutines: Flow<List<WorkoutRoutine>> = gymDao.getAllRoutines()

    fun routineExercises(routineId: Int): Flow<List<RoutineExercise>> =
        gymDao.getRoutineExercises(routineId)

    suspend fun saveProfile(profile: UserProfile) {
        gymDao.insertOrUpdateProfile(profile)
    }

    suspend fun addSet(set: ExerciseSet) {
        gymDao.insertSet(set)
    }

    suspend fun updateSet(set: ExerciseSet) {
        gymDao.updateSet(set)
    }

    suspend fun addMeal(meal: MealEntry) {
        gymDao.insertMeal(meal)
    }

    suspend fun updateMeal(meal: MealEntry) {
        gymDao.updateMeal(meal)
    }

    suspend fun deleteMeal(meal: MealEntry) {
        gymDao.deleteMeal(meal)
    }

    suspend fun addCustomFood(food: CustomFoodItem): Long {
        return gymDao.insertCustomFood(food)
    }

    suspend fun deleteCustomFood(food: CustomFoodItem) {
        gymDao.deleteCustomFood(food)
    }

    suspend fun deleteSet(set: ExerciseSet) {
        gymDao.deleteSet(set)
    }

    suspend fun deleteExerciseForDay(name: String, dayLabel: String, dayStart: Long, dayEnd: Long) {
        gymDao.deleteExerciseForDay(name, dayLabel, dayStart, dayEnd)
    }

    suspend fun deleteWorkoutForDay(dayLabel: String, dayStart: Long, dayEnd: Long) {
        gymDao.deleteWorkoutForDay(dayLabel, dayStart, dayEnd)
    }

    suspend fun addCustomExercise(exercise: CustomExercise): Long =
        gymDao.insertCustomExercise(exercise)

    suspend fun deleteCustomExercise(exercise: CustomExercise) {
        gymDao.deleteCustomExercise(exercise)
    }

    suspend fun routineCount(): Int = gymDao.routineCount()

    suspend fun insertRoutine(routine: WorkoutRoutine): Long = gymDao.insertRoutine(routine)

    suspend fun updateRoutine(routine: WorkoutRoutine) = gymDao.updateRoutine(routine)

    suspend fun deleteRoutine(routine: WorkoutRoutine) {
        gymDao.deleteRoutineExercises(routine.id)
        gymDao.deleteRoutine(routine)
    }

    suspend fun getRoutineExercisesOnce(routineId: Int): List<RoutineExercise> =
        gymDao.getRoutineExercisesOnce(routineId)

    suspend fun insertRoutineExercise(exercise: RoutineExercise): Long =
        gymDao.insertRoutineExercise(exercise)

    suspend fun updateRoutineExercise(exercise: RoutineExercise) =
        gymDao.updateRoutineExercise(exercise)

    suspend fun deleteRoutineExercise(exercise: RoutineExercise) =
        gymDao.deleteRoutineExercise(exercise)

    suspend fun replaceRoutineExercises(routineId: Int, exercises: List<RoutineExercise>) {
        gymDao.deleteRoutineExercises(routineId)
        exercises.forEachIndexed { index, ex ->
            gymDao.insertRoutineExercise(ex.copy(id = 0, routineId = routineId, sortOrder = index))
        }
    }

    suspend fun seedBuiltInRoutinesIfEmpty() {
        if (gymDao.routineCount() > 0) return
        RoutineTemplateSeeder.builtInTemplates.forEachIndexed { index, template ->
            val routineId = gymDao.insertRoutine(
                WorkoutRoutine(name = template.name, isBuiltIn = true, sortOrder = index)
            ).toInt()
            template.exercises.forEachIndexed { exIndex, ex ->
                gymDao.insertRoutineExercise(
                    RoutineExercise(
                        routineId = routineId,
                        exerciseName = ex.name,
                        exerciseType = ex.type,
                        equipment = ex.equipment,
                        sortOrder = exIndex,
                        defaultSets = ex.sets,
                        defaultReps = ex.reps
                    )
                )
            }
        }
    }

    val allWaterLogs = gymDao.getAllWaterLogs()
    val allWeightLogs = gymDao.getAllWeightLogs()
    val allBodyMeasurements = gymDao.getAllBodyMeasurements()

    fun waterLogsForDay(dayStart: Long, dayEnd: Long) = gymDao.getWaterLogsForDay(dayStart, dayEnd)

    suspend fun addWaterLog(amountMl: Int, timestamp: Long = System.currentTimeMillis()) {
        gymDao.insertWaterLog(WaterLog(amountMl = amountMl, timestamp = timestamp))
    }

    suspend fun setWaterGlassesForDay(dayStart: Long, glasses: Int) {
        val dayEnd = DietDateUtils.endOfDayMillis(dayStart)
        gymDao.deleteWaterLogsForDay(dayStart, dayEnd)
        if (glasses > 0) {
            val ts = DietDateUtils.timestampForDay(dayStart)
            gymDao.insertWaterLog(
                WaterLog(
                    amountMl = glasses * WaterGoalCalculator.ML_PER_GLASS,
                    timestamp = ts
                )
            )
        }
    }

    suspend fun upsertWeightLog(weightKg: Float, timestamp: Long = System.currentTimeMillis()) {
        val dayStart = DietDateUtils.startOfDayMillis(
            java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
        )
        val dayEnd = DietDateUtils.endOfDayMillis(dayStart)
        val existing = gymDao.getWeightLogForDay(dayStart, dayEnd)
        if (existing != null) {
            gymDao.insertWeightLog(existing.copy(weightKg = weightKg, timestamp = timestamp))
        } else {
            gymDao.insertWeightLog(WeightLog(weightKg = weightKg, timestamp = timestamp))
        }
    }

    suspend fun addWeightLog(weightKg: Float, timestamp: Long = System.currentTimeMillis()) {
        upsertWeightLog(weightKg, timestamp)
    }

    suspend fun addBodyMeasurement(type: String, valueCm: Float, timestamp: Long = System.currentTimeMillis()) {
        gymDao.insertBodyMeasurement(BodyMeasurementLog(measurementType = type, valueCm = valueCm, timestamp = timestamp))
    }

    fun bodyMeasurementsForType(type: String) = gymDao.getBodyMeasurements(type)

    val allGoalSnapshots = gymDao.getAllGoalSnapshots()
    val allNotifications = gymDao.getAllNotifications()

    suspend fun ensureGoalSnapshot(dayStart: Long, profile: UserProfile) {
        if (gymDao.getGoalSnapshot(dayStart) != null) return
        gymDao.upsertGoalSnapshot(profile.toGoalSnapshot(dayStart))
    }

    suspend fun updateTodayGoalSnapshot(profile: UserProfile) {
        val today = DietDateUtils.startOfTodayMillis()
        gymDao.upsertGoalSnapshot(profile.toGoalSnapshot(today))
    }

    suspend fun addNotification(title: String, body: String, category: String = "general") {
        gymDao.insertNotification(AppNotification(title = title, body = body, category = category))
    }

    suspend fun deleteNotification(id: Int) {
        gymDao.deleteNotification(id)
    }

    suspend fun normalizeProfileAfterRestore(): UserProfile? {
        val profile = gymDao.getUserProfile().first() ?: return null
        val normalized = WorkoutSetupProgress.profileAfterRestore(profile)
        gymDao.insertOrUpdateProfile(normalized)
        return normalized
    }

    private fun UserProfile.toGoalSnapshot(dayStart: Long) = DailyGoalSnapshot(
        dayStart = dayStart,
        dailyCalories = dailyCalories,
        protein = protein,
        carbs = carbs,
        fat = fat,
        fiber = fiber
    )
}
