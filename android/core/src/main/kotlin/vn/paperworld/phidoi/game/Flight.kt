package vn.paperworld.phidoi.game

import vn.paperworld.phidoi.engine.*
import vn.paperworld.phidoi.world.*
import kotlin.math.*

/* =========================================================
   FLIGHT — one point-mass model for the planes, a hover model for the helicopter
   v' = T − D − g·sinγ · γ' = g(n·cosφ − cosγ)/v · ψ' = g·n·sinφ/(v·cosγ)
   ========================================================= */
const val G = 9.8

/* ---------- aircraft models ---------- */
fun Game.buildModels() {
    run {
        // phản lực giấy: a delta-wing paper jet in Ahamove orange and blue
        val g = Group(); val cream = "#fbf8ef"; val O = World.AHA.orange; val Bl = World.AHA.blue
        val M = CraftModel(g)
        // triangles, each flipped so its normal agrees with ref(centroid) — keeps the ink outline intact
        fun part(tris: List<Array<DoubleArray>>, col: String, th: Double, ref: (DoubleArray) -> DoubleArray, poly: Boolean = false): Mesh {
            val P = FloatList()
            for (t in tris) {
                val a = t[0]; val b = t[1]; val c = t[2]
                val ux = b[0] - a[0]; val uy = b[1] - a[1]; val uz = b[2] - a[2]; val vx = c[0] - a[0]; val vy = c[1] - a[1]; val vz = c[2] - a[2]
                val nx = uy * vz - uz * vy; val ny = uz * vx - ux * vz; val nz = ux * vy - uy * vx
                val r = ref(doubleArrayOf((a[0] + b[0] + c[0]) / 3, (a[1] + b[1] + c[1]) / 3, (a[2] + b[2] + c[2]) / 3))
                val flip = nx * r[0] + ny * r[1] + nz * r[2] < 0
                for (v in if (flip) arrayOf(a, c, b) else arrayOf(a, b, c)) P.add3(v[0], v[1], v[2])
            }
            val geo = Geo.tris(P.toArray())
            val m = Mesh(geo, toon(col) { side = Side.DOUBLE; if (poly) { polygonOffset = true; polyFactor = -2f } })
            if (th > 0) outlined(m, th)
            g.add(m)
            return m
        }
        fun v(x: Double, y: Double, z: Double) = doubleArrayOf(x, y, z)
        val N = v(0.0, 0.0, -5.2)
        val Mi = arrayOf(v(0.0, 0.75, -1.2), v(0.8, 0.0, -1.2), v(0.0, -0.6, -1.2), v(-0.8, 0.0, -1.2))
        val T = arrayOf(v(0.0, 0.55, 3.6), v(0.6, 0.0, 3.6), v(0.0, -0.45, 3.6), v(-0.6, 0.0, 3.6))
        val fus = ArrayList<Array<DoubleArray>>()
        for (i in 0 until 4) { val j = (i + 1) % 4; fus.add(arrayOf(N, Mi[i], Mi[j])); fus.add(arrayOf(Mi[i], T[i], T[j])); fus.add(arrayOf(Mi[i], T[j], Mi[j])) }
        part(fus, cream, 0.045, { c -> v(c[0], c[1], 0.0) })
        part(listOf(arrayOf(T[0], T[1], T[2]), arrayOf(T[0], T[2], T[3])), "#3a3848", 0.0, { v(0.0, 0.0, 1.0) })
        val up = { _: DoubleArray -> v(0.0, 1.0, 0.0) }
        for (s in doubleArrayOf(-1.0, 1.0)) {
            fun X(p: DoubleArray) = v(p[0] * s, p[1], p[2])
            part(listOf(arrayOf(X(v(0.7, -0.08, -1.0)), X(v(4.8, 0.12, 2.3)), X(v(4.8, 0.12, 3.1))), arrayOf(X(v(0.7, -0.08, -1.0)), X(v(4.8, 0.12, 3.1)), X(v(0.65, -0.08, 3.3)))), cream, 0.045, up)
            fun band(x0: Double, x1: Double, col: String) {
                fun le(x: Double): DoubleArray { val t = (x - 0.7) / 4.1; return X(v(x, -0.08 + t * 0.2 + 0.03, -1.0 + t * 3.3)) }
                fun te(x: Double): DoubleArray { val t = (x - 0.65) / 4.15; return X(v(x, -0.08 + t * 0.2 + 0.03, 3.3 - t * 0.2)) }
                part(listOf(arrayOf(le(x0), le(x1), te(x1)), arrayOf(le(x0), te(x1), te(x0))), col, 0.0, up, true)
            }
            band(3.3, 4.0, O)
            band(2.6, 2.85, Bl)
            part(listOf(arrayOf(X(v(0.55, 0.15, -3.3)), X(v(1.9, 0.2, -2.4)), X(v(0.6, 0.15, -2.2)))), O, 0.035, up)
            part(listOf(arrayOf(X(v(0.45, 0.45, 1.6)), X(v(0.5, 0.4, 3.5)), X(v(1.15, 2.2, 3.9))), arrayOf(X(v(0.45, 0.45, 1.6)), X(v(1.15, 2.2, 3.9)), X(v(0.95, 2.2, 3.0)))), Bl, 0.035, { v(s, 0.3, 0.0) })
        }
        mk(GEO.sph, "#7fc3e0", 0.0, 0.62, -2.3, g, 0.03).scale.set(0.45, 0.38, 1.25)
        val nose = mk(GEO.cone6, O, 0.0, 0.0, -4.75, g, 0.02)
        nose.scale.set(0.22, 1.0, 0.22)
        nose.rotation.x = -PI / 2
        val noz = mk(GEO.cyl16, "#3a3848", 0.0, 0.0, 3.75, g, 0.02)
        noz.scale.set(0.45, 0.5, 0.45)
        noz.rotation.x = PI / 2
        val fm = BasicMaterial("#ffb347").also { it.transparent = true; it.opacity = 0.85; it.blending = Blend.ADDITIVE; it.depthWrite = false }
        val flame = Mesh(GEO.cone6, fm)
        flame.scale.set(0.4, 2.4, 0.4)
        flame.rotation.x = PI / 2
        flame.position.z = 5.1
        g.add(flame)
        g.traverse { it.castShadow = true }
        flame.castShadow = false
        M.flame = flame; M.flameMat = fm
        M.tips = arrayOf(Vec3(-4.8, 0.12, 2.7), Vec3(4.8, 0.12, 2.7))
        MODELS["jet"] = M
    }
    run {
        // thủy phi cơ: high-wing floatplane, folded from postcard paper
        val g = Group(); val cream = "#fbf8ef"; val red = "#d6452f"; val sea = "#2a9fd6"
        val M = CraftModel(g)
        mk(Geo.cylinder(0.85, 0.5, 7.5, 10).rotateX(PI / 2).addONormal(), cream, 0.0, 0.6, 0.4, g, 0.05)
        mk(GEO.sph, red, 0.0, 0.6, -3.5, g, 0.04).scale.set(0.85, 0.85, 0.9)
        box(1.3, 0.75, 1.8, "#7fc3e0", 0.0, 1.35, -1.4, g, 0.03)
        box(10.5, 0.18, 1.7, cream, 0.0, 1.85, -1.1, g, 0.05)
        box(10.5, 0.2, 0.5, red, 0.0, 1.87, -0.25, g, 0.0)
        box(3.6, 0.14, 1.1, cream, 0.0, 0.9, 3.8, g, 0.04)
        val fin = box(0.14, 1.9, 1.3, sea, 0.0, 1.7, 3.8, g, 0.04)
        fin.rotation.x = -0.25
        for (s in doubleArrayOf(-1.0, 1.0)) {
            val fl = mk(hullGeo, cream, s * 1.5, -1.1, -0.4, g, 0.04)
            fl.scale.set(0.55, 0.5, 4.6)
            box(0.12, 1.4, 0.12, "#3a3848", s * 1.2, -0.4, -1.6, g, 0.02)
            box(0.12, 1.4, 0.12, "#3a3848", s * 1.2, -0.4, 0.8, g, 0.02)
            val strut = box(0.1, 0.1, 2.6, "#3a3848", s * 2.4, 1.2, -0.9, g, 0.0)
            strut.rotation.z = s * 0.45
        }
        val prop = Group()
        prop.position.set(0.0, 0.6, -4.35)
        g.add(prop)
        box(3.0, 0.3, 0.1, "#3a3848", 0.0, 0.0, 0.0, prop, 0.02)
        val dm = BasicMaterial("#ffffff").also { it.transparent = true; it.opacity = 0.0; it.depthWrite = false }
        val disc = Mesh(Geo.circle(1.5, 20), dm)
        prop.add(disc)
        M.prop = prop; M.disc = disc; M.discMat = dm
        M.tips = arrayOf(Vec3(-5.2, 1.85, -1.1), Vec3(5.2, 1.85, -1.1))
        g.traverse { it.castShadow = true }
        MODELS["seaplane"] = M
    }
    run {
        val h = makeHeli(true)
        val M = CraftModel(h.g)
        M.rotor = h.rotor; M.tail = h.tail; M.disc = h.disc; M.discMat = h.discMat
        M.tips = arrayOf(h.tips[0], h.tips[1])
        MODELS["heli"] = M
    }
    for (M in MODELS.values) { M.g.visible = false; F.g.add(M.g) }
}

