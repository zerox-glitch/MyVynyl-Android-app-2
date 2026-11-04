package com.vynyl.record.ui.studio

import com.vynyl.record.data.StudioSettings
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.vynyl.record.audio.CUSTOM_MUSIC
import com.vynyl.record.audio.Mood
import com.vynyl.record.audio.Sample
import com.vynyl.record.audio.SampleStore
import com.vynyl.record.audio.WavPlayer
import com.vynyl.record.audio.decodeToMono
import com.vynyl.record.audio.makeCustomBed
import com.vynyl.record.audio.musicName
import com.vynyl.record.audio.sampleFor
import com.vynyl.record.audio.setCustomMusic
import kotlinx.coroutines.CancellationException
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vynyl.record.audio.CRACKLES
import com.vynyl.record.audio.Crackle
import com.vynyl.record.audio.Levels
import com.vynyl.record.audio.MOODS
import com.vynyl.record.audio.MUSIC
import com.vynyl.record.audio.PRESETS
import com.vynyl.record.audio.PreviewEngine
import com.vynyl.record.audio.RenderOpts
import com.vynyl.record.audio.SR
import com.vynyl.record.audio.STYLES
import com.vynyl.record.audio.encodeWav
import com.vynyl.record.audio.musicBed
import com.vynyl.record.audio.renderMaster
import com.vynyl.record.audio.waveform
import com.vynyl.record.data.RecordMeta
import com.vynyl.record.data.RecordStore
import com.vynyl.record.pro.Free
import com.vynyl.record.pro.Gate
import com.vynyl.record.pro.PRO_SECONDS
import com.vynyl.record.pro.Pro
import com.vynyl.record.pro.isFree
import com.vynyl.record.ui.components.Btn
import com.vynyl.record.ui.components.BtnVariant
import com.vynyl.record.ui.components.Eyebrow
import com.vynyl.record.ui.components.Meter
import com.vynyl.record.ui.paywall.ProBadge
import com.vynyl.record.ui.paywall.ProButton
import com.vynyl.record.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.roundToInt

private val STEPS = listOf("Capture", "Dedication", "Character", "Appearance", "Press")
/** One plain sentence per step, shown under the progress bar (same wording as the web). */
private val HINTS = listOf(
    "Record a voice or choose one, then tap Next.",
    "Say who it is for and write a short message. You can keep our example words.",
    "Pick a mood. Tap Listen to hear it for a few seconds, then tap Next.",
    "Choose the colour of your record, then tap Next.",
    "Tap Press record. It only takes a few seconds.",
)

private fun fileSize(ctx: Context, uri: Uri): Long = runCatching {
    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else -1L } ?: -1L
}.getOrDefault(-1L)
private val ROMAN = listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII", "XIII", "XIV", "XV")

private data class RenderState(val p: Float, val stage: String)

