# HIDE_FILES_SPEC.md — Encrypted Private File Vault Specification

**Document Version:** 1.0.0  
**Status:** Phase 0 (Specification & Security Architecture)  
**Authority:** Governs vault storage, encryption protocols, SAF file pickers, metadata persistence, and portable backups  
**Target Capabilities:** Documents, Pictures, and Videos  

---

## 1. Feature Overview & User Journey

The **Hide Files Vault** provides a private, encrypted on-device sanctuary for sensitive documents, pictures, and videos. Content inside the vault is shielded from gallery scanners, media indexers, file browsers, and third-party applications.

### User Journey Stages
1. **Entry & Gating:** Accessible from the main dashboard or Calculator Disguise, strictly requiring master authentication (`AuthChallengeDialog`).
2. **Category Browsing:** Clean navigation across three categories:
   - **Pictures** (JPEG, PNG, WEBP, GIF, HEIC)
   - **Videos** (MP4, MKV, WEBM, 3GP)
   - **Documents** (PDF, DOCX, XLSX, TXT, CSV)
3. **Import Flow:** User taps "+ Import" -> opens zero-permission Android Photo Picker or Storage Access Framework (SAF) document picker -> selected items are encrypted and copied into app-private storage.
4. **Verification & Original File Handling:** The vault verifies encryption integrity before reporting success. **Original files are NEVER automatically deleted or altered.** If the user desires to free external storage, a separate, explicit confirmation dialog is presented.
5. **Preview & Interaction:** In-memory decrypted viewing for photos, secure private playback for videos, and native document rendering without writing plaintext copies to public storage.
6. **Export / Restore:** User can select vault items and export them back to public device storage via the Android system file picker.
7. **Portable Encrypted Backup:** Export entire vault into a single encrypted container protected by a user-defined passphrase for migration or disaster recovery.

---

## 2. Security Architecture & Threat Model

### Cryptographic Standards
- **Authenticated Encryption:** **AES-256-GCM** (Galois/Counter Mode) via `javax.crypto.Cipher`.
- **Integrity Tag:** 128-bit authentication tag (`TLEN = 128`) protecting against tampering and bit-flipping attacks.
- **Initialization Vector (IV/Nonce):** Unique 96-bit (12-byte) random nonce generated via `java.security.SecureRandom()` for **every single encryption operation**. Nonces are never reused.
- **Hardware-Backed Key Protection:** Device-local encryption keys are generated and housed inside the **Android Keystore Provider** (`"AndroidKeyStore"`):
  - Key Alias: `SmartAppLock_Vault_MasterKey`
  - Purpose: `KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT`
  - Block Mode: `KeyProperties.BLOCK_MODE_GCM`
  - Padding: `KeyProperties.ENCRYPTION_PADDING_NONE`
  - Key Size: 256 bits

### Threat Model Boundaries & Limitations
- **App-Private Isolation:** Vault files reside strictly within `context.filesDir/vault/`, inaccessible to standard Android apps lacking root privileges.
- **Media Scanner Exclusion:** A `.nomedia` sentinel file is placed inside the vault root to prevent Android's `MediaScanner` from indexing files.
- **Honest System Boundaries:** On rooted devices or forensic physical dumps with unlocked bootloaders, memory dump attacks can theoretically extract keys during active decryption. Smart AppLock protects against everyday device compromise, casual snooping, shared device usage, and malware lacking root access.

---

## 3. Vault Storage Layout & Encrypted Container Format

### On-Device Directory Structure
```
/data/user/0/com.aistudio.applock.secjkv/files/vault/
├── .nomedia                        (Prevents OS media indexing)
├── metadata/
│   └── vault_index.enc             (AES-GCM encrypted metadata catalog)
├── documents/
│   ├── doc_8f3a9b1c.enc            (Payload: [12-byte IV] + [Ciphertext] + [16-byte Tag])
│   └── ...
├── pictures/
│   ├── pic_4e2d7a0b.enc
│   └── ...
└── videos/
    ├── vid_9c1b5e3f.enc
    └── ...
```

### Encrypted File Binary Format
Each `.enc` file in the vault follows a deterministic binary layout:
```
┌─────────────────┬─────────────────┬───────────────────────────────┬─────────────────┐
│ Magic Header    │ IV / Nonce      │ Encrypted Ciphertext Payload   │ GCM Auth Tag    │
│ (4 bytes)       │ (12 bytes)      │ (N bytes)                     │ (16 bytes)      │
│ "SALV" (0x53414C56)               │                               │ (Appended by GCM)│
└─────────────────┴─────────────────┴───────────────────────────────┴─────────────────┘
```

### Vault Metadata Catalog (`VaultItem`)
Metadata is tracked in an encrypted index:
```kotlin
data class VaultItem(
    val id: String,                     // UUID string
    val originalFileName: String,       // e.g. "Passport_Scan.pdf"
    val mimeType: String,               // e.g. "application/pdf"
    val fileSizeRaw: Long,              // Plaintext size in bytes
    val encryptedFileName: String,      // e.g. "doc_8f3a9b1c.enc"
    val category: VaultCategory,        // DOCUMENT, PICTURE, VIDEO
    val importedTimestamp: Long,        // Epoch millis
    val contentSha256: String           // Plaintext SHA-256 for duplicate detection
)

enum class VaultCategory {
    DOCUMENT,
    PICTURE,
    VIDEO
}
```