/* wingtip trails (vertex alpha ribbons) */
fun Game.pushTrail(tr: Trail, p: Vec3, a: Double, c: DoubleArray) {
    System.arraycopy(tr.pos, 0, tr.pos, 3, (TRAIL_N - 1) * 3)
    System.arraycopy(tr.col, 0, tr.col, 4, (TRAIL_N - 1) * 4)
    tr.pos[0] = p.x.toFloat(); tr.pos[1] = p.y.toFloat(); tr.pos[2] = p.z.toFloat()
    tr.col[0] = c[0].toFloat(); tr.col[1] = c[1].toFloat(); tr.col[2] = c[2].toFloat(); tr.col[3] = a.toFloat()
    for (i in 1 until TRAIL_N) tr.col[i * 4 + 3] *= 0.955f
    tr.geo.posVersion++
    tr.geo.colVersion++
}

fun Game.setCraft(id: String, quiet: Boolean = false) {
    val C = CRAFT[id] ?: return
    val was = F.c
    F.c = C
    for ((k, m) in MODELS) m.g.visible = k == id
    F.model = MODELS[id]
    if (F.mode == "fly" && was !== C) { F.v = clamp(F.v, C.vs * 1.15, C.vmax * 0.8); if (C.glide) F.v = C.vb }
    if (C.heli) { F.hv.copy(fwd(F.psi, 0.0, _v3)).multiplyScalar(min(F.v, 40.0)); F.vy = 0.0; F.gam = 0.0; F.trick = null }
    if (was.heli && !C.heli && F.mode == "landed") { F.mode = "fly"; F.pos.y += 3; F.v = C.vb; F.gam = 0.2 }
    if (F.mode == "water" && !C.water) { F.mode = "fly"; F.pos.y += 3; F.gam = 0.2; F.v = max(C.vb, F.v) }
    F.energy = 1.0
    F.thr = 0.6
    if (!quiet) { burst(F.pos, listOf("#ffffff", "#f2c230"), 30); sfx.fold(); toast(C.icon + " " + C.name) }
    store.put("craft", id)
}

