package com.atomicvolume

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var amount: TextView
    private lateinit var status: TextView
    private val prefs by lazy { getSharedPreferences("atomic_volume", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(13, 13, 16)
        window.navigationBarColor = Color.rgb(13, 13, 16)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 30, 24, 24)
            setBackgroundColor(Color.rgb(13, 13, 16))
        }
        fun label(value: String, size: Float, color: Int, bold: Boolean = false) =
            TextView(this).apply {
                text = value; textSize = size; setTextColor(color); gravity = Gravity.CENTER
                if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(4, 10, 4, 10)
            }
        root.addView(label("ATÔMIC VOLUME", 28f, Color.rgb(255, 157, 35), true))
        root.addView(label("AMPLIFICADOR DE ÁUDIO • SEGUNDO PLANO", 12f, Color.LTGRAY))
        amount = label("100%", 54f, Color.WHITE, true)
        root.addView(amount)
        root.addView(label("Escolha a amplificação", 15f, Color.LTGRAY))
        listOf(120, 150, 175, 190, 200).chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER }
            pair.forEach { level ->
                val button = Button(this).apply {
                    text = if (level == 200) "MAX 200%" else "$level%"
                    setOnClickListener { setBoost(level) }
                }
                row.addView(button, LinearLayout.LayoutParams(0, 64, 1f).apply { setMargins(5, 5, 5, 5) })
            }
            root.addView(row)
        }
        status = label("Amplificador desligado", 13f, Color.LTGRAY)
        root.addView(status)
        root.addView(Button(this).apply {
            text = "VOLUME NORMAL (100%)"
            setOnClickListener { setBoost(100) }
        }, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        root.addView(label("O modo global depende do suporte do Android/fabricante. Se o sistema bloquear efeitos na saída global, o app não consegue amplificar outros apps sem suporte especial. Volume alto pode causar distorção e danos auditivos.", 12f, Color.GRAY))
        setContentView(root)
        setBoost(prefs.getInt("percent", 100))
    }

    private fun setBoost(percent: Int) {
        val value = percent.coerceIn(100, 200)
        prefs.edit().putInt("percent", value).apply()
        amount.text = "$value%"
        if (value == 100) {
            stopService(Intent(this, VolumeBoostService::class.java))
            status.text = "Amplificação desligada"
            return
        }
        val intent = Intent(this, VolumeBoostService::class.java)
            .putExtra(VolumeBoostService.EXTRA_PERCENT, value)
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) startForegroundService(intent)
            else startService(intent)
            status.text = "Serviço em segundo plano solicitado • $value%"
        } catch (_: Exception) {
            status.text = "Não foi possível iniciar o serviço em segundo plano"
        }
    }
}
