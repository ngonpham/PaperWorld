package vn.paperworld.phidoi.engine

import kotlin.math.*

/** A buffer geometry: flat float arrays per attribute, optional index, optional material groups. */
class Geometry {
    var position = FloatArray(0)
    var normal: FloatArray? = null
    var uv: FloatArray? = null
    var color: FloatArray? = null
    var colorSize = 3
    var onormal: FloatArray? = null
    var index: IntArray? = null
    val groups = ArrayList<IntArray>() // [start, count, materialIndex]
    /** -1 = draw everything */
    var drawCount = -1
    var dynamic = false
    var boundingSphere: Sphere? = null
    /** bump after changing an attribute in place so the GPU copy is refreshed */
    var posVersion = 0
    var colVersion = 0
    var norVersion = 0
    @JvmField var gpu: Any? = null

    val vertexCount get() = position.size / 3

    fun addGroup(start: Int, count: Int, mi: Int) { groups.add(intArrayOf(start, count, mi)) }

    fun applyMatrix(m: Mat4): Geometry {
        val v = Vec3()
        for (i in 0 until vertexCount) { v.set(position[i * 3].toDouble(), position[i * 3 + 1].toDouble(), position[i * 3 + 2].toDouble()).applyMatrix4(m); position[i * 3] = v.x.toFloat(); position[i * 3 + 1] = v.y.toFloat(); position[i * 3 + 2] = v.z.toFloat() }
        val n = normal
        if (n != null) {
            val nm = Mat3().getNormalMatrix(m)
            for (i in 0 until vertexCount) { v.set(n[i * 3].toDouble(), n[i * 3 + 1].toDouble(), n[i * 3 + 2].toDouble()).applyMatrix3(nm).normalize(); n[i * 3] = v.x.toFloat(); n[i * 3 + 1] = v.y.toFloat(); n[i * 3 + 2] = v.z.toFloat() }
        }
        boundingSphere = null
        return this
    }
    fun rotateX(a: Double) = applyMatrix(Mat4().compose(Vec3(), Quat().setFromAxisAngle(Vec3(1.0, 0.0, 0.0), a), Vec3(1.0, 1.0, 1.0)))
    fun rotateY(a: Double) = applyMatrix(Mat4().compose(Vec3(), Quat().setFromAxisAngle(Vec3(0.0, 1.0, 0.0), a), Vec3(1.0, 1.0, 1.0)))
    fun rotateZ(a: Double) = applyMatrix(Mat4().compose(Vec3(), Quat().setFromAxisAngle(Vec3(0.0, 0.0, 1.0), a), Vec3(1.0, 1.0, 1.0)))
    fun translate(x: Double, y: Double, z: Double): Geometry {
        for (i in 0 until vertexCount) { position[i * 3] += x.toFloat(); position[i * 3 + 1] += y.toFloat(); position[i * 3 + 2] += z.toFloat() }
        boundingSphere = null
        return this
    }

    fun toNonIndexed(): Geometry {
        val idx = index ?: return this
        val g = Geometry()
        fun expand(src: FloatArray?, size: Int): FloatArray? {
            if (src == null) return null
            val out = FloatArray(idx.size * size)
            for (i in idx.indices) System.arraycopy(src, idx[i] * size, out, i * size, size)
            return out
        }
        g.position = expand(position, 3)!!
        g.normal = expand(normal, 3); g.uv = expand(uv, 2); g.color = expand(color, colorSize); g.colorSize = colorSize; g.onormal = expand(onormal, 3)
        return g
    }

