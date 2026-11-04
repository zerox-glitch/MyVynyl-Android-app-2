import { SR } from './dsp'

/**
 * Device-generated background beds. Each one uses a different instrument model
 * (wavetable strings/reeds/organ, FM keys and bells, Karplus–Strong plucks, noise brushes)
 * plus its own key, tempo and room so they sound genuinely distinct.
 */
const midi = (n: number) => 440 * Math.pow(2, (n - 69) / 12)
const TABLE = 2048

const table = (partials: number[]) => {
  const t = new Float32Array(TABLE)
  for (let i = 0; i < TABLE; i++) { let s = 0; for (let k = 0; k < partials.length; k++) s += partials[k] * Math.sin((2 * Math.PI * (k + 1) * i) / TABLE); t[i] = s }
  let m = 1e-6; for (const v of t) m = Math.max(m, Math.abs(v)); for (let i = 0; i < TABLE; i++) t[i] /= m
  return t
}
const SAW = table(Array.from({ length: 14 }, (_, k) => 1 / (k + 1)))
const REED = table([1, 0.08, 0.55, 0.06, 0.38, 0.05, 0.25, 0.04, 0.16, 0.03, 0.1])
const ORGAN = table([1, 0.9, 0.3, 0.55, 0, 0.2, 0, 0.3])
const WARM = table([1, 0.35, 0.12, 0.05])
const WHISTLE = table([1, 0.12, 0.05, 0.02])

type Env = { a: number; d?: number; r?: number }
/** Wavetable voice with optional detuned unison (chorus) and vibrato. */
function tone(out: Float32Array, at: number, dur: number, f: number, amp: number, tab: Float32Array, env: Env, opt: { unison?: number; detune?: number; vib?: number; vibDepth?: number; bright?: number } = {}) {
  const st = Math.floor(at * SR), len = Math.floor(dur * SR), rel = Math.floor((env.r ?? 0.15) * SR)
  const n = opt.unison ?? 1, det = opt.detune ?? 0.004
  let lp = 0; const k = opt.bright ?? 1
  for (let u = 0; u < n; u++) {
    const fu = f * (1 + (n > 1 ? (u / (n - 1) - 0.5) * det * 2 : 0)), inc = (fu * TABLE) / SR
    let ph = (u * 0.37 * TABLE) % TABLE
    for (let i = 0; i < len + rel; i++) {
      const t = i / SR
      let e = Math.min(1, t / env.a) * (env.d ? Math.exp(-t * env.d) : 1)
      if (i > len) e *= 1 - (i - len) / rel
      const v = opt.vib ? 1 + (opt.vibDepth ?? 0.005) * Math.sin(2 * Math.PI * opt.vib * t + u) : 1
      ph += inc * v; if (ph >= TABLE) ph -= TABLE
      const s = tab[ph | 0]
      lp += (s - lp) * k
      out[(st + i) % out.length] += lp * amp * e / n
    }
  }
}

/** Two-operator FM — electric piano, celesta, music box. */
function fm(out: Float32Array, at: number, dur: number, f: number, amp: number, ratio: number, index: number, decay: number) {
  const st = Math.floor(at * SR), len = Math.floor(dur * SR)
  for (let i = 0; i < len; i++) {
    const t = i / SR, e = Math.min(1, t / 0.003) * Math.exp(-t * decay)
    out[(st + i) % out.length] += Math.sin(2 * Math.PI * f * t + index * Math.exp(-t * decay * 1.6) * Math.sin(2 * Math.PI * f * ratio * t)) * amp * e
  }
}

/** Karplus–Strong plucked string — nylon guitar, harp, upright bass. */
function pluck(out: Float32Array, at: number, f: number, amp: number, damp = 0.996, bright = 0.5, seed = 1) {
  const st = Math.floor(at * SR), N = Math.max(2, Math.round(SR / f)), buf = new Float32Array(N)
  let x = seed * 9301 + 49297
  for (let i = 0; i < N; i++) { x = (x * 1103515245 + 12345) & 0x7fffffff; buf[i] = (x / 0x3fffffff - 1) }
  for (let p = 0; p < 2; p++) for (let i = 0; i < N; i++) buf[i] = buf[i] * (1 - bright) + buf[(i + 1) % N] * bright
  const len = Math.min(SR * 4, out.length)
  let idx = 0
  for (let i = 0; i < len; i++) {
    const nx = (idx + 1) % N, y = damp * 0.5 * (buf[idx] + buf[nx])
    out[(st + i) % out.length] += buf[idx] * amp * Math.min(1, i / 40)
    buf[idx] = y; idx = nx
  }
}

function brush(out: Float32Array, at: number, amp: number, seed: number, len = 0.12) {
  let x = seed * 7919 + 1, lp = 0
  const st = Math.floor(at * SR)
  for (let i = 0; i < SR * len; i++) {
    x = (x * 1103515245 + 12345) & 0x7fffffff
    const w = x / 0x3fffffff - 1; lp += (w - lp) * 0.3
    out[(st + i) % out.length] += (w - lp) * amp * Math.exp(-i / (SR * len * 0.3))
  }
}

