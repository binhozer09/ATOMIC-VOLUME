package com.atomicvolume

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

class MainActivity : Activity() {
    private lateinit var amount: TextView
    private lateinit var status: TextView
    private lateinit var track: TextView
    private lateinit var playButton: Button
    private var player: ExoPlayer? = null
    private var enhancer: LoudnessEnhancer? = null
    private var percent = 100
    private var audioSessionId = 0

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) loadAudio(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(13, 13, 16)
        window.navigationBarColor = Color.rgb(13, 13, 16)
        percent = getSharedPreferences("atomic_volume", MODE_PRIVATE).getInt("percent", 100).coerceIn(100, 200)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(22, 28, 22, 24)
            setBackgroundColor(Color.rgb(13, 13, 16))
        }
        fun label(value: String, size: Float, color: Int, bold: Boolean = false) =
            TextView(this).apply {
                text = value
                textSize = size
                setTextColor(color)
                gravity = Gravity.CENTER
                if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(4, 9, 4, 9)
            }

        root.addView(label("ATÔMIC VOLUME", 28f, Color.rgb(255, 157, 35), true))
        root.addView(label("AMPLIFICADOR DE MÍDIA", 12f, Color.LTGRAY))
        track = label("Nenhum áudio selecionado", 14f, Color.WHITE)
        root.addView(track)
        root.addView(Button(this).apply {
            text = "ABRIR ARQUIVO DE ÁUDIO"
            setOnClickListener { picker.launch(arrayOf("audio/*")) }
        }, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        amount = label("$percent%", 52f, Color.WHITE, true)
        root.addView(amount)
        root.addView(label("Ganho durante a reprodução", 14f, Color.LTGRAY))
        listOf(120, 150, 175, 190, 200).chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER }
            pair.forEach { level ->
                row.addView(Button(this).apply {
                    text = if (level == 200) "MAX 200%" else "$level%"
                    setOnClickListener { setBoost(level) }
                }, LinearLayout.LayoutParams(0, 60, 1f).apply { setMargins(4, 4, 4, 4) })
            }
            root.addView(row)
        }
        playButton = Button(this).apply {
            text = "REPRODUZIR"
            isEnabled = false
            setOnClickListener {
                val p = player ?: return@setOnClickListener
                if (p.isPlaying) p.pause() else p.play()
                updatePlayButton()
            }
        }
        root.addView(playButton, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        root.addView(Button(this).apply {
            text = "VOLUME NORMAL (100%)"
            setOnClickListener { setBoost(100) }
        }, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        status = label("Selecione um arquivo para começar.", 13f, Color.LTGRAY)
        root.addView(status)
        root.addView(label("O áudio é amplificado dentro deste reprodutor. Para evitar notificação permanente, a reprodução para quando o aplicativo é fechado.", 11f, Color.GRAY))
        setContentView(root)
        setBoost(percent)
    }

    private fun loadAudio(uri: Uri) {
        try {
            val p = player ?: ExoPlayer.Builder(this).build().also { exo ->
                exo.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true
                )
                exo.addListener(object : Player.Listener {
                    override fun onAudioSessionIdChanged(audioSessionId: Int) {
                        this@MainActivity.audioSessionId = audioSessionId
                        attachEnhancer(audioSessionId)
                    }
                    override fun onIsPlayingChanged(isPlaying: Boolean) = updatePlayButton()
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        status.text = "Falha ao reproduzir: ${error.errorCodeName}"
                    }
                })
                player = exo
            }
            p.setMediaItem(MediaItem.fromUri(uri))
            p.prepare()
            p.play()
            playButton.isEnabled = true
            track.text = uri.lastPathSegment ?: "Arquivo selecionado"
            status.text = "Preparando áudio e amplificador..."
            updatePlayButton()
            if (p.audioSessionId != 0) {
                audioSessionId = p.audioSessionId
                attachEnhancer(audioSessionId)
            }
        } catch (e: Exception) {
            status.text = "Não foi possível abrir o áudio: ${e.message ?: "erro desconhecido"}"
        }
    }

    private fun setBoost(value: Int) {
        percent = value.coerceIn(100, 200)
        getSharedPreferences("atomic_volume", MODE_PRIVATE).edit().putInt("percent", percent).apply()
        amount.text = "$percent%"
        if (percent == 100) {
            try { enhancer?.enabled = false } catch (_: Exception) {}
            status.text = "Ganho extra desligado (100%)."
        } else if (audioSessionId != 0) {
            attachEnhancer(audioSessionId)
        } else {
            status.text = "Nível selecionado. Abra e reproduza um áudio para aplicar o ganho."
        }
    }

    private fun attachEnhancer(sessionId: Int) {
        if (sessionId == 0) {
            status.text = "O reprodutor ainda não forneceu uma sessão de áudio."
            return
        }
        try {
            if (enhancer == null || audioSessionId != sessionId) {
                try { enhancer?.release() } catch (_: Exception) {}
                enhancer = LoudnessEnhancer(sessionId)
            }
            val gainMb = (2000.0 * kotlin.math.log10(percent / 100.0)).toInt().coerceIn(0, 602)
            enhancer?.setTargetGain(gainMb)
            enhancer?.enabled = percent > 100
            status.text = if (percent == 100) "Reprodução normal • 100%" else
                "Efeito aplicado à sessão de áudio • $percent% (+${gainMb} mB)"
        } catch (e: Exception) {
            try { enhancer?.release() } catch (_: Exception) {}
            enhancer = null
            status.text = "O Android recusou o efeito de áudio: ${e.message ?: "efeito indisponível"}"
        }
    }

    private fun updatePlayButton() {
        playButton.text = if (player?.isPlaying == true) "PAUSAR" else "REPRODUZIR"
    }

    override fun onDestroy() {
        try { enhancer?.enabled = false; enhancer?.release() } catch (_: Exception) {}
        enhancer = null
        player?.release()
        player = null
        super.onDestroy()
    }
}
