import { useEffect, useRef, useState } from 'react'
import Turntable from '../components/Turntable'
import PhotoAdjust, { DEFAULT_ADJUST, drawAdjusted } from '../components/PhotoAdjust'
import NameplatePreview from '../components/NameplatePreview'
import { ProBadge } from '../components/Paywall'
import VideoExport from '../components/VideoExport'
import { FREE, FREE_VIDEO_SECONDS, isFree, openPaywall, usePro, watermarkWav } from '../lib/pro'
import { playNeedleDrop, withNeedleDrop } from '../lib/dsp'
import { Wave, fmt } from '../components/ui'
import { PRESETS, STYLES } from '../lib/presets'
import { db, type PhotoAdjust as Adjust, type StoredRecord } from '../lib/db'

export default function Player({ record, onClose, onEdit, onUseMood }: { record: StoredRecord; onClose: () => void; onEdit?: (r: StoredRecord) => void; onUseMood?: (moodId: string) => void }) {
  // example records from the Samples shelf: listen only, nothing is saved
  const isSample = !!record.sample
  const audio = useRef<HTMLAudioElement>(null)
  const [url, setUrl] = useState<string>()
  useEffect(() => {
    const objectUrl = URL.createObjectURL(record.master)
    setUrl(objectUrl)
    return () => URL.revokeObjectURL(objectUrl)
  }, [record.master])
  const pro = usePro().pro
  const [exporting, setExporting] = useState(false)
  const [video, setVideo] = useState(false)
  const [platePreview, setPlatePreview] = useState(false)
  const [engaged, setEngaged] = useState(false)
  const [contact, setContact] = useState(false)
  const landed = useRef(false)
  const editsLeft = pro ? Infinity : Math.max(0, FREE.reedits - (record.reedits ?? 0))
  const backToStudio = async () => {
    if (!record.voice) return
    if (editsLeft <= 0) {
      openPaywall('Free records can go back to the Studio once. Go Pro to re-tune the music, crackle and mood as often as you like.')
      return
    }
    const next: StoredRecord = pro ? record : { ...record, reedits: (record.reedits ?? 0) + 1 }
    setEngaged(false)
    if (!pro) await db.put(next)
    onEdit?.(next)
  }
  const [t, setT] = useState(0)
  const [reset, setReset] = useState(0)
  const [styleId, setStyleId] = useState(record.styleId)
  const [full, setFull] = useState(false)
  const photoInput = useRef<HTMLInputElement>(null)
  const lastPlayedAt = useRef(record.lastPlayedAt)
  const [labelPhoto, setLabelPhoto] = useState(record.labelPhoto)
  const [photoPreview, setPhotoPreview] = useState<string>()
  const [photoBusy, setPhotoBusy] = useState(false)
  const [photoError, setPhotoError] = useState('')
  const [original, setOriginal] = useState(record.labelPhotoOriginal)
  const [adjust, setAdjust] = useState(record.labelPhotoAdjust)
  const [editing, setEditing] = useState<{ source: Blob; initial?: Adjust } | null>(null)
  useEffect(() => {
    if (!labelPhoto) { setPhotoPreview(undefined); return }
    const photoUrl = URL.createObjectURL(labelPhoto)
    setPhotoPreview(photoUrl)
    return () => URL.revokeObjectURL(photoUrl)
  }, [labelPhoto])
  const style = STYLES.find((s) => s.id === styleId)!
  const preset = PRESETS.find((p) => p.id === record.presetId)!
  const dur = record.duration, progress = dur ? t / dur : 0

  // audio starts only at needle contact
  useEffect(() => {
    if (engaged && contact && !landed.current) playNeedleDrop()
    landed.current = engaged && contact
    const a = audio.current!
    if (engaged && contact) a.play().catch(() => setEngaged(false))
    else a.pause()
  }, [engaged, contact])

  const seek = (s: number) => { const a = audio.current!; a.currentTime = Math.max(0, Math.min(dur, s)); setT(a.currentTime) }
  const toggle = () => {
    if (!engaged && t >= dur - 0.05) seek(0)
    setEngaged((e) => !e); navigator.vibrate?.(10)
    if (!engaged) {
      lastPlayedAt.current = Date.now()
      if (!isSample) void db.put({ ...record, styleId, labelPhoto, labelPhotoOriginal: original, labelPhotoAdjust: adjust, lastPlayedAt: lastPlayedAt.current })
    }
  }
  const pickPhoto = (file: File) => {
    setPhotoError('')
    if (file.size > 20 * 1024 * 1024) { setPhotoError('Choose a photo smaller than 20 MB.'); return }
    if (pro) { setEditing({ source: file }); return }
    // free tier: place the photo with the default crop; the adjust studio is Pro
    void (async () => {
      setPhotoBusy(true)
      try {
        const img = await createImageBitmap(file), c = document.createElement('canvas'); c.width = c.height = 1024
        drawAdjusted(c.getContext('2d')!, img, DEFAULT_ADJUST, 1024, style.label)
        const blob = await new Promise<Blob | null>((r) => c.toBlob(r, 'image/jpeg', 0.9))
        if (blob) await savePhoto(blob, file, DEFAULT_ADJUST); else setPhotoError('Could not prepare this photo.')
      } catch { setPhotoError('Could not read this photo.') } finally { setPhotoBusy(false) }
    })()
  }
  const exportWav = async () => {
    const name = `${record.title}.wav`
    setExporting(true)
    try {
      const withDrop = await withNeedleDrop(record.master)
      const blob = pro ? withDrop : await watermarkWav(withDrop), href = URL.createObjectURL(blob)
      const a = document.createElement('a'); a.href = href; a.download = name; a.click()
      setTimeout(() => URL.revokeObjectURL(href), 4000)
    } finally { setExporting(false) }
  }
  const savePhoto = async (photo?: Blob, source?: Blob, next?: Adjust) => {
    setPhotoError('')
    setPhotoBusy(true)
    try {
      await db.put({ ...record, styleId, labelPhoto: photo, labelPhotoOriginal: source, labelPhotoAdjust: next, lastPlayedAt: lastPlayedAt.current })
      setLabelPhoto(photo); setOriginal(source); setAdjust(next); setEditing(null)
    } catch {
      setPhotoError('Could not save this photo. Check available device storage and try again.')
    } finally { setPhotoBusy(false) }
  }
  const status = !engaged ? (t > 0 && t < dur ? 'Paused' : 'Idle') : contact ? 'Playing' : 'Needle dropping…'

  return (
    <div className="absolute inset-0 z-30 flex flex-col bg-obsidian">
      <audio ref={audio} src={url} onTimeUpdate={(e) => setT(e.currentTarget.currentTime)} onEnded={() => { setEngaged(false); setT(dur) }} />
      <div className="pointer-events-none absolute inset-0" style={{ background: 'radial-gradient(80% 50% at 50% 30%, rgba(217,119,6,.22), transparent 70%), radial-gradient(60% 40% at 50% 100%, rgba(153,27,27,.18), transparent)' }} />
      <header className="relative z-10 flex items-center justify-between px-5 pt-3">
        <button onClick={onClose} className="flex min-h-10 items-center gap-1.5 rounded-full border border-brass/30 bg-obsidian/80 px-3 text-xs text-cream/85 transition hover:border-amber-bright hover:text-cream active:scale-95" aria-label="Back to vault"><span className="text-amber-bright" aria-hidden>‹</span>Vault</button>
        <span className="flex items-center gap-1.5 font-mono text-[11px] text-amber-bright" aria-live="polite"><span className={`h-1.5 w-1.5 rounded-full ${engaged && contact ? 'animate-pulse bg-amber-bright shadow-[0_0_8px_#f59e0b]' : 'bg-cream/25'}`} />{status}</span>
        <div className="flex gap-1">
          <button onClick={() => setReset((r) => r + 1)} aria-label="Reset view" title="Reset view" className="flex min-h-10 items-center gap-1.5 rounded-full border border-brass/30 bg-obsidian/80 px-3 text-xs text-cream/85 transition hover:border-amber-bright hover:text-cream active:scale-95 w-10 justify-center px-0"><svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"><path d="M4 12a8 8 0 1 0 2.3-5.7M4 4v4h4" /></svg></button>
          <button onClick={() => setFull((f) => !f)} aria-label={full ? 'Exit full view' : 'Full view'} aria-pressed={full} className="flex min-h-10 items-center gap-1.5 rounded-full border border-brass/30 bg-obsidian/80 px-3 text-xs text-cream/85 transition hover:border-amber-bright hover:text-cream active:scale-95"><svg viewBox="0 0 24 24" className="h-3.5 w-3.5" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round">{full ? <path d="M9 4v5H4M15 4v5h5M9 20v-5H4M15 20v-5h5" /> : <path d="M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5" />}</svg>{full ? 'Exit' : 'Full'}</button>
        </div>
      </header>

      <div className={`relative ${full ? 'flex-1' : 'h-[46%]'} transition-all`}>
        <Turntable className="absolute inset-0" style={style} label={{ title: record.title, recipient: record.recipient, side: record.sideA, date: record.date }}
          engaged={engaged} progress={progress} onContact={setContact} resetKey={reset} playbackRef={audio} labelPhoto={labelPhoto} nameplate={pro ? { from: record.sender, to: record.recipient } : undefined} />
        <input ref={photoInput} type="file" accept="image/jpeg,image/png,image/webp" aria-label="Choose a loved-one photo" className="hidden" onChange={(event) => { const file = event.currentTarget.files?.[0]; event.currentTarget.value = ''; if (file) pickPhoto(file) }} />
        {!isSample && (
        <button type="button" disabled={photoBusy} onClick={() => photoInput.current?.click()} aria-label={labelPhoto ? 'Change record label photo' : 'Add record label photo'} className="absolute left-4 top-3 z-10 flex min-h-10 items-center gap-2 rounded-full border border-amber/50 bg-obsidian py-1 pl-1 pr-3.5 text-xs shadow-[0_8px_20px_-10px_black] active:scale-95 text-cream transition hover:border-amber-bright hover:bg-panel disabled:cursor-wait disabled:opacity-50">
          {photoPreview ? <img src={photoPreview} alt="" className="h-7 w-7 rounded-full object-cover ring-1 ring-amber/60" /> : <span className="grid h-7 w-7 place-items-center rounded-full bg-amber/15 text-base text-amber-bright" aria-hidden="true">＋</span>}
          {photoBusy ? 'Saving photo…' : labelPhoto ? 'Change photo' : 'Add loved-one photo'}
        </button>
        )}
        {full && photoError && <p role="alert" className="absolute inset-x-4 bottom-10 rounded-lg border border-err/30 bg-obsidian/90 p-3 text-xs leading-relaxed text-err">{photoError}</p>}
        <p className="pointer-events-none absolute bottom-2 w-full text-center text-[10px] text-muted/60">Drag to orbit · pinch to zoom · double-tap to reset</p>
      </div>

      {!full && (
        <div className="no-scrollbar relative flex-1 overflow-y-auto px-6 pb-4">
          <p className="deco text-[10px] text-amber-bright">{record.occasion} · {record.date}</p>
          <h1 className="mt-1 font-display text-3xl leading-tight">{record.title}</h1>
          <p className="text-sm text-muted">for {record.recipient} · from {record.sender}</p>
          {isSample ? (
            <p className="mt-2 inline-flex items-center gap-2 rounded-full border border-amber/40 bg-amber/10 px-3 py-1 text-[11px] text-cream/85">Sample record · plays only, nothing is saved</p>
          ) : (
            <div className="mt-2 flex flex-wrap items-center gap-2">
              {!pro && (
                <button type="button" onClick={() => setPlatePreview(true)} className="inline-flex items-center gap-2 rounded-full border border-amber/40 bg-amber/10 py-1 pl-1.5 pr-3 text-[11px] text-cream/85 transition hover:border-amber-bright">
                  <ProBadge />Gold nameplate on the plinth
                </button>
              )}
              {record.voice ? (
                <button type="button" onClick={() => void backToStudio()} className="inline-flex items-center gap-1.5 rounded-full border border-brass/35 px-3 py-1 text-[11px] text-cream/85 transition hover:border-amber-bright">
                  <span>↶ Back to Studio</span>
                  {!pro && (editsLeft > 0 ? <span className="text-muted">· {editsLeft} free</span> : <ProBadge />)}
                </button>
              ) : (
                <p className="w-full text-[11px] text-muted">Pressed before re-editing existed — record it again to change its sound.</p>
              )}
            </div>
          )}

          <div className="mt-4 h-10 cursor-pointer" onClick={(e) => { const r = e.currentTarget.getBoundingClientRect(); seek(((e.clientX - r.left) / r.width) * dur) }}>
            <Wave data={record.wave} progress={progress} className="h-full" />
          </div>
          <input type="range" min={0} max={dur} step={0.1} value={t} onChange={(e) => seek(+e.target.value)} aria-label="Seek" className="sr-only" />
          <div className="mt-1 flex justify-between font-mono text-[11px] text-muted"><span>{fmt(t)}</span><span>-{fmt(Math.max(0, dur - t))}</span></div>

          <div className="mt-4 flex items-center justify-center gap-7">
            <button onClick={() => seek(t - 10)} className="group grid h-12 w-12 place-items-center rounded-full border border-brass/40 bg-[linear-gradient(180deg,#2a241e,#16130f)] text-cream/85 shadow-[inset_0_1px_0_rgba(254,243,199,.08),0_8px_18px_-10px_black] transition hover:border-amber-bright hover:text-cream active:scale-90" aria-label="Back 10 seconds">
              <span className="relative grid place-items-center"><svg viewBox="0 0 24 24" className="h-8 w-8 transition group-hover:-rotate-12" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round"><path d="M5 12a7 7 0 1 0 2-4.9M5 4v3.5h3.5" /></svg><span className="absolute font-mono text-[9px] font-medium">10</span></span>
            </button>
            <button onClick={toggle} aria-label={engaged ? 'Pause' : 'Play'} className="relative grid h-[76px] w-[76px] place-items-center rounded-full bg-gradient-to-b from-[#f0c86a] via-[#b8862e] to-[#6b4210] p-[3px] shadow-[0_14px_36px_-10px_rgba(217,119,6,.75)] transition active:scale-95">
              {engaged && contact && <span className="absolute inset-0 animate-ping rounded-full bg-amber/20 [animation-duration:2s]" aria-hidden />}
              <span className="relative grid h-full w-full place-items-center rounded-full bg-[radial-gradient(circle_at_35%_28%,#fbbf24,#d97706_55%,#92400e)] shadow-[inset_0_2px_6px_rgba(255,255,255,.25),inset_0_-4px_10px_rgba(0,0,0,.35)]">
                {engaged ? <span className="flex gap-1.5"><span className="h-6 w-2 rounded-sm bg-obsidian" /><span className="h-6 w-2 rounded-sm bg-obsidian" /></span> : <span className="ml-1.5 h-0 w-0 border-y-[12px] border-l-[19px] border-y-transparent border-l-obsidian" />}
              </span>
            </button>
            <button onClick={() => seek(t + 10)} className="group grid h-12 w-12 place-items-center rounded-full border border-brass/40 bg-[linear-gradient(180deg,#2a241e,#16130f)] text-cream/85 shadow-[inset_0_1px_0_rgba(254,243,199,.08),0_8px_18px_-10px_black] transition hover:border-amber-bright hover:text-cream active:scale-90" aria-label="Forward 10 seconds">
              <span className="relative grid place-items-center"><svg viewBox="0 0 24 24" className="h-8 w-8 transition group-hover:rotate-12" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round"><path d="M19 12a7 7 0 1 1-2-4.9M19 4v3.5h-3.5" /></svg><span className="absolute font-mono text-[9px] font-medium">10</span></span>
            </button>
          </div>

          <blockquote className="mt-5 border-l-2 border-brass/50 pl-4 font-display text-lg italic leading-snug text-cream/90">“{record.dedication}”</blockquote>

          <div className="no-scrollbar -mx-1 mt-5 flex gap-2 overflow-x-auto px-1 py-1.5">
            {STYLES.map((s) => <button key={s.id} onClick={() => (pro || isFree('style', s.id) ? setStyleId(s.id) : openPaywall(`${s.name} wax is part of Vynyl Pro.`))} aria-label={pro || isFree('style', s.id) ? s.name : `${s.name} (Pro)`} aria-pressed={s.id === styleId} className={`relative h-9 w-9 shrink-0 rounded-full ${pro || isFree('style', s.id) ? '' : 'opacity-60'} shadow-[0_4px_10px_-4px_black] ring-offset-2 ring-offset-obsidian transition active:scale-90 ${s.id === styleId ? 'scale-110 ring-2 ring-amber-bright' : 'ring-1 ring-brass/25 hover:ring-brass/60'}`} style={{ background: `radial-gradient(${s.label} 30%, ${s.disc} 32%)` }} />)}
            <span className="ml-auto shrink-0 self-center whitespace-nowrap rounded-full border border-brass/30 px-3 py-1 text-[11px] text-muted">{preset.name}</span>
          </div>
          {isSample ? (
            <div className="mt-5 rounded-2xl border border-amber/40 bg-amber/10 p-4">
              <p className="font-display text-lg leading-tight text-cream">Like how this sounds?</p>
              <p className="mt-1 text-sm leading-relaxed text-muted">Use the same mood for your own record. Then record your voice and tap Next.</p>
              <button type="button" onClick={() => record.sampleMood && onUseMood?.(record.sampleMood)}
                className="mt-3 flex min-h-12 w-full items-center justify-center rounded-full bg-gradient-to-b from-[#f0c86a] via-[#c99234] to-[#8a5a1a] text-base font-semibold text-obsidian shadow-[0_12px_30px_-10px_rgba(245,158,11,.9)] active:scale-[.98]">
                Use this mood for my record
              </button>
            </div>
          ) : (
            <>
            <section className="mt-4 rounded-xl border border-brass/25 bg-panel/70 p-3" aria-labelledby="record-photo-title">
              <div className="flex items-center gap-3">
                <div className="grid h-12 w-12 shrink-0 place-items-center overflow-hidden rounded-full border border-amber/40 bg-stone">
                  {photoPreview ? <img src={photoPreview} alt="Your record label photo" className="h-full w-full object-cover" /> : <span className="font-display text-xl text-amber-bright" aria-hidden="true">♡</span>}
                </div>
                <div className="min-w-0 flex-1">
                  <h2 id="record-photo-title" className="font-display text-sm">A face on the record.</h2>
                  <p className="mt-0.5 text-[11px] leading-relaxed text-muted">A loved-one photo, on the spinning label.</p>
                </div>
              </div>
              <div className="mt-2 flex items-center gap-2">
                <button type="button" disabled={photoBusy} onClick={() => photoInput.current?.click()} className="min-h-11 flex-1 rounded-full border border-amber/50 bg-amber/15 px-3 text-xs font-medium text-cream transition hover:bg-amber/25 active:scale-[.98] disabled:cursor-wait disabled:opacity-50">{photoBusy ? 'Saving photo…' : labelPhoto ? 'Replace photo' : 'Add a loved-one photo'}</button>
                {labelPhoto && <button type="button" disabled={photoBusy} onClick={() => (pro ? setEditing({ source: original ?? labelPhoto, initial: original ? adjust : undefined }) : openPaywall('Crop, zoom, rotate and show the whole photo on the label with Vynyl Pro.'))} className="flex min-h-11 items-center gap-1.5 rounded-full border border-brass/35 px-4 text-xs text-cream transition hover:border-amber-bright active:scale-95 disabled:opacity-50">Adjust{!pro && <ProBadge />}</button>}
                {labelPhoto && <button type="button" disabled={photoBusy} onClick={() => void savePhoto()} className="min-h-11 rounded-full px-3 text-xs text-muted transition hover:bg-err/10 hover:text-err disabled:opacity-50">Remove</button>}
              </div>
              <p className="mt-1 text-[10px] text-muted/80">{adjust?.mode === 'fit' ? 'Full display' : 'Cropped to fill'} · saved only on this device</p>
              {photoError && <p role="alert" className="mt-2 text-xs leading-relaxed text-err">{photoError}</p>}
            </section>
            <button type="button" onClick={() => void exportWav()} disabled={exporting} className="mt-4 flex min-h-12 w-full items-center justify-center gap-2 rounded-full border border-brass/45 bg-[linear-gradient(180deg,#26211c,#17140f)] text-sm text-cream shadow-[inset_0_1px_0_rgba(254,243,199,.06)] transition hover:border-amber-bright active:scale-[.98] disabled:opacity-60"><svg viewBox="0 0 24 24" className="h-4 w-4 text-amber-bright" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"><path d="M12 4v11m0 0 4-4m-4 4-4-4M5 19h14" /></svg>{exporting ? 'Preparing…' : 'Export WAV master'}</button>
            {!pro && <p className="mt-2 text-center text-[11px] text-muted">Free exports end with a soft Vynyl chime. <button type="button" onClick={() => openPaywall('Export clean, full-quality WAV masters with Vynyl Pro.')} className="text-amber-bright underline underline-offset-2">Remove with Pro</button></p>}
            <button type="button" onClick={() => { setEngaged(false); setVideo(true) }} className="mt-3 flex min-h-12 w-full items-center justify-center gap-2 rounded-full border border-amber/55 bg-[linear-gradient(180deg,#3a2a14,#1c150d)] text-sm text-cream shadow-[inset_0_1px_0_rgba(254,243,199,.08)] transition hover:border-amber-bright active:scale-[.98]">
              <svg viewBox="0 0 24 24" className="h-4 w-4 text-amber-bright" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M4 7h11v10H4zM15 10l5-3v10l-5-3" /></svg>
              Export 3D turntable video{!pro && <ProBadge />}
            </button>
            <p className="mt-2 text-center text-[11px] text-muted">{pro ? 'Video · 1080p · full length, clean' : <>Free: first {FREE_VIDEO_SECONDS} s at 720p with a Vynyl watermark. <button type="button" onClick={() => openPaywall('Export full-length, 1080p turntable videos without the watermark with Vynyl Pro.')} className="text-amber-bright underline underline-offset-2">Go full length</button></>}</p>
            </>
          )}
        </div>
      )}
      {video && <VideoExport record={record} style={style} labelPhoto={labelPhoto} pro={pro} onClose={() => setVideo(false)} />}
      {platePreview && <NameplatePreview from={record.sender} to={record.recipient} onClose={() => setPlatePreview(false)} />}
      {editing && <PhotoAdjust source={editing.source} initial={editing.initial} labelColor={style.label} busy={photoBusy}
        onCancel={() => setEditing(null)} onSave={(photo, next) => void savePhoto(photo, editing.source, next)} />}
    </div>
  )
}
