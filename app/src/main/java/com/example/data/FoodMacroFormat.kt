package com.example.data

import java.util.Locale

object FoodMacroFormat {
    fun grams(value: Float): String = String.format(Locale.US, "%.2f", value)

    fun per100gLine(
        calories: Int,
        protein: Float,
        carbs: Float,
        fat: Float,
        fiber: Float
    ): String =
        "$calories kcal · Protein ${grams(protein)}g · Carbs ${grams(carbs)}g · " +
            "Fat ${grams(fat)}g · Fiber ${grams(fiber)}g"

    fun compactPcf(protein: Float, carbs: Float, fat: Float): String =
        "P${grams(protein)}g C${grams(carbs)}g F${grams(fat)}g"
}
