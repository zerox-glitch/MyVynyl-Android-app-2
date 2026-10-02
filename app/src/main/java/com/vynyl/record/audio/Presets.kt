package com.vynyl.record.audio

/** Ported 1:1 from src/lib/presets.ts. */
data class Preset(
    val id: String, val name: String, val blurb: String,
    val warmth: Int, val age: Int, val texture: Int,
    val rolloff: Float, val surfaceDb: Float, val drive: Float, val crackles: Float, val pops: Float,
    val wowDepth: Float, val wowHz: Float, val flutterDepth: Float, val width: Float,
    /** optional broadcast colouring: low cut (Hz), mains hum level, mid honk (dB) */
    val lowcut: Float? = null, val hum: Float? = null, val honk: Float? = null,
)

/** Crackle character — scales the preset's surface noise, crackle density and pops. */
data class Crackle(val id: String, val name: String, val blurb: String, val density: Float, val amp: Float, val len: Float, val pops: Float, val hissDb: Float, val hissTone: Float)

/** One-tap moods: a full combination of character, crackle and background. */
data class Mood(val id: String, val name: String, val blurb: String, val presetId: String, val crackleId: String, val musicId: String, val musicLevel: Float)

data class VinylStyle(val id: String, val name: String, val disc: Long, val label: Long, val ink: Long, val sheen: Float, val opacity: Float)

private fun c(hex: String) = 0xFF000000L or hex.removePrefix("#").toLong(16)

val PRESETS = listOf(
    Preset("clean", "Clean Vinyl", "A fresh pressing. Your voice, barely touched by time.", 1, 1, 1, 16500f, -42f, 0.15f, 10f, 0.1f, 0.0004f, 0.12f, 0.00004f, 1f),
    Preset("warm", "Warm Vintage", "Tape warmth and a soft hush, like a Sunday morning.", 3, 2, 2, 14200f, -34f, 0.35f, 22f, 0.4f, 0.0007f, 0.15f, 0.00006f, 0.9f),
    Preset("dusty", "Dusty Record", "Found in a crate, loved by many hands before yours.", 3, 3, 4, 12600f, -28f, 0.45f, 38f, 1f, 0.0009f, 0.18f, 0.00008f, 0.8f),
    Preset("family", "Old Family Record", "The one that lived by the radiogram for forty years.", 4, 4, 4, 11200f, -26f, 0.6f, 42f, 1.4f, 0.0016f, 0.09f, 0.0001f, 0.6f),
    Preset("archival", "Rare Archival", "A voice recovered from another century — still clear.", 5, 5, 5, 9800f, -24f, 0.7f, 48f, 2f, 0.0018f, 0.07f, 0.00012f, 0.4f),
    Preset("velvet", "Velvet Lounge", "Rich and close, like a crooner leaning into the mic.", 4, 2, 2, 13000f, -36f, 0.5f, 16f, 0.3f, 0.0006f, 0.14f, 0.00005f, 1f),
    Preset("jukebox", "Diner Jukebox", "A punchy 45 spinning in a chrome-and-neon diner.", 3, 3, 3, 12000f, -31f, 0.75f, 30f, 0.8f, 0.0008f, 0.2f, 0.00009f, 0.7f),
    Preset("radio", "Wartime Radio", "A crackling broadcast from far across the sea.", 2, 4, 4, 6200f, -25f, 0.85f, 34f, 1.2f, 0.0012f, 0.11f, 0.00015f, 0.15f),
    Preset("gramophone", "Brass Gramophone", "Wound by hand and sung through a great brass horn.", 5, 5, 5, 5200f, -22f, 0.9f, 55f, 2.4f, 0.0024f, 0.08f, 0.00018f, 0f),
    Preset("dreamy", "Dreamy Haze", "Soft, wobbly and half-remembered, like a fond dream.", 4, 3, 2, 8800f, -33f, 0.3f, 18f, 0.3f, 0.0035f, 0.32f, 0.00007f, 1.15f),
    Preset("hifi", "Hi-Fi Audiophile", "A 180-gram pressing on a polished deck. Crisp and wide.", 2, 1, 1, 18500f, -46f, 0.1f, 6f, 0.05f, 0.0002f, 0.1f, 0.00002f, 1.2f),
    Preset("tv", "Old TV Broadcast", "A 1950s evening programme through a wooden-cabinet set.", 2, 3, 2, 7000f, -44f, 0.55f, 14f, 0.2f, 0.0005f, 0.2f, 0.00004f, 0f, lowcut = 260f, hum = 0.006f, honk = 5f),
    Preset("warradio", "War Field Radio", "A crackling wireless message from the front line.", 1, 5, 4, 3600f, -40f, 0.95f, 40f, 1.6f, 0.0014f, 0.13f, 0.0002f, 0f, lowcut = 420f, honk = 8f),
)

