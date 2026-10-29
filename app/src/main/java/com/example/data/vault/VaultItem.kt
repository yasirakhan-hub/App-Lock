package com.example.data.vault

import org.json.JSONObject

/**
 * Metadata record for an encrypted item safely stored in the vault.
 */
data class VaultItem(
    val id: String,
    val originalFileName: String,
    val mimeType: String,
    val fileSizeRaw: Long,
    val encryptedFileName: String,
    val category: VaultCategory,
    val importedTimestamp: Long,
    val contentSha256: String
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("originalFileName", originalFileName)
            put("mimeType", mimeType)
            put("fileSizeRaw", fileSizeRaw)
            put("encryptedFileName", encryptedFileName)
            put("category", category.name)
            put("importedTimestamp", importedTimestamp)
            put("contentSha256", contentSha256)
        }
    }

    companion object {
        fun fromJsonObject(json: JSONObject): VaultItem {
            return VaultItem(
                id = json.getString("id"),
                originalFileName = json.getString("originalFileName"),
                mimeType = json.getString("mimeType"),
                fileSizeRaw = json.getLong("fileSizeRaw"),
                encryptedFileName = json.getString("encryptedFileName"),
                category = try {
                    VaultCategory.valueOf(json.optString("category", VaultCategory.DOCUMENT.name))
                } catch (_: Exception) {
                    VaultCategory.DOCUMENT
                },
                importedTimestamp = json.getLong("importedTimestamp"),
                contentSha256 = json.optString("contentSha256", "")
            )
        }
    }
}
