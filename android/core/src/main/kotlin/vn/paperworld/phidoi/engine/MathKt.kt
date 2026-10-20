package vn.paperworld.phidoi.engine

import kotlin.math.*

/* ---------- scalar helpers (same semantics as the web game) ---------- */
const val TAU = Math.PI * 2

fun clamp(v: Double, a: Double, b: Double) = if (v < a) a else if (v > b) b else v
fun clampF(v: Float, a: Float, b: Float) = if (v < a) a else if (v > b) b else v
fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t
fun damp(a: Double, b: Double, l: Double, dt: Double) = a + (b - a) * (1 - exp(-l * dt))
fun smooth(a: Double, b: Double, x: Double): Double {
    val t = clamp((x - a) / (b - a), 0.0, 1.0)
    return t * t * (3 - 2 * t)
}
fun angDiff(a: Double, b: Double): Double {
    var d = (a - b) % TAU
    if (d > PI) d -= TAU
    if (d < -PI) d += TAU
    return d
}
fun hash2(a: Double, b: Double): Double {
    val s = sin(a * 127.1 + b * 311.7) * 43758.5453
    return s - floor(s)
}
fun fract(x: Double) = x - floor(x)

/* ---------- Vec3 ---------- */
class Vec3(@JvmField var x: Double = 0.0, @JvmField var y: Double = 0.0, @JvmField var z: Double = 0.0) {
    fun set(x: Double, y: Double, z: Double): Vec3 { this.x = x; this.y = y; this.z = z; return this }
    fun copy(v: Vec3): Vec3 { x = v.x; y = v.y; z = v.z; return this }
    fun clone() = Vec3(x, y, z)
    fun add(v: Vec3): Vec3 { x += v.x; y += v.y; z += v.z; return this }
    fun sub(v: Vec3): Vec3 { x -= v.x; y -= v.y; z -= v.z; return this }
    fun addScaledVector(v: Vec3, s: Double): Vec3 { x += v.x * s; y += v.y * s; z += v.z * s; return this }
    fun multiplyScalar(s: Double): Vec3 { x *= s; y *= s; z *= s; return this }
    fun divideScalar(s: Double): Vec3 = multiplyScalar(1.0 / s)
    fun negate(): Vec3 { x = -x; y = -y; z = -z; return this }
    fun dot(v: Vec3) = x * v.x + y * v.y + z * v.z
    fun lengthSq() = x * x + y * y + z * z
    fun length() = sqrt(lengthSq())
    fun normalize(): Vec3 { val l = length(); return if (l > 0) divideScalar(l) else this }
    fun distanceTo(v: Vec3) = sqrt(distanceToSquared(v))
    fun distanceToSquared(v: Vec3): Double { val dx = x - v.x; val dy = y - v.y; val dz = z - v.z; return dx * dx + dy * dy + dz * dz }
    fun setY(v: Double): Vec3 { y = v; return this }
    fun lerp(v: Vec3, t: Double): Vec3 { x += (v.x - x) * t; y += (v.y - y) * t; z += (v.z - z) * t; return this }
    fun lerpVectors(a: Vec3, b: Vec3, t: Double): Vec3 { x = a.x + (b.x - a.x) * t; y = a.y + (b.y - a.y) * t; z = a.z + (b.z - a.z) * t; return this }
    fun crossVectors(a: Vec3, b: Vec3): Vec3 {
        val ax = a.x; val ay = a.y; val az = a.z; val bx = b.x; val by = b.y; val bz = b.z
        x = ay * bz - az * by; y = az * bx - ax * bz; z = ax * by - ay * bx; return this
    }
    fun cross(v: Vec3) = crossVectors(this, v)
    fun applyMatrix4(m: Mat4): Vec3 {
        val e = m.e; val x0 = x; val y0 = y; val z0 = z
        val w = 1.0 / (e[3] * x0 + e[7] * y0 + e[11] * z0 + e[15])
        x = (e[0] * x0 + e[4] * y0 + e[8] * z0 + e[12]) * w
        y = (e[1] * x0 + e[5] * y0 + e[9] * z0 + e[13]) * w
        z = (e[2] * x0 + e[6] * y0 + e[10] * z0 + e[14]) * w
        return this
    }
    fun transformDirection(m: Mat4): Vec3 {
        val e = m.e; val x0 = x; val y0 = y; val z0 = z
        x = e[0] * x0 + e[4] * y0 + e[8] * z0; y = e[1] * x0 + e[5] * y0 + e[9] * z0; z = e[2] * x0 + e[6] * y0 + e[10] * z0
        return normalize()
    }
    fun applyMatrix3(m: Mat3): Vec3 {
        val e = m.e; val x0 = x; val y0 = y; val z0 = z
        x = e[0] * x0 + e[3] * y0 + e[6] * z0; y = e[1] * x0 + e[4] * y0 + e[7] * z0; z = e[2] * x0 + e[5] * y0 + e[8] * z0
        return this
    }
    fun applyQuaternion(q: Quat): Vec3 {
        val vx = x; val vy = y; val vz = z; val qx = q.x; val qy = q.y; val qz = q.z; val qw = q.w
        val tx = 2 * (qy * vz - qz * vy); val ty = 2 * (qz * vx - qx * vz); val tz = 2 * (qx * vy - qy * vx)
        x = vx + qw * tx + qy * tz - qz * ty; y = vy + qw * ty + qz * tx - qx * tz; z = vz + qw * tz + qx * ty - qy * tx
        return this
    }
    fun applyAxisAngle(axis: Vec3, angle: Double): Vec3 = applyQuaternion(TMPQ.setFromAxisAngle(axis, angle))
    fun setFromMatrixPosition(m: Mat4): Vec3 { x = m.e[12]; y = m.e[13]; z = m.e[14]; return this }
    fun project(cam: Camera): Vec3 = applyMatrix4(cam.matrixWorldInverse).applyMatrix4(cam.projectionMatrix)
    override fun toString() = "($x, $y, $z)"
    companion object { private val TMPQ = Quat() }
}

