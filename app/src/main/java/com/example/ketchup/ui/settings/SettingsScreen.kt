package com.example.ketchup.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ketchup.KetchupApplication
import com.example.ketchup.auth.PinVerifyResult
import com.example.ketchup.ui.theme.TextMuted
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    app: KetchupApplication,
    onBack: () -> Unit,
    onResetApp: () -> Unit,
    onThemeChanged: () -> Unit,
) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(app))
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Show toast messages via snackbar
    LaunchedEffect(uiState.toastMessage) {
        if (uiState.toastMessage != null) {
            snackbarHostState.showSnackbar(uiState.toastMessage!!)
            viewModel.dismissToast()
        }
    }

    // File launchers
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) viewModel.exportFeeds(uri, context.contentResolver)
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importFeeds(uri, context.contentResolver)
    }

    // Dialog states
    var showSetPinDialog by remember { mutableStateOf(false) }
    var showVerifyPinDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    val biometricManager = remember { BiometricManager.from(context) }
    val biometricAvailable = biometricManager.canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_STRONG
    ) == BiometricManager.BIOMETRIC_SUCCESS

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
        ) {
            // Appearance
            item {
                SectionHeader("Appearance")
                SettingsRow(label = "Theme") {
                    val options = listOf("system", "light", "dark", "oled")
                    val labels = listOf("System", "Light", "Dark", "OLED")
                    SingleChoiceSegmentedButtonRow {
                        options.forEachIndexed { index, option ->
                            SegmentedButton(
                                selected = uiState.theme == option,
                                onClick = {
                                    viewModel.setTheme(option)
                                    onThemeChanged()
                                },
                                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                            ) { Text(labels[index], style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
                SettingsSwitchRow(
                    label = "Fullscreen reader",
                    checked = uiState.fullscreen,
                    onCheckedChange = { viewModel.setFullscreen(it) },
                )
            }

            // Feed List
            item {
                SectionDivider()
                SectionHeader("Feed List")
                SettingsSwitchRow(
                    label = "Featured layout",
                    subtitle = "Hero card every 7th article",
                    checked = uiState.featuredLayout,
                    onCheckedChange = { viewModel.setFeaturedLayout(it) },
                )
                SettingsRow(label = "Sort order") {
                    SingleChoiceSegmentedButtonRow {
                        listOf("newest_first" to "Newest", "oldest_first" to "Oldest")
                            .forEachIndexed { index, (value, label) ->
                                SegmentedButton(
                                    selected = uiState.sortOrder == value,
                                    onClick = { viewModel.setSortOrder(value) },
                                    shape = SegmentedButtonDefaults.itemShape(index, 2),
                                ) { Text(label, style = MaterialTheme.typography.labelSmall) }
                            }
                    }
                }
                SettingsSwitchRow(
                    label = "Show read articles",
                    checked = uiState.showReadArticles,
                    onCheckedChange = { viewModel.setShowReadArticles(it) },
                )
            }

            // Reader
            item {
                SectionDivider()
                SectionHeader("Reader")
                SettingsSwitchRow(
                    label = "Show hero image",
                    checked = uiState.showHeroImage,
                    onCheckedChange = { viewModel.setShowHeroImage(it) },
                )
                SettingsSwitchRow(
                    label = "Auto-mark as read",
                    subtitle = "Mark article read when you leave it",
                    checked = uiState.autoMarkRead,
                    onCheckedChange = { viewModel.setAutoMarkRead(it) },
                )
                SettingsSwitchRow(
                    label = "Swipe to navigate articles",
                    subtitle = "Swipe left/right in the reader to go to the next or previous article",
                    checked = uiState.swipeNavigation,
                    onCheckedChange = { viewModel.setSwipeNavigation(it) },
                )
            }

            // Cache
            item {
                SectionDivider()
                SectionHeader("Cache")
                SettingsRow(label = "Article cache TTL") {
                    val options = listOf(1, 6, 24, 72, 168)
                    val labels = listOf("1h", "6h", "1d", "3d", "1w")
                    SingleChoiceSegmentedButtonRow {
                        options.forEachIndexed { index, hours ->
                            SegmentedButton(
                                selected = uiState.cacheTtlHours == hours,
                                onClick = { viewModel.setCacheTtlHours(hours) },
                                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                            ) { Text(labels[index], style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
                SettingsActionRow(
                    label = "Clear fetched content",
                    onClick = { viewModel.clearCache() },
                )
            }

            // Data
            item {
                SectionDivider()
                SectionHeader("Data")
                SettingsActionRow(
                    label = "Export feeds",
                    onClick = {
                        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                        exportLauncher.launch("ketchup-feeds-$date.json")
                    },
                )
                SettingsActionRow(
                    label = "Import feeds",
                    onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                )
            }

            // Security
            item {
                SectionDivider()
                SectionHeader("Security")
                SettingsSwitchRow(
                    label = "PIN lock",
                    checked = uiState.isPinEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) showSetPinDialog = true else viewModel.clearPin()
                    },
                )
                if (uiState.isPinEnabled) {
                    SettingsActionRow(
                        label = "Change PIN",
                        onClick = { showVerifyPinDialog = true },
                    )
                }
                if (biometricAvailable) {
                    SettingsSwitchRow(
                        label = "Biometric unlock",
                        subtitle = if (!uiState.isPinEnabled) "Enable PIN first" else null,
                        checked = uiState.isBiometricEnabled,
                        onCheckedChange = { viewModel.setBiometric(it) },
                        enabled = uiState.isPinEnabled,
                    )
                }
            }

            // Danger Zone
            item {
                SectionDivider()
                SectionHeader("Danger Zone")
                SettingsActionRow(
                    label = "Reset app",
                    labelColor = MaterialTheme.colorScheme.error,
                    onClick = { showResetDialog = true },
                )
            }
        }
    }

    // Set PIN dialog
    if (showSetPinDialog) {
        PinEntryDialog(
            title = "Set PIN",
            confirmLabel = "Set",
            onConfirm = { pin ->
                if (pin.length == 4 && pin.all { it.isDigit() }) {
                    viewModel.setPin(pin)
                    showSetPinDialog = false
                }
            },
            onDismiss = { showSetPinDialog = false },
        )
    }

    // Verify then change PIN flow
    if (showVerifyPinDialog) {
        PinEntryDialog(
            title = "Current PIN",
            confirmLabel = "Verify",
            onConfirm = { pin ->
                val result = viewModel.verifyPin(pin)
                if (result is PinVerifyResult.Success) {
                    showVerifyPinDialog = false
                    showSetPinDialog = true
                }
            },
            onDismiss = { showVerifyPinDialog = false },
        )
    }

    // Reset confirm
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset app") },
            text = { Text("This will clear all PIN settings and remove all local data. Continue?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetApp()
                    showResetDialog = false
                    onResetApp()
                }) { Text("Reset", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
private fun SettingsRow(label: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
            if (subtitle != null) {
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun SettingsActionRow(
    label: String,
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    ) {
        Text(
            text = label,
            color = labelColor,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PinEntryDialog(
    title: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = pin,
                onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pin = it },
                label = { Text("4-digit PIN") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pin); pin = "" }) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = { pin = ""; onDismiss() }) { Text("Cancel") }
        },
    )
}
