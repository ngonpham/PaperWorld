package vn.paperworld.phidoi.game

import vn.paperworld.phidoi.engine.*
import vn.paperworld.phidoi.world.*
import kotlin.math.*

/* =========================================================
   MOVING THINGS
   ========================================================= */
/** cars on the coastal road and over the bridges */
class Lane(val pts: List<DoubleArray>, val off: Double, val dir: Double, val bridge: Bridge? = null) { var len = 0.0; var segs = DoubleArray(0) }
class Car(val L: Lane, var s: Double, val v: Double, val col: String)
class Cars { var im: InstancedMesh? = null; var ol: InstancedMesh? = null; val list = ArrayList<Car>(); var lights: Points? = null; var lightsGeo: Geometry? = null }

fun Game.setupCars() {
    val lanes = ArrayList<Lane>()
    val road = ROADS[ROADS.size - 1]
    lanes.add(Lane(road, 3.2, 1.0)); lanes.add(Lane(road, -3.2, -1.0))
    for (B in BRIDGES) { val x0 = B.x0 - B.half - 110; val x1 = B.x0 + B.half + 110; for (s in intArrayOf(-1, 1)) lanes.add(Lane(listOf(World.pt(x0, B.z), World.pt(x1, B.z)), s * 3.0, s.toDouble(), B)) }
    val cols = listOf("#e8562a", "#2a9fd6", "#f2c230", "#ffffff", "#1f9a8a", "#3a3848", "#c0392b", "#dfe6ea")
    for (L in lanes) {
        val segs = DoubleArray(L.pts.size - 1)
        var len = 0.0
        for (i in 0 until L.pts.size - 1) { val l = hypot(L.pts[i + 1][0] - L.pts[i][0], L.pts[i + 1][1] - L.pts[i][1]); segs[i] = len; len += l }
        L.len = len; L.segs = segs
        val n = floor(len / (if (L.bridge != null) 34 else 90) * Q.dens).toInt() + 1
        for (k in 0 until n) CARS.list.add(Car(L, rand() * len, RR(9.0, 15.0), pick(cols)))
    }
    val data = InstanceData(CARS.list.size, true)
    CARS.list.forEachIndexed { i, c -> data.setColorAt(i, linCol(c.col)) }
    val im = InstancedMesh(GEOC.car, white, data)
    im.castShadow = true
    scene.add(im)
    val ol = InstancedMesh(GEOC.car, outlineMat(), data)
    scene.add(ol)
    im.frustumCulled = false; ol.frustumCulled = false
    CARS.im = im; CARS.ol = ol
    // head- & tail-lights
    val N = CARS.list.size * 2; val pos = FloatArray(N * 3); val col = FloatArray(N * 3)
    for (i in CARS.list.indices) { val o = i * 6; col[o] = 1f; col[o + 1] = 0.95f; col[o + 2] = 0.75f; col[o + 3] = 1f; col[o + 4] = 0.15f; col[o + 5] = 0.1f }
    val geo = Geometry(); geo.position = pos; geo.color = col; geo.dynamic = true
    val lm = PointsMaterial(1.6).also { it.transparent = true; it.blending = Blend.ADDITIVE; it.depthWrite = false }
    val lights = Points(geo, lm); lights.frustumCulled = false
    scene.add(lights)
    CARS.lights = lights; CARS.lightsGeo = geo
}
private val _cp = LinePt()
private fun lanePoint(L: Lane, s: Double, out: LinePt): LinePt {
    var i = L.segs.size - 1
    while (i > 0 && L.segs[i] > s) i--
    val a = L.pts[i]; val b = L.pts[i + 1]; val l = hypot(b[0] - a[0], b[1] - a[1]); val t = (s - L.segs[i]) / l
    out.tx = (b[0] - a[0]) / l; out.tz = (b[1] - a[1]) / l
    out.x = a[0] + (b[0] - a[0]) * t - out.tz * L.off; out.z = a[1] + (b[1] - a[1]) * t + out.tx * L.off
    return out
}
fun Game.updateCars(dt: Double, cp: Vec3) {
    val im = CARS.im ?: return
    val lp = CARS.lightsGeo!!.position
    for (i in CARS.list.indices) {
        val c = CARS.list[i]; val L = c.L
        c.s = (c.s + c.v * dt * L.dir + L.len) % L.len
        lanePoint(L, c.s, _cp)
        val y: Double
        val B = L.bridge
        if (B != null) {
            val ax = abs(_cp.x - B.x0)
            y = if (ax < B.half) B.deckY else if (ax < B.half + 110) lerp(B.deckY, max(gridH(_cp.x, _cp.z), 1.0) + 0.4, min(1.0, (ax - B.half) / (if (B.half > 80) 95 else 50))) else gridH(_cp.x, _cp.z) + 0.3
        } else y = max(gridH(_cp.x, _cp.z), 0.5) + 0.3
        val ang = atan2(-_cp.tx * L.dir, -_cp.tz * L.dir)
        _m.compose(_p.set(_cp.x, y, _cp.z), _q.setFromAxisAngle(YUP, ang), _s.set(1.0, 1.0, 1.0))
        im.data.setMatrixAt(i, _m)
        val fx = -sin(ang); val fz = -cos(ang); val o = i * 6
        lp[o] = (_cp.x + fx * 1.9).toFloat(); lp[o + 1] = (y + 0.6).toFloat(); lp[o + 2] = (_cp.z + fz * 1.9).toFloat()
        lp[o + 3] = (_cp.x - fx * 1.9).toFloat(); lp[o + 4] = (y + 0.6).toFloat(); lp[o + 5] = (_cp.z - fz * 1.9).toFloat()
    }
    im.data.version++
    CARS.lightsGeo!!.posVersion++
    CARS.lights!!.visible = env.glow > 0.25
}

