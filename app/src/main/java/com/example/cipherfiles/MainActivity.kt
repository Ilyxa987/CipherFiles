package com.example.cipherfiles

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.Pair
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.util.component1
import androidx.core.util.component2
import androidx.lifecycle.lifecycleScope
import com.example.cipherfiles.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var tvSelectedFile: TextView
    private lateinit var btnEncrypt: Button
    private lateinit var btnDecrypt: Button
    private lateinit var btnChangePass: Button
    private var currentFileUri: Uri? = null
    private var currentFilePath: String? = null
    private lateinit var sessionManager: SessionKeyManager

    private lateinit var keyStoreManager: KeystoreManager
    private var aesKeyBytes: ByteArray? = null
    private lateinit var user: String

    private val selectFileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            currentFileUri = result.data?.data
            if (currentFileUri != null) {
                // Получаем доступ к URI
                contentResolver.takePersistableUriPermission(
                    currentFileUri!!,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )

                currentFilePath = getPathFromUri(currentFileUri!!)
                tvSelectedFile.text = "Выбран файл: ${currentFilePath ?: currentFileUri.toString()}"
                btnEncrypt.isEnabled = true
                btnDecrypt.isEnabled = true

                currentFilePath?.let { path ->
                    getFileSize(path)?.let { size ->
                        val sizeMB = size / (1024.0 * 1024.0)
                        tvSelectedFile.append("\nРазмер: %.2f MB".format(sizeMB))
                    }
                }
            }
        } else {
            Toast.makeText(this, "Выбор файла отменен", Toast.LENGTH_SHORT).show()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvSelectedFile = findViewById(R.id.tvSelectedFile)
        val btnSelectFile: Button = findViewById(R.id.btnSelectFile)
        btnEncrypt = findViewById(R.id.btnEncrypt)
        btnDecrypt = findViewById(R.id.btnDecrypt)
        btnChangePass = findViewById(R.id.btnChangePass)

        keyStoreManager = KeystoreManager.getInstance(this)
        user = intent.extras?.getString("username").toString()
        sessionManager = SessionKeyManager(this, user)

        //initializeKey()

        btnSelectFile.setOnClickListener {
            openFilePicker()
        }

        btnEncrypt.setOnClickListener {
            lifecycleScope.launch {
                encryptSelectedFile()
            }
        }

        btnDecrypt.setOnClickListener {
            lifecycleScope.launch {
                decryptSelectedFile()
            }
        }

        btnChangePass.setOnClickListener {
            val intent = Intent(this, ChangePasswordActivity::class.java)
            intent.putExtra("username", user)
            startActivity(intent)
        }
    }

    private fun initializeKey() {
        try {
            val secretKey = keyStoreManager.getOrCreateAesKey(user)
            aesKeyBytes = secretKey.encoded
            Toast.makeText(this, "Ключ AES-256 успешно загружен из Keystore" + aesKeyBytes.toString(), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка загрузки ключа: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun getEncryptedFilesDir(): File {
        val encryptedDir = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "EncryptedFiles")
        if (!encryptedDir.exists()) {
            encryptedDir.mkdirs() // создаём, если нет
        }
        return encryptedDir
    }

    private fun getDecryptedFilesDir(): File {
        val decryptedDir = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "DecryptedFiles")
        if (!decryptedDir.exists()) {
            decryptedDir.mkdirs()
        }
        return decryptedDir
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        selectFileLauncher.launch(intent)
    }

    fun hexToByteArray(hex: String): ByteArray {
        return ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    suspend private fun encryptSelectedFile() {
        currentFilePath?.let { sourcePath ->

            val sessionKey = sessionManager.generateSessionKey()
            Log.d("KEY", sessionKey.joinToString("") { "%02x".format(it) })
            Log.d("KEY", "${sessionKey.size}")

            val (wrappedKey, iv) = sessionManager.wrapSessionKey(sessionKey)

            val sourceFile = File(sourcePath)
            val encryptedDir = getEncryptedFilesDir()
            val encryptedFile = File(encryptedDir, "${sourceFile.nameWithoutExtension}_encrypted.enc")
            val encryptedPath = encryptedFile.absolutePath

            val result = encryptFile(sourcePath, encryptedPath, sessionKey)
            Log.d("MainActivity", "encrypt result = $result path=$encryptedPath")

            if (result) {
                val encryptedFile = File(encryptedPath)

                val copied = copyToPublicDownloads(encryptedFile, this)

                Log.d("FILE", "Copied to public downloads = $copied")
                withContext(Dispatchers.IO) {
                    val db = UserDatabase.getDatabase(this@MainActivity)
                    val stringKey = wrappedKey.joinToString("") { "%02x".format(it) }
                    val ivString = iv.joinToString("") { "%02x".format(it) }
                    val fileKey = FileKey(
                        user,
                        encryptedFile.name,
                        stringKey,
                        ivString
                    )

                    Log.d("MainActivity", encryptedFile.name);

                    db.fileKeyDao().insertAll(fileKey)
                }
                Toast.makeText(this, "Файл зашифрован", Toast.LENGTH_SHORT).show()
            }
        }
    }

    suspend private fun decryptSelectedFile() {
        currentFilePath?.let { sourcePath ->
            val encryptedFile = File(sourcePath)

            val (wrappedKey, iv) = withContext(Dispatchers.IO) {
                val db = UserDatabase.getDatabase(this@MainActivity)
                Log.d("MainActivity", encryptedFile.name)
                val FileKey = db.fileKeyDao().findKey(user, encryptedFile.name)
                val wrappedKey = hexToByteArray(FileKey?.key ?: "")
                val iv = hexToByteArray(FileKey?.iv ?: "")

                Log.d("MainActivity", wrappedKey.toString())
                Log.d("MainActivity", iv.toString())
                Log.d("MainActivity", "wrappedKey size = ${wrappedKey.size}")
                Log.d("MainActivity", "iv size = ${iv.size}")
                Pair(wrappedKey, iv)
            }
            val sessionKey = sessionManager.unwrapSessionKey(wrappedKey, iv)
            Log.d("KEY", sessionKey.joinToString("") { "%02x".format(it) })

            val sourceFile = File(sourcePath)

            val decryptedDir = getDecryptedFilesDir()
            val decryptedFile = File(decryptedDir, "${sourceFile.nameWithoutExtension}.dec")
            val decryptedPath = decryptedFile.absolutePath

            val result = decryptFile(sourcePath, decryptedPath, sessionKey)
            Log.d("MainActivity", "decrypt result = $result path=${decryptedPath}")
            if (result) {
                val decryptedFile = File(decryptedPath)

                val copied = copyToPublicDownloads(decryptedFile, this)

                Log.d("FILE", "Copied to public downloads = $copied")
                Toast.makeText(this, "Готово", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getPathFromUri(uri: Uri): String? {
        return try {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                val nameIndex = it.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME)
                if (it.moveToFirst()) {
                    val fileName = it.getString(nameIndex)
                    // Копируем файл в кэш для обработки
                    val cachedFile = File(cacheDir, fileName)
                    if (!cachedFile.exists()) {
                        contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(cachedFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                    cachedFile.absolutePath
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun copyToPublicDownloads(sourceFile: File, context: Context): Boolean {
        return try {

            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, sourceFile.name)
                put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val resolver = context.contentResolver

            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

            val uri = resolver.insert(collection, values) ?: return false

            resolver.openOutputStream(uri)?.use { output ->
                FileInputStream(sourceFile).use { input ->
                    input.copyTo(output)
                }
            }

            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)

            true

        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * A native method that is implemented by the 'cipherfiles' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String
    external fun encryptFile(sourcePath: String, destPath: String, key: ByteArray): Boolean
    external fun decryptFile(sourcePath: String, destPath: String, key: ByteArray): Boolean
    external fun getFileSize(filePath: String): Long

    companion object {
        // Used to load the 'cipherfiles' library on application startup.
        init {
            System.loadLibrary("cipherfiles")
        }
    }
}