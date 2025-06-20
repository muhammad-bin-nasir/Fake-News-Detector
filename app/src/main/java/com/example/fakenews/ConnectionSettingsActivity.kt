package com.example.fakenews

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.simplefakenews.databinding.ActivityConnectionSettingsBinding
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.*

class ConnectionSettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityConnectionSettingsBinding
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConnectionSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences("FakeNewsSettings", Context.MODE_PRIVATE)

        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Connection Settings"

        // Load saved settings
        loadSettings()

        // Setup click listeners
        setupClickListeners()

        // Update current settings display
        updateCurrentSettingsDisplay()
    }

    private fun setupClickListeners() {
        binding.btnTestConnection.setOnClickListener {
            testConnection()
        }

        binding.btnSaveSettings.setOnClickListener {
            saveSettings()
        }

        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }

    private fun loadSettings() {
        val savedIp = sharedPreferences.getString("server_ip", "192.168.1.100") ?: "192.168.1.100"
        val savedPort = sharedPreferences.getString("server_port", "5000") ?: "5000"

        binding.etServerIp.setText(savedIp)
        binding.etServerPort.setText(savedPort)
    }

    private fun saveSettings() {
        val serverIp = binding.etServerIp.text.toString().trim()
        val serverPort = binding.etServerPort.text.toString().trim()

        if (serverIp.isEmpty()) {
            showError("Please enter a server IP address")
            return
        }

        if (serverPort.isEmpty()) {
            showError("Please enter a server port")
            return
        }

        // Validate port number
        val portNumber = serverPort.toIntOrNull()
        if (portNumber == null || portNumber < 1 || portNumber > 65535) {
            showError("Please enter a valid port number (1-65535)")
            return
        }

        // Save to SharedPreferences
        with(sharedPreferences.edit()) {
            putString("server_ip", serverIp)
            putString("server_port", serverPort)
            apply()
        }

        // Update API service base URL
        FakeNewsApiService.updateBaseUrl("http://$serverIp:$serverPort/")

        // Update display
        updateCurrentSettingsDisplay()

        showSuccess("Settings saved successfully!")
    }

    private fun testConnection() {
        val serverIp = binding.etServerIp.text.toString().trim()
        val serverPort = binding.etServerPort.text.toString().trim()

        if (serverIp.isEmpty() || serverPort.isEmpty()) {
            showError("Please enter both IP address and port")
            return
        }

        // Validate port number
        val portNumber = serverPort.toIntOrNull()
        if (portNumber == null || portNumber < 1 || portNumber > 65535) {
            showError("Please enter a valid port number (1-65535)")
            return
        }

        // Show testing status
        binding.tvConnectionStatus.text = "🔄 Testing..."
        binding.tvConnectionStatus.setTextColor(getColor(android.R.color.holo_orange_dark))
        binding.btnTestConnection.isEnabled = false

        lifecycleScope.launch {
            try {
                // Temporarily update the API service for testing
                val testUrl = "http://$serverIp:$serverPort/"
                FakeNewsApiService.updateBaseUrl(testUrl)

                // Test with a simple request using the correct NewsRequest from com.example.fakenews
                val testRequest = NewsRequest("This is a test connection.")
                val response = FakeNewsApiService.instance.predictNews(testRequest)

                // Connection successful
                binding.tvConnectionStatus.text = "🟢 Online"
                binding.tvConnectionStatus.setTextColor(getColor(android.R.color.holo_green_dark))

                // Update last test time
                val currentTime = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date())
                binding.tvLastTest.text = "Success at $currentTime"

                showSuccess("✅ Connection successful! Server is responding.")

            } catch (e: Exception) {
                // Connection failed
                binding.tvConnectionStatus.text = "🔴 Offline"
                binding.tvConnectionStatus.setTextColor(getColor(android.R.color.holo_red_dark))

                val currentTime = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date())
                binding.tvLastTest.text = "Failed at $currentTime"

                val errorMessage = when (e) {
                    is ConnectException -> "❌ Cannot connect to server. Check if the server is running."
                    is UnknownHostException -> "❌ Server not found. Check the IP address."
                    is SocketTimeoutException -> "❌ Connection timeout. Server might be slow or unreachable."
                    is HttpException -> "❌ Server error: ${e.code()}. Check server configuration."
                    else -> "❌ Connection failed: ${e.message}"
                }

                showError(errorMessage)
            } finally {
                binding.btnTestConnection.isEnabled = true
            }
        }
    }

    private fun updateCurrentSettingsDisplay() {
        val currentIp = binding.etServerIp.text.toString().ifEmpty { "Not set" }
        val currentPort = binding.etServerPort.text.toString().ifEmpty { "Not set" }

        binding.tvCurrentUrl.text = "http://$currentIp:$currentPort"

        // Load last test result if available
        val lastTest = sharedPreferences.getString("last_test_result", "Never tested")
        binding.tvLastTest.text = lastTest
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showSuccess(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
