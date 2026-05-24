package com.vibebuilder.app.ui.screens.projectdetail

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vibebuilder.app.R
import com.vibebuilder.app.domain.model.PromptMessage
import com.vibebuilder.app.ui.components.AppCard
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock

private const val OptimisticUserMessageId = "local-optimistic-user"

@Composable
fun PromptTab(
    projectId: String,
    messages: List<PromptMessage>,
    currentVersionNumber: Int,
    input: PromptInputState,
    isOpeningPreview: Boolean,
    previewError: PreviewExternalError?,
    previewErrorMessage: String?,
    onPromptChange: (String) -> Unit,
    onSend: () -> Unit,
    onRetry: () -> Unit,
    onOpenPreviewInBrowser: (versionNumber: Int) -> Unit,
    onDismissPreviewFeedback: () -> Unit
) {
    val listState = rememberLazyListState()
    val thinkingVerbs = stringArrayResource(R.array.prompt_thinking_verbs).toList()
    var thinkingVerbIndex by remember { mutableIntStateOf(0) }
    var ellipsisPhase by remember { mutableIntStateOf(0) }

    val displayMessages = remember(messages, input.optimisticUserBubble, input.isSending, projectId) {
        val pending = input.optimisticUserBubble?.trim().orEmpty()
        if (pending.isEmpty() || !input.isSending) {
            messages
        } else {
            val last = messages.lastOrNull()
            if (last?.role == PromptMessage.Role.USER && last.content == pending) {
                messages
            } else {
                val pid = messages.firstOrNull()?.projectId?.takeIf { it.isNotBlank() } ?: projectId
                messages + PromptMessage(
                    id = OptimisticUserMessageId,
                    projectId = pid,
                    role = PromptMessage.Role.USER,
                    content = pending,
                    createdAt = Clock.System.now(),
                    versionNumber = null
                )
            }
        }
    }

    LaunchedEffect(input.isSending) {
        if (!input.isSending) return@LaunchedEffect
        thinkingVerbIndex = 0
        ellipsisPhase = 0
        while (true) {
            delay(2200)
            thinkingVerbIndex = (thinkingVerbIndex + 1) % thinkingVerbs.size
        }
    }

    LaunchedEffect(input.isSending) {
        if (!input.isSending) return@LaunchedEffect
        while (true) {
            delay(420)
            ellipsisPhase = (ellipsisPhase + 1) % 3
        }
    }

    val ellipsis = when (ellipsisPhase) {
        0 -> "."
        1 -> ".."
        else -> "..."
    }

    val userMessages = remember(displayMessages) {
        displayMessages.filter { it.role == PromptMessage.Role.USER }
    }
    val showAssistantCard = currentVersionNumber > 0 && userMessages.isNotEmpty()

    val lastListIndex = userMessages.lastIndex +
        (if (showAssistantCard) 1 else 0) +
        if (input.isSending) 1 else 0
    LaunchedEffect(userMessages.size, showAssistantCard, input.isSending) {
        if (lastListIndex >= 0) {
            listState.animateScrollToItem(lastListIndex)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items = userMessages, key = { it.id }) { message ->
                UserMessageBubble(message = message)
            }
            if (showAssistantCard) {
                item(key = "assistant-app-ready") {
                    AssistantAppReadyCard(
                        versionNumber = currentVersionNumber,
                        isOpeningPreview = isOpeningPreview,
                        onOpenPreviewInBrowser = onOpenPreviewInBrowser,
                        onDismissPreviewFeedback = onDismissPreviewFeedback
                    )
                }
            }
            if (input.isSending) {
                item(key = "thinking") {
                    ThinkingIndicator(
                        verb = thinkingVerbs[thinkingVerbIndex],
                        ellipsis = ellipsis
                    )
                }
            }
        }

        if (input.sendError != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = input.sendError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                TextButton(onClick = onRetry, enabled = input.canSend) {
                    Text(stringResource(R.string.retry))
                }
            }
        }

        val resolvedPreviewError = resolvePreviewErrorMessage(previewError, previewErrorMessage)
        if (resolvedPreviewError != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = resolvedPreviewError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                TextButton(onClick = onDismissPreviewFeedback) {
                    Text(stringResource(R.string.dismiss))
                }
            }
        }

        PromptInputBar(
            value = input.text,
            isSending = input.isSending,
            canSend = input.canSend,
            onChange = onPromptChange,
            onSend = onSend
        )
    }
}

@Composable
private fun ThinkingIndicator(verb: String, ellipsis: String) {
    val shape = MaterialTheme.shapes.large
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            strokeWidth = 2.dp,
            modifier = Modifier.size(18.dp),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = verb + ellipsis,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun resolvePreviewErrorMessage(
    previewError: PreviewExternalError?,
    previewErrorMessage: String?
): String? = when (previewError) {
    PreviewExternalError.NotReady -> stringResource(R.string.preview_not_ready)
    PreviewExternalError.Expired -> stringResource(R.string.preview_external_expired)
    PreviewExternalError.Unavailable -> stringResource(R.string.preview_external_unavailable)
    PreviewExternalError.NoBrowser -> stringResource(R.string.preview_external_no_browser)
    PreviewExternalError.Unknown -> previewErrorMessage ?: stringResource(R.string.preview_external_unknown)
    null -> null
}

@Composable
private fun UserMessageBubble(message: PromptMessage) {
    val shape = MaterialTheme.shapes.medium

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxWidth(0.92f)
                .widthIn(max = 520.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = stringResource(R.string.prompt_user_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.92f)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
private fun AssistantAppReadyCard(
    versionNumber: Int,
    isOpeningPreview: Boolean,
    onOpenPreviewInBrowser: (versionNumber: Int) -> Unit,
    onDismissPreviewFeedback: () -> Unit
) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        borderColor = MaterialTheme.colorScheme.outlineVariant,
        contentPadding = PaddingValues(16.dp)
    ) {
        Text(
            text = stringResource(R.string.prompt_assistant_label),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.prompt_assistant_app_ready),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.prompt_assistant_app_ready_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.prompt_version_format, versionNumber),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                onDismissPreviewFeedback()
                onOpenPreviewInBrowser(versionNumber)
            },
            enabled = !isOpeningPreview,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (isOpeningPreview) {
                    stringResource(R.string.preview_opening_browser)
                } else {
                    stringResource(R.string.preview_open_in_browser)
                }
            )
        }
    }
}

@Composable
private fun PromptInputBar(
    value: String,
    isSending: Boolean,
    canSend: Boolean,
    onChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.prompt_input_placeholder)) },
            minLines = 1,
            maxLines = 5,
            enabled = !isSending
        )
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onSend, enabled = canSend) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = stringResource(R.string.prompt_send_cd)
            )
        }
    }
}
