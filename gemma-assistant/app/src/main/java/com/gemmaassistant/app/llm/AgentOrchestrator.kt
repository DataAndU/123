package com.gemmaassistant.app.llm

import com.gemmaassistant.app.assistant.AssistantIntent
import com.gemmaassistant.app.data.AgentActivityRepository
import com.gemmaassistant.app.files.FileAccessManager
import com.gemmaassistant.app.net.WebFetchTool

/**
 * The "agent" loop: prompts the local model with the tool catalog plus a bit
 * of recent conversation, parses however many TOOL calls it produced, and
 * runs each one either through the same execution path the rule-based
 * parser uses ([executeIntent] — see AssistantEngine.executeIntent, for
 * every phone/system/screen tool) or, for the file and internet tools that
 * only exist in agent mode, directly here. Reads (list/search/read/GET) run
 * immediately; writes/deletes and non-GET requests are routed through
 * [confirmationGate] first — the one thing that must always ask, no matter
 * how permissive the read-side access is.
 */
class AgentOrchestrator(
    private val localLlmEngine: LocalLlmEngine,
    private val executeIntent: suspend (AssistantIntent) -> String,
    private val fileAccessManager: FileAccessManager,
    private val webFetchTool: WebFetchTool,
    private val confirmationGate: ConfirmationGate,
    private val agentActivityRepository: AgentActivityRepository
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
            val fileOrNetResult = tryFileOrNetTool(call)
            if (fileOrNetResult != null) {
                results.add(fileOrNetResult)
                continue
            }
            val intent = toIntent(call)
            if (intent != null) results.add(executeIntent(intent))
        }
        return if (results.isEmpty()) generated.trim() else results.joinToString("\n")
    }

    private suspend fun tryFileOrNetTool(call: ParsedToolCall): String? {
        fun arg(key: String) = call.args[key]

        return when (call.name) {
            "list_files" -> {
                if (!fileAccessManager.hasAnyAccess()) return "I don't have folder access yet — grant it in Settings first."
                val files = fileAccessManager.list(arg("path"))
                agentActivityRepository.log("file_list", arg("path") ?: "(top level)", "${files.size} entries")
                if (files.isEmpty()) "Nothing found there." else files.take(50).joinToString("\n") { "${if (it.isDirectory) "[folder] " else ""}${it.path}" }
            }
            "search_files" -> {
                if (!fileAccessManager.hasAnyAccess()) return "I don't have folder access yet — grant it in Settings first."
                val query = arg("query") ?: return "search_files needs a query."
                val files = fileAccessManager.search(query)
                agentActivityRepository.log("file_search", query, "${files.size} matches")
                if (files.isEmpty()) "No files matching \"$query\"." else files.joinToString("\n") { it.path }
            }
            "read_file" -> {
                if (!fileAccessManager.hasAnyAccess()) return "I don't have folder access yet — grant it in Settings first."
                val path = arg("path") ?: return "read_file needs a path."
                fileAccessManager.readText(path).fold(
                    onSuccess = { text ->
                        agentActivityRepository.log("file_read", path, "${text.length} chars")
                        text
                    },
                    onFailure = { "Couldn't read $path: ${it.message}" }
                )
            }
            "write_file" -> {
                if (!fileAccessManager.hasAnyAccess()) return "I don't have folder access yet — grant it in Settings first."
                val path = arg("path") ?: return "write_file needs a path."
                val content = arg("content") ?: return "write_file needs content."
                val approved = confirmationGate.requestConfirmation("Write file?", "Write to \"$path\"?\n\n${content.take(200)}")
                if (!approved) return "Cancelled — you didn't approve writing to $path."
                fileAccessManager.writeText(path, content).fold(
                    onSuccess = {
                        agentActivityRepository.log("file_write", path, "${content.length} chars")
                        "Wrote $path."
                    },
                    onFailure = { "Couldn't write $path: ${it.message}" }
                )
            }
            "delete_file" -> {
                if (!fileAccessManager.hasAnyAccess()) return "I don't have folder access yet — grant it in Settings first."
                val path = arg("path") ?: return "delete_file needs a path."
                val approved = confirmationGate.requestConfirmation("Delete file?", "Permanently delete \"$path\"?")
                if (!approved) return "Cancelled — you didn't approve deleting $path."
                fileAccessManager.delete(path).fold(
                    onSuccess = {
                        agentActivityRepository.log("file_delete", path, "deleted")
                        "Deleted $path."
                    },
                    onFailure = { "Couldn't delete $path: ${it.message}" }
                )
            }
            "fetch_url" -> {
                val url = arg("url") ?: return "fetch_url needs a url."
                val method = (arg("method") ?: "GET").uppercase()
                if (method != "GET") {
                    val approved = confirmationGate.requestConfirmation("Send $method request?", "Send a $method request to:\n$url")
                    if (!approved) return "Cancelled — you didn't approve the $method request to $url."
                }
                webFetchTool.fetch(url, method, arg("body")).fold(
                    onSuccess = { result ->
                        agentActivityRepository.log("web_fetch", url, "$method -> ${result.statusCode}")
                        "[$method $url -> ${result.statusCode}]\n${result.body}"
                    },
                    onFailure = { "Couldn't fetch $url: ${it.message}" }
                )
            }
            else -> null
        }
    }

    private fun toIntent(call: ParsedToolCall): AssistantIntent? {
        fun arg(key: String) = call.args[key]
        fun argBool(key: String) = call.args[key]?.equals("true", ignoreCase = true) == true

        return when (call.name) {
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
            "type_text" -> arg("text")?.let { AssistantIntent.TypeText(it) }
            "scroll" -> AssistantIntent.Scroll(arg("direction") != "up")
            else -> null
        }
    }
}
