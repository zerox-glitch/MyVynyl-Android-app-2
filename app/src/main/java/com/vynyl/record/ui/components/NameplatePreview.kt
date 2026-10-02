package com.vynyl.record.ui.components

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vynyl.record.pro.Pro
import com.vynyl.record.ui.paywall.ProBadge
import com.vynyl.record.ui.theme.Fonts
import com.vynyl.record.ui.theme.V
import com.vynyl.record.ui.theme.decoStyle
import com.vynyl.record.ui.theme.displayStyle
import com.vynyl.record.ui.theme.sansStyle

private val PlateGold = Brush.verticalGradient(listOf(Color(0xFFFFF1C1), Color(0xFFF5C451), Color(0xFFD4952A)))
private val PlateRim = Color(0xFFC9963E)
private val PlateGlow = Shadow(Color(0xFFF5B638), blurRadius = 14f)

/** The gold plinth nameplate drawn like the 3D texture (FROM · deco divider · TO), for previewing before Pro. */
@Composable
fun Nameplate(from: String, to: String, modifier: Modifier = Modifier) {
    val small = TextStyle(brush = PlateGold, fontFamily = Fonts.sans, fontWeight = FontWeight(700), fontSize = 11.sp, letterSpacing = 3.sp, shadow = PlateGlow)
    val name = TextStyle(brush = PlateGold, fontFamily = Fonts.display, fontStyle = FontStyle.Italic, fontSize = 20.sp, shadow = PlateGlow, textAlign = TextAlign.Center)
    val outer = RoundedCornerShape(12.dp)
    Box(
        modifier.aspectRatio(320f / 600f).clip(outer)
            .background(Brush.verticalGradient(listOf(Color(0xFF1D1611), Color(0xFF0E0B08))))
            .border(3.dp, PlateRim, outer),
    ) {
        Box(Modifier.fillMaxSize().padding(6.dp).border(1.dp, PlateRim.copy(alpha = 0.55f), RoundedCornerShape(7.dp)))
        Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("FROM", style = small)
                Text(from.ifBlank { "Someone" }, style = name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
            Canvas(Modifier.fillMaxWidth(0.8f).height(14.dp)) {
                val w = size.width; val h = size.height; val cy = h / 2
                drawLine(PlateGold, Offset(w * 0.05f, cy), Offset(w * 0.4f, cy), 2f)
                drawLine(PlateGold, Offset(w * 0.6f, cy), Offset(w * 0.95f, cy), 2f)
                drawPath(Path().apply { moveTo(w / 2, 0f); lineTo(w / 2 + cy, cy); lineTo(w / 2, h); lineTo(w / 2 - cy, cy); close() }, PlateGold)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("TO", style = small)
                Text(to.ifBlank { "You" }, style = name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** Sheet shown to free users: what their nameplate would look like on the plinth, then the unlock button. */
@Composable
fun NameplatePreview(from: String, to: String, onClose: () -> Unit) {
    val none = remember { MutableInteractionSource() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).clickable(none, null) { onClose() }, contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(V.stone)
                .clickable(none, null) {}.navigationBarsPadding().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 24.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(40.dp, 4.dp).clip(CircleShape).background(V.cream.copy(alpha = 0.25f)))
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("PREVIEW", style = decoStyle(10, V.amberBright), modifier = Modifier.weight(1f))
                Text("✕", style = sansStyle(16, V.muted), modifier = Modifier.clip(CircleShape).clickable { onClose() }.padding(10.dp))
            }
            Text("Their names, in glowing gold", style = displayStyle(24))

            // brushed plinth corner with the plate set into it
            Box(
                Modifier.padding(top = 20.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF3A3F52), Color(0xFF8B90A8), Color(0xFF4B4F63), Color(0xFF2B2E3B))))
                    .background(Brush.radialGradient(listOf(Color(0x73FFC35A), Color.Transparent), center = Offset(900f, 700f), radius = 420f))
                    .border(1.dp, V.brass.copy(alpha = 0.25f), RoundedCornerShape(16.dp)).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) { Nameplate(from, to, Modifier.width(112.dp)) }
            Text(
                "Engraved on the turntable plinth for this record, and in every video you export.",
                style = sansStyle(12, V.muted), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )

            Row(
                Modifier.padding(top = 20.dp).fillMaxWidth().height(48.dp).clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(V.gold1, V.gold2, V.gold3)))
                    .clickable { onClose(); Pro.openPaywall("Engrave their names in glowing gold on the turntable plinth with Vynyl Pro.") },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("UNLOCK WITH PRO", style = decoStyle(14, V.obsidian))
                Spacer(Modifier.width(8.dp)); ProBadge()
            }
        }
    }
}