/** paper airliners using the airport */
class Jet(val g: Group, var t: Double, val x: Double, val z0: Double, val z1: Double) { val pos = Vec3() }
fun Game.makeJet(col: String): Group {
    val g = Group()
    mk(Geo.cylinder(1.4, 1.1, 22.0, 10).rotateX(PI / 2).addONormal(), "#fbfaf6", 0.0, 0.0, 0.0, g, 0.06)
    mk(GEO.sph, "#fbfaf6", 0.0, 0.0, -11.0, g, 0.05).scale.set(1.4, 1.4, 2.4)
    mk(geoBox(26.0, 0.4, 4.2), "#e8e6e0", 0.0, -0.4, 0.6, g, 0.05)
    mk(geoBox(9.0, 0.3, 2.2), "#e8e6e0", 0.0, 0.4, 9.6, g, 0.04)
    val fin = mk(geoBox(0.4, 6.0, 3.6), col, 0.0, 3.0, 9.6, g, 0.04)
    fin.rotation.x = -0.35
    for (s in intArrayOf(-1, 1)) {
        val eng = mk(GEO.cyl16, "#cfcdc6", s * 5.5, -1.4, -0.4, g, 0.03)
        eng.scale.set(0.8, 3.0, 0.8)
        eng.rotation.x = PI / 2
    }
    box(0.2, 0.9, 20.0, col, 1.42, 0.2, -0.5, g, 0.0)
    box(0.2, 0.9, 20.0, col, -1.42, 0.2, -0.5, g, 0.0)
    scene.add(g)
    return g
}
fun Game.setupJets() {
    val x = AIRPORT.x; val z0 = AIRPORT.z0; val z1 = AIRPORT.z1
    for (k in 0 until 2) {
        val g = makeJet(if (k > 0) "#2a9fd6" else "#e8562a")
        JETS.add(Jet(g, k * 55.0, x, z0, z1))
    }
    // parked jets at the terminal
    for (k in 0 until 4) {
        val g = makeJet(pick(listOf("#2a9fd6", "#e8562a", "#1f9a8a", "#f2c230")))
        g.position.set(x + 62, 4.6, -200.0 + k * 32)
        g.rotation.y = PI / 2
        colCyl(x + 62, -200.0 + k * 32, 12.0, 0.0, 9.0, "jet")
    }
}
/** one cycle: take off to the north over the bay, later come back and land from the north */
private const val CYCLE = 110.0
fun Game.updateJets(dt: Double, t: Double) {
    for (J in JETS) {
        J.t = (J.t + dt) % CYCLE
        val u = J.t; val g = J.g; val gy0 = 4.4; val start = J.z1 - 20
        val px = J.x; var py = -100.0; var pz = 0.0; var pitch = 0.0; var yaw = 0.0; var vis = true
        if (u < 45) {
            val s = if (u < 17.5) 2 * u * u else 612.5 + 70 * (u - 17.5) // accelerate to 70 m/s, then cruise
            pz = start - s
            val air = max(0.0, s - 700)
            py = gy0 + air * 0.17
            pitch = if (air > 0) 0.17 else 0.0
            if (s > 3600) vis = false
        } else if (u >= 55 && u < 105) {
            val D = 3300 - 66 * (u - 55) // distance left to the touchdown point
            val touch = J.z0 + 60
            yaw = PI
            if (D > 0) { pz = touch - D; py = gy0 + D * 0.065; pitch = -0.03 }
            else { val r = min(-D, 1300.0); pz = touch + r * (1 - r / 2600); py = gy0; if (r >= 1300) vis = false }
        } else vis = false
        g.visible = vis
        if (!vis) { J.pos.set(0.0, -999.0, 0.0); continue }
        g.position.set(px, py, pz)
        g.rotation.set(pitch, yaw, 0.0, Euler.YXZ)
        J.pos.copy(g.position)
    }
}

