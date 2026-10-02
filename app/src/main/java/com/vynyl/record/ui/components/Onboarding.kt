package com.vynyl.record.ui.components

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vynyl.record.ui.theme.V
import com.vynyl.record.ui.theme.decoStyle
import com.vynyl.record.ui.theme.displayStyle
import com.vynyl.record.ui.theme.sansStyle
import kotlinx.coroutines.launch

private const val KEY = "onboarded"

private class Slide(val kicker: String, val title: String, val body: String, val art: DrawScope.() -> Unit)

private val SLIDES = listOf(
    Slide("WELCOME TO VYNYL", "Press a moment into wax", "Record a voice note — a birthday wish, a lullaby, a memory — and Vynyl presses it onto a one-of-a-kind vinyl record.") { artMic() },
    Slide("STUDIO", "Give it a character", "Pick a sound like a 1950s radio or a dusty jazz club, add crackle, set background music, and choose the colour of the wax.") { artDials() },
    Slide("TURNTABLE", "Drop the needle", "Play your record on a real 3D turntable. Drag to look around, pinch to zoom, and add a loved-one photo to the centre label.") { artDisc() },
    Slide("MASTER VAULT", "Keep it, share it", "Every record waits on your shelf. Export it as audio or a spinning-turntable video. Everything stays on this device unless you share it.") { artVault() },
)

/** True until the welcome slides have been finished or skipped once. */
fun needsOnboarding(ctx: Context) = !ctx.getSharedPreferences("vynyl", Context.MODE_PRIVATE).getBoolean(KEY, false)

