package vn.paperworld.phidoi.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

/** A compiled shader program and its uniform locations. */
class Program(val gl: GL, val id: Int) {
    private val locs = HashMap<String, Int>()
    var frame = -1
    fun loc(name: String): Int = locs.getOrPut(name) { gl.getUniformLocation(id, name) }
    fun f(name: String, v: Double) { val l = loc(name); if (l >= 0) gl.uniform1f(l, v.toFloat()) }
    fun i(name: String, v: Int) { val l = loc(name); if (l >= 0) gl.uniform1i(l, v) }
    fun v2(name: String, a: Double, b: Double) { val l = loc(name); if (l >= 0) gl.uniform2f(l, a.toFloat(), b.toFloat()) }
    fun v3(name: String, a: Double, b: Double, c: Double) { val l = loc(name); if (l >= 0) gl.uniform3f(l, a.toFloat(), b.toFloat(), c.toFloat()) }
    fun v3(name: String, v: Vec3) = v3(name, v.x, v.y, v.z)
    fun col(name: String, c: Color, k: Double = 1.0) = v3(name, c.r * k, c.g * k, c.b * k)
    fun m4(name: String, m: FloatArray) { val l = loc(name); if (l >= 0) gl.uniformMatrix4fv(l, m) }
    fun m3(name: String, m: FloatArray) { val l = loc(name); if (l >= 0) gl.uniformMatrix3fv(l, m) }
}

private class GpuGeo {
    var pos = 0; var nor = 0; var uv = 0; var col = 0; var onor = 0; var ibo = 0; var vao = 0
    var posV = -1; var norV = -1; var colV = -1
    var posCap = 0; var colCap = 0
}

private class GpuInst { var mat = 0; var col = 0; var matV = -1; var colV = -1; val vaos = HashMap<Geometry, Int>() }

private class Item {
    lateinit var obj: Object3D
    lateinit var geo: Geometry
    lateinit var mat: Material
    var inst: InstanceData? = null
    var start = 0
    var count = -1
    var mode = GLC.TRIANGLES
    var prog: Program? = null
    var z = 0.0
    var order = 0.0
    var progId = 0
}

/**
 * Forward renderer for the paper world: toon lighting from one sun + a hemisphere light, linear fog,
 * a sun shadow map, inverted-hull ink outlines and instancing. Output is linear colour; [Pipeline] adds the post pass.
 */
class Renderer(val gl: GL) {
    /* lighting + fog (linear colours) */
    val sunDir = Vec3(0.0, 1.0, 0.0)
    val sunColor = Color(); var sunIntensity = 2.6
    val hemiSky = Color(); val hemiGround = Color(); var hemiIntensity = 1.4
    val fogColor = Color.of("#eef3f5"); var fogNear = 600.0; var fogFar = 3000.0
    val ink = Color.of("#1f1d2b")
    /* shadows */
    var shadowSize = 2048
    var shadowEnabled = true
    var shadowExtent = 80.0
    val shadowTarget = Vec3()
    var shadowBias = -0.0006
    var shadowNormalBias = 0.08
    private var shadowFbo = 0
    private var shadowTex = 0
    private val shadowMat = Mat4()
    private val lightView = Mat4()
    private val lightProj = Mat4()
    private val lpF = FloatArray(16)
    private val lvF = FloatArray(16)
    var drawingBufferHeight = 720
    var drawCalls = 0

    private val programs = HashMap<String, Program>()
    private val opaque = ArrayList<Item>()
    private val transparent = ArrayList<Item>()
    private val shadowList = ArrayList<Item>()
    private val pool = ArrayList<Item>()
    private var poolN = 0
    private val frustum = Frustum()
    private val viewProj = Mat4()
    private val tmpSphere = Sphere()
    private val fm16 = FloatArray(16)
    private val fm16b = FloatArray(16)
    private val fm9 = FloatArray(9)
    private val nm = Mat3()
    private var frame = 0
    private var scratch: ByteBuffer = ByteBuffer.allocateDirect(1 shl 16).order(ByteOrder.nativeOrder())
    private var camera: Camera? = null
    private val camPos = Vec3()
    private val projF = FloatArray(16)
    private val viewF = FloatArray(16)
    private var curProg = -1
    private var aniso = 0f

    init {
        gl.vertexAttrib4f(3, 1f, 1f, 1f, 1f)
        if (gl.hasExtension("GL_EXT_texture_filter_anisotropic")) aniso = minOf(8f, gl.getInteger(GLC.MAX_TEXTURE_MAX_ANISOTROPY_EXT).toFloat())
    }

