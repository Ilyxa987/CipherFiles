package com.example.cipherfiles

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

class UserAuthManager(private val context: Context) {

    companion object {
        private const val TAG = "UserAuthManager"

    }

    suspend fun registerUser(username: String, password: String) : Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val digest = hashPassword(password)
                val db = UserDatabase.getDatabase(context)
                val userDao = db.userDao()
                val user = User(
                    username,
                    digest
                )
                userDao.insertAll(user)
                Log.d(TAG, "Register success")
                return@withContext true
            } catch (e: Exception) {
                Log.e(TAG, "Register failed", e)
                return@withContext false
            }
        }
    }

    suspend fun signUser(username: String, password: String): Boolean {
        return withContext(Dispatchers.IO) {
            val digest = hashPassword(password)

            val db = UserDatabase.getDatabase(context)

            val userDao = db.userDao()
            val user = userDao.findByLogin(username)
            if (user == null) {
                return@withContext false
            }
            if (user.password == digest) {
                Log.d(TAG, "Sign success")
                return@withContext true
            }
            else {
                return@withContext false
            }
        }
    }

    suspend fun countUser(): Int {
        return withContext(Dispatchers.IO) {
            val db = UserDatabase.getDatabase(context)
            return@withContext db.userDao().getRowCount()
        }
    }

    private fun hashPassword(password : String) : String {
        val bytes = password.toByteArray(Charsets.UTF_8)
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)

        return digest.joinToString("") { "%02x".format(it) }
    }

    suspend fun changePassword(username: String, oldPass: String, newPass: String): Boolean {
        return withContext(Dispatchers.IO) {
            val digest = hashPassword(oldPass)
            val db = UserDatabase.getDatabase(context)
            val userDao = db.userDao()
            val user = userDao.findByLogin(username)
            if (user == null) {
                return@withContext false
            }
            if (user.password == digest) {
                val hashPass = hashPassword(newPass)
                val updateUser = User(
                    username,
                    hashPass
                )
                userDao.Update(updateUser)
                return@withContext true
            }
            else {
                return@withContext false
            }
        }
    }
}