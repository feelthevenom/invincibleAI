package com.example.data

import android.app.ActivityManager
import android.content.Context

/** Estimates whether the device can load an on-device LiteRT-LM model. */
object DeviceModelCapability {

    data class Assessment(
        val canLoad: Boolean,
        val requiredRamGb: Double,
        val totalRamGb: Double,
        val availableRamGb: Double,
        val modelFileGb: Double,
        val message: String?
    )

    fun totalRamGb(context: Context): Double {
        val mem = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mem)
        return mem.totalMem / GB
    }

    fun availableRamGb(context: Context): Double {
        val mem = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mem)
        return mem.availMem / GB
    }

    /**
     * Heuristic: model weights + runtime overhead + headroom for Android.
     * Larger files need proportionally more RAM for KV cache and temp buffers.
     */
    fun estimateRequiredRamGb(fileSizeBytes: Long, fileName: String = ""): Double {
        OfflineModelConfig.specForBuiltInFile(fileName)?.minRamGb?.let { return it }
        val lower = fileName.lowercase()
        when {
            lower.contains("12b") || lower.contains("14b") || lower.contains("8b") ->
                return 10.0.coerceAtLeast(fileSizeGb(fileSizeBytes) * 1.8 + 2.0)
            lower.contains("fastvlm") && (lower.contains("0.5b") || lower.contains("0.5")) -> 2.5
            lower.contains("e4b") || (lower.contains("4b") && lower.contains("gemma")) -> 5.5
            lower.contains("e2b") || (lower.contains("2b") && lower.contains("gemma")) -> 4.0
            lower.contains("270m") || lower.contains("0.5b") || lower.contains("0.6b") -> 3.0
            lower.contains("1.5b") || lower.contains("1b") -> 3.5
        }
        val fileGb = fileSizeGb(fileSizeBytes)
        return when {
            fileGb >= 3.2 -> 6.0
            fileGb >= 2.2 -> 4.5
            fileGb >= 1.0 -> 3.5
            else -> 3.0
        }
    }

    fun assess(context: Context, fileSizeBytes: Long, fileName: String = ""): Assessment {
        val required = estimateRequiredRamGb(fileSizeBytes, fileName)
        val total = totalRamGb(context)
        val available = availableRamGb(context)
        val fileGb = fileSizeGb(fileSizeBytes)
        val minAvailable = (required * 0.45).coerceAtLeast(1.2)

        val canLoad = total >= required && available >= minAvailable
        val message = when {
            total < required ->
                "This device has ${formatGb(total)} GB RAM but this model needs about ${formatGb(required)} GB. " +
                    "Try Gemma 4 E2B-it or a smaller LiteRT-LM model."
            available < minAvailable ->
                "Not enough free memory right now (${formatGb(available)} GB free). Close other apps and try again."
            else -> null
        }
        return Assessment(canLoad, required, total, available, fileGb, message)
    }

    /** Import check — device total RAM only; free memory fluctuates during import. */
    fun assessForImport(context: Context, fileSizeBytes: Long, fileName: String = ""): Assessment {
        val required = estimateRequiredRamGb(fileSizeBytes, fileName)
        val total = totalRamGb(context)
        val available = availableRamGb(context)
        val fileGb = fileSizeGb(fileSizeBytes)
        val canLoad = total >= required * 0.92
        val message = when {
            !canLoad ->
                "This device has ${formatGb(total)} GB RAM but this model needs about ${formatGb(required)} GB. " +
                    "Try a smaller LiteRT-LM model."
            else -> null
        }
        return Assessment(canLoad, required, total, available, fileGb, message)
    }

    fun isLikelyLiteRtLmChatModel(fileName: String, fileSizeBytes: Long): Boolean {
        val lower = fileName.lowercase()
        if (!lower.endsWith(".litertlm")) return false
        if (lower.contains("embedding") || lower.contains("embedgemma") ||
            lower.contains("feature-extraction") || lower.contains("functiongemma")
        ) return false
        if (fileSizeBytes < 80_000_000L) return false
        val patterns = listOf(
            "fastvlm", "gemma", "qwen", "phi", "llama", "tinyllama", "smollm",
            "deepseek", "qualcomm", "litertlm", "vlm", "multimodal"
        )
        if (patterns.any { lower.contains(it) }) return true
        return fileSizeBytes >= 200_000_000L
    }

    fun isTrustedGemma4Model(fileName: String, fileSizeBytes: Long): Boolean {
        val lower = fileName.lowercase()
        if (!lower.endsWith(".litertlm")) return false
        return when {
            lower.contains("e2b") && lower.contains("gemma") ->
                fileSizeBytes >= 1_800_000_000L
            lower.contains("e4b") && lower.contains("gemma") ->
                fileSizeBytes >= 2_800_000_000L
            lower.contains("gemma-4-e2b") || lower.contains("gemma_4_e2b") ->
                fileSizeBytes >= 1_800_000_000L
            lower.contains("gemma-4-e4b") || lower.contains("gemma_4_e4b") ->
                fileSizeBytes >= 2_800_000_000L
            else -> false
        }
    }

    private fun fileSizeGb(bytes: Long): Double = bytes / GB

    private fun formatGb(value: Double): String =
        if (value >= 10) "%.0f".format(value) else "%.1f".format(value)

    private const val GB = 1024.0 * 1024.0 * 1024.0
}
