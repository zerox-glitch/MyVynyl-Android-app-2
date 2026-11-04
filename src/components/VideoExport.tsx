import { useEffect, useMemo, useRef, useState } from 'react'
import Turntable from './Turntable'
import { ProBadge } from './Paywall'
import { openPaywall, videoAudio, videoTier, FREE_VIDEO_SECONDS } from '../lib/pro'
import type { VinylStyle } from '../lib/presets'
import type { StoredRecord } from '../lib/db'
import { fmt } from './ui'

const FPS = 30
const INTRO = 3500 // ms title card: sender, receiver, title
const FADE = 700 // ms crossfade between cards and the deck
const CUE_DELAY = INTRO + 600 // ms before the start knob turns
const OUTRO = 3000 // ms free-tier watermark card
const TAIL = 2500 // ms after the music while the arm lifts and returns

/** Best container this browser can record: MP4 (Chrome 126+, Safari) first, WebM otherwise. */
function pickMime() {
  const opts = ['video/mp4;codecs=avc1.42E01F,mp4a.40.2', 'video/mp4', 'video/webm;codecs=vp9,opus', 'video/webm;codecs=vp8,opus', 'video/webm']
  return opts.find((m) => typeof MediaRecorder !== 'undefined' && MediaRecorder.isTypeSupported(m)) ?? ''
}

function drawMark(x: CanvasRenderingContext2D, cx: number, cy: number, sz: number) {
  const k = sz / 120
  x.save()
  x.translate(cx - sz / 2, cy - sz / 2)
  x.scale(k, k)
  x.save()
  x.shadowColor = 'rgba(217,119,6,.45)'
  x.shadowBlur = 20
  x.fillStyle = '#d97706'
  x.beginPath()
  x.roundRect(0, 0, 120, 120, 27)
  x.fill()
  x.restore()

  x.save()
  const clip = new Path2D()
  clip.rect(0, 0, 120, 120)
  clip.moveTo(41, 10)
  clip.lineTo(60, 47)
  clip.lineTo(79, 10)
  clip.closePath()
  x.clip(clip, 'evenodd')
  x.fillStyle = '#14100d'
  x.beginPath(); x.arc(60, 62, 45, 0, Math.PI * 2); x.fill()
  x.lineWidth = 1.6
  x.strokeStyle = 'rgba(245,233,211,.16)'
  x.beginPath(); x.arc(60, 62, 36, 0, Math.PI * 2); x.stroke()
  x.strokeStyle = 'rgba(245,233,211,.10)'
  x.beginPath(); x.arc(60, 62, 27, 0, Math.PI * 2); x.stroke()
  x.restore()

  x.fillStyle = '#f5e9d3'
  x.beginPath(); x.arc(60, 62, 14, 0, Math.PI * 2); x.fill()
  x.fillStyle = '#14100d'
  x.beginPath(); x.arc(60, 62, 2.6, 0, Math.PI * 2); x.fill()
  x.restore()
}