@Composable
/** [edit]: an existing record opened from the player — the Studio reloads its voice + settings and re-presses it in place. */
/** [capture]: bumped by the Record tab to jump to the Capture step. [active]: false while another tab covers the Studio. */
fun Studio(
    recordCount: Int, onDone: (RecordMeta) -> Unit, onPlay: (RecordMeta) -> Unit, edit: RecordMeta? = null, onCancelEdit: () -> Unit = {},
    capture: Int = 0, active: Boolean = true, onStep: (Int) -> Unit = {},
    initialVoice: com.vynyl.record.data.VaultVoice? = null, onClearInitialVoice: () -> Unit = {},
    /** "Use this mood" from a sample record: (mood id, request counter) */
    moodRequest: Pair<String, Int>? = null,
) {
    val ctx = LocalContext.current
    val ent by Pro.entitlement.collectAsState()
    val pro = ent.pro
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    fun locked(kind: Gate, id: String) = !pro && !isFree(kind, id)
    fun gate(kind: Gate, id: String, name: String, fn: () -> Unit) {
        if (locked(kind, id)) Pro.openPaywall("$name is part of Vynyl Pro. Unlock it — and every other sound — below.") else fn()
    }

    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(step) { onStep(step) }
    var source by remember { mutableStateOf<FloatArray?>(null) }
    var isDemo by remember { mutableStateOf(false) }
    var srcName by remember { mutableStateOf<String?>(null) }
    var currentVoiceId by remember { mutableStateOf<String?>(null) }
    var meta by remember { mutableStateOf(StudioMeta()) }
    var presetId by remember { mutableStateOf("warm") }
    var styleId by remember { mutableStateOf("ruby") }
    var crackleId by remember { mutableStateOf("preset") }
    var music by remember { mutableStateOf("hearth") }
    var musicLevel by remember { mutableFloatStateOf(0.35f) }
    var crackleLevel by remember { mutableFloatStateOf(1f) }
    var character by remember { mutableFloatStateOf(1f) }
    var volume by remember { mutableFloatStateOf(1f) }
    var moodId by remember { mutableStateOf<String?>(null) }
    var withVoice by remember { mutableStateOf(true) }
    /** the "Customise the sound yourself" panel */
    var custom by remember { mutableStateOf(false) }
    /** a mood picked before recording (from a sample record) */
    var moodReady by remember { mutableStateOf(false) }
    /** your own music: the whole decoded upload (for trimming) and the trimmed, loop-ready bed — (samples, name) */
    var customSource by remember { mutableStateOf<Pair<FloatArray, String>?>(null) }
    var customBed by remember { mutableStateOf<Pair<FloatArray, String>?>(null) }
    var cropping by remember { mutableStateOf(false) }
    var customError by remember { mutableStateOf("") }
    val hasVoice = source != null && !isDemo
    var render by remember { mutableStateOf<RenderState?>(null) }
    /** the record this session last pressed — the Studio stays on it so you can go back and change things */
    var pressed by remember { mutableStateOf<RecordMeta?>(null) }
    val live by RecordStore.records.collectAsState()
    // always the latest copy, so a photo or favourite added in the player isn't lost on re-press
    val base = pressed?.let { p -> live.firstOrNull { it.id == p.id } ?: p } ?: edit
    val editsLeft = if (pro) Int.MAX_VALUE else maxOf(0, Free.REEDITS - (base?.reedits ?: 0))
    LaunchedEffect(capture) { if (capture > 0) { pressed = null; step = 0 } }
    LaunchedEffect(edit?.id) { if (edit != null) pressed = null }
    fun reset() { pressed = null; step = 0; source = null; srcName = null; currentVoiceId = null; isDemo = false; onCancelEdit() }

    LaunchedEffect(initialVoice?.id) {
        val v = initialVoice ?: return@LaunchedEffect
        val samples = RecordStore.loadVaultVoice(v.id) ?: return@LaunchedEffect
        source = samples
        srcName = v.name
        isDemo = false
        currentVoiceId = v.id
        pressed = null
        step = 0
        onClearInitialVoice()
    }
    fun makeChanges() {
        val p = (if (pressed != null) base else null) ?: run { step = 2; return }
        if (pro) { step = 2; return }
        if (editsLeft <= 0) { Pro.openPaywall("Free records can be changed ${Free.REEDITS} times after pressing. Go Pro to change them as often as you like."); return }
        val next = p.copy(reedits = p.reedits + 1)
        pressed = next; step = 2
        scope.launch { RecordStore.update(p.id) { it.copy(reedits = next.reedits) } }
    }
    LaunchedEffect(edit?.id) {
        val e = edit ?: return@LaunchedEffect
        val v = RecordStore.loadVoice(e.id) ?: return@LaunchedEffect
        source = v; isDemo = false; srcName = null; currentVoiceId = e.id
        meta = StudioMeta(title = e.title, recipient = e.recipient, sender = e.sender, dedication = e.dedication, occasion = e.occasion, date = e.date, sideA = e.sideA, sideB = e.sideB)
        e.settings?.let { st ->
            presetId = st.presetId; styleId = st.styleId; crackleId = st.crackleId; music = st.music
            musicLevel = st.musicLevel; crackleLevel = st.crackleLevel; character = st.character; volume = st.volume; moodId = st.moodId
        }
        RecordStore.loadCustomMusic(e.id)?.let { cm ->
            setCustomMusic(cm); customBed = cm to (e.customMusicName ?: "Your music"); customSource = null
        }
        step = 2
    }

    // ---- live preview (usePreview) ----
    val engine = remember { PreviewEngine() }
    DisposableEffect(Unit) { onDispose { engine.stop(); engine.release() } }
    val enginePlaying by engine.playing.collectAsState()
    var activeKey by remember { mutableStateOf<String?>(null) }
    var activeLoading by remember { mutableStateOf(false) }
    var previewJob by remember { mutableStateOf<Job?>(null) }
    fun levels() = Levels(music = musicLevel, crackle = crackleLevel, character = character, mood = volume)
    fun stopPreview() { previewJob?.cancel(); previewJob = null; engine.stop(); activeKey = null; activeLoading = false }

    // ---- example records (someone else's message pressed with a mood) ----
    var exampleId by remember { mutableStateOf<String?>(null) }
    var exampleLoading by remember { mutableStateOf(false) }
    var exampleError by remember { mutableStateOf("") }
    var examplePlayer by remember { mutableStateOf<WavPlayer?>(null) }
    var exampleJob by remember { mutableStateOf<Job?>(null) }
    fun stopExample() {
        exampleJob?.cancel(); exampleJob = null
        examplePlayer?.release(); examplePlayer = null
        exampleId = null; exampleLoading = false
    }
    DisposableEffect(Unit) { onDispose { examplePlayer?.release() } }
    LaunchedEffect(examplePlayer) {
        val p = examplePlayer ?: return@LaunchedEffect
        p.isPlaying.collect { pl -> if (!pl && p.position.value >= p.duration - 0.1f && examplePlayer === p) stopExample() }
    }
    fun exampleState(id: String): ListenState = when {
        exampleId != id -> ListenState.Idle
        exampleLoading -> ListenState.Loading
        else -> ListenState.Playing
    }
    fun toggleExample(id: String) {
        if (exampleId == id) return stopExample()
        stopExample(); stopPreview(); exampleError = ""
        exampleId = id; exampleLoading = true
        exampleJob = scope.launch {
            try {
                SampleStore.record(ctx, id)
                val p = WavPlayer(SampleStore.masterFile(ctx, id))
                examplePlayer = p; exampleLoading = false
                p.play()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                stopExample(); exampleError = "Could not play this example. Please try again."
            }
        }
    }
    fun previewState(key: String): ListenState = when {
        activeKey != key -> ListenState.Idle
        activeLoading && !enginePlaying -> ListenState.Loading
        else -> ListenState.Playing
    }
    LaunchedEffect(active) { if (!active) { stopPreview(); stopExample() } }
    LaunchedEffect(enginePlaying) {
        if (enginePlaying) activeLoading = false
        else if (activeKey != null && !activeLoading) activeKey = null
    }
    LaunchedEffect(step) { if (step != 2) { stopPreview(); stopExample() } else moodReady = false }
    LaunchedEffect(musicLevel, crackleLevel, character, volume) { engine.apply(levels()) }
    fun listen(key: String, p: String = presetId, c: String = crackleId, m: String = music, ml: Float = musicLevel) {
        if (activeKey == key) return stopPreview()
        stopPreview(); stopExample()
        activeKey = key; activeLoading = true
        val voice: FloatArray? = if (withVoice && hasVoice) source else null
        val lv = Levels(music = ml, crackle = crackleLevel, character = character, mood = volume)
        previewJob = scope.launch {
            try {
                engine.play(voice, com.vynyl.record.audio.preset(p), com.vynyl.record.audio.crackle(c), m, lv, key = key)
            } finally {
                if (activeKey == key && activeLoading && !engine.playing.value) { activeKey = null; activeLoading = false }
                else if (activeKey == key) activeLoading = false
            }
        }
    }
    fun applyMood(id: String) {
        val m = MOODS.first { it.id == id }
        moodId = id; presetId = m.presetId; crackleId = m.crackleId; music = m.musicId; musicLevel = m.musicLevel
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    LaunchedEffect(moodRequest?.second) {
        val r = moodRequest ?: return@LaunchedEffect
        applyMood(r.first); pressed = null
        if (source != null) step = 2 else { step = 0; moodReady = true }
    }
    val musicPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            customError = ""
            if (fileSize(ctx, uri) > 60L * 1024 * 1024) { customError = "Choose a music file smaller than 60 MB."; return@launch }
            val data = runCatching { decodeToMono(ctx, uri) }.getOrNull()
            if (data == null || data.isEmpty()) { customError = "Could not read this music file. Try an MP3, M4A or WAV."; return@launch }
            customSource = data to (displayName(ctx, uri)?.substringBeforeLast('.') ?: "Your music")
            cropping = true
        }
    }
    fun saveCrop(start: Float, end: Float) {
        val src = customSource ?: return
        stopPreview()
        val bed = makeCustomBed(src.first, start, end)
        setCustomMusic(bed); customBed = bed to src.second
        music = CUSTOM_MUSIC; moodId = null; cropping = false
    }
    val style = com.vynyl.record.audio.style(styleId)
    val mood = MOODS.firstOrNull { it.id == moodId }
    val soundSummary = "${com.vynyl.record.audio.preset(presetId).name} · ${com.vynyl.record.audio.crackle(crackleId).name} · ${musicName(music, customBed?.second)}"

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        // header
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Eyebrow("STEP ${step + 1} OF ${STEPS.size} · ${STEPS[step].uppercase()}")
                ProButton()
            }
            if (base != null && !(pressed != null && step == 4)) base.let { e ->
                Row(
                    Modifier.padding(top = 12.dp).fillMaxWidth().clip(CircleShape).border(1.dp, V.amber.copy(alpha = 0.4f), CircleShape).background(V.amber.copy(alpha = 0.1f))
                        .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Changing “${e.title}”", style = sansStyle(12, V.cream.copy(alpha = 0.85f)), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("New record", style = sansStyle(12, V.muted), modifier = Modifier.clip(CircleShape)
                        .clickable(enabled = render == null) { reset() }.padding(horizontal = 12.dp, vertical = 10.dp))
                }
            }
            Row(Modifier.padding(top = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                STEPS.forEachIndexed { i, _ ->
                    Box(Modifier.weight(1f).height(2.dp).clip(CircleShape).background(if (i <= step) V.amber else V.cream.copy(alpha = 0.1f)))
                }
            }
            if (!(pressed != null && step == 4)) Text(HINTS[step], style = sansStyle(13, V.cream.copy(alpha = 0.75f)).copy(lineHeight = 18.sp), modifier = Modifier.padding(top = 8.dp))
        }
        // body
        Column(
            Modifier.weight(1f).fillMaxWidth()
                .drawBehind {
                    drawRect(Brush.radialGradient(listOf(V.amber.copy(alpha = 0.10f), Color.Transparent), center = Offset(size.width / 2, 0f), radius = size.width * 0.72f))
                }
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 32.dp),
        ) {
            when (step) {
                0 -> Column {
                    if (moodReady && mood != null) Text(
                        buildAnnotatedString {
                            append("Mood ready: "); pushStyle(SpanStyle(color = V.amberBright)); append(mood.name); pop()
                            append(". Now record your voice, then tap Next.")
                        },
                        style = sansStyle(14).copy(lineHeight = 21.sp),
                        modifier = Modifier.padding(bottom = 20.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .border(1.dp, V.amber.copy(alpha = 0.4f), RoundedCornerShape(16.dp)).background(V.amber.copy(alpha = 0.1f)).padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                    Capture(
                    source = source,
                    srcName = srcName,
                    max = if (pro) PRO_SECONDS else Free.MAX_SECONDS,
                    pro = pro,
                    onVoiceRecorded = { vid, name, data, dur ->
                        currentVoiceId = vid
                        scope.launch {
                            RecordStore.saveVaultVoice(
                                id = vid,
                                name = name,
                                voice = data,
                                duration = dur,
                                isPro = pro
                            )
                        }
                    },
                    setSource = { s, n, demo ->
                        source = s
                        srcName = n
                        isDemo = demo && s != null
                        if (s == null) currentVoiceId = null
                    }
                )
                }
                1 -> Dedication(meta) { meta = it }
                2 -> Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column {
                        Text(headline("How should it ", "sound?"), style = displayStyle(34).copy(letterSpacing = (-0.5).sp))
                        Text(
                            "Just pick a mood — it chooses the sound, crackle and music for you.",
                            style = sansStyle(16, V.cream.copy(alpha = 0.85f)).copy(lineHeight = 24.sp), modifier = Modifier.padding(top = 12.dp),
                        )
                        Text(
                            buildAnnotatedString {
                                pushStyle(SpanStyle(color = V.cream)); append("Listen"); pop()
                                append(" plays ${if (withVoice && hasVoice) "your own recording" else "the sound"} in that mood for a few seconds. ")
                                pushStyle(SpanStyle(color = V.cream)); append("Example"); pop()
                                append(" plays a message someone else made with it.")
                            },
                            style = sansStyle(14, V.muted).copy(lineHeight = 22.sp), modifier = Modifier.padding(top = 6.dp),
                        )
                    }

                    // I. Moods — the easy path
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionTitle("I", "Choose a mood")
                        if (exampleError.isNotEmpty()) Text(exampleError, style = sansStyle(12, V.err))
                        MOODS.forEach { m ->
                            MoodCard(
                                m, on = moodId == m.id, locked = locked(Gate.Mood, m.id), sample = sampleFor(m.id),
                                listenLabel = if (withVoice && hasVoice) "Listen with my voice" else "Listen",
                                listenState = previewState("mood:" + m.id), exampleState = exampleState(m.id),
                                onPick = { applyMood(m.id) },
                                onListen = { listen("mood:" + m.id, p = m.presetId, c = m.crackleId, m = m.musicId, ml = m.musicLevel) },
                                onExample = { toggleExample(m.id) },
                            )
                        }
                    }

                    // the expert panel, closed by default
                    val panel = RoundedCornerShape(16.dp)
                    Row(
                        Modifier.fillMaxWidth().clip(panel).background(if (custom) V.stone.copy(alpha = 0.6f) else V.panel)
                            .border(1.dp, if (custom) V.amber.copy(alpha = 0.5f) else V.brass.copy(alpha = 0.3f), panel)
                            .clickable { custom = !custom }.heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Customise the sound yourself", style = displayStyle(18))
                            Text("For experts: character, crackle, music, volumes and your own music.", style = sansStyle(12, V.muted), modifier = Modifier.padding(top = 2.dp))
                            Text(
                                "${mood?.let { "Mood: ${it.name}" } ?: "Your own mix"} · $soundSummary",
                                style = sansStyle(12, V.amberBright.copy(alpha = 0.9f)), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        Box(Modifier.size(36.dp).clip(CircleShape).border(1.dp, V.brass.copy(alpha = 0.4f), CircleShape), contentAlignment = Alignment.Center) {
                            Text("+", style = sansStyle(22, V.amberBright), modifier = Modifier.rotate(if (custom) 45f else 0f))
                        }
                    }

                    if (custom) Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
                    Column {
                        if (hasVoice) VoiceSwitch(withVoice) { stopPreview(); withVoice = !withVoice }
                        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LevelBar("Overall volume", volume, 1f, { volume = it }, Modifier.weight(1f))
                            LevelBar("Music volume", musicLevel, 1f, { musicLevel = it }, Modifier.weight(1f), ends = "Off" to "Full")
                        }
                    }

                    // II. Character
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionTitle("II", "Character")
                        LevelBar("Character strength", character, 1.5f, { character = it }, Modifier.fillMaxWidth(), ends = "Clean" to "Heavy", locked = !pro)
                        PRESETS.forEachIndexed { i, p ->
                            val on = presetId == p.id
                            val shape = RoundedCornerShape(16.dp)
                            Box(
                                Modifier.fillMaxWidth().clip(shape)
                                    .background(if (on) Brush.linearGradient(listOf(V.amber.copy(alpha = 0.18f), V.amber.copy(alpha = 0.04f))) else Brush.linearGradient(listOf(V.panel, V.panel)))
                                    .border(1.dp, if (on) V.amber else V.brass.copy(alpha = 0.2f), shape),
                            ) {
                                Column(
                                    Modifier.fillMaxWidth().clickable { gate(Gate.Preset, p.id, p.name) { presetId = p.id; moodId = null; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) } }
                                        .padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 64.dp),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(ROMAN[i], style = monoStyle(11, V.amberBright.copy(alpha = 0.8f)))
                                        Text(p.name, style = displayStyle(20))
                                        if (locked(Gate.Preset, p.id)) ProBadge()
                                    }
                                    Text(p.blurb, style = sansStyle(13, V.muted), modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Meter(p.warmth, "Warmth"); Meter(p.age, "Age"); Meter(p.texture, "Texture")
                                    }
                                }
                                Listen(previewState("preset:" + p.id), { listen("preset:" + p.id, p = p.id) }, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp))
                            }
                        }
                    }

                    // III. Crackle
                    Column {
                        SectionTitle("III", "Crackle")
                        Text(
                            "Every surface has its own voice. The strip shows how it falls across the groove.",
                            style = sansStyle(12, V.muted), modifier = Modifier.offset(y = (-4).dp).padding(bottom = 12.dp),
                        )
                        LevelBar("Crackle volume", crackleLevel, 2f, { crackleLevel = it }, Modifier.fillMaxWidth().padding(bottom = 12.dp), ends = "Off" to "Loud", locked = !pro)
                        CRACKLES.chunked(2).forEachIndexed { ri, row ->
                            Row(Modifier.padding(top = if (ri > 0) 10.dp else 0.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { c ->
                                    CrackleCard(
                                        c, on = crackleId == c.id, locked = locked(Gate.Crackle, c.id),
                                        listenState = previewState("crackle:" + c.id),
                                        onPick = { gate(Gate.Crackle, c.id, c.name) { crackleId = c.id; moodId = null; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) } },
                                        onListen = { listen("crackle:" + c.id, c = c.id) },
                                        modifier = Modifier.weight(1f).fillMaxHeight(),
                                    )
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }

                    // IV. Music
                    Column {
                        SectionTitle("IV", "Background music")
                        val shape = RoundedCornerShape(16.dp)
                        Column(Modifier.fillMaxWidth().clip(shape).background(V.panel).border(1.dp, V.brass.copy(alpha = 0.2f), shape)) {
                            MUSIC.forEachIndexed { i, m ->
                                val on = music == m.id
                                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(V.brass.copy(alpha = 0.1f)))
                                Row(Modifier.fillMaxWidth().background(if (on) V.amber.copy(alpha = 0.1f) else Color.Transparent), verticalAlignment = Alignment.CenterVertically) {
                                    Row(
                                        Modifier.weight(1f).heightIn(min = 56.dp).clickable { gate(Gate.Music, m.id, m.name) { music = m.id; moodId = null } }
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Box(
                                            Modifier.size(16.dp).clip(CircleShape).border(1.dp, if (on) V.amberBright else V.brass.copy(alpha = 0.4f), CircleShape),
                                            contentAlignment = Alignment.Center,
                                        ) { if (on) Box(Modifier.size(8.dp).clip(CircleShape).background(V.amberBright)) }
                                        Column(Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(m.name, style = sansStyle(13, V.cream, 500))
                                                if (locked(Gate.Music, m.id)) ProBadge()
                                                if (m.romantic) Text(
                                                    "♥ ROMANTIC", style = sansStyle(9, Color(0xFFFCA5A5)).copy(letterSpacing = 0.5.sp, lineHeight = 12.sp),
                                                    modifier = Modifier.clip(CircleShape).background(V.ruby.copy(alpha = 0.3f)).padding(horizontal = 6.dp, vertical = 1.dp),
                                                )
                                            }
                                            Text(m.blurb, style = sansStyle(11, V.muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                    if (m.id != "none") Listen(previewState("music:" + m.id), { listen("music:" + m.id, m = m.id) }, small = true, modifier = Modifier.padding(end = 12.dp))
                                }
                            }
                            // your own music
                            customBed?.let { (bed, name) ->
                                val on = music == CUSTOM_MUSIC
                                Box(Modifier.fillMaxWidth().height(1.dp).background(V.brass.copy(alpha = 0.1f)))
                                Row(Modifier.fillMaxWidth().background(if (on) V.amber.copy(alpha = 0.1f) else Color.Transparent), verticalAlignment = Alignment.CenterVertically) {
                                    Row(
                                        Modifier.weight(1f).heightIn(min = 56.dp).clickable { music = CUSTOM_MUSIC; moodId = null }.padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Box(
                                            Modifier.size(16.dp).clip(CircleShape).border(1.dp, if (on) V.amberBright else V.brass.copy(alpha = 0.4f), CircleShape),
                                            contentAlignment = Alignment.Center,
                                        ) { if (on) Box(Modifier.size(8.dp).clip(CircleShape).background(V.amberBright)) }
                                        Column(Modifier.weight(1f)) {
                                            Text("Your own music", style = sansStyle(13, V.cream, 500))
                                            Text("$name · ${com.vynyl.record.ui.components.fmt(bed.size / SR.toFloat())}", style = sansStyle(11, V.muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                    if (customSource != null) Text(
                                        "Trim", style = sansStyle(12),
                                        modifier = Modifier.padding(end = 8.dp).clip(CircleShape).border(1.dp, V.brass.copy(alpha = 0.4f), CircleShape)
                                            .clickable { cropping = true }.padding(horizontal = 12.dp, vertical = 8.dp),
                                    )
                                    Listen(previewState("music:custom"), { listen("music:custom", m = CUSTOM_MUSIC) }, small = true, modifier = Modifier.padding(end = 12.dp))
                                }
                            }
                            Box(Modifier.fillMaxWidth().height(1.dp).background(V.brass.copy(alpha = 0.1f)))
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { musicPicker.launch("audio/*") }.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(Modifier.size(32.dp).clip(CircleShape).border(1.dp, V.amber.copy(alpha = 0.5f), CircleShape), contentAlignment = Alignment.Center) {
                                    Text("↥", style = sansStyle(14, V.amberBright))
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(if (customBed != null) "Upload different music" else "Upload your own music", style = sansStyle(13, V.amberBright, 500))
                                    Text("A song from your device. You choose which part to use.", style = sansStyle(11, V.muted))
                                }
                            }
                        }
                        if (customError.isNotEmpty()) Text(customError, style = sansStyle(12, V.err), modifier = Modifier.padding(top = 8.dp))
                        if (music != "none") LevelBar("Music volume", musicLevel, 1f, { musicLevel = it }, Modifier.fillMaxWidth().padding(top = 12.dp))
                    }
                    }
                }
                3 -> Column {
                    Text(headline("Choose the ", "wax."), style = displayStyle(34).copy(letterSpacing = (-0.5).sp))
                    Box(Modifier.bleed(24.dp).padding(vertical = 8.dp).fillMaxWidth().height(256.dp), contentAlignment = Alignment.Center) {
                        WaxPreview(style, meta)
                    }
                    STYLES.chunked(5).forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { s ->
                                val on = styleId == s.id
                                Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                                    Box(
                                        Modifier.fillMaxSize().scale(if (on) 1.05f else 1f).clip(CircleShape)
                                            .border(2.dp, if (on) V.amberBright else Color.Transparent, CircleShape)
                                            .clickable { gate(Gate.Style, s.id, s.name) { styleId = s.id } }
                                            .padding(4.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        val disc = Color(s.disc)
                                        GrooveDisc(disc, mix(disc, Color.White, 0.2f), Modifier.fillMaxSize().clip(CircleShape), step = 3.dp)
                                        Box(Modifier.fillMaxSize(1f / 3f).clip(CircleShape).background(Color(s.label)))
                                    }
                                    if (locked(Gate.Style, s.id)) Box(Modifier.align(Alignment.BottomCenter).offset(y = 4.dp).scale(0.9f)) { ProBadge() }
                                }
                            }
                        }
                    }
                    Text(style.name, style = displayStyle(20).copy(fontStyle = FontStyle.Italic), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
                4 -> Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val r = render
                    val spin by rememberInfiniteTransition(label = "press").animateFloat(0f, 360f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "s")
                    Box(
                        Modifier.size(208.dp)
                            .drawBehind {
                                drawCircle(Brush.radialGradient(listOf(V.amber.copy(alpha = 0.35f), Color.Transparent), radius = size.minDimension * 0.7f), radius = size.minDimension * 0.7f)
                                drawCircle(V.brass.copy(alpha = 0.25f), radius = size.minDimension / 2 + 6.dp.toPx())
                            }
                            .rotate(if (r != null) spin else 0f),
                        contentAlignment = Alignment.Center,
                    ) {
                        GrooveDisc(Color(style.disc), Color(0xFF2A2522), Modifier.fillMaxSize())
                        Box(Modifier.size(64.dp).clip(CircleShape).background(Color(style.label)), contentAlignment = Alignment.Center) {
                            Text("VR", style = displayStyle(12, Color(style.ink)))
                        }
                    }
                    Text(if (r != null) "Pressing your record" else if (pressed != null) "Pressed! It’s on your shelf." else "Ready to press.", style = displayStyle(30), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 32.dp))
                    Text(
                        r?.stage ?: "${mood?.let { it.name + " · " } ?: ""}$soundSummary · ${style.name}. Every crackle is computed on this device.",
                        style = sansStyle(14, V.muted), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp),
                    )
                    if (r != null) {
                        val w by animateFloatAsState(r.p.coerceIn(0f, 1f), label = "w")
                        Box(Modifier.padding(top = 24.dp).fillMaxWidth().height(4.dp).clip(CircleShape).background(V.cream.copy(alpha = 0.1f))) {
                            Box(Modifier.fillMaxWidth(w).fillMaxHeight().background(V.amber))
                        }
                        Text("${(r.p * 100).roundToInt()}%", style = monoStyle(12), modifier = Modifier.padding(top = 8.dp))
                    }
                    val done = pressed
                    if (done != null && r == null) Column(Modifier.padding(top = 28.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.fillMaxWidth().height(56.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(V.gold1, V.gold2, V.gold3))).clickable { onPlay(done) },
                            contentAlignment = Alignment.Center) { Text("▶ PLAY MY RECORD", style = decoStyle(14, V.obsidian)) }
                        Row(Modifier.fillMaxWidth().height(56.dp).clip(CircleShape).border(1.dp, V.amber.copy(alpha = 0.5f), CircleShape).clickable { makeChanges() },
                            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Text("Go back and make changes", style = sansStyle(16))
                            if (!pro) { if (editsLeft > 0) Text(" · $editsLeft free left", style = sansStyle(14, V.amberBright)) else { Spacer(Modifier.width(8.dp)); ProBadge() } }
                        }
                        Text("Start a new record", style = sansStyle(16, V.muted), textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().clip(CircleShape).clickable { reset() }.padding(vertical = 14.dp))
                    }
                }
            }
        }
        // footer
        Box(Modifier.fillMaxWidth().height(1.dp).background(V.brass.copy(alpha = 0.15f)))
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Btn("‹ Back", { if (pressed != null && step == 4) makeChanges() else step -= 1 }, variant = BtnVariant.Ghost, enabled = step != 0 && render == null)
            val done = pressed
            if (done != null && step == 4) {
                Btn("Play", { onPlay(done) })
            } else if (step < 4) {
                Btn("Next: ${STEPS[step + 1]} ›", { step += 1 }, enabled = !(step == 0 && source == null))
            } else {
                Btn("Press record", enabled = render == null && source != null, onClick = press@{
                    if (base == null && !pro && recordCount >= Free.MAX_RECORDS) {
                        Pro.openPaywall("Your free shelf holds ${Free.MAX_RECORDS} records and it’s full. Go Pro for unlimited records."); return@press
                    }
                    if (locked(Gate.Preset, presetId) || locked(Gate.Crackle, crackleId) || locked(Gate.Music, music) || locked(Gate.Style, styleId)) {
                        Pro.openPaywall(
                            if (mood != null && locked(Gate.Mood, mood.id)) "“${mood.name}” is a Pro mood. Go Pro to press it, or pick a free mood."
                            else "This record uses Pro sounds or wax. Go Pro to press it, or pick free options.",
                        ); return@press
                    }
                    val src = source ?: return@press
                    val id = base?.id ?: UUID.randomUUID().toString()
                    val prev = base; val moodSel = moodId
                    val m = meta
                    val pId = presetId; val cId = crackleId; val sId = styleId
                    // uploaded music only counts if it is still loaded
                    val customNow = if (music == CUSTOM_MUSIC) customBed else null
                    val mus = if (music == CUSTOM_MUSIC && customNow == null) "none" else music
                    val ml = musicLevel; val cl = crackleLevel; val ch = character; val vol = volume
                    stopPreview(); stopExample(); render = RenderState(0f, "Preparing source")
                    scope.launch {
                        val bed = customNow?.first ?: withContext(Dispatchers.Default) { musicBed(mus) }
                        val opts = RenderOpts(
                            preset = com.vynyl.record.audio.preset(pId), crackle = CRACKLES.firstOrNull { it.id == cId }, seed = id, intro = 1.2f, tail = 1f,
                            music = bed, musicLevel = ml, crackleLevel = cl, character = ch, volume = vol,
                        )
                        val st = renderMaster(src, opts) { p, stage -> render = RenderState(p * 0.9f, stage) }
                        render = RenderState(0.94f, "Encoding"); delay(30)
                        val master = withContext(Dispatchers.Default) { encodeWav(st) }
                        render = RenderState(0.98f, "Generating waveform")
                        val wave = withContext(Dispatchers.Default) { waveform(st.l) }
                        val settings = StudioSettings(pId, sId, cId, mus, ml, cl, ch, vol, moodSel)
                        val fresh = RecordMeta(
                            id = id, title = m.title, recipient = m.recipient, sender = m.sender, dedication = m.dedication,
                            occasion = m.occasion, date = m.date, sideA = m.sideA, sideB = m.sideB,
                            presetId = pId, styleId = sId, duration = st.l.size / SR.toFloat(), wave = wave,
                            createdAt = System.currentTimeMillis(), favorite = false, crackleId = cId, musicId = mus,
                            hasVoice = true, settings = settings, customMusicName = customNow?.second,
                        )
                        // re-pressing keeps the record's identity, photo, favourite and edit count
                        val rec = prev?.let { fresh.copy(createdAt = it.createdAt, favorite = it.favorite, lastPlayedAt = it.lastPlayedAt, labelPhotoAdjust = it.labelPhotoAdjust, hasPhoto = it.hasPhoto, reedits = it.reedits) } ?: fresh
                        RecordStore.saveVoice(id, src)
                        RecordStore.saveCustomMusic(id, customNow?.first)
                        RecordStore.put(rec, master)
                        if (!isDemo) {
                            val vId = currentVoiceId ?: id
                            RecordStore.saveVaultVoice(
                                id = vId,
                                name = m.title.ifBlank { srcName ?: "Voice take" },
                                voice = src,
                                duration = st.l.size / SR.toFloat(),
                                recordTitle = m.title.ifBlank { "Untitled" },
                                isPro = pro
                            )
                        }
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        render = null; pressed = rec
                        onDone(rec)
                    }
                })
            }
        }
    }
    if (cropping) customSource?.let { (data, name) -> MusicCropSheet(data, name, onCancel = { cropping = false }, onSave = { a, b -> saveCrop(a, b) }) }
    }
}

