package com.example.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class ExerciseRepository(private val context: Context) {

    private val appContext = context.applicationContext
    private var exercisesById: Map<String, Exercise> = emptyMap()
    private var exercisesByName: List<Exercise> = emptyList()
    private val availableGifAssets = mutableSetOf<String>()
    private val availableImageAssets = mutableSetOf<String>()

    init {
        indexMediaAssets()
        loadExercises()
    }

    fun getExerciseById(id: String): Exercise? = exercisesById[id]

    fun allExercises(): List<Exercise> = exercisesByName

    fun searchExercises(query: String): List<Exercise> {
        val q = query.trim()
        if (q.isEmpty()) return exercisesByName

        return exercisesByName
            .map { exercise -> exercise to ExerciseNameMatcher.score(q, exercise.name) }
            .filter { (_, score) -> score > 0 }
            .sortedByDescending { (_, score) -> score }
            .map { (exercise, _) -> exercise }
    }

    /** Best dataset match for an app exercise name or custom title. */
    fun findBestMatch(query: String, minScore: Int = 120): Exercise? {
        val q = query.trim()
        if (q.isEmpty()) return null

        exercisesById[q]?.let { return it }

        exercisesByName.find { it.name.equals(q, ignoreCase = true) }?.let { return it }

        val expanded = ExerciseNameMatcher.expandAbbreviations(q)
        if (!expanded.equals(q, ignoreCase = true)) {
            exercisesByName.find { it.name.equals(expanded, ignoreCase = true) }?.let { return it }
        }

        return exercisesByName
            .map { exercise -> exercise to ExerciseNameMatcher.score(q, exercise.name) }
            .filter { (_, score) -> score >= minScore }
            .maxByOrNull { (_, score) -> score }
            ?.first
    }

    /** Top candidates for AI disambiguation when fuzzy match is uncertain. */
    fun candidateMatches(query: String, limit: Int = 40): List<Exercise> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()

        val scored = exercisesByName
            .map { exercise ->
                exercise to maxOf(
                    ExerciseNameMatcher.score(q, exercise.name),
                    FuzzyMatcher.score(q, exercise.name)
                )
            }
            .filter { (_, score) -> score >= 40 }
            .sortedByDescending { (_, score) -> score }

        return scored.take(limit).map { it.first }
    }

    fun hasLocalGif(exercise: Exercise): Boolean {
        resolveLocalGifAssetPath(exercise)?.let { return true }
        return false
    }

    fun hasLocalImage(exercise: Exercise): Boolean {
        resolveLocalImageAssetPath(exercise)?.let { return true }
        return false
    }

    /** First existing local GIF asset path, or null. */
    fun resolveLocalGifAssetPath(exercise: Exercise): String? =
        candidateGifAssetPaths(exercise).firstOrNull { path ->
            path in availableGifAssets || assetExists(path)
        }

    /** First existing local still-image asset path, or null. */
    fun resolveLocalImageAssetPath(exercise: Exercise): String? =
        candidateImageAssetPaths(exercise).firstOrNull { path ->
            path in availableImageAssets || assetExists(path)
        }

    fun androidAssetUri(assetPath: String): String = "file:///android_asset/$assetPath"

    private fun candidateGifAssetPaths(exercise: Exercise): List<String> = buildList {
        exercise.gifAssetPath?.let { add(it) }
        add("exercises-dataset-main/videos/${exercise.id}.gif")
        exercise.gifFileName()?.let { fileName ->
            add("exercises-dataset-main/videos/$fileName")
        }
    }.distinct()

    private fun candidateImageAssetPaths(exercise: Exercise): List<String> = buildList {
        exercise.imageAssetPath?.let { add(it) }
        exercise.imageUrl.substringAfterLast('/').takeIf { it.isNotBlank() }?.let { fileName ->
            add("exercises-dataset-main/images/$fileName")
        }
        add("exercises-dataset-main/images/${exercise.id}.jpg")
    }.distinct()

    private fun loadExercises() {
        try {
            val json = appContext.assets
                .open("exercises-dataset-main/data/exercises.json")
                .bufferedReader()
                .use { it.readText() }
            val array = JSONArray(json)
            val parsed = buildList {
                for (i in 0 until array.length()) {
                    parseExercise(array.optJSONObject(i) ?: continue)?.let { add(it) }
                }
            }
            exercisesById = parsed.associateBy { it.id }
            exercisesByName = parsed.sortedBy { it.name.lowercase(Locale.US) }
            Log.d(TAG, "Loaded ${parsed.size} exercises; ${availableGifAssets.size} GIF assets indexed.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse exercises dataset", e)
            exercisesById = emptyMap()
            exercisesByName = emptyList()
        }
    }

    private fun parseExercise(obj: JSONObject): Exercise? {
        val id = obj.optString("id").trim()
        val name = obj.optString("name").trim()
        if (id.isBlank() || name.isBlank()) return null

        val stepsArray = obj.optJSONObject("instruction_steps")?.optJSONArray("en")
        val instructions = stepsArray.toStringList()
            .ifEmpty {
                obj.optJSONObject("instructions")
                    ?.optString("en")
                    ?.split('.')
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() }
                    .orEmpty()
            }

        val secondary = obj.optJSONArray("secondary_muscles").toStringList()

        return Exercise(
            id = id,
            name = name,
            target = obj.optString("target").ifBlank { obj.optString("muscle_group") },
            bodyPart = obj.optString("body_part").ifBlank { obj.optString("category") },
            equipment = obj.optString("equipment"),
            secondaryMuscles = secondary,
            instructions = instructions,
            gifUrl = obj.optString("gif_url"),
            imageUrl = obj.optString("image")
        )
    }

    private fun indexMediaAssets() {
        availableGifAssets += listAssetFiles("exercises-dataset-main/videos")
        availableImageAssets += listAssetFiles("exercises-dataset-main/images")
    }

    private fun listAssetFiles(root: String): Set<String> {
        return try {
            appContext.assets.list(root)?.map { "$root/$it" }?.toSet().orEmpty()
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun assetExists(path: String): Boolean =
        try {
            appContext.assets.open(path).use { true }
        } catch (_: Exception) {
            false
        }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                optString(i).trim().takeIf { it.isNotBlank() }?.let { add(it) }
            }
        }
    }

    companion object {
        private const val TAG = "ExerciseRepository"
    }
}
