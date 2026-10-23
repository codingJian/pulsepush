package com.shing.pulsepush.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shing.pulsepush.data.preferences.UserPreferences
import com.shing.pulsepush.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val serverUrl: String = "",
    val username: String? = null,
    val email: String? = null,
    val isSaving: Boolean = false,
    val isLoggingOut: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: UserPreferences,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    serverUrl = preferences.currentServerUrl(),
                )
            }
        }
        viewModelScope.launch {
            preferences.username.collect { name ->
                _state.update { it.copy(username = name) }
            }
        }
        viewModelScope.launch {
            preferences.email.collect { mail ->
                _state.update { it.copy(email = mail) }
            }
        }
    }

    fun onServerUrlChange(v: String) =
        _state.update { it.copy(serverUrl = v, message = null, error = null) }

    fun saveServerUrl() {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null, message = null) }
            try {
                preferences.saveServerUrl(_state.value.serverUrl)
                _state.update {
                    it.copy(serverUrl = preferences.currentServerUrl(), message = "服务器地址已保存")
                }
                // Re-bind against the (possibly new) server so pushes keep flowing.
                runCatching { authRepository.bindCurrentDevice() }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "保存失败") }
            } finally {
                _state.update { it.copy(isSaving = false) }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            _state.update { it.copy(isLoggingOut = true) }
            // AuthRepository.logout() calls devices/unbind BEFORE clearing
            // the session, then MainActivity's token flow returns to Login.
            runCatching { authRepository.logout() }
            _state.update { it.copy(isLoggingOut = false) }
        }
    }
}
