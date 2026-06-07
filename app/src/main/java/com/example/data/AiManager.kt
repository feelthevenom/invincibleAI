package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val offlineEngineLock = Mutex()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.MINUTES)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // ── Status ────────────────────────────────────────────────────────

    fun getStatus(provider: String, modelId: String, offlineModelId: String): AiStatus = when (provider) {
        "gemini" -> {
            val key = resolveGeminiApiKey()
            val resolvedModel = AiProviderConfig.resolveGeminiModelId(modelId)
            val catalogModel = AiProviderConfig.findModel("gemini", modelId)
                ?: AiProviderConfig.findModel("gemini", resolvedModel)
            when {
                key.isNullOrBlank() ->
                    AiStatus(provider, false, "Gemini — not configured", "Add a Gemini API key in AI Settings.")
                else -> {
                    val display = catalogModel?.displayName ?: resolvedModel
                    AiStatus(provider, true, "Gemini — $display", "API key saved. Tap \"Test API Key\" to verify connectivity.")
                }
            }
        }
        "groq" -> {
            val key = secureStorageManager.getGroqApiKey()?.trim()
            val resolvedModel = AiProviderConfig.resolveGroqModelId(modelId)
            val catalogModel = AiProviderConfig.findModel("groq", modelId)
                ?: AiProviderConfig.findModel("groq", resolvedModel)
            when {
                key.isNullOrBlank() ->
                    AiStatus(provider, false, "Groq — not configured", "Add a Groq API key in AI Settings.")
                catalogModel == null && AiProviderConfig.groqVisionModels().isEmpty() ->
                    AiStatus(provider, false, "Groq — no vision models", "Groq vision models are not configured in the app.")
                else -> {
                    val display = catalogModel?.displayName ?: resolvedModel
                    val visionNote = if (catalogModel?.supportsVision == true) "Supports vision." else "Vision via Llama 4 Scout."
                    AiStatus(provider, true, "Groq — $display", "API key saved. $visionNote Tap \"Test API Key\" to verify.")
                }
            }
        }
        "openrouter" -> {
            val key = resolveOpenRouterApiKey()
            val resolvedModel = AiProviderConfig.resolveOpenRouterModelId(modelId)
            val catalogModel = AiProviderConfig.findModel("openrouter", modelId)
                ?: AiProviderConfig.findModel("openrouter", resolvedModel)
            when {
                key.isNullOrBlank() ->
                    AiStatus(provider, false, "OpenRouter — not configured", "Add an OpenRouter API key in AI Settings.")
                OpenRouterModelStore.allModels().isEmpty() ->
                    AiStatus(provider, false, "OpenRouter — models not loaded", "Save your API key and refresh models in AI Settings.")
                catalogModel == null ->
                    AiStatus(provider, false, "OpenRouter — no model selected", "Choose a model from the loaded OpenRouter list.")
                else -> {
                    val display = catalogModel.displayName
                    val visionNote = if (catalogModel.supportsVision) "Supports vision." else "Text only."
                    AiStatus(provider, true, "OpenRouter — $display", "API key saved. $visionNote Tap \"Test API Connection\" to verify.")
                }
            }
        }
        "offline" -> {
            val installed = modelDownloadManager.listInstalledModels().find { it.id == offlineModelId }
            when {
                installed == null ->
                    AiStatus(provider, false, "Offline — model missing", "Download or import a model in AI Settings.")
                else ->
                    AiStatus(provider, true, "Available — ${installed.displayName}", "On-device vision ready.")
            }
        }
        else -> AiStatus(provider, false, "Unknown provider", "Select Gemini, Groq, OpenRouter, or Offline.")
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
                    if (image != null) {
                        if (!AiProviderConfig.supportsVision("groq", modelId)) {
                            throw UnsupportedOperationException(
                                "Selected Groq model does not support vision. Use Llama 4 Scout."
                            )
                        }
                        callGroqWithImage(prompt, image, modelId)
                    } else {
                        callGroq(prompt, modelId)
                    }
                }
                "openrouter" -> {
                    if (image != null) {
                        if (!AiProviderConfig.supportsVision("openrouter", modelId)) {
                            throw UnsupportedOperationException(
                                "Selected OpenRouter model does not support vision. Pick a vision-capable model."
                            )
                        }
                        callOpenRouterWithImage(prompt, image, modelId)
                    } else {
                        callOpenRouter(prompt, modelId)
                    }
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

    fun hasGeminiApiKey(): Boolean = !resolveGeminiApiKey().isNullOrBlank()

    /**
     * Verifies the Gemini API key against the Developer API endpoint (same as curl test).
     * Returns success message or throws with the API error body.
     */
    suspend fun testGeminiConnection(modelId: String? = null): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = resolveGeminiApiKey()
            ?: return@withContext Result.failure(IllegalStateException("No Gemini API key saved. Add your key in AI Settings."))
        val model = AiProviderConfig.resolveGeminiModelId(modelId.orEmpty())
        try {
            val text = callGeminiRestGenerate("Reply with exactly: OK", model, apiKey)
            if (text.isNullOrBlank()) {
                Result.failure(IllegalStateException("Gemini returned an empty response for model $model."))
            } else {
                Result.success("Connected to $model successfully.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gemini test failed for model=$model", e)
            Result.failure(e)
        }
    }

    private suspend fun callGeminiText(prompt: String, modelId: String): String? = withContext(Dispatchers.IO) {
        val apiKey = resolveGeminiApiKey()
            ?: throw IllegalStateException("Gemini API key not configured. Add it in AI Settings.")
        val resolvedModel = AiProviderConfig.resolveGeminiModelId(modelId)
        try {
            val model = GenerativeModel(modelName = resolvedModel, apiKey = apiKey)
            model.generateContent(prompt).text
        } catch (sdkError: Exception) {
            Log.w(TAG, "Gemini SDK failed ($resolvedModel), trying REST", sdkError)
            callGeminiRestGenerate(prompt, resolvedModel, apiKey)
        }
    }

    private fun callGeminiRestGenerate(prompt: String, model: String, apiKey: String): String? {
        val url = "$GEMINI_REST_BASE/models/$model:generateContent?key=$apiKey"
        val body = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })
            })
        }
        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            val message = parseGeminiErrorMessage(response.code, responseBody)
            throw IllegalStateException(message)
        }
        val json = JSONObject(responseBody)
        return json.optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
            ?.optJSONObject(0)
            ?.optString("text")
    }

    private fun parseGeminiErrorMessage(code: Int, body: String): String {
        return try {
            val err = JSONObject(body).optJSONObject("error")
            val msg = err?.optString("message", "").orEmpty()
            val status = err?.optString("status", "").orEmpty()
            when {
                code == 400 && msg.contains("API key", ignoreCase = true) ->
                    "Invalid Gemini API key. Create a key at aistudio.google.com/apikey and paste it in AI Settings."
                code == 404 ->
                    "Model not found ($status). Try Gemini 2.5 Flash in AI Settings."
                msg.isNotBlank() -> "Gemini API error ($code): $msg"
                else -> "Gemini API error ($code): $body"
            }
        } catch (_: Exception) {
            "Gemini API error ($code): $body"
        }
    }

    private suspend fun callGeminiWithImage(prompt: String, bitmap: Bitmap, modelId: String): String? =
        withContext(Dispatchers.IO) {
        val apiKey = resolveGeminiApiKey()
            ?: throw IllegalStateException("Gemini API key not configured. Add it in AI Settings.")
        val resolvedModel = AiProviderConfig.resolveGeminiModelId(modelId)
        val model = GenerativeModel(modelName = resolvedModel, apiKey = apiKey)
        val input = content {
            image(bitmap)
            text(prompt)
        }
        model.generateContent(input).text
    }

    // ── Groq (OpenAI-compatible) ──────────────────────────────────────

    private fun resolveGroqApiKey(): String? =
        secureStorageManager.getGroqApiKey()?.trim()?.takeIf { it.isNotBlank() }

    fun hasGroqApiKey(): Boolean = !resolveGroqApiKey().isNullOrBlank()

    suspend fun testGroqConnection(modelId: String? = null): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = resolveGroqApiKey()
            ?: return@withContext Result.failure(IllegalStateException("No Groq API key saved. Add your key in AI Settings."))
        val model = AiProviderConfig.resolveGroqModelId(modelId.orEmpty())
        try {
            val text = callGroq("Reply with exactly: OK", model, apiKey)
            if (text.isNullOrBlank()) {
                Result.failure(IllegalStateException("Groq returned an empty response for model $model."))
            } else {
                Result.success("Connected to $model successfully.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Groq test failed for model=$model", e)
            Result.failure(e)
        }
    }

    private suspend fun callGroq(prompt: String, modelId: String): String? = withContext(Dispatchers.IO) {
        val apiKey = resolveGroqApiKey()
            ?: throw IllegalStateException("Groq API key not configured. Add it in AI Settings.")
        val model = AiProviderConfig.resolveGroqModelId(modelId)
        callGroq(prompt, model, apiKey)
    }

    private fun callGroq(prompt: String, model: String, apiKey: String): String? {
        val body = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("temperature", 0.3)
            put("max_tokens", 1024)
        }
        return postGroqChat(body, apiKey)
    }

    private suspend fun callGroqWithImage(prompt: String, bitmap: Bitmap, modelId: String): String? =
        withContext(Dispatchers.IO) {
            val apiKey = resolveGroqApiKey()
                ?: throw IllegalStateException("Groq API key not configured. Add it in AI Settings.")
            val model = AiProviderConfig.resolveGroqModelId(modelId)
            val imageUrl = bitmapToBase64DataUrl(bitmap)
            val body = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", JSONArray().apply {
                            put(JSONObject().apply {
                                put("type", "text")
                                put("text", prompt)
                            })
                            put(JSONObject().apply {
                                put("type", "image_url")
                                put("image_url", JSONObject().apply { put("url", imageUrl) })
                            })
                        })
                    })
                })
                put("temperature", 0.3)
                put("max_tokens", 2048)
            }
            postGroqChat(body, apiKey)
        }

    private fun postGroqChat(body: JSONObject, apiKey: String): String? {
        val request = Request.Builder()
            .url(GROQ_BASE_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string().orEmpty()
            throw IllegalStateException(parseGroqErrorMessage(response.code, errorBody))
        }

        val json = JSONObject(response.body?.string() ?: "{}")
        return json.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
    }

    private fun parseGroqErrorMessage(code: Int, body: String): String {
        return try {
            val err = JSONObject(body).optJSONObject("error")
            val msg = err?.optString("message", "").orEmpty()
            when {
                code == 401 ->
                    "Invalid Groq API key. Create a key at console.groq.com and paste it in AI Settings."
                code == 404 ->
                    "Groq model not found. Select Llama 4 Scout in AI Settings."
                msg.isNotBlank() -> "Groq API error ($code): $msg"
                else -> "Groq API error ($code): $body"
            }
        } catch (_: Exception) {
            "Groq API error ($code): $body"
        }
    }

    private fun bitmapToBase64DataUrl(bitmap: Bitmap): String {
        val stream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        val encoded = android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.NO_WRAP)
        return "data:image/jpeg;base64,$encoded"
    }

    // ── OpenRouter (OpenAI-compatible) ───────────────────────────────

    private fun resolveOpenRouterApiKey(): String? =
        secureStorageManager.getOpenRouterApiKey()?.trim()?.takeIf { it.isNotBlank() }

    fun hasOpenRouterApiKey(): Boolean = !resolveOpenRouterApiKey().isNullOrBlank()

    suspend fun testOpenRouterConnection(modelId: String? = null): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = resolveOpenRouterApiKey()
            ?: return@withContext Result.failure(IllegalStateException("No OpenRouter API key saved. Add your key in AI Settings."))
        val model = AiProviderConfig.resolveOpenRouterModelId(modelId.orEmpty())
        if (model.isBlank()) {
            return@withContext Result.failure(IllegalStateException("No OpenRouter model selected. Refresh models in AI Settings."))
        }
        try {
            val text = callOpenRouter("Reply with exactly: OK", model, apiKey)
            if (text.isNullOrBlank()) {
                Result.failure(IllegalStateException("OpenRouter returned an empty response for model $model."))
            } else {
                Result.success("Connected to $model successfully.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "OpenRouter test failed for model=$model", e)
            Result.failure(e)
        }
    }

    private suspend fun callOpenRouter(prompt: String, modelId: String): String? = withContext(Dispatchers.IO) {
        val apiKey = resolveOpenRouterApiKey()
            ?: throw IllegalStateException("OpenRouter API key not configured. Add it in AI Settings.")
        val model = AiProviderConfig.resolveOpenRouterModelId(modelId)
        callOpenRouter(prompt, model, apiKey)
    }

    private fun callOpenRouter(prompt: String, model: String, apiKey: String): String? {
        val body = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("temperature", 0.3)
            put("max_tokens", 1024)
        }
        return postOpenRouterChat(body, apiKey)
    }

    private suspend fun callOpenRouterWithImage(prompt: String, bitmap: Bitmap, modelId: String): String? =
        withContext(Dispatchers.IO) {
            val apiKey = resolveOpenRouterApiKey()
                ?: throw IllegalStateException("OpenRouter API key not configured. Add it in AI Settings.")
            val model = AiProviderConfig.resolveOpenRouterModelId(modelId, vision = true)
            val imageUrl = bitmapToBase64DataUrl(bitmap)
            val body = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", JSONArray().apply {
                            put(JSONObject().apply {
                                put("type", "text")
                                put("text", prompt)
                            })
                            put(JSONObject().apply {
                                put("type", "image_url")
                                put("image_url", JSONObject().apply { put("url", imageUrl) })
                            })
                        })
                    })
                })
                put("temperature", 0.3)
                put("max_tokens", 2048)
            }
            postOpenRouterChat(body, apiKey)
        }

    private fun postOpenRouterChat(body: JSONObject, apiKey: String): String? {
        val request = Request.Builder()
            .url("$OPENROUTER_BASE/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("HTTP-Referer", "https://gym-ai.app")
            .addHeader("X-Title", "GYM AI")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string().orEmpty()
            throw IllegalStateException(parseOpenRouterErrorMessage(response.code, errorBody))
        }

        val json = JSONObject(response.body?.string() ?: "{}")
        return json.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
    }

    private fun parseOpenRouterErrorMessage(code: Int, body: String): String {
        return try {
            val err = JSONObject(body).optJSONObject("error")
            val msg = err?.optString("message", "").orEmpty()
            when {
                code == 401 ->
                    "Invalid OpenRouter API key. Create a key at openrouter.ai/keys and paste it in AI Settings."
                code == 404 ->
                    "OpenRouter model not found. Refresh models and pick another model."
                msg.isNotBlank() -> "OpenRouter API error ($code): $msg"
                else -> "OpenRouter API error ($code): $body"
            }
        } catch (_: Exception) {
            "OpenRouter API error ($code): $body"
        }
    }

    // ── Offline (LiteRT-LM) ──────────────────────────────────────────

    private suspend fun callOffline(prompt: String, offlineModelId: String, image: Bitmap?): String =
        offlineEngineLock.withLock {
            val installed = modelDownloadManager.listInstalledModels().find { it.id == offlineModelId }
                ?: throw IllegalStateException("Select a downloaded or imported offline model in AI Settings.")

            val modelFile = modelDownloadManager.resolveModelFile(offlineModelId)
                ?: throw IllegalStateException("Model file missing for ${installed.displayName}")

            offlineEngine.generate(prompt, modelFile.absolutePath, image)
        }

    private suspend fun callOfflineStream(prompt: String, offlineModelId: String): Flow<String> {
        val installed = modelDownloadManager.listInstalledModels().find { it.id == offlineModelId }
            ?: throw IllegalStateException("Select a downloaded or imported offline model in AI Settings.")
        val modelFile = modelDownloadManager.resolveModelFile(offlineModelId)
            ?: throw IllegalStateException("Model file missing for ${installed.displayName}")
        return offlineEngine.generateStream(prompt, modelFile.absolutePath, null)
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

    suspend fun suggestRoutineName(
        routineNames: List<String>,
        yesterdaySummary: String,
        goal: String,
        workoutDaysPerWeek: Int,
        provider: String,
        modelId: String,
        offlineModelId: String
    ): String? {
        val options = routineNames.joinToString(", ")
        val prompt = """
            Pick ONE best workout routine for today from this list: [$options]
            User goal: $goal. Workout days/week: $workoutDaysPerWeek.
            $yesterdaySummary
            Return ONLY JSON: {"routine":"Pull","reason":"short reason"}
            The routine value must exactly match one name from the list.
        """.trimIndent()
        val text = runPromptRaw(prompt, provider, modelId, offlineModelId) ?: return null
        return try {
            val regex = Regex("\\{[\\s\\S]*\\}")
            val json = regex.find(text)?.value ?: return null
            org.json.JSONObject(json).optString("routine", "").trim().takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun generateRoutineExerciseBatch(
        routine: String,
        excludeNames: List<String>,
        provider: String,
        modelId: String,
        offlineModelId: String
    ): List<ExerciseItem> {
        val excludeList = excludeNames.distinct().joinToString(", ") { "\"$it\"" }.ifBlank { "none" }
        val cardioRoutine = routine.equals("Cardio", ignoreCase = true)
        val prompt = """
            Suggest up to 5 gym exercises for a "$routine" workout day.
            Do NOT suggest any of these existing exercises: $excludeList
            ${if (cardioRoutine) "Prefer cardio exercises (timer-based, no weights)." else "Prefer strength exercises with sets and reps."}
            Return ONLY a JSON array (max 5 items):
            [{"name":"Incline Bench Press","type":"Chest","sets":3,"reps":10,"isCardio":false}]
            For cardio use type "Cardio", sets 1, reps 0, isCardio true.
            Types: Chest, Back, Shoulders, Biceps, Triceps, Legs, Glutes, Core, Full Body, Cardio
        """.trimIndent()
        val text = runPromptRaw(prompt, provider, modelId, offlineModelId)
        return parseExerciseResponse(text)
            .filter { item -> excludeNames.none { it.equals(item.name, ignoreCase = true) } }
            .take(5)
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

    suspend fun generateCoachInsight(
        userSummary: String,
        provider: String,
        modelId: String,
        offlineModelId: String
    ): com.example.CoachInsight? = generateTypedCoachInsight(
        userSummary, provider, modelId, offlineModelId,
        defaultHeadline = "AI COACH INSIGHT"
    )

    suspend fun generateDietCoachInsight(
        userSummary: String,
        provider: String,
        modelId: String,
        offlineModelId: String
    ): com.example.CoachInsight? = generateTypedCoachInsight(
        userSummary, provider, modelId, offlineModelId,
        defaultHeadline = "AI DIET INSIGHT"
    )

    suspend fun generateExerciseCoachInsight(
        userSummary: String,
        provider: String,
        modelId: String,
        offlineModelId: String
    ): com.example.CoachInsight? = generateTypedCoachInsight(
        userSummary, provider, modelId, offlineModelId,
        defaultHeadline = "AI COACH INSIGHT"
    )

    private suspend fun generateTypedCoachInsight(
        userSummary: String,
        provider: String,
        modelId: String,
        offlineModelId: String,
        defaultHeadline: String
    ): com.example.CoachInsight? {
        val prompt = """
            You are a personal fitness coach. Using ONLY the data below, write one specific actionable insight.
            Keep it to 2 sentences max. Mention at least one number from the data.
            Return ONLY JSON: {"headline":"$defaultHeadline","body":"...","highlight":"number or phrase to emphasize"}
            User data: $userSummary
        """.trimIndent()
        val text = runPromptRaw(prompt, provider, modelId, offlineModelId) ?: return null
        return try {
            val json = Regex("\\{[\\s\\S]*\\}").find(text)?.value ?: return null
            val obj = org.json.JSONObject(json)
            com.example.CoachInsight(
                headline = obj.optString("headline", defaultHeadline).ifBlank { defaultHeadline },
                body = obj.optString("body", "").trim().ifBlank { return null },
                highlight = obj.optString("highlight", "").trim().takeIf { it.isNotBlank() }
            )
        } catch (_: Exception) {
            null
        }
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
            "openrouter" -> callOpenRouter(prompt, modelId)
            "offline" -> callOffline(prompt, offlineModelId, null)
            else -> null
        }
    } catch (e: Exception) {
        Log.e(TAG, "Exercise AI failed", e)
        null
    }

    suspend fun generateCoachChatReply(
        userContext: String,
        conversationHistory: List<Pair<String, String>>,
        userMessage: String,
        provider: String,
        modelId: String,
        offlineModelId: String
    ): String? {
        val historyBlock = if (conversationHistory.isEmpty()) {
            ""
        } else {
            conversationHistory.takeLast(6).joinToString("\n") { (role, text) ->
                val label = if (role == "user") "User" else "Coach"
                "$label: ${text.take(280)}"
            }
        }
        val contextForPrompt = if (provider == "offline") {
            userContext.lines().filter { it.isNotBlank() }.take(10).joinToString("\n").take(1400)
        } else {
            userContext
        }
        val prompt = if (provider == "offline") {
            """
            You are Coach AI — a fitness and nutrition coach in a mobile app.
            Answer briefly (2-3 short paragraphs). Use numbers from USER DATA when relevant.
            Do not mention being an AI model.
            
            USER DATA:
            $contextForPrompt
            
            ${if (historyBlock.isNotBlank()) "CONVERSATION:\n$historyBlock\n" else ""}
            User: $userMessage
            Coach:
            """.trimIndent()
        } else {
            """
            You are Coach AI — a personal fitness and nutrition coach inside the GYM AI mobile app.
            Answer questions about diet, macros, meal planning, workouts, exercise form, programming, recovery, and gym training.
            
            RULES:
            - Be conversational, supportive, and practical (2-5 short paragraphs max unless listing exercises).
            - Reference specific numbers from USER DATA when relevant (calories, protein, volume, PRs, etc.).
            - For exercise swaps or routines use bullet lists: "Exercise Name (sets×reps per side if applicable)".
            - Use **double asterisks** around key phrases you want emphasized (e.g. **Let's swap heavy back squats for:**).
            - Do NOT invent data not in USER DATA; if missing, ask a clarifying question or give general guidance.
            - Do NOT diagnose injuries or prescribe medical treatment; recommend a professional for persistent pain.
            - Redirect off-topic questions politely back to fitness/nutrition.
            - Never mention being an AI model unless asked.
            
            USER DATA:
            $contextForPrompt
            
            ${if (historyBlock.isNotBlank()) "CONVERSATION SO FAR:\n$historyBlock\n" else ""}
            User: $userMessage
            Coach:
            """.trimIndent()
        }
        return runPromptRaw(prompt, provider, modelId, offlineModelId)
    }

    fun generateCoachChatReplyStream(
        userContext: String,
        conversationHistory: List<Pair<String, String>>,
        userMessage: String,
        provider: String,
        modelId: String,
        offlineModelId: String
    ): Flow<String> {
        val prompt = buildCoachChatPrompt(userContext, conversationHistory, userMessage, provider)
        return when (provider) {
            "offline" -> flow {
                try {
                    offlineEngineLock.withLock {
                        callOfflineStream(prompt, offlineModelId).collect { chunk -> emit(chunk) }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Offline coach stream failed", e)
                }
            }
            "gemini" -> streamGeminiCoach(prompt, modelId)
            "groq" -> streamGroqCoach(prompt, modelId)
            "openrouter" -> streamOpenRouterCoach(prompt, modelId)
            else -> flow {
                runPromptRaw(prompt, provider, modelId, offlineModelId)?.let { emit(it) }
            }
        }
    }

    private fun buildCoachChatPrompt(
        userContext: String,
        conversationHistory: List<Pair<String, String>>,
        userMessage: String,
        provider: String
    ): String {
        val historyBlock = if (conversationHistory.isEmpty()) "" else {
            conversationHistory.takeLast(6).joinToString("\n") { (role, text) ->
                val label = if (role == "user") "User" else "Coach"
                "$label: ${text.take(280)}"
            }
        }
        val contextForPrompt = if (provider == "offline") {
            userContext.lines().filter { it.isNotBlank() }.take(10).joinToString("\n").take(1400)
        } else {
            userContext
        }
        return if (provider == "offline") {
            """
            You are Coach AI — a fitness and nutrition coach in a mobile app.
            Answer briefly (2-3 short paragraphs). Use numbers from USER DATA when relevant.
            Do not mention being an AI model.
            
            USER DATA:
            $contextForPrompt
            
            ${if (historyBlock.isNotBlank()) "CONVERSATION:\n$historyBlock\n" else ""}
            User: $userMessage
            Coach:
            """.trimIndent()
        } else {
            """
            You are Coach AI — a personal fitness and nutrition coach inside the GYM AI mobile app.
            Answer questions about diet, macros, meal planning, workouts, exercise form, programming, recovery, and gym training.
            
            RULES:
            - Be conversational, supportive, and practical (2-5 short paragraphs max unless listing exercises).
            - Reference specific numbers from USER DATA when relevant.
            - Use **double asterisks** around key phrases for emphasis.
            - Do NOT invent data not in USER DATA.
            - Never mention being an AI model unless asked.
            
            USER DATA:
            $contextForPrompt
            
            ${if (historyBlock.isNotBlank()) "CONVERSATION SO FAR:\n$historyBlock\n" else ""}
            User: $userMessage
            Coach:
            """.trimIndent()
        }
    }

    private fun streamGroqCoach(prompt: String, modelId: String): Flow<String> = flow {
        val apiKey = resolveGroqApiKey()
            ?: throw IllegalStateException("Groq API key not configured.")
        val model = AiProviderConfig.resolveGroqModelId(modelId)
        val body = JSONObject().apply {
            put("model", model)
            put("stream", true)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("temperature", 0.3)
            put("max_tokens", 1024)
        }
        streamOpenAiSse(GROQ_BASE_URL, body, apiKey).collect { emit(it) }
    }

    private fun streamOpenRouterCoach(prompt: String, modelId: String): Flow<String> = flow {
        val apiKey = secureStorageManager.getOpenRouterApiKey()?.trim().orEmpty()
            .takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("OpenRouter API key not configured.")
        val model = AiProviderConfig.resolveOpenRouterModelId(modelId)
        val body = JSONObject().apply {
            put("model", model)
            put("stream", true)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("temperature", 0.3)
            put("max_tokens", 1024)
        }
        streamOpenAiSse("$OPENROUTER_BASE/chat/completions", body, apiKey).collect { emit(it) }
    }

    private fun streamGeminiCoach(prompt: String, modelId: String): Flow<String> = flow {
        val apiKey = resolveGeminiApiKey()
            ?: throw IllegalStateException("Gemini API key not configured.")
        val model = AiProviderConfig.resolveGeminiModelId(modelId)
        val url = "$GEMINI_REST_BASE/models/$model:streamGenerateContent?alt=sse&key=$apiKey"
        val body = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })
            })
        }
        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException(parseGeminiErrorMessage(response.code, response.body?.string().orEmpty()))
            }
            response.body?.byteStream()?.bufferedReader()?.useLines { lines ->
                lines.forEach { line ->
                    if (!line.startsWith("data: ")) return@forEach
                    val payload = line.removePrefix("data: ").trim()
                    if (payload.isEmpty()) return@forEach
                    try {
                        val json = JSONObject(payload)
                        val text = json.optJSONArray("candidates")
                            ?.optJSONObject(0)
                            ?.optJSONObject("content")
                            ?.optJSONArray("parts")
                            ?.optJSONObject(0)
                            ?.optString("text")
                        if (!text.isNullOrEmpty()) emit(text)
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun streamOpenAiSse(url: String, body: JSONObject, apiKey: String): Flow<String> = flow {
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val err = response.body?.string().orEmpty()
                throw IllegalStateException("Stream failed (HTTP ${response.code}): $err")
            }
            response.body?.byteStream()?.bufferedReader()?.useLines { lines ->
                lines.forEach { line ->
                    if (!line.startsWith("data: ")) return@forEach
                    val payload = line.removePrefix("data: ").trim()
                    if (payload == "[DONE]") return@forEach
                    try {
                        val json = JSONObject(payload)
                        val delta = json.optJSONArray("choices")
                            ?.optJSONObject(0)
                            ?.optJSONObject("delta")
                            ?.optString("content")
                        if (!delta.isNullOrEmpty()) emit(delta)
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun generateExerciseSteps(
        exerciseName: String,
        provider: String,
        modelId: String,
        offlineModelId: String
    ): String? {
        val prompt = """
            You are a fitness coach. Give step-by-step setup and execution instructions for this exercise only: $exerciseName
            
            RULES:
            - Return ONLY a JSON array of strings
            - Each string MUST use the format: "Step:1 ...", "Step:2 ...", etc.
            - Cover starting position, movement path, breathing, and 1-2 form cues
            - Use 6-10 concise steps
            - Do NOT reference user stats, diet, or other exercises
            - Do not mention being an AI
            
            Example:
            ["Step:1 Set up...", "Step:2 Grip...", "Step:3 Pull..."]
        """.trimIndent()
        return runPromptRaw(prompt, provider, modelId, offlineModelId)
    }

    suspend fun estimateCardioCalories(
        exerciseName: String,
        durationSeconds: Int,
        age: Int,
        weightKg: Float,
        heightCm: Int,
        gender: String,
        provider: String,
        modelId: String,
        offlineModelId: String
    ): Int? {
        val minutes = durationSeconds / 60.0
        val prompt = """
            Estimate calories burned for this cardio session. Return ONLY an integer (kcal).
            Exercise: $exerciseName
            Duration: ${"%.1f".format(minutes)} minutes
            Age: ${age.coerceAtLeast(1)} years
            Weight: ${weightKg.coerceAtLeast(40f)} kg
            Height: ${heightCm.coerceAtLeast(100)} cm
            Gender: ${gender.ifBlank { "unspecified" }}
        """.trimIndent()
        val text = runPromptRaw(prompt, provider, modelId, offlineModelId) ?: return null
        return Regex("\\d+").find(text)?.value?.toIntOrNull()?.coerceIn(1, 5000)
    }

    suspend fun generateCoachPromptSuggestions(
        userContext: String,
        provider: String,
        modelId: String,
        offlineModelId: String
    ): List<com.example.CoachPromptChip>? {
        val contextForPrompt = if (provider == "offline") {
            userContext.lines().filter { it.isNotBlank() }.take(8).joinToString("\n").take(900)
        } else {
            userContext
        }
        val prompt = """
            You are Coach AI. Based on USER DATA, suggest 4-6 tap-to-send chat prompts.
            
            CRITICAL RULES:
            - Each "prompt" must be a first-person QUESTION the user asks the coach (e.g. "How can I hit my protein goal today?").
            - Use "I", "my", or "Can you" — never write coach answers, advice paragraphs, or statements.
            - Each prompt must end with "?".
            - "label" is a 2-3 word topic only (e.g. "Protein Gap", "Leg Day").
            
            Return ONLY JSON array:
            [{"label":"Protein Gap","prompt":"How can I close my protein gap today?"}]
            
            USER DATA:
            $contextForPrompt
        """.trimIndent()
        val text = runPromptRaw(prompt, provider, modelId, offlineModelId) ?: return null
        return parseCoachPromptChips(text)
    }

    private fun parseCoachPromptChips(jsonText: String): List<com.example.CoachPromptChip>? {
        val regex = Regex("\\[[\\s\\S]*\\]")
        val cleanJson = regex.find(jsonText)?.value ?: return null
        return try {
            val array = JSONArray(cleanJson)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val label = obj.optString("label", "").trim()
                    var message = obj.optString("prompt", obj.optString("message", "")).trim()
                    if (label.isBlank() || message.isBlank()) continue
                    message = normalizeCoachPromptQuestion(label, message)
                    if (message.isBlank()) continue
                    add(com.example.CoachPromptChip(label.take(24), message))
                }
            }.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizeCoachPromptQuestion(label: String, raw: String): String {
        var text = raw.trim().trim('"')
        if (looksLikeCoachAnswer(text)) {
            text = "Can you help me with ${label.lowercase()}?"
        }
        if (!text.contains("?")) {
            text = text.trimEnd('.', '!') + "?"
        }
        val lower = text.lowercase()
        if (!lower.startsWith("how") && !lower.startsWith("what") && !lower.startsWith("can") &&
            !lower.startsWith("should") && !lower.startsWith("am") && !lower.startsWith("is") &&
            !lower.startsWith("do") && !lower.startsWith("help") && !lower.startsWith("i ")
        ) {
            text = "How can I improve my ${label.lowercase()}?"
        }
        return text
    }

    private fun looksLikeCoachAnswer(text: String): Boolean {
        val lower = text.lowercase()
        return lower.startsWith("you should") ||
            lower.startsWith("try ") ||
            lower.startsWith("i recommend") ||
            lower.startsWith("great job") ||
            lower.startsWith("based on your") && !lower.contains("?") ||
            lower.startsWith("consider ") ||
            (lower.length > 80 && !lower.contains("?"))
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
                val type = obj.optString("type", "Full Body")
                val isCardio = obj.optBoolean("isCardio", false) ||
                    type.equals("Cardio", ignoreCase = true)
                items.add(
                    ExerciseItem(
                        id = "ai_ex_${name.hashCode()}_$i",
                        name = name,
                        exerciseType = if (isCardio) "Cardio" else type,
                        defaultSets = if (isCardio) 1 else obj.optInt("sets", 3),
                        defaultReps = if (isCardio) 0 else obj.optInt("reps", 10),
                        isCustom = true,
                        isCardio = isCardio
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exercise JSON parse failed", e)
        }
        return items
    }

    suspend fun releaseOfflineEngine() {
        offlineEngineLock.withLock {
            offlineEngine.release()
        }
    }

    companion object {
        private const val TAG = "AiManager"
        private const val GROQ_BASE_URL = "https://api.groq.com/openai/v1/chat/completions"
        private const val OPENROUTER_BASE = OpenRouterApiClient.OPENROUTER_BASE
        private const val GEMINI_REST_BASE = "https://generativelanguage.googleapis.com/v1beta"
    }
}
