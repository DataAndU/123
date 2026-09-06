package com.minijarvis.app.data

import com.minijarvis.app.util.TimeUtils
import kotlinx.coroutines.flow.Flow

class ExpenseRepository(private val dao: ExpenseDao) {
    fun observeAll(): Flow<List<ExpenseEntity>> = dao.observeAll()
    suspend fun add(amount: Double, category: String, note: String, atMillis: Long = TimeUtils.nowMillis()) =
        dao.insert(ExpenseEntity(amount = amount, category = category, note = note, timestampMillis = atMillis))
    suspend fun update(entity: ExpenseEntity) = dao.update(entity)
    suspend fun delete(entity: ExpenseEntity) = dao.delete(entity)
    suspend fun totalBetween(startMillis: Long, endMillis: Long) = dao.sumBetween(startMillis, endMillis)
    suspend fun getBetween(startMillis: Long, endMillis: Long) = dao.getBetween(startMillis, endMillis)
    suspend fun search(keyword: String) = dao.search(keyword)
}

class FoodRepository(private val dao: FoodDao) {
    fun observeAll(): Flow<List<FoodEntity>> = dao.observeAll()
    suspend fun add(name: String, mealType: String, calories: Int?, note: String, atMillis: Long = TimeUtils.nowMillis()) =
        dao.insert(FoodEntity(name = name, mealType = mealType, calories = calories, note = note, timestampMillis = atMillis))
    suspend fun update(entity: FoodEntity) = dao.update(entity)
    suspend fun delete(entity: FoodEntity) = dao.delete(entity)
    suspend fun caloriesBetween(startMillis: Long, endMillis: Long) = dao.sumCaloriesBetween(startMillis, endMillis)
    suspend fun getBetween(startMillis: Long, endMillis: Long) = dao.getBetween(startMillis, endMillis)
    suspend fun search(keyword: String) = dao.search(keyword)
}

class MedicineRepository(private val dao: MedicineDao) {
    fun observeActive(): Flow<List<MedicineEntity>> = dao.observeActive()
    fun observeAll(): Flow<List<MedicineEntity>> = dao.observeAll()
    fun observeDoseLogs(): Flow<List<MedicineDoseLogEntity>> = dao.observeDoseLogs()

    suspend fun addMedicine(name: String, dosage: String, scheduleNote: String, atMillis: Long = TimeUtils.nowMillis()) =
        dao.insert(MedicineEntity(name = name, dosage = dosage, scheduleNote = scheduleNote, active = true, createdAtMillis = atMillis))
    suspend fun update(entity: MedicineEntity) = dao.update(entity)
    suspend fun delete(entity: MedicineEntity) = dao.delete(entity)
    suspend fun logDose(medicine: MedicineEntity, taken: Boolean, atMillis: Long = TimeUtils.nowMillis()) =
        dao.insertDoseLog(MedicineDoseLogEntity(medicineId = medicine.id, medicineName = medicine.name, takenAtMillis = atMillis, taken = taken))
    suspend fun doseLogsBetween(startMillis: Long, endMillis: Long) = dao.getDoseLogsBetween(startMillis, endMillis)
    suspend fun search(keyword: String) = dao.search(keyword)
}

class WeightRepository(private val dao: WeightDao) {
    fun observeAll(): Flow<List<WeightEntity>> = dao.observeAll()
    fun observeHealthMetrics(): Flow<List<HealthMetricEntity>> = dao.observeHealthMetrics()
    suspend fun add(weightKg: Double, note: String, atMillis: Long = TimeUtils.nowMillis()) =
        dao.insert(WeightEntity(weightKg = weightKg, note = note, timestampMillis = atMillis))
    suspend fun delete(entity: WeightEntity) = dao.delete(entity)
    suspend fun latest() = dao.getLatest()
    suspend fun getBetween(startMillis: Long, endMillis: Long) = dao.getBetween(startMillis, endMillis)
    suspend fun addHealthMetric(type: String, value: Double, unit: String, atMillis: Long = TimeUtils.nowMillis()) =
        dao.insertHealthMetric(HealthMetricEntity(type = type, value = value, unit = unit, timestampMillis = atMillis))
}

class HabitRepository(private val dao: HabitDao) {
    fun observeActive(): Flow<List<HabitEntity>> = dao.observeActive()
    fun observeLogsForHabit(habitId: Long): Flow<List<HabitLogEntity>> = dao.observeLogsForHabit(habitId)

    suspend fun addHabit(name: String, targetPerWeek: Int, atMillis: Long = TimeUtils.nowMillis()) =
        dao.insert(HabitEntity(name = name, targetPerWeek = targetPerWeek, active = true, createdAtMillis = atMillis))
    suspend fun update(entity: HabitEntity) = dao.update(entity)
    suspend fun delete(entity: HabitEntity) = dao.delete(entity)

    /** Toggles today's (or [epochDay]'s) completion state for a habit. */
    suspend fun toggleForDay(habitId: Long, epochDay: Long = TimeUtils.toEpochDay()) {
        val existing = dao.getLogForDay(habitId, epochDay)
        if (existing != null) {
            dao.deleteLogForDay(habitId, epochDay)
        } else {
            dao.insertLog(HabitLogEntity(habitId = habitId, epochDay = epochDay, completed = true))
        }
    }

    suspend fun isCompletedForDay(habitId: Long, epochDay: Long = TimeUtils.toEpochDay()): Boolean =
        dao.getLogForDay(habitId, epochDay) != null

    suspend fun logsBetween(startDay: Long, endDay: Long) = dao.getLogsBetween(startDay, endDay)
}

class TaskRepository(private val dao: TaskDao) {
    fun observeAll(): Flow<List<TaskEntity>> = dao.observeAll()
    suspend fun add(
        title: String,
        description: String,
        dueAtMillis: Long?,
        reminderAtMillis: Long?,
        atMillis: Long = TimeUtils.nowMillis()
    ) = dao.insert(
        TaskEntity(
            title = title,
            description = description,
            dueAtMillis = dueAtMillis,
            reminderAtMillis = reminderAtMillis,
            completed = false,
            createdAtMillis = atMillis
        )
    )
    suspend fun update(entity: TaskEntity) = dao.update(entity)
    suspend fun delete(entity: TaskEntity) = dao.delete(entity)
    suspend fun setCompleted(entity: TaskEntity, completed: Boolean) = dao.update(entity.copy(completed = completed))
    suspend fun dueBetween(startMillis: Long, endMillis: Long) = dao.getDueBetween(startMillis, endMillis)
    suspend fun search(keyword: String) = dao.search(keyword)
}

class ChatRepository(private val dao: ChatDao) {
    fun observeAll(): Flow<List<ChatMessageEntity>> = dao.observeAll()
    suspend fun addMessage(role: String, content: String, source: String, atMillis: Long = TimeUtils.nowMillis()) =
        dao.insert(ChatMessageEntity(role = role, content = content, source = source, timestampMillis = atMillis))
    suspend fun clear() = dao.clearAll()
}
