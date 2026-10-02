package com.vynyl.record.ui.studio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.vynyl.record.audio.Recorder
import com.vynyl.record.audio.SR
import com.vynyl.record.audio.Stereo
import com.vynyl.record.audio.WavPlayer
import com.vynyl.record.audio.decodeToMono
import com.vynyl.record.audio.demoVoice
import com.vynyl.record.audio.encodeWav
import com.vynyl.record.pro.Pro
import com.vynyl.record.ui.components.Btn
import com.vynyl.record.ui.components.BtnVariant
import com.vynyl.record.ui.components.Wave
import com.vynyl.record.ui.components.fmt
import com.vynyl.record.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class RecState { Idle, Rec, Paused, Denied }

private fun concat(parts: List<FloatArray>): FloatArray {
    val out = FloatArray(parts.sumOf { it.size })
    var o = 0
    for (p in parts) { p.copyInto(out, o); o += p.size }
    return out
}

private fun displayName(ctx: Context, uri: Uri): String? = runCatching {
    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}.getOrNull() ?: uri.lastPathSegment

/**
 * Port of `Capture` in Studio.tsx. [setSource] receives (samples, displayName, isDemo).
 */
@Composable
internal fun Capture(
    source: FloatArray?, srcName: String?, max: Int, pro: Boolean,
    setSource: (FloatArray?, String?, Boolean) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var trimmed by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf(RecState.Idle) }
    val recorder = remember { Recorder() }
    val segments = remember { mutableListOf<FloatArray>() }
    var accSec by remember { mutableFloatStateOf(0f) }
    val level by recorder.level.collectAsState()
    val segSec by recorder.seconds.collectAsState()
    val t = if (state == RecState.Rec) accSec + segSec else accSec
    var live by remember { mutableStateOf(listOf<Float>()) }
    val shownLevel = if (state == RecState.Rec) level else 0f

    DisposableEffect(Unit) { onDispose { recorder.cancel() } }

    LaunchedEffect(state) {
        while (state == RecState.Rec) {
            live = (live + recorder.level.value).takeLast(60)
            delay(16)
        }
    }

    fun finish() {
        if (state == RecState.Rec) segments += recorder.stop()
        if (state != RecState.Rec && state != RecState.Paused) return
        accSec = segments.sumOf { it.size } / SR.toFloat()
        val data = concat(segments)
        segments.clear()
        state = RecState.Idle
        setSource(data, "Microphone take · " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()), false)
    }

    fun onLimit() { scope.launch(Dispatchers.Main) { finish() } }

    fun begin() {
        segments.clear(); accSec = 0f; live = emptyList()
        recorder.start(max) { onLimit() }
        state = RecState.Rec
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) begin() else state = RecState.Denied
    }
    fun start() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) begin()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
    fun pauseResume() {
        if (state == RecState.Rec) {
            segments += recorder.stop()
            accSec = segments.sumOf { it.size } / SR.toFloat()
            state = RecState.Paused
        } else {
            val left = (max - accSec).toInt().coerceAtLeast(1)
            recorder.start(left) { onLimit() }
            state = RecState.Rec
        }
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val raw = runCatching { decodeToMono(ctx, uri) }.getOrNull()
            val limit = max * SR
            val d = if (raw != null && raw.size > limit) raw.copyOfRange(0, limit) else raw
            setSource(d, if (d != null) displayName(ctx, uri) else null, false)
            trimmed = raw != null && raw.size > limit
        }
    }

    Column {
        Text(headline("A voice they can ", "return to."), style = displayStyle(36).copy(lineHeight = 37.sp, letterSpacing = (-0.5).sp))
        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.width(32.dp).height(1.dp).background(V.brass))
            Box(Modifier.size(6.dp).rotate(45f).background(V.amber))
            Box(Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(listOf(V.brass.copy(alpha = 0.6f), Color.Transparent))))
        }
        val intro = buildAnnotatedString {
            append("Speak, sing, or hum. Up to ${if (pro) "twenty minutes" else "three minutes free"}, kept on this device.")
            if (!pro) {
                append(" ")
                withStyle(SpanStyle(color = V.amberBright, textDecoration = TextDecoration.Underline)) { append("Twenty with Pro.") }
            }
        }
        Text(
            intro, style = sansStyle(14, V.muted).copy(lineHeight = 23.sp),
            modifier = Modifier.padding(top = 16.dp).then(if (!pro) Modifier.tap { Pro.openPaywall("Record for up to twenty minutes with Vynyl Pro.") } else Modifier),
        )

        // console
        Box(
            Modifier.padding(top = 28.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(Brush.verticalGradient(listOf(V.brass.copy(alpha = 0.6f), V.brass.copy(alpha = 0.15f), V.brass.copy(alpha = 0.4f))))
                .padding(1.dp),
        ) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(27.dp)).background(Brush.verticalGradient(listOf(V.consoleTop, V.consoleBottom)))) {
                Box(Modifier.matchParentSize().padding(8.dp).border(1.dp, V.cream.copy(alpha = 0.06f), RoundedCornerShape(22.dp)))
                Canvas(Modifier.align(Alignment.TopEnd).offset(x = 64.dp, y = (-64).dp).size(160.dp)) {
                    val r = size.minDimension / 2; val s = 5.dp.toPx(); var rr = s
                    while (rr < r) { drawCircle(V.cream.copy(alpha = 0.05f), rr, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx())); rr += s }
                }
                Column(Modifier.padding(20.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        val pulse by rememberInfiniteTransition(label = "p").animateFloat(1f, 0.5f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "a")
                        Box(
                            Modifier.size(8.dp).alpha(if (state == RecState.Rec) pulse else 1f).clip(CircleShape)
                                .background(when (state) { RecState.Rec -> V.err; RecState.Paused -> V.amber; else -> V.cream.copy(alpha = 0.2f) }),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when (state) { RecState.Rec -> "ON AIR"; RecState.Paused -> "PAUSED"; else -> "STANDBY" },
                            style = decoStyle(10, V.muted).copy(letterSpacing = 2.sp), modifier = Modifier.weight(1f),
                        )
                        Text("max ${fmt(max.toFloat())}", style = monoStyle(11))
                    }
                    Text(
                        fmt(t), style = monoStyle(48, V.cream, 500).copy(letterSpacing = (-1).sp),
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                    Box(
                        Modifier.padding(top = 16.dp).fillMaxWidth().height(64.dp).clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.25f)).border(1.dp, V.cream.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Wave(if (live.isNotEmpty()) live.map { minOf(1f, it * 2.5f) } else List(60) { 0.05f }, modifier = Modifier.fillMaxSize())
                    }
                    Row(Modifier.padding(top = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(24) { i ->
                            val lit = shownLevel * 24 > i
                            Box(
                                Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(1.dp)).background(
                                    if (lit) (if (i > 20) V.err else if (i > 15) V.amberBright else V.amber) else V.cream.copy(alpha = 0.07f),
                                ),
                            )
                        }
                    }
                    Text(
                        when {
                            state == RecState.Denied -> "Microphone access was declined — import a file instead."
                            shownLevel > 0.95f -> "Too loud — step back a little."
                            state == RecState.Rec && shownLevel < 0.02f && t > 2 -> "We can barely hear you."
                            state == RecState.Rec -> "Listening…"
                            state == RecState.Paused -> "Paused"
                            else -> ""
                        },
                        style = sansStyle(11, V.muted), textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth().height(16.dp),
                    )
                    Row(Modifier.padding(top = 20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                        if (state == RecState.Rec || state == RecState.Paused) {
                            Btn(if (state == RecState.Rec) "Pause" else "Resume", { pauseResume() }, variant = BtnVariant.Ghost)
                            StopButton(pinging = state == RecState.Rec, onClick = { finish() })
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                RecordButton { start() }
                                Text(if (source != null) "RECORD AGAIN" else "TAP TO RECORD", style = decoStyle(10, V.muted).copy(letterSpacing = 2.sp))
                            }
                        }
                    }
                }
            }
        }

        Row(Modifier.padding(top = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f).height(1.dp).background(V.brass.copy(alpha = 0.2f)))
            Text("OR", style = decoStyle(10, V.muted.copy(alpha = 0.7f)).copy(letterSpacing = 2.sp))
            Box(Modifier.weight(1f).height(1.dp).background(V.brass.copy(alpha = 0.2f)))
        }
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.weight(1f).heightIn(min = 48.dp).clip(CircleShape).background(V.panel.copy(alpha = 0.6f))
                    .border(1.dp, V.brass.copy(alpha = 0.4f), CircleShape).clickable { importer.launch("audio/*") },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("↥", style = sansStyle(14, V.amberBright))
                Spacer(Modifier.width(8.dp))
                Text("Import audio", style = sansStyle(14))
            }
            Btn("Use a lullaby", { setSource(demoVoice(), "Lullaby · built-in demo", true) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp), variant = BtnVariant.Ghost)
        }

        if (trimmed) {
            val s = buildAnnotatedString {
                append("This file was trimmed to ${fmt(max.toFloat())}.")
                if (!pro) { append(" "); withStyle(SpanStyle(color = V.amberBright, textDecoration = TextDecoration.Underline)) { append("Keep up to 20 min with Pro") } }
            }
            Text(
                s, style = sansStyle(11, V.muted), textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp).fillMaxWidth().then(if (!pro) Modifier.tap { Pro.openPaywall("Import and record up to twenty minutes with Vynyl Pro.") } else Modifier),
            )
        }
        if (source != null) SourceCard(source, srcName)
    }
}