@Composable
private fun MoodCard(
    m: Mood, on: Boolean, locked: Boolean, sample: Sample?, listenLabel: String,
    listenState: ListenState, exampleState: ListenState, onPick: () -> Unit, onListen: () -> Unit, onExample: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (on) Brush.linearGradient(listOf(V.amber.copy(alpha = 0.22f), V.amber.copy(alpha = 0.04f))) else SolidColor(V.panel))
            .border(1.dp, if (on) V.amber else V.brass.copy(alpha = 0.2f), shape).padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onPick), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.padding(top = 2.dp).size(24.dp).clip(CircleShape).background(if (on) V.amberBright else Color.Transparent)
                    .border(2.dp, if (on) V.amberBright else V.brass.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { if (on) Text("✓", style = sansStyle(12, V.obsidian, 700)) }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(m.name, style = displayStyle(20), modifier = Modifier.weight(1f, fill = false))
                    if (locked) ProBadge()
                }
                Text(m.blurb, style = sansStyle(14, V.muted).copy(lineHeight = 19.sp), modifier = Modifier.padding(top = 4.dp))
                sample?.let { Text("Example: a ${it.persona.lowercase()}'s message", style = sansStyle(12, V.cream.copy(alpha = 0.55f)), modifier = Modifier.padding(top = 4.dp)) }
            }
        }
        Row(Modifier.padding(start = 36.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ListenPill(listenState, listenLabel, ghost = false, onClick = onListen)
            if (sample != null) ListenPill(exampleState, "Example", ghost = true, onClick = onExample)
        }
        if (on && locked) Text(
            "Free to listen. Pressing a record with this mood needs Vynyl Pro.",
            style = sansStyle(12, V.amberBright).copy(lineHeight = 18.sp), modifier = Modifier.padding(start = 36.dp, top = 12.dp),
        )
    }
}

