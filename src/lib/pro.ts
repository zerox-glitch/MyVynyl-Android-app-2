import { useSyncExternalStore } from 'react'
import { SR, encodeWav, withNeedleDrop } from './dsp'
import { MOODS } from './presets'

/**
 * Free vs Pro. Everything gated in the app reads from this one file.
 *
 * BILLING: `billing` below is a DEMO adapter — purchases are stored in localStorage and no money moves.
 * In the Android build, replace only `billing.purchase` and `billing.restore` with Google Play Billing
 * (Digital Goods API in a Trusted Web Activity, or a Capacitor billing plugin). Google restores purchases
 * per Google account, so `restore()` just asks Play what this account owns. See HANDOFF.md.
 */

export const FREE = {
  maxSeconds: 3 * 60,
  maxRecords: 3,
  maxVoices: 2,
  /** times a free user may go back to the Studio to re-press one record */
  reedits: 3,
  presets: ['clean', 'warm', 'dusty'],
  crackles: ['preset', 'crisp', 'ticktick', 'fireside', 'rain'],
  music: ['none', 'serenade', 'piano', 'hearth', 'musicbox'],
  styles: ['ruby', 'sapphire', 'gold'],
}
export const PRO_SECONDS = 20 * 60

export type Gate = 'preset' | 'crackle' | 'music' | 'style' | 'mood'
/** True when a free user may use this item. Moods are free only if everything they set is free. */
export function isFree(kind: Gate, id: string): boolean {
  if (kind === 'preset') return FREE.presets.includes(id)
  if (kind === 'crackle') return FREE.crackles.includes(id)
  // music the person uploads themselves is always free
  if (kind === 'music') return id === 'custom' || FREE.music.includes(id)
  if (kind === 'style') return FREE.styles.includes(id)
  const m = MOODS.find((x) => x.id === id)
  return !!m && isFree('preset', m.presetId) && isFree('crackle', m.crackleId) && isFree('music', m.musicId)
}

export type PlanId = 'monthly' | 'yearly' | 'lifetime'
export const PLANS: { id: PlanId; name: string; price: string; per: string; note?: string; badge?: string }[] = [
  { id: 'yearly', name: 'Yearly', price: '$24.99', per: '/ year', note: 'Just $2.08 a month', badge: 'Best value · save 48%' },
  { id: 'monthly', name: 'Monthly', price: '$3.99', per: '/ month', note: 'Cancel anytime' },
  { id: 'lifetime', name: 'Lifetime', price: '$49.99', per: 'once', note: 'Pay once, keep Pro forever' },
]

export const PERKS = [
  { title: 'Twenty-minute recordings', body: 'Whole stories, songs and letters — not just three minutes.' },
  { title: 'Unlimited saved voices', body: 'Save as many recorded voices in the master vault as you like.' },
  { title: 'Every mood, character & crackle', body: 'Gramophone, war radio, TV broadcast, shellac and more.' },
  { title: 'The full music library', body: 'Romantic, vintage and dance-band beds for every occasion.' },
  { title: 'Unlimited records', body: 'Keep every voice on your shelf, not just three.' },
  { title: 'Gold nameplate & photo studio', body: 'Glowing names on the plinth, crop and full-label photos.' },
  { title: 'Clean, untagged exports', body: 'Full-quality WAV masters without the Vynyl tag.' },
  { title: 'Full-length 3D videos', body: '1080p videos of your spinning record — no watermark, no time limit.' },
]

type Entitlement = { pro: boolean; plan?: PlanId; since?: number; expires?: number }
const KEY = 'vynyl.entitlement'
const DAY = 86400000

function load(): Entitlement {
  try {
    const e = JSON.parse(localStorage.getItem(KEY) ?? 'null') as Entitlement | null
    if (e?.pro && (!e.expires || e.expires > Date.now())) return e
  } catch { /* corrupt or unavailable storage → free */ }
  return { pro: false }
}

let ent = load()
let paywall: { reason?: string } | null = null
const subs = new Set<() => void>()
const emit = () => subs.forEach((f) => f())
const subscribe = (f: () => void) => { subs.add(f); return () => subs.delete(f) }
const setEnt = (e: Entitlement) => { ent = e; try { localStorage.setItem(KEY, JSON.stringify(e)) } catch { /* private mode */ } emit() }

