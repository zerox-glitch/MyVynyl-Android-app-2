import { useEffect, useRef, useState } from 'react'
import type { PhotoAdjust as Adjust } from '../lib/db'

export const DEFAULT_ADJUST: Adjust = { mode: 'fill', zoom: 1, x: 0, y: 0, rot: 0, bg: 'blur' }

/** Draws the adjusted photo into a square canvas of `size` px. */
export function drawAdjusted(ctx: CanvasRenderingContext2D, img: ImageBitmap, a: Adjust, size: number, labelColor: string) {
  const turned = a.rot % 180 !== 0
  const w = turned ? img.height : img.width, h = turned ? img.width : img.height
  const cover = Math.max(size / w, size / h), contain = Math.min(size / w, size / h)
  const place = (scale: number, ox: number, oy: number) => {
    ctx.save()
    ctx.translate(size / 2 + ox * size, size / 2 + oy * size)
    ctx.rotate((a.rot * Math.PI) / 180)
    ctx.drawImage(img, (-img.width * scale) / 2, (-img.height * scale) / 2, img.width * scale, img.height * scale)
    ctx.restore()
  }
  ctx.clearRect(0, 0, size, size)
  if (a.mode === 'fit') {
    if (a.bg === 'blur') {
      ctx.filter = `blur(${Math.round(size / 24)}px) brightness(.7)`
      place(cover * 1.15, 0, 0)
      ctx.filter = 'none'
    } else { ctx.fillStyle = labelColor; ctx.fillRect(0, 0, size, size) }
    // inset so corners of the photo stay inside the circular label
    place(contain * 0.92, 0, 0)
  } else place(cover * a.zoom, a.x, a.y)
}

const clampPan = (a: Adjust, img: ImageBitmap) => {
  const turned = a.rot % 180 !== 0
  const w = turned ? img.height : img.width, h = turned ? img.width : img.height
  const s = Math.max(1 / w, 1 / h) * a.zoom
  const mx = Math.max(0, (w * s - 1) / 2), my = Math.max(0, (h * s - 1) / 2)
  return { ...a, x: Math.max(-mx, Math.min(mx, a.x)), y: Math.max(-my, Math.min(my, a.y)) }
}

type Props = { source: Blob; initial?: Adjust; labelColor: string; busy?: boolean; onCancel: () => void; onSave: (photo: Blob, adjust: Adjust) => void }

