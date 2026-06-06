package com.example.data

import com.example.CoachChatMessage
import com.example.CoachChatRole
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class CoachHistorySession(
    val id: String,
    val startedAt: Long,
    val updatedAt: Long,
    val preview: String,
    val messages: List<CoachChatMessage>
) {
    fun formattedTimestamp(): String =
        HISTORY_FORMAT.format(Date(updatedAt))
}

private val HISTORY_FORMAT = SimpleDateFormat("MMM d, yyyy · HH:mm:ss", Locale.getDefault())

class CoachHistoryRepository(private val gymDao: GymDao) {
    suspend fun loadHistory(): List<CoachHistorySession> =
        gymDao.getCoachChatHistory().map { it.toSession() }

    suspend fun archiveSession(messages: List<CoachChatMessage>) {
        if (messages.none { it.role == CoachChatRole.USER }) return
        val startedAt = messages.minOf { it.timestamp }
        val updatedAt = messages.maxOf { it.timestamp }
        val preview = messages.firstOrNull { it.role == CoachChatRole.USER }?.text?.take(80).orEmpty()
        val entity = CoachChatHistoryEntity(
            id = UUID.randomUUID().toString(),
            startedAt = startedAt,
            updatedAt = updatedAt,
            preview = preview.ifBlank { "Coach conversation" },
            messagesJson = encodeMessages(messages)
        )
        gymDao.insertCoachChatHistory(entity)
        trimHistoryToMax(3)
    }

    private suspend fun trimHistoryToMax(max: Int) {
        val all = gymDao.getCoachChatHistory()
        if (all.size <= max) return
        all.drop(max).forEach { gymDao.deleteCoachChatHistory(it.id) }
    }

    suspend fun getSession(id: String): CoachHistorySession? =
        gymDao.getCoachChatHistoryById(id)?.toSession()

    private fun CoachChatHistoryEntity.toSession() = CoachHistorySession(
        id = id,
        startedAt = startedAt,
        updatedAt = updatedAt,
        preview = preview,
        messages = decodeMessages(messagesJson)
    )

    private fun encodeMessages(messages: List<CoachChatMessage>): String {
        val array = JSONArray()
        messages.forEach { msg ->
            array.put(
                JSONObject()
                    .put("id", msg.id)
                    .put("role", msg.role.name)
                    .put("text", msg.text)
                    .put("timestamp", msg.timestamp)
            )
        }
        return array.toString()
    }

    private fun decodeMessages(json: String): List<CoachChatMessage> {
        return try {
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        CoachChatMessage(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            role = CoachChatRole.valueOf(obj.optString("role", CoachChatRole.ASSISTANT.name)),
                            text = obj.optString("text", ""),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
