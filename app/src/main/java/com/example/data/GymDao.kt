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

    @androidx.room.Delete
    suspend fun deleteSet(exerciseSet: ExerciseSet)

    @Query("DELETE FROM exercise_sets WHERE exerciseName = :name AND workoutDayLabel = :dayLabel AND timestamp >= :dayStart AND timestamp < :dayEnd")
    suspend fun deleteExerciseForDay(name: String, dayLabel: String, dayStart: Long, dayEnd: Long)

    @Query("DELETE FROM exercise_sets WHERE workoutDayLabel = :dayLabel AND timestamp >= :dayStart AND timestamp < :dayEnd")
    suspend fun deleteWorkoutForDay(dayLabel: String, dayStart: Long, dayEnd: Long)

    @Query("SELECT * FROM custom_exercises ORDER BY createdAt DESC")
    fun getAllCustomExercises(): Flow<List<CustomExercise>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomExercise(exercise: CustomExercise): Long

    @androidx.room.Delete
    suspend fun deleteCustomExercise(exercise: CustomExercise)

    @Query("SELECT * FROM meals WHERE timestamp >= :dayStart AND timestamp < :dayEnd ORDER BY timestamp DESC")
    fun getMealsForDay(dayStart: Long, dayEnd: Long): Flow<List<MealEntry>>

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

    @Query("SELECT * FROM workout_routines ORDER BY sortOrder ASC, createdAt ASC")
    fun getAllRoutines(): Flow<List<WorkoutRoutine>>

    @Query("SELECT * FROM routine_exercises WHERE routineId = :routineId ORDER BY sortOrder ASC")
    fun getRoutineExercises(routineId: Int): Flow<List<RoutineExercise>>

    @Query("SELECT * FROM routine_exercises WHERE routineId = :routineId ORDER BY sortOrder ASC")
    suspend fun getRoutineExercisesOnce(routineId: Int): List<RoutineExercise>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutine(routine: WorkoutRoutine): Long

    @Update
    suspend fun updateRoutine(routine: WorkoutRoutine)

    @androidx.room.Delete
    suspend fun deleteRoutine(routine: WorkoutRoutine)

    @Query("DELETE FROM routine_exercises WHERE routineId = :routineId")
    suspend fun deleteRoutineExercises(routineId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutineExercise(exercise: RoutineExercise): Long

    @Update
    suspend fun updateRoutineExercise(exercise: RoutineExercise)

    @androidx.room.Delete
    suspend fun deleteRoutineExercise(exercise: RoutineExercise)

    @Query("SELECT COUNT(*) FROM workout_routines")
    suspend fun routineCount(): Int

    @Query("SELECT * FROM water_logs ORDER BY timestamp DESC")
    fun getAllWaterLogs(): Flow<List<WaterLog>>

    @Query("SELECT * FROM water_logs WHERE timestamp >= :dayStart AND timestamp < :dayEnd")
    fun getWaterLogsForDay(dayStart: Long, dayEnd: Long): Flow<List<WaterLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWaterLog(log: WaterLog)

    @Query("SELECT * FROM weight_logs ORDER BY timestamp ASC")
    fun getAllWeightLogs(): Flow<List<WeightLog>>

    @Query("DELETE FROM water_logs WHERE timestamp >= :dayStart AND timestamp < :dayEnd")
    suspend fun deleteWaterLogsForDay(dayStart: Long, dayEnd: Long)

    @Query("SELECT * FROM weight_logs WHERE timestamp >= :dayStart AND timestamp < :dayEnd ORDER BY timestamp DESC LIMIT 1")
    suspend fun getWeightLogForDay(dayStart: Long, dayEnd: Long): WeightLog?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeightLog(log: WeightLog)

    @Query("SELECT * FROM body_measurement_logs WHERE measurementType = :type ORDER BY timestamp ASC")
    fun getBodyMeasurements(type: String): Flow<List<BodyMeasurementLog>>

    @Query("SELECT * FROM body_measurement_logs ORDER BY timestamp DESC")
    fun getAllBodyMeasurements(): Flow<List<BodyMeasurementLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBodyMeasurement(log: BodyMeasurementLog)

    @Query("SELECT * FROM daily_goal_snapshots WHERE dayStart = :dayStart LIMIT 1")
    suspend fun getGoalSnapshot(dayStart: Long): DailyGoalSnapshot?

    @Query("SELECT * FROM daily_goal_snapshots")
    fun getAllGoalSnapshots(): Flow<List<DailyGoalSnapshot>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGoalSnapshot(snapshot: DailyGoalSnapshot)

    @Query("SELECT * FROM app_notifications ORDER BY timestamp DESC")
    fun getAllNotifications(): Flow<List<AppNotification>>

    @Insert
    suspend fun insertNotification(notification: AppNotification)

    @Query("DELETE FROM app_notifications WHERE id = :id")
    suspend fun deleteNotification(id: Int)

    @Query("SELECT * FROM cached_exercise_guides WHERE lookupKey = :lookupKey LIMIT 1")
    suspend fun getCachedExerciseGuide(lookupKey: String): CachedExerciseGuide?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCachedExerciseGuide(guide: CachedExerciseGuide)

    @Query("SELECT * FROM coach_chat_history ORDER BY updatedAt DESC LIMIT 3")
    suspend fun getCoachChatHistory(): List<CoachChatHistoryEntity>

    @Query("SELECT * FROM coach_chat_history WHERE id = :id LIMIT 1")
    suspend fun getCoachChatHistoryById(id: String): CoachChatHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCoachChatHistory(session: CoachChatHistoryEntity)

    @Query("DELETE FROM coach_chat_history WHERE id = :id")
    suspend fun deleteCoachChatHistory(id: String)
}
