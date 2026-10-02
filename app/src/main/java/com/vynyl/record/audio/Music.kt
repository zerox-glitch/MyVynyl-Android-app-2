package com.vynyl.record.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Ported 1:1 from src/lib/music.ts. Device-generated background beds: wavetable strings/reeds/organ,
 * FM keys and bells, Karplus–Strong plucks, noise brushes, each with its own key, tempo and room.
 * All note writes wrap modulo the bed length so every bed loops seamlessly.
 */
data class MusicBed(val id: String, val name: String, val blurb: String, val romantic: Boolean = false)

private fun midi(n: Int): Double = 440 * 2.0.pow((n - 69) / 12.0)
private const val TABLE = 2048

private fun table(partials: DoubleArray): FloatArray {
    val t = FloatArray(TABLE)
    for (i in 0 until TABLE) {
        var s = 0.0
        for (k in partials.indices) s += partials[k] * sin((2 * PI * (k + 1) * i) / TABLE)
        t[i] = s.toFloat()
    }
    var m = 1e-6
    for (v in t) m = max(m, abs(v.toDouble()))
    for (i in 0 until TABLE) t[i] = (t[i] / m).toFloat()
    return t
}

private val SAW by lazy { table(DoubleArray(14) { 1.0 / (it + 1) }) }
private val REED by lazy { table(doubleArrayOf(1.0, 0.08, 0.55, 0.06, 0.38, 0.05, 0.25, 0.04, 0.16, 0.03, 0.1)) }
private val ORGAN by lazy { table(doubleArrayOf(1.0, 0.9, 0.3, 0.55, 0.0, 0.2, 0.0, 0.3)) }
private val WARM by lazy { table(doubleArrayOf(1.0, 0.35, 0.12, 0.05)) }
private val WHISTLE by lazy { table(doubleArrayOf(1.0, 0.12, 0.05, 0.02)) }

private fun bed(seconds: Double) = FloatArray(floor(SR * seconds).toInt())

/** Wavetable voice with optional detuned unison (chorus) and vibrato. env: a = attack, d = decay rate (0 = none), r = release. */
private fun tone(
    out: FloatArray, at: Double, dur: Double, f: Double, amp: Double, tab: FloatArray,
    a: Double, d: Double = 0.0, r: Double = 0.15,
    unison: Int = 1, detune: Double = 0.004, vib: Double = 0.0, vibDepth: Double = 0.005, bright: Double = 1.0,
) {
    val st = floor(at * SR).toInt()
    val len = floor(dur * SR).toInt()
    val rel = floor(r * SR).toInt()
    val n = unison
    var lp = 0.0
    val size = out.size
    for (u in 0 until n) {
        val fu = f * (1 + (if (n > 1) (u.toDouble() / (n - 1) - 0.5) * detune * 2 else 0.0))
        val inc = (fu * TABLE) / SR
        var ph = (u * 0.37 * TABLE) % TABLE
        for (i in 0 until len + rel) {
            val t = i.toDouble() / SR
            var e = min(1.0, t / a) * (if (d != 0.0) exp(-t * d) else 1.0)
            if (i > len) e *= 1 - (i - len).toDouble() / rel
            val v = if (vib != 0.0) 1 + vibDepth * sin(2 * PI * vib * t + u) else 1.0
            ph += inc * v
            if (ph >= TABLE) ph -= TABLE
            val s = tab[ph.toInt()]
            lp += (s - lp) * bright
            val idx = (st + i) % size
            out[idx] = (out[idx] + lp * amp * e / n).toFloat()
        }
    }
}

/** Two-operator FM — electric piano, celesta, music box. */
private fun fm(out: FloatArray, at: Double, dur: Double, f: Double, amp: Double, ratio: Double, index: Double, decay: Double) {
    val st = floor(at * SR).toInt()
    val len = floor(dur * SR).toInt()
    val size = out.size
    for (i in 0 until len) {
        val t = i.toDouble() / SR
        val e = min(1.0, t / 0.003) * exp(-t * decay)
        val idx = (st + i) % size
        out[idx] = (out[idx] + sin(2 * PI * f * t + index * exp(-t * decay * 1.6) * sin(2 * PI * f * ratio * t)) * amp * e).toFloat()
    }
}

/** JS `(x * 1103515245 + 12345) & 0x7fffffff` with double-precision multiply semantics. */
private fun lcg(x: Double): Double = (toInt32(x * 1103515245 + 12345) and 0x7fffffff).toDouble()

/** Karplus–Strong plucked string — nylon guitar, harp, upright bass. */
private fun pluck(out: FloatArray, at: Double, f: Double, amp: Double, damp: Double = 0.996, bright: Double = 0.5, seed: Int = 1) {
    val st = floor(at * SR).toInt()
    val N = max(2, (SR / f).roundToJs())
    val buf = FloatArray(N)
    var x = seed * 9301.0 + 49297
    for (i in 0 until N) { x = lcg(x); buf[i] = (x / 0x3fffffff - 1).toFloat() }
    for (p in 0 until 2) for (i in 0 until N) buf[i] = (buf[i].toDouble() * (1 - bright) + buf[(i + 1) % N] * bright).toFloat()
    val size = out.size
    val len = min(SR * 4, size)
    var idx = 0
    for (i in 0 until len) {
        val nx = (idx + 1) % N
        val y = damp * 0.5 * (buf[idx].toDouble() + buf[nx])
        val o = (st + i) % size
        out[o] = (out[o] + buf[idx] * amp * min(1.0, i / 40.0)).toFloat()
        buf[idx] = y.toFloat(); idx = nx
    }
}

