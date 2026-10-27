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

            // Anti-Force Stop Protection:
            // Intercept attempts to open Settings -> App Info for Secure AppLock
            if (isAppInfoForSelf(pkgName, className, event.text)) {
                performGlobalAction(GLOBAL_ACTION_HOME)
                return
            }

            // Anti-Uninstall Protection:
            // Intercept Package Installer when attempting to uninstall Secure AppLock
            if (appLockManager.isPackageInstaller(pkgName)) {
                val appName = getString(com.example.R.string.app_name)
                val isUninstallingSelf = event.text?.any { text ->
                    text.contains(appName, ignoreCase = true) || text.contains(packageName, ignoreCase = true)
                } ?: false

                if (isUninstallingSelf) {
                    appLockManager.launchLockScreen(pkgName, className)
                    return
                }
            }

            appLockManager.onForegroundPackageChanged(pkgName, className)
        }
    }

    private fun isAppInfoForSelf(pkgName: String, className: String?, textList: List<CharSequence>?): Boolean {
        if (pkgName == "com.android.settings") {
            val isAppDetailsClass = className != null && (
                    className.contains("InstalledAppDetails", ignoreCase = true) ||
                    className.contains("AppDetails", ignoreCase = true) ||
                    className.contains("ManageApplications", ignoreCase = true)
            )
            val appName = getString(com.example.R.string.app_name)
            val matchesSelf = textList?.any { text ->
                text.contains(appName, ignoreCase = true) || text.contains(packageName, ignoreCase = true)
            } ?: false

            return isAppDetailsClass && matchesSelf
        }
        return false
    }

    private fun isTransientUiElement(className: String?): Boolean {
        if (className == null) return false
        return className.contains("inputmethod", ignoreCase = true) ||
                className.contains("toast", ignoreCase = true) ||
                className.contains("popupwindow", ignoreCase = true) ||
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
