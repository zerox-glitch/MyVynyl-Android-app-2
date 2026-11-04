import { useState } from 'react'
import { CRACKLES, MOODS, PRESETS, STYLES } from '../lib/presets'
import { MUSIC } from '../lib/music'

type GItem = { name: string; text: string }
type GSection = { id: string; title: string; intro: string; tip?: string; items?: GItem[] }

/** Plain-language help for everyone — big type, short sentences, no jargon. Mirrors Android Guide.kt. */
const GUIDE: GSection[] = [
  {
    id: 'steps',
    title: 'Making a record, step by step',
    intro: 'There are five simple steps. Tap Next at the bottom to move on, and Back to go a step back and make changes. Tap Record at the bottom of the screen at any time to go straight to recording — nothing you have done is lost.',
    items: [
      { name: '1. Capture', text: 'Tap the big gold button and speak. Tap it again to stop. You can also choose a recording already on your device.' },
      { name: '2. Dedication', text: 'Write who the record is for, who it is from, and a short message. These words are printed on the record label.' },
      { name: '3. Character', text: 'Pick a mood — it chooses the sound for you. Tap Listen to hear your own voice in that mood, or Example to hear someone else\'s message. Experts can open “Customise the sound yourself” to choose each part.' },
      { name: '4. Appearance', text: 'Choose the colour of your record.' },
      { name: '5. Press', text: 'Tap Press and wait a few seconds. Your record is saved on your shelf. Then you can play it, or go back and make changes.' },
    ],
  },
  {
    id: 'moods',
    title: 'Moods — the easy choice',
    intro: 'A mood is a ready-made recipe. One tap chooses the sound style, the crackle and the music for you, all at once. If you are not sure what to pick, just choose a mood.',
    tip: 'Tap Listen on any mood to hear your recording in it for a few seconds. Tap Example to hear a message someone else made with it.',
    items: MOODS.map((m) => ({ name: m.name, text: m.blurb })),
  },
  {
    id: 'style',
    title: 'Sound style',
    intro: 'This changes how your voice sounds. Some styles keep your voice clear and modern. Others make it sound old — like a family record from long ago, or a radio broadcast.',
    tip: 'The little bars show Warmth (how soft and cosy), Age (how old it sounds) and Texture (how much grain and noise).',
    items: PRESETS.map((p) => ({ name: p.name, text: p.blurb })),
  },
  {
    id: 'crackle',
    title: 'Crackle',
    intro: 'Real records make small ticks, pops and a soft hiss as they spin. This is called crackle. It makes your record feel real and old-fashioned.',
    tip: 'Want no crackle at all? Choose “Silent Surface”.',
    items: CRACKLES.map((c) => ({ name: c.name, text: c.blurb })),
  },
  {
    id: 'music',
    title: 'Background music',
    intro: 'Soft music that plays quietly underneath your voice. Your voice always stays on top, so it is easy to hear.',
    tip: 'Want only your voice? Choose “No background”. Music marked ♥ is extra romantic. You can also upload your own song and choose which part to use — it is under “Upload your own music”.',
    items: MUSIC.map((m) => ({ name: m.romantic ? `♥ ${m.name}` : m.name, text: m.blurb })),
  },
  {
    id: 'sliders',
    title: 'The sliders',
    intro: 'Slide left for less, right for more.',
    items: [
      { name: 'Overall volume', text: 'How loud the whole record is.' },
      { name: 'Music volume', text: 'How loud the background music is. Slide all the way left to turn it off.' },
      { name: 'Character strength', text: 'How strong the sound style is. Left is clean, right is very old-sounding.' },
      { name: 'Crackle volume', text: 'How loud the ticks and hiss are. Slide all the way left to turn them off.' },
    ],
  },
  {
    id: 'colour',
    title: 'Record colour',
    intro: 'In the Appearance step you choose the colour of the vinyl and its label. It does not change the sound — only the look.',
    items: STYLES.map((s) => ({ name: s.name, text: '' })),
  },
  {
    id: 'play',
    title: 'Playing your record',
    intro: 'Your record plays on a turntable, just like the real thing.',
    items: [
      { name: 'Play', text: 'Tap play. The arm moves over and the needle drops onto the record — you will hear a soft thump, then your record.' },
      { name: 'Look around', text: 'Drag with one finger to turn the turntable. Pinch with two fingers to move closer or further away.' },
      { name: 'Add a photo', text: 'Put a photo of a loved one in the middle of the record.' },
      { name: 'Gold nameplate', text: 'Shows both your names in gold on the turntable.' },
      { name: 'Back to Studio', text: 'Changed your mind? Go back and change the music, crackle or mood. Your voice is kept, so you do not need to record again.' },
    ],
  },
  {
    id: 'shelf',
    title: 'Your shelf',
    intro: 'Every record you make is kept on your shelf. Tap a record to play it. You can share it as sound, or as a video of the record spinning. Your records stay on this device unless you share them.',
  },
  {
    id: 'samples',
    title: 'Sample records',
    intro: 'Not sure which mood to pick? In the Master Vault, open Samples. There is one example record for every mood — from a grandfather, a boyfriend, a mother and more. Play one on the turntable, then tap “Use this mood for my record”.',
  },
  {
    id: 'pro',
    title: 'Free and Pro',
    intro: 'You can make records for free. Some extras — more sounds, colours, longer recordings, the gold nameplate and changing a record more than 3 times after pressing — need Vynyl Pro. Anything marked PRO is part of it. You can listen to every mood and sample for free.',
  },
]

