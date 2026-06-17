package com.example.data

import android.content.Context
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.json.JSONArray
import org.json.JSONObject

/** Curated steps and muscle tags — never from ExerciseDB or AI hallucination. */
@JsonClass(generateAdapter = true)
data class BundledGuideEntry(
    val steps: List<String> = emptyList(),
    val targetMuscles: List<String> = emptyList(),
    val equipments: List<String> = emptyList(),
    val bodyParts: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class BundledGuidesFile(
    val guides: Map<String, BundledGuideEntry> = emptyMap()
)

data class ResolvedExerciseGuide(
    val exerciseId: String,
    val steps: List<String>,
    val targetMuscles: List<String>,
    val equipments: List<String>,
    val bodyParts: List<String>
)

class BundledExerciseGuideRepository(
    context: Context,
    private val localExercises: LocalExerciseRepository = LocalExerciseRepository(context)
) {
    private val overrides: Map<String, BundledGuideEntry> by lazy {
        loadOverrides(context.applicationContext)
    }

    fun resolve(exerciseName: String): ResolvedExerciseGuide? {
        val exercise = localExercises.findByNameOrAlias(exerciseName)
            ?: localExercises.all().firstOrNull {
                ExerciseNameMatcher.score(exerciseName, it.name) >= 120
            }
            ?: return null

        val override = overrides[exercise.id]
        val meta = metadataFromExercise(exercise)
        val steps = override?.steps.orEmpty().map { step ->
            if (step.trim().startsWith("Step:", ignoreCase = true)) step.trim()
            else ExerciseStepFormatter.toApiFormat(listOf(step)).first()
        }
        return ResolvedExerciseGuide(
            exerciseId = exercise.id,
            steps = steps,
            targetMuscles = override?.targetMuscles?.takeIf { it.isNotEmpty() } ?: meta.targetMuscles,
            equipments = override?.equipments?.takeIf { it.isNotEmpty() } ?: meta.equipments,
            bodyParts = override?.bodyParts?.takeIf { it.isNotEmpty() } ?: meta.bodyParts
        )
    }

    private fun metadataFromExercise(exercise: ExerciseItem): ResolvedExerciseGuide {
        val primary = primaryMuscleForType(exercise.exerciseType)
        val targets = buildList {
            add(primary)
            addAll(exercise.secondaryMuscles.map { it.trim().lowercase() }.filter { it.isNotBlank() })
        }.distinct()
        val equipment = listOfNotNull(exercise.equipment.takeIf { it.isNotBlank() })
        val bodyParts = listOf(exercise.exerciseType.lowercase()).filter { it.isNotBlank() }
        return ResolvedExerciseGuide(
            exerciseId = exercise.id,
            steps = emptyList(),
            targetMuscles = targets,
            equipments = equipment,
            bodyParts = bodyParts
        )
    }

    private fun primaryMuscleForType(type: String): String = when (type.lowercase()) {
        "chest" -> "pectorals"
        "back" -> "latissimus dorsi"
        "shoulders" -> "deltoids"
        "biceps" -> "biceps brachii"
        "triceps" -> "triceps brachii"
        "legs" -> "quadriceps"
        "glutes" -> "gluteus maximus"
        "core" -> "abdominals"
        "cardio" -> "cardiovascular system"
        "full body" -> "full body"
        else -> type.lowercase()
    }

    private fun loadOverrides(context: Context): Map<String, BundledGuideEntry> {
        return try {
            val json = context.assets.open("exercise_guides.json").bufferedReader().use { it.readText() }
            val adapter = Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()
                .adapter(BundledGuidesFile::class.java)
            adapter.fromJson(json)?.guides.orEmpty()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    companion object {
        fun stepsToJson(steps: List<String>): String = JSONArray(steps).toString()
        fun listToJson(items: List<String>): String = JSONArray(items).toString()
    }
}
