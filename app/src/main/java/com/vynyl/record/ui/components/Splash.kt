package com.vynyl.record.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.vynyl.record.ui.theme.Fonts
import com.vynyl.record.ui.theme.V
import kotlinx.coroutines.async
import kotlinx.coroutines.delay

private val Disc = Color(0xFF14100D)
private val Label = Color(0xFFF5E9D3)

/** Vynyl mark on a 120-unit grid (same as public/icon.svg): record with a V-notch cut from rim to label. */
fun DrawScope.vynylMark(spinDeg: Float = 0f, tile: Boolean = true) {
    val u = size.minDimension / 120f
    if (tile) drawRoundRect(V.amber, cornerRadius = CornerRadius(27 * u))
    val c = Offset(60 * u, 62 * u)
    rotate(spinDeg, c) {
        val notch = Path().apply { moveTo(41 * u, 10 * u); lineTo(60 * u, 47 * u); lineTo(79 * u, 10 * u); close() }
        clipPath(notch, ClipOp.Difference) {
            drawCircle(Disc, 45 * u, c)
            drawCircle(Label.copy(alpha = .16f), 36 * u, c, style = Stroke(1.6f * u))
            drawCircle(Label.copy(alpha = .1f), 27 * u, c, style = Stroke(1.6f * u))
        }
        drawCircle(Label, 14 * u, c)
        drawCircle(Disc, 2.6f * u, c)
    }
}

/** Launch animation: tile lands, the record spins up and settles with the V locked on top, wordmark rises, then fades out. */
@Composable
fun Splash(onDone: () -> Unit) {
    val tile = remember { Animatable(0f) }; val spin = remember { Animatable(-540f) }
    val word = remember { Animatable(0f) }; val out = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        val a = async { tile.animateTo(1f, tween(700, easing = CubicBezierEasing(.2f, .9f, .3f, 1.2f))) }
        val b = async { delay(150); spin.animateTo(0f, tween(1600, easing = CubicBezierEasing(.15f, .7f, .2f, 1f))) }
        val c = async { delay(1350); word.animateTo(1f, tween(700, easing = CubicBezierEasing(.2f, .8f, .2f, 1f))) }
        a.await(); b.await(); c.await()
        delay(400); out.animateTo(0f, tween(500)); onDone()
    }
    Column(
        Modifier.fillMaxSize().graphicsLayer { alpha = out.value }.background(V.obsidian),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Canvas(Modifier.size(112.dp).graphicsLayer {
            val s = 0.6f + 0.4f * tile.value; scaleX = s; scaleY = s; alpha = tile.value.coerceIn(0f, 1f)
        }) { vynylMark(spin.value) }
        Spacer(Modifier.height(28.dp))
        Text("VYNYL", fontFamily = Fonts.deco, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp, color = V.amberBright,
            letterSpacing = (0.7f - 0.28f * word.value).em,
            modifier = Modifier.graphicsLayer { alpha = word.value; translationY = (1 - word.value) * 12.dp.toPx() })
        Spacer(Modifier.height(8.dp))
        Text("press a moment to wax", fontFamily = Fonts.display, fontStyle = FontStyle.Italic, fontSize = 14.sp, color = V.cream.copy(alpha = .6f),
            modifier = Modifier.graphicsLayer { alpha = word.value })
    }
}
