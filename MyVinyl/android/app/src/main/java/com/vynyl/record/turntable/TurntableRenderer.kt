package com.vynyl.record.turntable

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.vynyl.record.audio.VinylStyle
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// Tonearm geometry (world units, xz-plane). Pivot P, platter centre C, effective length L (pivot → stylus).
private const val PIVOT_X = 1.35f
private const val PIVOT_Z = -1.0f
private const val CENTER_X = -0.45f
private const val CENTER_Z = 0f
private const val ARM_L = 2.2f
private const val R_LEAD_IN = 1.19f
private const val R_LEAD_OUT = 0.5f
private const val R_REST = 1.85f
private val DIST = hypot(CENTER_X - PIVOT_X, CENTER_Z - PIVOT_Z)
private val PHI0 = atan2(CENTER_Z - PIVOT_Z, CENTER_X - PIVOT_X)
/** Stylus heading from pivot for a groove of radius r (law of cosines). */
private fun headingFor(r: Float) = PHI0 - acos(((ARM_L * ARM_L + DIST * DIST - r * r) / (2 * ARM_L * DIST)).coerceIn(-1f, 1f))
/** Y rotation that turns local −Z into world heading θ. */
private fun yawFor(theta: Float) = atan2(-cos(theta), -sin(theta))
private fun armAngle(r: Float) = yawFor(headingFor(r))
private val ANG_REST = armAngle(R_REST)
private const val REST_TUBE_X = 0.0779f   // arm-tube centreline point (pivot frame) that lies on the rest
private const val REST_TUBE_Z = -1.3f
private const val REST_TOP = 0.32f - 0.0007f - 0.024f // world y of the tube's underside there with the cue lowered
private const val LIFT_UP = 0.045f
private const val KNOB_OFF = -0.75f
private const val KNOB_ON = 0.75f
private const val SPEED_33 = -0.6f
private val PIf = PI.toFloat()

/** Orbit camera mirroring three's OrbitControls config in Turntable.tsx. Touch thread writes, GL thread reads. */
class Orbit(reduced: Boolean) {
    private val home = floatArrayOf(0.2f, 3.4f, 4.6f)
    private val target = floatArrayOf(0f, 0.2f, 0f)
    var radius = 0f; var theta = 0f; var phi = 0f
    private var dTheta = 0f; private var dPhi = 0f; private var scale = 1f
    var autoRotate = !reduced
    var autoRotateSpeed = 0.35f

    init { reset() }

    @Synchronized fun reset() {
        val x = home[0] - target[0]; val y = home[1] - target[1]; val z = home[2] - target[2]
        radius = sqrt(x * x + y * y + z * z); theta = atan2(x, z); phi = acos((y / radius).coerceIn(-1f, 1f))
        dTheta = 0f; dPhi = 0f; scale = 1f
    }
    @Synchronized fun rotate(dx: Float, dy: Float, viewH: Float) {
        autoRotate = false
        dTheta -= 2 * PIf * dx / viewH; dPhi -= 2 * PIf * dy / viewH
    }
    /** Scripted camera (video export): places the eye exactly, no damping. */
    @Synchronized fun place(r: Float, th: Float, ph: Float) { autoRotate = false; radius = r; theta = th; phi = ph; dTheta = 0f; dPhi = 0f; scale = 1f }
    @Synchronized fun zoom(factor: Float) { autoRotate = false; scale /= factor }

    /** Returns eye position. */
    @Synchronized fun update(): FloatArray {
        if (autoRotate) dTheta -= 2 * PIf / 60f / 60f * autoRotateSpeed
        val damp = 0.05f
        theta += dTheta * damp; phi += dPhi * damp
        phi = phi.coerceIn(0.05f, PIf * 0.44f)
        radius = (radius * scale).coerceIn(3f, 8f); scale = 1f
        dTheta *= 1 - damp; dPhi *= 1 - damp
        return floatArrayOf(target[0] + radius * sin(phi) * sin(theta), target[1] + radius * cos(phi), target[2] + radius * sin(phi) * cos(theta))
    }
    fun target() = target
}