/** Soft sine-sweep kick / timpani thump. */
function kick(out: Float32Array, at: number, amp: number, f0 = 110, f1 = 45, len = 0.35) {
  const st = Math.floor(at * SR); let ph = 0
  for (let i = 0; i < SR * len; i++) {
    const t = i / SR, f = f1 + (f0 - f1) * Math.exp(-t * 30); ph += (2 * Math.PI * f) / SR
    out[(st + i) % out.length] += Math.sin(ph) * amp * Math.exp(-t * 8)
  }
}

/** All note writes wrap modulo the bed length, so ringing tails fold into the start and every bed loops seamlessly. */

/** Small Schroeder reverb: 4 combs + 2 allpasses. `size` 0..1, `mix` wet amount. Loop-wrapped so beds stay seamless. */
function room(dry: Float32Array, size: number, mix: number) {
  const n = dry.length, wet = new Float32Array(n)
  const combs = [1557, 1617, 1491, 1422].map((d) => Math.floor(d * (0.6 + size * 0.9)))
  const fb = 0.72 + size * 0.2
  for (const d of combs) {
    const line = new Float32Array(d); let j = 0, lp = 0
    for (let pass = 0; pass < 2; pass++) for (let i = 0; i < n; i++) {
      const y = line[j]; lp = y * 0.7 + lp * 0.3; line[j] = dry[i] + lp * fb; j = (j + 1) % d
      if (pass) wet[i] += y / combs.length
    }
  }
  for (const d of [225, 556]) {
    const line = new Float32Array(d); let j = 0
    for (let i = 0; i < n; i++) { const b = line[j], y = -wet[i] + b; line[j] = wet[i] + b * 0.5; wet[i] = y; j = (j + 1) % d }
  }
  for (let i = 0; i < n; i++) dry[i] = dry[i] * (1 - mix * 0.5) + wet[i] * mix
  return dry
}

const finish = (a: Float32Array, size: number, mix: number, peak = 0.26) => {
  room(a, size, mix)
  // remove DC / sub-rumble (plucked strings start from non-zero-mean noise); two passes so the filter state wraps the loop
  let x1 = 0, y1 = 0
  const R = 1 - (2 * Math.PI * 25) / SR
  for (let pass = 0; pass < 2; pass++) for (let i = 0; i < a.length; i++) { const y = a[i] - x1 + R * y1; x1 = a[i]; y1 = y; if (pass) a[i] = y }
  // close any residual jump at the loop point with a 60 ms ramp
  const fadeN = Math.floor(SR * 0.06), jump = a[0] - a[a.length - 1]
  for (let i = 0; i < fadeN; i++) a[a.length - fadeN + i] += jump * (i / fadeN)
  let m = 1e-6; for (const v of a) m = Math.max(m, Math.abs(v))
  for (let i = 0; i < a.length; i++) a[i] *= peak / m
  return a
}

// ——— beds ———

/** Music box — pitched down into a soft, mellow register: gentle chime tines over a quiet warm pad. */
function musicBox() {
  const out = new Float32Array(SR * 9.6), seq = [72, 76, 79, 84, 79, 76, 74, 77, 81, 86, 81, 77, 72, 76, 79, 83, 79, 76, 71, 74, 79, 83, 79, 74]
  seq.forEach((n, i) => {
    const t = i * 0.4 + (i % 2) * 0.012
    fm(out, t, 2.6, midi(n - 12), 0.26, 3, 0.45, 1.8)
    fm(out, t, 1.2, midi(n), 0.05, 2, 0.25, 3.2)
  })
  ;[[48, 55, 64], [45, 52, 60], [41, 48, 57], [43, 50, 59]].forEach((ch, i) => ch.forEach((n) => tone(out, i * 2.4, 2.5, midi(n), 0.05, WARM, { a: 0.6, r: 0.6 }, { bright: 0.1 })))
  return finish(out, 0.45, 0.35, 0.2)
}

/** Fireside — warm, round low-register chords and a gentle plucked bass. */
function hearth() {
  const out = new Float32Array(SR * 12.8), prog = [[41, 53, 57, 60, 64], [38, 50, 57, 60, 65], [46, 50, 58, 62, 65], [36, 52, 55, 60, 64]]
  prog.forEach((ch, bar) => {
    const t0 = bar * 3.2
    pluck(out, t0, midi(ch[0]), 0.5, 0.997, 0.3, bar); pluck(out, t0 + 1.6, midi(ch[0] + 7), 0.35, 0.997, 0.3, bar + 7)
    ch.slice(1).forEach((n) => tone(out, t0, 3.3, midi(n), 0.1, WARM, { a: 0.5, r: 0.7 }, { unison: 2, detune: 0.003, vib: 4.2, vibDepth: 0.0015, bright: 0.06 }))
  })
  return finish(out, 0.55, 0.35, 0.23)
}

