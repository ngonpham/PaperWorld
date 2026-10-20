package vn.paperworld.phidoi.world

import vn.paperworld.phidoi.engine.*
import vn.paperworld.phidoi.world.Geog.S
import kotlin.math.*

/** quality tiers: view = fog far at sea level, hiR = radius of 10 m terrain, prop = distance props stay drawn */
class Quality(val name: String, val pr: Double, val msaa: Int, val shadow: Int, val soft: Boolean, val view: Double, val hiR: Double, val prop: Double, val dens: Double, val outD: Double)

val QP = listOf(
    Quality("THẤP", 1.0, 0, 1024, false, 2300.0, 520.0, 900.0, 0.45, 500.0),
    Quality("VỪA", 1.5, 4, 2048, false, 3200.0, 760.0, 1300.0, 0.7, 750.0),
    Quality("CAO", 2.0, 4, 2048, true, 4200.0, 950.0, 1800.0, 1.0, 1000.0),
)

/** a named anchor used by routes, spawns and challenges */
class Anchor(var x: Double = 0.0, var y: Double = 0.0, var z: Double = 0.0) {
    var x0 = 0.0; var z0 = 0.0; var z1 = 0.0; var top = 0.0; var dx = 0.0; var dz = 0.0
}

class Collider(val t: Int, val x: Double, val z: Double, val hx: Double, val hz: Double, val ca: Double, val sa: Double, val r: Double, var y0: Double, var y1: Double, val tag: String?)

class Bridge(val name: String, val z: Double, val x0: Double, val deckY: Double, val half: Double, val w: Double, val g: Group)

/** a dấu mộc (discovery stamp) */
class Disc(val id: String, val zone: String, val name: String, val han: String, val desc: String, val x: Double, val y: Double, val z: Double, val r: Double) { var got = false }

class Glow(val x: Double, val y: Double, val z: Double, val col: String)

/**
 * The world of Phi Đội Giấy: terrain, sea, sky, time of day and the shared helpers the
 * landmark builders (World+*.kt) use. Mirrors the module scope of the web build so the
 * builders port line for line.
 */
open class World(val plat: Platform2D, val Q: Quality) {
    val scene = Group()

    /* ---------- random + noise (same generators, same call order as the web build) ---------- */
    fun rand() = Rnd.rand()
    fun RR(a: Double, b: Double) = Rnd.RR(a, b)
    fun <T> pick(a: List<T>): T = Rnd.pick(a)
    fun <T> pick(a: Array<T>): T = Rnd.pick(a)
    fun sn(x: Double, y: Double) = Simplex.sn(x, y)
    fun fbm(x: Double, y: Double, o: Int) = Simplex.fbm(x, y, o)
    fun ridged(x: Double, y: Double, o: Int) = Simplex.ridged(x, y, o)
    fun gy(x: Double, z: Double) = Geog.sample(x, z)

    /* ---------- fog (the renderer and the custom shaders read these) ---------- */
    val fogColor = Color.of("#eef3f5")
    var fogNear = 600.0
    var fogFar = Q.view

    /* ---------- materials + ink outlines ---------- */
    fun toon(hex: String, init: (ToonMaterial.() -> Unit)? = null): ToonMaterial { val m = ToonMaterial(hex); init?.invoke(m); return m }
    private val outlineMats = HashMap<Double, OutlineMaterial>()
    fun outlineMat(thick: Double = 0.075) = outlineMats.getOrPut(thick) { OutlineMaterial(thick) }
    /** inverted-hull ink outline as a child mesh */
    fun outlined(mesh: Mesh, thick: Double = 0.075): Mesh {
        mesh.geometry.addONormal()
        val o = Mesh(mesh.geometry, outlineMat(if (thick > 0) thick else 0.075))
        o.userData = OUTLINE
        mesh.add(o)
        return mesh
    }
    private val gCache = HashMap<String, Geometry>()
    private val mCache = HashMap<String, ToonMaterial>()
    fun geoBox(a: Double, b: Double, c: Double) = gCache.getOrPut("b$a,$b,$c") { Geo.box(a, b, c).addONormal() }
    fun mat(c: String) = mCache.getOrPut(c) { ToonMaterial(c) }
    fun mk(geo: Geometry, col: String, x: Double, y: Double, z: Double, parent: Object3D? = null, th: Double = 0.06) = mk(geo, mat(col), x, y, z, parent, th)
    fun mk(geo: Geometry, m: Material, x: Double, y: Double, z: Double, parent: Object3D? = null, th: Double = 0.06): Mesh {
        val o = Mesh(geo, m)
        if (th > 0) outlined(o, th)
        o.position.set(x, y, z)
        o.castShadow = true
        o.receiveShadow = true
        parent?.add(o)
        return o
    }
    fun box(w: Double, h: Double, d: Double, col: String, x: Double, y: Double, z: Double, parent: Object3D? = null, th: Double = 0.06) = mk(geoBox(w, h, d), col, x, y, z, parent, th)
    fun box(w: Double, h: Double, d: Double, m: Material, x: Double, y: Double, z: Double, parent: Object3D? = null, th: Double = 0.06) = mk(geoBox(w, h, d), m, x, y, z, parent, th)
    /** a part for [mergeGeo] (three: [geo, x, y, z, sx, sy, sz, rx, ry, rz, col]) */
    fun P(g: Geometry, x: Double, y: Double, z: Double, sx: Double = 1.0, sy: Double = 1.0, sz: Double = 1.0, rx: Double = 0.0, ry: Double = 0.0, rz: Double = 0.0, col: String? = null) = Geo.Part(g, x, y, z, sx, sy, sz, rx, ry, rz, col)
    fun mergeGeo(parts: List<Geo.Part>) = Geo.merge(parts)
    fun lathe(pts: List<DoubleArray>, seg: Int = 16) = Geo.lathe(pts, seg).addONormal()
    fun lathe(vararg pts: Pair<Double, Double>, seg: Int = 16) = lathe(pts.map { doubleArrayOf(it.first, it.second) }, seg)

    /** canvas-painted texture (sRGB) */
    fun canvasTex(w: Int, h: Int, wrap: Boolean = true, draw: (Canvas2D, Int, Int) -> Unit) = paintTexture(plat, w, h, wrap, draw)

    /* shared geometries */
    inner class Geos {
        val box = Geo.box(1.0, 1.0, 1.0).addONormal()
        val ico = Geo.icosahedron(1.0, 0).addONormal()
        val ico1 = Geo.icosahedron(1.0, 1).addONormal()
        val pyr = Geo.cone(1.0, 1.0, 4, 1).rotateY(PI / 4).addONormal()
        val cyl = Geo.cylinder(0.5, 0.5, 1.0, 8).addONormal()
        val cyl6 = Geo.cylinder(0.5, 0.6, 1.0, 6).addONormal()
        val cyl16 = Geo.cylinder(1.0, 1.0, 1.0, 16).addONormal()
        val sph = Geo.sphere(1.0, 14, 10).addONormal()
        val hemi = Geo.sphere(1.0, 14, 7, 0.0, PI * 2, 0.0, PI / 2).addONormal()
        val cone = Geo.cone(1.0, 1.0, 10, 1).addONormal()
        val cone6 = Geo.cone(1.0, 1.0, 6, 1).addONormal()
        val torus = Geo.torus(1.0, 0.12, 8, 28).addONormal()
        /** gabled roof: triangular prism, ridge along z, unit size, base at y=0 */
        val roof: Geometry = run {
            val sh = arrayOf(doubleArrayOf(-0.5, 0.0), doubleArrayOf(0.5, 0.0), doubleArrayOf(0.0, 1.0))
            val p = FloatList()
            fun v(i: Int, z: Double) = p.add3(sh[i][0], sh[i][1], z)
            v(0, 0.5); v(1, 0.5); v(2, 0.5)
            v(0, -0.5); v(2, -0.5); v(1, -0.5)
            for (i in 0 until 3) { val j = (i + 1) % 3; v(i, -0.5); v(j, -0.5); v(j, 0.5); v(i, -0.5); v(j, 0.5); v(i, 0.5) }
            Geo.tris(p.toArray()).addONormal()
        }
        /** curved "temple" roof: hip roof with upturned corners */
        val troof: Geometry = run {
            val g = Geo.cylinder(0.42, 0.78, 0.5, 4, 1).rotateY(PI / 4)
            val p = g.position
            for (i in 0 until g.vertexCount) {
                val x = p[i * 3]; val y = p[i * 3 + 1]; val z = p[i * 3 + 2]
                if (y < 0) { val c = abs(x) * abs(z) * 4; p[i * 3 + 1] = y + c * 0.32f }
            }
            g.translate(0.0, 0.25, 0.0)
            g.computeVertexNormals()
            g.addONormal()
        }
    }
    val GEO = Geos()

