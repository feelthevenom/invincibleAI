package com.example.data

import kotlin.math.min

/** Lightweight fuzzy text matching for food/product search (handles typos like "nacpro" → "nakpro"). */
object FuzzyMatcher {

    fun normalize(text: String): String =
        text.lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    fun score(query: String, target: String): Int {
        val q = normalize(query)
        val t = normalize(target)
        if (q.isBlank() || t.isBlank()) return 0

        var score = 0
        when {
            t == q -> score += 1000
            t.startsWith(q) -> score += 500
            else -> {
                val words = t.split(" ")
                if (words.any { it.startsWith(q) }) score += 200
                else if (t.contains(q)) score += 120
            }
        }

        val qTokens = q.split(" ").filter { it.length >= 2 }
        val tTokens = t.split(" ").filter { it.isNotBlank() }
        for (qt in qTokens) {
            val tokenScore = tTokens.maxOfOrNull { token ->
                when {
                    token == qt -> 180
                    token.startsWith(qt) -> 140
                    qt.length >= 3 && token.contains(qt) -> 90
                    qt.length >= 4 && levenshtein(qt, token) <= 1 -> 110
                    qt.length >= 5 && levenshtein(qt, token) <= 2 -> 70
                    else -> 0
                }
            } ?: 0
            score += tokenScore
        }

        if (score == 0 && q.length >= 4) {
            val dist = levenshtein(q, t)
            if (dist <= 2) score += 250 - dist * 40
        }

        return score
    }

    fun matches(query: String, target: String, minScore: Int = 80): Boolean =
        score(query, target) >= minScore

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val prev = IntArray(b.length + 1) { it }
        val curr = IntArray(b.length + 1)
        for (i in a.indices) {
            curr[0] = i + 1
            for (j in b.indices) {
                val cost = if (a[i] == b[j]) 0 else 1
                curr[j + 1] = min(
                    min(curr[j] + 1, prev[j + 1] + 1),
                    prev[j] + cost
                )
            }
            for (j in prev.indices) prev[j] = curr[j]
        }
        return prev[b.length]
    }
}