    fun computeVertexNormals(): Geometry {
        val n = FloatArray(position.size)
        val p = position
        val idx = index
        val a = Vec3(); val b = Vec3(); val c = Vec3(); val cb = Vec3(); val ab = Vec3()
        fun face(ia: Int, ib: Int, ic: Int, accumulate: Boolean) {
            a.set(p[ia * 3].toDouble(), p[ia * 3 + 1].toDouble(), p[ia * 3 + 2].toDouble())
            b.set(p[ib * 3].toDouble(), p[ib * 3 + 1].toDouble(), p[ib * 3 + 2].toDouble())
            c.set(p[ic * 3].toDouble(), p[ic * 3 + 1].toDouble(), p[ic * 3 + 2].toDouble())
            cb.copy(c).sub(b); ab.copy(a).sub(b); cb.cross(ab)
            for (i in intArrayOf(ia, ib, ic)) {
                if (accumulate) { n[i * 3] += cb.x.toFloat(); n[i * 3 + 1] += cb.y.toFloat(); n[i * 3 + 2] += cb.z.toFloat() }
                else { n[i * 3] = cb.x.toFloat(); n[i * 3 + 1] = cb.y.toFloat(); n[i * 3 + 2] = cb.z.toFloat() }
            }
        }
        if (idx != null) { var i = 0; while (i < idx.size) { face(idx[i], idx[i + 1], idx[i + 2], true); i += 3 } }
        else { var i = 0; while (i < vertexCount) { face(i, i + 1, i + 2, false); i += 3 } }
        for (i in 0 until vertexCount) {
            val l = sqrt(n[i * 3] * n[i * 3] + n[i * 3 + 1] * n[i * 3 + 1] + n[i * 3 + 2] * n[i * 3 + 2])
            if (l > 0) { n[i * 3] /= l; n[i * 3 + 1] /= l; n[i * 3 + 2] /= l }
        }
        normal = n
        norVersion++
        return this
    }

