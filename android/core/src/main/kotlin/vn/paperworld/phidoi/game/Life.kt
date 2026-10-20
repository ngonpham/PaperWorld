package vn.paperworld.phidoi.game

import vn.paperworld.phidoi.engine.*
import vn.paperworld.phidoi.world.*
import kotlin.math.*

/* =========================================================
   PARTICLES — paper bursts, sparks, splashes
   ========================================================= */
class Particles(val N: Int, size: Double, additive: Boolean, map: Texture?) {
    val geo = Geometry()
    val pos = FloatArray(N * 3)
    val col = FloatArray(N * 3)
    val vel = Array(N) { Vec3() }
    val life = FloatArray(N); val max = FloatArray(N); val grav = FloatArray(N); val drag = FloatArray(N)
    var i = 0
    val mat = PointsMaterial(size).also {
        it.depthWrite = false; it.transparent = true; it.map = map
        it.blending = if (additive) Blend.ADDITIVE else Blend.NORMAL; it.fog = !additive
    }
    val m: Points
    init {
        for (k in 0 until N) pos[k * 3 + 1] = -9999f
        geo.position = pos; geo.color = col; geo.dynamic = true
        m = Points(geo, mat)
        m.frustumCulled = false
    }
}

fun makePuffTex(w: World) = w.canvasTex(64, 64, false) { g, W, _ ->
    g.setFillRadial(W / 2f, W / 2f, W / 2f, floatArrayOf(0f, 0.55f, 0.8f, 1f), intArrayOf(rgba(255, 255, 255, 1.0), rgba(255, 255, 255, 0.85), rgba(255, 255, 255, 0.3), rgba(255, 255, 255, 0.0)))
    g.fillRect(0f, 0f, W.toFloat(), W.toFloat())
}

private val _pc = Color()
fun Game.emit(P: Particles, p: Vec3, v: Vec3, col: String, life: Double, grav: Double = -6.0, drag: Double = 1.6) {
    val i = P.i++ % P.N
    P.pos[i * 3] = p.x.toFloat(); P.pos[i * 3 + 1] = p.y.toFloat(); P.pos[i * 3 + 2] = p.z.toFloat()
    P.vel[i].copy(v)
    _pc.copy(linCol(col))
    P.col[i * 3] = _pc.r.toFloat(); P.col[i * 3 + 1] = _pc.g.toFloat(); P.col[i * 3 + 2] = _pc.b.toFloat()
    P.life[i] = life.toFloat(); P.max[i] = life.toFloat()
    P.grav[i] = grav.toFloat(); P.drag[i] = drag.toFloat()
}
private val _bv = Vec3()
/** col: a colour, or a palette to pick from (a palette draws one more random number, as in the web build) */
private fun Game.burstImpl(p: Vec3, one: String?, pal: List<String>?, n: Int, spd: Double, up: Double, life: Double) {
    for (k in 0 until n) {
        _bv.set(RR(-1.0, 1.0), RR(-1.0, 1.0), RR(-1.0, 1.0)).normalize().multiplyScalar(RR(spd * 0.4, spd)).add(_v1.set(0.0, up, 0.0))
        emit(PB, p, _bv, if (rand() < 0.3) "#ffffff" else one ?: pick(pal!!), RR(life * 0.6, life))
    }
}
fun Game.burst(p: Vec3, col: List<String>, n: Int = 24, spd: Double = 9.0, up: Double = 4.0, life: Double = 1.1) = burstImpl(p, null, col, n, spd, up, life)
fun Game.burst(p: Vec3, col: String, n: Int = 24, spd: Double = 9.0, up: Double = 4.0, life: Double = 1.1) = burstImpl(p, col, null, n, spd, up, life)

fun stepParticles(P: Particles, dt: Double, fade: Boolean) {
    val d = dt.toFloat()
    for (i in 0 until P.N) {
        if (P.life[i] <= 0) continue
        P.life[i] -= d
        val v = P.vel[i]
        v.multiplyScalar(1 - dt * P.drag[i])
        v.y += P.grav[i] * dt
        P.pos[i * 3] += (v.x * dt).toFloat(); P.pos[i * 3 + 1] += (v.y * dt).toFloat(); P.pos[i * 3 + 2] += (v.z * dt).toFloat()
        if (fade) { val k = max(0f, P.life[i] / P.max[i]); P.col[i * 3] *= 0.985f + 0.015f * k; P.col[i * 3 + 1] *= 0.97f + 0.03f * k; P.col[i * 3 + 2] *= 0.96f + 0.04f * k }
        if (P.life[i] <= 0) P.pos[i * 3 + 1] = -9999f
    }
    P.geo.posVersion++
    P.geo.colVersion++
}

