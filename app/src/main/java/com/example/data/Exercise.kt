package com.example.data

/**
 * Parsed entry from `assets/exercises-dataset-main/data/exercises.json`.
 */
data class Exercise(
    val id: String,
    val name: String,
    val target: String,
    val bodyPart: String,
    val equipment: String,
    val secondaryMuscles: List<String>,
    val instructions: List<String>,
    /** Relative path inside assets root, e.g. `videos/0001-2gPfomN.gif`. */
    val gifUrl: String,
    /** Relative path inside assets root, e.g. `images/0001-2gPfomN.jpg`. */
    val imageUrl: String
) {
    val gifAssetPath: String?
        get() = gifUrl.takeIf { it.isNotBlank() }?.let { "exercises-dataset-main/$it" }

    val imageAssetPath: String?
        get() = imageUrl.takeIf { it.isNotBlank() }?.let { "exercises-dataset-main/$it" }

    fun gifFileName(): String? =
        gifUrl.substringAfterLast('/').takeIf { it.isNotBlank() && it.contains('.') }
}