/* ---------- Quaternion ---------- */
class Quat(@JvmField var x: Double = 0.0, @JvmField var y: Double = 0.0, @JvmField var z: Double = 0.0, @JvmField var w: Double = 1.0) {
    fun set(x: Double, y: Double, z: Double, w: Double): Quat { this.x = x; this.y = y; this.z = z; this.w = w; return this }
    fun copy(q: Quat) = set(q.x, q.y, q.z, q.w)
    fun identity() = set(0.0, 0.0, 0.0, 1.0)
    fun setFromAxisAngle(a: Vec3, angle: Double): Quat {
        val h = angle / 2; val s = sin(h)
        return set(a.x * s, a.y * s, a.z * s, cos(h))
    }
    fun setFromEuler(e: Euler): Quat {
        val c1 = cos(e.x / 2); val c2 = cos(e.y / 2); val c3 = cos(e.z / 2)
        val s1 = sin(e.x / 2); val s2 = sin(e.y / 2); val s3 = sin(e.z / 2)
        when (e.order) {
            Euler.XYZ -> set(s1 * c2 * c3 + c1 * s2 * s3, c1 * s2 * c3 - s1 * c2 * s3, c1 * c2 * s3 + s1 * s2 * c3, c1 * c2 * c3 - s1 * s2 * s3)
            Euler.YXZ -> set(s1 * c2 * c3 + c1 * s2 * s3, c1 * s2 * c3 - s1 * c2 * s3, c1 * c2 * s3 - s1 * s2 * c3, c1 * c2 * c3 + s1 * s2 * s3)
            else -> error("euler order")
        }
        return this
    }
    fun setFromUnitVectors(from: Vec3, to: Vec3): Quat {
        var r = from.dot(to) + 1
        if (r < 1e-8) {
            r = 0.0
            if (abs(from.x) > abs(from.z)) set(-from.y, from.x, 0.0, r) else set(0.0, -from.z, from.y, r)
        } else {
            set(from.y * to.z - from.z * to.y, from.z * to.x - from.x * to.z, from.x * to.y - from.y * to.x, r)
        }
        return normalize()
    }
    fun normalize(): Quat {
        var l = sqrt(x * x + y * y + z * z + w * w)
        if (l == 0.0) return identity()
        l = 1 / l; x *= l; y *= l; z *= l; w *= l; return this
    }
    fun multiplyQuaternions(a: Quat, b: Quat): Quat {
        val qax = a.x; val qay = a.y; val qaz = a.z; val qaw = a.w; val qbx = b.x; val qby = b.y; val qbz = b.z; val qbw = b.w
        return set(qax * qbw + qaw * qbx + qay * qbz - qaz * qby, qay * qbw + qaw * qby + qaz * qbx - qax * qbz, qaz * qbw + qaw * qbz + qax * qby - qay * qbx, qaw * qbw - qax * qbx - qay * qby - qaz * qbz)
    }
    fun setFromRotationMatrix(m: Mat4): Quat {
        val te = m.e
        val m11 = te[0]; val m12 = te[4]; val m13 = te[8]; val m21 = te[1]; val m22 = te[5]; val m23 = te[9]; val m31 = te[2]; val m32 = te[6]; val m33 = te[10]
        val trace = m11 + m22 + m33
        if (trace > 0) { val s = 0.5 / sqrt(trace + 1.0); set((m32 - m23) * s, (m13 - m31) * s, (m21 - m12) * s, 0.25 / s) }
        else if (m11 > m22 && m11 > m33) { val s = 2.0 * sqrt(1.0 + m11 - m22 - m33); set(0.25 * s, (m12 + m21) / s, (m13 + m31) / s, (m32 - m23) / s) }
        else if (m22 > m33) { val s = 2.0 * sqrt(1.0 + m22 - m11 - m33); set((m12 + m21) / s, 0.25 * s, (m23 + m32) / s, (m13 - m31) / s) }
        else { val s = 2.0 * sqrt(1.0 + m33 - m11 - m22); set((m13 + m31) / s, (m23 + m32) / s, 0.25 * s, (m21 - m12) / s) }
        return this
    }
}

