package com.vibebuilder.app.ui.screens.projectdetail

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vibebuilder.app.R
import com.vibebuilder.app.ui.components.ErrorView
import com.vibebuilder.app.ui.components.LoadingView

private enum class DetailTab { Prompt, Preview, History }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    projectId: String,
    onBack: () -> Unit,
    viewModel: ProjectDetailViewModel = viewModel(
        factory = remember(projectId) { ProjectDetailViewModel.factory(projectId) }
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val promptInput by viewModel.promptInput.collectAsStateWithLifecycle()
    val previewExternalState by viewModel.previewExternalState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

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
        topBar = {
            TopAppBar(
                title = {
                    val title = (uiState as? ProjectDetailUiState.Content)?.data?.project?.title
                        ?: stringResource(R.string.app_name)
                    Text(title)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_cd)
                        )
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
                ProjectDetailUiState.Loading -> LoadingView()
                is ProjectDetailUiState.Error -> ErrorView(message = current.message)
                is ProjectDetailUiState.NotFound ->
                    ErrorView(message = "Proyecto no encontrado: ${current.projectId}")
                is ProjectDetailUiState.Content -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        TabRow(selectedTabIndex = selectedTab) {
                            DetailTab.entries.forEachIndexed { index, tab ->
                                Tab(
                                    selected = selectedTab == index,
                                    onClick = { selectedTab = index },
                                    text = { Text(stringResource(tab.labelResource())) }
                                )
                            }
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            when (DetailTab.entries[selectedTab]) {
                                DetailTab.Prompt -> PromptTab(
                                    messages = current.data.messages,
                                    input = promptInput,
                                    onPromptChange = viewModel::onPromptChange,
                                    onSend = viewModel::sendPrompt,
                                    onRetry = viewModel::retrySend
                                )
                                DetailTab.Preview -> PreviewTab(
                                    currentVersion = current.data.currentVersion,
                                    isOpeningExternal = previewExternalState.isResolving,
                                    externalError = previewExternalState.error,
                                    externalErrorMessage = previewExternalState.errorMessage,
                                    onOpenInBrowser = { viewModel.openPreviewInBrowser(current.data.currentVersion) },
                                    onDismissExternalFeedback = viewModel::clearPreviewFeedback
                                )
                                DetailTab.History -> HistoryTab(
                                    versions = current.data.versions,
                                    errorMessage = current.data.historyError
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun DetailTab.labelResource(): Int = when (this) {
    DetailTab.Prompt -> R.string.project_detail_tab_prompt
    DetailTab.Preview -> R.string.project_detail_tab_preview
    DetailTab.History -> R.string.project_detail_tab_history
}
