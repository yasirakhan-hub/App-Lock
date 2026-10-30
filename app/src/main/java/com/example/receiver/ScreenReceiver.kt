package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.repository.AppLockManager

/**
 * Listens for screen power-off and battery saver transitions to reset temporary sessions
 * and refresh system states cleanly.
 */
class ScreenReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> {
                AppLockManager.getInstance(context).onScreenOff()
            }
            android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                // When device enters/exits Battery Saver mode (often triggered at ~5% battery),
                // refresh framework caches so monitoring continues seamlessly
                AppLockManager.getInstance(context).invalidateLauncherCache()
            }
        }
    }
}
