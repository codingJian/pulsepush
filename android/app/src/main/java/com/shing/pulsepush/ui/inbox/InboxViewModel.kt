package com.shing.pulsepush.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shing.pulsepush.data.local.LocalMessage
import com.shing.pulsepush.data.repository.MessageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InboxUiState(
    val isRefreshing: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class InboxViewModel @Inject constructor(
    private val messageRepository: MessageRepository
) : ViewModel() {

    val messages: StateFlow<List<LocalMessage>> = messageRepository.observeMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val unreadCount: StateFlow<Int> = messageRepository.observeUnreadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _uiState = MutableStateFlow(InboxUiState())
    val uiState: StateFlow<InboxUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            try {
                messageRepository.syncMessages()
            } catch (e: Exception) {
                // Offline is fine: Room content stays on screen.
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }
}