val CRACKLES = listOf(
    Crackle("preset", "As pressed", "The preset's own surface.", 2.5f, 1.4f, 1f, 1f, -8f, 1f),
    Crackle("crisp", "Crisp Crackle", "Clear, popping crackle with almost no hiss.", 4f, 1.5f, 0.35f, 1.5f, -16f, 0.9f),
    Crackle("ticktick", "Tick Tick", "Clean surface, lots of crisp little ticks.", 9f, 1.7f, 0.18f, 0.15f, -26f, 0.9f),
    Crackle("cleanticks", "Clean Ticks", "Near-silent wax with a steady tick-tick-tick.", 14f, 1.2f, 0.14f, 0f, -32f, 0.9f),
    Crackle("vintage", "Vintage Tick", "Bright, distinct ticks — the classic record sound.", 2.5f, 1.8f, 0.25f, 2.5f, -14f, 1f),
    Crackle("static", "Sizzle", "Rapid, fizzy little crackles like frying bacon.", 18f, 0.6f, 0.3f, 1f, -12f, 1.1f),
    Crackle("fireside", "Fireside", "Dense, tiny ticks — like embers settling.", 14f, 0.6f, 0.45f, 0.3f, -12f, 0.8f),
    Crackle("dust", "Crate Dust", "Classic dusty crackle with the odd pop.", 5f, 1.2f, 1f, 2f, -8f, 1f),
    Crackle("rain", "Soft Patter", "Gentle, rounded little ticks, steady and soothing.", 9f, 0.55f, 1.8f, 0.3f, -16f, 0.6f),
    Crackle("shellac", "78 Shellac", "Bright hiss and grit from a 1930s disc.", 8f, 1.2f, 0.8f, 2.5f, -2f, 1.15f),
    Crackle("scratched", "Well-Loved", "Big, slow pops from a record played a thousand times.", 2f, 1.3f, 1.2f, 7f, -8f, 0.9f),
    Crackle("silent", "Silent Surface", "No crackle at all — pure warmth.", 0f, 0f, 1f, 0f, -30f, 1f),
)