    fun computeBoundingSphere(): Sphere {
        val p = position
        var x0 = Double.MAX_VALUE; var y0 = x0; var z0 = x0; var x1 = -x0; var y1 = -x0; var z1 = -x0
        for (i in 0 until vertexCount) {
            val x = p[i * 3].toDouble(); val y = p[i * 3 + 1].toDouble(); val z = p[i * 3 + 2].toDouble()
            if (x < x0) x0 = x; if (y < y0) y0 = y; if (z < z0) z0 = z; if (x > x1) x1 = x; if (y > y1) y1 = y; if (z > z1) z1 = z
        }
        val s = Sphere()
        if (vertexCount == 0) { s.radius = 0.0; boundingSphere = s; return s }
        s.center.set((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2)
        var r2 = 0.0
        for (i in 0 until vertexCount) {
            val dx = p[i * 3] - s.center.x; val dy = p[i * 3 + 1] - s.center.y; val dz = p[i * 3 + 2] - s.center.z
            r2 = max(r2, dx * dx + dy * dy + dz * dz)
        }
        s.radius = sqrt(r2)
        boundingSphere = s
        return s
    }

    /** smooth "outline normals": vertex normals averaged over coincident positions (inverted-hull ink) */
    fun addONormal(): Geometry {
        if (onormal != null) return this
        val n = normal ?: computeVertexNormals().normal!!
        val map = HashMap<PosKey, DoubleArray>()
        fun key(i: Int) = PosKey(Math.round(position[i * 3] * 1e3).toInt(), Math.round(position[i * 3 + 1] * 1e3).toInt(), Math.round(position[i * 3 + 2] * 1e3).toInt())
        for (i in 0 until vertexCount) {
            val v = map.getOrPut(key(i)) { DoubleArray(3) }
            v[0] += n[i * 3].toDouble(); v[1] += n[i * 3 + 1].toDouble(); v[2] += n[i * 3 + 2].toDouble()
        }
        val out = FloatArray(position.size)
        for (i in 0 until vertexCount) {
            val v = map[key(i)]!!
            val l = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
            if (l > 0) { out[i * 3] = (v[0] / l).toFloat(); out[i * 3 + 1] = (v[1] / l).toFloat(); out[i * 3 + 2] = (v[2] / l).toFloat() }
        }
        onormal = out
        return this
    }

    fun clone(): Geometry {
        val g = Geometry()
        g.position = position.copyOf(); g.normal = normal?.copyOf(); g.uv = uv?.copyOf(); g.color = color?.copyOf(); g.colorSize = colorSize
        g.onormal = onormal?.copyOf(); g.index = index?.copyOf(); g.groups.addAll(groups.map { it.copyOf() })
        return g
    }

    companion object {
        fun of(pos: FloatArray, nor: FloatArray?, uv: FloatArray?, idx: IntArray?): Geometry {
            val g = Geometry(); g.position = pos; g.normal = nor; g.uv = uv; g.index = idx; return g
        }
    }
}

data class PosKey(val x: Int, val y: Int, val z: Int)

/** growable builders */
class FloatList(cap: Int = 64) {
    var a = FloatArray(cap); var n = 0
    fun add(v: Float) { if (n == a.size) a = a.copyOf(a.size * 2); a[n++] = v }
    fun add(x: Double) = add(x.toFloat())
    fun add3(x: Double, y: Double, z: Double) { add(x.toFloat()); add(y.toFloat()); add(z.toFloat()) }
    fun toArray() = a.copyOf(n)
}
class IntList(cap: Int = 64) {
    var a = IntArray(cap); var n = 0
    fun add(v: Int) { if (n == a.size) a = a.copyOf(a.size * 2); a[n++] = v }
    fun add3(x: Int, y: Int, z: Int) { add(x); add(y); add(z) }
    fun toArray() = a.copyOf(n)
}

/** three.js geometry generators, ported 1:1 */
object Geo {
    fun box(width: Double = 1.0, height: Double = 1.0, depth: Double = 1.0, ws: Int = 1, hs: Int = 1, ds: Int = 1): Geometry {
        val pos = FloatList(); val nor = FloatList(); val uvs = FloatList(); val idx = IntList()
        var nv = 0
        fun plane(u: Int, v: Int, w: Int, udir: Double, vdir: Double, width: Double, height: Double, depth: Double, gx: Int, gy: Int) {
            val sw = width / gx; val sh = height / gy; val wh = width / 2; val hh = height / 2; val dh = depth / 2
            val gx1 = gx + 1
            val vec = DoubleArray(3)
            for (iy in 0..gy) {
                val y = iy * sh - hh
                for (ix in 0..gx) {
                    val x = ix * sw - wh
                    vec[u] = x * udir; vec[v] = y * vdir; vec[w] = dh
                    pos.add3(vec[0], vec[1], vec[2])
                    vec[u] = 0.0; vec[v] = 0.0; vec[w] = if (depth > 0) 1.0 else -1.0
                    nor.add3(vec[0], vec[1], vec[2])
                    uvs.add(ix.toFloat() / gx); uvs.add(1 - iy.toFloat() / gy)
                }
            }
            for (iy in 0 until gy) for (ix in 0 until gx) {
                val a = nv + ix + gx1 * iy; val b = nv + ix + gx1 * (iy + 1); val c = nv + (ix + 1) + gx1 * (iy + 1); val d = nv + (ix + 1) + gx1 * iy
                idx.add3(a, b, d); idx.add3(b, c, d)
            }
            nv += (gx + 1) * (gy + 1)
        }
        plane(2, 1, 0, -1.0, -1.0, depth, height, width, ds, hs)
        plane(2, 1, 0, 1.0, -1.0, depth, height, -width, ds, hs)
        plane(0, 2, 1, 1.0, 1.0, width, depth, height, ws, ds)
        plane(0, 2, 1, 1.0, -1.0, width, depth, -height, ws, ds)
        plane(0, 1, 2, 1.0, -1.0, width, height, depth, ws, hs)
        plane(0, 1, 2, -1.0, -1.0, width, height, -depth, ws, hs)
        return Geometry.of(pos.toArray(), nor.toArray(), uvs.toArray(), idx.toArray())
    }

