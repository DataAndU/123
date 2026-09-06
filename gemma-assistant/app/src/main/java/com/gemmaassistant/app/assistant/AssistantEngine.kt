package com.gemmaassistant.app.assistant

import android.content.Context
import android.content.Intent
import com.gemmaassistant.app.control.AgentAccessibilityService
import com.gemmaassistant.app.control.AppLauncher
import com.gemmaassistant.app.control.CallResult
import com.gemmaassistant.app.control.PhoneActionsManager
import com.gemmaassistant.app.control.SmsResult
import com.gemmaassistant.app.control.SystemControlManager
import com.gemmaassistant.app.control.VolumeStream
import com.gemmaassistant.app.data.AgentActivityRepository
import com.gemmaassistant.app.data.ChatRepository
import com.gemmaassistant.app.files.FileAccessManager
import com.gemmaassistant.app.llm.AgentOrchestrator
import com.gemmaassistant.app.llm.ConfirmationGate
import com.gemmaassistant.app.llm.LocalLlmEngine
import com.gemmaassistant.app.net.WebFetchTool
import com.gemmaassistant.app.system.AssistantSettingsStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * The local "brain" behind both text and voice chat. Without a model loaded
 * it's a rule-based router over device-control intents; once you load a
 * Gemma model (see [LocalLlmEngine]), [AgentOrchestrator] takes over and
 * additionally understands free-form phrasing, chains multi-step requests,
 * and reaches the file/internet tools this simple parser never does.
 */
class AssistantEngine(
    private val appContext: Context,
    private val chatRepository: ChatRepository,
    private val appLauncher: AppLauncher,
    private val systemControlManager: SystemControlManager,
    private val phoneActionsManager: PhoneActionsManager,
    private val localLlmEngine: LocalLlmEngine,
    private val assistantSettingsStore: AssistantSettingsStore,
    private val fileAccessManager: FileAccessManager,
    private val webFetchTool: WebFetchTool,
    private val confirmationGate: ConfirmationGate,
    private val agentActivityRepository: AgentActivityRepository
) {

    private val agentOrchestrator: AgentOrchestrator by lazy {
        AgentOrchestrator(
            localLlmEngine = localLlmEngine,
            executeIntent = ::executeIntent,
            fileAccessManager = fileAccessManager,
            webFetchTool = webFetchTool,
            confirmationGate = confirmationGate,
            agentActivityRepository = agentActivityRepository
        )
    }

    suspend fun handle(userInput: String, source: String = "text"): String {
        chatRepository.addMessage(role = "user", content = userInput, source = source)
        val reply = if (localLlmEngine.isLoaded()) {
            val history = chatRepository.observeAll().first().takeLast(6)
                .joinToString("\n") { "${it.role}: ${it.content}" }
            agentOrchestrator.handle(userInput, history)
        } else {
            executeIntent(IntentParser.parse(userInput))
        }
        chatRepository.addMessage(role = "assistant", content = reply, source = source)
        return reply
    }

    suspend fun executeIntent(intent: AssistantIntent): String {
        return when (intent) {
            is AssistantIntent.Greeting ->
                "Hi, I'm Gemma Assistant. Load a local model in Settings for real understanding, or try a plain command like \"call mom\" or \"open camera\" right now."

            is AssistantIntent.Help ->
                "Try: \"call mom\", \"text mom saying I'm on my way\", \"open camera\", \"turn on the flashlight\", " +
                    "\"turn up the volume\", \"go home\", \"take a screenshot\", \"what's on my screen\", " +
                    "\"tap settings\", \"type hello there\", or \"scroll down\". " +
                    "Load a local Gemma model in Settings to go far beyond these."

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
                if (systemControlManager.setFlashlight(intent.on)) {
                    assistantSettingsStore.setFlashlightOnAsFarAsWeKnow(intent.on)
                    "Flashlight ${if (intent.on) "on" else "off"}."
                } else "This device doesn't seem to have a flashlight I can control."
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

            is AssistantIntent.TypeText -> withAccessibility { it.typeText(intent.text) }?.let {
                if (it) "Typed \"${intent.text}\"." else "I couldn't find a text field to type into."
            } ?: accessibilityRequiredMessage()

            is AssistantIntent.Scroll -> withAccessibility { it.scroll(intent.forward) }?.let {
                if (it) "Scrolled ${if (intent.forward) "down" else "up"}." else "I couldn't find anything scrollable on screen."
            } ?: accessibilityRequiredMessage()

            is AssistantIntent.Unknown ->
                "I didn't catch that. Say \"help\" to see what I can do, or load a local model in Settings for real understanding."
        }
    }

    private fun streamFor(name: String): VolumeStream = when (name) {
        "ring" -> VolumeStream.RING
        "alarm" -> VolumeStream.ALARM
        else -> VolumeStream.MEDIA
    }

    private fun <T> withAccessibility(action: (AgentAccessibilityService) -> T): T? =
        AgentAccessibilityService.current()?.let(action)

    private fun accessibilityRequiredMessage() =
        "That needs the Accessibility Service enabled — turn it on from Settings → Phone & App Control."

    private suspend fun handleRadioToggle(name: String, on: Boolean, panelIntent: Intent, hints: List<String>): String {
        panelIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(panelIntent)
        val service = AgentAccessibilityService.current() ?: return "Opened $name settings — Android doesn't let apps toggle $name silently, so please tap it yourself, or enable Accessibility in Settings so I can tap it for you."
        delay(700) // let the settings panel/screen actually appear before searching it
        val toggled = service.toggleSwitchLike(hints, on)
        return if (toggled) "Turned $name ${if (on) "on" else "off"}."
        else "Opened $name settings and tried to switch it ${if (on) "on" else "off"} — double-check it took effect, this varies by phone."
    }
}
