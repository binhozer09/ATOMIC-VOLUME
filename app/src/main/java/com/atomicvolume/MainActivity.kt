package com.atomicvolume

import android.app.Activity
import android.graphics.Color
import android.media.audiofx.LoudnessEnhancer
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var amount: TextView
    private lateinit var status: TextView
    private var enhancer: LoudnessEnhancer? = null
    private var percent = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(13, 13, 16)
        window.navigationBarColor = Color.rgb(13, 13, 16)
        percent = getSharedPreferences("atomic_volume", MODE_PRIVATE).getInt("percent", 100).coerceIn(100, 200)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 30, 24, 24)
            setBackgroundColor(Color.rgb(13, 13, 16))
        }
        fun label(value: String, size: Float, color: Int, bold: Boolean = false) =
            TextView(this).apply {
                text = value
                textSize = size
                setTextColor(color)
                gravity = Gravity.CENTER
                if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(4, 10, 4, 10)
            }

        root.addView(label("ATÔMIC VOLUME", 28f, Color.rgb(255, 157, 35), true))
        root.addView(label("AMPLIFICADOR GLOBAL DE ÁUDIO", 12f, Color.LTGRAY))
        amount = label("$percent%", 54f, Color.WHITE, true)
        root.addView(amount)
        root.addView(label("Aumenta a saída de áudio do aparelho", 15f, Color.LTGRAY))
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
        status = label("Verificando suporte do Android...", 13f, Color.LTGRAY)
        root.addView(status)
        root.addView(Button(this).apply {
            text = "VOLUME NORMAL (100%)"
            setOnClickListener { setBoost(100) }
        }, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        root.addView(label("Compatibilidade global depende do sistema e do fabricante. Sem root e sem notificação permanente; o Android pode encerrar o efeito em segundo plano.", 12f, Color.GRAY))
        setContentView(root)
        setBoost(percent)
    }

    private fun setBoost(value: Int) {
        percent = value.coerceIn(100, 200)
        getSharedPreferences("atomic_volume", MODE_PRIVATE).edit().putInt("percent", percent).apply()
        amount.text = "$percent%"
        try {
            if (percent == 100) {
                enhancer?.enabled = false
                status.text = "Ganho extra desligado."
                return
            }
            if (enhancer == null) enhancer = LoudnessEnhancer(0)
            val gainMb = (2000.0 * kotlin.math.log10(percent / 100.0)).toInt().coerceIn(0, 602)
            enhancer?.setTargetGain(gainMb)
            enhancer?.enabled = true
            val active = enhancer?.enabled == true
            status.text = if (active) {
                "Efeito global habilitado pelo Android • +${gainMb} mB"
            } else {
                "O Android não confirmou a ativação do efeito global."
            }
        } catch (e: Exception) {
            try { enhancer?.release() } catch (_: Exception) {}
            enhancer = null
            status.text = "Efeito global não disponível neste aparelho: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    override fun onDestroy() {
        try { enhancer?.enabled = false; enhancer?.release() } catch (_: Exception) {}
        enhancer = null
        super.onDestroy()
    }
}
