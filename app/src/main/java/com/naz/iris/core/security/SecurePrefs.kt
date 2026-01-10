package com.naz.iris.core.security

import android.content.Context
import android.util.Base64

class SecurePrefs(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun putBytes(key: String, value: ByteArray) {
        val b64 = Base64.encodeToString(value, Base64.NO_WRAP)
        prefs.edit().putString(key, b64).apply()
    }

    fun getBytes(key: String): ByteArray? {
        val b64 = prefs.getString(key, null) ?: return null
        return Base64.decode(b64, Base64.NO_WRAP)
    }

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    private companion object {
        const val PREFS_NAME = "iris_secure_prefs"
    }
}