/** seagulls: V-shaped birds that flap by flipping their scale */
class Bird(val c: Vec3, val r: Double, val sp: Double, val ph: Double, val ph2: Double, val eagle: Boolean = false, val T: Thermal? = null, var y: Double = 0.0) { var flee = 0.0; val pos = Vec3(); val off = Vec3() }
class Birds { var im: InstancedMesh? = null; val list = ArrayList<Bird>() }
private val birdGeo: Geometry by lazy { Geo.tris(floatArrayOf(0f, 0f, -0.5f, -2.2f, 0.6f, 0.2f, 0f, 0f, 0.5f, 0f, 0f, -0.5f, 0f, 0f, 0.5f, 2.2f, 0.6f, 0.2f)) }
fun Game.setupBirds() {
    val cx = Geog::coastX
    val flocks = arrayOf(doubleArrayOf(cx(-1500.0) + 60, -1500.0), doubleArrayOf(cx(-400.0) + 80, -400.0), doubleArrayOf(cx(900.0) + 60, 900.0), doubleArrayOf(cx(2400.0) + 60, 2400.0), doubleArrayOf(cx(3500.0) + 40, 3500.0), doubleArrayOf(2300.0, -2600.0), doubleArrayOf(3600.0, 2800.0), doubleArrayOf(100.0, -2100.0))
    for (f in flocks)
        for (k in 0 until 14) {
            val c = Vec3(f[0], RR(14.0, 40.0), f[1]); val r = RR(20.0, 60.0); val sp = RR(0.25, 0.45) * (if (rand() < 0.5) 1 else -1); val ph = rand() * 6; val ph2 = rand() * 6
            BIRDS.list.add(Bird(c, r, sp, ph, ph2))
        }
    // eagles circling inside the thermals
    for (T in THERMALS) for (k in 0 until 2) {
        val r = T.r * RR(0.4, 0.8); val sp = RR(0.18, 0.28); val ph = rand() * 6; val ph2 = rand() * 6; val y = RR(40.0, T.top * 0.8)
        BIRDS.list.add(Bird(Vec3(T.x, 0.0, T.z), r, sp, ph, ph2, true, T, y))
    }
    val data = InstanceData(BIRDS.list.size, true)
    BIRDS.list.forEachIndexed { i, b -> data.setColorAt(i, linCol(if (b.eagle) "#5a4030" else "#f7f5ef")) }
    val im = InstancedMesh(birdGeo, toon("#ffffff") { side = Side.DOUBLE }, data)
    im.frustumCulled = false
    scene.add(im)
    BIRDS.im = im
}
fun Game.updateBirds(dt: Double, t: Double, pp: Vec3) {
    val im = BIRDS.im ?: return
    for (i in BIRDS.list.indices) {
        val b = BIRDS.list[i]
        val a = t * b.sp + b.ph
        val y = if (b.eagle) b.y + sin(t * 0.1 + b.ph) * 20 else b.c.y + sin(t * 0.7 + b.ph2) * 3
        if (b.eagle) { val T = b.T!!; b.y += dt * 1.5; if (b.y > T.top * 0.85) b.y = 40.0; b.c.set(T.x + WIND.v.x * (y / 4.5) * 0.6, 0.0, T.z + WIND.v.z * (y / 4.5) * 0.6) }
        b.pos.set(b.c.x + cos(a) * b.r, y, b.c.z + sin(a) * b.r)
        val d = b.pos.distanceTo(pp)
        if (d < 25 && b.flee < 0.5) { b.flee = 3.0; b.off.set(b.pos.x - pp.x, 10.0, b.pos.z - pp.z).normalize().multiplyScalar(30.0) }
        if (b.flee > 0) { b.flee -= dt; b.pos.addScaledVector(b.off, sin((b.flee / 3) * PI)) }
        val s = if (b.eagle) 2.4 else 1.1; val flap = sin(t * (if (b.flee > 0) 18 else if (b.eagle) 2 else 9) + b.ph) * (if (b.eagle) 0.25 else 1.0)
        _m.compose(b.pos, _q.setFromEuler(_e.set(0.0, -a + (if (b.sp > 0) PI else 0.0), sin(a * 2) * 0.2, Euler.YXZ)), _s.set(s, s * flap, s))
        im.data.setMatrixAt(i, _m)
    }
    im.data.version++
}

