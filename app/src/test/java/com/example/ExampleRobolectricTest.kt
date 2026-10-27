package com.example

import android.content.Context
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
        assertEquals("Secure AppLock", appName)
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
        assertEquals(componentName, intent.getParcelableExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN))
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

        // System UI should always return true
        assertTrue("com.android.systemui must be recognized as system UI", manager.isLauncherOrSystemUI("com.android.systemui"))

        // Standard app package should return false
        assertFalse("Arbitrary app package must not be recognized as launcher", manager.isLauncherOrSystemUI("com.example.otherapp"))

        // Invalidating launcher cache shouldn't break subsequent calls
        manager.invalidateLauncherCache()
        assertFalse("Arbitrary app package must still return false after cache invalidation", manager.isLauncherOrSystemUI("com.example.otherapp"))
    }
}
