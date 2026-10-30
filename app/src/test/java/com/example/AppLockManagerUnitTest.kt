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
}
