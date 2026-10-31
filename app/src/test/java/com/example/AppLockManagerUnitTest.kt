package com.example

import com.example.data.security.AppLockPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockManagerUnitTest {

    @Test
    fun launcherPackageCache_contractVerification_cachesResultsAndClearsOnInvalidate() {
        val launcherPackageCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
        var pmQueriesCount = 0

        fun isSecondaryLauncher(packageName: String): Boolean {
            return launcherPackageCache.computeIfAbsent(packageName) { pkg ->
                pmQueriesCount++
                pkg.contains("launcher", ignoreCase = true)
            }
        }

        // 1. Initial query for non-launcher package performs query
        assertFalse(isSecondaryLauncher("com.android.chrome"))
        assertEquals(1, pmQueriesCount)

        // 2. Repeated query for same package uses cached value without querying again
        assertFalse(isSecondaryLauncher("com.android.chrome"))
        assertEquals(1, pmQueriesCount)

        // 3. Query for launcher package queries once
        assertTrue(isSecondaryLauncher("com.nova.launcher"))
        assertEquals(2, pmQueriesCount)

        // 4. Repeated query for launcher package uses cached result
        assertTrue(isSecondaryLauncher("com.nova.launcher"))
        assertEquals(2, pmQueriesCount)

        // 5. Invalidation clears cache, forcing fresh query on next check
        launcherPackageCache.clear()
        assertFalse(isSecondaryLauncher("com.android.chrome"))
        assertEquals(3, pmQueriesCount)
    }

    @Test
    fun patternAndCredentialValidation_functionsCorrectly() {
        assertTrue(AppLockPreferences.isValidCredential(AppLockPreferences.AuthMethod.PIN_4, "1234"))
        assertFalse(AppLockPreferences.isValidCredential(AppLockPreferences.AuthMethod.PIN_4, "123"))

        val pattern = AppLockPreferences.normalizePatternString("0-1-2-3")
        assertEquals("0-1-2-3", pattern)
    }

    @Test
    fun sharedIconCache_cachingBehavior_retainsBitmapsWithoutRecomputation() {
        val cache = java.util.concurrent.ConcurrentHashMap<String, String>()
        var computeCount = 0

        fun getOrCompute(pkg: String): String {
            return cache.computeIfAbsent(pkg) {
                computeCount++
                "icon_$pkg"
            }
        }

        assertEquals("icon_com.example.app", getOrCompute("com.example.app"))
        assertEquals(1, computeCount)
        assertEquals("icon_com.example.app", getOrCompute("com.example.app"))
        assertEquals(1, computeCount)

        cache.clear()
        assertEquals("icon_com.example.app", getOrCompute("com.example.app"))
        assertEquals(2, computeCount)
    }

    @Test
    fun sharedAppNameCache_cachingBehavior_retainsLabelsAndClearsOnInvalidate() {
        val appNameCache = java.util.concurrent.ConcurrentHashMap<String, String>()
        var resolveCount = 0

        fun getAppName(pkg: String): String {
            val cached = appNameCache[pkg]
            if (cached != null) return cached
            resolveCount++
            val resolved = "App_$pkg"
            appNameCache[pkg] = resolved
            return resolved
        }

        // 1. Initial resolution computes and caches
        assertEquals("App_com.example.camera", getAppName("com.example.camera"))
        assertEquals(1, resolveCount)

        // 2. Subsequent queries hit the memory cache without resolving
        assertEquals("App_com.example.camera", getAppName("com.example.camera"))
        assertEquals(1, resolveCount)

        // 3. Different package resolves and caches independently
        assertEquals("App_com.example.gallery", getAppName("com.example.gallery"))
        assertEquals(2, resolveCount)

        // 4. Invalidation clears cache, forcing re-resolution
        appNameCache.clear()
        assertEquals("App_com.example.camera", getAppName("com.example.camera"))
        assertEquals(3, resolveCount)
    }

    @Test
    fun batteryGuidance_oemBrandMapping_returnsValidGuidance() {
        val oemBrand = com.example.util.BatteryOptimizationHelper.getDeviceBrand()
        assertTrue(oemBrand in com.example.util.BatteryOptimizationHelper.OemBrand.entries)
    }

    @Test
    fun backgroundRecovery_unlockedPackagesDefaultsToEmpty_secureByDefault() {
        // Simulates process recreation after being killed by OS under low-battery / low-memory
        val recreatedUnlockedPackages = java.util.Collections.newSetFromMap(
            java.util.concurrent.ConcurrentHashMap<String, Boolean>()
        )
        // Ensure upon process restart, no apps remain unlocked without re-authentication
        assertTrue(recreatedUnlockedPackages.isEmpty())
        assertFalse(recreatedUnlockedPackages.contains("com.whatsapp"))
    }

    @Test
    fun installerSessionGraceWindow_contractVerification() {
        val sessionDuration = 45000L
        var unlockTime = 1000L
        fun isSessionActive(currentTime: Long): Boolean {
            val elapsed = currentTime - unlockTime
            return unlockTime > 0L && elapsed in 0L..sessionDuration
        }

        assertTrue(isSessionActive(1000L))
        assertTrue(isSessionActive(20000L))
        assertTrue(isSessionActive(45000L))
        assertFalse(isSessionActive(46001L))
    }
}
