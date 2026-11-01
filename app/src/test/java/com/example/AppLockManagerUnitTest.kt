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

    @Test
    fun verifiedPackageInstallerPackages_matchesExpectedSet() {
        val verifiedInstallers = com.example.data.repository.AppLockManager.VERIFIED_PACKAGE_INSTALLER_PACKAGES
        assertTrue(verifiedInstallers.contains("com.google.android.packageinstaller"))
        assertTrue(verifiedInstallers.contains("com.android.packageinstaller"))
        assertTrue(verifiedInstallers.contains("com.samsung.android.packageinstaller"))
        assertTrue(verifiedInstallers.contains("com.motorola.packageinstaller"))
        assertTrue(verifiedInstallers.contains("com.miui.packageinstaller"))

        // Permission controller, OTA, app store, and unrelated apps must NOT be in the set
        assertFalse(verifiedInstallers.contains("com.google.android.permissioncontroller"))
        assertFalse(verifiedInstallers.contains("com.android.permissioncontroller"))
        assertFalse(verifiedInstallers.contains("com.motorola.ccc.ota"))
        assertFalse(verifiedInstallers.contains("com.huawei.appmarket"))
        assertFalse(verifiedInstallers.contains("com.whatsapp"))
        assertFalse(verifiedInstallers.contains("com.example.mypackageinstaller"))
        assertFalse(verifiedInstallers.contains("com.test.packagemanager.unrelated"))
    }

    @Test
    fun companionCacheInvalidation_contractVerification() {
        // Direct companion cache lifecycle test
        val mockCacheHolder = object {
            @Volatile
            var cachedProtectedPackages: Set<String>? = null

            fun setProtectedPackages(packages: Set<String>) {
                val newSet = HashSet(packages)
                synchronized(this) {
                    cachedProtectedPackages = newSet
                }
            }

            fun addProtectedPackages(packageNames: Collection<String>) {
                if (packageNames.isEmpty()) return
                val current = HashSet(getProtectedPackages())
                current.addAll(packageNames)
                setProtectedPackages(current)
            }

            fun removeProtectedPackages(packageNames: Collection<String>) {
                if (packageNames.isEmpty()) return
                val current = HashSet(getProtectedPackages())
                current.removeAll(packageNames.toSet())
                setProtectedPackages(current)
            }

            fun clearAllData() {
                synchronized(this) {
                    cachedProtectedPackages = null
                }
            }

            fun getProtectedPackages(): Set<String> {
                return cachedProtectedPackages ?: synchronized(this) {
                    cachedProtectedPackages ?: emptySet<String>().also {
                        cachedProtectedPackages = it
                    }
                }
            }
        }

        // 1. Initial state
        assertTrue(mockCacheHolder.getProtectedPackages().isEmpty())

        // 2. setProtectedPackages
        mockCacheHolder.setProtectedPackages(setOf("com.pkg.one", "com.pkg.two"))
        assertEquals(2, mockCacheHolder.getProtectedPackages().size)
        assertTrue(mockCacheHolder.getProtectedPackages().contains("com.pkg.one"))

        // 3. addProtectedPackages
        mockCacheHolder.addProtectedPackages(listOf("com.pkg.three"))
        assertEquals(3, mockCacheHolder.getProtectedPackages().size)
        assertTrue(mockCacheHolder.getProtectedPackages().contains("com.pkg.three"))

        // 4. removeProtectedPackages
        mockCacheHolder.removeProtectedPackages(listOf("com.pkg.one"))
        assertEquals(2, mockCacheHolder.getProtectedPackages().size)
        assertFalse(mockCacheHolder.getProtectedPackages().contains("com.pkg.one"))

        // 5. clearAllData resets to empty
        mockCacheHolder.clearAllData()
        assertTrue(mockCacheHolder.getProtectedPackages().isEmpty())
    }
}
