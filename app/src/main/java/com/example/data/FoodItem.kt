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
    /** When true, nutrition values are per 100 ml (liquids). */
    val volumeBased: Boolean = false,
    val cuisineTags: List<String> = emptyList(),
    val region: String = "generic",
    val aliases: List<String> = emptyList(),
    val popularity: Int = 50,
    val source: String = "local",
    val imageUrl: String? = null
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

    fun fromCustomEntity(entity: CustomFoodItem): FoodItem {
        val item = FoodItem(
            id = "custom_${entity.id}",
            name = entity.name,
            caloriesPer100g = entity.caloriesPer100g,
            proteinPer100g = entity.proteinPer100g,
            carbsPer100g = entity.carbsPer100g,
            fatPer100g = entity.fatPer100g,
            fiberPer100g = entity.fiberPer100g,
            isCustom = true,
            imageUrl = entity.imageUrl.takeIf { it.isNotBlank() }
        )
        return item.copy(volumeBased = FoodServingCatalog.isVolumeBased(item))
    }

    /** Derive per-100g values from a logged meal entry so weight edits can recalculate macros. */
    fun per100FromMealEntry(entry: MealEntry): FoodItem? {
        if (entry.weightGrams <= 0) return null
        val factor = 100f / entry.weightGrams
        return FoodItem(
            name = entry.foodName,
            caloriesPer100g = (entry.calories * factor).roundToInt(),
            proteinPer100g = entry.protein * factor,
            carbsPer100g = entry.carbs * factor,
            fatPer100g = entry.fat * factor,
            fiberPer100g = entry.fiber * factor
        )
    }

    fun recalculateEntryForWeight(entry: MealEntry, weightGrams: Int): MealEntry? {
        if (weightGrams <= 0) return null
        val per100 = per100FromMealEntry(entry) ?: return null
        val nutrition = nutritionForWeight(per100, weightGrams)
        return entry.copy(
            weightGrams = weightGrams,
            calories = nutrition.calories,
            protein = nutrition.protein,
            carbs = nutrition.carbs,
            fat = nutrition.fat,
            fiber = nutrition.fiber
        )
    }

    fun recalculateEntryForServing(
        entry: MealEntry,
        food: FoodItem,
        weightGrams: Int,
        servingLabel: String,
        servingQuantity: Float
    ): MealEntry? {
        if (weightGrams <= 0) return null
        val nutrition = nutritionForWeight(food, weightGrams)
        return entry.copy(
            weightGrams = weightGrams,
            servingLabel = servingLabel,
            servingQuantity = servingQuantity,
            calories = nutrition.calories,
            protein = nutrition.protein,
            carbs = nutrition.carbs,
            fat = nutrition.fat,
            fiber = nutrition.fiber
        )
    }
}
