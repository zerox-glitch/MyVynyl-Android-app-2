package com.vynyl.record.ui.vault

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vynyl.record.audio.MOODS
import com.vynyl.record.audio.PRESETS
import com.vynyl.record.audio.SAMPLES
import com.vynyl.record.audio.Sample
import com.vynyl.record.pro.Gate
import com.vynyl.record.pro.isFree
import com.vynyl.record.ui.paywall.ProBadge
import com.vynyl.record.audio.STYLES
import com.vynyl.record.data.RecordMeta
import com.vynyl.record.data.RecordStore
import com.vynyl.record.pro.Free
import com.vynyl.record.pro.Pro
import com.vynyl.record.ui.components.*
import com.vynyl.record.ui.paywall.ProButton
import com.vynyl.record.ui.theme.*
import kotlinx.coroutines.launch

import androidx.compose.ui.platform.LocalContext
import com.vynyl.record.audio.Stereo
import com.vynyl.record.audio.WavPlayer
import com.vynyl.record.audio.encodeWav
import com.vynyl.record.data.VaultVoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Sort(val label: String) { Newest("Newest"), Oldest("Oldest"), Title("Title"), Duration("Longest") }

@Composable
fun Vault(
    records: List<RecordMeta>,
    onPlay: (RecordMeta) -> Unit,
    onNew: () -> Unit,
    onUseVoice: (VaultVoice) -> Unit = {},
    /** "records", "voices" or "samples" — owned by the app so onboarding can open the samples */
    tab: String = "records",
    onTab: (String) -> Unit = {},
    /** plays a mood's example record on the turntable */
    onPlaySample: (String) -> Unit = {},
    /** starts a new record with this mood already chosen */
    onUseMood: (String) -> Unit = {},
    /** mood whose example record is being pressed right now */
    sampleLoading: String? = null,
) {
    val vaultTab = tab
    val vaultVoices by RecordStore.vaultVoices.collectAsState()
    var q by remember { mutableStateOf("") }
    var grid by remember { mutableStateOf(true) }
    var fav by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(Sort.Newest) }
    var sortOpen by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<RecordMeta?>(null) }
    var confirmVoice by remember { mutableStateOf<VaultVoice?>(null) }
    val pro = Pro.entitlement.collectAsState().value.pro
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    var playingVoiceId by remember { mutableStateOf<String?>(null) }
    var voicePlayer by remember { mutableStateOf<WavPlayer?>(null) }

    fun stopVoicePlayback() {
        voicePlayer?.pause()
        voicePlayer?.release()
        voicePlayer = null
        playingVoiceId = null
    }

    fun toggleVoicePlayback(v: VaultVoice) {
        if (playingVoiceId == v.id) {
            stopVoicePlayback()
        } else {
            stopVoicePlayback()
            playingVoiceId = v.id
            scope.launch {
                val samples = RecordStore.loadVaultVoice(v.id) ?: return@launch
                val f = withContext(Dispatchers.IO) {
                    File(ctx.cacheDir, "vault-voice-preview.wav").apply {
                        writeBytes(encodeWav(Stereo(samples, samples)))
                    }
                }
                val wp = WavPlayer(f)
                voicePlayer = wp
                wp.play()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { stopVoicePlayback() }
    }

    val list = remember(records, q, fav, sort) {
        val s = q.lowercase()
        records.filter { r -> (!fav || r.favorite) && listOf(r.title, r.recipient, r.occasion).any { it.lowercase().contains(s) } }
            .let { l -> when (sort) { Sort.Newest -> l.sortedByDescending { it.createdAt }; Sort.Oldest -> l.sortedBy { it.createdAt }; Sort.Title -> l.sortedBy { it.title.lowercase() }; Sort.Duration -> l.sortedByDescending { it.duration } } }
    }

    val voiceList = remember(vaultVoices, q) {
        val s = q.lowercase()
        vaultVoices.filter { v -> v.name.lowercase().contains(s) || (v.recordTitle?.lowercase()?.contains(s) == true) }
            .sortedByDescending { it.createdAt }
    }

    fun toggleFav(r: RecordMeta) = scope.launch { RecordStore.update(r.id) { it.copy(favorite = !it.favorite) } }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Eyebrow(when (vaultTab) { "records" -> "MASTER VAULT · ${records.size} PRESSED"; "voices" -> "MASTER VAULT · ${vaultVoices.size} VOICES SAVED"; else -> "MASTER VAULT · ${SAMPLES.size} SAMPLES" })
                    ProButton()
                }
                Text("Your shelf", Modifier.padding(top = 4.dp), style = displayStyle(36))

                // Switcher between Pressed Records and Saved Voices
                Row(
                    Modifier.padding(top = 12.dp).fillMaxWidth().border(1.dp, V.brass.copy(.25f), CircleShape).padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf("records" to "Records (${records.size})", "voices" to "Voices (${vaultVoices.size})", "samples" to "Samples").forEach { (tid, label) ->
                        val on = vaultTab == tid
                        Box(
                            Modifier.weight(1f).clip(CircleShape)
                                .background(if (on) V.amber.copy(.25f) else Color.Transparent)
                                .clickable {
                                    stopVoicePlayback()
                                    onTab(tid)
                                }
                                .heightIn(min = 40.dp).padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(label, style = sansStyle(13, if (on) V.cream else V.muted, if (on) 600 else 400), maxLines = 1)
                        }
                    }
                }

                if (!pro && vaultTab != "samples") {
                    val sh = RoundedCornerShape(12.dp)
                    if (vaultTab == "records") {
                        Column(Modifier.padding(top = 12.dp).fillMaxWidth().clip(sh).background(V.panel.copy(.7f)).border(1.dp, V.brass.copy(.25f), sh)
                            .clickable { Pro.openPaywall("Free shelves hold three records. Go Pro to keep every voice you press.") }.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${minOf(records.size, Free.MAX_RECORDS)} of ${Free.MAX_RECORDS} free shelf slots used", style = sansStyle(11, V.cream.copy(.85f)))
                                Text("UNLIMITED WITH PRO ›", style = decoStyle(10, V.amberBright))
                            }
                            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                repeat(Free.MAX_RECORDS) { i -> Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(if (i < records.size) V.amberBright else V.cream.copy(.1f))) }
                            }
                        }
                    } else {
                        Column(Modifier.padding(top = 12.dp).fillMaxWidth().clip(sh).background(V.panel.copy(.7f)).border(1.dp, V.brass.copy(.25f), sh)
                            .clickable { Pro.openPaywall("Free accounts can save up to two voices in the master vault. Go Pro to save unlimited voices.") }.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${minOf(vaultVoices.size, Free.MAX_VOICES)} of ${Free.MAX_VOICES} free voice slots used", style = sansStyle(11, V.cream.copy(.85f)))
                                Text("UNLIMITED WITH PRO ›", style = decoStyle(10, V.amberBright))
                            }
                            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                repeat(Free.MAX_VOICES) { i -> Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(if (i < vaultVoices.size) V.amberBright else V.cream.copy(.1f))) }
                            }
                        }
                    }
                }

                BasicTextField(q, { q = it }, Modifier.padding(top = 16.dp).fillMaxWidth().clip(CircleShape).background(V.panel).border(1.dp, V.brass.copy(.25f), CircleShape).padding(horizontal = 16.dp, vertical = 10.dp),
                    textStyle = sansStyle(14), singleLine = true, cursorBrush = SolidColor(V.amberBright),
                    decorationBox = { inner -> Box { if (q.isEmpty()) Text(when (vaultTab) { "records" -> "Search titles, people, occasions"; "voices" -> "Search saved voices"; else -> "Search moods and messages" }, style = sansStyle(14, V.muted.copy(.6f))); inner() } })

                if (vaultTab == "records") {
                    Row(Modifier.padding(top = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("♥ Favorites", fav) { fav = !fav }
                        Box {
                            Chip(sort.label + " ▾", false) { sortOpen = true }
                            DropdownMenu(sortOpen, { sortOpen = false }, Modifier.background(V.panel)) {
                                Sort.entries.forEach { s -> DropdownMenuItem({ Text(s.label, style = sansStyle(13, if (s == sort) V.cream else V.muted)) }, { sort = s; sortOpen = false }) }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Row(Modifier.border(1.dp, V.brass.copy(.25f), CircleShape).padding(2.dp)) {
                            listOf(true to "Grid", false to "List").forEach { (g, l) ->
                                Text(l, Modifier.clip(CircleShape).background(if (grid == g) V.amber.copy(.2f) else Color.Transparent).clickable { grid = g }.padding(horizontal = 12.dp, vertical = 4.dp),
                                    style = sansStyle(12, if (grid == g) V.cream else V.muted))
                            }
                        }
                    }
                }
            }

            if (vaultTab == "records") {
                if (records.isEmpty()) {
                    Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Canvas(Modifier.size(128.dp)) {
                            var r = size.minDimension / 2
                            while (r > 0) { drawCircle(V.brass.copy(.12f), r - 7.dp.toPx(), style = Stroke(1.dp.toPx())); r -= 8.dp.toPx() }
                            drawCircle(V.brass.copy(.4f), size.minDimension / 2, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))))
                        }
                        Text("Your shelf is waiting for its first voice.", Modifier.widthIn(max = 224.dp).padding(top = 24.dp), style = displayStyle(24).copy(lineHeight = androidx.compose.ui.unit.TextUnit(32f, androidx.compose.ui.unit.TextUnitType.Sp)), textAlign = TextAlign.Center)
                        Btn("Press a record", onNew, Modifier.padding(top = 24.dp))
                        Btn("Hear a sample record first", { onTab("samples") }, Modifier.padding(top = 12.dp), BtnVariant.Ghost)
                    }
                } else if (grid) {
                    LazyVerticalGrid(GridCells.Fixed(2), Modifier.weight(1f), contentPadding = PaddingValues(24.dp, 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        items(list, key = { it.id }) { r ->
                            val pr = PRESETS.firstOrNull { it.id == r.presetId } ?: PRESETS[0]
                            Column {
                                Sleeve(r, Modifier.fillMaxWidth().clickable { onPlay(r) })
                                Row(Modifier.padding(top = 8.dp).fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                    Column(Modifier.weight(1f)) {
                                        Text(r.title, style = sansStyle(14, V.cream, 600), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("for ${r.recipient} · ${r.occasion}", style = sansStyle(12, V.muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Text("♥", Modifier.clickable { toggleFav(r) }.padding(4.dp), style = sansStyle(14, if (r.favorite) V.amberBright else V.muted.copy(.5f)))
                                }
                                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(pr.name, Modifier.border(1.dp, V.brass.copy(.3f), CircleShape).padding(horizontal = 8.dp, vertical = 2.dp), style = sansStyle(10, V.muted))
                                    Text(fmt(r.duration), style = monoStyle(10))
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(24.dp, 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(list, key = { it.id }) { r ->
                            val pr = PRESETS.firstOrNull { it.id == r.presetId } ?: PRESETS[0]
                            val sh = RoundedCornerShape(12.dp)
                            Row(Modifier.fillMaxWidth().clip(sh).background(V.panel).border(1.dp, V.brass.copy(.15f), sh).padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 12.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Sleeve(r, Modifier.width(56.dp).clickable { onPlay(r) }, small = true)
                                Column(Modifier.weight(1f)) {
                                    Text(r.title, style = sansStyle(14, V.cream, 600), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${r.recipient} · ${pr.name} · ${fmt(r.duration)}", style = sansStyle(12, V.muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text("♥", Modifier.clickable { toggleFav(r) }, style = sansStyle(14, if (r.favorite) V.amberBright else V.muted.copy(.5f)))
                                Text("Delete", Modifier.clickable { confirm = r }, style = sansStyle(12, V.muted))
                            }
                        }
                    }
                }
            } else if (vaultTab == "samples") {
                val sampleList = remember(q) {
                    val s = q.lowercase()
                    SAMPLES.filter { x -> listOf(MOODS.firstOrNull { it.id == x.moodId }?.name ?: "", x.persona, x.script, x.occasion).any { it.lowercase().contains(s) } }
                }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(24.dp, 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Text(
                            "Not sure which mood to choose? Listen to example messages — from a grandfather, a boyfriend, a mother and more — each pressed with a different mood. Tap one to play it on the turntable.",
                            style = sansStyle(14, V.muted).copy(lineHeight = androidx.compose.ui.unit.TextUnit(21f, androidx.compose.ui.unit.TextUnitType.Sp)),
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    items(sampleList, key = { it.moodId }) { x -> SampleCard(x, pro, sampleLoading, onPlaySample, onUseMood) }
                    item {
                        Text(
                            "Example voices are computer-generated from open voice libraries (LibriVox, LibriTTS-R CC BY 4.0, Piper).",
                            style = sansStyle(10, V.muted.copy(.7f)), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        )
                    }
                }
            } else {
                // Saved Voices tab
                if (vaultVoices.isEmpty()) {
                    Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Canvas(Modifier.size(112.dp)) {
                            drawCircle(V.brass.copy(.35f), size.minDimension / 2, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))))
                            drawCircle(V.amber.copy(.15f), size.minDimension / 3)
                        }
                        Text("No voices in your vault yet.", Modifier.padding(top = 20.dp), style = displayStyle(24), textAlign = TextAlign.Center)
                        Text(
                            "When you record a voice to make a record, it will also be saved here so you can reuse it to press new records anytime.",
                            Modifier.widthIn(max = 280.dp).padding(top = 10.dp),
                            style = sansStyle(14, V.muted).copy(lineHeight = androidx.compose.ui.unit.TextUnit(20f, androidx.compose.ui.unit.TextUnitType.Sp)),
                            textAlign = TextAlign.Center
                        )
                        Btn("Record a voice", onNew, Modifier.padding(top = 24.dp))
                    }
                } else {
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(24.dp, 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(voiceList, key = { it.id }) { v ->
                            val isPlaying = playingVoiceId == v.id
                            val sh = RoundedCornerShape(14.dp)
                            Row(
                                Modifier.fillMaxWidth().clip(sh).background(V.panel).border(1.dp, V.brass.copy(.2f), sh).padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    Modifier.size(44.dp).clip(CircleShape)
                                        .background(if (isPlaying) SolidColor(V.amberBright) else Brush.verticalGradient(listOf(Color(0xFFE0B85A), Color(0xFF8A5A1A))))
                                        .clickable { toggleVoicePlayback(v) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (isPlaying) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                            repeat(2) { Box(Modifier.size(4.dp, 14.dp).clip(RoundedCornerShape(1.dp)).background(V.obsidian)) }
                                        }
                                    } else {
                                        Canvas(Modifier.padding(start = 3.dp).size(12.dp, 14.dp)) {
                                            val p = androidx.compose.ui.graphics.Path().apply {
                                                moveTo(0f, 0f)
                                                lineTo(size.width, size.height / 2)
                                                lineTo(0f, size.height)
                                                close()
                                            }
                                            drawPath(p, V.obsidian)
                                        }
                                    }
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(v.name, style = sansStyle(14, V.cream, 600), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(fmt(v.duration), style = monoStyle(11, V.amberBright))
                                        if (v.recordTitle != null) {
                                            Text("·", style = sansStyle(11, V.muted))
                                            Text("Record: ${v.recordTitle}", style = sansStyle(11, V.muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                    val dateStr = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(v.createdAt))
                                    Text(dateStr, style = sansStyle(10, V.muted.copy(0.7f)), modifier = Modifier.padding(top = 1.dp))
                                }
                                Box(
                                    Modifier.clip(CircleShape).background(V.amber.copy(0.2f)).border(1.dp, V.amber.copy(0.5f), CircleShape)
                                        .clickable {
                                            stopVoicePlayback()
                                            onUseVoice(v)
                                        }.padding(horizontal = 12.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("Make record", style = sansStyle(11, V.amberBright, 600))
                                }
                                Text("✕", Modifier.clip(CircleShape).clickable { confirmVoice = v }.padding(6.dp), style = sansStyle(14, V.muted))
                            }
                        }
                    }
                }
            }
        }

        confirm?.let { c ->
            Box(Modifier.fillMaxSize().background(V.obsidian.copy(.7f)).clickable(remember { MutableInteractionSource() }, null) { confirm = null }, contentAlignment = Alignment.BottomCenter) {
                val sh = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                Column(Modifier.fillMaxWidth().clip(sh).background(V.stone).drawBehind { drawRect(V.brass.copy(.3f), size = androidx.compose.ui.geometry.Size(size.width, 1f)) }
                    .clickable(remember { MutableInteractionSource() }, null) {}.padding(24.dp)) {
                    Text("Delete “${c.title}”?", style = displayStyle(24))
                    Text("The master and its artwork will be removed from this device. This can’t be undone without a backup.", Modifier.padding(top = 8.dp), style = sansStyle(14, V.muted))
                    Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Btn("Keep it", { confirm = null }, Modifier.weight(1f), BtnVariant.Ghost)
                        Box(Modifier.weight(1f).heightIn(min = 44.dp).clip(CircleShape).background(V.err).clickable { scope.launch { RecordStore.delete(c.id); confirm = null } }, contentAlignment = Alignment.Center) {
                            Text("Delete", style = sansStyle(14, V.obsidian, 600))
                        }
                    }
                }
            }
        }

        confirmVoice?.let { cv ->
            Box(Modifier.fillMaxSize().background(V.obsidian.copy(.7f)).clickable(remember { MutableInteractionSource() }, null) { confirmVoice = null }, contentAlignment = Alignment.BottomCenter) {
                val sh = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                Column(Modifier.fillMaxWidth().clip(sh).background(V.stone).drawBehind { drawRect(V.brass.copy(.3f), size = androidx.compose.ui.geometry.Size(size.width, 1f)) }
                    .clickable(remember { MutableInteractionSource() }, null) {}.padding(24.dp)) {
                    Text("Delete voice “${cv.name}”?", style = displayStyle(24))
                    Text("This voice will be removed from your Master Vault. Any existing pressed records will keep their master audio, but you won’t be able to reuse this voice for new records.", Modifier.padding(top = 8.dp), style = sansStyle(14, V.muted))
                    Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Btn("Keep it", { confirmVoice = null }, Modifier.weight(1f), BtnVariant.Ghost)
                        Box(Modifier.weight(1f).heightIn(min = 44.dp).clip(CircleShape).background(V.err).clickable {
                            scope.launch {
                                if (playingVoiceId == cv.id) stopVoicePlayback()
                                RecordStore.deleteVaultVoice(cv.id)
                                confirmVoice = null
                            }
                        }, contentAlignment = Alignment.Center) {
                            Text("Delete", style = sansStyle(14, V.obsidian, 600))
                        }
                    }
                }
            }
        }
    }
}

/** One example record on the Samples shelf: sleeve, who it's from, the words, and two big buttons. */
@Composable
private fun SampleCard(x: Sample, pro: Boolean, sampleLoading: String?, onPlay: (String) -> Unit, onUseMood: (String) -> Unit) {
    val m = MOODS.first { it.id == x.moodId }
    val st = STYLES.firstOrNull { it.id == x.styleId } ?: STYLES[0]
    val loading = sampleLoading == x.moodId
    val busy = sampleLoading != null
    val sh = RoundedCornerShape(16.dp)
    Column(Modifier.fillMaxWidth().clip(sh).background(V.panel).border(1.dp, V.brass.copy(.2f), sh).padding(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.width(64.dp).clickable(enabled = !busy) { onPlay(x.moodId) }) {
                SleeveArt(st, Modifier.fillMaxWidth())
                if (loading) Box(Modifier.matchParentSize().background(V.obsidian.copy(.6f)), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), color = V.amberBright, strokeWidth = 2.dp)
                }
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(m.name, style = displayStyle(18), modifier = Modifier.weight(1f, fill = false))
                    if (!pro && !isFree(Gate.Mood, x.moodId)) ProBadge()
                }
                Text("A ${x.persona.lowercase()}'s message for ${x.to}", style = sansStyle(13, V.muted), modifier = Modifier.padding(top = 2.dp))
                Text("“${x.script}”", style = sansStyle(12, V.cream.copy(.6f)).copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Row(Modifier.padding(top = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.weight(1f).heightIn(min = 44.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(V.gold1, V.gold2, V.gold3)))
                    .clickable(enabled = !busy) { onPlay(x.moodId) },
                contentAlignment = Alignment.Center,
            ) { Text(if (loading) "Getting it ready…" else "▶ Play it", style = sansStyle(14, V.obsidian, 600)) }
            Box(
                Modifier.weight(1f).heightIn(min = 44.dp).clip(CircleShape).border(1.dp, V.amber.copy(.5f), CircleShape).clickable { onUseMood(x.moodId) },
                contentAlignment = Alignment.Center,
            ) { Text("Use this mood", style = sansStyle(14, V.amberBright)) }
        }
    }
}

/** Square sleeve art (disc peeking out) for a wax style, without a title. */
@Composable
private fun SleeveArt(st: com.vynyl.record.audio.VinylStyle, modifier: Modifier) {
    val label = Color(st.label); val disc = Color(st.disc)
    val sh = RoundedCornerShape(6.dp)
    Box(modifier.aspectRatio(1f).shadow(8.dp, sh).clip(sh).background(Brush.linearGradient(listOf(label, V.obsidian), Offset.Zero, Offset.Infinite))) {
        Canvas(Modifier.fillMaxSize()) {
            val d = size.height * .84f; val r0 = d / 2
            val c = Offset(size.width * 1.25f - r0, size.height * .08f + r0)
            drawCircle(disc, r0, c)
            var rr = r0
            while (rr > 0) { drawCircle(Color(0x55000000), rr - 3.dp.toPx(), c, style = Stroke(1.dp.toPx())); rr -= 4.dp.toPx() }
            drawCircle(label, r0 * .32f, c)
        }
    }
}

@Composable
private fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    Text(text, Modifier.clip(CircleShape).background(V.panel).border(1.dp, if (on) V.amber else V.brass.copy(.25f), CircleShape).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        style = sansStyle(12, if (on) V.cream else V.muted))
}

