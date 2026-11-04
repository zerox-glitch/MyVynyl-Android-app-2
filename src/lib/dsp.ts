import type { Crackle, Preset } from './presets'

export const SR = 44100

/** mulberry32 seeded from a string — same record + preset ⇒ same crackle positions */
export function rng(seedStr: string) {
  let h = 1779033703
  for (let i = 0; i < seedStr.length; i++) h = Math.imul(h ^ seedStr.charCodeAt(i), 3432918353), (h = (h << 13) | (h >>> 19))
  let a = h >>> 0
  return () => {
    a = (a + 0x6d2b79f5) | 0
    let t = Math.imul(a ^ (a >>> 15), 1 | a)
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

class Biquad {
  b0 = 1; b1 = 0; b2 = 0; a1 = 0; a2 = 0; z1 = 0; z2 = 0
  static make(type: 'lp' | 'hp' | 'peak', f: number, q = 0.707, gainDb = 0) {
    const k = new Biquad(), w = (2 * Math.PI * f) / SR, cs = Math.cos(w), al = Math.sin(w) / (2 * q)
    let b0, b1, b2, a0, a1, a2
    if (type === 'peak') {
      const A = Math.pow(10, gainDb / 40)
      b0 = 1 + al * A; b1 = -2 * cs; b2 = 1 - al * A; a0 = 1 + al / A; a1 = -2 * cs; a2 = 1 - al / A
    } else {
      const lp = type === 'lp'
      b1 = lp ? 1 - cs : -(1 + cs); b0 = b2 = lp ? b1 / 2 : -b1 / 2; a0 = 1 + al; a1 = -2 * cs; a2 = 1 - al
    }
    Object.assign(k, { b0: b0 / a0, b1: b1 / a0, b2: b2 / a0, a1: a1 / a0, a2: a2 / a0 })
    return k
  }
  run(x: number) {
    const y = this.b0 * x + this.z1
    this.z1 = this.b1 * x - this.a1 * y + this.z2
    this.z2 = this.b2 * x - this.a2 * y
    return y
  }
}

export type RenderOpts = { preset: Preset; crackle?: Crackle; seed: string; intro?: number; tail?: number; maxGain?: number; music?: Float32Array | null; musicLevel: number; crackleLevel?: number; character?: number; volume?: number; fixedGain?: number; onProgress?: (p: number, stage: string) => void }

const tick = () => new Promise((r) => setTimeout(r, 0))

/** Stylus landing in the lead-in groove: a bright contact click, a low tonearm thump with a falling pitch, then a short scuff of run-in noise. */
function needleDrop(L: Float32Array, R: Float32Array, at: number, rnd: () => number) {
  const len = Math.min(L.length - at, Math.floor(SR * 0.35))
  const hp = Biquad.make('hp', 1800, 0.7), lp = Biquad.make('lp', 5200)
  let ph = 0
  for (let j = 0; j < len; j++) {
    const t = j / SR
    const click = (j < 2 ? 0.55 : 0) + hp.run(rnd() * 2 - 1) * 0.4 * Math.exp(-t / 0.004)
    ph += (2 * Math.PI * (44 + 30 * Math.exp(-t / 0.03))) / SR
    const thump = Math.sin(ph) * 0.42 * Math.exp(-t / 0.05) * Math.min(1, j / 40)
    const scuff = lp.run(rnd() * 2 - 1) * 0.07 * Math.exp(-t / 0.11)
    L[at + j] += click + thump + scuff
    R[at + j] += click * 0.9 + thump + scuff * 0.92
  }
}

let dropCtx: AudioContext | null = null
let dropBuf: AudioBuffer | null = null

/** Plays the needle-drop clip on every stylus landing; mirrors Android NeedleDrop.play(). */
export function playNeedleDrop() {
  try {
    dropCtx ??= new AudioContext()
    const ac = dropCtx
    void ac.resume()
    if (!dropBuf) {
      const n = Math.floor(SR * 0.35), l = new Float32Array(n), r = new Float32Array(n)
      needleDrop(l, r, 0, rng('needle-drop'))
      const b = ac.createBuffer(2, n, SR)
      b.getChannelData(0).set(l); b.getChannelData(1).set(r)
      dropBuf = b
    }
    const src = ac.createBufferSource()
    src.buffer = dropBuf
    src.connect(ac.destination)
    src.start()
  } catch { /* audio context unavailable */ }
}

/** Mixes the needle-drop over the start of a 16-bit stereo WAV, so exports open with the same landing heard live. */
export async function withNeedleDrop(master: Blob): Promise<Blob> {
  const buf = (await master.arrayBuffer()).slice(0)
  const d = new DataView(buf)
  const frames = Math.floor((buf.byteLength - 44) / 4)
  const dropLen = Math.min(frames, Math.floor(SR * 0.35))
  const l = new Float32Array(dropLen), r = new Float32Array(dropLen)
  needleDrop(l, r, 0, rng('needle-drop'))
  for (let i = 0; i < dropLen; i++) {
    const o = 44 + i * 4
    const sl = Math.max(-32768, Math.min(32767, d.getInt16(o, true) + Math.round(l[i] * 32767)))
    const sr = Math.max(-32768, Math.min(32767, d.getInt16(o + 2, true) + Math.round(r[i] * 32767)))
    d.setInt16(o, sl, true)
    d.setInt16(o + 2, sr, true)
  }
  return new Blob([buf], { type: 'audio/wav' })
}

/** Full vinyl pipeline on mono 44.1k voice → stereo master. Processes in blocks and yields to keep UI alive. */
export async function renderMaster(voice: Float32Array, o: RenderOpts): Promise<[Float32Array, Float32Array]> {
  // character amount scales the preset between a clean pressing (0) and an exaggerated one (1.5)
  const k = Math.max(0, Math.min(1.5, o.character ?? 1)), q = o.preset
  const p = { ...q, warmth: q.warmth * k, drive: Math.min(0.95, q.drive * k), rolloff: Math.max(3000, 19000 - (19000 - q.rolloff) * k), wowDepth: q.wowDepth * k, flutterDepth: q.flutterDepth * k, width: Math.max(0, 1 + (q.width - 1) * k), honk: (q.honk ?? 0) * k, hum: (q.hum ?? 0) * k, lowcut: q.lowcut ? 80 + (q.lowcut - 80) * Math.min(1, k) : undefined }
  const cl0 = Math.max(0, o.crackleLevel ?? 1)
  const c0 = o.crackle ?? { density: 1, amp: 1, len: 1, pops: 1, hissDb: 0, hissTone: 1 }, R = rng(o.seed + ':' + p.id)
  const c = cl0 === 1 ? c0 : { ...c0, amp: c0.amp * cl0, hissDb: c0.hissDb + (cl0 > 0 ? 20 * Math.log10(cl0) : -120) }, intro = Math.floor(SR * (o.intro ?? 1.2))
  const n = voice.length + intro + Math.floor(SR * (o.tail ?? 1)), L = new Float32Array(n), Rt = new Float32Array(n)
  const hp = Biquad.make('hp', p.lowcut ?? 80), hp2 = Biquad.make('hp', p.lowcut ?? 20), honk = Biquad.make('peak', 1500, 1.1, p.honk ?? 0), pres = Biquad.make('peak', 3200, 0.9, 2.5), warm = Biquad.make('peak', 220, 0.8, p.warmth * 0.8)
  const lpA = Biquad.make('lp', p.rolloff), lpB = Biquad.make('lp', p.rolloff * 1.05)
  const nLp = Biquad.make('lp', Math.min(18000, p.rolloff * 0.6 * c.hissTone))
  // period recording chain (voice only): narrow acoustic-horn band, boxy mid resonance, dull top
  const ak = q.age * k
  const vLow = Biquad.make('hp', 70 + ak * 42, 0.8), vLow2 = Biquad.make('hp', 60 + ak * 30), vHorn = Biquad.make('peak', 1250, 1.3, ak * 1.5), vBox = Biquad.make('peak', 650, 1.6, ak * 0.7)
  const vTop = Biquad.make('lp', Math.max(3200, 16000 - ak * 2300), 0.75), vTop2 = Biquad.make('lp', Math.max(3400, 17000 - ak * 2300), 0.6)
  const tickHp = Biquad.make('hp', 1400, 0.7), tickHp2 = Biquad.make('hp', 1400, 0.7), hissHp = Biquad.make('hp', 2500)
  const delay = new Float32Array(4096); let wi = 0
  let env = 0, duck = 0
  const thr = 0.25, ratio = 3, atk = Math.exp(-1 / (SR * 0.005)), rel = Math.exp(-1 / (SR * 0.12))
  const surf = Math.pow(10, (p.surfaceDb + c.hissDb) / 20), crackP = (p.crackles * c.density) / 60 / SR, popP = (p.pops * c.pops) / 60 / SR
  const ev: { t: number; amp: number; len: number; pan: number; f: number }[] = []
  const block = 8192
  for (let s = 0; s < n; s += block) {
    const end = Math.min(n, s + block)
    for (let i = s; i < end; i++) {
      const vi = i - intro
      let v = vi >= 0 && vi < voice.length ? voice[vi] : 0
      // voice cleanup + warmth
      v = honk.run(warm.run(pres.run(hp2.run(hp.run(v)))))
      if (ak > 0.01) { v = vTop2.run(vTop.run(vBox.run(vHorn.run(vLow2.run(vLow.run(v)))))); const sd = 1 + ak * 0.35; v = Math.tanh(v * sd) / Math.tanh(sd) }
      // background with voice-controlled ducking
      const lev = Math.abs(v); duck = lev > duck ? duck + (lev - duck) * 0.01 : duck * 0.99995
      if (o.music && o.music.length && vi >= 0) v += o.music[vi % o.music.length] * o.musicLevel * (1 - Math.min(0.75, duck * 6))
      // saturation
      const d = 1 + p.drive * 4
      v = (1 - p.drive) * v + p.drive * (Math.tanh(v * d) / Math.tanh(d))
      // compressor (soft knee approx)
      const a = Math.abs(v); env = a > env ? atk * env + (1 - atk) * a : rel * env + (1 - rel) * a
      if (env > thr) v *= Math.pow(env / thr, 1 / ratio - 1)
      v *= 1.4
      // wow + flutter via interpolated delay
      delay[wi] = v
      const t = i / SR
      const mod = 600 + SR * (p.wowDepth * Math.sin(2 * Math.PI * p.wowHz * t + 0.3 * Math.sin(t * 0.21)) + p.flutterDepth * Math.sin(2 * Math.PI * 42 * t))
      const rp = wi - mod, ip = Math.floor(rp), fr = rp - ip
      const x0 = delay[(ip + 4096) & 4095], x1 = delay[(ip + 4097) & 4095]
      v = x0 + (x1 - x0) * fr
      wi = (wi + 1) & 4095
      // surface: only a faint, steady high hiss — no breathing wash
      const w = R() * 2 - 1
      const noise = hissHp.run(nLp.run(w)) * surf * 0.9
      // crackle: sharp broadband clicks with a power-law size spread, plus the odd low "thock" pop
      if (R() < crackP * (ev.length ? 1.8 : 1)) { const r = R(); ev.push({ t: 0, amp: (0.03 + r * r * r * 0.32) * (p.texture / 3 + 0.4) * c.amp, len: (2 + R() * 10) * c.len + 2, pan: 0.15 + R() * 0.7, f: 0 }) }
      if (R() < popP) ev.push({ t: 0, amp: (0.16 + R() * 0.1) * Math.min(1.3, c0.amp) * cl0, len: 90 + R() * 120, pan: 0.3 + R() * 0.4, f: 1 })
      let cl = 0, cr = 0, pl = 0, pr = 0
      for (let k = ev.length - 1; k >= 0; k--) {
        const e = ev[k]
        if (e.f) { const sig = Math.sin(e.t * 0.06) * Math.exp(-e.t / (e.len * 0.25)) * e.amp; pl += sig * (1 - e.pan); pr += sig * e.pan }
        else { const sig = (e.t === 0 ? (R() < 0.5 ? -1 : 1) : (R() * 2 - 1) * 0.6) * Math.exp(-e.t / (e.len * 0.35)) * e.amp; cl += sig * (1 - e.pan); cr += sig * e.pan }
        if (++e.t > e.len) ev.splice(k, 1)
      }
      cl = tickHp.run(cl) + pl; cr = tickHp2.run(cr) + pr
      // eq rolloff per channel, stereo width
      const fade = Math.min(1, i / (SR * 0.4), (n - i) / (SR * 0.6))
      const vl = lpA.run(v), vr = lpB.run(v)
      const hum = p.hum ? p.hum * (Math.sin(2 * Math.PI * 60 * t) + 0.4 * Math.sin(2 * Math.PI * 180 * t)) : 0
      const mid = (vl + vr) / 2 + hum, side = ((vl - vr) / 2) * p.width
      L[i] = (mid + side + noise + cl * 2) * fade
      Rt[i] = (mid - side + noise * 0.92 + cr * 2) * fade
    }
    o.onProgress?.(s / n, s / n < 0.3 ? 'Mixing atmosphere' : s / n < 0.8 ? 'Adding vinyl character' : 'Mastering')
    await tick()
  }
  // limiter: normalize to -1 dBFS then soft ceiling
  let peak = 1e-6
  for (let i = 0; i < n; i++) peak = Math.max(peak, Math.abs(L[i]), Math.abs(Rt[i]))
  const g = o.fixedGain ?? Math.min(0.891 / peak, o.maxGain ?? Infinity) * Math.max(0, Math.min(1, o.volume ?? 1))
  for (let i = 0; i < n; i++) {
    L[i] = Number.isFinite(L[i]) ? L[i] * g : 0
    Rt[i] = Number.isFinite(Rt[i]) ? Rt[i] * g : 0
  }
  return [L, Rt]
}

export function encodeWav(l: Float32Array, r: Float32Array) {
  const n = l.length, buf = new ArrayBuffer(44 + n * 4), d = new DataView(buf)
  const w = (o: number, s: string) => [...s].forEach((c, i) => d.setUint8(o + i, c.charCodeAt(0)))
  w(0, 'RIFF'); d.setUint32(4, 36 + n * 4, true); w(8, 'WAVE'); w(12, 'fmt ')
  d.setUint32(16, 16, true); d.setUint16(20, 1, true); d.setUint16(22, 2, true); d.setUint32(24, SR, true)
  d.setUint32(28, SR * 4, true); d.setUint16(32, 4, true); d.setUint16(34, 16, true); w(36, 'data'); d.setUint32(40, n * 4, true)
  for (let i = 0, o = 44; i < n; i++, o += 4) {
    d.setInt16(o, Math.max(-1, Math.min(1, l[i])) * 32767, true)
    d.setInt16(o + 2, Math.max(-1, Math.min(1, r[i])) * 32767, true)
  }
  return new Blob([buf], { type: 'audio/wav' })
}

export async function decodeToMono(blob: Blob): Promise<Float32Array> {
  const ctx = new OfflineAudioContext(1, 1, SR)
  const ab = await ctx.decodeAudioData(await blob.arrayBuffer())
  const off = new OfflineAudioContext(1, Math.ceil(ab.duration * SR), SR)
  const src = off.createBufferSource(); src.buffer = ab; src.connect(off.destination); src.start()
  return (await off.startRendering()).getChannelData(0)
}

export function waveform(data: Float32Array, bars = 72) {
  const step = Math.floor(data.length / bars) || 1, out: number[] = []
  for (let b = 0; b < bars; b++) {
    let m = 0
    for (let i = b * step; i < (b + 1) * step && i < data.length; i += 32) m = Math.max(m, Math.abs(data[i]))
    out.push(m)
  }
  const mx = Math.max(...out, 1e-6)
  return out.map((v) => v / mx)
}

/** Arrays produced by demoVoice(), so previews can tell a stand-in from a real voice. */
export const demoSources = new WeakSet<Float32Array>()

/** A soft FM music-box lullaby so the prototype has something to press without a mic. */
export function demoVoice(): Float32Array {
  const notes = [0, 4, 7, 12, 11, 7, 9, 5, 4, 2, 0, -1, 0, 4, 7, 4]
  const len = SR * 9, out = new Float32Array(len), beat = len / notes.length
  notes.forEach((nt, k) => {
    const f = 784 * Math.pow(2, nt / 12), st = Math.floor(k * beat)
    for (let i = 0; i < SR * 2 && st + i < len; i++) {
      const t = i / SR, e = Math.min(1, t / 0.003) * Math.exp(-t * 2.4)
      out[st + i] += 0.22 * e * Math.sin(2 * Math.PI * f * t + 1.3 * Math.exp(-t * 4) * Math.sin(2 * Math.PI * f * 5.19 * t))
    }
  })
  demoSources.add(out)
  return out
}

/** Gentle pad bed used as the bundled background option. */
export function demoPad(): Float32Array {
  const len = SR * 8, out = new Float32Array(len), ch = [130.8, 164.8, 196, 246.9]
  for (let i = 0; i < len; i++) {
    const t = i / SR, sw = 0.5 + 0.5 * Math.sin((2 * Math.PI * t) / 8 - Math.PI / 2)
    let s = 0
    for (const f of ch) s += Math.sin(2 * Math.PI * f * t + Math.sin(t * 0.7))
    out[i] = s * 0.06 * (0.6 + 0.4 * sw)
  }
  return out
}