    /* ---------- programs ---------- */
    fun program(key: String, vs: String, fs: String, defines: List<String>): Program = programs.getOrPut(key) {
        val head = Shaders.HEAD + defines.joinToString("") { "#define $it\n" }
        fun compile(type: Int, src: String): Int {
            val s = gl.createShader(type)
            gl.shaderSource(s, head + src)
            gl.compileShader(s)
            if (!gl.getShaderCompiled(s)) error("shader compile failed [$key]: ${gl.getShaderInfoLog(s)}")
            return s
        }
        val v = compile(GLC.VERTEX_SHADER, vs); val f = compile(GLC.FRAGMENT_SHADER, fs)
        val p = gl.createProgram()
        gl.attachShader(p, v); gl.attachShader(p, f)
        gl.linkProgram(p)
        if (!gl.getProgramLinked(p)) error("program link failed [$key]: ${gl.getProgramInfoLog(p)}")
        gl.deleteShader(v); gl.deleteShader(f)
        Program(gl, p)
    }

    /* ---------- buffers ---------- */
    fun buf(bytes: Int): ByteBuffer {
        if (scratch.capacity() < bytes) scratch = ByteBuffer.allocateDirect(max(bytes, scratch.capacity() * 2)).order(ByteOrder.nativeOrder())
        scratch.clear()
        return scratch
    }
    fun floats(a: FloatArray, n: Int = a.size): ByteBuffer { val b = buf(n * 4); b.asFloatBuffer().put(a, 0, n); b.limit(n * 4); return b }
    private fun ints(a: IntArray): ByteBuffer { val b = buf(a.size * 4); b.asIntBuffer().put(a); b.limit(a.size * 4); return b }

    private fun upload(target: Int, id: Int, data: FloatArray, dynamic: Boolean) {
        gl.bindBuffer(target, id)
        gl.bufferData(target, floats(data), if (dynamic) GLC.DYNAMIC_DRAW else GLC.STATIC_DRAW)
    }

    private fun gpuGeo(g: Geometry): GpuGeo {
        var gg = g.gpu as GpuGeo?
        if (gg == null) {
            gg = GpuGeo()
            g.gpu = gg
            gg.pos = gl.genBuffer(); upload(GLC.ARRAY_BUFFER, gg.pos, g.position, g.dynamic); gg.posV = g.posVersion; gg.posCap = g.position.size
            g.normal?.let { gg.nor = gl.genBuffer(); upload(GLC.ARRAY_BUFFER, gg.nor, it, g.dynamic); gg.norV = g.norVersion }
            g.uv?.let { gg.uv = gl.genBuffer(); upload(GLC.ARRAY_BUFFER, gg.uv, it, false) }
            g.color?.let { gg.col = gl.genBuffer(); upload(GLC.ARRAY_BUFFER, gg.col, it, g.dynamic); gg.colV = g.colVersion; gg.colCap = it.size }
            g.onormal?.let { gg.onor = gl.genBuffer(); upload(GLC.ARRAY_BUFFER, gg.onor, it, false) }
            g.index?.let { gg.ibo = gl.genBuffer(); gl.bindBuffer(GLC.ELEMENT_ARRAY_BUFFER, gg.ibo); gl.bufferData(GLC.ELEMENT_ARRAY_BUFFER, ints(it), GLC.STATIC_DRAW) }
        } else {
            if (gg.posV != g.posVersion) {
                gl.bindBuffer(GLC.ARRAY_BUFFER, gg.pos)
                val n = if (g.drawCount >= 0 && g.dynamic) minOf(g.position.size, g.drawCount * 3) else g.position.size
                if (g.position.size != gg.posCap) { gl.bufferData(GLC.ARRAY_BUFFER, floats(g.position), GLC.DYNAMIC_DRAW); gg.posCap = g.position.size }
                else gl.bufferSubData(GLC.ARRAY_BUFFER, 0, floats(g.position, max(n, 0)))
                gg.posV = g.posVersion
            }
            if (gg.colV != g.colVersion && g.color != null && gg.col != 0) {
                val c = g.color!!
                gl.bindBuffer(GLC.ARRAY_BUFFER, gg.col)
                if (c.size != gg.colCap) { gl.bufferData(GLC.ARRAY_BUFFER, floats(c), GLC.DYNAMIC_DRAW); gg.colCap = c.size } else gl.bufferSubData(GLC.ARRAY_BUFFER, 0, floats(c))
                gg.colV = g.colVersion
            }
            if (gg.norV != g.norVersion && g.normal != null && gg.nor != 0) { upload(GLC.ARRAY_BUFFER, gg.nor, g.normal!!, g.dynamic); gg.norV = g.norVersion }
        }
        return gg
    }

