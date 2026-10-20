package vn.paperworld.phidoi.engine

import kotlin.math.max

/* =========================================================
   scene graph — a small three.js-shaped core
   ========================================================= */
open class Object3D {
    val position = Vec3()
    val rotation = Euler()
    val quaternion = Quat()
    val scale = Vec3(1.0, 1.0, 1.0)
    /** when true the quaternion is authoritative, otherwise it is derived from [rotation] */
    var useQuat = false
    val matrix = Mat4()
    val matrixWorld = Mat4()
    var matrixAutoUpdate = true
    var visible = true
    var parent: Object3D? = null
    val children = ArrayList<Object3D>(2)
    var castShadow = false
    var receiveShadow = false
    var frustumCulled = true
    var renderOrder = 0.0
    var name = ""
    @JvmField var userData: Any? = null

    fun add(vararg objs: Object3D): Object3D {
        for (o in objs) { o.parent?.remove(o); o.parent = this; children.add(o) }
        return this
    }
    fun remove(o: Object3D) { if (children.remove(o)) o.parent = null }

    fun updateMatrix() {
        if (!useQuat) quaternion.setFromEuler(rotation)
        matrix.compose(position, quaternion, scale)
    }
    fun updateMatrixWorld(force: Boolean = false) {
        if (matrixAutoUpdate) updateMatrix()
        val p = parent
        if (p == null) matrixWorld.copy(matrix) else matrixWorld.multiplyMatrices(p.matrixWorld, matrix)
        for (i in children.indices) children[i].updateMatrixWorld(force)
    }
    /** recompute this node's world matrix from its ancestors (three's updateWorldMatrix(true, false)) */
    fun updateWorldFromParents() {
        parent?.updateWorldFromParents()
        if (matrixAutoUpdate) updateMatrix()
        val p = parent
        if (p == null) matrixWorld.copy(matrix) else matrixWorld.multiplyMatrices(p.matrixWorld, matrix)
    }
    fun localToWorld(v: Vec3) = v.applyMatrix4(matrixWorld)
    fun traverse(f: (Object3D) -> Unit) { f(this); for (i in children.indices) children[i].traverse(f) }
    fun setScale(s: Double): Object3D { scale.set(s, s, s); return this }
}

class Group : Object3D()

open class Mesh(var geometry: Geometry, var material: Material) : Object3D() {
    /** multi-material meshes (geometry groups) */
    var materials: List<Material>? = null
}

/** per-instance transforms (+ optional colours), shareable between a mesh and its ink outline */
class InstanceData(val capacity: Int, withColor: Boolean) {
    val matrices = FloatArray(capacity * 16)
    val colors: FloatArray? = if (withColor) FloatArray(capacity * 3) else null
    var count = capacity
    var version = 1
    var colorVersion = 1
    var boundingSphere: Sphere? = null
    @JvmField var gpu: Any? = null
    fun setMatrixAt(i: Int, m: Mat4) { val e = m.e; val o = i * 16; for (k in 0 until 16) matrices[o + k] = e[k].toFloat() }
    fun setColorAt(i: Int, c: Color) { val a = colors ?: return; a[i * 3] = c.r.toFloat(); a[i * 3 + 1] = c.g.toFloat(); a[i * 3 + 2] = c.b.toFloat() }
    fun computeBoundingSphere(geo: Geometry) {
        val gs = geo.boundingSphere ?: geo.computeBoundingSphere()
        var x0 = Double.MAX_VALUE; var y0 = x0; var z0 = x0; var x1 = -x0; var y1 = -x0; var z1 = -x0
        var maxS = 0.0
        val m = Mat4()
        for (i in 0 until count) {
            val o = i * 16
            val x = matrices[o + 12].toDouble(); val y = matrices[o + 13].toDouble(); val z = matrices[o + 14].toDouble()
            if (x < x0) x0 = x; if (y < y0) y0 = y; if (z < z0) z0 = z; if (x > x1) x1 = x; if (y > y1) y1 = y; if (z > z1) z1 = z
            for (k in 0 until 16) m.e[k] = matrices[o + k].toDouble()
            maxS = max(maxS, m.maxScaleOnAxis() * (gs.radius + gs.center.length()))
        }
        val s = Sphere()
        if (count == 0) { s.radius = 0.0 } else {
            s.center.set((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2)
            var r = 0.0
            for (i in 0 until count) { val o = i * 16; val dx = matrices[o + 12] - s.center.x; val dy = matrices[o + 13] - s.center.y; val dz = matrices[o + 14] - s.center.z; r = max(r, kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)) }
            s.radius = r + maxS
        }
        boundingSphere = s
    }
}

