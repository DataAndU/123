package com.minijarvis.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.minijarvis.app.core.MiniJarvisApp
import com.minijarvis.app.ui.MiniJarvisRoot
import com.minijarvis.app.ui.theme.MiniJarvisTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as MiniJarvisApp).container
        setContent {
            MiniJarvisTheme {
                MiniJarvisRoot(container)
            }
        }

        // Best-effort: re-load whichever local model the user had selected last time.
        // Silently does nothing if none was ever selected, or if loading fails (e.g.
        // the file was removed) — the assistant just keeps using pattern matching.
        lifecycleScope.launch {
            val fileName = container.assistantSettingsStore.selectedModelFileName.first() ?: return@launch
            val file = container.modelManager.listImportedModels().firstOrNull { it.name == fileName } ?: return@launch
            container.localLlmEngine.load(file)
        }
    }
}
