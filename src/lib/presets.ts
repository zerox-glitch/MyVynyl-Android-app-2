export type Preset = {
  id: string; name: string; blurb: string
  warmth: number; age: number; texture: number
  rolloff: number; surfaceDb: number; drive: number; crackles: number; pops: number
  wowDepth: number; wowHz: number; flutterDepth: number; width: number
  /** optional broadcast colouring: low cut (Hz), mains hum level, mid honk (dB) */
  lowcut?: number; hum?: number; honk?: number
}

export const PRESETS: Preset[] = [
  { id: 'clean', name: 'Clean Vinyl', blurb: 'A fresh pressing. Your voice, barely touched by time.', warmth: 1, age: 1, texture: 1, rolloff: 16500, surfaceDb: -42, drive: 0.15, crackles: 10, pops: 0.1, wowDepth: 0.0004, wowHz: 0.12, flutterDepth: 0.00004, width: 1 },
  { id: 'warm', name: 'Warm Vintage', blurb: 'Tape warmth and a soft hush, like a Sunday morning.', warmth: 3, age: 2, texture: 2, rolloff: 14200, surfaceDb: -34, drive: 0.35, crackles: 22, pops: 0.4, wowDepth: 0.0007, wowHz: 0.15, flutterDepth: 0.00006, width: 0.9 },
  { id: 'dusty', name: 'Dusty Record', blurb: 'Found in a crate, loved by many hands before yours.', warmth: 3, age: 3, texture: 4, rolloff: 12600, surfaceDb: -28, drive: 0.45, crackles: 38, pops: 1, wowDepth: 0.0009, wowHz: 0.18, flutterDepth: 0.00008, width: 0.8 },
  { id: 'family', name: 'Old Family Record', blurb: 'The one that lived by the radiogram for forty years.', warmth: 4, age: 4, texture: 4, rolloff: 11200, surfaceDb: -26, drive: 0.6, crackles: 42, pops: 1.4, wowDepth: 0.0016, wowHz: 0.09, flutterDepth: 0.0001, width: 0.6 },
  { id: 'archival', name: 'Rare Archival', blurb: 'A voice recovered from another century — still clear.', warmth: 5, age: 5, texture: 5, rolloff: 9800, surfaceDb: -24, drive: 0.7, crackles: 48, pops: 2, wowDepth: 0.0018, wowHz: 0.07, flutterDepth: 0.00012, width: 0.4 },
  { id: 'velvet', name: 'Velvet Lounge', blurb: 'Rich and close, like a crooner leaning into the mic.', warmth: 4, age: 2, texture: 2, rolloff: 13000, surfaceDb: -36, drive: 0.5, crackles: 16, pops: 0.3, wowDepth: 0.0006, wowHz: 0.14, flutterDepth: 0.00005, width: 1 },
  { id: 'jukebox', name: 'Diner Jukebox', blurb: 'A punchy 45 spinning in a chrome-and-neon diner.', warmth: 3, age: 3, texture: 3, rolloff: 12000, surfaceDb: -31, drive: 0.75, crackles: 30, pops: 0.8, wowDepth: 0.0008, wowHz: 0.2, flutterDepth: 0.00009, width: 0.7 },
  { id: 'radio', name: 'Wartime Radio', blurb: 'A crackling broadcast from far across the sea.', warmth: 2, age: 4, texture: 4, rolloff: 6200, surfaceDb: -25, drive: 0.85, crackles: 34, pops: 1.2, wowDepth: 0.0012, wowHz: 0.11, flutterDepth: 0.00015, width: 0.15 },
  { id: 'gramophone', name: 'Brass Gramophone', blurb: 'Wound by hand and sung through a great brass horn.', warmth: 5, age: 5, texture: 5, rolloff: 5200, surfaceDb: -22, drive: 0.9, crackles: 55, pops: 2.4, wowDepth: 0.0024, wowHz: 0.08, flutterDepth: 0.00018, width: 0 },
  { id: 'dreamy', name: 'Dreamy Haze', blurb: 'Soft, wobbly and half-remembered, like a fond dream.', warmth: 4, age: 3, texture: 2, rolloff: 8800, surfaceDb: -33, drive: 0.3, crackles: 18, pops: 0.3, wowDepth: 0.0035, wowHz: 0.32, flutterDepth: 0.00007, width: 1.15 },
  { id: 'hifi', name: 'Hi-Fi Audiophile', blurb: 'A 180-gram pressing on a polished deck. Crisp and wide.', warmth: 2, age: 1, texture: 1, rolloff: 18500, surfaceDb: -46, drive: 0.1, crackles: 6, pops: 0.05, wowDepth: 0.0002, wowHz: 0.1, flutterDepth: 0.00002, width: 1.2 },
  { id: 'tv', name: 'Old TV Broadcast', blurb: 'A 1950s evening programme through a wooden-cabinet set.', warmth: 2, age: 3, texture: 2, rolloff: 7000, surfaceDb: -44, drive: 0.55, crackles: 14, pops: 0.2, wowDepth: 0.0005, wowHz: 0.2, flutterDepth: 0.00004, width: 0, lowcut: 260, hum: 0.006, honk: 5 },
  { id: 'warradio', name: 'War Field Radio', blurb: 'A crackling wireless message from the front line.', warmth: 1, age: 5, texture: 4, rolloff: 3600, surfaceDb: -40, drive: 0.95, crackles: 40, pops: 1.6, wowDepth: 0.0014, wowHz: 0.13, flutterDepth: 0.0002, width: 0, lowcut: 420, honk: 8 },
]

