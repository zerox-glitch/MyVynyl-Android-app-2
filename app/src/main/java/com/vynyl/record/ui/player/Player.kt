package com.vynyl.record.ui.player

import com.vynyl.record.pro.Free
import com.vynyl.record.audio.NeedleDrop
import com.vynyl.record.ui.components.NameplatePreview
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.vynyl.record.audio.STYLES
import com.vynyl.record.audio.WavPlayer
import com.vynyl.record.audio.preset
import com.vynyl.record.audio.watermarkWav
import com.vynyl.record.audio.withNeedleDrop
import com.vynyl.record.data.PhotoAdjust
import com.vynyl.record.export.VideoRequest
import com.vynyl.record.export.VideoTier
import com.vynyl.record.export.canSaveToGallery
import com.vynyl.record.export.exportVideo
import com.vynyl.record.export.saveToGallery
import com.vynyl.record.data.RecordMeta
import com.vynyl.record.data.RecordStore
import com.vynyl.record.pro.Gate
import com.vynyl.record.pro.Pro
import com.vynyl.record.pro.isFree
import com.vynyl.record.turntable.LabelInfo
import com.vynyl.record.turntable.Turntable
import com.vynyl.record.ui.components.Wave
import com.vynyl.record.ui.components.fmt
import com.vynyl.record.ui.paywall.ProBadge
import com.vynyl.record.ui.theme.V
import com.vynyl.record.ui.theme.decoStyle
import com.vynyl.record.ui.theme.displayStyle
import com.vynyl.record.ui.theme.monoStyle
import com.vynyl.record.ui.theme.sansStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min

private class Editing(val source: ByteArray, val initial: PhotoAdjust?)

/** CSS-ish radial glow: ellipse radii rx, ry centred at (cx, cy), colour fading to transparent at [stop]. */
private fun DrawScope.ellipseGlow(cx: Float, cy: Float, rx: Float, ry: Float, color: Color, stop: Float) {
    if (rx <= 0f || ry <= 0f) return
    withTransform({ scale(1f, ry / rx, Offset(cx, cy)) }) {
        drawCircle(Brush.radialGradient(0f to color, stop to color.copy(alpha = 0f), center = Offset(cx, cy), radius = rx), radius = rx, center = Offset(cx, cy))
    }
}

/** Stroked 24×24 SVG icon. */
@Composable
private fun SvgIcon(d: String, size: Dp, color: Color, stroke: Float = 1.8f, modifier: Modifier = Modifier) {
    val path = remember(d) { PathParser().parsePathString(d).toPath() }
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 24f
        scale(s, s, pivot = Offset.Zero) {
            drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
    }
}

/** active:scale-* feedback. */
private fun Modifier.pressScale(source: MutableInteractionSource, to: Float): Modifier = composed {
    val pressed by source.collectIsPressedAsState()
    graphicsLayer { val s = if (pressed) to else 1f; scaleX = s; scaleY = s }
}

private fun queryName(ctx: Context, uri: Uri): Long = runCatching {
    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else -1L } ?: -1L
}.getOrDefault(-1L)