    private fun bindGeoAttribs(g: Geometry, gg: GpuGeo) {
        gl.bindBuffer(GLC.ARRAY_BUFFER, gg.pos); gl.enableVertexAttribArray(0); gl.vertexAttribPointer(0, 3, GLC.FLOAT, false, 0, 0)
        if (gg.nor != 0) { gl.bindBuffer(GLC.ARRAY_BUFFER, gg.nor); gl.enableVertexAttribArray(1); gl.vertexAttribPointer(1, 3, GLC.FLOAT, false, 0, 0) } else gl.disableVertexAttribArray(1)
        if (gg.uv != 0) { gl.bindBuffer(GLC.ARRAY_BUFFER, gg.uv); gl.enableVertexAttribArray(2); gl.vertexAttribPointer(2, 2, GLC.FLOAT, false, 0, 0) } else gl.disableVertexAttribArray(2)
        if (gg.col != 0) { gl.bindBuffer(GLC.ARRAY_BUFFER, gg.col); gl.enableVertexAttribArray(3); gl.vertexAttribPointer(3, g.colorSize, GLC.FLOAT, false, 0, 0) } else gl.disableVertexAttribArray(3)
        if (gg.onor != 0) { gl.bindBuffer(GLC.ARRAY_BUFFER, gg.onor); gl.enableVertexAttribArray(4); gl.vertexAttribPointer(4, 3, GLC.FLOAT, false, 0, 0) } else gl.disableVertexAttribArray(4)
        if (gg.ibo != 0) gl.bindBuffer(GLC.ELEMENT_ARRAY_BUFFER, gg.ibo)
    }

    private fun vao(g: Geometry, inst: InstanceData?): Int {
        // the element-array binding is VAO state: never upload index buffers with a VAO bound
        gl.bindVertexArray(0)
        val gg = gpuGeo(g)
        if (inst == null) {
            if (gg.vao == 0) { gg.vao = gl.genVertexArray(); gl.bindVertexArray(gg.vao); bindGeoAttribs(g, gg); gl.bindVertexArray(0) }
            return gg.vao
        }
        var gi = inst.gpu as GpuInst?
        if (gi == null) { gi = GpuInst(); inst.gpu = gi }
        if (gi.mat == 0) gi.mat = gl.genBuffer()
        if (gi.matV != inst.version) { upload(GLC.ARRAY_BUFFER, gi.mat, inst.matrices, true); gi.matV = inst.version }
        val c = inst.colors
        if (c != null) {
            if (gi.col == 0) gi.col = gl.genBuffer()
            if (gi.colV != inst.colorVersion) { upload(GLC.ARRAY_BUFFER, gi.col, c, true); gi.colV = inst.colorVersion }
        }
        return gi.vaos.getOrPut(g) {
            val v = gl.genVertexArray()
            gl.bindVertexArray(v)
            bindGeoAttribs(g, gg)
            gl.bindBuffer(GLC.ARRAY_BUFFER, gi.mat)
            for (k in 0 until 4) { gl.enableVertexAttribArray(5 + k); gl.vertexAttribPointer(5 + k, 4, GLC.FLOAT, false, 64, k * 16); gl.vertexAttribDivisor(5 + k, 1) }
            if (gi.col != 0) { gl.bindBuffer(GLC.ARRAY_BUFFER, gi.col); gl.enableVertexAttribArray(9); gl.vertexAttribPointer(9, 3, GLC.FLOAT, false, 0, 0); gl.vertexAttribDivisor(9, 1) }
            gl.bindVertexArray(0)
            v
        }
    }

    /** free GPU memory held by a geometry (terrain LOD swaps) */
    fun dispose(g: Geometry) {
        val gg = g.gpu as GpuGeo? ?: return
        for (b in intArrayOf(gg.pos, gg.nor, gg.uv, gg.col, gg.onor, gg.ibo)) if (b != 0) gl.deleteBuffer(b)
        if (gg.vao != 0) gl.deleteVertexArray(gg.vao)
        g.gpu = null
    }

