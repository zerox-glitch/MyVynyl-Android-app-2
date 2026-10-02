package com.vynyl.record.turntable

import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLUtils
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow

/** Linear-space RGB from 0xRRGGBB (three.js converts hex material/light colours from sRGB). */
fun lin(hex: Int): FloatArray {
    fun ch(v: Int): Float { val c = v / 255f; return if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f) }
    return floatArrayOf(ch((hex shr 16) and 0xFF), ch((hex shr 8) and 0xFF), ch(hex and 0xFF))
}
fun lin(argb: Long) = lin((argb and 0xFFFFFF).toInt())

/** A texture backed by a Bitmap so it can be re-uploaded after EGL context loss. */
class Tex(bitmap: Bitmap?, val srgb: Boolean = true) {
    @Volatile var bitmap: Bitmap? = bitmap; private set
    @Volatile private var dirty = true
    var id = 0; private set

    fun replace(b: Bitmap?) { bitmap = b; dirty = true }
    fun invalidateGl() { id = 0; dirty = true }

    /** GL thread. Returns true when a texture is bound-ready. */
    fun ensure(): Boolean {
        val b = bitmap ?: return false
        if (!dirty && id != 0) return true
        if (id == 0) { val ids = IntArray(1); GLES30.glGenTextures(1, ids, 0); id = ids[0] }
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, b, 0)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        dirty = false
        return true
    }
}

/** MeshStandardMaterial subset. Colours are linear RGB. */
class Mat(
    var color: FloatArray = floatArrayOf(1f, 1f, 1f),
    var roughness: Float = 1f,
    var metalness: Float = 0f,
    var emissive: FloatArray = floatArrayOf(0f, 0f, 0f),
    var emissiveIntensity: Float = 1f,
    var opacity: Float = 1f,
    var transparent: Boolean = false,
    var map: Tex? = null,
    var emissiveMap: Tex? = null,
    var bump: Tex? = null,
    var bumpScale: Float = 0f,
    var shadowOnly: Boolean = false,
)

/** GPU buffers for a MeshData (one VAO). */
class Geo(val data: MeshData) {
    var vao = 0; private set
    private var vbo = IntArray(4)
    val count get() = data.idx.size

    fun invalidateGl() { vao = 0 }

    fun ensure() {
        if (vao != 0) return
        val v = IntArray(1); GLES30.glGenVertexArrays(1, v, 0); vao = v[0]
        GLES30.glBindVertexArray(vao)
        GLES30.glGenBuffers(4, vbo, 0)
        fun attr(i: Int, arr: FloatArray, comps: Int) {
            val fb = ByteBuffer.allocateDirect(arr.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(arr); fb.position(0)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[i])
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, arr.size * 4, fb, GLES30.GL_STATIC_DRAW)
            GLES30.glEnableVertexAttribArray(i)
            GLES30.glVertexAttribPointer(i, comps, GLES30.GL_FLOAT, false, 0, 0)
        }
        attr(0, data.pos, 3); attr(1, data.nrm, 3); attr(2, data.uv, 2)
        val ib = ByteBuffer.allocateDirect(data.idx.size * 4).order(ByteOrder.nativeOrder()).asIntBuffer().put(data.idx); ib.position(0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, vbo[3])
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, data.idx.size * 4, ib, GLES30.GL_STATIC_DRAW)
        GLES30.glBindVertexArray(0)
    }

    fun draw() {
        GLES30.glBindVertexArray(vao)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, count, GLES30.GL_UNSIGNED_INT, 0)
        GLES30.glBindVertexArray(0)
    }
}

class Item(val geo: Geo, val mat: Mat, val castShadow: Boolean = true)

/** Minimal scene-graph node; Euler order XYZ like three.js. Radians. */
class Node(x: Float = 0f, y: Float = 0f, z: Float = 0f) {
    val pos = floatArrayOf(x, y, z)
    val rot = floatArrayOf(0f, 0f, 0f)
    val children = ArrayList<Node>()
    val items = ArrayList<Item>()
    var visible = true
    fun add(n: Node): Node { children.add(n); return n }
}

fun compile(vs: String, fs: String): Int {
    fun sh(type: Int, src: String): Int {
        val s = GLES30.glCreateShader(type)
        GLES30.glShaderSource(s, src); GLES30.glCompileShader(s)
        val ok = IntArray(1); GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) Log.e("Turntable", "shader: " + GLES30.glGetShaderInfoLog(s))
        return s
    }
    val p = GLES30.glCreateProgram()
    GLES30.glAttachShader(p, sh(GLES30.GL_VERTEX_SHADER, vs))
    GLES30.glAttachShader(p, sh(GLES30.GL_FRAGMENT_SHADER, fs))
    GLES30.glLinkProgram(p)
    val ok = IntArray(1); GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, ok, 0)
    if (ok[0] == 0) Log.e("Turntable", "link: " + GLES30.glGetProgramInfoLog(p))
    return p
}

