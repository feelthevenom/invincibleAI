package com.example.data

/**
 * Generates workout day labels and suggested exercises from user profile (Hevy-style splits).
 */
object WorkoutSplitGenerator {

    data class WorkoutDayPlan(
        val label: String,
        val routine: String,
        val focus: String
    )

    fun planForDayIndex(dayIndex: Int, workoutDaysPerWeek: Int, goal: String): WorkoutDayPlan {
        val days = plansForWeek(workoutDaysPerWeek.coerceIn(1, 7), goal)
        return days[dayIndex.coerceIn(0, days.lastIndex)]
    }

    fun dayIndexForDate(dayStartMillis: Long, workoutDaysPerWeek: Int): Int {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = dayStartMillis }
        val dayOfYear = cal.get(java.util.Calendar.DAY_OF_YEAR)
        return (dayOfYear - 1) % workoutDaysPerWeek.coerceAtLeast(1)
    }

    fun plansForWeek(workoutDaysPerWeek: Int, goal: String): List<WorkoutDayPlan> {
        val style = when (goal) {
            "Gain Muscle" -> "Hypertrophy"
            "Athletic Performance" -> "Performance"
            "Lose Fat" -> "Metabolic"
            else -> "Hypertrophy"
        }
        return when (workoutDaysPerWeek) {
            1 -> listOf(WorkoutDayPlan("Full Body ($style)", "Full Body", "Full Body"))
            2 -> listOf(
                WorkoutDayPlan("Upper Body ($style)", "Upper", "Upper"),
                WorkoutDayPlan("Lower Body ($style)", "Lower", "Lower")
            )
            3 -> listOf(
                WorkoutDayPlan("Push Day ($style)", "Push", "Chest/Shoulders/Triceps"),
                WorkoutDayPlan("Pull Day ($style)", "Pull", "Back/Biceps"),
                WorkoutDayPlan("Leg Day ($style)", "Legs", "Quads/Hams/Glutes")
            )
            4 -> listOf(
                WorkoutDayPlan("Upper A ($style)", "Upper", "Upper"),
                WorkoutDayPlan("Lower A ($style)", "Lower", "Lower"),
                WorkoutDayPlan("Upper B ($style)", "Upper", "Upper"),
                WorkoutDayPlan("Lower B ($style)", "Lower", "Lower")
            )
            5 -> listOf(
                WorkoutDayPlan("Push Day ($style)", "Push", "Push"),
                WorkoutDayPlan("Pull Day ($style)", "Pull", "Pull"),
                WorkoutDayPlan("Leg Day ($style)", "Legs", "Legs"),
                WorkoutDayPlan("Upper Day ($style)", "Upper", "Upper"),
                WorkoutDayPlan("Lower Day ($style)", "Lower", "Lower")
            )
            6 -> listOf(
                WorkoutDayPlan("Push A ($style)", "Push", "Push"),
                WorkoutDayPlan("Pull A ($style)", "Pull", "Pull"),
                WorkoutDayPlan("Legs A ($style)", "Legs", "Legs"),
                WorkoutDayPlan("Push B ($style)", "Push", "Push"),
                WorkoutDayPlan("Pull B ($style)", "Pull", "Pull"),
                WorkoutDayPlan("Legs B ($style)", "Legs", "Legs")
            )
            else -> listOf(
                WorkoutDayPlan("Push ($style)", "Push", "Push"),
                WorkoutDayPlan("Pull ($style)", "Pull", "Pull"),
                WorkoutDayPlan("Legs ($style)", "Legs", "Legs"),
                WorkoutDayPlan("Upper ($style)", "Upper", "Upper"),
                WorkoutDayPlan("Lower ($style)", "Lower", "Lower"),
                WorkoutDayPlan("Full Body ($style)", "Full Body", "Full Body"),
                WorkoutDayPlan("Active Recovery", "Full Body", "Light")
            )
        }
    }
}