/** Math.round semantics (half up) for positive values. */
private fun Double.roundToJs(): Int = floor(this + 0.5).toInt()

private fun brush(out: FloatArray, at: Double, amp: Double, seed: Int, len: Double = 0.12) {
    var x = seed * 7919.0 + 1
    var lp = 0.0
    val st = floor(at * SR).toInt()
    val size = out.size
    var i = 0
    while (i < SR * len) {
        x = lcg(x)
        val w = x / 0x3fffffff - 1
        lp += (w - lp) * 0.3
        val o = (st + i) % size
        out[o] = (out[o] + (w - lp) * amp * exp(-i / (SR * len * 0.3))).toFloat()
        i++
    }
}

/** Small Schroeder reverb: 4 combs + 2 allpasses. Loop-wrapped so beds stay seamless. */
private fun room(dry: FloatArray, size: Double, mix: Double): FloatArray {
    val n = dry.size
    val wet = FloatArray(n)
    val combs = intArrayOf(1557, 1617, 1491, 1422).map { floor(it * (0.6 + size * 0.9)).toInt() }
    val fb = 0.72 + size * 0.2
    for (d in combs) {
        val line = FloatArray(d)
        var j = 0
        var lp = 0.0
        for (pass in 0 until 2) for (i in 0 until n) {
            val y = line[j].toDouble()
            lp = y * 0.7 + lp * 0.3
            line[j] = (dry[i] + lp * fb).toFloat()
            j = (j + 1) % d
            if (pass != 0) wet[i] = (wet[i] + y / combs.size).toFloat()
        }
    }
    for (d in intArrayOf(225, 556)) {
        val line = FloatArray(d)
        var j = 0
        for (i in 0 until n) {
            val b = line[j].toDouble()
            val y = -wet[i] + b
            line[j] = (wet[i] + b * 0.5).toFloat()
            wet[i] = y.toFloat()
            j = (j + 1) % d
        }
    }
    for (i in 0 until n) dry[i] = (dry[i] * (1 - mix * 0.5) + wet[i] * mix).toFloat()
    return dry
}

private fun finish(a: FloatArray, size: Double, mix: Double, peak: Double = 0.26): FloatArray {
    room(a, size, mix)
    // remove DC / sub-rumble; two passes so the filter state wraps the loop
    var x1 = 0.0
    var y1 = 0.0
    val R = 1 - (2 * PI * 25) / SR
    for (pass in 0 until 2) for (i in a.indices) {
        val y = a[i] - x1 + R * y1
        x1 = a[i].toDouble(); y1 = y
        if (pass != 0) a[i] = y.toFloat()
    }
    // close any residual jump at the loop point with a 60 ms ramp
    val fadeN = floor(SR * 0.06).toInt()
    val jump = a[0].toDouble() - a[a.size - 1]
    for (i in 0 until fadeN) { val k = a.size - fadeN + i; a[k] = (a[k] + jump * (i.toDouble() / fadeN)).toFloat() }
    var m = 1e-6
    for (v in a) m = max(m, abs(v.toDouble()))
    for (i in a.indices) a[i] = (a[i] * (peak / m)).toFloat()
    return a
}

private fun ints(vararg v: Int) = v

// ——— beds ———

private fun musicBox(): FloatArray {
    val out = bed(9.6)
    val seq = ints(72, 76, 79, 84, 79, 76, 74, 77, 81, 86, 81, 77, 72, 76, 79, 83, 79, 76, 71, 74, 79, 83, 79, 74)
    seq.forEachIndexed { i, n ->
        val t = i * 0.4 + (i % 2) * 0.012
        fm(out, t, 2.6, midi(n - 12), 0.26, 3.0, 0.45, 1.8)
        fm(out, t, 1.2, midi(n), 0.05, 2.0, 0.25, 3.2)
    }
    listOf(ints(48, 55, 64), ints(45, 52, 60), ints(41, 48, 57), ints(43, 50, 59)).forEachIndexed { i, ch ->
        ch.forEach { n -> tone(out, i * 2.4, 2.5, midi(n), 0.05, WARM, a = 0.6, r = 0.6, bright = 0.1) }
    }
    return finish(out, 0.45, 0.35, 0.2)
}

private fun hearth(): FloatArray {
    val out = bed(12.8)
    val prog = listOf(ints(41, 53, 57, 60, 64), ints(38, 50, 57, 60, 65), ints(46, 50, 58, 62, 65), ints(36, 52, 55, 60, 64))
    prog.forEachIndexed { bar, ch ->
        val t0 = bar * 3.2
        pluck(out, t0, midi(ch[0]), 0.5, 0.997, 0.3, bar); pluck(out, t0 + 1.6, midi(ch[0] + 7), 0.35, 0.997, 0.3, bar + 7)
        for (j in 1 until ch.size) tone(out, t0, 3.3, midi(ch[j]), 0.1, WARM, a = 0.5, r = 0.7, unison = 2, detune = 0.003, vib = 4.2, vibDepth = 0.0015, bright = 0.06)
    }
    return finish(out, 0.55, 0.35, 0.23)
}

