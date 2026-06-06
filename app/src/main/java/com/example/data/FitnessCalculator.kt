package com.example.data

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

object FitnessCalculator {

    val activityLevels = listOf(
        "Sedentary",
        "Lightly Active",
        "Moderately Active",
        "Very Active",
        "Extra Active"
    )

    val goals = listOf(
        "Lose Fat",
        "Gain Muscle",
        "Body Recomposition",
        "Maintain Weight",
        "Athletic Performance",
        "General Fitness"
    )

    val genders = listOf("Male", "Female")

    private fun activityMultiplier(level: String): Double = when (level) {
        "Sedentary" -> 1.2
        "Lightly Active" -> 1.375
        "Moderately Active" -> 1.55
        "Very Active" -> 1.725
        "Extra Active" -> 1.9
        else -> 1.375
    }

    /** Devine formula for ideal body weight (kg). */
    fun idealWeightKg(gender: String, heightCm: Int): Float {
        val heightInches = heightCm / 2.54
        val base = if (gender.equals("Female", ignoreCase = true)) 45.5 else 50.0
        return (base + 2.3 * (heightInches - 60)).toFloat().coerceAtLeast(40f)
    }

    fun calculateTargetWeight(
        gender: String,
        heightCm: Int,
        currentWeight: Float,
        goal: String
    ): Float {
        val ideal = idealWeightKg(gender, heightCm)
        return when (goal) {
            "Lose Fat" -> {
                if (currentWeight > ideal) {
                    max(currentWeight * 0.92f, ideal)
                } else {
                    currentWeight * 0.97f
                }
            }
            "Gain Muscle" -> {
                if (currentWeight < ideal) {
                    max(currentWeight * 1.05f, ideal * 0.98f)
                } else {
                    currentWeight * 1.03f
                }
            }
            "Body Recomposition" -> ideal
            "Maintain Weight" -> currentWeight
            "Athletic Performance" -> ideal * 1.02f
            "General Fitness" -> ideal
            else -> ideal
        }.roundTo1Decimal()
    }

    fun bmr(gender: String, weightKg: Float, heightCm: Int, age: Int): Double {
        val base = 10.0 * weightKg + 6.25 * heightCm - 5.0 * age
        return if (gender.equals("Female", ignoreCase = true)) base - 161 else base + 5
    }

    fun tdee(gender: String, weightKg: Float, heightCm: Int, age: Int, activityLevel: String): Int {
        return (bmr(gender, weightKg, heightCm, age) * activityMultiplier(activityLevel)).roundToInt()
    }

    fun dailyCalories(
        gender: String,
        weightKg: Float,
        heightCm: Int,
        age: Int,
        activityLevel: String,
        goal: String
    ): Int {
        val maintenance = tdee(gender, weightKg, heightCm, age, activityLevel)
        return when (goal) {
            "Lose Fat" -> (maintenance * 0.78).roundToInt()
            "Gain Muscle" -> maintenance + 300
            "Body Recomposition" -> (maintenance * 0.92).roundToInt()
            "Maintain Weight" -> maintenance
            "Athletic Performance" -> (maintenance * 1.05).roundToInt()
            "General Fitness" -> maintenance
            else -> maintenance
        }
    }

    data class MacroSplit(val protein: Int, val carbs: Int, val fat: Int, val fiber: Int)

    fun calculateMacros(weightKg: Float, dailyCalories: Int, goal: String): MacroSplit {
        val proteinPerKg = when (goal) {
            "Lose Fat" -> 2.2
            "Gain Muscle" -> 2.0
            "Body Recomposition" -> 2.1
            else -> 1.8
        }
        val protein = (weightKg * proteinPerKg).roundToInt()
        val proteinCal = protein * 4

        val fatPercent = when (goal) {
            "Lose Fat" -> 0.25
            "Gain Muscle" -> 0.30
            else -> 0.27
        }
        val fatCal = (dailyCalories * fatPercent).roundToInt()
        val fat = (fatCal / 9.0).roundToInt()

        val carbCal = max(0, dailyCalories - proteinCal - fat * 9)
        val carbs = (carbCal / 4.0).roundToInt()
        val fiber = (dailyCalories / 1000.0 * 14).roundToInt().coerceIn(20, 45)

        return MacroSplit(protein, carbs, fat, fiber)
    }

    enum class MacroField { PROTEIN, CARBS, FAT, FIBER }

