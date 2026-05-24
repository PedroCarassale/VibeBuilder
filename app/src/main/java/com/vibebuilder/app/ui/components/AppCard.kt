package com.vibebuilder.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.medium,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    colors: CardColors? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    expandInnerHeight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val resolvedColors = colors ?: CardDefaults.cardColors(containerColor = containerColor)
    val border = BorderStroke(1.dp, borderColor)
    val cardModifier = if (onClick != null) {
        modifier.clickable(onClick = onClick)
    } else {
        modifier
    }
    val innerColumnModifier = Modifier
        .padding(contentPadding)
        .fillMaxWidth()
        .then(if (expandInnerHeight) Modifier.fillMaxHeight() else Modifier)
    Card(
        modifier = cardModifier,
        shape = shape,
        colors = resolvedColors,
        border = border
    ) {
        Column(innerColumnModifier) {
            content()
        }
    }
}
