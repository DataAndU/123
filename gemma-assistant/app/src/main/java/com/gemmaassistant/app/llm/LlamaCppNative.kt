package com.gemmaassistant.app.llm

/**
 * Direct JNI bridge to the bundled llama.cpp (see app/src/main/cpp). Every
 * function here must be called in a specific order — init, then load, then
 * prepare, then processSystemPrompt once, then processUserPrompt +
 * generateNextToken* per turn, then unload — and there is exactly one
 * resident model at a time (tracked in native static state, not per Kotlin
 * instance). [LlamaCppEngine] is the only thing that should call this
 * directly.
 */
internal object LlamaCppNative {
    init {
        System.loadLibrary("llama-bridge")
    }

    /** One-time setup: registers the Android log sink and loads ggml's backend plugins from [nativeLibDir]. */
    external fun init(nativeLibDir: String)

    /** Loads a GGUF model file. Returns 0 on success. */
    external fun load(modelPath: String): Int

    /** Creates the inference context/sampler for the currently loaded model. Returns 0 on success. */
    external fun prepare(): Int

    /** Resets conversation state and processes (optionally empty) system instructions. Returns 0 on success. */
    external fun processSystemPrompt(systemPrompt: String): Int

    /** Feeds a user turn and prepares generation up to [nPredict] new tokens. Returns 0 on success. */
    external fun processUserPrompt(userPrompt: String, nPredict: Int): Int

    /** Samples and decodes the next token's text, or null once generation is complete. */
    external fun generateNextToken(): String?

    /** Frees the current model/context/sampler. Safe to call even if nothing is loaded. */
    external fun unload()

    /** Frees the shared llama.cpp backend. Only call once, at process/engine teardown. */
    external fun shutdown()
}
