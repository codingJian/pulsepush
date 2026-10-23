package com.shing.pulsepush.ui.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shing.pulsepush.data.preferences.UserPreferences
import com.shing.pulsepush.data.remote.ChannelDto
import com.shing.pulsepush.data.repository.ChannelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChannelsUiState(
    val channels: List<ChannelDto> = emptyList(),
    val serverUrl: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val showCreateDialog: Boolean = false,
    val newName: String = "",
    val newDescription: String = ""
)

@HiltViewModel
class ChannelsViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
    private val preferences: UserPreferences
) : ViewModel() {

    private val _state = MutableStateFlow(ChannelsUiState())
    val state: StateFlow<ChannelsUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val server = preferences.currentServerUrl()
                val list = channelRepository.fetchChannels()
                _state.update { it.copy(channels = list, serverUrl = server) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "加载通道失败") }
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun webhookUrl(channel: ChannelDto): String =
        _state.value.serverUrl.trimEnd('/') + "/api/v1/push/" + channel.channelToken

    fun curlExample(channel: ChannelDto): String =
        "curl -X POST '${webhookUrl(channel)}' \\\n" +
            "  -H 'Content-Type: application/json' \\\n" +
            "  -d '{\"title\":\"测试通知\",\"body\":\"Hello from curl\",\"level\":\"info\"}'"

    fun setShowCreateDialog(show: Boolean) = _state.update { it.copy(showCreateDialog = show) }
    fun onNewNameChange(v: String) = _state.update { it.copy(newName = v) }
    fun onNewDescriptionChange(v: String) = _state.update { it.copy(newDescription = v) }

    fun createChannel() {
        val s = _state.value
        if (s.newName.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                channelRepository.createChannel(
                    s.newName.trim(),
                    s.newDescription.trim().ifBlank { null }
                )
                _state.update {
                    it.copy(showCreateDialog = false, newName = "", newDescription = "")
                }
                load()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "创建通道失败", isLoading = false) }
            }
        }
    }

    fun deleteChannel(channel: ChannelDto) {
        viewModelScope.launch {
            try {
                channelRepository.deleteChannel(channel.id)
                load()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "删除通道失败") }
            }
        }
    }
}