private fun ballad(): FloatArray {
    val out = bed(12.8)
    val prog = listOf(ints(43, 55, 59, 62), ints(40, 52, 55, 59), ints(36, 52, 55, 60), ints(38, 50, 54, 57))
    val mel = ints(71, 74, 72, 71, 69, 67, 69, 71, 72, 71, 69, 66)
    prog.forEachIndexed { bar, ch ->
        val t0 = bar * 3.2
        tone(out, t0, 3.2, midi(ch[0]), 0.16, SAW, a = 0.4, r = 0.6, unison = 2, detune = 0.003, vib = 5.0, vibDepth = 0.004, bright = 0.07)
        ints(1, 2, 3, 2, 1, 2).forEachIndexed { i, k -> tone(out, t0 + i * 0.53, 0.3, midi(ch[k]), 0.13, WARM, a = 0.004, d = 1.4, r = 1.6) }
    }
    mel.forEachIndexed { i, n ->
        tone(out, i * 1.066, 0.4, midi(n), 0.2, WARM, a = 0.004, d = 1.1, r = 1.8)
        fm(out, i * 1.066, 2.0, midi(n), 0.04, 2.0, 0.5, 2.5)
    }
    return finish(out, 0.6, 0.38, 0.24)
}

/** Plays a [note, beats] melody line, mirroring the JS reduce. */
private inline fun line(notes: Array<DoubleArray>, play: (t: Double, n: Int, d: Double) -> Unit) {
    var t = 0.0
    for (nd in notes) { play(t, nd[0].toInt(), nd[1]); t += nd[1] }
}

private fun nd(n: Int, d: Double) = doubleArrayOf(n.toDouble(), d)

private fun sweetheart(): FloatArray {
    val beat = 60.0 / 80
    val out = FloatArray(floor(SR * beat * 16).toInt())
    val chords = listOf(ints(55, 59, 62, 65), ints(52, 55, 59, 62), ints(57, 60, 64, 67), ints(50, 54, 57, 60))
    val roots = ints(43, 40, 45, 38)
    chords.forEachIndexed { bar, ch ->
        val t0 = bar * 4 * beat
        ints(0, 7, 12, 7).forEachIndexed { b, iv -> pluck(out, t0 + b * beat, midi(roots[bar] + iv), 0.55, 0.993, 0.55, bar * 4 + b) }
        for (b in ints(1, 3)) ch.forEachIndexed { k, n -> fm(out, t0 + b * beat + k * 0.01, beat * 0.9, midi(n), 0.06, 1.0, 1.4, 3.0) }
        for (e in 0 until 8) brush(out, t0 + e * beat * 0.5 + (if (e % 2 != 0) beat * 0.16 else 0.0), if (e % 2 != 0) 0.035 else 0.06, bar * 17 + e, 0.1)
    }
    line(arrayOf(nd(74, 2.0), nd(72, 1.0), nd(71, 1.0), nd(69, 2.0), nd(67, 2.0), nd(71, 1.5), nd(72, 0.5), nd(74, 2.0), nd(76, 2.0), nd(74, 2.0))) { t, n, d ->
        tone(out, t * beat, d * beat * 0.95, midi(n), 0.15, REED, a = 0.05, r = 0.15, vib = 5.5, vibDepth = 0.005, bright = 0.25)
    }
    return finish(out, 0.45, 0.3, 0.24)
}

private fun foxtrot(): FloatArray {
    val beat = 60.0 / 116
    val out = FloatArray(floor(SR * beat * 16).toInt())
    val chords = listOf(ints(60, 64, 67), ints(57, 60, 64), ints(62, 65, 69), ints(55, 59, 62))
    val roots = ints(36, 33, 38, 31)
    chords.forEachIndexed { bar, ch ->
        val t0 = bar * 4 * beat
        ints(0, 7, 0, 7).forEachIndexed { b, iv -> tone(out, t0 + b * beat, beat * 0.55, midi(roots[bar] + iv), 0.3, REED, a = 0.015, r = 0.08, bright = 0.12) }
        for (b in 0 until 4) ch.forEachIndexed { k, n -> pluck(out, t0 + b * beat + 0.5 * beat + k * 0.006, midi(n), 0.14, 0.985, 0.7, bar * 40 + b * 4 + k) }
    }
    line(arrayOf(nd(76, 1.0), nd(74, 0.5), nd(72, 0.5), nd(69, 2.0), nd(72, 1.0), nd(74, 1.0), nd(77, 2.0), nd(76, 1.0), nd(74, 1.0), nd(71, 2.0), nd(67, 4.0))) { t, n, d ->
        tone(out, t * beat, d * beat * 0.85, midi(n), 0.12, SAW, a = 0.03, r = 0.1, vib = 6.0, vibDepth = 0.004, bright = 0.1)
    }
    return finish(out, 0.3, 0.2, 0.24)
}

