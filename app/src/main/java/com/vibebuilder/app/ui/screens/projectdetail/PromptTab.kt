package com.vibebuilder.app.ui.screens.projectdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vibebuilder.app.R
import com.vibebuilder.app.domain.model.PromptMessage

@Composable
fun PromptTab(
    messages: List<PromptMessage>,
    input: PromptInputState,
    onPromptChange: (String) -> Unit,
    onSend: () -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items = messages, key = { it.id }) { message ->
                MessageBubble(message)
            }
        }

        if (input.sendError != null) {
            Text(
                text = input.sendError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
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
private fun MessageBubble(message: PromptMessage) {
    val isUser = message.role == PromptMessage.Role.USER
    val bubbleColor = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .background(color = bubbleColor, shape = RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = if (isUser) "Tú" else "VibeBuilder",
                style = MaterialTheme.typography.labelLarge,
                color = textColor
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor
            )
            if (message.versionNumber != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Versión ${message.versionNumber}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
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
            if (isSending) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.padding(4.dp)
                )
            } else {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.prompt_send_cd)
                )
            }
        }
    }
}