const val MAIN_VS = """#version 300 es
layout(location=0) in vec3 aPos;
layout(location=1) in vec3 aNrm;
layout(location=2) in vec2 aUv;
uniform mat4 uModel;
uniform mat4 uViewProj;
out vec3 vPos;
out vec3 vNrm;
out vec2 vUv;
void main() {
  vec4 w = uModel * vec4(aPos, 1.0);
  vPos = w.xyz;
  vNrm = mat3(uModel) * aNrm;
  vUv = aUv;
  gl_Position = uViewProj * w;
}
"""

const val MAIN_FS = """#version 300 es
precision highp float;
precision highp sampler2DShadow;
in vec3 vPos;
in vec3 vNrm;
in vec2 vUv;
out vec4 frag;
uniform vec3 uColor;
uniform float uRough;
uniform float uMetal;
uniform float uOpacity;
uniform vec3 uEmissive;
uniform float uBumpScale;
uniform int uUseMap;
uniform int uUseEmMap;
uniform int uUseBump;
uniform int uShadowOnly;
uniform sampler2D uMap;
uniform sampler2D uEmMap;
uniform sampler2D uBump;
uniform sampler2DShadow uShadow;
uniform mat4 uLightVP;
uniform vec2 uShadowTexel;
uniform vec3 uCam;
uniform vec3 uHemiSky;
uniform vec3 uHemiGround;
uniform vec3 uDirDir[4];
uniform vec3 uDirCol[4];
uniform vec3 uPtPos[3];
uniform vec3 uPtCol[3];
uniform float uPtDist[3];
uniform float uExposure;

const float PI = 3.14159265;
vec3 srgbToLinear(vec3 c) { return mix(c / 12.92, pow((c + 0.055) / 1.055, vec3(2.4)), step(0.04045, c)); }
vec3 linearToSrgb(vec3 c) { return mix(c * 12.92, 1.055 * pow(c, vec3(0.41666)) - 0.055, step(0.0031308, c)); }
vec3 aces(vec3 color) {
  const mat3 inM = mat3(vec3(0.59719, 0.07600, 0.02840), vec3(0.35458, 0.90834, 0.13383), vec3(0.04823, 0.01566, 0.83777));
  const mat3 outM = mat3(vec3(1.60475, -0.10208, -0.00327), vec3(-0.53108, 1.10813, -0.07276), vec3(-0.07367, -0.00605, 1.07602));
  color *= uExposure / 0.6;
  color = inM * color;
  vec3 a = color * (color + 0.0245786) - 0.000090537;
  vec3 b = color * (0.983729 * color + 0.4329510) + 0.238081;
  color = outM * (a / b);
  return clamp(color, 0.0, 1.0);
}
float shadowAt(vec3 n) {
  vec4 lp = uLightVP * vec4(vPos + n * 0.02, 1.0);
  vec3 p = lp.xyz / lp.w * 0.5 + 0.5;
  if (p.x < 0.0 || p.x > 1.0 || p.y < 0.0 || p.y > 1.0 || p.z > 1.0) return 1.0;
  float z = p.z - 0.0005;
  float s = 0.0;
  for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) s += texture(uShadow, vec3(p.xy + vec2(float(x), float(y)) * uShadowTexel * 1.5, z));
  return s / 9.0;
}
vec3 bumpNormal(vec3 n) {
  vec2 dx = dFdx(vUv), dy = dFdy(vUv);
  float h = uBumpScale * texture(uBump, vUv).x;
  float dBx = uBumpScale * texture(uBump, vUv + dx).x - h;
  float dBy = uBumpScale * texture(uBump, vUv + dy).x - h;
  vec3 sx = dFdx(vPos), sy = dFdy(vPos);
  vec3 r1 = cross(sy, n), r2 = cross(n, sx);
  float det = dot(sx, r1);
  vec3 grad = sign(det) * (dBx * r1 + dBy * r2);
  return normalize(abs(det) * n - grad);
}
float dGGX(float a, float nh) { float a2 = a * a; float d = nh * nh * (a2 - 1.0) + 1.0; return a2 / (PI * d * d); }
float vGGX(float a, float nl, float nv) { float a2 = a * a; float gv = nl * sqrt(a2 + (1.0 - a2) * nv * nv); float gl = nv * sqrt(a2 + (1.0 - a2) * nl * nl); return 0.5 / max(gv + gl, 1e-6); }
vec3 fSchlick(vec3 f0, float vh) { float f = exp2((-5.55473 * vh - 6.98316) * vh); return f0 * (1.0 - f) + f; }
vec3 direct(vec3 L, vec3 rad, vec3 N, vec3 V, vec3 diff, vec3 f0, float a) {
  float nl = clamp(dot(N, L), 0.0, 1.0);
  if (nl <= 0.0) return vec3(0.0);
  vec3 H = normalize(L + V);
  float nv = clamp(dot(N, V), 1e-3, 1.0);
  float nh = clamp(dot(N, H), 0.0, 1.0);
  float vh = clamp(dot(V, H), 0.0, 1.0);
  return nl * rad * (diff / PI + fSchlick(f0, vh) * vGGX(a, nl, nv) * dGGX(a, nh));
}
void main() {
  vec3 N = normalize(vNrm);
  if (uShadowOnly == 1) { frag = vec4(0.0, 0.0, 0.0, uOpacity * (1.0 - shadowAt(N))); return; }
  vec2 tuv = vec2(vUv.x, 1.0 - vUv.y);
  vec3 albedo = uColor;
  if (uUseMap == 1) albedo *= srgbToLinear(texture(uMap, tuv).rgb);
  if (uUseBump == 1) N = bumpNormal(N);
  vec3 V = normalize(uCam - vPos);
  float rough = clamp(uRough, 0.0525, 1.0);
  float a = rough * rough;
  vec3 diff = albedo * (1.0 - uMetal);
  vec3 f0 = mix(vec3(0.04), albedo, uMetal);
  vec3 col = mix(uHemiGround, uHemiSky, 0.5 * N.y + 0.5) * diff / PI;
  float sh = shadowAt(normalize(vNrm));
  col += direct(uDirDir[0], uDirCol[0] * sh, N, V, diff, f0, a);
  for (int i = 1; i < 4; i++) col += direct(uDirDir[i], uDirCol[i], N, V, diff, f0, a);
  for (int i = 0; i < 3; i++) {
    vec3 d = uPtPos[i] - vPos;
    float dist = length(d);
    float att = 1.0 / max(dist * dist, 0.01);
    float k = clamp(1.0 - pow(dist / uPtDist[i], 4.0), 0.0, 1.0);
    att *= k * k;
    col += direct(d / dist, uPtCol[i] * att, N, V, diff, f0, a);
  }
  vec3 em = uEmissive;
  if (uUseEmMap == 1) em *= srgbToLinear(texture(uEmMap, tuv).rgb);
  col += em;
  frag = vec4(linearToSrgb(aces(col)), uOpacity);
}
"""

