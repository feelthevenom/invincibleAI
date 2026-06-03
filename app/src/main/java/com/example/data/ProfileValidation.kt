package com.example.data

import kotlin.math.abs
import kotlin.math.roundToInt

object ProfileValidation {

    const val MIN_AGE = 13
    const val MAX_AGE = 100
    const val MIN_HEIGHT_CM = 100
    const val MAX_HEIGHT_CM = 250
    const val MIN_WEIGHT_KG = 30f
    const val MAX_WEIGHT_KG = 300f
    const val MIN_WEEKLY_CHANGE = 0.1f
    const val MAX_WEEKLY_LOSS_KG = 1.0f
    const val MAX_WEEKLY_GAIN_KG = 0.5f

    fun cmFromFeetInches(feet: Int, inches: Int): Int =
        ((feet * 12 + inches) * 2.54).roundToInt()

    fun feetInchesFromCm(cm: Int): Pair<Int, Int> {
        val totalInches = (cm / 2.54).roundToInt()
        return totalInches / 12 to totalInches % 12
    }

    fun bmi(weightKg: Float, heightCm: Int): Float {
        if (heightCm <= 0) return 0f
        val m = heightCm / 100f
        return weightKg / (m * m)
    }

    fun minReasonableWeight(heightCm: Int): Float {
        val heightM = heightCm / 100f
        return (16f * heightM * heightM).coerceAtLeast(MIN_WEIGHT_KG)
    }

    fun maxReasonableWeight(heightCm: Int): Float {
        val heightM = heightCm / 100f
        return (35f * heightM * heightM).coerceAtMost(MAX_WEIGHT_KG)
    }

    fun isValidAge(age: Int): Boolean = age in MIN_AGE..MAX_AGE
    fun isValidHeightCm(cm: Int): Boolean = cm in MIN_HEIGHT_CM..MAX_HEIGHT_CM

    fun isValidWeight(weightKg: Float, heightCm: Int): Boolean =
        weightKg in MIN_WEIGHT_KG..MAX_WEIGHT_KG &&
            weightKg in minReasonableWeight(heightCm)..maxReasonableWeight(heightCm)

    fun isValidTargetWeight(targetKg: Float, currentKg: Float, heightCm: Int, age: Int): Boolean {
        if (targetKg !in minReasonableWeight(heightCm)..maxReasonableWeight(heightCm)) return false
        if (abs(targetKg - currentKg) > 80f) return false
        return true
    }

    /** Max safe weekly change based on goal and body weight (~1% rule for loss). */
    fun maxWeeklyChangeKg(goal: String, currentWeightKg: Float): Float = when (goal) {
        "Lose Fat" -> minOf(MAX_WEEKLY_LOSS_KG, currentWeightKg * 0.01f).coerceAtLeast(MIN_WEEKLY_CHANGE)
        "Gain Muscle" -> MAX_WEEKLY_GAIN_KG
        "Body Recomposition" -> 0.3f
        "Maintain Weight" -> 0f
        "Athletic Performance" -> 0.25f
        "General Fitness" -> 0.2f
        else -> 0.5f
    }

    fun minWeeklyChangeKg(goal: String): Float = when (goal) {
        "Maintain Weight" -> 0f
        else -> MIN_WEEKLY_CHANGE
    }

    data class WeeklyChangeValidation(val isValid: Boolean, val message: String? = null)

    fun validateWeeklyChange(goal: String, currentWeightKg: Float, changeKg: Float): WeeklyChangeValidation {
        if (goal == "Maintain Weight") {
            return if (changeKg == 0f) WeeklyChangeValidation(true)
            else WeeklyChangeValidation(false, "Maintain goal requires 0 kg/week change.")
        }
        val min = minWeeklyChangeKg(goal)
        val max = maxWeeklyChangeKg(goal, currentWeightKg)
        return when {
            changeKg < min -> WeeklyChangeValidation(
                false,
                "Minimum safe rate is ${String.format("%.1f", min)} kg/week."
            )
            changeKg > max -> WeeklyChangeValidation(
                false,
                "Maximum safe rate is ${String.format("%.1f", max)} kg/week (≈1% body weight/week for loss)."
            )
            else -> WeeklyChangeValidation(true)
        }
    }

    /** ~7700 kcal ≈ 1 kg fat; daily deficit needed for weekly change. */
    fun estimatedDailyDeficitForWeeklyChange(weeklyChangeKg: Float): Int =
        (abs(weeklyChangeKg) * 7700 / 7).roundToInt()
}