/** Big, labelled listen button for the mood cards — easier to see and tap than the round ▶. */
@Composable
private fun ListenPill(state: ListenState, label: String, ghost: Boolean, onClick: () -> Unit) {
    val busy = state != ListenState.Idle
    Row(
        Modifier.heightIn(min = 44.dp).clip(CircleShape)
            .background(if (busy) V.amber else if (ghost) Color.Transparent else V.amber.copy(alpha = 0.15f))
            .border(1.dp, if (busy) V.amber else if (ghost) V.brass.copy(alpha = 0.4f) else V.amber.copy(alpha = 0.6f), CircleShape)
            .clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (state) {
            ListenState.Loading -> {
                val rot by rememberInfiniteTransition(label = "spin").animateFloat(0f, 360f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "r")
                Canvas(Modifier.size(14.dp).rotate(rot)) { drawArc(V.obsidian, 0f, 270f, false, style = Stroke(2.dp.toPx())) }
            }
            ListenState.Playing -> Row(Modifier.height(12.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf(0.6f, 1f, 0.75f).forEach { h -> Box(Modifier.width(3.dp).fillMaxHeight(h).clip(RoundedCornerShape(2.dp)).background(V.obsidian)) }
            }
            ListenState.Idle -> Canvas(Modifier.size(10.dp, 12.dp)) {
                drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(0f, 0f); lineTo(size.width, size.height / 2); lineTo(0f, size.height); close() }, if (ghost) V.cream.copy(alpha = 0.8f) else V.amberBright)
            }
        }
        Text(
            when (state) { ListenState.Loading -> "Getting it ready…"; ListenState.Playing -> "Stop"; ListenState.Idle -> label },
            style = sansStyle(14, if (busy) V.obsidian else V.cream, 500),
        )
    }
}

