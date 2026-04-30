package com.vibebuilder.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun WebPreviewScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
        Card(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Generated Web App Preview")
                Text("Placeholder: a WebView or preview URL renderer will be connected in Delivery 2.")
            }
        }
    }
}
