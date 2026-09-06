package com.gemmaassistant.app.llm

import java.io.File

/** Common surface both on-device inference backends (MediaPipe, llama.cpp) implement. */
interface LlmEngine {
    fun isLoaded(): Boolean
    fun loadedModelName(): String?
    suspend fun load(modelFile: File): Result<Unit>
    fun unload()
    suspend fun generate(prompt: String): Result<String>
}