val MOODS = listOf(
    Mood("moonlit", "Moonlit Proposal", "Warm wax, soft rain, a moonlight string serenade.", "warm", "crisp", "serenade", 0.32f),
    Mood("firstdance", "Our First Dance", "A clean pressing over a slow romantic waltz.", "clean", "fireside", "firstdance", 0.3f),
    Mood("candle", "Candlelit Dinner", "Dusty grooves and a bossa for two.", "dusty", "vintage", "candlelight", 0.33f),
    Mood("serenata", "Serenata", "A love letter sung over Spanish guitar.", "warm", "crisp", "guitar", 0.35f),
    Mood("sunday", "Sunday Morning", "Warm wax, fireside ticks, parlour piano.", "warm", "fireside", "piano", 0.3f),
    Mood("jazzclub", "Jazz Club, 1958", "Dusty grooves and a late-night trio.", "dusty", "vintage", "jazz", 0.35f),
    Mood("paris", "Letters from Paris", "An accordion waltz for faraway love.", "warm", "crisp", "waltz", 0.32f),
    Mood("lullaby", "Lullaby Night", "A clean pressing with a music box.", "clean", "rain", "musicbox", 0.3f),
    Mood("attic", "Grandma's Attic", "An old family record with parlour strings.", "family", "scratched", "pad", 0.3f),
    Mood("chapel", "In Loving Memory", "Archival voice over a soft chapel organ.", "archival", "shellac", "organ", 0.28f),
    Mood("crooner", "Crooner Hour", "Velvet voice, fireside ticks and a smoky trio.", "velvet", "fireside", "jazz", 0.3f),
    Mood("diner", "Milkshake for Two", "A jukebox 45 and a dance in the diner.", "jukebox", "vintage", "waltz", 0.34f),
    Mood("overseas", "Letter from Overseas", "A wartime broadcast home to the one you miss.", "radio", "static", "none", 0f),
    Mood("gramo", "Victrola Parlour", "Brass horn, shellac grit and parlour strings.", "gramophone", "shellac", "pad", 0.26f),
    Mood("dream", "Daydream", "A hazy memory over a slow music box.", "dreamy", "rain", "musicbox", 0.28f),
    Mood("vows", "Wedding Vows", "A pristine pressing over the first-dance theme.", "hifi", "silent", "firstdance", 0.28f),
    Mood("starry", "Under the Stars", "Soft rain, Spanish guitar and a hazy glow.", "dreamy", "rain", "guitar", 0.32f),
    Mood("hearthside", "Fireside Evening", "Warm wax, ember ticks and soft round chords.", "warm", "fireside", "hearth", 0.3f),
    Mood("sweethearts", "Sweethearts, 1944", "A loved old record and a slow clarinet dance.", "family", "vintage", "sweetheart", 0.32f),
    Mood("twenties", "Roaring Twenties", "A brass-horn gramophone and a foxtrot band.", "gramophone", "crisp", "foxtrot", 0.3f),
    Mood("loveletter", "A Love Letter", "A tender piano ballad and gentle crackle.", "velvet", "crisp", "ballad", 0.3f),
    Mood("tvnight", "Evening Broadcast", "Your message, live on a 1950s television set.", "tv", "crisp", "organ", 0.22f),
    Mood("warfront", "Message from the Front", "A field-radio dispatch, crackling home through the static.", "warradio", "static", "none", 0f),
)

val STYLES = listOf(
    VinylStyle("ruby", "Classic Wax Ruby", c("#14100e"), c("#991b1b"), c("#fef3c7"), 0.55f, 1f),
    VinylStyle("sapphire", "Midnight Sapphire", c("#0d1730"), c("#e8dcc0"), c("#14213d"), 0.7f, 1f),
    VinylStyle("gold", "Imperial Gold Master", c("#a8781f"), c("#0c0a09"), c("#f59e0b"), 0.9f, 1f),
    VinylStyle("emerald", "Vintage Emerald", c("#0d3b2a"), c("#fff7e6"), c("#0d3b2a"), 0.6f, 1f),
    VinylStyle("obsidian", "Smoked Obsidian", c("#2a2522"), c("#d97706"), c("#0c0a09"), 0.8f, 0.78f),
    VinylStyle("rose", "Rose Quartz", c("#c27a86"), c("#fdf2f4"), c("#7a2e3d"), 0.75f, 0.85f),
    VinylStyle("ivory", "Pearl Ivory", c("#e9e1cf"), c("#3b2a1e"), c("#f3d9a4"), 0.85f, 1f),
    VinylStyle("amethyst", "Royal Amethyst", c("#3b1d55"), c("#e9d8a6"), c("#3b1d55"), 0.7f, 0.9f),
    VinylStyle("copper", "Burnished Copper", c("#7a3e1d"), c("#1c1210"), c("#e8a26a"), 0.9f, 1f),
    VinylStyle("marble", "Smoke & Cream", c("#5a524a"), c("#f5ecd7"), c("#3a2f27"), 0.6f, 0.92f),
)

val OCCASIONS = listOf("Wedding", "Anniversary", "Birthday", "Love Letter", "Long Distance", "Family Memory", "Grandparents", "Baby", "Memorial", "Something Else")

fun preset(id: String) = PRESETS.firstOrNull { it.id == id } ?: PRESETS[1]
fun crackle(id: String) = CRACKLES.firstOrNull { it.id == id } ?: CRACKLES[0]
fun style(id: String) = STYLES.firstOrNull { it.id == id } ?: STYLES[0]

