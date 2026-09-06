package com.gemmaassistant.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.gemmaassistant.app.core.GemmaAssistantApp
import com.gemmaassistant.app.ui.GemmaAssistantRoot
import com.gemmaassistant.app.ui.theme.GemmaAssistantTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as GemmaAssistantApp).container
        setContent {
            GemmaAssistantTheme {
                GemmaAssistantRoot(container)
            }
        }

        // Best-effort: re-load whichever local model the user had selected last time.
        lifecycleScope.launch {
            val fileName = container.assistantSettingsStore.selectedModelFileName.first() ?: return@launch
            val file = container.modelManager.listImportedModels().firstOrNull { it.name == fileName } ?: return@launch
            container.localLlmEngine.load(file)
        }
    }
}