    fun cylinder(rt: Double, rb: Double, height: Double, radial: Int, hseg: Int = 1, open: Boolean = false, thetaStart: Double = 0.0, thetaLength: Double = TAU): Geometry {
        val pos = FloatList(); val nor = FloatList(); val uvs = FloatList(); val idx = IntList()
        val g = Geometry()
        var index = 0
        val halfH = height / 2
        val grid = ArrayList<IntArray>()
        var groupStart = 0
        val slope = (rb - rt) / height
        for (y in 0..hseg) {
            val row = IntArray(radial + 1)
            val v = y.toDouble() / hseg
            val radius = v * (rb - rt) + rt
            for (x in 0..radial) {
                val u = x.toDouble() / radial
                val th = u * thetaLength + thetaStart
                val s = sin(th); val c = cos(th)
                pos.add3(radius * s, -v * height + halfH, radius * c)
                val n = Vec3(s, slope, c).normalize()
                nor.add3(n.x, n.y, n.z)
                uvs.add(u); uvs.add(1 - v)
                row[x] = index++
            }
            grid.add(row)
        }
        for (x in 0 until radial) for (y in 0 until hseg) {
            val a = grid[y][x]; val b = grid[y + 1][x]; val c = grid[y + 1][x + 1]; val d = grid[y][x + 1]
            if (rt > 0 || y != 0) idx.add3(a, b, d)
            if (rb > 0 || y != hseg - 1) idx.add3(b, c, d)
        }
        g.addGroup(groupStart, idx.n, 0)
        groupStart = idx.n
        if (!open) {
            for ((mi, top) in listOf(Pair(1, true), Pair(2, false))) {
                val radius = if (top) rt else rb
                if (radius <= 0) continue
                val sign = if (top) 1.0 else -1.0
                val centerStart = index
                for (x in 1..radial) { pos.add3(0.0, halfH * sign, 0.0); nor.add3(0.0, sign, 0.0); uvs.add(0.5f); uvs.add(0.5f); index++ }
                val centerEnd = index
                for (x in 0..radial) {
                    val u = x.toDouble() / radial; val th = u * thetaLength + thetaStart; val c = cos(th); val s = sin(th)
                    pos.add3(radius * s, halfH * sign, radius * c); nor.add3(0.0, sign, 0.0)
                    uvs.add((c * 0.5 + 0.5).toFloat()); uvs.add((s * 0.5 * sign + 0.5).toFloat())
                    index++
                }
                val start = idx.n
                for (x in 0 until radial) {
                    val c = centerStart + x; val i = centerEnd + x
                    if (top) idx.add3(i, i + 1, c) else idx.add3(i + 1, i, c)
                }
                g.addGroup(start, idx.n - start, mi)
            }
        }
        g.position = pos.toArray(); g.normal = nor.toArray(); g.uv = uvs.toArray(); g.index = idx.toArray()
        return g
    }

    fun cone(radius: Double, height: Double, radial: Int, hseg: Int = 1) = cylinder(0.0, radius, height, radial, hseg)

    fun sphere(radius: Double, ws: Int, hs: Int, phiStart: Double = 0.0, phiLength: Double = TAU, thetaStart: Double = 0.0, thetaLength: Double = PI): Geometry {
        val pos = FloatList(); val nor = FloatList(); val uvs = FloatList(); val idx = IntList()
        val thetaEnd = min(thetaStart + thetaLength, PI)
        val grid = ArrayList<IntArray>()
        var index = 0
        for (iy in 0..hs) {
            val row = IntArray(ws + 1)
            val v = iy.toDouble() / hs
            var uOff = 0.0
            if (iy == 0 && thetaStart == 0.0) uOff = 0.5 / ws else if (iy == hs && thetaEnd == PI) uOff = -0.5 / ws
            for (ix in 0..ws) {
                val u = ix.toDouble() / ws
                val x = -radius * cos(phiStart + u * phiLength) * sin(thetaStart + v * thetaLength)
                val y = radius * cos(thetaStart + v * thetaLength)
                val z = radius * sin(phiStart + u * phiLength) * sin(thetaStart + v * thetaLength)
                pos.add3(x, y, z)
                val n = Vec3(x, y, z).normalize(); nor.add3(n.x, n.y, n.z)
                uvs.add(u + uOff); uvs.add(1 - v)
                row[ix] = index++
            }
            grid.add(row)
        }
        for (iy in 0 until hs) for (ix in 0 until ws) {
            val a = grid[iy][ix + 1]; val b = grid[iy][ix]; val c = grid[iy + 1][ix]; val d = grid[iy + 1][ix + 1]
            if (iy != 0 || thetaStart > 0) idx.add3(a, b, d)
            if (iy != hs - 1 || thetaEnd < PI) idx.add3(b, c, d)
        }
        return Geometry.of(pos.toArray(), nor.toArray(), uvs.toArray(), idx.toArray())
    }

