package com.minijarvis.app.search

import com.minijarvis.app.data.CallLogRepository
import com.minijarvis.app.data.ExpenseRepository
import com.minijarvis.app.data.FoodRepository
import com.minijarvis.app.data.ImageAnalysisRepository
import com.minijarvis.app.data.MedicineRepository
import com.minijarvis.app.data.MusicHistoryRepository
import com.minijarvis.app.data.TaskRepository
import com.minijarvis.app.util.TimeUtils

data class SearchResult(
    val module: String,
    val title: String,
    val subtitle: String,
    val timestampMillis: Long
)

/**
 * A small local "natural language" search: it strips common date phrases
 * ("last week", "this month", ...) out of the query, then keyword-matches
 * whatever remains across every module's local table. Everything here runs
 * against the on-device encrypted database only.
 */
class SmartSearchEngine(
    private val expenseRepository: ExpenseRepository,
    private val foodRepository: FoodRepository,
    private val medicineRepository: MedicineRepository,
    private val taskRepository: TaskRepository,
    private val callLogRepository: CallLogRepository,
    private val musicHistoryRepository: MusicHistoryRepository,
    private val imageAnalysisRepository: ImageAnalysisRepository
) {

    suspend fun search(rawQuery: String): List<SearchResult> {
        val dateRange = TimeUtils.parseRelativeDatePhrase(rawQuery)
        val keyword = stripDatePhrases(rawQuery).trim()
        val results = mutableListOf<SearchResult>()

        if (dateRange != null && keyword.isEmpty()) {
            // Pure date query, e.g. "what did I spend last week" without a keyword left over.
            val (start, end) = dateRange
            expenseRepository.getBetween(start, end).forEach {
                results += SearchResult("Expense", "${it.category} — ₹${it.amount}", it.note, it.timestampMillis)
            }
            foodRepository.getBetween(start, end).forEach {
                results += SearchResult("Food", it.name, it.mealType, it.timestampMillis)
            }
            return results.sortedByDescending { it.timestampMillis }
        }

        if (keyword.isEmpty()) return emptyList()

        expenseRepository.search(keyword).forEach {
            results += SearchResult("Expense", "${it.category} — ₹${it.amount}", it.note, it.timestampMillis)
        }
        foodRepository.search(keyword).forEach {
            results += SearchResult("Food", it.name, it.mealType, it.timestampMillis)
        }
        medicineRepository.search(keyword).forEach {
            results += SearchResult("Medicine", it.name, it.dosage, it.createdAtMillis)
        }
        taskRepository.search(keyword).forEach {
            results += SearchResult("Task", it.title, it.description, it.createdAtMillis)
        }
        callLogRepository.search(keyword).forEach {
            results += SearchResult("Call", it.displayName ?: it.number, it.type, it.timestampMillis)
        }
        musicHistoryRepository.search(keyword).forEach {
            results += SearchResult("Music", it.title, it.artist ?: "", it.timestampMillis)
        }
        imageAnalysisRepository.search(keyword).forEach {
            results += SearchResult("Photo", it.labelsCsv, it.recognizedText ?: "", it.timestampMillis)
        }

        val filtered = if (dateRange != null) {
            val (start, end) = dateRange
            results.filter { it.timestampMillis in start..end }
        } else results

        return filtered.sortedByDescending { it.timestampMillis }
    }

    private fun stripDatePhrases(text: String): String {
        var result = text
        listOf("today", "yesterday", "this week", "last week", "this month", "last month").forEach {
            result = result.replace(Regex(it, RegexOption.IGNORE_CASE), "")
        }
        return result.replace(Regex("""\s+"""), " ")
    }
}
