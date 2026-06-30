package com.vibebuilder.app.ui.components

import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vibebuilder.app.ui.theme.AppShapes
import com.vibebuilder.app.ui.theme.AppSpacing
import kotlinx.coroutines.delay

@Composable
fun PixelStarLoader(
    modifier: Modifier = Modifier,
    animated: Boolean = rememberSystemAnimationsEnabled(),
    label: String = "Generando proyecto...",
    containerColor: Color = Color(0xFF111318),
    showContainer: Boolean = true
) {
    val frames = remember { listOf("·", "✣", "✦", "✣") }
    var frameIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(animated) {
        frameIndex = 0
        if (!animated) return@LaunchedEffect
        while (true) {
            delay(200)
            frameIndex = (frameIndex + 1) % frames.size
        }
    }

    val containerModifier = if (showContainer) {
        Modifier
            .clip(AppShapes.button)
            .background(containerColor)
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm)
    } else {
        Modifier
    }

    Row(
        modifier = modifier.then(containerModifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(width = 18.dp, height = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = frames[frameIndex],
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFFFFB86C)
            )
        }
        Spacer(Modifier.width(AppSpacing.sm))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFFC4C8D0)
        )
    }
}

@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    height: Dp,
    animated: Boolean = rememberSystemAnimationsEnabled()
) {
    val alpha = rememberSkeletonAlpha(animated)
    Box(
        modifier = modifier
            .height(height)
            .clip(AppShapes.card)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
    )
}

@Composable
fun rememberSystemAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) > 0f
        }.getOrDefault(true)
    }
}

@Composable
private fun rememberSkeletonAlpha(animated: Boolean): Float {
    if (!animated) return 0.42f
    val transition = rememberInfiniteTransition(label = "skeletonPulse")
    val alpha by transition.animateFloat(
        initialValue = 0.32f,
        targetValue = 0.62f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeletonAlpha"
    )
    return alpha
}
