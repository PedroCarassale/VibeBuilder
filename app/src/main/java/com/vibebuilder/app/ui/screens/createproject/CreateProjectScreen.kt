package com.vibebuilder.app.ui.screens.createproject

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vibebuilder.app.R
import com.vibebuilder.app.ui.components.AppCard
import com.vibebuilder.app.ui.components.AppTextField
import com.vibebuilder.app.ui.components.AppTopBar
import com.vibebuilder.app.ui.components.PrimaryButton
import com.vibebuilder.app.ui.components.SectionHeader
import com.vibebuilder.app.ui.components.StatusBanner
import com.vibebuilder.app.ui.theme.AppSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProjectScreen(
    onBack: () -> Unit,
    onProjectCreated: (String) -> Unit,
    viewModel: CreateProjectViewModel = viewModel(factory = CreateProjectViewModel.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.createdProjectId) {
        val id = state.createdProjectId
        if (id != null) {
            viewModel.consumeNavigation()
            onProjectCreated(id)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = stringResource(R.string.create_project_title),
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = AppSpacing.screenHorizontal,
                    vertical = AppSpacing.screenVertical
                ),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)
        ) {
            SectionHeader(
                title = stringResource(R.string.create_project_header_title),
                subtitle = stringResource(R.string.create_project_header_subtitle)
            )

            AppCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(AppSpacing.xl)
            ) {
                Text(
                    text = stringResource(R.string.create_project_details_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                AppTextField(
                    value = state.title,
                    onValueChange = viewModel::onTitleChange,
                    label = stringResource(R.string.create_project_name_label),
                    singleLine = true,
                    isError = state.titleError != null,
                    supportingText = state.titleError,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = AppSpacing.lg)
                )

                AppTextField(
                    value = state.description,
                    onValueChange = viewModel::onDescriptionChange,
                    label = stringResource(R.string.create_project_description_label),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = AppSpacing.md)
                )

                if (state.submitError != null) {
                    StatusBanner(
                        message = state.submitError!!,
                        isError = true,
                        modifier = Modifier.padding(top = AppSpacing.md)
                    )
                }

                PrimaryButton(
                    text = stringResource(R.string.create_project_submit),
                    onClick = viewModel::submit,
                    enabled = state.canSubmit,
                    isLoading = state.isSubmitting,
                    modifier = Modifier.padding(top = AppSpacing.lg)
                )
            }
        }
    }
}
