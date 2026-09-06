package com.minijarvis.app.vision

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class ImageAnalysisResult(
    val labels: List<String>,
    val recognizedText: String?
)

/**
 * Runs image labeling and text recognition using ML Kit's bundled,
 * on-device models. These models ship inside the app APK — no model
 * download and no network call happens at analysis time.
 */
class ImageAnalyzer {

    private val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun analyze(bitmap: Bitmap): ImageAnalysisResult {
        val image = InputImage.fromBitmap(bitmap, 0)
        val labels = runCatching { labelImage(image) }.getOrElse { emptyList() }
        val text = runCatching { recognizeText(image) }.getOrNull()?.takeIf { it.isNotBlank() }
        return ImageAnalysisResult(labels = labels, recognizedText = text)
    }

    private suspend fun labelImage(image: InputImage): List<String> = suspendCancellableCoroutine { cont ->
        labeler.process(image)
            .addOnSuccessListener { labels -> cont.resume(labels.map { it.text }) }
            .addOnFailureListener { cont.resume(emptyList()) }
    }

    private suspend fun recognizeText(image: InputImage): String = suspendCancellableCoroutine { cont ->
        textRecognizer.process(image)
            .addOnSuccessListener { result -> cont.resume(result.text) }
            .addOnFailureListener { cont.resume("") }
    }
}
