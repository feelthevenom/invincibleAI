package com.example.data.api

import com.example.data.FoodItem

/**
 * Repository that wraps the Open Food Facts API and converts responses
 * into our domain [FoodItem] model. Filters out products with missing
 * or zero nutrition data.
 */
class OpenFoodFactsRepository(
    private val api: OpenFoodFactsApi = OpenFoodFactsApi.create()
) {

    /**
     * Searches Open Food Facts for [query]. Returns up to [limit] items
     * that have valid nutrition data. Returns empty list on any error
     * (network, parsing, 503, etc.) — never throws.
     */
    suspend fun search(query: String, limit: Int = 20): List<FoodItem> {
        return try {
            val response = api.search(searchTerms = query, pageSize = limit)
            response.products
                .filter { it.isUsable() }
                .map { it.toFoodItem() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun OffProduct.isUsable(): Boolean {
        if (productName.isNullOrBlank()) return false
        val n = nutriments ?: return false
        // Must have at least calories to be useful
        return (n.energyKcal100g ?: 0.0) > 0
    }

    private fun OffProduct.toFoodItem(): FoodItem {
        val n = nutriments ?: OffNutriments()
        val displayName = buildString {
            append(productName?.trim() ?: "Unknown")
            if (!brands.isNullOrBlank()) {
                append(" (${brands.trim()})")
            }
        }
        return FoodItem(
            id = "off_$code",
            name = displayName,
            caloriesPer100g = (n.energyKcal100g ?: 0.0).toInt(),
            proteinPer100g = (n.proteins100g ?: 0.0).toFloat(),
            carbsPer100g = (n.carbohydrates100g ?: 0.0).toFloat(),
            fatPer100g = (n.fat100g ?: 0.0).toFloat(),
            fiberPer100g = (n.fiber100g ?: 0.0).toFloat(),
            isCustom = false,
            source = "openfoodfacts"
        )
    }
}
