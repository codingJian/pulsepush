package com.shing.pulsepush.ui.channels

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.shing.pulsepush.data.remote.ChannelDto

@Composable
fun ChannelsScreen(viewModel: ChannelsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.setShowCreateDialog(true) }) {
                Icon(Icons.Filled.Add, contentDescription = "新建通道")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading && state.channels.isEmpty() -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                state.channels.isEmpty() -> {
                    Text(
                        text = state.error ?: "暂无通道，点击右下角新建",
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (state.error != null) {
                            item {
                                Text(
                                    text = state.error!!,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        items(state.channels, key = { it.id }) { channel ->
                            ChannelCard(
                                channel = channel,
                                webhookUrl = viewModel.webhookUrl(channel),
                                curlExample = viewModel.curlExample(channel),
                                onDelete = { viewModel.deleteChannel(channel) },
                                context = context
                            )
                        }
                    }
                }
            }
        }
    }

    if (state.showCreateDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.setShowCreateDialog(false) },
            title = { Text("新建通道") },
            text = {
                Column {
                    OutlinedTextField(
                        value = state.newName,
                        onValueChange = viewModel::onNewNameChange,
                        label = { Text("通道名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.newDescription,
                        onValueChange = viewModel::onNewDescriptionChange,
                        label = { Text("描述（可选）") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::createChannel,
                    enabled = state.newName.isNotBlank() && !state.isLoading
                ) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setShowCreateDialog(false) }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun ChannelCard(
    channel: ChannelDto,
    webhookUrl: String,
    curlExample: String,
    onDelete: () -> Unit,
    context: Context
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = channel.channelName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "删除通道",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            if (!channel.description.isNullOrBlank()) {
                Text(
                    text = channel.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = webhookUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { copy(context, webhookUrl, "Webhook URL 已复制") }) {
                    Text("复制 Webhook URL")
                }
                OutlinedButton(onClick = { copy(context, curlExample, "curl 示例已复制") }) {
                    Text("复制 curl 示例")
                }
            }
        }
    }
}

private fun copy(context: Context, text: String, toast: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("PulsePush", text))
    Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
}
