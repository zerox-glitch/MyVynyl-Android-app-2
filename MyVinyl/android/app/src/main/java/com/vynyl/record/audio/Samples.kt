package com.vynyl.record.audio

import android.content.Context
import com.vynyl.record.data.RecordMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * One example record per mood, spoken by a fitting person, so people can hear what a mood does before choosing.
 * Voices are pre-generated with open, commercially usable neural voices (Piper: public-domain LibriVox voices and
 * LibriTTS-R, CC BY 4.0) and live in assets/samples. Mirrors web src/lib/samples.ts.
 */
data class Sample(val moodId: String, val persona: String, val from: String, val to: String, val occasion: String, val script: String, val styleId: String)

private val RAW = listOf(
    listOf("moonlit", "Boyfriend", "Sam", "Ella", "Love Letter", "Every night under this moon, I think of you. So here it is, out loud. Will you marry me?"),
    listOf("firstdance", "Husband", "Daniel", "Grace", "Anniversary", "This was playing the night we first danced. I'd ask you again, every single time. I love you."),
    listOf("candle", "Girlfriend", "Mia", "Leo", "Love Letter", "Table for two, candles lit, and you right across from me. That's my favourite place in the whole world."),
    listOf("serenata", "Boyfriend", "Mateo", "Lucia", "Love Letter", "I can't play the guitar, but I can tell you this. You are the best thing that ever happened to me."),
    listOf("sunday", "Mother", "Mum", "Olivia", "Family Memory", "Good morning, sweetheart. The kettle's on, the sun is out, and I am so proud of you. Always."),
    listOf("jazzclub", "Grandfather", "Grandpa Joe", "The grandkids", "Grandparents", "Your grandmother and I danced to this in 1958. She stole my heart that night, and kept it."),
    listOf("paris", "Girlfriend", "Chloé", "Tom", "Long Distance", "Thousands of miles away, and I still hear your laugh every morning. Count the days with me. I'm coming home."),
    listOf("lullaby", "Mother", "Mummy", "Baby Rose", "Baby", "Hush now, little one. Close your eyes. Mummy's right here, and I love you to the moon and back."),
    listOf("attic", "Grandmother", "Nana Ruth", "Theo", "Grandparents", "When you find this, remember our summers in the garden. You were always my favourite little helper."),
    listOf("chapel", "Son", "James", "Dad", "Memorial", "Dad, I still hear your voice in every quiet room. Thank you for everything. We miss you every day."),
    listOf("crooner", "Husband", "Frank", "Margaret", "Anniversary", "Forty years, darling, and you still make my heart skip. Put your feet up. This one's for you."),
    listOf("diner", "Girlfriend", "Ruby", "Jack", "Anniversary", "Two straws, one milkshake, and you stealing all my fries. Happy anniversary. I love you."),
    listOf("overseas", "Soldier", "Michael", "Sarah & the kids", "Long Distance", "Hello love, it's me. It's cold out here, but your letters keep me warm. Kiss the kids for me."),
    listOf("gramo", "Grandfather", "Grandpa Arthur", "The family", "Family Memory", "Gather round, children. Let me tell you how your great grandmother and I first met. It was a rainy Tuesday."),
    listOf("dream", "Girlfriend", "Zoe", "Ben", "Love Letter", "I dreamed about you again last night. We were laughing, and nothing else mattered. Sweet dreams, my love."),
    listOf("vows", "Groom", "Noah", "Emma", "Wedding", "I promise to hold your hand through every storm, and to dance with you in every kitchen. Forever."),
    listOf("starry", "Boyfriend", "Ethan", "Ava", "Long Distance", "Look up tonight. Whichever star you pick, that's me, thinking about you. Goodnight, beautiful."),
    listOf("hearthside", "Father", "Dad", "Lily", "Family Memory", "Hey kiddo. Pull up a chair by the fire. Whatever happens out there, home is always right here."),
    listOf("sweethearts", "Grandmother", "Grandma Edith", "Little Annie", "Grandparents", "He wrote to me every week of the war. Now I'm writing to you, my darling. Love wins, every time."),
    listOf("twenties", "Grandfather", "Grandpa George", "Henry", "Birthday", "Happy birthday, my boy! Ninety years young, and I could still out-dance anyone at this party."),
    listOf("loveletter", "Wife", "Hannah", "Chris", "Love Letter", "I don't say it enough, so I'm saying it here, forever. Thank you for loving me the way you do."),
    listOf("tvnight", "Father", "Dad", "The family", "Family Memory", "Good evening, family! Tonight's special broadcast. Dad says he loves you, and dinner is at seven."),
    listOf("warfront", "Soldier", "Private Evans", "Mum", "Long Distance", "This is Private Evans, calling home. Tell Mum I'm safe, and I'll be back before the snow falls."),
)

val SAMPLES: List<Sample> = RAW.mapIndexed { i, r -> Sample(r[0], r[1], r[2], r[3], r[4], r[5], STYLES[i % STYLES.size].id) }

fun sampleFor(moodId: String): Sample? = SAMPLES.firstOrNull { it.moodId == moodId }

/** Decodes and presses the sample records on demand. Rendered masters live in the cache dir; nothing touches the shelf. */
object SampleStore {
    private val lock = Mutex()
    private val records = HashMap<String, RecordMeta>()

    fun masterFile(ctx: Context, moodId: String) = File(File(ctx.cacheDir, "samples").apply { mkdirs() }, "$moodId.wav")

    /** The sample voice pressed through its mood — a full record the Player can spin. Call from a coroutine. */
    suspend fun record(ctx: Context, moodId: String): RecordMeta = lock.withLock {
        records[moodId]?.let { if (masterFile(ctx, moodId).exists()) return@withLock it }
        val s = sampleFor(moodId) ?: error("Unknown sample $moodId")
        val mood = MOODS.first { it.id == moodId }
        val voice = decodeAssetToMono(ctx, "samples/$moodId.m4a")
        val bed = withContext(Dispatchers.Default) { musicBed(mood.musicId) }
        val st = renderMaster(
            voice,
            RenderOpts(
                preset = preset(mood.presetId), crackle = crackle(mood.crackleId), seed = "sample:$moodId",
                intro = 1.2f, tail = 1f, music = bed, musicLevel = mood.musicLevel,
            ),
        )
        val wave = withContext(Dispatchers.Default) {
            masterFile(ctx, moodId).writeBytes(encodeWav(st))
            waveform(st.l)
        }
        RecordMeta(
            id = "sample-$moodId", title = mood.name, recipient = s.to, sender = s.from, dedication = s.script,
            occasion = s.occasion, date = "${s.persona}'s message", sideA = "Side A", sideB = "Side B",
            presetId = mood.presetId, styleId = s.styleId, duration = st.l.size / SR.toFloat(), wave = wave,
            createdAt = 0L, crackleId = mood.crackleId, musicId = mood.musicId, sample = true, sampleMood = moodId,
        ).also { records[moodId] = it }
    }
}
