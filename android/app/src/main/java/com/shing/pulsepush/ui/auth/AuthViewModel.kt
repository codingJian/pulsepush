package com.shing.pulsepush.ui.auth

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

data class AuthUiState(
    val serverUrl: String = "",
    val isRegisterMode: Boolean = false,
    val username: String = "",
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val preferences: UserPreferences
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.update { it.copy(serverUrl = preferences.currentServerUrl()) }
        }
    }

    fun onServerUrlChange(v: String) = _state.update { it.copy(serverUrl = v, error = null) }
    fun onUsernameChange(v: String) = _state.update { it.copy(username = v, error = null) }
    fun onEmailChange(v: String) = _state.update { it.copy(email = v, error = null) }
    fun onPasswordChange(v: String) = _state.update { it.copy(password = v, error = null) }
    fun toggleMode() = _state.update { it.copy(isRegisterMode = !it.isRegisterMode, error = null) }

    fun submit() {
        val s = _state.value
        if (s.email.isBlank() || s.password.isBlank() || (s.isRegisterMode && s.username.isBlank())) {
            _state.update { it.copy(error = "请填写完整信息") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                // Persist the server address first: the interceptors read it
                // synchronously, so login itself already hits the new server.
                preferences.saveServerUrl(s.serverUrl)
                if (s.isRegisterMode) {
                    authRepository.register(s.username.trim(), s.email.trim(), s.password)
                } else {
                    authRepository.login(s.email.trim(), s.password)
                }
                // Success: the token flow in MainActivity navigates to Inbox.
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "请求失败，请检查服务器地址与网络") }
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }
}