@Composable
private fun RecordButton(onClick: () -> Unit) {
    Box(
        Modifier.size(80.dp).clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color(0xFFE0B85A), Color(0xFF7A4A12))))
            .clickable(onClick = onClick).padding(3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.fillMaxSize().clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xFFC0392B), Color(0xFF7F1D1D)))),
            contentAlignment = Alignment.Center,
        ) { Box(Modifier.size(24.dp).clip(CircleShape).background(V.cream)) }
    }
}

@Composable
private fun StopButton(pinging: Boolean, onClick: () -> Unit) {
    val ping = rememberInfiniteTransition(label = "ping")
    val k by ping.animateFloat(0f, 1f, infiniteRepeatable(tween(1000, easing = LinearOutSlowInEasing)), label = "k")
    Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) {
        if (pinging) Box(Modifier.fillMaxSize().scale(1f + k).alpha(1f - k).clip(CircleShape).background(V.ruby.copy(alpha = 0.3f)))
        Box(
            Modifier.fillMaxSize().clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0xFFC9A24A), Color(0xFF7A4A12))))
                .clickable(onClick = onClick).padding(3.dp),
        ) {
            Box(
                Modifier.fillMaxSize().clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFFC0392B), Color(0xFF7F1D1D)))),
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.size(20.dp).clip(RoundedCornerShape(3.dp)).background(V.cream)) }
        }
    }
}

