package com.javis.launcher.engine.memory

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import com.javis.launcher.models.*

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
    suspend fun getRecent(limit: Int = 100): List<ConversationMessage>

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

// V4: Command log DAO
@Dao
interface CommandLogDao {
    @Query("SELECT * FROM command_log ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 100): List<CommandLog>

    @Insert
    suspend fun insert(log: CommandLog)

    @Query("DELETE FROM command_log WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}

@Database(
    entities = [Memory::class, ConversationMessage::class, AppUsage::class, ContactUsage::class, CommandLog::class],
    version = 3,
    exportSchema = true
)
abstract class MemoryDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
    abstract fun conversationDao(): ConversationDao
    abstract fun appUsageDao(): AppUsageDao
    abstract fun contactUsageDao(): ContactUsageDao
    abstract fun commandLogDao(): CommandLogDao

    companion object {
        @Volatile private var INSTANCE: MemoryDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS command_log (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        action TEXT NOT NULL,
                        detail TEXT NOT NULL,
                        result TEXT NOT NULL,
                        timestamp INTEGER NOT NULL
                    )
                """)
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // No schema changes between v2 and v3
            }
        }

        fun getInstance(context: Context): MemoryDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    MemoryDatabase::class.java,
                    "javis_memory.db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                .also { INSTANCE = it }
            }
        }
    }
}