private fun ballroom(): FloatArray {
    val beat = 0.7
    val out = FloatArray(floor(SR * beat * 24).toInt())
    val prog = listOf(ints(50, 62, 66, 69), ints(47, 62, 66, 71), ints(43, 59, 62, 67), ints(45, 57, 61, 64), ints(50, 62, 66, 69), ints(55, 59, 62, 67), ints(45, 61, 64, 67), ints(50, 62, 66, 69))
    prog.forEachIndexed { bar, ch ->
        val t0 = bar * 3 * beat
        tone(out, t0, beat * 1.2, midi(ch[0] - 12), 0.14, SAW, a = 0.05, r = 0.3, unison = 2, detune = 0.004, bright = 0.08)
        for (b in ints(1, 2)) for (j in 1 until ch.size) tone(out, t0 + b * beat, beat * 0.7, midi(ch[j]), 0.05, SAW, a = 0.05, r = 0.2, unison = 2, detune = 0.005, bright = 0.12)
    }
    ints(78, 76, 74, 73, 74, 71, 69, 73).forEachIndexed { i, n ->
        tone(out, i * 3 * beat, 3 * beat, midi(n), 0.11, SAW, a = 0.5, r = 0.5, unison = 3, detune = 0.004, vib = 5.4, vibDepth = 0.006, bright = 0.12)
    }
    return finish(out, 0.8, 0.45, 0.24)
}

private fun harp(): FloatArray {
    val out = bed(12.8)
    val prog = listOf(ints(48, 55, 60, 64, 67, 72), ints(45, 52, 57, 60, 64, 69), ints(41, 48, 53, 57, 60, 65), ints(43, 50, 55, 59, 62, 67))
    prog.forEachIndexed { bar, ch ->
        val up = ch.toList() + listOf(ch[4], ch[3], ch[2], ch[1])
        up.forEachIndexed { i, n -> pluck(out, bar * 3.2 + i * 0.32, midi(n), if (i != 0) 0.26 else 0.4, 0.9985, 0.3, bar * 20 + i) }
    }
    return finish(out, 0.7, 0.4, 0.24)
}

private fun jazz(): FloatArray {
    val beat = 60.0 / 92
    val out = FloatArray(floor(SR * beat * 16).toInt())
    val chords = listOf(ints(62, 65, 69, 72), ints(59, 62, 65, 69), ints(60, 64, 67, 71), ints(61, 64, 67, 70))
    val walk = listOf(ints(38, 41, 45, 44), ints(43, 47, 50, 49), ints(36, 40, 43, 44), ints(45, 49, 52, 51))
    chords.forEachIndexed { bar, ch ->
        walk[bar].forEachIndexed { b, n -> pluck(out, (bar * 4 + b) * beat, midi(n), 0.7, 0.993, 0.6, bar * 4 + b) }
        for (b in doubleArrayOf(1.5, 3.0)) ch.forEachIndexed { k, n -> fm(out, (bar * 4 + b) * beat + k * 0.008, beat * 1.4, midi(n), 0.09, 1.0, 1.8, 2.6) }
        for (e in 0 until 8) brush(out, (bar * 4 + e / 2.0) * beat + (if (e % 2 != 0) beat * 0.17 else 0.0), if (e % 2 != 0) 0.05 else 0.1, bar * 31 + e)
        brush(out, (bar * 4 + 1) * beat, 0.07, bar + 99, 0.25); brush(out, (bar * 4 + 3) * beat, 0.07, bar + 199, 0.25)
    }
    return finish(out, 0.4, 0.22)
}

private fun waltz(): FloatArray {
    val beat = 0.4
    val out = FloatArray(floor(SR * beat * 24).toInt())
    val bass = ints(45, 40, 45, 40, 50, 45, 40, 45)
    val chord = listOf(ints(60, 64, 69), ints(59, 64, 68), ints(60, 64, 69), ints(59, 62, 68), ints(62, 65, 69), ints(60, 64, 69), ints(59, 62, 68), ints(60, 64, 69))
    val mel = ints(76, 77, 76, 74, 72, 71, 72, 74, 76, 81, 80, 76)
    bass.forEachIndexed { bar, b ->
        tone(out, bar * 3 * beat, beat * 0.7, midi(b), 0.32, REED, a = 0.02, r = 0.08, unison = 2, detune = 0.006)
        for (k in ints(1, 2)) chord[bar].forEach { n -> tone(out, (bar * 3 + k) * beat, beat * 0.45, midi(n), 0.09, REED, a = 0.015, r = 0.06, unison = 2, detune = 0.006) }
    }
    // musette: two reeds beating against each other
    mel.forEachIndexed { i, n -> tone(out, i * 2 * beat, beat * 1.8, midi(n), 0.2, REED, a = 0.04, r = 0.1, unison = 2, detune = 0.008, vib = 5.2, vibDepth = 0.003) }
    return finish(out, 0.3, 0.18)
}

private fun piano(): FloatArray {
    val out = bed(9.6)
    val prog = listOf(ints(48, 55, 64, 67), ints(45, 52, 60, 64), ints(41, 48, 57, 60), ints(43, 50, 59, 62))
    prog.forEachIndexed { bar, ch ->
        val pat = ints(ch[0], ch[1], ch[2], ch[3], ch[2], ch[1], ch[2], ch[3])
        pat.forEachIndexed { i, n ->
            tone(out, bar * 2.4 + i * 0.3, 0.2, midi(n), if (i == 0) 0.34 else 0.22, WARM, a = 0.004, d = 1.6, r = 2.0)
            fm(out, bar * 2.4 + i * 0.3, 2.0, midi(n), 0.05, 2.0, 0.6, 3.0)
        }
    }
    return finish(out, 0.5, 0.3, 0.24)
}

