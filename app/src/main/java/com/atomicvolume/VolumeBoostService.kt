package com.atomicvolume

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.os.IBinder

class VolumeBoostService : Service() {
    private var enhancer: LoudnessEnhancer? = null
    private var currentPercent = 150
    private var sessionManager: MediaSessionManager? = null
    private var sessionsListener: MediaSessionManager.OnActiveSessionsChangedListener? = null
    private var receiverRegistered = false

    private val refreshReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { connectToSessions() }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val filter = android.content.IntentFilter(MediaSessionAccessService.ACTION_REFRESH)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(refreshReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else {
            @Suppress("DEPRECATION")
            registerReceiver(refreshReceiver, filter)
        }
        receiverRegistered = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        currentPercent = (intent?.getIntExtra(EXTRA_PERCENT, currentPercent) ?: currentPercent).coerceIn(100, 200)
        if (currentPercent == 100) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        connectToSessions()
        return START_STICKY
    }

    private fun connectToSessions() {
        try {
            val manager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            sessionManager = manager
            val listenerComponent = ComponentName(this, MediaSessionAccessService::class.java)
            if (sessionsListener == null) {
                val listener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
                    updateForActivePlayback(controllers.orEmpty())
                }
                sessionsListener = listener
                manager.addOnActiveSessionsChangedListener(listener, listenerComponent)
            }
            updateForActivePlayback(manager.getActiveSessions(listenerComponent))
        } catch (_: SecurityException) {
            disableEffect()
        } catch (_: Exception) {
            disableEffect()
        }
    }

    private fun updateForActivePlayback(controllers: List<android.media.session.MediaController>) {
        val playing = controllers.any { controller ->
            val state = controller.playbackState?.state
            state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
        }
        if (playing) applyBoost() else disableEffect()
    }

    private fun applyBoost() {
        try {
            if (enhancer == null) enhancer = LoudnessEnhancer(0)
            val gainMb = (2000.0 * kotlin.math.log10(currentPercent / 100.0)).toInt().coerceIn(0, 602)
            enhancer?.setTargetGain(gainMb)
            enhancer?.enabled = true
        } catch (_: Exception) { disableEffect() }
    }

    private fun disableEffect() {
        try { enhancer?.enabled = false } catch (_: Exception) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "ATÔMIC VOLUME", NotificationManager.IMPORTANCE_LOW)
            channel.description = "Monitoramento do player ativo e reforço de áudio"
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        val openApp = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flags)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, CHANNEL_ID)
        else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder.setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("ATÔMIC VOLUME ativo")
            .setContentText("Monitorando reprodução • reforço ${currentPercent}%")
            .setContentIntent(openApp).setOngoing(true).build()
    }

    override fun onDestroy() {
        try { sessionsListener?.let { sessionManager?.removeOnActiveSessionsChangedListener(it) } } catch (_: Exception) {}
        if (receiverRegistered) try { unregisterReceiver(refreshReceiver) } catch (_: Exception) {}
        disableEffect()
        try { enhancer?.release() } catch (_: Exception) {}
        enhancer = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_PERCENT = "boost_percent"
        private const val CHANNEL_ID = "atomic_volume_monitor"
        private const val NOTIFICATION_ID = 4102
    }
}
