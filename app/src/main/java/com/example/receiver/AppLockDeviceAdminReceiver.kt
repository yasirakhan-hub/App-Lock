package com.example.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.util.DeviceAdminHelper

/**
 * DeviceAdminReceiver that provides Anti-Uninstall deterrence.
 * When activated as a Device Administrator, standard users cannot directly uninstall
 * AppLock from the home screen or system app details without first deactivating admin rights.
 */
class AppLockDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        DeviceAdminHelper.setUninstallBlockedIfSupported(context, true)
        Toast.makeText(context, "Device Administrator activated: Anti-uninstall deterrence is active.", Toast.LENGTH_SHORT).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        // Prompt for master PIN verification when deactivating Device Admin
        val lockIntent = Intent(context, com.example.ui.lock.LockActivity::class.java).apply {
            putExtra(com.example.ui.lock.LockActivity.EXTRA_ACTION_DEACTIVATE_ADMIN, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        try {
            context.startActivity(lockIntent)
        } catch (_: Exception) {
        }

        return "Deactivating Device Administrator reduces protection. Standard Android requires deactivating administrator rights before uninstalling AppLock."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        DeviceAdminHelper.setUninstallBlockedIfSupported(context, false)
        Toast.makeText(context, "Device Administrator deactivated.", Toast.LENGTH_SHORT).show()
    }
}