    fun icosahedron(radius: Double, detail: Int): Geometry {
        val t = (1 + sqrt(5.0)) / 2
        val vs = doubleArrayOf(-1.0, t, 0.0, 1.0, t, 0.0, -1.0, -t, 0.0, 1.0, -t, 0.0, 0.0, -1.0, t, 0.0, 1.0, t, 0.0, -1.0, -t, 0.0, 1.0, -t, t, 0.0, -1.0, t, 0.0, 1.0, -t, 0.0, -1.0, -t, 0.0, 1.0)
        val ids = intArrayOf(0, 11, 5, 0, 5, 1, 0, 1, 7, 0, 7, 10, 0, 10, 11, 1, 5, 9, 5, 11, 4, 11, 10, 2, 10, 7, 6, 7, 1, 8, 3, 9, 4, 3, 4, 2, 3, 2, 6, 3, 6, 8, 3, 8, 9, 4, 9, 5, 2, 4, 11, 6, 2, 10, 8, 6, 7, 9, 8, 1)
        val pos = FloatList()
        fun push(v: Vec3) { val n = v.clone().normalize().multiplyScalar(radius); pos.add3(n.x, n.y, n.z) }
        val cols = detail + 1
        var f = 0
        while (f < ids.size) {
            val a = Vec3(vs[ids[f] * 3], vs[ids[f] * 3 + 1], vs[ids[f] * 3 + 2])
            val b = Vec3(vs[ids[f + 1] * 3], vs[ids[f + 1] * 3 + 1], vs[ids[f + 1] * 3 + 2])
            val c = Vec3(vs[ids[f + 2] * 3], vs[ids[f + 2] * 3 + 1], vs[ids[f + 2] * 3 + 2])
            val v = ArrayList<ArrayList<Vec3>>()
            for (i in 0..cols) {
                val row = ArrayList<Vec3>()
                val aj = a.clone().lerp(c, i.toDouble() / cols); val bj = b.clone().lerp(c, i.toDouble() / cols)
                val rows = cols - i
                for (j in 0..rows) row.add(if (j == 0 && i == cols) aj else aj.clone().lerp(bj, j.toDouble() / rows))
                v.add(row)
            }
            for (i in 0 until cols) for (j in 0 until 2 * (cols - i) - 1) {
                val k = j / 2
                if (j % 2 == 0) { push(v[i][k + 1]); push(v[i + 1][k]); push(v[i][k]) }
                else { push(v[i][k + 1]); push(v[i + 1][k + 1]); push(v[i + 1][k]) }
            }
            f += 3
        }
        val g = Geometry(); g.position = pos.toArray()
        if (detail == 0) g.computeVertexNormals()
        else { val n = g.position.copyOf(); for (i in 0 until g.vertexCount) { val l = sqrt(n[i * 3] * n[i * 3] + n[i * 3 + 1] * n[i * 3 + 1] + n[i * 3 + 2] * n[i * 3 + 2]); n[i * 3] /= l; n[i * 3 + 1] /= l; n[i * 3 + 2] /= l }; g.normal = n }
        return g
    }

