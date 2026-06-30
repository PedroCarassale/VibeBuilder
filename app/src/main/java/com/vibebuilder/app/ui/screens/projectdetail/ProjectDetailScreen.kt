package com.vibebuilder.app.ui.screens.projectdetail

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vibebuilder.app.R
import com.vibebuilder.app.ui.components.AppTopBar
import com.vibebuilder.app.ui.components.ErrorView
import com.vibebuilder.app.ui.components.ProjectDetailSkeleton
import com.vibebuilder.app.ui.theme.AppShapes
import com.vibebuilder.app.ui.theme.AppSpacing

private enum class DetailTab { Prompt, Preview, History }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    projectId: String,
    onBack: () -> Unit,
    onDeleted: () -> Unit = onBack,
    viewModel: ProjectDetailViewModel = viewModel(
        factory = remember(projectId) { ProjectDetailViewModel.factory(projectId) }
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val promptInput by viewModel.promptInput.collectAsStateWithLifecycle()
    val previewExternalState by viewModel.previewExternalState.collectAsStateWithLifecycle()
    val previewResolutionState by viewModel.previewResolutionState.collectAsStateWithLifecycle()
    val regenerationState by viewModel.regenerationState.collectAsStateWithLifecycle()
    val editState by viewModel.editProjectState.collectAsStateWithLifecycle()
    val deleteState by viewModel.deleteProjectState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var menuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(deleteState.succeeded) {
        if (deleteState.succeeded) onDeleted()
    }

    LaunchedEffect(previewExternalState.urlToOpen) {
        val url = previewExternalState.urlToOpen ?: return@LaunchedEffect
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onSuccess {
            viewModel.onPreviewUrlHandled()
        }.onFailure { error ->
            if (error is ActivityNotFoundException) {
                viewModel.onPreviewOpenFailedNoBrowser()
            } else {
                viewModel.onPreviewOpenFailedUnknown(error.message)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            val title = (uiState as? ProjectDetailUiState.Content)?.data?.project?.title
                ?: stringResource(R.string.app_name)
            AppTopBar(
                title = title,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_cd)
                        )
                    }
                },
                actions = {
                    if (uiState is ProjectDetailUiState.Content) {
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.project_actions_cd))
                            }
                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.project_edit_action)) },
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.showEditProject((uiState as ProjectDetailUiState.Content).data.project)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.project_delete_action), color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.showDeleteProject()
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val current = uiState) {
                ProjectDetailUiState.Loading -> ProjectDetailSkeleton()
                is ProjectDetailUiState.Error -> ErrorView(message = current.message)
                is ProjectDetailUiState.NotFound ->
                    ErrorView(message = "Proyecto no encontrado: ${current.projectId}")
                is ProjectDetailUiState.Content -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        DetailTabBar(
                            selectedTabIndex = selectedTab,
                            onTabSelected = { selectedTab = it }
                        )

                        Box(modifier = Modifier.weight(1f)) {
                            when (DetailTab.entries[selectedTab]) {
                                DetailTab.Prompt -> PromptTab(
                                    projectId = projectId,
                                    messages = current.data.messages,
                                    currentVersionNumber = current.data.project.currentVersionNumber,
                                    input = promptInput,
                                    isOpeningPreview = previewExternalState.isResolving,
                                    previewError = previewExternalState.error,
                                    previewErrorMessage = previewExternalState.errorMessage,
                                    onPromptChange = viewModel::onPromptChange,
                                    onSend = viewModel::sendPrompt,
                                    onRetry = viewModel::retrySend,
                                    onOpenPreviewInBrowser = { versionNumber ->
                                        val version = current.data.versions.firstOrNull {
                                            it.versionNumber == versionNumber
                                        } ?: current.data.currentVersion
                                        viewModel.openPreviewInBrowser(version)
                                    },
                                    onDismissPreviewFeedback = viewModel::clearPreviewFeedback
                                )
                                DetailTab.Preview -> {
                                    val currentVersion = current.data.currentVersion
                                    LaunchedEffect(currentVersion?.versionNumber) {
                                        viewModel.resolvePreviewForDisplay(currentVersion)
                                    }
                                    PreviewTab(
                                        currentVersion = currentVersion,
                                        previewResolution = previewResolutionState,
                                        isOpeningExternal = previewExternalState.isResolving,
                                        externalError = previewExternalState.error,
                                        externalErrorMessage = previewExternalState.errorMessage,
                                        onOpenInBrowser = {
                                            viewModel.openPreviewInBrowser(currentVersion)
                                        },
                                        onDismissExternalFeedback = viewModel::clearPreviewFeedback,
                                        onRetryResolvePreview = {
                                            viewModel.resolvePreviewForDisplay(currentVersion, force = true)
                                        }
                                    )
                                }
                                DetailTab.History -> HistoryTab(
                                    versions = current.data.versions,
                                    errorMessage = current.data.historyError,
                                    regenerationState = regenerationState,
                                    onRegenerate = viewModel::regenerateVersion,
                                    onDismissRegenerationError = viewModel::clearRegenerationError
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (editState.isVisible) {
        AlertDialog(
            onDismissRequest = viewModel::dismissEditProject,
            title = { Text(stringResource(R.string.project_edit_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                    OutlinedTextField(
                        value = editState.title,
                        onValueChange = viewModel::onEditTitleChange,
                        enabled = !editState.isSaving,
                        label = { Text(stringResource(R.string.create_project_name_label)) },
                        isError = editState.titleError != null,
                        supportingText = editState.titleError?.let { error ->
                            { Text(stringResource(if (error == EditProjectValidationError.Required) R.string.project_title_required else R.string.project_title_too_long)) }
                        },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editState.description,
                        onValueChange = viewModel::onEditDescriptionChange,
                        enabled = !editState.isSaving,
                        label = { Text(stringResource(R.string.create_project_description_label)) },
                        isError = editState.descriptionError != null,
                        supportingText = {
                            Text(
                                editState.errorMessage
                                    ?: if (editState.descriptionError != null) stringResource(R.string.project_description_too_long)
                                    else stringResource(R.string.project_description_counter, editState.description.length)
                            )
                        },
                        minLines = 3,
                        maxLines = 6
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::saveProjectEdit, enabled = !editState.isSaving) {
                    if (editState.isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.project_edit_save))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissEditProject, enabled = !editState.isSaving) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (deleteState.isVisible) {
        val projectTitle = (uiState as? ProjectDetailUiState.Content)?.data?.project?.title.orEmpty()
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteProject,
            title = { Text(stringResource(R.string.project_delete_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    Text(stringResource(R.string.project_delete_message, projectTitle))
                    deleteState.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::deleteProject, enabled = !deleteState.isDeleting) {
                    if (deleteState.isDeleting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.project_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleteProject, enabled = !deleteState.isDeleting) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

private fun DetailTab.labelResource(): Int = when (this) {
    DetailTab.Prompt -> R.string.project_detail_tab_prompt
    DetailTab.Preview -> R.string.project_detail_tab_preview
    DetailTab.History -> R.string.project_detail_tab_history
}

@Composable
private fun DetailTabBar(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = AppSpacing.screenHorizontal,
                vertical = AppSpacing.md
            )
            .clip(AppShapes.pill)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(AppSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
    ) {
        DetailTab.entries.forEachIndexed { index, tab ->
            val selected = selectedTabIndex == index
            val containerColor by animateColorAsState(
                targetValue = if (selected) {
                    MaterialTheme.colorScheme.surface
                } else {
                    Color.Transparent
                },
                label = "detailTabContainer"
            )
            val contentColor by animateColorAsState(
                targetValue = if (selected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                label = "detailTabContent"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(AppShapes.pill)
                    .background(containerColor)
                    .clickable { onTabSelected(index) }
                    .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.sm),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(tab.labelResource()),
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