    /**
     * Rebalance macros when one field is edited while keeping total calories fixed.
     * Protein/carbs/fat share calories (4/4/9 kcal per g). Fiber scales with carb intake.
     */
    fun rebalanceMacros(
        totalCalories: Int,
        protein: Int,
        carbs: Int,
        fat: Int,
        fiber: Int,
        changedField: MacroField,
        newValue: Int
    ): MacroSplit {
        var p = protein
        var c = carbs
        var f = fat
        var fib = fiber

        when (changedField) {
            MacroField.PROTEIN -> {
                p = newValue.coerceAtLeast(0)
                val remaining = max(0, totalCalories - p * 4)
                val oldCarbCal = c * 4.0
                val oldFatCal = f * 9.0
                val totalRemainingOld = oldCarbCal + oldFatCal
                if (totalRemainingOld > 0) {
                    val carbRatio = oldCarbCal / totalRemainingOld
                    c = ((remaining * carbRatio) / 4.0).roundToInt()
                    f = ((remaining * (1 - carbRatio)) / 9.0).roundToInt()
                } else {
                    c = (remaining * 0.55 / 4.0).roundToInt()
                    f = (remaining * 0.45 / 9.0).roundToInt()
                }
                fib = scaleFiber(fiber, carbs, c)
            }
            MacroField.CARBS -> {
                c = newValue.coerceAtLeast(0)
                val remaining = max(0, totalCalories - p * 4 - c * 4)
                f = (remaining / 9.0).roundToInt()
                fib = scaleFiber(fiber, carbs, c)
            }
            MacroField.FAT -> {
                f = newValue.coerceAtLeast(0)
                val remaining = max(0, totalCalories - p * 4 - f * 9)
                c = (remaining / 4.0).roundToInt()
                fib = scaleFiber(fiber, carbs, c)
            }
            MacroField.FIBER -> {
                fib = newValue.coerceIn(10, 60)
            }
        }

        return MacroSplit(p, c, f, fib)
    }

    private fun scaleFiber(originalFiber: Int, oldCarbs: Int, newCarbs: Int): Int {
        if (oldCarbs <= 0) return originalFiber
        return (originalFiber * (newCarbs.toDouble() / oldCarbs)).roundToInt().coerceIn(10, 60)
    }

    fun targetWeightChangePerWeek(goal: String): Float = when (goal) {
        "Lose Fat" -> 0.5f
        "Gain Muscle" -> 0.25f
        "Body Recomposition" -> 0.2f
        "Maintain Weight" -> 0f
        "Athletic Performance" -> 0.15f
        "General Fitness" -> 0.1f
        else -> 0.25f
    }

    fun weeksToReachGoal(currentWeight: Float, targetWeight: Float, weeklyChange: Float): Int {
        if (weeklyChange <= 0f) return 0
        val diff = abs(currentWeight - targetWeight)
        if (diff < 0.1f) return 0
        return (diff / weeklyChange).roundToInt().coerceAtLeast(1)
    }

    /** Daily calorie adjustment from weekly weight-change target (negative = deficit). */
    fun calorieAdjustmentFromWeeklyChange(goal: String, weeklyChangeKg: Float): Int {
        if (weeklyChangeKg <= 0f || goal == "Maintain Weight") return 0
        val daily = ProfileValidation.estimatedDailyDeficitForWeeklyChange(weeklyChangeKg)
        return when (goal) {
            "Lose Fat" -> -daily
            "Gain Muscle" -> daily
            "Body Recomposition" -> -(daily / 2)
            else -> -daily
        }
    }

    fun dailyCaloriesFromWeeklyChange(maintenanceCalories: Int, goal: String, weeklyChangeKg: Float): Int {
        val adjustment = calorieAdjustmentFromWeeklyChange(goal, weeklyChangeKg)
        return (maintenanceCalories + adjustment).coerceAtLeast(1200)
    }

    data class NutritionPlan(
        val maintenanceCalories: Int,
        val dailyCalories: Int,
        val calorieAdjustmentDaily: Int,
        val calorieAdjustmentWeekly: Int,
        val macros: MacroSplit
    )

    /** Recompute daily calories from TDEE + weekly weight-change rate and derive macros. */
    fun nutritionPlanFromProfile(profile: UserProfile): NutritionPlan {
        val maintenance = tdee(
            profile.gender, profile.currentWeight, profile.height, profile.age, profile.activityLevel
        )
        val weekly = profile.targetWeightChangePerWeek
        val dailyCal = if (weekly > 0f && profile.goal != "Maintain Weight") {
            dailyCaloriesFromWeeklyChange(maintenance, profile.goal, weekly)
        } else {
            profile.dailyCalories.takeIf { it >= 1200 } ?: dailyCalories(
                profile.gender, profile.currentWeight, profile.height, profile.age,
                profile.activityLevel, profile.goal
            )
        }
        val adjustment = dailyCal - maintenance
        val macros = calculateMacros(profile.currentWeight, dailyCal, profile.goal)
        return NutritionPlan(
            maintenanceCalories = maintenance,
            dailyCalories = dailyCal,
            calorieAdjustmentDaily = adjustment,
            calorieAdjustmentWeekly = adjustment * 7,
            macros = macros
        )
    }

    /** Daily calorie adjustment: negative = deficit, positive = surplus. */
    fun calorieAdjustmentDaily(maintenanceCalories: Int, targetCalories: Int): Int {
        return targetCalories - maintenanceCalories
    }

    private fun Float.roundTo1Decimal(): Float {
        return (this * 10).roundToInt() / 10f
    }
}
