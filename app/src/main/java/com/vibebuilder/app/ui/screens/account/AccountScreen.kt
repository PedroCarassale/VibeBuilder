package com.vibebuilder.app.ui.screens.account

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
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    onBack: () -> Unit,
    viewModel: AccountViewModel = viewModel(factory = AccountViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = stringResource(R.string.account_title),
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
                title = if (state.session == null) {
                    stringResource(R.string.account_guest_title)
                } else {
                    stringResource(R.string.account_signed_in_title)
                },
                subtitle = if (state.session == null) {
                    stringResource(R.string.account_guest_subtitle)
                } else {
                    stringResource(R.string.account_signed_in_subtitle)
                }
            )

            AppCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(AppSpacing.xl)
            ) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                val user = state.session?.user
                Text(
                    text = user?.name ?: stringResource(R.string.account_guest_name),
                    modifier = Modifier.padding(top = AppSpacing.md),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = user?.email ?: stringResource(R.string.account_guest_email),
                    modifier = Modifier.padding(top = AppSpacing.xs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (state.session == null) {
                if (state.registerMode) {
                    AppTextField(
                        value = state.nameInput,
                        onValueChange = viewModel::onNameChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = stringResource(R.string.account_name_label),
                        singleLine = true,
                        enabled = !state.loading
                    )
                }
                AppTextField(
                    value = state.emailInput,
                    onValueChange = viewModel::onEmailChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.account_email_label),
                    singleLine = true,
                    enabled = !state.loading
                )
                AppTextField(
                    value = state.passwordInput,
                    onValueChange = viewModel::onPasswordChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.account_password_label),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = !state.loading
                )
                PrimaryButton(
                    text = if (state.registerMode) {
                        stringResource(R.string.account_register)
                    } else {
                        stringResource(R.string.account_login)
                    },
                    onClick = viewModel::submit,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.loading,
                    isLoading = state.loading,
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null)
                    }
                )
                GhostButton(
                    text = if (state.registerMode) {
                        stringResource(R.string.account_switch_to_login)
                    } else {
                        stringResource(R.string.account_switch_to_register)
                    },
                    onClick = viewModel::toggleMode,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.loading
                )
            } else {
                SecondaryButton(
                    text = stringResource(R.string.account_logout),
                    onClick = viewModel::logout,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.loading,
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                    }
                )
            }

            state.statusMessage?.let { message ->
                StatusBanner(message = message)
            }
            state.errorMessage?.let { message ->
                StatusBanner(message = message, isError = true)
            }
        }
    }
}