/** other paragliders soaring over Sơn Trà + parasails off the beaches + Ahamove helicopters */
class Pilot(val g: Group, val T: Thermal, var y: Double, val ph: Double, val r: Double) { val pos = Vec3() }
class Para(val boat: Group, val sail: Group, val rope: Lines, val ropeGeo: Geometry, val z0: Double, val z1: Double, val off: Double, val sp: Double, var s: Double, val h: Double, val ph: Double) { val pos = Vec3(); val bp = Vec3() }
class AiHeli(val m: Heli, val route: List<World.Pad>, var i: Int, var hold: Double) { val g get() = m.g; val pos get() = m.g.position; val vel = Vec3(); var psi = 0.0 }

fun Game.makeCanopy(cols: List<String>): Group {
    val g = Group()
    val seg = 9
    for (k in 0 until seg) {
        val a = ((k - (seg - 1) / 2.0) / seg) * 2.2
        val cell = mk(geoBox(1.25, 0.5, 3.2), cols[k % cols.size], sin(a) * 6.5, cos(a) * 6.5 - 6.5 + 8, 0.0, g, 0.03)
        cell.rotation.z = -a
    }
    val P = FloatList()
    var k = 0
    while (k < seg) { val a = ((k - (seg - 1) / 2.0) / seg) * 2.2; P.add3(sin(a) * 6.5, cos(a) * 6.5 - 6.5 + 7.8, 0.0); P.add3(0.0, 0.6, 0.0); k += 2 }
    val lg = Geometry(); lg.position = P.toArray()
    g.add(Lines(lg, LineMaterial("#3a3848"), false))
    box(0.7, 1.2, 0.7, cols[0], 0.0, 0.0, 0.0, g, 0.03)
    mk(GEO.sph, "#f0c8a0", 0.0, 0.9, 0.0, g, 0.02).setScale(0.35)
    return g
}
fun Game.makeSpeedboat(stripe: String): Group {
    val g = Group()
    mk(hullGeo, "#fbfaf6", 0.0, 0.55, 0.0, g, 0.05).scale.set(2.4, 1.2, 8.5)
    box(2.0, 0.25, 6.5, stripe, 0.0, 0.85, 0.5, g, 0.02)
    box(1.9, 0.12, 4.4, "#1f5fbf", 0.0, 1.22, 1.3, g, 0.0)
    val ws = box(1.8, 0.9, 0.12, "#9fd8f0", 0.0, 1.7, -0.6, g, 0.02)
    ws.rotation.x = -0.4
    box(0.6, 1.0, 0.5, "#f2c230", 0.0, 1.8, 0.6, g, 0.02)
    mk(GEO.sph, "#f0c8a0", 0.0, 2.5, 0.6, g, 0.0).setScale(0.3)
    scene.add(g)
    return g
}
fun Game.setupPilots() {
    val T = THERMALS.filter { it.z < -2300 && it.x > 400 }
    val cs = listOf(listOf("#e8562a", "#f2c230"), listOf("#2a9fd6", "#ffffff"), listOf("#e0418f", "#ffffff", "#8e44ad"), listOf("#1f9a8a", "#f2c230"), listOf("#f39c12", "#c0392b"))
    for (k in 0 until 5) {
        val g = makeCanopy(cs[k])
        scene.add(g)
        val y = RR(120.0, 260.0); val ph = rand() * 6; val r = RR(40.0, 70.0)
        PILOTS.add(Pilot(g, T[k % T.size], y, ph, r))
    }
    // parasailing: speedboats towing canopies up and down the Đà Nẵng beaches
    val O = World.AHA.orange; val Bl = World.AHA.blue
    val chutes = listOf(
        listOf(O, "#ffffff", Bl), listOf(Bl, "#ffffff", O), listOf(O, O, "#ffffff"),
        listOf("#e0418f", "#ffffff", "#f2c230"), listOf("#1f9a8a", "#f2c230", "#ffffff"), listOf(O, Bl, "#ffffff", Bl), listOf("#f2c230", "#e8562a", "#ffffff"), listOf(Bl, Bl, "#ffffff"),
    )
    val lanes = arrayOf(doubleArrayOf(-1900.0, -700.0), doubleArrayOf(-1500.0, 200.0), doubleArrayOf(-1100.0, 400.0), doubleArrayOf(-800.0, 500.0), doubleArrayOf(-300.0, 650.0), doubleArrayOf(1200.0, 2300.0), doubleArrayOf(2900.0, 3500.0), doubleArrayOf(-1950.0, -900.0))
    lanes.forEachIndexed { i, zz ->
        val z0 = zz[0]; val z1 = zz[1]
        val boat = makeSpeedboat(if (i % 3 == 2) Bl else O)
        val sail = makeCanopy(chutes[i % chutes.size])
        sail.setScale(0.85)
        scene.add(sail)
        val rg = Geometry(); rg.position = FloatArray(6); rg.dynamic = true
        val rope = Lines(rg, LineMaterial("#3a3848"), true)
        rope.frustumCulled = false
        scene.add(rope)
        val off = RR(90.0, 240.0); val sp = RR(9.0, 13.0); val s = rand() * (z1 - z0) * 2; val h = RR(26.0, 40.0); val ph = rand() * 6
        PARAS.add(Para(boat, sail, rope, rg, z0, z1, off, sp, s, h, ph))
    }
    // Ahamove delivery helicopters shuttling between the pads
    val P = World.AHA.pads
    listOf(intArrayOf(1, 2), intArrayOf(1, 3)).forEachIndexed { i, ab ->
        val m = makeHeli(true)
        scene.add(m.g)
        val p = P[ab[0]]
        m.g.position.set(p.x, p.y + 1.9, p.z)
        AIHELI.add(AiHeli(m, listOf(P[ab[0]], P[ab[1]]), 1, 2.0 + i * 6))
    }
}
fun Game.updateParas(dt: Double, t: Double) {
    val cp = camera.position
    for (P in PARAS) {
        P.s += dt * P.sp
        val span = P.z1 - P.z0; val u = P.s % (span * 2); val dir = if (u < span) 1.0 else -1.0; val z = P.z0 + (if (u < span) u else span * 2 - u); val x = Geog.coastX(z) + P.off + sin(t * 0.05 + P.ph) * 30
        P.boat.position.set(x, 0.1 + sin(t * 3 + P.ph) * 0.08, z)
        P.boat.rotation.set(-0.06, if (dir > 0) PI else 0.0, sin(t * 2 + P.ph) * 0.03)
        P.bp.copy(P.boat.position)
        P.pos.set(x + sin(t * 0.4 + P.ph) * 4, P.h + sin(t * 0.6 + P.ph) * 3, z - dir * 62)
        P.sail.position.copy(P.pos)
        P.sail.rotation.set(0.0, if (dir > 0) PI else 0.0, sin(t * 0.5 + P.ph) * 0.12)
        val a = P.ropeGeo.position
        a[0] = x.toFloat(); a[1] = 1.6f; a[2] = (z + dir * 3).toFloat(); a[3] = P.pos.x.toFloat(); a[4] = P.pos.y.toFloat(); a[5] = P.pos.z.toFloat()
        P.ropeGeo.posVersion++
        if (abs(x - cp.x) + abs(z - cp.z) < 700 && rand() < 0.7)
            emit(PB, _v2.set(x + RR(-1.2, 1.2), 0.3, z + dir * 4.5), _v3.set(RR(-2.5, 2.5), RR(1.5, 4.0), dir * RR(1.0, 4.0)), "#ffffff", RR(0.8, 1.4), -9.0, 1.2)
    }
    for (H in AIHELI) {
        val tgt = H.route[H.i]; val dx = tgt.x - H.pos.x; val dz = tgt.z - H.pos.z; val d = hypot(dx, dz)
        H.m.rotor.rotation.y += dt * (if (H.hold > 0) 10 else 26)
        H.m.tail.rotation.x += dt * 40
        if (H.hold > 0) { H.hold -= dt; if (H.hold <= 0) H.i = (H.i + 1) % H.route.size; continue }
        val cruise = maxOf(95.0, gridH(H.pos.x, H.pos.z) + 70, tgt.y + 40)
        val spd = if (d > 30) min(34.0, d * 0.35 + 4) else 0.0
        H.vel.x = damp(H.vel.x, if (d > 1) (dx / d) * spd else 0.0, 1.2, dt)
        H.vel.z = damp(H.vel.z, if (d > 1) (dz / d) * spd else 0.0, 1.2, dt)
        val yT = if (d < 30) tgt.y + 1.9 else cruise
        H.vel.y = damp(H.vel.y, clamp((yT - H.pos.y) * 0.6, -7.0, 7.0), 1.5, dt)
        H.pos.addScaledVector(H.vel, dt)
        if (hypot(H.vel.x, H.vel.z) > 2) H.psi += angDiff(atan2(-H.vel.x, -H.vel.z), H.psi) * (1 - exp(-1.5 * dt))
        val sp = hypot(H.vel.x, H.vel.z)
        H.g.rotation.set(-clamp(sp / 34, 0.0, 1.0) * 0.22, H.psi, 0.0, Euler.YXZ)
        if (d < 4 && abs(H.pos.y - (tgt.y + 1.9)) < 0.6) { H.hold = RR(5.0, 9.0); H.vel.set(0.0, 0.0, 0.0) }
    }
}
fun Game.updatePilots(dt: Double, t: Double) {
    for (p in PILOTS) {
        val a = t * 0.16 + p.ph
        p.y += dt * 0.8
        if (p.y > p.T.top - 20) p.y = 110.0
        p.pos.set(p.T.x + cos(a) * p.r + WIND.v.x * 5, p.y, p.T.z + sin(a) * p.r + WIND.v.z * 5)
        p.g.position.copy(p.pos)
        p.g.rotation.set(0.0, -a, 0.25, Euler.YXZ)
    }
    updateParas(dt, t)
}