    /* =========================================================
       instanced props, bucketed into world cells (so far cells can be culled)
       ========================================================= */
    class Batch(val geo: Geometry, val mat: Material, val outline: Double, val shadow: Boolean, val maxD: Double, val recv: Boolean) {
        val cells = HashMap<Long, Pair<FloatList, FloatList>>()
        var n = 0
    }
    class CellItem(val im: InstancedMesh, val ol: InstancedMesh?, val maxD: Double)
    class CellGroup(val g: Group, val x: Double, val z: Double) { val list = ArrayList<CellItem>() }
    val IB = LinkedHashMap<String, Batch>()
    val cellGroups = LinkedHashMap<Long, CellGroup>()
    val cellGroupSet = HashSet<Object3D>()
    private fun ckey(x: Double, z: Double) = (floor(x / CELL).toLong() shl 32) or (floor(z / CELL).toLong() and 0xffffffffL)
    fun ibatch(name: String, geo: Geometry, material: Material, outline: Double = 0.06, shadow: Boolean = true, maxD: Double = Q.prop, recv: Boolean = true) {
        IB[name] = Batch(geo.addONormal(), material, outline, shadow, maxD, recv)
    }
    private val _m = Mat4(); private val _q = Quat(); private val _e = Euler(); private val _s = Vec3(); private val _p = Vec3()
    private val colCache = HashMap<String, Color>()
    fun linCol(hex: String) = colCache.getOrPut(hex) { Color.of(hex) }
    fun put(name: String, x: Double, y: Double, z: Double, sx: Double, sy: Double, sz: Double, ry: Double = 0.0, col: String = "#ffffff", rx: Double = 0.0, rz: Double = 0.0) {
        _e.set(rx, ry, rz, Euler.YXZ)
        _m.compose(_p.set(x, y, z), _q.setFromEuler(_e), _s.set(sx, sy, sz))
        putM(name, _m, x, z, col)
    }
    fun putM(name: String, m4: Mat4, x: Double, z: Double, col: String) {
        val b = IB[name] ?: error("no batch $name")
        val c = b.cells.getOrPut(ckey(x, z)) { Pair(FloatList(256), FloatList(48)) }
        for (k in 0 until 16) c.first.add(m4.e[k])
        val cc = linCol(col)
        c.second.add3(cc.r, cc.g, cc.b)
        b.n++
    }
    fun buildInstances() {
        for ((_, b) in IB) {
            for ((k, c) in b.cells) {
                val n = c.first.n / 16
                if (n == 0) continue
                val cg = cellGroups.getOrPut(k) {
                    val cx = (k shr 32).toInt(); val cz = k.toInt()
                    val g = CellGroup(Group(), (cx + 0.5) * CELL, (cz + 0.5) * CELL)
                    cellGroupSet.add(g.g); scene.add(g.g); g
                }
                val data = InstanceData(n, true)
                System.arraycopy(c.first.a, 0, data.matrices, 0, n * 16)
                System.arraycopy(c.second.a, 0, data.colors!!, 0, n * 3)
                data.computeBoundingSphere(b.geo)
                val im = InstancedMesh(b.geo, b.mat, data)
                im.castShadow = b.shadow; im.receiveShadow = b.recv
                cg.g.add(im)
                var ol: InstancedMesh? = null
                if (b.outline > 0) { ol = InstancedMesh(b.geo, outlineMat(b.outline), data); cg.g.add(ol) }
                cg.list.add(CellItem(im, ol, b.maxD))
            }
            b.cells.clear()
        }
    }
    fun updateCells(cp: Vec3) {
        for (cg in cellGroups.values) {
            val d = hypot(cg.x - cp.x, cg.z - cp.z) - CELL * 0.72
            for (it in cg.list) {
                it.im.visible = d < it.maxD
                it.ol?.visible = d < min(it.maxD, Q.outD)
            }
        }
    }

