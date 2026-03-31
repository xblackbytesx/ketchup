package com.example.ketchup.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ketchup.KetchupApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SetupUiState(
    val url: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
)

class SetupViewModel(private val app: KetchupApplication) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState

    fun updateUrl(url: String) {
        _uiState.value = _uiState.value.copy(url = url, error = null)
    }

    fun addFeed(onSuccess: () -> Unit) {
        val url = _uiState.value.url.trim().trimEnd('/')
        if (url.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Please enter a feed URL")
            return
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            _uiState.value = _uiState.value.copy(error = "URL must start with http:// or https://")
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                app.repository.addFeed(url)
                onSuccess()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to add feed",
                )
            }
        }
    }
}
