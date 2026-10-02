package com.vynyl.record.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.max

/**
 * Mono 44.1 kHz microphone capture. `level` is the block peak (0..1) like the web analyser loop;
 * `seconds` is elapsed recording time. Caller must hold RECORD_AUDIO.
 */
class Recorder {
    private val _level = MutableStateFlow(0f)
    private val _seconds = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()
    val seconds: StateFlow<Float> = _seconds.asStateFlow()

    private val lock = Any()
    private var data = FloatArray(SR * 10)
    private var count = 0
    @Volatile private var running = false
    private var record: AudioRecord? = null
    private var thread: Thread? = null
    private val main = Handler(Looper.getMainLooper())

    @SuppressLint("MissingPermission")
    fun start(maxSeconds: Int, onLimit: () -> Unit) {
        if (running) return
        val minBuf = AudioRecord.getMinBufferSize(SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufSize = max(minBuf, 4096) * 2
        val rec = AudioRecord(MediaRecorder.AudioSource.MIC, SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize)
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); throw IllegalStateException("Microphone unavailable") }
        synchronized(lock) { data = FloatArray(SR * 10); count = 0 }
        _level.value = 0f; _seconds.value = 0f
        record = rec
        running = true
        rec.startRecording()
        val limit = maxSeconds.toLong() * SR
        thread = Thread({
            val chunk = ShortArray(1024)
            var limitHit = false
            while (running) {
                val got = rec.read(chunk, 0, chunk.size)
                if (got <= 0) { if (got < 0) break else continue }
                var pk = 0f
                synchronized(lock) {
                    val take = minOf(got.toLong(), limit - count).toInt()
                    if (count + take > data.size) data = data.copyOf(max(data.size * 2, count + take))
                    for (i in 0 until take) {
                        val v = chunk[i] / 32768f
                        data[count++] = v
                        pk = max(pk, abs(v))
                    }
                    _seconds.value = count.toFloat() / SR
                    if (count >= limit) limitHit = true
                }
                _level.value = pk
                if (limitHit) {
                    running = false
                    main.post { onLimit() }
                }
            }
        }, "vynyl-recorder").also { it.start() }
    }

    /** Stops capture and returns the recorded mono samples at SR. */
    fun stop(): FloatArray {
        halt()
        val out = synchronized(lock) { data.copyOf(count) }
        _level.value = 0f
        return out
    }

    /** Stops capture and discards the take. */
    fun cancel() {
        halt()
        synchronized(lock) { count = 0 }
        _level.value = 0f; _seconds.value = 0f
    }

    private fun halt() {
        running = false
        val t = thread
        if (t != null && t !== Thread.currentThread()) try { t.join(500) } catch (_: InterruptedException) {}
        thread = null
        record?.let { r ->
            try { r.stop() } catch (_: Exception) {}
            r.release()
        }
        record = null
    }
}
