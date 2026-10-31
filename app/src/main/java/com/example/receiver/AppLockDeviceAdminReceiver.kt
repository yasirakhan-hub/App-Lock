package com.example.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.example.data.repository.AppLockManager
import com.example.util.DeviceAdminHelper

/**
 * DeviceAdminReceiver that provides Anti-Uninstall and Anti-Tamper protection.
 * When activated as a Device Administrator, standard users and intruders cannot
 * easily uninstall or terminate AppLock without deactivating admin rights.
 *
 * If deactivation is attempted through Android Settings, onDisableRequested triggers
 * authentication to block unauthorized deactivation.
 */
class AppLockDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        DeviceAdminHelper.notifyAdminStatusChanged(context)
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        try {
            val appLockManager = AppLockManager.getInstance(context)
            // Immediately launch lock screen over the deactivation screen
            appLockManager.launchLockScreen(context.packageName)
        } catch (_: Exception) {
        }
        return "SECURITY WARNING: Deactivating administrator privileges will remove uninstall and force-stop protection. Master PIN is required to authorize this change."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        DeviceAdminHelper.notifyAdminStatusChanged(context)
    }
}
