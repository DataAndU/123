package com.minijarvis.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.minijarvis.app.core.AppContainer
import com.minijarvis.app.data.ImageAnalysisEntity
import com.minijarvis.app.util.TimeUtils
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun CameraScreen(container: AppContainer) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasCameraPermission = it
    }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var lastResultText by remember { mutableStateOf<String?>(null) }
    val history by container.imageAnalysisRepository.observeAll().collectAsState(initial = emptyList())

    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Photos are analyzed on-device with ML Kit and stay in local, encrypted storage.",
            style = MaterialTheme.typography.bodySmall
        )

        if (!hasCameraPermission) {
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                Text("Grant camera permission")
            }
        } else {
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(300.dp),
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val providerFuture = ProcessCameraProvider.getInstance(ctx)
                    providerFuture.addListener({
                        val provider = providerFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val capture = ImageCapture.Builder().build()
                        provider.unbindAll()
                        provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                        imageCapture = capture
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                }
            )

            Button(
                enabled = imageCapture != null && !isAnalyzing,
                onClick = {
                    val capture = imageCapture ?: return@Button
                    val imagesDir = File(context.filesDir, "images").apply { mkdirs() }
                    val outputFile = File(imagesDir, "capture_${TimeUtils.nowMillis()}.jpg")
                    val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
                    isAnalyzing = true
                    capture.takePicture(
                        outputOptions,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                scope.launch {
                                    val bitmap = BitmapFactory.decodeFile(outputFile.absolutePath)
                                    if (bitmap != null) {
                                        val result = container.imageAnalyzer.analyze(bitmap)
                                        container.imageAnalysisRepository.add(
                                            imageFilePath = outputFile.absolutePath,
                                            labels = result.labels
                                        )
                                        lastResultText = "Labels: ${result.labels.joinToString(", ").ifEmpty { "none" }}"
                                    }
                                    isAnalyzing = false
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                isAnalyzing = false
                                lastResultText = "Capture failed: ${exception.message}"
                            }
                        }
                    )
                }
            ) {
                Text(if (isAnalyzing) "Analyzing…" else "Capture & Analyze")
            }

            if (isAnalyzing) CircularProgressIndicator()
            lastResultText?.let { Text(it) }
        }

        Text("History", style = MaterialTheme.typography.titleMedium)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(history, key = { it.id }) { entry -> ImageAnalysisRow(entry) }
        }
    }
}

@Composable
private fun ImageAnalysisRow(entry: ImageAnalysisEntity) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            Text(TimeUtils.formatDateTime(entry.timestampMillis), style = MaterialTheme.typography.bodySmall)
            Text("Labels: ${entry.labelsCsv.ifEmpty { "none" }}")
        }
    }
}
