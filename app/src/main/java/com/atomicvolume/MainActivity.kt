package com.atomicvolume

import android.app.Activity
import android.media.audiofx.LoudnessEnhancer
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private var enhancer: LoudnessEnhancer? = null
    private lateinit var amount: TextView
    private lateinit var status: TextView

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
        root.addView(label("AMPLIFICADOR DE ÁUDIO", 13f, Color.LTGRAY))
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
        root.addView(label("A amplificação global depende do suporte do Android e do fabricante. Volume alto pode causar distorção e danos auditivos.", 12f, Color.GRAY))
        setContentView(root)
        setBoost(100)
    }

    private fun setBoost(percent: Int) {
        amount.text = "$percent%"
        try {
            if (enhancer == null) enhancer = LoudnessEnhancer(0)
            val gainMb = (2000.0 * kotlin.math.log10(percent / 100.0)).toInt().coerceIn(0, 602)
            enhancer?.setTargetGain(gainMb)
            enhancer?.enabled = percent > 100
            status.text = if (percent == 100) "Amplificação desligada" else "Amplificação solicitada: " + (gainMb / 1000.0).toString() + " dB"
        } catch (e: Exception) {
            status.text = "Efeito global não suportado neste aparelho"
        }
    }

    override fun onDestroy() {
        try { enhancer?.release() } catch (_: Exception) {}
        enhancer = null
        super.onDestroy()
    }
}
