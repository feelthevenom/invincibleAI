package com.example.data

import org.json.JSONArray
import org.json.JSONObject

object ExerciseStepFormatter {
    private val stepPrefix = Regex("^Step:(\\d+)\\s*", RegexOption.IGNORE_CASE)

    fun toApiFormat(steps: List<String>): List<String> =
        steps.mapIndexed { index, raw ->
            val trimmed = raw.trim()
            if (stepPrefix.containsMatchIn(trimmed)) trimmed
            else {
                val withoutNumber = trimmed.removePrefix("${index + 1}.").removePrefix("${index + 1})").trim()
                "Step:${index + 1} $withoutNumber"
            }
        }

    fun parseAiSteps(raw: String): List<String> {
        val regex = Regex("\\[[\\s\\S]*\\]")
        val jsonArray = regex.find(raw)?.value?.let { JSONArray(it) }
        if (jsonArray != null) {
            return buildList {
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.get(i)
                    when (item) {
                        is JSONObject -> {
                            val step = item.optString("step", item.optString("instruction", "")).trim()
                            if (step.isNotBlank()) add(step)
                        }
                        is String -> if (item.isNotBlank()) add(item.trim())
                    }
                }
            }.let { toApiFormat(it) }.takeIf { it.isNotEmpty() }
                ?: emptyList()
        }

        val lines = raw.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { line ->
                line.removePrefix("-").removePrefix("•").trim()
            }
        return toApiFormat(lines)
    }

    data class StepParts(val tag: String, val body: String)

    fun parts(step: String, fallbackIndex: Int): StepParts {
        val match = Regex("^Step:(\\d+)\\s*(.*)", RegexOption.IGNORE_CASE).find(step.trim())
        return if (match != null) {
            StepParts("Step:${match.groupValues[1]}", match.groupValues[2].trim())
        } else {
            StepParts("Step:${fallbackIndex + 1}", step.trim())
        }
    }
}
