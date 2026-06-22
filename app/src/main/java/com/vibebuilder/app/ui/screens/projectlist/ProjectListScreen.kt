package com.vibebuilder.app.ui.screens.projectlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    deletionConfirmed: Boolean = false,
    onDeletionConfirmationShown: () -> Unit = {},
    viewModel: ProjectListViewModel = viewModel(factory = ProjectListViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(deletionConfirmed) {
        if (deletionConfirmed) {
            snackbarHostState.showSnackbar(context.getString(R.string.project_delete_success))
            onDeletionConfirmationShown()
        }
    }

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
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    if (current.totalCount == 0) {
                        EmptyView(
                            title = stringResource(R.string.project_list_empty_title),
                            subtitle = stringResource(R.string.project_list_empty_subtitle),
                            actionLabel = stringResource(R.string.project_list_create_cta),
                            onAction = onCreateProject
                        )
                    } else if (current.projects.isEmpty()) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            ProjectControls(current.query, current.sort, viewModel::onSearchQueryChange, viewModel::onSortChange)
                            EmptyView(
                                title = stringResource(R.string.project_list_no_matches_title),
                                subtitle = stringResource(R.string.project_list_no_matches_subtitle)
                            )
                        }
                    } else {
                        ProjectList(
                            projects = current.projects,
                            query = current.query,
                            sort = current.sort,
                            onQueryChange = viewModel::onSearchQueryChange,
                            onSortChange = viewModel::onSortChange,
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
    query: String,
    sort: ProjectSort,
    onQueryChange: (String) -> Unit,
    onSortChange: (ProjectSort) -> Unit,
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
        item { ProjectControls(query, sort, onQueryChange, onSortChange) }
        items(items = projects, key = { it.id }) { project ->
            ProjectCard(project = project, onClick = { onProjectClick(project.id) })
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun ProjectControls(
    query: String,
    sort: ProjectSort,
    onQueryChange: (String) -> Unit,
    onSortChange: (ProjectSort) -> Unit
) {
    var sortExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screenHorizontal, vertical = AppSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            label = { Text(stringResource(R.string.project_list_search_label)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
        )
        Box {
            IconButton(onClick = { sortExpanded = true }) {
                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.project_list_sort_cd))
            }
            DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                ProjectSort.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(option.labelResource())) },
                        onClick = {
                            onSortChange(option)
                            sortExpanded = false
                        },
                        leadingIcon = { if (option == sort) Text("✓") }
                    )
                }
            }
        }
    }
}

private fun ProjectSort.labelResource(): Int = when (this) {
    ProjectSort.RecentlyUpdated -> R.string.project_sort_recently_updated
    ProjectSort.NameAscending -> R.string.project_sort_name
    ProjectSort.NewestCreated -> R.string.project_sort_newest_created
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