/** Crackle character — scales the preset's surface noise, crackle density and pops. */
export type Crackle = { id: string; name: string; blurb: string; density: number; amp: number; len: number; pops: number; hissDb: number; hissTone: number }
export const CRACKLES: Crackle[] = [
  { id: 'preset', name: 'As pressed', blurb: "The preset's own surface.", density: 2.5, amp: 1.4, len: 1, pops: 1, hissDb: -8, hissTone: 1 },
  { id: 'crisp', name: 'Crisp Crackle', blurb: 'Clear, popping crackle with almost no hiss.', density: 4, amp: 1.5, len: 0.35, pops: 1.5, hissDb: -16, hissTone: 0.9 },
  { id: 'ticktick', name: 'Tick Tick', blurb: 'Clean surface, lots of crisp little ticks.', density: 9, amp: 1.7, len: 0.18, pops: 0.15, hissDb: -26, hissTone: 0.9 },
  { id: 'cleanticks', name: 'Clean Ticks', blurb: 'Near-silent wax with a steady tick-tick-tick.', density: 14, amp: 1.2, len: 0.14, pops: 0, hissDb: -32, hissTone: 0.9 },
  { id: 'vintage', name: 'Vintage Tick', blurb: 'Bright, distinct ticks — the classic record sound.', density: 2.5, amp: 1.8, len: 0.25, pops: 2.5, hissDb: -14, hissTone: 1 },
  { id: 'static', name: 'Sizzle', blurb: 'Rapid, fizzy little crackles like frying bacon.', density: 18, amp: 0.6, len: 0.3, pops: 1, hissDb: -12, hissTone: 1.1 },
  { id: 'fireside', name: 'Fireside', blurb: 'Dense, tiny ticks — like embers settling.', density: 14, amp: 0.6, len: 0.45, pops: 0.3, hissDb: -12, hissTone: 0.8 },
  { id: 'dust', name: 'Crate Dust', blurb: 'Classic dusty crackle with the odd pop.', density: 5, amp: 1.2, len: 1, pops: 2, hissDb: -8, hissTone: 1 },
  { id: 'rain', name: 'Soft Patter', blurb: 'Gentle, rounded little ticks, steady and soothing.', density: 9, amp: 0.55, len: 1.8, pops: 0.3, hissDb: -16, hissTone: 0.6 },
  { id: 'shellac', name: '78 Shellac', blurb: 'Bright hiss and grit from a 1930s disc.', density: 8, amp: 1.2, len: 0.8, pops: 2.5, hissDb: -2, hissTone: 1.15 },
  { id: 'scratched', name: 'Well-Loved', blurb: 'Big, slow pops from a record played a thousand times.', density: 2, amp: 1.3, len: 1.2, pops: 7, hissDb: -8, hissTone: 0.9 },
  { id: 'silent', name: 'Silent Surface', blurb: 'No crackle at all — pure warmth.', density: 0, amp: 0, len: 1, pops: 0, hissDb: -30, hissTone: 1 },
]

