package com.example.data

/**
 * Online AI providers and model catalog.
 * Offline models: [OfflineModelConfig].
 */
object AiProviderConfig {

    data class AiModel(
        val id: String,
        val displayName: String,
        val supportsVision: Boolean,
        val contextWindow: String = ""
    )

    val PROVIDERS = listOf("gemini", "groq", "offline")

    val GEMINI_MODELS = listOf(
        AiModel("gemini-2.0-flash", "Gemini 2.0 Flash", supportsVision = true, contextWindow = "1M"),
        AiModel("gemini-2.0-flash-lite", "Gemini 2.0 Flash Lite", supportsVision = true, contextWindow = "1M"),
        AiModel("gemini-1.5-flash", "Gemini 1.5 Flash", supportsVision = true, contextWindow = "1M"),
        AiModel("gemini-1.5-pro", "Gemini 1.5 Pro", supportsVision = true, contextWindow = "2M"),
    )

    /** Groq free-tier models (OpenAI-compatible, text only). */
    val GROQ_MODELS = listOf(
        AiModel("llama-3.3-70b-versatile", "Llama 3.3 70B Versatile", supportsVision = false),
        AiModel("llama-3.1-8b-instant", "Llama 3.1 8B Instant", supportsVision = false),
        AiModel("gemma2-9b-it", "Gemma 2 9B IT", supportsVision = false),
        AiModel("mixtral-8x7b-32768", "Mixtral 8x7B", supportsVision = false),
        AiModel("meta-llama/llama-4-scout-17b-16e-instruct", "Llama 4 Scout 17B", supportsVision = false),
    )

    fun displayNameFor(provider: String): String = when (provider) {
        "gemini" -> "Gemini"
        "groq" -> "Groq"
        "offline" -> "Offline (On-Device)"
        else -> provider
    }

    fun modelsFor(provider: String): List<AiModel> = when (provider) {
        "gemini" -> GEMINI_MODELS
        "groq" -> GROQ_MODELS
        else -> emptyList()
    }

    fun findModel(provider: String, modelId: String): AiModel? =
        modelsFor(provider).find { it.id == modelId }

    fun supportsVision(provider: String, modelId: String): Boolean = when (provider) {
        "offline" -> modelId.isNotBlank() // resolved at runtime via installed models
        else -> findModel(provider, modelId)?.supportsVision ?: false
    }

    fun modelWheelLabel(provider: String, model: AiModel): String {
        val vision = if (model.supportsVision) " · vision" else " · text only"
        return "${model.displayName}$vision"
    }

    fun offlineWheelLabels(): List<String> = OfflineModelConfig.ALL.map { spec ->
        val sizeGb = spec.expectedSizeBytes / 1_000_000_000.0
        "${spec.displayName} (${String.format("%.1f", sizeGb)} GB)"
    }
}
