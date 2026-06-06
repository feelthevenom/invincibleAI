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
        val contextWindow: String = "",
        val isFree: Boolean = false
    )

    val PROVIDERS = listOf("gemini", "groq", "openrouter", "offline")

    val GEMINI_MODELS = listOf(
        AiModel("gemini-2.5-flash", "Gemini 2.5 Flash", supportsVision = true, contextWindow = "1M"),
        AiModel("gemini-2.5-flash-lite", "Gemini 2.5 Flash Lite", supportsVision = true, contextWindow = "1M"),
        AiModel("gemini-2.0-flash", "Gemini 2.0 Flash", supportsVision = true, contextWindow = "1M"),
        AiModel("gemini-2.0-flash-lite", "Gemini 2.0 Flash Lite", supportsVision = true, contextWindow = "1M"),
        AiModel("gemini-1.5-flash", "Gemini 1.5 Flash", supportsVision = true, contextWindow = "1M"),
        AiModel("gemini-1.5-pro", "Gemini 1.5 Pro", supportsVision = true, contextWindow = "2M", isFree = false),
    )

    /** Maps stored model id to a supported Gemini model name; falls back to 2.5 Flash. */
    fun resolveGeminiModelId(modelId: String): String {
        if (modelId.isBlank()) return "gemini-2.5-flash"
        if (findModel("gemini", modelId) != null) return modelId
        if (modelId.startsWith("gemini-")) return modelId
        return "gemini-2.5-flash"
    }

    /** Groq vision models (image + text). */
    val GROQ_VISION_MODELS = listOf(
        AiModel(
            "meta-llama/llama-4-scout-17b-16e-instruct",
            "Llama 4 Scout 17B",
            supportsVision = true,
            contextWindow = "128K",
            isFree = true
        ),
    )

    /** Groq text models for split-mode text slot. */
    val GROQ_TEXT_MODELS = listOf(
        AiModel("llama-3.3-70b-versatile", "Llama 3.3 70B Versatile", supportsVision = false, contextWindow = "131K", isFree = true),
        AiModel("llama-3.1-8b-instant", "Llama 3.1 8B Instant", supportsVision = false, contextWindow = "131K", isFree = true),
        AiModel("openai/gpt-oss-120b", "GPT-OSS 120B", supportsVision = false, contextWindow = "131K", isFree = true),
        AiModel("openai/gpt-oss-20b", "GPT-OSS 20B", supportsVision = false, contextWindow = "131K", isFree = true),
        AiModel("qwen/qwen3-32b", "Qwen3 32B", supportsVision = false, contextWindow = "131K", isFree = true),
        AiModel("groq/compound", "Groq Compound", supportsVision = false, isFree = true),
        AiModel("groq/compound-mini", "Groq Compound Mini", supportsVision = false, isFree = true),
    )

    /** @deprecated use [GROQ_VISION_MODELS] or [GROQ_TEXT_MODELS] */
    val GROQ_MODELS = GROQ_VISION_MODELS

    private const val DEFAULT_GROQ_VISION_MODEL = "meta-llama/llama-4-scout-17b-16e-instruct"
    private const val DEFAULT_GROQ_TEXT_MODEL = "llama-3.3-70b-versatile"

    fun resolveGroqModelId(modelId: String, vision: Boolean = true): String {
        if (modelId.isBlank()) return if (vision) DEFAULT_GROQ_VISION_MODEL else DEFAULT_GROQ_TEXT_MODEL
        val catalog = if (vision) GROQ_VISION_MODELS else GROQ_TEXT_MODELS
        if (catalog.any { it.id == modelId }) return modelId
        return if (vision) DEFAULT_GROQ_VISION_MODEL else DEFAULT_GROQ_TEXT_MODEL
    }

    fun groqVisionModels(): List<AiModel> = GROQ_VISION_MODELS

    fun textModelsFor(provider: String): List<AiModel> = when (provider) {
        "gemini" -> GEMINI_MODELS
        "groq" -> GROQ_TEXT_MODELS
        "openrouter" -> OpenRouterModelStore.textModels()
        else -> emptyList()
    }

    fun visionModelsFor(provider: String): List<AiModel> = when (provider) {
        "gemini" -> GEMINI_MODELS
        "groq" -> GROQ_VISION_MODELS
        "openrouter" -> OpenRouterModelStore.visionModels()
        else -> emptyList()
    }

    fun defaultModelFor(provider: String, vision: Boolean): String = when (provider) {
        "gemini" -> "gemini-2.5-flash"
        "groq" -> if (vision) DEFAULT_GROQ_VISION_MODEL else DEFAULT_GROQ_TEXT_MODEL
        "openrouter" -> if (vision) {
            OpenRouterModelStore.defaultVisionModelId()
        } else {
            OpenRouterModelStore.defaultTextModelId()
        }
        else -> ""
    }

    fun displayNameFor(provider: String): String = when (provider) {
        "gemini" -> "Gemini"
        "groq" -> "Groq"
        "openrouter" -> "OpenRouter"
        "offline" -> "Offline"
        else -> provider
    }

    fun providerSubtitle(provider: String): String = when (provider) {
        "gemini" -> "Google multimodal cloud"
        "groq" -> "High-speed inference cloud"
        "openrouter" -> "Multi-provider model gateway"
        "offline" -> "On-device private inference"
        else -> ""
    }

    fun modelsFor(provider: String): List<AiModel> = when (provider) {
        "gemini" -> GEMINI_MODELS
        "groq" -> GROQ_VISION_MODELS
        "openrouter" -> OpenRouterModelStore.unifiedModels()
        else -> emptyList()
    }

    fun findModel(provider: String, modelId: String): AiModel? =
        if (provider == "openrouter") {
            OpenRouterModelStore.findModel(modelId)
        } else {
            modelsFor(provider).find { it.id == modelId }
                ?: textModelsFor(provider).find { it.id == modelId }
                ?: visionModelsFor(provider).find { it.id == modelId }
        }

    fun modelDisplayName(provider: String, modelId: String): String =
        findModel(provider, modelId)?.displayName ?: modelId

    fun supportsVision(provider: String, modelId: String): Boolean = when (provider) {
        "offline" -> modelId.isNotBlank()
        "groq" -> GROQ_VISION_MODELS.any { it.id == modelId } ||
            resolveGroqModelId(modelId, vision = true) == modelId
        "openrouter" -> OpenRouterModelStore.findModel(modelId)?.supportsVision == true
        else -> findModel(provider, modelId)?.supportsVision ?: modelId.startsWith("gemini-")
    }

    fun resolveOpenRouterModelId(modelId: String, vision: Boolean = false): String {
        if (modelId.isNotBlank() && OpenRouterModelStore.findModel(modelId) != null) return modelId
        return if (vision) OpenRouterModelStore.defaultVisionModelId() else OpenRouterModelStore.defaultTextModelId()
    }

    fun isFreeModel(provider: String, model: AiModel): Boolean = when {
        model.isFree -> true
        provider == "openrouter" && model.id.endsWith(":free", ignoreCase = true) -> true
        else -> false
    }

    fun modelWheelLabel(provider: String, model: AiModel): String {
        val vision = if (model.supportsVision) " · vision" else " · text only"
        val freeTag = if (isFreeModel(provider, model)) " · free" else ""
        return "${model.displayName}$vision$freeTag"
    }

    fun offlineWheelLabels(): List<String> = OfflineModelConfig.ALL.map { spec ->
        val sizeGb = spec.expectedSizeBytes / 1_000_000_000.0
        "${spec.displayName} (${String.format("%.1f", sizeGb)} GB)"
    }
}

