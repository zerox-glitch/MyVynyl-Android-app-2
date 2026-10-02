package com.vynyl.record.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES30
import android.opengl.GLUtils
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.Surface
import com.vynyl.record.audio.SR
import com.vynyl.record.audio.VinylStyle
import com.vynyl.record.audio.watermarkWav
import com.vynyl.record.audio.withNeedleDrop
import com.vynyl.record.turntable.LabelInfo
import com.vynyl.record.turntable.TexFonts
import com.vynyl.record.turntable.TurntableRenderer
import com.vynyl.record.turntable.labelBitmap
import com.vynyl.record.turntable.nameplateBitmaps
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

/** Free vs Pro video limits. Free: 720p, first 30 s, on-screen watermark + audio chime. Pro: 1080p, full length, clean. */
data class VideoTier(val size: Int, val maxSeconds: Float?, val watermark: Boolean, val videoBitrate: Int) {
    companion object {
        const val FREE_SECONDS = 30f
        val FREE = VideoTier(720, FREE_SECONDS, true, 6_000_000)
        val PRO = VideoTier(1080, null, false, 12_000_000)
        fun of(pro: Boolean) = if (pro) PRO else FREE
    }
}

class VideoRequest(
    val master: File,
    val out: File,
    val style: VinylStyle,
    val label: LabelInfo,
    val labelPhoto: Bitmap?,
    val nameplate: Pair<String, String>?,
    val title: String,
    val sender: String,
    val recipient: String,
    val tier: VideoTier,
)

private const val FPS = 30
private const val MAX_PREROLL = 8f   // safety cap on the cue → swing → lower sequence
private const val TAIL = 2.5f         // arm lifts and returns after the music ends
private const val INTRO = 3.5f        // title card: sender, receiver, title
private const val FADE = 0.7f         // crossfade between cards and the deck
private const val CUE_AT = INTRO + 0.6f
private const val OUTRO = 3f          // free-tier watermark card
private const val AUDIO_BITRATE = 192_000
private const val TIMEOUT_US = 10_000L

/**
 * Renders the real 3D [TurntableRenderer] offscreen, one frame per 1/30 s of virtual time, into an H.264 encoder
 * surface; the record's audio is encoded as AAC and both are muxed into an MP4. Audio is silent until the needle
 * touches down, exactly like the live player. Blocking — call from a background thread. [onProgress] gets 0..1;
 * return true from [cancelled] to abort (the partial file is deleted).
 */