/** One-tap moods: a full combination of character, crackle and background. */
export type Mood = { id: string; name: string; blurb: string; presetId: string; crackleId: string; musicId: string; musicLevel: number }
export const MOODS: Mood[] = [
  { id: 'moonlit', name: 'Moonlit Proposal', blurb: 'Warm wax, soft rain, a moonlight string serenade.', presetId: 'warm', crackleId: 'crisp', musicId: 'serenade', musicLevel: 0.32 },
  { id: 'firstdance', name: 'Our First Dance', blurb: 'A clean pressing over a slow romantic waltz.', presetId: 'clean', crackleId: 'fireside', musicId: 'firstdance', musicLevel: 0.3 },
  { id: 'candle', name: 'Candlelit Dinner', blurb: 'Dusty grooves and a bossa for two.', presetId: 'dusty', crackleId: 'vintage', musicId: 'candlelight', musicLevel: 0.33 },
  { id: 'serenata', name: 'Serenata', blurb: 'A love letter sung over Spanish guitar.', presetId: 'warm', crackleId: 'crisp', musicId: 'guitar', musicLevel: 0.35 },
  { id: 'sunday', name: 'Sunday Morning', blurb: 'Warm wax, fireside ticks, parlour piano.', presetId: 'warm', crackleId: 'fireside', musicId: 'piano', musicLevel: 0.3 },
  { id: 'jazzclub', name: 'Jazz Club, 1958', blurb: 'Dusty grooves and a late-night trio.', presetId: 'dusty', crackleId: 'vintage', musicId: 'jazz', musicLevel: 0.35 },
  { id: 'paris', name: 'Letters from Paris', blurb: 'An accordion waltz for faraway love.', presetId: 'warm', crackleId: 'crisp', musicId: 'waltz', musicLevel: 0.32 },
  { id: 'lullaby', name: 'Lullaby Night', blurb: 'A clean pressing with a music box.', presetId: 'clean', crackleId: 'rain', musicId: 'musicbox', musicLevel: 0.3 },
  { id: 'attic', name: "Grandma's Attic", blurb: 'An old family record with parlour strings.', presetId: 'family', crackleId: 'scratched', musicId: 'pad', musicLevel: 0.3 },
  { id: 'chapel', name: 'In Loving Memory', blurb: 'Archival voice over a soft chapel organ.', presetId: 'archival', crackleId: 'shellac', musicId: 'organ', musicLevel: 0.28 },
  { id: 'crooner', name: 'Crooner Hour', blurb: 'Velvet voice, fireside ticks and a smoky trio.', presetId: 'velvet', crackleId: 'fireside', musicId: 'jazz', musicLevel: 0.3 },
  { id: 'diner', name: 'Milkshake for Two', blurb: 'A jukebox 45 and a dance in the diner.', presetId: 'jukebox', crackleId: 'vintage', musicId: 'waltz', musicLevel: 0.34 },
  { id: 'overseas', name: 'Letter from Overseas', blurb: 'A wartime broadcast home to the one you miss.', presetId: 'radio', crackleId: 'static', musicId: 'none', musicLevel: 0 },
  { id: 'gramo', name: 'Victrola Parlour', blurb: 'Brass horn, shellac grit and parlour strings.', presetId: 'gramophone', crackleId: 'shellac', musicId: 'pad', musicLevel: 0.26 },
  { id: 'dream', name: 'Daydream', blurb: 'A hazy memory over a slow music box.', presetId: 'dreamy', crackleId: 'rain', musicId: 'musicbox', musicLevel: 0.28 },
  { id: 'vows', name: 'Wedding Vows', blurb: 'A pristine pressing over the first-dance theme.', presetId: 'hifi', crackleId: 'silent', musicId: 'firstdance', musicLevel: 0.28 },
  { id: 'starry', name: 'Under the Stars', blurb: 'Soft rain, Spanish guitar and a hazy glow.', presetId: 'dreamy', crackleId: 'rain', musicId: 'guitar', musicLevel: 0.32 },
  { id: 'hearthside', name: 'Fireside Evening', blurb: 'Warm wax, ember ticks and soft round chords.', presetId: 'warm', crackleId: 'fireside', musicId: 'hearth', musicLevel: 0.3 },
  { id: 'sweethearts', name: 'Sweethearts, 1944', blurb: 'A loved old record and a slow clarinet dance.', presetId: 'family', crackleId: 'vintage', musicId: 'sweetheart', musicLevel: 0.32 },
  { id: 'twenties', name: 'Roaring Twenties', blurb: 'A brass-horn gramophone and a foxtrot band.', presetId: 'gramophone', crackleId: 'crisp', musicId: 'foxtrot', musicLevel: 0.3 },
  { id: 'loveletter', name: 'A Love Letter', blurb: 'A tender piano ballad and gentle crackle.', presetId: 'velvet', crackleId: 'crisp', musicId: 'ballad', musicLevel: 0.3 },
  { id: 'tvnight', name: 'Evening Broadcast', blurb: 'Your message, live on a 1950s television set.', presetId: 'tv', crackleId: 'crisp', musicId: 'organ', musicLevel: 0.22 },
  { id: 'warfront', name: 'Message from the Front', blurb: 'A field-radio dispatch, crackling home through the static.', presetId: 'warradio', crackleId: 'static', musicId: 'none', musicLevel: 0 },
]

