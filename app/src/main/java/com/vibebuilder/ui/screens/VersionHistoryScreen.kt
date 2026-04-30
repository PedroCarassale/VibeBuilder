package com.vibebuilder.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vibebuilder.model.ProjectVersion

@Composable
fun VersionHistoryScreen(versions: List<ProjectVersion>, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(versions) { version ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Version ${version.versionNumber}")
                        Text(version.promptSummary)
                        Text("${version.status} • ${version.createdAt}")
                    }
                }
            }
        }
    }
}
