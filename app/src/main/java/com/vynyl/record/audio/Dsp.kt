package com.vynyl.record.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

/** Ported 1:1 from src/lib/dsp.ts (plus watermarkWav from src/lib/pro.ts). */
const val SR = 44100

class Stereo(val l: FloatArray, val r: FloatArray) {
    val size: Int get() = l.size
}

data class RenderOpts(
    val preset: Preset,
    val crackle: Crackle? = null,
    val seed: String,
    val intro: Float = 0f,
    val tail: Float = 0f,
    val maxGain: Float = 8f,
    val music: FloatArray? = null,
    val musicLevel: Float = 0f,
    val crackleLevel: Float = 1f,
    val character: Float = 1f,
    val volume: Float = 1f,
    val fixedGain: Float? = null,
)

/** JS ToInt32 of an integer-valued double (exact: fmod is exact in IEEE 754). */
internal fun toInt32(d: Double): Int = (d % 4294967296.0).toLong().toInt()

/** mulberry32 seeded from a string — same record + preset ⇒ same crackle positions. Bit-exact with the JS version. */
fun rng(seedStr: String): () -> Double {
    var h = 1779033703
    for (ch in seedStr) {
        h = (h xor ch.code) * -862048943 // Math.imul(h ^ c, 3432918353)
        h = (h shl 13) or (h ushr 19)
    }
    var a = h
    return {
        a += 0x6d2b79f5
        var t = (a xor (a ushr 15)) * (1 or a)
        t = (t + (t xor (t ushr 7)) * (61 or t)) xor t
        ((t xor (t ushr 14)).toLong() and 0xffffffffL).toDouble() / 4294967296.0
    }
}

