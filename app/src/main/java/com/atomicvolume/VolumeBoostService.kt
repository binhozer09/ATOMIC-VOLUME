package com.atomicvolume

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.os.IBinder

class VolumeBoostService : Service() {
    private var enhancer: LoudnessEnhancer? = null
    private var currentPercent = 100

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val percent = intent?.getIntExtra(EXTRA_PERCENT, -1) ?: -1
        if (percent >= 100) currentPercent = percent.coerceIn(100, 200)
        startForeground(NOTIFICATION_ID, buildNotification())
        applyBoost(currentPercent)
        if (currentPercent == 100) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_STICKY
    }

    private fun applyBoost(percent: Int) {
        try {
            if (enhancer == null) enhancer = LoudnessEnhancer(0)
            val gainMb = (2000.0 * kotlin.math.log10(percent / 100.0)).toInt().coerceIn(0, 602)
            enhancer?.setTargetGain(gainMb)
            enhancer?.enabled = percent > 100
        } catch (_: Exception) {
            // Some Android builds prohibit or ignore global output-mix effects.
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "ATÔMIC VOLUME", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        return builder.setContentTitle("ATÔMIC VOLUME ativo")
            .setContentText("Amplificação solicitada: ${currentPercent}%")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        try { enhancer?.enabled = false; enhancer?.release() } catch (_: Exception) {}
        enhancer = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_PERCENT = "boost_percent"
        private const val CHANNEL_ID = "atomic_volume_channel"
        private const val NOTIFICATION_ID = 1201
    }
}