/** Love Letter — a tender piano ballad with a cello line underneath. */
function ballad() {
  const out = new Float32Array(SR * 12.8), prog = [[43, 55, 59, 62], [40, 52, 55, 59], [36, 52, 55, 60], [38, 50, 54, 57]]
  const mel = [71, 74, 72, 71, 69, 67, 69, 71, 72, 71, 69, 66]
  prog.forEach((ch, bar) => {
    const t0 = bar * 3.2
    tone(out, t0, 3.2, midi(ch[0]), 0.16, SAW, { a: 0.4, r: 0.6 }, { unison: 2, detune: 0.003, vib: 5, vibDepth: 0.004, bright: 0.07 })
    ;[1, 2, 3, 2, 1, 2].forEach((k, i) => tone(out, t0 + i * 0.53, 0.3, midi(ch[k]), 0.13, WARM, { a: 0.004, d: 1.4, r: 1.6 }))
  })
  mel.forEach((n, i) => { tone(out, i * 1.066, 0.4, midi(n), 0.2, WARM, { a: 0.004, d: 1.1, r: 1.8 }); fm(out, i * 1.066, 2, midi(n), 0.04, 2, 0.5, 2.5) })
  return finish(out, 0.6, 0.38, 0.24)
}

/** Sweetheart Swing — a 1940s slow-dance clarinet over bass and brushes. */
function sweetheart() {
  const beat = 60 / 80, out = new Float32Array(Math.floor(SR * beat * 16))
  const chords = [[55, 59, 62, 65], [52, 55, 59, 62], [57, 60, 64, 67], [50, 54, 57, 60]], roots = [43, 40, 45, 38]
  chords.forEach((ch, bar) => {
    const t0 = bar * 4 * beat
    ;[0, 7, 12, 7].forEach((iv, b) => pluck(out, t0 + b * beat, midi(roots[bar] + iv), 0.55, 0.993, 0.55, bar * 4 + b))
    for (const b of [1, 3]) ch.forEach((n, k) => fm(out, t0 + b * beat + k * 0.01, beat * 0.9, midi(n), 0.06, 1, 1.4, 3))
    for (let e = 0; e < 8; e++) brush(out, t0 + e * beat * 0.5 + (e % 2 ? beat * 0.16 : 0), e % 2 ? 0.035 : 0.06, bar * 17 + e, 0.1)
  })
  ;[[74, 2], [72, 1], [71, 1], [69, 2], [67, 2], [71, 1.5], [72, 0.5], [74, 2], [76, 2], [74, 2]].reduce((t, [n, d]) => {
    tone(out, t * beat, d * beat * 0.95, midi(n), 0.15, REED, { a: 0.05, r: 0.15 }, { vib: 5.5, vibDepth: 0.005, bright: 0.25 }); return t + d
  }, 0)
  return finish(out, 0.45, 0.3, 0.24)
}

/** Gramophone Foxtrot — a 1920s dance band: oom-pah tuba, banjo and a muted horn. */
function foxtrot() {
  const beat = 60 / 116, out = new Float32Array(Math.floor(SR * beat * 16))
  const chords = [[60, 64, 67], [57, 60, 64], [62, 65, 69], [55, 59, 62]], roots = [36, 33, 38, 31]
  chords.forEach((ch, bar) => {
    const t0 = bar * 4 * beat
    ;[0, 7, 0, 7].forEach((iv, b) => tone(out, t0 + b * beat, beat * 0.55, midi(roots[bar] + iv), 0.3, REED, { a: 0.015, r: 0.08 }, { bright: 0.12 }))
    for (let b = 0; b < 4; b++) ch.forEach((n, k) => pluck(out, t0 + b * beat + 0.5 * beat + k * 0.006, midi(n), 0.14, 0.985, 0.7, bar * 40 + b * 4 + k))
  })
  ;[[76, 1], [74, 0.5], [72, 0.5], [69, 2], [72, 1], [74, 1], [77, 2], [76, 1], [74, 1], [71, 2], [67, 4]].reduce((t, [n, d]) => {
    tone(out, t * beat, d * beat * 0.85, midi(n), 0.12, SAW, { a: 0.03, r: 0.1 }, { vib: 6, vibDepth: 0.004, bright: 0.1 }); return t + d
  }, 0)
  return finish(out, 0.3, 0.2, 0.24)
}

/** Golden Ballroom — a sweeping vintage string orchestra in slow three. */
function ballroom() {
  const beat = 0.7, out = new Float32Array(Math.floor(SR * beat * 24))
  const prog = [[50, 62, 66, 69], [47, 62, 66, 71], [43, 59, 62, 67], [45, 57, 61, 64], [50, 62, 66, 69], [55, 59, 62, 67], [45, 61, 64, 67], [50, 62, 66, 69]]
  prog.forEach((ch, bar) => {
    const t0 = bar * 3 * beat
    tone(out, t0, beat * 1.2, midi(ch[0] - 12), 0.14, SAW, { a: 0.05, r: 0.3 }, { unison: 2, detune: 0.004, bright: 0.08 })
    for (const b of [1, 2]) ch.slice(1).forEach((n) => tone(out, t0 + b * beat, beat * 0.7, midi(n), 0.05, SAW, { a: 0.05, r: 0.2 }, { unison: 2, detune: 0.005, bright: 0.12 }))
  })
  ;[78, 76, 74, 73, 74, 71, 69, 73].forEach((n, i) => tone(out, i * 3 * beat, 3 * beat, midi(n), 0.11, SAW, { a: 0.5, r: 0.5 }, { unison: 3, detune: 0.004, vib: 5.4, vibDepth: 0.006, bright: 0.12 }))
  return finish(out, 0.8, 0.45, 0.24)
}

