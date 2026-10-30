package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.example.data.repository.AppLockManager

/**
 * Native Android AccessibilityService that detects foreground window transitions.
 * Intercepts protected application launches, captures the specific target intent
 * (package and class name), and triggers authentication.
 *
 * Privacy Note:
 * Configured with canRetrieveWindowContent="false".
 * It CANNOT read screen contents, user inputs, or keystrokes.
 */
class AppLockAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var isServiceRunning: Boolean = false
            private set
    }

    private lateinit var appLockManager: AppLockManager

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        appLockManager = AppLockManager.getInstance(applicationContext)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkgName = event.packageName?.toString() ?: return
            val className = event.className?.toString()

            // Skip transient window popups like keyboards, toasts, or dialogs
            if (isTransientUiElement(className)) {
                return
            }

            if (!::appLockManager.isInitialized) {
                appLockManager = AppLockManager.getInstance(applicationContext)
            }

            // 1. Recent Apps Overview entered:
            // Notify AppLockManager so Recents gesture / overview navigation does NOT trigger lock
            if (appLockManager.isRecentsOrOverview(pkgName, className)) {
                appLockManager.onRecentsOverviewEntered()
                return
            }

            // 2. Anti-Force Stop Protection:
            // Intercept attempts to open Settings -> App Info for Smart AppLock
            // Allows authenticated access, avoids permanent lockout, and never interferes with essential system settings
            if (isAppInfoForSelf(pkgName, className, event)) {
                if (!appLockManager.isSelfAppInfoUnlocked()) {
                    appLockManager.launchLockScreen(packageName, className)
                }
                return
            }

            // 3. Anti-Uninstall Protection:
            // Intercept Package Installer when attempting to uninstall Smart AppLock
            if (appLockManager.isPackageInstaller(pkgName)) {
                val appName = getString(com.example.R.string.app_name)
                val isUninstallingSelf = event.text?.any { text ->
                    text.contains(appName, ignoreCase = true) || text.contains(packageName, ignoreCase = true)
                } ?: false

                if (isUninstallingSelf) {
                    if (!appLockManager.isSelfAppInfoUnlocked()) {
                        appLockManager.launchLockScreen(pkgName, className)
                    }
                    return
                }
            }

            appLockManager.onForegroundPackageChanged(pkgName, className)
        }
    }

    private fun isAppInfoForSelf(pkgName: String, className: String?, event: AccessibilityEvent): Boolean {
        if (!isSettingsOrSecurityPackage(pkgName)) return false
        if (className == null) return false

        // Never lock essential system configuration screens (Accessibility, Battery, DeviceAdmin, WiFi, etc.)
        if (isEssentialSystemSettings(className)) return false

        val isAppDetailsScreen = isAppDetailsClass(className)
        if (!isAppDetailsScreen) return false

        val appName = getString(com.example.R.string.app_name)
        val targetPackage = packageName

        val matchesText = event.text?.any { text ->
            text.contains(appName, ignoreCase = true) ||
            text.contains(targetPackage, ignoreCase = true) ||
            text.contains("secjkv", ignoreCase = true)
        } ?: false

        val contentDesc = event.contentDescription?.toString()
        val matchesDesc = contentDesc != null && (
            contentDesc.contains(appName, ignoreCase = true) ||
            contentDesc.contains(targetPackage, ignoreCase = true) ||
            contentDesc.contains("secjkv", ignoreCase = true)
        )

        return matchesText || matchesDesc
    }

    private fun isSettingsOrSecurityPackage(pkgName: String): Boolean {
        return pkgName == "com.android.settings" ||
                pkgName == "com.miui.securitycenter" ||
                pkgName == "com.coloros.safecenter" ||
                pkgName == "com.oplus.safecenter" ||
                pkgName == "com.vivo.permissionmanager"
    }

    private fun isAppDetailsClass(className: String): Boolean {
        val lower = className.lowercase()
        return lower.contains("installedappdetails") ||
                lower.contains("appdetails") ||
                lower.contains("manageapplications") ||
                lower.contains("appmanagementactivity") ||
                lower.contains("spaactivity") ||
                lower.contains("applicationsstate") ||
                lower.contains("subsettings")
    }

    private fun isEssentialSystemSettings(className: String): Boolean {
        val lower = className.lowercase()
        return lower.contains("accessibility") ||
                lower.contains("deviceadmin") ||
                lower.contains("battery") ||
                lower.contains("wireless") ||
                lower.contains("wifi") ||
                lower.contains("network") ||
                lower.contains("bluetooth") ||
                lower.contains("display") ||
                lower.contains("sound") ||
                lower.contains("storage") ||
                lower.contains("volume") ||
                lower.contains("language") ||
                lower.contains("datetime") ||
                lower.contains("development") ||
                lower.contains("fingerprint") ||
                lower.contains("biometric") ||
                lower.contains("securitysettings") ||
                lower.contains("lockscreen") ||
                lower.contains("notification")
    }

    private fun isTransientUiElement(className: String?): Boolean {
        if (className == null) return false
        val lower = className.lowercase()
        return lower.contains("inputmethod") ||
                lower.contains("toast") ||
                lower.contains("popupwindow") ||
                lower.contains("volume") ||
                className == "android.widget.FrameLayout"
    }

    override fun onInterrupt() {
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isServiceRunning = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        isServiceRunning = false
        super.onDestroy()
    }
}