private fun organ(): FloatArray {
    val out = bed(12.0)
    val chords = listOf(ints(36, 48, 55, 60, 64), ints(33, 45, 52, 57, 60), ints(29, 45, 53, 57, 60), ints(31, 43, 50, 55, 59))
    chords.forEachIndexed { i, ch -> ch.forEach { n -> tone(out, i * 3.0, 3.0, midi(n), 0.14, ORGAN, a = 0.25, r = 0.6, vib = 6.5, vibDepth = 0.002) } }
    return finish(out, 1.0, 0.55, 0.22)
}

private fun parlour(): FloatArray {
    val out = bed(12.0)
    val prog = listOf(ints(48, 55, 64, 67), ints(43, 55, 62, 67), ints(45, 57, 60, 64), ints(41, 53, 60, 65))
    prog.forEachIndexed { i, ch ->
        ch.forEachIndexed { k, n -> tone(out, i * 3.0, 3.1, midi(n), if (k != 0) 0.1 else 0.14, SAW, a = 0.9, r = 0.9, unison = 3, detune = 0.004, vib = 4.6, vibDepth = 0.002, bright = 0.08) }
    }
    return finish(out, 0.6, 0.38, 0.22)
}

// ——— romantic ———

private fun serenade(): FloatArray {
    val out = bed(12.8)
    val prog = listOf(ints(51, 58, 63, 67), ints(48, 55, 63, 67), ints(44, 56, 60, 63), ints(46, 53, 58, 62))
    prog.forEachIndexed { bar, ch ->
        ch.forEach { n -> tone(out, bar * 3.2, 3.3, midi(n), 0.12, SAW, a = 0.6, r = 0.8, unison = 3, detune = 0.006, vib = 5.0, vibDepth = 0.003, bright = 0.18) }
        val arp = ints(ch[1] + 12, ch[2] + 12, ch[3] + 12, ch[2] + 24, ch[3] + 12, ch[2] + 12)
        arp.forEachIndexed { i, n -> pluck(out, bar * 3.2 + 0.1 + i * 0.27, midi(n), 0.22, 0.9985, 0.35, bar * 10 + i) }
    }
    // a soft violin line on top
    ints(79, 77, 75, 74, 75, 72, 70, 74).forEachIndexed { i, n ->
        tone(out, i * 1.6, 1.55, midi(n), 0.1, SAW, a = 0.35, r = 0.4, unison = 2, detune = 0.003, vib = 5.8, vibDepth = 0.006, bright = 0.12)
    }
    return finish(out, 0.85, 0.5, 0.24)
}

private fun guitar(): FloatArray {
    val out = bed(9.6)
    val prog = listOf(ints(45, 57, 60, 64), ints(41, 57, 60, 65), ints(43, 55, 59, 62), ints(40, 56, 59, 64))
    prog.forEachIndexed { bar, ch ->
        val t0 = bar * 2.4
        val pat = ints(0, 2, 1, 3, 2, 1, 3, 2)
        pluck(out, t0, midi(ch[0]), 0.55, 0.9975, 0.45, bar)
        pat.forEachIndexed { i, k -> pluck(out, t0 + 0.3 * i + (if (i % 2 != 0) 0.02 else 0.0), midi(ch[k]), 0.3, 0.997, 0.5, bar * 8 + i + 3) }
    }
    return finish(out, 0.25, 0.2, 0.26)
}

private fun candlelight(): FloatArray {
    val beat = 60.0 / 72
    val out = FloatArray(floor(SR * beat * 16).toInt())
    val chords = listOf(ints(53, 57, 60, 64), ints(52, 55, 59, 62), ints(50, 53, 57, 60), ints(55, 59, 62, 65))
    val roots = ints(41, 40, 38, 43)
    chords.forEachIndexed { bar, ch ->
        val t0 = bar * 4 * beat
        pluck(out, t0, midi(roots[bar]), 0.6, 0.994, 0.65, bar); pluck(out, t0 + 2 * beat, midi(roots[bar] + 7), 0.45, 0.994, 0.65, bar + 9)
        for (b in doubleArrayOf(0.0, 1.5, 3.0)) ch.forEachIndexed { k, n -> fm(out, t0 + b * beat + k * 0.01, beat * 1.6, midi(n), 0.08, 1.0, 2.2, 1.6) }
        for (e in 0 until 8) brush(out, t0 + e * beat * 0.5, if (e % 3 == 0) 0.06 else 0.03, bar * 13 + e, 0.08)
    }
    ints(72, 76, 74, 72, 71, 74, 72, 69).forEachIndexed { i, n -> fm(out, i * 2 * beat + beat, beat * 1.8, midi(n), 0.08, 3.5, 0.8, 1.2) }
    return finish(out, 0.5, 0.32, 0.25)
}

