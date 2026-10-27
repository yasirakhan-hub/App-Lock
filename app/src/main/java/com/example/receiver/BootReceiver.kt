package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.repository.AppLockManager

/**
 * Ensures AppLock resets session states after device reboot.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context != null && intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            val manager = AppLockManager.getInstance(context)
            manager.lockAll()
        }
    }
}
