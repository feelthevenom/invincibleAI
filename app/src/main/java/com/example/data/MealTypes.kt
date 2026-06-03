package com.example.data

object MealTypes {
    const val MORNING_SNACKS = "Morning Snacks"
    const val BREAKFAST = "Breakfast"
    const val LUNCH = "Lunch"
    const val EVENING_SNACKS = "Evening Snacks"
    const val DINNER = "Dinner"

    val ALL = listOf(MORNING_SNACKS, BREAKFAST, LUNCH, EVENING_SNACKS, DINNER)

    /** Share of daily calories/macros allocated to each meal. */
    private val calorieShare = mapOf(
        MORNING_SNACKS to 0.10,
        BREAKFAST to 0.25,
        LUNCH to 0.30,
        EVENING_SNACKS to 0.10,
        DINNER to 0.25
    )

    data class MealBudget(
        val mealType: String,
        val calories: Int,
        val protein: Int,
        val carbs: Int,
        val fat: Int,
        val fiber: Int
    )

    fun budgetForMeal(mealType: String, profile: UserProfile): MealBudget {
        val share = calorieShare[mealType] ?: 0.20
        return MealBudget(
            mealType = mealType,
            calories = (profile.dailyCalories * share).toInt().coerceAtLeast(1),
            protein = (profile.protein * share).toInt().coerceAtLeast(1),
            carbs = (profile.carbs * share).toInt().coerceAtLeast(1),
            fat = (profile.fat * share).toInt().coerceAtLeast(1),
            fiber = (profile.fiber * share).toInt().coerceAtLeast(1)
        )
    }

    fun allBudgets(profile: UserProfile): List<MealBudget> =
        ALL.map { budgetForMeal(it, profile) }
}
