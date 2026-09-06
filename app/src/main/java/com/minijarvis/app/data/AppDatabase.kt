package com.minijarvis.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.minijarvis.app.security.PassphraseStore
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [
        ExpenseEntity::class,
        FoodEntity::class,
        MedicineEntity::class,
        MedicineDoseLogEntity::class,
        WeightEntity::class,
        HealthMetricEntity::class,
        HabitEntity::class,
        HabitLogEntity::class,
        TaskEntity::class,
        ChatMessageEntity::class,
        CallLogEntryEntity::class,
        LocationVisitEntity::class,
        AppUsageEntity::class,
        MusicPlayEntity::class,
        ImageAnalysisEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun foodDao(): FoodDao
    abstract fun medicineDao(): MedicineDao
    abstract fun weightDao(): WeightDao
    abstract fun habitDao(): HabitDao
    abstract fun taskDao(): TaskDao
    abstract fun chatDao(): ChatDao
    abstract fun callLogEntryDao(): CallLogEntryDao
    abstract fun locationVisitDao(): LocationVisitDao
    abstract fun appUsageDao(): AppUsageDao
    abstract fun musicPlayDao(): MusicPlayDao
    abstract fun imageAnalysisDao(): ImageAnalysisDao

    companion object {
        const val DATABASE_FILE_NAME = "minijarvis_encrypted.db"

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
