package com.example.ketchup.ui.lock

import android.content.Intent
import android.os.Bundle
import com.example.ketchup.KetchupApplication
import com.example.ketchup.auth.AuthManager
import com.example.ketchup.auth.BiometricHelper
import com.example.ketchup.auth.PinVerifyResult
import com.example.ketchup.data.SecureStorage
import com.example.ketchup.databinding.ActivityLockBinding
import com.example.ketchup.ui.BaseActivity
import com.example.ketchup.ui.PinViewController
import com.example.ketchup.ui.feed.FeedActivity

class LockActivity : BaseActivity() {
    private lateinit var binding: ActivityLockBinding
    private lateinit var authManager: AuthManager
    private lateinit var storage: SecureStorage
    private lateinit var pinController: PinViewController
    private var biometricHelper: BiometricHelper? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        storage = SecureStorage(this)
        authManager = AuthManager(storage)

        pinController = PinViewController(binding.root) { pin ->
            handlePinEntry(pin)
        }

        if (storage.isBiometricEnabled) {
            biometricHelper = BiometricHelper(this)
            binding.btnBiometric?.setOnClickListener { triggerBiometric() }
            triggerBiometric()
        } else {
            binding.btnBiometric?.visibility = android.view.View.GONE
        }

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finishAffinity()
            }
        })
    }

    private fun handlePinEntry(pin: String) {
        when (val result = authManager.verifyPin(pin)) {
            is PinVerifyResult.Success -> {
                pinController.clearPin()
                (application as KetchupApplication).isAuthenticated = true
                startActivity(Intent(this, FeedActivity::class.java))
                finish()
            }
            is PinVerifyResult.Wrong -> {
                pinController.showError("Wrong PIN. ${result.attemptsLeft} attempts left.")
            }
            is PinVerifyResult.LockedOut -> {
                val remaining = (result.lockoutEndsMs - System.currentTimeMillis()) / 1000
                pinController.showError("Too many attempts. Try again in ${remaining}s.")
            }
        }
    }

    private fun triggerBiometric() {
        biometricHelper?.authenticate(
            title = "Unlock Ketchup",
            subtitle = "Use biometric to unlock",
            onSuccess = {
                (application as KetchupApplication).isAuthenticated = true
                startActivity(Intent(this, FeedActivity::class.java))
                finish()
            },
            onError = { /* ignore, PIN available */ },
            onFailed = { /* ignore */ }
        )
    }
}
