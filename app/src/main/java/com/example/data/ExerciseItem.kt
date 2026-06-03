package com.example.data

data class ExerciseItem(
    val id: String,
    val name: String,
    val exerciseType: String,
    val routines: List<String> = emptyList(),
    val defaultSets: Int = 3,
    val defaultReps: Int = 10,
    val aliases: List<String> = emptyList(),
    val isCustom: Boolean = false
)

data class WorkoutExerciseGroup(
    val exerciseName: String,
    val exerciseType: String,
    val workoutDayLabel: String,
    val sets: List<ExerciseSet>
) {
    val allCompleted: Boolean get() = sets.isNotEmpty() && sets.all { it.isCompleted }
    val groupKey: String get() = "$exerciseName|$exerciseType|$workoutDayLabel"
}

object WorkoutGrouping {
    fun groupSets(sets: List<ExerciseSet>): List<WorkoutExerciseGroup> {
        return sets
            .groupBy { "${it.exerciseName}|${it.exerciseType}|${it.workoutDayLabel}" }
            .map { (_, groupSets) ->
                val sorted = groupSets.sortedBy { it.setNumber }
                val first = sorted.first()
                WorkoutExerciseGroup(
                    exerciseName = first.exerciseName,
                    exerciseType = first.exerciseType,
                    workoutDayLabel = first.workoutDayLabel,
                    sets = sorted
                )
            }
            .sortedBy { it.exerciseName }
    }
}
