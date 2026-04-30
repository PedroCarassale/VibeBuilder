package com.vibebuilder.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vibebuilder.model.Project

@Composable
fun ProjectListScreen(
    projects: List<Project>,
    onCreateProject: () -> Unit,
    onOpenProject: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onCreateProject, modifier = Modifier.fillMaxWidth()) {
            Text("Create New Project")
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(projects) { project ->
                Card(modifier = Modifier.fillMaxWidth().clickable { onOpenProject(project.id) }) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(project.title, style = MaterialTheme.typography.titleMedium)
                        Text(project.description, style = MaterialTheme.typography.bodyMedium)
                        Text("Updated: ${project.updatedAt} • v${project.currentVersion}")
                    }
                }
            }
        }
    }
}
