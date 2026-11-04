package com.vynyl.record.turntable

import android.opengl.Matrix
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** CPU-side mesh: interleaved later into GL buffers. Mirrors the three.js primitives used by Turntable.tsx. */
class MeshData(val pos: FloatArray, val nrm: FloatArray, val uv: FloatArray, val idx: IntArray)

private class FloatBuf {
    var a = FloatArray(1024); var n = 0
    fun add(v: Float) { if (n == a.size) a = a.copyOf(a.size * 2); a[n++] = v }
    fun out() = a.copyOf(n)
}

private class IntBuf {
    var a = IntArray(1024); var n = 0
    fun add(v: Int) { if (n == a.size) a = a.copyOf(a.size * 2); a[n++] = v }
    fun out() = a.copyOf(n)
}

class MeshBuilder {
    private val p = FloatBuf(); private val nr = FloatBuf(); private val t = FloatBuf(); private val i = IntBuf()
    private var count = 0

    fun v(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float, u: Float, w: Float): Int {
        p.add(x); p.add(y); p.add(z)
        val l = sqrt(nx * nx + ny * ny + nz * nz).let { if (it < 1e-8f) 1f else it }
        nr.add(nx / l); nr.add(ny / l); nr.add(nz / l)
        t.add(u); t.add(w)
        return count++
    }

    fun tri(a: Int, b: Int, c: Int) { i.add(a); i.add(b); i.add(c) }
    fun quad(a: Int, b: Int, c: Int, d: Int) { tri(a, b, d); tri(b, c, d) }
    fun build() = MeshData(p.out(), nr.out(), t.out(), i.out())
}

/** Bakes a transform into a mesh (like BufferGeometry.rotateX / translate). Rotation+translation only. */
fun MeshData.transformed(m: FloatArray): MeshData {
    val pos = FloatArray(this.pos.size); val nrm = FloatArray(this.nrm.size)
    val inV = FloatArray(4); val outV = FloatArray(4)
    for (k in 0 until this.pos.size / 3) {
        inV[0] = this.pos[k * 3]; inV[1] = this.pos[k * 3 + 1]; inV[2] = this.pos[k * 3 + 2]; inV[3] = 1f
        Matrix.multiplyMV(outV, 0, m, 0, inV, 0)
        pos[k * 3] = outV[0]; pos[k * 3 + 1] = outV[1]; pos[k * 3 + 2] = outV[2]
        inV[0] = this.nrm[k * 3]; inV[1] = this.nrm[k * 3 + 1]; inV[2] = this.nrm[k * 3 + 2]; inV[3] = 0f
        Matrix.multiplyMV(outV, 0, m, 0, inV, 0)
        nrm[k * 3] = outV[0]; nrm[k * 3 + 1] = outV[1]; nrm[k * 3 + 2] = outV[2]
    }
    return MeshData(pos, nrm, uv.copyOf(), idx.copyOf())
}

fun MeshData.rotatedX(rad: Float): MeshData {
    val m = FloatArray(16); Matrix.setRotateM(m, 0, Math.toDegrees(rad.toDouble()).toFloat(), 1f, 0f, 0f)
    return transformed(m)
}

fun MeshData.translated(x: Float, y: Float, z: Float): MeshData {
    val m = FloatArray(16); Matrix.setIdentityM(m, 0); Matrix.translateM(m, 0, x, y, z)
    return transformed(m)
}

private const val TAU = (PI * 2).toFloat()

/** three.js CylinderGeometry (Y axis, centred, capped). */
fun cylinder(rTop: Float, rBottom: Float, h: Float, seg: Int): MeshData {
    val b = MeshBuilder()
    val half = h / 2; val slope = (rBottom - rTop) / h
    val top = IntArray(seg + 1); val bot = IntArray(seg + 1)
    for (s in 0..seg) {
        val th = s.toFloat() / seg * TAU
        val sx = sin(th); val cz = cos(th)
        top[s] = b.v(rTop * sx, half, rTop * cz, sx, slope, cz, s.toFloat() / seg, 1f)
        bot[s] = b.v(rBottom * sx, -half, rBottom * cz, sx, slope, cz, s.toFloat() / seg, 0f)
    }
    for (s in 0 until seg) b.quad(top[s], bot[s], bot[s + 1], top[s + 1])
    for ((r, y, ny) in listOf(Triple(rTop, half, 1f), Triple(rBottom, -half, -1f))) {
        if (r <= 0f) continue
        val c = b.v(0f, y, 0f, 0f, ny, 0f, 0.5f, 0.5f)
        val ring = IntArray(seg + 1)
        for (s in 0..seg) {
            val th = s.toFloat() / seg * TAU
            val x = r * sin(th); val z = r * cos(th)
            ring[s] = b.v(x, y, z, 0f, ny, 0f, x / r * 0.5f + 0.5f, -z / r * 0.5f * ny + 0.5f)
        }
        for (s in 0 until seg) b.tri(c, ring[s], ring[s + 1])
    }
    return b.build()
}