    fun torus(radius: Double, tube: Double, radial: Int, tubular: Int, arc: Double = TAU): Geometry {
        val pos = FloatList(); val nor = FloatList(); val uvs = FloatList(); val idx = IntList()
        for (j in 0..radial) for (i in 0..tubular) {
            val u = i.toDouble() / tubular * arc; val v = j.toDouble() / radial * TAU
            val x = (radius + tube * cos(v)) * cos(u); val y = (radius + tube * cos(v)) * sin(u); val z = tube * sin(v)
            pos.add3(x, y, z)
            val n = Vec3(x - radius * cos(u), y - radius * sin(u), z).normalize(); nor.add3(n.x, n.y, n.z)
            uvs.add(i.toFloat() / tubular); uvs.add(j.toFloat() / radial)
        }
        for (j in 1..radial) for (i in 1..tubular) {
            val a = (tubular + 1) * j + i - 1; val b = (tubular + 1) * (j - 1) + i - 1; val c = (tubular + 1) * (j - 1) + i; val d = (tubular + 1) * j + i
            idx.add3(a, b, d); idx.add3(b, c, d)
        }
        return Geometry.of(pos.toArray(), nor.toArray(), uvs.toArray(), idx.toArray())
    }

    fun lathe(points: List<DoubleArray>, segments: Int = 12): Geometry {
        val pos = FloatList(); val uvs = FloatList(); val idx = IntList()
        for (i in 0..segments) {
            val phi = i.toDouble() / segments * TAU; val s = sin(phi); val c = cos(phi)
            for (j in points.indices) { pos.add3(points[j][0] * s, points[j][1], points[j][0] * c); uvs.add(i.toFloat() / segments); uvs.add(j.toFloat() / (points.size - 1)) }
        }
        for (i in 0 until segments) for (j in 0 until points.size - 1) {
            val base = j + i * points.size
            val a = base; val b = base + points.size; val c = base + points.size + 1; val d = base + 1
            idx.add3(a, b, d); idx.add3(c, d, b)
        }
        val g = Geometry.of(pos.toArray(), null, uvs.toArray(), idx.toArray())
        g.computeVertexNormals()
        return g
    }

    fun plane(w: Double = 1.0, h: Double = 1.0): Geometry =
        Geometry.of(floatArrayOf((-w / 2).toFloat(), (h / 2).toFloat(), 0f, (w / 2).toFloat(), (h / 2).toFloat(), 0f, (-w / 2).toFloat(), (-h / 2).toFloat(), 0f, (w / 2).toFloat(), (-h / 2).toFloat(), 0f),
            floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f), floatArrayOf(0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f), intArrayOf(0, 2, 1, 2, 3, 1))

    fun circle(radius: Double, segments: Int): Geometry {
        val pos = FloatList(); val nor = FloatList(); val uvs = FloatList(); val idx = IntList()
        pos.add3(0.0, 0.0, 0.0); nor.add3(0.0, 0.0, 1.0); uvs.add(0.5f); uvs.add(0.5f)
        for (s in 0..segments) {
            val th = s.toDouble() / segments * TAU
            pos.add3(radius * cos(th), radius * sin(th), 0.0); nor.add3(0.0, 0.0, 1.0)
            uvs.add((cos(th) * 0.5 + 0.5).toFloat()); uvs.add((sin(th) * 0.5 + 0.5).toFloat())
        }
        for (i in 1..segments) idx.add3(i, i + 1, 0)
        return Geometry.of(pos.toArray(), nor.toArray(), uvs.toArray(), idx.toArray())
    }

    /** non-indexed triangle soup from raw positions, flat normals (BufferGeometry + computeVertexNormals) */
    fun tris(p: FloatArray): Geometry { val g = Geometry(); g.position = p; g.computeVertexNormals(); return g }
    fun tris(p: DoubleArray): Geometry = tris(FloatArray(p.size) { p[it].toFloat() })

    /** merge transformed parts into one non-indexed geometry: [geo, x,y,z, sx,sy,sz, rx,ry,rz (YXZ), colour?] */
    class Part(val g: Geometry, val x: Double, val y: Double, val z: Double, val sx: Double = 1.0, val sy: Double = 1.0, val sz: Double = 1.0, val rx: Double = 0.0, val ry: Double = 0.0, val rz: Double = 0.0, val col: String? = null)

