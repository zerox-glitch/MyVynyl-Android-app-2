package com.vynyl.record.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Mirrors src/lib/db.ts. Metadata lives in records.json; audio and photos are files beside it. */
@Serializable
data class PhotoAdjust(val mode: String = "fill", val zoom: Float = 1f, val x: Float = 0f, val y: Float = 0f, val rot: Float = 0f, val bg: String = "blur")

/** Everything the Studio needs to re-press a record from its original voice. */
@Serializable
data class StudioSettings(
    val presetId: String, val styleId: String, val crackleId: String, val music: String,
    val musicLevel: Float, val crackleLevel: Float, val character: Float, val volume: Float, val moodId: String? = null,
)

@Serializable
data class RecordMeta(
    val id: String, val title: String, val recipient: String, val sender: String, val dedication: String,
    val occasion: String, val date: String, val sideA: String, val sideB: String,
    val presetId: String, val styleId: String, val duration: Float, val wave: List<Float>,
    val createdAt: Long, val lastPlayedAt: Long? = null, val favorite: Boolean = false,
    val labelPhotoAdjust: PhotoAdjust? = null, val crackleId: String? = null, val musicId: String? = null,
    val hasPhoto: Boolean = false,
    /** original voice saved beside the master, so the record can go back to the Studio */
    val hasVoice: Boolean = false, val settings: StudioSettings? = null,
    /** trips back to the Studio so far (free users get Free.REEDITS) */
    val reedits: Int = 0,
)

object RecordStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var dir: File
    private val _records = MutableStateFlow<List<RecordMeta>>(emptyList())
    val records: StateFlow<List<RecordMeta>> = _records

    fun init(ctx: Context) {
        dir = File(ctx.filesDir, "records").apply { mkdirs() }
        _records.value = runCatching { json.decodeFromString<List<RecordMeta>>(index.readText()) }.getOrDefault(emptyList())
    }

    private val index get() = File(dir, "records.json")
    fun masterFile(id: String) = File(dir, "$id.wav")
    fun photoFile(id: String) = File(dir, "$id.label.jpg")
    fun photoOriginalFile(id: String) = File(dir, "$id.orig.jpg")
    fun voiceFile(id: String) = File(dir, "$id.voice.f32")

    suspend fun saveVoice(id: String, voice: FloatArray) = withContext(Dispatchers.IO) {
        val b = ByteBuffer.allocate(voice.size * 4).order(ByteOrder.LITTLE_ENDIAN); b.asFloatBuffer().put(voice)
        voiceFile(id).writeBytes(b.array())
    }

    suspend fun loadVoice(id: String): FloatArray? = withContext(Dispatchers.IO) {
        val f = voiceFile(id); if (!f.exists()) return@withContext null
        val fb = ByteBuffer.wrap(f.readBytes()).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        FloatArray(fb.remaining()).also { fb.get(it) }
    }

    private fun persist(list: List<RecordMeta>) {
        _records.value = list
        val tmp = File(dir, "records.json.tmp")
        tmp.writeText(json.encodeToString(list)); tmp.renameTo(index)
    }

    fun get(id: String) = _records.value.firstOrNull { it.id == id }

    suspend fun put(meta: RecordMeta, master: ByteArray? = null) = withContext(Dispatchers.IO) {
        master?.let { masterFile(meta.id).writeBytes(it) }
        persist(_records.value.filterNot { it.id == meta.id } + meta)
    }

    suspend fun update(id: String, fn: (RecordMeta) -> RecordMeta) = withContext(Dispatchers.IO) {
        persist(_records.value.map { if (it.id == id) fn(it) else it })
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        listOf(masterFile(id), photoFile(id), photoOriginalFile(id), voiceFile(id)).forEach { it.delete() }
        persist(_records.value.filterNot { it.id == id })
    }
}