fun cone(r: Float, h: Float, seg: Int) = cylinder(0f, r, h, seg)

/** three.js BoxGeometry. */
fun box(w: Float, h: Float, d: Float): MeshData {
    val b = MeshBuilder()
    val x = w / 2; val y = h / 2; val z = d / 2
    fun face(nx: Float, ny: Float, nz: Float, c: Array<FloatArray>) {
        val i0 = b.v(c[0][0], c[0][1], c[0][2], nx, ny, nz, 0f, 0f)
        val i1 = b.v(c[1][0], c[1][1], c[1][2], nx, ny, nz, 1f, 0f)
        val i2 = b.v(c[2][0], c[2][1], c[2][2], nx, ny, nz, 1f, 1f)
        val i3 = b.v(c[3][0], c[3][1], c[3][2], nx, ny, nz, 0f, 1f)
        b.quad(i0, i1, i2, i3)
    }
    face(1f, 0f, 0f, arrayOf(floatArrayOf(x, -y, z), floatArrayOf(x, -y, -z), floatArrayOf(x, y, -z), floatArrayOf(x, y, z)))
    face(-1f, 0f, 0f, arrayOf(floatArrayOf(-x, -y, -z), floatArrayOf(-x, -y, z), floatArrayOf(-x, y, z), floatArrayOf(-x, y, -z)))
    face(0f, 1f, 0f, arrayOf(floatArrayOf(-x, y, z), floatArrayOf(x, y, z), floatArrayOf(x, y, -z), floatArrayOf(-x, y, -z)))
    face(0f, -1f, 0f, arrayOf(floatArrayOf(-x, -y, -z), floatArrayOf(x, -y, -z), floatArrayOf(x, -y, z), floatArrayOf(-x, -y, z)))
    face(0f, 0f, 1f, arrayOf(floatArrayOf(-x, -y, z), floatArrayOf(x, -y, z), floatArrayOf(x, y, z), floatArrayOf(-x, y, z)))
    face(0f, 0f, -1f, arrayOf(floatArrayOf(x, -y, -z), floatArrayOf(-x, -y, -z), floatArrayOf(-x, y, -z), floatArrayOf(x, y, -z)))
    return b.build()
}

/** three.js SphereGeometry incl. partial phi/theta ranges. */
fun sphere(r: Float, wSeg: Int, hSeg: Int, phiStart: Float = 0f, phiLen: Float = TAU, thStart: Float = 0f, thLen: Float = PI.toFloat()): MeshData {
    val b = MeshBuilder()
    val grid = Array(hSeg + 1) { IntArray(wSeg + 1) }
    for (iy in 0..hSeg) {
        val v = iy.toFloat() / hSeg
        for (ix in 0..wSeg) {
            val u = ix.toFloat() / wSeg
            val phi = phiStart + u * phiLen; val th = thStart + v * thLen
            val x = -r * cos(phi) * sin(th); val y = r * cos(th); val z = r * sin(phi) * sin(th)
            grid[iy][ix] = b.v(x, y, z, x, y, z, u, 1 - v)
        }
    }
    for (iy in 0 until hSeg) for (ix in 0 until wSeg) b.quad(grid[iy][ix + 1], grid[iy][ix], grid[iy + 1][ix], grid[iy + 1][ix + 1])
    return b.build()
}

/** three.js CircleGeometry: XY plane facing +Z. */
fun circle(r: Float, seg: Int): MeshData {
    val b = MeshBuilder()
    val c = b.v(0f, 0f, 0f, 0f, 0f, 1f, 0.5f, 0.5f)
    val ring = IntArray(seg + 1)
    for (s in 0..seg) {
        val th = s.toFloat() / seg * TAU
        val x = r * cos(th); val y = r * sin(th)
        ring[s] = b.v(x, y, 0f, 0f, 0f, 1f, x / r * 0.5f + 0.5f, y / r * 0.5f + 0.5f)
    }
    for (s in 0 until seg) b.tri(c, ring[s], ring[s + 1])
    return b.build()
}

