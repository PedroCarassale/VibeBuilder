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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.vibebuilder.app.R
import com.vibebuilder.app.domain.model.ProjectVersion
import com.vibebuilder.app.domain.model.VersionStatus
import com.vibebuilder.app.ui.components.AppCard
import com.vibebuilder.app.ui.util.QrCodeEncoder

@Composable
fun PreviewTab(
    currentVersion: ProjectVersion?,
    previewResolution: PreviewResolutionUiState,
    isOpeningExternal: Boolean,
    externalError: PreviewExternalError?,
    externalErrorMessage: String?,
    onOpenInBrowser: () -> Unit,
    onDismissExternalFeedback: () -> Unit,
    onRetryResolvePreview: () -> Unit
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

    val localPreviewUrl = currentVersion.previewUrl?.trim().orEmpty()
    val hasLocalPreviewUrl = isSupportedPreviewUrl(localPreviewUrl)
    val resolvedPreviewUrl = previewResolution.url?.trim().orEmpty()
    val displayPreviewUrl = when {
        hasLocalPreviewUrl -> localPreviewUrl
        isSupportedPreviewUrl(resolvedPreviewUrl) -> resolvedPreviewUrl
        else -> ""
    }
    val canRenderWebView =
        currentVersion.status == VersionStatus.READY && hasLocalPreviewUrl
    val canShowQr =
        currentVersion.status == VersionStatus.READY && isSupportedPreviewUrl(displayPreviewUrl)

    var isLoading by remember(localPreviewUrl) { mutableStateOf(canRenderWebView) }
    var hasError by remember(localPreviewUrl) { mutableStateOf(false) }
    var webViewRef by remember(localPreviewUrl) { mutableStateOf<WebView?>(null) }

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

        AppCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(12.dp)
        ) {
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

        AppCard(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .fillMaxHeight(),
            shape = MaterialTheme.shapes.medium,
            contentPadding = PaddingValues(0.dp),
            expandInnerHeight = true
        ) {
            when {
                previewResolution.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                canRenderWebView && hasError -> {
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
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

                canRenderWebView -> {
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
                                    loadUrl(localPreviewUrl)
                                }
                            },
                            update = { webView ->
                                webViewRef = webView
                                if (webView.url != localPreviewUrl) {
                                    isLoading = true
                                    hasError = false
                                    webView.loadUrl(localPreviewUrl)
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

                canShowQr -> {
                    PreviewQrContent(
                        previewUrl = displayPreviewUrl,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    )
                }

                else -> {
                    val message = when {
                        currentVersion.status != VersionStatus.READY ->
                            stringResource(R.string.preview_not_ready)

                        previewResolution.error != null ->
                            previewResolutionErrorMessage(
                                error = previewResolution.error,
                                errorMessage = previewResolution.errorMessage
                            )

                        else -> stringResource(R.string.preview_url_unavailable)
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        if (previewResolution.error != null) {
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = onRetryResolvePreview) {
                                Text(text = stringResource(R.string.retry))
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
            AppCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                contentPadding = PaddingValues(12.dp)
            ) {
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

@Composable
private fun PreviewQrContent(
    previewUrl: String,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val density = LocalDensity.current
        val titleSpacing = 16.dp
        val qrSize = minOf(maxWidth, maxHeight - 40.dp - titleSpacing).coerceAtLeast(160.dp)
        val qrSizePx = with(density) { qrSize.roundToPx() }
        val qrBitmap = remember(previewUrl, qrSizePx) {
            QrCodeEncoder.encode(previewUrl, qrSizePx)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.preview_qr_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(titleSpacing))
            qrBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.preview_qr_content_description),
                    modifier = Modifier.size(qrSize)
                )
            }
        }
    }
}

@Composable
private fun previewResolutionErrorMessage(
    error: PreviewExternalError,
    errorMessage: String?
): String = when (error) {
    PreviewExternalError.NotReady -> stringResource(R.string.preview_not_ready)
    PreviewExternalError.Expired -> stringResource(R.string.preview_external_expired)
    PreviewExternalError.Unavailable -> stringResource(R.string.preview_external_unavailable)
    PreviewExternalError.NoBrowser -> stringResource(R.string.preview_external_no_browser)
    PreviewExternalError.Unknown -> errorMessage ?: stringResource(R.string.preview_external_unknown)
}

private fun isSupportedPreviewUrl(previewUrl: String): Boolean {
    if (previewUrl.isBlank()) return false
    return runCatching {
        val parsed = Uri.parse(previewUrl)
        val scheme = parsed.scheme?.lowercase()
        (scheme == "https" || scheme == "http") && !parsed.host.isNullOrBlank()
    }.getOrDefault(false)
}
