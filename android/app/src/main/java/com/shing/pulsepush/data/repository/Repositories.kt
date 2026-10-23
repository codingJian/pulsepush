package com.shing.pulsepush.data.repository

import com.google.firebase.messaging.FirebaseMessaging
import com.shing.pulsepush.data.local.LocalMessage
import com.shing.pulsepush.data.local.MessageDao
import com.shing.pulsepush.data.preferences.UserPreferences
import com.shing.pulsepush.data.remote.ApiService
import com.shing.pulsepush.data.remote.BindDeviceRequest
import com.shing.pulsepush.data.remote.ChannelDto
import com.shing.pulsepush.data.remote.CreateChannelRequest
import com.shing.pulsepush.data.remote.LoginRequest
import com.shing.pulsepush.data.remote.RegisterRequest
import com.shing.pulsepush.data.remote.UnbindDeviceRequest
import com.shing.pulsepush.data.remote.extractList
import com.shing.pulsepush.data.remote.toChannelDto
import com.shing.pulsepush.data.remote.toMessageDto
import com.shing.pulsepush.util.DeviceUtils
import com.shing.pulsepush.util.awaitTask
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

class ApiException(message: String) : Exception(message)

private fun checkCode(code: Int, message: String?, fallback: String) {
    if (code != 200 && code != 0) throw ApiException(message ?: fallback)
}

// ---------------- Auth + Device ----------------

@Singleton
class AuthRepository @Inject constructor(
    private val api: ApiService,
    private val preferences: UserPreferences,
    private val deviceUtils: DeviceUtils,
    private val messageDao: MessageDao
) {
    val accessToken: Flow<String?> = preferences.accessToken
    val username: Flow<String?> = preferences.username
    val email: Flow<String?> = preferences.email

    suspend fun register(username: String, email: String, password: String) {
        val resp = api.register(RegisterRequest(username, email, password))
        checkCode(resp.code, resp.message, "注册失败")
        // If the backend returns a token on register, treat it as a login.
        val token = resp.data?.effectiveToken
        if (!token.isNullOrBlank()) {
            preferences.saveSession(token, resp.data?.effectiveUsername ?: username,
                resp.data?.effectiveEmail ?: email)
            bindCurrentDevice()
        } else {
            // Otherwise fall through to an explicit login.
            login(email, password)
        }
    }

    suspend fun login(email: String, password: String) {
        val resp = api.login(LoginRequest(email = email, password = password))
        checkCode(resp.code, resp.message, "登录失败")
        val token = resp.data?.effectiveToken
            ?: throw ApiException(resp.message ?: "登录失败：服务器未返回 access_token")
        preferences.saveSession(
            token,
            resp.data?.effectiveUsername,
            resp.data?.effectiveEmail ?: email
        )
        bindCurrentDevice()
    }

    /** Fetches the FCM token and (re)binds this device to the signed-in user. */
    suspend fun bindCurrentDevice() {
        if (preferences.currentAccessToken().isNullOrBlank()) return
        val fcmToken = fetchFcmToken() ?: return
        bindWithToken(fcmToken)
    }

    suspend fun bindWithToken(fcmToken: String) {
        if (preferences.currentAccessToken().isNullOrBlank()) return
        val deviceId = deviceUtils.getOrCreateDeviceId()
        preferences.saveFcmToken(fcmToken)
        val resp = api.bindDevice(
            BindDeviceRequest(
                deviceId = deviceId,
                fcmToken = fcmToken,
                deviceName = deviceUtils.deviceName(),
                platform = "android",
                appVersion = deviceUtils.appVersion()
            )
        )
        checkCode(resp.code, resp.message, "设备绑定失败")
    }

    /** Logout flow (PRD 3.2/4.2): unbind first, then clear local session. */
    suspend fun logout() {
        runCatching {
            if (!preferences.currentAccessToken().isNullOrBlank()) {
                val deviceId = deviceUtils.getOrCreateDeviceId()
                api.unbindDevice(UnbindDeviceRequest(deviceId = deviceId))
            }
        }
        preferences.clearSession()
        messageDao.clearAll()
    }

    private suspend fun fetchFcmToken(): String? = runCatching {
        FirebaseMessaging.getInstance().token.awaitTask()
    }.getOrNull()
}

// ---------------- Messages ----------------

@Singleton
class MessageRepository @Inject constructor(
    private val api: ApiService,
    private val dao: MessageDao
) {
    fun observeMessages(): Flow<List<LocalMessage>> = dao.observeAll()

    fun observeMessage(msgId: String): Flow<LocalMessage?> = dao.observeByMsgId(msgId)

    fun observeUnreadCount(): Flow<Int> = dao.observeUnreadCount()

    /** Pull-to-refresh: fetch the newest page from the server into Room. */
    suspend fun syncMessages(limit: Int = 50, offset: Int = 0) {
        val resp = api.getMessages(limit = limit, offset = offset)
        checkCode(resp.code, resp.message, "同步消息失败")
        val items = resp.data.extractList("items", "messages", "list")
        val locals = items.mapNotNull { element ->
            val dto = element.toMessageDto() ?: return@mapNotNull null
            LocalMessage(
                msgId = dto.msgId,
                channelToken = dto.channelToken,
                title = dto.title,
                body = dto.body,
                url = dto.url,
                level = dto.level,
                isRead = dto.isRead,
                receivedAt = dto.receivedAt
            )
        }
        if (locals.isNotEmpty()) dao.insertAllIgnore(locals)
    }

    suspend fun markRead(msgId: String) {
        dao.markRead(msgId)
        runCatching { api.markMessageRead(msgId) } // local state is authoritative offline
    }

    /** Inserts a push payload (from FCM or a notification-tap intent). Deduped by msg_id. */
    suspend fun ingestPush(
        msgId: String,
        channelToken: String,
        title: String,
        body: String,
        url: String?,
        level: String
    ) {
        if (msgId.isBlank()) return
        // If we already have the full copy (e.g. from history sync), keep it.
        val existing = dao.getByMsgId(msgId)
        if (existing != null && existing.body.isNotBlank()) return
        dao.insertIgnore(
            LocalMessage(
                msgId = msgId,
                channelToken = channelToken,
                title = title.ifBlank { "Webhook 提醒" },
                body = body,
                url = url?.takeIf { it.isNotBlank() },
                level = level.ifBlank { "info" },
                isRead = false,
                receivedAt = System.currentTimeMillis()
            )
        )
    }
}

// ---------------- Channels ----------------

@Singleton
class ChannelRepository @Inject constructor(
    private val api: ApiService
) {
    suspend fun fetchChannels(): List<ChannelDto> {
        val resp = api.getChannels()
        checkCode(resp.code, resp.message, "获取通道列表失败")
        val items = resp.data.extractList("items", "channels", "list")
        return items.mapNotNull { it.toChannelDto() }
    }

    suspend fun createChannel(name: String, description: String?): ChannelDto? {
        val resp = api.createChannel(
            CreateChannelRequest(channelName = name, description = description)
        )
        checkCode(resp.code, resp.message, "创建通道失败")
        return resp.data.toChannelDto()
    }

    suspend fun deleteChannel(id: Long) {
        val resp = api.deleteChannel(id)
        checkCode(resp.code, resp.message, "删除通道失败")
    }
}
