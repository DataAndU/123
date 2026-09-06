package com.gemmaassistant.app.net

import com.gemmaassistant.app.system.AssistantSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class FetchResult(val statusCode: Int, val body: String)

/**
 * This is the ONLY code path in the entire app that ever makes a network
 * call, and it only runs at all if [AssistantSettingsStore.isInternetAccessEnabled]
 * is on — off by default, and turning it on requires reading and
 * acknowledging an explicit warning dialog in Settings (see SettingsScreen),
 * since INTERNET is a "normal" Android permission granted silently at
 * install with no runtime prompt — there is no OS-level gate for this one,
 * only this in-app check.
 *
 * GET requests execute directly (per the user's explicit choice to allow
 * free read access). Any other HTTP method is treated as a mutating action
 * by the caller (AgentOrchestrator) and routed through ConfirmationGate
 * before this class is ever invoked for it.
 */
class WebFetchTool(private val settingsStore: AssistantSettingsStore) {

    suspend fun fetch(urlString: String, method: String = "GET", body: String? = null): Result<FetchResult> =
        withContext(Dispatchers.IO) {
            if (!settingsStore.isInternetAccessEnabled.first()) {
                return@withContext Result.failure(IllegalStateException("Internet access is off — enable it in Settings first"))
            }
            var connection: HttpURLConnection? = null
            try {
                val url = URL(urlString)
                if (url.protocol != "http" && url.protocol != "https") {
                    return@withContext Result.failure(IllegalArgumentException("Only http/https URLs are supported"))
                }
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = method.uppercase()
                    connectTimeout = 15_000
                    readTimeout = 20_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "MiniJarvis/1.0 (local personal assistant)")
                    if (body != null) {
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    }
                }
                if (body != null) {
                    OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body) }
                }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.use { it.readBytes() }?.let { String(it, Charsets.UTF_8) } ?: ""
                val truncated = if (text.length > MAX_RESPONSE_CHARS) text.take(MAX_RESPONSE_CHARS) + "\n...[truncated]" else text
                Result.success(FetchResult(status, truncated))
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                connection?.disconnect()
            }
        }

    companion object {
        private const val MAX_RESPONSE_CHARS = 20_000
    }
}
