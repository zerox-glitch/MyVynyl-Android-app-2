package com.vynyl.record.turntable

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.vynyl.record.R
import com.vynyl.record.audio.VinylStyle
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

data class LabelInfo(val title: String, val recipient: String, val side: String, val date: String)

/** Typefaces matching the web fonts used in the canvas textures. */
class TexFonts(private val ctx: Context) {
    private fun f(id: Int) = runCatching { ResourcesCompat.getFont(ctx, id) }.getOrNull() ?: Typeface.DEFAULT
    val gloock = f(R.font.gloock_regular)
    val hanken = f(R.font.hanken_grotesk)
    val shoulders = f(R.font.big_shoulders_display)
    val monoMedium = f(R.font.dm_mono_medium)
}

private fun argb(c: Long) = c.toInt()
private fun hex(s: String) = (0xFF000000L or s.removePrefix("#").toLong(16)).toInt()

private fun Paint.font(tf: Typeface, size: Float, weight: Int? = null) {
    typeface = tf; textSize = size
    setFontVariationSettings(if (weight != null) "'wght' $weight" else null)
}

private fun fit(p: Paint, t: String, max: Float): String {
    var s = t
    while (p.measureText(s) > max && s.length > 1) s = s.dropLast(2).ifEmpty { s.take(1) }
    return if (s == t) s else "$s…"
}

/** Port of labelTexture() in Turntable.tsx — 512px square label. */
fun labelBitmap(fonts: TexFonts, style: VinylStyle, l: LabelInfo, photo: Bitmap?): Bitmap {
    val bmp = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
    val g = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    p.color = argb(style.label); g.drawCircle(256f, 256f, 256f, p)
    if (photo != null) {
        g.save()
        val clip = Path().apply { addCircle(256f, 256f, 228f, Path.Direction.CW) }
        g.clipPath(clip)
        val crop = min(photo.width, photo.height)
        val sx = (photo.width - crop) / 2; val sy = (photo.height - crop) / 2
        g.drawBitmap(photo, Rect(sx, sy, sx + crop, sy + crop), RectF(28f, 28f, 484f, 484f), p)
        val shade = Paint().apply { shader = LinearGradient(0f, 330f, 0f, 490f, 0x000C0A09, 0xCC0C0A09.toInt(), Shader.TileMode.CLAMP) }
        g.drawRect(28f, 330f, 484f, 484f, shade)
        g.restore()
    }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = Paint.Style.STROKE; color = argb(style.ink); alpha = (255 * 0.6f).toInt() }
    for (r in if (photo != null) listOf(240, 228) else listOf(240, 228, 120)) {
        stroke.strokeWidth = if (r == 228) 1f else 3f
        g.drawCircle(256f, 256f, r.toFloat(), stroke)
    }
    val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = argb(style.ink) }
    if (photo != null) {
        t.color = hex("#fef3c7")
        t.font(fonts.hanken, 24f, 500); g.drawText(fit(t, if (l.recipient.isNotEmpty()) "for ${l.recipient}" else "", 300f), 256f, 409f, t)
        t.font(fonts.monoMedium, 18f); g.drawText("SIDE ${l.side.uppercase()}", 256f, 442f, t)
    } else {
        t.font(fonts.shoulders, 26f, 600); g.drawText("V Y N Y L   R E C O R D", 256f, 92f, t)
        t.font(fonts.gloock, 48f); g.drawText(fit(t, l.title.ifEmpty { "Untitled" }, 360f), 256f, 190f, t)
        t.font(fonts.hanken, 24f, 500); g.drawText(fit(t, if (l.recipient.isNotEmpty()) "for ${l.recipient}" else "", 360f), 256f, 342f, t)
        t.font(fonts.monoMedium, 20f); g.drawText(l.date, 256f, 378f, t)
        t.font(fonts.shoulders, 30f, 800); g.drawText(l.side.uppercase(), 256f, 430f, t)
    }
    p.color = hex("#0c0a09"); g.drawCircle(256f, 256f, 10f, p)
    return bmp
}

/** Port of grooveTexture(): a grey bump map of fine concentric grooves. */
fun grooveBitmap(): Bitmap {
    val bmp = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
    val g = Canvas(bmp)
    g.drawColor(hex("#808080"))
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f }
    val rnd = Random(7)
    var r = 180f
    while (r < 508f) {
        p.color = if (rnd.nextFloat() < 0.5f) 0x17FFFFFF else 0x2E000000
        g.drawCircle(512f, 512f, r, p); r += 1.6f
    }
    p.color = 0x80000000.toInt(); p.strokeWidth = 5f
    for (rr in listOf(300f, 400f)) g.drawCircle(512f, 512f, rr, p)
    return bmp
}