/** three.js PlaneGeometry: XY plane facing +Z. */
fun plane(w: Float, h: Float): MeshData {
    val b = MeshBuilder()
    val a = b.v(-w / 2, -h / 2, 0f, 0f, 0f, 1f, 0f, 0f)
    val c = b.v(w / 2, -h / 2, 0f, 0f, 0f, 1f, 1f, 0f)
    val d = b.v(w / 2, h / 2, 0f, 0f, 0f, 1f, 1f, 1f)
    val e = b.v(-w / 2, h / 2, 0f, 0f, 0f, 1f, 0f, 1f)
    b.quad(a, c, d, e)
    return b.build()
}

/** three.js TorusGeometry (in XY plane). */
fun torus(R: Float, tube: Float, radialSeg: Int, tubularSeg: Int): MeshData {
    val b = MeshBuilder()
    val grid = Array(radialSeg + 1) { IntArray(tubularSeg + 1) }
    for (j in 0..radialSeg) for (i in 0..tubularSeg) {
        val u = i.toFloat() / tubularSeg * TAU; val v = j.toFloat() / radialSeg * TAU
        val x = (R + tube * cos(v)) * cos(u); val y = (R + tube * cos(v)) * sin(u); val z = tube * sin(v)
        val cx = R * cos(u); val cy = R * sin(u)
        grid[j][i] = b.v(x, y, z, x - cx, y - cy, z, i.toFloat() / tubularSeg, j.toFloat() / radialSeg)
    }
    for (j in 1..radialSeg) for (i in 1..tubularSeg) b.quad(grid[j][i - 1], grid[j - 1][i - 1], grid[j - 1][i], grid[j][i])
    return b.build()
}

/** TubeGeometry along a uniform Catmull-Rom spline through [pts] (each a float[3]). */
fun tube(pts: List<FloatArray>, segs: Int, radius: Float, radial: Int): MeshData {
    val n = pts.size
    fun at(i: Int): FloatArray = when {
        i < 0 -> FloatArray(3) { k -> 2 * pts[0][k] - pts[1][k] }
        i >= n -> FloatArray(3) { k -> 2 * pts[n - 1][k] - pts[n - 2][k] }
        else -> pts[i]
    }
    fun point(t: Float): FloatArray {
        val f = t * (n - 1); var i = f.toInt(); if (i >= n - 1) i = n - 2
        val w = f - i
        val p0 = at(i - 1); val p1 = at(i); val p2 = at(i + 1); val p3 = at(i + 2)
        val w2 = w * w; val w3 = w2 * w
        return FloatArray(3) { k ->
            0.5f * (2 * p1[k] + (-p0[k] + p2[k]) * w + (2 * p0[k] - 5 * p1[k] + 4 * p2[k] - p3[k]) * w2 + (-p0[k] + 3 * p1[k] - 3 * p2[k] + p3[k]) * w3)
        }
    }
    fun norm(v: FloatArray): FloatArray { val l = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).coerceAtLeast(1e-8f); return floatArrayOf(v[0] / l, v[1] / l, v[2] / l) }
    fun cross(a: FloatArray, c: FloatArray) = floatArrayOf(a[1] * c[2] - a[2] * c[1], a[2] * c[0] - a[0] * c[2], a[0] * c[1] - a[1] * c[0])
    val centres = (0..segs).map { point(it.toFloat() / segs) }
    val tangents = (0..segs).map { i ->
        val a = centres[maxOf(0, i - 1)]; val c = centres[minOf(segs, i + 1)]
        norm(floatArrayOf(c[0] - a[0], c[1] - a[1], c[2] - a[2]))
    }
    var normal = norm(cross(tangents[0], floatArrayOf(0f, 1f, 0f)))
    val b = MeshBuilder()
    val grid = Array(segs + 1) { IntArray(radial + 1) }
    for (i in 0..segs) {
        val t = tangents[i]
        val d = normal[0] * t[0] + normal[1] * t[1] + normal[2] * t[2]
        normal = norm(floatArrayOf(normal[0] - t[0] * d, normal[1] - t[1] * d, normal[2] - t[2] * d))
        val bin = cross(t, normal)
        for (j in 0..radial) {
            val v = j.toFloat() / radial * TAU
            val s = -cos(v); val c = sin(v)
            val nx = s * normal[0] + c * bin[0]; val ny = s * normal[1] + c * bin[1]; val nz = s * normal[2] + c * bin[2]
            val p = centres[i]
            grid[i][j] = b.v(p[0] + radius * nx, p[1] + radius * ny, p[2] + radius * nz, nx, ny, nz, i.toFloat() / segs, j.toFloat() / radial)
        }
    }
    for (i in 1..segs) for (j in 1..radial) b.quad(grid[i - 1][j - 1], grid[i][j - 1], grid[i][j], grid[i - 1][j])
    return b.build()
}

