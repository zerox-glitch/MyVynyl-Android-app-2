package com.vynyl.record.ui.studio

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vynyl.record.audio.CUSTOM_MAX_SECONDS
import com.vynyl.record.audio.CUSTOM_MIN_SECONDS
import com.vynyl.record.audio.SR
import com.vynyl.record.audio.Stereo
import com.vynyl.record.audio.WavPlayer
import com.vynyl.record.audio.encodeWav
import com.vynyl.record.audio.waveform
import com.vynyl.record.ui.components.fmt
import com.vynyl.record.ui.theme.V
import com.vynyl.record.ui.theme.decoStyle
import com.vynyl.record.ui.theme.displayStyle
import com.vynyl.record.ui.theme.monoStyle
import com.vynyl.record.ui.theme.sansStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * Trim sheet for uploaded background music: pick the part to use, hear it, then keep it.
 * Mirrors web src/components/MusicCrop.tsx.
 */
@Composable
internal fun MusicCropSheet(source: FloatArray, name: String, onCancel: () -> Unit, onSave: (start: Float, end: Float) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val dur = source.size / SR.toFloat()
    var start by remember { mutableFloatStateOf(0f) }
    var end by remember { mutableFloatStateOf(min(dur, 30f)) }
    var player by remember { mutableStateOf<WavPlayer?>(null) }
    val wave = remember(source) { waveform(source, 96) }
    val tooShort = dur < CUSTOM_MIN_SECONDS

    fun stop() { player?.release(); player = null }
    DisposableEffect(Unit) { onDispose { stop() } }
    BackHandler { stop(); onCancel() }

    fun play() {
        if (player != null) return stop()
        val a = (start * SR).toInt().coerceIn(0, source.size - 1)
        val b = (end * SR).toInt().coerceIn(a + 1, source.size)
        scope.launch {
            val f = withContext(Dispatchers.IO) {
                val part = source.copyOfRange(a, b)
                File(ctx.cacheDir, "music-crop-preview.wav").apply { writeBytes(encodeWav(Stereo(part, part))) }
            }
            val p = WavPlayer(f)
            player = p
            p.play()
        }
    }
    // keep the part between the shortest and longest allowed length
    fun moveStart(v: Float) {
        stop()
        val s = max(0f, min(v, dur - CUSTOM_MIN_SECONDS))
        start = s
        end = min(dur, max(s + CUSTOM_MIN_SECONDS, min(end, s + CUSTOM_MAX_SECONDS)))
    }
    fun moveEnd(v: Float) {
        stop()
        val e = min(dur, max(v, CUSTOM_MIN_SECONDS))
        end = e
        start = max(0f, max(e - CUSTOM_MAX_SECONDS, min(start, e - CUSTOM_MIN_SECONDS)))
    }

    val none = remember { MutableInteractionSource() }
    Box(Modifier.fillMaxSize().background(V.obsidian.copy(alpha = 0.8f)).clickable(none, null) {}, contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(V.stone)
                .navigationBarsPadding().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 24.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(40.dp, 4.dp).clip(CircleShape).background(V.cream.copy(alpha = 0.25f)))
            Text("YOUR OWN MUSIC", style = decoStyle(10, V.amberBright), modifier = Modifier.padding(top = 16.dp))
            Text("Choose the part to use", style = displayStyle(24), modifier = Modifier.padding(top = 4.dp))
            Text("$name · ${fmt(dur)}", style = sansStyle(14, V.muted), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))

            if (tooShort) {
                Text("This file is too short. Choose music that is at least ${CUSTOM_MIN_SECONDS.toInt()} seconds long.", style = sansStyle(14, V.err), modifier = Modifier.padding(top = 20.dp))
            } else {
                val p = player
                val pos = p?.position?.collectAsState()?.value ?: 0f
                val playing = p?.isPlaying?.collectAsState()?.value ?: false
                // the part finished playing by itself
                LaunchedEffect(p, playing) { if (p != null && !playing && p.position.value >= p.duration - 0.05f) stop() }
                Box(
                    Modifier.padding(top = 20.dp).fillMaxWidth().height(80.dp).clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.3f)).border(1.dp, V.cream.copy(alpha = 0.05f), RoundedCornerShape(12.dp)).padding(horizontal = 8.dp),
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val n = wave.size
                        val gap = 2.dp.toPx()
                        val bw = (size.width - gap * (n - 1)) / n
                        wave.forEachIndexed { i, v ->
                            val t = i.toFloat() / n * dur
                            val h = max(0.08f, v) * size.height
                            drawRoundRect(
                                if (t in start..end) V.amberBright else V.cream.copy(alpha = 0.15f),
                                Offset(i * (bw + gap), (size.height - h) / 2), Size(bw, h), CornerRadius(bw / 2),
                            )
                        }
                        if (p != null) {
                            val x = ((start + pos) / dur).coerceIn(0f, 1f) * size.width
                            drawLine(V.cream, Offset(x, 4.dp.toPx()), Offset(x, size.height - 4.dp.toPx()), 2.dp.toPx())
                        }
                    }
                }
                Text("Using ${fmt(start)} – ${fmt(end)} · ${(end - start).toInt()} seconds", style = monoStyle(12, V.amberBright), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))

                val sliderColors = SliderDefaults.colors(thumbColor = V.amberBright, activeTrackColor = V.amberBright, inactiveTrackColor = V.cream.copy(alpha = 0.12f))
                Row(Modifier.fillMaxWidth().padding(top = 16.dp)) { Text("Start", style = sansStyle(15), modifier = Modifier.weight(1f)); Text(fmt(start), style = monoStyle(13)) }
                Slider(start, { moveStart(it) }, valueRange = 0f..dur, colors = sliderColors, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth()) { Text("End", style = sansStyle(15), modifier = Modifier.weight(1f)); Text(fmt(end), style = monoStyle(13)) }
                Slider(end, { moveEnd(it) }, valueRange = 0f..dur, colors = sliderColors, modifier = Modifier.fillMaxWidth())
                Text(
                    "Up to ${CUSTOM_MAX_SECONDS.toInt()} seconds. It plays quietly under your voice and repeats if your message is longer.",
                    style = sansStyle(12, V.muted).copy(lineHeight = 18.sp), modifier = Modifier.padding(top = 4.dp),
                )
                Row(
                    Modifier.padding(top = 16.dp).fillMaxWidth().heightIn(min = 52.dp).clip(CircleShape)
                        .border(1.dp, V.amber.copy(alpha = 0.5f), CircleShape).background(V.amber.copy(alpha = 0.1f)).clickable { play() },
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (p != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { repeat(2) { Box(Modifier.size(5.dp, 16.dp).clip(RoundedCornerShape(2.dp)).background(V.cream)) } }
                        Spacer(Modifier.width(10.dp)); Text("Stop · ${fmt(pos)}", style = sansStyle(16))
                    } else {
                        Canvas(Modifier.size(12.dp, 14.dp)) { drawPath(Path().apply { moveTo(0f, 0f); lineTo(size.width, size.height / 2); lineTo(0f, size.height); close() }, V.amberBright) }
                        Spacer(Modifier.width(10.dp)); Text("Play this part", style = sansStyle(16))
                    }
                }
            }
            Row(Modifier.padding(top = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.weight(1f).heightIn(min = 52.dp).clip(CircleShape).border(1.dp, V.brass.copy(alpha = 0.4f), CircleShape).clickable { stop(); onCancel() },
                    contentAlignment = Alignment.Center,
                ) { Text("Cancel", style = sansStyle(16)) }
                Box(
                    Modifier.weight(1f).heightIn(min = 52.dp).alpha(if (tooShort) 0.4f else 1f).clip(CircleShape)
                        .background(Brush.verticalGradient(listOf(V.gold1, V.gold2, V.gold3)))
                        .clickable(enabled = !tooShort) { stop(); onSave(start, end) },
                    contentAlignment = Alignment.Center,
                ) { Text("Use this part", style = sansStyle(16, V.obsidian, 600)) }
            }
        }
    }
}
