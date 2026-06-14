package com.vibebuilder.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vibebuilder.app.ui.theme.AppShapes
import com.vibebuilder.app.ui.theme.AppSpacing

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AppSpacing.buttonHeight),
        enabled = enabled && !isLoading,
        shape = AppShapes.button,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            disabledElevation = 0.dp
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            leadingIcon?.let {
                it()
                Spacer(Modifier.width(AppSpacing.sm))
            }
            Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AppSpacing.buttonHeight),
        enabled = enabled,
        shape = AppShapes.button,
        border = BorderStroke(
            width = 1.dp,
            color = if (enabled) {
                MaterialTheme.colorScheme.outline
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        leadingIcon?.let {
            it()
            Spacer(Modifier.width(AppSpacing.sm))
        }
        Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = AppShapes.button,
        colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun PrimaryIconButton(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(AppSpacing.iconButtonSize),
        shape = AppShapes.button,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        content()
    }
}

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        enabled = enabled,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        shape = AppShapes.input,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.onSurface,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            disabledBorderColor = MaterialTheme.colorScheme.outlineVariant,
            errorBorderColor = MaterialTheme.colorScheme.error,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            cursorColor = MaterialTheme.colorScheme.onSurface,
            focusedLabelColor = MaterialTheme.colorScheme.onSurface,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        action?.let {
            Spacer(Modifier.width(AppSpacing.md))
            Row(content = it)
        }
    }
}

@Composable
fun StatusPill(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    contentColor: Color? = null,
    borderColor: Color? = null
) {
    val resolvedContainer = containerColor ?: MaterialTheme.colorScheme.secondaryContainer
    val resolvedContent = contentColor ?: MaterialTheme.colorScheme.onSecondaryContainer
    Surface(
        modifier = modifier,
        shape = AppShapes.pill,
        color = resolvedContainer,
        contentColor = resolvedContent,
        border = BorderStroke(1.dp, borderColor ?: MaterialTheme.colorScheme.outlineVariant)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun StatusBanner(
    message: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actionEnabled: Boolean = true
) {
    val container = if (isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val content = if (isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    AppCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = container,
        borderColor = MaterialTheme.colorScheme.outlineVariant,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(AppSpacing.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = content
            )
            if (actionLabel != null && onAction != null) {
                GhostButton(
                    text = actionLabel,
                    onClick = onAction,
                    enabled = actionEnabled,
                    modifier = Modifier
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    Column(modifier = modifier.fillMaxWidth()) {
        TopAppBar(
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = navigationIcon,
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                scrolledContainerColor = MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurface
            )
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
