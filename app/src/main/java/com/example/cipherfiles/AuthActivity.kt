package com.example.cipherfiles

import android.os.Bundle
import android.os.PersistableBundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class AuthActivity: AppCompatActivity()  {
    lateinit var etLogin: EditText
    lateinit var etPassword: EditText
    lateinit var btnAuth: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_auth)

        etLogin = findViewById(R.id.etLogin)
        etPassword = findViewById(R.id.etPassword)
        btnAuth = findViewById(R.id.btnAuth)


    }
}