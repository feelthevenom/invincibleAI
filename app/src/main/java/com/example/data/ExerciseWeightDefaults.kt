package com.example.data

import kotlin.math.roundToInt

/** Estimates starting working-set weight from profile, exercise, and history. */
object ExerciseWeightDefaults {
    fun resolveWeightKg(
        exerciseName: String,
        exerciseType: String,
        profile: UserProfile,
        setNumber: Int = 1,
        lastSetWeight: Float = 0f,
        lastRoutineWeight: Float = 0f,
        lastGlobalWeight: Float = 0f,
        templateWeight: Float = 0f
    ): Float {
        lastSetWeight.takeIf { it > 0f }?.let { return it }
        lastRoutineWeight.takeIf { it > 0f }?.let { return it }
        lastGlobalWeight.takeIf { it > 0f }?.let { return it }
        templateWeight.takeIf { it > 0f }?.let { return it }
        return estimatedWeightKg(exerciseName, exerciseType, profile, setNumber)
    }

    fun estimatedWeightKg(
        exerciseName: String,
        exerciseType: String,
        profile: UserProfile,
        setNumber: Int = 1
    ): Float {
        val levelFactor = when (profile.fitnessLevel) {
            "Beginner" -> 0.65f
            "Intermediate" -> 1f
            "Advanced" -> 1.15f
            "Elite" -> 1.25f
            else -> 0.85f
        }
        val bw = profile.currentWeight.coerceIn(45f, 150f)
        val name = exerciseName.lowercase()
        val type = exerciseType.lowercase()

        val fromBenchmark = benchmarkWorkingWeight(name, profile)
        if (fromBenchmark > 0f) return roundWeight(fromBenchmark)

        val ratio = when {
            "squat" in name -> 0.75f
            "deadlift" in name -> 0.85f
            "bench" in name -> 0.55f
            "press" in name && "leg" !in name -> 0.45f
            "row" in name -> 0.4f
            "pull" in name || "lat" in name -> 0.35f
            "curl" in name -> 0.12f
            "extension" in name -> 0.15f
            "raise" in name || "fly" in name -> 0.1f
            "leg" in type || "glute" in type -> 0.5f
            "back" in type -> 0.38f
            "chest" in type -> 0.32f
            "shoulder" in type -> 0.22f
            "bicep" in type || "tricep" in type || "arm" in type -> 0.14f
            else -> 0.2f
        }

        val setFactor = 1f - (setNumber - 1) * 0.03f
        return roundWeight(bw * ratio * levelFactor * setFactor.coerceAtLeast(0.85f))
    }

    private fun benchmarkWorkingWeight(exerciseName: String, profile: UserProfile): Float {
        val name = exerciseName.lowercase()
        return when {
            profile.squat1RmKg > 0f && "squat" in name -> profile.squat1RmKg * 0.75f
            profile.benchPress1RmKg > 0f && ("bench" in name || ("press" in name && "leg" !in name)) ->
                profile.benchPress1RmKg * 0.75f
            profile.deadlift1RmKg > 0f && "deadlift" in name -> profile.deadlift1RmKg * 0.75f
            else -> 0f
        }
    }

    private fun roundWeight(kg: Float): Float {
        val step = if (kg < 20f) 1f else 2.5f
        return ((kg / step).roundToInt() * step).coerceAtLeast(2.5f)
    }
}
