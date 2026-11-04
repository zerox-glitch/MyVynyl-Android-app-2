import { useEffect, useMemo, useRef, useState } from 'react'
import { SR, waveform } from '../lib/dsp'
import { CUSTOM_MAX_SECONDS, CUSTOM_MIN_SECONDS } from '../lib/music'
import { fmt } from './ui'

/**
 * Trim sheet for uploaded background music: pick the part to use, hear it, then keep it.
 * Mirrors Android ui/studio/MusicCrop.kt.
 */
export default function MusicCrop({ source, name, onCancel, onSave }: {
  source: Float32Array; name: string
  onCancel: () => void; onSave: (start: number, end: number) => void
}) {
  const dur = source.length / SR
  const [start, setStart] = useState(0)
  const [end, setEnd] = useState(Math.min(dur, 30))
  const [playing, setPlaying] = useState(false)
  const [head, setHead] = useState(0)
  const ctx = useRef<AudioContext | null>(null), node = useRef<AudioBufferSourceNode | null>(null), raf = useRef(0)
  const wave = useMemo(() => waveform(source, 96), [source])

  const stop = () => {
    cancelAnimationFrame(raf.current)
    try { node.current?.stop() } catch { /* already stopped */ }
    node.current = null; setPlaying(false); setHead(0)
  }
  useEffect(() => () => { stop(); ctx.current?.close() }, [])

  const play = () => {
    if (playing) return stop()
    ctx.current ??= new AudioContext()
    const ac = ctx.current; void ac.resume()
    const a = Math.floor(start * SR), b = Math.floor(end * SR)
    const buf = ac.createBuffer(1, Math.max(1, b - a), SR); buf.getChannelData(0).set(source.subarray(a, b))
    const n = ac.createBufferSource(); n.buffer = buf; n.connect(ac.destination); n.onended = () => { if (node.current === n) stop() }
    const t0 = ac.currentTime; n.start(); node.current = n; setPlaying(true)
    const tick = () => { setHead(ac.currentTime - t0); raf.current = requestAnimationFrame(tick) }
    raf.current = requestAnimationFrame(tick)
  }

  // keep the part between the shortest and longest allowed length
  const moveStart = (v: number) => {
    stop()
    const s = Math.min(v, dur - CUSTOM_MIN_SECONDS)
    setStart(Math.max(0, s))
    setEnd((e) => Math.min(dur, Math.max(s + CUSTOM_MIN_SECONDS, Math.min(e, s + CUSTOM_MAX_SECONDS))))
  }
  const moveEnd = (v: number) => {
    stop()
    const e = Math.max(v, CUSTOM_MIN_SECONDS)
    setEnd(Math.min(dur, e))
    setStart((s) => Math.max(0, e - CUSTOM_MAX_SECONDS, Math.min(s, e - CUSTOM_MIN_SECONDS)))
  }
  const tooShort = dur < CUSTOM_MIN_SECONDS
  const sel = (i: number) => { const t = (i / wave.length) * dur; return t >= start && t <= end }

  return (
    <div className="absolute inset-0 z-40 flex items-end bg-obsidian/80 backdrop-blur-sm" role="dialog" aria-modal="true" aria-labelledby="crop-title">
      <div className="w-full rounded-t-[28px] border-t border-brass/30 bg-stone px-6 pb-6 pt-3">
        <div className="mx-auto h-1 w-10 rounded-full bg-cream/25" />
        <p className="mt-4 deco text-[10px] text-amber-bright">Your own music</p>
        <h3 id="crop-title" className="mt-1 font-display text-2xl leading-tight text-cream">Choose the part to use</h3>
        <p className="mt-1 truncate text-sm text-muted" title={name}>{name} · {fmt(dur)}</p>

        {tooShort ? (
          <p role="alert" className="mt-5 text-sm text-err">This file is too short. Choose music that is at least {CUSTOM_MIN_SECONDS} seconds long.</p>
        ) : (
          <>
            <div className="relative mt-5 flex h-20 items-center gap-[2px] rounded-xl bg-black/30 px-2 ring-1 ring-inset ring-cream/5" aria-hidden>
              {wave.map((v, i) => <span key={i} className={`flex-1 rounded-full ${sel(i) ? 'bg-amber-bright' : 'bg-cream/15'}`} style={{ height: `${Math.max(8, v * 100)}%` }} />)}
              {playing && <span className="absolute inset-y-1 w-0.5 rounded bg-cream" style={{ left: `${Math.min(100, ((start + head) / dur) * 100)}%` }} />}
            </div>
            <p className="mt-2 text-center font-mono text-xs text-amber-bright">Using {fmt(start)} – {fmt(end)} · {Math.round(end - start)} seconds</p>

            <label className="mt-4 block">
              <span className="flex justify-between text-sm text-cream"><span>Start</span><span className="font-mono text-muted">{fmt(start)}</span></span>
              <input type="range" min={0} max={dur} step={0.1} value={start} onChange={(e) => moveStart(+e.target.value)} aria-label="Start of the part" className="mt-2 h-2 w-full cursor-pointer accent-amber-bright" />
            </label>
            <label className="mt-3 block">
              <span className="flex justify-between text-sm text-cream"><span>End</span><span className="font-mono text-muted">{fmt(end)}</span></span>
              <input type="range" min={0} max={dur} step={0.1} value={end} onChange={(e) => moveEnd(+e.target.value)} aria-label="End of the part" className="mt-2 h-2 w-full cursor-pointer accent-amber-bright" />
            </label>
            <p className="mt-2 text-xs leading-relaxed text-muted">Up to {CUSTOM_MAX_SECONDS} seconds. It plays quietly under your voice and repeats if your message is longer.</p>

            <button type="button" onClick={play} className="mt-4 flex min-h-12 w-full items-center justify-center gap-2 rounded-full border border-amber/50 bg-amber/10 text-base text-cream transition hover:border-amber-bright active:scale-[.98]">
              {playing ? <><span className="flex gap-1"><span className="h-4 w-1.5 rounded-sm bg-cream" /><span className="h-4 w-1.5 rounded-sm bg-cream" /></span>Stop · {fmt(head)}</> : <><span className="h-0 w-0 border-y-[7px] border-l-[12px] border-y-transparent border-l-amber-bright" />Play this part</>}
            </button>
          </>
        )}
        <div className="mt-3 flex gap-2">
          <button type="button" onClick={() => { stop(); onCancel() }} className="min-h-12 flex-1 rounded-full border border-brass/40 text-base text-cream transition hover:border-amber-bright">Cancel</button>
          <button type="button" disabled={tooShort} onClick={() => { stop(); onSave(start, end) }} className="min-h-12 flex-1 rounded-full bg-gradient-to-b from-[#f0c86a] via-[#c99234] to-[#8a5a1a] text-base font-semibold text-obsidian shadow-[0_12px_30px_-10px_rgba(245,158,11,.9)] active:scale-[.98] disabled:opacity-40">Use this part</button>
        </div>
      </div>
    </div>
  )
}
