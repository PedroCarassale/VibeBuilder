package com.vibebuilder.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CreateProjectScreen(
    onCancel: () -> Unit,
    onCreate: (String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Project title") }
        )
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Description") }
        )
        Button(onClick = { onCreate(title.trim(), description.trim()) }, enabled = title.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text("Create and Start Prompting")
        }
        Button(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}
