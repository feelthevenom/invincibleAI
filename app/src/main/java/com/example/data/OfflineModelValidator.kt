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
        val isBuiltIn: Boolean
    )

    sealed class ValidationResult {
        data class Valid(val capabilities: ModelCapabilities) : ValidationResult()
        data class Invalid(val message: String) : ValidationResult()
    }

    fun inferCapabilities(fileName: String, fileSizeBytes: Long, isBuiltIn: Boolean = false): ModelCapabilities {
        OfflineModelConfig.specForBuiltInFile(fileName)?.let { spec ->
            return ModelCapabilities(
                supportsVision = spec.supportsVision,
                supportsText = true,
                displayName = spec.displayName,
                minRamGb = spec.minRamGb,
                isBuiltIn = true
            )
        }
        val lower = fileName.lowercase()
        val supportsVision = lower.contains("e4b") || lower.contains("4b-it") ||
            lower.contains("3n") || lower.contains("vision") || lower.contains("multimodal")
        val minRam = when {
            lower.contains("e4b") || lower.contains("4b") -> 5.0
            lower.contains("e2b") || lower.contains("2b") -> 4.0
            fileSizeBytes > 3_200_000_000L -> 5.0
            else -> 4.0
        }
        val displayName = fileName
            .removeSuffix(".litertlm")
            .removeSuffix(".LITERTLM")
            .replace('-', ' ')
            .replace('_', ' ')
        return ModelCapabilities(
            supportsVision = supportsVision,
            supportsText = true,
            displayName = displayName,
            minRamGb = minRam,
            isBuiltIn = isBuiltIn
        )
    }

    suspend fun validateImportedFile(context: Context, file: File): ValidationResult = withContext(Dispatchers.IO) {
        if (!file.name.endsWith(".litertlm", ignoreCase = true)) {
            return@withContext ValidationResult.Invalid(
                "Unsupported file format. Only .litertlm LiteRT-LM models can be imported."
            )
        }
        val lowerName = file.name.lowercase()
        if (lowerName.contains("qualcomm") || lowerName.contains("qnn") || lowerName.contains(".bin")) {
            return@withContext ValidationResult.Invalid(
                "Unsupported model — \"${file.name}\" is a device-specific or non-LiteRT-LM export. " +
                    "Import Gemma 4 E2B/E4B .litertlm models from the download list or a compatible LiteRT-LM build."
            )
        }
        if (file.length() < 50_000_000L) {
            return@withContext ValidationResult.Invalid(
                "File is too small (${file.length() / 1_000_000} MB). Import a complete .litertlm LLM model."
            )
        }
        val known = OfflineModelConfig.ALL.any { it.fileName.equals(file.name, ignoreCase = true) }
        if (known) {
            val spec = OfflineModelConfig.ALL.first { it.fileName.equals(file.name, ignoreCase = true) }
            if (!OfflineModelConfig.isValidModelFile(spec, file.length())) {
                return@withContext ValidationResult.Invalid(
                    "Incomplete ${spec.displayName} file. Re-download or re-import the full model."
                )
            }
        }
        val probe = OfflineLlmEngine(context.applicationContext)
        try {
            if (!probe.probeModel(file.absolutePath)) {
                return@withContext ValidationResult.Invalid(
                    "Unsupported model — this app cannot load \"${file.name}\". " +
                        "Use Gemma 4 E2B/E4B .litertlm models from the download list or a compatible LiteRT-LM export."
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Model probe failed for ${file.name}", e)
            return@withContext ValidationResult.Invalid(
                "Unsupported model — LiteRT-LM could not initialize this file. " +
                    "Wrong format or incompatible model for this app."
            )
        } finally {
            probe.release()
        }
        ValidationResult.Valid(inferCapabilities(file.name, file.length()))
    }

    fun capabilityLabel(cap: ModelCapabilities): String = buildString {
        append(if (cap.supportsText) "Text" else "")
        if (cap.supportsVision) {
            if (isNotEmpty()) append(" + ")
            append("Vision")
        }
        if (isEmpty()) append("Unknown")
    }

    private const val TAG = "OfflineModelValidator"
}