    fun merge(parts: List<Part>): Geometry {
        val colored = parts.any { it.col != null }
        val P = FloatList(); val N = FloatList(); val CO = FloatList()
        val m4 = Mat4(); val q = Quat(); val e = Euler(); val nm = Mat3(); val v = Vec3(); val s = Vec3(); val t = Vec3()
        val cc = Color()
        for (pt in parts) {
            cc.set(pt.col ?: "#ffffff")
            val g = pt.g.toNonIndexed()
            val nor = g.normal ?: g.computeVertexNormals().normal!!
            m4.compose(t.set(pt.x, pt.y, pt.z), q.setFromEuler(e.set(pt.rx, pt.ry, pt.rz, Euler.YXZ)), s.set(pt.sx, pt.sy, pt.sz))
            nm.getNormalMatrix(m4)
            for (i in 0 until g.vertexCount) {
                v.set(g.position[i * 3].toDouble(), g.position[i * 3 + 1].toDouble(), g.position[i * 3 + 2].toDouble()).applyMatrix4(m4); P.add3(v.x, v.y, v.z)
                v.set(nor[i * 3].toDouble(), nor[i * 3 + 1].toDouble(), nor[i * 3 + 2].toDouble()).applyMatrix3(nm).normalize(); N.add3(v.x, v.y, v.z)
                if (colored) CO.add3(cc.r, cc.g, cc.b)
            }
        }
        val g = Geometry(); g.position = P.toArray(); g.normal = N.toArray()
        if (colored) g.color = CO.toArray()
        return g.addONormal()
    }
}

/** centripetal Catmull–Rom curve with arc-length lookup (three.js CatmullRomCurve3 defaults) */
class CatmullRom3(private val pts: List<Vec3>) {
    private var lengths: DoubleArray? = null
    private val divisions = 200

    fun getPoint(t: Double, out: Vec3 = Vec3()): Vec3 {
        val l = pts.size
        val p = (l - 1) * t
        var ip = floor(p).toInt()
        var w = p - ip
        if (w == 0.0 && ip == l - 1) { ip = l - 2; w = 1.0 }
        val p0 = if (ip > 0) pts[ip - 1] else Vec3().copy(pts[0]).sub(pts[1]).add(pts[0])
        val p1 = pts[ip]; val p2 = pts[ip + 1]
        val p3 = if (ip + 2 < l) pts[ip + 2] else Vec3().copy(pts[l - 1]).sub(pts[l - 2]).add(pts[l - 1])
        var dt0 = p0.distanceToSquared(p1).pow(0.25); var dt1 = p1.distanceToSquared(p2).pow(0.25); var dt2 = p2.distanceToSquared(p3).pow(0.25)
        if (dt1 < 1e-4) dt1 = 1.0
        if (dt0 < 1e-4) dt0 = dt1
        if (dt2 < 1e-4) dt2 = dt1
        fun c(x0: Double, x1: Double, x2: Double, x3: Double): Double {
            var t1 = (x1 - x0) / dt0 - (x2 - x0) / (dt0 + dt1) + (x2 - x1) / dt1
            var t2 = (x2 - x1) / dt1 - (x3 - x1) / (dt1 + dt2) + (x3 - x2) / dt2
            t1 *= dt1; t2 *= dt1
            val cc0 = x1; val cc1 = t1; val cc2 = -3 * x1 + 3 * x2 - 2 * t1 - t2; val cc3 = 2 * x1 - 2 * x2 + t1 + t2
            return cc0 + cc1 * w + cc2 * w * w + cc3 * w * w * w
        }
        return out.set(c(p0.x, p1.x, p2.x, p3.x), c(p0.y, p1.y, p2.y, p3.y), c(p0.z, p1.z, p2.z, p3.z))
    }
    private fun lens(): DoubleArray {
        lengths?.let { return it }
        val arr = DoubleArray(divisions + 1)
        var last = getPoint(0.0); var sum = 0.0
        for (i in 1..divisions) { val p = getPoint(i.toDouble() / divisions); sum += p.distanceTo(last); arr[i] = sum; last = p }
        lengths = arr
        return arr
    }
    fun getUtoTmapping(u: Double): Double {
        val arc = lens(); val il = arc.size
        val target = u * arc[il - 1]
        var low = 0; var high = il - 1
        while (low <= high) {
            val i = low + (high - low) / 2
            val cmp = arc[i] - target
            if (cmp < 0) low = i + 1 else if (cmp > 0) high = i - 1 else { high = i; break }
        }
        val i = max(high, 0)
        if (arc[i] == target) return i.toDouble() / (il - 1)
        val before = arc[i]; val after = arc[min(i + 1, il - 1)]
        val seg = after - before
        val frac = if (seg > 0) (target - before) / seg else 0.0
        return (i + frac) / (il - 1)
    }
    fun getPointAt(u: Double, out: Vec3 = Vec3()) = getPoint(getUtoTmapping(u), out)
    fun getTangent(t: Double, out: Vec3 = Vec3()): Vec3 {
        val d = 0.0001
        val t1 = max(0.0, t - d); val t2 = min(1.0, t + d)
        return out.copy(getPoint(t2)).sub(getPoint(t1)).normalize()
    }
    fun getTangentAt(u: Double, out: Vec3 = Vec3()) = getTangent(getUtoTmapping(u), out)

