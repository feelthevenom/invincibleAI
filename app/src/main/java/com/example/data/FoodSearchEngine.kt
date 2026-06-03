package com.example.data

/**
 * Smart in-memory search engine for local food items.
 *
 * Ranking strategy (higher = better):
 *  1. Exact name match               → +1000
 *  2. Name starts with query          → +500
 *  3. Word in name starts with query  → +200
 *  4. Name contains query             → +100
 *  5. Alias exact match               → +400
 *  6. Alias starts with query         → +300
 *  7. Alias contains query            → +80
 *  8. Cuisine preference bonus        → +150
 *  9. Popularity score                → +popularity (0-100)
 */
class FoodSearchEngine(private val allFoods: List<FoodItem>) {

    /** Returns top suggestions based on user's cuisine preferences, sorted by popularity. */
    fun suggestions(cuisinePreferences: List<String>, limit: Int = 20): List<FoodItem> {
        if (cuisinePreferences.isEmpty()) {
            return allFoods.sortedByDescending { it.popularity }.take(limit)
        }
        val preferred = allFoods.filter { food ->
            food.cuisineTags.any { it in cuisinePreferences }
        }.sortedByDescending { it.popularity }

        val remaining = if (preferred.size < limit) {
            allFoods.filter { food ->
                food.cuisineTags.none { it in cuisinePreferences }
            }.sortedByDescending { it.popularity }.take(limit - preferred.size)
        } else emptyList()

        return (preferred.take(limit) + remaining).take(limit)
    }

    /** Search foods by query with smart ranking. */
    fun search(query: String, cuisinePreferences: List<String>, limit: Int = 40): List<FoodItem> {
        val q = query.trim().lowercase()
        if (q.isBlank()) return suggestions(cuisinePreferences)

        val scored = allFoods.mapNotNull { food ->
            val score = computeScore(food, q, cuisinePreferences)
            if (score > 0) ScoredFood(food, score) else null
        }

        return scored
            .sortedByDescending { it.score }
            .take(limit)
            .map { it.food }
    }

    private fun computeScore(food: FoodItem, query: String, cuisinePreferences: List<String>): Int {
        val nameLower = food.name.lowercase()
        var score = 0

        // Name matching
        when {
            nameLower == query -> score += 1000
            nameLower.startsWith(query) -> score += 500
            else -> {
                // Check if any word in the name starts with the query
                val words = nameLower.split(" ", "(", ")", "/", "-")
                if (words.any { it.startsWith(query) }) {
                    score += 200
                } else if (nameLower.contains(query)) {
                    score += 100
                }
            }
        }

        // Alias matching
        if (score == 0 || score < 400) {
            for (alias in food.aliases) {
                val aliasLower = alias.lowercase()
                when {
                    aliasLower == query -> {
                        score = maxOf(score, 400)
                        break
                    }
                    aliasLower.startsWith(query) -> {
                        score = maxOf(score, 300)
                    }
                    aliasLower.contains(query) -> {
                        score = maxOf(score, 80)
                    }
                }
            }
        }

        // No match at all
        if (score == 0) return 0

        // Cuisine preference bonus
        if (cuisinePreferences.isNotEmpty() && food.cuisineTags.any { it in cuisinePreferences }) {
            score += 150
        }

        // Popularity bonus (0-100)
        score += food.popularity

        return score
    }

    private data class ScoredFood(val food: FoodItem, val score: Int)
}
