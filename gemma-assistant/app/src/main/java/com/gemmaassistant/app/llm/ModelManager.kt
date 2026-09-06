package com.gemmaassistant.app.llm

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Imports a user-supplied local LLM model file — either a `.task` bundle
 * (Google's LiteRT/MediaPipe conversion tooling) or a `.gguf` file (the
 * format most openly distributed quantized models ship as, for llama.cpp)
 * — into app-private storage, since [LocalLlmEngine] needs a real
 * filesystem path, not a content:// URI.
 *
 * This app never downloads a model itself. The user finds/converts a model
 * on their own machine (or downloads a `.gguf` some other way) and imports
 * the resulting file here.
 */
class ModelManager(private val context: Context) {

    private val modelsDir: File
        get() = File(context.filesDir, "models").apply { mkdirs() }

    fun listImportedModels(): List<File> = modelsDir.listFiles()?.toList().orEmpty().sortedBy { it.name }

    suspend fun importModel(sourceUri: Uri, fileName: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val destination = File(modelsDir, sanitizeFileName(fileName))
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext Result.failure(IllegalStateException("Could not open the selected file"))
            Result.success(destination)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun deleteModel(file: File): Boolean = file.delete()

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("""[^A-Za-z0-9._-]"""), "_").ifBlank { "model.task" }
}