/* ---------- Euler (orders used by the game: XYZ default, YXZ) ---------- */
class Euler(@JvmField var x: Double = 0.0, @JvmField var y: Double = 0.0, @JvmField var z: Double = 0.0, @JvmField var order: Int = XYZ) {
    fun set(x: Double, y: Double, z: Double, order: Int = this.order): Euler { this.x = x; this.y = y; this.z = z; this.order = order; return this }
    companion object { const val XYZ = 0; const val YXZ = 1 }
}

/* ---------- Mat3 / Mat4 (column-major, like three.js) ---------- */
class Mat3 {
    @JvmField val e = DoubleArray(9).also { it[0] = 1.0; it[4] = 1.0; it[8] = 1.0 }
    fun getNormalMatrix(m: Mat4): Mat3 {
        val te = m.e
        val n11 = te[0]; val n21 = te[1]; val n31 = te[2]; val n12 = te[4]; val n22 = te[5]; val n32 = te[6]; val n13 = te[8]; val n23 = te[9]; val n33 = te[10]
        val t11 = n33 * n22 - n32 * n23; val t12 = n32 * n13 - n33 * n12; val t13 = n23 * n12 - n22 * n13
        val det = n11 * t11 + n21 * t12 + n31 * t13
        if (det == 0.0) { e.fill(0.0); return this }
        val di = 1 / det
        // inverse, then transpose
        e[0] = t11 * di; e[3] = (n31 * n23 - n33 * n21) * di; e[6] = (n32 * n21 - n31 * n22) * di
        e[1] = t12 * di; e[4] = (n33 * n11 - n31 * n13) * di; e[7] = (n31 * n12 - n32 * n11) * di
        e[2] = t13 * di; e[5] = (n21 * n13 - n23 * n11) * di; e[8] = (n22 * n11 - n21 * n12) * di
        return this
    }
}