export const usePro = () => useSyncExternalStore(subscribe, () => ent)
export const usePaywall = () => useSyncExternalStore(subscribe, () => paywall)
export const openPaywall = (reason?: string) => { paywall = { reason }; emit(); navigator.vibrate?.(8) }
export const closePaywall = () => { paywall = null; emit() }

export const billing = {
  /** DEMO: grants Pro locally. Replace with Google Play Billing in the Android build. */
  async purchase(plan: PlanId): Promise<boolean> {
    await new Promise((r) => setTimeout(r, 900))
    const now = Date.now()
    setEnt({ pro: true, plan, since: now, expires: plan === 'monthly' ? now + 30 * DAY : plan === 'yearly' ? now + 365 * DAY : undefined })
    return true
  },
  /** DEMO: re-reads the local entitlement. On Android, ask Google Play what this account owns. */
  async restore(): Promise<boolean> {
    await new Promise((r) => setTimeout(r, 700))
    ent = load(); emit()
    return ent.pro
  },
  /** DEMO only — lets testers return to the free tier. Real cancellations happen in Google Play. */
  signOutDemo() { try { localStorage.removeItem(KEY) } catch { /* ignore */ } ent = { pro: false }; emit() },
  /** Testing helper to unlock/lock Pro during development and testing. */
  setProForTesting(pro: boolean) {
    if (pro) setEnt({ pro: true, plan: 'lifetime', since: Date.now() })
    else this.signOutDemo()
  },
}

/** Free exports get a soft music-box "Vynyl" tag appended. Masters are 16-bit stereo WAVs from encodeWav. */
export async function watermarkWav(master: Blob): Promise<Blob> {
  const d = new DataView(await master.arrayBuffer()), n = (d.byteLength - 44) / 4
  const tagLen = Math.floor(SR * 2.4), gap = Math.floor(SR * 0.4), total = n + gap + tagLen
  const l = new Float32Array(total), r = new Float32Array(total)
  for (let i = 0; i < n; i++) { l[i] = d.getInt16(44 + i * 4, true) / 32768; r[i] = d.getInt16(46 + i * 4, true) / 32768 }
  const notes = [72, 76, 79, 84], st = n + gap
  notes.forEach((m, k) => {
    const f = 440 * Math.pow(2, (m - 69) / 12), at = st + Math.floor(k * 0.16 * SR)
    for (let i = 0; at + i < total; i++) {
      const t = i / SR, e = Math.min(1, t / 0.004) * Math.exp(-t * 2.6)
      const s = Math.sin(2 * Math.PI * f * t + 0.5 * Math.exp(-t * 4) * Math.sin(2 * Math.PI * f * 3 * t)) * e * 0.12
      l[at + i] += s * (1 - k * 0.15); r[at + i] += s * (0.55 + k * 0.15)
    }
  })
  return encodeWav(l, r)
}

/** Turntable video export limits. Free: 720p, first 30 s, on-screen watermark + chime. Pro: 1080p, full length, clean. */
export type VideoTier = { size: number; maxSeconds?: number; watermark: boolean; bitrate: number }
export const FREE_VIDEO_SECONDS = 30
export const videoTier = (pro: boolean): VideoTier =>
  pro ? { size: 1080, watermark: false, bitrate: 12_000_000 } : { size: 720, maxSeconds: FREE_VIDEO_SECONDS, watermark: true, bitrate: 6_000_000 }

/** Soundtrack for a video export: the full master for Pro; the first 30 s faded out, plus the chime, for free. */
export async function videoAudio(master: Blob, tier: VideoTier): Promise<Blob> {
  let wav = await withNeedleDrop(master)
  if (tier.maxSeconds) {
    const buf = await wav.arrayBuffer(), n = (buf.byteLength - 44) / 4, keep = Math.min(n, Math.floor(tier.maxSeconds * SR))
    if (keep < n) {
      const d = new DataView(buf), fade = Math.min(keep, Math.floor(SR * 1.5))
      const l = new Float32Array(keep), r = new Float32Array(keep)
      for (let i = 0; i < keep; i++) {
        const g = i < keep - fade ? 1 : (keep - i) / fade
        l[i] = (d.getInt16(44 + i * 4, true) / 32768) * g; r[i] = (d.getInt16(46 + i * 4, true) / 32768) * g
      }
      wav = encodeWav(l, r)
    }
  }
  return tier.watermark ? watermarkWav(wav) : wav
}
