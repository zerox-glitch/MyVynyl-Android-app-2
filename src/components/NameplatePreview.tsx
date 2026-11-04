import { openPaywall } from '../lib/pro'
import { ProBadge } from './Paywall'

/** The gold plinth nameplate drawn like the 3D texture (FROM · deco divider · TO), for previewing before Pro. */
export function Nameplate({ from, to, className = '' }: { from: string; to: string; className?: string }) {
  const goldText = {
    background: 'linear-gradient(180deg, #fff1c1, #f5c451, #d4952a)',
    WebkitBackgroundClip: 'text',
    WebkitTextFillColor: 'transparent',
    filter: 'drop-shadow(0 0 8px rgba(245,182,56,.55))',
  } as const
  return (
    <div
      className={`relative aspect-[320/600] rounded-xl border-[3px] border-[#c9963e] bg-gradient-to-b from-[#1d1611] to-[#0e0b08] p-1.5 ${className}`}
    >
      <div className="flex h-full w-full flex-col items-center justify-evenly rounded-[7px] border border-[#c9963e]/55 px-2 py-4 text-center">
        <div className="w-full">
          <p className="text-[11px] font-bold tracking-[3px]" style={goldText}>FROM</p>
          <p className="mt-1 truncate font-display text-xl italic" style={goldText}>{from.trim() || 'Someone'}</p>
        </div>
        <svg viewBox="0 0 100 14" className="h-3.5 w-4/5" aria-hidden="true">
          <line x1="5" y1="7" x2="40" y2="7" stroke="#f5c451" strokeWidth="2" />
          <line x1="60" y1="7" x2="95" y2="7" stroke="#f5c451" strokeWidth="2" />
          <polygon points="50,0 57,7 50,14 43,7" fill="#f5c451" />
        </svg>
        <div className="w-full">
          <p className="text-[11px] font-bold tracking-[3px]" style={goldText}>TO</p>
          <p className="mt-1 truncate font-display text-xl italic" style={goldText}>{to.trim() || 'You'}</p>
        </div>
      </div>
    </div>
  )
}

/** Sheet shown to free users: what their nameplate would look like on the plinth, then the unlock button. */
export default function NameplatePreview({ from, to, onClose }: { from: string; to: string; onClose: () => void }) {
  return (
    <div className="absolute inset-0 z-40 flex items-end bg-black/70" role="dialog" aria-modal="true" onClick={onClose}>
      <div
        className="w-full rounded-t-[28px] bg-stone px-6 pb-6 pt-3"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="mx-auto h-1 w-10 rounded-full bg-cream/25" />
        <div className="mt-4 flex items-center justify-between">
          <p className="deco text-[10px] text-amber-bright">Preview</p>
          <button type="button" onClick={onClose} aria-label="Close" className="rounded-full p-2.5 text-base text-muted hover:text-cream">✕</button>
        </div>
        <h3 className="font-display text-2xl text-cream">Their names, in glowing gold</h3>
        <div
          className="mt-5 flex justify-center rounded-2xl border border-brass/25 p-6"
          style={{
            background:
              'radial-gradient(circle at 80% 70%, rgba(255,195,90,.45), transparent 60%), linear-gradient(135deg, #3a3f52, #8b90a8, #4b4f63, #2b2e3b)',
          }}
        >
          <Nameplate from={from} to={to} className="w-28" />
        </div>
        <p className="mt-3 text-center text-xs text-muted">
          Engraved on the turntable plinth for this record, and in every video you export.
        </p>
        <button
          type="button"
          onClick={() => {
            onClose()
            openPaywall('Engrave their names in glowing gold on the turntable plinth with Vynyl Pro.')
          }}
          className="mt-5 flex h-12 w-full items-center justify-center gap-2 rounded-full bg-gradient-to-b from-[#f0c86a] via-[#c99234] to-[#8a5a1a] deco text-sm text-obsidian shadow-[0_12px_30px_-10px_rgba(245,158,11,.9)] active:scale-[.98]"
        >
          Unlock with Pro
          <ProBadge />
        </button>
      </div>
    </div>
  )
}
