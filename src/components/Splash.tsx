import { useEffect, useState } from 'react'
import Logo from './Logo'

/** Launch animation: the tile lands, the record spins up and settles with its V-notch locked at the top, then the wordmark rises. */
export default function Splash() {
  const [gone, setGone] = useState(false)
  useEffect(() => { const t = setTimeout(() => setGone(true), 2900); return () => clearTimeout(t) }, [])
  if (gone) return null
  return (
    <div className="splash absolute inset-0 z-50 flex flex-col items-center justify-center bg-obsidian" aria-hidden>
      <Logo size={112} className="splash-tile drop-shadow-[0_18px_40px_rgba(217,119,6,.35)]" discClass="splash-disc" />
      <p className="splash-word mt-7 font-deco text-4xl font-bold tracking-[.42em] text-amber-bright">VYNYL</p>
      <p className="splash-tag mt-2 font-display text-sm italic text-cream/60">press a moment to wax</p>
    </div>
  )
}
