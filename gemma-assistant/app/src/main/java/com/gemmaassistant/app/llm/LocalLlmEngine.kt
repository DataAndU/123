package com.gemmaassistant.app.llm

import android.content.Context
import java.io.File

/**
 * Front door for on-device text generation. Picks the inference engine by
 * the imported model file's extension: MediaPipe's LLM Inference API for a
 * `.task` bundle (Gemma 3n and similar, packaged via Google's LiteRT/
 * MediaPipe conversion tooling), or a bundled llama.cpp for a `.gguf` file
 * (Llama, Mistral, Qwen, Phi, Gemma-GGUF, and most other openly distributed
 * quantized models). Only one model — and one native runtime — is ever
 * resident at a time; whichever loaded most recently is what [generate]
 * and [isLoaded] talk to. There is no bundled model and no download in
 * either case — the user supplies the file themselves (see [ModelManager]).
 */
class LocalLlmEngine(context: Context) {

    private val mediaPipeEngine = MediaPipeLlmEngine(context)
    private val llamaCppEngine = LlamaCppEngine(context)
    @Volatile private var active: LlmEngine? = null

    fun isLoaded(): Boolean = active?.isLoaded() == true

    fun loadedModelName(): String? = active?.loadedModelName()

    /** Which engine is currently loaded, for status display — null if none is. */
    fun loadedEngineLabel(): String? = when (active) {
        mediaPipeEngine -> "MediaPipe"
        llamaCppEngine -> "llama.cpp"
        else -> null
    }

    suspend fun load(modelFile: File): Result<Unit> {
        val engine = engineFor(modelFile) ?: return Result.failure(
            IllegalArgumentException("Unsupported model file \"${modelFile.name}\" — expected a .task (MediaPipe) or .gguf (llama.cpp) file")
        )
        unload()
        val result = engine.load(modelFile)
        if (result.isSuccess) active = engine
        return result
    }

    fun unload() {
        mediaPipeEngine.unload()
        llamaCppEngine.unload()
        active = null
    }

    /** Blocking generation — always call from a background dispatcher. */
    suspend fun generate(prompt: String): Result<String> {
        val engine = active ?: return Result.failure(IllegalStateException("No model loaded"))
        return engine.generate(prompt)
    }

    private fun engineFor(modelFile: File): LlmEngine? = when (modelFile.extension.lowercase()) {
        "task" -> mediaPipeEngine
        "gguf" -> llamaCppEngine
        else -> null
    }
}
