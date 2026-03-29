package com.example.cipherfiles

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChangePasswordActivity : AppCompatActivity() {

    private lateinit var user: String
    private lateinit var tvChangePass: TextView
    private lateinit var etOldPass: EditText
    private lateinit var etNewPass: EditText
    private lateinit var btnChangePass: Button
    private lateinit var userAuthManager: UserAuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change)

        user = intent.extras?.getString("username").toString()

        tvChangePass = findViewById(R.id.tvChangePass)
        tvChangePass.setText("Смена пароля для ${user}")

        etOldPass = findViewById(R.id.etOldPassword)
        etNewPass = findViewById(R.id.etNewPassword)
        btnChangePass = findViewById(R.id.btnChange)
        userAuthManager = UserAuthManager(this)

        btnChangePass.setOnClickListener {
            lifecycleScope.launch {
                changePassword()
            }
        }
    }

    suspend private fun changePassword() {
        val oldPass = etOldPass.text.toString()
        val newPass = etNewPass.text.toString()

        if (oldPass.length >= 4 && newPass.length >= 4) {
            val success = userAuthManager.changePassword(user, oldPass, newPass)
            if (success) {
                Toast.makeText(this, "Пароль изменен", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Пароль не изменен", Toast.LENGTH_SHORT).show()
            }
        }
    }
}