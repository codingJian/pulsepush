package com.shing.pulsepush

import android.app.Application
import com.shing.pulsepush.util.NotificationHelper
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PulsePushApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // PRD 4.3: register the graded notification channels up-front.
        NotificationHelper.createChannels(this)
    }
}