private fun firstDance(): FloatArray {
    val beat = 0.6
    val out = FloatArray(floor(SR * beat * 24).toInt())
    val prog = listOf(ints(48, 60, 64, 67), ints(45, 60, 64, 69), ints(41, 57, 60, 65), ints(43, 55, 59, 62), ints(48, 60, 64, 67), ints(52, 59, 64, 67), ints(41, 57, 60, 65), ints(43, 55, 62, 65))
    val mel = ints(76, 79, 81, 79, 77, 76, 74, 72, 76, 79, 77, 74)
    prog.forEachIndexed { bar, ch ->
        val t0 = bar * 3 * beat
        pluck(out, t0, midi(ch[0]), 0.5, 0.998, 0.4, bar)
        for (j in 1 until ch.size) tone(out, t0 + 0.05, 3 * beat, midi(ch[j]), 0.08, SAW, a = 0.4, r = 0.5, unison = 3, detune = 0.005, vib = 5.0, vibDepth = 0.003, bright = 0.15)
    }
    mel.forEachIndexed { i, n -> fm(out, i * 2 * beat, 2.4, midi(n), 0.14, 3.5, 1.1, 1.4) }
    return finish(out, 0.75, 0.42, 0.24)
}

/** Soft sine-sweep kick / timpani thump. */
private fun kick(out: FloatArray, at: Double, amp: Double, f0: Double = 110.0, f1: Double = 45.0, len: Double = 0.35) {
    val st = floor(at * SR).toInt(); var ph = 0.0
    for (i in 0 until (SR * len).toInt()) {
        val t = i.toDouble() / SR; val f = f1 + (f0 - f1) * exp(-t * 30); ph += 2 * PI * f / SR
        val idx = (st + i) % out.size; out[idx] = (out[idx] + sin(ph) * amp * exp(-t * 8)).toFloat()
    }
}

// ——— more styles ———

/** Lo-Fi Bedroom — dusty Rhodes sevenths over a lazy kick and brushed snare. */
private fun lofi(): FloatArray {
    val beat = 60.0 / 78; val out = FloatArray(floor(SR * beat * 16).toInt())
    val chords = listOf(ints(53, 57, 60, 64), ints(50, 53, 57, 60), ints(55, 58, 62, 65), ints(48, 52, 55, 59)); val roots = ints(41, 38, 43, 36)
    chords.forEachIndexed { bar, ch ->
        val t0 = bar * 4 * beat
        kick(out, t0, 0.5); kick(out, t0 + 2.5 * beat, 0.4)
        brush(out, t0 + beat, 0.09, bar * 7 + 1, 0.22); brush(out, t0 + 3 * beat, 0.09, bar * 7 + 2, 0.22)
        for (e in 0 until 8) brush(out, t0 + e * beat * 0.5 + (if (e % 2 != 0) beat * 0.18 else 0.0), 0.025, bar * 29 + e, 0.05)
        pluck(out, t0, midi(roots[bar]), 0.5, 0.994, 0.7, bar)
        ch.forEachIndexed { k, n -> fm(out, t0 + k * 0.015, beat * 3.6, midi(n), 0.07, 1.0, 1.5, 1.2) }
    }
    return finish(out, 0.35, 0.25, 0.24)
}

/** Blues Shuffle — a boogie bass, organ stabs and a wailing reed. */
private fun blues(): FloatArray {
    val beat = 60.0 / 96; val sw = beat * 2 / 3; val out = FloatArray(floor(SR * beat * 16).toInt())
    ints(40, 45, 40, 47).forEachIndexed { bar, r ->
        val t0 = bar * 4 * beat
        ints(0, 7, 9, 7).forEachIndexed { b, iv -> pluck(out, t0 + b * beat, midi(r + iv), 0.5, 0.992, 0.6, bar * 8 + b); pluck(out, t0 + b * beat + sw, midi(r + iv), 0.3, 0.992, 0.6, bar * 8 + b + 4) }
        for (b in ints(1, 3)) ints(r + 16, r + 19, r + 22).forEach { n -> tone(out, t0 + b * beat, beat * 0.5, midi(n), 0.06, ORGAN, a = 0.01, r = 0.08, vib = 6.5, vibDepth = 0.002) }
        for (b in 0 until 4) brush(out, t0 + b * beat + sw, 0.04, bar * 13 + b, 0.06)
    }
    line(arrayOf(nd(64, 1.5), nd(67, 0.5), nd(69, 1.0), nd(67, 1.0), nd(64, 2.0), nd(62, 1.0), nd(64, 1.0), nd(71, 1.5), nd(69, 0.5), nd(67, 2.0), nd(64, 4.0))) { t, n, d ->
        tone(out, t * beat, d * beat * 0.9, midi(n), 0.11, REED, a = 0.04, r = 0.12, vib = 5.0, vibDepth = 0.008, bright = 0.2)
    }
    return finish(out, 0.35, 0.22, 0.24)
}

