package com.example.data

object WaterTips {
    private val tips = listOf(
        "When traveling by plane, stay hydrated by drinking lots of water and avoiding alcohol and salty foods.",
        "Drinking water before meals can help with digestion and portion control.",
        "Carry a reusable bottle so you can sip consistently throughout the day.",
        "If you feel hungry between meals, try a glass of water first — thirst is often mistaken for hunger.",
        "Spread your intake across the day instead of drinking most of your goal in one sitting.",
        "After workouts, replenish fluids to support recovery and performance."
    )

    fun tipForDay(dayStart: Long): String {
        val index = ((dayStart / DietDateUtils.DAY_MS) % tips.size).toInt().coerceAtLeast(0)
        return tips[index]
    }
}