class Mat4 {
    @JvmField val e = DoubleArray(16).also { it[0] = 1.0; it[5] = 1.0; it[10] = 1.0; it[15] = 1.0 }
    fun identity(): Mat4 { e.fill(0.0); e[0] = 1.0; e[5] = 1.0; e[10] = 1.0; e[15] = 1.0; return this }
    fun copy(m: Mat4): Mat4 { System.arraycopy(m.e, 0, e, 0, 16); return this }
    fun clone() = Mat4().copy(this)
    fun compose(p: Vec3, q: Quat, s: Vec3): Mat4 {
        val x = q.x; val y = q.y; val z = q.z; val w = q.w
        val x2 = x + x; val y2 = y + y; val z2 = z + z
        val xx = x * x2; val xy = x * y2; val xz = x * z2; val yy = y * y2; val yz = y * z2; val zz = z * z2
        val wx = w * x2; val wy = w * y2; val wz = w * z2
        val sx = s.x; val sy = s.y; val sz = s.z
        e[0] = (1 - (yy + zz)) * sx; e[1] = (xy + wz) * sx; e[2] = (xz - wy) * sx; e[3] = 0.0
        e[4] = (xy - wz) * sy; e[5] = (1 - (xx + zz)) * sy; e[6] = (yz + wx) * sy; e[7] = 0.0
        e[8] = (xz + wy) * sz; e[9] = (yz - wx) * sz; e[10] = (1 - (xx + yy)) * sz; e[11] = 0.0
        e[12] = p.x; e[13] = p.y; e[14] = p.z; e[15] = 1.0
        return this
    }
    fun multiplyMatrices(a: Mat4, b: Mat4): Mat4 {
        val ae = a.e; val be = b.e
        val a11 = ae[0]; val a12 = ae[4]; val a13 = ae[8]; val a14 = ae[12]
        val a21 = ae[1]; val a22 = ae[5]; val a23 = ae[9]; val a24 = ae[13]
        val a31 = ae[2]; val a32 = ae[6]; val a33 = ae[10]; val a34 = ae[14]
        val a41 = ae[3]; val a42 = ae[7]; val a43 = ae[11]; val a44 = ae[15]
        val b11 = be[0]; val b12 = be[4]; val b13 = be[8]; val b14 = be[12]
        val b21 = be[1]; val b22 = be[5]; val b23 = be[9]; val b24 = be[13]
        val b31 = be[2]; val b32 = be[6]; val b33 = be[10]; val b34 = be[14]
        val b41 = be[3]; val b42 = be[7]; val b43 = be[11]; val b44 = be[15]
        e[0] = a11 * b11 + a12 * b21 + a13 * b31 + a14 * b41; e[4] = a11 * b12 + a12 * b22 + a13 * b32 + a14 * b42
        e[8] = a11 * b13 + a12 * b23 + a13 * b33 + a14 * b43; e[12] = a11 * b14 + a12 * b24 + a13 * b34 + a14 * b44
        e[1] = a21 * b11 + a22 * b21 + a23 * b31 + a24 * b41; e[5] = a21 * b12 + a22 * b22 + a23 * b32 + a24 * b42
        e[9] = a21 * b13 + a22 * b23 + a23 * b33 + a24 * b43; e[13] = a21 * b14 + a22 * b24 + a23 * b34 + a24 * b44
        e[2] = a31 * b11 + a32 * b21 + a33 * b31 + a34 * b41; e[6] = a31 * b12 + a32 * b22 + a33 * b32 + a34 * b42
        e[10] = a31 * b13 + a32 * b23 + a33 * b33 + a34 * b43; e[14] = a31 * b14 + a32 * b24 + a33 * b34 + a34 * b44
        e[3] = a41 * b11 + a42 * b21 + a43 * b31 + a44 * b41; e[7] = a41 * b12 + a42 * b22 + a43 * b32 + a44 * b42
        e[11] = a41 * b13 + a42 * b23 + a43 * b33 + a44 * b43; e[15] = a41 * b14 + a42 * b24 + a43 * b34 + a44 * b44
        return this
    }
    fun invert(): Mat4 {
        val te = e
        val n11 = te[0]; val n21 = te[1]; val n31 = te[2]; val n41 = te[3]
        val n12 = te[4]; val n22 = te[5]; val n32 = te[6]; val n42 = te[7]
        val n13 = te[8]; val n23 = te[9]; val n33 = te[10]; val n43 = te[11]
        val n14 = te[12]; val n24 = te[13]; val n34 = te[14]; val n44 = te[15]
        val t11 = n23 * n34 * n42 - n24 * n33 * n42 + n24 * n32 * n43 - n22 * n34 * n43 - n23 * n32 * n44 + n22 * n33 * n44
        val t12 = n14 * n33 * n42 - n13 * n34 * n42 - n14 * n32 * n43 + n12 * n34 * n43 + n13 * n32 * n44 - n12 * n33 * n44
        val t13 = n13 * n24 * n42 - n14 * n23 * n42 + n14 * n22 * n43 - n12 * n24 * n43 - n13 * n22 * n44 + n12 * n23 * n44
        val t14 = n14 * n23 * n32 - n13 * n24 * n32 - n14 * n22 * n33 + n12 * n24 * n33 + n13 * n22 * n34 - n12 * n23 * n34
        val det = n11 * t11 + n21 * t12 + n31 * t13 + n41 * t14
        if (det == 0.0) { e.fill(0.0); return this }
        val di = 1 / det
        te[0] = t11 * di
        te[1] = (n24 * n33 * n41 - n23 * n34 * n41 - n24 * n31 * n43 + n21 * n34 * n43 + n23 * n31 * n44 - n21 * n33 * n44) * di
        te[2] = (n22 * n34 * n41 - n24 * n32 * n41 + n24 * n31 * n42 - n21 * n34 * n42 - n22 * n31 * n44 + n21 * n32 * n44) * di
        te[3] = (n23 * n32 * n41 - n22 * n33 * n41 - n23 * n31 * n42 + n21 * n33 * n42 + n22 * n31 * n43 - n21 * n32 * n43) * di
        te[4] = t12 * di
        te[5] = (n13 * n34 * n41 - n14 * n33 * n41 + n14 * n31 * n43 - n11 * n34 * n43 - n13 * n31 * n44 + n11 * n33 * n44) * di
        te[6] = (n14 * n32 * n41 - n12 * n34 * n41 - n14 * n31 * n42 + n11 * n34 * n42 + n12 * n31 * n44 - n11 * n32 * n44) * di
        te[7] = (n12 * n33 * n41 - n13 * n32 * n41 + n13 * n31 * n42 - n11 * n33 * n42 - n12 * n31 * n43 + n11 * n32 * n43) * di
        te[8] = t13 * di
        te[9] = (n14 * n23 * n41 - n13 * n24 * n41 - n14 * n21 * n43 + n11 * n24 * n43 + n13 * n21 * n44 - n11 * n23 * n44) * di
        te[10] = (n12 * n24 * n41 - n14 * n22 * n41 + n14 * n21 * n42 - n11 * n24 * n42 - n12 * n21 * n44 + n11 * n22 * n44) * di
        te[11] = (n13 * n22 * n41 - n12 * n23 * n41 - n13 * n21 * n42 + n11 * n23 * n42 + n12 * n21 * n43 - n11 * n22 * n43) * di
        te[12] = t14 * di
        te[13] = (n13 * n24 * n31 - n14 * n23 * n31 + n14 * n21 * n33 - n11 * n24 * n33 - n13 * n21 * n34 + n11 * n23 * n34) * di
        te[14] = (n14 * n22 * n31 - n12 * n24 * n31 - n14 * n21 * n32 + n11 * n24 * n32 + n12 * n21 * n34 - n11 * n22 * n34) * di
        te[15] = (n12 * n23 * n31 - n13 * n22 * n31 + n13 * n21 * n32 - n11 * n23 * n32 - n12 * n21 * n33 + n11 * n22 * n33) * di
        return this
    }
    /** rotation part only, like three's Matrix4.lookAt(eye, target, up) */
    fun lookAt(eye: Vec3, target: Vec3, up: Vec3): Mat4 {
        val z = TZ.copy(eye).sub(target)
        if (z.lengthSq() == 0.0) z.z = 1.0
        z.normalize()
        val x = TX.crossVectors(up, z)
        if (x.lengthSq() == 0.0) {
            if (abs(up.z) == 1.0) z.x += 0.0001 else z.z += 0.0001
            z.normalize(); x.crossVectors(up, z)
        }
        x.normalize()
        val y = TY.crossVectors(z, x)
        e[0] = x.x; e[4] = y.x; e[8] = z.x
        e[1] = x.y; e[5] = y.y; e[9] = z.y
        e[2] = x.z; e[6] = y.z; e[10] = z.z
        return this
    }
    fun makePerspective(fovDeg: Double, aspect: Double, near: Double, far: Double): Mat4 {
        val top = near * tan(fovDeg * PI / 360.0); val height = 2 * top; val width = aspect * height; val left = -0.5 * width
        val right = left + width; val bottom = top - height
        identity()
        e[0] = 2 * near / (right - left); e[5] = 2 * near / (top - bottom)
        e[8] = (right + left) / (right - left); e[9] = (top + bottom) / (top - bottom)
        e[10] = -(far + near) / (far - near); e[11] = -1.0
        e[14] = -2 * far * near / (far - near); e[15] = 0.0
        return this
    }
    fun makeOrthographic(left: Double, right: Double, top: Double, bottom: Double, near: Double, far: Double): Mat4 {
        identity()
        val w = 1.0 / (right - left); val h = 1.0 / (top - bottom); val p = 1.0 / (far - near)
        e[0] = 2 * w; e[5] = 2 * h; e[10] = -2 * p
        e[12] = -(right + left) * w; e[13] = -(top + bottom) * h; e[14] = -(far + near) * p
        return this
    }
    /** determinant of the upper 3×3 (negative = mirrored) */
    fun determinant(): Double = e[0] * (e[5] * e[10] - e[9] * e[6]) - e[4] * (e[1] * e[10] - e[9] * e[2]) + e[8] * (e[1] * e[6] - e[5] * e[2])
    fun decompose(p: Vec3, q: Quat, s: Vec3): Mat4 {
        var sx = kotlin.math.sqrt(e[0] * e[0] + e[1] * e[1] + e[2] * e[2]); val sy = kotlin.math.sqrt(e[4] * e[4] + e[5] * e[5] + e[6] * e[6]); val sz = kotlin.math.sqrt(e[8] * e[8] + e[9] * e[9] + e[10] * e[10])
        if (determinant() < 0) sx = -sx
        p.set(e[12], e[13], e[14])
        val m = clone(); m.e[12] = 0.0; m.e[13] = 0.0; m.e[14] = 0.0
        m.e[0] /= sx; m.e[1] /= sx; m.e[2] /= sx; m.e[4] /= sy; m.e[5] /= sy; m.e[6] /= sy; m.e[8] /= sz; m.e[9] /= sz; m.e[10] /= sz
        q.setFromRotationMatrix(m)
        s.set(sx, sy, sz)
        return this
    }
    fun maxScaleOnAxis(): Double {
        val sx = e[0] * e[0] + e[1] * e[1] + e[2] * e[2]; val sy = e[4] * e[4] + e[5] * e[5] + e[6] * e[6]; val sz = e[8] * e[8] + e[9] * e[9] + e[10] * e[10]
        return sqrt(max(sx, max(sy, sz)))
    }
    fun toFloat(out: FloatArray, off: Int = 0): FloatArray { for (i in 0 until 16) out[off + i] = e[i].toFloat(); return out }
    companion object { private val TX = Vec3(); private val TY = Vec3(); private val TZ = Vec3() }
}