/** Celtic Morning — a D-major jig in six-eight: tin whistle, harp arpeggios, a pipe drone and a soft bodhrán. */
private fun celtic(): FloatArray {
    val e = 0.2; val out = FloatArray(floor(SR * e * 48).toInt())
    val D = ints(50, 57, 62, 66); val G = ints(43, 55, 59, 62); val A = ints(45, 52, 57, 61)
    ints(38, 45).forEach { n -> tone(out, 0.0, 48 * e, midi(n), 0.035, REED, a = 0.6, r = 0.6, bright = 0.04) }
    listOf(D, D, G, D, D, G, A, D).forEachIndexed { bar, ch ->
        val t0 = bar * 6 * e
        ints(0, 1, 2, 3, 2, 1).forEachIndexed { i, k -> pluck(out, t0 + i * e, midi(ch[k]), if (i != 0) 0.11 else 0.15, 0.997, 0.4, bar * 6 + i) }
        kick(out, t0, 0.16, 120.0, 70.0, 0.25); kick(out, t0 + 3 * e, 0.09, 120.0, 70.0, 0.2)
        brush(out, t0 + 5 * e, 0.025, bar * 5 + 3, 0.05)
    }
    line(arrayOf(nd(78, 2.0), nd(76, 1.0), nd(74, 2.0), nd(76, 1.0), nd(78, 1.0), nd(81, 1.0), nd(78, 1.0), nd(76, 2.0), nd(74, 1.0), nd(79, 2.0), nd(78, 1.0), nd(76, 2.0), nd(74, 1.0), nd(78, 3.0), nd(74, 3.0),
        nd(78, 2.0), nd(76, 1.0), nd(74, 2.0), nd(76, 1.0), nd(79, 1.0), nd(83, 1.0), nd(81, 1.0), nd(79, 2.0), nd(78, 1.0), nd(76, 2.0), nd(74, 1.0), nd(73, 2.0), nd(76, 1.0), nd(74, 6.0))) { t, n, d ->
        tone(out, t * e, d * e * 0.88, midi(n), 0.08, WHISTLE, a = 0.015, r = 0.06, vib = 5.5, vibDepth = 0.004, bright = 0.6)
    }
    return finish(out, 0.5, 0.3, 0.24)
}

/** Cinematic Dawn — Dm–B♭–F–C: string swells, a low cello line, a rising horn motif, a bell and a timpani roll. */
private fun cinematic(): FloatArray {
    val out = bed(16.0)
    val prog = listOf(ints(57, 62, 65, 69), ints(58, 62, 65, 70), ints(57, 60, 65, 69), ints(55, 60, 64, 67)); val roots = ints(38, 34, 41, 36)
    prog.forEachIndexed { bar, ch ->
        val t0 = bar * 4.0
        ch.forEach { n -> tone(out, t0, 4.1, midi(n), 0.055, SAW, a = 1.4, r = 1.2, unison = 3, detune = 0.004, vib = 4.8, vibDepth = 0.002, bright = 0.06) }
        tone(out, t0, 4.1, midi(roots[bar]), 0.12, WARM, a = 0.8, r = 1.0, unison = 2, detune = 0.003, bright = 0.2)
        if (bar % 2 == 0) kick(out, t0, 0.3, 75.0, 60.0, 1.4)
        fm(out, t0 + 2, 3.0, midi(ch[3] + 12), 0.03, 2.0, 0.8, 1.2)
    }
    for (i in 0 until 8) kick(out, 15 + i * 0.12, 0.04 + i * 0.015, 75.0, 60.0, 0.6)
    line(arrayOf(nd(62, 2.0), nd(65, 1.0), nd(69, 1.0), nd(70, 3.0), nd(65, 1.0), nd(69, 2.0), nd(72, 2.0), nd(74, 2.0), nd(72, 2.0))) { t, n, d ->
        tone(out, t, d * 0.95, midi(n), 0.075, SAW, a = 0.25, r = 0.5, vib = 4.5, vibDepth = 0.003, bright = 0.04)
    }
    return finish(out, 0.85, 0.45, 0.24)
}

/** Sunday Soul — a 12/8 gospel ballad: Leslie organ, triplet electric piano, round bass, backbeat and ride. */
private fun soul(): FloatArray {
    val beat = 60.0 / 64; val trip = beat / 3; val out = FloatArray(floor(SR * beat * 16).toInt())
    val chords = listOf(ints(52, 55, 59, 64), ints(55, 60, 64, 67), ints(53, 57, 60, 65), ints(53, 57, 59, 65)); val roots = ints(36, 33, 38, 31)
    val bass = arrayOf(doubleArrayOf(0.0, 0.0), doubleArrayOf(5.0 / 3, 0.0), doubleArrayOf(2.0, 7.0), doubleArrayOf(3.0, 12.0), doubleArrayOf(11.0 / 3, 7.0))
    chords.forEachIndexed { bar, ch ->
        val t0 = bar * 4 * beat
        ch.forEach { n -> tone(out, t0, 4 * beat, midi(n), 0.035, ORGAN, a = 0.2, r = 0.3, vib = 6.5, vibDepth = 0.003) }
        for (i in 0 until 12) for (n in ch.drop(1)) fm(out, t0 + i * trip, 0.6, midi(n), if (i % 3 != 0) 0.018 else 0.03, 1.0, 1.0, 3.0)
        bass.forEachIndexed { k, (b, iv) -> pluck(out, t0 + b * beat, midi(roots[bar] + iv.toInt()), 0.5, 0.995, 0.3, bar * 5 + k) }
        kick(out, t0, 0.45); kick(out, t0 + (8.0 / 3) * beat, 0.3)
        for (b in ints(1, 3)) { brush(out, t0 + b * beat, 0.09, bar * 9 + b, 0.16); kick(out, t0 + b * beat, 0.1, 200.0, 160.0, 0.12) }
        for (i in 0 until 12) brush(out, t0 + i * trip, if (i % 3 != 0) 0.01 else 0.018, bar * 40 + i, 0.03)
    }
    return finish(out, 0.45, 0.28, 0.24)
}

