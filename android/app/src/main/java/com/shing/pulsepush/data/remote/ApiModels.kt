package com.shing.pulsepush.data.remote

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

/** Uniform backend envelope: { code, message, data }. */
data class ApiResponse<T>(
    @SerializedName("code") val code: Int = 0,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: T? = null
) {
    val isSuccess: Boolean get() = code == 200 || code == 0
}

// ---- Auth ----

data class RegisterRequest(
    @SerializedName("username") val username: String,
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)

data class LoginRequest(
    @SerializedName("email") val email: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("password") val password: String
)

data class AuthData(
    @SerializedName("access_token") val accessToken: String? = null,
    @SerializedName("token") val tokenAlt: String? = null,
    @SerializedName("token_type") val tokenType: String? = null,
    @SerializedName("user_id") val userId: Long? = null,
    @SerializedName("id") val id: Long? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("email") val email: String? = null,
    // Some backends nest the user object: data.user.{username,email}
    @SerializedName("user") val user: UserData? = null
) {
    val effectiveToken: String? get() = accessToken ?: tokenAlt
    val effectiveUsername: String? get() = username ?: user?.username
    val effectiveEmail: String? get() = email ?: user?.email
}

data class UserData(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("email") val email: String? = null
)

// ---- Devices ----

data class BindDeviceRequest(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("fcm_token") val fcmToken: String,
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("platform") val platform: String = "android",
    @SerializedName("app_version") val appVersion: String
)

data class UnbindDeviceRequest(
    @SerializedName("device_id") val deviceId: String
)

// ---- Channels ----

data class CreateChannelRequest(
    @SerializedName("channel_name") val channelName: String,
    @SerializedName("description") val description: String? = null
)

data class ChannelDto(
    val id: Long,
    val channelName: String,
    val channelToken: String,
    val description: String?,
    val createdAt: String?
)

// ---- Messages ----

data class MessageDto(
    val msgId: String,
    val channelToken: String,
    val title: String,
    val body: String,
    val url: String?,
    val level: String,
    val isRead: Boolean,
    val receivedAt: Long
)

/** Raw list endpoints are read as JsonElement and parsed tolerantly in the repositories. */
typealias JsonApiResponse = ApiResponse<JsonElement>