class TurntableRenderer(private val fonts: TexFonts, initialStyle: VinylStyle, reduced: Boolean) : GLSurfaceView.Renderer {
    val orbit = Orbit(reduced)
    private val main = Handler(Looper.getMainLooper())

    // live inputs (UI thread → GL thread)
    @Volatile var engaged = false
    @Volatile var progress = 0f
    @Volatile var positionSec: () -> Float = { 0f }
    @Volatile var durationSec = 0f
    @Volatile var seekToken = 0
    @Volatile var onContact: (Boolean) -> Unit = {}
    @Volatile var onTick: () -> Unit = {}
    @Volatile var bgPlayer = floatArrayOf(1f, 1f)
    @Volatile var bgOffset = floatArrayOf(0f, 0f)

    private val root = Node()
    private val record: Node
    private val platter: Node
    private val pivot: Node
    private val lift: Node
    private val knob: Node
    private val nameplateNode: Node
    private val discMat: Mat
    private val labelMat: Mat
    private val lampMat: Mat
    private val plateMat: Mat
    private val textures = ArrayList<Tex>()
    private val geos = ArrayList<Geo>()
    private var glowIntensity = 0f
    @Volatile private var nameplateOn = false
    @Volatile private var pendingDisc: VinylStyle? = null

    // physics state
    private var spin = 0f; private var rot = 0f; private var ang = ANG_REST; private var vel = 0f
    private var liftV = LIFT_UP; private var down = false; private var settle = 1f
    private enum class Phase { Parked, Lifting, Swinging, Lowering, Tracking, Returning }
    private var phase = Phase.Parked
    private var audioTime = 0f
    private var lastSeekToken = 0

    // GL
    private var prog = 0; private var depthProg = 0; private var bgProg = 0
    private var shadowFbo = 0; private var shadowTex = 0
    private val shadowSize = 2048
    private var vw = 1; private var vh = 1
    private var last = 0L
    private val uni = HashMap<String, Int>()

    private fun L(hex: Int) = lin(hex)
    private fun std(color: Int, rough: Float, metal: Float = 0f) = Mat(color = L(color), roughness = rough, metalness = metal)
    private fun geo(m: MeshData) = Geo(m).also { geos.add(it) }
    private fun tex(t: Tex) = t.also { textures.add(it) }
    private fun Node.mesh(g: Geo, m: Mat, x: Float = 0f, y: Float = 0f, z: Float = 0f, cast: Boolean = true): Node {
        val n = Node(x, y, z); n.items.add(Item(g, m, cast)); add(n); return n
    }

