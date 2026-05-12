package com.vibebuilder.app.ui.screens.projectdetail

import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.vibebuilder.app.R
import com.vibebuilder.app.domain.model.ProjectVersion
import com.vibebuilder.app.domain.model.VersionStatus

@Composable
fun PreviewTab(
    currentVersion: ProjectVersion?,
    isOpeningExternal: Boolean,
    externalError: PreviewExternalError?,
    externalErrorMessage: String?,
    onOpenInBrowser: () -> Unit,
    onDismissExternalFeedback: () -> Unit
) {
    if (currentVersion == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.preview_placeholder_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val previewUrl = currentVersion.previewUrl?.trim().orEmpty()
    val canRenderPreview =
        currentVersion.status == VersionStatus.READY && isSupportedPreviewUrl(previewUrl)
    var isLoading by remember(previewUrl) { mutableStateOf(canRenderPreview) }
    var hasError by remember(previewUrl) { mutableStateOf(false) }
    var webViewRef by remember(previewUrl) { mutableStateOf<WebView?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.preview_placeholder_title),
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "Versión ${currentVersion.versionNumber}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Prompt",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = currentVersion.prompt,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            when {
                !canRenderPreview -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (currentVersion.status != VersionStatus.READY) {
                                stringResource(R.string.preview_not_ready)
                            } else {
                                stringResource(R.string.preview_url_unavailable)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                hasError -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.preview_load_failed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = {
                            hasError = false
                            isLoading = true
                            webViewRef?.reload()
                        }) {
                            Text(text = stringResource(R.string.retry))
                        }
                    }
                }
                else -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { context ->
                                WebView(context).apply {
                                    webViewRef = this
                                    settings.apply {
                                        javaScriptEnabled = true
                                        domStorageEnabled = true
                                        cacheMode = WebSettings.LOAD_DEFAULT
                                        loadWithOverviewMode = true
                                        useWideViewPort = true
                                    }
                                    webViewClient = object : WebViewClient() {
                                        override fun onPageStarted(
                                            view: WebView?,
                                            url: String?,
                                            favicon: Bitmap?
                                        ) {
                                            isLoading = true
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            isLoading = false
                                        }

                                        override fun onReceivedError(
                                            view: WebView?,
                                            request: WebResourceRequest?,
                                            error: WebResourceError?
                                        ) {
                                            if (request?.isForMainFrame == true) {
                                                hasError = true
                                                isLoading = false
                                            }
                                        }

                                        override fun onReceivedSslError(
                                            view: WebView?,
                                            handler: SslErrorHandler?,
                                            error: SslError?
                                        ) {
                                            handler?.cancel()
                                            hasError = true
                                            isLoading = false
                                        }
                                    }
                                    loadUrl(previewUrl)
                                }
                            },
                            update = { webView ->
                                webViewRef = webView
                                if (webView.url != previewUrl) {
                                    isLoading = true
                                    hasError = false
                                    webView.loadUrl(previewUrl)
                                }
                            }
                        )
                        if (isLoading) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }

        Text(
            text = stringResource(R.string.preview_help_text),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(
            onClick = {
                onDismissExternalFeedback()
                onOpenInBrowser()
            },
            enabled = !isOpeningExternal,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (isOpeningExternal) {
                    stringResource(R.string.preview_opening_browser)
                } else {
                    stringResource(R.string.preview_open_in_browser)
                }
            )
        }

        val resolvedErrorMessage = when (externalError) {
            PreviewExternalError.NotReady -> stringResource(R.string.preview_not_ready)
            PreviewExternalError.Expired -> stringResource(R.string.preview_external_expired)
            PreviewExternalError.Unavailable -> stringResource(R.string.preview_external_unavailable)
            PreviewExternalError.NoBrowser -> stringResource(R.string.preview_external_no_browser)
            PreviewExternalError.Unknown -> externalErrorMessage
                ?: stringResource(R.string.preview_external_unknown)

            null -> null
        }

        if (resolvedErrorMessage != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = resolvedErrorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    TextButton(
                        onClick = onDismissExternalFeedback,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(text = stringResource(R.string.dismiss))
                    }
                }
            }
        }
    }
}

private fun isSupportedPreviewUrl(previewUrl: String): Boolean {
    if (previewUrl.isBlank()) return false
    return runCatching {
        val parsed = Uri.parse(previewUrl)
        val scheme = parsed.scheme?.lowercase()
        (scheme == "https" || scheme == "http") && !parsed.host.isNullOrBlank()
    }.getOrDefault(false)
}