/** First-launch welcome slides; shown once, after the splash (mirrors web Onboarding.tsx). */
@Composable
fun Onboarding(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val pager = rememberPagerState { SLIDES.size }
    val scope = rememberCoroutineScope()
    val finish = { ctx.getSharedPreferences("vynyl", Context.MODE_PRIVATE).edit().putBoolean(KEY, true).apply(); onDone() }
    val last = pager.currentPage == SLIDES.lastIndex

    Column(
        Modifier.fillMaxSize().background(V.obsidian)
            .background(Brush.radialGradient(listOf(V.amber.copy(alpha = 0.2f), Color.Transparent), center = Offset(540f, 620f), radius = 900f))
            .clickable(remember { MutableInteractionSource() }, null) {}
            .statusBarsPadding().navigationBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
            Text("Skip", style = sansStyle(14), modifier = Modifier.clip(CircleShape).border(1.dp, V.cream.copy(alpha = 0.2f), CircleShape).clickable { finish() }.padding(horizontal = 20.dp, vertical = 10.dp))
        }
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { n ->
            val s = SLIDES[n]
            Column(Modifier.fillMaxSize().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Canvas(Modifier.size(176.dp)) { s.art(this) }
                Text(s.kicker, style = decoStyle(13, V.amberBright), modifier = Modifier.padding(top = 40.dp))
                Text(s.title, style = displayStyle(32), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
                Text(s.body, style = sansStyle(14, V.muted).copy(lineHeight = 22.sp), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp).widthIn(max = 300.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 28.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SLIDES.indices.forEach { n ->
                    val w by animateDpAsState(if (n == pager.currentPage) 24.dp else 6.dp, label = "dot")
                    Box(Modifier.size(w, 6.dp).clip(CircleShape).background(if (n == pager.currentPage) V.amberBright else V.cream.copy(alpha = 0.25f))
                        .clickable { scope.launch { pager.animateScrollToPage(n) } })
                }
            }
            Box(
                Modifier.height(48.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(V.gold1, V.gold2, V.gold3)))
                    .clickable { if (last) finish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) { Text(if (last) "START RECORDING" else "NEXT", style = decoStyle(14, V.obsidian)) }
        }
    }
}

// ── slide art, on the same 160-unit grid as the web SVGs ──
private fun DrawScope.u() = size.minDimension / 160f
private val Gold = Color(0xFFFBBF24)
private fun DrawScope.goldBrush(y0: Float, y1: Float) = Brush.verticalGradient(listOf(Color(0xFFFDE68A), Color(0xFFF59E0B), Color(0xFFB45309)), y0 * u(), y1 * u())
private fun DrawScope.line(x1: Float, y1: Float, x2: Float, y2: Float, a: Float = 1f, w: Float = 3f, c: Color = Gold) =
    drawLine(c.copy(alpha = a), Offset(x1 * u(), y1 * u()), Offset(x2 * u(), y2 * u()), w * u(), StrokeCap.Round)
private fun DrawScope.rr(x: Float, y: Float, w: Float, h: Float, r: Float, brush: Brush) = drawRoundRect(brush, Offset(x * u(), y * u()), Size(w * u(), h * u()), CornerRadius(r * u()))
private fun DrawScope.rr(x: Float, y: Float, w: Float, h: Float, r: Float, c: Color) = drawRoundRect(c, Offset(x * u(), y * u()), Size(w * u(), h * u()), CornerRadius(r * u()))
private fun DrawScope.ring(cx: Float, cy: Float, r: Float, c: Color, w: Float = 1f) = drawCircle(c, r * u(), Offset(cx * u(), cy * u()), style = Stroke(w * u()))
private fun DrawScope.dot(cx: Float, cy: Float, r: Float, c: Color) = drawCircle(c, r * u(), Offset(cx * u(), cy * u()))
private fun DrawScope.dot(cx: Float, cy: Float, r: Float, b: Brush) = drawCircle(b, r * u(), Offset(cx * u(), cy * u()))

/** The shared medallion behind every slide's art. */
private fun DrawScope.medallion() {
    drawCircle(Brush.radialGradient(listOf(Color(0xFF3A2A17), Color(0xFF15110E)), Offset(80 * u(), 56 * u()), 112 * u()), 76 * u())
    drawCircle(goldBrush(4f, 156f), 76 * u(), style = Stroke(1.5f * u()), alpha = 0.7f)
    ring(80f, 80f, 68f, V.amberBright.copy(alpha = 0.15f))
}

private fun DrawScope.artMic() {
    medallion(); val k = u(); val st = Stroke(3 * k, cap = StrokeCap.Round)
    rr(62f, 30f, 36f, 58f, 18f, goldBrush(30f, 88f))
    for (y in listOf(44f, 52f, 60f, 68f, 76f)) line(66f, y, 94f, y, 0.55f, 1.6f, Color(0xFF7C3F0A))
    drawArc(Gold, 0f, 180f, false, Offset(52 * k, 46 * k), Size(56 * k, 56 * k), style = st)
    line(80f, 102f, 80f, 118f); line(64f, 120f, 96f, 120f)
    for (i in 0..2) drawArc(Gold.copy(alpha = 0.9f - i * 0.28f), -60f, 120f, false, Offset((104 + i * 8) * k, (56 - i * 4) * k), Size(16 * k, (28 + i * 8) * k), style = Stroke(2.5f * k, cap = StrokeCap.Round))
}

private fun DrawScope.artDials() {
    medallion()
    rr(34f, 36f, 92f, 88f, 12f, Color(0xFF1C1611)); drawRoundRect(V.amberBright.copy(alpha = 0.35f), Offset(34 * u(), 36 * u()), Size(92 * u(), 88 * u()), CornerRadius(12 * u()), style = Stroke(1 * u()))
    listOf(54f to 82f, 80f to 56f, 106f to 70f).forEach { (x, y) ->
        line(x, 50f, x, 110f, 0.35f, 3f, V.amberBright)
        rr(x - 10, y, 20f, 12f, 3f, goldBrush(y, y + 12))
        dot(x, 118f, 2f, Gold)
    }
}

private fun DrawScope.artDisc() {
    medallion(); val k = u()
    rr(24f, 30f, 112f, 100f, 10f, Color(0xFF2A1D12))
    drawRoundRect(V.brass.copy(alpha = 0.6f), Offset(24 * k, 30 * k), Size(112 * k, 100 * k), CornerRadius(10 * k), style = Stroke(1 * k))
    dot(72f, 80f, 40f, V.obsidian)
    listOf(36f, 31f, 26f, 21f).forEach { ring(72f, 80f, it, Gold.copy(alpha = 0.22f)) }
    drawArc(Color(0x40FFF7E6), 200f, 55f, false, Offset(38 * k, 46 * k), Size(68 * k, 68 * k), style = Stroke(3 * k, cap = StrokeCap.Round))
    dot(72f, 80f, 13f, goldBrush(67f, 93f)); dot(72f, 80f, 2.5f, V.obsidian)
    dot(120f, 44f, 7f, Color(0xFF3A2A17)); ring(120f, 44f, 7f, Gold, 2f)
    line(120f, 44f, 112f, 96f); line(112f, 96f, 100f, 106f)
    rotate(-40f, Offset(99 * k, 105 * k)) { rr(94f, 102f, 10f, 7f, 1.5f, goldBrush(102f, 109f)) }
}

private fun DrawScope.artVault() {
    medallion()
    line(30f, 82f, 130f, 82f, 1f, 4f, V.brass); line(30f, 124f, 130f, 124f, 1f, 4f, V.brass)
    listOf(38f to Color(0xFF991B1B), 52f to Color(0xFFF59E0B), 66f to Color(0xFF0D3B2A), 80f to Color(0xFFE9E1CF)).forEach { (x, c) ->
        rr(x, 40f, 12f, 42f, 2f, c); drawRoundRect(Gold.copy(alpha = 0.5f), Offset(x * u(), 40 * u()), Size(12 * u(), 42 * u()), CornerRadius(2 * u()), style = Stroke(1 * u()))
    }
    rotate(12f, Offset(102 * u(), 82 * u())) { rr(96f, 44f, 12f, 38f, 2f, Color(0xFF3B1D55)) }
    dot(104f, 106f, 17f, V.obsidian); ring(104f, 106f, 17f, Gold.copy(alpha = 0.4f)); dot(104f, 106f, 6f, goldBrush(100f, 112f))
    line(44f, 112f, 80f, 112f, 0.6f, 2.5f); line(44f, 102f, 68f, 102f, 0.6f, 2.5f)
}
