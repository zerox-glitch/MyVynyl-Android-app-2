import { useCallback, useEffect, useState } from 'react'
import Studio from './screens/Studio'
import Vault, { type VaultTab } from './screens/Vault'
import Player from './screens/Player'
import Paywall from './components/Paywall'
import Guide from './components/Guide'
import Onboarding, { needsOnboarding } from './components/Onboarding'
import Splash from './components/Splash'
import Logo from './components/Logo'
import { db, type StoredRecord, type VaultVoice } from './lib/db'
import { sampleRecord } from './lib/samples'

export default function App() {
  const [tab, setTab] = useState<'studio' | 'vault'>('studio')
  const [vaultTab, setVaultTab] = useState<VaultTab>('records')
  const [guide, setGuide] = useState(false)
  const [welcome, setWelcome] = useState(() => needsOnboarding())
  const [capture, setCapture] = useState(0)
  const [studioStep, setStudioStep] = useState(0)
  const [records, setRecords] = useState<StoredRecord[]>([])
  const [voices, setVoices] = useState<VaultVoice[]>([])
  const [playingId, setPlayingId] = useState<string | null>(null)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [loadVoice, setLoadVoice] = useState<VaultVoice | null>(null)
  /** an example record from the Samples shelf, open in the Player */
  const [sample, setSample] = useState<StoredRecord | null>(null)
  const [sampleLoading, setSampleLoading] = useState<string | null>(null)
  const [moodRequest, setMoodRequest] = useState<{ id: string; n: number } | null>(null)
  const refresh = useCallback(() => {
    db.all().then(setRecords)
    db.voices().then(setVoices)
  }, [])
  useEffect(refresh, [refresh])
  const playing = (playingId ? records.find((r) => r.id === playingId) ?? null : null) ?? sample
  const editing = editingId ? records.find((r) => r.id === editingId) ?? null : null
  const playSample = (moodId: string) => {
    setSampleLoading(moodId)
    sampleRecord(moodId)
      .then((r) => { setPlayingId(null); setSample(r) })
      .catch(() => alert('Could not open this sample record. Check your connection and try again.'))
      .finally(() => setSampleLoading(null))
  }
  const useMood = (moodId: string) => {
    setSample(null); setPlayingId(null); setGuide(false)
    setTab('studio')
    setMoodRequest((r) => ({ id: moodId, n: (r?.n ?? 0) + 1 }))
  }

  return (
    <div className="grain relative min-h-[100dvh] overflow-hidden" style={{ background: 'radial-gradient(60% 50% at 20% 20%, rgba(217,119,6,.18), transparent 70%), radial-gradient(50% 50% at 85% 80%, rgba(153,27,27,.2), transparent 70%), #0c0a09' }}>
      <div className="mx-auto grid min-h-[100dvh] max-w-6xl items-center sm:gap-12 sm:px-6 sm:py-10 lg:grid-cols-[1fr_auto]">
        <aside className="hidden lg:block">
          <Logo size={64} className="mb-8" />
          <p className="deco text-xs text-amber-bright">Digital Wax Preserver</p>
          <h1 className="mt-4 font-display text-7xl leading-[0.95]">Vynyl<br />Record</h1>
          <div className="mt-8 h-px w-24 bg-brass" />
          <p className="mt-8 max-w-sm text-muted">An Android app prototype. Record a voice, press it through a real on-device vinyl pipeline — saturation, wow, flutter, surface noise, seeded crackle and pops — and play it back on a procedural 3D turntable.</p>
          <p className="mt-6 max-w-sm font-display text-lg italic text-cream/80">“Your recordings stay on this device unless you choose to export or share them.”</p>
        </aside>

        <div className="relative mx-auto flex h-[100dvh] w-full flex-col overflow-hidden bg-obsidian pt-[env(safe-area-inset-top)] sm:h-[860px] sm:max-h-[calc(100dvh-40px)] sm:w-[400px] sm:rounded-[36px] sm:border-[8px] sm:border-[#1a1715] sm:pt-0 sm:shadow-[0_40px_120px_-20px_black,0_0_0_1px_rgba(180,83,9,.35)]">
          <div className="hidden h-8 shrink-0 items-center justify-between px-6 font-mono text-[11px] text-cream/75 sm:flex" aria-hidden>
            <span>12:30</span>
            <span className="h-3 w-3 rounded-full bg-black ring-2 ring-[#1a1715]" />
            <span className="flex items-center gap-1.5"><span>▾</span><span>▴▴</span><span className="inline-block h-2.5 w-5 rounded-[2px] border border-cream/70 p-px"><span className="block h-full w-4/5 bg-cream/70" /></span></span>
          </div>
          <main className="relative min-h-0 flex-1">
            <div className={tab === 'studio' ? 'h-full' : 'hidden'}>
              <Studio
                records={records}
                voices={voices}
                recordCount={records.length}
                edit={editing}
                onCancelEdit={() => setEditingId(null)}
                onDone={() => refresh()}
                onPlay={(r) => { refresh(); setPlayingId(r.id) }}
                capture={capture}
                active={tab === 'studio' && !playing && !guide}
                onStep={setStudioStep}
                initialVoice={loadVoice}
                onClearInitialVoice={() => setLoadVoice(null)}
                onVoicesChange={refresh}
                moodRequest={moodRequest}
              />
            </div>
            {tab === 'vault' && (
              <Vault
                records={records}
                voices={voices}
                refresh={refresh}
                onPlay={(r) => setPlayingId(r.id)}
                onNew={() => { setTab('studio'); setCapture((c) => c + 1) }}
                onUseVoice={(v) => {
                  setLoadVoice(v)
                  setTab('studio')
                  setCapture((c) => c + 1)
                }}
                tab={vaultTab}
                onTab={setVaultTab}
                onPlaySample={playSample}
                onUseMood={useMood}
                sampleLoading={sampleLoading}
              />
            )}
            {playing && (
              <Player
                key={playing.id}
                record={playing}
                onClose={() => { setPlayingId(null); setSample(null); refresh() }}
                onEdit={(r) => {
                  refresh()
                  setPlayingId(null)
                  setEditingId(r.id)
                  setTab('studio')
                }}
                onUseMood={useMood}
              />
            )}
            {guide && <Guide onClose={() => setGuide(false)} />}
            <Paywall />
          </main>
          <nav className="flex min-h-[68px] shrink-0 items-start justify-around border-t border-brass/20 bg-stone pt-2 pb-[max(8px,env(safe-area-inset-bottom))]" aria-label="Main">
            {([
              ['record', 'Record'],
              ['studio', 'Studio'],
              ['vault', 'Master Vault'],
              ['guide', 'Guide'],
            ] as const).map(([id, label]) => {
              const here = tab === 'studio' && !playing && !guide
              const on =
                id === 'guide'
                  ? guide
                  : id === 'record'
                    ? here && studioStep === 0
                    : id === 'studio'
                      ? here && studioStep > 0
                      : tab === 'vault' && !playing && !guide
              return (
                <button
                  key={id}
                  type="button"
                  onClick={() => {
                    if (id === 'guide') {
                      setGuide(true)
                    } else {
                      setGuide(false)
                      setPlayingId(null)
                      setSample(null)
                      setTab(id === 'vault' ? 'vault' : 'studio')
                      if (id === 'record') setCapture((c) => c + 1)
                    }
                  }}
                  aria-current={on}
                  className={`flex min-h-[52px] flex-1 touch-manipulation flex-col items-center gap-1 text-xs font-medium transition ${on ? 'text-amber-bright' : 'text-cream/70 hover:text-cream'}`}
                >
                  <NavIcon id={id} on={on} />
                  {label}
                </button>
              )
            })}
          </nav>
          {welcome && <Onboarding onDone={() => setWelcome(false)} onSamples={() => { setWelcome(false); setTab('vault'); setVaultTab('samples') }} />}
          <Splash />
          <div className="hidden h-5 shrink-0 items-center justify-center bg-stone sm:flex" aria-hidden><span className="h-1 w-28 rounded-full bg-cream/50" /></div>
        </div>
      </div>
    </div>
  )
}

