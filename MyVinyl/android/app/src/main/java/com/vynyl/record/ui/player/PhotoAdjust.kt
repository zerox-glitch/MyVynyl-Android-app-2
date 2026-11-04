package com.vynyl.record.ui.player

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vynyl.record.data.PhotoAdjust
import com.vynyl.record.ui.theme.V
import com.vynyl.record.ui.theme.decoStyle
import com.vynyl.record.ui.theme.displayStyle
import com.vynyl.record.ui.theme.monoStyle
import com.vynyl.record.ui.theme.sansStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

val DEFAULT_ADJUST = PhotoAdjust(mode = "fill", zoom = 1f, x = 0f, y = 0f, rot = 0f, bg = "blur")

/** Decodes image bytes, downsampled so the long edge is ≤ [maxDim]. */
fun decodePhoto(bytes: ByteArray, maxDim: Int = 2048): Bitmap? {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
    if (o.outWidth <= 0 || o.outHeight <= 0) return null
    var sample = 1
    while (max(o.outWidth, o.outHeight) / (sample * 2) >= maxDim) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
}

/** Draws the adjusted photo into a square canvas of [size] px (port of drawAdjusted). */
fun drawAdjusted(c: Canvas, img: Bitmap, a: PhotoAdjust, size: Int, labelColor: Int) {
    val turned = (a.rot.toInt() % 180) != 0
    val w = (if (turned) img.height else img.width).toFloat()
    val h = (if (turned) img.width else img.height).toFloat()
    val s = size.toFloat()
    val cover = max(s / w, s / h); val contain = min(s / w, s / h)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    fun place(canvas: Canvas, sz: Float, scale: Float, ox: Float, oy: Float, p: Paint) {
        canvas.save()
        canvas.translate(sz / 2 + ox * sz, sz / 2 + oy * sz)
        canvas.rotate(a.rot)
        val iw = img.width * scale; val ih = img.height * scale
        canvas.drawBitmap(img, null, RectF(-iw / 2, -ih / 2, iw / 2, ih / 2), p)
        canvas.restore()
    }
    c.drawColor(0, PorterDuff.Mode.CLEAR)
    if (a.mode == "fit") {
        if (a.bg == "blur") {
            // blur(size/24) brightness(.7): render tiny then upscale with bilinear filtering
            val tiny = 24
            val small = Bitmap.createBitmap(tiny, tiny, Bitmap.Config.ARGB_8888)
            place(Canvas(small), tiny.toFloat(), cover * 1.15f * tiny / s, 0f, 0f, paint)
            val mid = Bitmap.createScaledBitmap(small, tiny * 2, tiny * 2, true)
            val dim = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setScale(0.7f, 0.7f, 0.7f, 1f) }) }
            c.drawBitmap(mid, null, RectF(0f, 0f, s, s), dim)
            small.recycle(); mid.recycle()
        } else c.drawColor(labelColor)
        // inset so corners of the photo stay inside the circular label
        place(c, s, contain * 0.92f, 0f, 0f, paint)
    } else place(c, s, cover * a.zoom, a.x, a.y, paint)
}

fun renderAdjusted(img: Bitmap, a: PhotoAdjust, size: Int, labelColor: Int): Bitmap {
    val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    drawAdjusted(Canvas(out), img, a, size, labelColor)
    return out
}

fun Bitmap.toJpeg(q: Int = 90): ByteArray = ByteArrayOutputStream().also { compress(Bitmap.CompressFormat.JPEG, q, it) }.toByteArray()

private fun clampPan(a: PhotoAdjust, img: Bitmap): PhotoAdjust {
    val turned = (a.rot.toInt() % 180) != 0
    val w = (if (turned) img.height else img.width).toFloat()
    val h = (if (turned) img.width else img.height).toFloat()
    val s = max(1 / w, 1 / h) * a.zoom
    val mx = max(0f, (w * s - 1) / 2); val my = max(0f, (h * s - 1) / 2)
    return a.copy(x = a.x.coerceIn(-mx, mx), y = a.y.coerceIn(-my, my))
}