    init {
        val lacquer = std(0x2a1a10, 0.35f, 0.1f)
        val brass = std(0xb8862e, 0.28f, 1f)
        val steel = std(0xc9c4bd, 0.3f, 1f)
        val rubber = std(0x141210, 0.95f)

        // floor contact shadow
        root.mesh(geo(plane(20f, 20f)), Mat(opacity = 0.45f, transparent = true, shadowOnly = true), 0f, -0.36f, 0f, cast = false).rot[0] = -PIf / 2

        // plinth
        val plinth = roundedSlab(2.1f, 1.6f, 0.14f, 0.36f, 0.03f).rotatedX(-PIf / 2).translated(0f, -0.3f, 0f)
        root.mesh(geo(plinth), lacquer)
        root.mesh(geo(box(4.24f, 0.02f, 3.24f)), brass, 0f, 0.075f, 0f)
        val foot = geo(cylinder(0.16f, 0.2f, 0.1f, 24))
        for ((x, z) in listOf(-1.8f to -1.3f, 1.8f to -1.3f, -1.8f to 1.3f, 1.8f to 1.3f)) root.mesh(foot, rubber, x, -0.33f, z)

        // platter + mat
        platter = root.add(Node(CENTER_X, 0.1f, 0f))
        platter.mesh(geo(cylinder(1.35f, 1.35f, 0.12f, 96)), steel)
        platter.mesh(geo(cylinder(1.32f, 1.32f, 0.02f, 96)), rubber, 0f, 0.07f, 0f)
        val dotGeo = geo(box(0.03f, 0.06f, 0.02f)); val dotMat = std(0xe6e0d8, 0.2f, 1f)
        for (i in 0 until 60) {
            val a = i / 60f * 2 * PIf
            platter.mesh(dotGeo, dotMat, cos(a) * 1.355f, 0f, sin(a) * 1.355f, cast = false).rot[1] = -a
        }
        // record
        record = platter.add(Node())
        discMat = Mat(color = lin(initialStyle.disc), roughness = 1 - initialStyle.sheen * 0.8f, metalness = 0.3f, bump = tex(Tex(grooveBitmap(), srgb = false)), bumpScale = 0.6f, opacity = initialStyle.opacity, transparent = initialStyle.opacity < 1f)
        record.mesh(geo(cylinder(1.25f, 1.25f, 0.025f, 128)), discMat, 0f, 0.095f, 0f)
        labelMat = Mat(roughness = 0.8f, map = tex(Tex(null)))
        record.mesh(geo(circle(0.42f, 64)), labelMat, 0f, 0.109f, 0f).rot[0] = -PIf / 2
        record.mesh(geo(cylinder(0.03f, 0.03f, 0.18f, 16)), steel, 0f, 0.14f, 0f)

        // tonearm — arm points along local −Z, stylus tip at (0, −0.1125, −L)
        val arm = root.add(Node(PIVOT_X, 0.1f, PIVOT_Z))
        arm.mesh(geo(cylinder(0.2f, 0.24f, 0.12f, 40)), brass)
        pivot = arm.add(Node(0f, 0.22f, 0f))
        pivot.mesh(geo(cylinder(0.06f, 0.07f, 0.22f, 20)), steel, 0f, -0.1f, 0f)
        lift = pivot.add(Node())
        lift.mesh(geo(sphere(0.075f, 24, 16)), steel)
        val curve = listOf(floatArrayOf(0f, 0f, 0.42f), floatArrayOf(0f, 0f, 0f), floatArrayOf(0.09f, 0f, -ARM_L * 0.45f), floatArrayOf(0.02f, -0.01f, -ARM_L + 0.32f), floatArrayOf(0f, -0.03f, -ARM_L + 0.16f))
        lift.mesh(geo(tube(curve, 96, 0.024f, 12)), steel)
        lift.mesh(geo(cylinder(0.11f, 0.11f, 0.2f, 32).rotatedX(PIf / 2)), brass, 0f, 0f, 0.5f)
        val rMid = (R_LEAD_IN + R_LEAD_OUT) / 2; val th = headingFor(rMid)
        val sx = PIVOT_X + cos(th) * ARM_L - CENTER_X; val sz = PIVOT_Z + sin(th) * ARM_L - CENTER_Z
        var offset = yawFor(atan2(sx, -sz)) - yawFor(th)
        offset = atan2(sin(offset), cos(offset)); if (offset > PIf / 2) offset -= PIf; if (offset < -PIf / 2) offset += PIf
        val shell = lift.add(Node(0f, 0f, -ARM_L)); shell.rot[1] = offset
        shell.mesh(geo(box(0.15f, 0.03f, 0.3f)), std(0x9a948c, 0.3f, 0.35f), 0f, -0.035f, 0.06f)
        shell.mesh(geo(box(0.08f, 0.05f, 0.12f)), brass, 0f, -0.07f, 0.01f)
        shell.mesh(geo(cone(0.008f, 0.02f, 8)), steel, 0f, -0.1025f, 0f).rot[0] = PIf
        shell.mesh(geo(box(0.02f, 0.012f, 0.1f)), brass, 0.09f, -0.03f, 0.14f)
        // arm rest: post + rubber cradle exactly under the parked tube (tube point at local z = −1.3, incl. its S-bend),
        // topped at the tube's underside so the arm sits on it when lowered
        val rx = REST_TUBE_X * cos(ANG_REST) + REST_TUBE_Z * sin(ANG_REST); val rz = -REST_TUBE_X * sin(ANG_REST) + REST_TUBE_Z * cos(ANG_REST)
        root.mesh(geo(cylinder(0.035f, 0.05f, REST_TOP - 0.012f - 0.09f, 16)), steel, PIVOT_X + rx, (REST_TOP - 0.012f + 0.09f) / 2, PIVOT_Z + rz)
        root.mesh(geo(cylinder(0.055f, 0.05f, 0.012f, 20)), rubber, PIVOT_X + rx, REST_TOP - 0.006f, PIVOT_Z + rz)

        // controls: dial plates, knurled knobs, jewel lamp
        fun plate(r: Float, t: Tex, x: Float, z: Float) {
            root.mesh(geo(circle(r, 64)), Mat(roughness = 0.55f, metalness = 0.35f, map = t), x, 0.093f, z, cast = false).rot[0] = -PIf / 2
        }
        fun knurled(r: Float, h: Float, mat: Mat, capMat: Mat): Node {
            val k = Node()
            k.mesh(geo(cylinder(r * 1.08f, r * 1.12f, 0.02f, 48)), capMat, 0f, 0.01f, 0f)
            k.mesh(geo(cylinder(r * 0.94f, r, h, 48)), mat, 0f, 0.02f + h / 2, 0f)
            val rib = geo(box(0.012f, h * 0.9f, 0.018f))
            for (i in 0 until 28) {
                val a = i / 28f * 2 * PIf
                k.mesh(rib, mat, sin(a) * r * 0.98f, 0.02f + h / 2, cos(a) * r * 0.98f).rot[1] = a
            }
            k.mesh(geo(cylinder(r * 0.8f, r * 0.94f, 0.02f, 48)), capMat, 0f, 0.03f + h, 0f)
            k.mesh(geo(box(0.018f, 0.006f, r * 0.75f)), Mat(color = L(0xf5deb0), emissive = L(0xf59e0b), emissiveIntensity = 0.25f, roughness = 0.4f), 0f, 0.043f + h, -r * 0.42f)
            return k
        }
        plate(0.27f, tex(Tex(dialBitmap(fonts, listOf("STOP" to KNOB_OFF, "START" to KNOB_ON), listOf(KNOB_OFF, 0f, KNOB_ON)))), -1.75f, 1.25f)
        val polishedBrass = std(0xd4a24c, 0.18f, 1f)
        knob = root.add(knurled(0.13f, 0.07f, brass, polishedBrass)); knob.pos[0] = -1.75f; knob.pos[1] = 0.092f; knob.pos[2] = 1.25f; knob.rot[1] = -KNOB_OFF
        plate(0.17f, tex(Tex(dialBitmap(fonts, listOf("33" to SPEED_33, "45" to -SPEED_33), listOf(SPEED_33, -SPEED_33)))), -1.25f, 1.33f)
        val speed = root.add(knurled(0.075f, 0.06f, steel, std(0xe6e0d8, 0.15f, 1f))); speed.pos[0] = -1.25f; speed.pos[1] = 0.092f; speed.pos[2] = 1.33f; speed.rot[1] = -SPEED_33
        lampMat = Mat(color = L(0x5a2a08), emissive = L(0xf59e0b), emissiveIntensity = 0f, roughness = 0.15f, metalness = 0f, transparent = true, opacity = 0.92f)
        root.mesh(geo(torus(0.06f, 0.016f, 12, 40)), polishedBrass, -0.9f, 0.1f, 1.42f).rot[0] = -PIf / 2
        root.mesh(geo(cylinder(0.062f, 0.07f, 0.025f, 32)), std(0x120e0b, 0.6f), -0.9f, 0.095f, 1.42f)
        root.mesh(geo(sphere(0.052f, 24, 12, 0f, 2 * PIf, 0f, PIf / 2)), lampMat, -0.9f, 0.1f, 1.42f)

        // golden nameplate (front-right corner); hidden until a nameplate is set
        plateMat = Mat(map = tex(Tex(null)), emissiveMap = tex(Tex(null)), emissive = L(0xffc35a), emissiveIntensity = 1.2f, roughness = 0.35f, metalness = 0.4f)
        val pw = 0.6f; val ph = pw * (600f / 320f)
        nameplateNode = root.mesh(geo(plane(pw, ph)), plateMat, 1.6f, 0.094f, 0.62f, cast = false)
        nameplateNode.rot[0] = -PIf / 2; nameplateNode.visible = false
    }

