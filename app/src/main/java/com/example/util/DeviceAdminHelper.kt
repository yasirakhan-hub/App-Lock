package com.example.util

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.example.receiver.AppLockDeviceAdminReceiver

/**
 * Helper to query and request Device Administrator privileges.
 */
object DeviceAdminHelper {

    fun getComponentName(context: Context): ComponentName {
        return ComponentName(context, AppLockDeviceAdminReceiver::class.java)
    }

    /**
     * Checks if Secure AppLock is currently an active Device Administrator.
     */
    fun isDeviceAdminActive(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return false
        return dpm.isAdminActive(getComponentName(context))
    }

    /**
     * Creates an Intent to prompt the user to activate Device Administrator privileges.
     */
    fun createAddDeviceAdminIntent(context: Context): Intent {
        return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, getComponentName(context))
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Activate Device Administrator to prevent intruders from force-stopping or uninstalling Secure AppLock without authorization."
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Deactivates Device Administrator privileges.
     */
    fun deactivateDeviceAdmin(context: Context) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return
        try {
            dpm.removeActiveAdmin(getComponentName(context))
        } catch (_: Exception) {
        }
    }
}
