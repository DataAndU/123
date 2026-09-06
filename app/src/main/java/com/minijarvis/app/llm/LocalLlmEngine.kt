package com.minijarvis.app.llm

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Thin wrapper over MediaPipe's on-device LLM Inference API. This is the
 * one place a real language model runs — and it only runs at all if the
 * user has imported a `.task` model file themselves (see [ModelManager]);
 * there is no bundled model and no download, so the app's zero-INTERNET
 * guarantee is untouched by this class either way.
 */
class LocalLlmEngine(private val context: Context) {

    @Volatile private var inference: LlmInference? = null
    @Volatile private var loadedModelPath: String? = null

    fun isLoaded(): Boolean = inference != null

    fun loadedModelName(): String? = loadedModelPath?.let { File(it).name }

    suspend fun load(modelFile: File): Result<Unit> = withContext(Dispatchers.IO) {
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

    fun unload() {
        inference?.close()
        inference = null
        loadedModelPath = null
    }

    /** Blocking generation — always call from a background dispatcher. */
    suspend fun generate(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        val engine = inference ?: return@withContext Result.failure(IllegalStateException("No model loaded"))
        try {
            Result.success(engine.generateResponse(prompt))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
