package com.vibebuilder.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vibebuilder.model.PromptMessage

@Composable
fun PromptScreen(
    messages: List<PromptMessage>,
    onBack: () -> Unit,
    onSendPrompt: (String) -> Unit,
    onOpenPreview: () -> Unit,
    onOpenHistory: () -> Unit
) {
    var prompt by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
            Button(onClick = onOpenPreview, modifier = Modifier.weight(1f)) { Text("Preview") }
            Button(onClick = onOpenHistory, modifier = Modifier.weight(1f)) { Text("History") }
        }

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages) { message ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(message.role.name)
                        Text(message.content)
                    }
                }
            }
        }

        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text("Describe your next change") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                onSendPrompt(prompt.trim())
                prompt = ""
            },
            enabled = prompt.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Generate Iteration") }
    }
}