fun exportVideo(ctx: Context, req: VideoRequest, onProgress: (Float) -> Unit, cancelled: () -> Boolean) {
    val pcm = loadPcm(req.master, req.tier)
    val audioSec = pcm.capacity() / 4f / SR
    val w = req.tier.size; val h = req.tier.size

    val fonts = TexFonts(ctx.applicationContext)
    val renderer = TurntableRenderer(fonts, req.style, reduced = false)
    renderer.setStyle(req.style, labelBitmap(fonts, req.style, req.label, req.labelPhoto))
    renderer.setNameplate(req.nameplate?.let { nameplateBitmaps(fonts, it.first, it.second) })
    renderer.durationSec = audioSec
    renderer.bgPlayer = floatArrayOf(w.toFloat(), h.toFloat())
    renderer.bgOffset = floatArrayOf(0f, 0f)

    val vFmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
        setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        setInteger(MediaFormat.KEY_BIT_RATE, req.tier.videoBitrate)
        setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
    }
    val aFmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SR, 2).apply {
        setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BITRATE)
        setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
    }
    val venc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
    val aenc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
    venc.configure(vFmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
    aenc.configure(aFmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
    val input = venc.createInputSurface()
    venc.start(); aenc.start()

    req.out.parentFile?.mkdirs(); req.out.delete()
    val mux = Mux(MediaMuxer(req.out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4))
    val egl = EglRecorder(input)
    var ok = false
    try {
        egl.makeCurrent()
        renderer.initGl()
        renderer.resize(w, h)
        val msaa = MsaaTarget(w, h)
        renderer.targetFbo = msaa.fbo
        val intro = Overlay(introBitmap(fonts, w, h, req.title, req.sender, req.recipient))
        val outro = if (req.tier.watermark) Overlay(outroBitmap(fonts, w, h)) else null
        val outroLen = if (outro != null) OUTRO else 0f

        val audio = AudioFeed(aenc, pcm)
        val dt = 1f / FPS
        var t = 0f              // video clock
        var contactAt = -1f     // video time of needle touchdown
        var endAt = -1f         // video time the music finished
        var frame = 0L
        var cueT = -1f           // video time the deck started (camera glide begins)
        // estimate for progress until touchdown is known
        fun total() = (if (contactAt >= 0) contactAt else CUE_AT + 3f) + audioSec + TAIL + outroLen

        while (true) {
            if (cancelled()) return
            val pos = if (contactAt >= 0) (t - contactAt).coerceIn(0f, audioSec) else 0f
            renderer.positionSec = { pos }
            if (contactAt < 0 && !renderer.engaged && t >= CUE_AT) renderer.engaged = true
            if (contactAt < 0 && (renderer.needleDown || t >= CUE_AT + MAX_PREROLL)) contactAt = t
            if (contactAt >= 0 && endAt < 0 && t - contactAt >= audioSec) { endAt = t; renderer.engaged = false }
            if (endAt >= 0 && t - endAt >= TAIL + outroLen) break
            if (renderer.engaged && cueT < 0) cueT = t
            renderer.orbit.place(shotRadius(cueT, t), shotTheta(cueT, t), shotPhi(cueT, t))

            renderer.render(dt, (t * 1000).toLong())
            msaa.blitToWindow()
            if (contactAt >= 0) drawProgress(w, h, pos / audioSec)
            val ia = 1f - ((t - INTRO) / FADE).coerceIn(0f, 1f)
            if (ia > 0f) intro.draw(w, h, ia)
            if (outro != null && endAt >= 0) ((t - endAt - TAIL) / FADE).coerceIn(0f, 1f).let { if (it > 0f) outro.draw(w, h, it) }
            egl.swap(frame * 1_000_000_000L / FPS)
            frame++
            t = frame.toFloat() / FPS

            // audio clock: silence before touchdown, then PCM; keep ~0.5 s ahead of video
            audio.feedUntil(t + 0.5f, contactAt)
            drain(venc, mux, Track.Video, false)
            drain(aenc, mux, Track.Audio, false)
            onProgress(min(0.99f, t / total()))
        }
        while (!audio.finish()) drain(aenc, mux, Track.Audio, false)
        venc.signalEndOfInputStream()
        drain(venc, mux, Track.Video, true)
        drain(aenc, mux, Track.Audio, true)
        intro.release(); outro?.release(); msaa.release()
        ok = true
        onProgress(1f)
    } finally {
        runCatching { venc.stop() }; venc.release()
        runCatching { aenc.stop() }; aenc.release()
        egl.release(); input.release()
        mux.release(ok)
        if (!ok) req.out.delete()
    }
}

// ---------- camera shot ----------
// Hold the hero angle, glide down near record level with the whole deck in frame (3.8 s), then sway gently.
private fun shotEase(cue: Float, t: Float): Float { if (cue < 0) return 0f; val k = ((t - cue) / 3.8f).coerceIn(0f, 1f); return k * k * (3 - 2 * k) }
private fun shotRadius(cue: Float, t: Float) = 5.62f + (6.9f - 5.62f) * shotEase(cue, t)
private fun shotPhi(cue: Float, t: Float) = 0.96f + (1.2f - 0.96f) * shotEase(cue, t)
private fun shotTheta(cue: Float, t: Float) = 0.043f + if (cue < 0) 0f else kotlin.math.sin(((t - cue - 3.8f).coerceAtLeast(0f)) / 9f) * 0.28f

/** Gallery saves use scoped storage (Android 10+), so no storage permission is needed. */
val canSaveToGallery get() = Build.VERSION.SDK_INT >= 29

/** Copies a finished export into the user's Movies/Vynyl gallery folder. Returns the content URI. */
fun saveToGallery(ctx: Context, file: File, displayName: String): Uri? {
    if (!canSaveToGallery) return null
    val r = ctx.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        if (Build.VERSION.SDK_INT >= 29) {
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Vynyl")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
    }
    val uri = r.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null
    r.openOutputStream(uri)?.use { o -> file.inputStream().use { it.copyTo(o) } }
    if (Build.VERSION.SDK_INT >= 29) r.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
    return uri
}

// ---------- audio ----------

/** 16-bit stereo PCM (little endian) for the export: full master for Pro; first 30 s, faded, plus chime for free. */
private fun loadPcm(master: File, tier: VideoTier): ByteBuffer {
    var wav = withNeedleDrop(master.readBytes())
    tier.maxSeconds?.let { cap ->
        val n = (wav.size - 44) / 4
        val keep = min(n, (cap * SR).toInt())
        if (keep < n) {
            val b = ByteBuffer.wrap(wav, 0, 44 + keep * 4).order(ByteOrder.LITTLE_ENDIAN)
            val fade = min(keep, (SR * 1.5f).toInt())
            for (i in 0 until fade) {
                val idx = 44 + (keep - fade + i) * 4
                val g = 1f - i / fade.toFloat()
                b.putShort(idx, (b.getShort(idx) * g).toInt().toShort())
                b.putShort(idx + 2, (b.getShort(idx + 2) * g).toInt().toShort())
            }
            wav = wav.copyOf(44 + keep * 4)
        }
    }
    if (tier.watermark) wav = watermarkWav(wav)
    return ByteBuffer.wrap(wav, 44, wav.size - 44).slice().order(ByteOrder.LITTLE_ENDIAN)
}

/** Feeds the AAC encoder in lock-step with the video clock. */
private class AudioFeed(private val enc: MediaCodec, private val pcm: ByteBuffer) {
    private var frames = 0L       // stereo frames queued (timeline position)
    private var pcmFrames = 0L    // frames consumed from pcm
    private val pcmTotal = pcm.capacity() / 4L

    fun feedUntil(sec: Float, contactAt: Float) {
        val target = (sec * SR).toLong()
        while (frames < target) {
            val i = enc.dequeueInputBuffer(TIMEOUT_US); if (i < 0) return
            val buf = enc.getInputBuffer(i)!!; buf.clear()
            val chunk = min((buf.capacity() / 4).toLong(), min(4096L, target - frames)).toInt()
            val silentUntil = if (contactAt < 0) Long.MAX_VALUE else (contactAt * SR).toLong()
            for (k in 0 until chunk) {
                val f = frames + k
                if (f >= silentUntil && pcmFrames < pcmTotal) {
                    buf.putInt(pcm.getInt((pcmFrames * 4).toInt())); pcmFrames++
                } else buf.putInt(0)
            }
            enc.queueInputBuffer(i, 0, chunk * 4, frames * 1_000_000L / SR, 0)
            frames += chunk
        }
    }

    /** Queues end-of-stream; false if no input buffer was free yet (drain and retry). */
    fun finish(): Boolean {
        val i = enc.dequeueInputBuffer(TIMEOUT_US); if (i < 0) return false
        enc.queueInputBuffer(i, 0, 0, frames * 1_000_000L / SR, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        return true
    }
}

// ---------- muxing ----------

private enum class Track { Video, Audio }

/** Holds samples until both tracks are known, since MediaMuxer can only start once all tracks are added. */
private class Mux(private val m: MediaMuxer) {
    private val ids = HashMap<Track, Int>()
    private var started = false
    private val pending = ArrayList<Triple<Track, ByteBuffer, MediaCodec.BufferInfo>>()

    fun addTrack(t: Track, f: MediaFormat) {
        ids[t] = m.addTrack(f)
        if (ids.size == 2) {
            m.start(); started = true
            pending.forEach { (tr, b, info) -> m.writeSampleData(ids[tr]!!, b, info) }
            pending.clear()
        }
    }

    fun write(t: Track, data: ByteBuffer, info: MediaCodec.BufferInfo) {
        if (started) { m.writeSampleData(ids[t]!!, data, info); return }
        val copy = ByteBuffer.allocateDirect(info.size).apply { put(data); flip() }
        pending.add(Triple(t, copy, MediaCodec.BufferInfo().apply { set(0, info.size, info.presentationTimeUs, info.flags) }))
    }

    fun release(ok: Boolean) {
        if (started) runCatching { m.stop() }.onFailure { if (ok) throw it }
        m.release()
    }
}

private fun drain(enc: MediaCodec, mux: Mux, t: Track, eos: Boolean) {
    val info = MediaCodec.BufferInfo()
    while (true) {
        val i = enc.dequeueOutputBuffer(info, TIMEOUT_US)
        when {
            i == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!eos) return
            i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> mux.addTrack(t, enc.outputFormat)
            i >= 0 -> {
                val buf = enc.getOutputBuffer(i)!!
                if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                if (info.size > 0) {
                    buf.position(info.offset); buf.limit(info.offset + info.size)
                    mux.write(t, buf, info)
                }
                enc.releaseOutputBuffer(i, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
            }
        }
    }
}

// ---------- EGL / GL ----------

/** ES3 context bound to the encoder's input surface (EGL_RECORDABLE_ANDROID). */
private class EglRecorder(surface: Surface) {
    private val display: EGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
    private val context: EGLContext
    private val surf: EGLSurface

    init {
        val v = IntArray(2); check(EGL14.eglInitialize(display, v, 0, v, 1)) { "eglInitialize failed" }
        val attrs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR, EGLExt.EGL_RECORDABLE_ANDROID, 1, EGL14.EGL_NONE,
        )
        val cfg = arrayOfNulls<EGLConfig>(1); val n = IntArray(1)
        check(EGL14.eglChooseConfig(display, attrs, 0, cfg, 0, 1, n, 0) && n[0] > 0) { "No recordable GLES3 config" }
        context = EGL14.eglCreateContext(display, cfg[0], EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0)
        surf = EGL14.eglCreateWindowSurface(display, cfg[0], surface, intArrayOf(EGL14.EGL_NONE), 0)
    }

    fun makeCurrent() = check(EGL14.eglMakeCurrent(display, surf, surf, context)) { "eglMakeCurrent failed" }
    fun swap(ptsNs: Long) { EGLExt.eglPresentationTimeANDROID(display, surf, ptsNs); EGL14.eglSwapBuffers(display, surf) }
    fun release() {
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(display, surf); EGL14.eglDestroyContext(display, context)
        EGL14.eglReleaseThread(); EGL14.eglTerminate(display)
    }
}

/** 4× MSAA colour+depth target (encoder surfaces have no multisampling), resolved into the window by blit. */
private class MsaaTarget(private val w: Int, private val h: Int) {
    val fbo: Int
    private val rbs = IntArray(2)

    init {
        val f = IntArray(1); GLES30.glGenFramebuffers(1, f, 0); fbo = f[0]
        GLES30.glGenRenderbuffers(2, rbs, 0)
        val max = IntArray(1); GLES30.glGetIntegerv(GLES30.GL_MAX_SAMPLES, max, 0)
        val samples = min(4, max[0])
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, rbs[0])
        GLES30.glRenderbufferStorageMultisample(GLES30.GL_RENDERBUFFER, samples, GLES30.GL_RGBA8, w, h)
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, rbs[1])
        GLES30.glRenderbufferStorageMultisample(GLES30.GL_RENDERBUFFER, samples, GLES30.GL_DEPTH_COMPONENT24, w, h)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
        GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_RENDERBUFFER, rbs[0])
        GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_RENDERBUFFER, rbs[1])
        check(GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) == GLES30.GL_FRAMEBUFFER_COMPLETE) { "MSAA FBO incomplete" }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    fun blitToWindow() {
        GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, fbo)
        GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER, 0)
        GLES30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GLES30.GL_COLOR_BUFFER_BIT, GLES30.GL_NEAREST)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    fun release() { GLES30.glDeleteRenderbuffers(2, rbs, 0); GLES30.glDeleteFramebuffers(1, intArrayOf(fbo), 0) }
}