function NavIcon({ id, on }: { id: 'record' | 'studio' | 'vault' | 'guide'; on: boolean }) {
  return (
    <svg viewBox="0 0 24 24" className="h-6 w-6" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      {id === 'record' && (
        <>
          <rect x="9" y="3" width="6" height="11" rx="3" fill={on ? 'currentColor' : 'none'} />
          <path d="M5.5 11a6.5 6.5 0 0 0 13 0" />
          <line x1="12" y1="17.5" x2="12" y2="21" />
          <line x1="8.5" y1="21" x2="15.5" y2="21" />
        </>
      )}
      {id === 'studio' && (
        <>
          <circle cx="11" cy="13" r="8" />
          <circle cx="11" cy="13" r="2.6" fill={on ? 'currentColor' : 'none'} />
          <line x1="19.5" y1="3.5" x2="15" y2="12" />
          <circle cx="19.5" cy="3.5" r="1.2" fill="currentColor" stroke="none" />
        </>
      )}
      {id === 'vault' && (
        <>
          <line x1="3" y1="20" x2="21" y2="20" />
          <line x1="3" y1="12" x2="21" y2="12" />
          <rect x="4.5" y="4" width="3" height="8" rx="0.6" />
          <rect x="8.5" y="4" width="3" height="8" rx="0.6" fill={on ? 'currentColor' : 'none'} />
          <rect x="12.5" y="5.5" width="3" height="6.5" rx="0.6" />
          <circle cx="16" cy="16" r="3.2" />
          <circle cx="16" cy="16" r="0.9" fill="currentColor" stroke="none" />
        </>
      )}
      {id === 'guide' && (
        <>
          {on && <circle cx="12" cy="12" r="9" fill="currentColor" fillOpacity="0.15" stroke="none" />}
          <circle cx="12" cy="12" r="9" />
          <path d="M9.7 8.4a2.5 2.5 0 1 1 3.7 2.2c-.8.4-1.4 1-1.4 2" />
          <circle cx="12" cy="17" r="0.8" fill="currentColor" stroke="none" />
        </>
      )}
    </svg>
  )
}
