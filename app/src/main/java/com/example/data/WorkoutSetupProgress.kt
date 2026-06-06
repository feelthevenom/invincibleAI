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

    /** Heuristic: profile clearly came from a completed onboarding. */
    fun looksOnboarded(profile: UserProfile): Boolean =
        profile.onboardingComplete ||
            (profile.age > 0 && profile.gender.isNotBlank() && profile.dailyCalories > 0)

    fun profileAfterRestore(profile: UserProfile): UserProfile {
        val onboardingDone = looksOnboarded(profile)
        val setupDone = profile.workoutSetupComplete || firstIncompleteStep(profile) > 6
        return profile.copy(
            onboardingComplete = onboardingDone,
            workoutSetupComplete = setupDone
        )
    }

    fun mergeWithSettingsBackup(db: UserProfile?, settings: UserProfile): UserProfile {
        val base = db ?: settings
        return profileAfterRestore(
            base.copy(
                onboardingComplete = base.onboardingComplete || settings.onboardingComplete,
                workoutSetupComplete = base.workoutSetupComplete || settings.workoutSetupComplete,
                age = if (base.age > 0) base.age else settings.age,
                gender = base.gender.ifBlank { settings.gender },
                height = if (base.height > 0) base.height else settings.height,
                dailyCalories = if (base.dailyCalories > 0) base.dailyCalories else settings.dailyCalories,
                protein = if (base.protein > 0) base.protein else settings.protein,
                carbs = if (base.carbs > 0) base.carbs else settings.carbs,
                fat = if (base.fat > 0) base.fat else settings.fat,
                fiber = if (base.fiber > 0) base.fiber else settings.fiber,
                currentWeight = if (base.currentWeight > 0f) base.currentWeight else settings.currentWeight,
                targetWeight = if (base.targetWeight > 0f) base.targetWeight else settings.targetWeight,
                fitnessLevel = base.fitnessLevel.ifBlank { settings.fitnessLevel },
                equipmentSelection = base.equipmentSelection.ifBlank { settings.equipmentSelection },
                gymLocation = base.gymLocation.ifBlank { settings.gymLocation },
                workoutDaysPerWeek = if (base.workoutDaysPerWeek in 3..7) base.workoutDaysPerWeek else settings.workoutDaysPerWeek,
                weekStartDay = base.weekStartDay.takeIf { it in Calendar.SUNDAY..Calendar.SATURDAY } ?: settings.weekStartDay
            )
        )
    }
}
