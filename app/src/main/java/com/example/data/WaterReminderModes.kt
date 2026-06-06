package com.example.data

object WaterReminderModes {
    const val INTERVAL = "interval"
    const val TIMES = "times"
    const val DAILY = "daily"
    const val WEEKLY = "weekly"

    val ALL = listOf(INTERVAL, TIMES, DAILY, WEEKLY)

    fun label(mode: String): String = when (mode) {
        INTERVAL -> "Remind me every"
        TIMES -> "Remind me"
        DAILY -> "Remind me every day at"
        WEEKLY -> "Remind me every week on"
        else -> mode
    }
}