/** Tender Harp — a solo harp, slow and unhurried. */
function harp() {
  const out = new Float32Array(SR * 12.8), prog = [[48, 55, 60, 64, 67, 72], [45, 52, 57, 60, 64, 69], [41, 48, 53, 57, 60, 65], [43, 50, 55, 59, 62, 67]]
  prog.forEach((ch, bar) => {
    const up = [...ch, ch[4], ch[3], ch[2], ch[1]]
    up.forEach((n, i) => pluck(out, bar * 3.2 + i * 0.32, midi(n), i ? 0.26 : 0.4, 0.9985, 0.3, bar * 20 + i))
  })
  return finish(out, 0.7, 0.4, 0.24)
}

function jazz() {
  const beat = 60 / 92, out = new Float32Array(Math.floor(SR * beat * 16))
  const chords = [[62, 65, 69, 72], [59, 62, 65, 69], [60, 64, 67, 71], [61, 64, 67, 70]]
  const walk = [[38, 41, 45, 44], [43, 47, 50, 49], [36, 40, 43, 44], [45, 49, 52, 51]]
  chords.forEach((ch, bar) => {
    walk[bar].forEach((n, b) => pluck(out, (bar * 4 + b) * beat, midi(n), 0.7, 0.993, 0.6, bar * 4 + b))
    for (const b of [1.5, 3]) ch.forEach((n, k) => fm(out, (bar * 4 + b) * beat + k * 0.008, beat * 1.4, midi(n), 0.09, 1, 1.8, 2.6))
    for (let e = 0; e < 8; e++) brush(out, (bar * 4 + e / 2) * beat + (e % 2 ? beat * 0.17 : 0), e % 2 ? 0.05 : 0.1, bar * 31 + e)
    brush(out, (bar * 4 + 1) * beat, 0.07, bar + 99, 0.25); brush(out, (bar * 4 + 3) * beat, 0.07, bar + 199, 0.25)
  })
  return finish(out, 0.4, 0.22)
}

function waltz() {
  const beat = 0.4, out = new Float32Array(Math.floor(SR * beat * 24))
  const bass = [45, 40, 45, 40, 50, 45, 40, 45], chord = [[60, 64, 69], [59, 64, 68], [60, 64, 69], [59, 62, 68], [62, 65, 69], [60, 64, 69], [59, 62, 68], [60, 64, 69]]
  const mel = [76, 77, 76, 74, 72, 71, 72, 74, 76, 81, 80, 76]
  bass.forEach((b, bar) => {
    tone(out, bar * 3 * beat, beat * 0.7, midi(b), 0.32, REED, { a: 0.02, r: 0.08 }, { unison: 2, detune: 0.006 })
    for (const k of [1, 2]) chord[bar].forEach((n) => tone(out, (bar * 3 + k) * beat, beat * 0.45, midi(n), 0.09, REED, { a: 0.015, r: 0.06 }, { unison: 2, detune: 0.006 }))
  })
  // musette: two reeds beating against each other
  mel.forEach((n, i) => tone(out, i * 2 * beat, beat * 1.8, midi(n), 0.2, REED, { a: 0.04, r: 0.1 }, { unison: 2, detune: 0.008, vib: 5.2, vibDepth: 0.003 }))
  return finish(out, 0.3, 0.18)
}

function piano() {
  const out = new Float32Array(SR * 9.6), prog = [[48, 55, 64, 67], [45, 52, 60, 64], [41, 48, 57, 60], [43, 50, 59, 62]]
  prog.forEach((ch, bar) => {
    const pat = [ch[0], ch[1], ch[2], ch[3], ch[2], ch[1], ch[2], ch[3]]
    pat.forEach((n, i) => { tone(out, bar * 2.4 + i * 0.3, 0.2, midi(n), i === 0 ? 0.34 : 0.22, WARM, { a: 0.004, d: 1.6, r: 2 }); fm(out, bar * 2.4 + i * 0.3, 2, midi(n), 0.05, 2, 0.6, 3) })
  })
  return finish(out, 0.5, 0.3, 0.24)
}

function organ() {
  const out = new Float32Array(SR * 12), chords = [[36, 48, 55, 60, 64], [33, 45, 52, 57, 60], [29, 45, 53, 57, 60], [31, 43, 50, 55, 59]]
  chords.forEach((ch, i) => ch.forEach((n) => tone(out, i * 3, 3, midi(n), 0.14, ORGAN, { a: 0.25, r: 0.6 }, { vib: 6.5, vibDepth: 0.002 })))
  return finish(out, 1, 0.55, 0.22)
}

