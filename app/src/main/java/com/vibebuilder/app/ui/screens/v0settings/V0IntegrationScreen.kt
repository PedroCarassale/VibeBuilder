package com.vibebuilder.app.ui.screens.v0settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
            TopAppBar(
                title = { Text(stringResource(R.string.v0_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_cd)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.v0_settings_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(8.dp))

                TextButton(
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(V0_KEYS_URL))
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.v0_settings_open_keys_page))
                }

                Spacer(Modifier.height(8.dp))

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
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = state.apiKeyInput,
                onValueChange = viewModel::onApiKeyChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.v0_settings_api_key_label)) },
                placeholder = { Text(stringResource(R.string.v0_settings_api_key_placeholder)) },
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    TextButton(onClick = { passwordVisible = !passwordVisible }) {
                        Text(
                            if (passwordVisible) stringResource(R.string.v0_settings_hide)
                            else stringResource(R.string.v0_settings_show)
                        )
                    }
                },
                singleLine = true,
                enabled = !state.loading
            )

            if (state.loading) {
                CircularProgressIndicator(modifier = Modifier.padding(vertical = 8.dp))
            }

            Button(
                onClick = viewModel::saveKey,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.loading && state.keyStorageAvailable
            ) {
                Text(stringResource(R.string.v0_settings_save))
            }

            OutlinedButton(
                onClick = { viewModel.testConnection(useInputField = true) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.loading && state.apiKeyInput.isNotBlank()
            ) {
                Text(stringResource(R.string.v0_settings_test_field))
            }

            OutlinedButton(
                onClick = { viewModel.testConnection(useInputField = false) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.loading && state.sessionKeyConfigured
            ) {
                Text(stringResource(R.string.v0_settings_test_saved))
            }

            OutlinedButton(
                onClick = viewModel::clearSavedKey,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.loading && state.sessionKeyConfigured && state.keyStorageAvailable
            ) {
                Text(stringResource(R.string.v0_settings_clear))
            }

            state.statusMessage?.let { msg ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            state.errorMessage?.let { err ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.v0_settings_footer_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
