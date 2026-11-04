import { FREE } from './pro'

/** Everything the Studio needs to re-press a record from its original voice. */
export type StudioSettings = {
  presetId: string; styleId: string; crackleId: string; music: string
  musicLevel: number; crackleLevel: number; character: number; volume: number; moodId?: string | null
}

export type RecordMeta = {
  id: string; title: string; recipient: string; sender: string; dedication: string
  occasion: string; date: string; sideA: string; sideB: string
  presetId: string; styleId: string; duration: number; wave: number[]
  createdAt: number; lastPlayedAt?: number; favorite: boolean
}
export type PhotoAdjust = { mode: 'fill' | 'fit'; zoom: number; x: number; y: number; rot: number; bg: 'blur' | 'label' }
export type StoredRecord = RecordMeta & {
  master: Blob; labelPhoto?: Blob; labelPhotoOriginal?: Blob; labelPhotoAdjust?: PhotoAdjust; crackleId?: string; musicId?: string
  /** original voice saved beside the master, so the record can go back to the Studio */
  voice?: Float32Array; settings?: StudioSettings
  /** trips back to the Studio so far (free users get FREE.reedits) */
  reedits?: number
  /** your own background music: the trimmed, loop-ready bed, and the file it came from */
  customMusic?: Float32Array; customMusicName?: string
  /** example records built from a mood's sample voice — shown in the Player, never stored */
  sample?: boolean; sampleMood?: string
}

/** Saved recorded voice in Master Vault, available for reuse across records. */
export type VaultVoice = {
  id: string; name: string; duration: number; createdAt: number; recordTitle?: string; voice: Float32Array
}

const open = () =>
  new Promise<IDBDatabase>((res, rej) => {
    const r = indexedDB.open('vynyl', 2)
    r.onupgradeneeded = () => {
      const d = r.result
      if (!d.objectStoreNames.contains('records')) d.createObjectStore('records', { keyPath: 'id' })
      if (!d.objectStoreNames.contains('voices')) d.createObjectStore('voices', { keyPath: 'id' })
    }
    r.onsuccess = () => res(r.result)
    r.onerror = () => rej(r.error)
  })

async function tx<T>(store: 'records' | 'voices', mode: IDBTransactionMode, fn: (s: IDBObjectStore) => IDBRequest<T>) {
  const db = await open()
  return new Promise<T>((res, rej) => {
    const q = fn(db.transaction(store, mode).objectStore(store))
    q.onsuccess = () => res(q.result)
    q.onerror = () => rej(q.error)
  })
}

export const db = {
  all: () => tx<StoredRecord[]>('records', 'readonly', (s) => s.getAll() as IDBRequest<StoredRecord[]>),
  put: (r: StoredRecord) => tx('records', 'readwrite', (s) => s.put(r)),
  del: (id: string) => tx('records', 'readwrite', (s) => s.delete(id)),
  voices: () => tx<VaultVoice[]>('voices', 'readonly', (s) => s.getAll() as IDBRequest<VaultVoice[]>),
  async saveVaultVoice(opts: { id: string; name: string; voice: Float32Array; duration: number; recordTitle?: string; isPro?: boolean }): Promise<VaultVoice | null> {
    const list = await tx<VaultVoice[]>('voices', 'readonly', (s) => s.getAll() as IDBRequest<VaultVoice[]>)
    const existing = list.find((v) => v.id === opts.id)
    if (!existing && !opts.isPro && list.length >= FREE.maxVoices) return null
    const updated: VaultVoice = existing
      ? { ...existing, name: opts.name, voice: opts.voice, duration: opts.duration, recordTitle: opts.recordTitle ?? existing.recordTitle }
      : { id: opts.id, name: opts.name, duration: opts.duration, createdAt: Date.now(), recordTitle: opts.recordTitle, voice: opts.voice }
    await tx('voices', 'readwrite', (s) => s.put(updated))
    return updated
  },
  delVoice: (id: string) => tx('voices', 'readwrite', (s) => s.delete(id)),
}