/* =========================================================
   WIND · THERMALS · CLOUDS
   ========================================================= */
class Wind { val v = Vec3(); val dir = Vec3(); var speed = 0.0; var gust = 0.0 }
fun Game.updateWind(t: Double) {
    val h = ((ENV.hour % 24) + 24) % 24
    val sea = smooth(8.0, 11.0, h) * smooth(19.0, 16.5, h) // afternoon sea breeze from the ESE
    val a = lerp(atan2(-0.1, 1.0), atan2(-0.383, -0.924), sea) // land breeze (to the east) → sea breeze (to the WNW)
    WIND.speed = lerp(2.2, 5.6, sea) * (1 + ENV.rain * 0.5) + 0.8 * sn(t * 0.05, 1.3)
    WIND.dir.set(cos(a), 0.0, sin(a))
    WIND.v.copy(WIND.dir).multiplyScalar(WIND.speed)
}
class Thermal(val x: Double, val z: Double, val r: Double, val w: Double, val top: Double, val ph: Double) { var cloud: Cloud? = null }
class Cloud(val g: Group, val x: Double, val y: Double, val z: Double, val s: Double, val thermal: Thermal?) { var haivan = false }

fun Game.makeCloud(x: Double, y: Double, z: Double, s: Double, thermal: Thermal? = null): Cloud {
    val g = Group()
    val parts = ArrayList<Geo.Part>()
    g.position.set(x, y, z)
    val n = 6 + floor(rand() * 5).toInt()
    for (k in 0 until n) {
        val a = rand() * 6.28; val r = RR(0.2, 1.0) * s; val bx = cos(a) * r * 1.3; val bz = sin(a) * r; val bs = RR(0.45, 0.85) * s
        val by = RR(-0.1, 0.5) * s * (1 - r / (s * 1.4))
        parts.add(P(GEO.ico1, bx, by, bz, bs * 1.1, bs * 0.8, bs, rand(), rand(), 0.0))
    }
    parts.add(P(GEO.ico1, 0.0, -s * 0.12, 0.0, s * 1.6, s * 0.3, s * 1.3))
    val m = Mesh(mergeGeo(parts), cloudMat)
    outlined(m, 0.12)
    g.add(m)
    scene.add(g)
    val c = Cloud(g, x, y, z, s, thermal)
    CLOUDS.add(c)
    return c
}
fun Game.setupThermals() {
    val list = arrayOf(
        // [x, z, radius, strength m/s, cloudbase]
        doubleArrayOf(-600.0, -900.0, 90.0, 4.5, 330.0), doubleArrayOf(-300.0, -250.0, 80.0, 4.0, 300.0), doubleArrayOf(150.0, -1500.0, 70.0, 3.6, 300.0), doubleArrayOf(-1250.0, -1300.0, 95.0, 4.8, 360.0), doubleArrayOf(-1450.0, 200.0, 90.0, 4.2, 340.0),
        doubleArrayOf(900.0, -2520.0, 85.0, 5.2, 400.0), doubleArrayOf(1520.0, -2560.0, 80.0, 4.8, 380.0), doubleArrayOf(640.0, -2440.0, 70.0, 4.2, 360.0), doubleArrayOf(1960.0, -2680.0, 70.0, 4.0, 340.0),
        doubleArrayOf(760.0, 820.0, 70.0, 4.4, 330.0), doubleArrayOf(560.0, 1020.0, 60.0, 3.6, 300.0),
        doubleArrayOf(-800.0, 1800.0, 100.0, 4.6, 360.0), doubleArrayOf(-200.0, 2400.0, 90.0, 4.2, 340.0), doubleArrayOf(300.0, 3000.0, 85.0, 4.0, 330.0), doubleArrayOf(-1200.0, 3200.0, 100.0, 4.8, 380.0), doubleArrayOf(-600.0, 4300.0, 90.0, 4.4, 350.0), doubleArrayOf(150.0, 1500.0, 80.0, 3.8, 320.0),
        doubleArrayOf(650.0, 3330.0, 70.0, 3.8, 320.0),
        doubleArrayOf(-2600.0, -2600.0, 120.0, 6.0, 520.0), doubleArrayOf(-3000.0, -1200.0, 120.0, 5.6, 520.0), doubleArrayOf(-3300.0, 300.0, 130.0, 6.2, 560.0), doubleArrayOf(-2800.0, 1800.0, 120.0, 5.4, 500.0), doubleArrayOf(-3200.0, 3200.0, 120.0, 5.6, 520.0), doubleArrayOf(-2400.0, 4300.0, 110.0, 5.0, 480.0),
        doubleArrayOf(3800.0, 2620.0, 90.0, 4.2, 360.0),
    )
    for (e in list) {
        val T = Thermal(e[0], e[1], e[2], e[3], e[4] * (0.9 + rand() * 0.2), rand() * 6)
        THERMALS.add(T)
        T.cloud = makeCloud(e[0], T.top + 25, e[1], RR(26.0, 38.0), T)
    }
    // fair-weather clouds without thermals + the Hải Vân "sea clouds"
    for (k in 0 until 26) makeCloud(RR(-4500.0, 4500.0), RR(380.0, 620.0), RR(-4200.0, 5000.0), RR(24.0, 46.0))
    for (k in 0 until 9) { val c = makeCloud(-2500 + RR(-500.0, 500.0), RR(200.0, 300.0), -3400 + RR(-300.0, 300.0), RR(30.0, 48.0)); c.haivan = true }
}
/** vertical air at a point: thermals + ridge lift + cloud suck - sink over the sea */
class Lift { var therm = 0.0; var ridge = 0.0; var total = 0.0; var inCloud = 0.0; var near: Thermal? = null; var nearD = 1e9 }
fun Game.airLift(x: Double, y: Double, z: Double, t: Double): Double {
    val h = ((ENV.hour % 24) + 24) % 24
    val sunK = (0.2 + 0.8 * smooth(7.0, 11.0, h) * smooth(18.2, 15.0, h)) * (1 - ENV.rain * 0.7)
    var th = 0.0; var near: Thermal? = null; var nd = 1e9
    for (T in THERMALS) {
        // thermals lean downwind as they rise
        val cx = T.x + WIND.v.x * (y / 4.5) * 0.6; val cz = T.z + WIND.v.z * (y / 4.5) * 0.6
        val d = hypot(x - cx, z - cz)
        if (d < nd) { nd = d; near = T }
        if (d > T.r * 3) continue
        val pulse = 0.8 + 0.2 * sin(t * 0.15 + T.ph)
        val core = exp(-((d / T.r).pow(2))); val ring = exp(-(((d - T.r * 1.8) / (T.r * 0.5)).pow(2)))
        val top = if (y < T.top) 1.0 else exp(-(y - T.top) / 25)
        th += (T.w * core * pulse - 0.9 * ring) * sunK * top * smooth(-5.0, 25.0, y)
    }
    // ridge lift: wind blowing up a slope (sampled a little upwind too)
    var ridge = 0.0
    val gh = gridH(x, z)
    val agl = y - max(gh, 0.0)
    if (agl < 320 && gh > 2) {
        for (off in doubleArrayOf(0.0, 35.0)) {
            val sx = x - WIND.dir.x * off; val sz = z - WIND.dir.z * off
            val gx = (gridH(sx + 15, sz) - gridH(sx - 15, sz)) / 30; val gz = (gridH(sx, sz + 15) - gridH(sx, sz - 15)) / 30
            val up = gx * WIND.dir.x + gz * WIND.dir.z
            val r = WIND.speed * (if (up > 0) min(up, 0.9) * 1.1 else max(up, -0.9) * 0.5)
            if (abs(r) > abs(ridge)) ridge = r
        }
        ridge = clamp(ridge * smooth(320.0, 20.0, agl) * clamp(gh / 60, 0.3, 1.2), -2.5, 4.2)
    }
    // sink over open water, cloud suck under the cumulus
    var extra = if (gh < -1) -0.25 else 0.0; var inC = 0.0
    for (c in CLOUDS) {
        val dx = x - c.g.position.x; val dz = z - c.g.position.z
        if (abs(dx) > c.s * 2.4 || abs(dz) > c.s * 2.4) continue
        val dy = y - c.g.position.y
        val e = (dx * dx) / (c.s * c.s * 2.6) + (dz * dz) / (c.s * c.s * 1.8) + (dy * dy) / (c.s * c.s * 0.45)
        if (e < 1) inC = max(inC, 1 - e)
        if (dy < 0 && dy > -90 && e < 3) extra += 0.6 * (1 - e / 3)
    }
    LIFT.therm = th; LIFT.ridge = ridge; LIFT.inCloud = inC; LIFT.near = near; LIFT.nearD = nd
    LIFT.total = th + ridge + extra
    return LIFT.total
}
/** swirling paper scraps that show where the thermals are */
class ThermalFx(g: Game) {
    val N = 360
    val geo = Geometry(); val pos = FloatArray(N * 3); val col = FloatArray(N * 3)
    val mat = PointsMaterial(1.1).also { it.transparent = true; it.opacity = 0.85; it.depthWrite = false }
    val m: Points
    val seeds: Array<DoubleArray>
    init {
        val cols = listOf("#ffffff", "#fff2b0", "#ffd9e6", "#d9fff4")
        for (i in 0 until N) { val c = Color.of(g.pick(cols)); col[i * 3] = c.r.toFloat(); col[i * 3 + 1] = c.g.toFloat(); col[i * 3 + 2] = c.b.toFloat() }
        geo.position = pos; geo.color = col; geo.dynamic = true
        m = Points(geo, mat); m.frustumCulled = false
        g.scene.add(m)
        seeds = Array(N) { doubleArrayOf(g.rand(), g.rand(), g.rand()) }
    }
}
fun Game.updateThermalFx(cp: Vec3, t: Double) {
    val near = THERMALS.map { Pair(hypot(it.x - cp.x, it.z - cp.z), it) }.sortedBy { it.first }.take(4)
    val per = TP.N / 4
    for (i in 0 until TP.N) {
        val (d, T) = near[i / per]; val s = TP.seeds[i]; val a = s[0]; val b = s[1]; val c = s[2]
        if (d > 1400) { TP.pos[i * 3 + 1] = -9999f; continue }
        val y = ((t * (2 + c * 3) + b * T.top) % T.top) + 4; val ang = t * (0.3 + a * 0.4) + a * 40; val r = T.r * (0.25 + 0.75 * sqrt(c)) * (0.6 + 0.4 * (y / T.top))
        TP.pos[i * 3] = (T.x + cos(ang) * r + WIND.v.x * (y / 4.5) * 0.6).toFloat()
        TP.pos[i * 3 + 1] = max(y, gridH(T.x, T.z) + 3 + y * 0.1).toFloat()
        TP.pos[i * 3 + 2] = (T.z + sin(ang) * r + WIND.v.z * (y / 4.5) * 0.6).toFloat()
    }
    TP.geo.posVersion++
    TP.mat.opacity = 0.85 * (1 - env.glow * 0.7)
}
/** wind streaks around the camera */
class WindFx(g: Game) {
    val N = 160
    val geo = Geometry(); val pos = FloatArray(N * 6)
    val mat = LineMaterial("#ffffff").also { it.transparent = true; it.opacity = 0.35; it.depthWrite = false }
    val m: Lines
    val off: Array<DoubleArray>
    init {
        geo.position = pos; geo.dynamic = true
        m = Lines(geo, mat, false); m.frustumCulled = false
        g.scene.add(m)
        off = Array(N) { doubleArrayOf(g.RR(-90.0, 90.0), g.RR(-40.0, 40.0), g.RR(-90.0, 90.0)) }
    }
}
fun Game.updateWindFx(cp: Vec3, t: Double, dt: Double) {
    val k = WIND.speed * 0.9
    fun wrap(v: Double, c: Double, R: Double) = ((((v - c) % (2 * R)) + 3 * R) % (2 * R)) - R + c
    for (i in 0 until WS.N) {
        val o = WS.off[i]
        o[0] += WIND.v.x * dt * 1.6; o[2] += WIND.v.z * dt * 1.6; o[1] += sin(t + i) * dt * 0.5
        val x = wrap(o[0], cp.x, 90.0); val y = wrap(o[1], cp.y, 40.0); val z = wrap(o[2], cp.z, 90.0)
        val b = i * 6
        WS.pos[b] = x.toFloat(); WS.pos[b + 1] = y.toFloat(); WS.pos[b + 2] = z.toFloat()
        WS.pos[b + 3] = (x + WIND.dir.x * k).toFloat(); WS.pos[b + 4] = y.toFloat(); WS.pos[b + 5] = (z + WIND.dir.z * k).toFloat()
    }
    WS.geo.posVersion++
    WS.mat.opacity = 0.22 * (1 - env.glow * 0.6) * smooth(1.5, 5.0, WIND.speed)
}
