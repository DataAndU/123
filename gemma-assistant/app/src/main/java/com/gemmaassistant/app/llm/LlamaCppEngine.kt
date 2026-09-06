package com.gemmaassistant.app.llm

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Wraps the bundled llama.cpp (via [LlamaCppNative]) for `.gguf` model
 * files — Llama, Mistral, Qwen, Phi, Gemma-GGUF, and most other openly
 * distributed quantized models, as opposed to [MediaPipeLlmEngine]'s
 * MediaPipe-specific `.task` format.
 *
 * Every [generate] call resets the model's native conversation state and
 * re-processes an empty system prompt first, rather than relying on
 * llama.cpp's own multi-turn memory — [com.gemmaassistant.app.assistant.AssistantEngine]
 * and [AgentOrchestrator] already compose the full prompt (instructions +
 * recent history + the new message) themselves for both engines this app
 * supports, so leaning on native memory too would double-count history.
 */
class LlamaCppEngine(private val context: Context) : LlmEngine {

    @Volatile private var initialized = false
    @Volatile private var loadedModelPath: String? = null

    override fun isLoaded(): Boolean = loadedModelPath != null

    override fun loadedModelName(): String? = loadedModelPath?.let { File(it).name }

    override suspend fun load(modelFile: File): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            unload()
            if (!initialized) {
                LlamaCppNative.init(context.applicationInfo.nativeLibraryDir)
                initialized = true
            }
            if (LlamaCppNative.load(modelFile.absolutePath) != 0) {
                return@withContext Result.failure(IllegalStateException("llama.cpp couldn't load ${modelFile.name} — is it a valid GGUF file?"))
            }
            if (LlamaCppNative.prepare() != 0) {
                LlamaCppNative.unload()
                return@withContext Result.failure(IllegalStateException("llama.cpp failed to create an inference context for ${modelFile.name}"))
            }
            loadedModelPath = modelFile.absolutePath
            Result.success(Unit)
        } catch (e: Throwable) {
            loadedModelPath = null
            Result.failure(e)
        }
    }

    override fun unload() {
        if (loadedModelPath == null) return
        try {
            LlamaCppNative.unload()
        } catch (_: Throwable) {
            // best-effort cleanup — the model is being replaced or the engine torn down either way
        }
        loadedModelPath = null
    }

    override suspend fun generate(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        if (loadedModelPath == null) return@withContext Result.failure(IllegalStateException("No model loaded"))
        try {
            if (LlamaCppNative.processSystemPrompt("") != 0) {
                return@withContext Result.failure(IllegalStateException("Failed to reset the model's conversation state"))
            }
            if (LlamaCppNative.processUserPrompt(prompt, MAX_NEW_TOKENS) != 0) {
                return@withContext Result.failure(IllegalStateException("The prompt was rejected by the model (too long for its context window?)"))
            }
            val text = StringBuilder()
            while (true) {
                val token = LlamaCppNative.generateNextToken() ?: break
                text.append(token)
            }
            Result.success(text.toString())
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    private companion object {
        const val MAX_NEW_TOKENS = 1024
    }
}
