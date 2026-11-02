@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.instasharedownloader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

class LoginActivity : ComponentActivity() {
    private var webView: WebView? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        CookieManager.getInstance().setAcceptCookie(true)
        setContent {
            DownloaderTheme {
                var loading by remember { mutableStateOf(true) }
                BackHandler { if (webView?.canGoBack() == true) webView?.goBack() else finishLogin() }
                Scaffold(topBar = {
                    TopAppBar(title = { Text("Instagram 로그인") },
                        navigationIcon = { IconButton({ finishLogin() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "닫기") } },
                        actions = { TextButton({ finishLogin() }) { Text("완료") } })
                }) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        Text("Instagram 공식 페이지에서 로그인해 주세요.", Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                        AndroidView(factory = { context ->
                            WebView(context).also { view ->
                                webView = view
                                view.settings.javaScriptEnabled = true
                                view.settings.domStorageEnabled = true
                                CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)
                                view.webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) { loading = true }
                                    override fun onPageFinished(view: WebView?, url: String?) { loading = false }
                                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                        val uri = request.url
                                        val host = uri.host.orEmpty()
                                        if (uri.scheme == "https" && (host == "instagram.com" || host.endsWith(".instagram.com") ||
                                                host == "facebook.com" || host.endsWith(".facebook.com"))) return false
                                        try { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                                        catch (_: android.content.ActivityNotFoundException) { }
                                        return true
                                    }
                                }
                                val restored = savedInstanceState?.getBundle("instagram-web")?.let { view.restoreState(it) }
                                if (restored == null) view.loadUrl("https://www.instagram.com/")
                            }
                        }, modifier = Modifier.fillMaxWidth().weight(1f))
                    }
                }
            }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        webView?.let { view -> outState.putBundle("instagram-web", Bundle().also { view.saveState(it) }) }
        super.onSaveInstanceState(outState)
    }
    private fun finishLogin() { CookieManager.getInstance().flush(); finish() }
    override fun onDestroy() { webView?.destroy(); webView = null; super.onDestroy() }
}