/** Dragon Bridge: LEDs at night, breathes fire, then water */
fun Game.updateDragon(dt: Double, t: Double, pp: Vec3) {
    val D = DRAGON
    if (D.mats.size < 2) return
    val night = env.glow > 0.5
    D.fireT -= dt
    val near = pp.distanceTo(D.head) < 160
    if (D.fireT < -(if (night) 22 else 55) || (near && D.fireT < -12)) { D.fireT = 9.0; sfx.whoosh(D.head) }
    val hue = (t * 0.06) % 1
    D.mats[0].emissive.setHSL(if (night) hue else 0.12, if (night) 0.9 else 0.8, 0.5)
    D.mats[0].emissiveIntensity = if (night) 0.55 else 0.0
    D.mats[1].emissive.setHSL(if (night) (hue + 0.5) % 1 else 0.05, 0.9, 0.5)
    D.mats[1].emissiveIntensity = if (night) 0.5 else 0.0
    D.fire = 0.0
    if (D.fireT > 0) {
        val water = D.fireT < 3.5
        D.fire = if (water) 0.0 else 1.0
        for (k in 0 until (if (water) 6 else 9)) {
            _v2.set(RR(16.0, 30.0), RR(-1.0, 4.0), RR(-3.0, 3.0))
            if (water) emit(PB, D.head, _v2.multiplyScalar(0.9), pick(listOf("#ffffff", "#bfe8ff", "#e0f6ff")), RR(1.0, 1.8), -12.0, 0.5)
            else emit(PF, D.head, _v2, pick(listOf("#ffd35a", "#ff8a2a", "#ff5a20", "#ffe9a0")), RR(0.5, 1.1), 3.0, 1.4)
        }
        D.headG?.let { it.rotation.z = sin(t * 3) * 0.06 }
    }
}

