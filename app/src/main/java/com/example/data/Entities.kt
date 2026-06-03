package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 1,
    val age: Int = 0,
    val gender: String = "",
    val height: Int = 0,
    val activityLevel: String = "",
    val goal: String = "",
    val currentWeight: Float = 0f,
    val targetWeight: Float = 0f,
    val dailyCalories: Int = 0,
    val protein: Int = 0,
    val carbs: Int = 0,
    val fat: Int = 0,
    val fiber: Int = 0,
    val workoutDaysPerWeek: Int = 0,
    val targetWeightChangePerWeek: Float = 0f,
    val weeksToGoal: Int = 0,
    val maintenanceCalories: Int = 0,
    val calorieAdjustmentDaily: Int = 0,
    val calorieAdjustmentWeekly: Int = 0,
    val cuisinePreferences: String = "",
    val onboardingComplete: Boolean = false,
    val aiProvider: String = "gemini",       // "gemini", "groq", "offline"
    val aiModelId: String = "gemini-2.0-flash",
    val offlineModelId: String = "offline_2b"   // which offline model to use when provider=offline
)

@Entity(tableName = "progress_photos")
data class ProgressPhoto(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val photoPath: String,
    val dateMillis: Long = System.currentTimeMillis(),
    val weightKg: Float = 0f
)

@Entity(tableName = "custom_foods")
data class CustomFoodItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val caloriesPer100g: Int,
    val proteinPer100g: Float,
    val carbsPer100g: Float,
    val fatPer100g: Float,
    val fiberPer100g: Float,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "exercise_sets")
data class ExerciseSet(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val exerciseName: String,
    val exerciseType: String = "",
    val workoutDayLabel: String = "",
    val setNumber: Int,
    val weight: Float,
    val reps: Int,
    val isCompleted: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "custom_exercises")
data class CustomExercise(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val exerciseType: String,
    val defaultSets: Int = 3,
    val defaultReps: Int = 10,
    val defaultWeight: Float = 0f,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "meals")
data class MealEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val mealType: String,
    val foodName: String = "",
    val weightGrams: Int = 0,
    val calories: Int,
    val protein: Int,
    val carbs: Int,
    val fat: Int,
    val fiber: Int,
    val timestamp: Long = System.currentTimeMillis()
)