@Composable
fun Player(record: RecordMeta, onClose: () -> Unit, onEdit: (RecordMeta) -> Unit = {}) {
    val ctx = LocalContext.current
    val activity = ctx as Activity
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val player = remember(record.id) { WavPlayer(RecordStore.masterFile(record.id)) }
    DisposableEffect(player) { onDispose { player.release() } }
    val pro = Pro.entitlement.collectAsState().value.pro
    val position by player.position.collectAsState()

    var exporting by remember { mutableStateOf(false) }
    var video by remember { mutableStateOf<VideoJob?>(null) }
    var platePreview by remember { mutableStateOf(false) }
    var engaged by remember { mutableStateOf(false) }
    // back to the Studio to change music, crackle, mood… free users get Free.REEDITS trips per record
    val editsLeft = if (pro) Int.MAX_VALUE else max(0, Free.REEDITS - record.reedits)
    fun backToStudio() {
        if (!record.hasVoice) return
        if (editsLeft <= 0) { Pro.openPaywall("Free records can go back to the Studio once. Go Pro to re-tune the music, crackle and mood as often as you like."); return }
        val next = if (pro) record else record.copy(reedits = record.reedits + 1)
        engaged = false
        scope.launch { if (!pro) RecordStore.put(next); onEdit(next) }
    }
    var contact by remember { mutableStateOf(false) }
    var reset by remember { mutableIntStateOf(0) }
    var seekToken by remember { mutableIntStateOf(0) }
    var styleId by remember { mutableStateOf(record.styleId) }
    var full by remember { mutableStateOf(false) }
    var lastPlayedAt by remember { mutableStateOf(record.lastPlayedAt) }
    var labelPhoto by remember { mutableStateOf<Bitmap?>(null) }
    var photoBytes by remember { mutableStateOf<ByteArray?>(null) }
    var original by remember { mutableStateOf<ByteArray?>(null) }
    var adjust by remember { mutableStateOf(record.labelPhotoAdjust) }
    var photoBusy by remember { mutableStateOf(false) }
    var photoError by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Editing?>(null) }
    var ended by remember { mutableStateOf(false) }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var rootSize by remember { mutableStateOf(IntSize(1, 1)) }

    LaunchedEffect(record.id) {
        withContext(Dispatchers.IO) {
            val pf = RecordStore.photoFile(record.id); val of = RecordStore.photoOriginalFile(record.id)
            val pb = if (record.hasPhoto && pf.exists()) runCatching { pf.readBytes() }.getOrNull() else null
            val ob = if (of.exists()) runCatching { of.readBytes() }.getOrNull() else null
            val bmp = pb?.let { decodePhoto(it, 1024) }
            withContext(Dispatchers.Main) { photoBytes = pb; original = ob; labelPhoto = bmp }
        }
    }

    val style = STYLES.firstOrNull { it.id == styleId } ?: STYLES[0]
    val presetName = preset(record.presetId).name
    val dur = record.duration
    val t = if (ended) dur else min(position, dur)
    val progress = if (dur > 0f) t / dur else 0f

    // the stylus touching down: play the needle-drop thump on every landing
    var landed by remember { mutableStateOf(false) }
    LaunchedEffect(engaged, contact) {
        if (engaged && contact && !landed) NeedleDrop.play()
        landed = engaged && contact
    }
    // audio starts only at needle contact
    LaunchedEffect(engaged, contact) {
        if (engaged && contact) runCatching { player.play() }.onFailure { engaged = false } else player.pause()
    }
    // onEnded
    LaunchedEffect(position, engaged) {
        if (engaged && contact && dur > 0f && position >= dur - 0.03f) { engaged = false; ended = true }
    }

    fun seek(s: Float) { ended = false; player.seek(s.coerceIn(0f, dur)); seekToken++ }
    fun persist(patch: (RecordMeta) -> RecordMeta) = scope.launch { runCatching { RecordStore.update(record.id, patch) } }
    fun toggle() {
        if (!engaged && t >= dur - 0.05f) seek(0f)
        val wasEngaged = engaged
        engaged = !engaged
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        if (!wasEngaged) {
            val now = System.currentTimeMillis(); lastPlayedAt = now
            val sid = styleId
            persist { it.copy(styleId = sid, lastPlayedAt = now) }
        }
    }
    suspend fun savePhoto(photo: ByteArray?, source: ByteArray?, next: PhotoAdjust?) {
        photoError = ""
        photoBusy = true
        try {
            val sid = styleId; val lp = lastPlayedAt
            val bmp = withContext(Dispatchers.IO) {
                val pf = RecordStore.photoFile(record.id); val of = RecordStore.photoOriginalFile(record.id)
                if (photo != null) pf.writeBytes(photo) else pf.delete()
                if (source != null) of.writeBytes(source) else of.delete()
                RecordStore.update(record.id) { it.copy(styleId = sid, hasPhoto = photo != null, labelPhotoAdjust = next, lastPlayedAt = lp) }
                photo?.let { decodePhoto(it, 1024) }
            }
            labelPhoto = bmp; photoBytes = photo; original = source; adjust = next; editing = null
        } catch (e: Exception) {
            photoError = "Could not save this photo. Check available device storage and try again."
        } finally { photoBusy = false }
    }
    fun pickPhoto(uri: Uri) {
        photoError = ""
        val size = queryName(ctx, uri)
        if (size > 20L * 1024 * 1024) { photoError = "Choose a photo smaller than 20 MB."; return }
        scope.launch {
            val bytes = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() }
            if (bytes == null) { photoError = "Could not read this photo."; return@launch }
            if (bytes.size > 20 * 1024 * 1024) { photoError = "Choose a photo smaller than 20 MB."; return@launch }
            if (pro) { editing = Editing(bytes, null); return@launch }
            // free tier: place the photo with the default crop; the adjust studio is Pro
            photoBusy = true
            val labelArgb = Color(style.label).toArgb()
            val out = withContext(Dispatchers.Default) {
                runCatching { decodePhoto(bytes)?.let { renderAdjusted(it, DEFAULT_ADJUST, 1024, labelArgb).toJpeg(90) } }.getOrNull()
            }
            if (out == null) { photoError = "Could not read this photo."; photoBusy = false; return@launch }
            savePhoto(out, bytes, DEFAULT_ADJUST)
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) pickPhoto(uri) }
    val openPicker = { picker.launch("image/*") }

    fun exportWav() {
        exporting = true
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val master = RecordStore.masterFile(record.id).readBytes()
                    val bytes = withNeedleDrop(master).let { if (pro) it else watermarkWav(it) }
                    val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
                    File(dir, "${safeName(record.title)}.wav").apply { writeBytes(bytes) }
                }
                val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "audio/wav"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TITLE, "${record.title}.wav")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                activity.startActivity(Intent.createChooser(send, "${record.title}.wav"))
            } catch (_: Exception) {
            } finally { exporting = false }
        }
    }

    fun exportMp4() {
        if (video != null) return
        val tier = VideoTier.of(pro)
        val photo = labelPhoto
        val st = style
        val job = VideoJob()
        video = job
        job.job = scope.launch {
            try {
                val file = withContext(Dispatchers.Default) {
                    val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
                    val out = File(dir, "${safeName(record.title)}.mp4")
                    val req = VideoRequest(
                        master = RecordStore.masterFile(record.id), out = out, style = st,
                        label = LabelInfo(record.title, record.recipient, record.sideA, record.date), labelPhoto = photo,
                        nameplate = if (pro) record.sender to record.recipient else null,
                        title = record.title, sender = record.sender, recipient = record.recipient, tier = tier,
                    )
                    val ctxJob = coroutineContext
                    exportVideo(ctx, req, onProgress = { job.progress = it }, cancelled = { !ctxJob.isActive })
                    out
                }
                if (isActive) job.file = file
            } catch (e: Exception) {
                if (isActive) job.error = "Could not create the video. Check available storage and try again."
            }
        }
    }
    fun shareVideo(f: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, f.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(Intent.createChooser(send, f.name))
    }

    BackHandler(enabled = editing == null && video == null) { onClose() }

    val status = if (!engaged) (if (t > 0f && t < dur) "Paused" else "Idle") else if (contact) "Playing" else "Needle dropping…"

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(V.obsidian)
            .onGloballyPositioned { rootOrigin = it.positionInRoot(); rootSize = it.size }
            .pointerInputBlocker()
            .drawBehind {
                ellipseGlow(size.width * 0.5f, size.height, size.width * 0.6f, size.height * 0.4f, V.ruby.copy(alpha = 0.18f), 1f)
                ellipseGlow(size.width * 0.5f, size.height * 0.3f, size.width * 0.8f, size.height * 0.5f, V.amber.copy(alpha = 0.22f), 0.7f)
            },
    ) {
        val stageH = maxHeight * 0.46f
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // header
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                HeaderPill(onClick = onClose) {
                    Text("‹", style = sansStyle(12, V.amberBright)); Spacer(Modifier.width(6.dp)); Text("Vault", style = sansStyle(12, V.cream.copy(alpha = 0.85f)))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val live = engaged && contact
                    val pulse = rememberInfiniteTransition(label = "pulse").animateFloat(1f, 0.5f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "p")
                    Box(
                        Modifier.size(6.dp)
                            .then(if (live) Modifier.shadow(4.dp, CircleShape, ambientColor = V.amberBright, spotColor = V.amberBright) else Modifier)
                            .alpha(if (live) pulse.value else 1f)
                            .background(if (live) V.amberBright else V.cream.copy(alpha = 0.25f), CircleShape),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(status, style = monoStyle(11, V.amberBright))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    HeaderPill(onClick = { reset++ }, square = true) { SvgIcon("M4 12a8 8 0 1 0 2.3-5.7M4 4v4h4", 16.dp, V.cream.copy(alpha = 0.85f)) }
                    HeaderPill(onClick = { full = !full }) {
                        SvgIcon(if (full) "M9 4v5H4M15 4v5h5M9 20v-5H4M15 20v-5h5" else "M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5", 14.dp, V.cream.copy(alpha = 0.85f))
                        Spacer(Modifier.width(6.dp))
                        Text(if (full) "Exit" else "Full", style = sansStyle(12, V.cream.copy(alpha = 0.85f)))
                    }
                }
            }

            // stage
            Box(if (full) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth().height(stageH)) {
                Turntable(
                    modifier = Modifier.fillMaxSize(),
                    style = style,
                    label = LabelInfo(record.title, record.recipient, record.sideA, record.date),
                    engaged = engaged,
                    progress = progress,
                    position = { player.position.value },
                    duration = player.duration,
                    seekToken = seekToken,
                    onContact = { contact = it },
                    resetKey = reset,
                    labelPhoto = labelPhoto,
                    nameplate = if (pro) record.sender to record.recipient else null,
                    bgOrigin = rootOrigin,
                    bgSize = rootSize,
                )
                val photoSrc = remember { MutableInteractionSource() }
                Row(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 16.dp, top = 12.dp)
                        .pressScale(photoSrc, 0.95f)
                        .shadow(10.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                        .heightIn(min = 40.dp)
                        .clip(CircleShape)
                        .background(V.obsidian)
                        .border(1.dp, V.amber.copy(alpha = 0.5f), CircleShape)
                        .alpha(if (photoBusy) 0.5f else 1f)
                        .clickable(interactionSource = photoSrc, indication = null, enabled = !photoBusy) { openPicker() }
                        .padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val lp = labelPhoto
                    if (lp != null) Image(lp.asImageBitmap(), null, Modifier.size(28.dp).clip(CircleShape).border(1.dp, V.amber.copy(alpha = 0.6f), CircleShape), contentScale = ContentScale.Crop)
                    else Box(Modifier.size(28.dp).background(V.amber.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) { Text("＋", style = sansStyle(16, V.amberBright)) }
                    Spacer(Modifier.width(8.dp))
                    Text(if (photoBusy) "Saving photo…" else if (labelPhoto != null) "Change photo" else "Add loved-one photo", style = sansStyle(12, V.cream))
                }
                if (full && photoError.isNotEmpty()) {
                    Text(
                        photoError, style = sansStyle(12, V.err).copy(lineHeight = 19.sp),
                        modifier = Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 40.dp).fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp)).border(1.dp, V.err.copy(alpha = 0.3f), RoundedCornerShape(8.dp)).background(V.obsidian.copy(alpha = 0.9f)).padding(12.dp),
                    )
                }
                Text(
                    "Drag to orbit · pinch to zoom · double-tap to reset", style = sansStyle(10, V.muted.copy(alpha = 0.6f)), textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 8.dp),
                )
            }

            if (!full) {
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 16.dp).navigationBarsPadding()) {
                    Text("${record.occasion} · ${record.date}".uppercase(), style = decoStyle(10, V.amberBright))
                    Text(record.title, style = displayStyle(30).copy(lineHeight = 34.sp), modifier = Modifier.padding(top = 4.dp))
                    Text("for ${record.recipient} · from ${record.sender}", style = sansStyle(14, V.muted))
                    if (!pro) {
                        Row(
                            Modifier.padding(top = 8.dp).clip(CircleShape).border(1.dp, V.amber.copy(alpha = 0.4f), CircleShape).background(V.amber.copy(alpha = 0.1f))
                                .clickable { platePreview = true }
                                .padding(start = 6.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) { ProBadge(); Spacer(Modifier.width(8.dp)); Text("Gold nameplate on the plinth", style = sansStyle(11, V.cream.copy(alpha = 0.85f))) }
                    }
                    if (record.hasVoice) {
                        Row(
                            Modifier.padding(top = 8.dp).clip(CircleShape).border(1.dp, V.brass.copy(alpha = 0.35f), CircleShape)
                                .clickable { backToStudio() }.padding(start = 10.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("↶  Back to Studio", style = sansStyle(11, V.cream.copy(alpha = 0.85f)))
                            if (!pro) {
                                if (editsLeft > 0) Text(" · $editsLeft free", style = sansStyle(11, V.muted))
                                else { Spacer(Modifier.width(6.dp)); ProBadge() }
                            }
                        }
                    } else Text("Pressed before re-editing existed — record it again to change its sound.", style = sansStyle(11, V.muted), modifier = Modifier.padding(top = 8.dp))

                    var waveW by remember { mutableIntStateOf(1) }
                    Box(Modifier.padding(top = 16.dp).fillMaxWidth().height(40.dp).onSizeChanged { waveW = max(1, it.width) }.pointerInputTap { x -> seek(x / waveW * dur) }) {
                        Wave(record.wave, progress, Modifier.fillMaxSize())
                    }
                    Row(Modifier.padding(top = 4.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(fmt(t), style = monoStyle(11, V.muted)); Text("-" + fmt(max(0f, dur - t)), style = monoStyle(11, V.muted))
                    }

                    Row(Modifier.padding(top = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                        SkipButton(back = true) { seek(t - 10f) }
                        PlayButton(engaged = engaged, live = engaged && contact) { toggle() }
                        SkipButton(back = false) { seek(t + 10f) }
                    }

                    Row(Modifier.padding(top = 20.dp).height(IntrinsicSize.Min)) {
                        Box(Modifier.width(2.dp).fillMaxHeight().background(V.brass.copy(alpha = 0.5f)))
                        Text("“${record.dedication}”", style = displayStyle(18, V.cream.copy(alpha = 0.9f)).copy(fontStyle = FontStyle.Italic, lineHeight = 23.sp), modifier = Modifier.padding(start = 16.dp))
                    }

                    Row(Modifier.padding(top = 20.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (s in STYLES) {
                                val allowed = pro || isFree(Gate.Style, s.id)
                                val sel = s.id == styleId
                                val label = Color(s.label); val disc = Color(s.disc)
                                Box(
                                    Modifier.size(36.dp).scale(if (sel) 1.1f else 1f)
                                        .border(if (sel) 2.dp else 1.dp, if (sel) V.amberBright else V.brass.copy(alpha = 0.25f), CircleShape)
                                        .padding(if (sel) 4.dp else 1.dp)
                                        .shadow(4.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                                        .alpha(if (allowed) 1f else 0.6f)
                                        .clip(CircleShape)
                                        .drawBehind {
                                            val r = size.minDimension * 0.7071f
                                            drawRect(Brush.radialGradient(0f to label, 0.30f to label, 0.32f to disc, 1f to disc, center = center, radius = r))
                                        }
                                        .clickable { if (allowed) styleId = s.id else Pro.openPaywall("${s.name} wax is part of Vynyl Pro.") },
                                )
                            }
                        }
                        Text(presetName, style = sansStyle(11, V.muted), maxLines = 1, modifier = Modifier.padding(start = 8.dp).clip(CircleShape).border(1.dp, V.brass.copy(alpha = 0.3f), CircleShape).padding(horizontal = 12.dp, vertical = 4.dp))
                    }

                    // photo section
                    Column(Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, V.brass.copy(alpha = 0.25f), RoundedCornerShape(12.dp)).background(V.panel.copy(alpha = 0.7f)).padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(48.dp).clip(CircleShape).border(1.dp, V.amber.copy(alpha = 0.4f), CircleShape).background(V.stone), contentAlignment = Alignment.Center) {
                                val lp = labelPhoto
                                if (lp != null) Image(lp.asImageBitmap(), "Your record label photo", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                else Text("♡", style = displayStyle(20, V.amberBright))
                            }
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text("A face on the record.", style = displayStyle(14))
                                Text("A loved-one photo, on the spinning label.", style = sansStyle(11, V.muted).copy(lineHeight = 18.sp), modifier = Modifier.padding(top = 2.dp))
                            }
                        }
                        Row(Modifier.padding(top = 8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                Modifier.weight(1f).heightIn(min = 44.dp).clip(CircleShape).border(1.dp, V.amber.copy(alpha = 0.5f), CircleShape).background(V.amber.copy(alpha = 0.15f))
                                    .alpha(if (photoBusy) 0.5f else 1f).clickable(enabled = !photoBusy) { openPicker() }.padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text(if (photoBusy) "Saving photo…" else if (labelPhoto != null) "Replace photo" else "Add a loved-one photo", style = sansStyle(12, V.cream, 500)) }
                            if (labelPhoto != null) {
                                Row(
                                    Modifier.heightIn(min = 44.dp).clip(CircleShape).border(1.dp, V.brass.copy(alpha = 0.35f), CircleShape).alpha(if (photoBusy) 0.5f else 1f)
                                        .clickable(enabled = !photoBusy) {
                                            if (pro) {
                                                val src = original ?: photoBytes
                                                if (src != null) editing = Editing(src, if (original != null) adjust else null)
                                            } else Pro.openPaywall("Crop, zoom, rotate and show the whole photo on the label with Vynyl Pro.")
                                        }
                                        .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) { Text("Adjust", style = sansStyle(12, V.cream)); if (!pro) { Spacer(Modifier.width(6.dp)); ProBadge() } }
                                Box(
                                    Modifier.heightIn(min = 44.dp).clip(CircleShape).alpha(if (photoBusy) 0.5f else 1f)
                                        .clickable(enabled = !photoBusy) { scope.launch { savePhoto(null, null, null) } }.padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) { Text("Remove", style = sansStyle(12, V.muted)) }
                            }
                        }
                        Text("${if (adjust?.mode == "fit") "Full display" else "Cropped to fill"} · saved only on this device", style = sansStyle(10, V.muted.copy(alpha = 0.8f)), modifier = Modifier.padding(top = 4.dp))
                        if (photoError.isNotEmpty()) Text(photoError, style = sansStyle(12, V.err).copy(lineHeight = 19.sp), modifier = Modifier.padding(top = 8.dp))
                    }

                    // export
                    val exSrc = remember { MutableInteractionSource() }
                    Row(
                        Modifier.padding(top = 16.dp).fillMaxWidth().heightIn(min = 48.dp).pressScale(exSrc, 0.98f).clip(CircleShape)
                            .border(1.dp, V.brass.copy(alpha = 0.45f), CircleShape)
                            .background(Brush.verticalGradient(listOf(Color(0xFF26211C), Color(0xFF17140F))))
                            .alpha(if (exporting) 0.6f else 1f)
                            .clickable(interactionSource = exSrc, indication = null, enabled = !exporting) { exportWav() },
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SvgIcon("M12 4v11m0 0 4-4m-4 4-4-4M5 19h14", 16.dp, V.amberBright)
                        Spacer(Modifier.width(8.dp))
                        Text(if (exporting) "Preparing…" else "Export WAV master", style = sansStyle(14, V.cream))
                    }
                    if (!pro) {
                        val text = buildAnnotatedString {
                            append("Free exports end with a soft Vynyl chime. ")
                            withLink(LinkAnnotation.Clickable("pro", TextLinkStyles(SpanStyle(color = V.amberBright, textDecoration = TextDecoration.Underline))) {
                                Pro.openPaywall("Export clean, full-quality WAV masters with Vynyl Pro.")
                            }) { append("Remove with Pro") }
                        }
                        Text(text, style = sansStyle(11, V.muted), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp).fillMaxWidth())
                    }

                    val vSrc = remember { MutableInteractionSource() }
                    Row(
                        Modifier.padding(top = 12.dp).fillMaxWidth().heightIn(min = 48.dp).pressScale(vSrc, 0.98f).clip(CircleShape)
                            .border(1.dp, V.amber.copy(alpha = 0.55f), CircleShape)
                            .background(Brush.verticalGradient(listOf(Color(0xFF3A2A14), Color(0xFF1C150D))))
                            .alpha(if (video != null) 0.6f else 1f)
                            .clickable(interactionSource = vSrc, indication = null, enabled = video == null) { exportMp4() },
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SvgIcon("M4 7h11v10H4zM15 10l5-3v10l-5-3", 16.dp, V.amberBright)
                        Spacer(Modifier.width(8.dp))
                        Text("Export 3D turntable video", style = sansStyle(14, V.cream))
                        if (!pro) { Spacer(Modifier.width(8.dp)); ProBadge() }
                    }
                    Text(
                        if (pro) "MP4 · 1080p · full length, clean" else "Free: first ${VideoTier.FREE_SECONDS.toInt()} s at 720p with a Vynyl watermark",
                        style = sansStyle(11, V.muted), textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth()
                            .then(if (pro) Modifier else Modifier.clickable { Pro.openPaywall("Export full-length, 1080p turntable videos without the watermark with Vynyl Pro.") }),
                    )
                }
            }
        }

        video?.let { job ->
            VideoExportSheet(
                job = job, pro = pro,
                onCancel = { job.job?.cancel(); video = null },
                onShare = { job.file?.let { shareVideo(it) } },
                onSave = {
                    val f = job.file ?: return@VideoExportSheet
                    scope.launch {
                        val uri = withContext(Dispatchers.IO) { runCatching { saveToGallery(ctx, f, f.name) }.getOrNull() }
                        job.saved = if (uri != null) "Saved to Movies/Vynyl" else "Could not save to the gallery"
                    }
                },
                onUpgrade = { video = null; Pro.openPaywall("Export full-length, 1080p turntable videos without the watermark with Vynyl Pro.") },
            )
        }

        if (platePreview) NameplatePreview(record.sender, record.recipient) { platePreview = false }

        editing?.let { ed ->
            PhotoAdjustDialog(
                source = ed.source, initial = ed.initial, labelColor = Color(style.label).toArgb(), busy = photoBusy,
                onCancel = { editing = null },
                onSave = { photo, next -> scope.launch { savePhoto(photo, ed.source, next) } },
            )
        }
    }
}

