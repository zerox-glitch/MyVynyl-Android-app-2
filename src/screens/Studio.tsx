import { useEffect, useRef, useState } from 'react'
import { Btn, Eyebrow, Meter, Wave, fmt } from '../components/ui'
import { CRACKLES, MOODS, OCCASIONS, PRESETS, STYLES } from '../lib/presets'
import { CUSTOM_MUSIC, MUSIC, makeCustomBed, musicBed, musicName, setCustomMusic } from '../lib/music'
import { SR, decodeToMono, demoSources, demoVoice, encodeWav, renderMaster, waveform } from '../lib/dsp'
import { db, type StoredRecord, type VaultVoice } from '../lib/db'
import { sampleFor, sampleRecord } from '../lib/samples'
import Turntable from '../components/Turntable'
import MusicCrop from '../components/MusicCrop'
import { ProBadge, ProButton } from '../components/Paywall'
import { FREE, PRO_SECONDS, isFree, openPaywall, usePro, type Gate } from '../lib/pro'

const STEPS = ['Capture', 'Dedication', 'Character', 'Appearance', 'Press']
/** One plain sentence per step, shown under the progress bar. */
const HINTS = [
  'Record a voice or choose one, then tap Next.',
  'Say who it is for and write a short message. You can keep our example words.',
  'Pick a mood. Tap Listen to hear it for a few seconds, then tap Next.',
  'Choose the colour of your record, then tap Next.',
  'Tap Press record. It only takes a few seconds.',
]