/**
 * The plinth: a rounded rectangle (half extents w, d, corner r) extruded by [depth] with a chamfered bevel,
 * built in shape space (XY outline, +Z extrusion) like THREE.ExtrudeGeometry.
 */
fun roundedSlab(w: Float, d: Float, r: Float, depth: Float, bevel: Float, curveSegs: Int = 12): MeshData {
    // outline (counter-clockwise), with per-point outward normals
    val pts = ArrayList<FloatArray>()
    fun quadTo(sx: Float, sy: Float, cx: Float, cy: Float, ex: Float, ey: Float) {
        for (k in 1..curveSegs) {
            val t = k.toFloat() / curveSegs; val m = 1 - t
            pts.add(floatArrayOf(m * m * sx + 2 * m * t * cx + t * t * ex, m * m * sy + 2 * m * t * cy + t * t * ey))
        }
    }
    pts.add(floatArrayOf(-w + r, -d)); pts.add(floatArrayOf(w - r, -d))
    quadTo(w - r, -d, w, -d, w, -d + r); pts.add(floatArrayOf(w, d - r))
    quadTo(w, d - r, w, d, w - r, d); pts.add(floatArrayOf(-w + r, d))
    quadTo(-w + r, d, -w, d, -w, d - r); pts.add(floatArrayOf(-w, -d + r))
    quadTo(-w, -d + r, -w, -d, -w + r, -d)
    if (pts.size > 1 && pts.last()[0] == pts[0][0] && pts.last()[1] == pts[0][1]) pts.removeAt(pts.size - 1)
    val n = pts.size
    val nrm = Array(n) { i ->
        val a = pts[(i - 1 + n) % n]; val c = pts[(i + 1) % n]
        val tx = c[0] - a[0]; val ty = c[1] - a[1]
        val l = sqrt(tx * tx + ty * ty).coerceAtLeast(1e-8f)
        floatArrayOf(ty / l, -tx / l)
    }
    val b = MeshBuilder()
    // rings: (offset, z)
    val rings = listOf(0f to -bevel, bevel to 0f, bevel to depth, 0f to depth + bevel)
    for (band in 0 until 3) {
        val (o0, z0) = rings[band]; val (o1, z1) = rings[band + 1]
        val nz = when (band) { 0 -> -1f; 2 -> 1f; else -> 0f }
        val a = IntArray(n + 1); val c = IntArray(n + 1)
        for (i in 0..n) {
            val p = pts[i % n]; val q = nrm[i % n]
            a[i] = b.v(p[0] + q[0] * o0, p[1] + q[1] * o0, z0, q[0], q[1], nz, i.toFloat() / n, 0f)
            c[i] = b.v(p[0] + q[0] * o1, p[1] + q[1] * o1, z1, q[0], q[1], nz, i.toFloat() / n, 1f)
        }
        for (i in 0 until n) b.quad(a[i], a[i + 1], c[i + 1], c[i])
    }
    for ((z, nz) in listOf(-bevel to -1f, depth + bevel to 1f)) {
        val centre = b.v(0f, 0f, z, 0f, 0f, nz, 0.5f, 0.5f)
        val ring = IntArray(n + 1) { i -> val p = pts[i % n]; b.v(p[0], p[1], z, 0f, 0f, nz, p[0] / (2 * w) + 0.5f, p[1] / (2 * d) + 0.5f) }
        for (i in 0 until n) b.tri(centre, ring[i], ring[i + 1])
    }
    return b.build()
}