function parlour() {
  const out = new Float32Array(SR * 12), prog = [[48, 55, 64, 67], [43, 55, 62, 67], [45, 57, 60, 64], [41, 53, 60, 65]]
  prog.forEach((ch, i) => ch.forEach((n, k) => tone(out, i * 3, 3.1, midi(n), k ? 0.1 : 0.14, SAW, { a: 0.9, r: 0.9 }, { unison: 3, detune: 0.004, vib: 4.6, vibDepth: 0.002, bright: 0.08 })))
  return finish(out, 0.6, 0.38, 0.22)
}

// ——— romantic ———

/** Moonlight Serenade — lush detuned strings + harp arpeggios in E♭. */
function serenade() {
  const out = new Float32Array(SR * 12.8), prog = [[51, 58, 63, 67], [48, 55, 63, 67], [44, 56, 60, 63], [46, 53, 58, 62]]
  prog.forEach((ch, bar) => {
    ch.forEach((n) => tone(out, bar * 3.2, 3.3, midi(n), 0.12, SAW, { a: 0.6, r: 0.8 }, { unison: 3, detune: 0.006, vib: 5, vibDepth: 0.003, bright: 0.18 }))
    const arp = [ch[1] + 12, ch[2] + 12, ch[3] + 12, ch[2] + 24, ch[3] + 12, ch[2] + 12]
    arp.forEach((n, i) => pluck(out, bar * 3.2 + 0.1 + i * 0.27, midi(n), 0.22, 0.9985, 0.35, bar * 10 + i))
  })
  // a soft violin line on top
  ;[79, 77, 75, 74, 75, 72, 70, 74].forEach((n, i) => tone(out, i * 1.6, 1.55, midi(n), 0.1, SAW, { a: 0.35, r: 0.4 }, { unison: 2, detune: 0.003, vib: 5.8, vibDepth: 0.006, bright: 0.12 }))
  return finish(out, 0.85, 0.5, 0.24)
}

/** Spanish Guitar — nylon fingerpicking, A minor, intimate room. */
function guitar() {
  const out = new Float32Array(SR * 9.6), prog = [[45, 57, 60, 64], [41, 57, 60, 65], [43, 55, 59, 62], [40, 56, 59, 64]]
  prog.forEach((ch, bar) => {
    const t0 = bar * 2.4, pat = [0, 2, 1, 3, 2, 1, 3, 2]
    pluck(out, t0, midi(ch[0]), 0.55, 0.9975, 0.45, bar)
    pat.forEach((k, i) => pluck(out, t0 + 0.3 * i + (i % 2 ? 0.02 : 0), midi(ch[k]), 0.3, 0.997, 0.5, bar * 8 + i + 3))
  })
  return finish(out, 0.25, 0.2, 0.26)
}

/** Candlelight — slow bossa on electric piano with brushed rim. */
function candlelight() {
  const beat = 60 / 72, out = new Float32Array(Math.floor(SR * beat * 16))
  const chords = [[53, 57, 60, 64], [52, 55, 59, 62], [50, 53, 57, 60], [55, 59, 62, 65]], roots = [41, 40, 38, 43]
  chords.forEach((ch, bar) => {
    const t0 = bar * 4 * beat
    pluck(out, t0, midi(roots[bar]), 0.6, 0.994, 0.65, bar); pluck(out, t0 + 2 * beat, midi(roots[bar] + 7), 0.45, 0.994, 0.65, bar + 9)
    for (const b of [0, 1.5, 3]) ch.forEach((n, k) => fm(out, t0 + b * beat + k * 0.01, beat * 1.6, midi(n), 0.08, 1, 2.2, 1.6))
    for (let e = 0; e < 8; e++) brush(out, t0 + e * beat * 0.5, e % 3 === 0 ? 0.06 : 0.03, bar * 13 + e, 0.08)
  })
  ;[72, 76, 74, 72, 71, 74, 72, 69].forEach((n, i) => fm(out, i * 2 * beat + beat, beat * 1.8, midi(n), 0.08, 3.5, 0.8, 1.2))
  return finish(out, 0.5, 0.32, 0.25)
}

/** First Dance — slow romantic waltz: strings, celesta melody, harp on the downbeat. */
function firstDance() {
  const beat = 0.6, out = new Float32Array(Math.floor(SR * beat * 24))
  const prog = [[48, 60, 64, 67], [45, 60, 64, 69], [41, 57, 60, 65], [43, 55, 59, 62], [48, 60, 64, 67], [52, 59, 64, 67], [41, 57, 60, 65], [43, 55, 62, 65]]
  const mel = [76, 79, 81, 79, 77, 76, 74, 72, 76, 79, 77, 74]
  prog.forEach((ch, bar) => {
    const t0 = bar * 3 * beat
    pluck(out, t0, midi(ch[0]), 0.5, 0.998, 0.4, bar)
    ch.slice(1).forEach((n) => tone(out, t0 + 0.05, 3 * beat, midi(n), 0.08, SAW, { a: 0.4, r: 0.5 }, { unison: 3, detune: 0.005, vib: 5, vibDepth: 0.003, bright: 0.15 }))
  })
  mel.forEach((n, i) => fm(out, i * 2 * beat, 2.4, midi(n), 0.14, 3.5, 1.1, 1.4))
  return finish(out, 0.75, 0.42, 0.24)
}