data class AiSlot(
    val provider: String,
    val modelId: String,
    val offlineModelId: String
)

object AiRouteResolver {
    fun textSlot(profile: UserProfile): AiSlot = if (profile.aiSplitModels) {
        AiSlot(profile.aiTextProvider, profile.aiTextModelId, profile.offlineModelId)
    } else {
        AiSlot(profile.aiProvider, profile.aiModelId, profile.offlineModelId)
    }

    fun visionSlot(profile: UserProfile): AiSlot = if (profile.aiSplitModels) {
        AiSlot(profile.aiVisionProvider, profile.aiVisionModelId, profile.offlineModelId)
    } else {
        AiSlot(profile.aiProvider, profile.aiModelId, profile.offlineModelId)
    }

    /** Online API slots that need a key + model (text, vision, or unified). */
    fun onlineApiChecks(profile: UserProfile): List<OnlineApiSlotCheck> {
        if (profile.aiSplitModels) {
            return buildList {
                if (profile.aiTextProvider != "offline") {
                    add(OnlineApiSlotCheck(profile.aiTextProvider, profile.aiTextModelId, "Text"))
                }
                if (profile.aiVisionProvider != "offline") {
                    add(OnlineApiSlotCheck(profile.aiVisionProvider, profile.aiVisionModelId, "Vision"))
                }
            }
        }
        if (profile.aiProvider == "offline") return emptyList()
        return listOf(OnlineApiSlotCheck(profile.aiProvider, profile.aiModelId, "Unified"))
    }

    fun needsOnlineApi(profile: UserProfile): Boolean = onlineApiChecks(profile).isNotEmpty()
}

data class OnlineApiSlotCheck(
    val provider: String,
    val modelId: String,
    val slotLabel: String
)
