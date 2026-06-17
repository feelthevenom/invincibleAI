package com.example.data

/** Resolves food product image URLs (explicit URL, cached). */
object FoodImageResolver {

    fun resolveImmediate(food: FoodItem): String? {
        food.imageUrl?.takeIf { it.isNotBlank() }?.let { return it }
        if (food.isCustom) return null
        if (food.id.startsWith("cached_")) return food.imageUrl
        return null
    }

    fun normalizeName(name: String): String =
        name.lowercase().replace(Regex("\\([^)]*\\)"), "").trim()
}
