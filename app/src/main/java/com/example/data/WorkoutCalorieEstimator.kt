package com.example.data

/**
 * Workout calorie estimates using MET-based formulas (Compendium of Physical Activities).
 * Cardio: kcal = MET × weight(kg) × duration(hours)
 * Strength: volume-based + session MET estimate (~5 MET, ~3 min per set).
 */
object WorkoutCalorieEstimator {

    fun metForCardio(exerciseName: String): Float = when {
        exerciseName.contains("walk", ignoreCase = true) -> 3.5f
        exerciseName.contains("treadmill", ignoreCase = true) && exerciseName.contains("walk", ignoreCase = true) -> 4.0f
        exerciseName.contains("treadmill", ignoreCase = true) || exerciseName.contains("run", ignoreCase = true) -> 9.0f
        exerciseName.contains("cycle", ignoreCase = true) || exerciseName.contains("bike", ignoreCase = true) -> 7.5f
        exerciseName.contains("row", ignoreCase = true) -> 7.0f
        exerciseName.contains("swim", ignoreCase = true) -> 8.0f
        exerciseName.contains("elliptical", ignoreCase = true) -> 5.0f
        exerciseName.contains("stair", ignoreCase = true) -> 8.8f
        exerciseName.contains("rope", ignoreCase = true) -> 10.0f
        else -> 6.0f
    }

    fun estimateCardioCalories(
        durationSeconds: Int,
        weightKg: Float,
        exerciseName: String,
        storedCalories: Int = 0
    ): Int {
        if (storedCalories > 0) return storedCalories
        if (durationSeconds <= 0) return 0
        val weight = weightKg.coerceAtLeast(40f)
        val hours = durationSeconds / 3600f
        return (metForCardio(exerciseName) * weight * hours).toInt().coerceAtLeast(1)
    }

    fun estimateStrengthCalories(completedSets: List<ExerciseSet>, weightKg: Float): Int {
        if (completedSets.isEmpty()) return 0
        val bodyWeight = weightKg.coerceAtLeast(40f)
        val volumeCal = completedSets.sumOf { set ->
            val volume = set.weight * set.reps
            when {
                volume > 0 -> (volume * 0.05).toInt().coerceAtLeast(3)
                set.reps > 0 -> (set.reps * 0.4).toInt().coerceAtLeast(2)
                else -> 5
            }
        }
        val durationMinutes = completedSets.size * 3f
        val metCal = (5.0 * bodyWeight * durationMinutes / 60.0).toInt()
        return (volumeCal + metCal).coerceAtLeast(completedSets.size * 4)
    }

    fun estimateBurn(completedSets: List<ExerciseSet>, profile: UserProfile): Int {
        if (completedSets.isEmpty()) return 0
        val weightKg = profile.currentWeight.coerceAtLeast(40f)
        return completedSets
            .groupBy { "${it.exerciseName}|${it.exerciseType}" }
            .values
            .sumOf { sets ->
                val name = sets.first().exerciseName
                val type = sets.first().exerciseType
                if (WorkoutExerciseKind.isCardioType(type) ||
                    sets.all { it.durationSeconds > 0 && it.weight == 0f && it.reps == 0 }
                ) {
                    sets.sumOf { set ->
                        estimateCardioCalories(
                            durationSeconds = set.durationSeconds,
                            weightKg = weightKg,
                            exerciseName = name,
                            storedCalories = set.caloriesBurned
                        )
                    }
                } else {
                    estimateStrengthCalories(sets, weightKg)
                }
            }
    }

    /** Weekly calorie burn target heuristic from profile maintenance calories. */
    fun weeklyBurnTarget(profile: UserProfile): Int {
        val maintenance = profile.maintenanceCalories.coerceAtLeast(profile.dailyCalories)
        return ((maintenance * 0.15f) * profile.workoutDaysPerWeek.coerceIn(3, 7) / 7f).toInt()
            .coerceIn(800, 4000)
    }
}
