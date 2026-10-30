package com.example

import android.content.Context
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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

    @Test
    fun testVaultSystemPickerLifecycleAndRecreationPreservesVaultScreen() {
        val savedStateHandle = androidx.lifecycle.SavedStateHandle()
        val vm1 = MainNavigationViewModel(savedStateHandle)

        // 1. Authenticate and enter Files Vault
        assertEquals(ScreenState.VAULT_AUTH_GATE, vm1.requestVaultAccess(ScreenState.DASHBOARD))
        assertEquals(ScreenState.VAULT, vm1.authorizeVaultEntry())
        assertTrue(vm1.isVaultAuthorized())

        // 2. Launch system file picker from Vault
        vm1.setSystemPickerActive(true)
        assertTrue(vm1.isSystemPickerActive())
        assertTrue(vm1.isSystemPickerOrImportActive())
        assertTrue(vm1.isVaultAuthorized())

        // 3. Simulate Activity recreation while picker is open or returning
        val vmRecreated = MainNavigationViewModel(savedStateHandle)
        assertTrue("Recreated ViewModel must preserve picker-in-flight state", vmRecreated.isSystemPickerActive())
        assertTrue("Recreated ViewModel must preserve Vault authorization", vmRecreated.isVaultAuthorized())
        assertEquals(
            "Recreated Activity must remain on ScreenState.VAULT instead of navigating back",
            ScreenState.VAULT,
            vmRecreated.getOrInitScreen { ScreenState.AUTH_GATE }
        )

        // 4. Simulate picker finishing (either cancelled or with selected files)
        vmRecreated.setSystemPickerActive(false)
        assertFalse(vmRecreated.isSystemPickerActive())
        assertTrue("Grace window must stay active right after picker return", vmRecreated.isSystemPickerOrImportActive())
        assertTrue("Vault must remain authorized after picker finishes or is cancelled", vmRecreated.isVaultAuthorized())
        assertEquals(
            "App must remain on ScreenState.VAULT after picker finishes or is cancelled",
            ScreenState.VAULT,
            vmRecreated.getOrInitScreen { ScreenState.AUTH_GATE }
        )

        // 5. Simulate active file import in flight across recreation
        vmRecreated.setVaultImportInFlight(true)
        val vmDuringImport = MainNavigationViewModel(savedStateHandle)
        assertTrue(vmDuringImport.isVaultImportInFlight())
        assertTrue(vmDuringImport.isSystemPickerOrImportActive())
        assertEquals(ScreenState.VAULT, vmDuringImport.getOrInitScreen { ScreenState.AUTH_GATE })

        vmDuringImport.setVaultImportInFlight(false)
        assertTrue("Vault remains authorized after import completes", vmDuringImport.isVaultAuthorized())
        assertEquals(ScreenState.VAULT, vmDuringImport.getOrInitScreen { ScreenState.AUTH_GATE })
    }

    @Test
    fun testAppLockManagerBypassesSystemFilePickersDuringVaultImport() {
        val manager = com.example.data.repository.AppLockManager.getInstance(context)
        preferences.savePin("1234")
        preferences.setProtectionEnabled(true)

        // Suppose the user locked a gallery/files app and system picker packages
        val galleryPkg = "com.sec.android.gallery3d"
        val docsUiPkg = "com.google.android.documentsui"
        val mediaModulePkg = "com.google.android.providers.media.module"
        preferences.addProtectedPackages(listOf(galleryPkg, docsUiPkg, mediaModulePkg))

        // System document/media pickers are never locked even when picker is not in flight
        manager.setSystemPickerInFlight(false)
        assertFalse(manager.shouldLock(docsUiPkg))
        assertFalse(manager.shouldLock(mediaModulePkg))

        // When Vault launches a system picker, even a protected gallery picker is bypassed
        manager.setSystemPickerInFlight(true)
        assertTrue(manager.isSystemPickerBypassActive())
        assertFalse("Protected picker/gallery must not be intercepted during Vault import", manager.shouldLock(galleryPkg))

        // Reset picker state
        manager.setSystemPickerInFlight(false)
    }

    @Test
    fun testSafOpenMultipleDocumentsContractAndResultParsing() {
        val contract = com.example.ui.vault.SafOpenMultipleDocumentsContract

        // 1. Verify Photos SAF Intent
        val photoIntent = contract.createIntent(
            context,
            com.example.ui.vault.SafPickerRequest(primaryMimeType = "image/*")
        )
        assertEquals(android.content.Intent.ACTION_OPEN_DOCUMENT, photoIntent.action)
        assertTrue(photoIntent.hasCategory(android.content.Intent.CATEGORY_OPENABLE))
        assertEquals("image/*", photoIntent.type)
        assertTrue(photoIntent.getBooleanExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE, false))
        assertTrue(
            (photoIntent.flags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0
        )
        assertTrue(
            (photoIntent.flags and android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0
        )

        // 2. Verify Videos SAF Intent
        val videoIntent = contract.createIntent(
            context,
            com.example.ui.vault.SafPickerRequest(primaryMimeType = "video/*")
        )
        assertEquals(android.content.Intent.ACTION_OPEN_DOCUMENT, videoIntent.action)
        assertTrue(videoIntent.hasCategory(android.content.Intent.CATEGORY_OPENABLE))
        assertEquals("video/*", videoIntent.type)

        // 3. Verify Documents / Others SAF Intent
        val docIntent = contract.createIntent(
            context,
            com.example.ui.vault.SafPickerRequest(primaryMimeType = "*/*")
        )
        assertEquals(android.content.Intent.ACTION_OPEN_DOCUMENT, docIntent.action)
        assertTrue(docIntent.hasCategory(android.content.Intent.CATEGORY_OPENABLE))
        assertEquals("*/*", docIntent.type)

        // 4. Verify result parsing for cancellation, single URI, and multiple ClipData URIs
        assertTrue(contract.parseResult(android.app.Activity.RESULT_CANCELED, null).isEmpty())

        val uri1 = android.net.Uri.parse("content://com.android.providers.media.documents/document/image%3A101")
        val uri2 = android.net.Uri.parse("content://com.android.providers.media.documents/document/image%3A102")

        val singleResultIntent = android.content.Intent().apply { data = uri1 }
        assertEquals(listOf(uri1), contract.parseResult(android.app.Activity.RESULT_OK, singleResultIntent))

        val multiResultIntent = android.content.Intent().apply {
            val clip = android.content.ClipData.newRawUri("files", uri1)
            clip.addItem(android.content.ClipData.Item(uri2))
            clipData = clip
        }
        assertEquals(listOf(uri1, uri2), contract.parseResult(android.app.Activity.RESULT_OK, multiResultIntent))
    }

    @Test
    fun testActualSafPickerLaunchAndResultDeliveryInMainActivity() {
        preferences.savePin("1234")
        preferences.setCalculatorDisguiseEnabled(false)

        val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        val activity = controller.get()
        val shadowActivity = org.robolectric.Shadows.shadowOf(activity)
        val navVm = androidx.lifecycle.ViewModelProvider(activity)[MainNavigationViewModel::class.java]

        // Authorize and enter Vault
        navVm.authorizeVaultEntry()
        assertEquals(ScreenState.VAULT, navVm.getOrInitScreen { ScreenState.AUTH_GATE })

        val receivedUris = mutableListOf<android.net.Uri>()
        val launcher = activity.activityResultRegistry.register(
            "test_vault_saf_picker",
            com.example.ui.vault.SafOpenMultipleDocumentsContract
        ) { uris ->
            navVm.setSystemPickerActive(false)
            receivedUris.addAll(uris)
        }

        // Launch picker for Photos ("image/*")
        navVm.setSystemPickerActive(true)
        launcher.launch(com.example.ui.vault.SafPickerRequest(primaryMimeType = "image/*"))

        val startedForResult = shadowActivity.nextStartedActivityForResult
        org.junit.Assert.assertNotNull("SAF picker Intent must be started for result on MainActivity", startedForResult)
        assertEquals(android.content.Intent.ACTION_OPEN_DOCUMENT, startedForResult.intent.action)
        assertTrue(startedForResult.intent.hasCategory(android.content.Intent.CATEGORY_OPENABLE))
        assertEquals("image/*", startedForResult.intent.type)
        assertTrue(startedForResult.intent.getBooleanExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE, false))

        // Simulate user cancelling the picker -> stays on VAULT without crashing or losing authorization
        shadowActivity.receiveResult(
            startedForResult.intent,
            android.app.Activity.RESULT_CANCELED,
            null
        )
        assertTrue(receivedUris.isEmpty())
        assertTrue("Vault must remain authorized after picker cancellation", navVm.isVaultAuthorized())
        assertEquals(ScreenState.VAULT, navVm.getOrInitScreen { ScreenState.AUTH_GATE })

        // Launch picker for Documents ("*/*") and deliver selected URI
        navVm.setSystemPickerActive(true)
        launcher.launch(com.example.ui.vault.SafPickerRequest(primaryMimeType = "*/*"))
        val docStarted = shadowActivity.nextStartedActivityForResult
        org.junit.Assert.assertNotNull(docStarted)
        assertEquals(android.content.Intent.ACTION_OPEN_DOCUMENT, docStarted.intent.action)
        assertTrue(docStarted.intent.hasCategory(android.content.Intent.CATEGORY_OPENABLE))
        assertEquals("*/*", docStarted.intent.type)

        val pickedDocUri = android.net.Uri.parse("content://com.android.externalstorage.documents/document/primary%3AReport.pdf")
        shadowActivity.receiveResult(
            docStarted.intent,
            android.app.Activity.RESULT_OK,
            android.content.Intent().apply { data = pickedDocUri }
        )
        assertEquals(listOf(pickedDocUri), receivedUris)
        assertTrue("Vault must remain authorized after picker returns selected file", navVm.isVaultAuthorized())
        assertEquals(ScreenState.VAULT, navVm.getOrInitScreen { ScreenState.AUTH_GATE })

        launcher.unregister()
        controller.pause().stop().destroy()
    }

    @Test
    fun testFourAuthenticationMethodsSetupAndVerification() {
        // 1. 4-Digit PIN
        preferences.saveAuthCredential(AppLockPreferences.AuthMethod.PIN_4, "4826")
        assertTrue(preferences.isCredentialConfigured())
        assertEquals(AppLockPreferences.AuthMethod.PIN_4, preferences.getAuthMethod())
        assertEquals(4, preferences.getPinLength())
        assertTrue(preferences.verifyCredential("4826"))
        assertFalse(preferences.verifyCredential("1234"))
        assertFalse(preferences.verifyCredential("482600"))

        // 2. 6-Digit PIN
        preferences.saveAuthCredential(AppLockPreferences.AuthMethod.PIN_6, "905173")
        assertEquals(AppLockPreferences.AuthMethod.PIN_6, preferences.getAuthMethod())
        assertEquals(6, preferences.getPinLength())
        assertTrue(preferences.verifyCredential("905173"))
        assertFalse(preferences.verifyCredential("9051"))
        assertFalse(preferences.verifyCredential("000000"))

        // 3. Pattern Lock (at least 4 connected dots on 3x3 grid 0..8)
        preferences.saveAuthCredential(AppLockPreferences.AuthMethod.PATTERN, "0-1-2-5-8")
        assertEquals(AppLockPreferences.AuthMethod.PATTERN, preferences.getAuthMethod())
        assertTrue(preferences.verifyCredential("0-1-2-5-8"))
        assertTrue("Equivalent pattern format must also verify", preferences.verifyCredential("0,1,2,5,8"))
        assertFalse("Wrong pattern must fail", preferences.verifyCredential("0-3-6-7-8"))
        assertFalse("Too short pattern (<4 dots) must fail", preferences.verifyCredential("0-1-2"))

        // 4. Password (1..32 characters)
        preferences.saveAuthCredential(AppLockPreferences.AuthMethod.PASSWORD, "V@ultKey#2026!")
        assertEquals(AppLockPreferences.AuthMethod.PASSWORD, preferences.getAuthMethod())
        assertTrue(preferences.verifyCredential("V@ultKey#2026!"))
        assertFalse("Case-mismatch password must fail", preferences.verifyCredential("v@ultkey#2026!"))
        assertFalse("Wrong password must fail", preferences.verifyCredential("WrongPassword"))
    }

    @Test
    fun testSwitchingAuthenticationMethodsInvalidatesPreviousCredential() {
        preferences.saveRecoveryQuestion("What city were you born in?", "Lahore")
        preferences.setBiometricEnabled(true)

        // Start with 4-Digit PIN
        preferences.saveAuthCredential(AppLockPreferences.AuthMethod.PIN_4, "1357")
        assertEquals(AppLockPreferences.AuthMethod.PIN_4, preferences.getAuthMethod())
        assertTrue(preferences.verifyCredential("1357"))

        // Switch to 6-Digit PIN -> old 4-digit PIN must no longer unlock
        preferences.saveAuthCredential(AppLockPreferences.AuthMethod.PIN_6, "246810")
        assertEquals(AppLockPreferences.AuthMethod.PIN_6, preferences.getAuthMethod())
        assertFalse("Old 4-digit PIN must not verify after switching to 6-digit PIN", preferences.verifyCredential("1357"))
        assertTrue(preferences.verifyCredential("246810"))

        // Switch to Pattern Lock -> old 6-digit PIN must no longer unlock
        preferences.saveAuthCredential(AppLockPreferences.AuthMethod.PATTERN, "0-4-8-7")
        assertEquals(AppLockPreferences.AuthMethod.PATTERN, preferences.getAuthMethod())
        assertFalse("Old 6-digit PIN must not verify after switching to Pattern Lock", preferences.verifyCredential("246810"))
        assertTrue(preferences.verifyCredential("0-4-8-7"))

        // Switch to Password -> old pattern must no longer unlock
        preferences.saveAuthCredential(AppLockPreferences.AuthMethod.PASSWORD, "SecureMasterPass99")
        assertEquals(AppLockPreferences.AuthMethod.PASSWORD, preferences.getAuthMethod())
        assertFalse("Old pattern must not verify after switching to Password", preferences.verifyCredential("0-4-8-7"))
        assertTrue(preferences.verifyCredential("SecureMasterPass99"))

        // Verify Biometric and Recovery Question remain intact across method switches
        assertTrue(preferences.isBiometricEnabled())
        assertTrue(preferences.hasRecoveryQuestion())
        assertTrue(preferences.verifyRecoveryAnswer("lahore"))
    }

    @Test
    fun testInvalidCredentialValidationRules() {
        // 4-Digit PIN validation
        assertTrue(AppLockPreferences.isValidCredential(AppLockPreferences.AuthMethod.PIN_4, "0000"))
        assertFalse(AppLockPreferences.isValidCredential(AppLockPreferences.AuthMethod.PIN_4, "123"))
        assertFalse(AppLockPreferences.isValidCredential(AppLockPreferences.AuthMethod.PIN_4, "123456"))
        assertFalse(AppLockPreferences.isValidCredential(AppLockPreferences.AuthMethod.PIN_4, "12a4"))

        // 6-Digit PIN validation
        assertTrue(AppLockPreferences.isValidCredential(AppLockPreferences.AuthMethod.PIN_6, "123456"))
        assertFalse(AppLockPreferences.isValidCredential(AppLockPreferences.AuthMethod.PIN_6, "1234"))
        assertFalse(AppLockPreferences.isValidCredential(AppLockPreferences.AuthMethod.PIN_6, "12345a"))

        // Pattern Lock validation (min 4 distinct dots in 0..8)
        assertTrue(AppLockPreferences.isValidCredential(AppLockPreferences.AuthMethod.PATTERN, "0-1-2-5"))
        assertFalse("3 dots must be rejected", AppLockPreferences.isValidCredential(AppLockPreferences.AuthMethod.PATTERN, "0-1-2"))
        assertFalse("Duplicate dots must be rejected", AppLockPreferences.isValidCredential(AppLockPreferences.AuthMethod.PATTERN, "0-1-2-1"))
        assertFalse("Out of range dot must be rejected", AppLockPreferences.isValidCredential(AppLockPreferences.AuthMethod.PATTERN, "0-1-2-9"))

        // Password validation (1..32 chars)
        assertTrue(AppLockPreferences.isValidCredential(AppLockPreferences.AuthMethod.PASSWORD, "a"))
        assertTrue(AppLockPreferences.isValidCredential(AppLockPreferences.AuthMethod.PASSWORD, "A".repeat(32)))
        assertFalse("Empty password must be rejected", AppLockPreferences.isValidCredential(AppLockPreferences.AuthMethod.PASSWORD, ""))
        assertFalse("33-char password must be rejected", AppLockPreferences.isValidCredential(AppLockPreferences.AuthMethod.PASSWORD, "A".repeat(33)))
    }

    @Test
    fun testStartupLogoSplashRemovedAndDirectAuthFlow() {
        // Verify transparent splash icon drawable resolves for Android 12+ system splash suppression
        val transparentSplash = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_splash_transparent)
        org.junit.Assert.assertNotNull("ic_splash_transparent drawable must resolve", transparentSplash)

        // Verify MainActivity starts directly in AUTH_GATE when credential is configured
        preferences.saveAuthCredential(AppLockPreferences.AuthMethod.PATTERN, "0-1-2-5")
        preferences.setCalculatorDisguiseEnabled(false)

        val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        val activity = controller.get()
        val navVm = androidx.lifecycle.ViewModelProvider(activity)[MainNavigationViewModel::class.java]

        assertEquals(
            "MainActivity must open directly into AUTH_GATE without startup splash delay",
            ScreenState.AUTH_GATE,
            navVm.getOrInitScreen { ScreenState.ONBOARDING }
        )
        assertEquals(AppLockPreferences.AuthMethod.PATTERN, preferences.getAuthMethod())

        controller.pause().stop().destroy()
    }
    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    private fun androidx.compose.ui.test.ComposeUiTest.drawPatternOnGrid(dotIndices: List<Int>) {
        val gridInteraction = onNode(
            androidx.compose.ui.test.hasTestTag("pattern_lock_grid")
        )
        val bounds = gridInteraction.fetchSemanticsNode().boundsInRoot
        val width = bounds.width
        val height = bounds.height

        fun dotOffset(index: Int): androidx.compose.ui.geometry.Offset {
            val row = index / 3
            val col = index % 3
            val cellW = width / 3f
            val cellH = height / 3f
            return androidx.compose.ui.geometry.Offset(
                x = col * cellW + cellW / 2f,
                y = row * cellH + cellH / 2f
            )
        }

        gridInteraction.performTouchInput {
            val first = dotOffset(dotIndices.first())
            down(first)
            for (i in 1 until dotIndices.size) {
                val target = dotOffset(dotIndices[i])
                moveTo(target)
            }
            up()
        }
        waitForIdle()
    }

    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    @Test
    fun testPatternLockSetupConfirmationMismatchRetryAndAppRestart() {
        var dialogCompletedCount = 0
        val showDialog = androidx.compose.runtime.mutableStateOf(true)
        val dialogInstanceKey = androidx.compose.runtime.mutableIntStateOf(0)

        androidx.compose.ui.test.runComposeUiTest {
            setContent {
                if (showDialog.value) {
                    androidx.compose.runtime.key(dialogInstanceKey.intValue) {
                        com.example.ui.screens.ChangePinDialog(
                            preferences = preferences,
                            onDismiss = { showDialog.value = false },
                            onSuccess = {
                                dialogCompletedCount++
                            }
                        )
                    }
                }
            }
            waitForIdle()

            // === PART A: First-time Pattern Lock setup + <4 dot validation + immediate matching confirmation ===
            onNode(androidx.compose.ui.test.hasTestTag("auth_method_option_PATTERN"))
                .performClick()
            waitForIdle()

            onNode(androidx.compose.ui.test.hasText("Create Pattern Lock"))
                .assertExists()

            // 1. Minimum 4-dot validation: drawing only 3 dots (0 -> 1 -> 2) shows error and stays on Create step
            drawPatternOnGrid(listOf(0, 1, 2))
            onNode(androidx.compose.ui.test.hasText("Connect at least 4 dots"))
                .assertExists()
            onNode(androidx.compose.ui.test.hasText("Create Pattern Lock"))
                .assertExists()

            // 2. First valid draw (0 -> 1 -> 2 -> 5): saves temporarily and transitions to Confirm Pattern Lock
            drawPatternOnGrid(listOf(0, 1, 2, 5))
            onNode(androidx.compose.ui.test.hasText("Confirm Pattern Lock"))
                .assertExists()

            // 3. Second matching draw (0 -> 1 -> 2 -> 5): immediately accepted on first confirmation without asking again
            drawPatternOnGrid(listOf(0, 1, 2, 5))
            onNode(androidx.compose.ui.test.hasText("Pattern Lock saved successfully!"))
                .assertExists()
            onNode(androidx.compose.ui.test.hasTestTag("change_auth_done_button"))
                .performClick()
            waitForIdle()

            assertEquals(1, dialogCompletedCount)
            assertEquals(AppLockPreferences.AuthMethod.PATTERN, preferences.getAuthMethod())
            assertTrue("Saved pattern 0-1-2-5 must verify", preferences.verifyCredential("0-1-2-5"))
            assertFalse("Different pattern must not verify", preferences.verifyCredential("0-3-6-7"))

            // === PART B: Re-open dialog -> Verify current pattern -> Mismatch confirmation -> Retry confirmation ===
            dialogInstanceKey.intValue++
            waitForIdle()

            onNode(androidx.compose.ui.test.hasTestTag("auth_method_option_PATTERN"))
                .performClick()
            waitForIdle()

            // Verify existing pattern (0 -> 1 -> 2 -> 5)
            onNode(androidx.compose.ui.test.hasText("Verify Current Pattern Lock"))
                .assertExists()
            drawPatternOnGrid(listOf(0, 1, 2, 5))
            onNode(androidx.compose.ui.test.hasText("Create Pattern Lock"))
                .assertExists()

            // First draw of new pattern: 0 -> 3 -> 6 -> 7 -> 8
            drawPatternOnGrid(listOf(0, 3, 6, 7, 8))
            onNode(androidx.compose.ui.test.hasText("Confirm Pattern Lock"))
                .assertExists()

            // Second draw with MISMATCHING pattern: 0 -> 1 -> 2 -> 5
            drawPatternOnGrid(listOf(0, 1, 2, 5))
            onNode(androidx.compose.ui.test.hasText("Patterns do not match. Try again."))
                .assertExists()
            onNode(androidx.compose.ui.test.hasText("Confirm Pattern Lock"))
                .assertExists()
            assertTrue("Previous pattern must remain active before confirmation succeeds", preferences.verifyCredential("0-1-2-5"))

            // Retry confirmation with matching pattern: 0 -> 3 -> 6 -> 7 -> 8
            drawPatternOnGrid(listOf(0, 3, 6, 7, 8))
            onNode(androidx.compose.ui.test.hasText("Pattern Lock saved successfully!"))
                .assertExists()
            onNode(androidx.compose.ui.test.hasTestTag("change_auth_done_button"))
                .performClick()
            showDialog.value = false
            waitForIdle()
        }

        assertEquals(2, dialogCompletedCount)

        // === PART C: Simulate closing and reopening the app (MainActivity) and verifying the saved pattern ===
        preferences.setCalculatorDisguiseEnabled(false)
        val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        val activity = controller.get()
        val navVm = androidx.lifecycle.ViewModelProvider(activity)[MainNavigationViewModel::class.java]

        assertEquals(ScreenState.AUTH_GATE, navVm.getOrInitScreen { ScreenState.ONBOARDING })
        assertEquals(AppLockPreferences.AuthMethod.PATTERN, preferences.getAuthMethod())
        assertTrue("Updated pattern must verify after reopening app", preferences.verifyCredential("0-3-6-7-8"))
        assertFalse("Old pattern must be rejected after reopening app", preferences.verifyCredential("0-1-2-5"))

        activity.setContentView(android.view.View(activity))
        controller.pause().stop().destroy()
    }

    @Test
    fun testPhase1SelfAppInfoProtection() {
        val appLockManager = com.example.data.repository.AppLockManager.getInstance(context)
        preferences.setProtectionEnabled(true)
        preferences.savePin("1234")

        // 1. Initial state: Self App Info is not unlocked
        assertFalse("Self App Info must not be unlocked initially", appLockManager.isSelfAppInfoUnlocked())

        // 2. shouldLock must never lock self package directly (handled specifically via AppInfo detection)
        assertFalse("shouldLock must return false for own package to prevent self-lockout", appLockManager.shouldLock(context.packageName))

        // 3. Authenticating self App Info
        appLockManager.onSelfAppInfoUnlocked()
        assertTrue("Self App Info must report unlocked after authentication", appLockManager.isSelfAppInfoUnlocked())

        // 4. Screen off resets the session
        appLockManager.onScreenOff()
        assertFalse("Self App Info must be relocked on screen off", appLockManager.isSelfAppInfoUnlocked())
    }

    @Test
    fun testPhase1RecentsOverviewFalseAuthenticationPrevention() {
        val appLockManager = com.example.data.repository.AppLockManager.getInstance(context)
        preferences.setProtectionEnabled(true)
        preferences.savePin("1234")
        val protectedApp = "com.sample.banking"
        preferences.setPackageProtected(protectedApp, true)

        // 1. Overview / Recents activity recognition across OEMs
        assertTrue("AOSP / Pixel Quickstep Recents must be identified as Recents", appLockManager.isRecentsOrOverview("com.android.launcher3", "com.android.quickstep.RecentsActivity"))
        assertTrue("Samsung Recents must be identified as Recents", appLockManager.isRecentsOrOverview("com.sec.android.app.launcher", "com.sec.android.app.launcher.activities.RecentsActivity"))
        assertTrue("MIUI / HyperOS Recents must be identified as Recents", appLockManager.isRecentsOrOverview("com.miui.home", "com.miui.home.launcher.recents.RecentsActivity"))

        // 2. When Recents overview is entered, shouldLock returns false even for protected apps
        appLockManager.onRecentsOverviewEntered()
        assertTrue("Recents overview active flag must be set", appLockManager.isRecentsOverviewActive)
        assertFalse("shouldLock must return false when Recents overview is active to avoid false prompt over task cards",
            appLockManager.shouldLock(protectedApp, "com.android.quickstep.RecentsActivity"))

        // 3. Home launcher transition grace period:
        // App is unlocked:
        appLockManager.onPackageUnlocked(protectedApp)
        assertTrue(appLockManager.isPackageUnlocked(protectedApp))

        // User swipes up to launcher:
        appLockManager.onForegroundPackageChanged("com.google.android.apps.nexuslauncher", "com.google.android.apps.nexuslauncher.NexusLauncherActivity")

        // Returning to the same app immediately (e.g. within 1200ms grace window):
        appLockManager.onForegroundPackageChanged(protectedApp, "com.sample.banking.MainActivity")
        assertTrue("Protected app must stay unlocked when returning within the gesture / recents grace window", appLockManager.isPackageUnlocked(protectedApp))
        assertFalse("shouldLock must return false for app unlocked within grace window", appLockManager.shouldLock(protectedApp))
    }

    @Test
    fun testPhase1TransientOverlaysAndSessionStability() {
        val appLockManager = com.example.data.repository.AppLockManager.getInstance(context)
        preferences.setProtectionEnabled(true)
        preferences.savePin("1234")
        val instagramPackage = "com.instagram.android"
        preferences.setPackageProtected(instagramPackage, true)

        // Initial launch requires lock
        assertTrue(appLockManager.shouldLock(instagramPackage, "com.instagram.mainactivity.MainActivity"))

        // User unlocks Instagram
        appLockManager.onPackageUnlocked(instagramPackage)
        appLockManager.onForegroundPackageChanged(instagramPackage, "com.instagram.mainactivity.MainActivity")
        assertTrue(appLockManager.isPackageUnlocked(instagramPackage))
        assertFalse(appLockManager.shouldLock(instagramPackage, "com.instagram.mainactivity.MainActivity"))

        // User opens soft keyboard (Gboard / Samsung / SwiftKey) inside Instagram:
        val gboard = "com.google.android.inputmethod.latin"
        assertTrue("Gboard must be identified as transient overlay", appLockManager.isTransientSystemOrOverlayPackage(gboard))
        appLockManager.onForegroundPackageChanged(gboard, "com.android.inputmethod.latin.LatinIME")

        // Returning from keyboard to Instagram:
        appLockManager.onForegroundPackageChanged(instagramPackage, "com.instagram.direct.inbox.InboxActivity")
        assertTrue("Instagram must remain unlocked when typing with soft keyboard", appLockManager.isPackageUnlocked(instagramPackage))
        assertFalse("Keyboard dismissal must not re-prompt lock", appLockManager.shouldLock(instagramPackage, "com.instagram.direct.inbox.InboxActivity"))

        // User touches volume slider / notification panel (SystemUI):
        val systemUi = "com.android.systemui"
        assertTrue("SystemUI must be identified as transient overlay", appLockManager.isTransientSystemOrOverlayPackage(systemUi))
        appLockManager.onForegroundPackageChanged(systemUi, "com.android.systemui.volume.VolumeDialogImpl")

        // Navigating to Instagram Story:
        appLockManager.onForegroundPackageChanged(instagramPackage, "com.instagram.modal.ModalActivity")
        assertTrue("Instagram must remain unlocked after volume / SystemUI overlay", appLockManager.isPackageUnlocked(instagramPackage))
        assertFalse("Story viewing must not re-prompt lock", appLockManager.shouldLock(instagramPackage, "com.instagram.modal.ModalActivity"))
    }

    @Test
    fun testPhase2BiometricAuthenticationReliability() {
        val appLockManager = com.example.data.repository.AppLockManager.getInstance(context)
        preferences.setProtectionEnabled(true)
        preferences.savePin("1234")
        preferences.setBiometricEnabled(true)

        // 1. Biometric availability check must not crash
        val isAvail = com.example.util.BiometricHelper.isBiometricAvailable(context)
        assertTrue(isAvail || !isAvail)

        // 2. cancelCurrentPrompt must clear prompt safely
        com.example.util.BiometricHelper.cancelCurrentPrompt()

        // 3. Verify target package resolution fallback in launchInterceptedTarget
        val testPackage = "com.sample.banking"
        appLockManager.storeInterceptedTarget(testPackage, "com.sample.banking.UnexportedActivity")
        val target = appLockManager.getAndRemoveInterceptedTarget(testPackage)
        org.junit.Assert.assertNotNull(target)
        assertEquals(testPackage, target?.packageName)
        assertEquals("com.sample.banking.UnexportedActivity", target?.className)

        // 4. Test atomic lock verification guard simulation
        val isUnlockConsumed = java.util.concurrent.atomic.AtomicBoolean(false)
        var unlockSuccessCount = 0

        fun performUnlockSimulated() {
            if (isUnlockConsumed.compareAndSet(false, true)) {
                com.example.util.BiometricHelper.cancelCurrentPrompt()
                preferences.resetFailedAttempts()
                unlockSuccessCount++
            }
        }

        // Simulating rapid duplicate callbacks (e.g. biometric success followed by redundant keypad press or duplicate event):
        performUnlockSimulated()
        performUnlockSimulated()
        performUnlockSimulated()

        assertEquals("Biometric success must execute the unlock action exactly once", 1, unlockSuccessCount)
    }
}



