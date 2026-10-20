package com.vynyl.record.data

import com.vynyl.record.pro.Free
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

/** Saved recorded voice in Master Vault, available for reuse across records. */
@Serializable
data class VaultVoice(
    val id: String,
    val name: String,
    val duration: Float,
    val createdAt: Long,
    val recordTitle: String? = null,
)

object RecordStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var dir: File
    private val _records = MutableStateFlow<List<RecordMeta>>(emptyList())
    val records: StateFlow<List<RecordMeta>> = _records
    private val _vaultVoices = MutableStateFlow<List<VaultVoice>>(emptyList())
    val vaultVoices: StateFlow<List<VaultVoice>> = _vaultVoices

    fun init(ctx: Context) {
        dir = File(ctx.filesDir, "records").apply { mkdirs() }
        _records.value = runCatching { json.decodeFromString<List<RecordMeta>>(index.readText()) }.getOrDefault(emptyList())
        _vaultVoices.value = runCatching { json.decodeFromString<List<VaultVoice>>(vaultVoicesIndex.readText()) }.getOrDefault(emptyList())
    }

    private val index get() = File(dir, "records.json")
    private val vaultVoicesIndex get() = File(dir, "vault_voices.json")

    fun masterFile(id: String) = File(dir, "$id.wav")
    fun photoFile(id: String) = File(dir, "$id.label.jpg")
    fun photoOriginalFile(id: String) = File(dir, "$id.orig.jpg")
    fun voiceFile(id: String) = File(dir, "$id.voice.f32")
    fun vaultVoiceFile(id: String) = File(dir, "voice_$id.f32")

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

    private fun persistVaultVoices(list: List<VaultVoice>) {
        _vaultVoices.value = list
        val tmp = File(dir, "vault_voices.json.tmp")
        tmp.writeText(json.encodeToString(list)); tmp.renameTo(vaultVoicesIndex)
    }

    suspend fun saveVaultVoice(
        id: String,
        name: String,
        voice: FloatArray,
        duration: Float,
        recordTitle: String? = null,
        isPro: Boolean = false,
    ): VaultVoice? = withContext(Dispatchers.IO) {
        val existing = _vaultVoices.value.firstOrNull { it.id == id }
        if (existing == null && !isPro && _vaultVoices.value.size >= Free.MAX_VOICES) {
            return@withContext null
        }
        val b = ByteBuffer.allocate(voice.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        b.asFloatBuffer().put(voice)
        vaultVoiceFile(id).writeBytes(b.array())
        val updated = existing?.copy(name = name, recordTitle = recordTitle ?: existing.recordTitle)
            ?: VaultVoice(id = id, name = name, duration = duration, createdAt = System.currentTimeMillis(), recordTitle = recordTitle)
        val next = _vaultVoices.value.filterNot { it.id == id } + updated
        persistVaultVoices(next)
        updated
    }

    suspend fun updateVaultVoiceTitle(id: String, newName: String, recordTitle: String? = null) = withContext(Dispatchers.IO) {
        persistVaultVoices(_vaultVoices.value.map {
            if (it.id == id) it.copy(name = newName, recordTitle = recordTitle ?: it.recordTitle) else it
        })
    }

    suspend fun loadVaultVoice(id: String): FloatArray? = withContext(Dispatchers.IO) {
        val f = vaultVoiceFile(id)
        val fileToRead = if (f.exists()) f else voiceFile(id)
        if (!fileToRead.exists()) return@withContext null
        val fb = ByteBuffer.wrap(fileToRead.readBytes()).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        FloatArray(fb.remaining()).also { fb.get(it) }
    }

    suspend fun deleteVaultVoice(id: String) = withContext(Dispatchers.IO) {
        vaultVoiceFile(id).delete()
        persistVaultVoices(_vaultVoices.value.filterNot { it.id == id })
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
