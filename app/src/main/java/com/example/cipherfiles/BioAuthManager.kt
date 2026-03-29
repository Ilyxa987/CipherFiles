package com.example.cipherfiles

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class BioAuthManager(private val context: Context) {

    enum class BiometricStatus {
        AVAILABLE,
        NOT_AVAILABLE,
        NOT_ENROLLED,
        HARDWARE_UNAVAILABLE
    }

    fun checkBiometricAvailability(): BiometricStatus {
        val biometricManager = BiometricManager.from(context)

        when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                return BiometricStatus.AVAILABLE
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                return BiometricStatus.HARDWARE_UNAVAILABLE
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                return BiometricStatus.NOT_ENROLLED
            }
            else -> {
                return BiometricStatus.NOT_AVAILABLE
            }
        }
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String = "Аутентификация",
        subtitle: String = "Подтвердите личность с помощью отпечатка пальца",
        description: String = "Приложите палец к сканеру",
        negativeButtonText: String = "Отмена",
        onSuccess: () -> Unit,
        onFailed: () -> Unit = {},
        onError: (errorCode: Int, errorMessage: String) -> Unit = { _, _ -> }
    ) {
        val executor = ContextCompat.getMainExecutor(context)

        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onFailed()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errorCode, errString.toString())
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setDescription(description)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}