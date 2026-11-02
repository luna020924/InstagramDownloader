package com.example.instasharedownloader

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/** A translucent, task-local Android share surface; no draw-over-other-apps permission is needed. */
class ShareActivity : DownloadActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null || DownloadSession.state.value.phase == DownloadPhase.IDLE) receive(intent)
        setContent {
            DownloaderTheme {
                DownloadContent(shared = true) {
                    DownloadSession.reset()
                    finish()
                }
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receive(intent)
    }
    private fun receive(intent: Intent) {
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: intent.dataString.orEmpty()
        withStoragePermission { DownloadSession.resolve(this, text) }
    }
}
