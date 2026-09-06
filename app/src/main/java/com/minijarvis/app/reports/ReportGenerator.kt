package com.minijarvis.app.reports

import com.minijarvis.app.data.ExpenseRepository
import com.minijarvis.app.data.FoodRepository
import com.minijarvis.app.data.HabitRepository
import com.minijarvis.app.data.MedicineRepository
import com.minijarvis.app.data.TaskRepository
import com.minijarvis.app.data.WeightRepository
import com.minijarvis.app.util.TimeUtils

enum class ReportPeriod { DAILY, WEEKLY, MONTHLY }

data class PeriodReport(
    val period: ReportPeriod,
    val rangeStartMillis: Long,
    val rangeEndMillis: Long,
    val totalSpent: Double,
    val totalCaloriesLogged: Int,
    val medicineDosesTaken: Int,
    val medicineDosesMissed: Int,
    val latestWeightKg: Double?,
    val habitsCompleted: Int,
    val habitsTracked: Int,
    val tasksCompleted: Int,
    val tasksDue: Int
)

/** Aggregates every tracker's local data into a single daily/weekly/monthly snapshot. */
class ReportGenerator(
    private val expenseRepository: ExpenseRepository,
    private val foodRepository: FoodRepository,
    private val medicineRepository: MedicineRepository,
    private val weightRepository: WeightRepository,
    private val habitRepository: HabitRepository,
    private val taskRepository: TaskRepository
) {

    suspend fun generate(period: ReportPeriod): PeriodReport {
        val (start, end) = when (period) {
            ReportPeriod.DAILY -> TimeUtils.startOfDayMillis() to TimeUtils.endOfDayMillis()
            ReportPeriod.WEEKLY -> TimeUtils.startOfWeekMillis() to TimeUtils.endOfWeekMillis()
            ReportPeriod.MONTHLY -> TimeUtils.startOfMonthMillis() to TimeUtils.endOfMonthMillis()
        }

        val totalSpent = expenseRepository.totalBetween(start, end)
        val totalCalories = foodRepository.caloriesBetween(start, end)
        val doseLogs = medicineRepository.doseLogsBetween(start, end)
        val dosesTaken = doseLogs.count { it.taken }
        val dosesMissed = doseLogs.count { !it.taken }
        val latestWeight = weightRepository.latest()?.weightKg

        val startDay = TimeUtils.toEpochDay(start)
        val endDay = TimeUtils.toEpochDay(end)
        val habitLogs = habitRepository.logsBetween(startDay, endDay)
        val habitsCompleted = habitLogs.count { it.completed }
        val habitsTracked = habitLogs.map { it.habitId }.distinct().size

        val dueTasks = taskRepository.dueBetween(start, end)
        val tasksCompleted = dueTasks.count { it.completed }

        return PeriodReport(
            period = period,
            rangeStartMillis = start,
            rangeEndMillis = end,
            totalSpent = totalSpent,
            totalCaloriesLogged = totalCalories,
            medicineDosesTaken = dosesTaken,
            medicineDosesMissed = dosesMissed,
            latestWeightKg = latestWeight,
            habitsCompleted = habitsCompleted,
            habitsTracked = habitsTracked,
            tasksCompleted = tasksCompleted,
            tasksDue = dueTasks.size
        )
    }
}
