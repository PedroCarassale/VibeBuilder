package com.vibebuilder.app.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vibebuilder.app.R
import com.vibebuilder.app.domain.model.ProjectVersion
import com.vibebuilder.app.domain.model.PublicProject
import com.vibebuilder.app.ui.components.AppCard
import com.vibebuilder.app.ui.components.AppTopBar
import com.vibebuilder.app.ui.components.EmptyView
import com.vibebuilder.app.ui.components.ErrorView
import com.vibebuilder.app.ui.components.ProjectListSkeleton
import com.vibebuilder.app.ui.components.SectionHeader
import com.vibebuilder.app.ui.components.StatusPill
import com.vibebuilder.app.ui.theme.AppSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityLibraryScreen(
    onBack: () -> Unit,
    onProjectClick: (String) -> Unit,
    viewModel: CommunityLibraryViewModel = viewModel(factory = CommunityLibraryViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = stringResource(R.string.library_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_cd))
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val current = state) {
                LibraryUiState.Loading -> ProjectListSkeleton()
                is LibraryUiState.Error -> ErrorView(message = current.message)
                is LibraryUiState.Content -> {
                    if (current.totalCount == 0) {
                        EmptyView(
                            title = stringResource(R.string.library_empty_title),
                            subtitle = stringResource(R.string.library_empty_subtitle)
                        )
                    } else {
                        LibraryList(
                            projects = current.projects,
                            query = current.query,
                            onQueryChange = viewModel::onSearchQueryChange,
                            onProjectClick = onProjectClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryList(
    projects: List<PublicProject>,
    query: String,
    onQueryChange: (String) -> Unit,
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
                title = stringResource(R.string.library_header_title),
                subtitle = stringResource(R.string.library_header_subtitle)
            )
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.library_search_label)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
            )
        }
        if (projects.isEmpty()) {
            item {
                EmptyView(
                    title = stringResource(R.string.library_no_matches_title),
                    subtitle = stringResource(R.string.library_no_matches_subtitle)
                )
            }
        } else {
            items(projects, key = { it.id }) { project ->
                PublicProjectCard(project = project, onClick = { onProjectClick(project.id) })
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun PublicProjectCard(project: PublicProject, onClick: () -> Unit) {
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
            Column(Modifier.weight(1f)) {
                Text(
                    text = project.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.library_by_author, project.ownerName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
        Spacer(Modifier.height(AppSpacing.sm))
        Text(
            text = stringResource(R.string.library_fork_count, project.forkCount),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicProjectDetailScreen(
    projectId: String,
    onBack: () -> Unit,
    onForkCreated: (String) -> Unit,
    viewModel: PublicProjectDetailViewModel = viewModel(
        factory = remember(projectId) { PublicProjectDetailViewModel.factory(projectId) }
    )
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val forkState by viewModel.forkState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(forkState.forkedProjectId) {
        val forkedProjectId = forkState.forkedProjectId ?: return@LaunchedEffect
        viewModel.onForkNavigationHandled()
        onForkCreated(forkedProjectId)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = stringResource(R.string.library_detail_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_cd))
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val current = state) {
                PublicProjectDetailUiState.Loading -> ProjectListSkeleton()
                is PublicProjectDetailUiState.Error -> ErrorView(message = current.message)
                is PublicProjectDetailUiState.NotFound -> ErrorView(
                    message = stringResource(R.string.library_not_found, current.projectId)
                )
                is PublicProjectDetailUiState.Content -> PublicProjectDetailContent(
                    project = current.project,
                    isForking = forkState.isForking,
                    onOpenPreview = {
                        current.project.currentPreviewUrl?.let(uriHandler::openUri)
                    },
                    onFork = { viewModel.forkProject(current.project.id) }
                )
            }
        }
    }

    forkState.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearForkError,
            title = { Text(stringResource(R.string.library_fork_error_title)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::clearForkError) {
                    Text(stringResource(R.string.dismiss))
                }
            }
        )
    }
}

@Composable
private fun PublicProjectDetailContent(
    project: PublicProject,
    isForking: Boolean,
    onOpenPreview: () -> Unit,
    onFork: () -> Unit
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
                title = project.title,
                subtitle = stringResource(R.string.library_by_author, project.ownerName)
            )
        }
        if (project.description.isNotBlank()) {
            item {
                Text(
                    text = project.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        project.originalProjectTitle?.let { originalTitle ->
            item {
                Text(
                    text = stringResource(
                        R.string.library_attribution,
                        originalTitle,
                        project.originalAuthorName ?: stringResource(R.string.library_unknown_author)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = project.currentPreviewUrl != null,
                    onClick = onOpenPreview
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Text(stringResource(R.string.preview_open_in_browser))
                }
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = !isForking,
                    onClick = onFork
                ) {
                    if (isForking) {
                        CircularProgressIndicator(Modifier.padding(end = AppSpacing.sm), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Add, contentDescription = null)
                    }
                    Text(stringResource(R.string.library_fork_action))
                }
            }
        }
        item {
            SectionHeader(
                title = stringResource(R.string.history_title),
                subtitle = stringResource(R.string.library_versions_subtitle)
            )
        }
        if (project.versions.isEmpty()) {
            item { Text(stringResource(R.string.history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(project.versions, key = { it.id }) { version ->
                PublicVersionRow(version)
            }
        }
    }
}

@Composable
private fun PublicVersionRow(version: ProjectVersion) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(AppSpacing.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.prompt_version_format, version.versionNumber),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall
            )
            StatusPill(text = version.status.name.lowercase())
        }
        Spacer(Modifier.height(AppSpacing.xs))
        Text(
            text = version.prompt,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
