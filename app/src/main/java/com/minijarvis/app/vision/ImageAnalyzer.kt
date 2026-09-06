package com.minijarvis.app.vision

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class ImageAnalysisResult(val labels: List<String>)

/**
 * Runs image labeling using ML Kit's bundled, on-device model. The model
 * ships inside the app APK — no model download and no network call happens
 * at analysis time.
 *
 * On-device text recognition (OCR) was intentionally left out: ML Kit's
 * bundled text-recognition artifact pulls in its OCR pipeline's native
 * libraries, which alone add roughly 22MB to the APK. Re-adding
 * `com.google.mlkit:text-recognition` and a `TextRecognition.getClient(...)`
 * call here is a straightforward follow-up if that size trade-off is
 * acceptable for a given build.
 */
class ImageAnalyzer {

    private val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)

    suspend fun analyze(bitmap: Bitmap): ImageAnalysisResult {
        val image = InputImage.fromBitmap(bitmap, 0)
        val labels = runCatching { labelImage(image) }.getOrElse { emptyList() }
        return ImageAnalysisResult(labels = labels)
    }

    private suspend fun labelImage(image: InputImage): List<String> = suspendCancellableCoroutine { cont ->
        labeler.process(image)
            .addOnSuccessListener { labels -> cont.resume(labels.map { it.text }) }
            .addOnFailureListener { cont.resume(emptyList()) }
    }
}
