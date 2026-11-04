package com.vynyl.record.ui.studio

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vynyl.record.audio.OCCASIONS
import com.vynyl.record.pro.Pro
import com.vynyl.record.ui.paywall.ProBadge
import com.vynyl.record.ui.theme.*
import kotlin.math.roundToInt

/** Dedication fields, mirrors the `meta` object in Studio.tsx. */
data class StudioMeta(
    val title: String = "The Porch Song",
    val recipient: String = "Nana Ruth",
    val sender: String = "Theo",
    val dedication: String = "For every summer evening you hummed this to me.",
    val occasion: String = "Grandparents",
    val date: String = java.time.LocalDate.now().toString(),
    val sideA: String = "Side A",
    val sideB: String = "Side B",
)

/** "Text <em>accent</em> rest" headline used throughout the studio. */
internal fun headline(before: String, em: String, after: String = ""): AnnotatedString = buildAnnotatedString {
    append(before)
    withStyle(SpanStyle(color = V.amberBright, fontStyle = FontStyle.Italic)) { append(em) }
    append(after)
}

/** Extends a child past the parent's horizontal padding (Tailwind `-mx-6`). */
internal fun Modifier.bleed(d: Dp): Modifier = layout { m, c ->
    val px = d.roundToPx()
    val maxW = if (c.hasBoundedWidth) c.maxWidth + 2 * px else c.maxWidth
    val p = m.measure(c.copy(minWidth = maxW, maxWidth = maxW))
    val w = if (c.hasBoundedWidth) c.maxWidth else p.width
    layout(w, p.height) { p.place(-px, 0) }
}

internal fun Modifier.tap(onClick: () -> Unit): Modifier = composed {
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
}

@Composable
internal fun Dedication(meta: StudioMeta, setMeta: (StudioMeta) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(headline("Who is this ", "voice", " for?"), style = displayStyle(34).copy(letterSpacing = (-0.5).sp))
        Field("Memory title", meta.title, "The night we met") { setMeta(meta.copy(title = it)) }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Field("For", meta.recipient, modifier = Modifier.weight(1f)) { setMeta(meta.copy(recipient = it)) }
            Field("From", meta.sender, modifier = Modifier.weight(1f)) { setMeta(meta.copy(sender = it)) }
        }
        Column {
            Text("OCCASION", style = decoStyle(10, V.muted))
            Spacer(Modifier.height(8.dp))
            OccasionChips(meta.occasion) { setMeta(meta.copy(occasion = it)) }
        }
        Column {
            Text("DEDICATION", style = decoStyle(10, V.muted))
            Spacer(Modifier.height(8.dp))
            var focused by remember { mutableStateOf(false) }
            BasicTextField(
                value = meta.dedication, onValueChange = { setMeta(meta.copy(dedication = it)) },
                textStyle = displayStyle(18).copy(fontStyle = FontStyle.Italic, lineHeight = 24.sp),
                cursorBrush = SolidColor(V.amberBright), minLines = 3, maxLines = 3,
                modifier = Modifier.fillMaxWidth()
                    .onFocusChangedCompat { focused = it }
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF26211C), Color(0xFF1A1714))))
                    .border(1.dp, if (focused) V.amberBright else V.brass.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                    .padding(16.dp),
                decorationBox = { inner ->
                    Box {
                        if (meta.dedication.isEmpty()) Text("What do you want them to remember?", style = displayStyle(18, V.muted.copy(alpha = 0.5f)).copy(fontStyle = FontStyle.Italic))
                        inner()
                    }
                },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Field("Date", meta.date, modifier = Modifier.weight(1f)) { setMeta(meta.copy(date = it)) }
            Field("Side A", meta.sideA, modifier = Modifier.weight(1f)) { setMeta(meta.copy(sideA = it)) }
            Field("Side B", meta.sideB, modifier = Modifier.weight(1f)) { setMeta(meta.copy(sideB = it)) }
        }
    }
}

internal fun Modifier.onFocusChangedCompat(f: (Boolean) -> Unit): Modifier = this.onFocusChanged { f(it.isFocused) }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OccasionChips(selected: String, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OCCASIONS.forEach { o ->
            val on = selected == o
            Box(
                Modifier.clip(CircleShape)
                    .background(if (on) V.amber.copy(alpha = 0.15f) else Color.Transparent)
                    .border(1.dp, if (on) V.amber else V.brass.copy(alpha = 0.25f), CircleShape)
                    .clickable { onPick(o) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) { Text(o, style = sansStyle(12, if (on) V.cream else V.muted)) }
        }
    }
}

@Composable
private fun Field(label: String, value: String, placeholder: String = "", modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(label.uppercase(), style = decoStyle(10, V.muted))
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            textStyle = sansStyle(16, V.cream), cursorBrush = SolidColor(V.amberBright),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).onFocusChangedCompat { focused = it },
            decorationBox = { inner ->
                Column {
                    Box(Modifier.padding(vertical = 8.dp)) {
                        if (value.isEmpty() && placeholder.isNotEmpty()) Text(placeholder, style = sansStyle(16, V.muted.copy(alpha = 0.5f)))
                        inner()
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(if (focused) V.amberBright else V.brass.copy(alpha = 0.3f)))
                }
            },
        )
    }
}

