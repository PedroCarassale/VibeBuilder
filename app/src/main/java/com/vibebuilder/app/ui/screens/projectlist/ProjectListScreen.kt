package com.vibebuilder.app.ui.screens.projectlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vibebuilder.app.R
import com.vibebuilder.app.domain.model.Project
import com.vibebuilder.app.ui.components.EmptyView
import com.vibebuilder.app.ui.components.ErrorView
import com.vibebuilder.app.ui.components.LoadingView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectListScreen(
    onCreateProject: () -> Unit,
    onProjectClick: (String) -> Unit,
    viewModel: ProjectListViewModel = viewModel(factory = ProjectListViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.project_list_title)) })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateProject,
                icon = {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.project_list_fab_cd)
                    )
                },
                text = { Text(stringResource(R.string.project_list_create_cta)) }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val current = state) {
                ProjectListUiState.Loading -> LoadingView()
                is ProjectListUiState.Error -> ErrorView(message = current.message)
                is ProjectListUiState.Content -> {
                    if (current.projects.isEmpty()) {
                        EmptyView(
                            title = stringResource(R.string.project_list_empty_title),
                            subtitle = stringResource(R.string.project_list_empty_subtitle),
                            actionLabel = stringResource(R.string.project_list_create_cta),
                            onAction = onCreateProject
                        )
                    } else {
                        ProjectList(
                            projects = current.projects,
                            onProjectClick = onProjectClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectList(
    projects: List<Project>,
    onProjectClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items = projects, key = { it.id }) { project ->
            ProjectCard(project = project, onClick = { onProjectClick(project.id) })
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun ProjectCard(project: Project, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = project.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (project.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = project.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "v${project.currentVersionNumber}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
