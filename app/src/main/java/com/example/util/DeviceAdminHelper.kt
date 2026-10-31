package com.example.util

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.example.receiver.AppLockDeviceAdminReceiver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Helper to query and request Device Administrator privileges and manage anti-uninstall policy.
 */
object DeviceAdminHelper {

    private val _adminStatusFlow = MutableStateFlow<Boolean?>(null)
    val adminStatusFlow: StateFlow<Boolean?> = _adminStatusFlow.asStateFlow()

    @Volatile
    private var deactivationAuthorizedTimestamp: Long = 0L

    fun setDeactivationAuthorized(authorized: Boolean) {
        deactivationAuthorizedTimestamp = if (authorized) System.currentTimeMillis() else 0L
    }

    fun isDeactivationAuthorized(): Boolean {
        val now = System.currentTimeMillis()
        val elapsed = now - deactivationAuthorizedTimestamp
        return deactivationAuthorizedTimestamp > 0L && elapsed in 0L..15000L
    }

    fun consumeDeactivationAuthorized(): Boolean {
        val authorized = isDeactivationAuthorized()
        deactivationAuthorizedTimestamp = 0L
        return authorized
    }

    /**
     * Notifies all observers that the Device Administrator activation status has changed.
     */
    fun notifyAdminStatusChanged(context: Context) {
        _adminStatusFlow.value = isDeviceAdminActive(context)
    }

    fun getComponentName(context: Context): ComponentName {
        return ComponentName(context, AppLockDeviceAdminReceiver::class.java)
    }

    /**
     * Checks if Secure AppLock is currently an active Device Administrator by querying DevicePolicyManager.
     * Uses DevicePolicyManager.isAdminActive() as the authoritative source.
     */
    fun isDeviceAdminActive(context: Context): Boolean {
        val appContext = context.applicationContext ?: context
        return try {
            val dpm = appContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return false
            val adminComponent = getComponentName(appContext)
            if (dpm.isAdminActive(adminComponent)) {
                return true
            }
            val activeAdmins = dpm.activeAdmins
            if (!activeAdmins.isNullOrEmpty()) {
                activeAdmins.any { admin ->
                    val isSamePkg = admin.packageName == appContext.packageName || admin.packageName == context.packageName
                    val isSameClass = admin.className == adminComponent.className ||
                            admin.className == AppLockDeviceAdminReceiver::class.java.name ||
                            admin.shortClassName.endsWith("AppLockDeviceAdminReceiver")
                    (isSamePkg && isSameClass) || (isSamePkg && dpm.isAdminActive(admin))
                }
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
     * Checks if Secure AppLock is configured as a Device Owner or Profile Owner.
     */
    fun isDeviceOrProfileOwner(context: Context): Boolean {
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return false
            val pkg = context.packageName
            dpm.isDeviceOwnerApp(pkg) || dpm.isProfileOwnerApp(pkg)
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
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
            if (context !is android.app.Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    /**
     * Opens the Device Administrator activation screen with sequential official Android fallbacks.
     * Handles OEM-specific restrictions (MIUI, ColorOS, EMUI, Knox) where ACTION_ADD_DEVICE_ADMIN
     * may not resolve directly or requires opening Device Admin Settings / Security Settings.
     */
    fun openDeviceAdminActivation(context: Context): Boolean {
        // Attempt 1: Direct ACTION_ADD_DEVICE_ADMIN prompt
        try {
            val primaryIntent = createAddDeviceAdminIntent(context)
            context.startActivity(primaryIntent)
            return true
        } catch (_: Exception) {
        }

        // Attempt 2: System Device Admin Settings list (standard Android action)
        try {
            val adminSettingsIntent = Intent("android.settings.DEVICE_ADMIN_SETTINGS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(adminSettingsIntent)
            return true
        } catch (_: Exception) {
        }

        // Attempt 3: Android Security Settings
        try {
            val securityIntent = Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(securityIntent)
            return true
        } catch (_: Exception) {
        }

        // Attempt 4: General Settings fallback
        return try {
            val generalSettingsIntent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(generalSettingsIntent)
            true
        } catch (_: Exception) {
            false
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
     * Returns true if successfully removed or already inactive.
     */
    fun deactivateDeviceAdmin(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return false
        return try {
            setDeactivationAuthorized(true)
            dpm.removeActiveAdmin(getComponentName(context))
            notifyAdminStatusChanged(context)
            !isDeviceAdminActive(context)
        } catch (_: SecurityException) {
            setDeactivationAuthorized(false)
            false
        } catch (_: Exception) {
            setDeactivationAuthorized(false)
            false
        }
    }
}
