# MASTER_SPEC.md — Smart AppLock Master Specification

**Document Version:** 1.0.0  
**Status:** Phase 0 (Specification & Approval)  
**Authority:** Highest (Governs scope, architecture, phase transitions, and cross-feature decisions)  
**Author:** Senior Android Architect, Security Engineer & UI/UX Specialist  
**Target Platform:** Native Android (Kotlin, Jetpack Compose, Material Design 3)  
**Application ID:** `com.aistudio.applock.secjkv`  
**Namespace:** `com.example`  
**App Display Name:** `Smart AppLock`  
**Developer Attribution:** `Developed by Yasir A Khan`  

---

## 1. Document Precedence & Governance Hierarchy

When requirements appear ambiguous or conflicting across documentation files, the following strict order of precedence applies:

1. **`MASTER_SPEC.md`** — Scope, constraints, architectural boundaries, and phase rollout.
2. **`SECURITY_RULES.md`** — Cryptography, threat models, Keystore lifecycle, and privacy boundaries.
3. **`UI_DESIGN_SYSTEM.md`** — Visual design, Neomorphism tokens, motion, and accessibility compliance.
4. **`HIDE_FILES_SPEC.md`** — Encrypted private vault behavior, SAF integration, and portable backups.
5. **`CALCULATOR_VAULT_SPEC.md`** — Calculator redesign, arithmetic engine, and disguise gateway flow.
6. **`TESTING_ACCEPTANCE.md`** — Test cases, verification methodology, and acceptance gates.
7. **`IMPLEMENTATION_STATUS.md`** — Progress tracking, feature readiness, and known dependencies.

---

## 2. Project Identity & Inviolable Constraints

The following parameters are permanently locked and **MUST NOT** be modified, deleted, or altered in any phase:

- **Application ID:** `com.aistudio.applock.secjkv` *(configured in `app/build.gradle.kts`)*
- **Package / Namespace:** `com.example`
- **Application Label:** `Smart AppLock` *(in `res/values/strings.xml` and `metadata.json`)*
- **Developer Attribution:** `Developed by Yasir A Khan` *(displayed in Settings / About)*
- **Master Authentication Integrity:** The existing 5,000-iteration salted SHA-256 key-stretching implementation in `PinHashUtil.kt` and `AppLockPreferences.kt` must remain strictly backwards compatible. Stored user credentials, salts, and recovery data must never be reset, cleared, or reformatted.
- **Signing Configurations:** Existing signing keys (`debug.keystore` and release configurations) must remain intact.

---

## 3. High-Level System Architecture

Smart AppLock consists of five interconnected subsystems:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Jetpack Compose UI Layer                        │
│  MainActivity (Root NavHost: AUTH_GATE -> ONBOARDING / DASHBOARD)       │
│  ├─ DashboardScreen (Protection status, permissions, quick links)      │
│  ├─ AppSelectionScreen (Search, filter, lock toggles, auth challenges) │
│  ├─ SettingsScreen (PIN, recovery, theme, Calculator Disguise, About)  │
│  ├─ TestLockScreen (In-app simulation & verification)                  │
│  ├─ LockActivity (Fullscreen overlay, PIN keypad, BiometricPrompt)     │
│  ├─ [PLANNED] CalculatorDisguiseActivity (Functional disguise keypad)  │
│  └─ [PLANNED] VaultScreen (Encrypted documents, photos, and videos)    │
└────────────────────────────────────┬───────────────────────────────────┘
                                     │
┌────────────────────────────────────▼───────────────────────────────────┐
│                    Shared UI & Dialog Components                       │
│  ├─ AuthChallengeDialog (Action-scoped Biometric + PIN fallback)       │
│  ├─ PinKeypad & PinDots (Tactile, haptic, lockout-aware keypad)        │
│  └─ [PLANNED] NeomorphicComponents (Shared surface depth & cards)      │
└────────────────────────────────────┬───────────────────────────────────┘
                                     │
┌────────────────────────────────────▼───────────────────────────────────┐
│                    Repository & Domain Controller                      │
│  ├─ AppLockManager (Singleton: session cache, debounce, target intents)│
│  ├─ AppDiscoveryRepository (Launcher apps, label/icon cache, sorting)  │
│  ├─ DeviceAdminHelper / PermissionHelper (OS state & intent dispatch)  │
│  ├─ [PLANNED] VaultRepository (File encryption, metadata, storage)     │
│  └─ [PLANNED] CalculatorDisguiseManager (Sequence detection & routing) │
└────────────────────────────────────┬───────────────────────────────────┘
                                     │
┌────────────────────────────────────▼───────────────────────────────────┐
│                 Foreground Interception & Lifecycle                    │
│  ├─ AppLockAccessibilityService (TYPE_WINDOW_STATE_CHANGED detector)   │
│  ├─ AppLockDeviceAdminReceiver (Anti-tamper & uninstallation policy)   │
│  ├─ ScreenReceiver (Dynamic broadcast: screen off session clear)       │
│  └─ BootReceiver (System start notification & setup health)            │
└────────────────────────────────────┬───────────────────────────────────┘
                                     │