    /** three.js TubeGeometry along this curve (open ends) */
    fun tube(tubular: Int, radius: Double, radial: Int): Geometry {
        val segs = tubular
        val tangents = ArrayList<Vec3>(); val normals = ArrayList<Vec3>(); val binormals = ArrayList<Vec3>()
        for (i in 0..segs) tangents.add(getTangentAt(i.toDouble() / segs))
        // initial normal: smallest tangent component
        var mn = Double.MAX_VALUE
        val tx = abs(tangents[0].x); val ty = abs(tangents[0].y); val tz = abs(tangents[0].z)
        val nrm = Vec3()
        if (tx <= mn) { mn = tx; nrm.set(1.0, 0.0, 0.0) }
        if (ty <= mn) { mn = ty; nrm.set(0.0, 1.0, 0.0) }
        if (tz <= mn) nrm.set(0.0, 0.0, 1.0)
        val vec = Vec3().crossVectors(tangents[0], nrm).normalize()
        normals.add(Vec3().crossVectors(tangents[0], vec)); binormals.add(Vec3().crossVectors(tangents[0], normals[0]))
        for (i in 1..segs) {
            val n = normals[i - 1].clone()
            val v = Vec3().crossVectors(tangents[i - 1], tangents[i])
            if (v.length() > 1e-9) {
                v.normalize()
                val th = acos(clamp(tangents[i - 1].dot(tangents[i]), -1.0, 1.0))
                n.applyAxisAngle(v, th)
            }
            normals.add(n); binormals.add(Vec3().crossVectors(tangents[i], n))
        }
        val pos = FloatList(); val nor = FloatList(); val uvs = FloatList(); val idx = IntList()
        val p = Vec3()
        for (i in 0..segs) {
            getPointAt(i.toDouble() / segs, p)
            val N = normals[i]; val B = binormals[i]
            for (j in 0..radial) {
                val v = j.toDouble() / radial * TAU; val s = sin(v); val c = -cos(v)
                val nx = c * N.x + s * B.x; val ny = c * N.y + s * B.y; val nz = c * N.z + s * B.z
                val l = sqrt(nx * nx + ny * ny + nz * nz)
                nor.add3(nx / l, ny / l, nz / l)
                pos.add3(p.x + radius * nx, p.y + radius * ny, p.z + radius * nz)
                uvs.add(i.toFloat() / segs); uvs.add(j.toFloat() / radial)
            }
        }
        for (j in 1..segs) for (i in 1..radial) {
            val a = (radial + 1) * (j - 1) + (i - 1); val b = (radial + 1) * j + (i - 1); val c = (radial + 1) * j + i; val d = (radial + 1) * (j - 1) + i
            idx.add3(a, b, d); idx.add3(b, c, d)
        }
        return Geometry.of(pos.toArray(), nor.toArray(), uvs.toArray(), idx.toArray())
    }
}
