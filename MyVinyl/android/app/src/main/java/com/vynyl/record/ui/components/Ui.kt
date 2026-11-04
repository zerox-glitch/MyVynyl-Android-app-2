package com.vynyl.record.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.vynyl.record.ui.theme.V
import com.vynyl.record.ui.theme.decoStyle
import com.vynyl.record.ui.theme.sansStyle
import kotlin.math.floor
import kotlin.math.max

enum class BtnVariant { Primary, Ghost, Quiet }

private val Pill = RoundedCornerShape(50)

@Composable
fun Btn(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, variant: BtnVariant = BtnVariant.Primary, enabled: Boolean = true) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val base = modifier
        .alpha(if (enabled) 1f else 0.4f)
        .heightIn(min = 44.dp)
    val styled = when (variant) {
        BtnVariant.Primary -> base
            .drawBehind {
                // shadow-[0_0_24px_-6px_#d97706] approximation: soft amber halo
                drawRoundRect(
                    brush = Brush.radialGradient(listOf(V.amber.copy(alpha = 0.35f), Color.Transparent), center = center, radius = size.maxDimension * 0.65f),
                    cornerRadius = CornerRadius(size.height / 2),
                )
            }
            .clip(Pill).background(if (pressed) V.amberBright else V.amber)
        BtnVariant.Ghost -> base.clip(Pill)
            .background(if (pressed) V.amber.copy(alpha = 0.1f) else Color.Transparent)
            .border(1.dp, if (pressed) V.amberBright else V.brass.copy(alpha = 0.5f), Pill)
        BtnVariant.Quiet -> base.clip(Pill)
    }
    val color = when (variant) {
        BtnVariant.Primary -> V.obsidian
        BtnVariant.Ghost -> V.cream
        BtnVariant.Quiet -> if (pressed) V.cream else V.muted
    }
    Box(
        styled.clickable(interactionSource = src, indication = null, enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) { BasicText(text, style = sansStyle(14, color, 600)) }
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    BasicText(text.uppercase(), modifier, style = decoStyle(12, V.amberBright))
}

@Composable
fun Meter(n: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(label, Modifier.width(56.dp), style = sansStyle(11, V.muted))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (i in 1..5) Box(Modifier.size(16.dp, 6.dp).clip(Pill).background(if (i <= n) V.amber else V.cream.copy(alpha = 0.1f)))
        }
    }
}

@Composable
fun Wave(data: List<Float>, progress: Float = 1f, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        val n = data.size.coerceAtLeast(1)
        data.forEachIndexed { i, v ->
            Box(
                Modifier.weight(1f).fillMaxHeight(max(0.08f, v).coerceAtMost(1f)).clip(Pill)
                    .background(if (i.toFloat() / n <= progress) V.amberBright else V.cream.copy(alpha = 0.15f))
            )
        }
    }
}

fun fmt(s: Float): String {
    val t = if (s.isFinite() && s > 0f) s else 0f
    val m = floor(t / 60f).toInt()
    val sec = floor(t % 60f).toInt()
    return "$m:${sec.toString().padStart(2, '0')}"
}