export default function Guide({ onClose }: { onClose: () => void }) {
  const [open, setOpen] = useState<string | null>('steps')
  return (
    <div className="absolute inset-0 z-40 flex flex-col bg-obsidian" role="dialog" aria-modal="true" aria-labelledby="guide-title">
      <header className="flex items-center justify-between border-b border-cream/10 px-6 py-4">
        <div>
          <p className="deco text-[11px] text-amber-bright">Guide</p>
          <h2 id="guide-title" className="font-display text-[30px] leading-tight">How Vynyl works</h2>
        </div>
        <button type="button" onClick={onClose} className="rounded-full border border-cream/20 px-5 py-3 text-base text-cream transition hover:border-amber-bright active:scale-95">
          Close
        </button>
      </header>
      <div className="no-scrollbar flex-1 space-y-3 overflow-y-auto p-5">
        {GUIDE.map((s) => {
          const on = open === s.id
          return (
            <section key={s.id} className={`overflow-hidden rounded-2xl border transition ${on ? 'border-amber/50 bg-stone' : 'border-cream/10 bg-stone/60'}`}>
              <button
                type="button"
                onClick={() => setOpen(on ? null : s.id)}
                aria-expanded={on}
                className="flex min-h-16 w-full items-center justify-between px-5 text-left"
              >
                <span className="text-[18px] font-medium text-cream">{s.title}</span>
                <span className={`text-2xl text-amber-bright transition-transform ${on ? 'rotate-45' : ''}`} aria-hidden>+</span>
              </button>
              {on && (
                <div className="px-5 pb-5">
                  <p className="text-[17px] leading-[27px] text-cream/90">{s.intro}</p>
                  {s.tip && (
                    <p className="mt-3 rounded-xl bg-amber/10 px-4 py-3 text-[16px] leading-[25px] text-amber-bright">
                      Tip: {s.tip}
                    </p>
                  )}
                  {s.items?.map((it, i) => (
                    <div key={it.name} className={`py-3 ${i > 0 ? 'border-t border-cream/10' : 'pt-4'}`}>
                      <p className="text-[16px] font-semibold text-cream">{it.name}</p>
                      {it.text && <p className="mt-0.5 text-[16px] leading-[25px] text-muted">{it.text}</p>}
                    </div>
                  ))}
                </div>
              )}
            </section>
          )
        })}
        <button
          type="button"
          onClick={onClose}
          className="flex h-14 w-full items-center justify-center rounded-full bg-gradient-to-b from-[#f0c86a] via-[#c99234] to-[#8a5a1a] deco text-sm text-obsidian shadow-[0_12px_30px_-10px_rgba(245,158,11,.9)] active:scale-[.98]"
        >
          Got it
        </button>
      </div>
    </div>
  )
}
