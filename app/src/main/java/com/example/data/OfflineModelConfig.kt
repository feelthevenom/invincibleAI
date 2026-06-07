package com.example.data

/**
 * Gemma 4 on-device models — pinned to Gallery allowlist commit SHAs.
 * @see <a href="https://github.com/google-ai-edge/gallery/blob/main/model_allowlists/1_0_15.json">Gallery allowlist</a>
 */
object OfflineModelConfig {

    data class ModelSpec(
        val id: String,
        val displayName: String,
        val fileName: String,
        val huggingFaceRepo: String,
        /** Full Git commit SHA — Gallery pins downloads to this revision. */
        val commitHash: String,
        val expectedSizeBytes: Long,
        val minRamGb: Double,
        val supportsVision: Boolean
    )

    val OFFLINE_2B = ModelSpec(
        id = "offline_2b",
        displayName = "Gemma 4 E2B-it",
        fileName = "gemma-4-E2B-it.litertlm",
        huggingFaceRepo = "litert-community/gemma-4-E2B-it-litert-lm",
        commitHash = "7fa1d78473894f7e736a21d920c3aa80f950c0db",
        expectedSizeBytes = 2_588_147_712L,
        minRamGb = 4.0,
        supportsVision = true
    )

    val OFFLINE_4B = ModelSpec(
        id = "offline_4b",
        displayName = "Gemma 4 E4B-it",
        fileName = "gemma-4-E4B-it.litertlm",
        huggingFaceRepo = "litert-community/gemma-4-E4B-it-litert-lm",
        commitHash = "9695417f248178c63a9f318c6e0c56cb917cb837",
        expectedSizeBytes = 3_659_530_240L,
        minRamGb = 5.0,
        supportsVision = true
    )

    val ALL = listOf(OFFLINE_2B, OFFLINE_4B)

    fun specFor(modelType: String): ModelSpec? = ALL.find { it.id == modelType }

    fun specForBuiltInFile(fileName: String): ModelSpec? =
        ALL.find { it.fileName.equals(fileName, ignoreCase = true) }

    /** Minimum size for unknown imports — built-in specs use stricter checks. */
    fun isValidImportedFile(lengthBytes: Long): Boolean = lengthBytes >= 50_000_000L

    fun importedId(fileName: String): String = "imported:$fileName"

    /** Same URL pattern as Google AI Edge Gallery DownloadWorker. */
    fun downloadUrl(spec: ModelSpec): String =
        "https://huggingface.co/${spec.huggingFaceRepo}/resolve/${spec.commitHash}/${spec.fileName}?download=true"

    fun isValidModelFile(spec: ModelSpec, lengthBytes: Long): Boolean =
        lengthBytes >= (spec.expectedSizeBytes * 0.90).toLong()
}