/** Swallow taps so the vault underneath the overlay never receives them. */
private fun Modifier.pointerInputBlocker(): Modifier = this.then(Modifier.pointerInputTap { })

private fun Modifier.pointerInputTap(onTap: (Float) -> Unit): Modifier =
    this.then(Modifier.pointerInput(Unit) { detectTapGestures { onTap(it.x) } })

@Composable
private fun HeaderPill(onClick: () -> Unit, square: Boolean = false, content: @Composable () -> Unit) {
    val src = remember { MutableInteractionSource() }
    Row(
        Modifier
            .pressScale(src, 0.95f)
            .heightIn(min = 40.dp)
            .then(if (square) Modifier.size(40.dp) else Modifier)
            .clip(CircleShape)
            .border(1.dp, V.brass.copy(alpha = 0.3f), CircleShape)
            .background(V.obsidian.copy(alpha = 0.8f))
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = if (square) 0.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) { content() }
}

@Composable
private fun SkipButton(back: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    Box(
        Modifier
            .pressScale(src, 0.9f)
            .size(48.dp)
            .shadow(8.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(V.consoleTop, V.consoleBottom)))
            .border(1.dp, V.brass.copy(alpha = 0.4f), CircleShape)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        SvgIcon(if (back) "M5 12a7 7 0 1 0 2-4.9M5 4v3.5h3.5" else "M19 12a7 7 0 1 1-2-4.9M19 4v3.5h-3.5", 32.dp, V.cream.copy(alpha = 0.85f), stroke = 1.4f)
        Text("10", style = monoStyle(9, V.cream.copy(alpha = 0.85f), 500))
    }
}

