package com.shing.pulsepush

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.messaging.FirebaseMessaging
import com.shing.pulsepush.data.preferences.UserPreferences
import com.shing.pulsepush.data.repository.AuthRepository
import com.shing.pulsepush.data.repository.MessageRepository
import com.shing.pulsepush.ui.navigation.PulsePushNavHost
import com.shing.pulsepush.ui.theme.PulsePushTheme
import dagger.hilt.android.AndroidEntryPoint
import com.shing.pulsepush.util.awaitTask
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var preferences: UserPreferences
    @Inject lateinit var messageRepository: MessageRepository
    @Inject lateinit var authRepository: AuthRepository

    /** msg_id delivered by a notification tap, consumed by the NavHost. */
    private var pendingMsgId by mutableStateOf<String?>(null)

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* best effort */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        handlePushIntent(intent)
        refreshDeviceBinding()

        setContent {
            PulsePushTheme {
                val isLoggedIn by preferences.accessToken
                    .map { token -> token?.let { it.isNotBlank() } }
                    .collectAsState(initial = null)

                if (isLoggedIn == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    PulsePushNavHost(
                        isLoggedIn = isLoggedIn,
                        pendingMsgId = pendingMsgId,
                        onPendingMsgHandled = { pendingMsgId = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePushIntent(intent)
    }

    /**
     * Notification tap path (PRD 4.3 scenario 1): FCM delivers the `data`
     * payload as intent extras. Persist it to Room first (the system-rendered
     * notification never went through onMessageReceived), then navigate to
     * the detail page.
     */
    private fun handlePushIntent(intent: Intent?) {
        val extras = intent?.extras ?: return
        val msgId = extras.getString("msg_id")
            ?: extras.getString("msgId")
            ?: return
        val title = extras.getString("title") ?: "Webhook 提醒"
        val body = extras.getString("body") ?: extras.getString("content") ?: ""
        val channelToken = extras.getString("channel_token") ?: ""
        val url = extras.getString("url")?.takeIf { it.isNotBlank() }
        val level = extras.getString("level") ?: "info"

        lifecycleScope.launch {
            messageRepository.ingestPush(msgId, channelToken, title, body, url, level)
            pendingMsgId = msgId
        }
        // Avoid re-processing the same intent on configuration changes.
        intent.removeExtra("msg_id")
        intent.removeExtra("msgId")
    }

    /** Keeps the FCM token / device binding fresh on every cold start. */
    private fun refreshDeviceBinding() {
        lifecycleScope.launch {
            runCatching {
                val token = FirebaseMessaging.getInstance().token.awaitTask()
                preferences.saveFcmToken(token)
                authRepository.bindWithToken(token)
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
