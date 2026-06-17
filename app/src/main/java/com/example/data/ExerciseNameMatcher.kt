package com.example.data

object ExerciseNameMatcher {
    private val stopWords = setOf("the", "a", "an", "with", "on", "in", "and", "or", "male", "female")
    private val abbreviations = mapOf(
        "db" to "dumbbell",
        "bb" to "barbell",
        "bw" to "bodyweight",
        "ohp" to "overhead press",
        "rdl" to "romanian deadlift",
        "cgbp" to "close grip bench press"
    )

    fun expandAbbreviations(name: String): String {
        val tokens = normalize(name).split(" ").filter { it.isNotBlank() }
        if (tokens.isEmpty()) return name
        return tokens.joinToString(" ") { abbreviations[it] ?: it }
    }

    fun normalize(name: String): String =
        name.lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    fun tokens(name: String): List<String> =
        normalize(name).split(" ").filter { it.length > 1 && it !in stopWords }

    fun score(query: String, candidate: String): Int {
        val qNorm = normalize(query)
        val cNorm = normalize(candidate)
        if (qNorm.isBlank() || cNorm.isBlank()) return 0
        if (qNorm == cNorm) return 10_000

        val qTokens = tokens(query)
        val cTokens = tokens(candidate).toSet()
        if (qTokens.isEmpty()) return 0

        val expandedTokens = qTokens.map { abbreviations[it] ?: it }
        val allPresent = expandedTokens.all { qt ->
            cTokens.any { ct -> ct == qt || (qt.length >= 3 && (ct.contains(qt) || qt.contains(ct))) }
        }
        if (!allPresent) return 0

        if (cNorm.contains(qNorm)) return 5_000 + qNorm.length

        var score = 0
        for (token in qTokens) {
            score += when {
                cTokens.contains(token) -> 100
                cTokens.any { it.contains(token) || token.contains(it) } -> 60
                else -> 0
            }
        }

        val extraTokens = cTokens.count { ct ->
            qTokens.none { qt -> ct == qt || ct.contains(qt) || qt.contains(ct) }
        }
        score -= extraTokens * 25
        return score
    }
}