    /* ---------- textures ---------- */
    fun bindTexture(t: Texture, unit: Int) {
        gl.activeTexture(GLC.TEXTURE0 + unit)
        if (t.gpu == 0) t.gpu = gl.genTexture()
        gl.bindTexture(GLC.TEXTURE_2D, t.gpu)
        if (t.gpuVersion != t.version) {
            t.gpuVersion = t.version
            gl.pixelStorei(GLC.UNPACK_ALIGNMENT, 1)
            val px = t.pixels
            val data: ByteBuffer
            if (px != null) {
                data = buf(px.size * 4)
                for (p in px) { data.put(((p shr 16) and 255).toByte()); data.put(((p shr 8) and 255).toByte()); data.put((p and 255).toByte()); data.put(((p ushr 24) and 255).toByte()) }
                data.flip()
            } else { val b = t.bytes!!; data = buf(b.size); data.put(b); data.flip() }
            gl.texImage2D(GLC.TEXTURE_2D, 0, if (t.srgb) GLC.SRGB8_ALPHA8 else GLC.RGBA8, t.width, t.height, GLC.RGBA, GLC.UNSIGNED_BYTE, data)
            val wrap = if (t.repeat) GLC.REPEAT else GLC.CLAMP_TO_EDGE
            gl.texParameteri(GLC.TEXTURE_2D, GLC.TEXTURE_WRAP_S, wrap); gl.texParameteri(GLC.TEXTURE_2D, GLC.TEXTURE_WRAP_T, wrap)
            if (t.nearest) { gl.texParameteri(GLC.TEXTURE_2D, GLC.TEXTURE_MIN_FILTER, GLC.NEAREST); gl.texParameteri(GLC.TEXTURE_2D, GLC.TEXTURE_MAG_FILTER, GLC.NEAREST) }
            else {
                gl.texParameteri(GLC.TEXTURE_2D, GLC.TEXTURE_MAG_FILTER, GLC.LINEAR)
                if (t.mipmaps) { gl.generateMipmap(GLC.TEXTURE_2D); gl.texParameteri(GLC.TEXTURE_2D, GLC.TEXTURE_MIN_FILTER, GLC.LINEAR_MIPMAP_LINEAR); if (aniso > 1) gl.texParameterf(GLC.TEXTURE_2D, GLC.TEXTURE_MAX_ANISOTROPY_EXT, aniso) }
                else gl.texParameteri(GLC.TEXTURE_2D, GLC.TEXTURE_MIN_FILTER, GLC.LINEAR)
            }
        }
    }

    /* ---------- render list ---------- */
    private fun item(): Item { if (poolN == pool.size) pool.add(Item()); return pool[poolN++] }

    private fun visibleSphere(o: Object3D, g: Geometry, inst: InstanceData?): Boolean {
        if (!o.frustumCulled) return true
        val s = if (inst != null) (inst.boundingSphere ?: return true) else (g.boundingSphere ?: g.computeBoundingSphere())
        tmpSphere.copy(s).applyMatrix4(o.matrixWorld)
        return frustum.intersectsSphere(tmpSphere.center, tmpSphere.radius)
    }

    private fun collect(o: Object3D, cam: Camera) {
        if (!o.visible) return
        when (o) {
            is Mesh -> {
                val inst = (o as? InstancedMesh)?.data
                if (inst != null && inst.count == 0) { /* nothing */ }
                else if (visibleSphere(o, o.geometry, inst)) {
                    val mats = o.materials
                    if (mats != null) for (gr in o.geometry.groups) push(o, o.geometry, mats[gr[2]], inst, gr[0], gr[1], GLC.TRIANGLES)
                    else push(o, o.geometry, o.material, inst, 0, -1, GLC.TRIANGLES)
                }
                if (o.castShadow && shadowEnabled && o.material !is OutlineMaterial && o.material.visible) {
                    val it = item(); it.obj = o; it.geo = o.geometry; it.mat = o.material; it.inst = inst; it.start = 0; it.count = -1; it.mode = GLC.TRIANGLES
                    shadowList.add(it)
                }
            }
            is Points -> if (visibleSphere(o, o.geometry, null)) push(o, o.geometry, o.material, null, 0, -1, GLC.POINTS)
            is Lines -> if (visibleSphere(o, o.geometry, null)) push(o, o.geometry, o.material, null, 0, -1, if (o.strip) GLC.LINE_STRIP else GLC.LINES)
        }
        for (i in o.children.indices) collect(o.children[i], cam)
    }