export type VinylStyle = { id: string; name: string; disc: string; label: string; ink: string; sheen: number; opacity: number }
export const STYLES: VinylStyle[] = [
  { id: 'ruby', name: 'Classic Wax Ruby', disc: '#14100e', label: '#991b1b', ink: '#fef3c7', sheen: 0.55, opacity: 1 },
  { id: 'sapphire', name: 'Midnight Sapphire', disc: '#0d1730', label: '#e8dcc0', ink: '#14213d', sheen: 0.7, opacity: 1 },
  { id: 'gold', name: 'Imperial Gold Master', disc: '#a8781f', label: '#0c0a09', ink: '#f59e0b', sheen: 0.9, opacity: 1 },
  { id: 'emerald', name: 'Vintage Emerald', disc: '#0d3b2a', label: '#fff7e6', ink: '#0d3b2a', sheen: 0.6, opacity: 1 },
  { id: 'obsidian', name: 'Smoked Obsidian', disc: '#2a2522', label: '#d97706', ink: '#0c0a09', sheen: 0.8, opacity: 0.78 },
  { id: 'rose', name: 'Rose Quartz', disc: '#c27a86', label: '#fdf2f4', ink: '#7a2e3d', sheen: 0.75, opacity: 0.85 },
  { id: 'ivory', name: 'Pearl Ivory', disc: '#e9e1cf', label: '#3b2a1e', ink: '#f3d9a4', sheen: 0.85, opacity: 1 },
  { id: 'amethyst', name: 'Royal Amethyst', disc: '#3b1d55', label: '#e9d8a6', ink: '#3b1d55', sheen: 0.7, opacity: 0.9 },
  { id: 'copper', name: 'Burnished Copper', disc: '#7a3e1d', label: '#1c1210', ink: '#e8a26a', sheen: 0.9, opacity: 1 },
  { id: 'marble', name: 'Smoke & Cream', disc: '#5a524a', label: '#f5ecd7', ink: '#3a2f27', sheen: 0.6, opacity: 0.92 },
]

export const OCCASIONS = ['Wedding', 'Anniversary', 'Birthday', 'Love Letter', 'Long Distance', 'Family Memory', 'Grandparents', 'Baby', 'Memorial', 'Something Else']