/** fireworks over the Hàn river (Đà Nẵng International Fireworks Festival) */
class Shell(val p: Vec3, val v: Vec3, var fuse: Double, val kind: Int, val col: String)
class Fireworks { var t = 2.0; val shells = ArrayList<Shell>(); var last: Vec3? = null; var lastT = -99.0 }
fun Game.updateFireworks(dt: Double, t: Double) {
    val show = env.glow >= 0.6 && ENV.rain <= 0.5
    if (!show && FW.shells.isEmpty()) return
    FW.t -= dt
    if (show && FW.t < 0) {
        FW.t = RR(0.6, 2.2)
        val x = Geog.lineXatZ(Geog.R_HAN, -560.0) + RR(-40.0, 40.0); val z = -560 + RR(-60.0, 60.0)
        val v = Vec3(RR(-3.0, 3.0), RR(55.0, 70.0), RR(-3.0, 3.0)); val fuse = RR(1.6, 2.4); val kind = floor(rand() * 4).toInt()
        FW.shells.add(Shell(Vec3(x, 2.0, z), v, fuse, kind, pick(listOf("#ff4d6d", "#ffd35a", "#5ad1ff", "#7dff8a", "#c45aff", "#ffffff", "#ff8a2a"))))
    }
    var i = FW.shells.size - 1
    while (i >= 0) {
        val s = FW.shells[i]
        s.fuse -= dt
        s.v.y -= 22 * dt
        s.p.addScaledVector(s.v, dt)
        emit(PF, s.p, _v2.set(RR(-1.0, 1.0), RR(-2.0, 0.0), RR(-1.0, 1.0)), "#ffcf7a", 0.35, -4.0, 2.0)
        if (s.fuse <= 0) {
            val n = if (s.kind == 2) 70 else 110
            for (k in 0 until n) {
                if (s.kind == 1) { val a = (k.toDouble() / n) * PI * 2; _v2.set(cos(a), RR(-0.1, 0.1), sin(a)).multiplyScalar(30.0) }
                else if (s.kind == 3) { val a = (k.toDouble() / n) * PI * 2; val r = 16 * sin(a).pow(3); val h = 13 * cos(a) - 5 * cos(2 * a) - 2 * cos(3 * a) - cos(4 * a); _v2.set(r, h, RR(-1.0, 1.0)).multiplyScalar(1.6) }
                else _v2.set(RR(-1.0, 1.0), RR(-1.0, 1.0), RR(-1.0, 1.0)).normalize().multiplyScalar(RR(22.0, 34.0))
                emit(PF, s.p, _v2, if (s.kind == 2) "#ffd35a" else if (rand() < 0.15) "#ffffff" else s.col, if (s.kind == 2) RR(2.0, 3.0) else RR(1.4, 2.2), if (s.kind == 2) -9.0 else -5.0, if (s.kind == 2) 1.4 else 1.1)
            }
            sfx.boom(s.p)
            FW.last = s.p.clone()
            FW.lastT = t
            FW.shells.removeAt(i)
        }
        i--
    }
}

