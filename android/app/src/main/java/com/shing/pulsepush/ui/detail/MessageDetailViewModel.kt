package com.shing.pulsepush.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shing.pulsepush.data.local.LocalMessage
import com.shing.pulsepush.data.repository.MessageRepository
import com.shing.pulsepush.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MessageDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val messageRepository: MessageRepository
) : ViewModel() {

    private val msgId: String = savedStateHandle[Routes.ARG_MSG_ID] ?: ""

    val message: StateFlow<LocalMessage?> = messageRepository.observeMessage(msgId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // Opening the detail page marks the message read, locally and remotely.
        if (msgId.isNotBlank()) {
            viewModelScope.launch { messageRepository.markRead(msgId) }
        }
    }
}
