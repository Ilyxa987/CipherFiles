package com.example.cipherfiles

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class KeystoreManager(private val context: Context) {

    companion object {
        private const val TAG = "KeystoreManager"
        private const val KEY_ALIAS = "cipherfiles_aes_key"
        @Volatile
        private var instance: KeystoreManager? = null

        fun getInstance(context: Context): KeystoreManager {
            return instance ?: synchronized(this) {
                instance ?: KeystoreManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }

    fun getOrCreateAesKey(user: String): SecretKey {
        if (keyStore.containsAlias("${user}_key")) {
            val keyEntry = keyStore.getEntry("${user}_key", null) as KeyStore.SecretKeyEntry
            Log.d(TAG, "Ключ загружен из хранилища")
            return keyEntry.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )

        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            "${user}_key",
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setUnlockedDeviceRequired(false)
                }
            }
            .build()

        keyGenerator.init(keyGenParameterSpec)
        Log.d(TAG, "Ключ сгенерирован")
        return keyGenerator.generateKey()
    }
}