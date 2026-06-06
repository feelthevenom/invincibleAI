package com.example.data

object BodyMeasurementTypes {
    data class Type(val id: String, val label: String)

    val WEIGHT = "weight"
    val CHEST = "chest"
    val WAIST = "waist"
    val BICEP = "bicep"
    val FOREARM = "forearm"
    val THIGH = "thigh"
    val HIPS = "hips"
    val BELLY = "belly"
    val NECK = "neck"
    val CALF = "calf"
    val SHOULDERS = "shoulders"

    val ALL: List<Type> = listOf(
        Type(WEIGHT, "Weight"),
        Type(CHEST, "Chest"),
        Type(WAIST, "Waist"),
        Type(BICEP, "Bicep"),
        Type(FOREARM, "Forearm"),
        Type(THIGH, "Thigh"),
        Type(HIPS, "Hips"),
        Type(BELLY, "Belly"),
        Type(NECK, "Neck"),
        Type(CALF, "Calf"),
        Type(SHOULDERS, "Shoulders")
    )

    fun labelFor(id: String): String = ALL.find { it.id == id }?.label ?: id

    fun isWeight(id: String): Boolean = id == WEIGHT
}
