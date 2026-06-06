package com.example.data

object WaterGoalCalculator {
    const val MIN_GOAL_ML = 1000
    const val MAX_GOAL_ML = 5000
    const val ML_PER_GLASS = 250
    const val DEFAULT_QUICK_ADD_ML = ML_PER_GLASS

    /** ~35 ml per kg body weight, clamped to a healthy daily range. */
    fun suggestedGoalMl(profile: UserProfile): Int {
        val weightKg = profile.currentWeight.coerceAtLeast(40f)
        val activityBoost = when {
            profile.activityLevel.contains("Very", ignoreCase = true) -> 400
            profile.activityLevel.contains("Active", ignoreCase = true) -> 250
            else -> 0
        }
        return (weightKg * 35f + activityBoost).toInt().coerceIn(MIN_GOAL_ML, MAX_GOAL_ML)
    }

    fun effectiveGoalMl(profile: UserProfile): Int =
        profile.dailyWaterGoalMl.takeIf { it in MIN_GOAL_ML..MAX_GOAL_ML }
            ?: suggestedGoalMl(profile)

    fun effectiveGoalGlasses(profile: UserProfile): Int =
        (effectiveGoalMl(profile) / ML_PER_GLASS).coerceAtLeast(1)

    fun suggestedGoalGlasses(profile: UserProfile): Int =
        (suggestedGoalMl(profile) / ML_PER_GLASS).coerceAtLeast(1)

    fun validateGoalMl(goalMl: Int): String? = when {
        goalMl < MIN_GOAL_ML -> "Minimum recommended goal is ${MIN_GOAL_ML / ML_PER_GLASS} glasses (${MIN_GOAL_ML / 1000f}L) per day."
        goalMl > MAX_GOAL_ML -> "Maximum safe daily goal is ${MAX_GOAL_ML / ML_PER_GLASS} glasses (${MAX_GOAL_ML / 1000f}L). Consult a doctor for higher intake."
        else -> null
    }

    fun validateGoalGlasses(glasses: Int): String? = validateGoalMl(glasses * ML_PER_GLASS)

    fun glassesFromMl(ml: Int): Int = kotlin.math.round(ml / ML_PER_GLASS.toFloat()).toInt()

    fun formatLiters(ml: Int): String = String.format("%.1f", ml / 1000f)

    fun formatLitersFromMl(totalMl: Int): String = String.format("%.1f", totalMl / 1000f)
}
