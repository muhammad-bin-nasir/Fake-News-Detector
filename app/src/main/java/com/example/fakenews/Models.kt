package com.example.fakenews

// Data classes for API requests and responses
data class NewsRequest(
    val text: String
)

data class NewsResponse(
    val prediction: String,
    val confidence: Int,
    val message: String?
)

// Data classes for URL scraping
data class UrlScrapeRequest(
    val url: String
)

data class UrlScrapeResponse(
    val text: String,
    val title: String,
    val status: String,
    val message: String?
)

// Data classes for SHAP explanation
data class ShapExplanationResponse(
    val explanation: ShapExplanation,
    val prediction: String,
    val confidence: Int
)

data class ShapExplanation(
    val embeddings_contribution: Double,
    val sentiment_contribution: Double,
    val clickbait_contribution: Double,
    val base_value: Double,
    val prediction_value: Double
)

// Data class for feedback
data class FeedbackRequest(
    val text: String,
    val prediction: String,
    val confidence: Int,
    val label: String
)

data class FeedbackResponse(
    val message: String,
    val status: String
)
