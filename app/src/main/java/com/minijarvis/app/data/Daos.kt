package com.minijarvis.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Insert suspend fun insert(entity: ExpenseEntity): Long
    @Update suspend fun update(entity: ExpenseEntity)
    @Delete suspend fun delete(entity: ExpenseEntity)
    @Query("SELECT * FROM expenses ORDER BY timestampMillis DESC")
    fun observeAll(): Flow<List<ExpenseEntity>>
    @Query("SELECT * FROM expenses WHERE timestampMillis BETWEEN :startMillis AND :endMillis ORDER BY timestampMillis DESC")
    suspend fun getBetween(startMillis: Long, endMillis: Long): List<ExpenseEntity>
    @Query("SELECT * FROM expenses WHERE category LIKE '%' || :keyword || '%' OR note LIKE '%' || :keyword || '%' ORDER BY timestampMillis DESC LIMIT 50")
    suspend fun search(keyword: String): List<ExpenseEntity>
    @Query("SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE timestampMillis BETWEEN :startMillis AND :endMillis")
    suspend fun sumBetween(startMillis: Long, endMillis: Long): Double
}

@Dao
interface FoodDao {
    @Insert suspend fun insert(entity: FoodEntity): Long
    @Update suspend fun update(entity: FoodEntity)
    @Delete suspend fun delete(entity: FoodEntity)
    @Query("SELECT * FROM food_entries ORDER BY timestampMillis DESC")
    fun observeAll(): Flow<List<FoodEntity>>
    @Query("SELECT * FROM food_entries WHERE timestampMillis BETWEEN :startMillis AND :endMillis ORDER BY timestampMillis DESC")
    suspend fun getBetween(startMillis: Long, endMillis: Long): List<FoodEntity>
    @Query("SELECT * FROM food_entries WHERE name LIKE '%' || :keyword || '%' OR note LIKE '%' || :keyword || '%' ORDER BY timestampMillis DESC LIMIT 50")
    suspend fun search(keyword: String): List<FoodEntity>
    @Query("SELECT COALESCE(SUM(calories), 0) FROM food_entries WHERE timestampMillis BETWEEN :startMillis AND :endMillis")
    suspend fun sumCaloriesBetween(startMillis: Long, endMillis: Long): Int
}

@Dao
interface MedicineDao {
    @Insert suspend fun insert(entity: MedicineEntity): Long
    @Update suspend fun update(entity: MedicineEntity)
    @Delete suspend fun delete(entity: MedicineEntity)
    @Query("SELECT * FROM medicines WHERE active = 1 ORDER BY name ASC")
    fun observeActive(): Flow<List<MedicineEntity>>
    @Query("SELECT * FROM medicines ORDER BY name ASC")
    fun observeAll(): Flow<List<MedicineEntity>>

    @Insert suspend fun insertDoseLog(entity: MedicineDoseLogEntity): Long
    @Query("SELECT * FROM medicine_dose_logs ORDER BY takenAtMillis DESC")
    fun observeDoseLogs(): Flow<List<MedicineDoseLogEntity>>
    @Query("SELECT * FROM medicine_dose_logs WHERE takenAtMillis BETWEEN :startMillis AND :endMillis ORDER BY takenAtMillis DESC")
    suspend fun getDoseLogsBetween(startMillis: Long, endMillis: Long): List<MedicineDoseLogEntity>
    @Query("SELECT * FROM medicines WHERE name LIKE '%' || :keyword || '%' LIMIT 50")
    suspend fun search(keyword: String): List<MedicineEntity>
}

@Dao
interface WeightDao {
    @Insert suspend fun insert(entity: WeightEntity): Long
    @Delete suspend fun delete(entity: WeightEntity)
    @Query("SELECT * FROM weight_entries ORDER BY timestampMillis DESC")
    fun observeAll(): Flow<List<WeightEntity>>
    @Query("SELECT * FROM weight_entries WHERE timestampMillis BETWEEN :startMillis AND :endMillis ORDER BY timestampMillis ASC")
    suspend fun getBetween(startMillis: Long, endMillis: Long): List<WeightEntity>
    @Query("SELECT * FROM weight_entries ORDER BY timestampMillis DESC LIMIT 1")
    suspend fun getLatest(): WeightEntity?

    @Insert suspend fun insertHealthMetric(entity: HealthMetricEntity): Long
    @Query("SELECT * FROM health_metrics ORDER BY timestampMillis DESC")
    fun observeHealthMetrics(): Flow<List<HealthMetricEntity>>
}

@Dao
interface HabitDao {
    @Insert suspend fun insert(entity: HabitEntity): Long
    @Update suspend fun update(entity: HabitEntity)
    @Delete suspend fun delete(entity: HabitEntity)
    @Query("SELECT * FROM habits WHERE active = 1 ORDER BY name ASC")
    fun observeActive(): Flow<List<HabitEntity>>

    @Insert suspend fun insertLog(entity: HabitLogEntity): Long
    @Query("DELETE FROM habit_logs WHERE habitId = :habitId AND epochDay = :epochDay")
    suspend fun deleteLogForDay(habitId: Long, epochDay: Long)
    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId ORDER BY epochDay DESC")
    fun observeLogsForHabit(habitId: Long): Flow<List<HabitLogEntity>>
    @Query("SELECT * FROM habit_logs WHERE epochDay BETWEEN :startDay AND :endDay")
    suspend fun getLogsBetween(startDay: Long, endDay: Long): List<HabitLogEntity>
    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId AND epochDay = :epochDay LIMIT 1")
    suspend fun getLogForDay(habitId: Long, epochDay: Long): HabitLogEntity?
}

