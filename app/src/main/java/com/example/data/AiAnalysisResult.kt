package com.example.data

sealed class AiAnalysisResult {
    data class Success(val items: List<FoodItem>) : AiAnalysisResult()
    data class Error(val message: String) : AiAnalysisResult()
}

data class AiStatus(
    val mode: String,
    val isReady: Boolean,
    val label: String,
    val detail: String
)
