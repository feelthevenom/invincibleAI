package com.example.data

import com.example.data.api.WikimediaImageApi

/**
 * 2-tier zero-cost food image pipeline:
 * 1. Wikimedia Commons (raw ingredients / CC0)
 * 2. Pollinations.ai (AI-generated fallback URL — cached by Coil)
 */
class FoodImageFetcher(
    private val wikimediaApi: WikimediaImageApi = WikimediaImageApi()
) {
    suspend fun fetchFromInternet(foodName: String): FoodImageResult? {
        val query = foodName.trim()
        if (query.isBlank()) return null

        val variants = FoodImageClassifier.expandNameVariants(query)

        for (q in variants) {
            wikimediaApi.searchFoodImage(q)?.let {
                return FoodImageResult(it, "wikimedia")
            }
        }

        return FoodImageResult(PollinationsImageUrl.build(query), "pollinations")
    }
}

data class FoodImageResult(
    val url: String,
    val source: String
)
