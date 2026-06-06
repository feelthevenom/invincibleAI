package com.example.data

/**
 * Fuzzy search for AI model pickers — supports subsequence matching and collapsed duplicate letters
 * (e.g. "opennn" still matches "OpenAI" via "open").
 */
object ModelSearchUtils {

    fun filterAndRank(models: List<AiProviderConfig.AiModel>, rawQuery: String): List<AiProviderConfig.AiModel> {
        val query = rawQuery.trim()
        if (query.isBlank()) return models
        return models
            .mapNotNull { model ->
                val score = scoreModel(model, query)
                if (score > 0) score to model else null
            }
            .sortedByDescending { it.first }
            .map { it.second }
    }

    fun scoreModel(model: AiProviderConfig.AiModel, rawQuery: String): Int {
        val query = rawQuery.trim().lowercase()
        if (query.isBlank()) return 1
        val collapsed = collapseRepeatedLetters(query)
        val haystacks = listOf(model.displayName, model.id)
        return haystacks.maxOf { hay -> scoreHaystack(hay.lowercase(), query, collapsed) }
    }

    private fun scoreHaystack(hay: String, query: String, collapsedQuery: String): Int {
        if (hay == query) return 1000
        if (hay.startsWith(query)) return 520
        if (hay.contains(query)) return 380
        hay.split(Regex("[\\s/._:-]+")).forEach { word ->
            if (word.startsWith(query)) return 450
            if (query.length >= 2 && word.contains(query)) return 340
        }
        if (subsequenceMatch(hay, query)) return 260 + query.length * 4
        if (collapsedQuery != query && subsequenceMatch(hay, collapsedQuery)) return 220 + collapsedQuery.length * 4
        return 0
    }

    private fun subsequenceMatch(hay: String, query: String): Boolean {
        if (query.isEmpty()) return true
        var qi = 0
        for (c in hay) {
            if (c == query[qi]) {
                qi++
                if (qi == query.length) return true
            }
        }
        return false
    }

    private fun collapseRepeatedLetters(text: String): String =
        buildString {
            var last: Char? = null
            for (c in text) {
                if (c != last) {
                    append(c)
                    last = c
                }
            }
        }
}
