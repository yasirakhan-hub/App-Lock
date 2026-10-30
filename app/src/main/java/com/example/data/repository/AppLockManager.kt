package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.inputmethod.InputMethodManager
import com.example.data.security.AppLockPreferences
import com.example.ui.lock.LockActivity
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Data model representing an intercepted target application launch.
 */
data class InterceptedTarget(
    val packageName: String,
    val className: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Resolves the best Intent to relaunch the exact intercepted Activity or main entry point.
     */
    fun createLaunchIntent(packageManager: PackageManager): Intent? {
        // First try to resolve the exact activity class that was intercepted
        if (!className.isNullOrBlank()) {
            try {
                val explicitIntent = Intent().apply {
                    setClassName(packageName, className)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                val resolveInfo = packageManager.resolveActivity(explicitIntent, PackageManager.MATCH_DEFAULT_ONLY)
                if (resolveInfo != null) {
                    return explicitIntent
                }
            } catch (_: Exception) {
            }
        }

        // Fallback to the package's default launcher Intent
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return launchIntent
    }
}

/**
 * Singleton managing session authentication states, package lock decisions,
 * storing intercepted target intents, and resuming target applications upon verification.
 */
class AppLockManager private constructor(private val context: Context) {

    val preferences = AppLockPreferences(context)

    // Set of package names authenticated in the current session
    private val unlockedPackages = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    // Thread-safe map holding the latest intercepted target for each locked package
    private val pendingInterceptedTargets = ConcurrentHashMap<String, InterceptedTarget>()

    @Volatile
    var currentForegroundPackage: String? = null
        private set

    @Volatile
    private var lastLockLaunchTime: Long = 0L

    @Volatile
    var isLockActivityActive: Boolean = false

    @Volatile
    var isSystemPickerInFlight: Boolean = false
        private set

    @Volatile
    private var lastSystemPickerTimestamp: Long = 0L

    // Cache the default home launcher package name to avoid repeated PackageManager IPC Binder calls on every event
    @Volatile
    private var cachedLauncherPackage: String? = null

    @Volatile
    private var cachedImePackages: Set<String>? = null

    @Volatile
    private var lastImeCacheTimestamp: Long = 0L

    @Volatile
    var isRecentsOverviewActive: Boolean = false
        private set

    @Volatile
    private var lastRecentsOverviewTimestamp: Long = 0L

    @Volatile
    private var lastLauncherExitTimestamp: Long = 0L

    @Volatile
    private var pendingExitPackage: String? = null

    @Volatile
    private var pendingExitTimestamp: Long = 0L

    companion object {
        @Volatile
        private var instance: AppLockManager? = null

        fun getInstance(context: Context): AppLockManager {
            val appCtx = context.applicationContext
            val current = instance
            if (current != null && current.context === appCtx) {
                return current
            }
            return synchronized(this) {
                val existing = instance
                if (existing != null && existing.context === appCtx) {
                    existing
                } else {
                    AppLockManager(appCtx).also { instance = it }
                }
            }
        }

        private const val LAUNCH_DEBOUNCE_MS = 600L
        private const val SYSTEM_PICKER_GRACE_MS = 3000L
        private const val RECENTS_GRACE_WINDOW_MS = 1200L
        private const val IME_CACHE_TTL_MS = 30000L

        val KNOWN_IME_PACKAGES = setOf(
            "com.google.android.inputmethod.latin", // Gboard
            "com.touchtype.swiftkey", // SwiftKey
            "com.samsung.android.honeyboard", // Samsung Keyboard
            "com.baidu.input_mi", // Xiaomi Baidu IME
            "com.sohu.inputmethod.sogou", // Sogou IME
            "com.emoji.keyboard.touchpal",
            "com.syntellia.fleksy.keyboard",
            "com.sec.android.inputmethod",
            "com.oppo.keyboard"
        )

        val SYSTEM_PERMISSION_RESOLVER_PACKAGES = setOf(
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
            "com.android.intentresolver",
            "com.google.android.intentresolver",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.samsung.android.packageinstaller"
        )

        val SYSTEM_PICKER_PACKAGES = setOf(
            "com.android.documentsui",
            "com.google.android.documentsui",
            "com.android.providers.media.module",
            "com.google.android.providers.media.module",
            "com.android.providers.media",
            "com.google.android.providers.media",
            "com.android.intentresolver",
            "com.google.android.intentresolver",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller"
        )

        val KNOWN_OEM_LAUNCHERS = setOf(
            "com.google.android.apps.nexuslauncher", // Pixel Launcher
            "com.android.launcher3", // AOSP Launcher3
            "com.sec.android.app.launcher", // Samsung One UI Home
            "com.miui.home", // Xiaomi HyperOS / MIUI Launcher
            "com.oppo.launcher", // OPPO ColorOS Launcher
            "com.bbk.launcher2", // Vivo FuntouchOS Launcher
            "com.vivo.launcher", // Vivo Launcher
            "com.motorola.launcher3", // Motorola My UX Launcher
            "net.oneplus.launcher", // OnePlus OxygenOS Launcher
            "com.huawei.android.launcher", // Huawei EMUI / HarmonyOS
            "com.transsion.hilauncher", // Tecno / Infinix HiOS
            "com.teslacoilsw.launcher", // Nova Launcher
            "com.microsoft.launcher", // Microsoft Launcher
            "com.actionlauncher.playstore", // Action Launcher
            "com.smartlauncher" // Smart Launcher
        )
    }

    /**
     * Marks whether our own app has launched a system file/media picker (SAF / PhotoPicker)
     * so AccessibilityService does not intercept the picker activity with LockActivity.
     */
    fun setSystemPickerInFlight(active: Boolean) {
        isSystemPickerInFlight = active
        lastSystemPickerTimestamp = System.currentTimeMillis()
    }

    /**
     * Returns true while a system picker is active or within a short return grace window.
     */
    fun isSystemPickerBypassActive(): Boolean {
        if (isSystemPickerInFlight) return true
        val elapsed = System.currentTimeMillis() - lastSystemPickerTimestamp
        return lastSystemPickerTimestamp > 0L && elapsed in 0L..SYSTEM_PICKER_GRACE_MS
    }

    /**
     * Checks if protection is enabled and initial PIN setup is complete.
     */
    fun isProtectionActive(): Boolean {
        return preferences.isProtectionEnabled() && preferences.isPinConfigured()
    }

    /**
     * Called by AccessibilityService when user enters the Recent Apps overview.
     */
    fun onRecentsOverviewEntered() {
        isRecentsOverviewActive = true
        lastRecentsOverviewTimestamp = System.currentTimeMillis()
    }

    /**
     * Called whenever a foreground window state change is detected by the AccessibilityService.
     */
    fun onForegroundPackageChanged(newPackage: String, className: String? = null) {
        if (newPackage.isBlank()) return

        // Ignore our own application or lock activity
        if (newPackage == context.packageName) {
            return
        }

        // Never intercept system pickers or chooser dialogs launched while importing/exporting vault files
        if (isSystemPickerBypassActive() || isSystemDocumentOrMediaPicker(newPackage)) {
            return
        }

        // 1. Transient UI elements (keyboards, system UI volume/status bars, autofill, permission controllers):
        // NEVER treat transient overlays as leaving the current protected app!
        // This ensures Instagram Stories, Chats, keyboard toggles, and volume panels
        // never reset or evict the active authenticated session.
        if (isTransientSystemOrOverlayPackage(newPackage)) {
            return
        }

        val previous = currentForegroundPackage
        val isLauncher = isLauncherOrSystemUI(newPackage)
        val isRecents = isRecentsOrOverview(newPackage, className)

        // 2. Recent Apps Overview entered:
        if (isRecents) {
            isRecentsOverviewActive = true
            lastRecentsOverviewTimestamp = System.currentTimeMillis()
            return
        }

        // 3. User is on Home screen or Launcher:
        if (isLauncher) {
            isRecentsOverviewActive = false
            currentForegroundPackage = newPackage
            lastLauncherExitTimestamp = System.currentTimeMillis()

            // If immediate relock on exit is enabled:
            // Stage previous app for relock, but allow a grace period so gesture
            // navigation into Recent Apps overview does NOT falsely lock the app!
            if (!preferences.isRelockOnScreenOff() && previous != null &&
                previous != newPackage && !isLauncherOrSystemUI(previous) && !isTransientSystemOrOverlayPackage(previous)
            ) {
                pendingExitPackage = previous
                pendingExitTimestamp = System.currentTimeMillis()
            }
            return
        }

        // 4. We are entering a real application (e.g., Instagram, WhatsApp, Banking app, Settings, etc.):
        isRecentsOverviewActive = false
        val now = System.currentTimeMillis()

        // If user switched directly to a DIFFERENT real app (App A -> App B):
        if (!preferences.isRelockOnScreenOff() && previous != null &&
            previous != newPackage && !isLauncherOrSystemUI(previous) && !isTransientSystemOrOverlayPackage(previous)
        ) {
            unlockedPackages.remove(previous)
        }

        // Handle pending exit package from recent launcher transition:
        val pendingExit = pendingExitPackage
        if (pendingExit != null) {
            if (pendingExit != newPackage) {
                // User opened a different app: immediately relock the previous app
                unlockedPackages.remove(pendingExit)
                pendingExitPackage = null
            } else {
                // User returned to the same app:
                // Check if user stayed away on launcher longer than the gesture/recents grace window
                val elapsedSinceExit = now - pendingExitTimestamp
                if (elapsedSinceExit > RECENTS_GRACE_WINDOW_MS) {
                    // Truly left and stayed out: relock
                    unlockedPackages.remove(newPackage)
                }
                // Otherwise: quick gesture or returned from Recents within grace window: keep unlocked!
                pendingExitPackage = null
            }
        }

        currentForegroundPackage = newPackage

        if (shouldLock(newPackage, className)) {
            // Store the intercepted target intent before launching LockActivity
            storeInterceptedTarget(newPackage, className)
            launchLockScreen(newPackage, className)
        }
    }

    /**
     * Stores the intercepted target info so it can be resumed after successful PIN entry.
     */
    fun storeInterceptedTarget(packageName: String, className: String?) {
        pendingInterceptedTargets[packageName] = InterceptedTarget(packageName, className)
    }

    /**
     * Retrieves and clears the stored intercepted target for a package.
     */
    fun getAndRemoveInterceptedTarget(packageName: String): InterceptedTarget? {
        return pendingInterceptedTargets.remove(packageName)
    }

    /**
     * Launches the intercepted target intent after authentication succeeds.
     * Returns true if an intent was resolved and started.
     */
    fun launchInterceptedTarget(context: Context, packageName: String): Boolean {
        val intercepted = getAndRemoveInterceptedTarget(packageName)
        val intent = intercepted?.createLaunchIntent(context.packageManager)
            ?: context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }

        return if (intent != null) {
            try {
                context.startActivity(intent)
                true
            } catch (_: Exception) {
                // If starting the explicit intercepted activity failed (e.g. unexported component),
                // fallback to the package's default root launch intent
                val fallbackIntent = context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                if (fallbackIntent != null) {
                    try {
                        context.startActivity(fallbackIntent)
                        true
                    } catch (_: Exception) {
                        false
                    }
                } else {
                    false
                }
            }
        } else {
            false
        }
    }

    /**
     * Checks if a package is currently authenticated/unlocked in this session.
     */
    fun isPackageUnlocked(packageName: String): Boolean {
        return unlockedPackages.contains(packageName)
    }

    /**
     * Checks if Smart AppLock's self App Info screen has been unlocked in this session.
     */
    fun isSelfAppInfoUnlocked(): Boolean {
        return unlockedPackages.contains(context.packageName)
    }

    /**
     * Records that Smart AppLock's self App Info screen was unlocked.
     */
    fun onSelfAppInfoUnlocked() {
        unlockedPackages.add(context.packageName)
    }

    /**
     * Determines whether the given package should prompt for authentication.
     */
    fun shouldLock(packageName: String, className: String? = null): Boolean {
        if (!isProtectionActive()) return false
        if (packageName == context.packageName) return false
        if (isSystemPickerBypassActive()) return false
        if (isSystemDocumentOrMediaPicker(packageName)) return false
        if (isTransientSystemOrOverlayPackage(packageName)) return false
        if (isLauncherOrSystemUI(packageName)) return false
        if (isRecentsOverviewActive || isRecentsOrOverview(packageName, className)) return false
        if (!preferences.isPackageProtected(packageName)) return false
        if (unlockedPackages.contains(packageName)) return false

        return true
    }

    /**
     * Launches the LockActivity over the protected application.
     */
    fun launchLockScreen(targetPackage: String, targetClass: String? = null) {
        val now = System.currentTimeMillis()
        if (isLockActivityActive && (now - lastLockLaunchTime < LAUNCH_DEBOUNCE_MS)) {
            return
        }
        lastLockLaunchTime = now

        val intent = Intent(context, LockActivity::class.java).apply {
            putExtra(LockActivity.EXTRA_TARGET_PACKAGE, targetPackage)
            if (!targetClass.isNullOrBlank()) {
                putExtra(LockActivity.EXTRA_TARGET_CLASS, targetClass)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    /**
     * Called when the user successfully enters the PIN for targetPackage.
     */
    fun onPackageUnlocked(packageName: String) {
        unlockedPackages.add(packageName)
        preferences.resetFailedAttempts()
    }

    /**
     * Called when screen turns off. Resets all temporary session authentications.
     */
    fun onScreenOff() {
        unlockedPackages.clear()
        pendingInterceptedTargets.clear()
        pendingExitPackage = null
        pendingExitTimestamp = 0L
        isRecentsOverviewActive = false
    }

    /**
     * Clears session cache (e.g. for testing or manual lock).
     */
    fun lockAll() {
        unlockedPackages.clear()
        pendingInterceptedTargets.clear()
        pendingExitPackage = null
        pendingExitTimestamp = 0L
        isRecentsOverviewActive = false
        invalidateLauncherCache()
    }

    /**
     * Invalidates cached launcher package, forcing a re-query on the next check.
     */
    fun invalidateLauncherCache() {
        cachedLauncherPackage = null
    }

    /**
     * Checks if the window state belongs to an Input Method (soft keyboard).
     * Soft keyboard show/hide events must NEVER evict the active session of
     * the underlying protected app (e.g. typing comments or messages in Instagram).
     */
    fun isInputMethodPackage(packageName: String): Boolean {
        if (isKnownImePackage(packageName)) return true

        val now = System.currentTimeMillis()
        val cached = cachedImePackages
        if (cached != null && (now - lastImeCacheTimestamp) < IME_CACHE_TTL_MS) {
            return cached.contains(packageName)
        }

        return try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            val imes = imm?.inputMethodList?.map { it.packageName }?.toSet() ?: emptySet()
            cachedImePackages = imes
            lastImeCacheTimestamp = now
            imes.contains(packageName)
        } catch (_: Exception) {
            isKnownImePackage(packageName)
        }
    }

    /**
     * Fallback lookup of common OEM keyboard package names.
     */
    fun isKnownImePackage(packageName: String): Boolean {
        return packageName in KNOWN_IME_PACKAGES
    }

    /**
     * Transient system overlays such as keyboards, volume panels, heads-up notifications,
     * autofill dialogs, and permission popups.
     */
    fun isTransientSystemOrOverlayPackage(packageName: String): Boolean {
        if (packageName == "com.android.systemui") return true
        if (packageName == "android") return true
        if (isInputMethodPackage(packageName)) return true
        if (isSystemPermissionOrResolverPackage(packageName)) return true
        return false
    }

    /**
     * Identifies system permission dialogues, package installers, and intent choosers.
     */
    fun isSystemPermissionOrResolverPackage(packageName: String): Boolean {
        return packageName in SYSTEM_PERMISSION_RESOLVER_PACKAGES
    }

    /**
     * Checks whether an event corresponds to Android Recent Apps (Overview).
     */
    fun isRecentsOrOverview(packageName: String, className: String?): Boolean {
        if (className != null) {
            val lower = className.lowercase()
            if (lower.contains("recents") || lower.contains("quickstep") || lower.contains("overview")) {
                return true
            }
        }
        return false
    }

    /**
     * Identifies known home launchers or system UI to avoid locking essential navigation.
     * Caches the home launcher package name in memory to eliminate expensive synchronous
     * PackageManager IPC/Binder calls on every foreground window state change.
     */
    fun isLauncherOrSystemUI(packageName: String): Boolean {
        if (packageName == "com.android.systemui") return true
        if (packageName == "android") return true
        if (packageName in KNOWN_OEM_LAUNCHERS) return true

        val launcherPkg = cachedLauncherPackage ?: synchronized(this) {
            cachedLauncherPackage ?: try {
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                val resolveInfo = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                resolveInfo?.activityInfo?.packageName?.also {
                    cachedLauncherPackage = it
                }
            } catch (_: Exception) {
                null
            }
        }

        if (packageName == launcherPkg) return true

        // Fallback check: check if the package explicitly provides a CATEGORY_HOME launcher activity
        return try {
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(packageName)
            val activities = context.packageManager.queryIntentActivities(homeIntent, 0)
            activities.isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Checks whether the given package is an Android Package Installer / Uninstaller component.
     */
    fun isPackageInstaller(packageName: String): Boolean {
        return packageName == "com.google.android.packageinstaller" ||
                packageName == "com.android.packageinstaller" ||
                packageName == "com.samsung.android.packageinstaller"
    }

    /**
     * Identifies Android system file/media pickers, intent resolvers, and permission controllers
     * so they are never intercepted by the app-lock overlay.
     */
    fun isSystemDocumentOrMediaPicker(packageName: String): Boolean {
        return packageName in SYSTEM_PICKER_PACKAGES
    }
}
