package com.gemmaassistant.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.gemmaassistant.app.security.PassphraseStore
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [
        ChatMessageEntity::class,
        AgentActivityLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun agentActivityLogDao(): AgentActivityLogDao

    companion object {
        const val DATABASE_FILE_NAME = "gemma_assistant_encrypted.db"

        /**
         * Builds the single, on-device Room database backed by SQLCipher.
         * The passphrase comes from [PassphraseStore], whose key material is
         * anchored in the Android Keystore — the database file on disk is
         * unreadable without this device's Keystore-protected key.
         */
        fun build(context: Context, passphraseStore: PassphraseStore): AppDatabase {
            SQLiteDatabase.loadLibs(context)
            val passphrase = passphraseStore.getOrCreateDatabasePassphrase()
            val factory = SupportFactory(SQLiteDatabase.getBytes(passphrase))
            passphrase.fill('0')

            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_FILE_NAME
            )
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
