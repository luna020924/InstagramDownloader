package com.example.instasharedownloader

import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/** Existing public-post fallback, isolated from the native selection and save UI. */
@Composable
internal fun InstagramRenderedFallback(state: DownloadState) {
    val context = LocalContext.current
    val webView = remember(state.requestId) { WebView(context) }
    DisposableEffect(webView) {
        val handler = Handler(Looper.getMainLooper())
        var finished = false
        var probing = false
        var attempts = 0
        lateinit var probe: Runnable
        probe = Runnable {
            if (!finished) {
                attempts++
                webView.evaluateJavascript(InstagramWebScript.RENDERED_MEDIA_SCRIPT) { value ->
                    if (!finished) {
                        val items = InstagramExtractor.extractFromRenderedDump(InstagramExtractor.decodeJavascriptString(value))
                        if (items.isNotEmpty() || attempts >= 10) {
                            finished = true
                            DownloadSession.acceptRendered(items, state.requestId)
                        } else handler.postDelayed(probe, 1500)
                    }
                }
            }
        }
        webView.apply {
            isEnabled = false
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (!probing) { probing = true; handler.postDelayed(probe, 1200) }
                }
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val host = request.url.host.orEmpty()
                    return request.url.scheme != "https" || !(host == "instagram.com" || host.endsWith(".instagram.com"))
                }
            }
            loadUrl(state.sourceUrl)
        }
        onDispose { finished = true; handler.removeCallbacksAndMessages(null); webView.stopLoading(); webView.destroy() }
    }
    AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize().alpha(0f))
}