    private fun push(o: Object3D, g: Geometry, m: Material, inst: InstanceData?, start: Int, count: Int, mode: Int) {
        if (!m.visible) return
        val it = item()
        it.obj = o; it.geo = g; it.mat = m; it.inst = inst; it.start = start; it.count = count; it.mode = mode
        it.order = o.renderOrder
        val e = o.matrixWorld.e
        val cx = e[12] - camPos.x; val cy = e[13] - camPos.y; val cz = e[14] - camPos.z
        it.z = cx * cx + cy * cy + cz * cz
        it.prog = programFor(it)
        it.progId = it.prog!!.id
        if (m.transparent) transparent.add(it) else opaque.add(it)
    }

    private val sigCache = HashMap<Int, Program>()
    private fun programFor(it: Item): Program {
        val m = it.mat
        val kind = when (m) { is ToonMaterial -> 1; is OutlineMaterial -> 2; is BasicMaterial -> 3; is LineMaterial -> 4; is PointsMaterial -> 5; is SkyMaterial -> 6; is SeaMaterial -> 7; else -> error("unknown material ${m::class}") }
        var sig = kind
        if (it.inst != null) sig = sig or (1 shl 4)
        if (it.inst?.colors != null) sig = sig or (1 shl 5)
        val vcol = when (m) { is ToonMaterial -> m.vertexColors; is BasicMaterial -> m.vertexColors; is LineMaterial -> m.vertexColors; is PointsMaterial -> m.vertexColors; else -> false } && it.geo.color != null
        if (vcol) sig = sig or (1 shl 6)
        val hasMap = when (m) { is ToonMaterial -> m.map != null; is BasicMaterial -> m.map != null; is PointsMaterial -> m.map != null; else -> false }
        if (hasMap) sig = sig or (1 shl 7)
        if (m is ToonMaterial) {
            if (m.emissiveTint) sig = sig or (1 shl 8)
            if (m.emissiveMap && m.map != null) sig = sig or (1 shl 14)
            if (m.building != null) sig = sig or (1 shl 9)
            if (m.side == Side.DOUBLE) sig = sig or (1 shl 10)
            if (it.obj.receiveShadow && shadowEnabled) sig = sig or (1 shl 11)
        }
        if (m.fog && kind != 2 && kind != 6 && kind != 7) sig = sig or (1 shl 12)
        if (m is PointsMaterial && m.sizeAttenuation) sig = sig or (1 shl 13)
        sigCache[sig]?.let { return it }
        val d = ArrayList<String>()
        if (sig and (1 shl 4) != 0) d.add("INSTANCED")
        if (sig and (1 shl 5) != 0) d.add("ICOLOR")
        if (sig and (1 shl 6) != 0) d.add("VCOLOR")
        if (sig and (1 shl 7) != 0) d.add("MAP")
        if (sig and (1 shl 8) != 0) d.add("EMTINT")
        if (sig and (1 shl 9) != 0) d.add("BUILDING")
        if (sig and (1 shl 10) != 0) d.add("DOUBLE")
        if (sig and (1 shl 11) != 0) d.add("SHADOW")
        if (sig and (1 shl 12) != 0) d.add("FOG")
        if (sig and (1 shl 13) != 0) d.add("ATTEN")
        if (sig and (1 shl 14) != 0) d.add("EMMAP")
        val key = "p$sig"
        val p = when (kind) {
            1 -> program(key, Shaders.TOON_VS, Shaders.TOON_FS, d)
            2 -> program(key, Shaders.OUTLINE_VS, Shaders.OUTLINE_FS, d)
            3, 4 -> program(key, Shaders.BASIC_VS, Shaders.BASIC_FS, d)
            5 -> program(key, Shaders.POINTS_VS, Shaders.POINTS_FS, d)
            6 -> program(key, Shaders.SKY_VS, Shaders.SKY_FS, d)
            else -> program(key, Shaders.SEA_VS, Shaders.SEA_FS, d)
        }
        sigCache[sig] = p
        return p
    }

    private val opaqueOrder = Comparator<Item> { a, b ->
        when {
            a.order != b.order -> a.order.compareTo(b.order)
            a.progId != b.progId -> a.progId - b.progId
            a.mat.id != b.mat.id -> a.mat.id - b.mat.id
            else -> a.z.compareTo(b.z)
        }
    }
    private val transOrder = Comparator<Item> { a, b -> if (a.order != b.order) a.order.compareTo(b.order) else b.z.compareTo(a.z) }