private const val QUAD_VS = """#version 300 es
out vec2 vUv;
void main() {
  vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
  vUv = vec2(p.x, 1.0 - p.y);
  gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
}"""
private const val QUAD_FS = """#version 300 es
precision mediump float;
in vec2 vUv;
uniform sampler2D uTex;
uniform float uAlpha;
out vec4 frag;
void main() { frag = texture(uTex, vUv) * uAlpha; }"""

/** Full-frame premultiplied card (intro / outro), drawn with a fade alpha. */
private class Overlay(bmp: Bitmap) {
    private val prog = com.vynyl.record.turntable.compile(QUAD_VS, QUAD_FS)
    private val tex: Int

    init {
        val t = IntArray(1); GLES30.glGenTextures(1, t, 0); tex = t[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        bmp.recycle()
    }

    fun draw(w: Int, h: Int, alpha: Float) {
        GLES30.glViewport(0, 0, w, h)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA) // Android bitmaps upload premultiplied
        GLES30.glUseProgram(prog)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0); GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(prog, "uTex"), 0)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(prog, "uAlpha"), alpha)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    fun release() { GLES30.glDeleteTextures(1, intArrayOf(tex), 0); GLES30.glDeleteProgram(prog) }
}

/** Thin amber progress hairline along the bottom edge. */
private fun drawProgress(w: Int, h: Int, p: Float) {
    val bar = (h * 0.006f).toInt().coerceAtLeast(3)
    GLES30.glEnable(GLES30.GL_SCISSOR_TEST)
    GLES30.glScissor(0, 0, w, bar); GLES30.glClearColor(0.1f, 0.08f, 0.06f, 1f); GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
    GLES30.glScissor(0, 0, (w * p.coerceIn(0f, 1f)).toInt(), bar); GLES30.glClearColor(0.98f, 0.75f, 0.14f, 1f); GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
    GLES30.glDisable(GLES30.GL_SCISSOR_TEST)
}

