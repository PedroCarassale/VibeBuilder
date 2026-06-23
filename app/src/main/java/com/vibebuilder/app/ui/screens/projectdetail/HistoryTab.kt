package com.vibebuilder.app.ui.screens.projectdetail

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vibebuilder.app.R
import com.vibebuilder.app.domain.model.ProjectVersion
import com.vibebuilder.app.domain.model.VersionStatus
import com.vibebuilder.app.ui.components.AppCard
import com.vibebuilder.app.ui.components.EmptyView
import com.vibebuilder.app.ui.components.ErrorView
import com.vibebuilder.app.ui.components.PrimaryButton
import com.vibebuilder.app.ui.components.SectionHeader
import com.vibebuilder.app.ui.components.StatusPill
import com.vibebuilder.app.ui.theme.AppSpacing
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun HistoryTab(
    versions: List<ProjectVersion>,
    errorMessage: String?,
    regenerationState: VersionRegenerationUiState,
    onRegenerate: (ProjectVersion) -> Unit,
    onDismissRegenerationError: (String) -> Unit
) {
    if (!errorMessage.isNullOrBlank()) {
        ErrorView(message = errorMessage)
        return
    }

    if (versions.isEmpty()) {
        EmptyView(
            title = stringResource(R.string.history_empty_title),
            subtitle = stringResource(R.string.history_empty)
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = AppSpacing.screenHorizontal,
            vertical = AppSpacing.screenVertical
        ),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        val latestSuccessVersionNumber = versions
            .filter { it.status == VersionStatus.READY }
            .maxOfOrNull { it.versionNumber }
        item {
            SectionHeader(
                title = stringResource(R.string.history_title),
                subtitle = stringResource(R.string.history_subtitle),
                modifier = Modifier.padding(bottom = AppSpacing.sm)
            )
        }
        items(items = versions, key = { it.id }) { version ->
            VersionCard(
                version = version,
                isLatestSuccess = version.versionNumber == latestSuccessVersionNumber,
                isRegenerating = regenerationState.isRegenerating(version.id),
                regenerationError = regenerationState.errorFor(version.id),
                onRegenerate = { onRegenerate(version) },
                onDismissRegenerationError = { onDismissRegenerationError(version.id) }
            )
        }
        item { Spacer(Modifier.height(AppSpacing.xxl)) }
    }
}

@Composable
private fun VersionCard(
    version: ProjectVersion,
    isLatestSuccess: Boolean,
    isRegenerating: Boolean,
    regenerationError: String?,
    onRegenerate: () -> Unit,
    onDismissRegenerationError: () -> Unit
) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(AppSpacing.lg)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            Text(
                text = "Versión ${version.versionNumber}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            StatusPill(
                text = if (isLatestSuccess) {
                    stringResource(R.string.history_latest_success)
                } else {
                    statusLabel(version.status)
                },
                containerColor = when {
                    isLatestSuccess -> MaterialTheme.colorScheme.primary
                    version.status == VersionStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.secondaryContainer
                },
                contentColor = when {
                    isLatestSuccess -> MaterialTheme.colorScheme.onPrimary
                    version.status == VersionStatus.FAILED -> MaterialTheme.colorScheme.onErrorContainer
                    else -> MaterialTheme.colorScheme.onSecondaryContainer
                }
            )
        }
        Spacer(Modifier.height(AppSpacing.sm))
        Text(
            text = if (isLatestSuccess) {
                stringResource(R.string.history_latest_success)
            } else {
                stringResource(R.string.history_status_format, statusLabel(version.status))
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(AppSpacing.xs))
        Text(
            text = formatTimestamp(version),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(AppSpacing.md))
        Text(
            text = version.prompt,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (version.status == VersionStatus.FAILED) {
            Spacer(Modifier.height(AppSpacing.md))
            PrimaryButton(
                text = if (isRegenerating) {
                    stringResource(R.string.history_regenerating)
                } else {
                    stringResource(R.string.history_regenerate)
                },
                onClick = {
                    onDismissRegenerationError()
                    onRegenerate()
                },
                enabled = !isRegenerating,
                isLoading = isRegenerating,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null
                    )
                }
            )
            regenerationError?.let { error ->
                Spacer(Modifier.height(AppSpacing.sm))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun formatTimestamp(version: ProjectVersion): String {
    val local = version.createdAt.toLocalDateTime(TimeZone.currentSystemDefault())
    val day = local.dayOfMonth.toString().padStart(2, '0')
    val month = local.monthNumber.toString().padStart(2, '0')
    val hour = local.hour.toString().padStart(2, '0')
    val minute = local.minute.toString().padStart(2, '0')
    return "$day/$month/${local.year} $hour:$minute"
}

private fun statusLabel(status: VersionStatus): String = when (status) {
    VersionStatus.READY -> "lista"
    VersionStatus.FAILED -> "fallida"
    VersionStatus.QUEUED -> "en cola"
    VersionStatus.GENERATING -> "generando"
    VersionStatus.VALIDATING -> "validando"
    VersionStatus.CANCELLED -> "cancelada"
}
