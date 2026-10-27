package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.repository.AppLockManager

/**
 * Listens for screen power-off events to reset temporarily unlocked app sessions.
 */
class ScreenReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context != null && intent?.action == Intent.ACTION_SCREEN_OFF) {
            AppLockManager.getInstance(context).onScreenOff()
        }
    }
}
