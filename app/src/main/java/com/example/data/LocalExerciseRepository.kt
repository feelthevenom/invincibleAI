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
        val pool = if (q.isNotEmpty()) {
            allExercises
        } else {
            allExercises.filter { matchesRoutine(it, routineFilter) }
        }
        if (q.isBlank()) return pool.sortedBy { it.name.lowercase() }
        return pool.filter { exercise ->
            exercise.name.lowercase().contains(q) ||
                exercise.exerciseType.lowercase().contains(q) ||
                exercise.aliases.any { it.lowercase().contains(q) } ||
                exercise.equipment.lowercase().contains(q)
        }.sortedWith(
            compareByDescending<ExerciseItem> { it.name.lowercase().startsWith(q) }
                .thenBy { it.name.lowercase() }
        )
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

    fun suggestions(routine: String, limit: Int = 12): List<ExerciseItem> {
        val filtered = forRoutine(routine)
        return (if (filtered.isEmpty()) allExercises else filtered).take(limit)
    }

    /** Maps workout routine names (incl. built-ins like Upper Body) to exercise routine tags. */
    fun resolveRoutineTags(routineName: String): Set<String>? {
        val n = routineName.trim().lowercase()
        return when {
            n == "push" || n.contains("push") -> setOf("Push")
            n == "pull" || n.contains("pull") -> setOf("Pull")
            n.contains("leg") -> setOf("Legs", "Lower", "Glutes")
            n.contains("upper") -> setOf("Upper", "Push", "Pull", "Chest", "Back", "Shoulders", "Biceps", "Triceps")
            n.contains("lower") -> setOf("Lower", "Legs", "Glutes")
            n.contains("full") -> setOf("Full Body")
            n.contains("core") || n.contains("abs") -> setOf("Full Body", "Core")
            n.contains("cardio") -> setOf("Cardio", "Full Body")
            else -> null
        }
    }

    private fun matchesRoutine(exercise: ExerciseItem, routineFilter: String?): Boolean {
        if (routineFilter.isNullOrBlank()) return true
        val tags = resolveRoutineTags(routineFilter) ?: return true
        if (tags.contains("Cardio") && (exercise.isCardio || exercise.exerciseType.equals("Cardio", true))) {
            return true
        }
        if (exercise.routines.any { tag -> tags.any { it.equals(tag, ignoreCase = true) } }) return true
        if (tags.any { it.equals(exercise.exerciseType, ignoreCase = true) }) return true
        return false
    }

    private fun loadExercises(context: Context): List<ExerciseItem> {
        return try {
            val json = context.assets.open("exercises.json").bufferedReader().use { it.readText() }
            val adapter = Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()
                .adapter(BundledExercisesFile::class.java)
            val seen = mutableSetOf<String>()
            adapter.fromJson(json)?.exercises.orEmpty()
                .filter { seen.add(it.id) }
                .map { entry ->
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
