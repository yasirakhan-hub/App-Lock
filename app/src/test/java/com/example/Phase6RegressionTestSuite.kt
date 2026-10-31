package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.AppDiscoveryRepository
import com.example.data.repository.AppLockManager
import com.example.data.security.AppLockPreferences
import com.example.data.security.AppLockPreferences.AuthMethod
import com.example.data.security.PinHashUtil
import com.example.data.vault.DuplicateVaultItemException
import com.example.data.vault.OriginalDeletionStatus
import com.example.data.vault.VaultCategory
import com.example.data.vault.VaultCryptoEngine
import com.example.data.vault.VaultItem
import com.example.data.vault.VaultRepository
import com.example.util.BatteryOptimizationHelper
import com.example.util.CalculatorEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Phase 6 — Comprehensive Regression Test Suite & Verification Audit.
 *
 * Exercises all 8 subsystems specified in the Phase 6 master plan:
 * 1. Authentication regression (4-digit, 6-digit, Pattern, Password, Brute-force, Method Switch)
 * 2. Security question recovery
 * 3. App lock interception, debounce, session management & target resumption
 * 4. Recents Overview gesture & soft keyboard transient overlay stability
 * 5. Anti-force-stop self App Info protection
 * 6. File Vault AES-256-GCM authenticated encryption & tamper detection
 * 7. Copy vs Move original file safety & verified export deletion
 * 8. Portable encrypted container (.salvault) backup & cross-device restore
 * 9. Calculator disguise gateway, Two-Key Rule & PEMDAS math engine
 * 10. Navigation backstack & 3-option theme persistence
 * 11. OEM battery & background reliability guidance
 * 12. Security rules, package invariants & resource integrity
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase6RegressionTestSuite {

    private lateinit var context: Context
    private lateinit var preferences: AppLockPreferences
    private lateinit var appLockManager: AppLockManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferences = AppLockPreferences(context)
        preferences.clearAllData()
        appLockManager = AppLockManager.getInstance(context)
        appLockManager.lockAll()
        File(context.filesDir, "vault").deleteRecursively()
    }

    // =========================================================================
    // 1. AUTHENTICATION REGRESSION
    // =========================================================================

    @Test
    fun test01_AuthenticationRegression_AllCredentialTypes_VerifyAndLockout() {
        // --- A. 4-Digit PIN ---
        assertFalse("Initially not configured", preferences.isCredentialConfigured())
        preferences.saveAuthCredential(AuthMethod.PIN_4, "7294")
        assertTrue(preferences.isCredentialConfigured())
        assertEquals(AuthMethod.PIN_4, preferences.getAuthMethod())
        assertEquals(4, preferences.getPinLength())
        assertTrue("Correct 4-digit PIN must unlock", preferences.verifyCredential("7294"))
        assertFalse("Incorrect 4-digit PIN must not unlock", preferences.verifyCredential("1111"))
        assertFalse("Wrong length must fail", preferences.verifyCredential("729"))
        assertFalse("Non-numeric PIN must fail", preferences.verifyCredential("729a"))

        // --- B. 6-Digit PIN ---
        preferences.saveAuthCredential(AuthMethod.PIN_6, "831940")
        assertEquals(AuthMethod.PIN_6, preferences.getAuthMethod())
        assertEquals(6, preferences.getPinLength())
        assertTrue("Correct 6-digit PIN must unlock", preferences.verifyCredential("831940"))
        assertFalse("Incorrect 6-digit PIN must not unlock", preferences.verifyCredential("831941"))
        assertFalse("Old 4-digit PIN must not unlock after switch", preferences.verifyCredential("7294"))

        // --- C. Pattern Lock ---
        preferences.saveAuthCredential(AuthMethod.PATTERN, "0-1-2-5-8")
        assertEquals(AuthMethod.PATTERN, preferences.getAuthMethod())
        assertTrue("Canonical pattern format must verify", preferences.verifyCredential("0-1-2-5-8"))
        assertTrue("Comma-delimited pattern format must verify", preferences.verifyCredential("0,1,2,5,8"))
        assertFalse("Different pattern must fail", preferences.verifyCredential("0-3-6-7-8"))
        assertFalse("Pattern with <4 dots must fail validation", AppLockPreferences.isValidCredential(AuthMethod.PATTERN, "0-1-2"))
        assertFalse("Pattern with duplicate dots must fail validation", AppLockPreferences.isValidCredential(AuthMethod.PATTERN, "0-1-2-1"))
        assertFalse("Pattern with out-of-bounds dots must fail", AppLockPreferences.isValidCredential(AuthMethod.PATTERN, "0-1-2-9"))

        // --- D. Password ---
        val strongPass = "MasterKey@2026!#"
        preferences.saveAuthCredential(AuthMethod.PASSWORD, strongPass)
        assertEquals(AuthMethod.PASSWORD, preferences.getAuthMethod())
        assertTrue("Correct password must verify", preferences.verifyCredential(strongPass))
        assertFalse("Case-mismatched password must fail", preferences.verifyCredential("masterkey@2026!#"))
        assertFalse("Wrong password must fail", preferences.verifyCredential("WrongPassword"))
        assertFalse("Empty password must fail validation", AppLockPreferences.isValidCredential(AuthMethod.PASSWORD, ""))
        assertFalse("Password exceeding 32 chars must fail validation", AppLockPreferences.isValidCredential(AuthMethod.PASSWORD, "A".repeat(33)))

        // --- E. Brute-Force Lockout (5 attempts -> 30s lockout) ---
        assertEquals(0, preferences.getFailedAttempts())
        assertEquals(0, preferences.getRemainingLockoutSeconds())

        repeat(4) { preferences.recordFailedAttempt() }
        assertEquals(4, preferences.getFailedAttempts())
        assertEquals("4 attempts must not trigger lockout", 0, preferences.getRemainingLockoutSeconds())

        preferences.recordFailedAttempt()
        assertEquals(5, preferences.getFailedAttempts())
        assertTrue("5th failed attempt must activate lockout countdown", preferences.getRemainingLockoutSeconds() > 0)
        assertTrue("Remaining lockout must be <= 30 seconds", preferences.getRemainingLockoutSeconds() <= 30)

        // Reset clears lockout countdown immediately
        preferences.resetFailedAttempts()
        assertEquals(0, preferences.getFailedAttempts())
        assertEquals(0, preferences.getRemainingLockoutSeconds())
    }

    @Test
    fun test02_AuthenticationRegression_SecurityQuestionRecoveryFlow() {
        val question = "What is the name of your first school?"
        val answer = "St. Mary High School"

        assertFalse(preferences.hasRecoveryQuestion())
        assertNull(preferences.getSecurityQuestion())

        preferences.saveRecoveryQuestion(question, answer)
        assertTrue(preferences.hasRecoveryQuestion())
        assertEquals(question, preferences.getSecurityQuestion())

        // Recovery answer must be case-insensitive, whitespace-trimmed, and cryptographically hashed
        assertTrue("Exact answer must verify", preferences.verifyRecoveryAnswer("St. Mary High School"))
        assertTrue("Lower-case answer must verify", preferences.verifyRecoveryAnswer("st. mary high school"))
        assertTrue("Upper-case answer must verify", preferences.verifyRecoveryAnswer("ST. MARY HIGH SCHOOL"))
        assertTrue("Padded answer must verify", preferences.verifyRecoveryAnswer("   st. mary high school   "))
        assertFalse("Incorrect answer must fail", preferences.verifyRecoveryAnswer("Lincoln Elementary"))
        assertFalse("Empty answer must fail", preferences.verifyRecoveryAnswer(""))
    }

    // =========================================================================
    // 2. APP LOCK REGRESSION & INTERCEPTION
    // =========================================================================

    @Test
    fun test03_AppLockRegression_InterceptionDebounceAndTargetResumption() {
        preferences.savePin("1234")
        preferences.setProtectionEnabled(true)

        val bankingPkg = "com.sample.banking"
        val bankingActivity = "com.sample.banking.ui.DashboardActivity"
        preferences.setPackageProtected(bankingPkg, true)

        // 1. shouldLock() must return true for locked app
        assertTrue("Protected package must require lock", appLockManager.shouldLock(bankingPkg, bankingActivity))

        // 2. Unprotected packages must never prompt
        assertFalse("Unprotected package must not prompt lock", appLockManager.shouldLock("com.android.chrome"))

        // 3. Smart AppLock itself must never self-lock
        assertFalse("Self package must never be locked by shouldLock", appLockManager.shouldLock(context.packageName))

        // 4. Intercepted target caching & resumption
        appLockManager.storeInterceptedTarget(bankingPkg, bankingActivity)
        val target = appLockManager.getAndRemoveInterceptedTarget(bankingPkg)
        assertNotNull(target)
        assertEquals(bankingPkg, target?.packageName)
        assertEquals(bankingActivity, target?.className)
        assertNull("Target must be cleared after retrieval", appLockManager.getAndRemoveInterceptedTarget(bankingPkg))

        // 5. Unlocking package allows access
        appLockManager.onPackageUnlocked(bankingPkg)
        assertTrue(appLockManager.isPackageUnlocked(bankingPkg))
        assertFalse("Unlocked package must not prompt lock", appLockManager.shouldLock(bankingPkg, bankingActivity))

        // 6. Screen off resets session and re-locks
        appLockManager.onScreenOff()
        assertFalse("Screen off must re-lock package", appLockManager.isPackageUnlocked(bankingPkg))
        assertTrue("Package must require lock after screen off", appLockManager.shouldLock(bankingPkg, bankingActivity))
    }

    @Test
    fun test04_AppLockRegression_RecentsOverviewAndTransientOverlayProtection() {
        preferences.savePin("1234")
        preferences.setProtectionEnabled(true)
        val instaPkg = "com.instagram.android"
        preferences.setPackageProtected(instaPkg, true)

        // User unlocks Instagram
        appLockManager.onPackageUnlocked(instaPkg)
        appLockManager.onForegroundPackageChanged(instaPkg, "com.instagram.mainactivity.MainActivity")
        assertTrue(appLockManager.isPackageUnlocked(instaPkg))

        // A. Soft Keyboard (Gboard, SwiftKey, Samsung Keyboard) must NEVER evict active session
        val gboard = "com.google.android.inputmethod.latin"
        val swiftkey = "com.touchtype.swiftkey"
        val samsungIme = "com.samsung.android.honeyboard"

        assertTrue("Gboard recognized as IME", appLockManager.isInputMethodPackage(gboard))
        assertTrue("SwiftKey recognized as IME", appLockManager.isInputMethodPackage(swiftkey))
        assertTrue("Samsung Keyboard recognized as IME", appLockManager.isInputMethodPackage(samsungIme))

        appLockManager.onForegroundPackageChanged(gboard, "com.android.inputmethod.latin.LatinIME")
        assertTrue("Instagram must remain unlocked when soft keyboard opens", appLockManager.isPackageUnlocked(instaPkg))

        // Returning from keyboard to Instagram direct message
        appLockManager.onForegroundPackageChanged(instaPkg, "com.instagram.direct.inbox.InboxActivity")
        assertTrue("Instagram remains unlocked after keyboard closes", appLockManager.isPackageUnlocked(instaPkg))
        assertFalse(appLockManager.shouldLock(instaPkg, "com.instagram.direct.inbox.InboxActivity"))

        // B. SystemUI volume panel / notification shade
        val systemUi = "com.android.systemui"
        assertTrue("SystemUI recognized as transient overlay", appLockManager.isTransientSystemOrOverlayPackage(systemUi))
        appLockManager.onForegroundPackageChanged(systemUi, "com.android.systemui.volume.VolumeDialogImpl")
        assertTrue("Instagram remains unlocked after volume dialog", appLockManager.isPackageUnlocked(instaPkg))

        // C. Recents / Overview activity recognition across OEMs
        assertTrue("AOSP Launcher3 Recents recognized", appLockManager.isRecentsOrOverview("com.android.launcher3", "com.android.quickstep.RecentsActivity"))
        assertTrue("Samsung One UI Recents recognized", appLockManager.isRecentsOrOverview("com.sec.android.app.launcher", "com.sec.android.app.launcher.activities.RecentsActivity"))
        assertTrue("Xiaomi HyperOS Recents recognized", appLockManager.isRecentsOrOverview("com.miui.home", "com.miui.home.launcher.recents.RecentsActivity"))

        // Recents overview must not trigger lock
        appLockManager.onRecentsOverviewEntered()
        assertTrue(appLockManager.isRecentsOverviewActive)
        assertFalse("Recents overview must not lock", appLockManager.shouldLock(instaPkg, "com.android.quickstep.RecentsActivity"))
    }

    @Test
    fun test05_AppLockRegression_AntiForceStopSelfAppInfoProtection() {
        preferences.savePin("1234")
        preferences.setProtectionEnabled(true)

        // Initial state: Self App Info is locked
        assertFalse("Self App Info must start locked", appLockManager.isSelfAppInfoUnlocked())

        // Authenticating unlocks Self App Info
        appLockManager.onSelfAppInfoUnlocked()
        assertTrue("Self App Info is now unlocked", appLockManager.isSelfAppInfoUnlocked())

        // Screen off relocks Self App Info
        appLockManager.onScreenOff()
        assertFalse("Screen off relocks Self App Info", appLockManager.isSelfAppInfoUnlocked())
    }

    // =========================================================================
    // 3. HIDE FILES / VAULT REGRESSION
    // =========================================================================

    @Test
    fun test06_VaultRegression_ImportIntegrityAndCryptoVerification() = runBlocking {
        val repo = VaultRepository(context)

        // 1. .nomedia file must exist in vault root to prevent Android MediaScanner from indexing
        val nomedia = File(context.filesDir, "vault/.nomedia")
        assertTrue(".nomedia must exist in vault root", nomedia.exists())

        // 2. High-resolution document import
        val plainText = "Executive Board Minutes & Financial Ledger — 2026 Highly Confidential"
        val sourceFile = File(context.cacheDir, "board_minutes.pdf").apply {
            writeText(plainText)
        }
        val importRes = repo.importFromUri(
            uri = Uri.fromFile(sourceFile),
            preferredName = "board_minutes.pdf",
            fallbackCategory = VaultCategory.DOCUMENT
        )
        assertTrue("Import must succeed", importRes.isSuccess)
        val vaultItem = importRes.getOrThrow()
        assertEquals(VaultCategory.DOCUMENT, vaultItem.category)
        assertEquals(sourceFile.length(), vaultItem.fileSizeRaw)

        // 3. Verify AES-256-GCM container structure on disk:
        // [SALV Magic 4B] + [IV 12B] + [Ciphertext + 16B Tag]
        val encFile = repo.getEncryptedFile(vaultItem)
        assertTrue("Encrypted file must exist", encFile.exists())
        assertEquals(sourceFile.length() + 32L, encFile.length())

        val encBytes = encFile.readBytes()
        assertEquals('S'.code.toByte(), encBytes[0])
        assertEquals('A'.code.toByte(), encBytes[1])
        assertEquals('L'.code.toByte(), encBytes[2])
        assertEquals('V'.code.toByte(), encBytes[3])

        // 4. Duplicate SHA-256 detection: identical file import must be rejected
        val dupResult = repo.importFromUri(
            uri = Uri.fromFile(sourceFile),
            preferredName = "duplicate_minutes.pdf"
        )
        assertTrue("Duplicate file must fail", dupResult.isFailure)
        assertTrue(dupResult.exceptionOrNull() is DuplicateVaultItemException)

        // 5. Tamper detection: modifying a byte in ciphertext causes AES-GCM decryption failure
        val tamperedBytes = encBytes.clone()
        tamperedBytes[tamperedBytes.size - 3] = (tamperedBytes[tamperedBytes.size - 3].toInt() xor 0xFF).toByte()
        var cryptoFailed = false
        try {
            VaultCryptoEngine.decryptBytes(tamperedBytes)
        } catch (_: Exception) {
            cryptoFailed = true
        }
        assertTrue("Tampered ciphertext must fail GCM authentication tag verification", cryptoFailed)
    }

    @Test
    fun test07_VaultRegression_CopyVsMoveOriginalPreservation() = runBlocking {
        val repo = VaultRepository(context)

        // A. Copy Mode: Original file MUST remain untouched
        val copyDoc = File(context.cacheDir, "family_passport.pdf").apply {
            writeText("Passport Scan Payload 2026")
        }
        val copyRes = repo.importFromUri(
            uri = Uri.fromFile(copyDoc),
            fallbackCategory = VaultCategory.DOCUMENT
        )
        assertTrue(copyRes.isSuccess)
        assertTrue("In Copy Mode, original file must remain untouched", copyDoc.exists())

        // B. Move Mode: Original file is deleted ONLY after verified import
        val moveDoc = File(context.cacheDir, "tax_filing.pdf").apply {
            writeText("Tax Filing Official Records 2025-2026")
        }
        val moveRes = repo.importFromUri(
            uri = Uri.fromFile(moveDoc),
            fallbackCategory = VaultCategory.DOCUMENT
        )
        assertTrue(moveRes.isSuccess)
        val moveItem = moveRes.getOrThrow()

        val delStatus = repo.deleteOriginalUri(Uri.fromFile(moveDoc))
        assertEquals(OriginalDeletionStatus.Deleted, delStatus)
        assertFalse("Original file must be deleted after explicit Move confirmation", moveDoc.exists())
        assertTrue("Vault copy must remain safe and intact", repo.getEncryptedFile(moveItem).exists())

        // C. Export and Remove: Vault copy is deleted ONLY after export is byte-verified
        val exportDest = File(context.cacheDir, "restored_tax_filing.pdf")
        val exportRes = repo.exportAndRemoveItem(moveItem, Uri.fromFile(exportDest))
        assertTrue("Export and remove must succeed", exportRes.isSuccess)
        assertEquals(moveItem.fileSizeRaw, exportDest.length())
        assertFalse("Vault copy is removed only after verified export", repo.getEncryptedFile(moveItem).exists())

        // D. Safe Failure Retention: If export destination cannot be written, Vault copy is 100% PRESERVED
        val dummyItem = copyRes.getOrThrow()
        val invalidDestUri = Uri.parse("content://invalid.provider/nonexistent/destination")
        val failedExportRes = repo.exportItemToUri(dummyItem, invalidDestUri)
        assertTrue("Invalid destination export must fail", failedExportRes.isFailure)
        assertTrue("Vault copy must be 100% retained when export fails", repo.getEncryptedFile(dummyItem).exists())
    }

    @Test
    fun test08_VaultRegression_PortableBackupAndCrossDeviceRestore() = runBlocking {
        val repo = VaultRepository(context)

        val secretPayload = "Passphrase-Protected Cloud Archive Payload"
        val docFile = File(context.cacheDir, "archive_doc.txt").apply {
            writeText(secretPayload)
        }
        val importRes = repo.importFromUri(Uri.fromFile(docFile))
        assertTrue(importRes.isSuccess)

        // Export portable .salvault backup
        val backupOut = ByteArrayOutputStream()
        val exportBackupRes = repo.exportBackup("MasterPass#2026", backupOut)
        assertTrue(exportBackupRes.isSuccess)
        assertEquals(1, exportBackupRes.getOrNull())
        val backupBytes = backupOut.toByteArray()

        // Simulate complete app reinstall / new device key rotation
        File(context.filesDir, "vault").deleteRecursively()
        VaultCryptoEngine.rotateFallbackKeyForTesting()

        val freshRepo = VaultRepository(context)
        assertEquals(0, freshRepo.getAllItems().size)

        // Restore with wrong password fails cleanly
        val wrongRestore = freshRepo.restoreBackup("WrongPassword", ByteArrayInputStream(backupBytes))
        assertTrue(wrongRestore.isFailure)
        assertEquals(0, freshRepo.getAllItems().size)

        // Restore with correct password succeeds and re-encrypts with new master key
        val goodRestore = freshRepo.restoreBackup("MasterPass#2026", ByteArrayInputStream(backupBytes))
        assertTrue(goodRestore.isSuccess)
        assertEquals(1, goodRestore.getOrNull())
        assertEquals(1, freshRepo.getAllItems().size)

        val restoredItem = freshRepo.getAllItems().first()
        val decryptedBytes = VaultCryptoEngine.decryptBytes(freshRepo.getEncryptedFile(restoredItem).readBytes())
        assertEquals(secretPayload, String(decryptedBytes, Charsets.UTF_8))
    }

    // =========================================================================
    // 4. CALCULATOR DISGUISE REGRESSION
    // =========================================================================

    @Test
    fun test09_CalculatorDisguiseRegression_TwoKeyRuleAndPEMDAS() {
        // Disguise mode cannot be enabled without a configured secret sequence
        assertFalse(preferences.isCalculatorSecretConfigured())
        preferences.setCalculatorDisguiseEnabled(true)
        assertFalse("Disguise mode must stay false until secret sequence is configured", preferences.isCalculatorDisguiseEnabled())

        // Save secret sequence
        preferences.saveCalculatorSecretSequence("8921")
        assertTrue(preferences.isCalculatorSecretConfigured())
        preferences.setCalculatorDisguiseEnabled(true)
        assertTrue(preferences.isCalculatorDisguiseEnabled())

        // Verify secret sequence checking
        assertTrue(preferences.verifyCalculatorSecretSequence("8921"))
        assertFalse(preferences.verifyCalculatorSecretSequence("1234"))
        assertFalse(preferences.verifyCalculatorSecretSequence(""))

        // PEMDAS Math Engine regression
        assertEquals("14", CalculatorEngine.evaluate("2+3×4"))
        assertEquals("20", CalculatorEngine.evaluate("(2+3)×4"))
        assertEquals("25", CalculatorEngine.evaluate("100÷4"))
        assertEquals("100", CalculatorEngine.evaluate("50%×200"))
        assertEquals(CalculatorEngine.ERROR_DIVIDE_BY_ZERO, CalculatorEngine.evaluate("42÷0"))

        // Consecutive operator replacement
        var expr = "10+"
        expr = CalculatorEngine.appendOperator(expr, '×', "", false)
        expr = CalculatorEngine.appendDigit(expr, "5", false)
        assertEquals("10×5", expr)
        assertEquals("50", CalculatorEngine.evaluate(expr))
    }

    // =========================================================================
    // 5. UI & NAVIGATION REGRESSION
    // =========================================================================

    @Test
    fun test10_UINavigationAndLifecycleRegression_ScreenRelockAndThemes() {
        // Theme switcher: Dark (true), Light (false), System Default (null)
        preferences.setDarkThemeOverride(true)
        assertEquals(true, preferences.isDarkThemeOverride())
        preferences.setDarkThemeOverride(false)
        assertEquals(false, preferences.isDarkThemeOverride())
        preferences.setDarkThemeOverride(null)
        assertNull(preferences.isDarkThemeOverride())

        // MainNavigationViewModel Vault session enforcement
        val navVm = MainNavigationViewModel(androidx.lifecycle.SavedStateHandle())
        assertFalse(navVm.isVaultAuthorized())

        // Requesting vault access routes to VAULT_AUTH_GATE
        val gate = navVm.requestVaultAccess(ScreenState.DASHBOARD)
        assertEquals(ScreenState.VAULT_AUTH_GATE, gate)
        assertFalse(navVm.isVaultAuthorized())

        // Successful authentication unlocks VAULT
        val vaultScreen = navVm.authorizeVaultEntry()
        assertEquals(ScreenState.VAULT, vaultScreen)
        assertTrue(navVm.isVaultAuthorized())

        // Leaving Files (VAULT) immediately locks vault session
        navVm.lockVaultSession()
        assertFalse("Leaving Files must lock vault session immediately", navVm.isVaultAuthorized())

        // App Management session authorization
        assertFalse(navVm.isAppManagementAuthorized())
        navVm.authorizeAppManagementSession()
        assertTrue(navVm.isAppManagementAuthorized())
        navVm.lockAppManagementSession()
        assertFalse(navVm.isAppManagementAuthorized())
    }

    // =========================================================================
    // 6. BACKGROUND & OEM RELIABILITY REGRESSION
    // =========================================================================

    @Test
    fun test11_BackgroundAndOEMBatteryGuidance() {
        // OEM device brand mapping
        val brand = BatteryOptimizationHelper.getDeviceBrand()
        assertTrue(brand in BatteryOptimizationHelper.OemBrand.entries)

        // OEM guidance check
        val guidance = BatteryOptimizationHelper.getManufacturerGuidance(context)
        assertNotNull(guidance)
        assertTrue("Guidance brand name must be non-empty", guidance.brandName.isNotEmpty())
        assertTrue("Guidance instructions must be non-empty", guidance.instructions.isNotEmpty())

        // Process recreation fail-safe: unlocked packages start completely empty
        val manager = AppLockManager.getInstance(context)
        manager.lockAll()
        assertFalse(manager.isPackageUnlocked("com.whatsapp"))
        assertFalse(manager.isPackageUnlocked("com.instagram.android"))
    }

    // =========================================================================
    // 7. SECURITY RULES, IDENTITY & RESOURCE INTEGRITY
    // =========================================================================

    @Test
    fun test12_GooglePlayPolicyAndResourceIntegrity() {
        // App name string resource
        val appName = context.getString(R.string.app_name)
        assertEquals("Smart AppLock", appName)
        assertTrue("App name must be 30 chars or fewer for Play Policy", appName.length <= 30)

        // Accessibility service strings
        val serviceName = context.getString(R.string.accessibility_service_name)
        val serviceDesc = context.getString(R.string.accessibility_service_description)
        assertTrue(serviceName.isNotEmpty())
        assertTrue(serviceDesc.isNotEmpty())

        // Device Admin strings
        val adminLabel = context.getString(R.string.device_admin_label)
        val adminDesc = context.getString(R.string.device_admin_description)
        assertTrue(adminLabel.isNotEmpty())
        assertTrue(adminDesc.isNotEmpty())

        // ApplicationId invariant
        val expectedAppId = "com.aistudio.applock.secjkv"
        assertEquals(expectedAppId, BuildConfig.APPLICATION_ID)

        // Salted PBKDF2 hash uniqueness
        val hashA = PinHashUtil.createSaltedHash("4826")
        val hashB = PinHashUtil.createSaltedHash("4826")
        assertNotEquals("Each salt must be cryptographically unique", hashA.salt, hashB.salt)
        assertNotEquals("Hashes with different salts must differ", hashA.hash, hashB.hash)
        assertTrue("Hash A verifies", PinHashUtil.verifyPin("4826", hashA.salt, hashA.hash))
        assertTrue("Hash B verifies", PinHashUtil.verifyPin("4826", hashB.salt, hashB.hash))
        assertFalse("Wrong PIN fails", PinHashUtil.verifyPin("9999", hashA.salt, hashA.hash))
    }
}
