package com.utub.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * 오프라인 저장 실행 서비스 (3차) — Foreground(dataSync) + 알림 진행률 + 취소.
 * 큐 소진 시 자기 종료. 실제 다운로드 로직은 DownloadManager가 담당.
 */
@AndroidEntryPoint
class DownloadService : Service() {

    @Inject lateinit var manager: DownloadManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var worker: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL_ALL -> {
                manager.clearQueue()
                worker?.cancel()
                return START_NOT_STICKY
            }
            else -> startWorkerIfIdle()
        }
        return START_NOT_STICKY
    }

    private fun startWorkerIfIdle() {
        if (worker?.isActive == true) return
        ensureChannel()
        startAsForeground(buildNotification("다운로드 준비 중…", 0, 0, indeterminate = true))

        worker = scope.launch {
            val dir = downloadsDir(this@DownloadService)
            try {
                manager.cleanUp(dir) // .part 잔재 + 유령 DB 기록 정리
                // 진행률 → 알림 반영 (수집은 worker와 별도 코루틴, worker 종료 시 함께 취소)
                val notifier = launch {
                    manager.progress.collect { p ->
                        if (p != null) {
                            val percent = if (p.totalBytes > 0) (p.bytesRead * 100 / p.totalBytes).toInt() else 0
                            notify(
                                buildNotification(
                                    title = p.request.title,
                                    percent = percent,
                                    max = 100,
                                    indeterminate = p.totalBytes <= 0,
                                ),
                            )
                        }
                    }
                }
                while (manager.processNext(dir)) { /* 큐 소진까지 순차 처리 */ }
                notifier.cancel()
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun startAsForeground(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(
        title: String,
        percent: Int,
        max: Int,
        indeterminate: Boolean,
    ): android.app.Notification {
        val cancelIntent = PendingIntent.getService(
            this, 1,
            Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL_ALL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("오프라인 저장")
            .setContentText(title)
            .setProgress(max, percent, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "취소", cancelIntent)
            .build()
    }

    private fun notify(notification: android.app.Notification) {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "다운로드", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "downloads_channel"
        private const val NOTIFICATION_ID = 2001
        const val ACTION_CANCEL_ALL = "com.utub.download.CANCEL_ALL"

        fun downloadsDir(context: android.content.Context): File =
            context.getExternalFilesDir("downloads") ?: File(context.filesDir, "downloads")

        /** 큐에 넣은 뒤 서비스 기동 (ViewModel에서 호출) */
        fun start(context: android.content.Context) {
            context.startForegroundService(Intent(context, DownloadService::class.java))
        }

        fun cancelAll(context: android.content.Context) {
            context.startService(
                Intent(context, DownloadService::class.java).setAction(ACTION_CANCEL_ALL),
            )
        }
    }
}