// ——— more styles ———

/** Lo-Fi Bedroom — dusty Rhodes sevenths over a lazy kick and brushed snare. */
function lofi() {
  const beat = 60 / 78, out = new Float32Array(Math.floor(SR * beat * 16))
  const chords = [[53, 57, 60, 64], [50, 53, 57, 60], [55, 58, 62, 65], [48, 52, 55, 59]], roots = [41, 38, 43, 36]
  chords.forEach((ch, bar) => {
    const t0 = bar * 4 * beat
    kick(out, t0, 0.5); kick(out, t0 + 2.5 * beat, 0.4)
    brush(out, t0 + beat, 0.09, bar * 7 + 1, 0.22); brush(out, t0 + 3 * beat, 0.09, bar * 7 + 2, 0.22)
    for (let e = 0; e < 8; e++) brush(out, t0 + e * beat * 0.5 + (e % 2 ? beat * 0.18 : 0), 0.025, bar * 29 + e, 0.05)
    pluck(out, t0, midi(roots[bar]), 0.5, 0.994, 0.7, bar)
    ch.forEach((n, k) => fm(out, t0 + k * 0.015, beat * 3.6, midi(n), 0.07, 1, 1.5, 1.2))
  })
  return finish(out, 0.35, 0.25, 0.24)
}

/** Blues Shuffle — a boogie bass, organ stabs and a wailing reed. */
function blues() {
  const beat = 60 / 96, sw = beat * 2 / 3, out = new Float32Array(Math.floor(SR * beat * 16))
  const roots = [40, 45, 40, 47]
  roots.forEach((r, bar) => {
    const t0 = bar * 4 * beat
    ;[0, 7, 9, 7].forEach((iv, b) => { pluck(out, t0 + b * beat, midi(r + iv), 0.5, 0.992, 0.6, bar * 8 + b); pluck(out, t0 + b * beat + sw, midi(r + iv), 0.3, 0.992, 0.6, bar * 8 + b + 4) })
    for (const b of [1, 3]) [r + 16, r + 19, r + 22].forEach((n) => tone(out, t0 + b * beat, beat * 0.5, midi(n), 0.06, ORGAN, { a: 0.01, r: 0.08 }, { vib: 6.5, vibDepth: 0.002 }))
    for (let b = 0; b < 4; b++) brush(out, t0 + b * beat + sw, 0.04, bar * 13 + b, 0.06)
  })
  ;[[64, 1.5], [67, 0.5], [69, 1], [67, 1], [64, 2], [62, 1], [64, 1], [71, 1.5], [69, 0.5], [67, 2], [64, 4]].reduce((t, [n, d]) => {
    tone(out, t * beat, d * beat * 0.9, midi(n), 0.11, REED, { a: 0.04, r: 0.12 }, { vib: 5, vibDepth: 0.008, bright: 0.2 }); return t + d
  }, 0)
  return finish(out, 0.35, 0.22, 0.24)
}

/** Celtic Morning — a D-major jig in six-eight: tin whistle, harp arpeggios, a pipe drone and a soft bodhrán. */
function celtic() {
  const e = 0.2, out = new Float32Array(Math.floor(SR * e * 48))
  const D = [50, 57, 62, 66], G = [43, 55, 59, 62], A = [45, 52, 57, 61]
  ;[38, 45].forEach((n) => tone(out, 0, 48 * e, midi(n), 0.035, REED, { a: 0.6, r: 0.6 }, { bright: 0.04 }))
  ;[D, D, G, D, D, G, A, D].forEach((ch, bar) => {
    const t0 = bar * 6 * e
    ;[0, 1, 2, 3, 2, 1].forEach((k, i) => pluck(out, t0 + i * e, midi(ch[k]), i ? 0.11 : 0.15, 0.997, 0.4, bar * 6 + i))
    kick(out, t0, 0.16, 120, 70, 0.25); kick(out, t0 + 3 * e, 0.09, 120, 70, 0.2)
    brush(out, t0 + 5 * e, 0.025, bar * 5 + 3, 0.05)
  })
  ;[
    [78, 2], [76, 1], [74, 2], [76, 1], [78, 1], [81, 1], [78, 1], [76, 2], [74, 1], [79, 2], [78, 1], [76, 2], [74, 1], [78, 3], [74, 3],
    [78, 2], [76, 1], [74, 2], [76, 1], [79, 1], [83, 1], [81, 1], [79, 2], [78, 1], [76, 2], [74, 1], [73, 2], [76, 1], [74, 6],
  ].reduce((t, [n, d]) => {
    tone(out, t * e, d * e * 0.88, midi(n), 0.08, WHISTLE, { a: 0.015, r: 0.06 }, { vib: 5.5, vibDepth: 0.004, bright: 0.6 }); return t + d
  }, 0)
  return finish(out, 0.5, 0.3, 0.24)
}

