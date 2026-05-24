package com.vibebuilder.app.ui.screens.projectdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vibebuilder.app.R
import com.vibebuilder.app.domain.model.ProjectVersion
import com.vibebuilder.app.domain.model.VersionStatus
import com.vibebuilder.app.ui.components.AppCard
import com.vibebuilder.app.ui.components.ErrorView
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun HistoryTab(
    versions: List<ProjectVersion>,
    errorMessage: String?
) {
    if (!errorMessage.isNullOrBlank()) {
        ErrorView(message = errorMessage)
        return
    }

    if (versions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.history_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val latestSuccessVersionNumber = versions
            .filter { it.status == VersionStatus.READY }
            .maxOfOrNull { it.versionNumber }
        items(items = versions, key = { it.id }) { version ->
            VersionCard(
                version = version,
                isLatestSuccess = version.versionNumber == latestSuccessVersionNumber
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun VersionCard(
    version: ProjectVersion,
    isLatestSuccess: Boolean
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Versión ${version.versionNumber}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (isLatestSuccess) {
                stringResource(R.string.history_latest_success)
            } else {
                stringResource(R.string.history_status_format, statusLabel(version.status))
            },
            style = MaterialTheme.typography.labelLarge,
            color = if (isLatestSuccess) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = formatTimestamp(version),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = version.prompt,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
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
    VersionStatus.READY -> "success"
    VersionStatus.FAILED -> "failed"
    VersionStatus.GENERATING -> "generating"
}
