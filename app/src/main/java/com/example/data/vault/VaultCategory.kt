package com.example.data.vault

/**
 * Supported categories in the encrypted private vault.
 */
enum class VaultCategory {
    PICTURE,
    VIDEO,
    DOCUMENT;

    companion object {
        fun fromMimeType(mimeType: String): VaultCategory {
            val lower = mimeType.lowercase()
            return when {
                lower.startsWith("image/") -> PICTURE
                lower.startsWith("video/") -> VIDEO
                else -> DOCUMENT
            }
        }
    }
}
