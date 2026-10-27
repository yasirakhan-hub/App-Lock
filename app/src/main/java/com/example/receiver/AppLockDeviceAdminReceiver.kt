package com.example.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * DeviceAdminReceiver that provides Anti-Uninstall and Anti-Tamper protection.
 * When activated as a Device Administrator, standard users and intruders cannot
 * easily uninstall or terminate AppLock without deactivating admin rights.
 */
class AppLockDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Warning: Deactivating administrator privileges will remove uninstall protection and allow unauthorized access to your locked applications."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
    }
}