/** Warm stage (same glows as the Player) that both cards sit on. */
private fun stageBitmap(w: Int, h: Int): Pair<Bitmap, Canvas> {
    val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(b)
    c.drawColor(0xFF0C0A09.toInt())
    fun glow(cx: Float, cy: Float, rx: Float, ry: Float, col: Int) {
        c.save(); c.translate(cx, cy); c.scale(1f, ry / rx)
        c.drawCircle(0f, 0f, rx, Paint().apply { shader = RadialGradient(0f, 0f, rx, col, 0, Shader.TileMode.CLAMP) })
        c.restore()
    }
    glow(w * 0.5f, h * 0.3f, w * 0.8f, h * 0.5f, 0x3DD97706)
    glow(w * 0.5f, h.toFloat(), w * 0.6f, h * 0.4f, 0x33991B1B)
    return b to c
}

private fun centered(c: Canvas, p: Paint, t: String, w: Int, y: Float, max: Float) {
    val s = ellipsize(p, t, max); c.drawText(s, (w - p.measureText(s)) / 2f, y, p)
}

private fun rule(c: Canvas, w: Int, y: Float, u: Float) =
    c.drawLine(w / 2f - 90 * u, y, w / 2f + 90 * u, y, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x8CD97706.toInt(); strokeWidth = 2 * u })