/** Cinematic Dawn — Dm–B♭–F–C: string swells, a low cello line, a rising horn motif, a bell and a timpani roll. */
function cinematic() {
  const out = new Float32Array(SR * 16)
  const prog = [[57, 62, 65, 69], [58, 62, 65, 70], [57, 60, 65, 69], [55, 60, 64, 67]], roots = [38, 34, 41, 36]
  prog.forEach((ch, bar) => {
    const t0 = bar * 4
    ch.forEach((n) => tone(out, t0, 4.1, midi(n), 0.055, SAW, { a: 1.4, r: 1.2 }, { unison: 3, detune: 0.004, vib: 4.8, vibDepth: 0.002, bright: 0.06 }))
    tone(out, t0, 4.1, midi(roots[bar]), 0.12, WARM, { a: 0.8, r: 1.0 }, { unison: 2, detune: 0.003, bright: 0.2 })
    if (bar % 2 === 0) kick(out, t0, 0.3, 75, 60, 1.4)
    fm(out, t0 + 2, 3.0, midi(ch[3] + 12), 0.03, 2.0, 0.8, 1.2)
  })
  for (let i = 0; i < 8; i++) kick(out, 15 + i * 0.12, 0.04 + i * 0.015, 75, 60, 0.6)
  ;[[62, 2], [65, 1], [69, 1], [70, 3], [65, 1], [69, 2], [72, 2], [74, 2], [72, 2]].reduce((t, [n, d]) => {
    tone(out, t, d * 0.95, midi(n), 0.075, SAW, { a: 0.25, r: 0.5 }, { vib: 4.5, vibDepth: 0.003, bright: 0.04 }); return t + d
  }, 0)
  return finish(out, 0.85, 0.45, 0.24)
}

/** Sunday Soul — a 12/8 gospel ballad: Leslie organ, triplet electric piano, round bass, backbeat and ride. */
function soul() {
  const beat = 60 / 64, trip = beat / 3, out = new Float32Array(Math.floor(SR * beat * 16))
  const chords = [[52, 55, 59, 64], [55, 60, 64, 67], [53, 57, 60, 65], [53, 57, 59, 65]], roots = [36, 33, 38, 31]
  const bass: [number, number][] = [[0, 0], [5 / 3, 0], [2, 7], [3, 12], [11 / 3, 7]]
  chords.forEach((ch, bar) => {
    const t0 = bar * 4 * beat
    ch.forEach((n) => tone(out, t0, 4 * beat, midi(n), 0.035, ORGAN, { a: 0.2, r: 0.3 }, { vib: 6.5, vibDepth: 0.003 }))
    for (let i = 0; i < 12; i++) ch.slice(1).forEach((n) => fm(out, t0 + i * trip, 0.6, midi(n), i % 3 ? 0.018 : 0.03, 1, 1, 3))
    bass.forEach(([b, iv], k) => pluck(out, t0 + b * beat, midi(roots[bar] + iv), 0.5, 0.995, 0.3, bar * 5 + k))
    kick(out, t0, 0.45); kick(out, t0 + (8 / 3) * beat, 0.3)
    for (const b of [1, 3]) { brush(out, t0 + b * beat, 0.09, bar * 9 + b, 0.16); kick(out, t0 + b * beat, 0.1, 200, 160, 0.12) }
    for (let i = 0; i < 12; i++) brush(out, t0 + i * trip, i % 3 ? 0.01 : 0.018, bar * 40 + i, 0.03)
  })
  return finish(out, 0.45, 0.28, 0.24)
}

/** Porch Country — G–C–D–G: alternating bass, a guitar "chick" with brushes, a banjo roll and a fiddle tune. */
function country() {
  const beat = 60 / 104, out = new Float32Array(Math.floor(SR * beat * 16))
  const chords = [[55, 59, 62, 67], [55, 60, 64, 67], [57, 62, 66, 69], [55, 59, 62, 67]], roots = [43, 48, 50, 43]
  chords.forEach((ch, bar) => {
    const t0 = bar * 4 * beat, r = roots[bar]
    pluck(out, t0, midi(r), 0.5, 0.994, 0.35, bar * 4); pluck(out, t0 + 2 * beat, midi(r - 5), 0.45, 0.994, 0.35, bar * 4 + 1)
    for (const b of [1, 3]) { ch.forEach((n, k) => pluck(out, t0 + b * beat + k * 0.01, midi(n), 0.07, 0.97, 0.7, bar * 20 + b * 4 + k)); brush(out, t0 + b * beat, 0.06, bar * 7 + b, 0.1) }
    ;[0, 1, 3, 0, 1, 3, 0, 2].forEach((k, i) => pluck(out, t0 + i * beat * 0.5, midi(ch[k] + 12), 0.09, 0.975, 0.85, bar * 16 + i + 100))
  })
  ;[[71, 1], [74, 1], [76, 1], [74, 1], [76, 2], [79, 1], [76, 1], [74, 1.5], [72, 0.5], [69, 1], [66, 1], [67, 4]].reduce((t, [n, d]) => {
    tone(out, t * beat, d * beat * 0.92, midi(n), 0.07, SAW, { a: 0.06, r: 0.15 }, { vib: 5.6, vibDepth: 0.005, bright: 0.12 }); return t + d
  }, 0)
  return finish(out, 0.28, 0.18, 0.24)
}

