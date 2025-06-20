package com.example.fakenews.models

// Data classes for API communication
data class TextRequest(val text: String)

data class UrlRequest(val url: String)

data class PredictionResult(
    val prediction: String,
    val confidence: Double,
    val probabilities: Probabilities
)

data class Probabilities(
    val fake: Double,
    val real: Double
)

data class ApiResponse(
    val success: Boolean,
    val result: PredictionResult?,
    val error: String?,
    val input_type: String?,
    val scraped_text_length: Int?,
    val url: String?,
    val preview: String? = null  // Added preview field
)

data class HealthResponse(
    val status: String,
    val model_loaded: Boolean,
    val server_ip: String?
)