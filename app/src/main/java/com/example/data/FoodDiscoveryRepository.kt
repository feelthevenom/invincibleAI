package com.example.data

/**
 * Hybrid food discovery: local fuzzy search → cached products → online AI parse (cache miss).
 */
class FoodDiscoveryRepository(
    private val repository: GymRepository,
    private val localFoodRepository: LocalFoodRepository,
    private val aiManager: AiManager,
    private val foodImageFetcher: FoodImageFetcher = FoodImageFetcher()
) {

    data class SearchResult(
        val local: List<FoodItem> = emptyList(),
        val cached: List<FoodItem> = emptyList(),
        val aiLookup: List<FoodItem> = emptyList()
    )

    suspend fun search(
        query: String,
        cuisinePreferences: String,
        customFoods: List<CustomFoodItem>,
        onlineProvider: String,
        onlineModelId: String
    ): SearchResult {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return SearchResult()

        val prefs = cuisinePreferences
        val bundled = localFoodRepository.search(trimmed, prefs)
        val custom = searchCustomFoods(trimmed, customFoods)
        val local = (custom + bundled).distinctBy { it.id }

        val cached = searchCached(trimmed)

        val hasStrongMatch = local.isNotEmpty() || cached.isNotEmpty()
        val aiLookup = if (!hasStrongMatch && trimmed.length >= 4 && onlineProvider != "offline") {
            lookupWithOnlineAi(trimmed, onlineProvider, onlineModelId)
        } else emptyList()

        return SearchResult(local = local, cached = cached, aiLookup = aiLookup)
    }

    private fun searchCustomFoods(query: String, customFoods: List<CustomFoodItem>): List<FoodItem> =
        customFoods
            .mapNotNull { cf ->
                val food = FoodNutritionCalculator.fromCustomEntity(cf)
                val score = FuzzyMatcher.score(query, food.name)
                if (score >= 80) food to score else null
            }
            .sortedByDescending { it.second }
            .map { it.first }

    private suspend fun searchCached(query: String): List<FoodItem> {
        val all = repository.getAllCachedFoodProducts()
        return all
            .mapNotNull { product ->
                val score = maxOf(
                    FuzzyMatcher.score(query, product.name),
                    FuzzyMatcher.score(query, product.searchKey),
                    if (product.brand.isNotBlank()) FuzzyMatcher.score(query, product.brand) else 0
                )
                if (score >= 80) product to score else null
            }
            .sortedByDescending { it.second }
            .take(20)
            .map { CachedFoodProductMapper.toFoodItem(it.first) }
    }

    private suspend fun lookupWithOnlineAi(
        query: String,
        provider: String,
        modelId: String
    ): List<FoodItem> {
        val item = aiManager.lookupPackagedProduct(query, provider, modelId) ?: return emptyList()
        val enriched = enrichWithImageIfMissing(item)
        repository.cacheFoodProduct(CachedFoodProductMapper.fromFoodItem(enriched, query))
        return listOf(enriched)
    }

    private suspend fun enrichWithImageIfMissing(food: FoodItem): FoodItem {
        if (!food.imageUrl.isNullOrBlank()) return food
        val key = FoodImageResolver.normalizeName(food.name)
        repository.getFoodImageCache(key)?.imageUrl?.takeIf { it.isNotBlank() }?.let { cachedUrl ->
            return food.copy(imageUrl = cachedUrl)
        }
        val fetched = foodImageFetcher.fetchFromInternet(food.name) ?: return food
        repository.cacheFoodImage(key, fetched.url, fetched.source)
        return food.copy(imageUrl = fetched.url)
    }
}