/** Square sleeve with a disc peeking out, same as the web Vault. */
@Composable
private fun Sleeve(r: RecordMeta, modifier: Modifier, small: Boolean = false) {
    val st = STYLES.firstOrNull { it.id == r.styleId } ?: STYLES[0]
    val label = Color(st.label); val disc = Color(st.disc); val ink = Color(st.ink)
    val sh = RoundedCornerShape(6.dp)
    Box(modifier.aspectRatio(1f).shadow(10.dp, sh).clip(sh).background(Brush.linearGradient(listOf(label, V.obsidian), Offset.Zero, Offset.Infinite))) {
        Canvas(Modifier.fillMaxSize()) {
            val d = size.height * .84f; val r0 = d / 2
            val c = Offset(size.width * 1.25f - r0, size.height * .08f + r0)
            drawCircle(disc, r0, c)
            var rr = r0
            while (rr > 0) { drawCircle(Color(0x55000000), rr - 3.dp.toPx(), c, style = Stroke(1.dp.toPx())); rr -= 4.dp.toPx() }
            drawCircle(label, r0 * .32f, c)
        }
        if (!small) Text(r.title, Modifier.align(Alignment.BottomStart).padding(8.dp).fillMaxWidth(.62f), style = displayStyle(14).copy(lineHeight = androidx.compose.ui.unit.TextUnit(17f, androidx.compose.ui.unit.TextUnitType.Sp)),
            color = if (st.ink == 0xFF0C0A09L) V.cream else ink)
    }
}
