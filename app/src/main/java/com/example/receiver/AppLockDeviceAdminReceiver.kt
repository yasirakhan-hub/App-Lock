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
        // Prompt for master PIN verification to deactivate Device Admin
        val lockIntent = Intent(context, com.example.ui.lock.LockActivity::class.java).apply {
            putExtra(com.example.ui.lock.LockActivity.EXTRA_ACTION_DEACTIVATE_ADMIN, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        try {
            context.startActivity(lockIntent)
        } catch (_: Exception) {
        }

        return "Warning: Deactivating administrator privileges will remove uninstall protection and require master PIN verification."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
    }
}
