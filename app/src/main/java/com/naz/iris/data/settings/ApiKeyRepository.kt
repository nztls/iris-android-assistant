package com.naz.iris.data.settings

import android.content.Context
import com.naz.iris.core.security.CryptoManager
import com.naz.iris.core.security.SecurePrefs

class ApiKeyRepository(context: Context) {

    private val crypto = CryptoManager()
    private val securePrefs = SecurePrefs(context)

    fun saveApiKey(apiKey: String) {
        val encrypted = crypto.encrypt(apiKey).toBytes()
        securePrefs.putBytes(KEY_API, encrypted)
    }

    fun loadApiKey(): String? {
        val bytes = securePrefs.getBytes(KEY_API) ?: return null
        val encryptedData = CryptoManager.EncryptedData.fromBytes(bytes)
        return crypto.decrypt(encryptedData)
    }

    fun clearApiKey() {
        securePrefs.remove(KEY_API)
    }

    private companion object {
        const val KEY_API = "gemini_api_key"
    }
}
