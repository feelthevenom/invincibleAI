package com.example.data.api

import com.example.data.ExerciseNameMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Free wger.de exercise diagrams — fallback when no Lottie animation is available.
 *
 * Two-step lookup (wger requires numeric exercise IDs, not English names):
 * 1. `/exercise/search/?term=…` → exercise id
 * 2. `/exerciseimage/?exercise={id}` → image URL
 */
class WgerImageApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()
) {
    suspend fun findExerciseImageUrl(exerciseName: String): String? = withContext(Dispatchers.IO) {
        val term = exerciseName.trim()
        if (term.isBlank()) return@withContext null

        searchExerciseImage(term)?.let { return@withContext it }

        val expanded = ExerciseNameMatcher.expandAbbreviations(term)
        if (expanded != term) {
            searchExerciseImage(expanded)?.let { return@withContext it }
        }

        ExerciseNameMatcher.tokens(term).takeIf { it.isNotEmpty() }?.let { tokens ->
            searchExerciseImage(tokens.joinToString(" "))?.let { return@withContext it }
        }

        null
    }

    private fun searchExerciseImage(term: String): String? {
        return try {
            val exerciseId = findExerciseIdBySearch(term)
                ?: findExerciseIdByList(term)
                ?: return null
            fetchMainImage(exerciseId)
        } catch (_: Exception) {
            null
        }
    }

    /** Step 1a: autocomplete search → exercise id. */
    private fun findExerciseIdBySearch(term: String): Int? {
        val encoded = URLEncoder.encode(term, Charsets.UTF_8.name())
        val searchJson = getJson("$BASE/exercise/search/?term=$encoded&language=2") ?: return null
        val suggestions = searchJson.optJSONArray("suggestions").orEmpty()
        if (suggestions.length() == 0) return null

        var bestId: Int? = null
        var bestScore = 0
        for (i in 0 until suggestions.length()) {
            val item = suggestions.optJSONObject(i) ?: continue
            val label = item.optString("value", "")
            val score = ExerciseNameMatcher.score(term, label)
            val id = extractExerciseId(item) ?: continue
            if (score > bestScore) {
                bestScore = score
                bestId = id
            }
        }
        return bestId?.takeIf { bestScore >= 60 }
    }

    /** Step 1b: list filter fallback when autocomplete misses. */
    private fun findExerciseIdByList(term: String): Int? {
        val encoded = URLEncoder.encode(term, Charsets.UTF_8.name())
        val json = getJson("$BASE/exercise/?language=2&name=$encoded&limit=10") ?: return null
        val results = json.optJSONArray("results").orEmpty()
        if (results.length() == 0) return null

        var bestId: Int? = null
        var bestScore = 0
        for (i in 0 until results.length()) {
            val row = results.optJSONObject(i) ?: continue
            val id = row.optInt("id", -1).takeIf { it > 0 } ?: continue
            val names = englishNames(row)
            for (name in names) {
                val score = ExerciseNameMatcher.score(term, name)
                if (score > bestScore) {
                    bestScore = score
                    bestId = id
                }
            }
        }
        return bestId?.takeIf { bestScore >= 60 }
    }

    /** Step 2: fetch main image for exercise id. */
    private fun fetchMainImage(exerciseId: Int): String? {
        val json = getJson("$BASE/exerciseimage/?exercise=$exerciseId&limit=5") ?: return null
        val results = json.optJSONArray("results").orEmpty()
        if (results.length() == 0) return null

        var mainUrl: String? = null
        for (i in 0 until results.length()) {
            val row = results.optJSONObject(i) ?: continue
            val image = row.optString("image", "").ifBlank { continue }
            if (row.optBoolean("is_main", false)) return image
            if (mainUrl == null) mainUrl = image
        }
        return mainUrl
    }

    private fun englishNames(exercise: JSONObject): List<String> {
        val names = mutableListOf<String>()
        val translations = exercise.optJSONArray("translations").orEmpty()
        for (i in 0 until translations.length()) {
            val t = translations.optJSONObject(i) ?: continue
            if (t.optInt("language", 0) != 2) continue
            t.optString("name", "").takeIf { it.isNotBlank() }?.let { names.add(it) }
        }
        exercise.optString("name", "").takeIf { it.isNotBlank() }?.let { names.add(it) }
        return names
    }

    private fun extractExerciseId(item: JSONObject): Int? {
        when (val data = item.opt("data")) {
            is JSONObject -> {
                data.optInt("id", -1).takeIf { it > 0 }?.let { return it }
                data.optInt("base_id", -1).takeIf { it > 0 }?.let { return it }
            }
            is Int -> if (data > 0) return data
            is String -> data.toIntOrNull()?.takeIf { it > 0 }?.let { return it }
        }
        return item.optInt("id", -1).takeIf { it > 0 }
    }

    private fun getJson(url: String): JSONObject? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return null
            JSONObject(body)
        }
    }

    companion object {
        private const val BASE = "https://wger.de/api/v2"
        private const val USER_AGENT = "InvincibleAI/1.0 (Android fitness app; wger image lookup)"
    }
}

private fun org.json.JSONArray?.orEmpty(): org.json.JSONArray =
    this ?: org.json.JSONArray()