/** Warm stage behind the deck — same glows as the Player — plus the intro card and the free-tier outro card. */
function stageLayers(size: number, title: string, from: string, to: string, watermark: boolean) {
  const u = size / 1080
  const bg = document.createElement('canvas'); bg.width = bg.height = size
  const b = bg.getContext('2d')!
  b.fillStyle = '#0c0a09'; b.fillRect(0, 0, size, size)
  const glow = (cx: number, cy: number, rx: number, ry: number, c: string) => {
    b.save(); b.translate(cx, cy); b.scale(1, ry / rx)
    const g = b.createRadialGradient(0, 0, 0, 0, 0, rx); g.addColorStop(0, c); g.addColorStop(1, 'rgba(0,0,0,0)')
    b.fillStyle = g; b.beginPath(); b.arc(0, 0, rx, 0, Math.PI * 2); b.fill(); b.restore()
  }
  glow(size * 0.5, size * 0.3, size * 0.8, size * 0.5, 'rgba(217,119,6,.24)')
  glow(size * 0.5, size, size * 0.6, size * 0.4, 'rgba(153,27,27,.2)')

  const card = () => { const c = document.createElement('canvas'); c.width = c.height = size; const x = c.getContext('2d')!; x.drawImage(bg, 0, 0); x.textAlign = 'center'; return x }
  const ls = (x: CanvasRenderingContext2D, px: number) => ((x as CanvasRenderingContext2D & { letterSpacing: string }).letterSpacing = `${px * u}px`)
  const fit = (x: CanvasRenderingContext2D, t: string, max: number) => { if (x.measureText(t).width <= max) return t; let s = t; while (s && x.measureText(s + '…').width > max) s = s.slice(0, -1); return s + '…' }
  const rule = (x: CanvasRenderingContext2D, y: number) => { x.strokeStyle = 'rgba(217,119,6,.55)'; x.lineWidth = 2 * u; x.beginPath(); x.moveTo(size / 2 - 90 * u, y); x.lineTo(size / 2 + 90 * u, y); x.stroke() }

  // intro: sender → receiver, then the title
  const ix = card(), mid = size / 2
  ix.fillStyle = 'rgba(251,191,36,.85)'; ix.font = `600 ${26 * u}px "Big Shoulders Display", sans-serif`; ls(ix, 8)
  ix.fillText('A RECORD PRESSED', mid, size * 0.3)
  ls(ix, 0); ix.fillStyle = 'rgba(254,243,199,.65)'; ix.font = `italic ${34 * u}px Gloock, serif`; ix.fillText('from', mid, size * 0.38)
  ix.fillStyle = '#fef3c7'; ix.font = `${58 * u}px Gloock, serif`; ix.fillText(fit(ix, from, size - 160 * u), mid, size * 0.44)
  ix.fillStyle = 'rgba(254,243,199,.65)'; ix.font = `italic ${34 * u}px Gloock, serif`; ix.fillText('for', mid, size * 0.51)
  ix.fillStyle = '#fef3c7'; ix.font = `${58 * u}px Gloock, serif`; ix.fillText(fit(ix, to, size - 160 * u), mid, size * 0.57)
  rule(ix, size * 0.63)
  ix.fillStyle = '#fbbf24'; ix.font = `${82 * u}px Gloock, serif`; ix.fillText(fit(ix, title, size - 140 * u), mid, size * 0.73)

  // outro (free): app watermark
  let outro: HTMLCanvasElement | null = null
  if (watermark) {
    const ox = card()
    drawMark(ox, mid, size * 0.36, 190 * u)
    ox.fillStyle = 'rgba(254,243,199,.6)'; ox.font = `italic ${32 * u}px Gloock, serif`; ox.fillText('made with', mid, size * 0.53)
    ox.fillStyle = '#fbbf24'; ox.font = `${124 * u}px Gloock, serif`; ls(ox, 10); ox.fillText('Vynyl', mid + 5 * u, size * 0.64)
    ls(ox, 0); rule(ox, size * 0.685)
    ox.fillStyle = 'rgba(254,243,199,.75)'; ox.font = `700 ${22 * u}px "Hanken Grotesk", sans-serif`; ls(ox, 6); ox.fillText('PRESS YOUR OWN RECORD', mid + 3 * u, size * 0.735)
    ls(ox, 0)
    outro = ox.canvas
  }
  return { bg, intro: ix.canvas, outro }
}

type Phase = 'preparing' | 'cueing' | 'recording' | 'done' | 'error'

/**
 * Records the real three.js turntable — tonearm cue, swing, needle drop, tracking, return — with the record's audio,
 * starting the music at needle contact exactly like the Player. Mirrors android/…/export/VideoExport.kt.
 */