class InstancedMesh(geometry: Geometry, material: Material, val data: InstanceData) : Mesh(geometry, material) {
    constructor(geometry: Geometry, material: Material, count: Int, withColor: Boolean = false) : this(geometry, material, InstanceData(count, withColor))
}

class Points(var geometry: Geometry, var material: PointsMaterial) : Object3D()

class Lines(var geometry: Geometry, var material: LineMaterial, val strip: Boolean = false) : Object3D()

class Camera(var fov: Double, var aspect: Double, var near: Double, var far: Double) : Object3D() {
    val up = Vec3(0.0, 1.0, 0.0)
    val projectionMatrix = Mat4()
    val matrixWorldInverse = Mat4()
    init { useQuat = true; updateProjectionMatrix() }
    fun updateProjectionMatrix() { projectionMatrix.makePerspective(fov, aspect, near, far) }
    fun lookAt(t: Vec3) {
        updateWorldFromParents()
        val pos = Vec3().setFromMatrixPosition(matrixWorld)
        val m = Mat4().lookAt(pos, t, up)
        quaternion.setFromRotationMatrix(m)
    }
    fun updateCameraMatrices() {
        updateWorldFromParents()
        matrixWorldInverse.copy(matrixWorld).invert()
    }
}

/* =========================================================
   textures
   ========================================================= */
class Texture(var width: Int, var height: Int) {
    /** ARGB pixels (canvas-painted textures) */
    var pixels: IntArray? = null
    /** raw RGBA bytes (data textures) */
    var bytes: ByteArray? = null
    var srgb = true
    var mipmaps = true
    var nearest = false
    var repeat = true
    var version = 1
    @JvmField var gpu = 0
    @JvmField var gpuVersion = 0
}

/* =========================================================
   materials
   ========================================================= */
object Side { const val FRONT = 0; const val BACK = 1; const val DOUBLE = 2 }
object Blend { const val NORMAL = 0; const val ADDITIVE = 1 }

abstract class Material {
    var transparent = false
    var opacity = 1.0
    var depthWrite = true
    var depthTest = true
    var side = Side.FRONT
    var blending = Blend.NORMAL
    var fog = true
    var polygonOffset = false
    var polyFactor = 0f
    var polyUnits = 0f
    var visible = true
    val id = nextId++
    companion object { private var nextId = 1 }
}

class BuildingParams(val floor: Double, val cell: Double, val glass: Color, val litP: Double, val warm: Color) { var glow = 0.0 }

class ToonMaterial(val color: Color = Color()) : Material() {
    var vertexColors = false
    var map: Texture? = null
    val emissive = Color(0.0, 0.0, 0.0)
    var emissiveIntensity = 1.0
    /** emissive is multiplied by the vertex/instance colour (lanterns, lamps) */
    var emissiveTint = false
    /** emissive is multiplied by the colour map (three's emissiveMap = map) */
    var emissiveMap = false
    var building: BuildingParams? = null
    constructor(hex: String) : this(Color.of(hex))
}

class BasicMaterial(val color: Color = Color()) : Material() {
    var vertexColors = false
    var map: Texture? = null
    constructor(hex: String) : this(Color.of(hex))
}

class PointsMaterial(var size: Double, val color: Color = Color()) : Material() {
    var vertexColors = true
    var map: Texture? = null
    var sizeAttenuation = true
}

class LineMaterial(val color: Color = Color()) : Material() {
    var vertexColors = false
    constructor(hex: String) : this(Color.of(hex))
}

class OutlineMaterial(val thick: Double) : Material() { init { side = Side.BACK } }

class SkyMaterial : Material() {
    val zen = Color(); val hor = Color(); val sun = Color(); val sunDir = Vec3(); val cloudLit = Color(); val cloudShade = Color()
    var time = 0.0; var stars = 0.0; var rain = 0.0; var moon = 0.0
    init { side = Side.BACK; depthWrite = false; fog = false }
}

class SeaMaterial(val map: Texture) : Material() {
    var time = 0.0
    val deep = Color.of("#1c86a6"); val mid = Color.of("#2fb3bf"); val shallow = Color.of("#8fe6d8"); val foam = Color.of("#ffffff"); val river = Color.of("#5c9e86")
    val sunDir = Vec3(); val sunCol = Color(); var dark = 0.0
    var map0x = 0.0; var map0z = 0.0; var mapS = 1.0; var gridN = 1
}
