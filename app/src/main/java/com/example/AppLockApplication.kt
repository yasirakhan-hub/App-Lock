package com.example

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.example.data.repository.AppLockManager
import com.example.receiver.ScreenReceiver

/**
 * Application entry point for Secure AppLock.
 * Sets up global managers and registers screen off broadcast receiver.
 */
class AppLockApplication : Application() {

    private val screenReceiver = ScreenReceiver()

    override fun onCreate() {
        super.onCreate()
        AppLockManager.getInstance(this)

        // Register screen off listener dynamically (required on Android 8+)
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }
    }
}
