# SECURITY_RULES.md — Cryptographic & Mobile Security Rules

**Document Version:** 1.0.0  
**Status:** Phase 0 (Specification & Security Architecture)  
**Authority:** Governs all cryptographic operations, key lifecycles, permission models, threat boundaries, and privacy compliance  

---

## 1. Authentication Security (PIN & Recovery)

### Master PIN Cryptographic Specification
1. **Algorithm:** SHA-256 applied in a 5,000-iteration key-stretching loop (`PinHashUtil.kt`).
2. **Salt Generation:** 16 cryptographically random bytes via `java.security.SecureRandom()`, Base64-encoded (`NO_WRAP`).
3. **Verification Method:** Constant-time byte-by-byte comparison using `java.security.MessageDigest.isEqual(candidateBytes, storedBytes)`. Strictly forbids standard string `==` or `equals()` to prevent side-channel timing attacks.
4. **Brute-Force Rate Limiting:**
   - Maximum 5 consecutive failed attempts.
   - Enforces an unskippable 30-second lockout timestamp persisted to SharedPreferences (`KEY_LOCKOUT_UNTIL_TIMESTAMP`), surviving process termination and reboot.
5. **Backwards Compatibility Invariant:** The existing hashing scheme in `PinHashUtil.kt` must remain strictly untouched. Any modification to salt length, stretching rounds, or byte order will break existing user PINs.

### Security Recovery Question
1. **Sanitization:** Normalized via `trim().lowercase()` prior to hashing.
2. **Storage:** Stored as an independently salted SHA-256 hash (`KEY_ANSWER_HASH` and `KEY_ANSWER_SALT`). Plaintext recovery answers are **never stored on disk**.
3. **Recovery Scope:** Validating the security question unlocks access for that specific session or allows setting a new PIN; it never reveals the existing PIN in plaintext.

---

## 2. Biometric Security & Sensor Fallback

1. **API Standard:** AndroidX `BiometricPrompt` (`androidx.biometric:biometric:1.2.0-alpha05`).
2. **Authenticator Classes:** `BIOMETRIC_STRONG or BIOMETRIC_WEAK` (hardware-backed fingerprint or secure face unlock).
3. **Fallback Architecture:** Biometric authentication serves as an ergonomic alternative to the master PIN. If biometric hardware is unvailable, not enrolled, cancelled by the user, or locked out by the OS, the UI falls back seamlessly to the numeric PIN keypad.
4. **Hardware Lockout Handling:** If the sensor reports `ERROR_LOCKOUT` or `ERROR_LOCKOUT_PERMANENT`, the prompt suppresses further biometric requests and displays an actionable message (*"Fingerprint sensor locked. Please use PIN."*).
5. **Brute-Force Lockout Binding:** When the 30-second brute-force lockout is active, all biometric prompts are suppressed until the cooldown timer expires.

---

## 3. App Monitoring, Accessibility & System Permissions

### Accessibility Service (`AppLockAccessibilityService.kt`)
1. **Zero-Read Privacy Rule:** The service configuration (`accessibility_service_config.xml`) must permanently enforce:
   ```xml
   android:canRetrieveWindowContent="false"
   android:accessibilityEventTypes="typeWindowStateChanged"
   android:accessibilityFeedbackType="feedbackGeneric"
   ```
   The application **cannot** read screen contents, user messages, keystrokes, or private application data.
2. **Debounce Safeguard:** A 600ms debounce threshold prevents rapid activity launch loops.
3. **Home Launcher Exemption:** The default home launcher is dynamically cached in memory to bypass Binder IPC overhead while ensuring the home screen is never accidentally locked.
4. **System Insets & Edge-to-Edge:** All lock activities run with `enableEdgeToEdge()`, `taskAffinity=""`, and `excludeFromRecents="true"`.

### Honest Android System Boundaries (Device Administrator)
1. **No Absolute Force-Stop Guarantee:** On standard Android (non-MDM / non-Device Owner), the OS does not allow third-party apps to disable the "Force Stop" button in Settings.
2. **Compliant Anti-Tamper Heuristics:**
   - Detects transitions to `com.android.settings` App Info for `com.aistudio.applock.secjkv` and executes `performGlobalAction(GLOBAL_ACTION_HOME)`.
   - `AppLockDeviceAdminReceiver` implements `onDisableRequested()` to warn users that deactivation requires master authorization.
   - The app must transparently disclose these platform limits in Settings to maintain user trust and Google Play policy compliance.

---

## 4. File Vault Cryptography & Android Keystore

### Device-Local File Encryption
1. **Algorithm:** **AES-256-GCM** with 128-bit authentication tags.
2. **Key Storage:** Android Keystore Provider (`"AndroidKeyStore"`), alias `SmartAppLock_Vault_MasterKey`.
3. **Nonce / IV Rules:** Exactly 96 bits (12 bytes) generated via `SecureRandom` per file. **Nonces must never be reused.**
4. **Storage Location:** App-private directory `context.filesDir/vault/`.
5. **No Automatic Deletion of Originals:** Ingested files are copied and encrypted. The original file in public storage is **NEVER** deleted automatically. Original deletion must be an explicit, separate user choice.
6. **Integrity Verification:** Encrypted files are written to `.tmp` files and verified for GCM tag validity and readability before atomic rename to `.enc`.

---

## 5. Portable Backup & Disaster Recovery Security

### Separation of Keystore vs Portable Keys
- **Device-Bound Keys:** Non-exportable keys in Android Keystore are tied to hardware and lost if the app is uninstalled.
- **Portable Backups:** Must be readable across devices or post-reinstall. Therefore, backups use a **Passphrase-Derived Key**:
  - **KDF:** `PBKDF2WithHmacSHA256` with at least 100,000 iterations.
  - **Salt:** 32 cryptographically random bytes.
  - **Cipher:** AES-256-GCM container protecting an encrypted bundle.
  - **Passphrase Rules:** Passphrases are never stored in plaintext or logged. If forgotten, backup data cannot be recovered.

---

## 6. Calculator Disguise Gateway Security

1. **The Two-Key Security Rule:**
   > The secret sequence alone **NEVER** unlocks the private vault.
   It merely activates the master authentication gateway (`AuthChallengeDialog`). Even if the secret code is observed, the vault remains protected by the user's master PIN or biometrics.
2. **Display & Logging Secrecy:**
   - The secret sequence is suppressed from the calculator display.
   - Sequences are stored as cryptographic salted hashes, never in plaintext.
   - Keystrokes are never logged to Logcat.

---

## 7. Data Sanitation, Relocking & Upgrades

1. **Session Termination & Relock:**
   - When the device screen turns off, `ScreenReceiver` triggers `AppLockManager.onScreenOff()`, wiping all temporary session unlocks (`unlockedPackages.clear()`).
   - If the relock policy is set to "Immediately on app exit", leaving an app relocks it instantly upon window change.
2. **Logcat Sanitization:** No passwords, PINs, recovery answers, file paths, or decrypted payloads may be logged.
3. **Upgrade Safety:** Future migrations must strictly preserve existing user preferences, PIN hashes, and package selections.
