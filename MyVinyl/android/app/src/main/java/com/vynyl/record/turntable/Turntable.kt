package com.vynyl.record.turntable

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLSurfaceView
import android.provider.Settings
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vynyl.record.audio.VinylStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay

/** ES3 config with 4× MSAA (antialias: true), falling back to no MSAA. */
private class MsaaChooser : GLSurfaceView.EGLConfigChooser {
    override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig {
        val es3 = 0x40 // EGL_OPENGL_ES3_BIT_KHR
        fun pick(samples: Int): EGLConfig? {
            val attrs = if (samples > 0) intArrayOf(
                EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8, EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_DEPTH_SIZE, 24, EGL10.EGL_RENDERABLE_TYPE, es3,
                EGL10.EGL_SAMPLE_BUFFERS, 1, EGL10.EGL_SAMPLES, samples, EGL10.EGL_NONE,
            ) else intArrayOf(
                EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8, EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_DEPTH_SIZE, 16, EGL10.EGL_RENDERABLE_TYPE, es3, EGL10.EGL_NONE,
            )
            val num = IntArray(1)
            val configs = arrayOfNulls<EGLConfig>(1)
            return if (egl.eglChooseConfig(display, attrs, configs, 1, num) && num[0] > 0) configs[0] else null
        }
        return pick(4) ?: pick(0) ?: throw IllegalStateException("No GLES3 EGL config")
    }
}

@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
private class TurntableView(ctx: Context, val renderer: TurntableRenderer) : GLSurfaceView(ctx) {
    private var lastX = 0f
    private var lastY = 0f
    private var tracking = false
    private val scale = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean { renderer.orbit.zoom(d.scaleFactor); return true }
    })
    private val gestures = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onDoubleTap(e: MotionEvent): Boolean { renderer.reset(); return true }
    })

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(MsaaChooser())
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        scale.onTouchEvent(e); gestures.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { lastX = e.x; lastY = e.y; tracking = true }
            MotionEvent.ACTION_POINTER_DOWN -> tracking = false
            MotionEvent.ACTION_MOVE -> if (e.pointerCount == 1 && tracking && !scale.isInProgress) {
                renderer.orbit.rotate(e.x - lastX, e.y - lastY, height.toFloat().coerceAtLeast(1f))
                lastX = e.x; lastY = e.y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> tracking = false
        }
        return true
    }
}

/**
 * Port of src/components/Turntable.tsx: a GLES 3 turntable whose tonearm cues, swings and lowers onto the groove
 * before audio starts (onContact(true)), tracking playback position from [position].
 *
 * [bgOrigin]/[bgSize] describe the Player root in root coordinates so the GL background continues its gradients.
 */
@Composable
fun Turntable(
    modifier: Modifier,
    style: VinylStyle,
    label: LabelInfo,
    engaged: Boolean,
    progress: Float,
    position: () -> Float,
    duration: Float,
    seekToken: Int,
    onContact: (Boolean) -> Unit,
    resetKey: Int,
    labelPhoto: Bitmap?,
    nameplate: Pair<String, String>?,
    bgOrigin: Offset,
    bgSize: IntSize,
) {
    val ctx = LocalContext.current
    val fonts = remember { TexFonts(ctx.applicationContext) }
    val reduced = remember { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    val renderer = remember { TurntableRenderer(fonts, style, reduced) }
    val contact = rememberUpdatedState(onContact)
    val view = remember { TurntableView(ctx, renderer) }

    renderer.engaged = engaged
    renderer.progress = progress
    renderer.positionSec = position
    renderer.durationSec = duration
    renderer.seekToken = seekToken
    renderer.onContact = { contact.value(it) }
    renderer.onTick = { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }

    LaunchedEffect(style, label, labelPhoto) {
        val bmp = withContext(Dispatchers.Default) { labelBitmap(fonts, style, label, labelPhoto) }
        renderer.setStyle(style, bmp)
    }
    LaunchedEffect(nameplate) {
        val np = nameplate
        renderer.setNameplate(if (np == null) null else withContext(Dispatchers.Default) { nameplateBitmaps(fonts, np.first, np.second) })
    }
    LaunchedEffect(resetKey) { if (resetKey != 0) renderer.reset() }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> view.onResume()
                Lifecycle.Event.ON_PAUSE -> view.onPause()
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs); view.onPause() }
    }

    val desc = "3D turntable with ${style.name} record" + if (labelPhoto != null) " and a loved-one photo on its label" else ""
    AndroidView(
        factory = { view },
        modifier = modifier
            .semantics { contentDescription = desc }
            .onGloballyPositioned { c ->
                val p = c.positionInRoot() - bgOrigin
                renderer.bgOffset = floatArrayOf(p.x, p.y)
                renderer.bgPlayer = floatArrayOf(bgSize.width.toFloat(), bgSize.height.toFloat())
            },
    )
}
