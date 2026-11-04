import { useState } from 'react'

const KEY = 'vynyl.onboarded'

/** True until the welcome slides have been finished or skipped once. */
export function needsOnboarding(): boolean {
  try {
    return localStorage.getItem(KEY) !== '1'
  } catch {
    return false
  }
}

function Medallion({ children }: { children: React.ReactNode }) {
  return (
    <svg viewBox="0 0 160 160" className="h-44 w-44" aria-hidden="true">
      <defs>
        <radialGradient id="med-bg" cx="50%" cy="35%" r="70%">
          <stop offset="0%" stopColor="#3a2a17" />
          <stop offset="100%" stopColor="#15110e" />
        </radialGradient>
        <linearGradient id="med-gold" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="#fde68a" />
          <stop offset="50%" stopColor="#f59e0b" />
          <stop offset="100%" stopColor="#b45309" />
        </linearGradient>
      </defs>
      <circle cx="80" cy="80" r="76" fill="url(#med-bg)" />
      <circle cx="80" cy="80" r="76" fill="none" stroke="url(#med-gold)" strokeWidth="1.5" opacity="0.7" />
      <circle cx="80" cy="80" r="68" fill="none" stroke="#f59e0b" strokeOpacity="0.15" strokeWidth="1" />
      {children}
    </svg>
  )
}

const SLIDES = [
  {
    kicker: 'WELCOME TO VYNYL',
    title: 'Turn a voice into a keepsake',
    body: 'Record a short message — a birthday wish, a lullaby, a memory — and Vynyl presses it onto your very own vinyl record.',
    art: (
      <Medallion>
        <rect x="62" y="30" width="36" height="58" rx="18" fill="url(#med-gold)" />
        {[44, 52, 60, 68, 76].map((y) => (
          <line key={y} x1="66" y1={y} x2="94" y2={y} stroke="#7c3f0a" strokeOpacity="0.55" strokeWidth="1.6" strokeLinecap="round" />
        ))}
        <path d="M52 74 A28 28 0 0 0 108 74" fill="none" stroke="#fbbf24" strokeWidth="3" strokeLinecap="round" />
        <line x1="80" y1="102" x2="80" y2="118" stroke="#fbbf24" strokeWidth="3" strokeLinecap="round" />
        <line x1="64" y1="120" x2="96" y2="120" stroke="#fbbf24" strokeWidth="3" strokeLinecap="round" />
        {[0, 1, 2].map((i) => (
          <path
            key={i}
            d={`M ${112 + i * 8} ${58 - i * 4} A ${8} ${14 + i * 4} 0 0 1 ${112 + i * 8} ${82 + i * 4}`}
            fill="none"
            stroke="#fbbf24"
            strokeOpacity={0.9 - i * 0.28}
            strokeWidth="2.5"
            strokeLinecap="round"
          />
        ))}
      </Medallion>
    ),
  },
  {
    kicker: 'EASY AS 1, 2, 3',
    title: 'Just pick a mood',
    body: 'No settings to learn. Choose a mood, tap Listen to hear your own voice in it, then tap Next. Experts can open “Customise” to choose every detail.',
    art: (
      <Medallion>
        <rect x="34" y="36" width="92" height="88" rx="12" fill="#1c1611" stroke="#f59e0b" strokeOpacity="0.35" strokeWidth="1" />
        {([
          [54, 82],
          [80, 56],
          [106, 70],
        ] as const).map(([x, y]) => (
          <g key={x}>
            <line x1={x} y1="50" x2={x} y2="110" stroke="#f59e0b" strokeOpacity="0.35" strokeWidth="3" strokeLinecap="round" />
            <rect x={x - 10} y={y} width="20" height="12" rx="3" fill="url(#med-gold)" />
            <circle cx={x} cy="118" r="2" fill="#fbbf24" />
          </g>
        ))}
      </Medallion>
    ),
  },
  {
    kicker: 'TURNTABLE',
    title: 'Drop the needle',
    body: 'Your record plays on a real 3D turntable, crackle and all. You can add a photo of a loved one to the label.',
    art: (
      <Medallion>
        <rect x="24" y="30" width="112" height="100" rx="10" fill="#2a1d12" stroke="#b45309" strokeOpacity="0.6" strokeWidth="1" />
        <circle cx="72" cy="80" r="40" fill="#0c0a09" />
        {[36, 31, 26, 21].map((r) => (
          <circle key={r} cx="72" cy="80" r={r} fill="none" stroke="#fbbf24" strokeOpacity="0.22" strokeWidth="1" />
        ))}
        <circle cx="72" cy="80" r="13" fill="url(#med-gold)" />
        <circle cx="72" cy="80" r="2.5" fill="#0c0a09" />
        <circle cx="120" cy="44" r="7" fill="#3a2a17" stroke="#fbbf24" strokeWidth="2" />
        <line x1="120" y1="44" x2="112" y2="96" stroke="#fbbf24" strokeWidth="3" strokeLinecap="round" />
        <line x1="112" y1="96" x2="100" y2="106" stroke="#fbbf24" strokeWidth="3" strokeLinecap="round" />
        <rect x="94" y="102" width="10" height="7" rx="1.5" fill="url(#med-gold)" transform="rotate(-40 99 105)" />
      </Medallion>
    ),
  },
  {
    kicker: 'MASTER VAULT',
    title: 'Keep it, share it',
    body: 'Every record waits on your shelf, ready to share as sound or video. Not sure where to start? Hear our sample records first.',
    art: (
      <Medallion>
        <line x1="30" y1="82" x2="130" y2="82" stroke="#b45309" strokeWidth="4" strokeLinecap="round" />
        <line x1="30" y1="124" x2="130" y2="124" stroke="#b45309" strokeWidth="4" strokeLinecap="round" />
        {([
          [38, '#991b1b'],
          [52, '#f59e0b'],
          [66, '#0d3b2a'],
          [80, '#e9e1cf'],
        ] as const).map(([x, c]) => (
          <rect key={x} x={x} y="40" width="12" height="42" rx="2" fill={c} stroke="#fbbf24" strokeOpacity="0.5" strokeWidth="1" />
        ))}
        <rect x="96" y="44" width="12" height="38" rx="2" fill="#3b1d55" transform="rotate(12 102 82)" />
        <circle cx="104" cy="106" r="17" fill="#0c0a09" stroke="#fbbf24" strokeOpacity="0.4" strokeWidth="1" />
        <circle cx="104" cy="106" r="6" fill="url(#med-gold)" />
        <line x1="44" y1="112" x2="80" y2="112" stroke="#fbbf24" strokeOpacity="0.6" strokeWidth="2.5" strokeLinecap="round" />
        <line x1="44" y1="102" x2="68" y2="102" stroke="#fbbf24" strokeOpacity="0.6" strokeWidth="2.5" strokeLinecap="round" />
      </Medallion>
    ),
  },
]

