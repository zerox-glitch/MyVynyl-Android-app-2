package com.vynyl.record.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Live preview levels. [music] = music volume (0..1), [crackle] = crackle volume (0..2),
 * [character] = character strength (0..1.5), [mood] = OVERALL VOLUME (0..1) — the Studio "Overall volume" bar.
 */
data class Levels(val music: Float, val crackle: Float, val character: Float, val mood: Float) {
    val volume: Float get() = mood
}

/**
 * Port of Studio.tsx usePreview: renders stems (voice clean/heavy, music clean/heavy, crackle) once, then mixes
 * them live in an AudioTrack loop so the level bars change the sound in real time (gains glide with a 40 ms
 * time constant, like setTargetAtTime(…, 0.04)).
 */
class PreviewEngine {
    private val _playing = MutableStateFlow(false)
    private val _loading = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()
    /** True while stems are rendering for the latest play() call. */
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    @Volatile private var token = 0
    @Volatile private var mix: Mix? = null
    private var thread: Thread? = null
    private var track: AudioTrack? = null

    private class Mix(
        val v0: Stereo?, val v1: Stereo?, val m0: Stereo?, val m1: Stereo?, val cr: Stereo, val norm: Float,
    ) {
        // targets (written from UI thread) and current smoothed values (audio thread)
        @Volatile var tV0 = 0f; @Volatile var tV1 = 0f; @Volatile var tM0 = 0f; @Volatile var tM1 = 0f
        @Volatile var tC = 0f; @Volatile var tOut = 0f
        var gV0 = 0f; var gV1 = 0f; var gM0 = 0f; var gM1 = 0f; var gC = 0f; var gOut = 0f

        fun target(lv: Levels) {
            val w = max(0f, min(1.5f, lv.character)) / 1.5f
            tV0 = 1 - w; tV1 = w; tM0 = (1 - w) * lv.music; tM1 = w * lv.music
            tC = lv.crackle; tOut = norm * lv.mood
        }

        fun snap() { gV0 = tV0; gV1 = tV1; gM0 = tM0; gM1 = tM1; gC = tC; gOut = tOut }
    }

    /**
     * Renders preview stems and starts looping playback. Pass the real recording as [voice] (or null / a
     * demoVoice() array to preview only wax and music). [key] seeds the crackle like the web ('preview:' + key).
     */
    suspend fun play(voice: FloatArray?, preset: Preset, crackle: Crackle, musicId: String, levels: Levels, key: String = "") {
        stop()
        val my = token
        _loading.value = true
        try {
            val stems = withContext(Dispatchers.Default) {
                // no recording yet → preview only the wax and music, no stand-in melody
                val real = if (voice != null && !isDemoVoice(voice)) voice else null
                val clip = real?.copyOfRange(0, min(real.size, SR * 7)) ?: FloatArray(SR * 7)
                val silent = FloatArray(clip.size)
                val bed = musicBed(musicId)
                val base = RenderOpts(preset = preset, crackle = crackle, seed = "preview:$key", intro = 0.6f, tail = 0.6f, fixedGain = 1f)
                suspend fun stem(src: FloatArray, character: Float, music: Boolean, crk: Boolean) =
                    renderMaster(src, base.copy(character = character, music = if (music) bed else null, musicLevel = if (music) 1f else 0f, crackleLevel = if (crk) 1f else 0f))
                val v0 = if (real != null) stem(clip, 0f, false, false) else null
                val v1 = if (real != null) stem(clip, 1.5f, false, false) else null
                val m0 = if (bed != null) stem(silent, 0f, true, false) else null
                val m1 = if (bed != null) stem(silent, 1.5f, true, false) else null
                val cr = stem(silent, 1f, false, true)
                // normalise against the mix at its default levels so the bars move around a sensible loudness
                var peak = 1e-6f
                val len = cr.size
                var i = 0
                while (i < len) {
                    for (ch in 0 until 2) {
                        val a = if (ch == 0) Pick.L else Pick.R
                        val x = (if (v0 != null) (a(v0)[i] + a(v1!!)[i]) / 2 else 0f) +
                            (if (m0 != null) ((a(m0)[i] + a(m1!!)[i]) / 2) * levels.music else 0f) +
                            a(cr)[i] * levels.crackle
                        peak = max(peak, abs(x))
                    }
                    i += 4
                }
                Mix(v0, v1, m0, m1, cr, min(0.891f / peak, if (real != null) 8f else 2.5f))
            }
            if (my != token) return
            stems.target(levels); stems.snap()
            mix = stems
            start(stems, my)
            _playing.value = true
        } finally {
            if (my == token) _loading.value = false
        }
    }

    private enum class Pick { L, R;
        operator fun invoke(s: Stereo): FloatArray = if (this == L) s.l else s.r
    }

    /** Glide live gains to new levels (no-op when nothing is playing). */
    fun apply(levels: Levels) { mix?.target(levels) }

    fun stop() {
        token++
        mix = null
        val t = thread
        thread = null
        track?.let { tr ->
            try { tr.pause(); tr.flush() } catch (_: Exception) {}
        }
        if (t != null && t !== Thread.currentThread()) try { t.join(300) } catch (_: InterruptedException) {}
        track?.let { tr -> try { tr.stop() } catch (_: Exception) {}; tr.release() }
        track = null
        _playing.value = false
        _loading.value = false
    }

    fun release() = stop()

    private fun start(m: Mix, my: Int) {
        val minBuf = AudioTrack.getMinBufferSize(SR, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
        val tr = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(SR).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
            .setBufferSizeInBytes(max(minBuf, 4096 * 8))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = tr
        tr.play()
        val coef = (1 - exp(-1.0 / (SR * 0.04))).toFloat()
        thread = Thread({
            val frames = 1024
            val buf = FloatArray(frames * 2)
            val len = m.cr.size
            var pos = 0
            while (my == token) {
                for (f in 0 until frames) {
                    m.gV0 += (m.tV0 - m.gV0) * coef; m.gV1 += (m.tV1 - m.gV1) * coef
                    m.gM0 += (m.tM0 - m.gM0) * coef; m.gM1 += (m.tM1 - m.gM1) * coef
                    m.gC += (m.tC - m.gC) * coef; m.gOut += (m.tOut - m.gOut) * coef
                    var l = m.cr.l[pos] * m.gC
                    var r = m.cr.r[pos] * m.gC
                    m.v0?.let { l += it.l[pos] * m.gV0; r += it.r[pos] * m.gV0 }
                    m.v1?.let { l += it.l[pos] * m.gV1; r += it.r[pos] * m.gV1 }
                    m.m0?.let { l += it.l[pos] * m.gM0; r += it.r[pos] * m.gM0 }
                    m.m1?.let { l += it.l[pos] * m.gM1; r += it.r[pos] * m.gM1 }
                    buf[f * 2] = l * m.gOut
                    buf[f * 2 + 1] = r * m.gOut
                    pos++
                    if (pos >= len) pos = 0
                }
                val w = try { tr.write(buf, 0, buf.size, AudioTrack.WRITE_BLOCKING) } catch (_: Exception) { -1 }
                if (w < 0) break
            }
        }, "vynyl-preview").also { it.start() }
    }
}