export default function PhotoAdjust({ source, initial, labelColor, busy, onCancel, onSave }: Props) {
  const canvas = useRef<HTMLCanvasElement>(null)
  const [img, setImg] = useState<ImageBitmap | null>(null)
  const [a, setA] = useState<Adjust>(initial ?? DEFAULT_ADJUST)
  const [error, setError] = useState('')
  const pointers = useRef(new Map<number, { x: number; y: number }>())
  const pinch = useRef(0)

  useEffect(() => {
    let bmp: ImageBitmap | undefined, live = true
    createImageBitmap(source).then((b) => { if (live) { bmp = b; setImg(b) } else b.close() }).catch(() => setError('This photo could not be opened. Try a JPG, PNG, or WebP.'))
    return () => { live = false; bmp?.close() }
  }, [source])

  useEffect(() => {
    const c = canvas.current
    if (!c || !img) return
    const size = c.width
    drawAdjusted(c.getContext('2d')!, img, a, size, labelColor)
  }, [img, a, labelColor])

  const update = (patch: Partial<Adjust>) => setA((prev) => (img ? clampPan({ ...prev, ...patch }, img) : { ...prev, ...patch }))

  const onDown = (e: React.PointerEvent) => { e.currentTarget.setPointerCapture(e.pointerId); pointers.current.set(e.pointerId, { x: e.clientX, y: e.clientY }) }
  const onUp = (e: React.PointerEvent) => { pointers.current.delete(e.pointerId); pinch.current = 0 }
  const onMove = (e: React.PointerEvent) => {
    const prev = pointers.current.get(e.pointerId)
    if (!prev || a.mode !== 'fill') return
    const box = e.currentTarget.getBoundingClientRect().width
    pointers.current.set(e.pointerId, { x: e.clientX, y: e.clientY })
    const pts = [...pointers.current.values()]
    if (pts.length === 2) {
      const d = Math.hypot(pts[0].x - pts[1].x, pts[0].y - pts[1].y)
      if (pinch.current) update({ zoom: Math.max(1, Math.min(4, a.zoom * (d / pinch.current))) })
      pinch.current = d
    } else update({ x: a.x + (e.clientX - prev.x) / box, y: a.y + (e.clientY - prev.y) / box })
  }

  const save = async () => {
    if (!img) return
    const out = document.createElement('canvas'); out.width = out.height = 768
    drawAdjusted(out.getContext('2d')!, img, a, 768, labelColor)
    out.toBlob((b) => b ? onSave(b, a) : setError('Could not prepare this photo.'), 'image/jpeg', 0.9)
  }

  const seg = (on: boolean) => `flex-1 rounded-full py-2 text-xs transition ${on ? 'bg-amber text-obsidian font-medium' : 'text-muted hover:text-cream'}`

  return (
    <div role="dialog" aria-modal="true" aria-labelledby="adjust-title" className="absolute inset-0 z-50 flex flex-col bg-obsidian/95 backdrop-blur-sm">
      <header className="flex items-center justify-between px-5 pt-4">
        <button type="button" onClick={onCancel} className="min-h-11 px-1 text-sm text-muted hover:text-cream">Cancel</button>
        <h2 id="adjust-title" className="font-display text-lg">Adjust photo</h2>
        <button type="button" onClick={save} disabled={!img || busy} className="min-h-11 px-1 text-sm font-medium text-amber-bright disabled:opacity-40">{busy ? 'Saving…' : 'Save'}</button>
      </header>

      <div className="no-scrollbar flex-1 overflow-y-auto px-6 pb-6">
        <div className="relative mx-auto mt-4 aspect-square w-full max-w-[290px]">
          <div className="absolute inset-0 rounded-full bg-[repeating-radial-gradient(circle,#181512_0_2px,#221d19_3px_4px)] shadow-[0_24px_60px_-20px_black,0_0_0_1px_rgba(180,83,9,.35)]" aria-hidden />
          <div className={`absolute inset-[9%] touch-none overflow-hidden rounded-full ring-2 ring-brass/60 ${a.mode === 'fill' ? 'cursor-grab active:cursor-grabbing' : ''}`}
            onPointerDown={onDown} onPointerMove={onMove} onPointerUp={onUp} onPointerCancel={onUp}
            onWheel={(e) => a.mode === 'fill' && update({ zoom: Math.max(1, Math.min(4, a.zoom * (e.deltaY < 0 ? 1.06 : 0.94))) })}
            onDoubleClick={() => update({ zoom: 1, x: 0, y: 0 })}>
            <canvas ref={canvas} width={560} height={560} className="h-full w-full" aria-label="Label photo preview" />
            {!img && !error && <span className="absolute inset-0 grid place-items-center text-xs text-muted">Opening photo…</span>}
          </div>
          <span className="pointer-events-none absolute left-1/2 top-1/2 h-3 w-3 -translate-x-1/2 -translate-y-1/2 rounded-full bg-obsidian ring-2 ring-cream/30" aria-hidden />
        </div>
        <p className="mt-3 text-center text-[11px] text-muted">{a.mode === 'fill' ? 'Drag to position · pinch or scroll to zoom · double-tap to reset' : 'The whole photo is shown, nothing is cut off'}</p>
        {error && <p role="alert" className="mt-2 text-center text-xs text-err">{error}</p>}

        <div className="mt-5 flex rounded-full border border-brass/30 bg-panel p-1" role="radiogroup" aria-label="Photo display">
          <button type="button" role="radio" aria-checked={a.mode === 'fill'} onClick={() => update({ mode: 'fill' })} className={seg(a.mode === 'fill')}>Crop to fill</button>
          <button type="button" role="radio" aria-checked={a.mode === 'fit'} onClick={() => update({ mode: 'fit' })} className={seg(a.mode === 'fit')}>Full display</button>
        </div>

        {a.mode === 'fill' ? (
          <label className="mt-5 flex items-center gap-3 text-xs text-muted">
            <span className="w-10 deco text-[10px]">Zoom</span>
            <input type="range" min={1} max={4} step={0.01} value={a.zoom} onChange={(e) => update({ zoom: +e.target.value })} className="flex-1 accent-amber" />
            <span className="w-10 text-right font-mono">{a.zoom.toFixed(1)}×</span>
          </label>
        ) : (
          <div className="mt-5 flex items-center gap-3 text-xs text-muted">
            <span className="w-16 deco text-[10px]">Edges</span>
            <div className="flex flex-1 rounded-full border border-brass/25 p-1">
              <button type="button" aria-pressed={a.bg === 'blur'} onClick={() => update({ bg: 'blur' })} className={seg(a.bg === 'blur')}>Soft blur</button>
              <button type="button" aria-pressed={a.bg === 'label'} onClick={() => update({ bg: 'label' })} className={seg(a.bg === 'label')}>Label color</button>
            </div>
          </div>
        )}

        <div className="mt-4 grid grid-cols-2 gap-2">
          <button type="button" onClick={() => update({ rot: (a.rot + 90) % 360 })} className="min-h-11 rounded-full border border-brass/30 text-xs text-cream hover:border-amber-bright">↻ Rotate 90°</button>
          <button type="button" onClick={() => setA({ ...DEFAULT_ADJUST, mode: a.mode, bg: a.bg })} className="min-h-11 rounded-full border border-brass/30 text-xs text-muted hover:text-cream">Reset</button>
        </div>
      </div>
    </div>
  )
}
