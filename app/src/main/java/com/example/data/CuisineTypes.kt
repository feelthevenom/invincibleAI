package com.example.data

object CuisineTypes {
    val ALL = listOf(
        "South Indian",
        "North Indian",
        "Chinese",
        "Continental",
        "Mediterranean",
        "Japanese",
        "Mexican",
        "Middle Eastern"
    )

    fun parsePreferences(stored: String): List<String> =
        if (stored.isBlank()) emptyList()
        else stored.split(",").map { it.trim() }.filter { it in ALL }

    fun serializePreferences(selected: List<String>): String =
        selected.filter { it in ALL }.joinToString(",")
}

