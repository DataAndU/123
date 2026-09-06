package com.minijarvis.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * All persisted data. Every table lives only inside the encrypted, on-device
 * SQLCipher database created in [AppDatabase] — nothing here is ever synced
 * or uploaded anywhere.
 */

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val category: String,
    val note: String,
    val timestampMillis: Long
)

@Entity(tableName = "food_entries")
data class FoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val mealType: String,
    val calories: Int?,
    val note: String,
    val timestampMillis: Long
)

@Entity(tableName = "medicines")
data class MedicineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dosage: String,
    val scheduleNote: String,
    val active: Boolean,
    val createdAtMillis: Long
)

@Entity(tableName = "medicine_dose_logs")
data class MedicineDoseLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicineId: Long,
    val medicineName: String,
    val takenAtMillis: Long,
    val taken: Boolean
)

@Entity(tableName = "weight_entries")
data class WeightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weightKg: Double,
    val note: String,
    val timestampMillis: Long
)

@Entity(tableName = "health_metrics")
data class HealthMetricEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val value: Double,
    val unit: String,
    val timestampMillis: Long
)

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val targetPerWeek: Int,
    val active: Boolean,
    val createdAtMillis: Long
)

@Entity(tableName = "habit_logs")
data class HabitLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val epochDay: Long,
    val completed: Boolean
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    val dueAtMillis: Long?,
    val reminderAtMillis: Long?,
    val completed: Boolean,
    val createdAtMillis: Long
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String, // "user" or "assistant"
    val content: String,
    val source: String, // "text" or "voice"
    val timestampMillis: Long
)

@Entity(tableName = "call_log_entries")
data class CallLogEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: String,
    val displayName: String?,
    val type: String, // incoming, outgoing, missed
    val durationSeconds: Int,
    val timestampMillis: Long
)

@Entity(tableName = "location_visits")
data class LocationVisitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val label: String?,
    val timestampMillis: Long
)

@Entity(tableName = "app_usage_daily")
data class AppUsageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appLabel: String,
    val totalTimeMillis: Long,
    val epochDay: Long
)

@Entity(tableName = "music_plays")
data class MusicPlayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val artist: String?,
    val sourceApp: String,
    val timestampMillis: Long
)

@Entity(tableName = "image_analyses")
data class ImageAnalysisEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val imageFilePath: String,
    val labelsCsv: String,
    val recognizedText: String?,
    val timestampMillis: Long
)
