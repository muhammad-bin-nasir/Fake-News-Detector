package com.example.fakenews

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.fakenews.db.AnalysisRecord
import com.example.fakenews.db.DatabaseHelper
import com.example.simplefakenews.databinding.ActivityDatabaseViewerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DatabaseViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDatabaseViewerBinding
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var adapter: AnalysisHistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDatabaseViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = DatabaseHelper(this)

        setSupportActionBar(binding.toolbarDatabaseViewer)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbarDatabaseViewer.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        setupRecyclerView()
        loadHistoryData()

        binding.btnClearHistory.setOnClickListener {
            confirmClearHistory()
        }
    }

    private fun setupRecyclerView() {
        adapter = AnalysisHistoryAdapter(emptyList())
        binding.rvAnalysisHistory.layoutManager = LinearLayoutManager(this)
        binding.rvAnalysisHistory.adapter = adapter
    }

    private fun loadHistoryData() {
        lifecycleScope.launch {
            val records = withContext(Dispatchers.IO) {
                dbHelper.getAllAnalyses()
            }
            if (records.isEmpty()) {
                binding.tvEmptyHistory.visibility = View.VISIBLE
                binding.rvAnalysisHistory.visibility = View.GONE
                binding.btnClearHistory.visibility = View.GONE
            } else {
                binding.tvEmptyHistory.visibility = View.GONE
                binding.rvAnalysisHistory.visibility = View.VISIBLE
                binding.btnClearHistory.visibility = View.VISIBLE
                adapter.updateData(records)
            }
        }
    }

    private fun confirmClearHistory() {
        AlertDialog.Builder(this)
            .setTitle("Clear History")
            .setMessage("Are you sure you want to delete all analysis history? This action cannot be undone.")
            .setPositiveButton("Clear All") { _, _ ->
                clearHistory()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun clearHistory() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                dbHelper.clearHistory()
            }
            Toast.makeText(this@DatabaseViewerActivity, "History cleared", Toast.LENGTH_SHORT).show()
            loadHistoryData() // Refresh the list
        }
    }
}