@Composable
private fun PlayButton(engaged: Boolean, live: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val ping = rememberInfiniteTransition(label = "ping").animateFloat(0f, 1f, infiniteRepeatable(tween(2000, easing = LinearEasing)), label = "ping")
    Box(Modifier.size(76.dp).pressScale(src, 0.95f), contentAlignment = Alignment.Center) {
        if (live) {
            val k = ping.value
            Box(Modifier.fillMaxSize().graphicsLayer { scaleX = 1f + k; scaleY = 1f + k; alpha = 1f - k }.background(V.amber.copy(alpha = 0.2f), CircleShape))
        }
        Box(
            Modifier
                .fillMaxSize()
                .shadow(18.dp, CircleShape, ambientColor = V.amber, spotColor = V.amber)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0xFFF0C86A), Color(0xFFB8862E), Color(0xFF6B4210))))
                .clickable(interactionSource = src, indication = null, onClick = onClick)
                .padding(3.dp)
                .clip(CircleShape)
                .drawBehind {
                    drawCircle(Brush.radialGradient(0f to Color(0xFFFBBF24), 0.55f to Color(0xFFD97706), 1f to Color(0xFF92400E), center = Offset(size.width * 0.35f, size.height * 0.28f), radius = size.width * 0.85f))
                    // inset highlight / shade
                    drawCircle(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent, Color.Black.copy(alpha = 0.25f))))
                },
            contentAlignment = Alignment.Center,
        ) {
            if (engaged) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(8.dp, 24.dp).background(V.obsidian, RoundedCornerShape(2.dp)))
                    Box(Modifier.size(8.dp, 24.dp).background(V.obsidian, RoundedCornerShape(2.dp)))
                }
            } else {
                Canvas(Modifier.offset(x = 3.dp).size(19.dp, 24.dp)) {
                    val p = Path().apply { moveTo(0f, 0f); lineTo(size.width, size.height / 2); lineTo(0f, size.height); close() }
                    drawPath(p, V.obsidian)
                }
            }
        }
    }
}

