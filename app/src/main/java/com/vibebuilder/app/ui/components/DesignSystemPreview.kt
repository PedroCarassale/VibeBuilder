package com.vibebuilder.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.vibebuilder.app.ui.theme.AppSpacing
import com.vibebuilder.app.ui.theme.VibeBuilderTheme

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun DesignSystemPreview() {
    VibeBuilderTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(AppSpacing.screenHorizontal),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)
            ) {
                SectionHeader(
                    title = "Construye desde una idea",
                    subtitle = "Crea, previsualiza e itera web apps generadas por IA desde tu teléfono."
                )
                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(AppSpacing.lg)
                ) {
                    Text(
                        text = "Dashboard para un estudio creativo",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Landing, tareas y panel de métricas en una sola web app.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AppTextField(
                    value = "Landing para un gimnasio boutique",
                    onValueChange = {},
                    label = "Prompt"
                )
                PrimaryButton(text = "Crear proyecto", onClick = {})
                SecondaryButton(text = "Probar conexión", onClick = {})
                StatusBanner(message = "La preview estará disponible cuando termine la generación.")
            }
        }
    }
}
