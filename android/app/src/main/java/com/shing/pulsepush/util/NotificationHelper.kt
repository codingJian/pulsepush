package com.shing.pulsepush.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.shing.pulsepush.MainActivity
import com.shing.pulsepush.R

object NotificationChannels {
    const val CRITICAL_CHANNEL_ID = "critical_channel"
    const val DEFAULT_CHANNEL_ID = "default_channel"
    const val SILENT_CHANNEL_ID = "silent_channel"

    fun channelIdForLevel(level: String?): String = when (level?.lowercase()) {
        "critical" -> CRITICAL_CHANNEL_ID
        "silent" -> SILENT_CHANNEL_ID
        else -> DEFAULT_CHANNEL_ID // info / warning both use the default channel
    }
}

object NotificationHelper {

    /** Called from PulsePushApp.onCreate() (PRD 4.3 channel grading). */
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val critical = NotificationChannel(
            NotificationChannels.CRITICAL_CHANNEL_ID,
            "重要告警",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "critical 级别消息：强提醒"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 150, 250)
            setSound(alarmSound, audioAttrs)
            enableLights(true)
        }

        val default = NotificationChannel(
            NotificationChannels.DEFAULT_CHANNEL_ID,
            "默认通知",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "info / warning 级别消息"
            enableVibration(true)
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), audioAttrs)
        }

        val silent = NotificationChannel(
            NotificationChannels.SILENT_CHANNEL_ID,
            "静默通知",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "仅在通知栏显示，不弹窗、不发声"
            enableVibration(false)
            setSound(null, null)
        }

        manager.createNotificationChannels(listOf(critical, default, silent))
    }

    /**
     * Shows a local notification (used when a push arrives while the app is
     * in the foreground, where the system does not render FCM notification
     * messages itself). Tapping it opens MainActivity with the same extras
     * a system-rendered FCM notification would deliver.
     */
    fun showMessageNotification(
        context: Context,
        msgId: String,
        channelToken: String,
        title: String,
        body: String,
        url: String?,
        level: String
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            action = "OPEN_MESSAGE_DETAIL"
            putExtra("msg_id", msgId)
            putExtra("channel_token", channelToken)
            putExtra("title", title)
            putExtra("body", body)
            putExtra("url", url ?: "")
            putExtra("level", level)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            msgId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = NotificationChannels.channelIdForLevel(level)
        val priority = if (channelId == NotificationChannels.CRITICAL_CHANNEL_ID) {
            NotificationCompat.PRIORITY_MAX
        } else {
            NotificationCompat.PRIORITY_DEFAULT
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(priority)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            try {
                NotificationManagerCompat.from(context)
                    .notify(msgId.hashCode(), notification)
            } catch (_: SecurityException) {
                // POST_NOTIFICATIONS not granted on Android 13+; message is
                // already in Room, so failing to post the banner is non-fatal.
            }
        }
    }
}
