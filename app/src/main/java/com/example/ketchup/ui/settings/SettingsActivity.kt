package com.example.ketchup.ui.settings

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.example.ketchup.KetchupApplication
import com.example.ketchup.auth.AuthManager
import com.example.ketchup.auth.BiometricHelper
import com.example.ketchup.data.ArticleRepository
import com.example.ketchup.data.PreferencesManager
import com.example.ketchup.data.SecureStorage
import com.example.ketchup.data.db.AppDatabase
import com.example.ketchup.databinding.ActivitySettingsBinding
import com.example.ketchup.ui.BaseActivity
import com.example.ketchup.ui.ThemeHelper
import kotlinx.coroutines.launch

class SettingsActivity : BaseActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: PreferencesManager
    private lateinit var storage: SecureStorage
    private lateinit var authManager: AuthManager
    private lateinit var repository: ArticleRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"

        prefs = PreferencesManager(this)
        storage = SecureStorage(this)
        authManager = AuthManager(storage)
        val app = application as KetchupApplication
        repository = ArticleRepository(
            db = AppDatabase.getInstance(this),
            api = app.api,
            fetcher = app.fetcher,
            secureStorage = storage,
            prefs = prefs
        )

        setupAccountSection()
        setupDisplaySection()
        setupCacheSection()
        setupSecuritySection()
    }

    private fun setupAccountSection() {
        binding.tvServerUrl.text = storage.serverUrl
        binding.btnLogout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("This will clear all credentials. Continue?")
                .setPositiveButton("Logout") { _, _ ->
                    storage.clearAll()
                    finishAffinity()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun setupDisplaySection() {
        val themes = arrayOf("System default", "Light", "Dark", "OLED")
        val themeValues = arrayOf(ThemeHelper.THEME_SYSTEM, ThemeHelper.THEME_LIGHT, ThemeHelper.THEME_DARK, ThemeHelper.THEME_OLED)
        val currentThemeIdx = themeValues.indexOf(prefs.theme).coerceAtLeast(0)
        binding.spinnerTheme.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, themes)
        binding.spinnerTheme.setSelection(currentThemeIdx)
        binding.spinnerTheme.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = themeValues[position]
                if (selected == prefs.theme) return  // initial setSelection or no real change
                prefs.theme = selected
                recreate()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.switchFullscreen.isChecked = prefs.fullscreen
        binding.switchFullscreen.setOnCheckedChangeListener { _, checked ->
            prefs.fullscreen = checked
            recreate()  // re-apply window flags immediately
        }

        binding.switchFeaturedLayout.isChecked = prefs.featuredLayout
        binding.switchFeaturedLayout.setOnCheckedChangeListener { _, checked ->
            prefs.featuredLayout = checked
        }

        binding.switchShowRead.isChecked = prefs.showReadArticles
        binding.switchShowRead.setOnCheckedChangeListener { _, checked ->
            prefs.showReadArticles = checked
        }

        val sortOptions = arrayOf("Newest first", "Oldest first")
        val sortValues = arrayOf("newest_first", "oldest_first")
        val currentSortIdx = sortValues.indexOf(prefs.sortOrder).coerceAtLeast(0)
        binding.spinnerSort.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, sortOptions)
        binding.spinnerSort.setSelection(currentSortIdx)
        binding.spinnerSort.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                prefs.sortOrder = sortValues[position]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupCacheSection() {
        val ttlOptions = arrayOf("1 hour", "6 hours", "24 hours", "3 days", "1 week")
        val ttlValues = arrayOf(1, 6, 24, 72, 168)
        val currentTtlIdx = ttlValues.indexOf(prefs.cacheTtlHours).coerceAtLeast(2)
        binding.spinnerCacheTtl.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, ttlOptions)
        binding.spinnerCacheTtl.setSelection(currentTtlIdx)
        binding.spinnerCacheTtl.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                prefs.cacheTtlHours = ttlValues[position]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.switchAutoMarkRead.isChecked = prefs.autoMarkRead
        binding.switchAutoMarkRead.setOnCheckedChangeListener { _, checked ->
            prefs.autoMarkRead = checked
        }

        binding.btnClearCache.setOnClickListener {
            lifecycleScope.launch {
                repository.clearFetchedContent()
                Toast.makeText(this@SettingsActivity, "Cache cleared", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupSecuritySection() {
        binding.switchPin.isChecked = storage.isPinConfigured()
        binding.switchPin.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                showSetPinDialog()
            } else {
                authManager.clearPin()
            }
        }

        binding.btnChangePin.setOnClickListener { showSetPinDialog() }

        val biometricHelper = BiometricHelper(this)
        if (!biometricHelper.isAvailable()) {
            binding.switchBiometric.isEnabled = false
        }
        binding.switchBiometric.isChecked = storage.isBiometricEnabled
        binding.switchBiometric.setOnCheckedChangeListener { _, checked ->
            if (checked && !storage.isPinConfigured()) {
                binding.switchBiometric.isChecked = false
                Toast.makeText(this, "Enable PIN first", Toast.LENGTH_SHORT).show()
            } else {
                storage.isBiometricEnabled = checked
            }
        }
    }

    private fun showSetPinDialog() {
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "Enter 4-digit PIN"
        }
        AlertDialog.Builder(this)
            .setTitle("Set PIN")
            .setView(input)
            .setPositiveButton("Set") { _, _ ->
                val pin = input.text.toString()
                input.text?.clear()
                if (pin.length == 4 && pin.all { it.isDigit() }) {
                    authManager.setPin(pin)
                    binding.switchPin.isChecked = true
                    Toast.makeText(this, "PIN set successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "PIN must be exactly 4 digits", Toast.LENGTH_SHORT).show()
                    binding.switchPin.isChecked = storage.isPinConfigured()
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                input.text?.clear()
                binding.switchPin.isChecked = storage.isPinConfigured()
            }
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
