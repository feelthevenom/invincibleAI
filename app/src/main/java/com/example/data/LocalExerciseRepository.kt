package com.example.data

import android.content.Context
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

@JsonClass(generateAdapter = true)
data class BundledExerciseEntry(
    val id: String,
    val name: String,
    val type: String,
    val routines: List<String> = emptyList(),
    val defaultSets: Int = 3,
    val defaultReps: Int = 10,
    val aliases: List<String> = emptyList(),
    val equipment: String = "",
    val secondaryMuscles: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class BundledExercisesFile(
    val version: Int = 1,
    val exercises: List<BundledExerciseEntry> = emptyList()
)

class LocalExerciseRepository(context: Context) {

    private val allExercises: List<ExerciseItem> by lazy {
        loadExercises(context.applicationContext)
    }

    fun all(): List<ExerciseItem> = allExercises

    fun search(query: String, routineFilter: String? = null): List<ExerciseItem> {
        val q = query.trim().lowercase()
        return allExercises.filter { exercise ->
            if (!matchesRoutine(exercise, routineFilter)) return@filter false
            if (q.isBlank()) return@filter true
            exercise.name.lowercase().contains(q) ||
                exercise.exerciseType.lowercase().contains(q) ||
                exercise.aliases.any { it.lowercase().contains(q) }
        }.sortedByDescending { it.name.lowercase().startsWith(q) }
    }

    fun forRoutine(routine: String): List<ExerciseItem> =
        allExercises.filter { matchesRoutine(it, routine) }

    fun cardioExercises(): List<ExerciseItem> =
        allExercises.filter { it.isCardio || it.exerciseType.equals("Cardio", ignoreCase = true) }

    fun findById(id: String): ExerciseItem? = allExercises.find { it.id == id }

    fun findByName(name: String): ExerciseItem? =
        allExercises.find { it.name.equals(name, ignoreCase = true) }

    fun findByNameOrAlias(name: String): ExerciseItem? {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return null
        return allExercises.find { exercise ->
            exercise.name.equals(trimmed, ignoreCase = true) ||
                exercise.aliases.any { it.equals(trimmed, ignoreCase = true) }
        }
    }

    fun suggestions(routine: String, limit: Int = 12): List<ExerciseItem> =
        forRoutine(routine).take(limit)

    private fun matchesRoutine(exercise: ExerciseItem, routineFilter: String?): Boolean {
        if (routineFilter.isNullOrBlank()) return true
        if (exercise.routines.any { it.equals(routineFilter, ignoreCase = true) }) return true
        if (routineFilter.equals("Cardio", ignoreCase = true) &&
            exercise.exerciseType.equals("Cardio", ignoreCase = true)
        ) {
            return true
        }
        return false
    }

    private fun loadExercises(context: Context): List<ExerciseItem> {
        return try {
            val json = context.assets.open("exercises.json").bufferedReader().use { it.readText() }
            val adapter = Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()
                .adapter(BundledExercisesFile::class.java)
            adapter.fromJson(json)?.exercises.orEmpty().map { entry ->
                val isCardio = entry.type.equals("Cardio", ignoreCase = true)
                ExerciseItem(
                    id = entry.id,
                    name = entry.name,
                    exerciseType = entry.type,
                    routines = entry.routines,
                    defaultSets = if (isCardio) 1 else entry.defaultSets,
                    defaultReps = if (isCardio) 0 else entry.defaultReps,
                    aliases = entry.aliases,
                    equipment = entry.equipment,
                    secondaryMuscles = entry.secondaryMuscles,
                    isCardio = isCardio
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
