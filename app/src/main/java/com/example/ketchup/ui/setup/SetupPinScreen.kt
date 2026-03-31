package com.example.ketchup.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ketchup.KetchupApplication
import com.example.ketchup.ui.components.PinInput
import com.example.ketchup.ui.theme.KetchupRed
import com.example.ketchup.ui.theme.TextMuted

private enum class PinSetupStep { ENTER, CONFIRM }

@Composable
fun SetupPinScreen(
    app: KetchupApplication,
    onComplete: () -> Unit,
) {
    var step by remember { mutableStateOf(PinSetupStep.ENTER) }
    var firstPin by remember { mutableStateOf("") }
    var currentPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var shakeError by remember { mutableStateOf(false) }

    fun handleDigit(digit: Char) {
        if (currentPin.length >= 4) return
        val newPin = currentPin + digit
        currentPin = newPin
        error = null
        shakeError = false

        if (newPin.length == 4) {
            when (step) {
                PinSetupStep.ENTER -> {
                    firstPin = newPin
                    currentPin = ""
                    step = PinSetupStep.CONFIRM
                }
                PinSetupStep.CONFIRM -> {
                    if (newPin == firstPin) {
                        app.authManager.setPin(newPin)
                        onComplete()
                    } else {
                        currentPin = ""
                        firstPin = ""
                        step = PinSetupStep.ENTER
                        error = "PINs didn't match. Please try again."
                        shakeError = true
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = KetchupRed,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (step == PinSetupStep.ENTER) "Set a PIN" else "Confirm your PIN",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (step == PinSetupStep.ENTER)
                "Create a 4-digit PIN to secure Ketchup"
            else
                "Enter the same PIN again to confirm",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
        )

        if (error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        PinInput(
            pin = currentPin,
            error = shakeError,
            onDigit = { handleDigit(it) },
            onDelete = {
                if (currentPin.isNotEmpty()) {
                    currentPin = currentPin.dropLast(1)
                    error = null
                }
            },
        )

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = onComplete) {
            Text("Skip for now")
        }
    }
}