@Dao
interface TaskDao {
    @Insert suspend fun insert(entity: TaskEntity): Long
    @Update suspend fun update(entity: TaskEntity)
    @Delete suspend fun delete(entity: TaskEntity)
    @Query("SELECT * FROM tasks ORDER BY completed ASC, dueAtMillis ASC")
    fun observeAll(): Flow<List<TaskEntity>>
    @Query("SELECT * FROM tasks WHERE completed = 0 AND dueAtMillis BETWEEN :startMillis AND :endMillis ORDER BY dueAtMillis ASC")
    suspend fun getDueBetween(startMillis: Long, endMillis: Long): List<TaskEntity>
    @Query("SELECT * FROM tasks WHERE title LIKE '%' || :keyword || '%' OR description LIKE '%' || :keyword || '%' LIMIT 50")
    suspend fun search(keyword: String): List<TaskEntity>
}

@Dao
interface ChatDao {
    @Insert suspend fun insert(entity: ChatMessageEntity): Long
    @Query("SELECT * FROM chat_messages ORDER BY timestampMillis ASC")
    fun observeAll(): Flow<List<ChatMessageEntity>>
    @Query("DELETE FROM chat_messages")
    suspend fun clearAll()
}

@Dao
interface CallLogEntryDao {
    @Insert suspend fun insertAll(entities: List<CallLogEntryEntity>)
    @Query("DELETE FROM call_log_entries")
    suspend fun clearAll()
    @Query("SELECT * FROM call_log_entries ORDER BY timestampMillis DESC LIMIT 200")
    fun observeRecent(): Flow<List<CallLogEntryEntity>>
    @Query("SELECT * FROM call_log_entries WHERE timestampMillis BETWEEN :startMillis AND :endMillis ORDER BY timestampMillis DESC")
    suspend fun getBetween(startMillis: Long, endMillis: Long): List<CallLogEntryEntity>
    @Query("SELECT * FROM call_log_entries WHERE displayName LIKE '%' || :keyword || '%' OR number LIKE '%' || :keyword || '%' LIMIT 50")
    suspend fun search(keyword: String): List<CallLogEntryEntity>
}

@Dao
interface LocationVisitDao {
    @Insert suspend fun insert(entity: LocationVisitEntity): Long
    @Query("SELECT * FROM location_visits ORDER BY timestampMillis DESC LIMIT 200")
    fun observeRecent(): Flow<List<LocationVisitEntity>>
    @Query("SELECT * FROM location_visits WHERE timestampMillis BETWEEN :startMillis AND :endMillis ORDER BY timestampMillis DESC")
    suspend fun getBetween(startMillis: Long, endMillis: Long): List<LocationVisitEntity>
}

@Dao
interface AppUsageDao {
    @Insert suspend fun insertAll(entities: List<AppUsageEntity>)
    @Query("DELETE FROM app_usage_daily WHERE epochDay = :epochDay")
    suspend fun clearForDay(epochDay: Long)
    @Query("SELECT * FROM app_usage_daily WHERE epochDay = :epochDay ORDER BY totalTimeMillis DESC")
    fun observeForDay(epochDay: Long): Flow<List<AppUsageEntity>>
    @Query("SELECT * FROM app_usage_daily WHERE epochDay BETWEEN :startDay AND :endDay ORDER BY totalTimeMillis DESC")
    suspend fun getBetween(startDay: Long, endDay: Long): List<AppUsageEntity>
}

@Dao
interface MusicPlayDao {
    @Insert suspend fun insert(entity: MusicPlayEntity): Long
    @Query("SELECT * FROM music_plays ORDER BY timestampMillis DESC LIMIT 200")
    fun observeRecent(): Flow<List<MusicPlayEntity>>
    @Query("SELECT * FROM music_plays WHERE timestampMillis BETWEEN :startMillis AND :endMillis ORDER BY timestampMillis DESC")
    suspend fun getBetween(startMillis: Long, endMillis: Long): List<MusicPlayEntity>
    @Query("SELECT * FROM music_plays WHERE title LIKE '%' || :keyword || '%' OR artist LIKE '%' || :keyword || '%' LIMIT 50")
    suspend fun search(keyword: String): List<MusicPlayEntity>
    @Query("SELECT COUNT(*) FROM music_plays WHERE title = :title AND timestampMillis > :sinceMillis")
    suspend fun countRecentPlays(title: String, sinceMillis: Long): Int
}

@Dao
interface ImageAnalysisDao {
    @Insert suspend fun insert(entity: ImageAnalysisEntity): Long
    @Delete suspend fun delete(entity: ImageAnalysisEntity)
    @Query("SELECT * FROM image_analyses ORDER BY timestampMillis DESC")
    fun observeAll(): Flow<List<ImageAnalysisEntity>>
    @Query("SELECT * FROM image_analyses WHERE labelsCsv LIKE '%' || :keyword || '%' LIMIT 50")
    suspend fun search(keyword: String): List<ImageAnalysisEntity>
}