/** Opening card: from sender, for receiver, then the record's title. */
private fun introBitmap(fonts: TexFonts, w: Int, h: Int, title: String, from: String, to: String): Bitmap {
    val (b, c) = stageBitmap(w, h); val u = w / 1080f; val max = w - 160 * u
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.typeface = fonts.hanken; p.setFontVariationSettings("'wght' 700"); p.textSize = 26 * u; p.color = 0xE6FBBF24.toInt(); p.letterSpacing = 0.3f
    centered(c, p, "A RECORD PRESSED", w, h * 0.30f, max)
    p.letterSpacing = 0f; p.setFontVariationSettings(null)
    val small = Paint(p).apply { typeface = Typeface.create(fonts.gloock, Typeface.ITALIC); textSize = 34 * u; color = 0xA6FEF3C7.toInt() }
    val name = Paint(p).apply { typeface = fonts.gloock; textSize = 58 * u; color = 0xFFFEF3C7.toInt() }
    centered(c, small, "from", w, h * 0.38f, max); centered(c, name, from, w, h * 0.44f, max)
    centered(c, small, "for", w, h * 0.51f, max); centered(c, name, to, w, h * 0.57f, max)
    rule(c, w, h * 0.63f, u)
    p.typeface = fonts.gloock; p.textSize = 82 * u; p.color = 0xFFFBBF24.toInt()
    centered(c, p, title, w, h * 0.73f, w - 140 * u)
    return b
}

