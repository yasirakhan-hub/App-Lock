package com.example

import android.content.Context
import androidx.core.content.IntentCompat
import androidx.test.core.app.ApplicationProvider
import com.example.data.security.AppLockPreferences
import com.example.data.security.PinHashUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var preferences: AppLockPreferences

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        preferences = AppLockPreferences(context)
        preferences.clearAllData()
    }

    @Test
    fun testAppNameResource() {
        val appName = context.getString(R.string.app_name)
        assertEquals("Smart AppLock", appName)
    }

    @Test
    fun testPinHashUtilSaltingAndHashing() {
        val pin = "1234"
        val saltedHash1 = PinHashUtil.createSaltedHash(pin)

        assertTrue("Salt must not be empty", saltedHash1.salt.isNotEmpty())
        assertTrue("Hash must not be empty", saltedHash1.hash.isNotEmpty())

        // Salting uniqueness: hashing same PIN twice should yield different salts and hashes
        val saltedHash2 = PinHashUtil.createSaltedHash(pin)
        assertNotEquals("Each salt must be unique", saltedHash1.salt, saltedHash2.salt)
        assertNotEquals("Hashes with different salts must differ", saltedHash1.hash, saltedHash2.hash)

        // Verification tests
        assertTrue("Correct PIN must verify against stored hash", PinHashUtil.verifyPin(pin, saltedHash1.salt, saltedHash1.hash))
        assertFalse("Incorrect PIN must fail verification", PinHashUtil.verifyPin("9999", saltedHash1.salt, saltedHash1.hash))
        assertFalse("Empty PIN input must fail", PinHashUtil.verifyPin("", saltedHash1.salt, saltedHash1.hash))
        assertFalse("Empty salt must fail", PinHashUtil.verifyPin(pin, "", saltedHash1.hash))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testPinHashUtilEmptyPinThrows() {
        PinHashUtil.createSaltedHash("")
    }

    @Test
    fun testPreferencesPinFlowWithPinHashUtil() {
        assertFalse("PIN should not be configured initially", preferences.isPinConfigured())

        preferences.savePin("5678")
        assertTrue("PIN should be configured after saving", preferences.isPinConfigured())
        assertTrue("Correct PIN verifies through preferences", preferences.verifyPin("5678"))
        assertFalse("Wrong PIN fails through preferences", preferences.verifyPin("1111"))
    }

    @Test
    fun testRecoveryQuestionFlow() {
        val question = "What city were you born in?"
        val answer = "Seattle"

        preferences.saveRecoveryQuestion(question, answer)
        assertTrue(preferences.hasRecoveryQuestion())
        assertEquals(question, preferences.getSecurityQuestion())

        assertTrue("Matching answer verifies", preferences.verifyRecoveryAnswer("Seattle"))
        assertTrue("Case-insensitive answer verifies", preferences.verifyRecoveryAnswer("seattle"))
        assertTrue("Trimmed answer verifies", preferences.verifyRecoveryAnswer("  seattle  "))
        assertFalse("Wrong answer fails", preferences.verifyRecoveryAnswer("Portland"))
    }

    @Test
    fun testBiometricPreferences() {
        assertTrue("Biometric should be enabled by default", preferences.isBiometricEnabled())

        preferences.setBiometricEnabled(false)
        assertFalse("Biometric should be disabled after setting false", preferences.isBiometricEnabled())

        preferences.setBiometricEnabled(true)
        assertTrue("Biometric should be re-enabled", preferences.isBiometricEnabled())

        // Ensure BiometricHelper availability check runs cleanly
        val available = com.example.util.BiometricHelper.isBiometricAvailable(context)
        // Returns boolean without crashing on Robolectric
        assertTrue(available || !available)
    }

    @Test
    fun testDeviceAdminHelper() {
        val componentName = com.example.util.DeviceAdminHelper.getComponentName(context)
        assertEquals(context.packageName, componentName.packageName)
        assertEquals(com.example.receiver.AppLockDeviceAdminReceiver::class.java.name, componentName.className)

        // Ensure isDeviceAdminActive runs cleanly
        val isActive = com.example.util.DeviceAdminHelper.isDeviceAdminActive(context)
        assertTrue(isActive || !isActive)

        // Ensure intent creation has correct action and extra
        val intent = com.example.util.DeviceAdminHelper.createAddDeviceAdminIntent(context)
        assertEquals(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN, intent.action)
        assertEquals(componentName, IntentCompat.getParcelableExtra(intent, android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, android.content.ComponentName::class.java))
    }

    @Test
    fun testProtectedPackagesPersistence() {
        val pkg1 = "com.sample.banking"
        val pkg2 = "com.sample.gallery"

        assertFalse(preferences.isPackageProtected(pkg1))

        preferences.setPackageProtected(pkg1, true)
        assertTrue(preferences.isPackageProtected(pkg1))
        assertFalse(preferences.isPackageProtected(pkg2))

        preferences.setPackageProtected(pkg2, true)
        val set = preferences.getProtectedPackages()
        assertEquals(2, set.size)
        assertTrue(set.contains(pkg1))
        assertTrue(set.contains(pkg2))

        preferences.setPackageProtected(pkg1, false)
        assertFalse(preferences.isPackageProtected(pkg1))
        assertTrue(preferences.isPackageProtected(pkg2))
    }

    @Test
    fun testBruteForceLockout() {
        assertEquals(0, preferences.getFailedAttempts())
        assertEquals(0, preferences.getRemainingLockoutSeconds())

        // 4 failed attempts should not lock out
        repeat(4) { preferences.recordFailedAttempt() }
        assertEquals(4, preferences.getFailedAttempts())
        assertEquals(0, preferences.getRemainingLockoutSeconds())

        // 5th attempt triggers lockout
        preferences.recordFailedAttempt()
        assertEquals(5, preferences.getFailedAttempts())
        assertTrue("Should be locked out after 5 attempts", preferences.getRemainingLockoutSeconds() > 0)

        // Reset clears lockout
        preferences.resetFailedAttempts()
        assertEquals(0, preferences.getFailedAttempts())
        assertEquals(0, preferences.getRemainingLockoutSeconds())
    }

    @Test
    fun testInterceptedTargetStorageAndRetrieval() {
        val manager = com.example.data.repository.AppLockManager.getInstance(context)
        val testPkg = "com.google.android.deskclock"
        val testClass = "com.android.deskclock.DeskClock"

        manager.storeInterceptedTarget(testPkg, testClass)

        val retrieved = manager.getAndRemoveInterceptedTarget(testPkg)
        assertEquals(testPkg, retrieved?.packageName)
        assertEquals(testClass, retrieved?.className)

        // Second retrieval should be null since it was removed
        val secondRetrieval = manager.getAndRemoveInterceptedTarget(testPkg)
        org.junit.Assert.assertNull(secondRetrieval)
    }

    @Test
    fun testLauncherOrSystemUIDetectionAndCaching() {
        val manager = com.example.data.repository.AppLockManager.getInstance(context)

        // System UI and Android system resolver should return true
        assertTrue("com.android.systemui must be recognized as system UI", manager.isLauncherOrSystemUI("com.android.systemui"))
        assertTrue("android resolver must be recognized as system UI", manager.isLauncherOrSystemUI("android"))

        // Standard app package should return false
        assertFalse("Arbitrary app package must not be recognized as launcher", manager.isLauncherOrSystemUI("com.example.otherapp"))

        // Invalidating launcher cache shouldn't break subsequent calls
        manager.invalidateLauncherCache()
        assertFalse("Arbitrary app package must still return false after cache invalidation", manager.isLauncherOrSystemUI("com.example.otherapp"))
    }

    @Test
    fun testBatteryOptimizationHelper() {
        val ignoring = com.example.util.PermissionHelper.isIgnoringBatteryOptimizations(context)
        assertTrue(ignoring || !ignoring)
    }

    @Test
    fun testPackageInstallerDetection() {
        val manager = com.example.data.repository.AppLockManager.getInstance(context)
        assertTrue(manager.isPackageInstaller("com.google.android.packageinstaller"))
        assertTrue(manager.isPackageInstaller("com.android.packageinstaller"))
        assertTrue(manager.isPackageInstaller("com.samsung.android.packageinstaller"))
        assertFalse(manager.isPackageInstaller("com.example.otherapp"))
    }

    @Test
    fun testDeviceAdminReceiverOnDisableRequested() {
        val receiver = com.example.receiver.AppLockDeviceAdminReceiver()
        val intent = android.content.Intent()
        val warning = receiver.onDisableRequested(context, intent)
        assertTrue("Warning message must be non-empty", warning.isNotEmpty())
        assertTrue("Warning message must mention uninstall or PIN", warning.contains("uninstall") || warning.contains("PIN"))
    }

    @Test
    fun testMandatoryVaultAuthenticationGateAndSessionRelock() {
        val navVm = MainNavigationViewModel(androidx.lifecycle.SavedStateHandle())

        // Initially not authorized for Vault; cold start targeting VAULT redirects to VAULT_AUTH_GATE
        assertFalse(navVm.isVaultAuthorized())
        assertEquals(ScreenState.VAULT_AUTH_GATE, navVm.getOrInitScreen { ScreenState.VAULT })

        // Requesting vault access from any entry point routes to VAULT_AUTH_GATE
        val requested = navVm.requestVaultAccess(ScreenState.DASHBOARD)
        assertEquals(ScreenState.VAULT_AUTH_GATE, requested)
        assertEquals(ScreenState.VAULT_AUTH_GATE, navVm.getOrInitScreen { ScreenState.DASHBOARD })
        assertFalse(navVm.isVaultAuthorized())

        // Authorizing vault entry unlocks VAULT
        val authorizedScreen = navVm.authorizeVaultEntry()
        assertTrue(navVm.isVaultAuthorized())
        assertEquals(ScreenState.VAULT, authorizedScreen)
        assertEquals(ScreenState.VAULT, navVm.getOrInitScreen { ScreenState.DASHBOARD })

        // Backgrounding lockVaultSession() immediately locks the vault and reverts VAULT to VAULT_AUTH_GATE
        navVm.lockVaultSession()
        assertFalse(navVm.isVaultAuthorized())
        assertEquals(ScreenState.VAULT_AUTH_GATE, navVm.getOrInitScreen { ScreenState.DASHBOARD })

        // Re-authorize and then leave Files via bottom nav / Back button -> immediately locks vault
        navVm.authorizeVaultEntry()
        assertTrue(navVm.isVaultAuthorized())
        navVm.setScreen(ScreenState.SETTINGS)
        assertFalse("Leaving Files screen must lock the vault immediately", navVm.isVaultAuthorized())
        assertEquals(ScreenState.SETTINGS, navVm.getOrInitScreen { ScreenState.DASHBOARD })

        // Re-entering Files requires VAULT_AUTH_GATE again
        val reRequested = navVm.requestVaultAccess(ScreenState.SETTINGS)
        assertEquals(ScreenState.VAULT_AUTH_GATE, reRequested)
        assertEquals(ScreenState.VAULT_AUTH_GATE, navVm.getOrInitScreen { ScreenState.DASHBOARD })
        assertFalse(navVm.isVaultAuthorized())
    }

    @Test
    fun testBatchAddAndRemoveProtectedPackages() {
        val batch = listOf("com.whatsapp", "com.instagram.android", "com.google.android.youtube")
        preferences.addProtectedPackages(batch)

        val protectedSet = preferences.getProtectedPackages()
        assertEquals(3, protectedSet.size)
        assertTrue(preferences.isPackageProtected("com.whatsapp"))
        assertTrue(preferences.isPackageProtected("com.instagram.android"))
        assertTrue(preferences.isPackageProtected("com.google.android.youtube"))

        // Adding another batch preserves existing protected packages
        preferences.addProtectedPackages(listOf("com.google.android.gm", "com.whatsapp"))
        assertEquals(4, preferences.getProtectedPackages().size)
        assertTrue(preferences.isPackageProtected("com.google.android.gm"))

        // Removing a subset updates immediately
        preferences.removeProtectedPackages(listOf("com.instagram.android", "com.google.android.youtube"))
        assertEquals(2, preferences.getProtectedPackages().size)
        assertFalse(preferences.isPackageProtected("com.instagram.android"))
        assertTrue(preferences.isPackageProtected("com.whatsapp"))
    }

    @Test
    fun testAppManagementSessionAuthenticationLifecycle() {
        val navVm = MainNavigationViewModel(androidx.lifecycle.SavedStateHandle())

        // Initially not authenticated for App Management
        assertFalse(navVm.isAppManagementAuthorized())

        // Entering APP_SELECTION and authenticating once keeps session active
        navVm.setScreen(ScreenState.APP_SELECTION)
        assertFalse(navVm.isAppManagementAuthorized())

        navVm.authorizeAppManagementSession()
        assertTrue("Management session must remain active after single auth", navVm.isAppManagementAuthorized())

        // Staying in APP_SELECTION preserves management session
        navVm.setScreen(ScreenState.APP_SELECTION)
        assertTrue(navVm.isAppManagementAuthorized())

        // Exiting APP_SELECTION to DASHBOARD immediately revokes session so re-entry requires auth again
        navVm.setScreen(ScreenState.DASHBOARD)
        assertFalse("Exiting App Selection must revoke management session", navVm.isAppManagementAuthorized())

        // Re-entering APP_SELECTION requires authentication again
        navVm.setScreen(ScreenState.APP_SELECTION)
        assertFalse(navVm.isAppManagementAuthorized())

        // Backgrounding via lockAppManagementSession() also revokes session
        navVm.authorizeAppManagementSession()
        assertTrue(navVm.isAppManagementAuthorized())
        navVm.lockAppManagementSession()
        assertFalse(navVm.isAppManagementAuthorized())
    }

    @Test
    fun testLauncherIconResourcesResolve() {
        val launcherDrawable = androidx.core.content.ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
        val roundLauncherDrawable = androidx.core.content.ContextCompat.getDrawable(context, R.mipmap.ic_launcher_round)
        val fgDrawable = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)
        val fgLogoDrawable = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground_logo)
        val inAppLogoDrawable = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.smart_applock_logo)
        val bgDrawable = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_launcher_background)

        org.junit.Assert.assertNotNull("ic_launcher mipmap must resolve", launcherDrawable)
        org.junit.Assert.assertNotNull("ic_launcher_round mipmap must resolve", roundLauncherDrawable)
        org.junit.Assert.assertNotNull("ic_launcher_foreground drawable must resolve", fgDrawable)
        org.junit.Assert.assertNotNull("ic_launcher_foreground_logo drawable must resolve", fgLogoDrawable)
        org.junit.Assert.assertNotNull("smart_applock_logo drawable must resolve", inAppLogoDrawable)
        org.junit.Assert.assertNotNull("ic_launcher_background drawable must resolve", bgDrawable)
    }

    @Test
    fun testFilesToHomeAndTabsDirectNavigationAfterVaultAuth() {
        val navVm = MainNavigationViewModel(androidx.lifecycle.SavedStateHandle())

        // 1. User enters Files -> must go through VAULT_AUTH_GATE
        assertEquals(ScreenState.VAULT_AUTH_GATE, navVm.requestVaultAccess(ScreenState.DASHBOARD))
        assertEquals(ScreenState.VAULT, navVm.authorizeVaultEntry())
        assertTrue(navVm.isVaultAuthorized())

        // 2. From Files (VAULT), user taps Home -> locks vault and goes directly to DASHBOARD (NOT VAULT_AUTH_GATE)
        navVm.lockVaultSession()
        navVm.setScreen(ScreenState.DASHBOARD)
        assertFalse("Vault must be locked immediately upon leaving Files", navVm.isVaultAuthorized())
        assertEquals(
            "Navigating from Files to Home must land directly on DASHBOARD without re-prompting VAULT_AUTH_GATE",
            ScreenState.DASHBOARD,
            navVm.getOrInitScreen { ScreenState.AUTH_GATE }
        )

        // 3. Re-enter Files, authenticate, then tap Apps -> goes directly to APP_SELECTION
        navVm.requestVaultAccess(ScreenState.DASHBOARD)
        navVm.authorizeVaultEntry()
        assertTrue(navVm.isVaultAuthorized())
        navVm.lockVaultSession()
        navVm.setScreen(ScreenState.APP_SELECTION)
        assertFalse(navVm.isVaultAuthorized())
        assertEquals(ScreenState.APP_SELECTION, navVm.getOrInitScreen { ScreenState.AUTH_GATE })
    }

    @Test
    fun testThreeOptionHomeThemeSwitcherPersistence() {
        // Default is System Default (null)
        org.junit.Assert.assertNull(preferences.isDarkThemeOverride())

        // Option 1: Dark
        preferences.setDarkThemeOverride(true)
        assertEquals(true, preferences.isDarkThemeOverride())

        // Option 2: Light
        preferences.setDarkThemeOverride(false)
        assertEquals(false, preferences.isDarkThemeOverride())

        // Option 3: System Default (Auto)
        preferences.setDarkThemeOverride(null)
        org.junit.Assert.assertNull(preferences.isDarkThemeOverride())
    }
}





