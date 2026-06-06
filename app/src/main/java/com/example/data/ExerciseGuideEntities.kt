package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_exercise_guides")
data class CachedExerciseGuide(
    @PrimaryKey val lookupKey: String,
    val exerciseId: String,
    val apiName: String,
    val gifUrl: String,
    val localGifPath: String?,
    val instructionsJson: String,
    val targetMusclesJson: String,
    val equipmentsJson: String,
    val bodyPartsJson: String,
    val source: String = "api",
    val cachedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "coach_chat_history")
data class CoachChatHistoryEntity(
    @PrimaryKey val id: String,
    val startedAt: Long,
    val updatedAt: Long,
    val preview: String,
    val messagesJson: String
)
