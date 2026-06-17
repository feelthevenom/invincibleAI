package com.example.data.api

import com.example.data.FoodImageClassifier
import com.example.data.FuzzyMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Fetches food-related images from Wikimedia Commons (free, no API key).
 * Used as fallback when Open Food Facts has no relevant product photo.
 */
class WikimediaImageApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    suspend fun searchFoodImage(query: String): String? = withContext(Dispatchers.IO) {
        val term = query.trim()
        if (term.isBlank()) return@withContext null
        val searches = buildList {
            add(term)
            if (FoodImageClassifier.isPreparedDish(term)) {
                add("$term dish")
                add("$term food")
            } else {
                add("$term food")
            }
        }.distinct()
        var bestUrl: String? = null
        var bestScore = 0
        for (search in searches) {
            val candidate = fetchBestImageUrl(search, term) ?: continue
            val score = FuzzyMatcher.score(term, search) + 50
            if (score > bestScore) {
                bestScore = score
                bestUrl = candidate
            }
        }
        bestUrl
    }

    private fun fetchBestImageUrl(search: String, originalQuery: String): String? {
        return try {
            val encoded = URLEncoder.encode(search, Charsets.UTF_8.name())
            val url = "https://commons.wikimedia.org/w/api.php" +
                "?action=query&generator=search&gsrsearch=$encoded&gsrnamespace=6" +
                "&gsrlimit=12&prop=imageinfo|info&inprop=url&iiprop=url|mime&iiurlwidth=480&format=json"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return null
                pickBestImage(body, originalQuery)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun pickBestImage(json: String, query: String): String? {
        return try {
            val pages = JSONObject(json)
                .optJSONObject("query")
                ?.optJSONObject("pages") ?: return null
            var bestUrl: String? = null
            var bestScore = 0
            val keys = pages.keys()
            while (keys.hasNext()) {
                val page = pages.optJSONObject(keys.next()) ?: continue
                val title = page.optString("title", "").removePrefix("File:")
                val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
                val mime = info.optString("mime", "")
                if (!mime.startsWith("image/") || mime == "image/svg+xml") continue
                val score = FoodImageClassifier.wikimediaTitleScore(query, title)
                if (score < 55) continue
                val imageUrl = info.optString("thumburl", "").ifBlank { info.optString("url", "") }
                if (imageUrl.isBlank()) continue
                if (score > bestScore) {
                    bestScore = score
                    bestUrl = imageUrl
                }
            }
            bestUrl
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val USER_AGENT = "GymAI/1.0 (Android fitness app; food image lookup)"
    }
}
