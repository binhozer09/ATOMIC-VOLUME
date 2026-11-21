package com.atomicvolume

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var amount: TextView
    private lateinit var status: TextView
    private var percent = 150

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(13, 13, 16)
        window.navigationBarColor = Color.rgb(13, 13, 16)
        percent = getSharedPreferences("atomic_volume", MODE_PRIVATE).getInt("percent", 150).coerceIn(100, 200)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 28, 24, 24)
            setBackgroundColor(Color.rgb(13, 13, 16))
        }
        fun label(value: String, size: Float, color: Int, bold: Boolean = false) =
            TextView(this).apply {
                text = value; textSize = size; setTextColor(color); gravity = Gravity.CENTER
                if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(4, 9, 4, 9)
            }
        root.addView(label("ATÔMIC VOLUME", 28f, Color.rgb(255, 157, 35), true))
        root.addView(label("REFORÇO AUTOMÁTICO DO PLAYER ATIVO", 12f, Color.LTGRAY))
        amount = label("$percent%", 54f, Color.WHITE, true)
        root.addView(amount)
        root.addView(label("Detecta a reprodução ativa e habilita o reforço de áudio.", 14f, Color.LTGRAY))
        root.addView(Button(this).apply {
            text = "LIBERAR ACESSO AOS PLAYERS"
            setOnClickListener { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        }, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        listOf(120, 150, 175, 190, 200).chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER }
            pair.forEach { level ->
                row.addView(Button(this).apply {
                    text = if (level == 200) "MAX 200%" else "$level%"
                    setOnClickListener { setBoost(level) }
                }, LinearLayout.LayoutParams(0, 62, 1f).apply { setMargins(5, 5, 5, 5) })
            }
            root.addView(row)
        }
        status = label("", 13f, Color.LTGRAY)
        root.addView(status)
        root.addView(Button(this).apply {
            text = "DESLIGAR REFORÇO (100%)"
            setOnClickListener { setBoost(100) }
        }, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        root.addView(label(
            "O Android permite detectar o player, mas o reforço usa o efeito de saída global do aparelho. O resultado depende do suporte do sistema e do fabricante.",
            12f, Color.GRAY
        ))
        setContentView(root)
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) refreshStatus()
    }

    private fun refreshStatus() {
        val component = ComponentName(this, MediaSessionAccessService::class.java)
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(android.app.NotificationManager::class.java).isNotificationListenerAccessGranted(component)
        } else {
            Settings.Secure.getString(contentResolver, "enabled_notification_listeners")?.contains(packageName) == true
        }
        status.text = if (granted) "Acesso aos players liberado. Reforço: $percent%."
        else "Ative o acesso acima para o ATÔMIC identificar o player em reprodução."
    }

    private fun setBoost(value: Int) {
        percent = value.coerceIn(100, 200)
        getSharedPreferences("atomic_volume", MODE_PRIVATE).edit().putInt("percent", percent).apply()
        amount.text = "$percent%"
        if (percent == 100) {
            stopService(Intent(this, VolumeBoostService::class.java))
            status.text = "Reforço desligado."
            return
        }
        val intent = Intent(this, VolumeBoostService::class.java).putExtra(VolumeBoostService.EXTRA_PERCENT, percent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        refreshStatus()
    }
}