/* ---------- spawn points ---------- */
class SpawnPt(val x: Double, val z: Double, val y: Double? = null, val psi: Double? = null)
fun Game.spawnAt(where: SpawnPt) {
    val x = where.x; val z = where.z; val psi = where.psi ?: F.psi
    val y = maxOf(where.y ?: 0.0, gridH(x, z) + 40, 40.0)
    F.pos.set(x, y, z)
    F.psi = psi; F.gam = 0.0; F.phi = 0.0; F.v = F.c.vb * 1.15; F.hv.set(0.0, 0.0, 0.0); F.vy = 0.0; F.mode = "fly"; F.energy = 1.0; F.trick = null
    if (F.c.heli) { F.hv.copy(fwd(psi, 0.0, _v3)).multiplyScalar(20.0); F.v = 20.0 }
    F.hist.clear(); F.hist.add(Hist(F.pos.clone(), psi, game.t))
    F.prevY = y
    F.model?.let { it.g.scale.set(1.0, 1.0, 1.0); it.g.rotation.set(0.0, 0.0, 0.0) }
    for (tr in trails) { for (i in 0 until TRAIL_N) { tr.pos[i * 3] = x.toFloat(); tr.pos[i * 3 + 1] = y.toFloat(); tr.pos[i * 3 + 2] = z.toFloat() }; tr.col.fill(0f); tr.geo.posVersion++; tr.geo.colVersion++ }
    game.camSnap = true
    game.tileBurst = 10
    if (READY) { updateCells(F.pos); updateStatics(F.pos, fogFar) }
}
fun Game.respawn() {
    val ok = F.hist.filter { it.t < game.t - 5 }
    val h = if (ok.isNotEmpty()) ok[ok.size - 1] else F.hist[0]
    spawnAt(SpawnPt(h.p.x, h.p.z, h.p.y, h.psi))
    F.pos.copy(h.p)
    F.pos.y = maxOf(h.p.y + 40, gridH(h.p.x, h.p.z) + 90, SEA_Y + 70)
    F.gam = 0.05
    game.fade = 0.9
    PL.hp = PL.hpMax; PL.heat = 0.0; PL.over = false; PL.inv = 3.0; PL.lock = 0.0; PL.calmT = 0.0
    sfx.fold()
}

/* ---------- crash / bounce ---------- */
fun Game.crash(msg: String, shot: Boolean = false) {
    if (F.mode == "crash") return
    if (!SET.crash && !shot) {
        val s = surfaceAt(F.pos.x, F.pos.z)
        F.pos.y = max(F.pos.y, s + 2.2)
        F.gam = max(F.gam, 0.3)
        F.v = max(F.c.vs * 1.2, F.v * 0.7)
        F.pos.addScaledVector(fwd(F.psi, 0.0, _v3), -2.0)
        burst(F.pos, "#fbf8ef", 16, 7.0)
        sfx.thud(0.4)
        game.shake = 0.35
        hurtPlayer(10.0, _v3.set(0.0, -1.0, 0.0))
        return
    }
    F.mode = "crash"
    F.lastCrash = msg + " @" + listOf(F.pos.x, F.pos.y, F.pos.z).joinToString(",") { it.roundToInt().toString() }
    F.crashT = 0.0
    F.crashes++
    burst(F.pos, listOf("#fbf8ef", "#f4efe2", "#d6452f"), 70, 14.0, 6.0, 1.6)
    sfx.crumple()
    game.shake = 1.0
    game.hurt = 1.0
    if (MS.def != null) playerDown(msg)
    else showTip("✂ Nhàu giấy rồi!", msg, "Đang gấp lại cánh mới…", 2200.0)
}
fun Game.updateCrash(dt: Double) {
    F.crashT += dt
    val k = min(1.0, F.crashT * 2.5)
    val M = F.model!!.g
    M.scale.set(1 - k * 0.45, 1 - k * 0.7, 1 - k * 0.55)
    M.rotation.x += dt * 5; M.rotation.z += dt * 3.3
    val s = surfaceAt(F.pos.x, F.pos.z)
    if (F.pos.y > s + 0.8) F.pos.y = max(s + 0.8, F.pos.y - dt * 14)
    F.g.position.copy(F.pos)
    if (F.crashT > 2.2 && MS.state != "fail" && MS.state != "done") respawn()
}