---

## 4. Import, Verification & Error Recovery Protocols

### Import State Machine
```
[User Selects Files] 
       │
       ▼
[Validate Storage Space] ──(Insufficient)──> [Abort with Clear Storage Warning]
       │
       ▼
[Calculate Plaintext SHA-256 & Check Duplicates]
       │
       ▼
[Stream Content via ContentResolver]
       │
       ▼
[Encrypt via AES-256-GCM to Temp File (.tmp)]
       │
       ▼
[Verify Tag & Readability Verification Check]
       │
       ├─(Verification Failed)──> [Delete .tmp File & Report Import Error]
       │
       ▼
[Atomic Rename (.tmp -> .enc) & Update Encrypted Index]
       │
       ▼
[Report Import Success]
       │
       ▼
[Offer OPTIONAL Separate Original Deletion Dialog]
```

### Critical Safety Invariants
1. **Never Automatically Delete Originals:** The source file on public storage is strictly untouched during import. Once import verification completes, the app displays:  
   *"Files successfully encrypted into vault. Would you like to delete the originals from your gallery/storage?"* with explicit **"Keep Originals" (Default)** and **"Delete Originals"** buttons.
2. **Atomic Ingestion:** Files are written to `.tmp` files first. If the device reboots, loses power, or runs out of space, partial `.tmp` files are cleaned on app startup. No half-encrypted files ever pollute the active index.
3. **Duplicate Detection:** If the calculated `contentSha256` already exists in `vault_index`, the user is alerted: *"This item is already stored in your vault."*

---

## 5. Preview, Playback & Export Protocols

1. **Picture Viewing:** Decrypted strictly in-memory into `android.graphics.Bitmap` or `androidx.compose.ui.graphics.ImageBitmap`. Plaintext bitmaps are held in lifecycle-aware memory and cleared on screen exit.
2. **Video Playback:** Handled via a private local piping mechanism (or encrypted streaming data source) to AndroidX Media3/ExoPlayer without dumping unencrypted video files to disk.
3. **Document Viewing:** Rendered natively via Android `PdfRenderer` using an in-memory or private pipe file descriptor.
4. **Export / Restore:**
   - User selects items -> launches SAF `ActivityResultContracts.CreateDocument` or `OpenDocumentTree`.
   - Payload is decrypted on the fly and streamed directly into the destination `Uri` via `contentResolver.openOutputStream(uri)`.

---

## 6. Portable Encrypted Backup & Disaster Recovery

### The Fundamental Key Distinction
- **Device-Local Keystore Keys:** Bound to the physical device hardware. If the user uninstalls the app or factory-resets their device, Keystore keys are permanently destroyed by Android OS.
- **Portable Backups:** Must be readable on another device or after a clean reinstall. Therefore, portable backups **CANNOT** rely on local Android Keystore keys.

### Portable Backup Container Format (`.salvault`)
Portable backups are protected by a user-supplied **Master Backup Passphrase**:

```
┌────────────────────────────────────────────────────────────────────────┐
│ Header Magic: "SAL_BACKUP_V1" (14 bytes)                               │
├────────────────────────────────────────────────────────────────────────┤
│ Salt: 32 random bytes for Key Derivation                               │
├────────────────────────────────────────────────────────────────────────┤
│ KDF Iterations: 100,000 rounds (PBKDF2-HMAC-SHA256)                   │
├────────────────────────────────────────────────────────────────────────┤
│ Container IV: 12 random bytes                                          │
├────────────────────────────────────────────────────────────────────────┤
│ Payload: AES-256-GCM encrypted ZIP archive containing:                 │
│   ├── manifest.json (item catalog, original names, MIME types)         │
│   ├── files/ (raw encrypted payloads or serialized vault items)        │
├────────────────────────────────────────────────────────────────────────┤
│ GCM Authentication Tag: 16 bytes                                       │
└────────────────────────────────────────────────────────────────────────┘
```

### Key Derivation Specification
- **Algorithm:** `PBKDF2WithHmacSHA256`
- **Salt:** 32 bytes from `SecureRandom`
- **Iteration Count:** 100,000 iterations minimum
- **Key Length:** 256 bits

### Backup & Restore Rules
1. **Passphrase Guidelines:** User must confirm passphrase; strength meter warns against short/trivial passwords.
2. **No Plaintext Passphrase Storage:** Passphrases are never saved on disk. If lost, the backup cannot be recovered.
3. **Restore Conflict Handling:** When restoring a backup into an existing vault:
   - Duplicate items (matched by checksum) are skipped.
   - Non-duplicate items are seamlessly merged.
   - If passphrase decryption fails (bad GCM tag), the existing vault remains **100% untouched**.