const val DEPTH_VS = """#version 300 es
layout(location=0) in vec3 aPos;
uniform mat4 uModel;
uniform mat4 uLightVP;
void main() { gl_Position = uLightVP * uModel * vec4(aPos, 1.0); }
"""

const val DEPTH_FS = """#version 300 es
precision mediump float;
out vec4 frag;
void main() { frag = vec4(1.0); }
"""

/** Full-screen background matching the Player's CSS radial gradients (sRGB, untonemapped). */
const val BG_VS = """#version 300 es
const vec2 P[3] = vec2[3](vec2(-1.0, -1.0), vec2(3.0, -1.0), vec2(-1.0, 3.0));
void main() { gl_Position = vec4(P[gl_VertexID], 0.0, 1.0); }
"""

const val BG_FS = """#version 300 es
precision highp float;
out vec4 frag;
uniform vec2 uPlayer;
uniform vec2 uOffset;
uniform float uViewH;
void main() {
  vec2 p = vec2(gl_FragCoord.x + uOffset.x, (uViewH - gl_FragCoord.y) + uOffset.y);
  float W = max(uPlayer.x, 1.0), H = max(uPlayer.y, 1.0);
  float d1 = length((p - vec2(0.5 * W, 0.3 * H)) / vec2(0.8 * W, 0.5 * H));
  float a1 = 0.22 * clamp(1.0 - d1 / 0.7, 0.0, 1.0);
  float d2 = length((p - vec2(0.5 * W, H)) / vec2(0.6 * W, 0.4 * H));
  float a2 = 0.18 * clamp(1.0 - d2, 0.0, 1.0);
  vec3 c = vec3(12.0, 10.0, 9.0) / 255.0;
  c = mix(c, vec3(153.0, 27.0, 27.0) / 255.0, a2);
  c = mix(c, vec3(217.0, 119.0, 6.0) / 255.0, a1);
  frag = vec4(c, 1.0);
}
"""