export default function VideoExport({ record, style, labelPhoto, pro, onClose }: { record: StoredRecord; style: VinylStyle; labelPhoto?: Blob; pro: boolean; onClose: () => void }) {
  const tier = useMemo(() => videoTier(pro), [pro])
  const audio = useRef<HTMLAudioElement>(null)
  const out = useRef<HTMLCanvasElement>(null)
  const layers = useRef<ReturnType<typeof stageLayers> | null>(null)
  const rec = useRef<{ recorder: MediaRecorder; ctx: AudioContext; started: boolean; endedAt: number } | null>(null)
  const startedAt = useRef(0)
  const [phase, setPhase] = useState<Phase>('preparing')
  const [engaged, setEngaged] = useState(false)
  const [audioSrc, setAudioSrc] = useState<string>()
  const [audioDur, setAudioDur] = useState(0)
  const [elapsed, setElapsed] = useState(0)
  const [result, setResult] = useState<{ url: string; blob: Blob; ext: string }>()
  const [error, setError] = useState('')
  const [monitor, setMonitor] = useState(true)
  const monitorGain = useRef<GainNode | null>(null)
  const label = useMemo(() => ({ title: record.title, recipient: record.recipient, side: record.sideA, date: record.date }), [record])

  // build the soundtrack and the recorder
  useEffect(() => {
    let cancelled = false, src = ''
    ;(async () => {
      try {
        const mime = pickMime()
        if (!mime || !out.current?.captureStream) throw new Error('This browser cannot record video.')
        await document.fonts?.ready
        layers.current = stageLayers(tier.size, record.title, record.sender, record.recipient, tier.watermark)
        const wav = await videoAudio(record.master, tier)
        if (cancelled) return
        src = URL.createObjectURL(wav); setAudioSrc(src)
        const a = audio.current!
        const ctx = new AudioContext()
        const node = ctx.createMediaElementSource(a), dest = ctx.createMediaStreamDestination(), mon = ctx.createGain()
        node.connect(dest); node.connect(mon); mon.connect(ctx.destination); monitorGain.current = mon
        const stream = new MediaStream([...out.current!.captureStream(FPS).getVideoTracks(), ...dest.stream.getAudioTracks()])
        const recorder = new MediaRecorder(stream, { mimeType: mime, videoBitsPerSecond: tier.bitrate, audioBitsPerSecond: 192_000 })
        const chunks: Blob[] = []
        recorder.ondataavailable = (e) => e.data.size && chunks.push(e.data)
        recorder.onstop = () => {
          if (cancelled) return
          const blob = new Blob(chunks, { type: mime.split(';')[0] })
          setResult({ url: URL.createObjectURL(blob), blob, ext: mime.startsWith('video/mp4') ? 'mp4' : 'webm' })
          setPhase('done'); ctx.close()
        }
        rec.current = { recorder, ctx, started: false, endedAt: 0 }
        setPhase('cueing')
        setTimeout(() => !cancelled && setEngaged(true), CUE_DELAY)
      } catch (e) {
        if (!cancelled) { setError(e instanceof Error ? e.message : 'Could not prepare the video.'); setPhase('error') }
      }
    })()
    return () => {
      cancelled = true
      const r = rec.current
      if (r) { if (r.recorder.state !== 'inactive') r.recorder.stop(); r.ctx.close().catch(() => {}) }
      if (src) URL.revokeObjectURL(src)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])
  useEffect(() => () => { if (result) URL.revokeObjectURL(result.url) }, [result])
  useEffect(() => { if (monitorGain.current) monitorGain.current.gain.value = monitor ? 1 : 0 }, [monitor])

  const onFrame = (gl: HTMLCanvasElement) => {
    const c = out.current, L = layers.current, r = rec.current
    if (!c || !L || !r || phase === 'done' || phase === 'error') return
    if (!r.started) { r.started = true; r.recorder.start(250); startedAt.current = performance.now() }
    const g = c.getContext('2d')!, s = tier.size
    const t = performance.now() - startedAt.current
    g.globalAlpha = 1; g.drawImage(L.bg, 0, 0); g.drawImage(gl, 0, 0, s, s)
    const intro = 1 - Math.max(0, Math.min(1, (t - INTRO) / FADE))
    if (intro > 0) { g.globalAlpha = intro; g.drawImage(L.intro, 0, 0); g.globalAlpha = 1 }
    const a = audio.current
    if (a && audioDur && (phase === 'recording' || r.endedAt)) {
      const bar = Math.max(3, s * 0.006), p = Math.min(1, a.currentTime / audioDur)
      g.fillStyle = '#1a1410'; g.fillRect(0, s - bar, s, bar)
      g.fillStyle = '#f59e0b'; g.fillRect(0, s - bar, s * p, bar)
    }
    if (L.outro && r.endedAt) {
      const o = Math.max(0, Math.min(1, (performance.now() - r.endedAt - TAIL) / FADE))
      if (o > 0) { g.globalAlpha = o; g.drawImage(L.outro, 0, 0); g.globalAlpha = 1 }
    }
    setElapsed((performance.now() - startedAt.current) / 1000)
  }

  const onContact = (down: boolean) => {
    if (!down || phase !== 'cueing') return
    rec.current?.ctx.resume()
    audio.current?.play().then(() => setPhase('recording')).catch(() => { setError('Playback was blocked by the browser.'); setPhase('error') })
  }
  const onEnded = () => {
    const r = rec.current; if (!r) return
    r.endedAt = performance.now(); setEngaged(false)
    setTimeout(() => { if (r.recorder.state !== 'inactive') r.recorder.stop() }, TAIL + (tier.watermark ? OUTRO : 0))
  }

  const fileName = `${record.title.replace(/[\\/:*?"<>|]/g, '_').trim() || 'Vynyl record'}.${result?.ext ?? 'mp4'}`
  const download = () => { if (!result) return; const a = document.createElement('a'); a.href = result.url; a.download = fileName; a.click() }
  const file = result && new File([result.blob], fileName, { type: result.blob.type })
  const canShare = !!file && !!navigator.canShare?.({ files: [file] })
  const share = () => { if (file) navigator.share({ files: [file], title: record.title }).catch(() => {}) }

  const est = audioDur + (CUE_DELAY + 2500 + TAIL + (tier.watermark ? OUTRO : 0)) / 1000
  const progress = phase === 'done' ? 1 : Math.min(0.99, elapsed / est)
  const upgrade = () => { onClose(); openPaywall('Export full-length, 1080p turntable videos without the watermark with Vynyl Pro.') }

  return (
    <div className="absolute inset-0 z-40 flex flex-col bg-obsidian/95 backdrop-blur-sm" role="dialog" aria-modal="true" aria-labelledby="video-export-title">
      <audio ref={audio} src={audioSrc} preload="auto" onLoadedMetadata={(e) => setAudioDur(e.currentTarget.duration)} onEnded={onEnded} />
      {/* the deck being filmed: rendered at export resolution, composited into the visible canvas below */}
      {phase !== 'done' && phase !== 'error' && (
        <div className="pointer-events-none absolute h-px w-px overflow-hidden opacity-0" aria-hidden>
          <Turntable className="h-px w-px" style={style} label={label} engaged={engaged} progress={0} playbackRef={audio} onContact={onContact}
            labelPhoto={labelPhoto} nameplate={pro ? { from: record.sender, to: record.recipient } : undefined} captureSize={tier.size} onFrame={onFrame} />
        </div>
      )}

      <header className="flex items-center justify-between px-5 pt-4">
        <div>
          <p className="deco text-[10px] text-amber-bright">{phase === 'done' ? 'Your video is ready' : 'Pressing your video'}</p>
          <h2 id="video-export-title" className="mt-1 font-display text-2xl leading-tight">
            {phase === 'error' ? 'Something went wrong' : phase === 'done' ? 'A record worth sharing.' : phase === 'recording' ? 'Rolling…' : 'Cueing the needle…'}
          </h2>
        </div>
        <span className={`flex items-center gap-1.5 font-mono text-[11px] ${phase === 'recording' ? 'text-err' : 'text-muted'}`}>
          <span className={`h-2 w-2 rounded-full ${phase === 'recording' ? 'animate-pulse bg-err shadow-[0_0_8px_#f87171]' : phase === 'done' ? 'bg-ok' : 'bg-cream/25'}`} />
          {phase === 'recording' || phase === 'cueing' ? `REC ${fmt(elapsed)}` : phase === 'done' ? 'DONE' : ''}
        </span>
      </header>

      <div className="relative mx-5 mt-4 aspect-square overflow-hidden rounded-2xl border border-brass/30 bg-black shadow-[0_24px_60px_-20px_black]">
        {result ? <video src={result.url} controls autoPlay playsInline className="h-full w-full" />
          : <canvas ref={out} width={tier.size} height={tier.size} className="h-full w-full" />}
        {phase === 'preparing' && <p className="absolute inset-0 grid place-items-center text-xs text-muted">Preparing the soundtrack…</p>}
        <span className="absolute left-3 top-3 rounded-full border border-brass/40 bg-obsidian/70 px-2.5 py-1 font-mono text-[10px] text-cream/80">{tier.size}p · {FPS}fps</span>
      </div>

      <div className="mt-4 flex-1 overflow-y-auto px-5 pb-5">
        {phase === 'error' ? <p role="alert" className="text-xs leading-relaxed text-err">{error}</p> : (
          <>
            <div className="h-1.5 overflow-hidden rounded-full bg-brass/20"><div className="h-full rounded-full bg-gradient-to-r from-amber to-amber-bright transition-[width] duration-300" style={{ width: `${progress * 100}%` }} /></div>
            <div className="mt-1.5 flex justify-between font-mono text-[11px] text-muted">
              <span>{pro ? '1080p · full length' : `720p · ${FREE_VIDEO_SECONDS} s preview`}</span><span className="text-amber-bright">{Math.round(progress * 100)}%</span>
            </div>
            {phase !== 'done' && <p className="mt-3 text-[11px] leading-relaxed text-muted">Filmed in real time on the 3D deck. The music starts the moment the needle touches the groove.
              <button type="button" onClick={() => setMonitor((m) => !m)} className="ml-1 text-amber-bright underline underline-offset-2">{monitor ? 'Mute preview' : 'Listen along'}</button></p>}
          </>
        )}
        <div className="mt-4 flex gap-2">
          {phase === 'done' ? (
            <>
              <button type="button" onClick={download} className="min-h-11 flex-1 rounded-full border border-amber/60 bg-amber/20 px-4 text-sm font-medium text-cream transition hover:bg-amber/30 active:scale-[.98]">Save video</button>
              {canShare && <button type="button" onClick={share} className="min-h-11 flex-1 rounded-full border border-brass/40 px-4 text-sm text-cream transition hover:border-amber-bright active:scale-[.98]">Share</button>}
              <button type="button" onClick={onClose} className="min-h-11 rounded-full px-4 text-sm text-muted transition hover:text-cream">Done</button>
            </>
          ) : <button type="button" onClick={onClose} className="min-h-11 flex-1 rounded-full border border-brass/40 px-4 text-sm text-cream transition hover:border-amber-bright active:scale-[.98]">{phase === 'error' ? 'Close' : 'Cancel'}</button>}
        </div>
        {!pro && (
          <button type="button" onClick={upgrade} className="mt-3 inline-flex items-center gap-2 rounded-full border border-amber/40 bg-amber/10 py-1 pl-1.5 pr-3 text-[11px] text-cream/85 transition hover:border-amber-bright">
            <ProBadge />Full length, 1080p and no watermark
          </button>
        )}
      </div>
    </div>
  )
}
