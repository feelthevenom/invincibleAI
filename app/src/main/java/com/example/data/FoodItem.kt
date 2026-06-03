package com.example.data

import kotlin.math.roundToInt

data class FoodItem(
    val id: String = "",
    val name: String,
    val caloriesPer100g: Int,
    val proteinPer100g: Float,
    val carbsPer100g: Float,
    val fatPer100g: Float,
    val fiberPer100g: Float,
    val isCustom: Boolean = false,
    val cuisineTags: List<String> = emptyList(),
    val region: String = "generic",
    val aliases: List<String> = emptyList(),
    val popularity: Int = 50,
    val source: String = "local"
)

data class FoodNutrition(
    val calories: Int,
    val protein: Int,
    val carbs: Int,
    val fat: Int,
    val fiber: Int
)

object FoodNutritionCalculator {

    fun nutritionForWeight(food: FoodItem, grams: Int): FoodNutrition {
        val factor = grams / 100f
        return FoodNutrition(
            calories = (food.caloriesPer100g * factor).roundToInt(),
            protein = (food.proteinPer100g * factor).roundToInt(),
            carbs = (food.carbsPer100g * factor).roundToInt(),
            fat = (food.fatPer100g * factor).roundToInt(),
            fiber = (food.fiberPer100g * factor).roundToInt()
        )
    }

    fun fromCustomEntity(entity: CustomFoodItem): FoodItem = FoodItem(
        id = "custom_${entity.id}",
        name = entity.name,
        caloriesPer100g = entity.caloriesPer100g,
        proteinPer100g = entity.proteinPer100g,
        carbsPer100g = entity.carbsPer100g,
        fatPer100g = entity.fatPer100g,
        fiberPer100g = entity.fiberPer100g,
        isCustom = true
    )
}
