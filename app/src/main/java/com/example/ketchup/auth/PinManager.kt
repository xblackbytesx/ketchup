package com.example.ketchup.auth

import android.util.Base64
import com.example.ketchup.data.SecureStorage
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

sealed class PinVerifyResult {
    object Success : PinVerifyResult()
    data class Wrong(val attemptsLeft: Int) : PinVerifyResult()
    data class LockedOut(val lockoutEndsMs: Long) : PinVerifyResult()
}

class PinManager(private val storage: SecureStorage) {
    companion object {
        private const val ITERATIONS = 100_000
        private const val KEY_LENGTH = 256
        private const val MAX_ATTEMPTS = 5
        private val random = SecureRandom()
    }

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        val hash = hashPin(pin, salt)
        storage.setPinAtomic(saltB64, hash)
    }

    fun verifyPin(pin: String): PinVerifyResult {
        if (!storage.isPinConfigured()) return PinVerifyResult.Wrong(0)

        val now = System.currentTimeMillis()
        val lockoutEnd = storage.pinLockoutEnd
        if (lockoutEnd > now) return PinVerifyResult.LockedOut(lockoutEnd)

        val saltStr = storage.pinSalt
        if (saltStr.isBlank()) return PinVerifyResult.Wrong(0)
        val salt = Base64.decode(saltStr, Base64.NO_WRAP)
        val expected = storage.pinHash
        val actual = hashPin(pin, salt)

        return if (MessageDigest.isEqual(expected.toByteArray(), actual.toByteArray())) {
            storage.pinFailCount = 0
            storage.pinLockoutEnd = 0L
            PinVerifyResult.Success
        } else {
            val failCount = storage.pinFailCount + 1
            storage.pinFailCount = failCount
            val lockoutMs = when {
                failCount >= 8 -> 10 * 60_000L
                failCount >= 7 -> 5 * 60_000L
                failCount >= 6 -> 60_000L
                failCount >= 5 -> 30_000L
                else -> 0L
            }
            if (lockoutMs > 0) {
                storage.pinLockoutEnd = now + lockoutMs
                PinVerifyResult.LockedOut(now + lockoutMs)
            } else {
                PinVerifyResult.Wrong(MAX_ATTEMPTS - failCount)
            }
        }
    }

    fun clearPin() {
        storage.clearPinAtomic()
    }

    private fun hashPin(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }
}
