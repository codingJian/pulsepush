package com.shing.pulsepush.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.shing.pulsepush.BuildConfig
import com.shing.pulsepush.data.preferences.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceUtils @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: UserPreferences
) {
    /**
     * Stable per-install device id: Settings.Secure.ANDROID_ID when usable,
     * otherwise a UUID generated once and persisted in DataStore.
     */
    suspend fun getOrCreateDeviceId(): String {
        preferences.currentDeviceId()?.let { return it }
        val androidId = readAndroidId()
        val id = if (!androidId.isNullOrBlank() && androidId != BROKEN_ANDROID_ID) {
            androidId
        } else {
            UUID.randomUUID().toString()
        }
        preferences.saveDeviceId(id)
        return id
    }

    @SuppressLint("HardwareIds")
    private fun readAndroidId(): String? = runCatching {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }.getOrNull()

    fun deviceName(): String {
        val manufacturer = Build.MANUFACTURER?.trim().orEmpty()
        val model = Build.MODEL?.trim().orEmpty()
        return listOf(manufacturer, model).filter { it.isNotEmpty() }
            .distinct()
            .joinToString(" ")
            .ifBlank { "Android Device" }
    }

    fun appVersion(): String = BuildConfig.VERSION_NAME

    private companion object {
        // Well-known broken ANDROID_ID shipped on some old devices.
        const val BROKEN_ANDROID_ID = "9774d56d682e549c"
    }
}