private fun safeName(title: String) = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { "Vynyl record" }

/** Observable state of one MP4 export. */
private class VideoJob {
    var job: Job? = null
    var progress by mutableStateOf(0f)
    var file by mutableStateOf<File?>(null)
    var error by mutableStateOf<String?>(null)
    var saved by mutableStateOf<String?>(null)
}

@Composable
private fun VideoExportSheet(job: VideoJob, pro: Boolean, onCancel: () -> Unit, onShare: () -> Unit, onSave: () -> Unit, onUpgrade: () -> Unit) {
    BackHandler { onCancel() }
    val done = job.file != null
    Box(
        Modifier.fillMaxSize().background(V.obsidian.copy(alpha = 0.82f)).pointerInputBlocker().navigationBarsPadding(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .border(1.dp, V.brass.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF221C16), Color(0xFF14110D))))
                .padding(20.dp),
        ) {
            Text(if (done) "YOUR VIDEO IS READY" else "PRESSING YOUR VIDEO", style = decoStyle(10, V.amberBright))
            Text(
                when { job.error != null -> "Something went wrong"; done -> "A record worth sharing."; else -> "Spinning it up, frame by frame…" },
                style = displayStyle(22).copy(lineHeight = 27.sp), modifier = Modifier.padding(top = 6.dp),
            )
            val err = job.error
            if (err != null) {
                Text(err, style = sansStyle(12, V.err).copy(lineHeight = 19.sp), modifier = Modifier.padding(top = 8.dp))
            } else {
                // groove-style progress
                Box(Modifier.padding(top = 16.dp).fillMaxWidth().height(6.dp).clip(CircleShape).background(V.brass.copy(alpha = 0.18f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(job.progress.coerceIn(0f, 1f)).background(Brush.horizontalGradient(listOf(V.amber, V.amberBright)), CircleShape))
                }
                Row(Modifier.padding(top = 6.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (pro) "1080p · full length" else "720p · ${VideoTier.FREE_SECONDS.toInt()} s preview", style = monoStyle(11, V.muted))
                    Text("${(job.progress * 100).toInt()}%", style = monoStyle(11, V.amberBright))
                }
            }
            job.saved?.let { Text(it, style = sansStyle(12, V.cream.copy(alpha = 0.8f)), modifier = Modifier.padding(top = 10.dp)) }
            Row(Modifier.padding(top = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                @Composable
                fun Pill(text: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) = Box(
                    modifier.heightIn(min = 44.dp).clip(CircleShape)
                        .border(1.dp, if (primary) V.amber.copy(alpha = 0.6f) else V.brass.copy(alpha = 0.35f), CircleShape)
                        .background(if (primary) V.amber.copy(alpha = 0.18f) else Color.Transparent)
                        .clickable(onClick = onClick).padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(text, style = sansStyle(13, V.cream, if (primary) 500 else 400)) }
                if (done) {
                    Pill("Share", true, Modifier.weight(1f), onShare)
                    if (canSaveToGallery) Pill("Save to gallery", false, Modifier.weight(1f), onSave)
                    Pill("Done", false, Modifier, onCancel)
                } else Pill(if (err != null) "Close" else "Cancel", false, Modifier.weight(1f), onCancel)
            }
            if (!pro && done) {
                Row(Modifier.padding(top = 12.dp).clickable(onClick = onUpgrade), verticalAlignment = Alignment.CenterVertically) {
                    ProBadge(); Spacer(Modifier.width(8.dp))
                    Text("Full length, 1080p and no watermark", style = sansStyle(11, V.cream.copy(alpha = 0.85f)))
                }
            }
        }
    }
}
