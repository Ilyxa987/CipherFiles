package com.example.cipherfiles

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class AuthActivity: AppCompatActivity()  {
    lateinit var etLogin: EditText
    lateinit var etPassword: EditText
    lateinit var btnAuth: Button
    lateinit var btnReg: Button
    lateinit var btnBioAuth: Button
    private lateinit var bioAuthManager: BioAuthManager
    private lateinit var sharedPreferences: SharedPreferences

    companion object {
        private const val PREFS_NAME = "auth_prefs"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_SAVED_USERNAME = "saved_username"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_auth)

        etLogin = findViewById(R.id.etLogin)
        etPassword = findViewById(R.id.etPassword)
        btnAuth = findViewById(R.id.btnAuth)
        btnReg = findViewById(R.id.btnReg)
        btnBioAuth = findViewById(R.id.bioauthbtn)
        val userAuthManager = UserAuthManager(this)
        bioAuthManager = BioAuthManager(this)
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        checkBiometricAvailability()

        btnReg.setOnClickListener {
            val username = etLogin.text.toString()
            val password = etPassword.text.toString()
            lifecycleScope.launch {
                val success = userAuthManager.registerUser(username, password)
                if (success) {
                    Toast.makeText(this@AuthActivity, "Регистрация успешна", Toast.LENGTH_SHORT).show()
                }
                else {
                    Toast.makeText(this@AuthActivity, "Регистрация неуспешна", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnAuth.setOnClickListener {
            val username = etLogin.text.toString()
            val password = etPassword.text.toString()

            lifecycleScope.launch {
                val success = userAuthManager.signUser(username, password)
                if (success) {
                    val count = userAuthManager.countUser()
                    if (count == 1) saveBiometricCredentials(username)
                    val intent = Intent(this@AuthActivity, MainActivity::class.java)
                    intent.putExtra("username", username)
                    startActivity(intent)
                }
                else {
                    Toast.makeText(this@AuthActivity, "Неверный логин или пароль", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnBioAuth.setOnClickListener {
            authenticateWithBiometric()
        }
    }

    private fun checkBiometricAvailability() {
        val status = bioAuthManager.checkBiometricAvailability()

        when (status) {
            BioAuthManager.BiometricStatus.AVAILABLE -> {
                btnBioAuth.isEnabled = true
                btnBioAuth.visibility = Button.VISIBLE

                // Если есть сохраненный пользователь, показываем кнопку биометрии
                val savedUsername = sharedPreferences.getString(KEY_SAVED_USERNAME, null)
                if (savedUsername != null && sharedPreferences.getBoolean(KEY_BIOMETRIC_ENABLED, false)) {
                    btnBioAuth.text = "Войти по отпечатку ($savedUsername)"
                } else {
                    btnBioAuth.text = "Войти по отпечатку"
                }
            }
            BioAuthManager.BiometricStatus.NOT_ENROLLED -> {
                btnBioAuth.isEnabled = false
                btnBioAuth.text = "Отпечатки не добавлены"
                Toast.makeText(this, "Добавьте отпечатки пальцев в настройках устройства", Toast.LENGTH_LONG).show()
            }
            else -> {
                btnBioAuth.isEnabled = false
                btnBioAuth.visibility = Button.GONE
            }
        }
    }

    private fun saveBiometricCredentials(username: String) {
        sharedPreferences.edit().apply {
            putBoolean(KEY_BIOMETRIC_ENABLED, true)
            putString(KEY_SAVED_USERNAME, username)
            apply()
        }
    }

    private fun authenticateWithBiometric() {
        val savedUsername = sharedPreferences.getString(KEY_SAVED_USERNAME, null)

        if (savedUsername == null) {
            Toast.makeText(this, "Сначала войдите с паролем", Toast.LENGTH_SHORT).show()
            return
        }

        bioAuthManager.authenticate(
            activity = this,
            title = "Вход по отпечатку",
            subtitle = "Подтвердите личность для входа как $savedUsername",
            description = "Приложите палец к сканеру",
            onSuccess = {
                Toast.makeText(this, "Добро пожаловать, $savedUsername!", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("username", savedUsername)
                startActivity(intent)
                finish()
            },
            onFailed = {
                Toast.makeText(this, "Отпечаток не распознан", Toast.LENGTH_SHORT).show()
            },
            onError = { errorCode, errorMessage ->
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                    Toast.makeText(this, "Ошибка: $errorMessage", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}