export default function Studio({
  records,
  voices,
  recordCount,
  edit = null,
  onCancelEdit = () => {},
  onDone,
  onPlay,
  capture = 0,
  active = true,
  onStep = () => {},
  initialVoice = null,
  onClearInitialVoice = () => {},
  onVoicesChange = () => {},
  moodRequest = null,
}: {
  records: StoredRecord[]
  voices: VaultVoice[]
  recordCount: number
  edit?: StoredRecord | null
  onCancelEdit?: () => void
  onDone: (r: StoredRecord) => void
  onPlay: (r: StoredRecord) => void
  capture?: number
  active?: boolean
  onStep?: (step: number) => void
  initialVoice?: VaultVoice | null
  onClearInitialVoice?: () => void
  onVoicesChange?: () => void
  /** "Use this mood" from a sample record: applies the mood and moves on */
  moodRequest?: { id: string; n: number } | null
}) {
  const pro = usePro().pro
  const locked = (kind: Gate, id: string) => !pro && !isFree(kind, id)
  const gate = (kind: Gate, id: string, name: string, fn: () => void) => (locked(kind, id) ? openPaywall(`${name} is part of Vynyl Pro. Unlock it — and every other sound — below.`) : fn())
  const [step, setStep] = useState(0)
  useEffect(() => { onStep(step) }, [step, onStep])
  const [source, setSource] = useState<Float32Array | null>(null)
  const [srcName, setSrcName] = useState<string | null>(null)
  const [currentVoiceId, setCurrentVoiceId] = useState<string | null>(null)
  const [meta, setMeta] = useState({ title: 'The Porch Song', recipient: 'Nana Ruth', sender: 'Theo', dedication: 'For every summer evening you hummed this to me.', occasion: 'Grandparents', date: new Date().toISOString().slice(0, 10), sideA: 'Side A', sideB: 'Side B' })
  const [presetId, setPreset] = useState('warm')
  const [styleId, setStyle] = useState('ruby')
  const [crackleId, setCrackle] = useState('preset')
  const [music, setMusic] = useState('hearth')
  const [musicLevel, setMusicLevel] = useState(0.35)
  const [crackleLevel, setCrackleLevel] = useState(1)
  const [character, setCharacter] = useState(1)
  const [volume, setVolume] = useState(1)
  const [moodId, setMood] = useState<string | null>(null)
  const [withVoice, setWithVoice] = useState(true)
  /** the "Customise the sound yourself" panel */
  const [custom, setCustom] = useState(false)
  /** a mood picked before recording (from a sample record) */
  const [moodReady, setMoodReady] = useState(false)
  /** your own music: the whole decoded upload (for trimming) and the trimmed, loop-ready bed */
  const [customSource, setCustomSource] = useState<{ data: Float32Array; name: string } | null>(null)
  const [customBed, setCustomBed] = useState<{ bed: Float32Array; name: string } | null>(null)
  const [cropping, setCropping] = useState(false)
  const [customError, setCustomError] = useState('')
  const [pressed, setPressed] = useState<StoredRecord | null>(null)
  const hasVoice = !!source && !demoSources.has(source)
  const base = (pressed ? records.find((r) => r.id === pressed.id) ?? pressed : null) ?? edit
  const editsLeft = pro ? Infinity : Math.max(0, FREE.reedits - (base?.reedits ?? 0))

  const preview = usePreview(withVoice && hasVoice ? source : null)
  const example = useExample(() => preview.stop())
  useEffect(() => { if (!active) { preview.stop(); example.stop() } }, [active])
  useEffect(() => { if (step !== 2) { preview.stop(); example.stop() } else setMoodReady(false) }, [step])
  useEffect(() => { preview.apply({ musicLevel, crackleLevel, character, volume }) }, [musicLevel, crackleLevel, character, volume])

  useEffect(() => {
    if (!moodRequest) return
    applyMood(moodRequest.id)
    setPressed(null)
    if (source) setStep(2)
    else { setStep(0); setMoodReady(true) }
  }, [moodRequest?.n])

  useEffect(() => {
    if (capture > 0) {
      setPressed(null)
      setStep(0)
    }
  }, [capture])

  useEffect(() => {
    if (!initialVoice) return
    setSource(initialVoice.voice)
    setSrcName(initialVoice.name)
    setCurrentVoiceId(initialVoice.id)
    setPressed(null)
    setStep(0)
    onClearInitialVoice()
  }, [initialVoice, onClearInitialVoice])

  useEffect(() => {
    if (!edit || !edit.voice) return
    setPressed(null)
    setSource(edit.voice)
    setSrcName(null)
    setCurrentVoiceId(edit.id)
    setMeta({
      title: edit.title,
      recipient: edit.recipient,
      sender: edit.sender,
      dedication: edit.dedication,
      occasion: edit.occasion,
      date: edit.date,
      sideA: edit.sideA,
      sideB: edit.sideB,
    })
    if (edit.settings) {
      setPreset(edit.settings.presetId)
      setStyle(edit.settings.styleId)
      setCrackle(edit.settings.crackleId)
      setMusic(edit.settings.music)
      setMusicLevel(edit.settings.musicLevel)
      setCrackleLevel(edit.settings.crackleLevel)
      setCharacter(edit.settings.character)
      setVolume(edit.settings.volume)
      setMood(edit.settings.moodId ?? null)
    }
    if (edit.customMusic) {
      setCustomMusic(edit.customMusic)
      setCustomBed({ bed: edit.customMusic, name: edit.customMusicName ?? 'Your music' })
      setCustomSource(null)
    }
    setStep(2)
  }, [edit?.id])

  const reset = () => {
    setPressed(null)
    setStep(0)
    setSource(null)
    setSrcName(null)
    setCurrentVoiceId(null)
    onCancelEdit()
  }

  const makeChanges = async () => {
    const p = pressed ? base : null
    if (!p) { setStep(2); return }
    if (pro) { setStep(2); return }
    if (editsLeft <= 0) {
      openPaywall(`Free records can be changed ${FREE.reedits} times after pressing. Go Pro to change them as often as you like.`)
      return
    }
    const next: StoredRecord = { ...p, reedits: (p.reedits ?? 0) + 1 }
    setPressed(next)
    setStep(2)
    await db.put(next)
    onDone(next)
  }

  const listen = (key: string, over: { presetId?: string; crackleId?: string; music?: string; musicLevel?: number }) => {
    example.stop()
    void preview.toggle(key, { presetId: over.presetId ?? presetId, crackleId: over.crackleId ?? crackleId, music: over.music ?? music, musicLevel: over.musicLevel ?? musicLevel, crackleLevel, character, volume })
  }
  const applyMood = (id: string) => {
    const m = MOODS.find((x) => x.id === id)!
    setMood(id); setPreset(m.presetId); setCrackle(m.crackleId); setMusic(m.musicId); setMusicLevel(m.musicLevel); navigator.vibrate?.(8)
  }
  const pickUpload = async (f: File) => {
    setCustomError('')
    if (f.size > 60 * 1024 * 1024) { setCustomError('Choose a music file smaller than 60 MB.'); return }
    try {
      const data = await decodeToMono(f)
      setCustomSource({ data, name: f.name.replace(/\.[^.]+$/, '') }); setCropping(true)
    } catch { setCustomError('Could not read this music file. Try an MP3, M4A or WAV.') }
  }
  const saveCrop = (start: number, end: number) => {
    if (!customSource) return
    preview.stop()
    const bed = makeCustomBed(customSource.data, start, end)
    setCustomMusic(bed); setCustomBed({ bed, name: customSource.name })
    setMusic(CUSTOM_MUSIC); setMood(null); setCropping(false)
  }
  const mood = MOODS.find((m) => m.id === moodId)
  const soundSummary = `${PRESETS.find((p) => p.id === presetId)!.name} · ${CRACKLES.find((c) => c.id === crackleId)!.name} · ${musicName(music, customBed?.name)}`
  const [render, setRender] = useState<{ p: number; stage: string } | null>(null)
  const style = STYLES.find((s) => s.id === styleId)!

  return (
    <div className="relative flex h-full flex-col">
      <header className="px-6 pt-4">
        <div className="flex items-center justify-between gap-3"><Eyebrow>Step {step + 1} of {STEPS.length} · {STEPS[step]}</Eyebrow><ProButton /></div>
        {base && !(pressed && step === 4) && (
          <div className="mt-3 flex items-center justify-between rounded-full border border-amber/40 bg-amber/10 py-0.5 pl-3 pr-1">
            <span className="truncate text-xs text-cream/85">Changing “{base.title}”</span>
            <button type="button" disabled={!!render} onClick={reset} className="rounded-full px-3 py-2 text-xs text-muted transition hover:text-cream">
              New record
            </button>
          </div>
        )}
        <div className="mt-3 flex gap-1.5" role="progressbar" aria-valuenow={step + 1} aria-valuemax={5}>
          {STEPS.map((s, i) => <span key={s} className={`h-0.5 flex-1 rounded-full ${i <= step ? 'bg-amber' : 'bg-cream/10'}`} />)}
        </div>
        {!(pressed && step === 4) && <p className="mt-2 text-[13px] leading-snug text-cream/75">{HINTS[step]}</p>}
      </header>
      <div className="no-scrollbar relative flex-1 overflow-y-auto bg-[radial-gradient(120%_60%_at_50%_0%,rgba(217,119,6,.10),transparent_60%)] px-6 pb-8 pt-6">
        {step === 0 && moodReady && mood && (
          <p className="mb-5 rounded-2xl border border-amber/40 bg-amber/10 px-4 py-3 text-sm leading-relaxed text-cream">
            Mood ready: <span className="font-semibold text-amber-bright">{mood.name}</span>. Now record your voice, then tap Next.
          </p>
        )}
        {step === 0 && (
          <Capture
            source={source}
            srcName={srcName}
            voices={voices}
            max={pro ? PRO_SECONDS : FREE.maxSeconds}
            pro={pro}
            onVoiceRecorded={async (vid, name, data, dur) => {
              setCurrentVoiceId(vid)
              await db.saveVaultVoice({ id: vid, name, voice: data, duration: dur, isPro: pro })
              onVoicesChange()
            }}
            setSource={(s, name, vid) => {
              setSource(s)
              setSrcName(name)
              if (vid !== undefined) setCurrentVoiceId(vid)
              else if (!s) setCurrentVoiceId(null)
            }}
          />
        )}
        {step === 1 && <Dedication meta={meta} setMeta={setMeta} />}
        {step === 2 && (
          <div className="space-y-6">
            <div>
              <h2 className="font-display text-[34px] leading-[1.05] tracking-tight">How should it <em className="text-amber-bright">sound?</em></h2>
              <p className="mt-3 text-[15px] leading-relaxed text-cream/85">
                Just pick a mood — it chooses the sound, crackle and music for you.
              </p>
              <p className="mt-1.5 text-sm leading-relaxed text-muted">
                <span className="text-cream">Listen</span> plays {withVoice && hasVoice ? 'your own recording' : 'the sound'} in that mood for a few seconds. <span className="text-cream">Example</span> plays a message someone else made with it.
              </p>
            </div>

            <section aria-labelledby="moods-h">
              <SectionTitle id="moods-h" n="I">Choose a mood</SectionTitle>
              {example.error && <p role="alert" className="mb-3 text-xs text-err">{example.error}</p>}
              <div className="space-y-3">
                {MOODS.map((m) => {
                  const on = moodId === m.id, s = sampleFor(m.id), isLocked = locked('mood', m.id)
                  return (
                    <div key={m.id} className={`rounded-2xl border p-4 transition ${on ? 'border-amber bg-[linear-gradient(160deg,rgba(217,119,6,.22),rgba(217,119,6,.04))] shadow-[0_14px_30px_-20px_rgba(217,119,6,.9)]' : 'border-brass/20 bg-panel'}`}>
                      <button type="button" onClick={() => applyMood(m.id)} aria-pressed={on} className="flex w-full items-start gap-3 text-left">
                        <span className={`mt-0.5 grid h-6 w-6 shrink-0 place-items-center rounded-full border-2 text-xs font-bold ${on ? 'border-amber-bright bg-amber-bright text-obsidian' : 'border-brass/50 text-transparent'}`} aria-hidden>✓</span>
                        <span className="min-w-0 flex-1">
                          <span className="flex flex-wrap items-center gap-2"><span className="font-display text-xl leading-tight text-cream">{m.name}</span>{isLocked && <ProBadge />}</span>
                          <span className="mt-1 block text-sm leading-snug text-muted">{m.blurb}</span>
                          {s && <span className="mt-1 block text-xs text-cream/55">Example: a {s.persona.toLowerCase()}'s message</span>}
                        </span>
                      </button>
                      <div className="mt-3 flex flex-wrap gap-2 pl-9">
                        <ListenPill state={preview.state('mood:' + m.id)} label={withVoice && hasVoice ? 'Listen with my voice' : 'Listen'} name={m.name}
                          onClick={() => listen('mood:' + m.id, { presetId: m.presetId, crackleId: m.crackleId, music: m.musicId, musicLevel: m.musicLevel })} />
                        {s && <ListenPill ghost state={example.state(m.id)} label="Example" name={`${m.name} example`} onClick={() => { preview.stop(); void example.toggle(m.id) }} />}
                      </div>
                      {on && isLocked && <p className="mt-3 pl-9 text-xs leading-relaxed text-amber-bright">Free to listen. Pressing a record with this mood needs Vynyl Pro.</p>}
                    </div>
                  )
                })}
              </div>
            </section>

            <div className={`rounded-2xl border transition ${custom ? 'border-amber/50 bg-stone/60' : 'border-brass/30 bg-panel'}`}>
              <button type="button" onClick={() => setCustom((o) => !o)} aria-expanded={custom} aria-controls="custom-panel" className="flex min-h-16 w-full items-center gap-3 px-4 py-3 text-left">
                <span className="min-w-0 flex-1">
                  <span className="block font-display text-lg leading-tight text-cream">Customise the sound yourself</span>
                  <span className="mt-0.5 block text-xs text-muted">For experts: character, crackle, music, volumes and your own music.</span>
                  <span className="mt-1.5 block truncate text-xs text-amber-bright/90">{mood ? `Mood: ${mood.name}` : 'Your own mix'} · {soundSummary}</span>
                </span>
                <span className={`grid h-9 w-9 shrink-0 place-items-center rounded-full border border-brass/40 text-xl text-amber-bright transition-transform ${custom ? 'rotate-45' : ''}`} aria-hidden>+</span>
              </button>
            </div>

            {custom && (
            <div id="custom-panel" className="space-y-7">
            <div className="space-y-3">
              {hasVoice && (
                <button type="button" role="switch" aria-checked={withVoice} onClick={() => { preview.stop(); setWithVoice((v) => !v) }}
                  className="flex min-h-11 items-center gap-3 rounded-full border border-brass/30 bg-panel py-1.5 pl-1.5 pr-4 text-xs text-cream">
                  <span className={`relative h-6 w-11 rounded-full transition ${withVoice ? 'bg-amber' : 'bg-cream/15'}`}><span className={`absolute top-0.5 h-5 w-5 rounded-full bg-cream shadow transition-all ${withVoice ? 'left-[22px]' : 'left-0.5'}`} /></span>
                  Include my recording in previews
                </button>
              )}
              <div className="grid grid-cols-2 gap-2">
                <LevelBar label="Overall volume" value={volume} max={1} onChange={setVolume} />
                <LevelBar label="Music volume" value={musicLevel} max={1} onChange={setMusicLevel} ends={['Off', 'Full']} />
              </div>
            </div>

            <section aria-labelledby="char-h" className="space-y-3">
              <SectionTitle id="char-h" n="II">Character</SectionTitle>
              <LevelBar label="Character strength" value={character} max={1.5} onChange={setCharacter} ends={['Clean', 'Heavy']} locked={!pro} />
              {PRESETS.map((p, i) => (
                <div key={p.id} className={`relative rounded-2xl border transition ${presetId === p.id ? 'border-amber bg-[linear-gradient(135deg,rgba(217,119,6,.18),rgba(217,119,6,.04))] shadow-[0_16px_40px_-24px_rgba(217,119,6,.8)]' : 'border-brass/20 bg-panel hover:border-brass/50'}`}>
                  <button type="button" onClick={() => gate('preset', p.id, p.name, () => { setPreset(p.id); setMood(null); navigator.vibrate?.(8) })} aria-pressed={presetId === p.id} className="block w-full p-4 pr-16 text-left">
                    <span className="flex items-baseline gap-2 font-display text-xl"><span className="font-mono text-[11px] text-amber-bright/80">{['I', 'II', 'III', 'IV', 'V', 'VI', 'VII', 'VIII', 'IX', 'X', 'XI', 'XII', 'XIII', 'XIV', 'XV'][i]}</span>{p.name}{locked('preset', p.id) && <ProBadge className="self-center" />}</span>
                    <p className="mb-3 mt-1 text-[13px] text-muted">{p.blurb}</p>
                    <div className="space-y-1"><Meter n={p.warmth} label="Warmth" /><Meter n={p.age} label="Age" /><Meter n={p.texture} label="Texture" /></div>
                  </button>
                  <Listen className="absolute right-3 top-3" state={preview.state('preset:' + p.id)} label={`Preview ${p.name}`} onClick={() => listen('preset:' + p.id, { presetId: p.id })} />
                </div>
              ))}
            </section>

            <section aria-labelledby="crackle-h">
              <SectionTitle id="crackle-h" n="III">Crackle</SectionTitle>
              <p className="-mt-1 mb-3 text-[12px] text-muted">Every surface has its own voice. The strip shows how it falls across the groove.</p>
              <LevelBar label="Crackle volume" value={crackleLevel} max={2} onChange={setCrackleLevel} ends={['Off', 'Loud']} className="mb-3" locked={!pro} />
              <div className="grid grid-cols-2 gap-2.5">
                {CRACKLES.map((c) => {
                  const on = crackleId === c.id
                  // deterministic groove signature drawn from the crackle's character
                  let seed = [...c.id].reduce((a, ch) => a * 31 + ch.charCodeAt(0), 7) >>> 0
                  const rnd = () => ((seed = (seed * 1664525 + 1013904223) >>> 0) / 4294967296)
                  const ticks = Array.from({ length: Math.round(c.density * 3.2) }, () => ({ x: rnd() * 120, h: 2 + rnd() * 7 * Math.min(c.amp, 1.4), w: 0.6 + c.len * 0.5 }))
                  const pops = Array.from({ length: Math.round(c.pops * 0.9) }, () => ({ x: 4 + rnd() * 112, h: 9 + rnd() * 4 }))
                  const hiss = Math.max(0, Math.min(1, (c.hissDb + 30) / 36))
                  const level = c.id === 'silent' ? 0 : Math.min(5, Math.max(1, Math.round((c.density * c.amp) / 2.4 + c.pops / 2.2 + hiss)))
                  return (
                    <div key={c.id} className={`group relative overflow-hidden rounded-xl border transition duration-300 ${on ? 'border-amber bg-gradient-to-b from-amber/15 to-amber/[0.04] shadow-[0_0_0_1px_rgba(245,158,11,.25),0_12px_28px_-14px_rgba(217,119,6,.55)]' : 'border-brass/20 bg-panel hover:border-brass/50'}`}>
                      <button type="button" onClick={() => gate('crackle', c.id, c.name, () => { setCrackle(c.id); setMood(null); navigator.vibrate?.(6) })} aria-pressed={on} className="block w-full p-3 pr-11 text-left">
                        {locked('crackle', c.id) && <ProBadge className="mb-1" />}
                        <span className={`block font-display text-[15px] leading-tight ${on ? 'text-amber-bright' : 'text-cream'}`}>{c.name}</span>
                        <span className="mt-1 block min-h-[30px] text-[11px] leading-snug text-muted">{c.blurb}</span>
                        <span className={`mt-2.5 block rounded-md border px-1.5 py-1 ${on ? 'border-amber/40 bg-obsidian/70' : 'border-brass/15 bg-obsidian/50'}`}>
                          <svg viewBox="0 0 120 26" className="block h-[26px] w-full" aria-hidden="true">
                            <line x1="0" y1="13" x2="120" y2="13" stroke="currentColor" strokeWidth={0.6 + hiss * 1.6} className="text-brass" opacity={0.15 + hiss * 0.35} strokeDasharray={c.hissTone < 0.8 ? '3 1.5' : c.hissTone > 1.05 ? '0.8 0.8' : undefined} />
                            {ticks.map((t, k) => <rect key={k} x={t.x} y={13 - t.h / 2} width={t.w} height={t.h} rx={t.w / 2} className={on ? 'fill-amber-bright' : 'fill-cream/55'} opacity={0.45 + (k % 3) * 0.2} />)}
                            {pops.map((t, k) => <rect key={'p' + k} x={t.x} y={13 - t.h} width="1.6" height={t.h * 2} rx="0.8" className={on ? 'fill-amber' : 'fill-ruby'} />)}
                            {c.id === 'silent' && <text x="60" y="16.5" textAnchor="middle" className="fill-muted font-mono text-[8px] tracking-[0.3em]">HUSH</text>}
                          </svg>
                        </span>
                        <span className="mt-2 flex items-center justify-between font-mono text-[9px] uppercase tracking-[0.18em] text-muted">
                          <span>{level === 0 ? 'None' : ['Whisper', 'Gentle', 'Present', 'Rich', 'Heavy'][level - 1]}</span>
                          <span className="flex gap-[3px]">{[1, 2, 3, 4, 5].map((n) => <span key={n} className={`h-1.5 w-1.5 rounded-full ${n <= level ? (on ? 'bg-amber-bright shadow-[0_0_6px_rgba(251,191,36,.8)]' : 'bg-brass') : 'bg-brass/20'}`} />)}</span>
                        </span>
                      </button>
                      <Listen small className="absolute right-2 top-2" state={preview.state('crackle:' + c.id)} label={`Preview ${c.name} crackle`} onClick={() => listen('crackle:' + c.id, { crackleId: c.id })} />
                    </div>
                  )
                })}
              </div>
            </section>

            <section aria-labelledby="music-h">
              <SectionTitle id="music-h" n="IV">Background music</SectionTitle>
              <div className="overflow-hidden rounded-2xl border border-brass/20 bg-panel">
                {MUSIC.map((m, i) => (
                  <div key={m.id} className={`relative flex items-center ${i ? 'border-t border-brass/10' : ''} ${music === m.id ? 'bg-amber/10' : ''}`}>
                    <button type="button" onClick={() => gate('music', m.id, m.name, () => { setMusic(m.id); setMood(null) })} aria-pressed={music === m.id} className="flex min-h-14 flex-1 items-center gap-3 px-4 py-2.5 text-left">
                      <span className={`grid h-4 w-4 shrink-0 place-items-center rounded-full border ${music === m.id ? 'border-amber-bright' : 'border-brass/40'}`}>{music === m.id && <span className="h-2 w-2 rounded-full bg-amber-bright" />}</span>
                      <span className="min-w-0"><span className="flex items-center gap-1.5 text-[13px] font-medium text-cream">{m.name}{locked('music', m.id) && <ProBadge />}{m.romantic && <span className="rounded-full bg-ruby/30 px-1.5 py-px text-[9px] font-normal uppercase tracking-wider text-[#fca5a5]">♥ Romantic</span>}</span><span className="block truncate text-[11px] text-muted">{m.blurb}</span></span>
                    </button>
                    {m.id !== 'none' && <Listen small className="mr-3 shrink-0" state={preview.state('music:' + m.id)} label={`Preview ${m.name}`} onClick={() => listen('music:' + m.id, { music: m.id })} />}
                  </div>
                ))}
                {customBed && (
                  <div className={`relative flex items-center border-t border-brass/10 ${music === CUSTOM_MUSIC ? 'bg-amber/10' : ''}`}>
                    <button type="button" onClick={() => { setMusic(CUSTOM_MUSIC); setMood(null) }} aria-pressed={music === CUSTOM_MUSIC} className="flex min-h-14 min-w-0 flex-1 items-center gap-3 px-4 py-2.5 text-left">
                      <span className={`grid h-4 w-4 shrink-0 place-items-center rounded-full border ${music === CUSTOM_MUSIC ? 'border-amber-bright' : 'border-brass/40'}`}>{music === CUSTOM_MUSIC && <span className="h-2 w-2 rounded-full bg-amber-bright" />}</span>
                      <span className="min-w-0"><span className="block text-[13px] font-medium text-cream">Your own music</span><span className="block truncate text-[11px] text-muted">{customBed.name} · {fmt(customBed.bed.length / SR)}</span></span>
                    </button>
                    {customSource && <button type="button" onClick={() => setCropping(true)} className="mr-2 min-h-9 shrink-0 rounded-full border border-brass/40 px-3 text-xs text-cream transition hover:border-amber-bright">Trim</button>}
                    <Listen small className="mr-3 shrink-0" state={preview.state('music:custom')} label="Preview your music" onClick={() => listen('music:custom', { music: CUSTOM_MUSIC })} />
                  </div>
                )}
                <label className="flex min-h-14 cursor-pointer items-center gap-3 border-t border-brass/10 px-4 py-2.5 transition hover:bg-amber/5">
                  <span className="grid h-8 w-8 shrink-0 place-items-center rounded-full border border-amber/50 text-amber-bright" aria-hidden>↥</span>
                  <span className="min-w-0"><span className="block text-[13px] font-medium text-amber-bright">{customBed ? 'Upload different music' : 'Upload your own music'}</span><span className="block text-[11px] text-muted">A song from your device. You choose which part to use.</span></span>
                  <input type="file" accept="audio/*" className="sr-only" onChange={(e) => { const f = e.target.files?.[0]; e.target.value = ''; if (f) void pickUpload(f) }} />
                </label>
              </div>
              {customError && <p role="alert" className="mt-2 text-xs text-err">{customError}</p>}
              {music !== 'none' && <LevelBar label="Music volume" value={musicLevel} max={1} onChange={setMusicLevel} className="mt-3" />}
            </section>
            </div>
            )}
          </div>
        )}
        {step === 3 && (
          <div>
            <h2 className="font-display text-[34px] leading-[1.05] tracking-tight">Choose the <em className="text-amber-bright">wax.</em></h2>
            <div className="relative -mx-6 my-2 h-64"><Turntable className="absolute inset-0" style={style} label={{ title: meta.title, recipient: meta.recipient, side: meta.sideA, date: meta.date }} engaged={false} progress={0} /></div>
            <div className="grid grid-cols-5 gap-2">
              {STYLES.map((s) => (
                <button key={s.id} onClick={() => gate('style', s.id, s.name, () => setStyle(s.id))} aria-label={locked('style', s.id) ? `${s.name} (Pro)` : s.name} aria-pressed={styleId === s.id}
                  className={`relative aspect-square rounded-full border-2 p-1 transition ${styleId === s.id ? 'border-amber-bright scale-105' : 'border-transparent'}`}>
                  <span className="grid h-full place-items-center rounded-full" style={{ background: `repeating-radial-gradient(${s.disc} 0 2px, color-mix(in srgb, ${s.disc} 80%, white) 3px)` }}>
                    <span className="h-1/3 w-1/3 rounded-full" style={{ background: s.label }} />
                  </span>
                  {locked('style', s.id) && <span className="absolute -bottom-1 left-1/2 -translate-x-1/2 scale-90"><ProBadge /></span>}
                </button>
              ))}
            </div>
            <p className="mt-4 text-center font-display text-xl italic">{style.name}</p>
          </div>
        )}
        {step === 4 && (
          <div className="flex flex-col items-center pt-6 text-center">
            <div className={`relative grid h-52 w-52 place-items-center rounded-full shadow-[0_30px_60px_-20px_black,0_0_0_6px_rgba(180,83,9,.25),0_0_60px_-10px_rgba(217,119,6,.35)] ${render ? 'animate-spin [animation-duration:1.8s]' : ''}`} style={{ background: `repeating-radial-gradient(${style.disc} 0 2px, #2a2522 3px 4px)` }}>
              <span className="grid h-16 w-16 place-items-center rounded-full font-display text-xs" style={{ background: style.label, color: style.ink }}>VR</span>
            </div>
            <h2 className="mt-8 font-display text-3xl">{render ? 'Pressing your record' : pressed ? 'Pressed! It’s on your shelf.' : 'Ready to press.'}</h2>
            <p className="mt-2 text-sm text-muted">{render ? render.stage : `${mood ? mood.name + ' · ' : ''}${soundSummary} · ${style.name}. Every crackle is computed on this device.`}</p>
            {render && <div className="mt-6 h-1 w-full overflow-hidden rounded-full bg-cream/10"><div className="h-full bg-amber transition-[width]" style={{ width: `${render.p * 100}%` }} /></div>}
            {render && <p className="mt-2 font-mono text-xs text-muted">{Math.round(render.p * 100)}%</p>}
            {pressed && !render && (
              <div className="mt-7 flex w-full flex-col gap-3">
                <button
                  type="button"
                  onClick={() => onPlay(pressed)}
                  className="flex h-14 w-full items-center justify-center rounded-full bg-gradient-to-b from-[#f0c86a] via-[#c99234] to-[#8a5a1a] deco text-sm text-obsidian shadow-[0_12px_30px_-10px_rgba(245,158,11,.9)] active:scale-[.98]"
                >
                  ▶ Play my record
                </button>
                <button
                  type="button"
                  onClick={() => void makeChanges()}
                  className="flex h-14 w-full items-center justify-center gap-2 rounded-full border border-amber/50 text-base text-cream transition hover:border-amber-bright active:scale-[.98]"
                >
                  <span>Go back and make changes</span>
                  {!pro && (editsLeft > 0 ? <span className="text-sm text-amber-bright">· {editsLeft} free left</span> : <ProBadge />)}
                </button>
                <button
                  type="button"
                  onClick={reset}
                  className="w-full rounded-full py-3.5 text-base text-muted transition hover:text-cream"
                >
                  Start a new record
                </button>
              </div>
            )}
          </div>
        )}
      </div>
      <footer className="flex items-center justify-between gap-3 border-t border-brass/15 px-6 py-3">
        <Btn variant="ghost" className="min-h-12 px-6 text-base" onClick={() => (pressed && step === 4 ? void makeChanges() : setStep((s) => s - 1))} disabled={step === 0 || !!render}>‹ Back</Btn>
        {pressed && step === 4 ? (
          <Btn className="min-h-12 px-6 text-base" onClick={() => onPlay(pressed)}>Play</Btn>
        ) : step < 4 ? (
          <Btn className="min-h-12 px-6 text-base" onClick={() => setStep((s) => s + 1)} disabled={step === 0 && !source}>Next: {STEPS[step + 1]} ›</Btn>
        ) : (
          <Btn className="min-h-12 px-6 text-base" disabled={!!render || !source} onClick={async () => {
            if (!base && !pro && recordCount >= FREE.maxRecords) return openPaywall(`Your free shelf holds ${FREE.maxRecords} records and it’s full. Go Pro for unlimited records.`)
            if (locked('preset', presetId) || locked('crackle', crackleId) || locked('music', music) || locked('style', styleId)) return openPaywall(mood && locked('mood', mood.id) ? `“${mood.name}” is a Pro mood. Go Pro to press it, or pick a free mood.` : 'This record uses Pro sounds or wax. Go Pro to press it, or pick free options.')
            const id = base?.id ?? crypto.randomUUID(), preset = PRESETS.find((p) => p.id === presetId)!
            const prev = base
            const src = source!
            // uploaded music only counts if it is still loaded
            const useCustom = music === CUSTOM_MUSIC && !!customBed
            const musicId = music === CUSTOM_MUSIC && !customBed ? 'none' : music
            preview.stop(); example.stop(); setRender({ p: 0, stage: 'Preparing source' })
            const [l, r] = await renderMaster(src, { preset, crackle: CRACKLES.find((c) => c.id === crackleId), seed: id, music: useCustom ? customBed!.bed : musicBed(musicId), musicLevel, crackleLevel, character, volume, onProgress: (p, stage) => setRender({ p: p * 0.9, stage }) })
            setRender({ p: 0.94, stage: 'Encoding' }); await new Promise((r) => setTimeout(r, 30))
            const master = encodeWav(l, r)
            setRender({ p: 0.98, stage: 'Generating waveform' })
            const settings = { presetId, styleId, crackleId, music: musicId, musicLevel, crackleLevel, character, volume, moodId }
            const fresh: StoredRecord = {
              id, ...meta, presetId, crackleId, musicId, styleId, duration: l.length / SR, wave: waveform(l),
              createdAt: Date.now(), favorite: false, master, voice: src, settings, reedits: 0,
              customMusic: useCustom ? customBed!.bed : undefined, customMusicName: useCustom ? customBed!.name : undefined,
            }
            const rec: StoredRecord = prev
              ? {
                  ...fresh,
                  createdAt: prev.createdAt,
                  favorite: prev.favorite,
                  lastPlayedAt: prev.lastPlayedAt,
                  labelPhoto: prev.labelPhoto,
                  labelPhotoOriginal: prev.labelPhotoOriginal,
                  labelPhotoAdjust: prev.labelPhotoAdjust,
                  reedits: prev.reedits ?? 0,
                }
              : fresh
            await db.put(rec); navigator.vibrate?.([10, 40, 20])
            if (!demoSources.has(src)) {
              const vId = currentVoiceId ?? id
              await db.saveVaultVoice({
                id: vId,
                name: meta.title.trim() || srcName || 'Voice take',
                voice: src,
                duration: l.length / SR,
                recordTitle: meta.title.trim() || 'Untitled',
                isPro: pro,
              })
              onVoicesChange()
            }
            setRender(null); setPressed(rec); onDone(rec)
          }}>Press record</Btn>
        )}
      </footer>
      {cropping && customSource && <MusicCrop source={customSource.data} name={customSource.name} onCancel={() => setCropping(false)} onSave={saveCrop} />}
    </div>
  )
}

function Capture({
  source,
  srcName,
  voices,
  setSource,
  onVoiceRecorded,
  max: MAX,
  pro,
}: {
  source: Float32Array | null
  srcName: string | null
  voices: VaultVoice[]
  setSource: (s: Float32Array | null, name: string | null, voiceId?: string | null) => void
  onVoiceRecorded?: (id: string, name: string, data: Float32Array, duration: number) => void
  max: number
  pro: boolean
}) {
  const [trimmed, setTrimmed] = useState(false)
  const [state, setState] = useState<'idle' | 'rec' | 'paused' | 'denied'>('idle')
  const [showVaultVoices, setShowVaultVoices] = useState(false)
  const [t, setT] = useState(0)
  const [level, setLevel] = useState(0)
  const [live, setLive] = useState<number[]>([])
  const mr = useRef<MediaRecorder | null>(null)
  const raf = useRef(0)
  const previewUrl = useRef<string | null>(null)
  const [url, setUrl] = useState<string | null>(null)
  const player = useRef<HTMLAudioElement>(null)
  const [pl, setPl] = useState({ on: false, t: 0 })

  useEffect(() => () => { cancelAnimationFrame(raf.current); mr.current?.stream.getTracks().forEach((t) => t.stop()) }, [])
  useEffect(() => {
    if (!source) return setUrl(null)
    if (previewUrl.current) URL.revokeObjectURL(previewUrl.current)
    previewUrl.current = URL.createObjectURL(encodeWav(source, source)); setUrl(previewUrl.current); setPl({ on: false, t: 0 })
  }, [source])

  const start = async () => {
    let stream: MediaStream
    try { stream = await navigator.mediaDevices.getUserMedia({ audio: true }) } catch { return setState('denied') }
    const ctx = new AudioContext(), an = ctx.createAnalyser(); an.fftSize = 1024
    ctx.createMediaStreamSource(stream).connect(an)
    const buf = new Float32Array(an.fftSize), chunks: Blob[] = []
    const rec = new MediaRecorder(stream); mr.current = rec
    rec.ondataavailable = (e) => chunks.push(e.data)
    rec.onstop = async () => {
      stream.getTracks().forEach((t) => t.stop()); ctx.close()
      const data = await decodeToMono(new Blob(chunks, { type: rec.mimeType }))
      const takeName = `Microphone take · ${new Date().toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })}`
      const vid = crypto.randomUUID()
      setSource(data, takeName, vid)
      onVoiceRecorded?.(vid, takeName, data, data.length / SR)
      setState('idle')
    }
    rec.start(250); setState('rec'); setLive([]); setT(0); navigator.vibrate?.(12)
    let lastT = performance.now()
    const loop = (now: number) => {
      raf.current = requestAnimationFrame(loop)
      if (rec.state === 'recording') {
        an.getFloatTimeDomainData(buf); let pk = 0
        for (const v of buf) pk = Math.max(pk, Math.abs(v))
        setLevel(pk); setLive((l) => [...l.slice(-59), pk])
        setT((x) => { const nx = x + (now - lastT) / 1000; if (nx >= MAX) rec.stop(); return nx })
      }
      lastT = now
    }
    raf.current = requestAnimationFrame(loop)
  }
  const stop = () => { cancelAnimationFrame(raf.current); mr.current?.stop() }

  return (
    <div>
      <h2 className="font-display text-[36px] leading-[1.02] tracking-tight">A voice they can <em className="text-amber-bright">return to.</em></h2>
      <div className="mt-4 flex items-center gap-3" aria-hidden><span className="h-px w-8 bg-brass" /><span className="h-1.5 w-1.5 rotate-45 bg-amber" /><span className="h-px flex-1 bg-gradient-to-r from-brass/60 to-transparent" /></div>
      <p className="mt-4 text-sm leading-relaxed text-muted">Speak, sing, or hum. Up to {pro ? 'twenty minutes' : 'three minutes free'}, kept on this device.{!pro && <> <button type="button" onClick={() => openPaywall('Record for up to twenty minutes with Vynyl Pro.')} className="text-amber-bright underline decoration-amber/40 underline-offset-2">Twenty with Pro.</button></>}</p>

      <div className="relative mt-7 rounded-[28px] bg-gradient-to-b from-brass/60 via-brass/15 to-brass/40 p-px shadow-[0_24px_60px_-28px_rgba(217,119,6,.55)]">
      <div className="relative overflow-hidden rounded-[27px] bg-[linear-gradient(180deg,#2a241e,#16130f)] p-5">
        <span className="pointer-events-none absolute inset-2 rounded-[22px] border border-cream/[.06]" aria-hidden />
        <span className="pointer-events-none absolute -right-16 -top-16 h-40 w-40 rounded-full bg-[repeating-radial-gradient(circle,rgba(254,243,199,.05)_0_1px,transparent_1px_5px)]" aria-hidden />
        <div className="relative flex items-center justify-between">
          <span className="flex items-center gap-2 deco text-[10px] tracking-[.2em] text-muted">
            <span className={`h-2 w-2 rounded-full ${state === 'rec' ? 'animate-pulse bg-err shadow-[0_0_10px_#f87171]' : state === 'paused' ? 'bg-amber' : 'bg-cream/20'}`} />
            {state === 'rec' ? 'On air' : state === 'paused' ? 'Paused' : 'Standby'}
          </span>
          <span className="font-mono text-[11px] text-muted">max {fmt(MAX)}</span>
        </div>
        <div className="relative mt-3 text-center font-mono text-5xl font-medium tabular-nums tracking-tight text-cream">{fmt(t)}</div>
        <div className="relative mt-4 h-16 rounded-xl bg-black/25 px-2 py-1 ring-1 ring-inset ring-cream/5"><Wave data={live.length ? live.map((v) => Math.min(1, v * 2.5)) : Array(60).fill(0.05)} /></div>
        <div className="relative mt-3 flex gap-[3px]" aria-hidden>
          {Array.from({ length: 24 }, (_, i) => <span key={i} className={`h-1.5 flex-1 rounded-[1px] transition-colors ${level * 24 > i ? (i > 20 ? 'bg-err' : i > 15 ? 'bg-amber-bright' : 'bg-amber') : 'bg-cream/[.07]'}`} />)}
        </div>
        <p className="relative mt-2 h-4 text-center text-[11px] text-muted" aria-live="polite">
          {state === 'denied' ? 'Microphone access was declined — import a file instead.' : level > 0.95 ? 'Too loud — step back a little.' : state === 'rec' && level < 0.02 && t > 2 ? 'We can barely hear you.' : state === 'rec' ? 'Listening…' : state === 'paused' ? 'Paused' : ''}
        </p>
        <div className="relative mt-5 flex items-center justify-center gap-5">
          {state === 'rec' || state === 'paused' ? (
            <>
              <Btn variant="ghost" onClick={() => { if (state === 'rec') { mr.current?.pause(); setState('paused') } else { mr.current?.resume(); setState('rec') } navigator.vibrate?.(8) }}>{state === 'rec' ? 'Pause' : 'Resume'}</Btn>
              <button onClick={stop} aria-label="Stop recording" className="relative grid h-20 w-20 place-items-center rounded-full bg-gradient-to-b from-[#c9a24a] to-[#7a4a12] p-[3px] shadow-[0_10px_30px_-8px_rgba(153,27,27,.8)]">
                {state === 'rec' && <span className="absolute inset-0 animate-ping rounded-full bg-ruby/30" />}
                <span className="relative grid h-full w-full place-items-center rounded-full bg-[radial-gradient(circle_at_35%_30%,#c0392b,#7f1d1d)]"><span className="h-5 w-5 rounded-[3px] bg-cream" /></span>
              </button>
            </>
          ) : (
            <div className="flex flex-col items-center gap-2">
              <button onClick={start} aria-label={source ? 'Record again' : 'Start recording'} className="group grid h-20 w-20 place-items-center rounded-full bg-gradient-to-b from-[#e0b85a] to-[#7a4a12] p-[3px] shadow-[0_12px_30px_-10px_rgba(217,119,6,.7)] transition active:scale-95">
                <span className="grid h-full w-full place-items-center rounded-full bg-[radial-gradient(circle_at_35%_30%,#c0392b,#7f1d1d)] shadow-[inset_0_2px_6px_rgba(0,0,0,.5)]"><span className="h-6 w-6 rounded-full bg-cream shadow-[0_0_14px_rgba(254,243,199,.5)] transition group-hover:scale-110" /></span>
              </button>
              <span className="deco text-[10px] tracking-[.2em] text-muted">{source ? 'Record again' : 'Tap to record'}</span>
              </div>
          )}
        </div>
      </div>
      </div>

      <div className="mt-6 flex items-center gap-3 deco text-[10px] tracking-[.2em] text-muted/70" aria-hidden><span className="h-px flex-1 bg-brass/20" />or<span className="h-px flex-1 bg-brass/20" /></div>
      <div className="mt-4 grid grid-cols-2 gap-2">
        <label className="flex min-h-12 cursor-pointer items-center justify-center gap-2 rounded-full border border-brass/40 bg-panel/60 text-sm transition hover:border-amber-bright hover:bg-panel">
          <span className="text-amber-bright" aria-hidden>↥</span>Import audio<input type="file" accept="audio/*" className="sr-only" onChange={async (e) => { const f = e.target.files?.[0]; if (f) { const raw = await decodeToMono(f).catch(() => null), d = raw && raw.length > MAX * SR ? raw.slice(0, MAX * SR) : raw; setSource(d, d ? f.name : null, null); setTrimmed(!!raw && raw.length > MAX * SR) } e.target.value = '' }} />
        </label>
        <Btn variant="ghost" onClick={() => { setSource(demoVoice(), 'Lullaby · built-in demo', null) }}>Use a lullaby</Btn>
      </div>
      {voices.length > 0 && (
        <button
          type="button"
          onClick={() => setShowVaultVoices(true)}
          className="mt-2.5 flex min-h-12 w-full items-center justify-center gap-2 rounded-full border border-amber/50 bg-gradient-to-r from-amber/20 to-panel px-4 text-[13px] font-semibold text-cream transition hover:border-amber-bright active:scale-[.98]"
        >
          <span className="text-amber-bright" aria-hidden>✦</span>
          Use voice from Master Vault ({voices.length})
        </button>
      )}

      {trimmed && <p className="mt-3 text-center text-[11px] text-muted">This file was trimmed to {fmt(MAX)}.{!pro && <> <button type="button" onClick={() => openPaywall('Import and record up to twenty minutes with Vynyl Pro.')} className="text-amber-bright underline underline-offset-2">Keep up to 20 min with Pro</button></>}</p>}
      {source && url && (
        <div className="relative mt-5 overflow-hidden rounded-2xl border border-brass/30 bg-[linear-gradient(135deg,#2a231c,#17140f)] p-4 shadow-[0_18px_40px_-26px_rgba(217,119,6,.7)]">
          <span className="pointer-events-none absolute -right-10 -top-10 h-28 w-28 rounded-full bg-[repeating-radial-gradient(circle,rgba(254,243,199,.06)_0_1px,transparent_1px_4px)]" aria-hidden />
          <div className="relative flex items-center gap-3">
            <button type="button" onClick={() => { const a = player.current; if (!a) return; a.paused ? void a.play() : a.pause() }} aria-label={pl.on ? 'Pause preview' : 'Play preview'}
              className="grid h-12 w-12 shrink-0 place-items-center rounded-full bg-gradient-to-b from-[#e0b85a] to-[#8a5a1a] text-obsidian shadow-[0_6px_16px_-6px_rgba(217,119,6,.8)] transition active:scale-95">
              {pl.on ? <span className="flex gap-1"><span className="h-4 w-1.5 rounded-sm bg-obsidian" /><span className="h-4 w-1.5 rounded-sm bg-obsidian" /></span> : <span className="ml-1 h-0 w-0 border-y-[8px] border-l-[13px] border-y-transparent border-l-obsidian" />}
            </button>
            <div className="min-w-0 flex-1">
              <p className="flex items-center gap-1.5 deco text-[10px] tracking-[.2em] text-ok"><span className="h-1.5 w-1.5 rounded-full bg-ok shadow-[0_0_8px_#34d399]" />Source captured</p>
              <p className="mt-0.5 truncate font-display text-lg leading-tight text-cream" title={srcName ?? 'Your recording'}>{srcName ?? 'Your recording'}</p>
            </div>
          </div>
          <div className="relative mt-4">
            <input type="range" min={0} max={source.length / SR} step={0.01} value={pl.t} aria-label="Preview position"
              onChange={(e) => { const a = player.current; if (a) a.currentTime = +e.target.value; setPl((p) => ({ ...p, t: +e.target.value })) }}
              className="h-1 w-full cursor-pointer appearance-none rounded-full accent-amber-bright"
              style={{ background: `linear-gradient(90deg,#f59e0b ${(pl.t / (source.length / SR)) * 100}%,rgba(254,243,199,.12) 0)` }} />
            <div className="mt-1.5 flex justify-between font-mono text-[11px] text-muted"><span>{fmt(pl.t)}</span><span>{fmt(source.length / SR)}</span></div>
          </div>
          <audio ref={player} src={url} className="hidden" onPlay={() => setPl((p) => ({ ...p, on: true }))} onPause={() => setPl((p) => ({ ...p, on: false }))}
            onEnded={() => setPl({ on: false, t: 0 })} onTimeUpdate={(e) => { const t = e.currentTarget.currentTime; setPl((p) => ({ ...p, t })) }} />
        </div>
      )}
      {showVaultVoices && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-obsidian/75 p-4" onClick={() => setShowVaultVoices(false)}>
          <div className="w-full max-w-[360px] rounded-[20px] border border-brass/30 bg-stone p-5" onClick={(e) => e.stopPropagation()}>
            <div className="flex items-center justify-between">
              <h3 className="font-display text-xl text-cream">Master Vault Voices</h3>
              <button type="button" onClick={() => setShowVaultVoices(false)} className="rounded-full p-1.5 text-sm text-muted hover:text-cream">✕</button>
            </div>
            <p className="mb-3 mt-1 text-xs text-muted">Select a saved voice to make a record:</p>
            <div className="max-h-72 space-y-2 overflow-y-auto">
              {voices.map((v) => {
                const isCurrent = srcName === v.name
                return (
                  <button
                    key={v.id}
                    type="button"
                    onClick={() => {
                      setSource(v.voice, v.name, v.id)
                      setShowVaultVoices(false)
                    }}
                    className={`flex w-full items-center gap-2.5 rounded-xl border p-3 text-left transition ${isCurrent ? 'border-amber bg-amber/20' : 'border-brass/20 bg-panel hover:border-amber/60'}`}
                  >
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-sm font-semibold text-cream">{v.name}</span>
                      <span className="mt-0.5 flex items-center gap-1.5 text-[11px] text-muted">
                        <span className="font-mono text-amber-bright">{fmt(v.duration)}</span>
                        {v.recordTitle && (
                          <>
                            <span>·</span>
                            <span className="truncate">Record: {v.recordTitle}</span>
                          </>
                        )}
                      </span>
                    </span>
                    <span className="rounded-full border border-amber bg-amber/25 px-2.5 py-1 text-[11px] font-semibold text-cream">Select</span>
                  </button>
                )
              })}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function Dedication({ meta, setMeta }: { meta: Record<string, string>; setMeta: (fn: (m: any) => any) => void }) {
  const field = (k: string, label: string, ph = '') => (
    <label className="block">
      <span className="deco text-[10px] text-muted">{label}</span>
      <input value={meta[k]} placeholder={ph} onChange={(e) => setMeta((m: any) => ({ ...m, [k]: e.target.value }))}
        className="mt-1 w-full border-b border-brass/30 bg-transparent py-2 text-cream outline-none placeholder:text-muted/50 focus:border-amber-bright" />
    </label>
  )
  return (
    <div className="space-y-5">
      <h2 className="font-display text-[34px] leading-[1.05] tracking-tight">Who is this <em className="text-amber-bright">voice</em> for?</h2>
      {field('title', 'Memory title', 'The night we met')}
      <div className="grid grid-cols-2 gap-4">{field('recipient', 'For')}{field('sender', 'From')}</div>
      <div>
        <span className="deco text-[10px] text-muted">Occasion</span>
        <div className="mt-2 flex flex-wrap gap-1.5">
          {OCCASIONS.map((o) => <button key={o} onClick={() => setMeta((m: any) => ({ ...m, occasion: o }))} className={`rounded-full border px-3.5 py-2 text-xs transition ${meta.occasion === o ? 'border-amber bg-amber/15 text-cream shadow-[0_0_16px_-4px_rgba(217,119,6,.6)]' : 'border-brass/25 text-muted hover:border-brass/60'}`}>{o}</button>)}
        </div>
      </div>
      <label className="block">
        <span className="deco text-[10px] text-muted">Dedication</span>
        <textarea rows={3} value={meta.dedication} onChange={(e) => setMeta((m: any) => ({ ...m, dedication: e.target.value }))} placeholder="What do you want them to remember?"
          className="mt-2 w-full resize-none rounded-2xl border border-brass/25 bg-[linear-gradient(180deg,#26211c,#1a1714)] p-4 font-display text-lg italic leading-snug outline-none focus:border-amber-bright" />
      </label>
      <div className="grid grid-cols-3 gap-4">{field('date', 'Date')}{field('sideA', 'Side A')}{field('sideB', 'Side B')}</div>
    </div>
  )
}

function SectionTitle({ id, n, children }: { id: string; n: string; children: string }) {
  return <h3 id={id} className="mb-3 flex items-center gap-2 deco text-[11px] tracking-[.2em] text-amber-bright"><span className="font-mono text-[10px] text-brass">{n}</span>{children}<span className="h-px flex-1 bg-gradient-to-r from-brass/40 to-transparent" /></h3>
}

type ListenState = 'idle' | 'loading' | 'playing'

/** Big, labelled listen button for the mood cards — easier to see and tap than the round ▶. */
function ListenPill({ state, onClick, label, name, ghost }: { state: ListenState; onClick: () => void; label: string; name: string; ghost?: boolean }) {
  const busy = state !== 'idle'
  return (
    <button type="button" onClick={onClick} aria-pressed={state === 'playing'} aria-label={state === 'playing' ? `Stop ${name}` : `${label}: ${name}`}
      className={`flex min-h-11 items-center gap-2 rounded-full border px-4 text-sm font-medium transition active:scale-95 ${busy ? 'border-amber bg-amber text-obsidian shadow-[0_0_18px_-4px_#f59e0b]' : ghost ? 'border-brass/40 text-cream hover:border-amber-bright' : 'border-amber/60 bg-amber/15 text-cream hover:border-amber-bright'}`}>
      {state === 'loading' ? <span className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-obsidian border-t-transparent" />
        : state === 'playing' ? <span className="flex h-3 items-end gap-[2px]">{[0, 1, 2].map((i) => <span key={i} className="w-[3px] animate-pulse rounded-sm bg-obsidian" style={{ height: `${[60, 100, 75][i]}%`, animationDelay: `${i * 0.15}s` }} />)}</span>
        : <span className={`ml-0.5 h-0 w-0 border-y-[6px] border-l-[10px] border-y-transparent ${ghost ? 'border-l-cream/80' : 'border-l-amber-bright'}`} />}
      {state === 'loading' ? 'Getting it ready…' : state === 'playing' ? 'Stop' : label}
    </button>
  )
}

/** Plays a mood's example record (someone else's message pressed with that mood). */
function useExample(onStart: () => void) {
  const [active, setActive] = useState<{ id: string; loading: boolean } | null>(null)
  const [error, setError] = useState('')
  const audio = useRef<HTMLAudioElement | null>(null), url = useRef<string | null>(null), token = useRef(0)
  const stop = () => {
    token.current++
    audio.current?.pause(); audio.current = null
    if (url.current) { URL.revokeObjectURL(url.current); url.current = null }
    setActive(null)
  }
  useEffect(() => () => stop(), [])
  const toggle = async (id: string) => {
    if (active?.id === id) return stop()
    stop(); onStart(); setError('')
    const my = token.current
    setActive({ id, loading: true })
    try {
      const rec = await sampleRecord(id)
      if (my !== token.current) return
      const u = URL.createObjectURL(rec.master); url.current = u
      const a = new Audio(u); audio.current = a
      a.onended = () => { if (my === token.current) stop() }
      await a.play()
      if (my === token.current) setActive({ id, loading: false })
    } catch {
      if (my === token.current) { setActive(null); setError('Could not play this example. Check your connection and try again.') }
    }
  }
  const state = (id: string): ListenState => (active?.id !== id ? 'idle' : active.loading ? 'loading' : 'playing')
  return { toggle, stop, state, error }
}

function Listen({ state, onClick, label, small, className = '' }: { state: ListenState; onClick: () => void; label: string; small?: boolean; className?: string }) {
  const size = small ? 'h-8 w-8' : 'h-10 w-10'
  return (
    <button type="button" onClick={onClick} aria-label={state === 'playing' ? `Stop ${label.replace(/^Preview /, '')}` : label} aria-pressed={state === 'playing'}
      className={`grid ${size} place-items-center rounded-full border transition active:scale-90 ${state === 'idle' ? 'border-brass/40 bg-obsidian/60 text-amber-bright hover:border-amber-bright' : 'border-amber bg-amber text-obsidian shadow-[0_0_18px_-4px_#f59e0b]'} ${className}`}>
      {state === 'loading' ? <span className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-obsidian border-t-transparent" />
        : state === 'playing' ? <span className="flex h-3 items-end gap-[2px]">{[0, 1, 2].map((i) => <span key={i} className="w-[3px] animate-pulse rounded-sm bg-obsidian" style={{ height: `${[60, 100, 75][i]}%`, animationDelay: `${i * 0.15}s` }} />)}</span>
        : <span className={`ml-0.5 h-0 w-0 border-y-transparent border-l-current ${small ? 'border-y-[5px] border-l-[8px]' : 'border-y-[6px] border-l-[10px]'}`} />}
    </button>
  )
}

type Levels = { musicLevel: number; crackleLevel: number; character: number; volume: number }

/**
 * Renders a short clip through the real vinyl pipeline as separate stems (voice and music at
 * clean and heavy character, plus crackle) and mixes them live, so the level bars respond in real time.
 * Plays about eight seconds once, then stops by itself.
 */
function usePreview(source: Float32Array | null) {
  const [active, setActive] = useState<{ key: string; loading: boolean } | null>(null)
  const ctx = useRef<AudioContext | null>(null), nodes = useRef<AudioBufferSourceNode[]>([]), token = useRef(0)
  const gains = useRef<{ v0: GainNode; v1: GainNode; m0: GainNode; m1: GainNode; c: GainNode; out: GainNode; norm: number } | null>(null)
  const stop = () => { token.current++; nodes.current.forEach((n) => { try { n.stop() } catch { /* already stopped */ } }); nodes.current = []; gains.current = null; setActive(null) }
  useEffect(() => () => { stop(); ctx.current?.close() }, [])
  const apply = (lv: Levels, now = false) => {
    const g = gains.current, c = ctx.current
    if (!g || !c) return
    const w = Math.max(0, Math.min(1.5, lv.character)) / 1.5, t = c.currentTime
    const set = (n: GainNode, v: number) => (now ? n.gain.setValueAtTime(v, t) : n.gain.setTargetAtTime(v, t, 0.04))
    set(g.v0, 1 - w); set(g.v1, w); set(g.m0, (1 - w) * lv.musicLevel); set(g.m1, w * lv.musicLevel)
    set(g.c, lv.crackleLevel); set(g.out, g.norm * lv.volume)
  }
  const toggle = async (key: string, o: { presetId: string; crackleId: string; music: string } & Levels) => {
    if (active?.key === key) return stop()
    stop(); const my = token.current
    setActive({ key, loading: true })
    ctx.current ??= new AudioContext()
    const ac = ctx.current
    void ac.resume()
    // no recording yet → preview only the wax and music, no stand-in melody
    const voice = source && !demoSources.has(source) ? source : null
    const clip = voice ? voice.subarray(0, Math.min(voice.length, SR * 8)) : new Float32Array(SR * 8)
    const silent = new Float32Array(clip.length), bed = musicBed(o.music)
    const base = { preset: PRESETS.find((p) => p.id === o.presetId)!, crackle: CRACKLES.find((c) => c.id === o.crackleId), seed: 'preview:' + key, intro: 0.6, tail: 0.6, fixedGain: 1 }
    const stem = (src: Float32Array, character: number, music: boolean, crackle: boolean) =>
      renderMaster(src, { ...base, character, music: music ? bed : null, musicLevel: music ? 1 : 0, crackleLevel: crackle ? 1 : 0 })
    const v0 = voice ? await stem(clip, 0, false, false) : null
    const v1 = voice ? await stem(clip, 1.5, false, false) : null
    const m0 = bed ? await stem(silent, 0, true, false) : null
    const m1 = bed ? await stem(silent, 1.5, true, false) : null
    const cr = await stem(silent, 1, false, true)
    if (my !== token.current) return
    // normalise against the mix at its default levels so the bars move around a sensible loudness
    let peak = 1e-6
    const len = cr[0].length
    for (let i = 0; i < len; i += 4) for (let ch = 0; ch < 2; ch++) {
      const x = (v0 ? (v0[ch][i] + v1![ch][i]) / 2 : 0) + (m0 ? ((m0[ch][i] + m1![ch][i]) / 2) * o.musicLevel : 0) + cr[ch][i] * o.crackleLevel
      peak = Math.max(peak, Math.abs(x))
    }
    const out = ac.createGain(); out.connect(ac.destination)
    const mk = () => { const g = ac.createGain(); g.connect(out); return g }
    gains.current = { v0: mk(), v1: mk(), m0: mk(), m1: mk(), c: mk(), out, norm: Math.min(0.891 / peak, voice ? 8 : 2.5) }
    apply(o, true)
    const at = ac.currentTime + 0.05
    const play = (st: Float32Array[] | null, g: GainNode) => {
      if (!st) return
      const buf = ac.createBuffer(2, st[0].length, SR); buf.getChannelData(0).set(st[0]); buf.getChannelData(1).set(st[1])
      const n = ac.createBufferSource(); n.buffer = buf; n.connect(g); n.start(at); nodes.current.push(n)
      return n
    }
    const g = gains.current
    play(v0, g.v0); play(v1, g.v1); play(m0, g.m0); play(m1, g.m1)
    // every stem is the same length, so the crackle stem ending means the preview is over
    const last = play(cr, g.c)!
    last.onended = () => { if (my === token.current) { nodes.current = []; gains.current = null; setActive(null) } }
    setActive({ key, loading: false })
  }
  const state = (key: string): ListenState => (active?.key !== key ? 'idle' : active.loading ? 'loading' : 'playing')
  return { toggle, stop, state, apply }
}

function LevelBar({ label, value, max, onChange, ends = ['Soft', 'Full'], className = '', locked = false }: { label: string; value: number; max: number; onChange: (v: number) => void; ends?: [string, string]; className?: string; locked?: boolean }) {
  const pct = (value / max) * 100
  return (
    <label className={`relative block rounded-xl border border-brass/20 bg-panel/70 px-3.5 py-2.5 ${className}`}>
      {locked && <button type="button" onClick={() => openPaywall(`${label} control is part of Vynyl Pro.`)} aria-label={`${label} — unlock with Pro`} className="absolute inset-0 z-10 rounded-xl" />}
      <span className="flex items-baseline justify-between">
        <span className="flex items-center gap-1.5 deco text-[10px] text-cream/80">{label}{locked && <ProBadge />}</span>
        <span className="font-mono text-[11px] text-amber-bright">{Math.round(pct)}%</span>
      </span>
      <input type="range" min={0} max={max} step={0.01} value={value} onChange={(e) => onChange(+e.target.value)} aria-label={label} disabled={locked}
        className="mt-2 disabled:opacity-40 h-1.5 w-full cursor-pointer appearance-none rounded-full accent-amber [&::-webkit-slider-thumb]:h-5 [&::-webkit-slider-thumb]:w-5 [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:rounded-full [&::-webkit-slider-thumb]:border-2 [&::-webkit-slider-thumb]:border-amber-bright [&::-webkit-slider-thumb]:bg-obsidian [&::-webkit-slider-thumb]:shadow-[0_0_10px_rgba(251,191,36,.6)]"
        style={{ background: `linear-gradient(90deg, var(--color-amber-bright) ${pct}%, rgba(254,243,199,.12) ${pct}%)` }} />
      <span className="mt-1 flex justify-between font-mono text-[9px] uppercase tracking-[0.18em] text-muted"><span>{ends[0]}</span><span>{ends[1]}</span></span>
    </label>
  )
}
