package com.minijarvis.app.llm

/** The tool schema handed to the local model as part of its prompt. */
object AgentTools {

    fun systemPrompt(): String = """
        You are Mini JARVIS, an on-device assistant that can act on the user's phone. For each
        request, decide which tool(s) to call, one per line, in exactly this format:
        TOOL: <name> | <argKey>=<argValue> | <argKey>=<argValue>
        You may output several TOOL lines to complete a multi-step request, in the order they
        should happen. If you're just responding with no action needed, use:
        TOOL: reply | text=<your response>
        Only use the tools listed below with their exact names and argument keys. Never invent a
        new tool. Keep replies short and natural.

        Tools:
        - add_expense | amount=<number> | category=<text>
        - log_weight | kg=<number>
        - add_food | name=<text> | meal=<breakfast|lunch|dinner|snack>
        - add_task | title=<text>
        - log_habit | name=<text>
        - log_medicine | name=<text>
        - search | query=<text>
        - open_app | app=<text>
        - call | target=<contact name or phone number>
        - send_text | target=<contact name or phone number> | message=<text>
        - read_last_message | from=<sender name, optional>
        - set_volume | stream=<media|ring|alarm> | direction=<up|down>
        - mute_volume | stream=<media|ring|alarm> | mute=<true|false>
        - set_brightness | direction=<up|down>
        - toggle_flashlight | on=<true|false>
        - toggle_wifi | on=<true|false>
        - toggle_bluetooth | on=<true|false>
        - toggle_dnd | on=<true|false>
        - take_screenshot
        - go_home
        - go_back
        - show_recents
        - lock_screen
        - read_screen
        - tap | target=<visible text of the thing to tap>
        - reply | text=<text>
    """.trimIndent()
}

data class ParsedToolCall(val name: String, val args: Map<String, String>)

object AgentResponseParser {

    /** Extracts every `TOOL:` line; if the model produced none, treats its whole output as a reply. */
    fun parse(modelOutput: String): List<ParsedToolCall> {
        val toolLines = modelOutput.lines()
            .map { it.trim() }
            .filter { it.startsWith("TOOL:", ignoreCase = true) }
            .mapNotNull(::parseLine)
        return toolLines.ifEmpty { listOf(ParsedToolCall("reply", mapOf("text" to modelOutput.trim()))) }
    }

    private fun parseLine(line: String): ParsedToolCall? {
        val body = line.substringAfter(":", "").trim()
        if (body.isEmpty()) return null
        val parts = body.split("|").map { it.trim() }
        val name = parts.firstOrNull()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        val args = parts.drop(1).mapNotNull { part ->
            val eq = part.indexOf('=')
            if (eq == -1) null else part.substring(0, eq).trim().lowercase() to part.substring(eq + 1).trim()
        }.toMap()
        return ParsedToolCall(name, args)
    }
}