@Composable
private fun SourceCard(source: FloatArray, srcName: String?) {
    val ctx = LocalContext.current
    var player by remember { mutableStateOf<WavPlayer?>(null) }
    LaunchedEffect(source) {
        player?.release(); player = null
        val f = withContext(Dispatchers.IO) {
            File(ctx.cacheDir, "capture-preview.wav").apply { writeBytes(encodeWav(Stereo(source, source))) }
        }
        player = WavPlayer(f)
    }
    DisposableEffect(Unit) { onDispose { player?.release() } }
    val p = player ?: return
    val playing by p.isPlaying.collectAsState()
    val pos by p.position.collectAsState()
    val dur = source.size / SR.toFloat()
    LaunchedEffect(playing) { if (!playing && pos >= dur - 0.05f) p.seek(0f) }

    Box(
        Modifier.padding(top = 20.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF2A231C), Color(0xFF17140F))))
            .border(1.dp, V.brass.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
    ) {
        Canvas(Modifier.align(Alignment.TopEnd).offset(x = 40.dp, y = (-40).dp).size(112.dp)) {
            val r = size.minDimension / 2; val s = 4.dp.toPx(); var rr = s
            while (rr < r) { drawCircle(V.cream.copy(alpha = 0.06f), rr, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx())); rr += s }
        }
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(Color(0xFFE0B85A), Color(0xFF8A5A1A))))
                        .clickable { if (playing) p.pause() else p.play() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (playing) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(2) { Box(Modifier.size(6.dp, 16.dp).clip(RoundedCornerShape(2.dp)).background(V.obsidian)) }
                    } else Canvas(Modifier.padding(start = 4.dp).size(13.dp, 16.dp)) {
                        drawPath(Path().apply { moveTo(0f, 0f); lineTo(size.width, size.height / 2); lineTo(0f, size.height); close() }, V.obsidian)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(V.ok))
                        Text("SOURCE CAPTURED", style = decoStyle(10, V.ok).copy(letterSpacing = 2.sp))
                    }
                    Text(srcName ?: "Your recording", style = displayStyle(18).copy(lineHeight = 22.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                }
            }
            Slider(
                value = pos.coerceIn(0f, dur), onValueChange = { p.seek(it) }, valueRange = 0f..dur.coerceAtLeast(0.01f),
                modifier = Modifier.padding(top = 12.dp).fillMaxWidth().height(24.dp),
                colors = SliderDefaults.colors(thumbColor = V.amberBright, activeTrackColor = V.amberBright, inactiveTrackColor = V.cream.copy(alpha = 0.12f)),
            )
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmt(pos), style = monoStyle(11))
                Text(fmt(dur), style = monoStyle(11))
            }
        }
    }
}
