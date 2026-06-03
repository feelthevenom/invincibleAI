package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * Unified AI manager that routes prompts to Gemini, Groq, or on-device LiteRT-LM
 * based on [UserProfile.aiProvider] and [UserProfile.aiModelId].
 */
class AiManager(
    private val context: Context,
    private val secureStorageManager: SecureStorageManager,
    private val modelDownloadManager: ModelDownloadManager
) {
    private val offlineEngine = OfflineLlmEngine(context)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.MINUTES)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // ── Status ────────────────────────────────────────────────────────

    fun getStatus(provider: String, modelId: String, offlineModelId: String): AiStatus = when (provider) {
        "gemini" -> {
            val key = resolveGeminiApiKey()
            val model = AiProviderConfig.findModel("gemini", modelId)
            when {
                model == null ->
                    AiStatus(provider, false, "Gemini — not available", "Model \"$modelId\" is not supported. Pick another in AI Settings.")
                key.isNullOrBlank() ->
                    AiStatus(provider, false, "Gemini — not configured", "Add a Gemini API key in AI Settings.")
                else ->
                    AiStatus(provider, true, "Gemini — ${model.displayName}", "Ready for text and vision.")
            }
        }
        "groq" -> {
            val key = secureStorageManager.getGroqApiKey()
            val model = AiProviderConfig.findModel("groq", modelId)
            when {
                model == null ->
                    AiStatus(provider, false, "Groq — not available", "Model \"$modelId\" is not supported. Pick another in AI Settings.")
                key.isNullOrBlank() ->
                    AiStatus(provider, false, "Groq — not configured", "Add a Groq API key in AI Settings.")
                else ->
                    AiStatus(provider, true, "Groq — ${model.displayName}", "Ready for text. Vision not supported.")
            }
        }
        "offline" -> {
            val installed = modelDownloadManager.listInstalledModels().find { it.id == offlineModelId }
            when {
                installed == null ->
                    AiStatus(provider, false, "Offline — model missing", "Download or import a model in AI Settings.")
                else ->
                    AiStatus(provider, true, "Offline — ${installed.displayName}", "On-device vision ready.")
            }
        }
        else -> AiStatus(provider, false, "Unknown provider", "Select Gemini, Groq, or Offline.")
    }

    // ── Public API ────────────────────────────────────────────────────

    suspend fun generateFoodSuggestions(
        query: String,
        provider: String,
        modelId: String,
        offlineModelId: String
    ): List<FoodItem> {
        val prompt = """
            You are a nutrition assistant. The user wants food named "$query".
            Return ONLY a JSON array with up to 4 foods and macros per 100g.
            Example:
            [{"name":"White Rice","calories":130,"protein":2.7,"carbs":28.0,"fat":0.3,"fiber":0.4}]
        """.trimIndent()

        return when (val result = runPrompt(prompt, provider, modelId, offlineModelId, image = null)) {
            is AiAnalysisResult.Success -> result.items
            is AiAnalysisResult.Error -> emptyList()
        }
    }

    suspend fun analyzeFoodImage(
        bitmap: Bitmap,
        provider: String,
        modelId: String,
        offlineModelId: String
    ): AiAnalysisResult = withContext(Dispatchers.IO) {
        // Check vision support
        if (!AiProviderConfig.supportsVision(provider, if (provider == "offline") offlineModelId else modelId)) {
            return@withContext AiAnalysisResult.Error(
                "Vision is not available with ${AiProviderConfig.displayNameFor(provider)}. " +
                    "Switch to Gemini or Offline for image analysis."
            )
        }

        val prompt = """
            Identify every distinct food item visible in this photo.
            For each item provide name and estimated nutrition per 100g.
            Be specific (e.g. "Steamed White Rice", "Dal", "Chicken Curry").
            Return ONLY a valid JSON array, no markdown:
            [{"name":"Steamed White Rice","calories":130,"protein":2.7,"carbs":28.0,"fat":0.3,"fiber":0.4}]
        """.trimIndent()

        runPrompt(prompt, provider, modelId, offlineModelId, scaleForVision(bitmap, provider, offlineModelId))
    }

    // ── Internal routing ──────────────────────────────────────────────

    private suspend fun runPrompt(
        prompt: String,
        provider: String,
        modelId: String,
        offlineModelId: String,
        image: Bitmap?
    ): AiAnalysisResult {
        return try {
            val responseText = when (provider) {
                "gemini" -> {
                    if (image != null) callGeminiWithImage(prompt, image, modelId)
                    else callGeminiText(prompt, modelId)
                }
                "groq" -> {
                    if (image != null) throw UnsupportedOperationException(
                        "Groq does not support image analysis. Switch to Gemini or Offline."
                    )
                    callGroq(prompt, modelId)
                }
                "offline" -> callOffline(prompt, offlineModelId, image)
                else -> return AiAnalysisResult.Error("Unknown AI provider: $provider")
            }

            if (responseText.isNullOrBlank()) {
                return AiAnalysisResult.Error("AI returned an empty response. Try again or retake the photo.")
            }

            val items = parseAiFoodResponse(responseText)
            if (items.isEmpty()) {
                AiAnalysisResult.Error("AI could not extract food items. Use a clear photo with the plate centered and good lighting.")
            } else {
                AiAnalysisResult.Success(items)
            }
        } catch (e: Exception) {
            Log.e(TAG, "AI prompt failed", e)
            AiAnalysisResult.Error(e.message ?: "AI request failed")
        }
    }

    // ── Gemini ────────────────────────────────────────────────────────

    private fun resolveGeminiApiKey(): String? {
        val stored = secureStorageManager.getGeminiApiKey()?.trim().orEmpty()
        if (stored.isNotBlank() && stored != "MY_GEMINI_API_KEY") return stored
        val fromEnv = BuildConfig.GEMINI_API_KEY.trim()
        return fromEnv.takeIf { it.isNotBlank() && it != "MY_GEMINI_API_KEY" }
    }

    private suspend fun callGeminiText(prompt: String, modelId: String): String? {
        val apiKey = resolveGeminiApiKey()
            ?: throw IllegalStateException("Gemini API key not configured. Add it in AI Settings.")
        val model = GenerativeModel(modelName = modelId, apiKey = apiKey)
        return model.generateContent(prompt).text
    }

    private suspend fun callGeminiWithImage(prompt: String, bitmap: Bitmap, modelId: String): String? {
        val apiKey = resolveGeminiApiKey()
            ?: throw IllegalStateException("Gemini API key not configured. Add it in AI Settings.")
        val model = GenerativeModel(modelName = modelId, apiKey = apiKey)
        val input = content {
            image(bitmap)
            text(prompt)
        }
        return model.generateContent(input).text
    }

    // ── Groq (OpenAI-compatible) ──────────────────────────────────────

    private suspend fun callGroq(prompt: String, modelId: String): String? = withContext(Dispatchers.IO) {
        val apiKey = secureStorageManager.getGroqApiKey()
            ?: throw IllegalStateException("Groq API key not configured. Add it in AI Settings.")

        val body = JSONObject().apply {
            put("model", modelId)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "You are a nutrition data assistant. Always respond with valid JSON only.")
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("temperature", 0.3)
            put("max_tokens", 1024)
        }

        val request = Request.Builder()
            .url(GROQ_BASE_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string().orEmpty()
            throw IllegalStateException("Groq API error (${response.code}): $errorBody")
        }

        val json = JSONObject(response.body?.string() ?: "{}")
        val choices = json.optJSONArray("choices")
        choices?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
    }

    // ── Offline (LiteRT-LM) ──────────────────────────────────────────

    private suspend fun callOffline(prompt: String, offlineModelId: String, image: Bitmap?): String {
        val installed = modelDownloadManager.listInstalledModels().find { it.id == offlineModelId }
            ?: throw IllegalStateException("Select a downloaded or imported offline model in AI Settings.")

        val modelFile = modelDownloadManager.resolveModelFile(offlineModelId)
            ?: throw IllegalStateException("Model file missing for ${installed.displayName}")

        return offlineEngine.generate(prompt, modelFile.absolutePath, image)
    }

    // ── Utilities ─────────────────────────────────────────────────────

    private fun scaleForVision(source: Bitmap, provider: String, offlineModelId: String, maxSide: Int = 1024): Bitmap {
        val side = when {
            provider == "offline" && offlineModelId == "offline_4b" -> 512
            provider == "offline" -> 768
            else -> maxSide
        }
        val safe = ensureSoftwareCopy(source)
        val w = safe.width
        val h = safe.height
        if (max(w, h) <= side) return safe
        val scale = side.toFloat() / max(w, h)
        return Bitmap.createScaledBitmap(
            safe,
            max(1, (w * scale).toInt()),
            max(1, (h * scale).toInt()),
            true
        )
    }

    private fun ensureSoftwareCopy(source: Bitmap): Bitmap {
        if (source.config == Bitmap.Config.HARDWARE) {
            return source.copy(Bitmap.Config.ARGB_8888, false) ?: source
        }
        return source
    }

    private fun parseAiFoodResponse(jsonText: String): List<FoodItem> {
        val regex = Regex("\\[[\\s\\S]*\\]")
        val cleanJson = regex.find(jsonText)?.value ?: jsonText
        val items = mutableListOf<FoodItem>()
        try {
            val array = JSONArray(cleanJson)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val name = obj.optString("name", "").trim()
                if (name.isBlank()) continue
                items.add(
                    FoodItem(
                        id = "ai_${name.hashCode()}_${System.currentTimeMillis()}_$i",
                        name = name,
                        caloriesPer100g = obj.optDouble("calories", 0.0).toInt(),
                        proteinPer100g = obj.optDouble("protein", 0.0).toFloat(),
                        carbsPer100g = obj.optDouble("carbs", 0.0).toFloat(),
                        fatPer100g = obj.optDouble("fat", 0.0).toFloat(),
                        fiberPer100g = obj.optDouble("fiber", 0.0).toFloat(),
                        isCustom = true,
                        source = "ai_generated"
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "JSON parse failed: $cleanJson", e)
        }
        return items
    }

    suspend fun generateExerciseSuggestions(
        query: String,
        routine: String,
        provider: String,
        modelId: String,
        offlineModelId: String
    ): List<ExerciseItem> {
        val prompt = """
            Suggest up to 4 gym exercises for "$query" on a $routine workout day.
            Return ONLY JSON array:
            [{"name":"Incline Bench Press","type":"Chest","sets":3,"reps":10}]
            Types: Chest, Back, Shoulders, Biceps, Triceps, Legs, Glutes, Core, Full Body, Cardio
        """.trimIndent()
        val text = runPromptRaw(prompt, provider, modelId, offlineModelId)
        return parseExerciseResponse(text)
    }

    private suspend fun runPromptRaw(
        prompt: String,
        provider: String,
        modelId: String,
        offlineModelId: String
    ): String? = try {
        when (provider) {
            "gemini" -> callGeminiText(prompt, modelId)
            "groq" -> callGroq(prompt, modelId)
            "offline" -> callOffline(prompt, offlineModelId, null)
            else -> null
        }
    } catch (e: Exception) {
        Log.e(TAG, "Exercise AI failed", e)
        null
    }

    private fun parseExerciseResponse(jsonText: String?): List<ExerciseItem> {
        if (jsonText.isNullOrBlank()) return emptyList()
        val regex = Regex("\\[[\\s\\S]*\\]")
        val cleanJson = regex.find(jsonText)?.value ?: jsonText
        val items = mutableListOf<ExerciseItem>()
        try {
            val array = JSONArray(cleanJson)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val name = obj.optString("name", "").trim()
                if (name.isBlank()) continue
                items.add(
                    ExerciseItem(
                        id = "ai_ex_${name.hashCode()}_$i",
                        name = name,
                        exerciseType = obj.optString("type", "Full Body"),
                        defaultSets = obj.optInt("sets", 3),
                        defaultReps = obj.optInt("reps", 10),
                        isCustom = true
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exercise JSON parse failed", e)
        }
        return items
    }

    fun releaseOfflineEngine() {
        offlineEngine.release()
    }

    companion object {
        private const val TAG = "AiManager"
        private const val GROQ_BASE_URL = "https://api.groq.com/openai/v1/chat/completions"
    }
}
