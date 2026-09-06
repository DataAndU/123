package com.minijarvis.app.llm

import com.minijarvis.app.assistant.AssistantIntent

/**
 * The "agent" loop: prompts the local model with the tool catalog plus a bit
 * of recent conversation, parses however many TOOL calls it produced, and
 * runs each one through the exact same execution path the rule-based parser
 * uses ([executeIntent] — see AssistantEngine.executeIntent) so every
 * permission check, fallback message, and side effect behaves identically
 * whether the request was matched by regex or decided by the model.
 */
class AgentOrchestrator(
    private val localLlmEngine: LocalLlmEngine,
    private val executeIntent: suspend (AssistantIntent) -> String
) {

    suspend fun handle(userInput: String, recentHistory: String): String {
        val prompt = buildString {
            append(AgentTools.systemPrompt())
            append("\n\n")
            if (recentHistory.isNotBlank()) {
                append("Recent conversation:\n")
                append(recentHistory)
                append("\n\n")
            }
            append("User: ")
            append(userInput)
        }

        val generated = localLlmEngine.generate(prompt).getOrElse {
            return "The local model had trouble responding (${it.message}). You can still use plain commands like \"open camera\" without it."
        }

        val calls = AgentResponseParser.parse(generated)
        val results = mutableListOf<String>()
        for (call in calls) {
            if (call.name == "reply") {
                results.add(call.args["text"]?.takeIf { it.isNotBlank() } ?: generated.trim())
                continue
            }
            val intent = toIntent(call)
            if (intent != null) results.add(executeIntent(intent))
        }
        return if (results.isEmpty()) generated.trim() else results.joinToString("\n")
    }

    private fun toIntent(call: ParsedToolCall): AssistantIntent? {
        fun arg(key: String) = call.args[key]
        fun argBool(key: String) = call.args[key]?.equals("true", ignoreCase = true) == true

        return when (call.name) {
            "add_expense" -> arg("amount")?.toDoubleOrNull()?.let { AssistantIntent.AddExpense(it, arg("category") ?: "misc") }
            "log_weight" -> arg("kg")?.toDoubleOrNull()?.let { AssistantIntent.LogWeight(it) }
            "add_food" -> arg("name")?.let { AssistantIntent.AddFood(it, arg("meal") ?: "snack") }
            "add_task" -> arg("title")?.let { AssistantIntent.AddTask(it) }
            "log_habit" -> arg("name")?.let { AssistantIntent.LogHabit(it) }
            "log_medicine" -> arg("name")?.let { AssistantIntent.LogMedicine(it) }
            "search" -> arg("query")?.let { AssistantIntent.Search(it) }
            "open_app" -> arg("app")?.let { AssistantIntent.OpenApp(it) }
            "call" -> arg("target")?.let { AssistantIntent.MakeCall(it) }
            "send_text" -> {
                val target = arg("target")
                val message = arg("message")
                if (target != null && message != null) AssistantIntent.SendText(target, message) else null
            }
            "read_last_message" -> AssistantIntent.ReadLastMessage(arg("from"))
            "set_volume" -> AssistantIntent.SetVolume(arg("stream") ?: "media", arg("direction") != "down")
            "mute_volume" -> AssistantIntent.MuteVolume(arg("stream") ?: "media", argBool("mute"))
            "set_brightness" -> AssistantIntent.SetBrightness(arg("direction") != "down")
            "toggle_flashlight" -> AssistantIntent.ToggleFlashlight(argBool("on"))
            "toggle_wifi" -> AssistantIntent.ToggleWifi(argBool("on"))
            "toggle_bluetooth" -> AssistantIntent.ToggleBluetooth(argBool("on"))
            "toggle_dnd" -> AssistantIntent.ToggleDnd(argBool("on"))
            "take_screenshot" -> AssistantIntent.TakeScreenshot
            "go_home" -> AssistantIntent.GoHome
            "go_back" -> AssistantIntent.GoBack
            "show_recents" -> AssistantIntent.ShowRecents
            "lock_screen" -> AssistantIntent.LockScreen
            "read_screen" -> AssistantIntent.ReadScreen
            "tap" -> arg("target")?.let { AssistantIntent.TapOnScreen(it) }
            else -> null
        }
    }
}
