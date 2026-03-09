package com.example.ketchup.ui.setup

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.ketchup.KetchupApplication
import com.example.ketchup.data.SecureStorage
import com.example.ketchup.databinding.ActivitySetupBinding
import com.example.ketchup.ui.BaseActivity
import com.example.ketchup.ui.feed.FeedActivity
import com.example.ketchup.ui.lock.LockActivity
import kotlinx.coroutines.launch

class SetupActivity : BaseActivity() {
    private lateinit var binding: ActivitySetupBinding
    private lateinit var storage: SecureStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        storage = SecureStorage(this)

        binding.btnSave.setOnClickListener { attemptLogin() }
    }

    private fun attemptLogin() {
        val url = binding.etServerUrl.text.toString().trim().trimEnd('/')
        val username = binding.etUsername.text.toString().trim()
        val apiPassword = binding.etApiPassword.text.toString().trim()

        if (url.isBlank() || username.isBlank() || apiPassword.isBlank()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnSave.isEnabled = false
        val api = (application as KetchupApplication).api
        lifecycleScope.launch {
            try {
                val token = api.login(url, username, apiPassword)
                storage.serverUrl = url
                storage.username = username
                storage.apiPassword = apiPassword
                storage.authToken = token

                val intent = if (storage.isPinConfigured()) {
                    Intent(this@SetupActivity, LockActivity::class.java)
                } else {
                    Intent(this@SetupActivity, FeedActivity::class.java)
                }
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@SetupActivity, "Login failed: ${e.message}", Toast.LENGTH_LONG).show()
                binding.btnSave.isEnabled = true
            }
        }
    }
}
