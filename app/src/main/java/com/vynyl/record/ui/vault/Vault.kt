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
import com.vynyl.record.audio.PRESETS
import com.vynyl.record.audio.STYLES
import com.vynyl.record.data.RecordMeta
import com.vynyl.record.data.RecordStore
import com.vynyl.record.pro.Free
import com.vynyl.record.pro.Pro
import com.vynyl.record.ui.components.*
import com.vynyl.record.ui.paywall.ProButton
import com.vynyl.record.ui.theme.*
import kotlinx.coroutines.launch

private enum class Sort(val label: String) { Newest("Newest"), Oldest("Oldest"), Title("Title"), Duration("Longest") }

@Composable
fun Vault(records: List<RecordMeta>, onPlay: (RecordMeta) -> Unit, onNew: () -> Unit) {
    var q by remember { mutableStateOf("") }
    var grid by remember { mutableStateOf(true) }
    var fav by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(Sort.Newest) }
    var sortOpen by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<RecordMeta?>(null) }
    val pro = Pro.entitlement.collectAsState().value.pro
    val scope = rememberCoroutineScope()

    val list = remember(records, q, fav, sort) {
        val s = q.lowercase()
        records.filter { r -> (!fav || r.favorite) && listOf(r.title, r.recipient, r.occasion).any { it.lowercase().contains(s) } }
            .let { l -> when (sort) { Sort.Newest -> l.sortedByDescending { it.createdAt }; Sort.Oldest -> l.sortedBy { it.createdAt }; Sort.Title -> l.sortedBy { it.title.lowercase() }; Sort.Duration -> l.sortedByDescending { it.duration } } }
    }
    fun toggleFav(r: RecordMeta) = scope.launch { RecordStore.update(r.id) { it.copy(favorite = !it.favorite) } }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Eyebrow("MASTER VAULT · ${records.size} PRESSED"); ProButton()
                }
                Text("Your shelf", Modifier.padding(top = 4.dp), style = displayStyle(36))
                if (!pro) {
                    val sh = RoundedCornerShape(12.dp)
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
                }
                BasicTextField(q, { q = it }, Modifier.padding(top = 16.dp).fillMaxWidth().clip(CircleShape).background(V.panel).border(1.dp, V.brass.copy(.25f), CircleShape).padding(horizontal = 16.dp, vertical = 10.dp),
                    textStyle = sansStyle(14), singleLine = true, cursorBrush = SolidColor(V.amberBright),
                    decorationBox = { inner -> Box { if (q.isEmpty()) Text("Search titles, people, occasions", style = sansStyle(14, V.muted.copy(.6f))); inner() } })
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
            if (records.isEmpty()) {
                Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Canvas(Modifier.size(128.dp)) {
                        var r = size.minDimension / 2
                        while (r > 0) { drawCircle(V.brass.copy(.12f), r - 7.dp.toPx(), style = Stroke(1.dp.toPx())); r -= 8.dp.toPx() }
                        drawCircle(V.brass.copy(.4f), size.minDimension / 2, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))))
                    }
                    Text("Your shelf is waiting for its first voice.", Modifier.widthIn(max = 224.dp).padding(top = 24.dp), style = displayStyle(24).copy(lineHeight = androidx.compose.ui.unit.TextUnit(32f, androidx.compose.ui.unit.TextUnitType.Sp)), textAlign = TextAlign.Center)
                    Btn("Press a record", onNew, Modifier.padding(top = 24.dp))
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
