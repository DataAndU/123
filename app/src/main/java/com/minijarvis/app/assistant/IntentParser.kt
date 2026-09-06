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

    // ---------------- Whole-phone control ----------------
    data class OpenApp(val appName: String) : AssistantIntent()
    data class MakeCall(val target: String) : AssistantIntent()
    data class SendText(val target: String, val message: String) : AssistantIntent()
    data class ReadLastMessage(val fromContaining: String?) : AssistantIntent()
    data class SetVolume(val stream: String, val raise: Boolean) : AssistantIntent()
    data class MuteVolume(val stream: String, val mute: Boolean) : AssistantIntent()
    data class SetBrightness(val raise: Boolean) : AssistantIntent()
    data class ToggleFlashlight(val on: Boolean) : AssistantIntent()
    data class ToggleWifi(val on: Boolean) : AssistantIntent()
    data class ToggleBluetooth(val on: Boolean) : AssistantIntent()
    data class ToggleDnd(val on: Boolean) : AssistantIntent()
    object TakeScreenshot : AssistantIntent()
    object GoHome : AssistantIntent()
    object GoBack : AssistantIntent()
    object ShowRecents : AssistantIntent()
    object LockScreen : AssistantIntent()
    object ReadScreen : AssistantIntent()
    data class TapOnScreen(val target: String) : AssistantIntent()

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

    // ---------------- Whole-phone control ----------------
    private val openAppPattern = Regex("""(?:open|launch|start)\s+(?:the\s+)?(.+?)(?:\s+app)?$""", RegexOption.IGNORE_CASE)
    private val callPattern = Regex("""(?:call|dial|phone)\s+(.+)""", RegexOption.IGNORE_CASE)
    private val textPattern = Regex("""(?:text|message|sms)\s+(.+?)\s+(?:saying|that says|with message|to say)\s+(.+)""", RegexOption.IGNORE_CASE)
    private val readLastMessagePattern = Regex("""read\s+(?:my\s+)?(?:last|latest|new)\s+(?:message|text|sms)s?(?:\s+from\s+(.+))?""", RegexOption.IGNORE_CASE)
    private val volumeUpPattern = Regex("""(?:turn\s+up|increase|raise)\s+(?:the\s+)?(media|music|ring(?:er)?|alarm)?\s*volume|volume\s+up""", RegexOption.IGNORE_CASE)
    private val volumeDownPattern = Regex("""(?:turn\s+down|decrease|lower)\s+(?:the\s+)?(media|music|ring(?:er)?|alarm)?\s*volume|volume\s+down""", RegexOption.IGNORE_CASE)
    private val muteOnPattern = Regex("""mute\s+(?:the\s+)?(media|music|ring(?:er)?|alarm)?\s*(?:volume|sound)?""", RegexOption.IGNORE_CASE)
    private val muteOffPattern = Regex("""unmute\s+(?:the\s+)?(media|music|ring(?:er)?|alarm)?\s*(?:volume|sound)?""", RegexOption.IGNORE_CASE)
    private val brightnessUpPattern = Regex("""(?:turn\s+up|increase|raise)\s+(?:the\s+)?(?:screen\s+)?brightness|brightness\s+up""", RegexOption.IGNORE_CASE)
    private val brightnessDownPattern = Regex("""(?:turn\s+down|decrease|lower)\s+(?:the\s+)?(?:screen\s+)?brightness|brightness\s+down""", RegexOption.IGNORE_CASE)
    private val flashlightOnPattern = Regex("""(?:turn\s+on|switch\s+on)\s+(?:the\s+)?(?:flashlight|torch|flash)""", RegexOption.IGNORE_CASE)
    private val flashlightOffPattern = Regex("""(?:turn\s+off|switch\s+off)\s+(?:the\s+)?(?:flashlight|torch|flash)""", RegexOption.IGNORE_CASE)
    private val wifiOnPattern = Regex("""(?:turn\s+on|switch\s+on|enable)\s+(?:the\s+)?wi-?fi""", RegexOption.IGNORE_CASE)
    private val wifiOffPattern = Regex("""(?:turn\s+off|switch\s+off|disable)\s+(?:the\s+)?wi-?fi""", RegexOption.IGNORE_CASE)
    private val bluetoothOnPattern = Regex("""(?:turn\s+on|switch\s+on|enable)\s+(?:the\s+)?bluetooth""", RegexOption.IGNORE_CASE)
    private val bluetoothOffPattern = Regex("""(?:turn\s+off|switch\s+off|disable)\s+(?:the\s+)?bluetooth""", RegexOption.IGNORE_CASE)
    private val dndOnPattern = Regex("""(?:turn\s+on|enable)\s+(?:do\s+not\s+disturb|dnd)""", RegexOption.IGNORE_CASE)
    private val dndOffPattern = Regex("""(?:turn\s+off|disable)\s+(?:do\s+not\s+disturb|dnd)""", RegexOption.IGNORE_CASE)
    private val screenshotPattern = Regex("""take\s+a\s+screenshot|screenshot\s+(?:this|my\s+screen)""", RegexOption.IGNORE_CASE)
    private val goHomePattern = Regex("""go\s+home|home\s+screen|go\s+to\s+the\s+home\s+screen""", RegexOption.IGNORE_CASE)
    private val goBackPattern = Regex("""go\s+back|press\s+back""", RegexOption.IGNORE_CASE)
    private val recentsPattern = Regex("""(?:show|open)\s+recent(?:\s+apps)?""", RegexOption.IGNORE_CASE)
    private val lockPattern = Regex("""lock\s+(?:my\s+)?(?:the\s+)?(?:phone|screen)""", RegexOption.IGNORE_CASE)
    private val readScreenPattern = Regex("""what'?s\s+on\s+(?:my\s+)?screen|read\s+(?:my\s+)?(?:the\s+)?screen""", RegexOption.IGNORE_CASE)
    private val tapPattern = Regex("""(?:tap|click|press)\s+(?:on\s+)?(?:the\s+)?(.+?)(?:\s+button)?$""", RegexOption.IGNORE_CASE)

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

        // Whole-phone control — checked before the generic greeting/search/medicine fallbacks.
        textPattern.find(input)?.let {
            return AssistantIntent.SendText(it.groupValues[1].trim(), it.groupValues[2].trim())
        }
        callPattern.find(input)?.let {
            return AssistantIntent.MakeCall(it.groupValues[1].trim())
        }
        readLastMessagePattern.find(input)?.let {
            val from = it.groupValues[1].trim().ifEmpty { null }
            return AssistantIntent.ReadLastMessage(from)
        }
        if (volumeUpPattern.containsMatchIn(input)) return AssistantIntent.SetVolume(streamFrom(volumeUpPattern.find(input)), raise = true)
        if (volumeDownPattern.containsMatchIn(input)) return AssistantIntent.SetVolume(streamFrom(volumeDownPattern.find(input)), raise = false)
        if (muteOnPattern.containsMatchIn(input)) return AssistantIntent.MuteVolume(streamFrom(muteOnPattern.find(input)), mute = true)
        if (muteOffPattern.containsMatchIn(input)) return AssistantIntent.MuteVolume(streamFrom(muteOffPattern.find(input)), mute = false)
        if (brightnessUpPattern.containsMatchIn(input)) return AssistantIntent.SetBrightness(raise = true)
        if (brightnessDownPattern.containsMatchIn(input)) return AssistantIntent.SetBrightness(raise = false)
        if (flashlightOnPattern.containsMatchIn(input)) return AssistantIntent.ToggleFlashlight(on = true)
        if (flashlightOffPattern.containsMatchIn(input)) return AssistantIntent.ToggleFlashlight(on = false)
        if (wifiOnPattern.containsMatchIn(input)) return AssistantIntent.ToggleWifi(on = true)
        if (wifiOffPattern.containsMatchIn(input)) return AssistantIntent.ToggleWifi(on = false)
        if (bluetoothOnPattern.containsMatchIn(input)) return AssistantIntent.ToggleBluetooth(on = true)
        if (bluetoothOffPattern.containsMatchIn(input)) return AssistantIntent.ToggleBluetooth(on = false)
        if (dndOnPattern.containsMatchIn(input)) return AssistantIntent.ToggleDnd(on = true)
        if (dndOffPattern.containsMatchIn(input)) return AssistantIntent.ToggleDnd(on = false)
        if (screenshotPattern.containsMatchIn(input)) return AssistantIntent.TakeScreenshot
        if (goHomePattern.containsMatchIn(input)) return AssistantIntent.GoHome
        if (goBackPattern.containsMatchIn(input)) return AssistantIntent.GoBack
        if (recentsPattern.containsMatchIn(input)) return AssistantIntent.ShowRecents
        if (lockPattern.containsMatchIn(input)) return AssistantIntent.LockScreen
        if (readScreenPattern.containsMatchIn(input)) return AssistantIntent.ReadScreen
        openAppPattern.find(input)?.let {
            return AssistantIntent.OpenApp(it.groupValues[1].trim())
        }

        if (greetingPattern.containsMatchIn(input)) return AssistantIntent.Greeting
        if (helpPattern.containsMatchIn(input)) return AssistantIntent.Help
        searchPattern.find(input)?.let {
            return AssistantIntent.Search(it.groupValues[1].trim())
        }
        tapPattern.find(input)?.let {
            val target = it.groupValues[1].trim()
            if (target.isNotEmpty()) return AssistantIntent.TapOnScreen(target)
        }
        medicinePattern.find(input)?.let {
            val name = it.groupValues[1].trim()
            if (name.isNotEmpty()) return AssistantIntent.LogMedicine(name)
        }
        return AssistantIntent.Unknown(input)
    }

    private fun streamFrom(match: MatchResult?): String {
        val raw = match?.groupValues?.getOrNull(1)?.trim()?.lowercase().orEmpty()
        return when {
            raw.startsWith("ring") -> "ring"
            raw.startsWith("alarm") -> "alarm"
            else -> "media"
        }
    }
}