    /* static landmark groups: hidden once they are lost in the haze, outlines dropped at range */
    class Static(val o: Object3D, val c: Vec3, val r: Double, val outl: List<Object3D>) { var ol = true }
    val STATICS = ArrayList<Static>()
    fun collectStatics(skip: Set<Object3D>) {
        scene.updateMatrixWorld()
        for (o in scene.children) {
            if (o in skip || o in cellGroupSet || o is InstancedMesh || o is Points || o is Lines) continue
            if (o is Mesh && o.children.isEmpty() && o.geometry.index != null && o.geometry.color != null) continue // terrain tile
            var x0 = 1e18; var y0 = 1e18; var z0 = 1e18; var x1 = -1e18; var y1 = -1e18; var z1 = -1e18
            val outl = ArrayList<Object3D>()
            val sp = Sphere()
            o.traverse { m ->
                if (m.userData === OUTLINE) outl.add(m)
                if (m is Mesh && m !is InstancedMesh) {
                    val bs = m.geometry.boundingSphere ?: m.geometry.computeBoundingSphere()
                    sp.copy(bs).applyMatrix4(m.matrixWorld)
                    x0 = min(x0, sp.center.x - sp.radius); y0 = min(y0, sp.center.y - sp.radius); z0 = min(z0, sp.center.z - sp.radius)
                    x1 = max(x1, sp.center.x + sp.radius); y1 = max(y1, sp.center.y + sp.radius); z1 = max(z1, sp.center.z + sp.radius)
                }
            }
            if (x0 > x1) continue
            val c = Vec3((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2)
            val r = sqrt((x1 - x0).pow(2) + (y1 - y0).pow(2) + (z1 - z0).pow(2)) / 2
            if (r > 3000) continue
            STATICS.add(Static(o, c, r, outl))
        }
    }
    fun updateStatics(cp: Vec3, fogFar: Double) {
        for (s in STATICS) {
            val d = s.c.distanceTo(cp) - s.r
            s.o.visible = d < fogFar * 1.05
            val ol = d < Q.outD * 1.4
            if (ol != s.ol) { s.ol = ol; for (m in s.outl) m.visible = ol }
        }
    }

    /* =========================================================
       colliders: oriented boxes + cylinders in a spatial hash
       ========================================================= */
    val colGrid = HashMap<Long, ArrayList<Collider>>()
    val colliders = ArrayList<Collider>()
    private fun gkey(gx: Int, gz: Int) = (gx.toLong() shl 32) or (gz.toLong() and 0xffffffffL)
    fun addCol(c: Collider): Collider {
        colliders.add(c)
        val r = if (c.t == 1) c.r else hypot(c.hx, c.hz)
        for (gx in floor((c.x - r) / COLC).toInt()..floor((c.x + r) / COLC).toInt())
            for (gz in floor((c.z - r) / COLC).toInt()..floor((c.z + r) / COLC).toInt())
                colGrid.getOrPut(gkey(gx, gz)) { ArrayList(4) }.add(c)
        return c
    }
    fun colBox(x: Double, z: Double, hx: Double, hz: Double, ang: Double, y0: Double, y1: Double, tag: String? = null) = addCol(Collider(0, x, z, hx, hz, cos(ang), sin(ang), 0.0, y0, y1, tag))
    fun colCyl(x: Double, z: Double, r: Double, y0: Double, y1: Double, tag: String? = null) = addCol(Collider(1, x, z, 0.0, 0.0, 1.0, 0.0, r, y0, y1, tag))
    fun colInside(c: Collider, x: Double, y: Double, z: Double, pad: Double): Boolean {
        if (y < c.y0 - pad || y > c.y1 + pad) return false
        val dx = x - c.x; val dz = z - c.z
        if (c.t == 1) return dx * dx + dz * dz < (c.r + pad) * (c.r + pad)
        val u = dx * c.ca - dz * c.sa; val v = dx * c.sa + dz * c.ca
        return abs(u) < c.hx + pad && abs(v) < c.hz + pad
    }
    fun hitCollider(x: Double, y: Double, z: Double, pad: Double = 0.6): Collider? {
        val l = colGrid[gkey(floor(x / COLC).toInt(), floor(z / COLC).toInt())] ?: return null
        for (c in l) if (colInside(c, x, y, z, pad)) return c
        return null
    }
    /** highest solid top under (x,z) — used to keep the camera out of buildings */
    fun obstacleTop(x: Double, z: Double, pad: Double = 1.0): Double {
        val l = colGrid[gkey(floor(x / COLC).toInt(), floor(z / COLC).toInt())] ?: return -1e9
        var top = -1e9
        for (c in l) if (c.y1 > top && colInside(c, x, (c.y0 + c.y1) / 2, z, pad)) top = c.y1
        return top
    }

    /* =========================================================
       world grid (20 m) — shared by the far terrain, water map and maps
       ========================================================= */
    val GH = FloatArray(GN * GN)
    val GC = ByteArray(GN * GN * 3)
    val GM = ByteArray(GN * GN * 4)
    fun buildGrid(progress: (Double) -> Unit = {}) {
        val riv = FloatArray(GN * GN); val rk = FloatArray(GN * GN); val fl = IntArray(GN * GN * 8)
        fun u8(v: Double) = clamp(v, 0.0, 255.0).toInt()
        for (j in 0 until GN) {
            for (i in 0 until GN) {
                val x = WX0 + i * GS; val z = WZ0 + j * GS; val k = j * GN + i
                GH[k] = Geog.sample(x, z).toFloat()
                riv[k] = S.riv.toFloat(); rk[k] = S.rock.toFloat()
                val f = k * 8
                fl[f] = u8(S.city * 255); fl[f + 1] = u8(S.hoian * 255); fl[f + 2] = u8(S.rice * 255); fl[f + 3] = u8(S.karst * 255)
                fl[f + 4] = u8(S.sd + 128); fl[f + 5] = u8(S.rock * 255); fl[f + 6] = u8(S.riv * 255)
            }
            if (j % 24 == 23) progress(j.toDouble() / GN)
        }
        val c3 = DoubleArray(3)
        for (j in 0 until GN) for (i in 0 until GN) {
            val k = j * GN + i; val f = k * 8
            val hx = GH[k + (if (i < GN - 1) 1 else 0)] - GH[k - (if (i > 0) 1 else 0)]; val hz = GH[k + (if (j < GN - 1) GN else 0)] - GH[k - (if (j > 0) GN else 0)]
            val slope = hypot(hx.toDouble(), hz.toDouble()) / (2 * GS)
            S.city = fl[f] / 255.0; S.hoian = fl[f + 1] / 255.0; S.rice = fl[f + 2] / 255.0; S.karst = fl[f + 3] / 255.0
            S.sd = fl[f + 4] - 128.0 + (if (fl[f + 4] >= 255) 500 else 0); S.rock = fl[f + 5] / 255.0; S.riv = fl[f + 6] / 255.0
            Geog.colorOf(WX0 + i * GS, WZ0 + j * GS, GH[k].toDouble(), slope, c3)
            GC[k * 3] = c3[0].toInt().toByte(); GC[k * 3 + 1] = c3[1].toInt().toByte(); GC[k * 3 + 2] = c3[2].toInt().toByte()
            GM[k * 4] = u8((GH[k] + 40) / 80.0 * 255).toByte()
            GM[k * 4 + 1] = u8(riv[k] * 255.0).toByte()
            GM[k * 4 + 2] = u8(rk[k] * 255.0).toByte()
            GM[k * 4 + 3] = 255.toByte()
        }
        mapTex.version++
    }
    fun gridH(x: Double, z: Double): Double {
        val fx = clamp((x - WX0) / GS, 0.0, GN - 1.001); val fz = clamp((z - WZ0) / GS, 0.0, GN - 1.001)
        val i = floor(fx).toInt(); val j = floor(fz).toInt(); val u = fx - i; val v = fz - j; val k = j * GN + i
        val h00 = GH[k]; val h10 = GH[k + 1]; val h01 = GH[k + GN]; val h11 = GH[k + GN + 1]
        return if (u + v <= 1) h00 + (h10 - h00) * u + (h01 - h00) * v else h11 + (h01 - h11) * (1 - u) + (h10 - h11) * (1 - v)
    }

    /* =========================================================
       TERRAIN — 400 m tiles, three levels of detail (10 / 20 / 40 m)
       ========================================================= */
    val terrainMat = toon("#ffffff") { vertexColors = true }
    class Tile(val i: Int, val j: Int) {
        val cx = WX0 + (i + 0.5) * TILE; val cz = WZ0 + (j + 0.5) * TILE
        val mesh = arrayOfNulls<Mesh>(3)
        var hs: FloatArray? = null
        var lod = -1; var want = 2; var maxH = -99.0; var under = false
    }
    val tiles = ArrayList<Tile>()
    private val LODS = intArrayOf(10, 20, 40)
    private val GAMMA = FloatArray(256) { (it / 255.0).pow(2.2).toFloat() }
    private fun tileGeo(t: Tile, lod: Int): Pair<Geometry, FloatArray?> {
        val st = LODS[lod]; val n = (TILE / st).toInt(); val W = n + 1; val x0 = WX0 + t.i * TILE; val z0 = WZ0 + t.j * TILE
        val nv = W * W + 4 * W; val pos = FloatArray(nv * 3); val col = FloatArray(nv * 3)
        var hs: FloatArray? = null
        if (lod == 0) {
            val h = FloatArray(W * W); hs = h
            val fl = DoubleArray(W * W * 7)
            for (j in 0 until W) for (i in 0 until W) {
                val k = j * W + i
                h[k] = Geog.sample(x0 + i * st, z0 + j * st).toFloat()
                val f = k * 7
                fl[f] = S.city; fl[f + 1] = S.hoian; fl[f + 2] = S.rice; fl[f + 3] = S.karst; fl[f + 4] = S.sd; fl[f + 5] = S.rock; fl[f + 6] = S.riv
            }
            val c3 = DoubleArray(3)
            for (j in 0 until W) for (i in 0 until W) {
                val k = j * W + i; val x = x0 + i * st; val z = z0 + j * st
                val hx = (if (i < n) h[k + 1] else h[k]) - (if (i > 0) h[k - 1] else h[k]); val hz = (if (j < n) h[k + W] else h[k]) - (if (j > 0) h[k - W] else h[k])
                val f = k * 7
                S.city = fl[f]; S.hoian = fl[f + 1]; S.rice = fl[f + 2]; S.karst = fl[f + 3]; S.sd = fl[f + 4]; S.rock = fl[f + 5]; S.riv = fl[f + 6]
                Geog.colorOf(x, z, h[k].toDouble(), hypot(hx.toDouble(), hz.toDouble()) / ((if (i > 0 && i < n) 2 else 1) * st), c3)
                pos[k * 3] = x.toFloat(); pos[k * 3 + 1] = h[k]; pos[k * 3 + 2] = z.toFloat()
                col[k * 3] = (c3[0] / 255).pow(2.2).toFloat(); col[k * 3 + 1] = (c3[1] / 255).pow(2.2).toFloat(); col[k * 3 + 2] = (c3[2] / 255).pow(2.2).toFloat()
            }
        } else {
            val gstep = (st / GS).toInt(); val gi0 = t.i * (TILE / GS).toInt(); val gj0 = t.j * (TILE / GS).toInt()
            for (j in 0 until W) for (i in 0 until W) {
                val k = j * W + i; val g = (gj0 + j * gstep) * GN + gi0 + i * gstep
                pos[k * 3] = (x0 + i * st).toFloat(); pos[k * 3 + 1] = GH[g]; pos[k * 3 + 2] = (z0 + j * st).toFloat()
                col[k * 3] = GAMMA[GC[g * 3].toInt() and 255]; col[k * 3 + 1] = GAMMA[GC[g * 3 + 1].toInt() and 255]; col[k * 3 + 2] = GAMMA[GC[g * 3 + 2].toInt() and 255]
            }
        }
        val idx = IntList(n * n * 6 + 4 * W * 12)
        for (j in 0 until n) for (i in 0 until n) {
            val a = j * W + i; val b = a + 1; val c = a + W; val d = c + 1
            idx.add3(a, c, b); idx.add3(b, c, d)
        }
        // skirts hide cracks between neighbouring levels of detail
        var s = W * W
        val edge = ArrayList<IntArray>(4 * W + 4)
        for (i in 0 until W) edge.add(intArrayOf(i, 0))
        for (j in 0 until W) edge.add(intArrayOf(n, j))
        for (i in n downTo 0) edge.add(intArrayOf(i, n))
        for (j in n downTo 0) edge.add(intArrayOf(0, j))
        val skirtStart = s
        for (e in 0 until min(edge.size, 4 * W)) {
            val k = edge[e][1] * W + edge[e][0]
            pos[s * 3] = pos[k * 3]; pos[s * 3 + 1] = pos[k * 3 + 1] - 10; pos[s * 3 + 2] = pos[k * 3 + 2]
            col[s * 3] = col[k * 3]; col[s * 3 + 1] = col[k * 3 + 1]; col[s * 3 + 2] = col[k * 3 + 2]
            s++
        }
        for (e in 0 until 4 * W - 1) {
            val a = edge[e][1] * W + edge[e][0]; val b = edge[e + 1][1] * W + edge[e + 1][0]; val c = skirtStart + e; val d = skirtStart + e + 1
            idx.add3(a, b, c); idx.add3(b, d, c); idx.add3(a, c, b); idx.add3(b, c, d)
        }
        val g = Geometry.of(pos, null, null, idx.toArray())
        g.color = col
        g.computeVertexNormals()
        val nrm = g.normal!!
        for (e in 0 until 4 * W) { val k = edge[e][1] * W + edge[e][0]; val sk = skirtStart + e; nrm[sk * 3] = nrm[k * 3]; nrm[sk * 3 + 1] = nrm[k * 3 + 1]; nrm[sk * 3 + 2] = nrm[k * 3 + 2] }
        g.computeBoundingSphere()
        return Pair(g, hs)
    }
    private fun tileMesh(g: Geometry): Mesh {
        val m = Mesh(g, terrainMat)
        m.receiveShadow = true; m.visible = false; m.matrixAutoUpdate = false
        scene.add(m)
        return m
    }
    fun buildTiles() {
        for (j in 0 until TN) for (i in 0 until TN) {
            val t = Tile(i, j)
            for (l in 1..2) t.mesh[l] = tileMesh(tileGeo(t, l).first)
            val p = t.mesh[1]!!.geometry.position
            var k = 1
            while (k < p.size) { t.maxH = max(t.maxH, p[k].toDouble()); k += 3 }
            t.under = t.maxH < -1.5 // fully under water: never draw
            tiles.add(t)
        }
    }
    var tileBuildQ = 0
    /** hi-res tiles freed this frame; the GL thread disposes their buffers */
    val disposeQ = ArrayList<Geometry>()
    fun updateTiles(cp: Vec3, budget: Int = 1) {
        val hiR = Q.hiR; val altK = clamp((cp.y - 120) / 600, 0.0, 1.0)
        var built = 0
        val todo = ArrayList<Pair<Double, Tile>>()
        for (t in tiles) {
            if (t.under) continue
            val d = hypot(t.cx - cp.x, t.cz - cp.z) - TILE * 0.7
            val want = if (d < hiR * (1 - altK * 0.5)) 0 else if (d < 1500 + altK * 900) 1 else 2
            t.want = want
            if (want == 0 && t.mesh[0] == null) todo.add(Pair(d, t))
        }
        todo.sortBy { it.first }
        for ((_, t) in todo) {
            if (built >= budget) break
            val (g, hs) = tileGeo(t, 0)
            t.mesh[0] = tileMesh(g)
            t.hs = hs
            built++
        }
        val far0 = fogFar * 1.08 + TILE
        for (t in tiles) {
            if (t.under) continue
            var l = t.want
            if (hypot(t.cx - cp.x, t.cz - cp.z) > far0) l = -2
            if (l == 0 && t.mesh[0] == null) l = 1
            if (t.lod != l) {
                for (k in 0 until 3) t.mesh[k]?.visible = k == l
                t.lod = l
            }
            // free far hi-res meshes
            val m0 = t.mesh[0]
            if (m0 != null && t.want == 2) {
                scene.remove(m0)
                disposeQ.add(m0.geometry)
                t.mesh[0] = null
                t.hs = null
                if (t.lod == 0) t.lod = -1
            }
        }
        tileBuildQ = todo.size - built
    }
    /** height exactly as drawn (same triangle split as the tile mesh) */
    fun heightAt(x: Double, z: Double): Double {
        val i = floor((x - WX0) / TILE).toInt(); val j = floor((z - WZ0) / TILE).toInt()
        if (i < 0 || j < 0 || i >= TN || j >= TN) return -36.0
        val t = tiles.getOrNull(j * TN + i)
        val h = t?.hs ?: return gridH(x, z)
        val st = 10.0; val W = (TILE / st).toInt() + 1; val lx = (x - (WX0 + i * TILE)) / st; val lz = (z - (WZ0 + j * TILE)) / st
        val ix = min(W - 2, floor(lx).toInt()); val iz = min(W - 2, floor(lz).toInt()); val u = lx - ix; val v = lz - iz
        val h00 = h[iz * W + ix]; val h10 = h[iz * W + ix + 1]; val h01 = h[(iz + 1) * W + ix]; val h11 = h[(iz + 1) * W + ix + 1]
        return if (u + v <= 1) h00 + (h10 - h00) * u + (h01 - h00) * v else h11 + (h01 - h11) * (1 - u) + (h10 - h11) * (1 - v)
    }
    fun surfaceAt(x: Double, z: Double) = max(heightAt(x, z), SEA_Y)

    /* =========================================================
       WATER · SKY · distant ranges
       ========================================================= */
    val mapTex = Texture(GN, GN).also { it.bytes = GM; it.srgb = false; it.mipmaps = false; it.repeat = false }
    val seaMat = SeaMaterial(mapTex).also { it.map0x = WX0; it.map0z = WZ0; it.mapS = WSIZE; it.gridN = GN }
    val sea = Mesh(Geo.plane(1.0, 1.0).rotateX(-PI / 2), seaMat).also {
        it.scale.set(40000.0, 1.0, 40000.0); it.position.y = SEA_Y; it.frustumCulled = false; it.renderOrder = 0.0; scene.add(it)
    }
    val skyMat = SkyMaterial()
    val sky = Mesh(Geo.sphere(7000.0, 32, 16), skyMat).also { it.renderOrder = -1.0; it.frustumCulled = false; scene.add(it) }
    private fun makeRange(R: Double, seed: Double, lo: Double, hi: Double, a0: Double, a1: Double): Mesh {
        val N = 260; val pos = FloatList(); val col = FloatList(); val idx = IntList()
        for (i in 0..N) {
            val a = a0 + (i.toDouble() / N) * (a1 - a0); val ca = cos(a); val sa = sin(a)
            val n = 0.5 + 0.5 * fbm(ca * 2.4 + seed, sa * 2.4 - seed, 4); val r = 1 - abs(sn(ca * 8 + seed * 2, sa * 8))
            val env = smooth(0.0, 0.18, i.toDouble() / N) * smooth(1.0, 0.82, i.toDouble() / N)
            val h = (lo + (hi - lo) * (n * 0.65 + r * r * 0.35)) * env
            pos.add3(ca * R, -120.0, sa * R); pos.add3(ca * R, h, sa * R)
            col.add3(0.82, 0.84, 0.88); col.add3(1.0, 1.0, 1.0)
        }
        for (i in 0 until N) { val a = i * 2; idx.add3(a, a + 1, a + 2); idx.add3(a + 1, a + 3, a + 2) }
        val g = Geometry.of(pos.toArray(), null, null, idx.toArray()); g.color = col.toArray()
        val m = Mesh(g, BasicMaterial("#ffffff").also { it.vertexColors = true; it.fog = false; it.side = Side.DOUBLE })
        m.frustumCulled = false; m.renderOrder = -0.5
        scene.add(m)
        return m
    }
    // angles measured from +x toward +z; west is π
    val ranges = listOf(makeRange(8200.0, 3.1, 160.0, 620.0, PI * 0.45, PI * 1.55), makeRange(9400.0, 7.7, 260.0, 900.0, PI * 0.55, PI * 1.42))
    private val RANGE_TINT = listOf(Color.of("#6f8f8a"), Color.of("#8b9fb3"))

    /* =========================================================
       LIGHTS + TIME OF DAY (Đà Nẵng: sun rises from the sea, sets behind Bà Nà)
       ========================================================= */
    class Key(val h: Double, zen: String, hor: String, sun: String, val sunI: Double, hs: String, hg: String, val hi: Double, val elev: Double, val glow: Double, val stars: Double, cl: String, cs: String, val label: String) {
        val zen = Color.of(zen); val hor = Color.of(hor); val sun = Color.of(sun); val hs = Color.of(hs); val hg = Color.of(hg); val cl = Color.of(cl); val cs = Color.of(cs)
    }
    val KEYS = listOf(
        Key(3.0, "#0b1030", "#26305e", "#9fb2ff", 0.3, "#4a5a9a", "#1a1a3a", 0.65, 0.35, 1.0, 1.0, "#4a5584", "#262c55", "ĐÊM"),
        Key(5.6, "#6a7fc0", "#ffc9a8", "#ffb58a", 1.0, "#ffd9c9", "#9c8fc9", 1.05, 0.08, 0.35, 0.1, "#ffe0cf", "#b6a3d1", "BÌNH MINH"),
        Key(7.2, "#79b4ea", "#fde6c8", "#ffe2b0", 2.2, "#fff4e0", "#b7aee0", 1.25, 0.3, 0.0, 0.0, "#ffffff", "#c9c3ea", "SÁNG SỚM"),
        Key(9.5, "#5fb0ee", "#e6f4f6", "#fff6e4", 2.7, "#ffffff", "#b4c8d8", 1.38, 0.62, 0.0, 0.0, "#ffffff", "#c7d3ea", "SÁNG"),
        Key(12.5, "#4aa6f0", "#dcf0fa", "#ffffff", 3.0, "#ffffff", "#b9c4e0", 1.45, 1.05, 0.0, 0.0, "#ffffff", "#cfd8f0", "TRƯA"),
        Key(16.8, "#7aa3d8", "#ffd99c", "#ffc46b", 2.4, "#ffe8c8", "#b39acb", 1.2, 0.3, 0.05, 0.0, "#fff0cf", "#d9a8b8", "HOÀNG HÔN"),
        Key(18.6, "#3f3c6d", "#f19a8c", "#ff8a6a", 1.0, "#f6b8b0", "#6e5a9a", 0.95, 0.07, 0.75, 0.3, "#ffc1ae", "#7a5f95", "CHẠNG VẠNG"),
        Key(21.5, "#0b1030", "#26305e", "#9fb2ff", 0.32, "#4a5a9a", "#1a1a3a", 0.68, 0.35, 1.0, 1.0, "#4a5584", "#262c55", "ĐÊM"),
    )
    class EnvState { var hour = 9.5; var target = 9.5; var auto = false; var rain = 0.0; var rainOn = false }
    val ENV = EnvState()
    class Env {
        val zen = Color(); val hor = Color(); val sun = Color(); val hs = Color(); val hg = Color(); val cl = Color(); val cs = Color()
        var sunI = 0.0; var hi = 0.0; var elev = 0.0; var glow = 0.0; var stars = 0.0; var label = ""
    }
    val env = Env()
    private val GREY = Color.of("#9aa0b4"); private val GREY2 = Color.of("#6b7088")
    fun sampleEnv(h0: Double) {
        var h = ((h0 % 24) + 24) % 24
        if (h < 3) h += 24
        var i = 0
        while (i < KEYS.size - 1 && !(h >= KEYS[i].h && h < KEYS[i + 1].h)) i++
        val a = KEYS[i]; val b = if (i + 1 < KEYS.size) KEYS[i + 1] else KEYS[0]
        val bh = if (i + 1 < KEYS.size) b.h else 27.0
        var t = if (bh > a.h) (h - a.h) / (bh - a.h) else 0.0
        t = clamp(t, 0.0, 1.0)
        t = t * t * (3 - 2 * t)
        env.zen.copy(a.zen).lerp(b.zen, t); env.hor.copy(a.hor).lerp(b.hor, t); env.sun.copy(a.sun).lerp(b.sun, t); env.hs.copy(a.hs).lerp(b.hs, t)
        env.hg.copy(a.hg).lerp(b.hg, t); env.cl.copy(a.cl).lerp(b.cl, t); env.cs.copy(a.cs).lerp(b.cs, t)
        env.sunI = lerp(a.sunI, b.sunI, t); env.hi = lerp(a.hi, b.hi, t); env.elev = lerp(a.elev, b.elev, t); env.glow = lerp(a.glow, b.glow, t); env.stars = lerp(a.stars, b.stars, t)
        env.label = if (t < 0.5) a.label else b.label
        if (ENV.rain > 0) {
            val r = ENV.rain
            env.zen.lerp(GREY, r * 0.6); env.hor.lerp(GREY, r * 0.45); env.sunI *= 1 - r * 0.55
            env.cl.lerp(GREY, r * 0.5); env.cs.lerp(GREY2, r * 0.6); env.stars *= 1 - r
        }
    }
    val sunDir = Vec3()
    /** 1 by day, 0 at night */
    fun dayK() = clamp(1 - env.glow * 1.2, 0.0, 1.0)
    /** things that light up at night register here: f(glow 0..1) */
    val NIGHT = ArrayList<(Double) -> Unit>()
    fun applyEnv() {
        sampleEnv(ENV.hour)
        val hh = ((ENV.hour % 24) + 24) % 24
        val az = ((hh - 6) / 12) * PI // 6h east (+x) → 18h west (−x)
        sunDir.set(cos(az) * 0.85, max(0.1, env.elev), 0.32 + 0.1 * sin(az)).normalize()
        if (env.glow > 0.85) sunDir.set(-0.35, 0.55, 0.5).normalize() // moonlight
        skyMat.zen.copy(env.zen); skyMat.hor.copy(env.hor); skyMat.sun.copy(env.sun); skyMat.sunDir.copy(sunDir)
        skyMat.cloudLit.copy(env.cl); skyMat.cloudShade.copy(env.cs); skyMat.stars = env.stars; skyMat.rain = ENV.rain
        skyMat.moon = smooth(0.6, 1.0, env.glow)
        fogColor.copy(env.hor)
        seaMat.sunDir.copy(sunDir)
        seaMat.sunCol.copy(env.sun).multiplyScalar(if (env.glow > 0.85) 0.35 else 1.0)
        seaMat.dark = max(env.glow * 0.78, ENV.rain * 0.3)
        for (i in ranges.indices) (ranges[i].material as BasicMaterial).color.copy(env.hor).lerp(RANGE_TINT[i], 0.42 - i * 0.12 - env.glow * 0.1)
        for (f in NIGHT) f(env.glow)
    }
    /** copy lights + fog into the renderer */
    fun applyTo(r: Renderer) {
        r.sunDir.copy(sunDir); r.sunColor.copy(env.sun); r.sunIntensity = env.sunI
        r.hemiSky.copy(env.hs); r.hemiGround.copy(env.hg); r.hemiIntensity = env.hi
        r.fogColor.copy(fogColor); r.fogNear = fogNear; r.fogFar = fogFar
    }
    fun glowMat(c: String, k: Double = 1.6, base: Double = 0.15): ToonMaterial {
        val m = toon(c) { emissive.copy(Color.of(c)); emissiveIntensity = base }
        NIGHT.add { g -> m.emissiveIntensity = base + g * k }
        return m
    }

    /* =========================================================
       MATERIALS for props
       ========================================================= */
    val white = toon("#ffffff")
    val matBlob = toon("#ffffff")
    val matDouble = toon("#ffffff") { side = Side.DOUBLE }
    /** emissive tinted by the per-instance colour (lanterns, lamps, LEDs) */
    fun tintedGlow(base: Double = 0.2, k: Double = 1.8, hue: String = "#ffffff"): ToonMaterial {
        val m = toon("#ffffff") { emissive.copy(Color.of(hue)); emissiveIntensity = base; emissiveTint = true }
        NIGHT.add { g -> m.emissiveIntensity = base + g * k }
        return m
    }
    /** buildings: procedural window grid in world space, lit windows at night */
    private val BLD_U = ArrayList<BuildingParams>()
    fun buildingMat(floor: Double, cell: Double, glass: String, lit: Double, warm: String = "#ffd08a"): ToonMaterial {
        val bp = BuildingParams(floor, cell, Color.of(glass), lit, Color.of(warm))
        BLD_U.add(bp)
        return toon("#ffffff") { building = bp }
    }
    init { NIGHT.add { g -> for (U in BLD_U) U.glow = smooth(0.15, 0.8, g) } }
    val bldMat = buildingMat(3.4, 3.2, "#5e7290", 0.55)
    val hoiMat = buildingMat(3.8, 2.4, "#6b4630", 0.7, "#ffb35c")
    val lanternMat = tintedGlow(0.35, 1.9)
    val lampMat = tintedGlow(0.05, 2.2, "#fff1c4")

    /** palm fronds: drooping folded leaves around a crown */
    val frondGeo: Geometry = run {
        val P = FloatList()
        val L = 7
        fun push(vararg v: DoubleArray) { for (a in v) P.add3(a[0], a[1], a[2]) }
        for (k in 0 until L) {
            val a = (k.toDouble() / L) * PI * 2 + 0.3; val ca = cos(a); val sa = sin(a); val px = -sa; val pz = ca
            val seg = arrayOf(doubleArrayOf(0.0, 0.0), doubleArrayOf(0.35, 0.18), doubleArrayOf(0.7, 0.1), doubleArrayOf(1.0, -0.32))
            for (s in 0 until seg.size - 1) {
                val r0 = seg[s][0]; val y0 = seg[s][1]; val r1 = seg[s + 1][0]; val y1 = seg[s + 1][1]
                val w0 = 0.16 * sin(((s + 0.3) / 3) * PI) + 0.02; val w1 = 0.16 * sin(((s + 1.3) / 3) * PI) * (if (s == 2) 0.1 else 1.0) + 0.01
                val A = doubleArrayOf(ca * r0, y0, sa * r0); val B = doubleArrayOf(ca * r1, y1, sa * r1)
                val A1 = doubleArrayOf(A[0] + px * w0, A[1] - 0.05, A[2] + pz * w0); val A2 = doubleArrayOf(A[0] - px * w0, A[1] - 0.05, A[2] - pz * w0)
                val B1 = doubleArrayOf(B[0] + px * w1, B[1] - 0.05, B[2] + pz * w1); val B2 = doubleArrayOf(B[0] - px * w1, B[1] - 0.05, B[2] - pz * w1)
                push(A, A1, B, A1, B1, B, A, B, A2, A2, B, B2)
            }
        }
        Geo.tris(P.toArray()).addONormal()
    }
    val hullGeo: Geometry = run {
        val g = Geo.box(1.0, 1.0, 1.0, 1, 1, 2)
        val p = g.position
        for (i in 0 until g.vertexCount) {
            val z = p[i * 3 + 2]; val y = p[i * 3 + 1]
            if (z < -0.4f) { p[i * 3] *= 0.08f; p[i * 3 + 1] = y + 0.25f } else if (z > 0.4f) p[i * 3] *= 0.7f
            if (y < 0) p[i * 3] *= 0.55f
        }
        g.computeVertexNormals()
        g
    }
    inner class GeosC {
        private val G = GEO
        val boat = mergeGeo(listOf(P(hullGeo, 0.0, 0.5, 0.0, 2.2, 1.1, 7.0), P(G.box, 0.0, 1.4, 1.2, 1.5, 1.1, 2.2), P(G.box, 0.0, 2.1, 1.2, 1.7, 0.18, 2.6)))
        val hoiboat = mergeGeo(listOf(P(hullGeo, 0.0, 0.35, 0.0, 1.8, 0.8, 7.5), P(G.box, 0.0, 1.15, 0.6, 1.5, 0.08, 3.4), P(G.box, -0.68, 0.75, 0.6, 0.08, 0.8, 3.2), P(G.box, 0.68, 0.75, 0.6, 0.08, 0.8, 3.2)))
        val thung = mergeGeo(listOf(P(G.hemi, 0.0, 0.55, 0.0, 1.15, -0.55, 1.15), P(G.torus, 0.0, 0.55, 0.0, 1.12, 1.12, 0.9, PI / 2)))
        val umb = mergeGeo(listOf(P(G.cone, 0.0, 3.0, 0.0, 2.1, 0.75, 2.1)))
        val pole = mergeGeo(listOf(P(G.box, 0.0, 1.5, 0.0, 0.12, 3.0, 0.12)))
        val bed = mergeGeo(listOf(P(G.box, 0.0, 0.35, 0.0, 0.75, 0.12, 2.0), P(G.box, 0.0, 0.6, 0.85, 0.75, 0.5, 0.12, -0.6)))
        val person = mergeGeo(listOf(P(G.cyl, 0.0, 0.55, 0.0, 0.42, 1.1, 0.3), P(G.sph, 0.0, 1.35, 0.0, 0.2, 0.22, 0.2)))
        val hat = mergeGeo(listOf(P(G.cone, 0.0, 1.42, 0.0, 0.42, 0.3, 0.42), P(G.cyl, 0.0, 0.55, 0.0, 0.36, 1.05, 0.28)))
        val buffalo = mergeGeo(listOf(
            P(G.box, 0.0, 1.15, 0.0, 1.15, 0.95, 2.3), P(G.box, 0.0, 1.35, -1.4, 0.6, 0.6, 0.75), P(G.cone, -0.55, 1.7, -1.4, 0.12, 0.7, 0.12, 0.0, 0.0, 1.2), P(G.cone, 0.55, 1.7, -1.4, 0.12, 0.7, 0.12, 0.0, 0.0, -1.2),
            P(G.box, -0.4, 0.35, -0.8, 0.22, 0.7, 0.22), P(G.box, 0.4, 0.35, -0.8, 0.22, 0.7, 0.22), P(G.box, -0.4, 0.35, 0.8, 0.22, 0.7, 0.22), P(G.box, 0.4, 0.35, 0.8, 0.22, 0.7, 0.22),
        ))
        val car = mergeGeo(listOf(P(G.box, 0.0, 0.55, 0.0, 1.6, 0.7, 3.6), P(G.box, 0.0, 1.1, 0.2, 1.4, 0.55, 1.9)))
        val statue = lathe(0.0 to 0.0, 0.7 to 0.0, 0.75 to 0.3, 0.55 to 0.35, 0.62 to 0.9, 0.45 to 1.5, 0.3 to 1.75, 0.24 to 1.85, 0.3 to 2.05, 0.22 to 2.3, 0.0 to 2.38, seg = 10)
        val tower = mergeGeo(listOf(P(G.box, 0.0, 2.0, 0.0, 0.12, 4.0, 0.12, 0.15, 0.0, 0.15), P(G.box, 0.0, 4.2, 0.0, 1.6, 0.15, 1.6), P(G.box, 0.0, 4.8, 0.0, 1.4, 1.0, 1.4), P(G.pyr, 0.0, 5.6, 0.0, 1.4, 0.7, 1.4)))
        val stall = mergeGeo(listOf(P(G.box, 0.0, 0.5, 0.0, 2.4, 1.0, 1.6), P(G.pyr, 0.0, 2.1, 0.0, 2.2, 0.8, 1.8)))
    }
    val GEOC = GeosC()

    init {
        ibatch("crown", GEO.ico, matBlob)
        ibatch("trunk", GEO.cyl6, white, outline = 0.0)
        ibatch("pine", GEO.cone6, matBlob)
        ibatch("palm", frondGeo, matDouble, outline = 0.04)
        ibatch("palmT", GEO.cyl6, white, outline = 0.03)
        ibatch("rock", GEO.ico, matBlob)
        ibatch("bld", GEO.box, bldMat, maxD = Q.view)
        ibatch("hoi", GEO.box, hoiMat, maxD = 2400.0)
        ibatch("wall", GEO.box, white)
        ibatch("roof", GEO.roof, white)
        ibatch("troof", GEO.troof, white)
        ibatch("lantern", GEO.ico1, lanternMat, outline = 0.0, shadow = false, maxD = 1600.0)
        ibatch("slamp", GEO.sph, lampMat, outline = 0.0, shadow = false, maxD = 1800.0)
        ibatch("umb", GEOC.umb, white, outline = 0.04, maxD = 900.0)
        ibatch("pole", GEOC.pole, white, outline = 0.0, maxD = 700.0)
        ibatch("bed", GEOC.bed, white, outline = 0.0, maxD = 500.0)
        ibatch("thung", GEOC.thung, white, outline = 0.04, maxD = 900.0)
        ibatch("boat", GEOC.boat, white, outline = 0.04, maxD = 2200.0)
        ibatch("hoiboat", GEOC.hoiboat, white, outline = 0.04, maxD = 1200.0)
        ibatch("person", GEOC.person, white, outline = 0.0, shadow = false, maxD = 420.0)
        ibatch("hat", GEOC.hat, white, outline = 0.0, shadow = false, maxD = 520.0)
        ibatch("buffalo", GEOC.buffalo, white, outline = 0.03, maxD = 600.0)
        ibatch("statue", GEOC.statue, white, outline = 0.03, maxD = 600.0)
        ibatch("ltower", GEOC.tower, white, outline = 0.03, maxD = 700.0)
        ibatch("stall", GEOC.stall, white, outline = 0.03, maxD = 800.0)
        ibatch("pool", GEO.box, toon("#ffffff") { emissive.copy(Color.of("#2fb3bf")); emissiveIntensity = 0.25 }, outline = 0.0, shadow = false, maxD = 1200.0)
        ibatch("road", GEO.box, white, outline = 0.0, shadow = false, maxD = Q.view)
    }

    object PALS {
        val forest = listOf("#3f7d45", "#4f8f4a", "#5f9e50", "#2f6b3f", "#6aa858", "#4a8a3c")
        val mtn = listOf("#3a7044", "#46804a", "#557f4a", "#2f6340", "#5a8f55")
        val karst = listOf("#6aa35a", "#7fb562", "#5a944f", "#8bbf6a")
        // Paper-World-bright walls led by Ahamove orange and blue
        val city = listOf("#ff8a3d", "#ffb98a", "#ffd2b0", "#f37021", "#9cc8f5", "#3d8be0", "#bcd9fa", "#ffc48a", "#fff1e2", "#7ee0c3", "#ffd166", "#ff9aa2", "#cfe3ff", "#ffa25c", "#5fa3ec", "#fbf8ef")
        val tall = listOf("#3d8be0", "#5fa3ec", "#9cc8f5", "#1f5fbf", "#f37021", "#ff8a3d", "#bcd9fa", "#fff1e2", "#ffb98a")
        val roofs = listOf("#f37021", "#e0662f", "#ff8a3d", "#d4552a", "#1f5fbf", "#e2743c", "#2f63b5")
        val band = listOf("#f37021", "#1f5fbf", "#ff8a3d", "#3d8be0", "#ffffff")
        val hoi = listOf("#e9c46a", "#f0cf6d", "#e5b84f", "#f2d58a", "#dfb24a", "#e8c36a", "#eac35d", "#c9d6b0", "#9fc3c8")
        val hoiRoof = listOf("#8c4a32", "#7a3e2c", "#94553a", "#6e3a2a", "#83452f")
        val rural = listOf("#f2e6c4", "#efe0b0", "#e8d6a8", "#f4ecd6", "#dce8d0")
        val ruralRoof = listOf("#b8553a", "#a84b32", "#8c8c94", "#c06040")
        val lantern = listOf("#e8332a", "#f5a623", "#f2c230", "#e0418f", "#8e44ad", "#2ecc71", "#ff6b3d", "#e8332a", "#f2c230")
        val umb = listOf("#f37021", "#1f5fbf", "#f37021", "#ffffff", "#3d8be0", "#ff8a3d", "#f2c230", "#1f9a8a", "#e0418f")
        val boat = listOf("#2f6f9f", "#2a5d8f", "#3a7d44", "#c0392b", "#2f8fb0", "#1f6f8b")
        val clothes = listOf("#e8562a", "#2a9fd6", "#f2c230", "#ffffff", "#1f9a8a", "#e0418f", "#3a5bd9", "#222222", "#f39c12", "#9b59b6")
    }

    /* =========================================================
       DISCOVERIES (dấu mộc) + golden swiftlet nests
       ========================================================= */
    val DISC = ArrayList<Disc>()
    /** halos that light up at night */
    val GLOWS = ArrayList<Glow>()
    fun disc(id: String, zone: String, name: String, han: String, desc: String, x: Double, y: Double, z: Double, r: Double = 120.0): Disc {
        val d = Disc(id, zone, name, han, desc, x, y, z, r)
        DISC.add(d)
        return d
    }
    val LANDMARKS = HashMap<String, Anchor>()

    /* =========================================================
       generic prop helpers
       ========================================================= */
    fun tree(x: Double, z: Double, s: Double, pal: List<String> = PALS.forest, trunk: Boolean = true) {
        val y = gy(x, z)
        if (trunk) put("trunk", x, y + s * 0.5, z, s * 0.35, s * 1.1, s * 0.35, rand() * 3, "#6b4a2f")
        put("crown", x, y + s * 1.45, z, s * RR(0.9, 1.15), s * RR(0.8, 1.05), s * RR(0.9, 1.15), rand() * 6, pick(pal))
        if (rand() < 0.35) put("crown", x + RR(-0.5, 0.5) * s, y + s * 2.05, z + RR(-0.5, 0.5) * s, s * 0.62, s * 0.56, s * 0.62, rand() * 6, pick(pal))
    }
    private val PALM_COLS = listOf("#3f8f3a", "#4f9f3f", "#5aa845", "#468c3a")
    fun palm(x: Double, z: Double, s: Double = 1.0, y0: Double? = null) {
        val y = y0 ?: gy(x, z)
        val lean = RR(-0.18, 0.18); val lean2 = RR(-0.18, 0.18); val h = 7.5 * s
        put("palmT", x + lean * h * 0.5, y + h / 2, z + lean2 * h * 0.5, 0.42 * s, h, 0.42 * s, 0.0, "#8b6b4a", lean2, -lean)
        put("palm", x + lean * h, y + h, z + lean2 * h, 4.2 * s, 3 * s, 4.2 * s, rand() * 6, pick(PALM_COLS))
    }
    private val PINE_COLS = listOf("#3d6b48", "#4a7a52", "#355f40")
    fun casuarina(x: Double, z: Double, s: Double = 1.0) {
        val y = gy(x, z)
        put("trunk", x, y + 1, z, 0.3 * s, 2 * s, 0.3 * s, 0.0, "#6b4a2f")
        put("pine", x, y + 2 + 3.2 * s, z, 1.5 * s, 7 * s, 1.5 * s, rand() * 3, pick(PINE_COLS))
    }
    fun slope(x: Double, z: Double): Double {
        val a = gy(x + 4, z) - gy(x - 4, z); val b = gy(x, z + 4) - gy(x, z - 4)
        return hypot(a, b) / 8
    }
    /** circles kept free of scatter: [x, z, r] */
    val KEEP = ArrayList<DoubleArray>()
    fun keep(x: Double, z: Double, r: Double) { KEEP.add(doubleArrayOf(x, z, r)) }
    fun kept(x: Double, z: Double): Boolean { for (k in KEEP) if ((x - k[0]).pow(2) + (z - k[1]).pow(2) < k[2] * k[2]) return true; return false }
    /** road ribbon on the ground */
    val roadTex = canvasTex(64, 256) { g, W, H ->
        val rnd = java.util.Random(7)
        g.setFill(argb("#6e6c7c")); g.fillRect(0f, 0f, W.toFloat(), H.toFloat())
        for (i in 0 until 60) { g.setFill(rgba(255, 255, 255, rnd.nextDouble() * 0.05)); g.fillRect(rnd.nextFloat() * W, rnd.nextFloat() * H, 6f, 6f) }
        g.setFill(argb("#f2f0e6")); g.fillRect(3f, 0f, 3f, H.toFloat()); g.fillRect(W - 6f, 0f, 3f, H.toFloat())
        g.setFill(argb("#f2c230")); g.fillRect(W / 2 - 1.5f, 20f, 3f, 90f); g.fillRect(W / 2 - 1.5f, 148f, 3f, 90f)
    }
    val roadMat = toon("#ffffff") { map = roadTex; polygonOffset = true; polyFactor = -2f; polyUnits = -2f }
    /** road polylines: each point is [x, z] */
    val ROADS = ArrayList<List<DoubleArray>>()
    /** pts: [x, z] or [x, z, y] */
    fun ribbon(pts: List<DoubleArray>, w: Double, lift: Double = 0.25, mat: Material = roadMat, vScale: Double = 0.04): Mesh {
        val P = FloatList(); val UV = FloatList(); val I = IntList()
        var v = 0.0
        for (i in pts.indices) {
            val a = pts[max(0, i - 1)]; val b = pts[min(pts.size - 1, i + 1)]
            var dx = b[0] - a[0]; var dz = b[1] - a[1]
            val l = hypot(dx, dz).let { if (it == 0.0) 1.0 else it }
            dx /= l; dz /= l
            val nx = -dz; val nz = dx; val x = pts[i][0]; val z = pts[i][1]
            if (i > 0) v += hypot(x - pts[i - 1][0], z - pts[i - 1][1]) * vScale
            val fixed = if (pts[i].size > 2) pts[i][2] else Double.NaN
            val yl = (if (fixed.isNaN()) max(gy(x + nx * w * 0.5, z + nz * w * 0.5), 0.3) else fixed) + lift
            val yr = (if (fixed.isNaN()) max(gy(x - nx * w * 0.5, z - nz * w * 0.5), 0.3) else fixed) + lift
            P.add3(x + nx * w * 0.5, yl, z + nz * w * 0.5); P.add3(x - nx * w * 0.5, yr, z - nz * w * 0.5)
            UV.add(0.0); UV.add(v); UV.add(1.0); UV.add(v)
            if (i > 0) { val k = i * 2; I.add3(k - 2, k - 1, k); I.add3(k - 1, k + 1, k) }
        }
        val g = Geometry.of(P.toArray(), null, UV.toArray(), I.toArray())
        g.computeVertexNormals()
        val m = Mesh(g, mat)
        m.receiveShadow = true
        scene.add(m)
        return m
    }

    /* =========================================================
       LANDMARKS — shared registries
       ========================================================= */
    val BRIDGES = ArrayList<Bridge>()
    /** per-frame animations f(dt, t) */
    val ANIM = ArrayList<(Double, Double) -> Unit>()

    /* ---------- Ahamove: brand colours, painted signs, helipads ---------- */
    class Pad(val x: Double, val y: Double, val z: Double, val r: Double) { var hub = false }
    object AHA { const val orange = "#f37021"; const val blue = "#1f5fbf"; val pads = ArrayList<Pad>() }
    fun ahaTex(bg: String, fg: String, txt: String, w: Int = 512, h: Int = 128) = canvasTex(w, h, false) { g, W, H ->
        g.setFill(argb(bg)); g.fillRect(0f, 0f, W.toFloat(), H.toFloat())
        g.setFill(argb(fg)); g.setFont(Face.UI800, H * 0.62f); g.setTextAlign(Align.CENTER); g.setTextBaseline(Baseline.MIDDLE)
        g.fillText(txt, W / 2f, H * 0.54f)
    }
    val padTex = canvasTex(256, 256, false) { g, W, _ ->
        g.setFill(argb("#2b2f3a")); g.beginPath(); g.arc(W / 2f, W / 2f, W / 2f, 0f, 7f); g.fill()
        g.setStroke(argb(AHA.orange)); g.setLineWidth(W * 0.06f); g.beginPath(); g.arc(W / 2f, W / 2f, W * 0.4f, 0f, 7f); g.stroke()
        g.setFill(argb("#ffffff")); g.setFont(Face.UI800, W * 0.5f); g.setTextAlign(Align.CENTER); g.setTextBaseline(Baseline.MIDDLE); g.fillText("H", W / 2f, W * 0.53f)
    }
    fun helipad(x: Double, y: Double, z: Double, r: Double = 9.0): Pad {
        val m = Mesh(Geo.cylinder(r, r, 0.5, 28), white)
        m.materials = listOf(toon("#2b2f3a"), toon("#ffffff") { map = padTex }, toon("#2b2f3a"))
        m.position.set(x, y + 0.25, z)
        m.receiveShadow = true
        scene.add(m)
        val pad = Pad(x, y + 0.5, z, r)
        AHA.pads.add(pad)
        return pad
    }
    /** highest ground sample in a square: [x, z, h] */
    fun findHigh(cx: Double, cz: Double, r: Double, step: Double = 10.0): DoubleArray {
        var best = doubleArrayOf(cx, cz, -1e9)
        var x = cx - r
        while (x <= cx + r) { var z = cz - r; while (z <= cz + r) { val h = gy(x, z); if (h > best[2]) best = doubleArrayOf(x, z, h); z += step }; x += step }
        return best
    }
    /** wind socks: filled by the Sơn Trà / airport builders, animated by the wind */
    val WINDSOCKS = ArrayList<Object3D>()
    fun lighthouse(x: Double, z: Double, name: String, id: String, zone: String, desc: String, han: String = ""): Vec3 {
        val y = gy(x, z); val g = Group()
        g.position.set(x, y, z)
        scene.add(g)
        mk(lathe(0.0 to 0.0, 3.0 to 0.0, 2.4 to 16.0, 2.2 to 18.0, 0.0 to 18.0, seg = 14), "#f7f5ef", 0.0, 0.0, 0.0, g, 0.06)
        box(5.6, 0.5, 5.6, "#3a3848", 0.0, 18.2, 0.0, g, 0.03)
        val lampM = toon("#fff2b0") { emissive.copy(Color.of("#ffd35a")); emissiveIntensity = 0.3 }
        val lamp = mk(GEO.cyl16, lampM, 0.0, 19.8, 0.0, g, 0.03)
        lamp.scale.set(1.8, 3.0, 1.8)
        NIGHT.add { gl -> lampM.emissiveIntensity = 0.3 + gl * 2 }
        val cap = mk(GEO.cone, "#d6452f", 0.0, 22.3, 0.0, g, 0.04); cap.scale.set(2.3, 2.2, 2.3)
        val beamM = BasicMaterial("#fff3c0").also { it.transparent = true; it.opacity = 0.0; it.depthWrite = false; it.side = Side.DOUBLE; it.blending = Blend.ADDITIVE }
        val beam = Mesh(Geo.cylinder(0.0, 14.0, 160.0, 16, 1, true).translate(0.0, -80.0, 0.0).rotateZ(PI / 2), beamM)
        beam.position.set(0.0, 19.8, 0.0)
        g.add(beam)
        ANIM.add { _, t -> beam.rotation.y = t * 0.9; beamM.opacity = smooth(0.3, 0.9, env.glow) * 0.22; beam.visible = env.glow > 0.3 }
        colCyl(x, z, 3.2, y - 2, y + 25, "lighthouse")
        keep(x, z, 14.0)
        disc(id, zone, name, han, desc, x, y + 20, z, 110.0)
        return Vec3(x, y, z)
    }

    /* ---------- registries filled by World+City.kt ---------- */
    /** Dragon Bridge state: LED materials [scales, fins], fire point, head group (read by the fire animation) */
    class DragonState { val mats = ArrayList<ToonMaterial>(); val head = Vec3(); val dir = Vec3(1.0, 0.0, 0.0); var fireT = 0.0; var fire = 0.0; var headG: Group? = null }
    val DRAGON = DragonState()
    /** Đà Nẵng airport runway: x and its z extent */
    class AirportArea(val x: Double, val z0: Double, val z1: Double)
    val AIRPORT = AirportArea(-900.0, -700.0, 450.0)

    /* ---------- registries filled by World+SonTra.kt / World+Coast.kt ---------- */
    /** car spawn z's along the coast road (web COAST_CARS) */
    val COAST_CARS = ArrayList<Double>()
    /** anchored fishing boats [x, z] (web BOAT_LIGHTS) */
    val BOAT_LIGHTS = ArrayList<DoubleArray>()
    /** douc langurs: bobbing group, base y, phase (web DOUCS: { g, y, ph }) */
    class Douc(val g: Group, val y: Double, val ph: Double)
    val DOUCS = ArrayList<Douc>()
    /** discoveries found at several spots (web disc.multi: [[x, z], ...]) */
    val DISC_MULTI = HashMap<Disc, List<DoubleArray>>()

    /* ---------- registries filled by World+South.kt ---------- */
    /** Hội An lantern strings across the streets: [x0, y0, z0, x1, y1, z1] */
    val LANTERN_LINES = ArrayList<DoubleArray>()
    /** Bà Nà cable car: cabins run between the stations a → b (animated by the game loop) */
    class CableCar(val g: Group, var t: Double, val lane: Int)
    class Cable { val cars = ArrayList<CableCar>(); val a = Vec3(); val b = Vec3() }
    val CABLE = Cable()

    /* ---------- (end of builder registries) ---------- */

    companion object {
        const val CELL = 800.0
        const val COLC = 64.0
        val OUTLINE = Any()
        fun pt(x: Double, z: Double) = doubleArrayOf(x, z)
        fun pt(x: Double, z: Double, y: Double) = doubleArrayOf(x, z, y)
    }
}