@Composable
internal fun SectionTitle(n: String, title: String) {
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(n, style = monoStyle(10, V.brass))
        Text(title.uppercase(), style = decoStyle(11, V.amberBright).copy(letterSpacing = 2.2.sp))
        Box(Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(listOf(V.brass.copy(alpha = 0.4f), Color.Transparent))))
    }
}

enum class ListenState { Idle, Loading, Playing }

@Composable
internal fun Listen(state: ListenState, onClick: () -> Unit, small: Boolean = false, modifier: Modifier = Modifier) {
    val btnSize = if (small) 32.dp else 40.dp
    val idle = state == ListenState.Idle
    Box(
        modifier.size(btnSize).clip(CircleShape)
            .background(if (idle) V.obsidian.copy(alpha = 0.6f) else V.amber)
            .border(1.dp, if (idle) V.brass.copy(alpha = 0.4f) else V.amber, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            ListenState.Loading -> {
                val rot by rememberInfiniteTransition(label = "spin").animateFloat(0f, 360f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "r")
                Canvas(Modifier.size(14.dp).rotate(rot)) {
                    drawArc(V.obsidian, 0f, 270f, false, style = Stroke(2.dp.toPx()))
                }
            }
            ListenState.Playing -> {
                val t = rememberInfiniteTransition(label = "eq")
                Row(Modifier.height(12.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    listOf(0.6f, 1f, 0.75f).forEachIndexed { i, h ->
                        val a by t.animateFloat(1f, 0.5f, infiniteRepeatable(tween(1000, delayMillis = i * 150), RepeatMode.Reverse), label = "a$i")
                        Box(Modifier.width(3.dp).fillMaxHeight(h).alpha(a).clip(RoundedCornerShape(2.dp)).background(V.obsidian))
                    }
                }
            }
            ListenState.Idle -> {
                val w = if (small) 8.dp else 10.dp
                val h = if (small) 10.dp else 12.dp
                Canvas(Modifier.padding(start = 2.dp).size(w, h)) {
                    val pw = size.width
                    val ph = size.height
                    drawPath(Path().apply { moveTo(0f, 0f); lineTo(pw, ph / 2); lineTo(0f, ph); close() }, V.amberBright)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LevelBar(
    label: String, value: Float, max: Float, onChange: (Float) -> Unit,
    modifier: Modifier = Modifier, ends: Pair<String, String> = "Soft" to "Full", locked: Boolean = false,
) {
    val pct = value / max * 100f
    val shape = RoundedCornerShape(12.dp)
    Box(modifier.clip(shape).background(V.panel.copy(alpha = 0.7f)).border(1.dp, V.brass.copy(alpha = 0.2f), shape)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(label.uppercase(), style = decoStyle(10, V.cream.copy(alpha = 0.8f)), maxLines = 1)
                    if (locked) ProBadge()
                }
                Text("${pct.roundToInt()}%", style = monoStyle(11, V.amberBright))
            }
            Slider(
                value = value, onValueChange = onChange, valueRange = 0f..max, enabled = !locked,
                modifier = Modifier.fillMaxWidth().height(28.dp).alpha(if (locked) 0.4f else 1f),
                colors = SliderDefaults.colors(
                    thumbColor = V.obsidian, activeTrackColor = V.amberBright, inactiveTrackColor = V.cream.copy(alpha = 0.12f),
                    disabledThumbColor = V.obsidian, disabledActiveTrackColor = V.amberBright, disabledInactiveTrackColor = V.cream.copy(alpha = 0.12f),
                ),
                thumb = {
                    Box(Modifier.size(20.dp).shadowGlow().clip(CircleShape).background(V.obsidian).border(2.dp, V.amberBright, CircleShape))
                },
                track = {
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(V.cream.copy(alpha = 0.12f))) {
                        Box(Modifier.fillMaxWidth((value / max).coerceIn(0f, 1f)).fillMaxHeight().background(V.amberBright))
                    }
                },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(ends.first.uppercase(), style = monoStyle(9).copy(letterSpacing = 1.6.sp))
                Text(ends.second.uppercase(), style = monoStyle(9).copy(letterSpacing = 1.6.sp))
            }
        }
        if (locked) Box(Modifier.matchParentSize().clickable { Pro.openPaywall("$label control is part of Vynyl Pro.") })
    }
}

private fun Modifier.shadowGlow(): Modifier = this.drawBehind {
    drawCircle(Brush.radialGradient(listOf(V.amberBright.copy(alpha = 0.6f), Color.Transparent), radius = size.minDimension), radius = size.minDimension)
}

/** CSS `repeating-radial-gradient(disc 0 2px, groove 3px …)` disc with an optional centre label. */
@Composable
internal fun GrooveDisc(disc: Color, groove: Color, modifier: Modifier = Modifier, step: Dp = 4.dp) {
    Canvas(modifier) {
        val r = size.minDimension / 2
        val c = Offset(size.width / 2, size.height / 2)
        drawCircle(disc, r, c)
        val s = step.toPx()
        var rr = s * 0.75f
        while (rr < r) {
            drawCircle(groove, rr, c, style = Stroke(s * 0.4f))
            rr += s
        }
    }
}

internal fun mix(a: Color, b: Color, t: Float) = Color(
    red = a.red + (b.red - a.red) * t, green = a.green + (b.green - a.green) * t, blue = a.blue + (b.blue - a.blue) * t, alpha = 1f,
)

