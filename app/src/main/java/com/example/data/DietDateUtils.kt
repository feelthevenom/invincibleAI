package com.example.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object DietDateUtils {

    const val DAY_MS = 86_400_000L

    fun startOfDayMillis(calendar: Calendar = Calendar.getInstance()): Long =
        calendar.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    fun startOfTodayMillis(): Long = startOfDayMillis()

    fun startOfYesterdayMillis(): Long = startOfTodayMillis() - DAY_MS

    fun endOfDayMillis(dayStart: Long): Long = dayStart + DAY_MS

    fun isToday(dayStart: Long): Boolean = dayStart == startOfTodayMillis()

    fun isFuture(dayStart: Long): Boolean = dayStart > startOfTodayMillis()

    fun isPastOrToday(dayStart: Long): Boolean = !isFuture(dayStart)

    fun timestampForDay(dayStart: Long, referenceMs: Long = System.currentTimeMillis()): Long {
        if (isToday(dayStart)) return referenceMs
        val ref = Calendar.getInstance().apply { timeInMillis = referenceMs }
        return Calendar.getInstance().apply {
            timeInMillis = dayStart
            set(Calendar.HOUR_OF_DAY, ref.get(Calendar.HOUR_OF_DAY))
            set(Calendar.MINUTE, ref.get(Calendar.MINUTE))
            set(Calendar.SECOND, ref.get(Calendar.SECOND))
            set(Calendar.MILLISECOND, ref.get(Calendar.MILLISECOND))
        }.timeInMillis
    }

    fun formatDisplayDate(dayStart: Long): String {
        val fmt = SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault())
        return fmt.format(dayStart)
    }

    fun formatLogTimestamp(timestamp: Long): String {
        val fmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        return fmt.format(timestamp)
    }

    fun formatMonthYear(dayStart: Long): String {
        val fmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        return fmt.format(dayStart)
    }

    fun dayOfMonth(dayStart: Long): Int =
        Calendar.getInstance().apply { timeInMillis = dayStart }.get(Calendar.DAY_OF_MONTH)

    fun mealsForDay(meals: List<MealEntry>, dayStart: Long): List<MealEntry> {
        val end = endOfDayMillis(dayStart)
        return meals.filter { it.timestamp >= dayStart && it.timestamp < end }
    }

    data class DaySummary(
        val dayStart: Long,
        val totalCalories: Int,
        val hasData: Boolean,
        val goalMet: Boolean
    )

    fun summarizeDays(
        meals: List<MealEntry>,
        goalForDay: (Long) -> Int
    ): Map<Long, DaySummary> {
        if (meals.isEmpty()) return emptyMap()
        return meals.groupBy { meal ->
            startOfDayMillis(Calendar.getInstance().apply { timeInMillis = meal.timestamp })
        }.mapValues { (dayStart, dayMeals) ->
            val total = dayMeals.sumOf { it.calories }
            val dailyGoal = goalForDay(dayStart).coerceAtLeast(1)
            DaySummary(
                dayStart = dayStart,
                totalCalories = total,
                hasData = dayMeals.isNotEmpty(),
                goalMet = dailyGoal > 0 && total >= (dailyGoal * 0.9).toInt()
            )
        }
    }

    @Deprecated("Use summarizeDays with goalForDay for historical accuracy")
    fun summarizeDays(meals: List<MealEntry>, dailyGoal: Int): Map<Long, DaySummary> =
        summarizeDays(meals) { dailyGoal }

    fun summarizeWorkoutDays(sets: List<ExerciseSet>): Map<Long, DaySummary> {
        if (sets.isEmpty()) return emptyMap()
        return sets.groupBy { set ->
            startOfDayMillis(Calendar.getInstance().apply { timeInMillis = set.timestamp })
        }.mapValues { (dayStart, daySets) ->
            DaySummary(
                dayStart = dayStart,
                totalCalories = 0,
                hasData = daySets.isNotEmpty(),
                goalMet = daySets.isNotEmpty() && daySets.all { it.isCompleted }
            )
        }
    }

    fun summarizeWaterDays(
        logs: List<com.example.data.WaterLog>,
        goalGlasses: Int
    ): Map<Long, DaySummary> {
        if (logs.isEmpty()) return emptyMap()
        return logs.groupBy { log ->
            startOfDayMillis(Calendar.getInstance().apply { timeInMillis = log.timestamp })
        }.mapValues { (dayStart, dayLogs) ->
            val totalMl = dayLogs.sumOf { it.amountMl }
            val glasses = (totalMl / com.example.data.WaterGoalCalculator.ML_PER_GLASS)
            DaySummary(
                dayStart = dayStart,
                totalCalories = glasses,
                hasData = glasses > 0,
                goalMet = goalGlasses > 0 && glasses >= goalGlasses
            )
        }
    }

    fun last7DayStarts(endingAt: Long = startOfTodayMillis()): List<Long> =
        (6 downTo 0).map { offset -> endingAt - offset * DAY_MS }

    fun formatShortDay(dayStart: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = dayStart }
        val dayFmt = SimpleDateFormat("EEE", Locale.getDefault())
        return "${dayFmt.format(dayStart)} ${cal.get(Calendar.DAY_OF_MONTH)}"
    }

    fun formatTimeFromMinute(minuteOfDay: Int): String {
        val hour = minuteOfDay / 60
        val minute = minuteOfDay % 60
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }
        return SimpleDateFormat("hh:mm a", Locale.getDefault()).format(cal.time)
    }

    fun dayStartFromMinute(minuteOfDay: Int, dayStart: Long = startOfTodayMillis()): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = dayStart }
        cal.set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
        cal.set(Calendar.MINUTE, minuteOfDay % 60)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun daysInMonthGrid(year: Int, month: Int): List<CalendarDayCell> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1 // Sun=0
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val cells = mutableListOf<CalendarDayCell>()
        repeat(firstDayOfWeek) { cells.add(CalendarDayCell.Empty) }
        for (day in 1..daysInMonth) {
            cal.set(Calendar.DAY_OF_MONTH, day)
            cells.add(CalendarDayCell.Day(startOfDayMillis(cal), day))
        }
        while (cells.size % 7 != 0) cells.add(CalendarDayCell.Empty)
        return cells
    }

    sealed class CalendarDayCell {
        data object Empty : CalendarDayCell()
        data class Day(val dayStart: Long, val dayNumber: Int) : CalendarDayCell()
    }
}
