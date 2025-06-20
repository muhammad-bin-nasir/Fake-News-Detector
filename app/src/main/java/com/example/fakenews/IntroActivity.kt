package com.example.fakenews

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.simplefakenews.databinding.ActivityIntroBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class IntroActivity : AppCompatActivity() {
    private lateinit var binding: ActivityIntroBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIntroBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup click listeners
        binding.btnGetStarted.setOnClickListener {
            startMainActivity()
        }

        binding.btnSettings.setOnClickListener {
            openConnectionSettings()
        }

        // Check server connection
        checkServerConnection()
    }

    private fun checkServerConnection() {
        binding.tvServerStatus.text = "🔄 Checking server connection..."

        lifecycleScope.launch {
            try {
                // Add a small delay to show the checking status
                delay(1000)

                // Test with a simple request
                val testRequest = NewsRequest("This is a test connection.")
                val response = FakeNewsApiService.instance.predictNews(testRequest)

                // Connection successful
                binding.tvServerStatus.text = "✅ Server connected"
                binding.tvServerStatus.setTextColor(getColor(android.R.color.holo_green_dark))

            } catch (e: Exception) {
                // Connection failed
                val errorMessage = when (e) {
                    is ConnectException -> "❌ Cannot connect to server"
                    is UnknownHostException -> "❌ Server not found"
                    is SocketTimeoutException -> "❌ Connection timeout"
                    is HttpException -> "❌ Server error: ${e.code()}"
                    else -> "❌ Connection failed"
                }

                binding.tvServerStatus.text = errorMessage
                binding.tvServerStatus.setTextColor(getColor(android.R.color.holo_red_dark))
            }
        }
    }

    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish() // Close intro activity
    }

    private fun openConnectionSettings() {
        val intent = Intent(this, ConnectionSettingsActivity::class.java)
        startActivity(intent)
    }
}