/* ---------- colours: stored linear, set from sRGB hex like THREE.Color ---------- */
class Color(@JvmField var r: Double = 1.0, @JvmField var g: Double = 1.0, @JvmField var b: Double = 1.0) {
    fun set(hex: String): Color = setHex(parseHex(hex))
    fun setHex(h: Int): Color {
        r = srgbToLinear(((h shr 16) and 255) / 255.0); g = srgbToLinear(((h shr 8) and 255) / 255.0); b = srgbToLinear((h and 255) / 255.0)
        return this
    }
    fun setRGB(r: Double, g: Double, b: Double): Color { this.r = r; this.g = g; this.b = b; return this }
    /** HSL in sRGB space, converted to linear (three.js Color.setHSL) */
    fun setHSL(h0: Double, s0: Double, l0: Double): Color {
        val h = ((h0 % 1) + 1) % 1; val s = clamp(s0, 0.0, 1.0); val l = clamp(l0, 0.0, 1.0)
        if (s == 0.0) { r = l; g = l; b = l } else {
            val p = if (l <= 0.5) l * (1 + s) else l + s - l * s
            val q = 2 * l - p
            r = hue2rgb(q, p, h + 1.0 / 3); g = hue2rgb(q, p, h); b = hue2rgb(q, p, h - 1.0 / 3)
        }
        r = srgbToLinear(r); g = srgbToLinear(g); b = srgbToLinear(b)
        return this
    }
    fun copy(c: Color): Color { r = c.r; g = c.g; b = c.b; return this }
    fun clone() = Color(r, g, b)
    fun lerp(c: Color, t: Double): Color { r += (c.r - r) * t; g += (c.g - g) * t; b += (c.b - b) * t; return this }
    fun multiplyScalar(s: Double): Color { r *= s; g *= s; b *= s; return this }
    companion object {
        fun srgbToLinear(c: Double) = if (c < 0.04045) c * 0.0773993808 else (c * 0.9478672986 + 0.0521327014).pow(2.4)
        fun parseHex(hex: String): Int {
            val s = hex.removePrefix("#")
            return if (s.length == 3) { val r = s[0].digitToInt(16); val g = s[1].digitToInt(16); val b = s[2].digitToInt(16); (r * 17 shl 16) or (g * 17 shl 8) or (b * 17) } else s.toInt(16)
        }
        private fun hue2rgb(p: Double, q: Double, t0: Double): Double {
            var t = t0
            if (t < 0) t += 1.0
            if (t > 1) t -= 1.0
            if (t < 1.0 / 6) return p + (q - p) * 6 * t
            if (t < 1.0 / 2) return q
            if (t < 2.0 / 3) return p + (q - p) * 6 * (2.0 / 3 - t)
            return p
        }
        fun of(hex: String) = Color().set(hex)
        /** raw sRGB channels of a hex colour, 0..255 */
        fun rgb255(hex: String): IntArray { val h = parseHex(hex); return intArrayOf((h shr 16) and 255, (h shr 8) and 255, h and 255) }
    }
}

