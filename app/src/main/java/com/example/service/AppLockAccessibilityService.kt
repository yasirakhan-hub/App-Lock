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

            appLockManager.onForegroundPackageChanged(pkgName, className)
        }
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
