package com.vibebuilder.app.ui.screens.v0settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vibebuilder.app.R
import com.vibebuilder.app.ui.components.AppCard
import com.vibebuilder.app.ui.components.AppTextField
import com.vibebuilder.app.ui.components.AppTopBar
import com.vibebuilder.app.ui.components.GhostButton
import com.vibebuilder.app.ui.components.PrimaryButton
import com.vibebuilder.app.ui.components.SecondaryButton
import com.vibebuilder.app.ui.components.SectionHeader
import com.vibebuilder.app.ui.components.StatusBanner
import com.vibebuilder.app.ui.theme.AppSpacing

private const val V0_KEYS_URL = "https://v0.app/chat/settings/keys"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V0IntegrationScreen(
    onBack: () -> Unit,
    viewModel: V0IntegrationViewModel = viewModel(factory = V0IntegrationViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = stringResource(R.string.v0_settings_title),
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
                .padding(
                    horizontal = AppSpacing.screenHorizontal,
                    vertical = AppSpacing.screenVertical
                )
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)
        ) {
            SectionHeader(
                title = stringResource(R.string.v0_settings_header_title),
                subtitle = stringResource(R.string.v0_settings_header_subtitle)
            )

            AppCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(AppSpacing.xl)
            ) {
                Text(
                    text = stringResource(R.string.v0_settings_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                GhostButton(
                    text = stringResource(R.string.v0_settings_open_keys_page),
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(V0_KEYS_URL))
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = AppSpacing.sm)
                )
            }

            AppCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(AppSpacing.xl)
            ) {
                Text(
                    text = stringResource(R.string.v0_settings_status_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                SelectionContainer {
                    val envYes = stringResource(R.string.v0_settings_yes)
                    val envNo = stringResource(R.string.v0_settings_no)
                    val sessionLine =
                        if (state.sessionKeyConfigured) {
                            state.sessionKeyHint
                                ?: stringResource(R.string.v0_settings_session_active_masked)
                        } else {
                            envNo
                        }
                    Text(
                        text = stringResource(R.string.v0_settings_status_block).format(
                            if (state.envKeyActive) envYes else envNo,
                            if (state.keyStorageAvailable) envYes else envNo,
                            sessionLine
                        ),
                        modifier = Modifier.padding(top = AppSpacing.sm),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AppTextField(
                value = state.apiKeyInput,
                onValueChange = viewModel::onApiKeyChange,
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.v0_settings_api_key_label),
                placeholder = stringResource(R.string.v0_settings_api_key_placeholder),
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    GhostButton(
                        text = if (passwordVisible) {
                            stringResource(R.string.v0_settings_hide)
                        } else {
                            stringResource(R.string.v0_settings_show)
                        },
                        onClick = { passwordVisible = !passwordVisible }
                    )
                },
                singleLine = true,
                enabled = !state.loading
            )

            if (state.loading) {
                StatusBanner(message = stringResource(R.string.loading))
            }

            PrimaryButton(
                text = stringResource(R.string.v0_settings_save),
                onClick = viewModel::saveKey,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.loading && state.keyStorageAvailable
            )

            SecondaryButton(
                text = stringResource(R.string.v0_settings_test_field),
                onClick = { viewModel.testConnection(useInputField = true) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.loading && state.apiKeyInput.isNotBlank()
            )

            SecondaryButton(
                text = stringResource(R.string.v0_settings_test_saved),
                onClick = { viewModel.testConnection(useInputField = false) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.loading && state.sessionKeyConfigured
            )

            SecondaryButton(
                text = stringResource(R.string.v0_settings_clear),
                onClick = viewModel::clearSavedKey,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.loading && state.sessionKeyConfigured && state.keyStorageAvailable
            )

            state.statusMessage?.let { msg ->
                StatusBanner(message = msg)
            }

            state.errorMessage?.let { err ->
                StatusBanner(message = err, isError = true)
            }

            AppCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(AppSpacing.lg),
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = stringResource(R.string.v0_settings_footer_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
