package com.minijarvis.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.minijarvis.app.core.MiniJarvisApp
import com.minijarvis.app.ui.MiniJarvisRoot
import com.minijarvis.app.ui.theme.MiniJarvisTheme

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
    }
}
