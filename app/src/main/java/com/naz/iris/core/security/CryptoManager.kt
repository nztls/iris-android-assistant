package com.naz.iris.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class CryptoManager {

    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    fun encrypt(plainText: String): EncryptedData {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())

        val iv = cipher.iv
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        return EncryptedData(iv = iv, cipherText = cipherText)
    }

    fun decrypt(data: EncryptedData): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, data.iv)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)

        val plainBytes = cipher.doFinal(data.cipherText)
        return plainBytes.toString(Charsets.UTF_8)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val keyGenSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        keyGenerator.init(keyGenSpec)
        return keyGenerator.generateKey()
    }

    data class EncryptedData(
        val iv: ByteArray,
        val cipherText: ByteArray
    ) {
        fun toBytes(): ByteArray {
            // format: [ivLength(4 bytes)] [iv] [cipherText]
            val buffer = ByteBuffer.allocate(4 + iv.size + cipherText.size)
            buffer.putInt(iv.size)
            buffer.put(iv)
            buffer.put(cipherText)
            return buffer.array()
        }

        companion object {
            fun fromBytes(bytes: ByteArray): EncryptedData {
                val buffer = ByteBuffer.wrap(bytes)
                val ivLength = buffer.int
                val iv = ByteArray(ivLength)
                buffer.get(iv)
                val cipherText = ByteArray(buffer.remaining())
                buffer.get(cipherText)
                return EncryptedData(iv, cipherText)
            }
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "iris_gemini_api_key_aes"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH = 128
    }
}