/** Porch Country — G–C–D–G: alternating bass, a guitar "chick" with brushes, a banjo roll and a fiddle tune. */
private fun country(): FloatArray {
    val beat = 60.0 / 104; val out = FloatArray(floor(SR * beat * 16).toInt())
    val chords = listOf(ints(55, 59, 62, 67), ints(55, 60, 64, 67), ints(57, 62, 66, 69), ints(55, 59, 62, 67)); val roots = ints(43, 48, 50, 43)
    chords.forEachIndexed { bar, ch ->
        val t0 = bar * 4 * beat; val r = roots[bar]
        pluck(out, t0, midi(r), 0.5, 0.994, 0.35, bar * 4); pluck(out, t0 + 2 * beat, midi(r - 5), 0.45, 0.994, 0.35, bar * 4 + 1)
        for (b in ints(1, 3)) { ch.forEachIndexed { k, n -> pluck(out, t0 + b * beat + k * 0.01, midi(n), 0.07, 0.97, 0.7, bar * 20 + b * 4 + k) }; brush(out, t0 + b * beat, 0.06, bar * 7 + b, 0.1) }
        ints(0, 1, 3, 0, 1, 3, 0, 2).forEachIndexed { i, k -> pluck(out, t0 + i * beat * 0.5, midi(ch[k] + 12), 0.09, 0.975, 0.85, bar * 16 + i + 100) }
    }
    line(arrayOf(nd(71, 1.0), nd(74, 1.0), nd(76, 1.0), nd(74, 1.0), nd(76, 2.0), nd(79, 1.0), nd(76, 1.0), nd(74, 1.5), nd(72, 0.5), nd(69, 1.0), nd(66, 1.0), nd(67, 4.0))) { t, n, d ->
        tone(out, t * beat, d * beat * 0.92, midi(n), 0.07, SAW, a = 0.06, r = 0.15, vib = 5.6, vibDepth = 0.005, bright = 0.12)
    }
    return finish(out, 0.28, 0.18, 0.24)
}

private val MAKERS: Map<String, () -> FloatArray> = mapOf(
    "serenade" to ::serenade, "guitar" to ::guitar, "candlelight" to ::candlelight, "ballad" to ::ballad,
    "sweetheart" to ::sweetheart, "harp" to ::harp, "firstdance" to ::firstDance, "hearth" to ::hearth,
    "ballroom" to ::ballroom, "foxtrot" to ::foxtrot, "pad" to ::parlour, "musicbox" to ::musicBox,
    "jazz" to ::jazz, "waltz" to ::waltz, "piano" to ::piano, "organ" to ::organ,
    "lofi" to ::lofi, "blues" to ::blues, "celtic" to ::celtic, "cinematic" to ::cinematic, "soul" to ::soul, "country" to ::country,
)

val MUSIC: List<MusicBed> = listOf(
    MusicBed("none", "No background", "Just the voice and the wax."),
    MusicBed("serenade", "Moonlight Serenade", "Lush strings and a harp under the stars.", true),
    MusicBed("guitar", "Spanish Guitar", "Nylon strings, fingerpicked, close and tender.", true),
    MusicBed("candlelight", "Candlelight Bossa", "A slow electric-piano bossa for two.", true),
    MusicBed("ballad", "Love Letter Ballad", "Tender piano with a cello line beneath.", true),
    MusicBed("sweetheart", "Sweetheart Swing", "A 1940s slow dance: clarinet, bass and brushes.", true),
    MusicBed("harp", "Tender Harp", "A solo harp, slow and loving.", true),
    MusicBed("firstdance", "Our First Dance", "A slow waltz — strings, harp and celesta.", true),
    MusicBed("hearth", "Fireside Warmth", "Round, warm chords and a soft plucked bass."),
    MusicBed("ballroom", "Golden Ballroom", "A sweeping vintage string orchestra in three."),
    MusicBed("foxtrot", "Gramophone Foxtrot", "A 1920s dance band: tuba, banjo and muted horn."),
    MusicBed("pad", "Parlour Strings", "A soft, slow string bed."),
    MusicBed("musicbox", "Music Box", "A soft, mellow music box with a warm hush."),
    MusicBed("jazz", "Late-Night Jazz", "Upright bass, brushes, a smoky Rhodes."),
    MusicBed("waltz", "Parisian Waltz", "A musette accordion in three-quarter time."),
    MusicBed("piano", "Parlour Piano", "Gentle broken chords by the window."),
    MusicBed("organ", "Chapel Organ", "Slow, reverent chords in a stone hall."),
    MusicBed("lofi", "Lo-Fi Bedroom", "Dusty Rhodes chords over a lazy beat."),
    MusicBed("blues", "Blues Shuffle", "Boogie bass, organ stabs and a wailing reed."),
    MusicBed("celtic", "Celtic Morning", "Tin whistle and harp in a lilting six-eight."),
    MusicBed("cinematic", "Cinematic Dawn", "Rising horns over swelling strings and timpani."),
    MusicBed("soul", "Sunday Soul", "A 12/8 gospel ballad: organ, keys and backbeat."),
    MusicBed("country", "Porch Country", "A banjo roll, boom-chick bass and a lazy fiddle."),
)

private val cache = HashMap<String, FloatArray?>()

/** Generated bed for [id] (cached; null for "none"/unknown). Thread-safe; first call per id is CPU-heavy — call off the main thread. */
fun musicBed(id: String): FloatArray? = synchronized(cache) {
    if (!cache.containsKey(id)) cache[id] = MAKERS[id]?.invoke()
    cache[id]
}
