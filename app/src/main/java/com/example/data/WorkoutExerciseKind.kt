package com.example.data

object WorkoutExerciseKind {
    enum class Kind { STRENGTH, CARDIO, BODYWEIGHT }

    fun isCardioType(exerciseType: String, isCardioFlag: Boolean = false): Boolean =
        isCardioFlag || exerciseType.equals("Cardio", ignoreCase = true)

    fun kindFor(group: WorkoutExerciseGroup): Kind {
        val first = group.sets.firstOrNull() ?: return Kind.STRENGTH
        if (isCardioType(first.exerciseType) || first.durationSeconds > 0 && first.weight == 0f && first.reps == 0) {
            return Kind.CARDIO
        }
        if (group.sets.all { it.weight == 0f }) return Kind.BODYWEIGHT
        return Kind.STRENGTH
    }

    fun kindFor(exerciseType: String, isCardio: Boolean): Kind =
        if (isCardioType(exerciseType, isCardio)) Kind.CARDIO else Kind.STRENGTH

    fun formatDuration(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return "%d:%02d".format(mins, secs)
    }

    fun parseDurationInput(text: String): Int {
        val trimmed = text.trim()
        if (trimmed.contains(":")) {
            val parts = trimmed.split(":")
            val mins = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val secs = parts.getOrNull(1)?.toIntOrNull() ?: 0
            return (mins * 60 + secs).coerceAtLeast(0)
        }
        return (trimmed.toIntOrNull() ?: 0).coerceAtLeast(0) * 60
    }
}