    // ---------- API (UI thread) ----------
    fun setStyle(s: VinylStyle, label: android.graphics.Bitmap) {
        pendingDisc = s
        labelMat.map?.replace(label)
    }
    fun setNameplate(maps: Pair<android.graphics.Bitmap, android.graphics.Bitmap>?) {
        if (maps != null) { plateMat.map?.replace(maps.first); plateMat.emissiveMap?.replace(maps.second) }
        nameplateOn = maps != null
    }
    fun reset() = orbit.reset()

    // ---------- GL ----------
    /** Framebuffer the main pass draws into (0 = window; video export uses an MSAA FBO). */
    var targetFbo = 0
    /** True once the stylus is resting in the groove. */
    val needleDown get() = phase == Phase.Tracking

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) { initGl(); last = SystemClock.uptimeMillis() }

    /** Compiles programs and allocates GL resources on the current context. */
    fun initGl() {
        geos.forEach { it.invalidateGl() }; textures.forEach { it.invalidateGl() }; uni.clear()
        prog = compile(MAIN_VS, MAIN_FS); depthProg = compile(DEPTH_VS, DEPTH_FS); bgProg = compile(BG_VS, BG_FS)
        val t = IntArray(1); GLES30.glGenTextures(1, t, 0); shadowTex = t[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, shadowTex)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_DEPTH_COMPONENT24, shadowSize, shadowSize, 0, GLES30.GL_DEPTH_COMPONENT, GLES30.GL_UNSIGNED_INT, null)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_COMPARE_MODE, GLES30.GL_COMPARE_REF_TO_TEXTURE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_COMPARE_FUNC, GLES30.GL_LEQUAL)
        val f = IntArray(1); GLES30.glGenFramebuffers(1, f, 0); shadowFbo = f[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, shadowFbo)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_TEXTURE_2D, shadowTex, 0)
        GLES30.glDrawBuffers(1, intArrayOf(GLES30.GL_NONE), 0)
        GLES30.glReadBuffer(GLES30.GL_NONE)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) = resize(width, height)
    fun resize(width: Int, height: Int) { vw = max(1, width); vh = max(1, height) }

    private fun u(p: Int, name: String): Int = uni.getOrPut("$p/$name") { GLES30.glGetUniformLocation(p, name) }

    override fun onDrawFrame(gl: GL10?) {
        val now = SystemClock.uptimeMillis()
        val dt = min(0.05f, (now - last) / 1000f); last = now
        render(dt, now)
    }

    /** Advances physics by [dt] seconds and draws one frame. [now] drives cosmetic pulses (ms). */
    fun render(dt: Float, now: Long) {
        pendingDisc?.let { s ->
            discMat.color = lin(s.disc); discMat.opacity = s.opacity; discMat.transparent = s.opacity < 1f; discMat.roughness = 1 - s.sheen * 0.8f
            pendingDisc = null
        }
        nameplateNode.visible = nameplateOn
        step(dt, now)

        // camera
        val eye = orbit.update(); val tg = orbit.target()
        val view = FloatArray(16); val proj = FloatArray(16); val vp = FloatArray(16)
        Matrix.setLookAtM(view, 0, eye[0], eye[1], eye[2], tg[0], tg[1], tg[2], 0f, 1f, 0f)
        Matrix.perspectiveM(proj, 0, 36f, vw.toFloat() / vh, 0.1f, 50f)
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0)
        // key light shadow camera
        val lView = FloatArray(16); val lProj = FloatArray(16); val lvp = FloatArray(16)
        Matrix.setLookAtM(lView, 0, 3f, 6f, 3f, 0f, 0f, 0f, 0f, 1f, 0f)
        Matrix.orthoM(lProj, 0, -3f, 3f, -3f, 3f, 1f, 15f)
        Matrix.multiplyMM(lvp, 0, lProj, 0, lView, 0)

        // flatten scene
        val draws = ArrayList<Pair<FloatArray, Item>>()
        fun walk(n: Node, parent: FloatArray) {
            if (!n.visible) return
            val m = FloatArray(16)
            Matrix.translateM(m.also { Matrix.setIdentityM(it, 0) }, 0, n.pos[0], n.pos[1], n.pos[2])
            if (n.rot[0] != 0f) Matrix.rotateM(m, 0, Math.toDegrees(n.rot[0].toDouble()).toFloat(), 1f, 0f, 0f)
            if (n.rot[1] != 0f) Matrix.rotateM(m, 0, Math.toDegrees(n.rot[1].toDouble()).toFloat(), 0f, 1f, 0f)
            if (n.rot[2] != 0f) Matrix.rotateM(m, 0, Math.toDegrees(n.rot[2].toDouble()).toFloat(), 0f, 0f, 1f)
            val w = FloatArray(16); Matrix.multiplyMM(w, 0, parent, 0, m, 0)
            for (it in n.items) draws.add(w to it)
            for (c in n.children) walk(c, w)
        }
        walk(root, FloatArray(16).also { Matrix.setIdentityM(it, 0) })
        for ((_, it) in draws) it.geo.ensure()
        textures.forEach { it.ensure() }

        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_CULL_FACE)

        // shadow pass
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, shadowFbo)
        GLES30.glViewport(0, 0, shadowSize, shadowSize)
        GLES30.glClear(GLES30.GL_DEPTH_BUFFER_BIT)
        GLES30.glUseProgram(depthProg)
        GLES30.glUniformMatrix4fv(u(depthProg, "uLightVP"), 1, false, lvp, 0)
        for ((m, it) in draws) {
            if (!it.castShadow || it.mat.shadowOnly) continue
            GLES30.glUniformMatrix4fv(u(depthProg, "uModel"), 1, false, m, 0)
            it.geo.draw()
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, targetFbo)

        // background
        GLES30.glViewport(0, 0, vw, vh)
        GLES30.glClearColor(12 / 255f, 10 / 255f, 9 / 255f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glUseProgram(bgProg)
        val bp = bgPlayer; val bo = bgOffset
        GLES30.glUniform2f(u(bgProg, "uPlayer"), bp[0], bp[1])
        GLES30.glUniform2f(u(bgProg, "uOffset"), bo[0], bo[1])
        GLES30.glUniform1f(u(bgProg, "uViewH"), vh.toFloat())
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)

        // main pass
        val p = prog
        GLES30.glUseProgram(p)
        GLES30.glUniformMatrix4fv(u(p, "uViewProj"), 1, false, vp, 0)
        GLES30.glUniformMatrix4fv(u(p, "uLightVP"), 1, false, lvp, 0)
        GLES30.glUniform2f(u(p, "uShadowTexel"), 1f / shadowSize, 1f / shadowSize)
        GLES30.glUniform3f(u(p, "uCam"), eye[0], eye[1], eye[2])
        GLES30.glUniform1f(u(p, "uExposure"), 1.6f)
        val sky = lin(0x8a6a4a); val ground = lin(0x1a1410)
        GLES30.glUniform3f(u(p, "uHemiSky"), sky[0] * 1.1f, sky[1] * 1.1f, sky[2] * 1.1f)
        GLES30.glUniform3f(u(p, "uHemiGround"), ground[0] * 1.1f, ground[1] * 1.1f, ground[2] * 1.1f)
        // directional: key (shadowed), rim, front, overhead fill over the record
        val dirs = arrayOf(floatArrayOf(3f, 6f, 3f), floatArrayOf(-2f, 3f, -5f), floatArrayOf(0.5f, 3f, 6f), floatArrayOf(-0.45f, 6f, 0.5f))
        val dcol = arrayOf(lin(0xffd9a0) * 3.2f, lin(0x8fb3ff) * 1.2f, lin(0xffe6c4) * 0.9f, lin(0xfff1dc) * 1.4f)
        val dirFlat = FloatArray(12); val dcolFlat = FloatArray(12)
        for (i in 0 until 4) {
            val d = dirs[i]; val l = sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2])
            for (k in 0 until 3) { dirFlat[i * 3 + k] = d[k] / l; dcolFlat[i * 3 + k] = dcol[i][k] }
        }
        GLES30.glUniform3fv(u(p, "uDirDir"), 4, dirFlat, 0)
        GLES30.glUniform3fv(u(p, "uDirCol"), 4, dcolFlat, 0)
        // point: amber fill, lamp glow, nameplate glow
        val ptPos = floatArrayOf(-4f, 2f, 1f, -0.9f, 0.22f, 1.42f, 1.6f, 0.4f, 0.62f)
        val amber = lin(0xd97706) * 8f; val glow = lin(0xf59e0b) * glowIntensity; val np = lin(0xffc35a) * (if (nameplateOn) 0.5f else 0f)
        GLES30.glUniform3fv(u(p, "uPtPos"), 3, ptPos, 0)
        GLES30.glUniform3fv(u(p, "uPtCol"), 3, amber + glow + np, 0)
        GLES30.glUniform1fv(u(p, "uPtDist"), 3, floatArrayOf(12f, 0.9f, 1.2f), 0)
        GLES30.glUniform1i(u(p, "uMap"), 0); GLES30.glUniform1i(u(p, "uEmMap"), 1)
        GLES30.glUniform1i(u(p, "uBump"), 2); GLES30.glUniform1i(u(p, "uShadow"), 3)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE3); GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, shadowTex)

        val opaque = draws.filter { !it.second.mat.transparent }
        val blended = draws.filter { it.second.mat.transparent }
        GLES30.glDisable(GLES30.GL_BLEND)
        for ((m, it) in opaque) drawItem(p, m, it)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFuncSeparate(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA, GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        for ((m, it) in blended) drawItem(p, m, it)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    private operator fun FloatArray.times(s: Float) = FloatArray(size) { this[it] * s }

    private fun bindTex(unit: Int, t: Tex?): Boolean {
        if (t == null || t.id == 0 || t.bitmap == null) return false
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, t.id)
        return true
    }

    private fun drawItem(p: Int, model: FloatArray, it: Item) {
        val m = it.mat
        GLES30.glUniformMatrix4fv(u(p, "uModel"), 1, false, model, 0)
        GLES30.glUniform3f(u(p, "uColor"), m.color[0], m.color[1], m.color[2])
        GLES30.glUniform1f(u(p, "uRough"), m.roughness)
        GLES30.glUniform1f(u(p, "uMetal"), m.metalness)
        GLES30.glUniform1f(u(p, "uOpacity"), m.opacity)
        GLES30.glUniform3f(u(p, "uEmissive"), m.emissive[0] * m.emissiveIntensity, m.emissive[1] * m.emissiveIntensity, m.emissive[2] * m.emissiveIntensity)
        GLES30.glUniform1f(u(p, "uBumpScale"), m.bumpScale)
        GLES30.glUniform1i(u(p, "uShadowOnly"), if (m.shadowOnly) 1 else 0)
        GLES30.glUniform1i(u(p, "uUseMap"), if (bindTex(0, m.map)) 1 else 0)
        GLES30.glUniform1i(u(p, "uUseEmMap"), if (bindTex(1, m.emissiveMap)) 1 else 0)
        GLES30.glUniform1i(u(p, "uUseBump"), if (bindTex(2, m.bump)) 1 else 0)
        it.geo.draw()
    }

    /** Platter, record and tonearm physics — port of the playbackRef branch of Turntable.tsx's loop. */
    private fun step(dt: Float, now: Long) {
        val on = engaged
        val dur = durationSec
        val pos = positionSec()
        val pr = if (dur > 0f) pos / dur else progress
        val targetSpin = if (on) (33.333f / 60f) * 2 * PIf else 0f
        spin += (targetSpin - spin) * min(1f, dt * (if (on) 1.6f else 0.9f))
        rot += spin * dt; platter.rot[1] = -rot
        settle = min(1f, settle + dt * 1.5f)
        record.pos[1] = (1 - settle) * (1 - settle) * 0.5f
        val spunUp = on && spin > targetSpin * 0.9f
        val grooveR = R_LEAD_IN - (R_LEAD_IN - R_LEAD_OUT) * pr.coerceIn(0f, 1f)
        val grooveAng = armAngle(grooveR)
        val token = seekToken
        val seeked = token != lastSeekToken || abs(pos - audioTime) > 1.0f + dt
        lastSeekToken = token
        audioTime = pos
        if ((on && phase == Phase.Parked) || (!on && phase != Phase.Parked && phase != Phase.Lifting && phase != Phase.Returning) || (on && phase == Phase.Returning) || (phase == Phase.Tracking && seeked)) {
            phase = Phase.Lifting
            vel = 0f
            if (down) { down = false; contact(false) }
        }
        if (phase == Phase.Lifting) {
            liftV += (LIFT_UP - liftV) * (1 - exp(-dt * 12))
            if (LIFT_UP - liftV < 0.0002f) { liftV = LIFT_UP; phase = if (on) Phase.Swinging else Phase.Returning }
        }
        if (phase == Phase.Swinging || phase == Phase.Returning) {
            val target = if (on) grooveAng else ANG_REST
            val steps = max(1, ceil(dt / 0.004f).toInt()); val h = dt / steps
            repeat(steps) {
                val delta = atan2(sin(target - ang), cos(target - ang))
                vel += (36 * delta - 12 * vel) * h
                ang += vel * h
            }
            if (abs(target - ang) < 0.0005f && abs(vel) < 0.003f) {
                ang = target; vel = 0f
                if (!on) phase = Phase.Parked
                else if (spunUp && settle == 1f) phase = Phase.Lowering
            }
        }
        if (phase == Phase.Lowering) {
            if (abs(grooveAng - ang) > 0.002f || settle < 1f) phase = Phase.Lifting
            else {
                ang = grooveAng
                liftV *= exp(-dt * 3.5f)
                if (liftV < 0.00008f) {
                    liftV = 0f; down = true; phase = Phase.Tracking
                    contact(true)
                    main.post { onTick() }
                }
            }
        }
        if (phase == Phase.Tracking) { ang = grooveAng; liftV = 0f; vel = 0f }
        // parked: lower the cue so the tube settles onto the rest cradle (engaging lifts it again before swinging)
        if (phase == Phase.Parked) liftV *= exp(-dt * 5)
        pivot.rot[1] = ang
        lift.rot[0] = liftV
        knob.rot[1] += ((if (on) -KNOB_ON else -KNOB_OFF) - knob.rot[1]) * min(1f, dt * 10)
        lampMat.emissiveIntensity += ((if (on) 2.6f else 0.08f) - lampMat.emissiveIntensity) * dt * 4
        glowIntensity = lampMat.emissiveIntensity * 0.6f
        plateMat.emissiveIntensity = 1.1f + sin(now * 0.0018f) * 0.25f + (if (on) 0.3f else 0f)
    }

    private fun contact(d: Boolean) { val cb = onContact; main.post { cb(d) } }
}