/** floating flower lanterns on the Thu Bồn at night */
class HoaDangL(var s: Double, val off: Double, val sp: Double, val ph: Double)
class HoaDang { var im: InstancedMesh? = null; val list = ArrayList<HoaDangL>() }
fun Game.setupHoaDang() {
    val N = floor(220 * Q.dens).toInt() + 60
    val data = InstanceData(N, true)
    val cols = listOf("#ffb347", "#ff6b6b", "#ffd35a", "#ff8ad8", "#ffe9a0")
    for (i in 0 until N) {
        val s = RR(3900.0, 4500.0); val off = RR(-38.0, 38.0); val sp = RR(0.6, 1.6); val ph = rand() * 6
        HOADANG.list.add(HoaDangL(s, off, sp, ph))
        data.setColorAt(i, linCol(pick(cols)))
    }
    val im = InstancedMesh(GEO.cone6, lanternMat, data)
    im.frustumCulled = false
    scene.add(im)
    HOADANG.im = im
}
fun Game.updateHoaDang(dt: Double, t: Double) {
    val im = HOADANG.im ?: return
    val on = env.glow > 0.35
    im.visible = on
    if (!on) return
    for (i in HOADANG.list.indices) {
        val h = HOADANG.list[i]
        h.s += h.sp * dt
        if (h.s > 4700) h.s = 3900.0
        Geog.linePoint(Geog.R_THUBON, h.s, _cp)
        val x = _cp.x - _cp.tz * h.off; val z = _cp.z + _cp.tx * h.off
        _m.compose(_p.set(x, 0.35 + sin(t * 2 + h.ph) * 0.05, z), _q.setFromAxisAngle(YUP, t * 0.2 + h.ph), _s.set(0.7, 0.6, 0.7))
        im.data.setMatrixAt(i, _m)
    }
    im.data.version++
}

