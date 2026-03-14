package com.example.ketchup.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureStorage(context: Context) {
    private val prefs: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        prefs = EncryptedSharedPreferences.create(
            context,
            "ketchup_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    var pinHash: String
        get() = prefs.getString(KEY_PIN_HASH, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PIN_HASH, value).apply()

    var pinSalt: String
        get() = prefs.getString(KEY_PIN_SALT, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PIN_SALT, value).apply()

    var isPinEnabled: Boolean
        get() = prefs.getBoolean(KEY_PIN_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_PIN_ENABLED, value).apply()

    var isBiometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, value).apply()

    var pinLockoutEnd: Long
        get() = prefs.getLong(KEY_PIN_LOCKOUT_END, 0L)
        set(value) = prefs.edit().putLong(KEY_PIN_LOCKOUT_END, value).apply()

    var pinFailCount: Int
        get() = prefs.getInt(KEY_PIN_FAIL_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_PIN_FAIL_COUNT, value).apply()

    fun isPinConfigured(): Boolean = isPinEnabled && pinHash.isNotBlank()

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_PIN_ENABLED = "pin_enabled"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_PIN_LOCKOUT_END = "pin_lockout_end"
        private const val KEY_PIN_FAIL_COUNT = "pin_fail_count"
    }
}
