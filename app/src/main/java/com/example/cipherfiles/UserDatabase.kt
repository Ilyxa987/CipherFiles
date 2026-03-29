package com.example.cipherfiles

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update

@Entity(tableName = "user")
data class User(
    @PrimaryKey val firstName: String,
    @ColumnInfo(name = "password") val password: String
)

@Entity(tableName = "file_key",
    primaryKeys = ["firstName", "fileName"])
data class FileKey(
    val firstName: String,
    val fileName: String,
    @ColumnInfo(name = "key") val key: String,
    @ColumnInfo(name = "iv") val iv: String
)

@Dao
interface UserDao {
    @Query("SELECT * FROM user")
    fun getAll(): List<User>

    @Query("SELECT * FROM user WHERE firstName = :first")
    suspend fun findByLogin(first: String): User?

    @Insert
    suspend fun insertAll(vararg users: User)

    @Update
    suspend fun Update(user: User)

    @Delete
    fun delete(user: User)

    @Query("SELECT COUNT(firstName) FROM user")
    fun getRowCount(): Int
}

@Dao
interface FileKeyDao {
    @Query("SELECT * FROM file_key WHERE firstName = :name and fileName = :filename")
    suspend fun findKey(name: String, filename: String): FileKey?

    @Query("SELECT * FROM file_key WHERE firstName = :name")
    suspend fun findKeyByName(name: String): FileKey?

    @Insert
    suspend fun insertAll(vararg fileKey: FileKey)

    @Delete
    fun delete(fileKey: FileKey)
}

@Database(entities = [User::class, FileKey::class], version = 1)
abstract class UserDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun fileKeyDao(): FileKeyDao

    companion object {
        @Volatile
        private var INSTANCE: UserDatabase? = null

        fun getDatabase(context: Context): UserDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    UserDatabase::class.java,
                    "user_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}