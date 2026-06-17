package com.example.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object OfflineModelValidator {

    data class ModelCapabilities(
        val supportsVision: Boolean,
        val supportsText: Boolean,
        val displayName: String,
        val minRamGb: Double,
        val isBuiltIn: Boolean,
        val probeUsedVisionBackend: Boolean = false
    )

    sealed class ValidationResult {
        data class Valid(val capabilities: ModelCapabilities) : ValidationResult()
        data class Invalid(val message: String) : ValidationResult()
    }

    fun likelyVisionModel(fileName: String): Boolean {
        val lower = fileName.lowercase()
        return lower.contains("vlm") || lower.contains("fastvlm") ||
            lower.contains("vision") || lower.contains("multimodal") ||
            lower.contains("e4b") || lower.contains("4b-it") ||
            lower.contains("gemma-4-e4b") || lower.contains("3n") ||
            lower.contains("image-text") || lower.contains("any-to-any")
    }

    fun likelyEmbeddingModel(fileName: String): Boolean {
        val lower = fileName.lowercase()
        return lower.contains("embedding") || lower.contains("embedgemma") ||
            lower.contains("feature-extraction") || lower.contains("functiongemma")
    }

    fun inferCapabilities(
        fileName: String,
        fileSizeBytes: Long,
        isBuiltIn: Boolean = false,
        metadata: OfflineModelMetadata? = null
    ): ModelCapabilities {
        metadata?.let {
            return ModelCapabilities(
                supportsVision = it.supportsVision,
                supportsText = it.supportsText,
                displayName = displayNameFromFile(fileName),
                minRamGb = DeviceModelCapability.estimateRequiredRamGb(fileSizeBytes, fileName),
                isBuiltIn = isBuiltIn,
                probeUsedVisionBackend = it.probeUsedVisionBackend
            )
        }
        OfflineModelConfig.specForBuiltInFile(fileName)?.let { spec ->
            return ModelCapabilities(
                supportsVision = spec.supportsVision,
                supportsText = true,
                displayName = spec.displayName,
                minRamGb = spec.minRamGb,
                isBuiltIn = true
            )
        }
        OfflineModelConfig.matchImportedFile(fileName, fileSizeBytes)?.let { spec ->
            return ModelCapabilities(
                supportsVision = spec.supportsVision,
                supportsText = true,
                displayName = spec.displayName,
                minRamGb = spec.minRamGb,
                isBuiltIn = false
            )
        }
        val supportsVision = likelyVisionModel(fileName)
        val supportsText = !likelyEmbeddingModel(fileName)
        return ModelCapabilities(
            supportsVision = supportsVision,
            supportsText = supportsText,
            displayName = displayNameFromFile(fileName),
            minRamGb = DeviceModelCapability.estimateRequiredRamGb(fileSizeBytes, fileName),
            isBuiltIn = isBuiltIn,
            probeUsedVisionBackend = supportsVision
        )
    }

    suspend fun validateImportedFile(
        context: Context,
        file: File,
        revalidateInstalled: Boolean = false
    ): ValidationResult = withContext(Dispatchers.IO) {
        if (!file.name.endsWith(".litertlm", ignoreCase = true)) {
            return@withContext ValidationResult.Invalid(
                "Unsupported file format. Only .litertlm LiteRT-LM models can be imported."
            )
        }
        if (likelyEmbeddingModel(file.name)) {
            return@withContext ValidationResult.Invalid(
                "\"${file.name}\" is an embedding or task-specific model, not a chat LLM. " +
                    "Import text or vision LLMs from https://huggingface.co/models?library=litert-lm"
            )
        }
        if (file.length() < 20_000_000L) {
            return@withContext ValidationResult.Invalid(
                "File is too small (${file.length() / 1_000_000} MB). Import a complete .litertlm LLM model."
            )
        }

        OfflineModelMetadata.load(file)?.takeIf { !revalidateInstalled }?.let { meta ->
            return@withContext ValidationResult.Valid(
                inferCapabilities(file.name, file.length(), metadata = meta)
            )
        }

        val capability = DeviceModelCapability.assessForImport(context, file.length(), file.name)
        if (!capability.canLoad) {
            return@withContext ValidationResult.Invalid(
                capability.message ?: "This device cannot load this model."
            )
        }

        OfflineModelConfig.matchImportedFile(file.name, file.length())?.let { spec ->
            if (!OfflineModelConfig.isValidModelFile(spec, file.length())) {
                return@withContext ValidationResult.Invalid(
                    "Incomplete ${spec.displayName} file. Re-download or re-import the full model."
                )
            }
            val meta = OfflineModelMetadata(
                supportsVision = spec.supportsVision,
                supportsText = true,
                probeUsedVisionBackend = spec.supportsVision
            )
            OfflineModelMetadata.save(file, meta)
            return@withContext ValidationResult.Valid(
                inferCapabilities(file.name, file.length(), metadata = meta)
            )
        }

        if (DeviceModelCapability.isTrustedGemma4Model(file.name, file.length())) {
            val meta = OfflineModelMetadata(
                supportsVision = likelyVisionModel(file.name),
                supportsText = true,
                probeUsedVisionBackend = likelyVisionModel(file.name)
            )
            OfflineModelMetadata.save(file, meta)
            return@withContext ValidationResult.Valid(
                inferCapabilities(file.name, file.length(), metadata = meta)
            )
        }

        val probe = OfflineLlmEngine(context.applicationContext)
        try {
            val result = probe.probeModel(file.absolutePath, file.name)
            if (result.success) {
                val meta = OfflineModelMetadata(
                    supportsVision = result.supportsVision,
                    supportsText = result.supportsText,
                    probeUsedVisionBackend = result.useVisionBackendAtInit
                )
                OfflineModelMetadata.save(file, meta)
                return@withContext ValidationResult.Valid(
                    inferCapabilities(file.name, file.length(), metadata = meta)
                )
            }

            if (DeviceModelCapability.isLikelyLiteRtLmChatModel(file.name, file.length())) {
                Log.w(TAG, "Probe failed for ${file.name}; accepting based on file signature")
                val meta = OfflineModelMetadata(
                    supportsVision = likelyVisionModel(file.name),
                    supportsText = true,
                    probeUsedVisionBackend = likelyVisionModel(file.name)
                )
                OfflineModelMetadata.save(file, meta)
                return@withContext ValidationResult.Valid(
                    inferCapabilities(file.name, file.length(), metadata = meta)
                )
            }

            ValidationResult.Invalid(
                "Unsupported model — LiteRT-LM could not load \"${file.name}\". " +
                    "Use a chat or vision LLM from https://huggingface.co/models?library=litert-lm " +
                    "(e.g. Gemma 4, FastVLM, Qwen3)."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Model probe failed for ${file.name}", e)
            if (DeviceModelCapability.isLikelyLiteRtLmChatModel(file.name, file.length())) {
                val meta = OfflineModelMetadata(
                    supportsVision = likelyVisionModel(file.name),
                    supportsText = true,
                    probeUsedVisionBackend = likelyVisionModel(file.name)
                )
                OfflineModelMetadata.save(file, meta)
                ValidationResult.Valid(inferCapabilities(file.name, file.length(), metadata = meta))
            } else {
                ValidationResult.Invalid(
                    "Unsupported model — LiteRT-LM could not initialize this file."
                )
            }
        } finally {
            probe.release()
        }
    }

    fun capabilityLabel(cap: ModelCapabilities): String = buildString {
        append(if (cap.supportsText) "Text" else "")
        if (cap.supportsVision) {
            if (isNotEmpty()) append(" + ")
            append("Vision")
        }
        if (isEmpty()) append("Unsupported")
    }

    private fun displayNameFromFile(fileName: String): String =
        fileName
            .removeSuffix(".litertlm")
            .removeSuffix(".LITERTLM")
            .replace('-', ' ')
            .replace('_', ' ')

    private const val TAG = "OfflineModelValidator"
}
