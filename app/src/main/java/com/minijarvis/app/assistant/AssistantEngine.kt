package com.minijarvis.app.assistant

import com.minijarvis.app.data.ChatRepository
import com.minijarvis.app.data.ExpenseRepository
import com.minijarvis.app.data.FoodRepository
import com.minijarvis.app.data.HabitRepository
import com.minijarvis.app.data.MedicineRepository
import com.minijarvis.app.data.TaskRepository
import com.minijarvis.app.data.WeightRepository
import com.minijarvis.app.search.SmartSearchEngine
import com.minijarvis.app.system.ReminderScheduler
import kotlinx.coroutines.flow.first

/**
 * The local "brain" behind both text and voice chat. It is a rule-based
 * router over the modules below, not a generative model — every reply is
 * produced from data already stored on this device.
 */
class AssistantEngine(
    private val expenseRepository: ExpenseRepository,
    private val foodRepository: FoodRepository,
    private val medicineRepository: MedicineRepository,
    private val weightRepository: WeightRepository,
    private val habitRepository: HabitRepository,
    private val taskRepository: TaskRepository,
    private val chatRepository: ChatRepository,
    private val reminderScheduler: ReminderScheduler,
    private val searchEngine: SmartSearchEngine
) {

    suspend fun handle(userInput: String, source: String = "text"): String {
        chatRepository.addMessage(role = "user", content = userInput, source = source)
        val reply = respond(userInput)
        chatRepository.addMessage(role = "assistant", content = reply, source = source)
        return reply
    }

    private suspend fun respond(userInput: String): String {
        return when (val intent = IntentParser.parse(userInput)) {
            is AssistantIntent.Greeting ->
                "Hi, I'm Mini JARVIS — fully offline, running only on this device. Ask me to log an expense, meal, habit, or search your data."

            is AssistantIntent.Help ->
                "Try: \"spent 200 on lunch\", \"I weigh 68 kg\", \"ate rice for dinner\", \"remind me to call mom\", " +
                    "\"did my running habit\", \"took my vitamin\", or \"search expenses last week\"."

            is AssistantIntent.AddExpense -> {
                expenseRepository.add(amount = intent.amount, category = intent.category, note = "via assistant")
                "Logged ₹${intent.amount} under \"${intent.category}\"."
            }

            is AssistantIntent.LogWeight -> {
                weightRepository.add(weightKg = intent.kg, note = "via assistant")
                "Logged weight: ${intent.kg} kg."
            }

            is AssistantIntent.AddFood -> {
                foodRepository.add(name = intent.name, mealType = intent.mealType, calories = null, note = "via assistant")
                "Logged \"${intent.name}\" for ${intent.mealType}."
            }

            is AssistantIntent.AddTask -> {
                taskRepository.add(title = intent.title, description = "", dueAtMillis = null, reminderAtMillis = null)
                "Added task: \"${intent.title}\"."
            }

            is AssistantIntent.LogHabit -> {
                val habit = habitRepository.observeActive().first()
                    .firstOrNull { it.name.contains(intent.habitName, ignoreCase = true) }
                if (habit == null) {
                    "I couldn't find a habit called \"${intent.habitName}\". Add it first from the Habits screen."
                } else {
                    habitRepository.toggleForDay(habit.id)
                    "Marked \"${habit.name}\" done for today."
                }
            }

            is AssistantIntent.LogMedicine -> {
                val match = medicineRepository.search(intent.medicineName).firstOrNull()
                if (match == null) {
                    "I couldn't find a medicine called \"${intent.medicineName}\". Add it first from the Medicine screen."
                } else {
                    medicineRepository.logDose(match, taken = true)
                    "Logged \"${match.name}\" as taken."
                }
            }

            is AssistantIntent.Search -> {
                val results = searchEngine.search(intent.query)
                if (results.isEmpty()) {
                    "No local results for \"${intent.query}\"."
                } else {
                    val preview = results.take(5).joinToString("\n") { "• [${it.module}] ${it.title}" }
                    "Found ${results.size} result(s):\n$preview"
                }
            }

            is AssistantIntent.Unknown ->
                "I didn't catch that. Say \"help\" to see what I can do — everything stays on this device."
        }
    }

    /** Kept for symmetry with the reminder module; not auto-invoked by chat parsing. */
    fun scheduleReminderFor(taskId: Long, title: String, triggerAtMillis: Long) {
        reminderScheduler.scheduleTaskReminder(taskId, title, triggerAtMillis)
    }
}
