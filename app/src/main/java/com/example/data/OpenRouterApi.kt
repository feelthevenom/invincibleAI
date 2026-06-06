package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object OpenRouterModelStore {
    @Volatile
    private var models: List<AiProviderConfig.AiModel> = emptyList()

    fun update(fetched: List<AiProviderConfig.AiModel>) {
        models = fetched
    }

    fun clear() {
        models = emptyList()
    }

    fun allModels(): List<AiProviderConfig.AiModel> = models

    fun textModels(): List<AiProviderConfig.AiModel> = models.filter { !it.supportsVision }

    fun visionModels(): List<AiProviderConfig.AiModel> = models.filter { it.supportsVision }

    fun unifiedModels(): List<AiProviderConfig.AiModel> = visionModels().ifEmpty { models }

    fun findModel(modelId: String): AiProviderConfig.AiModel? = models.find { it.id == modelId }

    fun defaultTextModelId(): String = textModels().firstOrNull()?.id.orEmpty()

    fun defaultVisionModelId(): String = visionModels().firstOrNull()?.id.orEmpty()

    fun defaultUnifiedModelId(): String = unifiedModels().firstOrNull()?.id.orEmpty()
}

class OpenRouterApiClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()
) {
    suspend fun fetchModels(apiKey: String): List<AiProviderConfig.AiModel> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$OPENROUTER_BASE/models")
            .addHeader("Authorization", "Bearer $apiKey")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException(parseErrorMessage(response.code, body))
            }
            parseModels(body)
        }
    }

    fun parseModels(jsonBody: String): List<AiProviderConfig.AiModel> {
        val root = JSONObject(jsonBody)
        val data = root.optJSONArray("data") ?: JSONArray()
        val parsed = buildList {
            for (i in 0 until data.length()) {
                val obj = data.optJSONObject(i) ?: continue
                val id = obj.optString("id", "").trim()
                if (id.isBlank()) continue
                val name = obj.optString("name", id).trim().ifBlank { id }
                val architecture = obj.optJSONObject("architecture")
                val supportsVision = supportsVision(architecture)
                val context = obj.optJSONObject("top_provider")?.optInt("context_length")
                    ?: obj.optInt("context_length", 0)
                val contextLabel = if (context > 0) formatContext(context) else ""
                val pricing = obj.optJSONObject("pricing")
                val isFree = isFreeOpenRouterModel(id, pricing)
                add(
                    AiProviderConfig.AiModel(
                        id = id,
                        displayName = name,
                        supportsVision = supportsVision,
                        contextWindow = contextLabel,
                        isFree = isFree
                    )
                )
            }
        }
        return parsed
            .distinctBy { it.id }
            .sortedBy { it.displayName.lowercase() }
    }

    private fun isFreeOpenRouterModel(id: String, pricing: JSONObject?): Boolean {
        if (id.endsWith(":free", ignoreCase = true)) return true
        if (pricing == null) return false
        return isZeroPrice(pricing.optString("prompt", "1")) &&
            isZeroPrice(pricing.optString("completion", "1"))
    }

    private fun isZeroPrice(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isEmpty() || trimmed == "0") return true
        return trimmed.toDoubleOrNull()?.let { it == 0.0 } == true
    }

    private fun supportsVision(architecture: JSONObject?): Boolean {
        if (architecture == null) return false
        val modality = architecture.optString("modality", "").lowercase()
        if (modality.contains("image")) return true
        val inputs = architecture.optJSONArray("input_modalities") ?: return false
        for (i in 0 until inputs.length()) {
            if (inputs.optString(i).contains("image", ignoreCase = true)) return true
        }
        return false
    }

    private fun formatContext(length: Int): String = when {
        length >= 1_000_000 -> "${length / 1_000_000}M"
        length >= 1_000 -> "${length / 1_000}K"
        else -> length.toString()
    }

    private fun parseErrorMessage(code: Int, body: String): String = try {
        val err = JSONObject(body).optJSONObject("error")
        val msg = err?.optString("message", "").orEmpty()
        when {
            code == 401 -> "Invalid OpenRouter API key. Create a key at openrouter.ai/keys."
            msg.isNotBlank() -> "OpenRouter API error ($code): $msg"
            else -> "OpenRouter API error ($code): $body"
        }
    } catch (_: Exception) {
        "OpenRouter API error ($code): $body"
    }

    companion object {
        const val OPENROUTER_BASE = "https://openrouter.ai/api/v1"
    }
}