export type MusicBed = { id: string; name: string; blurb: string; romantic?: boolean; make: () => Float32Array | null }
export const MUSIC: MusicBed[] = [
  { id: 'none', name: 'No background', blurb: 'Just the voice and the wax.', make: () => null },
  { id: 'serenade', name: 'Moonlight Serenade', blurb: 'Lush strings and a harp under the stars.', romantic: true, make: serenade },
  { id: 'guitar', name: 'Spanish Guitar', blurb: 'Nylon strings, fingerpicked, close and tender.', romantic: true, make: guitar },
  { id: 'candlelight', name: 'Candlelight Bossa', blurb: 'A slow electric-piano bossa for two.', romantic: true, make: candlelight },
  { id: 'ballad', name: 'Love Letter Ballad', blurb: 'Tender piano with a cello line beneath.', romantic: true, make: ballad },
  { id: 'sweetheart', name: 'Sweetheart Swing', blurb: 'A 1940s slow dance: clarinet, bass and brushes.', romantic: true, make: sweetheart },
  { id: 'harp', name: 'Tender Harp', blurb: 'A solo harp, slow and loving.', romantic: true, make: harp },
  { id: 'firstdance', name: 'Our First Dance', blurb: 'A slow waltz — strings, harp and celesta.', romantic: true, make: firstDance },
  { id: 'hearth', name: 'Fireside Warmth', blurb: 'Round, warm chords and a soft plucked bass.', make: hearth },
  { id: 'ballroom', name: 'Golden Ballroom', blurb: 'A sweeping vintage string orchestra in three.', make: ballroom },
  { id: 'foxtrot', name: 'Gramophone Foxtrot', blurb: 'A 1920s dance band: tuba, banjo and muted horn.', make: foxtrot },
  { id: 'pad', name: 'Parlour Strings', blurb: 'A soft, slow string bed.', make: parlour },
  { id: 'musicbox', name: 'Music Box', blurb: 'A soft, mellow music box with a warm hush.', make: musicBox },
  { id: 'jazz', name: 'Late-Night Jazz', blurb: 'Upright bass, brushes, a smoky Rhodes.', make: jazz },
  { id: 'waltz', name: 'Parisian Waltz', blurb: 'A musette accordion in three-quarter time.', make: waltz },
  { id: 'piano', name: 'Parlour Piano', blurb: 'Gentle broken chords by the window.', make: piano },
  { id: 'organ', name: 'Chapel Organ', blurb: 'Slow, reverent chords in a stone hall.', make: organ },
  { id: 'lofi', name: 'Lo-Fi Bedroom', blurb: 'Dusty Rhodes chords over a lazy beat.', make: lofi },
  { id: 'blues', name: 'Blues Shuffle', blurb: 'Boogie bass, organ stabs and a wailing reed.', make: blues },
  { id: 'celtic', name: 'Celtic Morning', blurb: 'Tin whistle and harp in a lilting six-eight.', make: celtic },
  { id: 'cinematic', name: 'Cinematic Dawn', blurb: 'Rising horns over swelling strings and timpani.', make: cinematic },
  { id: 'soul', name: 'Sunday Soul', blurb: 'A 12/8 gospel ballad: organ, keys and backbeat.', make: soul },
  { id: 'country', name: 'Porch Country', blurb: 'A banjo roll, boom-chick bass and a lazy fiddle.', make: country },
]

const cache = new Map<string, Float32Array | null>()
export const musicBed = (id: string) => {
  if (!cache.has(id)) cache.set(id, MUSIC.find((m) => m.id === id)?.make() ?? null)
  return cache.get(id)!
}

/** Id used for music the person uploads themselves. */
export const CUSTOM_MUSIC = 'custom'

/** Registers the person's own trimmed music as the "custom" bed (null clears it). */
export function setCustomMusic(bed: Float32Array | null) { cache.set(CUSTOM_MUSIC, bed) }

/** Display name for a music id, including uploaded music. */
export function musicName(id: string, customName?: string | null) {
  if (id === CUSTOM_MUSIC) return customName ? `Your music · ${customName}` : 'Your own music'
  return MUSIC.find((m) => m.id === id)?.name ?? 'No background'
}

/** Longest part of an uploaded song that can be used as a background, in seconds. */
export const CUSTOM_MAX_SECONDS = 60
export const CUSTOM_MIN_SECONDS = 3

/** Cuts [start, end) seconds out of an uploaded song, softens the edges so it loops gently, and levels it like the built-in beds. */
export function makeCustomBed(src: Float32Array, start: number, end: number) {
  const a = Math.max(0, Math.floor(start * SR)), b = Math.min(src.length, Math.floor(end * SR))
  const out = src.slice(a, Math.max(a + 1, b))
  const fade = Math.min(Math.floor(SR * 0.4), Math.floor(out.length / 4))
  for (let i = 0; i < fade; i++) { const g = i / fade; out[i] *= g; out[out.length - 1 - i] *= g }
  let m = 1e-6
  for (const v of out) m = Math.max(m, Math.abs(v))
  for (let i = 0; i < out.length; i++) out[i] *= 0.26 / m
  return out
}
