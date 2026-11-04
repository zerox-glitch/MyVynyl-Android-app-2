import { useState } from 'react'
import { FREE, PERKS, PLANS, PRO_SECONDS, billing, closePaywall, openPaywall, usePaywall, usePro, type PlanId } from '../lib/pro'

/** Small gold "PRO" tag shown on locked items. */
export function ProBadge({ className = '' }: { className?: string }) {
  return (
    <span className={`inline-flex items-center gap-1 rounded-full border border-amber-bright/60 bg-[linear-gradient(180deg,rgba(245,158,11,.28),rgba(180,83,9,.18))] px-1.5 py-px font-mono text-[9px] font-medium tracking-[0.14em] text-amber-bright shadow-[0_0_10px_-3px_rgba(245,158,11,.7)] ${className}`}>
      <svg viewBox="0 0 12 12" className="h-2.5 w-2.5" fill="currentColor" aria-hidden="true"><path d="M3.5 5V3.8a2.5 2.5 0 0 1 5 0V5h.4c.6 0 1.1.5 1.1 1.1v3.8c0 .6-.5 1.1-1.1 1.1H3.1C2.5 11 2 10.5 2 9.9V6.1C2 5.5 2.5 5 3.1 5h.4Zm1.2 0h2.6V3.8a1.3 1.3 0 0 0-2.6 0V5Z" /></svg>
      PRO
    </span>
  )
}

/** Header pill: "Go Pro" for free users, a gold "Pro" crest for subscribers (opens the plan sheet). */
export function ProButton() {
  const pro = usePro()
  return pro.pro ? (
    <button type="button" onClick={() => openPaywall()} aria-label="Vynyl Pro — manage plan"
      className="flex h-8 shrink-0 items-center gap-1.5 whitespace-nowrap rounded-full bg-gradient-to-b from-[#f0c86a] via-[#c99234] to-[#8a5a1a] p-px shadow-[0_0_16px_-4px_rgba(245,158,11,.8)] active:scale-95">
      <span className="flex h-full items-center gap-1.5 rounded-full bg-gradient-to-b from-[#262019] to-[#14110e] px-3 font-deco text-[11px] uppercase tracking-[0.2em] text-amber-bright"><Crown />Pro</span>
    </button>
  ) : (
    <button type="button" onClick={() => openPaywall()}
      className="flex h-8 shrink-0 items-center gap-1.5 whitespace-nowrap rounded-full bg-gradient-to-b from-[#f0c86a] via-[#c99234] to-[#8a5a1a] px-3 font-deco text-[11px] uppercase tracking-[0.2em] text-obsidian shadow-[0_6px_18px_-8px_rgba(245,158,11,.9)] transition active:scale-95">
      <Crown />Go Pro
    </button>
  )
}

const Crown = () => <svg viewBox="0 0 16 16" className="h-3.5 w-3.5" fill="currentColor" aria-hidden="true"><path d="M2 5.2 5.2 8 8 3l2.8 5L14 5.2 12.8 12H3.2L2 5.2ZM3.4 13h9.2v1.2H3.4z" /></svg>

const ROWS: [string, string, string][] = [
  ['Recording length', `${FREE.maxSeconds / 60} min`, `${PRO_SECONDS / 60} min`],
  ['Records on your shelf', String(FREE.maxRecords), 'Unlimited'],
  ['Characters', String(FREE.presets.length), 'All'],
  ['Crackle styles', String(FREE.crackles.length), 'All + levels'],
  ['Background music', String(FREE.music.length - 1), 'All'],
  ['Wax colours', String(FREE.styles.length), 'All'],
  ['Gold nameplate', '—', '✓'],
  ['WAV export', 'Tagged', 'Clean'],
]

