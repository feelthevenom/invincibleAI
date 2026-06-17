package com.example.data

object CachedFoodProductMapper {

    fun toFoodItem(product: CachedFoodProduct): FoodItem = FoodItem(
        id = "cached_${product.id}",
        name = product.name,
        caloriesPer100g = product.caloriesPer100g,
        proteinPer100g = product.proteinPer100g,
        carbsPer100g = product.carbsPer100g,
        fatPer100g = product.fatPer100g,
        fiberPer100g = product.fiberPer100g,
        volumeBased = product.volumeBased,
        source = product.source,
        imageUrl = product.imageUrl.takeIf { it.isNotBlank() }
    )

    fun fromFoodItem(food: FoodItem, searchQuery: String): CachedFoodProduct = CachedFoodProduct(
        name = food.name,
        searchKey = FuzzyMatcher.normalize("$searchQuery ${food.name}"),
        brand = extractBrand(food.name),
        caloriesPer100g = food.caloriesPer100g,
        proteinPer100g = food.proteinPer100g,
        carbsPer100g = food.carbsPer100g,
        fatPer100g = food.fatPer100g,
        fiberPer100g = food.fiberPer100g,
        volumeBased = food.volumeBased,
        source = food.source,
        externalId = food.id.removePrefix("off_").removePrefix("cached_").removePrefix("ai_lookup_"),
        imageUrl = food.imageUrl.orEmpty()
    )

    private fun extractBrand(name: String): String {
        val paren = Regex("\\(([^)]+)\\)").find(name)?.groupValues?.getOrNull(1)
        return paren?.trim().orEmpty()
    }
}