┌────────────────────────────────────▼───────────────────────────────────┐
│                       Data & Cryptographic Security                    │
│  ├─ AppLockPreferences (SharedPreferences with in-memory caching)      │
│  ├─ PinHashUtil (SHA-256 + 5,000 salt iterations + constant-time cmp)  │
│  └─ [PLANNED] VaultCryptoEngine (AES-256-GCM + Android Keystore)       │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Master Phased Implementation Roadmap

To avoid sprawling regressions or breaking existing features, implementation must execute in strict, isolated sequential phases:

### Phase 0: Specifications, Architecture & Approval *(Current Phase)*
- Complete and align all seven source-of-truth documents.
- Identify all platform assumptions, cryptographic dependencies, and user experience flows.
- **Restriction:** Strictly documentation-only. No application source code, assets, manifests, or Gradle files are modified.
- **Exit Gate:** Formal user review and approval of the master specification.

### Phase 1: Complete-App Neomorphism Redesign
- Implement the centralized Neomorphism design system defined in `UI_DESIGN_SYSTEM.md`.
- Unify color tokens (very dark navy/black base surfaces with cyan/blue accents).
- Build shared Neomorphic composables (`NeoSurface`, `NeoCard`, `NeoButton`, `NeoInsetField`, `NeoToggle`).
- Apply the unified styling across all existing screens (Dashboard, App Selection, Settings, Lock Screen, Onboarding, Dialogs).
- **Exit Gate:** Visual verification across Light/Dark modes, zero accessibility contrast regressions, all touch targets >= 48dp, and all automated unit tests passing.

### Phase 2: Encrypted Hide Files Vault
- Implement the private file vault defined in `HIDE_FILES_SPEC.md`.
- Categories: Documents, Pictures, and Videos.
- Security: AES-256-GCM authenticated encryption backed by Android Keystore for device-bound protection.
- Storage: Standard Android Photo Picker (`PickVisualMedia`) and Storage Access Framework (SAF).
- Integrity: Checksum validation, atomic file writes, zero plaintext temporary leaks.
- Backup: Portable encrypted container export and restore with user-defined passphrase using PBKDF2 key derivation.
- **Rule:** Never automatically delete or alter original files upon import. Deletion of originals must be an explicit, separate user action.
- **Exit Gate:** Automated cryptographic unit tests, corrupted file import handling, and SAF export verification.

### Phase 3: Calculator Disguise & Authenticated Vault Entry
- Implement the full-screen calculator interface defined in `CALCULATOR_VAULT_SPEC.md` based on the supplied layout reference.
- Keypad: 4-column circular button layout, dark muted surfaces, blue-green operators, highlighted "AC", distinct light-blue "=".
- Arithmetic Engine: Fully functional arithmetic (addition, subtraction, multiplication, division, percentages, parentheses, decimals).
- Gateway Logic: Secret sequence detection triggers master PIN or biometric verification.
- **Rule:** The secret sequence alone **NEVER** unlocks the vault. It only triggers the existing master authentication challenge.
- Scope: Disguise applies **ONLY** to opening Smart AppLock itself, never to external protected apps.
- Settings: Add toggle and code configuration in `SettingsScreen.kt`.
- **Exit Gate:** Functional arithmetic tests, secret sequence detection tests, authentication fallback tests, and clean disablement.

### Phase 4: Integrated System Verification & Hardening
- End-to-end regression testing across all combined subsystems.
- Automated Robolectric test execution.
- Physical device verification checklist for OS-dependent behavior (accessibility events, biometric prompt, Doze mode).
- Verification of APK packaging and CI/CD workflow reproducibility.
- **Exit Gate:** 100% pass rate on all automated tests, zero memory leaks, and documentation updated to reflect final state.

---

## 5. Cross-Feature Architectural Rules

1. **Authentication Gatekeeper Rule:**
   - Any sensitive operation—modifying protected apps, changing master PIN, configuring recovery questions, altering Calculator Disguise, deactivating Device Admin, or viewing Hide Files vault—must pass through `AuthChallengeDialog`.
   - Access to the private vault requires master authentication (PIN or BiometricPrompt). Under no circumstances can a simple calculator sequence bypass the master credentials.

2. **Android Platform Compliance & Honesty Rule:**
   - The app must never claim guaranteed prevention of "Force Stop" or "Uninstallation" on standard Android devices.
   - All background services must respect Android 12+ background activity launch restrictions and OEM battery policies.
   - AccessibilityService configuration must maintain `canRetrieveWindowContent="false"` for strict Google Play privacy compliance.

3. **Data Preservation Rule:**
   - Future migrations to encrypted file storage or calculator preferences must never touch or alter existing SharedPreferences keys (`pin_hash`, `pin_salt`, `pin_length`, `security_question`, `answer_hash`, `protected_packages`).
   - If an error occurs during vault operations, existing data must remain intact and undisturbed.
