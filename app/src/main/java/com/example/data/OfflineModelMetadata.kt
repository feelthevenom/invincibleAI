package com.example.data

import org.json.JSONObject
import java.io.File

/** Persisted capabilities after a successful LiteRT-LM probe on import. */
data class OfflineModelMetadata(
    val supportsVision: Boolean,
    val supportsText: Boolean,
    val probeUsedVisionBackend: Boolean
) {
    fun toJson(): String = JSONObject().apply {
        put("supportsVision", supportsVision)
        put("supportsText", supportsText)
        put("probeUsedVisionBackend", probeUsedVisionBackend)
    }.toString()

    companion object {
        fun metaFileFor(modelFile: File): File =
            File(modelFile.parentFile, "${modelFile.name}.meta.json")

        fun load(modelFile: File): OfflineModelMetadata? {
            return try {
                val meta = metaFileFor(modelFile)
                if (!meta.exists()) return null
                val json = JSONObject(meta.readText())
                OfflineModelMetadata(
                    supportsVision = json.optBoolean("supportsVision", false),
                    supportsText = json.optBoolean("supportsText", true),
                    probeUsedVisionBackend = json.optBoolean("probeUsedVisionBackend", false)
                )
            } catch (_: Exception) {
                null
            }
        }

        fun save(modelFile: File, metadata: OfflineModelMetadata) {
            metaFileFor(modelFile).writeText(metadata.toJson())
        }

        fun delete(modelFile: File) {
            metaFileFor(modelFile).delete()
        }
    }
}
