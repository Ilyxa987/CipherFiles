package com.example.cipherfiles

import android.content.Context
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec


class SessionKeyManager(private val context: Context, private val user: String) {
    private val keystore = KeystoreManager.getInstance(context)
    private val masterKey: SecretKey
        get() = keystore.getOrCreateAesKey(user)

    fun generateSessionKey(): ByteArray {
        val key = ByteArray(32)
        SecureRandom().nextBytes(key)
        return key
    }

    fun wrapSessionKey(sessionKey: ByteArray): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
        cipher.init(Cipher.ENCRYPT_MODE, masterKey)
        val encryptedKey = cipher.doFinal(sessionKey)
        val iv = cipher.iv
        return Pair(encryptedKey, iv)
    }

    fun unwrapSessionKey(encryptedKey: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
        cipher.init(Cipher.DECRYPT_MODE, masterKey, IvParameterSpec(iv))
        return cipher.doFinal(encryptedKey)
    }
}