/* ---------- the flight step ---------- */
private const val TRICK_ROLL = 1.05
fun Game.startTrick() {
    if (!F.c.tricks || F.mode != "fly" || F.trick != null) return
    val pin = IN.pitch * (if (SET.invert) -1 else 1)
    if (pin > 0.4 || IN.loopKey) {
        if (F.v < F.c.vb * 0.95) { toast("Cần thêm tốc độ để lộn vòng — chúc mũi lấy đà!"); return }
        F.trick = Trick("loop").also { it.a = 0.0; it.g0 = F.gam; it.psi = F.psi }
        sfx.whoosh(null)
    } else {
        F.trick = Trick("roll").also { it.t = 0.0; it.dir = if (IN.roll < -0.2) -1.0 else 1.0 }
        sfx.whoosh(null)
    }
}
/** helicopter: hover by default · W/S forward/back · A/D turn · Q/E sidestep · SPACE climb · X descend · SHIFT fast */
fun Game.roofUnder(x: Double, y: Double, z: Double): Double { val t = obstacleTop(x, z, 0.0); return if (y >= t - 1) t else -1e9 }
fun Game.heliStep(h: Double, lift: Double) {
    val C = F.c; val fIn = clamp(IN.pitch, -1.0, 1.0); val turn = clamp(IN.roll, -1.0, 1.0); val side = clamp(IN.yaw, -1.0, 1.0)
    val spd = F.hv.length(); val vmax = C.vmax
    F.psi -= turn * (1.6 - 0.7 * clamp(spd / 60, 0.0, 1.0)) * h
    val fx = -sin(F.psi); val fz = -cos(F.psi); val rx = cos(F.psi); val rz = -sin(F.psi)
    val k = 1 - exp(-(if (fIn != 0.0 || side != 0.0) 1.1 else 0.8) * h)
    F.hv.x += (fx * fIn * vmax + rx * side * 14 - F.hv.x) * k
    F.hv.z += (fz * fIn * vmax + rz * side * 14 - F.hv.z) * k
    var vyT = (if (IN.boost > 0.05) 11.0 else 0.0) - (if (IN.brake > 0) 9.0 else 0.0)
    if (SET.assist && vyT < 0) {
        // flare: slow the descent close to the ground or a roof
        val below = F.pos.y - maxOf(heightAt(F.pos.x, F.pos.z), SEA_Y, roofUnder(F.pos.x, F.pos.y, F.pos.z))
        vyT = max(vyT, -1.2 - below * 0.35)
    }
    F.vy = damp(F.vy, vyT, 2.2, h)
    F.pos.x += (F.hv.x + WIND.v.x * 0.35) * h
    F.pos.z += (F.hv.z + WIND.v.z * 0.35) * h
    F.pos.y += (F.vy + lift * 0.15) * h
    F.v = F.hv.length()
    F.gam = damp(F.gam, -fIn * 0.28, 3.0, h)
    F.phi = damp(F.phi, turn * 0.32 * clamp(F.v / 25, 0.3, 1.0) + side * 0.2, 3.0, h)
    F.energy = clamp(0.5 + F.vy / 22, 0.0, 1.0)
    F.n = 1.0; F.stall = 0.0
    // contact: ground, sea, or a flat roof
    F.ground = heightAt(F.pos.x, F.pos.z)
    F.overWater = F.ground < SEA_Y - 0.25
    val roof = roofUnder(F.pos.x, F.pos.y, F.pos.z); val surf = max(if (F.overWater) SEA_Y else F.ground, roof); val clear = 1.9
    F.agl = F.pos.y - surf
    if (F.agl < clear) {
        if (F.overWater && roof < SEA_Y) return crash("Trực thăng rơi xuống biển!")
        if (F.vy > -C.land.vs && F.v < C.land.v) {
            F.mode = "landed"; F.landY = surf + clear; F.pos.y = F.landY; F.vy = 0.0; F.hv.set(0.0, 0.0, 0.0); F.v = 0.0; F.gam = 0.0; F.phi = 0.0
            sfx.thud(0.3)
            burst(F.pos, "#e6d6a8", 16, 5.0, 2.0)
            val pad = World.AHA.pads.find { hypot(it.x - F.pos.x, it.z - F.pos.z) < it.r + 1 && abs(it.y - surf) < 2 }
            showTip("🚁 Hạ cánh", if (pad != null) (if (pad.hub) "Đáp đúng chữ H trên Ahamove Sky Hub!" else "Đáp xuống bãi trực thăng Ahamove") else if (roof > -1e8 && roof == surf) "Đáp trên nóc nhà" else "Hạ cánh an toàn", if (pad != null) "Đang sửa máy bay & nạp tên lửa · SHIFT để cất cánh" else "SHIFT để cất cánh", 3000.0)
            return
        }
        return crash(if (F.ground > 40) "Trực thăng đâm vào núi!" else "Trực thăng chạm đất quá mạnh!")
    }
    val hit = hitCollider(F.pos.x, F.pos.y, F.pos.z, 0.8) ?: hitCollider(F.pos.x + rx * 5, F.pos.y + 1.9, F.pos.z + rz * 5, 0.3) ?: hitCollider(F.pos.x - rx * 5, F.pos.y + 1.9, F.pos.z - rz * 5, 0.3) ?: hitCollider(F.pos.x + fx * 5, F.pos.y + 1.9, F.pos.z + fz * 5, 0.3)
    if (hit != null) crash(hitMsg(hit.tag))
}
fun Game.flyStep(h: Double, lift: Double) {
    val C = F.c; val assist = SET.assist
    var pin = clamp(IN.pitch * (if (SET.invert) -1 else 1), -1.0, 1.0)
    val rin = clamp(IN.roll, -1.0, 1.0)
    val gcas = assist && F.warn > 1 && F.trick == null // auto ground-collision avoidance: wings level, full pull
    if (gcas) pin = 1.0
    val v = max(F.v, 2.0)
    var T = 0.0; var dragK = 1.0
    F.tuck = 0.0
    // --- bank
    if (C.glide) F.phi = damp(F.phi, rin * C.bankMax, 1.5, h)
    else if (assist) F.phi = damp(F.phi, rin * C.bankMax, 3.4, h)
    else { F.phi = clamp(F.phi + rin * C.rollRate * h, -1.5, 1.5); if (abs(rin) < 0.05) F.phi *= 1 - 0.35 * h }
    if (gcas) F.phi = damp(F.phi, 0.0, 5.0, h)
    // --- propulsion
    if (C.id == "jet") {
        T += C.cruise
        val on = IN.boost > 0.05 && F.energy > 0.01
        if (on) { T += C.boost * IN.boost; F.energy = max(0.0, F.energy - C.eUse * h) }
        else F.energy = min(1.0, F.energy + C.eGain * h * (if (assist) 1.6 else 1.0))
        if (assist) T += clamp((C.vb * 0.95 - v) * 0.55, 0.0, 3.0) * max(0.0, cos(F.gam))
        if (IN.brake > 0) dragK += IN.brake * 2.4
    } else if (C.id == "seaplane") {
        F.thr = damp(F.thr, if (IN.boost > 0) 1.0 else if (IN.brake > 0) 0.0 else 0.62, 1.6, h)
        T += F.thr * C.thrust
        F.energy = F.thr
    }
    val cg = cos(F.gam); val cp = cos(F.phi); val sp = sin(F.phi)
    var gamDot: Double; var psiDot: Double; val n: Double
    run {
        val nMax = min(C.gMax, (v / C.vs).pow(2)) * (1 - 0.55 * F.tuck)
        val nLevel = cg / max(cp, 0.3)
        var nCmd = (if (assist) nLevel else cg + (nLevel - cg) * 0.8) + pin * C.nGain
        if (assist && v < C.vs * 1.3 && pin > 0) nCmd = min(nCmd, nMax * 0.88)
        if (gcas) nCmd = max(nCmd, nMax * 0.95)
        n = clamp(nCmd, -1.2, nMax)
        gamDot = (G * (n * cp - cg)) / v
        psiDot = (-G * n * sp) / (v * max(cg, 0.25)) - IN.yaw * C.yawK
        if (v < C.vs * 0.85) gamDot -= (1 - v / (C.vs * 0.85)) * 2.8
        if (abs(pin) < 0.05) gamDot += clamp((v - C.vb) / C.vb, -0.6, 0.6) * (if (assist) 0.25 else 0.5) // pitch stability: slow → nose drops
        if (assist && abs(pin) < 0.05) gamDot += (-0.035 - F.gam) * 0.7
        if (assist && v < C.vs * 1.45 && F.gam > 0.15) gamDot -= (1.45 - v / C.vs) * 1.6 // stall protection
        if (F.tuck > 0) gamDot += (-0.9 - F.gam) * F.tuck * 1.2
        if (assist && F.warn > 1) gamDot += 0.9 // terrain-avoid nudge
        F.stall = if (n >= nMax - 0.01 && nCmd > nMax + 0.05 && v < C.vs * 1.25) 1.0 else if (v < C.vs) 1.0 else 0.0
        val ge = if (F.agl < 8) 0.7 + F.agl * 0.0375 else 1.0 // ground effect
        val D = ((G * ((v / C.vb).pow(2) + n * n * (C.vb / v).pow(2))) / (2 * C.LD)) * dragK * ge
        F.v = clamp(F.v + (T - D - G * sin(F.gam)) * h, 1.5, C.vmax)
    }
    // --- tricks override the path angle
    val tr = F.trick
    if (tr != null && tr.type == "loop") {
        val R = max(14.0, (F.v * F.v) / (G * 4.5)); val w = F.v / R
        tr.a += w * h
        F.gam = tr.g0 + tr.a
        gamDot = 0.0; psiDot = 0.0
        if (tr.a >= PI * 2) { F.gam = tr.g0; F.trick = null; F.tricks++; trickDone("Lộn vòng!") }
        else if (F.v < C.vs * 0.7) { F.gam = -0.6; F.psi += PI * (if (tr.a > PI * 0.5 && tr.a < PI * 1.5) 1 else 0); F.trick = null; toast("Hụt hơi giữa vòng lộn…") }
    } else {
        F.gam = clamp(F.gam + gamDot * h, -1.45, 1.45)
    }
    F.psi += psiDot * h
    F.n = n
    // --- move with the air mass (wind + rising air)
    val cgN = cos(F.gam)
    F.pos.x += (-sin(F.psi) * cgN * F.v + WIND.v.x) * h
    F.pos.y += (sin(F.gam) * F.v + lift) * h
    F.pos.z += (-cos(F.psi) * cgN * F.v + WIND.v.z) * h
    // --- contact
    F.ground = heightAt(F.pos.x, F.pos.z)
    F.overWater = F.ground < SEA_Y - 0.25
    val surf = if (F.overWater) SEA_Y else F.ground; val clear = if (C.glide) 0.9 else if (C.id == "seaplane") 1.7 else 0.55
    F.agl = F.pos.y - surf
    if (F.agl < clear) contact(surf, clear, lift)
    if (F.mode != "fly") return
    val hit = hitCollider(F.pos.x, F.pos.y, F.pos.z, C.radius * 0.55)
    if (hit != null) crash(hitMsg(hit.tag))
}
private val HIT_MSG = mapOf(
    "bridge" to "Va vào thành cầu!", "dragon" to "Ôm trọn lưng rồng vàng!", "bld" to "Lao vào toà nhà!", "house" to "Đáp lên mái ngói phố Hội!", "temple" to "Va mái chùa — A di đà Phật!",
    "statue" to "Chạm vào tượng!", "radar" to "Đụng quả cầu radar!", "lighthouse" to "Va vào hải đăng!", "wheel" to "Kẹt vào vòng quay!", "ship" to "Đâm vào tàu du lịch!", "crane" to "Vướng cần cẩu!",
    "hand" to "Bàn tay đá đã tóm được bạn!", "tree" to "Mắc vào cây đa!", "jet" to "Va máy bay đang đỗ!", "gate" to "Va cổng Hải Vân Quan!", "pier" to "Va cầu cảng!", "station" to "Va nhà ga cáp treo!", "pylon" to "Vướng trụ cáp treo!",
)
fun hitMsg(tag: String?) = HIT_MSG[tag] ?: "Va chạm!"
fun Game.contact(surf: Double, clear: Double, lift: Double) {
    val C = F.c; val vs = F.v * sin(F.gam) + lift
    if (F.overWater) {
        if (C.water && vs > -C.land.vs && abs(F.phi) < 0.42 && F.v < C.land.v + 14 && F.gam < 0.15) {
            F.mode = "water"; F.pos.y = SEA_Y + clear; F.gam = 0.0; F.phi = 0.0
            splash(F.pos, 40); sfx.splash()
            toast("🛩 Đáp nước êm ru · SHIFT tăng ga · W để cất cánh")
            return
        }
        if (C.id == "jet" && vs > -4 && F.v > 15 && abs(F.phi) < 0.6) {
            F.pos.y = SEA_Y + clear + 0.1; F.gam = 0.1 + rand() * 0.08; F.v *= 0.9
            splash(F.pos, 18); sfx.splash(0.5)
            F.skips = if (game.t - F.skipT < 4) F.skips + 1 else 1; F.skipT = game.t; toast("Thia lia × " + F.skips)
            return
        }
        F.pos.y = SEA_Y + 0.3
        splash(F.pos, 50)
        sfx.splash()
        return crash(if (C.id == "jet") "Cánh giấy ướt sũng nước biển!" else "Lao xuống nước!")
    }
    val sl = hypot(heightAt(F.pos.x + 3, F.pos.z) - heightAt(F.pos.x - 3, F.pos.z), heightAt(F.pos.x, F.pos.z + 3) - heightAt(F.pos.x, F.pos.z - 3)) / 6
    if (vs > -C.land.vs && F.v < C.land.v && abs(F.phi) < 0.42 && sl < 0.45) {
        F.mode = "landed"; F.pos.y = surf + clear; F.gam = 0.0; F.phi = 0.0; F.trick = null
        sfx.thud(0.25)
        burst(F.pos, "#e6d6a8", 14, 4.0, 2.0)
        Geog.sample(F.pos.x, F.pos.z)
        val beach = Geog.S.sd < 50 && Geog.S.rock < 0.5
        showTip("✓ Hạ cánh", if (beach) "Đáp êm trên cát trắng" else "Hạ cánh nhẹ nhàng", "Đang vá cánh · SHIFT hoặc W để cất cánh", 3000.0)
        return
    }
    if (F.agl < 0.2 - (if (C.glide) 0.0 else 0.6) || vs < -C.land.vs * 1.6 || sl > 0.8 || F.v > C.land.v * 1.4) return crash(if (F.ground > 40) "Đâm vào sườn núi!" else "Đâm xuống đất!")
    // a skim: nudge back up
    F.pos.y = surf + clear
    F.gam = max(F.gam, 0.06)
}
private val SPLASH_COLS = listOf("#ffffff", "#e0f6ff", "#bfe8ff")
fun Game.splash(p: Vec3, n: Int) {
    for (k in 0 until n) emit(PB, _v2.set(p.x + RR(-1.5, 1.5), SEA_Y + 0.3, p.z + RR(-1.5, 1.5)), _v3.set(RR(-4.0, 4.0), RR(4.0, 11.0), RR(-4.0, 4.0)), pick(SPLASH_COLS), RR(0.6, 1.2), -18.0, 0.6)
}
fun Game.trickDone(name: String) {
    toast("★ " + name + " · " + F.tricks + " pha nhào lộn")
    sfx.chime(4)
}
fun Game.updateLanded(dt: Double) {
    if (F.c.heli) {
        F.pos.y = F.landY
        F.psi -= IN.roll * dt * 0.9
        if (IN.flap || IN.boost > 0.5) { F.mode = "fly"; F.vy = 6.0; F.pos.y += 0.5; sfx.whoosh(null) }
        return
    }
    F.v = damp(F.v, 0.0, 2.2, dt)
    F.pos.addScaledVector(fwd(F.psi, 0.0, _v3), F.v * dt)
    val s = surfaceAt(F.pos.x, F.pos.z)
    F.pos.y = s + (if (F.c.glide) 0.9 else 0.55)
    if (s <= SEA_Y + 0.01 && heightAt(F.pos.x, F.pos.z) < -0.4) { crash("Trôi xuống nước!"); return }
    if (IN.flapPressed || IN.pitch * (if (SET.invert) -1 else 1) > 0.5 || IN.boost > 0.5) {
        F.mode = "fly"
        F.v = max(F.v, F.c.vb * 0.95)
        F.gam = 0.38
        F.pos.y += 1.5
        sfx.fold()
        burst(F.pos, "#ffffff", 16, 6.0)
    }
}
private val wRand = java.util.Random(5)
fun Game.updateWater(dt: Double) {
    val C = F.c
    F.thr = damp(F.thr, if (IN.boost > 0) 1.0 else if (IN.brake > 0) 0.0 else 0.45, 1.5, dt)
    F.energy = F.thr
    val T = F.thr * C.thrust
    F.v = max(0.0, F.v + (T - (0.7 + 0.011 * F.v * F.v)) * dt)
    F.psi += -IN.roll * 0.75 * clamp(F.v / 6, 0.25, 1.0) * dt - IN.yaw * 0.5 * dt
    F.pos.addScaledVector(fwd(F.psi, 0.0, _v3), F.v * dt)
    F.pos.x += WIND.v.x * 0.15 * dt; F.pos.z += WIND.v.z * 0.15 * dt
    val g = heightAt(F.pos.x, F.pos.z)
    if (g > SEA_Y - 0.3) { F.pos.addScaledVector(fwd(F.psi, 0.0, _v3), -F.v * dt * 1.5); F.v = 0.0; if (game.t - F.beachT > 3) { toast("Cập bờ · quay đầu bằng A/D"); F.beachT = game.t } }
    F.pos.y = SEA_Y + 1.7 + sin(game.t * 2.1) * 0.12
    F.gam = damp(F.gam, if (F.v > 18) 0.05 else 0.0, 3.0, dt)
    F.phi = damp(F.phi, -IN.roll * 0.08, 3.0, dt)
    if (F.v > 4 && wRand.nextDouble() < F.v * 0.06) for (s in doubleArrayOf(-1.5, 1.5)) { val r = _v1.set(-cos(F.psi), 0.0, sin(F.psi)).multiplyScalar(s); emit(PB, _v2.copy(F.pos).add(r).setY(0.3), _v3.set(RR(-2.0, 2.0), RR(2.0, 5.0), RR(-2.0, 2.0)), "#ffffff", 0.7, -14.0, 1.0) }
    if (F.v > C.vs * 1.12 && IN.pitch * (if (SET.invert) -1 else 1) > 0.25) {
        F.mode = "fly"; F.gam = 0.12; F.pos.y += 0.6
        splash(F.pos, 24)
        toast("Cất cánh!")
    }
}

