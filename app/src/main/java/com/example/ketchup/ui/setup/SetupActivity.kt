package com.example.ketchup.ui.setup

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.ketchup.KetchupApplication
import com.example.ketchup.databinding.ActivitySetupBinding
import com.example.ketchup.ui.BaseActivity
import com.example.ketchup.ui.feed.FeedActivity
import kotlinx.coroutines.launch

class SetupActivity : BaseActivity() {
    private lateinit var binding: ActivitySetupBinding
    private val repository by lazy { (application as KetchupApplication).repository }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSave.setOnClickListener { attemptAddFeed() }
    }

    private fun attemptAddFeed() {
        val url = binding.etServerUrl.text.toString().trim().trimEnd('/')

        if (url.isBlank()) {
            Toast.makeText(this, "Please enter a feed URL", Toast.LENGTH_SHORT).show()
            return
        }

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            Toast.makeText(this, "URL must start with http:// or https://", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnSave.isEnabled = false
        lifecycleScope.launch {
            try {
                repository.addFeed(url)
                startActivity(Intent(this@SetupActivity, FeedActivity::class.java))
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@SetupActivity, e.message ?: "Failed to add feed", Toast.LENGTH_LONG).show()
                binding.btnSave.isEnabled = true
            }
        }
    }
}