    /* ---------- state ---------- */
    private var stDepthTest = -1; private var stDepthWrite = -1; private var stCull = -2; private var stBlend = -2; private var stPoly = -1
    private fun resetState() { stDepthTest = -1; stDepthWrite = -1; stCull = -2; stBlend = -2; stPoly = -1; curProg = -1 }
    private fun setDepth(test: Boolean, write: Boolean) {
        val t = if (test) 1 else 0; val w = if (write) 1 else 0
        if (t != stDepthTest) { if (test) gl.enable(GLC.DEPTH_TEST) else gl.disable(GLC.DEPTH_TEST); stDepthTest = t }
        if (w != stDepthWrite) { gl.depthMask(write); stDepthWrite = w }
    }
    /** -1 = no culling, else the GL face to cull */
    private fun setCull(face: Int) {
        if (face == stCull) return
        if (face == -1) gl.disable(GLC.CULL_FACE) else { if (stCull == -1 || stCull == -2) gl.enable(GLC.CULL_FACE); gl.cullFace(face) }
        stCull = face
    }
    private fun setBlend(m: Material) {
        val b = if (m.blending == Blend.ADDITIVE) 2 else if (m.transparent) 1 else 0
        if (b == stBlend) return
        when (b) {
            0 -> gl.disable(GLC.BLEND)
            1 -> { gl.enable(GLC.BLEND); gl.blendEquation(GLC.FUNC_ADD); gl.blendFuncSeparate(GLC.SRC_ALPHA, GLC.ONE_MINUS_SRC_ALPHA, GLC.ONE, GLC.ONE_MINUS_SRC_ALPHA) }
            2 -> { gl.enable(GLC.BLEND); gl.blendEquation(GLC.FUNC_ADD); gl.blendFunc(GLC.SRC_ALPHA, GLC.ONE) }
        }
        stBlend = b
    }
    private fun setPoly(m: Material) {
        val p = if (m.polygonOffset) 1 else 0
        if (p != stPoly) { if (m.polygonOffset) gl.enable(GLC.POLYGON_OFFSET_FILL) else gl.disable(GLC.POLYGON_OFFSET_FILL); stPoly = p }
        if (m.polygonOffset) gl.polygonOffset(m.polyFactor, m.polyUnits)
    }

    /* ---------- frame ---------- */
    fun render(scene: Object3D, cam: Camera) {
        frame++
        drawCalls = 0
        camera = cam
        scene.updateMatrixWorld()
        cam.updateCameraMatrices()
        camPos.setFromMatrixPosition(cam.matrixWorld)
        viewProj.multiplyMatrices(cam.projectionMatrix, cam.matrixWorldInverse)
        frustum.setFromProjectionMatrix(viewProj)
        cam.projectionMatrix.toFloat(projF); cam.matrixWorldInverse.toFloat(viewF)
        opaque.clear(); transparent.clear(); shadowList.clear(); poolN = 0
        collect(scene, cam)
        if (shadowEnabled) renderShadow()
        opaque.sortWith(opaqueOrder)
        transparent.sortWith(transOrder)
        resetState()
        for (it in opaque) draw(it)
        for (it in transparent) draw(it)
        gl.bindVertexArray(0)
        gl.depthMask(true)
        gl.disable(GLC.POLYGON_OFFSET_FILL)
    }

    private val depthPlain by lazy { program("depth", Shaders.DEPTH_VS, Shaders.DEPTH_FS, emptyList()) }
    private val depthInst by lazy { program("depthI", Shaders.DEPTH_VS, Shaders.DEPTH_FS, listOf("INSTANCED")) }

