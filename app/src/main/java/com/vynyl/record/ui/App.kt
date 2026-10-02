package com.vynyl.record.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vynyl.record.data.RecordMeta
import com.vynyl.record.data.RecordStore
import com.vynyl.record.pro.Pro
import com.vynyl.record.ui.paywall.Paywall
import com.vynyl.record.ui.player.Player
import com.vynyl.record.ui.studio.Studio
import com.vynyl.record.ui.components.Guide
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import com.vynyl.record.ui.theme.*
import com.vynyl.record.ui.vault.Vault

/** App shell — port of src/App.tsx (phone frame omitted; this is the phone). */
@Composable
fun VynylApp() {
    var tab by rememberSaveable { mutableStateOf("studio") }
    var guide by rememberSaveable { mutableStateOf(false) }
    var capture by remember { mutableIntStateOf(0) }
    var studioStep by remember { mutableIntStateOf(0) }
    val records by RecordStore.records.collectAsState()
    var playingId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val editing = editingId?.let { id -> records.firstOrNull { it.id == id } }
    val playing = playingId?.let { id -> records.firstOrNull { it.id == id } }
    val paywall by Pro.paywall.collectAsState()

    BackHandler(enabled = paywall != null) { Pro.closePaywall() }
    BackHandler(enabled = paywall == null && playing != null) { playingId = null }

    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier.fillMaxSize().background(V.obsidian).drawBehind {
            drawRect(Brush.radialGradient(listOf(V.amber.copy(.18f), Color.Transparent), Offset(size.width * .2f, size.height * .2f), size.width * .6f))
            drawRect(Brush.radialGradient(listOf(V.ruby.copy(.2f), Color.Transparent), Offset(size.width * .85f, size.height * .8f), size.width * .5f))
        },
    ) {
        Box(Modifier.weight(1f).fillMaxWidth().statusBarsPadding()) {
            // the Studio stays composed so a half-made record survives a trip to the vault
            Studio(
                recordCount = records.size, edit = editing, onCancelEdit = { editingId = null }, onDone = {}, onPlay = { r -> playingId = r.id },
                capture = capture, active = tab == "studio" && playing == null && !guide, onStep = { studioStep = it },
            )
            if (tab == "vault") Box(Modifier.fillMaxSize().background(V.obsidian).clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) {}) {
                Vault(records = records, onPlay = { playingId = it.id }, onNew = { tab = "studio"; capture++ })
            }
            playing?.let { Player(record = it, onClose = { playingId = null }, onEdit = { r -> playingId = null; editingId = r.id; tab = "studio" }) }
        }
        Row(
            Modifier.fillMaxWidth().background(V.stone)
                .drawBehind { drawRect(V.brass.copy(.2f), size = Size(size.width, 1f)) }
                .navigationBarsPadding().heightIn(min = 68.dp).padding(top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            listOf("record" to "Record", "studio" to "Studio", "vault" to "Master Vault", "guide" to "Guide").forEach { (id, label) ->
                val here = tab == "studio" && playing == null && !guide
                val on = when (id) { "guide" -> guide; "record" -> here && studioStep == 0; "studio" -> here && studioStep > 0; else -> tab == id && playing == null && !guide }
                val c = if (on) V.amberBright else V.cream.copy(alpha = 0.7f)
                Column(
                    Modifier.weight(1f).heightIn(min = 52.dp).clickable {
                        if (id == "guide") guide = true
                        else { guide = false; playingId = null; tab = if (id == "vault") "vault" else "studio"; if (id == "record") capture++ }
                    },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    NavIcon(id, on, c)
                    Text(label, style = sansStyle(12, c, 500))
                }
            }
        }
        if (guide) Guide { guide = false }
    }
    Paywall()
    }
}

/** Bottom-bar icons on the same 24-unit grid as the web NavIcon. */
@Composable
private fun NavIcon(id: String, on: Boolean, c: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(24.dp)) {
        val k = size.minDimension / 24f
        val st = androidx.compose.ui.graphics.drawscope.Stroke(1.7f * k, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        fun o(x: Float, y: Float) = Offset(x * k, y * k)
        fun ln(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(c, o(x1, y1), o(x2, y2), 1.7f * k, androidx.compose.ui.graphics.StrokeCap.Round)
        fun box(x: Float, y: Float, w: Float, h: Float, r: Float, fill: Boolean) =
            if (fill) drawRoundRect(c, o(x, y), Size(w * k, h * k), androidx.compose.ui.geometry.CornerRadius(r * k))
            else drawRoundRect(c, o(x, y), Size(w * k, h * k), androidx.compose.ui.geometry.CornerRadius(r * k), style = st)
        when (id) {
            "record" -> {
                box(9f, 3f, 6f, 11f, 3f, on); if (on) box(9f, 3f, 6f, 11f, 3f, false)
                drawArc(c, 0f, 180f, false, o(5.5f, 4.5f), Size(13 * k, 13 * k), style = st)
                ln(12f, 17.5f, 12f, 21f); ln(8.5f, 21f, 15.5f, 21f)
            }
            "studio" -> {
                drawCircle(c, 8 * k, o(11f, 13f), style = st)
                if (on) drawCircle(c, 2.6f * k, o(11f, 13f)) else drawCircle(c, 2.6f * k, o(11f, 13f), style = st)
                ln(19.5f, 3.5f, 15f, 12f); drawCircle(c, 1.2f * k, o(19.5f, 3.5f))
            }
            "vault" -> {
                ln(3f, 20f, 21f, 20f); ln(3f, 12f, 21f, 12f)
                box(4.5f, 4f, 3f, 8f, 0.6f, false); box(8.5f, 4f, 3f, 8f, 0.6f, on); if (on) box(8.5f, 4f, 3f, 8f, 0.6f, false); box(12.5f, 5.5f, 3f, 6.5f, 0.6f, false)
                drawCircle(c, 3.2f * k, o(16f, 16f), style = st); drawCircle(c, 0.9f * k, o(16f, 16f))
            }
            else -> {
                if (on) drawCircle(c.copy(alpha = 0.15f), 9 * k, o(12f, 12f))
                drawCircle(c, 9 * k, o(12f, 12f), style = st)
                drawArc(c, 160f, 250f, false, o(9.5f, 6.8f), Size(5 * k, 5 * k), style = st)
                ln(12f, 12f, 12f, 13.6f); drawCircle(c, 0.8f * k, o(12f, 17f))
            }
        }
    }
}
