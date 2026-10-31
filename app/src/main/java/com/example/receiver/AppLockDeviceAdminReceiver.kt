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
        DeviceAdminHelper.setUninstallBlockedIfSupported(context, true)
        DeviceAdminHelper.notifyAdminStatusChanged(context)
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        try {
            // Only launch lock screen if deactivation was not already authenticated
            if (!DeviceAdminHelper.isDeactivationAuthorized()) {
                val lockIntent = Intent(context, com.example.ui.lock.LockActivity::class.java).apply {
                    putExtra(com.example.ui.lock.LockActivity.EXTRA_TARGET_PACKAGE, context.packageName)
                    putExtra(com.example.ui.lock.LockActivity.EXTRA_ACTION_DEACTIVATE_ADMIN, true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                context.startActivity(lockIntent)
            }
        } catch (_: Exception) {
        }
        return "SECURITY WARNING: Deactivating administrator privileges will remove uninstall and force-stop protection. Master PIN is required to authorize this change."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        DeviceAdminHelper.setDeactivationAuthorized(false)
        DeviceAdminHelper.notifyAdminStatusChanged(context)
    }
}