/* ---------- bounds + frustum ---------- */
class Sphere(@JvmField val center: Vec3 = Vec3(), @JvmField var radius: Double = -1.0) {
    fun copy(s: Sphere): Sphere { center.copy(s.center); radius = s.radius; return this }
    fun applyMatrix4(m: Mat4): Sphere { center.applyMatrix4(m); radius *= m.maxScaleOnAxis(); return this }
}

class Frustum {
    private val p = Array(6) { DoubleArray(4) }
    fun setFromProjectionMatrix(m: Mat4): Frustum {
        val me = m.e
        val me0 = me[0]; val me1 = me[1]; val me2 = me[2]; val me3 = me[3]; val me4 = me[4]; val me5 = me[5]; val me6 = me[6]; val me7 = me[7]
        val me8 = me[8]; val me9 = me[9]; val me10 = me[10]; val me11 = me[11]; val me12 = me[12]; val me13 = me[13]; val me14 = me[14]; val me15 = me[15]
        set(0, me3 - me0, me7 - me4, me11 - me8, me15 - me12)
        set(1, me3 + me0, me7 + me4, me11 + me8, me15 + me12)
        set(2, me3 + me1, me7 + me5, me11 + me9, me15 + me13)
        set(3, me3 - me1, me7 - me5, me11 - me9, me15 - me13)
        set(4, me3 - me2, me7 - me6, me11 - me10, me15 - me14)
        set(5, me3 + me2, me7 + me6, me11 + me10, me15 + me14)
        return this
    }
    private fun set(i: Int, a: Double, b: Double, c: Double, d: Double) {
        val l = sqrt(a * a + b * b + c * c)
        p[i][0] = a / l; p[i][1] = b / l; p[i][2] = c / l; p[i][3] = d / l
    }
    fun intersectsSphere(c: Vec3, r: Double): Boolean {
        for (q in p) if (q[0] * c.x + q[1] * c.y + q[2] * c.z + q[3] < -r) return false
        return true
    }
}
