package com.minijarvis.app.assistant

import android.content.Context
import android.content.Intent
import com.minijarvis.app.control.AppLauncher
import com.minijarvis.app.control.CallResult
import com.minijarvis.app.control.JarvisAccessibilityService
import com.minijarvis.app.control.PhoneActionsManager
import com.minijarvis.app.control.SmsResult
import com.minijarvis.app.control.SystemControlManager
import com.minijarvis.app.control.VolumeStream
import com.minijarvis.app.data.ChatRepository
import com.minijarvis.app.data.ExpenseRepository
import com.minijarvis.app.data.FoodRepository
import com.minijarvis.app.data.HabitRepository
import com.minijarvis.app.data.MedicineRepository
import com.minijarvis.app.data.TaskRepository
import com.minijarvis.app.data.WeightRepository
import com.minijarvis.app.search.SmartSearchEngine
import com.minijarvis.app.system.ReminderScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * The local "brain" behind both text and voice chat. It is a rule-based
 * router over the modules below, not a generative model — every reply is
 * produced from data already stored on this device (or a real, immediate
 * action against the phone itself for the control intents).
 */
class AssistantEngine(
    private val appContext: Context,
    private val expenseRepository: ExpenseRepository,
    private val foodRepository: FoodRepository,
    private val medicineRepository: MedicineRepository,
    private val weightRepository: WeightRepository,
    private val habitRepository: HabitRepository,
    private val taskRepository: TaskRepository,
    private val chatRepository: ChatRepository,
    private val reminderScheduler: ReminderScheduler,
    private val searchEngine: SmartSearchEngine,
    private val appLauncher: AppLauncher,
    private val systemControlManager: SystemControlManager,
    private val phoneActionsManager: PhoneActionsManager
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
                "Hi, I'm Mini JARVIS — fully offline, running only on this device. Ask me to log an expense, meal, or habit, control your phone, or search your data."

            is AssistantIntent.Help ->
                "Try: \"spent 200 on lunch\", \"I weigh 68 kg\", \"ate rice for dinner\", \"remind me to call mom\", " +
                    "\"did my running habit\", \"took my vitamin\", \"search expenses last week\", \"call mom\", " +
                    "\"text mom saying I'm on my way\", \"open camera\", \"turn on the flashlight\", \"turn up the volume\", " +
                    "\"go home\", \"take a screenshot\", or \"what's on my screen\"."

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

            is AssistantIntent.OpenApp -> {
                if (appLauncher.launchByName(intent.appName)) "Opening ${intent.appName}."
                else "I couldn't find an app called \"${intent.appName}\" on this phone."
            }

            is AssistantIntent.MakeCall -> {
                when (val result = phoneActionsManager.call(intent.target)) {
                    is CallResult.PlacedDirectly -> "Calling ${intent.target}."
                    is CallResult.OpenedDialer -> "Opened the dialer for ${intent.target} — grant Call permission in Settings so I can dial directly."
                    is CallResult.ContactNotFound -> "I couldn't find a contact or number for \"${result.name}\"."
                }
            }

            is AssistantIntent.SendText -> {
                when (val result = phoneActionsManager.sendSms(intent.target, intent.message)) {
                    is SmsResult.Sent -> "Sent to ${intent.target}: \"${intent.message}\"."
                    is SmsResult.MissingPermission -> "I need SMS permission to send that — grant it in Settings."
                    is SmsResult.ContactNotFound -> "I couldn't find a contact or number for \"${result.name}\"."
                }
            }

            is AssistantIntent.ReadLastMessage -> {
                val message = phoneActionsManager.lastIncomingMessage(intent.fromContaining)
                when {
                    message == null && intent.fromContaining != null -> "No recent message from \"${intent.fromContaining}\", or I don't have SMS permission yet."
                    message == null -> "No recent messages, or I don't have SMS permission yet."
                    else -> "Message from ${message.first}: \"${message.second}\""
                }
            }

            is AssistantIntent.SetVolume -> {
                val stream = streamFor(intent.stream)
                if (systemControlManager.adjustVolume(stream, intent.raise)) "${if (intent.raise) "Raised" else "Lowered"} ${intent.stream} volume."
                else "I need Do Not Disturb access to change ringer volume — grant it in Settings."
            }

            is AssistantIntent.MuteVolume -> {
                val stream = streamFor(intent.stream)
                if (systemControlManager.setMuted(stream, intent.mute)) "${if (intent.mute) "Muted" else "Unmuted"} ${intent.stream}."
                else "I need Do Not Disturb access to mute the ringer — grant it in Settings."
            }

            is AssistantIntent.SetBrightness -> {
                if (systemControlManager.adjustBrightness(intent.raise)) "${if (intent.raise) "Increased" else "Decreased"} screen brightness."
                else "I need the \"Modify system settings\" permission for brightness — grant it in Settings."
            }

            is AssistantIntent.ToggleFlashlight -> {
                if (systemControlManager.setFlashlight(intent.on)) "Flashlight ${if (intent.on) "on" else "off"}."
                else "This device doesn't seem to have a flashlight I can control."
            }

            is AssistantIntent.ToggleWifi -> handleRadioToggle("WiFi", intent.on, systemControlManager.wifiPanelIntent(), listOf("wifi", "wi-fi"))

            is AssistantIntent.ToggleBluetooth -> handleRadioToggle("Bluetooth", intent.on, systemControlManager.bluetoothSettingsIntent(), listOf("bluetooth"))

            is AssistantIntent.ToggleDnd -> {
                if (systemControlManager.setDoNotDisturb(intent.on)) "Do Not Disturb ${if (intent.on) "on" else "off"}."
                else "I need Do Not Disturb access — grant it in Settings, then try again."
            }

            is AssistantIntent.TakeScreenshot -> withAccessibility { it.takeScreenshot() }?.let {
                if (it) "Screenshot taken." else "Couldn't take a screenshot."
            } ?: accessibilityRequiredMessage()

            is AssistantIntent.GoHome -> withAccessibility { it.goHome() }?.let { "Going home." } ?: accessibilityRequiredMessage()
            is AssistantIntent.GoBack -> withAccessibility { it.goBack() }?.let { "Going back." } ?: accessibilityRequiredMessage()
            is AssistantIntent.ShowRecents -> withAccessibility { it.showRecents() }?.let { "Here are your recent apps." } ?: accessibilityRequiredMessage()
            is AssistantIntent.LockScreen -> withAccessibility { it.lockScreen() }?.let { "Locking the screen." } ?: accessibilityRequiredMessage()

            is AssistantIntent.ReadScreen -> withAccessibility { it.readScreenText() }?.let { text ->
                if (text.isBlank()) "The screen doesn't seem to have readable text right now." else text.take(600)
            } ?: accessibilityRequiredMessage()

            is AssistantIntent.TapOnScreen -> withAccessibility { it.clickByText(intent.target) }?.let {
                if (it) "Tapped \"${intent.target}\"." else "I couldn't find anything called \"${intent.target}\" on screen."
            } ?: accessibilityRequiredMessage()

            is AssistantIntent.Unknown ->
                "I didn't catch that. Say \"help\" to see what I can do — everything stays on this device."
        }
    }

    private fun streamFor(name: String): VolumeStream = when (name) {
        "ring" -> VolumeStream.RING
        "alarm" -> VolumeStream.ALARM
        else -> VolumeStream.MEDIA
    }

    private fun <T> withAccessibility(action: (JarvisAccessibilityService) -> T): T? =
        JarvisAccessibilityService.current()?.let(action)

    private fun accessibilityRequiredMessage() =
        "That needs the Accessibility Service enabled — turn it on from Settings → Phone & App Control."

    private suspend fun handleRadioToggle(name: String, on: Boolean, panelIntent: Intent, hints: List<String>): String {
        panelIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(panelIntent)
        val service = JarvisAccessibilityService.current() ?: return "Opened $name settings — Android doesn't let apps toggle $name silently, so please tap it yourself, or enable Accessibility in Settings so I can tap it for you."
        delay(700) // let the settings panel/screen actually appear before searching it
        val toggled = service.toggleSwitchLike(hints, on)
        return if (toggled) "Turned $name ${if (on) "on" else "off"}."
        else "Opened $name settings and tried to switch it ${if (on) "on" else "off"} — double-check it took effect, this varies by phone."
    }

    /** Kept for symmetry with the reminder module; not auto-invoked by chat parsing. */
    fun scheduleReminderFor(taskId: Long, title: String, triggerAtMillis: Long) {
        reminderScheduler.scheduleTaskReminder(taskId, title, triggerAtMillis)
    }
}
