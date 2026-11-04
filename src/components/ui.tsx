import type { ButtonHTMLAttributes, ReactNode } from 'react'

export function Btn({ variant = 'primary', className = '', ...p }: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: 'primary' | 'ghost' | 'quiet' }) {
  const v = {
    primary: 'bg-amber text-obsidian hover:bg-amber-bright shadow-[0_0_24px_-6px_#d97706]',
    ghost: 'border border-brass/50 text-cream hover:border-amber-bright hover:bg-amber/10',
    quiet: 'text-muted hover:text-cream',
  }[variant]
  return <button {...p} className={`min-h-11 rounded-full px-5 text-sm font-semibold transition disabled:opacity-40 disabled:pointer-events-none focus-visible:outline-2 focus-visible:outline-amber-bright focus-visible:outline-offset-2 ${v} ${className}`} />
}

export const Eyebrow = ({ children }: { children: ReactNode }) => <p className="deco text-[11px] text-amber-bright/90">{children}</p>

export function Meter({ n, label }: { n: number; label: string }) {
  return (
    <div className="flex items-center gap-2" aria-label={`${label} ${n} of 5`}>
      <span className="w-14 text-[11px] text-muted">{label}</span>
      <div className="flex gap-1">{[1, 2, 3, 4, 5].map((i) => <span key={i} className={`h-1.5 w-4 rounded-full ${i <= n ? 'bg-amber' : 'bg-cream/10'}`} />)}</div>
    </div>
  )
}

export function Wave({ data, progress = 1, className = '' }: { data: number[]; progress?: number; className?: string }) {
  return (
    <div className={`flex items-center gap-[2px] ${className}`} aria-hidden>
      {data.map((v, i) => <span key={i} className={`flex-1 rounded-full transition-colors ${i / data.length <= progress ? 'bg-amber-bright' : 'bg-cream/15'}`} style={{ height: `${Math.max(8, v * 100)}%` }} />)}
    </div>
  )
}

export const fmt = (s: number) => `${Math.floor(s / 60)}:${String(Math.floor(s % 60)).padStart(2, '0')}`
