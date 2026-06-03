package com.example.data

import kotlinx.coroutines.flow.Flow

class GymRepository(private val gymDao: GymDao) {
    val userProfile: Flow<UserProfile?> = gymDao.getUserProfile()
    val allSets: Flow<List<ExerciseSet>> = gymDao.getAllSets()
    val allMeals: Flow<List<MealEntry>> = gymDao.getAllMeals()
    val customFoods: Flow<List<CustomFoodItem>> = gymDao.getAllCustomFoods()
    val customExercises: Flow<List<CustomExercise>> = gymDao.getAllCustomExercises()

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

    suspend fun addCustomExercise(exercise: CustomExercise): Long =
        gymDao.insertCustomExercise(exercise)

    suspend fun deleteCustomExercise(exercise: CustomExercise) {
        gymDao.deleteCustomExercise(exercise)
    }
}
