package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GymDao {
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun getUserProfile(): Flow<UserProfile?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProfile(profile: UserProfile)

    @Query("SELECT * FROM exercise_sets ORDER BY timestamp ASC")
    fun getAllSets(): Flow<List<ExerciseSet>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSet(exerciseSet: ExerciseSet)

    @Update
    suspend fun updateSet(exerciseSet: ExerciseSet)

    @Query("SELECT * FROM meals ORDER BY timestamp DESC")
    fun getAllMeals(): Flow<List<MealEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeal(meal: MealEntry)

    @Query("SELECT * FROM custom_foods ORDER BY createdAt DESC")
    fun getAllCustomFoods(): Flow<List<CustomFoodItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomFood(food: CustomFoodItem): Long

    @Query("SELECT * FROM progress_photos ORDER BY dateMillis DESC")
    fun getAllProgressPhotos(): Flow<List<ProgressPhoto>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgressPhoto(photo: ProgressPhoto)

    @androidx.room.Delete
    suspend fun deleteMeal(meal: MealEntry)

    @Update
    suspend fun updateMeal(meal: MealEntry)

    @androidx.room.Delete
    suspend fun deleteCustomFood(food: CustomFoodItem)
}