private class Biquad(
    private val b0: Double, private val b1: Double, private val b2: Double,
    private val a1: Double, private val a2: Double,
) {
    private var z1 = 0.0
    private var z2 = 0.0

    fun run(x: Double): Double {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y
    }

    companion object {
        fun make(type: Char, f: Double, q: Double = 0.707, gainDb: Double = 0.0): Biquad {
            val w = (2 * PI * f) / SR
            val cs = cos(w)
            val al = sin(w) / (2 * q)
            val b0: Double; val b1: Double; val b2: Double; val a0: Double; val a1: Double; val a2: Double
            if (type == 'p') {
                val A = 10.0.pow(gainDb / 40)
                b0 = 1 + al * A; b1 = -2 * cs; b2 = 1 - al * A; a0 = 1 + al / A; a1 = -2 * cs; a2 = 1 - al / A
            } else {
                val lp = type == 'l'
                b1 = if (lp) 1 - cs else -(1 + cs)
                b0 = if (lp) b1 / 2 else -b1 / 2
                b2 = b0
                a0 = 1 + al; a1 = -2 * cs; a2 = 1 - al
            }
            return Biquad(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
        }
    }
}

private class Ev(var t: Int, val amp: Double, val len: Double, val pan: Double, val pop: Boolean)

/** Stylus landing in the lead-in groove: a bright contact click, a low tonearm thump with a falling pitch, then a short scuff of run-in noise. */
private fun needleDrop(L: FloatArray, R: FloatArray, at: Int, rnd: () -> Double) {
    val len = min(L.size - at, floor(SR * 0.35).toInt())
    val hp = Biquad.make('h', 1800.0, 0.7); val lp = Biquad.make('l', 5200.0)
    var ph = 0.0
    for (j in 0 until len) {
        val t = j.toDouble() / SR
        val click = (if (j < 2) 0.55 else 0.0) + hp.run(rnd() * 2 - 1) * 0.4 * exp(-t / 0.004)
        ph += (2 * PI * (44 + 30 * exp(-t / 0.03))) / SR
        val thump = sin(ph) * 0.42 * exp(-t / 0.05) * min(1.0, j / 40.0)
        val scuff = lp.run(rnd() * 2 - 1) * 0.07 * exp(-t / 0.11)
        L[at + j] += (click + thump + scuff).toFloat()
        R[at + j] += (click * 0.9 + thump + scuff * 0.92).toFloat()
    }
}

/** The needle-drop as 16-bit interleaved stereo PCM, played live by the player the moment the stylus touches the record. */
fun needleDropPcm(): ShortArray {
    val n = floor(SR * 0.35).toInt(); val l = FloatArray(n); val r = FloatArray(n)
    needleDrop(l, r, 0, rng("needle-drop"))
    return ShortArray(n * 2) { i -> ((if (i % 2 == 0) l[i / 2] else r[i / 2]).coerceIn(-1f, 1f) * 32767).toInt().toShort() }
}

/** Full vinyl pipeline on mono 44.1k voice → stereo master. Processes in blocks and yields to keep UI alive. */
suspend fun renderMaster(voice: FloatArray, o: RenderOpts, onProgress: (Float, String) -> Unit = { _, _ -> }): Stereo =
    withContext(Dispatchers.Default) {
        // character amount scales the preset between a clean pressing (0) and an exaggerated one (1.5)
        val k = max(0.0, min(1.5, o.character.toDouble()))
        val q = o.preset
        val warmth = q.warmth * k
        val drive = min(0.95, q.drive * k)
        val rolloff = max(3000.0, 19000 - (19000 - q.rolloff.toDouble()) * k)
        val wowDepth = q.wowDepth * k
        val flutterDepth = q.flutterDepth * k
        val width = max(0.0, 1 + (q.width - 1.0) * k)
        val honkDb = (q.honk ?: 0f) * k
        val humLv = (q.hum ?: 0f) * k
        val lowcut: Double? = q.lowcut?.takeIf { it != 0f }?.let { 80 + (it - 80.0) * min(1.0, k) }
        val wowHz = q.wowHz.toDouble()

        val cl0 = max(0.0, o.crackleLevel.toDouble())
        val c0 = o.crackle
        val c0Density = c0?.density?.toDouble() ?: 1.0
        val c0Amp = c0?.amp?.toDouble() ?: 1.0
        val cLen = c0?.len?.toDouble() ?: 1.0
        val cPops = c0?.pops?.toDouble() ?: 1.0
        val c0HissDb = c0?.hissDb?.toDouble() ?: 0.0
        val cHissTone = c0?.hissTone?.toDouble() ?: 1.0
        val cAmp = if (cl0 == 1.0) c0Amp else c0Amp * cl0
        val cHissDb = if (cl0 == 1.0) c0HissDb else c0HissDb + (if (cl0 > 0) 20 * log10(cl0) else -120.0)
        val R = rng(o.seed + ":" + q.id)
        val intro = floor(SR * o.intro.toDouble()).toInt()
        val n = voice.size + intro + floor(SR * o.tail.toDouble()).toInt()
        val L = FloatArray(n)
        val Rt = FloatArray(n)

        val hp = Biquad.make('h', lowcut ?: 80.0)
        val hp2 = Biquad.make('h', lowcut ?: 20.0)
        val honk = Biquad.make('p', 1500.0, 1.1, honkDb)
        val pres = Biquad.make('p', 3200.0, 0.9, 2.5)
        val warm = Biquad.make('p', 220.0, 0.8, warmth * 0.8)
        val lpA = Biquad.make('l', rolloff)
        val lpB = Biquad.make('l', rolloff * 1.05)
        val nLp = Biquad.make('l', min(18000.0, rolloff * 0.6 * cHissTone))
        // period recording chain (voice only): narrow acoustic-horn band, boxy mid resonance, dull top
        val ak = q.age * k
        val vLow = Biquad.make('h', 70 + ak * 42, 0.8)
        val vLow2 = Biquad.make('h', 60 + ak * 30)
        val vHorn = Biquad.make('p', 1250.0, 1.3, ak * 1.5)
        val vBox = Biquad.make('p', 650.0, 1.6, ak * 0.7)
        val vTop = Biquad.make('l', max(3200.0, 16000 - ak * 2300), 0.75)
        val vTop2 = Biquad.make('l', max(3400.0, 17000 - ak * 2300), 0.6)
        val tickHp = Biquad.make('h', 1400.0, 0.7)
        val tickHp2 = Biquad.make('h', 1400.0, 0.7)
        val hissHp = Biquad.make('h', 2500.0)
        val delay = FloatArray(4096)
        var wi = 0
        var env = 0.0
        var duck = 0.0
        val thr = 0.25
        val ratio = 3.0
        val atk = exp(-1 / (SR * 0.005))
        val rel = exp(-1 / (SR * 0.12))
        val surf = 10.0.pow((q.surfaceDb + cHissDb) / 20)
        val crackP = (q.crackles * c0Density) / 60 / SR
        val popP = (q.pops * cPops) / 60 / SR
        val texture = q.texture.toDouble()
        val music = o.music
        val musicLevel = o.musicLevel.toDouble()
        val ev = ArrayList<Ev>()
        val block = 8192
        var s = 0
        while (s < n) {
            val end = min(n, s + block)
            for (i in s until end) {
                val vi = i - intro
                var v: Double = if (vi >= 0 && vi < voice.size) voice[vi].toDouble() else 0.0
                // voice cleanup + warmth
                v = honk.run(warm.run(pres.run(hp2.run(hp.run(v)))))
                if (ak > 0.01) {
                    v = vTop2.run(vTop.run(vBox.run(vHorn.run(vLow2.run(vLow.run(v))))))
                    val sd = 1 + ak * 0.35
                    v = tanh(v * sd) / tanh(sd)
                }
                // background with voice-controlled ducking
                val lev = abs(v)
                duck = if (lev > duck) duck + (lev - duck) * 0.01 else duck * 0.99995
                if (music != null && music.isNotEmpty() && vi >= 0) v += music[vi % music.size] * musicLevel * (1 - min(0.75, duck * 6))
                // saturation
                val d = 1 + drive * 4
                v = (1 - drive) * v + drive * (tanh(v * d) / tanh(d))
                // compressor (soft knee approx)
                val a = abs(v)
                env = if (a > env) atk * env + (1 - atk) * a else rel * env + (1 - rel) * a
                if (env > thr) v *= (env / thr).pow(1 / ratio - 1)
                v *= 1.4
                // wow + flutter via interpolated delay
                delay[wi] = v.toFloat()
                val t = i.toDouble() / SR
                val mod = 600 + SR * (wowDepth * sin(2 * PI * wowHz * t + 0.3 * sin(t * 0.21)) + flutterDepth * sin(2 * PI * 42 * t))
                val rp = wi - mod
                val ipD = floor(rp)
                val fr = rp - ipD
                val ip = ipD.toInt()
                val x0 = delay[(ip + 4096) and 4095].toDouble()
                val x1 = delay[(ip + 4097) and 4095].toDouble()
                v = x0 + (x1 - x0) * fr
                wi = (wi + 1) and 4095
                // surface: only a faint, steady high hiss
                val w = R() * 2 - 1
                val noise = hissHp.run(nLp.run(w)) * surf * 0.9
                // crackle: sharp broadband clicks with a power-law size spread, plus the odd low "thock" pop
                if (R() < crackP * (if (ev.isNotEmpty()) 1.8 else 1.0)) {
                    val r = R()
                    val amp = (0.03 + r * r * r * 0.32) * (texture / 3 + 0.4) * cAmp
                    val len = (2 + R() * 10) * cLen + 2
                    val pan = 0.15 + R() * 0.7
                    ev.add(Ev(0, amp, len, pan, false))
                }
                if (R() < popP) {
                    val amp = (0.16 + R() * 0.1) * min(1.3, c0Amp) * cl0
                    val len = 90 + R() * 120
                    val pan = 0.3 + R() * 0.4
                    ev.add(Ev(0, amp, len, pan, true))
                }
                var cl = 0.0; var cr = 0.0; var pl = 0.0; var pr = 0.0
                var kk = ev.size - 1
                while (kk >= 0) {
                    val e = ev[kk]
                    if (e.pop) {
                        val sig = sin(e.t * 0.06) * exp(-e.t / (e.len * 0.25)) * e.amp
                        pl += sig * (1 - e.pan); pr += sig * e.pan
                    } else {
                        val base = if (e.t == 0) (if (R() < 0.5) -1.0 else 1.0) else (R() * 2 - 1) * 0.6
                        val sig = base * exp(-e.t / (e.len * 0.35)) * e.amp
                        cl += sig * (1 - e.pan); cr += sig * e.pan
                    }
                    e.t += 1
                    if (e.t > e.len) ev.removeAt(kk)
                    kk--
                }
                cl = tickHp.run(cl) + pl
                cr = tickHp2.run(cr) + pr
                // eq rolloff per channel, stereo width
                val fade = min(1.0, min(i / (SR * 0.4), (n - i) / (SR * 0.6)))
                val vl = lpA.run(v)
                val vr = lpB.run(v)
                val hum = if (humLv != 0.0) humLv * (sin(2 * PI * 60 * t) + 0.4 * sin(2 * PI * 180 * t)) else 0.0
                val mid = (vl + vr) / 2 + hum
                val side = ((vl - vr) / 2) * width
                L[i] = ((mid + side + noise + cl * 2) * fade).toFloat()
                Rt[i] = ((mid - side + noise * 0.92 + cr * 2) * fade).toFloat()
            }
            val p = s.toFloat() / n
            onProgress(p, if (p < 0.3f) "Mixing atmosphere" else if (p < 0.8f) "Adding vinyl character" else "Mastering")
            yield()
            s += block
        }
        // limiter: normalize to -1 dBFS then soft ceiling
        var peak = 1e-6
        for (i in 0 until n) peak = max(peak, max(abs(L[i].toDouble()), abs(Rt[i].toDouble())))
        val g = o.fixedGain?.toDouble()
            ?: (min(0.891 / peak, o.maxGain.toDouble()) * max(0.0, min(1.0, o.volume.toDouble())))
        for (i in 0 until n) {
            L[i] = if (L[i].isFinite()) (L[i] * g).toFloat() else 0f
            Rt[i] = if (Rt[i].isFinite()) (Rt[i] * g).toFloat() else 0f
        }
        Stereo(L, Rt)
    }

private fun encodeWav(l: FloatArray, r: FloatArray): ByteArray {
    val n = l.size
    val b = ByteBuffer.allocate(44 + n * 4).order(ByteOrder.LITTLE_ENDIAN)
    b.put("RIFF".toByteArray(Charsets.US_ASCII)); b.putInt(36 + n * 4)
    b.put("WAVE".toByteArray(Charsets.US_ASCII)); b.put("fmt ".toByteArray(Charsets.US_ASCII))
    b.putInt(16); b.putShort(1); b.putShort(2); b.putInt(SR)
    b.putInt(SR * 4); b.putShort(4); b.putShort(16)
    b.put("data".toByteArray(Charsets.US_ASCII)); b.putInt(n * 4)
    for (i in 0 until n) {
        b.putShort((max(-1f, min(1f, l[i])) * 32767f).toInt().toShort())
        b.putShort((max(-1f, min(1f, r[i])) * 32767f).toInt().toShort())
    }
    return b.array()
}

/** 16-bit stereo 44.1 kHz WAV. */
fun encodeWav(s: Stereo): ByteArray = encodeWav(s.l, s.r)

fun waveform(data: FloatArray, bars: Int = 72): List<Float> {
    val step = (data.size / bars).takeIf { it != 0 } ?: 1
    val out = ArrayList<Float>(bars)
    for (b in 0 until bars) {
        var m = 0f
        var i = b * step
        while (i < (b + 1) * step && i < data.size) { m = max(m, abs(data[i])); i += 32 }
        out.add(m)
    }
    val mx = max(out.maxOrNull() ?: 0f, 1e-6f)
    return out.map { it / mx }
}

/** Mixes the needle-drop over the start of a 16-bit stereo WAV, so exports open with the same landing heard live. */
fun withNeedleDrop(wav: ByteArray): ByteArray {
    val out = wav.copyOf(); val d = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
    val drop = needleDropPcm(); val n = min((out.size - 44) / 2, drop.size)
    for (i in 0 until n) d.putShort(44 + i * 2, (d.getShort(44 + i * 2) + drop[i]).coerceIn(-32768, 32767).toShort())
    return out
}

/** Free exports get a soft music-box "Vynyl" tag appended. Input/output are 16-bit stereo WAVs from encodeWav. */
fun watermarkWav(wav: ByteArray): ByteArray {
    val d = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
    val n = (wav.size - 44) / 4
    val tagLen = floor(SR * 2.4).toInt()
    val gap = floor(SR * 0.4).toInt()
    val total = n + gap + tagLen
    val l = FloatArray(total)
    val r = FloatArray(total)
    for (i in 0 until n) {
        l[i] = d.getShort(44 + i * 4) / 32768f
        r[i] = d.getShort(46 + i * 4) / 32768f
    }
    val notes = intArrayOf(72, 76, 79, 84)
    val st = n + gap
    notes.forEachIndexed { k, m ->
        val f = 440 * 2.0.pow((m - 69) / 12.0)
        val at = st + floor(k * 0.16 * SR).toInt()
        var i = 0
        while (at + i < total) {
            val t = i.toDouble() / SR
            val e = min(1.0, t / 0.004) * exp(-t * 2.6)
            val s = sin(2 * PI * f * t + 0.5 * exp(-t * 4) * sin(2 * PI * f * 3 * t)) * e * 0.12
            l[at + i] = (l[at + i] + s * (1 - k * 0.15)).toFloat()
            r[at + i] = (r[at + i] + s * (0.55 + k * 0.15)).toFloat()
            i++
        }
    }
    return encodeWav(l, r)
}

/** Decode any audio file the platform supports to mono float PCM at SR (linear resampling). */
suspend fun decodeToMono(ctx: Context, uri: Uri): FloatArray = withContext(Dispatchers.IO) {
    val ex = MediaExtractor()
    var codec: MediaCodec? = null
    try {
        ex.setDataSource(ctx, uri, null)
        var track = -1
        for (i in 0 until ex.trackCount) {
            val mime = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) { track = i; break }
        }
        require(track >= 0) { "No audio track" }
        ex.selectTrack(track)
        val fmt = ex.getTrackFormat(track)
        val mime = fmt.getString(MediaFormat.KEY_MIME)!!
        var srcRate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var pcmFloat = false
        val c = MediaCodec.createDecoderByType(mime)
        codec = c
        c.configure(fmt, null, null, 0)
        c.start()
        var mono = FloatArray(1 shl 16)
        var count = 0
        val info = MediaCodec.BufferInfo()
        var inDone = false
        var outDone = false
        while (!outDone) {
            if (!inDone) {
                val ii = c.dequeueInputBuffer(10_000)
                if (ii >= 0) {
                    val buf = c.getInputBuffer(ii)!!
                    val sz = ex.readSampleData(buf, 0)
                    if (sz < 0) {
                        c.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inDone = true
                    } else {
                        c.queueInputBuffer(ii, 0, sz, ex.sampleTime, 0)
                        ex.advance()
                    }
                }
            }
            val oi = c.dequeueOutputBuffer(info, 10_000)
            if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val of = c.outputFormat
                srcRate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                pcmFloat = of.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                    of.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
            } else if (oi >= 0) {
                val ob = c.getOutputBuffer(oi)
                if (ob != null && info.size > 0) {
                    ob.position(info.offset); ob.limit(info.offset + info.size)
                    val bb = ob.slice().order(ByteOrder.nativeOrder())
                    val ch = max(1, channels)
                    val frames = if (pcmFloat) info.size / (4 * ch) else info.size / (2 * ch)
                    if (count + frames > mono.size) mono = mono.copyOf(max(mono.size * 2, count + frames))
                    if (pcmFloat) {
                        val fb = bb.asFloatBuffer()
                        for (f in 0 until frames) { var acc = 0f; for (k in 0 until ch) acc += fb.get(f * ch + k); mono[count++] = acc / ch }
                    } else {
                        val sb = bb.asShortBuffer()
                        for (f in 0 until frames) { var acc = 0f; for (k in 0 until ch) acc += sb.get(f * ch + k) / 32768f; mono[count++] = acc / ch }
                    }
                }
                c.releaseOutputBuffer(oi, false)
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outDone = true
            }
        }
        val src = mono.copyOf(count)
        if (srcRate == SR || count == 0) src else {
            val outN = ceil(count.toDouble() * SR / srcRate).toInt()
            val out = FloatArray(outN)
            val ratio = srcRate.toDouble() / SR
            for (i in 0 until outN) {
                val pos = i * ratio
                val i0 = pos.toInt()
                val fr = (pos - i0).toFloat()
                val a = src[min(i0, count - 1)]
                val b = src[min(i0 + 1, count - 1)]
                out[i] = a + (b - a) * fr
            }
            out
        }
    } finally {
        try { codec?.stop() } catch (_: Exception) {}
        try { codec?.release() } catch (_: Exception) {}
        ex.release()
    }
}

/** Arrays produced by demoVoice(), so previews can tell a stand-in from a real voice (identity-based). */
private val demoSources: MutableSet<FloatArray> = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<FloatArray, Boolean>()))

fun isDemoVoice(a: FloatArray?): Boolean = a != null && demoSources.contains(a)

/** A soft FM music-box lullaby so there's something to press without a mic. */
fun demoVoice(): FloatArray {
    val notes = intArrayOf(0, 4, 7, 12, 11, 7, 9, 5, 4, 2, 0, -1, 0, 4, 7, 4)
    val len = SR * 9
    val out = FloatArray(len)
    val beat = len.toDouble() / notes.size
    notes.forEachIndexed { k, nt ->
        val f = 784 * 2.0.pow(nt / 12.0)
        val st = floor(k * beat).toInt()
        var i = 0
        while (i < SR * 2 && st + i < len) {
            val t = i.toDouble() / SR
            val e = min(1.0, t / 0.003) * exp(-t * 2.4)
            out[st + i] = (out[st + i] + 0.22 * e * sin(2 * PI * f * t + 1.3 * exp(-t * 4) * sin(2 * PI * f * 5.19 * t))).toFloat()
            i++
        }
    }
    demoSources.add(out)
    return out
}