    private fun ensureShadow() {
        if (shadowFbo != 0) return
        shadowTex = gl.genTexture()
        gl.bindTexture(GLC.TEXTURE_2D, shadowTex)
        gl.texImage2D(GLC.TEXTURE_2D, 0, GLC.DEPTH_COMPONENT24, shadowSize, shadowSize, GLC.DEPTH_COMPONENT, GLC.UNSIGNED_INT, null)
        gl.texParameteri(GLC.TEXTURE_2D, GLC.TEXTURE_MIN_FILTER, GLC.LINEAR); gl.texParameteri(GLC.TEXTURE_2D, GLC.TEXTURE_MAG_FILTER, GLC.LINEAR)
        gl.texParameteri(GLC.TEXTURE_2D, GLC.TEXTURE_WRAP_S, GLC.CLAMP_TO_EDGE); gl.texParameteri(GLC.TEXTURE_2D, GLC.TEXTURE_WRAP_T, GLC.CLAMP_TO_EDGE)
        gl.texParameteri(GLC.TEXTURE_2D, GLC.TEXTURE_COMPARE_MODE, GLC.COMPARE_REF_TO_TEXTURE); gl.texParameteri(GLC.TEXTURE_2D, GLC.TEXTURE_COMPARE_FUNC, GLC.LEQUAL)
        shadowFbo = gl.genFramebuffer()
        gl.bindFramebuffer(GLC.FRAMEBUFFER, shadowFbo)
        gl.framebufferTexture2D(GLC.FRAMEBUFFER, GLC.DEPTH_ATTACHMENT, GLC.TEXTURE_2D, shadowTex, 0)
        gl.drawBuffersNone(); gl.readBufferNone()
    }

    var currentFbo = 0
    var viewportW = 1
    var viewportH = 1

    private fun renderShadow() {
        ensureShadow()
        val e = shadowExtent
        val sunPos = Vec3().copy(shadowTarget).addScaledVector(sunDir, 450.0)
        val m = Mat4().lookAt(sunPos, shadowTarget, Vec3(0.0, 1.0, 0.0))
        val q = Quat().setFromRotationMatrix(m)
        lightView.compose(sunPos, q, Vec3(1.0, 1.0, 1.0)).invert()
        lightProj.makeOrthographic(-e, e, e, -e, 1.0, 900.0)
        val vp = Mat4().multiplyMatrices(lightProj, lightView)
        val bias = Mat4(); bias.e[0] = 0.5; bias.e[5] = 0.5; bias.e[10] = 0.5; bias.e[12] = 0.5; bias.e[13] = 0.5; bias.e[14] = 0.5
        shadowMat.multiplyMatrices(bias, vp)
        val lf = Frustum().setFromProjectionMatrix(vp)
        gl.bindFramebuffer(GLC.FRAMEBUFFER, shadowFbo)
        gl.viewport(0, 0, shadowSize, shadowSize)
        resetState()
        setDepth(true, true)
        gl.disable(GLC.BLEND); stBlend = 0
        gl.clear(GLC.DEPTH_BUFFER_BIT)
        val pf = lightProj.toFloat(lpF); val vf = lightView.toFloat(lvF)
        for (it in shadowList) {
            val inst = it.inst
            if (it.obj.frustumCulled) {
                val s = if (inst != null) inst.boundingSphere else (it.geo.boundingSphere ?: it.geo.computeBoundingSphere())
                if (s != null) { tmpSphere.copy(s).applyMatrix4(it.obj.matrixWorld); if (!lf.intersectsSphere(tmpSphere.center, tmpSphere.radius)) continue }
            }
            val p = if (inst != null) depthInst else depthPlain
            if (curProg != p.id) { gl.useProgram(p.id); curProg = p.id; p.m4("uProj", pf); p.m4("uView", vf) }
            p.m4("uModel", it.obj.matrixWorld.toFloat(fm16))
            setCull(when (it.mat.side) { Side.FRONT -> GLC.FRONT; Side.BACK -> GLC.BACK; else -> -1 })
            drawGeo(it)
        }
        gl.bindFramebuffer(GLC.FRAMEBUFFER, currentFbo)
        gl.viewport(0, 0, viewportW, viewportH)
        resetState()
    }

    private fun frameUniforms(p: Program, m: Material) {
        p.m4("uProj", projF); p.m4("uView", viewF)
        p.v3("uCamPos", camPos)
        p.col("uFogColor", fogColor); p.f("uFogNear", fogNear); p.f("uFogFar", fogFar)
        if (m is ToonMaterial) {
            p.v3("uSunDir", sunDir); p.col("uSunColor", sunColor, sunIntensity)
            p.col("uHemiSky", hemiSky, hemiIntensity); p.col("uHemiGround", hemiGround, hemiIntensity)
            if (shadowEnabled) { p.m4("uShadowMat", shadowMat.toFloat(fm16b)); p.f("uShadowBias", shadowBias); p.f("uNormalBias", shadowNormalBias); p.v2("uShadowTexel", 1.0 / shadowSize, 1.0 / shadowSize); p.i("uShadowMap", 1) }
        }
        if (m is OutlineMaterial) p.col("uInk", ink)
        if (m is PointsMaterial) p.f("uScale", drawingBufferHeight * 0.5)
    }

