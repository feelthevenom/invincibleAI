package com.example.data

import android.content.Context
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

@JsonClass(generateAdapter = true)
data class BundledFoodEntry(
    val name: String,
    val cuisines: List<String> = emptyList(),
    val region: String = "generic",
    @Json(name = "caloriesPer100g") val caloriesPer100g: Int,
    val protein: Float,
    val carbs: Float,
    val fat: Float,
    val fiber: Float,
    val aliases: List<String> = emptyList(),
    val popularity: Int = 50
)

@JsonClass(generateAdapter = true)
data class BundledFoodsFile(
    val version: Int = 1,
    val foods: List<BundledFoodEntry> = emptyList()
)

class LocalFoodRepository(context: Context) {

    private val allFoods: List<FoodItem> by lazy {
        loadFoods(context.applicationContext)
    }

    private val searchEngine: FoodSearchEngine by lazy {
        FoodSearchEngine(allFoods)
    }

    fun search(query: String, cuisinePreferences: String): List<FoodItem> {
        val cuisines = CuisineTypes.parsePreferences(cuisinePreferences)
        return searchEngine.search(query, cuisines)
    }

    fun suggestions(cuisinePreferences: String): List<FoodItem> {
        val cuisines = CuisineTypes.parsePreferences(cuisinePreferences)
        return searchEngine.suggestions(cuisines)
    }

    private fun loadFoods(context: Context): List<FoodItem> {
        return try {
            val json = context.assets.open("foods.json").bufferedReader().use { it.readText() }
            val adapter = Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()
                .adapter(BundledFoodsFile::class.java)
            adapter.fromJson(json)?.foods.orEmpty().mapIndexed { index, entry ->
                FoodItem(
                    id = "local_${entry.name.hashCode()}_$index",
                    name = entry.name,
                    caloriesPer100g = entry.caloriesPer100g,
                    proteinPer100g = entry.protein,
                    carbsPer100g = entry.carbs,
                    fatPer100g = entry.fat,
                    fiberPer100g = entry.fiber,
                    isCustom = false,
                    cuisineTags = entry.cuisines,
                    region = entry.region,
                    aliases = entry.aliases,
                    popularity = entry.popularity
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
