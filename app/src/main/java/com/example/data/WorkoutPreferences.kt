package com.example.data

import java.util.Calendar

/**
 * Workout profile options inspired by Gym Workout (Play Store) reference assets.
 */
object WorkoutPreferences {

    val FITNESS_LEVELS = listOf(
        "Beginner",
        "Intermediate",
        "Advanced",
        "Elite"
    )

    val WORKOUT_FREQUENCY_OPTIONS = listOf(
        FrequencyOption(3, "3 days / week"),
        FrequencyOption(4, "4 days / week"),
        FrequencyOption(5, "5 days / week"),
        FrequencyOption(6, "6 days / week"),
        FrequencyOption(7, "Every day")
    )

    val WEEK_DAYS = listOf(
        WeekDayOption(Calendar.MONDAY, "Monday"),
        WeekDayOption(Calendar.TUESDAY, "Tuesday"),
        WeekDayOption(Calendar.WEDNESDAY, "Wednesday"),
        WeekDayOption(Calendar.THURSDAY, "Thursday"),
        WeekDayOption(Calendar.FRIDAY, "Friday"),
        WeekDayOption(Calendar.SATURDAY, "Saturday"),
        WeekDayOption(Calendar.SUNDAY, "Sunday")
    )

    val GYM_LOCATIONS = listOf(
        GymLocationOption("home", "Home", "Train at home with limited or bodyweight gear"),
        GymLocationOption("small_gym", "Small Gym", "Compact gym with basics"),
        GymLocationOption("moderate_gym", "Moderate Gym", "Standard commercial gym"),
        GymLocationOption("large_gym", "Large Gym", "Full-service gym with wide equipment range")
    )

    /** Popular equipment keys aligned with reference `equipments.json`. */
    val EQUIPMENT_OPTIONS = listOf(
        EquipmentOption("all", "All Equipment", "Everything available", isSpecial = true),
        EquipmentOption("barbell", "Barbells", "Free Weights"),
        EquipmentOption("dumbbell", "Dumbbells", "Free Weights"),
        EquipmentOption("flat_bench", "Flat Bench", "Bar & Benches"),
        EquipmentOption("pull_up_bar", "Pull-up Bar", "Bar & Benches"),
        EquipmentOption("cable_machine", "Cable Machine", "Weight Machines"),
        EquipmentOption("smith_machine", "Smith Machine", "Weight Machines"),
        EquipmentOption("kettlebell", "Kettlebells", "Free Weights"),
        EquipmentOption("leg_press", "Leg Press", "Weight Machines"),
        EquipmentOption("resistance_band", "Resistance Bands", "Other Equipment"),
        EquipmentOption("none", "None of the above", "Bodyweight / minimal setup", isSpecial = true)
    )

    data class FrequencyOption(val daysPerWeek: Int, val label: String)
    data class WeekDayOption(val calendarDay: Int, val label: String)
    data class GymLocationOption(val id: String, val label: String, val description: String)
    data class EquipmentOption(
        val id: String,
        val label: String,
        val group: String,
        val isSpecial: Boolean = false
    )

    fun serializeEquipment(ids: Set<String>): String =
        when {
            "all" in ids -> "all"
            "none" in ids -> "none"
            ids.isEmpty() -> ""
            else -> ids.sorted().joinToString(",")
        }

    fun parseEquipment(raw: String): Set<String> =
        when {
            raw.isBlank() -> emptySet()
            raw == "all" -> setOf("all")
            raw == "none" -> setOf("none")
            else -> raw.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
        }

    fun equipmentDisplay(raw: String): String {
        val ids = parseEquipment(raw)
        return when {
            "all" in ids -> "All Equipment"
            "none" in ids -> "None of the above"
            ids.isEmpty() -> "Not set"
            else -> ids.mapNotNull { id -> EQUIPMENT_OPTIONS.find { it.id == id }?.label }
                .joinToString(", ")
        }
    }

    fun gymLocationLabel(id: String): String =
        GYM_LOCATIONS.find { it.id == id }?.label ?: id.ifBlank { "Not set" }

    fun weekDayLabel(calendarDay: Int): String =
        WEEK_DAYS.find { it.calendarDay == calendarDay }?.label ?: "Monday"

    fun totalStrengthKg(profile: UserProfile): Float =
        profile.squat1RmKg + profile.benchPress1RmKg + profile.deadlift1RmKg

    fun strengthSummary(profile: UserProfile): String =
        if (profile.benchmarkSkipped) {
            "Beginner — benchmarks skipped"
        } else {
            buildString {
                append("Squat ${formatKg(profile.squat1RmKg)}")
                append(" · Bench ${formatKg(profile.benchPress1RmKg)}")
                append(" · Deadlift ${formatKg(profile.deadlift1RmKg)}")
            }
        }

    fun formatKg(value: Float): String =
        if (value <= 0f) "—" else "${value.toInt()} kg"

    fun toggleEquipmentSelection(current: Set<String>, toggledId: String): Set<String> {
        return when (toggledId) {
            "all" -> setOf("all")
            "none" -> setOf("none")
            else -> {
                val base = current - "all" - "none"
                val next = if (toggledId in base) base - toggledId else base + toggledId
                next
            }
        }
    }

    fun estimatedFrequencyLabel(days: Int): String =
        WORKOUT_FREQUENCY_OPTIONS.find { it.daysPerWeek == days }?.label
            ?: "$days days / week"
}