/** Port of dialTexture(): engraved control plate. Angles in radians, clockwise from 12 o'clock. */
fun dialBitmap(fonts: TexFonts, marks: List<Pair<String, Float>>, ticks: List<Float>): Bitmap {
    val bmp = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
    val g = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.shader = RadialGradient(128f, 116f, 124f, hex("#2a211a"), hex("#120e0b"), Shader.TileMode.CLAMP)
    g.drawCircle(128f, 128f, 126f, p); p.shader = null
    p.style = Paint.Style.STROKE; p.color = hex("#b8862e"); p.strokeWidth = 4f
    g.drawCircle(128f, 128f, 122f, p)
    p.strokeWidth = 1.5f; p.alpha = 128; g.drawCircle(128f, 128f, 112f, p); p.alpha = 255
    p.color = hex("#e8c27a"); p.strokeCap = Paint.Cap.ROUND; p.strokeWidth = 3f
    for (a in ticks) g.drawLine(128 + sin(a) * 92, 128 - cos(a) * 92, 128 + sin(a) * 106, 128 - cos(a) * 106, p)
    val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = hex("#f5deb0"); textAlign = Paint.Align.CENTER }
    t.font(fonts.shoulders, 22f, 600)
    val mid = -(t.fontMetrics.ascent + t.fontMetrics.descent) / 2
    for ((txt, a) in marks) {
        g.save(); g.translate(128 + sin(a) * 76, 128 - cos(a) * 76); g.rotate(Math.toDegrees(a.toDouble()).toFloat())
        g.drawText(txt, 0f, mid, t); g.restore()
    }
    return bmp
}

/** Port of the golden nameplate canvas. Returns (map, emissiveMap). */
fun nameplateBitmaps(fonts: TexFonts, from: String, to: String): Pair<Bitmap, Bitmap> {
    val W = 320; val H = 600
    fun draw(glowOnly: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val g = Canvas(bmp)
        if (!glowOnly) {
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, 0f, 0f, H.toFloat(), hex("#1d1611"), hex("#0e0b08"), Shader.TileMode.CLAMP) }
            val rr = RectF(4f, 4f, W - 4f, H - 4f)
            g.drawRoundRect(rr, 26f, 26f, bg)
            val s = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = hex("#c9963e"); strokeWidth = 6f }
            g.drawRoundRect(rr, 26f, 26f, s)
            s.strokeWidth = 1.5f; s.alpha = (255 * 0.55f).toInt()
            g.drawRoundRect(RectF(18f, 18f, W - 18f, H - 18f), 16f, 16f, s)
        } else g.drawColor(0xFF000000.toInt())
        val gold = LinearGradient(0f, 0f, 0f, H.toFloat(), intArrayOf(hex("#fff1c1"), hex("#f5c451"), hex("#d4952a")), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = gold; textAlign = Paint.Align.CENTER }
        if (glowOnly) p.setShadowLayer(9f, 0f, 0f, hex("#f5b638"))
        fun mid() = -(p.fontMetrics.ascent + p.fontMetrics.descent) / 2
        fun small(t: String, y: Float) {
            p.font(fonts.shoulders, 30f, 600); p.textSkewX = 0f; p.letterSpacing = 8f / 30f
            g.drawText(t, W / 2f + 4f, y + mid(), p); p.letterSpacing = 0f
        }
        fun name(t: String, y: Float) {
            var f = 76
            do { p.font(fonts.gloock, f.toFloat()); p.textSkewX = -0.22f } while (p.measureText(t) > W - 50 && --f > 18)
            g.drawText(t, W / 2f, y + mid(), p); p.textSkewX = 0f
        }
        small("FROM", 120f); name(from.ifEmpty { "Someone" }, 190f)
        val line = Paint(p).apply { style = Paint.Style.STROKE; strokeWidth = 2f }
        g.drawLine(60f, 300f, 130f, 300f, line); g.drawLine(190f, 300f, 260f, 300f, line)
        val diamond = Path().apply { moveTo(160f, 286f); lineTo(174f, 300f); lineTo(160f, 314f); lineTo(146f, 300f); close() }
        g.drawPath(diamond, Paint(p).apply { style = Paint.Style.FILL })
        small("TO", 400f); name(to.ifEmpty { "You" }, 470f)
        return bmp
    }
    return draw(false) to draw(true)
}
