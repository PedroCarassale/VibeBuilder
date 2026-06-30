package com.vibebuilder.app.ui.components

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vibebuilder.app.R
import com.vibebuilder.app.ui.theme.AppSpacing

@Composable
fun LoadingView(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        PixelStarLoader(label = stringResource(R.string.loading_generating_project))
    }
}

@Composable
fun ProjectListSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                horizontal = AppSpacing.screenHorizontal,
                vertical = AppSpacing.screenVertical
            ),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        SkeletonBlock(modifier = Modifier.fillMaxWidth(0.72f), height = 28.dp)
        SkeletonBlock(modifier = Modifier.fillMaxWidth(0.92f), height = 18.dp)
        Spacer(Modifier.height(AppSpacing.sm))
        SkeletonBlock(modifier = Modifier.fillMaxWidth(), height = 56.dp)
        repeat(4) {
            AppCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(AppSpacing.lg)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                    SkeletonBlock(modifier = Modifier.weight(1f), height = 22.dp)
                    SkeletonBlock(modifier = Modifier.weight(0.28f), height = 22.dp)
                }
                Spacer(Modifier.height(AppSpacing.md))
                SkeletonBlock(modifier = Modifier.fillMaxWidth(0.88f), height = 16.dp)
                Spacer(Modifier.height(AppSpacing.xs))
                SkeletonBlock(modifier = Modifier.fillMaxWidth(0.66f), height = 16.dp)
            }
        }
    }
}

@Composable
fun ProjectDetailSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                horizontal = AppSpacing.screenHorizontal,
                vertical = AppSpacing.screenVertical
            ),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        SkeletonBlock(modifier = Modifier.fillMaxWidth(), height = 48.dp)
        AppCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(AppSpacing.xl)
        ) {
            SkeletonBlock(modifier = Modifier.fillMaxWidth(0.64f), height = 22.dp)
            Spacer(Modifier.height(AppSpacing.md))
            SkeletonBlock(modifier = Modifier.fillMaxWidth(), height = 18.dp)
            Spacer(Modifier.height(AppSpacing.xs))
            SkeletonBlock(modifier = Modifier.fillMaxWidth(0.74f), height = 18.dp)
        }
        AppCard(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(AppSpacing.xl)
        ) {
            SkeletonBlock(modifier = Modifier.fillMaxWidth(), height = 120.dp)
            Spacer(Modifier.height(AppSpacing.md))
            SkeletonBlock(modifier = Modifier.fillMaxWidth(0.86f), height = 18.dp)
        }
    }
}

@Composable
fun ErrorView(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize().padding(AppSpacing.xxl), contentAlignment = Alignment.Center) {
        AppCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(AppSpacing.xxl)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
            ) {
                Text(
                    text = stringResource(R.string.generic_error),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                if (onRetry != null) {
                    SecondaryButton(
                        text = stringResource(R.string.retry),
                        onClick = onRetry
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize().padding(AppSpacing.xxl), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                PrimaryButton(
                    text = actionLabel,
                    onClick = onAction,
                    modifier = Modifier.padding(top = AppSpacing.xs)
                )
            }
        }
    }
}

@Composable
fun EmptyView(
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    EmptyState(
        title = title,
        subtitle = subtitle,
        actionLabel = actionLabel,
        onAction = onAction,
        modifier = modifier
    )
}