@Composable
fun PhotoAdjustDialog(source: ByteArray, initial: PhotoAdjust?, labelColor: Int, busy: Boolean, onCancel: () -> Unit, onSave: (ByteArray, PhotoAdjust) -> Unit) {
    var img by remember { mutableStateOf<Bitmap?>(null) }
    var a by remember { mutableStateOf(initial ?: DEFAULT_ADJUST) }
    var error by remember { mutableStateOf("") }

    LaunchedEffect(source) {
        val b = withContext(Dispatchers.Default) { runCatching { decodePhoto(source) }.getOrNull() }
        if (b == null) error = "This photo could not be opened. Try a JPG, PNG, or WebP." else img = b
    }
    val preview = remember(img, a, labelColor) { img?.let { renderAdjusted(it, a, 560, labelColor).asImageBitmap() } }

    fun update(f: (PhotoAdjust) -> PhotoAdjust) { val n = f(a); a = img?.let { clampPan(n, it) } ?: n }

    fun save() {
        val i = img ?: return
        val out = renderAdjusted(i, a, 768, labelColor)
        val bytes = runCatching { out.toJpeg(90) }.getOrNull()
        if (bytes != null) onSave(bytes, a) else error = "Could not prepare this photo."
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(V.obsidian.copy(alpha = 0.95f))
            .pointerInput(Unit) { detectTapGestures { } }
            .statusBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.heightIn(min = 44.dp).clickable(onClick = onCancel).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                Text("Cancel", style = sansStyle(14, V.muted))
            }
            Text("Adjust photo", style = displayStyle(18))
            val canSave = img != null && !busy
            Box(Modifier.heightIn(min = 44.dp).alpha(if (canSave) 1f else 0.4f).clickable(enabled = canSave) { save() }.padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                Text(if (busy) "Saving…" else "Save", style = sansStyle(14, V.amberBright, 500))
            }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
            Box(Modifier.padding(top = 16.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.widthIn(max = 290.dp).fillMaxWidth().aspectRatio(1f)) {
                    // grooved disc
                    Canvas(Modifier.fillMaxSize().shadow(24.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black).clip(CircleShape)) {
                        drawCircle(Color(0xFF181512))
                        val step = 4.dp.toPx()
                        var r = 3.dp.toPx()
                        while (r < size.minDimension / 2) { drawCircle(Color(0xFF221D19), radius = r, style = Stroke(width = 1.5.dp.toPx())); r += step }
                    }
                    Box(Modifier.fillMaxSize().border(1.dp, Color(0xFFB45309).copy(alpha = 0.35f), CircleShape))
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val inset = maxWidth * 0.09f
                        val boxPx = constraints.maxWidth * 0.82f
                        Box(
                            Modifier
                                .padding(inset)
                                .fillMaxSize()
                                .clip(CircleShape)
                                .border(2.dp, V.brass.copy(alpha = 0.6f), CircleShape)
                                .pointerInput(a.mode, img) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        if (a.mode == "fill") update { it.copy(x = it.x + pan.x / boxPx, y = it.y + pan.y / boxPx, zoom = (it.zoom * zoom).coerceIn(1f, 4f)) }
                                    }
                                }
                                .pointerInput(img) { detectTapGestures(onDoubleTap = { update { it.copy(zoom = 1f, x = 0f, y = 0f) } }) },
                            contentAlignment = Alignment.Center,
                        ) {
                            preview?.let { Image(it, contentDescription = "Label photo preview", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
                            if (img == null && error.isEmpty()) Text("Opening photo…", style = sansStyle(12, V.muted))
                        }
                    }
                    Box(Modifier.align(Alignment.Center).size(12.dp).border(2.dp, V.cream.copy(alpha = 0.3f), CircleShape).padding(2.dp).background(V.obsidian, CircleShape))
                }
            }
            Text(
                if (a.mode == "fill") "Drag to position · pinch or scroll to zoom · double-tap to reset" else "The whole photo is shown, nothing is cut off",
                style = sansStyle(11, V.muted), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            if (error.isNotEmpty()) Text(error, style = sansStyle(12, V.err), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))

            Row(Modifier.padding(top = 20.dp).fillMaxWidth().clip(CircleShape).border(1.dp, V.brass.copy(alpha = 0.3f), CircleShape).background(V.panel).padding(4.dp)) {
                Seg("Crop to fill", a.mode == "fill") { update { it.copy(mode = "fill") } }
                Seg("Full display", a.mode == "fit") { update { it.copy(mode = "fit") } }
            }

            if (a.mode == "fill") {
                Row(Modifier.padding(top = 20.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("ZOOM", style = decoStyle(10, V.muted), modifier = Modifier.width(40.dp))
                    Slider(
                        value = a.zoom, onValueChange = { z -> update { it.copy(zoom = z) } }, valueRange = 1f..4f,
                        colors = SliderDefaults.colors(thumbColor = V.amber, activeTrackColor = V.amber, inactiveTrackColor = V.brass.copy(alpha = 0.3f)),
                        modifier = Modifier.weight(1f),
                    )
                    Text("%.1f×".format(a.zoom), style = monoStyle(12, V.muted), textAlign = TextAlign.End, modifier = Modifier.width(40.dp))
                }
            } else {
                Row(Modifier.padding(top = 20.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("EDGES", style = decoStyle(10, V.muted), modifier = Modifier.width(64.dp))
                    Row(Modifier.weight(1f).clip(CircleShape).border(1.dp, V.brass.copy(alpha = 0.25f), CircleShape).padding(4.dp)) {
                        Seg("Soft blur", a.bg == "blur") { update { it.copy(bg = "blur") } }
                        Seg("Label color", a.bg == "label") { update { it.copy(bg = "label") } }
                    }
                }
            }

            Row(Modifier.padding(top = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinePill("↻ Rotate 90°", V.cream, Modifier.weight(1f)) { update { it.copy(rot = (it.rot + 90f) % 360f) } }
                OutlinePill("Reset", V.muted, Modifier.weight(1f)) { a = DEFAULT_ADJUST.copy(mode = a.mode, bg = a.bg) }
            }
        }
    }
}

@Composable
private fun RowScope.Seg(text: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.weight(1f).clip(CircleShape).background(if (on) V.amber else Color.Transparent)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = sansStyle(12, if (on) V.obsidian else V.muted, if (on) 500 else 400)) }
}

@Composable
private fun OutlinePill(text: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 44.dp).clip(CircleShape).border(1.dp, V.brass.copy(alpha = 0.3f), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = sansStyle(12, color)) }
}

