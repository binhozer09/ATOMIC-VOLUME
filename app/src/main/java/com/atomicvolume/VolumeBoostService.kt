package com.atomicvolume

import android.app.Service
import android.content.Intent
import android.media.audiofx.LoudnessEnhancer
import android.os.IBinder

class VolumeBoostService : Service() {
    private var enhancer: LoudnessEnhancer? = null
    private var currentPercent = 100

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val requested = intent?.getIntExtra(EXTRA_PERCENT, currentPercent) ?: currentPercent
        currentPercent = requested.coerceIn(100, 200)
        if (currentPercent == 100) {
            stopSelf()
            return START_NOT_STICKY
        }
        applyBoost(currentPercent)
        return START_STICKY
    }

    private fun applyBoost(percent: Int) {
        try {
            if (enhancer == null) enhancer = LoudnessEnhancer(0)
            val gainMb = (2000.0 * kotlin.math.log10(percent / 100.0)).toInt().coerceIn(0, 602)
            enhancer?.setTargetGain(gainMb)
            enhancer?.enabled = percent > 100
        } catch (_: Exception) {
            // Global output effects may be blocked or ignored by Android/OEM.
        }
    }

    override fun onDestroy() {
        try { enhancer?.enabled = false; enhancer?.release() } catch (_: Exception) {}
        enhancer = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_PERCENT = "boost_percent"
    }
}