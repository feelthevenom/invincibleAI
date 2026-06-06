package com.example.data

data class DayActivityBar(
    val label: String,
    val volumeKg: Float,
    val completedSets: Int
)

data class WorkoutDashboardStats(
    val totalVolumeKg: Float,
    val volumeChangePercent: Int?,
    val workoutsCompleted: Int,
    val workoutsTarget: Int,
    val prsHit: Int,
    val prsNewThisWeek: Int,
    val weeklyActivity: List<DayActivityBar>,
    val caloriesBurnedWeek: Int = 0,
    val weeklyCalorieTarget: Int = 2000
)

object WorkoutStatsCalculator {
    fun compute(
        sets: List<ExerciseSet>,
        profile: UserProfile,
        weekStartDay: Long = DietDateUtils.startOfTodayMillis() - 6 * DietDateUtils.DAY_MS
    ): WorkoutDashboardStats {
        val todayEnd = DietDateUtils.endOfDayMillis(DietDateUtils.startOfTodayMillis())
        val weekEnd = todayEnd
        val completedAll = sets.filter { it.isCompleted && it.timestamp >= weekStartDay && it.timestamp < weekEnd }
        val completed = completedAll.filter { it.weight > 0f }

        val thisWeek = completed
        val lastWeek = completed.filter { it.timestamp >= weekStartDay - 7 * DietDateUtils.DAY_MS && it.timestamp < weekStartDay }

        val volumeThisWeek = thisWeek.sumOf { (it.weight * it.reps).toDouble() }.toFloat()
        val volumeLastWeek = lastWeek.sumOf { (it.weight * it.reps).toDouble() }.toFloat()
        val volumeChange = if (volumeLastWeek > 0f) {
            ((volumeThisWeek - volumeLastWeek) / volumeLastWeek * 100).toInt()
        } else null

        val workoutDaysThisWeek = completedAll.map { set ->
            DietDateUtils.startOfDayMillis(
                java.util.Calendar.getInstance().apply { timeInMillis = set.timestamp }
            )
        }.distinct().size

        val target = profile.workoutDaysPerWeek.coerceIn(3, 7).let { if (it < 3) 3 else it }
        val prStats = countPersonalRecords(completed, profile)
        val caloriesBurnedWeek = WorkoutCalorieEstimator.estimateBurn(completedAll, profile)
        val weeklyCalorieTarget = WorkoutCalorieEstimator.weeklyBurnTarget(profile)

        val weeklyActivity = (0..6).map { offset ->
            val dayStart = weekStartDay + offset * DietDateUtils.DAY_MS
            val dayEnd = DietDateUtils.endOfDayMillis(dayStart)
            val daySets = completedAll.filter { it.timestamp in dayStart until dayEnd }
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = dayStart }
            val label = java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault())
                .format(cal.time).uppercase()
            DayActivityBar(
                label = label,
                volumeKg = daySets.sumOf { (it.weight * it.reps).toDouble() }.toFloat(),
                completedSets = daySets.size
            )
        }

        return WorkoutDashboardStats(
            totalVolumeKg = volumeThisWeek,
            volumeChangePercent = volumeChange,
            workoutsCompleted = workoutDaysThisWeek,
            workoutsTarget = target,
            prsHit = prStats.total,
            prsNewThisWeek = prStats.newThisWeek,
            weeklyActivity = weeklyActivity,
            caloriesBurnedWeek = caloriesBurnedWeek,
            weeklyCalorieTarget = weeklyCalorieTarget
        )
    }

    private data class PrStats(val total: Int, val newThisWeek: Int)

    private fun countPersonalRecords(completed: List<ExerciseSet>, profile: UserProfile): PrStats {
        val weekStart = DietDateUtils.startOfTodayMillis() - 6 * DietDateUtils.DAY_MS
        val byExercise = completed.groupBy { it.exerciseName.lowercase() }
        var newThisWeek = 0
        var total = 0

        byExercise.forEach { (key, exerciseSets) ->
            val sorted = exerciseSets.sortedBy { it.timestamp }
            var runningMax = 0f
            var hadRealPr = false

            sorted.forEach { set ->
                val estimated = ExerciseWeightDefaults.estimatedWeightKg(
                    set.exerciseName, set.exerciseType, profile, set.setNumber
                )
                val isDefaultOnly = runningMax <= 0f &&
                    kotlin.math.abs(set.weight - estimated) < 2.5f

                if (set.weight > runningMax && !isDefaultOnly) {
                    if (runningMax > 0f) {
                        hadRealPr = true
                        if (set.timestamp >= weekStart) newThisWeek++
                    }
                    runningMax = set.weight
                } else if (set.weight > runningMax) {
                    runningMax = set.weight
                }
            }
            if (hadRealPr) total++
        }
        return PrStats(total = total, newThisWeek = newThisWeek)
    }
}
