package com.example.fakenews

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.simplefakenews.R // Make sure this import is correct for your R file
import com.example.simplefakenews.databinding.ActivityMainBinding // Make sure this import is correct

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Fake News Detector"

        setupBottomNavigation()

        if (savedInstanceState == null) {
            loadFragment(TextAnalysisFragment())
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_text_analysis -> {
                    loadFragment(TextAnalysisFragment())
                    true
                }
                R.id.nav_url_analysis -> {
                    loadFragment(UrlAnalysisFragment())
                    true
                }
                else -> false
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                // Launch connection settings activity
                val intent = Intent(this, ConnectionSettingsActivity::class.java)
                startActivity(intent)
                true
            }
            R.id.action_view_database -> { // Added this case
                // Launch database viewer activity
                val intent = Intent(this, DatabaseViewerActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
