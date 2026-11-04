package com.vynyl.record.ui.theme

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.vynyl.record.R

/** Same tokens as the web app's `@theme` block in src/index.css. */
object V {
    val obsidian = Color(0xFF0C0A09)
    val stone = Color(0xFF1C1917)
    val panel = Color(0xFF211E1A)
    val amber = Color(0xFFD97706)
    val amberBright = Color(0xFFF59E0B)
    val brass = Color(0xFFB45309)
    val cream = Color(0xFFFEF3C7)
    val paper = Color(0xFFFFF7E6)
    val ruby = Color(0xFF991B1B)
    val muted = Color(0xFFA8A29E)
    val err = Color(0xFFF87171)
    val ok = Color(0xFF34D399)

    /** gold button gradient stops used across the app (#f0c86a → #c99234 → #8a5a1a) */
    val gold1 = Color(0xFFF0C86A)
    val gold2 = Color(0xFFC99234)
    val gold3 = Color(0xFF8A5A1A)
    val consoleTop = Color(0xFF2A241E)
    val consoleBottom = Color(0xFF16130F)
}

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, w: Int) = Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

object Fonts {
    /** Gloock — display serif */
    val display = FontFamily(Font(R.font.gloock_regular))
    /** Hanken Grotesk — body */
    val sans = FontFamily(variable(R.font.hanken_grotesk, 400), variable(R.font.hanken_grotesk, 500), variable(R.font.hanken_grotesk, 600), variable(R.font.hanken_grotesk, 700))
    /** Big Shoulders Display — uppercase deco labels */
    val deco = FontFamily(variable(R.font.big_shoulders_display, 600), variable(R.font.big_shoulders_display, 800))
    /** DM Mono */
    val mono = FontFamily(Font(R.font.dm_mono_regular, FontWeight.Normal), Font(R.font.dm_mono_medium, FontWeight.Medium))
}

/** `.deco` utility from the web app: bold Hanken Grotesk, uppercase, wide tracking — readable at small sizes. Callers uppercase the text. */
fun decoStyle(size: Int = 11, color: Color = V.amberBright): TextStyle {
    val s = maxOf(size, 12)
    return TextStyle(fontFamily = Fonts.sans, fontWeight = FontWeight(700), fontSize = s.sp, letterSpacing = (s * 0.14).sp, color = color)
}
fun displayStyle(size: Int, color: Color = V.cream) = TextStyle(fontFamily = Fonts.display, fontSize = size.sp, lineHeight = (size * 1.05).sp, color = color)
fun sansStyle(size: Int, color: Color = V.cream, weight: Int = 400) = TextStyle(fontFamily = Fonts.sans, fontWeight = FontWeight(weight), fontSize = size.sp, lineHeight = (size * 1.4).sp, color = color)
fun monoStyle(size: Int, color: Color = V.muted, weight: Int = 400) = TextStyle(fontFamily = Fonts.mono, fontWeight = FontWeight(weight), fontSize = size.sp, color = color)

@Composable
fun VynylTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(primary = V.amber, secondary = V.brass, background = V.obsidian, surface = V.stone, onPrimary = V.obsidian, onBackground = V.cream, onSurface = V.cream, error = V.err),
        typography = MaterialTheme.typography.copy(bodyMedium = sansStyle(14), bodyLarge = sansStyle(16)),
    ) {
        CompositionLocalProvider(LocalTextSelectionColors provides TextSelectionColors(V.amberBright, V.amber.copy(alpha = 0.35f)), content = content)
    }
}
