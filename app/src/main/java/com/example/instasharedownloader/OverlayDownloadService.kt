package com.example.instasharedownloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/** Keeps active reads/saves running; the standard Android share activity owns the visible sheet. */
class OverlayDownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: Job? = null
    private var latestStartId = 0
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "다운로드", NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        startForeground(NOTIFICATION, notification(DownloadSession.state.value))
        if (observer == null) observer = scope.launch {
            DownloadSession.state.collectLatest { state ->
                if (state.busy) startForeground(NOTIFICATION, notification(state))
                else {
                    delay(500)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelfResult(latestStartId)
                }
            }
        }
        return START_NOT_STICKY
    }
    private fun notification(state: DownloadState): Notification {
        val launch = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(if (state.phase == DownloadPhase.SAVING) "사진·동영상 저장 중" else "항목 불러오는 중")
            .setContentText(if (state.phase == DownloadPhase.SAVING)
                "${state.savedCount}/${state.totalCount}개 저장" else state.sourceName)
            .setContentIntent(launch).setOnlyAlertOnce(true).setOngoing(true).build()
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    companion object { private const val CHANNEL = "instagram_overlay_download"; private const val NOTIFICATION = 2301 }
}
