package com.example.util

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.example.receiver.AppLockDeviceAdminReceiver

/**
 * Helper to query and request Device Administrator privileges and manage anti-uninstall policy.
 */
object DeviceAdminHelper {

    fun getComponentName(context: Context): ComponentName {
        return ComponentName(context, AppLockDeviceAdminReceiver::class.java)
    }

    /**
     * Checks if Secure AppLock is currently an active Device Administrator by querying DevicePolicyManager.
     */
    fun isDeviceAdminActive(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return false
        return dpm.isAdminActive(getComponentName(context))
    }

    /**
     * Checks if Secure AppLock is configured as a Device Owner or Profile Owner.
     */
    fun isDeviceOrProfileOwner(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return false
        val pkg = context.packageName
        return dpm.isDeviceOwnerApp(pkg) || dpm.isProfileOwnerApp(pkg)
    }

    /**
     * Creates an Intent to prompt the user to activate Device Administrator privileges.
     */
    fun createAddDeviceAdminIntent(context: Context): Intent {
        return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, getComponentName(context))
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Activate Device Administrator to enable anti-uninstall protection. Standard users must deactivate Device Admin before uninstalling Secure AppLock."
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Applies setUninstallBlocked if the app has Device Owner / Profile Owner privileges.
     */
    fun setUninstallBlockedIfSupported(context: Context, blocked: Boolean): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return false
        val adminComponent = getComponentName(context)
        return try {
            if (isDeviceOrProfileOwner(context)) {
                dpm.setUninstallBlocked(adminComponent, context.packageName, blocked)
                true
            } else {
                false
            }
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
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
