package com.example.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

/** Resolved workout GIF / image / fallback media for tutorials. */
sealed class WorkoutMedia {
    /** Local `file:///android_asset/…` URI or remote GIF/image URL for Coil. */
    data class Gif(val model: Any) : WorkoutMedia()

    /** Still-image fallback when a GIF is unavailable locally and offline. */
    data class StillImage(val model: Any) : WorkoutMedia()

    data class CustomFallback(val exerciseName: String, val searchUrl: String) : WorkoutMedia()
    data object None : WorkoutMedia()
}

/**
 * Resolves exercise tutorial media:
 * 1. Local bundled GIF (`exercises-dataset-main/videos/…`)
 * 2. CDN GIF backup (jsDelivr)
 * 3. Local still image
 * 4. YouTube search for unmatched custom exercises
 */
class WorkoutMediaEngine(
    context: Context,
    private val localExercises: LocalExerciseRepository = LocalExerciseRepository(context),
    private val exerciseRepository: ExerciseRepository = ExerciseRepository(context)
) {
    suspend fun resolve(
        exerciseName: String,
        allowNetwork: Boolean = true,
        aiMatchedExercise: Exercise? = null
    ): WorkoutMedia = withContext(Dispatchers.IO) {
        val exercise = aiMatchedExercise ?: resolveExerciseMatch(exerciseName)
        if (exercise == null) {
            return@withContext youtubeFallback(exerciseName)
        }
        resolveForExercise(exercise, allowNetwork)
    }

    suspend fun resolveForExercise(exercise: Exercise, allowNetwork: Boolean = true): WorkoutMedia =
        withContext(Dispatchers.IO) {
            exerciseRepository.resolveLocalGifAssetPath(exercise)?.let { path ->
                return@withContext WorkoutMedia.Gif(exerciseRepository.androidAssetUri(path))
            }

            if (allowNetwork) {
                cdnGifUrl(exercise)?.let { url ->
                    return@withContext WorkoutMedia.Gif(url)
                }
            }

            exerciseRepository.resolveLocalImageAssetPath(exercise)?.let { path ->
                return@withContext WorkoutMedia.StillImage(exerciseRepository.androidAssetUri(path))
            }

            if (allowNetwork) {
                cdnGifUrl(exercise, idOnly = true)?.let { url ->
                    return@withContext WorkoutMedia.Gif(url)
                }
            }

            WorkoutMedia.None
        }

    private fun resolveExerciseMatch(exerciseName: String): Exercise? {
        exerciseRepository.findBestMatch(exerciseName, minScore = 120)?.let { return it }

        localExercises.findByNameOrAlias(exerciseName)?.let { appExercise ->
            val aliasCandidates = buildList {
                add(appExercise.name)
                addAll(appExercise.aliases)
            }
            aliasCandidates.forEach { candidate ->
                exerciseRepository.findBestMatch(candidate, minScore = 100)?.let { return it }
            }
        }

        return exerciseRepository.findBestMatch(exerciseName, minScore = 80)
    }

    private fun cdnGifUrl(exercise: Exercise, idOnly: Boolean = false): String? {
        if (!idOnly) {
            exercise.gifFileName()?.let { fileName ->
                return "$CDN_BASE/$fileName"
            }
        }
        return "$CDN_BASE/${exercise.id}.gif"
    }

    private fun youtubeFallback(exerciseName: String): WorkoutMedia {
        val encodedQuery = URLEncoder.encode("$exerciseName form tutorial", Charsets.UTF_8.name())
        val searchUrl = "https://www.youtube.com/results?search_query=$encodedQuery"
        return WorkoutMedia.CustomFallback(exerciseName, searchUrl)
    }

    companion object {
        const val CDN_BASE =
            "https://cdn.jsdelivr.net/gh/feelthevenom/invincibleAI-assets@main/workouts"
    }
}