    private fun draw(it: Item) {
        val m = it.mat; val p = it.prog!!
        if (curProg != p.id) {
            gl.useProgram(p.id); curProg = p.id
            if (p.frame != frame) { p.frame = frame; frameUniforms(p, m) }
        }
        if (m is ToonMaterial && shadowEnabled && it.obj.receiveShadow) { gl.activeTexture(GLC.TEXTURE0 + 1); gl.bindTexture(GLC.TEXTURE_2D, shadowTex) }
        setDepth(m.depthTest, m.depthWrite)
        setCull(when (m.side) { Side.FRONT -> GLC.BACK; Side.BACK -> GLC.FRONT; else -> -1 })
        setBlend(m)
        setPoly(m)
        p.m4("uModel", it.obj.matrixWorld.toFloat(fm16))
        when (m) {
            is ToonMaterial -> {
                nm.getNormalMatrix(it.obj.matrixWorld); for (k in 0 until 9) fm9[k] = nm.e[k].toFloat(); p.m3("uNormalMat", fm9)
                p.col("uColor", m.color); p.f("uOpacity", m.opacity); p.col("uEmissive", m.emissive, m.emissiveIntensity)
                m.map?.let { bindTexture(it, 0); p.i("uMap", 0) }
                m.building?.let { b -> p.f("uGlow", b.glow); p.f("uFloor", b.floor); p.f("uCell", b.cell); p.f("uLitP", b.litP); p.col("uGlass", b.glass); p.col("uWarm", b.warm) }
            }
            is OutlineMaterial -> p.f("uThick", m.thick)
            is BasicMaterial -> { p.col("uColor", m.color); p.f("uOpacity", m.opacity); m.map?.let { bindTexture(it, 0); p.i("uMap", 0) } }
            is LineMaterial -> { p.col("uColor", m.color); p.f("uOpacity", m.opacity) }
            is PointsMaterial -> { p.col("uColor", m.color); p.f("uOpacity", m.opacity); p.f("uSize", m.size); m.map?.let { bindTexture(it, 0); p.i("uMap", 0) } }
            is SkyMaterial -> {
                p.f("uTime", m.time); p.f("uStars", m.stars); p.f("uRain", m.rain); p.f("uMoon", m.moon)
                p.col("uZen", m.zen); p.col("uHor", m.hor); p.col("uSun", m.sun); p.v3("uSunDir", m.sunDir); p.col("uCloudLit", m.cloudLit); p.col("uCloudShade", m.cloudShade); p.col("uInk", ink)
            }
            is SeaMaterial -> {
                bindTexture(m.map, 0); p.i("uMap", 0)
                p.f("uTime", m.time); p.f("uDark", m.dark); p.f("uMapS", m.mapS); p.f("uGridN", m.gridN.toDouble()); p.v2("uMap0", m.map0x, m.map0z)
                p.col("uDeep", m.deep); p.col("uMid", m.mid); p.col("uShallow", m.shallow); p.col("uFoam", m.foam); p.col("uRiver", m.river)
                p.v3("uSunDir", m.sunDir); p.col("uSunCol", m.sunCol)
            }
        }
        drawGeo(it)
    }

    /** draw a bare triangle geometry with whatever program is bound (post passes) */
    fun drawFullscreen(g: Geometry) {
        gl.bindVertexArray(vao(g, null))
        gl.drawArrays(GLC.TRIANGLES, 0, g.vertexCount)
        gl.bindVertexArray(0)
        curProg = -1
    }

    private fun drawGeo(it: Item) {
        val g = it.geo
        gl.bindVertexArray(vao(g, it.inst))
        val inst = it.inst
        val idx = g.index
        val total = if (idx != null) idx.size else g.vertexCount
        var count = if (it.count >= 0) it.count else if (g.drawCount >= 0) minOf(g.drawCount, total) else total
        if (count <= 0) return
        drawCalls++
        if (inst != null) {
            if (idx != null) gl.drawElementsInstanced(it.mode, count, GLC.UNSIGNED_INT, it.start * 4, inst.count)
            else gl.drawArraysInstanced(it.mode, it.start, count, inst.count)
        } else {
            if (idx != null) gl.drawElements(it.mode, count, GLC.UNSIGNED_INT, it.start * 4)
            else gl.drawArrays(it.mode, it.start, count)
        }
        count = 0
    }
}
