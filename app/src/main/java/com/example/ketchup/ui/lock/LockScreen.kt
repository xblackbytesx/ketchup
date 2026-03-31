package com.example.ketchup.ui.lock

import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RssFeed
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ketchup.KetchupApplication
import com.example.ketchup.auth.BiometricHelper
import com.example.ketchup.ui.components.PinInput
import com.example.ketchup.ui.theme.KetchupRed
import com.example.ketchup.ui.theme.TextMuted
import kotlinx.coroutines.delay

@Composable
fun LockScreen(
    app: KetchupApplication,
    activity: FragmentActivity,
    onUnlocked: () -> Unit,
) {
    val viewModel = remember { LockViewModel(app) }
    val uiState by viewModel.uiState.collectAsState()
    val biometricHelper = remember { BiometricHelper(activity) }

    // Intercept back press — no exit from lock screen
    BackHandler(enabled = true) {
        activity.finishAffinity()
    }

    // Navigate when auth succeeds
    LaunchedEffect(Unit) {
        viewModel.authenticated.collect { authenticated ->
            if (authenticated) onUnlocked()
        }
    }

    // Trigger biometric on launch
    LaunchedEffect(Unit) {
        if (app.secureStorage.isBiometricEnabled && biometricHelper.isAvailable()) {
            biometricHelper.authenticate(
                title = "Unlock Ketchup",
                subtitle = "Use biometric to unlock",
                onSuccess = { viewModel.notifyAuthenticated() },
                onError = { /* user will use PIN */ },
                onFailed = { /* ignore */ },
            )
        }
    }

    // Countdown refresh while locked out
    LaunchedEffect(uiState.lockedOut) {
        if (uiState.lockedOut) {
            while (true) {
                delay(1000)
                viewModel.checkLockout()
                if (!viewModel.uiState.value.lockedOut) break
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
            imageVector = Icons.Default.RssFeed,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = KetchupRed,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Ketchup",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Enter your PIN to unlock",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
        )

        if (uiState.errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = uiState.errorMessage!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        PinInput(
            pin = uiState.pin,
            error = uiState.error,
            onDigit = { if (!uiState.lockedOut) viewModel.onDigit(it) },
            onDelete = { viewModel.onDelete() },
        )
    }
}