/** squid-fishing boats light up the sea at night (module scope in the web build: built at load) */
class Squid(val m: Points, val mat: PointsMaterial)
fun Game.makeSquid(): Squid {
    val N = 260; val pos = FloatArray(N * 3); val col = FloatArray(N * 3)
    for (i in 0 until N) {
        val z = RR(-3600.0, 4800.0); val x = if (i < 120) Geog.coastX(max(-2200.0, z)) + RR(500.0, 3200.0) else RR(2600.0, 6000.0)
        if (Geog.sdIslands(x, z) > -60 || Geog.sdSonTra(x, z) > -60) { pos[i * 3 + 1] = -999f; continue }
        pos[i * 3] = x.toFloat(); pos[i * 3 + 1] = 2.6f; pos[i * 3 + 2] = z.toFloat()
        if (rand() < 0.8) { col[i * 3] = 1f; col[i * 3 + 1] = 0.97f; col[i * 3 + 2] = 0.85f } else { col[i * 3] = 0.75f; col[i * 3 + 1] = 1f; col[i * 3 + 2] = 0.85f }
    }
    val geo = Geometry(); geo.position = pos; geo.color = col
    val mat = PointsMaterial(7.0).also { it.transparent = true; it.blending = Blend.ADDITIVE; it.depthWrite = false; it.fog = false }
    val m = Points(geo, mat); m.frustumCulled = false
    scene.add(m)
    return Squid(m, mat)
}
/** soft halos around lanterns at night */
fun Game.setupHalos() {
    val N = GLOWS.size; val pos = FloatArray(N * 3); val col = FloatArray(N * 3)
    GLOWS.forEachIndexed { i, g -> pos[i * 3] = g.x.toFloat(); pos[i * 3 + 1] = g.y.toFloat(); pos[i * 3 + 2] = g.z.toFloat(); val c = linCol(g.col); col[i * 3] = c.r.toFloat(); col[i * 3 + 1] = c.g.toFloat(); col[i * 3 + 2] = c.b.toFloat() }
    val geo = Geometry(); geo.position = pos; geo.color = col
    val tex = canvasTex(64, 64, false) { g, W, _ ->
        g.setFillRadial(W / 2f, W / 2f, W / 2f, floatArrayOf(0f, 0.35f, 1f), intArrayOf(rgba(255, 255, 255, 1.0), rgba(255, 255, 255, 0.45), rgba(255, 255, 255, 0.0)))
        g.fillRect(0f, 0f, W.toFloat(), W.toFloat())
    }
    val mat = PointsMaterial(4.5).also { it.transparent = true; it.opacity = 0.0; it.blending = Blend.ADDITIVE; it.depthWrite = false; it.map = tex }
    val h = Points(geo, mat); h.frustumCulled = false
    scene.add(h)
    HALO = h; haloMat = mat
}
/** Bảy Mẫu spinning basket boats */
class Spinner(val g: Group, val sp: Double)
fun Game.setupSpinners() {
    for (k in 0 until 4) {
        val L = Geog.RIVERS[4]
        val p = Geog.linePoint(L, RR(0.2, 0.8) * L.len); val g = Group()
        g.position.set(p.x, 0.05, p.z)
        mk(GEOC.thung, "#5b4630", 0.0, 0.0, 0.0, g, 0.04).scale.set(1.3, 1.0, 1.3)
        box(0.5, 1.2, 0.4, pick(World.PALS.clothes), 0.4, 1.2, 0.0, g, 0.02)
        mk(GEOC.hat, "#f1e3b5", 0.4, 0.6, 0.0, g, 0.0).setScale(0.9)
        scene.add(g)
        SPINNERS.add(Spinner(g, RR(1.5, 4.0) * (if (rand() < 0.5) 1 else -1)))
    }
}
fun Game.updateMisc(dt: Double, t: Double) {
    for (s in SPINNERS) s.g.rotation.y += s.sp * dt
    for (d in DOUCS) { d.g.position.y = d.y + abs(sin(t * 1.3 + d.ph)) * 0.4; d.g.rotation.y += sin(t * 0.7 + d.ph) * dt * 0.6 }
    for (w in WINDSOCKS) { val a = atan2(WIND.dir.x, WIND.dir.z); w.parent?.rotation?.y = 0.0; w.rotation.set(PI / 2 - 0.25 + sin(t * 3) * 0.06, 0.0, 0.0, Euler.YXZ); w.rotation.y = a - (w.parent?.rotation?.y ?: 0.0) }
    if (CABLE.cars.isNotEmpty()) {
        for (c in CABLE.cars) {
            c.t = (c.t + dt * 0.012) % 1
            val u = if (c.lane != 0) 1 - c.t else c.t
            c.g.position.lerpVectors(CABLE.a, CABLE.b, u)
            c.g.position.z += if (c.lane != 0) 3 else 0
            c.g.position.y -= sin(u * PI) * 18
        }
    }
    for (a in ANIM) a(dt, t)
    HALO?.let { h -> haloMat!!.opacity = smooth(0.3, 0.8, env.glow) * 0.9; h.visible = env.glow > 0.3 }
    SQUID.m.visible = env.glow > 0.45
    SQUID.mat.opacity = smooth(0.45, 0.9, env.glow)
}
