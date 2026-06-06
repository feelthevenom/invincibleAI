package com.example.data

import java.util.Calendar

/** Determines which workout-setup steps still need user input (e.g. after restore). */
object WorkoutSetupProgress {
    const val TOTAL_STEPS = 8

    fun hasAnyBenchmark(profile: UserProfile): Boolean =
        profile.squat1RmKg > 0f || profile.benchPress1RmKg > 0f || profile.deadlift1RmKg > 0f

    fun firstIncompleteStep(profile: UserProfile): Int {
        if (profile.fitnessLevel.isBlank()) return 1
        if (!profile.benchmarkSkipped && !hasAnyBenchmark(profile)) return 2
        if (profile.workoutDaysPerWeek !in 3..7) return 3
        if (profile.weekStartDay !in Calendar.SUNDAY..Calendar.SATURDAY) return 4
        if (WorkoutPreferences.parseEquipment(profile.equipmentSelection).isEmpty()) return 5
        if (profile.gymLocation.isBlank()) return 6
        return 7
    }

    fun isSetupComplete(profile: UserProfile): Boolean =
        profile.onboardingComplete && firstIncompleteStep(profile) > 6 && profile.workoutSetupComplete

    fun profileAfterRestore(profile: UserProfile): UserProfile =
        profile.copy(workoutSetupComplete = firstIncompleteStep(profile) > 6)
}
