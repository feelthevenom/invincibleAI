package com.example.data

import android.net.Uri

/**
 * Builds Pollinations.ai image URLs (free, no API key).
 * Used as Tier 3 when Open Food Facts and Wikimedia have no match.
 */
object PollinationsImageUrl {
    fun build(foodName: String): String {
        val prompt = "${foodName.trim()} food photography".trim()
        val encoded = Uri.encode(if (prompt.isBlank()) "meal food photography" else prompt)
        return "https://image.pollinations.ai/prompt/$encoded"
    }
}
