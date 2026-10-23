package com.shing.pulsepush.service

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.shing.pulsepush.data.preferences.UserPreferences
import com.shing.pulsepush.data.repository.AuthRepository
import com.shing.pulsepush.data.repository.MessageRepository
import com.shing.pulsepush.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FCM entry point (PRD 4.2 / 4.3).
 *
 * - Background / killed + notification message: the system renders the
 *   banner itself via the top-level `notification` block; this callback is
 *   NOT invoked. The tap then lands in MainActivity with `data` as extras.
 * - Foreground, or data-only messages: this callback runs — persist to Room
 *   first, then post a local notification so the user still sees a banner.
 */
@AndroidEntryPoint
class MyFirebaseMessagingService : FirebaseMessagingService() {

    @Inject lateinit var messageRepository: MessageRepository
    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var preferences: UserPreferences

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        val title = message.notification?.title
            ?: data["title"] ?: "Webhook 提醒"
        val body = message.notification?.body
            ?: data["body"] ?: data["content"] ?: ""
        val msgId = data["msg_id"] ?: data["msgId"]
            ?: message.messageId
            ?: "fcm_${System.currentTimeMillis()}"
        val channelToken = data["channel_token"] ?: ""
        val url = data["url"]?.takeIf { it.isNotBlank() }
        val level = data["level"] ?: "info"

        serviceScope.launch {
            messageRepository.ingestPush(
                msgId = msgId,
                channelToken = channelToken,
                title = title,
                body = body,
                url = url,
                level = level
            )
        }
        NotificationHelper.showMessageNotification(
            context = this,
            msgId = msgId,
            channelToken = channelToken,
            title = title,
            body = body,
            url = url,
            level = level
        )
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        serviceScope.launch {
            preferences.saveFcmToken(token)
            // Silent re-bind only when a session exists (PRD 4.2).
            runCatching { authRepository.bindWithToken(token) }
        }
    }
}
