package com.example.instasharedownloader

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle

abstract class DownloadActivity : ComponentActivity() {
    private var pendingStorageAction: (() -> Unit)? = null
    private val storagePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pendingStorageAction
        pendingStorageAction = null
        if (granted) action?.invoke()
        else Toast.makeText(this, "사진을 저장하려면 저장공간 권한이 필요해요.", Toast.LENGTH_LONG).show()
    }

    internal fun withStoragePermission(action: () -> Unit) {
        if (Build.VERSION.SDK_INT <= 28 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingStorageAction = action
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else action()
    }

    internal fun login() { startActivity(Intent(this, LoginActivity::class.java)) }
    internal fun openService(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
        catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(this, "링크를 열 수 있는 앱이 없어요.", Toast.LENGTH_LONG).show()
        }
    }
    internal fun openSaved() {
        val state = DownloadSession.state.value
        val uri = state.lastUri ?: return
        try {
            startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, state.lastMime ?: "image/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(this, "갤러리에서 InstaShareDownloader 앨범을 열어 주세요.", Toast.LENGTH_LONG).show()
        }
    }

    @Composable
    internal fun DownloadContent(shared: Boolean, onClose: () -> Unit) {
        val state by DownloadSession.state.collectAsStateWithLifecycle()
        if (shared) SharePanel(state, onClose, DownloadSession::toggle, DownloadSession::selectAll,
            { withStoragePermission(DownloadSession::save) }, DownloadSession::chooseSharedStory,
            DownloadSession::chooseAllStories, { withStoragePermission { DownloadSession.retry(this) } },
            ::login, ::openSaved)
        else WorkScreen(state, onClose, DownloadSession::toggle, DownloadSession::selectAll,
            { withStoragePermission(DownloadSession::save) }, DownloadSession::chooseSharedStory,
            DownloadSession::chooseAllStories, { withStoragePermission { DownloadSession.retry(this) } },
            ::login, ::openSaved)
        if (state.phase == DownloadPhase.RENDERING) InstagramRenderedFallback(state)
    }
}

class MainActivity : DownloadActivity() {
    private var connected by mutableStateOf(false)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DownloaderTheme {
                val state by DownloadSession.state.collectAsStateWithLifecycle()
                val input = rememberTextFieldState()
                BackHandler(state.phase != DownloadPhase.IDLE) {
                    if (state.busy) moveTaskToBack(true) else DownloadSession.reset()
                }
                if (state.phase == DownloadPhase.IDLE) HomeScreen(input, connected,
                    clipboardText = {
                        val clipboard = getSystemService(ClipboardManager::class.java)
                        clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
                    },
                    onResolve = { raw -> withStoragePermission { DownloadSession.resolve(this, raw) } },
                    onLogin = ::login, onOpenService = ::openService)
                else DownloadContent(shared = false) {
                    if (state.busy) moveTaskToBack(true) else DownloadSession.reset()
                }
            }
        }
    }
    override fun onResume() { super.onResume(); connected = DownloadSession.hasInstagramSession() }
}