@Composable
private fun VoiceSwitch(on: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.padding(top = 12.dp).heightIn(min = 44.dp).clip(CircleShape).background(V.panel)
            .border(1.dp, V.brass.copy(alpha = 0.3f), CircleShape).clickable(onClick = onToggle)
            .padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val x by animateDpAsState(if (on) 22.dp else 2.dp, label = "x")
        Box(Modifier.size(44.dp, 24.dp).clip(CircleShape).background(if (on) V.amber else V.cream.copy(alpha = 0.15f))) {
            Box(Modifier.offset(x = x, y = 2.dp).size(20.dp).clip(CircleShape).background(V.cream))
        }
        Text("Include my recording in previews", style = sansStyle(12))
    }
}

private data class Tick(val x: Float, val h: Float, val w: Float)

private fun toUint32(d: Double): Long {
    var m = d % 4294967296.0
    if (m < 0) m += 4294967296.0
    return m.toLong()
}

@Composable
private fun CrackleCard(
    c: Crackle, on: Boolean, locked: Boolean, listenState: ListenState,
    onPick: () -> Unit, onListen: () -> Unit, modifier: Modifier = Modifier,
) {
    // deterministic groove signature drawn from the crackle's character (same PRNG as the web)
    val sig = remember(c.id) {
        var a = 7.0
        for (ch in c.id) a = a * 31 + ch.code
        var seed = toUint32(a)
        fun rnd(): Float { seed = (seed * 1664525L + 1013904223L) and 0xFFFFFFFFL; return (seed / 4294967296.0).toFloat() }
        val ticks = List((c.density * 3.2f).roundToInt()) { Tick(rnd() * 120f, 2f + rnd() * 7f * minOf(c.amp, 1.4f), 0.6f + c.len * 0.5f) }
        val pops = List((c.pops * 0.9f).roundToInt()) { Tick(4f + rnd() * 112f, 9f + rnd() * 4f, 1.6f) }
        ticks to pops
    }
    val hiss = ((c.hissDb + 30f) / 36f).coerceIn(0f, 1f)
    val level = if (c.id == "silent") 0 else ((c.density * c.amp) / 2.4f + c.pops / 2.2f + hiss).roundToInt().coerceIn(1, 5)
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier.clip(shape)
            .background(if (on) Brush.verticalGradient(listOf(V.amber.copy(alpha = 0.15f), V.amber.copy(alpha = 0.04f))) else Brush.verticalGradient(listOf(V.panel, V.panel)))
            .border(1.dp, if (on) V.amber else V.brass.copy(alpha = 0.2f), shape),
    ) {
        Column(Modifier.fillMaxSize().clickable(onClick = onPick).padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 44.dp)) {
            if (locked) { ProBadge(); Spacer(Modifier.height(4.dp)) }
            Text(c.name, style = displayStyle(15, if (on) V.amberBright else V.cream).copy(lineHeight = 18.sp))
            Text(c.blurb, style = sansStyle(11, V.muted).copy(lineHeight = 15.sp), modifier = Modifier.padding(top = 4.dp).heightIn(min = 30.dp))
            Spacer(Modifier.weight(1f).heightIn(min = 10.dp))
            val stripShape = RoundedCornerShape(6.dp)
            Box(
                Modifier.fillMaxWidth().clip(stripShape).background(V.obsidian.copy(alpha = if (on) 0.7f else 0.5f))
                    .border(1.dp, if (on) V.amber.copy(alpha = 0.4f) else V.brass.copy(alpha = 0.15f), stripShape)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxWidth().height(26.dp)) {
                    val sx = size.width / 120f; val sy = size.height / 26f
                    val dash = when {
                        c.hissTone < 0.8f -> PathEffect.dashPathEffect(floatArrayOf(3f * sx, 1.5f * sx))
                        c.hissTone > 1.05f -> PathEffect.dashPathEffect(floatArrayOf(0.8f * sx, 0.8f * sx))
                        else -> null
                    }
                    drawLine(V.brass.copy(alpha = 0.15f + hiss * 0.35f), Offset(0f, 13f * sy), Offset(size.width, 13f * sy), strokeWidth = (0.6f + hiss * 1.6f) * sy, pathEffect = dash)
                    sig.first.forEachIndexed { k, t ->
                        val col = if (on) V.amberBright else V.cream.copy(alpha = 0.55f)
                        drawRoundRect(col.copy(alpha = col.alpha * (0.45f + (k % 3) * 0.2f).coerceAtMost(1f)), Offset(t.x * sx, (13f - t.h / 2) * sy), Size(t.w * sx, t.h * sy), CornerRadius(t.w / 2 * sx))
                    }
                    sig.second.forEach { t ->
                        drawRoundRect(if (on) V.amber else V.ruby, Offset(t.x * sx, (13f - t.h) * sy), Size(1.6f * sx, t.h * 2 * sy), CornerRadius(0.8f * sx))
                    }
                }
                if (c.id == "silent") Text("HUSH", style = monoStyle(8, V.muted).copy(letterSpacing = 2.4.sp))
            }
            Row(Modifier.padding(top = 8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    (if (level == 0) "None" else listOf("Whisper", "Gentle", "Present", "Rich", "Heavy")[level - 1]).uppercase(),
                    style = monoStyle(9).copy(letterSpacing = 1.6.sp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (n in 1..5) Box(
                        Modifier.size(6.dp).clip(CircleShape).background(if (n <= level) (if (on) V.amberBright else V.brass) else V.brass.copy(alpha = 0.2f)),
                    )
                }
            }
        }
        Listen(listenState, onListen, small = true, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
    }
}