/** First-launch welcome slides; shown once, after the splash (mirrors Android Onboarding.kt). */
export default function Onboarding({ onDone, onSamples }: { onDone: () => void; onSamples?: () => void }) {
  const [page, setPage] = useState(0)
  const remember = () => { try { localStorage.setItem(KEY, '1') } catch { /* ignore */ } }
  const finish = () => { remember(); onDone() }
  const s = SLIDES[page]
  const last = page === SLIDES.length - 1

  return (
    <div
      className="absolute inset-0 z-40 flex flex-col bg-obsidian"
      style={{ background: 'radial-gradient(60% 50% at 50% 42%, rgba(217,119,6,.2), transparent 70%), #0c0a09' }}
      role="dialog" aria-modal="true" aria-labelledby="onboarding-title"
    >
      <div className="flex items-center justify-between p-4">
        <span className="pl-2 font-mono text-sm text-muted" aria-live="polite">{page + 1} of {SLIDES.length}</span>
        <button
          type="button"
          onClick={finish}
          className="rounded-full border border-cream/20 px-5 py-2.5 text-base text-cream transition hover:border-amber-bright active:scale-95"
        >
          Skip
        </button>
      </div>
      <div className="flex flex-1 flex-col items-center justify-center px-8 text-center">
        {s.art}
        <p className="mt-8 deco text-[13px] text-amber-bright">{s.kicker}</p>
        <h2 id="onboarding-title" className="mt-3 font-display text-[32px] leading-tight text-cream">{s.title}</h2>
        <p className="mt-4 max-w-[320px] text-[17px] leading-[26px] text-cream/80">{s.body}</p>
      </div>
      <div className="px-6 pb-7 pt-4">
        <div className="mb-4 flex items-center justify-center gap-1.5">
          {SLIDES.map((_, n) => (
            <button
              key={n}
              type="button"
              aria-label={`Slide ${n + 1}`}
              aria-current={n === page}
              onClick={() => setPage(n)}
              className={`h-2 rounded-full transition-all ${n === page ? 'w-7 bg-amber-bright' : 'w-2 bg-cream/25'}`}
            />
          ))}
        </div>
        {last ? (
          <div className="space-y-2.5">
            <button
              type="button"
              onClick={finish}
              className="flex h-14 w-full items-center justify-center rounded-full bg-gradient-to-b from-[#f0c86a] via-[#c99234] to-[#8a5a1a] deco text-base text-obsidian shadow-[0_12px_30px_-10px_rgba(245,158,11,.9)] active:scale-[.98]"
            >
              Start recording
            </button>
            {onSamples && (
              <button
                type="button"
                onClick={() => { remember(); onSamples() }}
                className="flex h-14 w-full items-center justify-center rounded-full border border-amber/50 text-base text-cream transition hover:border-amber-bright active:scale-[.98]"
              >
                Hear sample records first
              </button>
            )}
            <button type="button" onClick={() => setPage((p) => p - 1)} className="w-full py-2 text-base text-muted hover:text-cream">‹ Back</button>
          </div>
        ) : (
          <div className="flex gap-3">
            {page > 0 && (
              <button type="button" onClick={() => setPage((p) => p - 1)} className="h-14 flex-1 rounded-full border border-cream/20 text-base text-cream transition hover:border-amber-bright active:scale-[.98]">
                ‹ Back
              </button>
            )}
            <button
              type="button"
              onClick={() => setPage((p) => p + 1)}
              className="flex h-14 flex-[2] items-center justify-center rounded-full bg-gradient-to-b from-[#f0c86a] via-[#c99234] to-[#8a5a1a] deco text-base text-obsidian shadow-[0_12px_30px_-10px_rgba(245,158,11,.9)] active:scale-[.98]"
            >
              Next ›
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
