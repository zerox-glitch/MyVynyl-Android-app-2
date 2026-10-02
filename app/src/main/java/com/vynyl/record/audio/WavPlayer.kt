package com.vynyl.record.audio

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** Plays a mastered WAV file. Create/use on the main thread. [position] is in seconds, updated ~30x/s while playing. */
class WavPlayer(file: File) {
    private val mp = MediaPlayer()
    private val main = Handler(Looper.getMainLooper())
    private val _position = MutableStateFlow(0f)
    private val _isPlaying = MutableStateFlow(false)
    val position: StateFlow<Float> = _position.asStateFlow()
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    val duration: Float
    private var released = false

    init {
        mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        mp.setDataSource(file.absolutePath)
        mp.prepare()
        // exact duration from the WAV header (16-bit stereo, SR) when possible; fall back to the player
        val bytes = file.length() - 44
        duration = if (bytes > 0) bytes / 4f / SR else mp.duration / 1000f
        mp.setOnCompletionListener {
            _isPlaying.value = false
            _position.value = duration
            main.removeCallbacks(tick)
        }
    }

    private val tick: Runnable = object : Runnable {
        override fun run() {
            if (released) return
            _position.value = mp.currentPosition / 1000f
            if (_isPlaying.value) main.postDelayed(this, 33)
        }
    }

    fun play() {
        if (released || _isPlaying.value) return
        if (_position.value >= duration - 0.05f) { mp.seekTo(0); _position.value = 0f }
        mp.start()
        _isPlaying.value = true
        main.removeCallbacks(tick)
        main.post(tick)
    }

    fun pause() {
        if (released) return
        if (mp.isPlaying) mp.pause()
        _isPlaying.value = false
        main.removeCallbacks(tick)
        _position.value = mp.currentPosition / 1000f
    }

    fun seek(sec: Float) {
        if (released) return
        val s = sec.coerceIn(0f, duration)
        mp.seekTo((s * 1000).toLong(), MediaPlayer.SEEK_CLOSEST)
        _position.value = s
    }

    fun release() {
        if (released) return
        released = true
        main.removeCallbacks(tick)
        _isPlaying.value = false
        mp.release()
    }
}
