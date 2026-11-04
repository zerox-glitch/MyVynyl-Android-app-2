import { SR, decodeToMono, encodeWav, renderMaster, waveform } from './dsp'
import { CRACKLES, MOODS, PRESETS, STYLES } from './presets'
import { musicBed } from './music'
import type { StoredRecord } from './db'

/**
 * One example record per mood, spoken by a fitting person, so people can hear what a mood does before choosing.
 * Voices are pre-generated with open, commercially usable neural voices (Piper: public-domain LibriVox voices
 * and LibriTTS-R, CC BY 4.0) and live in public/samples. Mirrors Android audio/Samples.kt.
 */
export type Sample = { moodId: string; persona: string; from: string; to: string; occasion: string; script: string; styleId: string }

const RAW: [string, string, string, string, string, string][] = [
  ['moonlit', 'Boyfriend', 'Sam', 'Ella', 'Love Letter', 'Every night under this moon, I think of you. So here it is, out loud. Will you marry me?'],
  ['firstdance', 'Husband', 'Daniel', 'Grace', 'Anniversary', "This was playing the night we first danced. I'd ask you again, every single time. I love you."],
  ['candle', 'Girlfriend', 'Mia', 'Leo', 'Love Letter', "Table for two, candles lit, and you right across from me. That's my favourite place in the whole world."],
  ['serenata', 'Boyfriend', 'Mateo', 'Lucia', 'Love Letter', "I can't play the guitar, but I can tell you this. You are the best thing that ever happened to me."],
  ['sunday', 'Mother', 'Mum', 'Olivia', 'Family Memory', "Good morning, sweetheart. The kettle's on, the sun is out, and I am so proud of you. Always."],
  ['jazzclub', 'Grandfather', 'Grandpa Joe', 'The grandkids', 'Grandparents', 'Your grandmother and I danced to this in 1958. She stole my heart that night, and kept it.'],
  ['paris', 'Girlfriend', 'Chloé', 'Tom', 'Long Distance', "Thousands of miles away, and I still hear your laugh every morning. Count the days with me. I'm coming home."],
  ['lullaby', 'Mother', 'Mummy', 'Baby Rose', 'Baby', "Hush now, little one. Close your eyes. Mummy's right here, and I love you to the moon and back."],
  ['attic', 'Grandmother', 'Nana Ruth', 'Theo', 'Grandparents', 'When you find this, remember our summers in the garden. You were always my favourite little helper.'],
  ['chapel', 'Son', 'James', 'Dad', 'Memorial', 'Dad, I still hear your voice in every quiet room. Thank you for everything. We miss you every day.'],
  ['crooner', 'Husband', 'Frank', 'Margaret', 'Anniversary', "Forty years, darling, and you still make my heart skip. Put your feet up. This one's for you."],
  ['diner', 'Girlfriend', 'Ruby', 'Jack', 'Anniversary', 'Two straws, one milkshake, and you stealing all my fries. Happy anniversary. I love you.'],
  ['overseas', 'Soldier', 'Michael', 'Sarah & the kids', 'Long Distance', "Hello love, it's me. It's cold out here, but your letters keep me warm. Kiss the kids for me."],
  ['gramo', 'Grandfather', 'Grandpa Arthur', 'The family', 'Family Memory', 'Gather round, children. Let me tell you how your great grandmother and I first met. It was a rainy Tuesday.'],
  ['dream', 'Girlfriend', 'Zoe', 'Ben', 'Love Letter', 'I dreamed about you again last night. We were laughing, and nothing else mattered. Sweet dreams, my love.'],
  ['vows', 'Groom', 'Noah', 'Emma', 'Wedding', 'I promise to hold your hand through every storm, and to dance with you in every kitchen. Forever.'],
  ['starry', 'Boyfriend', 'Ethan', 'Ava', 'Long Distance', "Look up tonight. Whichever star you pick, that's me, thinking about you. Goodnight, beautiful."],
  ['hearthside', 'Father', 'Dad', 'Lily', 'Family Memory', 'Hey kiddo. Pull up a chair by the fire. Whatever happens out there, home is always right here.'],
  ['sweethearts', 'Grandmother', 'Grandma Edith', 'Little Annie', 'Grandparents', "He wrote to me every week of the war. Now I'm writing to you, my darling. Love wins, every time."],
  ['twenties', 'Grandfather', 'Grandpa George', 'Henry', 'Birthday', 'Happy birthday, my boy! Ninety years young, and I could still out-dance anyone at this party.'],
  ['loveletter', 'Wife', 'Hannah', 'Chris', 'Love Letter', "I don't say it enough, so I'm saying it here, forever. Thank you for loving me the way you do."],
  ['tvnight', 'Father', 'Dad', 'The family', 'Family Memory', "Good evening, family! Tonight's special broadcast. Dad says he loves you, and dinner is at seven."],
  ['warfront', 'Soldier', 'Private Evans', 'Mum', 'Long Distance', "This is Private Evans, calling home. Tell Mum I'm safe, and I'll be back before the snow falls."],
]

export const SAMPLES: Sample[] = RAW.map(([moodId, persona, from, to, occasion, script], i) => ({ moodId, persona, from, to, occasion, script, styleId: STYLES[i % STYLES.length].id }))

export const sampleFor = (moodId: string) => SAMPLES.find((s) => s.moodId === moodId)

const voices = new Map<string, Promise<Float32Array>>()

/** The raw sample voice for a mood (decoded once, cached). */
export function sampleVoice(moodId: string): Promise<Float32Array> {
  let p = voices.get(moodId)
  if (!p) {
    p = fetch(`${import.meta.env.BASE_URL}samples/${moodId}.m4a`)
      .then((r) => { if (!r.ok) throw new Error('Sample missing'); return r.blob() })
      .then(decodeToMono)
    p.catch(() => voices.delete(moodId))
    voices.set(moodId, p)
  }
  return p
}

const records = new Map<string, Promise<StoredRecord>>()

/** The sample voice pressed through its mood — a full record the Player can spin. Never saved to the shelf. */
export function sampleRecord(moodId: string): Promise<StoredRecord> {
  let p = records.get(moodId)
  if (!p) {
    p = (async () => {
      const s = sampleFor(moodId), mood = MOODS.find((m) => m.id === moodId)
      if (!s || !mood) throw new Error('Unknown sample')
      const voice = await sampleVoice(moodId)
      const preset = PRESETS.find((x) => x.id === mood.presetId)!
      const [l, r] = await renderMaster(voice, {
        preset, crackle: CRACKLES.find((c) => c.id === mood.crackleId), seed: 'sample:' + moodId,
        music: musicBed(mood.musicId), musicLevel: mood.musicLevel, intro: 1.2, tail: 1,
      })
      return {
        id: 'sample-' + moodId, title: mood.name, recipient: s.to, sender: s.from, dedication: s.script,
        occasion: s.occasion, date: `${s.persona}'s message`, sideA: 'Side A', sideB: 'Side B',
        presetId: mood.presetId, styleId: s.styleId, crackleId: mood.crackleId, musicId: mood.musicId,
        duration: l.length / SR, wave: waveform(l), createdAt: 0, favorite: false, master: encodeWav(l, r),
        sample: true, sampleMood: moodId,
      }
    })()
    p.catch(() => records.delete(moodId))
    records.set(moodId, p)
  }
  return p
}
