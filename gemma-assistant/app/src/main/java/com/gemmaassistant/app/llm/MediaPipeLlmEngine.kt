package com.gemmaassistant.app.llm

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Thin wrapper over MediaPipe's on-device LLM Inference API, for a `.task`
 * model file (Gemma 3n and similar, packaged via Google's LiteRT/MediaPipe
 * conversion tooling). See [LlamaCppEngine] for the other supported format
 * (`.gguf`) — [LocalLlmEngine] picks between the two by file extension.
 */
class MediaPipeLlmEngine(private val context: Context) : LlmEngine {

    @Volatile private var inference: LlmInference? = null
    @Volatile private var loadedModelPath: String? = null

    override fun isLoaded(): Boolean = inference != null

    override fun loadedModelName(): String? = loadedModelPath?.let { File(it).name }

    override suspend fun load(modelFile: File): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            unload()
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(1024)
                .build()
            inference = LlmInference.createFromOptions(context, options)
            loadedModelPath = modelFile.absolutePath
            Result.success(Unit)
        } catch (e: Exception) {
            inference = null
            loadedModelPath = null
            Result.failure(e)
        }
    }

    override fun unload() {
        inference?.close()
        inference = null
        loadedModelPath = null
    }

    /** Blocking generation — always call from a background dispatcher. */
    override suspend fun generate(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        val engine = inference ?: return@withContext Result.failure(IllegalStateException("No model loaded"))
        try {
            Result.success(engine.generateResponse(prompt))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
