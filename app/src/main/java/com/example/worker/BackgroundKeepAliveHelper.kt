package com.example.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import com.example.MainActivity
import com.example.R
import java.util.UUID

object BackgroundKeepAliveHelper {

    const val CHANNEL_ID = "openbullet_runner_channel"
    const val RUNNER_NOTIFICATION_ID = 1001
    const val PROXY_CHECK_NOTIFICATION_ID = 1002
    private const val WAKE_LOCK_TAG = "com.example:OpenBulletRunnerWakeLock"
    private const val WIFI_LOCK_TAG = "com.example:OpenBulletRunnerWifiLock"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notif_channel_runner_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = context.getString(R.string.notif_channel_runner_desc)
                    setShowBadge(false)
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    fun buildForegroundInfo(
        context: Context,
        workId: UUID,
        notificationId: Int,
        title: String,
        contentText: String,
        progressCurrent: Int,
        progressTotal: Int
    ): ForegroundInfo {
        ensureChannel(context)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = WorkManager.getInstance(context).createCancelPendingIntent(workId)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.notif_action_stop),
                cancelIntent
            )

        if (progressTotal > 0) {
            builder.setProgress(progressTotal, progressCurrent.coerceIn(0, progressTotal), false)
        } else {
            builder.setProgress(0, 0, true)
        }

        val notification = builder.build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    suspend fun <T> withKeepAwakeLocks(
        context: Context,
        enabled: Boolean,
        block: suspend () -> T
    ): T {
        if (!enabled) return block()

        val pm = context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = runCatching {
            pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)?.apply {
                setReferenceCounted(false)
                acquire(6L * 60L * 60L * 1000L) // Safety 6-hour max timeout
            }
        }.getOrNull()

        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        @Suppress("DEPRECATION")
        val wifiLock = runCatching {
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            wm?.createWifiLock(mode, WIFI_LOCK_TAG)?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }.getOrNull()

        return try {
            block()
        } finally {
            runCatching {
                if (wakeLock?.isHeld == true) wakeLock.release()
            }
            runCatching {
                if (wifiLock?.isHeld == true) wifiLock.release()
            }
        }
    }
}
