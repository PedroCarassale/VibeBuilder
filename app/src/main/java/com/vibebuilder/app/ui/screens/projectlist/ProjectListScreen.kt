package com.vibebuilder.app.ui.screens.projectlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vibebuilder.app.R
import com.vibebuilder.app.domain.model.Project
import com.vibebuilder.app.ui.components.AppCard
import com.vibebuilder.app.ui.components.AppTopBar
import com.vibebuilder.app.ui.components.EmptyView
import com.vibebuilder.app.ui.components.ErrorView
import com.vibebuilder.app.ui.components.LoadingView
import com.vibebuilder.app.ui.components.SectionHeader
import com.vibebuilder.app.ui.components.StatusPill
import com.vibebuilder.app.ui.theme.AppShapes
import com.vibebuilder.app.ui.theme.AppSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectListScreen(
    onCreateProject: () -> Unit,
    onOpenV0Settings: () -> Unit,
    onProjectClick: (String) -> Unit,
    viewModel: ProjectListViewModel = viewModel(factory = ProjectListViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = stringResource(R.string.project_list_title),
                actions = {
                    IconButton(onClick = onOpenV0Settings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.project_list_settings_cd)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateProject,
                shape = AppShapes.button,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    focusedElevation = 0.dp,
                    hoveredElevation = 0.dp
                ),
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
                is ProjectListUiState.Error -> ErrorView(
                    message = current.message,
                    onRetry = viewModel::retry
                )
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
        contentPadding = PaddingValues(
            horizontal = AppSpacing.screenHorizontal,
            vertical = AppSpacing.screenVertical
        ),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        item {
            SectionHeader(
                title = stringResource(R.string.project_list_header_title),
                subtitle = stringResource(R.string.project_list_header_subtitle),
                modifier = Modifier.padding(bottom = AppSpacing.sm)
            )
        }
        items(items = projects, key = { it.id }) { project ->
            ProjectCard(project = project, onClick = { onProjectClick(project.id) })
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun ProjectCard(project: Project, onClick: () -> Unit) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = PaddingValues(AppSpacing.lg)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = project.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            StatusPill(text = "v${project.currentVersionNumber}")
        }
        if (project.description.isNotBlank()) {
            Spacer(Modifier.height(AppSpacing.sm))
            Text(
                text = project.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(AppSpacing.md))
        Text(
            text = stringResource(R.string.project_card_tagline),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