/** The app mark (same as public/icon.svg): amber tile, record with a V-notch cut from rim to label. */
private fun drawMark(c: Canvas, cx: Float, cy: Float, sz: Float) {
    val k = sz / 120f
    c.save(); c.translate(cx - sz / 2, cy - sz / 2); c.scale(k, k)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = 0xFFD97706.toInt(); p.setShadowLayer(20f, 0f, 0f, 0x73D97706)
    c.drawRoundRect(0f, 0f, 120f, 120f, 27f, 27f, p); p.clearShadowLayer()
    c.save()
    c.clipOutPath(android.graphics.Path().apply { moveTo(41f, 10f); lineTo(60f, 47f); lineTo(79f, 10f); close() })
    p.color = 0xFF14100D.toInt(); c.drawCircle(60f, 62f, 45f, p)
    p.style = Paint.Style.STROKE; p.strokeWidth = 1.6f
    p.color = 0x29F5E9D3; c.drawCircle(60f, 62f, 36f, p)
    p.color = 0x1AF5E9D3; c.drawCircle(60f, 62f, 27f, p)
    p.style = Paint.Style.FILL
    c.restore()
    p.color = 0xFFF5E9D3.toInt(); c.drawCircle(60f, 62f, 14f, p)
    p.color = 0xFF14100D.toInt(); c.drawCircle(60f, 62f, 2.6f, p)
    c.restore()
}

/** Free-tier closing card: the app mark and wordmark. */
private fun outroBitmap(fonts: TexFonts, w: Int, h: Int): Bitmap {
    val (b, c) = stageBitmap(w, h); val u = w / 1080f; val max = w - 160 * u
    drawMark(c, w / 2f, h * 0.36f, 190 * u)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.typeface = Typeface.create(fonts.gloock, Typeface.ITALIC); p.textSize = 32 * u; p.color = 0x99FEF3C7.toInt()
    centered(c, p, "made with", w, h * 0.53f, max)
    p.typeface = fonts.gloock; p.textSize = 124 * u; p.color = 0xFFFBBF24.toInt(); p.letterSpacing = 0.08f
    centered(c, p, "Vynyl", w, h * 0.64f, max)
    rule(c, w, h * 0.685f, u)
    p.typeface = fonts.hanken; p.setFontVariationSettings("'wght' 700"); p.textSize = 22 * u; p.color = 0xBFFEF3C7.toInt(); p.letterSpacing = 0.28f
    centered(c, p, "PRESS YOUR OWN RECORD", w, h * 0.735f, max)
    return b
}

private fun ellipsize(p: Paint, t: String, max: Float): String {
    if (p.measureText(t) <= max) return t
    var s = t
    while (s.isNotEmpty() && p.measureText("$s…") > max) s = s.dropLast(1)
    return "$s…"
}
