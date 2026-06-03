package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class AiManager(
    private val context: Context,
    private val secureStorageManager: SecureStorageManager
) {
    private var llmInference: LlmInference? = null
    private var currentModelPath: String? = null

    /**
     * Generate food suggestions using the appropriate AI model (online or offline).
     * Parses the JSON response to return a list of FoodItem objects.
     */
    suspend fun generateFoodSuggestions(
        query: String,
        aiMode: String
    ): List<FoodItem> = withContext(Dispatchers.IO) {
        val prompt = """
            You are a helpful nutrition assistant. The user wants to add a custom food item named "$query".
            Provide 4 common variations or closely related foods along with their macros per 100g.
            Return ONLY a valid JSON array of objects. Do not wrap in markdown blocks. Do not add any text before or after the JSON.
            Example format:
            [
              {"name": "Peanut", "calories": 567, "protein": 25.8, "carbs": 16.1, "fat": 49.2, "fiber": 8.5},
              {"name": "Peanut Butter", "calories": 588, "protein": 25.1, "carbs": 20.0, "fat": 50.4, "fiber": 6.0}
            ]
        """.trimIndent()

        val responseText = try {
            when {
                aiMode == "online" -> callGemini(prompt)
                aiMode.startsWith("offline") -> callGemma(prompt, aiMode)
                else -> null
            }
        } catch (e: Exception) {
            Log.e("AiManager", "Error generating suggestions", e)
            null
        }

        parseAiFoodResponse(responseText)
    }

    /**
     * Analyze an image to detect foods and estimate their macros per 100g.
     */
    suspend fun analyzeFoodImage(
        bitmap: Bitmap,
        aiMode: String
    ): List<FoodItem> = withContext(Dispatchers.IO) {
        val prompt = """
            You are an expert nutritionist. Analyze this image of a meal.
            Identify the distinct food items present.
            For each item, estimate its nutritional value per 100g.
            Return ONLY a valid JSON array of objects. Do not wrap in markdown blocks. Do not add any text before or after the JSON.
            Example format:
            [
              {"name": "Chapati", "calories": 297, "protein": 11.0, "carbs": 46.0, "fat": 7.0, "fiber": 4.9},
              {"name": "Paneer Butter Masala", "calories": 265, "protein": 14.0, "carbs": 8.0, "fat": 20.0, "fiber": 1.0}
            ]
        """.trimIndent()

        val responseText = try {
            when {
                aiMode == "online" -> callGeminiWithImage(prompt, bitmap)
                aiMode.startsWith("offline") -> {
                    // MediaPipe Gemma doesn't currently support image inputs in the Android SDK 
                    // out of the box in the stable `tasks-genai` package for pure LlmInference.
                    // To handle the multimodal offline requirement, we would need to pass image data,
                    // but since the SDK limitation exists, we log a warning or throw.
                    // For the sake of the exercise, we will assume standard text prompt if multimodal isn't available
                    // or we fallback. If the SDK supports `generateResponse(image, prompt)` we'd use it.
                    // As of tasks-genai 0.10.18, LlmInference primarily takes text.
                    throw UnsupportedOperationException("Offline Gemma Image Analysis is not fully supported by the current MediaPipe SDK.")
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.e("AiManager", "Error analyzing image", e)
            null
        }

        parseAiFoodResponse(responseText)
    }

    private suspend fun callGemini(prompt: String): String? {
        val apiKey = secureStorageManager.getGeminiApiKey()
        if (apiKey.isNullOrBlank()) {
            throw IllegalStateException("Gemini API key not configured")
        }

        val generativeModel = GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = apiKey
        )
        
        val response = generativeModel.generateContent(prompt)
        return response.text
    }

    private suspend fun callGeminiWithImage(prompt: String, bitmap: Bitmap): String? {
        val apiKey = secureStorageManager.getGeminiApiKey()
        if (apiKey.isNullOrBlank()) {
            throw IllegalStateException("Gemini API key not configured")
        }

        val generativeModel = GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = apiKey
        )
        
        val inputContent = content {
            image(bitmap)
            text(prompt)
        }

        val response = generativeModel.generateContent(inputContent)
        return response.text
    }

    private fun callGemma(prompt: String, mode: String): String? {
        val modelName = if (mode == "offline_4b") "gemma-4b.bin" else "gemma-2b.bin"
        val modelFile = File(context.filesDir, "models/$modelName")
        
        if (!modelFile.exists()) {
            throw IllegalStateException("Offline model not downloaded")
        }

        if (llmInference == null || currentModelPath != modelFile.absolutePath) {
            llmInference?.close()
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .build()
            llmInference = LlmInference.createFromOptions(context, options)
            currentModelPath = modelFile.absolutePath
        }

        return llmInference?.generateResponse(prompt)
    }

    private fun parseAiFoodResponse(jsonText: String?): List<FoodItem> {
        if (jsonText.isNullOrBlank()) return emptyList()

        val cleanJson = jsonText
            .replace("```json", "")
            .replace("```", "")
            .trim()

        val items = mutableListOf<FoodItem>()
        try {
            val array = JSONArray(cleanJson)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val name = obj.optString("name", "Unknown Item")
                val cal = obj.optDouble("calories", 0.0).toInt()
                val pro = obj.optDouble("protein", 0.0).toFloat()
                val carbs = obj.optDouble("carbs", 0.0).toFloat()
                val fat = obj.optDouble("fat", 0.0).toFloat()
                val fiber = obj.optDouble("fiber", 0.0).toFloat()

                items.add(
                    FoodItem(
                        id = "ai_${name.hashCode()}_${System.currentTimeMillis()}",
                        name = name,
                        caloriesPer100g = cal,
                        proteinPer100g = pro,
                        carbsPer100g = carbs,
                        fatPer100g = fat,
                        fiberPer100g = fiber,
                        isCustom = true,
                        source = "ai_generated"
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("AiManager", "Failed to parse JSON: $cleanJson", e)
        }
        return items
    }
}
