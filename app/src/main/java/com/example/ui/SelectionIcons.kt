package com.example.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

object GoalIcons {
    fun iconFor(goal: String): ImageVector = when (goal) {
        "Lose Fat" -> Icons.Default.LocalFireDepartment
        "Gain Muscle" -> Icons.Default.FitnessCenter
        "Body Recomposition" -> Icons.Default.Timeline
        "Maintain Weight" -> Icons.Default.Balance
        "Athletic Performance" -> Icons.Default.Bolt
        else -> Icons.Default.HealthAndSafety
    }
}

object EquipmentIcons {
    fun iconFor(equipmentId: String): ImageVector = when (equipmentId) {
        "all" -> Icons.Default.GridView
        "barbell" -> Icons.Default.FitnessCenter
        "dumbbell" -> Icons.Default.SportsGymnastics
        "flat_bench" -> Icons.Default.AirlineSeatFlat
        "pull_up_bar" -> Icons.Default.Height
        "cable_machine" -> Icons.Default.Link
        "smith_machine" -> Icons.Default.Build
        "kettlebell" -> Icons.Default.Adjust
        "leg_press" -> Icons.Default.FitnessCenter
        "resistance_band" -> Icons.Default.Straighten
        "none" -> Icons.AutoMirrored.Filled.DirectionsRun
        else -> Icons.Default.Build
    }
}
