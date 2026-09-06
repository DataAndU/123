package com.minijarvis.app.assistant

/**
 * Everything the assistant can be asked to do. Parsing is 100% local,
 * rule-based pattern matching — there is no bundled LLM and no network
 * call, so recognition is intentionally simple and keyword-driven rather
 * than a general conversational model.
 */
sealed class AssistantIntent {
    data class AddExpense(val amount: Double, val category: String) : AssistantIntent()
    data class LogWeight(val kg: Double) : AssistantIntent()
    data class AddFood(val name: String, val mealType: String) : AssistantIntent()
    data class AddTask(val title: String) : AssistantIntent()
    data class LogHabit(val habitName: String) : AssistantIntent()
    data class LogMedicine(val medicineName: String) : AssistantIntent()
    data class Search(val query: String) : AssistantIntent()
    object Greeting : AssistantIntent()
    object Help : AssistantIntent()
    data class Unknown(val raw: String) : AssistantIntent()
}

object IntentParser {

    private val expensePattern =
        Regex("""(?:spent|paid|spend)\s+(?:rs\.?|inr|₹)?\s*(\d+(?:\.\d+)?)\s*(?:rs\.?|rupees|inr)?\s*(?:on|for)\s+(.+)""", RegexOption.IGNORE_CASE)
    private val weightPattern =
        Regex("""(?:weigh|weight|i\s*weigh)\D*(\d+(?:\.\d+)?)\s*(?:kg|kgs|kilograms?)?""", RegexOption.IGNORE_CASE)
    private val foodPattern =
        Regex("""(?:ate|eating|had)\s+(.+?)\s+for\s+(breakfast|lunch|dinner|snack)""", RegexOption.IGNORE_CASE)
    private val taskPattern =
        Regex("""(?:remind me to|add task|create task|task:)\s+(.+)""", RegexOption.IGNORE_CASE)
    private val habitPattern =
        Regex("""(?:did|completed|finished)\s+(?:my\s+)?(.+?)\s+habit""", RegexOption.IGNORE_CASE)
    private val medicinePattern =
        Regex("""(?:took|taking)\s+(?:my\s+)?(.+?)(?:\s+medicine|\s+pill|\s+tablet)?$""", RegexOption.IGNORE_CASE)
    private val searchPattern =
        Regex("""(?:search|find|show me|how much|how many)\s+(.+)""", RegexOption.IGNORE_CASE)
    private val greetingPattern = Regex("""^(hi|hello|hey|namaste|vanakkam)\b""", RegexOption.IGNORE_CASE)
    private val helpPattern = Regex("""help|what can you do""", RegexOption.IGNORE_CASE)

    fun parse(rawInput: String): AssistantIntent {
        val input = rawInput.trim()
        if (input.isEmpty()) return AssistantIntent.Unknown(rawInput)

        expensePattern.find(input)?.let {
            val amount = it.groupValues[1].toDoubleOrNull()
            if (amount != null) return AssistantIntent.AddExpense(amount, it.groupValues[2].trim())
        }
        weightPattern.find(input)?.let {
            val kg = it.groupValues[1].toDoubleOrNull()
            if (kg != null) return AssistantIntent.LogWeight(kg)
        }
        foodPattern.find(input)?.let {
            return AssistantIntent.AddFood(it.groupValues[1].trim(), it.groupValues[2].lowercase())
        }
        taskPattern.find(input)?.let {
            return AssistantIntent.AddTask(it.groupValues[1].trim())
        }
        habitPattern.find(input)?.let {
            return AssistantIntent.LogHabit(it.groupValues[1].trim())
        }
        if (greetingPattern.containsMatchIn(input)) return AssistantIntent.Greeting
        if (helpPattern.containsMatchIn(input)) return AssistantIntent.Help
        searchPattern.find(input)?.let {
            return AssistantIntent.Search(it.groupValues[1].trim())
        }
        medicinePattern.find(input)?.let {
            val name = it.groupValues[1].trim()
            if (name.isNotEmpty()) return AssistantIntent.LogMedicine(name)
        }
        return AssistantIntent.Unknown(input)
    }
}
