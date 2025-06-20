package com.example.fakenews

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

interface FakeNewsApiService {

    @POST("predict")
    suspend fun predictNews(@Body request: NewsRequest): NewsResponse

    @POST("explain")
    suspend fun explainNews(@Body request: NewsRequest): ShapExplanationResponse

    @POST("feedback")
    suspend fun submitFeedback(@Body request: FeedbackRequest): FeedbackResponse

    @POST("scrape")
    suspend fun scrapeUrl(@Body request: UrlScrapeRequest): UrlScrapeResponse

    companion object {
        private var BASE_URL = "http://192.168.1.100:5000/"

        // ✅ FIXED: Use nullable instance that can be recreated
        @Volatile
        private var _instance: FakeNewsApiService? = null

        val instance: FakeNewsApiService
            get() {
                return _instance ?: synchronized(this) {
                    _instance ?: createInstance().also { _instance = it }
                }
            }

        private fun createInstance(): FakeNewsApiService {
            // Create OkHttpClient with increased timeouts
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)  // Increase connection timeout to 30 seconds
                .readTimeout(60, TimeUnit.SECONDS)     // Increase read timeout to 60 seconds (for SHAP)
                .writeTimeout(30, TimeUnit.SECONDS)    // Increase write timeout to 30 seconds
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)  // Use the custom client with increased timeouts
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            return retrofit.create(FakeNewsApiService::class.java)
        }

        // ✅ FIXED: Now properly recreates instance with new URL
        fun updateBaseUrl(newBaseUrl: String) {
            BASE_URL = if (newBaseUrl.endsWith("/")) {
                newBaseUrl
            } else {
                "$newBaseUrl/"
            }

            // Force recreation of instance with new URL
            synchronized(this) {
                _instance = null
            }

            println("✅ API Base URL updated to: $BASE_URL")
        }

        // Helper function to get current URL
        fun getCurrentBaseUrl(): String = BASE_URL

        // Helper function to test if URL is reachable
        suspend fun testConnection(): Boolean {
            return try {
                val testRequest = NewsRequest("Connection test")
                instance.predictNews(testRequest)
                true
            } catch (e: Exception) {
                false
            }
        }
    }
}
