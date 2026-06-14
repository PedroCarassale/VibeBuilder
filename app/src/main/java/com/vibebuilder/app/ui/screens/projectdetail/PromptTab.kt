package com.vibebuilder.app.ui.screens.projectdetail

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.vibebuilder.app.ui.components.AppTextField
import com.vibebuilder.app.ui.components.PrimaryButton
import com.vibebuilder.app.ui.components.PrimaryIconButton
import com.vibebuilder.app.ui.components.StatusBanner
import com.vibebuilder.app.ui.components.StatusPill
import com.vibebuilder.app.ui.theme.AppShapes
import com.vibebuilder.app.ui.theme.AppSpacing
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
                .padding(
                    horizontal = AppSpacing.screenHorizontal,
                    vertical = AppSpacing.md
                ),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            if (userMessages.isEmpty() && !input.isSending) {
                item(key = "prompt-empty") {
                    PromptEmptyCard()
                }
            }
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
            StatusBanner(
                message = input.sendError,
                isError = true,
                actionLabel = stringResource(R.string.retry),
                onAction = onRetry,
                actionEnabled = input.canSend,
                modifier = Modifier.padding(
                    horizontal = AppSpacing.screenHorizontal,
                    vertical = AppSpacing.xs
                )
            )
        }

        val resolvedPreviewError = resolvePreviewErrorMessage(previewError, previewErrorMessage)
        if (resolvedPreviewError != null) {
            StatusBanner(
                message = resolvedPreviewError,
                isError = true,
                actionLabel = stringResource(R.string.dismiss),
                onAction = onDismissPreviewFeedback,
                modifier = Modifier.padding(
                    horizontal = AppSpacing.screenHorizontal,
                    vertical = AppSpacing.xs
                )
            )
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
private fun PromptEmptyCard() {
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppSpacing.xl),
        containerColor = MaterialTheme.colorScheme.surface,
        contentPadding = PaddingValues(AppSpacing.xxl)
    ) {
        Text(
            text = stringResource(R.string.prompt_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(AppSpacing.sm))
        Text(
            text = stringResource(R.string.prompt_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ThinkingIndicator(verb: String, ellipsis: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth(),
        shape = AppShapes.card,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(AppSpacing.md))
            Text(
                text = verb + ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxWidth(0.92f)
                .widthIn(max = 520.dp)
                .clip(AppShapes.card)
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md)
        ) {
            Text(
                text = stringResource(R.string.prompt_user_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
            )
            Spacer(Modifier.height(AppSpacing.xs))
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
        contentPadding = PaddingValues(AppSpacing.lg)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.prompt_assistant_label),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            StatusPill(text = stringResource(R.string.prompt_version_format, versionNumber))
        }
        Spacer(Modifier.height(AppSpacing.md))
        Text(
            text = stringResource(R.string.prompt_assistant_app_ready),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(AppSpacing.xs))
        Text(
            text = stringResource(R.string.prompt_assistant_app_ready_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        PrimaryButton(
            text = if (isOpeningPreview) {
                stringResource(R.string.preview_opening_browser)
            } else {
                stringResource(R.string.preview_open_in_browser)
            },
            onClick = {
                onDismissPreviewFeedback()
                onOpenPreviewInBrowser(versionNumber)
            },
            enabled = !isOpeningPreview,
            isLoading = isOpeningPreview,
            modifier = Modifier.padding(top = AppSpacing.lg)
        )
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
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.background
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = AppSpacing.screenHorizontal,
                        vertical = AppSpacing.md
                    ),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                AppTextField(
                    value = value,
                    onValueChange = onChange,
                    modifier = Modifier.weight(1f),
                    placeholder = stringResource(R.string.prompt_input_placeholder),
                    minLines = 1,
                    maxLines = 5,
                    enabled = !isSending
                )
                PrimaryIconButton(onClick = onSend, enabled = canSend) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.prompt_send_cd)
                    )
                }
            }
        }
    }
}