/** Static stand-in for the Turntable component on the wax step: the disc with its printed label. */
@Composable
private fun WaxPreview(style: com.vynyl.record.audio.VinylStyle, meta: StudioMeta) {
    val disc = Color(style.disc)
    Box(Modifier.size(232.dp).alpha(style.opacity), contentAlignment = Alignment.Center) {
        GrooveDisc(disc, mix(disc, Color.Black, 0.35f), Modifier.fillMaxSize().drawBehind {
            drawCircle(Color.Black.copy(alpha = 0.5f), radius = size.minDimension / 2 + 4.dp.toPx(), center = Offset(size.width / 2, size.height / 2 + 10.dp.toPx()))
        })
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f * style.sheen), Color.Transparent, Color.White.copy(alpha = 0.06f * style.sheen))))
        }
        Column(
            Modifier.size(88.dp).clip(CircleShape).background(Color(style.label)).padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            val ink = Color(style.ink)
            Text(meta.title, style = displayStyle(10, ink).copy(lineHeight = 11.sp), textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("for ${meta.recipient}", style = TextStyle(fontFamily = Fonts.sans, fontSize = 7.sp, color = ink.copy(alpha = 0.8f), fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(Modifier.padding(vertical = 2.dp).size(6.dp).clip(CircleShape).background(V.obsidian))
            Text("${meta.sideA.uppercase()} · ${meta.date}", style = monoStyle(6, ink.copy(alpha = 0.8f)), maxLines = 1)
        }
    }
}
