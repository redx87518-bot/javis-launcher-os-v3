package com.javis.launcher.engine.memory

import android.content.Context
import androidx.room.*
import com.javis.launcher.models.AppUsage
import com.javis.launcher.models.ContactUsage
import com.javis.launcher.models.ConversationMessage
import com.javis.launcher.models.Memory

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories WHERE `key` = :key LIMIT 1")
    suspend fun getByKey(key: String): Memory?

    @Query("SELECT * FROM memories ORDER BY timestamp DESC")
    suspend fun getAll(): List<Memory>

    @Query("SELECT * FROM memories WHERE category = :category ORDER BY timestamp DESC")
    suspend fun getByCategory(category: String): List<Memory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memory: Memory)

    @Delete
    suspend fun delete(memory: Memory)

    @Query("DELETE FROM memories WHERE `key` = :key")
    suspend fun deleteByKey(key: String)
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 50): List<ConversationMessage>

    @Insert
    suspend fun insert(message: ConversationMessage)

    @Query("DELETE FROM conversations WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}

@Dao
interface AppUsageDao {
    @Query("SELECT * FROM app_usage ORDER BY useCount DESC LIMIT :limit")
    suspend fun getTopApps(limit: Int = 10): List<AppUsage>

    @Query("SELECT * FROM app_usage WHERE packageName = :packageName LIMIT 1")
    suspend fun getApp(packageName: String): AppUsage?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(usage: AppUsage)
}

@Dao
interface ContactUsageDao {
    @Query("SELECT * FROM contact_usage ORDER BY callCount DESC LIMIT :limit")
    suspend fun getTopContacts(limit: Int = 10): List<ContactUsage>

    @Query("SELECT * FROM contact_usage WHERE contactId = :id LIMIT 1")
    suspend fun getContact(id: String): ContactUsage?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(usage: ContactUsage)
}

@Database(
    entities = [Memory::class, ConversationMessage::class, AppUsage::class, ContactUsage::class],
    version = 1,
    exportSchema = false
)
abstract class MemoryDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
    abstract fun conversationDao(): ConversationDao
    abstract fun appUsageDao(): AppUsageDao
    abstract fun contactUsageDao(): ContactUsageDao

    companion object {
        @Volatile private var INSTANCE: MemoryDatabase? = null
        fun getInstance(context: Context): MemoryDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context.applicationContext, MemoryDatabase::class.java, "javis_memory.db")
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}