/** Full-screen paywall / plan sheet. Rendered once in the app shell; opened from anywhere via openPaywall(). */
export default function Paywall() {
  const open = usePaywall()
  const pro = usePro()
  const [plan, setPlan] = useState<PlanId>('yearly')
  const [busy, setBusy] = useState<'buy' | 'restore' | null>(null)
  const [msg, setMsg] = useState('')
  const [welcome, setWelcome] = useState(false)
  if (!open) return null
  const close = () => { closePaywall(); setMsg(''); setWelcome(false) }
  const chosen = PLANS.find((p) => p.id === plan)!

  const buy = async () => {
    setBusy('buy'); setMsg('')
    try { if (await billing.purchase(plan)) { setWelcome(true); navigator.vibrate?.([10, 40, 20]) } }
    catch { setMsg('The purchase didn’t go through. You haven’t been charged.') }
    finally { setBusy(null) }
  }
  const restore = async () => {
    setBusy('restore'); setMsg('')
    const ok = await billing.restore().catch(() => false)
    setBusy(null)
    if (ok) setWelcome(true)
    else setMsg('No Pro purchase found for this account.')
  }

  return (
    <div className="absolute inset-0 z-50 flex flex-col overflow-hidden bg-obsidian" role="dialog" aria-modal="true" aria-labelledby="paywall-title">
      <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(90%_45%_at_50%_0%,rgba(245,158,11,.28),transparent_70%),radial-gradient(70%_40%_at_50%_100%,rgba(153,27,27,.25),transparent_70%)]" />
      <div className="pointer-events-none absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-amber-bright/70 to-transparent" />
      <button type="button" onClick={close} aria-label="Close" className="absolute right-4 top-3 z-10 grid h-10 w-10 place-items-center rounded-full border border-brass/30 bg-obsidian/70 text-cream/80 transition hover:border-amber-bright hover:text-cream active:scale-90">
        <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><path d="M6 6l12 12M18 6 6 18" /></svg>
      </button>

      {welcome || (pro.pro && !busy) ? (
        <div className="relative flex flex-1 flex-col items-center justify-center px-8 text-center">
          <Disc spin />
          <p className="mt-8 deco text-[11px] tracking-[0.3em] text-amber-bright">{welcome ? 'Welcome to' : 'You’re on'}</p>
          <h2 id="paywall-title" className="mt-1 font-display text-5xl leading-none text-cream">Vynyl <em className="text-amber-bright">Pro</em></h2>
          <p className="mt-4 max-w-64 text-sm leading-relaxed text-muted">
            {pro.plan === 'lifetime' ? 'Lifetime access — every feature, forever.' : `${PLANS.find((p) => p.id === pro.plan)?.name ?? 'Pro'} plan${pro.expires ? ` · renews ${new Date(pro.expires).toLocaleDateString()}` : ''}.`} Every mood, character, crackle and track is unlocked.
          </p>
          <button type="button" onClick={close} className="mt-8 min-h-12 w-full max-w-64 rounded-full bg-gradient-to-b from-[#f0c86a] via-[#c99234] to-[#8a5a1a] font-semibold text-obsidian shadow-[0_12px_30px_-10px_rgba(245,158,11,.9)] active:scale-[.98]">Start pressing</button>
          {!welcome && <p className="mt-6 text-[11px] leading-relaxed text-muted/80">Manage or cancel your subscription in Google Play.</p>}
          <button type="button" onClick={() => { billing.setProForTesting(false); setWelcome(false) }} className="mt-4 font-mono text-[11px] text-muted underline underline-offset-2 hover:text-cream">
            ⚡ Test Mode: Relock Paywall
          </button>
        </div>
      ) : (
        <>
          <div className="no-scrollbar relative flex-1 overflow-y-auto px-6 pb-4 pt-6">
            <div className="flex flex-col items-center text-center">
              <Disc />
              <p className="mt-5 deco text-[11px] tracking-[0.3em] text-amber-bright">Vynyl Record</p>
              <h2 id="paywall-title" className="font-display text-[44px] leading-none text-cream">Go <em className="text-amber-bright">Pro.</em></h2>
              <p className="mt-3 max-w-72 text-sm leading-relaxed text-muted">{open.reason ?? 'Press records the way they deserve — every sound, every colour, every minute.'}</p>
            </div>

            <ul className="mt-6 space-y-3">
              {PERKS.map((p) => (
                <li key={p.title} className="flex gap-3">
                  <span className="mt-0.5 grid h-5 w-5 shrink-0 place-items-center rounded-full bg-amber/20 text-amber-bright ring-1 ring-amber/50"><svg viewBox="0 0 12 12" className="h-3 w-3" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M2.5 6.2 5 8.5l4.5-5" /></svg></span>
                  <span><span className="block text-[13px] font-medium text-cream">{p.title}</span><span className="block text-[11px] leading-snug text-muted">{p.body}</span></span>
                </li>
              ))}
            </ul>

            <div className="mt-6 overflow-hidden rounded-2xl border border-brass/25 bg-panel/70">
              <div className="grid grid-cols-[1fr_64px_80px] border-b border-brass/20 px-4 py-2 deco text-[10px] tracking-[0.2em]">
                <span className="text-muted">Compare</span><span className="text-center text-muted">Free</span><span className="text-center text-amber-bright">Pro</span>
              </div>
              {ROWS.map(([k, f, p], i) => (
                <div key={k} className={`grid grid-cols-[1fr_64px_80px] items-center px-4 py-2 text-[12px] ${i % 2 ? 'bg-cream/[.02]' : ''}`}>
                  <span className="text-cream/85">{k}</span><span className="text-center font-mono text-[11px] text-muted">{f}</span><span className="text-center font-mono text-[11px] text-amber-bright">{p}</span>
                </div>
              ))}
            </div>

            <div className="mt-6 space-y-2" role="radiogroup" aria-label="Choose a plan">
              {PLANS.map((p) => {
                const on = plan === p.id
                return (
                  <button key={p.id} type="button" role="radio" aria-checked={on} onClick={() => setPlan(p.id)}
                    className={`relative flex w-full items-center gap-3 rounded-2xl border p-4 text-left transition ${on ? 'border-amber-bright bg-[linear-gradient(135deg,rgba(245,158,11,.2),rgba(245,158,11,.04))] shadow-[0_14px_30px_-18px_rgba(245,158,11,.9)]' : 'border-brass/25 bg-panel hover:border-brass/60'}`}>
                    {p.badge && <span className="absolute -top-2.5 right-4 rounded-full bg-gradient-to-b from-[#f0c86a] to-[#b8862e] px-2.5 py-0.5 font-mono text-[9px] font-medium uppercase tracking-[0.12em] text-obsidian">{p.badge}</span>}
                    <span className={`grid h-5 w-5 shrink-0 place-items-center rounded-full border-2 ${on ? 'border-amber-bright' : 'border-brass/50'}`}>{on && <span className="h-2.5 w-2.5 rounded-full bg-amber-bright" />}</span>
                    <span className="flex-1"><span className="block font-display text-lg leading-tight text-cream">{p.name}</span><span className="block text-[11px] text-muted">{p.note}</span></span>
                    <span className="text-right"><span className="block font-display text-xl leading-none text-cream">{p.price}</span><span className="font-mono text-[10px] text-muted">{p.per}</span></span>
                  </button>
                )
              })}
            </div>
          </div>

          <div className="relative border-t border-brass/20 bg-stone/95 px-6 pb-4 pt-3">
            {msg && <p role="alert" className="mb-2 text-center text-xs text-err">{msg}</p>}
            <button type="button" onClick={buy} disabled={!!busy}
              className="flex min-h-[52px] w-full items-center justify-center gap-2 rounded-full bg-gradient-to-b from-[#f0c86a] via-[#c99234] to-[#8a5a1a] font-semibold text-obsidian shadow-[0_12px_30px_-10px_rgba(245,158,11,.9)] transition active:scale-[.98] disabled:opacity-70">
              {busy === 'buy' ? <span className="h-4 w-4 animate-spin rounded-full border-2 border-obsidian border-t-transparent" /> : <Crown />}
              {busy === 'buy' ? 'Confirming…' : plan === 'lifetime' ? `Unlock Pro forever · ${chosen.price}` : `Continue · ${chosen.price} ${chosen.per}`}
            </button>
            <div className="mt-2.5 flex items-center justify-between text-[11px] text-muted">
              <button type="button" onClick={restore} disabled={!!busy} className="min-h-8 underline decoration-brass/50 underline-offset-2 hover:text-cream">{busy === 'restore' ? 'Checking…' : 'Restore purchase'}</button>
              <span className="flex gap-3"><a href="#terms" className="hover:text-cream">Terms</a><a href="#privacy" className="hover:text-cream">Privacy</a></span>
            </div>
            <p className="mt-1.5 text-center text-[10px] leading-snug text-muted/70">{plan === 'lifetime' ? 'One-time purchase.' : 'Renews automatically until cancelled in Google Play.'} Demo build — no payment is taken.</p>
            <div className="mt-2.5 flex justify-center">
              <button
                type="button"
                onClick={() => { billing.setProForTesting(true); setWelcome(true) }}
                className="rounded-lg border border-amber-bright/60 bg-amber/20 px-3.5 py-1.5 font-mono text-[11px] text-amber-bright transition hover:bg-amber/30 active:scale-95"
              >
                ⚡ Test Mode: Unlock Pro Free
              </button>
            </div>
          </div>
        </>
      )}
    </div>
  )
}

function Disc({ spin }: { spin?: boolean }) {
  return (
    <div className="relative h-28 w-28">
      <span className="absolute inset-0 rounded-full bg-amber-bright/25 blur-2xl" aria-hidden="true" />
      <div className={`relative grid h-full w-full place-items-center rounded-full bg-[repeating-radial-gradient(circle,#15110e_0_2px,#2a221b_2.5px_3.5px)] shadow-[0_18px_40px_-14px_black,0_0_0_1px_rgba(245,158,11,.4)] ${spin ? 'animate-spin [animation-duration:3s]' : ''}`}>
        <span className="absolute inset-0 rounded-full bg-[conic-gradient(from_30deg,transparent_0_20%,rgba(254,243,199,.14)_25%,transparent_32%_70%,rgba(254,243,199,.1)_75%,transparent_82%)]" aria-hidden="true" />
        <span className="relative grid h-11 w-11 place-items-center rounded-full bg-[radial-gradient(circle_at_35%_30%,#fbbf24,#b45309)] text-obsidian shadow-[inset_0_1px_3px_rgba(255,255,255,.4)]"><Crown /></span>
      </div>
    </div>
  )
}