/* per-frame flight driver */
private val _f = Vec3(); private val _r = Vec3(); private val _tip = Vec3()
fun Game.updateFlight(dt: Double, t: Double) {
    updateWind(t)
    val C = F.c
    if (F.mode == "crash") { updateCrash(dt); return }
    var lift = airLift(F.pos.x, F.pos.y, F.pos.z, t)
    // turbulence: stronger at thermal edges and in clouds
    val turb = (0.25 + abs(LIFT.therm) * 0.18 + LIFT.inCloud * 1.2) * (if (C.glide) 0.6 else 1.0)
    lift += turb * sn(t * 1.7, F.pos.x * 0.01)
    F.lift = lift
    if (IN.trickPressed) startTrick()
    if (F.mode == "landed") updateLanded(dt)
    else if (F.mode == "water") updateWater(dt)
    else {
        val steps = ceil(dt / (1.0 / 90)).toInt().coerceAtLeast(1); val h = dt / steps
        var i = 0
        while (i < steps && F.mode == "fly") { if (C.heli) heliStep(h, lift) else flyStep(h, lift); i++ }
        if (F.mode == "fly" && !C.heli) {
            F.phi += sn(t * 2.3, 7.7) * turb * 0.03
            F.gam += sn(t * 2.9, 3.1) * turb * 0.006
        }
    }
    if (F.mode == "crash") return
    // dynamic obstacles
    if (F.mode == "fly") {
        for (J in JETS) if (J.g.visible && J.pos.distanceTo(F.pos) < 12) crash("Suýt nữa thì… va phải máy bay!")
        for (p in PILOTS) if (p.pos.distanceTo(F.pos) < 6) crash("Va vào một phi công dù lượn!")
        for (P in PARAS) { if (P.pos.distanceTo(F.pos) < 6) crash("Mắc vào dù kéo của cano!") else if (P.bp.distanceTo(F.pos) < 5) crash("Va vào cano kéo dù!") }
        for (H in AIHELI) if (H.pos.distanceTo(F.pos) < 7) crash("Va vào trực thăng Ahamove!")
        for (c in CABLE.cars) if (c.g.position.distanceTo(F.pos) < 4) crash("Va cabin cáp treo!")
    }
    // terrain look-ahead warning
    fwd(F.psi, F.gam, _f)
    F.warn = 0.0
    if (F.mode == "fly" && F.v > 6) {
        for (s in 1..4) {
            val vx = if (C.heli) F.hv.x else _f.x * F.v; val vz = if (C.heli) F.hv.z else _f.z * F.v; val vy = if (C.heli) F.vy else _f.y * F.v + lift
            val px = F.pos.x + (vx + WIND.v.x) * s * 0.8; val pz = F.pos.z + (vz + WIND.v.z) * s * 0.8; val py = F.pos.y + vy * s * 0.8
            val gh = max(gridH(px, pz), SEA_Y); val ob = obstacleTop(px, pz, 2.0)
            if ((py < gh + 4 && gh > SEA_Y + 1) || (gh <= SEA_Y + 1 && py < SEA_Y + 2 && vy < -6) || (ob > gh && py < ob + 3 && py > ob - 60)) F.warn = max(F.warn, 4.0 - s)
        }
    }
    // vertical speed (smoothed) + safe history
    val vsNow = (F.pos.y - F.prevY) / max(dt, 1e-3)
    F.prevY = F.pos.y
    vsSm = damp(vsSm, vsNow, 3.0, dt)
    F.vs = vsSm
    liftSm = damp(liftSm, lift, 2.0, dt)
    F.liftSm = liftSm
    F.histT += dt
    if (F.histT > 1.2 && F.mode == "fly" && F.agl > 18 && F.warn == 0.0) {
        F.histT = 0.0
        F.hist.add(Hist(F.pos.clone(), F.psi, game.t))
        if (F.hist.size > 12) F.hist.removeAt(0)
    }
    updateModel(dt, t)
}
private val mRand = java.util.Random(9)
fun Game.updateModel(dt: Double, t: Double) {
    val C = F.c; val M = F.model ?: return
    var rollV = F.phi
    F.offVis.set(0.0, 0.0, 0.0)
    val tr = F.trick
    if (tr != null && tr.type == "roll") {
        tr.t += dt
        val u = tr.t / TRICK_ROLL; val e = if (u < 1) u * u * (3 - 2 * u) else 1.0
        rollV += tr.dir * PI * 2 * e
        _r.set(-cos(F.psi), 0.0, sin(F.psi))
        F.offVis.addScaledVector(_r, sin(e * PI * 2) * 2.4 * tr.dir).add(_v1.set(0.0, (1 - cos(e * PI * 2)) * 1.4, 0.0))
        if (u >= 1) { F.trick = null; F.tricks++; trickDone("Lộn xoắn!") }
    }
    F.rollVis = rollV
    F.g.position.copy(F.pos).add(F.offVis)
    F.g.rotation.set(F.gam, F.psi, -rollV, Euler.YXZ)
    if (C.id == "jet") {
        val ab = IN.boost > 0.05 && F.energy > 0.01 && F.mode == "fly"
        val fl = M.flame!!
        fl.visible = F.mode == "fly"
        fl.scale.set(if (ab) 0.55 else 0.3, (if (ab) 3.4 else 1.2) * (0.85 + 0.3 * mRand.nextDouble()), if (ab) 0.55 else 0.3)
        fl.position.z = if (ab) 5.4 else 4.4
    }
    if (C.heli) {
        M.rotor?.let { it.rotation.y += dt * (if (F.mode == "landed") 9 else 26) }
        M.tail?.let { it.rotation.x += dt * 40 }
        M.discMat?.opacity = if (F.mode == "landed") 0.06 else 0.14
    }
    if (C.id == "seaplane") {
        M.prop!!.rotation.z += dt * (8 + F.thr * 60)
        M.discMat!!.opacity = 0.15 + F.thr * 0.25
    }
    // trails: paper streamers + white vortices when pulling G
    val boosting = C.id == "jet" && IN.boost > 0.05 && F.energy > 0.01
    val a = clamp((F.n - 1.6) * 0.35 + (if (boosting) 0.45 else 0.0) + (if (F.trick != null) 0.5 else 0.0), 0.0, 0.85) * (if (C.glide || C.heli) 0.0 else 1.0)
    F.g.updateWorldFromParents()
    F.g.updateMatrixWorld()
    for (i in 0 until 2) {
        _tip.copy(M.tips[i]).applyMatrix4(F.g.matrixWorld)
        pushTrail(trails[i], _tip, if (F.mode == "fly") a else 0.0, if (C.id == "seaplane" && boosting) doubleArrayOf(0.84, 0.27, 0.18) else doubleArrayOf(1.0, 1.0, 1.0))
    }
    // blob shadow
    val s = surfaceAt(F.pos.x, F.pos.z); val agl = F.pos.y - s
    blob.position.set(F.pos.x, s + 0.15, F.pos.z)
    val bs = (if (C.id == "seaplane" || C.heli) 4.5 else 3.2) * (1 + agl * 0.01)
    blob.scale.set(bs, 1.0, bs)
    blobMat.opacity = 0.32 * smooth(90.0, 2.0, agl) * (1 - env.glow * 0.6)
    blob.visible = agl < 90
}
