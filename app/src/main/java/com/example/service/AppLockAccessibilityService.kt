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

    @Volatile
    private var lastAppInfoContentCheckTime: Long = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            return
        }

        val pkgName = event.packageName?.toString() ?: return
        val className = event.className?.toString()

        isServiceRunning = true
        if (!::appLockManager.isInitialized) {
            appLockManager = AppLockManager.getInstance(applicationContext)
        }

        // 1. Anti-Force Stop & Anti-Tamper Protection:
        // Intercept attempts to open Settings -> App Info for Smart AppLock.
        // Both TYPE_WINDOW_STATE_CHANGED and TYPE_WINDOW_CONTENT_CHANGED are checked
        // to handle asynchronous view population across OEM ROMs and modern Jetpack Compose Settings (SpaActivity).
        if (isSettingsOrSecurityPackage(pkgName) && !appLockManager.isSelfAppInfoUnlocked() && appLockManager.isProtectionActive()) {
            val now = System.currentTimeMillis()
            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || (now - lastAppInfoContentCheckTime > 250L)) {
                if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                    lastAppInfoContentCheckTime = now
                }
                if (isAppInfoForSelf(pkgName, className, event)) {
                    appLockManager.launchLockScreen(packageName, className, this)
                    return
                }
            }
        }

        // For remaining events, only process TYPE_WINDOW_STATE_CHANGED
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }

        // Skip transient window popups like keyboards, toasts, or dialogs
        if (isTransientUiElement(className)) {
            return
        }

        // 2. Recent Apps Overview entered:
        // Notify AppLockManager so Recents gesture / overview navigation does NOT trigger lock
        if (appLockManager.isRecentsOrOverview(pkgName, className)) {
            appLockManager.onRecentsOverviewEntered()
            return
        }

        // 3. Package Installer Handling (Installation / Update vs Uninstall Protection):
        // Intercept Package Installer when attempting to uninstall Smart AppLock,
        // while allowing legitimate installation and update flows once authenticated without looping.
        if (appLockManager.isPackageInstaller(pkgName)) {
            val appName = getString(com.example.R.string.app_name)
            val isUninstallingSelf = event.text?.any { text ->
                text.contains(appName, ignoreCase = true) || text.contains(packageName, ignoreCase = true)
            } ?: false

            if (isUninstallingSelf) {
                if (!appLockManager.isSelfAppInfoUnlocked()) {
                    appLockManager.launchLockScreen(pkgName, className, this)
                }
                return
            }

            // If this is an installation/update flow and session is already unlocked, bypass repeated prompts
            if (appLockManager.isInstallerSessionUnlocked()) {
                return
            }
        }

        appLockManager.onForegroundPackageChanged(pkgName, className, this)
    }

    internal fun isAppInfoForSelf(pkgName: String, className: String?, event: AccessibilityEvent): Boolean {
        if (!isSettingsOrSecurityPackage(pkgName)) return false

        // Never lock essential system configuration screens (Accessibility, DeviceAdmin, WiFi, etc.)
        if (isEssentialSystemSettings(className)) return false

        val appName = getString(com.example.R.string.app_name)
        val targetPackage = packageName

        // 1. Direct text/contentDescription check on event
        val matchesText = event.text?.any { text ->
            val str = text.toString()
            str.contains(appName, ignoreCase = true) ||
            str.contains(targetPackage, ignoreCase = true) ||
            str.contains("secjkv", ignoreCase = true)
        } ?: false

        val contentDesc = event.contentDescription?.toString()
        val matchesDesc = contentDesc != null && (
            contentDesc.contains(appName, ignoreCase = true) ||
            contentDesc.contains(targetPackage, ignoreCase = true) ||
            contentDesc.contains("secjkv", ignoreCase = true)
        )

        val hasDirectIdentifier = matchesText || matchesDesc

        // 2. Deep inspection via rootInActiveWindow or event.source
        var nodeHasIdentifier = false
        var hasAppDetailsIndicators = false

        val rootNode = try {
            rootInActiveWindow ?: event.source
        } catch (_: Exception) {
            null
        }

        if (rootNode != null) {
            try {
                val appNameNodes = rootNode.findAccessibilityNodeInfosByText(appName)
                val pkgNodes = rootNode.findAccessibilityNodeInfosByText("secjkv")
                nodeHasIdentifier = !appNameNodes.isNullOrEmpty() || !pkgNodes.isNullOrEmpty()

                // Distinguish App Info detail page from general app list (ManageApplications)
                val forceStopNodes = rootNode.findAccessibilityNodeInfosByText("Force stop")
                val uninstallNodes = rootNode.findAccessibilityNodeInfosByText("Uninstall")
                val disableNodes = rootNode.findAccessibilityNodeInfosByText("Disable")
                val storageNodes = rootNode.findAccessibilityNodeInfosByText("Storage")
                val clearDataNodes = rootNode.findAccessibilityNodeInfosByText("Clear")

                hasAppDetailsIndicators = !forceStopNodes.isNullOrEmpty() ||
                        !uninstallNodes.isNullOrEmpty() ||
                        !disableNodes.isNullOrEmpty() ||
                        !storageNodes.isNullOrEmpty() ||
                        !clearDataNodes.isNullOrEmpty()
            } catch (_: Exception) {
            }
        }

        val hasAppIdentifier = hasDirectIdentifier || nodeHasIdentifier
        if (!hasAppIdentifier) return false

        val isAppDetailsActivity = isAppDetailsClass(className)

        // Intercept if:
        // a) Class name is a recognized App Details screen, OR
        // b) Screen contains App Info action buttons (Force stop, Uninstall, Storage, etc.), OR
        // c) Direct event text explicitly identified our app name/package on a Settings screen
        return isAppDetailsActivity || hasAppDetailsIndicators || hasDirectIdentifier
    }

    internal fun isSettingsOrSecurityPackage(pkgName: String): Boolean {
        val lower = pkgName.lowercase()
        return lower == "com.android.settings" ||
                lower == "com.google.android.settings" ||
                lower.contains("settings") ||
                lower.contains("securitycenter") ||
                lower.contains("safecenter") ||
                lower.contains("permissionmanager") ||
                lower == "com.samsung.android.app.telephonyui" ||
                lower == "com.samsung.android.sm_cn"
    }

    internal fun isAppDetailsClass(className: String?): Boolean {
        if (className == null) return false
        val lower = className.lowercase()
        return lower.contains("installedappdetails") ||
                lower.contains("appdetails") ||
                lower.contains("appmanagementactivity") ||
                lower.contains("spaactivity") ||
                lower.contains("applicationsstate") ||
                lower.contains("subsettings") ||
                lower.contains("applicationsetting") ||
                lower.contains("appinfo")
    }

    internal fun isEssentialSystemSettings(className: String?): Boolean {
        if (className == null) return false
        val lower = className.lowercase()
        return lower.contains("accessibility") ||
                lower.contains("deviceadmin") ||
                lower.contains("wireless") ||
                lower.contains("wifi") ||
                lower.contains("network") ||
                lower.contains("bluetooth") ||
                lower.contains("display") ||
                lower.contains("sound") ||
                lower.contains("volume") ||
                lower.contains("language") ||
                lower.contains("datetime") ||
                lower.contains("development") ||
                lower.contains("fingerprint") ||
                lower.contains("biometric") ||
                lower.contains("securitysettings") ||
                lower.contains("lockscreen")
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
        // System paused accessibility events (e.g. Doze mode, low memory, or battery saver throttling).
        // Safely reset transient in-flight states to keep sessions strictly secure.
        if (::appLockManager.isInitialized) {
            appLockManager.onScreenOff()
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isServiceRunning = false
        // Return true so Android framework triggers onRebind when service reconnects after temporary unbind
        return true
    }

    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
        isServiceRunning = true
        if (!::appLockManager.isInitialized) {
            appLockManager = AppLockManager.getInstance(applicationContext)
        }
    }

    override fun onDestroy() {
        isServiceRunning = false
        super.onDestroy()
    }
}
