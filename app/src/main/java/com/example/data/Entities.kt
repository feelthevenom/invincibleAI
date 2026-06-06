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
    // Workout setup profile (Gym Workout–style)
    val workoutSetupComplete: Boolean = false,
    val fitnessLevel: String = "",
    val benchmarkSkipped: Boolean = false,
    val squat1RmKg: Float = 0f,
    val benchPress1RmKg: Float = 0f,
    val deadlift1RmKg: Float = 0f,
    val weekStartDay: Int = java.util.Calendar.MONDAY,
    val equipmentSelection: String = "",
    val gymLocation: String = "",
    val aiProvider: String = "gemini",       // "gemini", "groq", "offline"
    val aiModelId: String = "gemini-2.5-flash",
    val offlineModelId: String = "offline_2b",   // which offline model to use when provider=offline
    val aiSplitModels: Boolean = false,
    val aiTextProvider: String = "gemini",
    val aiTextModelId: String = "gemini-2.5-flash",
    val aiVisionProvider: String = "gemini",
    val aiVisionModelId: String = "gemini-2.5-flash",
    // Hydration & progress tracking
    val dailyWaterGoalMl: Int = 0,              // 0 = auto from body weight; stored as glasses * 250
    val waterReminderEnabled: Boolean = false,
    val waterSnoozeUntilMs: Long = 0L,
    val measurementUseMetric: Boolean = true,   // true = cm/kg, false = inch/lbs display for measurements
    val notificationsEnabled: Boolean = false,  // granted during initial setup
    val waterAlarmRemindersEnabled: Boolean = false, // exact-alarm based scheduling
    val waterReminderMode: String = "times",    // interval | times | daily | weekly
    val waterReminderIntervalMinutes: Int = 60,
    val waterReminderTimesPerDay: Int = 3,
    val waterReminderDailyTimeMinute: Int = 21 * 60 + 30, // minutes from midnight
    val waterReminderWeeklyDay: Int = java.util.Calendar.SUNDAY,
    val waterReminderWindowStartMinute: Int = 9 * 60 + 32,
    val waterReminderWindowEndMinute: Int = 22 * 60,
    val workoutReminderEnabled: Boolean = false,
    val workoutReminderTimeMinute: Int = 6 * 60 + 30,
    val workoutReminderRepeat: Boolean = true
)

@Entity(tableName = "daily_goal_snapshots")
data class DailyGoalSnapshot(
    @PrimaryKey val dayStart: Long,
    val dailyCalories: Int,
    val protein: Int,
    val carbs: Int,
    val fat: Int,
    val fiber: Int
)

@Entity(tableName = "app_notifications")
data class AppNotification(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val body: String,
    val timestamp: Long = System.currentTimeMillis(),
    val category: String = "general" // hydration | general | workout
)

@Entity(tableName = "water_logs")
data class WaterLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val amountMl: Int,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "weight_logs")
data class WeightLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val weightKg: Float,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "body_measurement_logs")
data class BodyMeasurementLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val measurementType: String,
    val valueCm: Float,
    val timestamp: Long = System.currentTimeMillis()
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

@Entity(tableName = "workout_routines")
data class WorkoutRoutine(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val isBuiltIn: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "routine_exercises")
data class RoutineExercise(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val routineId: Int,
    val exerciseName: String,
    val exerciseType: String,
    val equipment: String = "",
    val sortOrder: Int = 0,
    val defaultSets: Int = 3,
    val defaultReps: Int = 10,
    val defaultWeight: Float = 0f,
    val isCardio: Boolean = false
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
    val durationSeconds: Int = 0,
    val caloriesBurned: Int = 0,
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
    val isCardio: Boolean = false,
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
