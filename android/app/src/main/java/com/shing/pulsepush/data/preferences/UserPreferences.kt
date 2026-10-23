package com.shing.pulsepush.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pulsepush_settings")

const val DEFAULT_SERVER_URL = "https://pulsepush.lxj.link/"

/**
 * Synchronous holders that OkHttp interceptors can read without blocking.
 * They are kept in sync with DataStore by [UserPreferences] (see its init),
 * so Retrofit always talks to the server the user last saved, and always
 * attaches the current JWT.
 */
@Singleton
class ServerUrlHolder @Inject constructor() {
    @Volatile
    var current: String = DEFAULT_SERVER_URL
}

@Singleton
class TokenHolder @Inject constructor() {
    @Volatile
    var current: String? = null
}

/**
 * DataStore-backed settings: session (JWT / user), server base URL,
 * device_id and last known FCM token.
 */
@Singleton
class UserPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val serverUrlHolder: ServerUrlHolder,
    private val tokenHolder: TokenHolder,
    private val appScope: CoroutineScope
) {
    private object Keys {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val USERNAME = stringPreferencesKey("username")
        val EMAIL = stringPreferencesKey("email")
        val SERVER_URL = stringPreferencesKey("server_url")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val FCM_TOKEN = stringPreferencesKey("fcm_token")
    }

    init {
        // Mirror persisted values into the synchronous holders used by the
        // OkHttp interceptors. Runs for the whole app lifetime.
        appScope.launch {
            serverUrl.collect { serverUrlHolder.current = it }
        }
        appScope.launch {
            accessToken.collect { tokenHolder.current = it }
        }
    }

    val accessToken: Flow<String?> = dataStore.data.map { it[Keys.ACCESS_TOKEN] }
    val username: Flow<String?> = dataStore.data.map { it[Keys.USERNAME] }
    val email: Flow<String?> = dataStore.data.map { it[Keys.EMAIL] }
    val serverUrl: Flow<String> = dataStore.data.map { prefs ->
        normalizeServerUrl(prefs[Keys.SERVER_URL] ?: DEFAULT_SERVER_URL)
    }
    val deviceId: Flow<String?> = dataStore.data.map { it[Keys.DEVICE_ID] }
    val fcmToken: Flow<String?> = dataStore.data.map { it[Keys.FCM_TOKEN] }

    suspend fun currentServerUrl(): String = serverUrl.first()
    suspend fun currentAccessToken(): String? = accessToken.first()
    suspend fun currentDeviceId(): String? = deviceId.first()
    suspend fun currentFcmToken(): String? = fcmToken.first()

    suspend fun saveSession(token: String, username: String?, email: String?) {
        tokenHolder.current = token
        dataStore.edit { prefs ->
            prefs[Keys.ACCESS_TOKEN] = token
            if (username != null) prefs[Keys.USERNAME] = username
            if (email != null) prefs[Keys.EMAIL] = email
        }
    }

    suspend fun saveServerUrl(url: String) {
        val normalized = normalizeServerUrl(url)
        serverUrlHolder.current = normalized
        dataStore.edit { it[Keys.SERVER_URL] = normalized }
    }

    suspend fun saveDeviceId(id: String) {
        dataStore.edit { it[Keys.DEVICE_ID] = id }
    }

    suspend fun saveFcmToken(token: String) {
        dataStore.edit { it[Keys.FCM_TOKEN] = token }
    }

    /** Clears only the session; server URL and device_id survive logout. */
    suspend fun clearSession() {
        tokenHolder.current = null
        dataStore.edit { prefs ->
            prefs.remove(Keys.ACCESS_TOKEN)
            prefs.remove(Keys.USERNAME)
            prefs.remove(Keys.EMAIL)
        }
    }

    companion object {
        fun normalizeServerUrl(raw: String): String {
            var url = raw.trim()
            if (url.isEmpty()) return DEFAULT_SERVER_URL
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://$url"
            }
            if (!url.endsWith("/")) url += "/"
            return url
        }
    }
}
