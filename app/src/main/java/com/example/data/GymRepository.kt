package com.example.data

import kotlinx.coroutines.flow.Flow

class GymRepository(private val gymDao: GymDao) {
    val userProfile: Flow<UserProfile?> = gymDao.getUserProfile()
    val allSets: Flow<List<ExerciseSet>> = gymDao.getAllSets()
    val allMeals: Flow<List<MealEntry>> = gymDao.getAllMeals()
    val customFoods: Flow<List<CustomFoodItem>> = gymDao.getAllCustomFoods()

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
}
