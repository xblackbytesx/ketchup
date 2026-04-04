package com.example.ketchup.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ketchup.KetchupApplication
import com.example.ketchup.auth.PinVerifyResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

data class SettingsUiState(
    val theme: String = "dark",
    val showReadArticles: Boolean = true,
    val featuredLayout: Boolean = false,
    val cacheTtlHours: Int = 24,
    val autoMarkRead: Boolean = false,
    val sortOrder: String = "newest_first",
    val fullscreen: Boolean = true,
    val showHeroImage: Boolean = true,
    val swipeNavigation: Boolean = true,
    val isPinEnabled: Boolean = false,
    val isBiometricEnabled: Boolean = false,
    val toastMessage: String? = null,
)

class SettingsViewModel(private val app: KetchupApplication) : ViewModel() {

    private val prefs = app.prefsManager
    private val storage = app.secureStorage
    private val authManager = app.authManager
    private val repository = app.repository

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            theme = prefs.theme,
            showReadArticles = prefs.showReadArticles,
            featuredLayout = prefs.featuredLayout,
            cacheTtlHours = prefs.cacheTtlHours,
            autoMarkRead = prefs.autoMarkRead,
            sortOrder = prefs.sortOrder,
            fullscreen = prefs.fullscreen,
            showHeroImage = prefs.showHeroImage,
            swipeNavigation = prefs.swipeNavigation,
            isPinEnabled = storage.isPinConfigured(),
            isBiometricEnabled = storage.isBiometricEnabled,
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState

    fun setTheme(theme: String) {
        prefs.theme = theme
        _uiState.value = _uiState.value.copy(theme = theme)
    }

    fun setShowReadArticles(show: Boolean) {
        prefs.showReadArticles = show
        _uiState.value = _uiState.value.copy(showReadArticles = show)
    }

    fun setFeaturedLayout(enabled: Boolean) {
        prefs.featuredLayout = enabled
        _uiState.value = _uiState.value.copy(featuredLayout = enabled)
    }

    fun setCacheTtlHours(hours: Int) {
        prefs.cacheTtlHours = hours
        _uiState.value = _uiState.value.copy(cacheTtlHours = hours)
    }

    fun setAutoMarkRead(enabled: Boolean) {
        prefs.autoMarkRead = enabled
        _uiState.value = _uiState.value.copy(autoMarkRead = enabled)
    }

    fun setSortOrder(order: String) {
        prefs.sortOrder = order
        _uiState.value = _uiState.value.copy(sortOrder = order)
    }

    fun setFullscreen(enabled: Boolean) {
        prefs.fullscreen = enabled
        _uiState.value = _uiState.value.copy(fullscreen = enabled)
    }

    fun setShowHeroImage(enabled: Boolean) {
        prefs.showHeroImage = enabled
        _uiState.value = _uiState.value.copy(showHeroImage = enabled)
    }

    fun setSwipeNavigation(enabled: Boolean) {
        prefs.swipeNavigation = enabled
        _uiState.value = _uiState.value.copy(swipeNavigation = enabled)
    }

    fun setBiometric(enabled: Boolean) {
        if (enabled && !storage.isPinConfigured()) {
            showToast("Enable PIN first")
            return
        }
        storage.isBiometricEnabled = enabled
        _uiState.value = _uiState.value.copy(isBiometricEnabled = enabled)
    }

    fun setPin(pin: String) {
        authManager.setPin(pin)
        _uiState.value = _uiState.value.copy(isPinEnabled = true)
        showToast("PIN set successfully")
    }

    fun clearPin() {
        authManager.clearPin()
        storage.isBiometricEnabled = false
        _uiState.value = _uiState.value.copy(isPinEnabled = false, isBiometricEnabled = false)
    }

    fun verifyPin(pin: String): PinVerifyResult = authManager.verifyPin(pin)

    fun clearCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.clearFetchedContent() }
            showToast("Cache cleared")
        }
    }

    fun exportFeeds(uri: Uri, contentResolver: android.content.ContentResolver) {
        viewModelScope.launch {
            try {
                val json = withContext(Dispatchers.IO) { repository.exportFeedsJson() }
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                }
                showToast("Feeds exported")
            } catch (e: Exception) {
                showToast("Export failed: ${e.message}")
            }
        }
    }

    fun importFeeds(uri: Uri, contentResolver: android.content.ContentResolver) {
        viewModelScope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { stream ->
                        val out = ByteArrayOutputStream()
                        val buf = ByteArray(4096)
                        var total = 0
                        var n: Int
                        while (stream.read(buf).also { n = it } != -1) {
                            total += n
                            if (total > 1_048_576) throw Exception("Backup file too large (max 1 MB)")
                            out.write(buf, 0, n)
                        }
                        out.toString(Charsets.UTF_8.name())
                    } ?: throw Exception("Could not read file")
                }
                val count = withContext(Dispatchers.IO) { repository.importFeedsJson(json) }
                showToast("Imported $count feed${if (count == 1) "" else "s"}")
            } catch (e: Exception) {
                showToast("Import failed: ${e.message}")
            }
        }
    }

    fun resetApp() {
        storage.clearAll()
        prefs.apply {
            theme = "dark"
            showReadArticles = true
            featuredLayout = false
            cacheTtlHours = 24
            autoMarkRead = false
            sortOrder = "newest_first"
            fullscreen = true
            showHeroImage = true
        }
    }

    fun dismissToast() {
        _uiState.value = _uiState.value.copy(toastMessage = null)
    }

    private fun showToast(message: String) {
        _uiState.value = _uiState.value.copy(toastMessage = message)
    }
}

class SettingsViewModelFactory(private val app: KetchupApplication) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(app) as T
}
