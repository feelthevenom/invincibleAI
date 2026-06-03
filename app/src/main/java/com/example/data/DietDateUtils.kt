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

    fun summarizeDays(meals: List<MealEntry>, dailyGoal: Int): Map<Long, DaySummary> {
        if (meals.isEmpty()) return emptyMap()
        return meals.groupBy { meal ->
            startOfDayMillis(Calendar.getInstance().apply { timeInMillis = meal.timestamp })
        }.mapValues { (dayStart, dayMeals) ->
            val total = dayMeals.sumOf { it.calories }
            DaySummary(
                dayStart = dayStart,
                totalCalories = total,
                hasData = dayMeals.isNotEmpty(),
                goalMet = dailyGoal > 0 && total >= (dailyGoal * 0.9).toInt()
            )
        }
    }

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
