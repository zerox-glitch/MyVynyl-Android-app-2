import { useEffect, useMemo, useRef, useState } from 'react'
import { Btn, Eyebrow, fmt } from '../components/ui'
import { MOODS, PRESETS, STYLES } from '../lib/presets'
import { encodeWav } from '../lib/dsp'
import { db, type StoredRecord, type VaultVoice } from '../lib/db'
import { FREE, isFree, openPaywall, usePro } from '../lib/pro'
import { SAMPLES } from '../lib/samples'
import { ProBadge, ProButton } from '../components/Paywall'

export type VaultTab = 'records' | 'voices' | 'samples'

export default function Vault({
  records,
  voices,
  refresh,
  onPlay,
  onNew,
  onUseVoice,
  tab: vaultTab,
  onTab: setVaultTab,
  onPlaySample,
  onUseMood,
  sampleLoading,
}: {
  records: StoredRecord[]
  voices: VaultVoice[]
  refresh: () => void
  onPlay: (r: StoredRecord) => void
  onNew: () => void
  onUseVoice: (v: VaultVoice) => void
  tab: VaultTab
  onTab: (t: VaultTab) => void
  /** plays a mood's example record on the turntable */
  onPlaySample: (moodId: string) => void
  /** starts a new record with this mood already chosen */
  onUseMood: (moodId: string) => void
  /** mood whose example record is being pressed right now */
  sampleLoading: string | null
}) {
  const [q, setQ] = useState('')
  const [view, setView] = useState<'grid' | 'list'>('grid')
  const [fav, setFav] = useState(false)
  const [sort, setSort] = useState<'newest' | 'oldest' | 'title' | 'duration'>('newest')
  const [confirm, setConfirm] = useState<StoredRecord | null>(null)
  const [confirmVoice, setConfirmVoice] = useState<VaultVoice | null>(null)
  const [playingVoiceId, setPlayingVoiceId] = useState<string | null>(null)
  const audioRef = useRef<HTMLAudioElement | null>(null)
  const audioUrlRef = useRef<string | null>(null)
  const pro = usePro().pro

  const stopVoicePlayback = () => {
    audioRef.current?.pause()
    audioRef.current = null
    if (audioUrlRef.current) {
      URL.revokeObjectURL(audioUrlRef.current)
      audioUrlRef.current = null
    }
    setPlayingVoiceId(null)
  }

  useEffect(() => () => stopVoicePlayback(), [])

  const toggleVoicePlayback = (v: VaultVoice) => {
    if (playingVoiceId === v.id) {
      stopVoicePlayback()
      return
    }
    stopVoicePlayback()
    const url = URL.createObjectURL(encodeWav(v.voice, v.voice))
    audioUrlRef.current = url
    const a = new Audio(url)
    audioRef.current = a
    setPlayingVoiceId(v.id)
    a.onended = () => stopVoicePlayback()
    void a.play().catch(() => stopVoicePlayback())
  }

  const list = useMemo(() => {
    const s = q.toLowerCase()
    return records
      .filter((r) => (!fav || r.favorite) && [r.title, r.recipient, r.occasion].some((x) => x.toLowerCase().includes(s)))
      .sort((a, b) => sort === 'newest' ? b.createdAt - a.createdAt : sort === 'oldest' ? a.createdAt - b.createdAt : sort === 'title' ? a.title.localeCompare(b.title) : b.duration - a.duration)
  }, [records, q, fav, sort])

  const voiceList = useMemo(() => {
    const s = q.toLowerCase()
    return voices
      .filter((v) => v.name.toLowerCase().includes(s) || (v.recordTitle?.toLowerCase().includes(s) ?? false))
      .sort((a, b) => b.createdAt - a.createdAt)
  }, [voices, q])

  const sampleList = useMemo(() => {
    const s = q.toLowerCase()
    return SAMPLES.filter((x) => {
      const m = MOODS.find((mm) => mm.id === x.moodId)
      return [m?.name ?? '', x.persona, x.script, x.occasion].some((t) => t.toLowerCase().includes(s))
    })
  }, [q])

  const toggleFav = async (r: StoredRecord) => { await db.put({ ...r, favorite: !r.favorite }); refresh() }

  return (
    <div className="flex h-full flex-col">
      <header className="px-6 pt-4">
        <div className="flex items-center justify-between gap-3">
          <Eyebrow>{vaultTab === 'records' ? `Master Vault · ${records.length} pressed` : vaultTab === 'voices' ? `Master Vault · ${voices.length} voices saved` : `Master Vault · ${SAMPLES.length} sample records`}</Eyebrow>
          <ProButton />
        </div>
        <h1 className="mt-1 font-display text-4xl">Your shelf</h1>
        <div className="mt-3 flex gap-1 rounded-full border border-brass/25 p-0.5">
          {([
            ['records', `Records (${records.length})`],
            ['voices', `Voices (${voices.length})`],
            ['samples', 'Samples'],
          ] as const).map(([tid, label]) => {
            const on = vaultTab === tid
            return (
              <button
                key={tid}
                type="button"
                onClick={() => {
                  stopVoicePlayback()
                  setVaultTab(tid)
                }}
                aria-pressed={on}
                className={`min-h-10 flex-1 rounded-full py-2 text-[13px] transition ${on ? 'bg-amber/25 font-semibold text-cream' : 'text-muted'}`}
              >
                {label}
              </button>
            )
          })}
        </div>
        {!pro && vaultTab !== 'samples' && (
          vaultTab === 'records' ? (
            <button type="button" onClick={() => openPaywall('Free shelves hold three records. Go Pro to keep every voice you press.')} className="mt-3 block w-full rounded-xl border border-brass/25 bg-panel/70 px-3.5 py-2.5 text-left transition hover:border-amber/60">
              <span className="flex items-center justify-between text-[11px]"><span className="text-cream/85">{Math.min(records.length, FREE.maxRecords)} of {FREE.maxRecords} free shelf slots used</span><span className="deco text-[10px] text-amber-bright">Unlimited with Pro ›</span></span>
              <span className="mt-2 flex gap-1">{Array.from({ length: FREE.maxRecords }, (_, i) => <span key={i} className={`h-1 flex-1 rounded-full ${i < records.length ? 'bg-amber-bright' : 'bg-cream/10'}`} />)}</span>
            </button>
          ) : (
            <button type="button" onClick={() => openPaywall('Free accounts can save up to two voices in the master vault. Go Pro to save unlimited voices.')} className="mt-3 block w-full rounded-xl border border-brass/25 bg-panel/70 px-3.5 py-2.5 text-left transition hover:border-amber/60">
              <span className="flex items-center justify-between text-[11px]"><span className="text-cream/85">{Math.min(voices.length, FREE.maxVoices)} of {FREE.maxVoices} free voice slots used</span><span className="deco text-[10px] text-amber-bright">Unlimited with Pro ›</span></span>
              <span className="mt-2 flex gap-1">{Array.from({ length: FREE.maxVoices }, (_, i) => <span key={i} className={`h-1 flex-1 rounded-full ${i < voices.length ? 'bg-amber-bright' : 'bg-cream/10'}`} />)}</span>
            </button>
          )
        )}
        <input value={q} onChange={(e) => setQ(e.target.value)} placeholder={vaultTab === 'records' ? 'Search titles, people, occasions' : vaultTab === 'voices' ? 'Search saved voices' : 'Search moods and messages'} aria-label="Search"
          className="mt-4 w-full rounded-full border border-brass/25 bg-panel px-4 py-2.5 text-sm outline-none placeholder:text-muted/60 focus:border-amber-bright" />
        {vaultTab === 'records' && (
          <div className="mt-3 flex items-center gap-2 text-xs">
            <button onClick={() => setFav((f) => !f)} aria-pressed={fav} className={`rounded-full border px-3 py-1.5 ${fav ? 'border-amber text-cream' : 'border-brass/25 text-muted'}`}>♥ Favorites</button>
            <select value={sort} onChange={(e) => setSort(e.target.value as any)} aria-label="Sort" className="rounded-full border border-brass/25 bg-panel px-3 py-1.5 text-muted outline-none">
              <option value="newest">Newest</option><option value="oldest">Oldest</option><option value="title">Title</option><option value="duration">Longest</option>
            </select>
            <div className="ml-auto flex rounded-full border border-brass/25 p-0.5">
              {(['grid', 'list'] as const).map((v) => <button key={v} onClick={() => setView(v)} aria-pressed={view === v} className={`rounded-full px-3 py-1 capitalize ${view === v ? 'bg-amber/20 text-cream' : 'text-muted'}`}>{v}</button>)}
            </div>
          </div>
        )}
      </header>

      <div className="no-scrollbar flex-1 overflow-y-auto px-6 py-5">
        {vaultTab === 'records' ? (
          !records.length ? (
            <div className="flex h-full flex-col items-center justify-center text-center">
              <div className="h-32 w-32 rounded-full border border-dashed border-brass/40" style={{ background: 'repeating-radial-gradient(transparent 0 6px, rgba(180,83,9,.12) 7px 8px)' }} />
              <p className="mt-6 max-w-56 font-display text-2xl leading-snug">Your shelf is waiting for its first voice.</p>
              <Btn className="mt-6" onClick={onNew}>Press a record</Btn>
              <Btn variant="ghost" className="mt-3" onClick={() => setVaultTab('samples')}>Hear a sample record first</Btn>
            </div>
          ) : (
            <div className={view === 'grid' ? 'grid grid-cols-2 gap-x-4 gap-y-6' : 'space-y-2'}>
              {list.map((r) => {
                const st = STYLES.find((s) => s.id === r.styleId)!, pr = PRESETS.find((p) => p.id === r.presetId)!
                const sleeve = (
                  <div className="relative aspect-square overflow-hidden rounded-md bg-stone shadow-[0_10px_30px_-10px_black]" style={{ background: `linear-gradient(140deg, ${st.label}, #0c0a09 120%)` }}>
                    <div className="absolute -right-1/4 top-[8%] h-[84%] aspect-square rounded-full" style={{ background: `repeating-radial-gradient(${st.disc} 0 2px, #00000055 3px 4px)` }}>
                      <span className="absolute inset-[34%] rounded-full" style={{ background: st.label }} />
                    </div>
                    <span className="absolute bottom-2 left-2 right-1/3 font-display text-sm leading-tight" style={{ color: st.ink === '#0c0a09' ? '#fef3c7' : st.ink }}>{r.title}</span>
                  </div>
                )
                return view === 'grid' ? (
                  <article key={r.id} className="group">
                    <button onClick={() => onPlay(r)} className="block w-full transition group-hover:-translate-y-1" aria-label={`Play ${r.title}`}>{sleeve}</button>
                    <div className="mt-2 flex items-start justify-between gap-1">
                      <div className="min-w-0"><p className="truncate text-sm font-semibold">{r.title}</p><p className="truncate text-xs text-muted">for {r.recipient} · {r.occasion}</p></div>
                      <button onClick={() => toggleFav(r)} aria-label="Favorite" className={`p-1 ${r.favorite ? 'text-amber-bright' : 'text-muted/50'}`}>♥</button>
                    </div>
                    <div className="mt-1 flex items-center gap-2"><span className="rounded-full border border-brass/30 px-2 py-0.5 text-[10px] text-muted">{pr.name}</span><span className="font-mono text-[10px] text-muted">{fmt(r.duration)}</span></div>
                  </article>
                ) : (
                  <article key={r.id} className="flex items-center gap-3 rounded-xl border border-brass/15 bg-panel p-2 pr-3">
                    <button onClick={() => onPlay(r)} className="w-14 shrink-0" aria-label={`Play ${r.title}`}>{sleeve}</button>
                    <div className="min-w-0 flex-1"><p className="truncate text-sm font-semibold">{r.title}</p><p className="truncate text-xs text-muted">{r.recipient} · {pr.name} · <span className="font-mono">{fmt(r.duration)}</span></p></div>
                    <button onClick={() => toggleFav(r)} aria-label="Favorite" className={r.favorite ? 'text-amber-bright' : 'text-muted/50'}>♥</button>
                    <button onClick={() => setConfirm(r)} aria-label="Delete" className="text-xs text-muted hover:text-err">Delete</button>
                  </article>
                )
              })}
            </div>
          )
        ) : vaultTab === 'samples' ? (
          <div>
            <p className="mb-4 text-sm leading-relaxed text-muted">
              Not sure which mood to choose? Listen to example messages — from a grandfather, a boyfriend, a mother and more — each pressed with a different mood. Tap one to play it on the turntable.
            </p>
            <div className="space-y-2.5">
              {sampleList.map((s) => {
                const m = MOODS.find((mm) => mm.id === s.moodId)!, st = STYLES.find((x) => x.id === s.styleId)!
                const loading = sampleLoading === s.moodId, isLocked = !pro && !isFree('mood', s.moodId)
                return (
                  <article key={s.moodId} className="rounded-2xl border border-brass/20 bg-panel p-3.5">
                    <div className="flex items-start gap-3">
                      <button type="button" onClick={() => onPlaySample(s.moodId)} disabled={!!sampleLoading} aria-label={`Play the ${m.name} sample record`}
                        className="relative aspect-square w-16 shrink-0 overflow-hidden rounded-md shadow-[0_8px_20px_-10px_black] disabled:cursor-wait" style={{ background: `linear-gradient(140deg, ${st.label}, #0c0a09 120%)` }}>
                        <span className="absolute -right-1/4 top-[8%] aspect-square h-[84%] rounded-full" style={{ background: `repeating-radial-gradient(${st.disc} 0 2px, #00000055 3px 4px)` }}>
                          <span className="absolute inset-[34%] rounded-full" style={{ background: st.label }} />
                        </span>
                        {loading && <span className="absolute inset-0 grid place-items-center bg-obsidian/60"><span className="h-5 w-5 animate-spin rounded-full border-2 border-amber-bright border-t-transparent" /></span>}
                      </button>
                      <div className="min-w-0 flex-1">
                        <p className="flex flex-wrap items-center gap-x-2 gap-y-1"><span className="font-display text-lg leading-tight text-cream">{m.name}</span>{isLocked && <ProBadge />}</p>
                        <p className="mt-0.5 text-[13px] text-muted">A {s.persona.toLowerCase()}'s message for {s.to}</p>
                        <p className="mt-1 line-clamp-2 text-xs italic leading-snug text-cream/60">“{s.script}”</p>
                      </div>
                    </div>
                    <div className="mt-3 grid grid-cols-2 gap-2">
                      <button type="button" onClick={() => onPlaySample(s.moodId)} disabled={!!sampleLoading}
                        className="min-h-11 rounded-full bg-gradient-to-b from-[#f0c86a] via-[#c99234] to-[#8a5a1a] px-3 text-sm font-semibold text-obsidian active:scale-95 disabled:opacity-60">
                        {loading ? 'Getting it ready…' : '▶ Play it'}
                      </button>
                      <button type="button" onClick={() => onUseMood(s.moodId)} className="min-h-11 rounded-full border border-amber/50 px-3 text-sm text-amber-bright transition hover:bg-amber/10 active:scale-95">
                        Use this mood
                      </button>
                    </div>
                  </article>
                )
              })}
            </div>
            <p className="mt-5 text-center text-[10px] leading-relaxed text-muted/70">Example voices are computer-generated from open voice libraries (LibriVox, LibriTTS-R CC BY 4.0, Piper).</p>
          </div>
        ) : !voices.length ? (
          <div className="flex h-full flex-col items-center justify-center text-center">
            <div className="grid h-28 w-28 place-items-center rounded-full border border-dashed border-brass/35">
              <div className="h-16 w-16 rounded-full bg-amber/15" />
            </div>
            <p className="mt-5 font-display text-2xl">No voices in your vault yet.</p>
            <p className="mt-2.5 max-w-[280px] text-sm leading-relaxed text-muted">
              When you record a voice to make a record, it will also be saved here so you can reuse it to press new records anytime.
            </p>
            <Btn className="mt-6" onClick={onNew}>Record a voice</Btn>
          </div>
        ) : (
          <div className="space-y-2.5">
            {voiceList.map((v) => {
              const isPlaying = playingVoiceId === v.id
              const dateStr = new Date(v.createdAt).toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
              return (
                <article key={v.id} className="flex items-center gap-3 rounded-[14px] border border-brass/20 bg-panel p-3">
                  <button
                    type="button"
                    onClick={() => toggleVoicePlayback(v)}
                    aria-label={isPlaying ? `Pause ${v.name}` : `Play ${v.name}`}
                    className={`grid h-11 w-11 shrink-0 place-items-center rounded-full text-obsidian transition active:scale-95 ${isPlaying ? 'bg-amber-bright' : 'bg-gradient-to-b from-[#e0b85a] to-[#8a5a1a]'}`}
                  >
                    {isPlaying ? (
                      <span className="flex gap-1"><span className="h-3.5 w-1 rounded-xs bg-obsidian" /><span className="h-3.5 w-1 rounded-xs bg-obsidian" /></span>
                    ) : (
                      <span className="ml-0.5 h-0 w-0 border-y-[7px] border-l-[12px] border-y-transparent border-l-obsidian" />
                    )}
                  </button>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-semibold text-cream">{v.name}</p>
                    <p className="mt-0.5 flex items-center gap-1.5 text-[11px] text-muted">
                      <span className="font-mono text-amber-bright">{fmt(v.duration)}</span>
                      {v.recordTitle && (
                        <>
                          <span>·</span>
                          <span className="truncate">Record: {v.recordTitle}</span>
                        </>
                      )}
                    </p>
                    <p className="mt-0.5 text-[10px] text-muted/70">{dateStr}</p>
                  </div>
                  <button
                    type="button"
                    onClick={() => {
                      stopVoicePlayback()
                      onUseVoice(v)
                    }}
                    className="shrink-0 rounded-full border border-amber/50 bg-amber/20 px-3 py-1.5 text-[11px] font-semibold text-amber-bright transition hover:bg-amber/30 active:scale-95"
                  >
                    Make record
                  </button>
                  <button
                    type="button"
                    aria-label={`Delete ${v.name}`}
                    onClick={() => setConfirmVoice(v)}
                    className="rounded-full p-1.5 text-sm text-muted hover:text-err"
                  >
                    ✕
                  </button>
                </article>
              )
            })}
          </div>
        )}
      </div>

      {confirm && (
        <div className="absolute inset-0 z-20 flex items-end bg-obsidian/70 backdrop-blur-sm" role="dialog" aria-modal>
          <div className="w-full rounded-t-3xl border-t border-brass/30 bg-stone p-6">
            <h3 className="font-display text-2xl">Delete “{confirm.title}”?</h3>
            <p className="mt-2 text-sm text-muted">The master and its artwork will be removed from this device. This can’t be undone without a backup.</p>
            <div className="mt-6 flex gap-2"><Btn variant="ghost" className="flex-1" onClick={() => setConfirm(null)}>Keep it</Btn><Btn className="flex-1 !bg-err" onClick={async () => { await db.del(confirm.id); setConfirm(null); refresh() }}>Delete</Btn></div>
          </div>
        </div>
      )}
      {confirmVoice && (
        <div className="absolute inset-0 z-20 flex items-end bg-obsidian/70 backdrop-blur-sm" role="dialog" aria-modal>
          <div className="w-full rounded-t-3xl border-t border-brass/30 bg-stone p-6">
            <h3 className="font-display text-2xl">Delete voice “{confirmVoice.name}”?</h3>
            <p className="mt-2 text-sm text-muted">This voice will be removed from your Master Vault. Any existing pressed records will keep their master audio, but you won’t be able to reuse this voice for new records.</p>
            <div className="mt-6 flex gap-2">
              <Btn variant="ghost" className="flex-1" onClick={() => setConfirmVoice(null)}>Keep it</Btn>
              <Btn className="flex-1 !bg-err" onClick={async () => {
                if (playingVoiceId === confirmVoice.id) stopVoicePlayback()
                await db.delVoice(confirmVoice.id)
                setConfirmVoice(null)
                refresh()
              }}>Delete</Btn>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
