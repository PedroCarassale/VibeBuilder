package com.vibebuilder.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.vibebuilder.app.ui.navigation.VibeNavGraph
import com.vibebuilder.app.ui.theme.VibeBuilderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VibeBuilderTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    VibeNavGraph()
                }
            }
        }
    }
}
