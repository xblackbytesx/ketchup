package com.example.ketchup.auth

import com.example.ketchup.data.SecureStorage

class AuthManager(private val storage: SecureStorage) {
    private val pinManager = PinManager(storage)

    fun verifyPin(pin: String): PinVerifyResult = pinManager.verifyPin(pin)
    fun setPin(pin: String) = pinManager.setPin(pin)
    fun clearPin() = pinManager.clearPin()
}
