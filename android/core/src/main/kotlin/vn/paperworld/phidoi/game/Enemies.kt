package vn.paperworld.phidoi.game

import vn.paperworld.phidoi.engine.*
import vn.paperworld.phidoi.world.*
import kotlin.math.*

/* =========================================================
   ENEMIES — models, AI with attack tokens, the boss Mực Khổng Lồ
   ========================================================= */
/** a squid tentacle (web: { x, y, a, long }) */
class Tent(val x: Double, val y: Double, val a: Double, val long: Boolean)
/** the squid's animated parts (web: g.userData = { props, tm, tent, NT, NS, pods, core, shell }) */
class SquidParts(val props: List<Group>, val tm: InstancedMesh, val tent: List<Tent>, val NT: Int, val NS: Int, val pods: List<Mesh>, val core: Mesh, val shell: Mesh)
/** an enemy model + the parts its animation drives (web: g.userData) */
class EModel(val g: Group) {
    var tail: List<Mesh>? = null
    var squid: SquidParts? = null
    /** geometries only this model uses (freed when the enemy is removed) */
    val own = ArrayList<Geometry>()
}
class EKind(val name: String, val hp: Double, val r: Double, val v: Double, val turn: Double, val score: Int, val boom: Double, val build: () -> EModel)
/** one of the squid's weak points (web: e.parts[i]) */
class BossPart(val m: Mesh, val off: Vec3, val r: Double, var hp: Double, val max: Double, var alive: Boolean, val wp: Vec3, val core: Boolean)
class HullBall(val off: Vec3, val r: Double, val wp: Vec3)

class Enemy(val k: String, val kd: EKind, val model: EModel, val pos: Vec3) {
    val g get() = model.g
    val vel = Vec3(); var psi = 0.0; var gam = 0.0; var phi = 0.0; var v = 0.0
    var hp = 0.0; var hpMax = 0.0; var r = 0.0; var alive = true; var dying = 0.0; var st = "attack"; var stT = 0.0; var fireT = 0.0; var burst = 0; var gunT = 0.0
    var t = 0.0; var ph = 0.0; var anchor: Vec3? = null; var lm: MsLm? = null; var alt = 0.0; var minAlt = 26.0
    var smokeT = 0.0; var brk = 1.0; var clb = 0.0; val aim = Vec3(); var demo = false; var ang = 0.0; var spin = 1.0
    var tok = false; var sndT = 0.0; var dropT = 0.0; var lx = 0.0; var lz = 0.0
    /* the boss */
    var sc = 1.0; var parts: List<BossPart>? = null; var hull: List<HullBall> = emptyList(); var podsLeft = 0
    var misT = 0.0; var flakT = 0.0; var spawnT = 0.0; var boomT = 0.0; var ricT = 0.0
}

/* ---------- enemy models ---------- */
fun Game.matDS(c: String, off: Boolean = false): ToonMaterial {
    val k = c + (if (off) "o" else "")
    return dsCache.getOrPut(k) { if (off) toon(c) { side = Side.DOUBLE; polygonOffset = true; polyFactor = -2f } else toon(c) { side = Side.DOUBLE } }
}
private fun Game.triMesh(M: EModel, g: Group, pts: DoubleArray, material: Material, th: Double = 0.05): Mesh {
    val geo = Geo.tris(pts)
    M.own.add(geo)
    val m = Mesh(geo, material)
    if (th > 0) outlined(m, th)
    m.castShadow = true
    g.add(m)
    return m
}
private val DART_BODY = doubleArrayOf(0.0, 0.0, -2.6, -2.1, 0.42, 1.5, 0.0, -0.42, 1.0, 0.0, 0.0, -2.6, 0.0, -0.42, 1.0, 2.1, 0.42, 1.5, 0.0, 0.0, -2.6, 0.0, -0.42, 1.0, 0.0, -0.9, 1.3)
private val DART_STRIPE = doubleArrayOf(-1.55, 0.29, 1.05, -2.1, 0.425, 1.5, -1.8, 0.37, 1.5, 1.55, 0.29, 1.05, 1.8, 0.37, 1.5, 2.1, 0.425, 1.5)
private val DART_KEEL = doubleArrayOf(0.0, -0.42, 1.0, 0.0, -0.9, 1.3, 0.0, -0.62, 1.32)
/** carbon-paper fighter: the paper dart folded from black copy paper */
fun Game.mkDart(body: String, stripe: String, keel: String, s: Double): EModel {
    val g = Group(); val M = EModel(g)
    triMesh(M, g, DART_BODY, matDS(body), 0.045)
    triMesh(M, g, DART_STRIPE, matDS(stripe, true), 0.0)
    triMesh(M, g, DART_KEEL, matDS(keel), 0.0)
    mk(GEO.sph, eyeMat, 0.0, 0.1, -1.5, g, 0.0).scale.set(0.2, 0.13, 0.32)
    g.setScale(s)
    return M
}
/** ink kite drone: a vertical diamond with a glaring eye and a tail of paper bows */
fun Game.mkKite(): EModel {
    val g = Group(); val M = EModel(g)
    val T = doubleArrayOf(0.0, 2.8, 0.0); val R = doubleArrayOf(2.1, 0.5, 0.0); val B = doubleArrayOf(0.0, -3.2, 0.0); val L = doubleArrayOf(-2.1, 0.5, 0.0); val C = doubleArrayOf(0.0, 0.5, -0.6)
    triMesh(M, g, T + C + R + B + C + L, matDS("#2d2a3e"), 0.05)
    triMesh(M, g, R + C + B + L + C + T, matDS("#6b3fa0"), 0.05)
    box(0.12, 6.0, 0.12, "#c9a46a", 0.0, -0.2, 0.05, g, 0.0)
    box(4.2, 0.12, 0.12, "#c9a46a", 0.0, 0.5, 0.05, g, 0.0)
    mk(GEO.sph, "#fbf8ef", 0.0, 0.5, -0.55, g, 0.03).scale.set(0.8, 0.58, 0.3)
    mk(GEO.sph, eyeMat, 0.0, 0.5, -0.78, g, 0.0).scale.set(0.34, 0.34, 0.2)
    val tail = ArrayList<Mesh>()
    for (i in 0 until 6) { val b = box(0.9, 0.5, 0.08, if (i % 2 != 0) "#6b3fa0" else "#d6452f", 0.0, -3.7 - i * 1.2, 0.0, g, 0.02); b.rotation.z = PI / 4; tail.add(b) }
    M.tail = tail
    g.setScale(1.3)
    return M
}
/** ink bomber: a folded flying wing with violet engines and a tail gunner */
fun Game.mkBomber(): EModel {
    val g = Group(); val M = EModel(g)
    val O = arrayOf(doubleArrayOf(0.0, -7.0), doubleArrayOf(14.0, 3.0), doubleArrayOf(9.0, 5.0), doubleArrayOf(5.0, 3.2), doubleArrayOf(0.0, 5.5), doubleArrayOf(-5.0, 3.2), doubleArrayOf(-9.0, 5.0), doubleArrayOf(-14.0, 3.0))
    val top = ArrayList<Double>(); val bot = ArrayList<Double>()
    for (i in O.indices) {
        val a = O[i]; val b = O[(i + 1) % O.size]
        top.addAll(listOf(0.0, 1.4, -0.5, a[0], 0.0, a[1], b[0], 0.0, b[1]))
        bot.addAll(listOf(0.0, -0.9, -0.5, b[0], 0.0, b[1], a[0], 0.0, a[1]))
    }
    triMesh(M, g, top.toDoubleArray(), matDS("#2d2a3e"), 0.07)
    triMesh(M, g, bot.toDoubleArray(), matDS("#6b3fa0"), 0.07)
    for (s in doubleArrayOf(-1.0, 1.0)) {
        val e = mk(GEO.cyl, engMat, s * 3.2, 0.5, 3.4, g, 0.03)
        e.scale.set(1.2, 2.4, 1.2)
        e.rotation.x = PI / 2
        mk(GEO.sph, "#1f1d2b", s * 6, -0.95, 0.2, g, 0.03).scale.set(1.0, 1.0, 1.8)
        val st = box(4.5, 0.06, 0.6, "#d6452f", s * 8.5, 0.02, 2.2, g, 0.0)
        st.rotation.y = -s * 0.55
    }
    mk(GEO.sph, eyeMat, 0.0, 0.9, -3.6, g, 0.03).scale.set(0.9, 0.5, 1.3)
    mk(GEO.sph, "#9a98ae", 0.0, 1.0, 3.4, g, 0.03).setScale(0.75)
    return M
}
/** Mực Khổng Lồ: a giant squid airship — mantle first, tentacles trailing */
fun Game.mkSquid(): EModel {
    val g = Group(); val M = EModel(g); val Bc = "#3b2a5a"; val Lc = "#6b4a8a"; val Dc = "#241a38"
    fun add(geo: Geometry, col: String, x: Double, y: Double, z: Double, sx: Double, sy: Double, sz: Double, th: Double = 0.1): Mesh { val m = mk(geo, col, x, y, z, g, th); m.scale.set(sx, sy, sz); return m }
    add(GEO.sph, Bc, 0.0, 0.0, -14.0, 12.0, 11.0, 30.0, 0.12)
    add(GEO.sph, Lc, 0.0, -4.2, -13.0, 9.6, 7.4, 26.0, 0.0)
    for (s in doubleArrayOf(-1.0, 1.0)) { val f = add(GEO.sph, Lc, s * 12.5, 0.0, -35.0, 11.0, 1.2, 9.0, 0.08); f.rotation.y = s * 0.55 }
    for (k in 0 until 16) {
        val z = RR(-36.0, 6.0); val u = (z + 14) / 30; val rr = sqrt(max(0.0, 1 - u * u)); val a = RR(0.4, 2.7)
        add(GEO.sph, "#8a63b5", cos(a) * 12 * rr * (if (rand() < 0.5) 1 else -1), sin(a) * 11 * rr, z, RR(1.0, 2.2), RR(1.0, 2.2), RR(1.4, 3.0), 0.0)
    }
    add(GEO.sph, Bc, 0.0, -1.0, 14.0, 10.5, 9.5, 9.0, 0.1)
    for (s in doubleArrayOf(-1.0, 1.0)) { add(GEO.sph, "#fbf8ef", s * 8.4, 1.6, 15.0, 3.4, 4.0, 4.0, 0.06); add(GEO.sph, "#1f1d2b", s * 11.2, 1.6, 15.3, 1.5, 2.6, 2.6, 0.0) }
    // blimp engines
    val props = ArrayList<Group>()
    for (s in doubleArrayOf(-1.0, 1.0)) {
        add(GEO.sph, Dc, s * 15.5, -5.0, -4.0, 2.2, 2.2, 4.6, 0.06)
        val pr = Group()
        pr.position.set(s * 15.5, -5.0, 0.9)
        g.add(pr)
        for (k in 0 until 3) { val piv = Group(); piv.rotation.z = (k / 3.0) * PI * 2; pr.add(piv); box(0.7, 6.0, 0.25, "#f2c230", 0.0, 3.0, 0.0, piv, 0.03) }
        props.add(pr)
    }
    // a hanging gondola with the gang's banner
    add(GEO.box, Dc, 0.0, -12.5, -10.0, 5.0, 3.0, 12.0, 0.06)
    for (s in doubleArrayOf(-1.0, 1.0)) box(0.3, 2.6, 0.3, "#1f1d2b", s * 1.8, -10.2, -10.0, g, 0.0)
    // tentacles (instanced spheres + instanced ink outline)
    val NT = 8; val NS = 11
    val data = InstanceData(NT * NS, false)
    val tm = InstancedMesh(GEO.sph, toon(Bc), data); val to = InstancedMesh(GEO.sph, outlineMat(), data)
    tm.frustumCulled = false; to.frustumCulled = false
    tm.castShadow = true
    g.add(tm, to)
    val tent = ArrayList<Tent>()
    for (k in 0 until NT) { val a = (k.toDouble() / NT) * PI * 2; tent.add(Tent(cos(a) * 5, sin(a) * 4 - 3, a, k == 1 || k == 6)) }
    // weak points: six ink sacs, then the ink heart under the head (shielded until the sacs are gone)
    val pods = ArrayList<Mesh>()
    for (p in arrayOf(doubleArrayOf(-11.6, 2.0, -26.0), doubleArrayOf(11.6, 2.0, -26.0), doubleArrayOf(-12.7, -1.0, -12.0), doubleArrayOf(12.7, -1.0, -12.0), doubleArrayOf(-10.9, 1.0, 1.0), doubleArrayOf(10.9, 1.0, 1.0))) {
        val m = mk(GEO.sph, podMat, p[0], p[1], p[2], g, 0.06)
        m.setScale(3.1)
        pods.add(m)
    }
    val core = mk(GEO.sph, coreMat, 0.0, -9.5, 12.0, g, 0.06)
    core.setScale(4.2)
    val shell = mk(GEO.ico1, Dc, 0.0, -9.5, 12.0, g, 0.1)
    shell.setScale(5.8)
    M.squid = SquidParts(props, tm, tent, NT, NS, pods, core, shell)
    g.setScale(1.6)
    return M
}

/* ---------- enemies ---------- */
fun Game.makeEK(): Map<String, EKind> = linkedMapOf(
    "kite" to EKind("Diều mực", 5.0, 3.8, 14.0, 1.2, 100, 0.6) { mkKite() },
    "fighter" to EKind("Phi cơ giấy than", 11.0, 4.4, 50.0, 1.1, 250, 1.0) { mkDart("#2d2a3e", "#8e44ad", "#d6452f", 1.9) },
    "ace" to EKind("Quạ Đen", 55.0, 4.8, 56.0, 1.4, 1500, 1.4) { mkDart("#15131c", "#d6452f", "#f2c230", 2.2) },
    "bomber" to EKind("Oanh tạc cơ mực", 50.0, 10.0, 32.0, 0.42, 600, 1.8) { mkBomber() },
    "boss" to EKind("Mực Khổng Lồ", 1.0, 70.0, 11.0, 0.35, 8000, 3.0) { mkSquid() },
)
fun Game.spawnEnemy(k: String, x: Double, y: Double, z: Double, psi: Double? = null, st: String? = null, anchor: Vec3? = null, lm: MsLm? = null, alt: Double? = null, demo: Boolean = false, ang: Double? = null): Enemy {
    val kd = EK[k]!!; val M = kd.build()
    combat.add(M.g)
    val e = Enemy(k, kd, M, Vec3(x, y, z))
    e.psi = psi ?: RR(-3.0, 3.0)
    e.v = kd.v * (if (k == "kite" || k == "boss") 1.0 else RR(0.95, 1.08))
    e.hp = kd.hp * DF.hpK; e.hpMax = kd.hp * DF.hpK; e.r = kd.r; e.st = st ?: "attack"; e.fireT = RR(1.5, 3.5)
    e.t = rand() * 10; e.ph = rand() * 6; e.anchor = anchor; e.lm = lm; e.alt = alt ?: y; e.minAlt = if (k == "bomber") 60.0 else if (k == "boss") 140.0 else 26.0
    e.demo = demo; e.ang = ang ?: (rand() * 6.28); e.spin = if (rand() < 0.5) -1.0 else 1.0
    e.g.position.copy(e.pos)
    if (k == "boss") initBoss(e)
    EN.add(e)
    return e
}
fun Game.removeEnemy(e: Enemy) {
    combat.remove(e.g)
    EN.remove(e)
    disposeQ.addAll(e.model.own)
    if (PL.target === e) { PL.target = null; PL.lock = 0.0 }
}
fun Game.playerVel(out: Vec3): Vec3 = if (F.c.heli) out.set(F.hv.x, F.vy, F.hv.z) else fwd(F.psi, F.gam, out).multiplyScalar(F.v)
fun leadPoint(src: Vec3, tp: Vec3, tv: Vec3, spd: Double, out: Vec3): Vec3 {
    var t = src.distanceTo(tp) / spd
    out.copy(tp).addScaledVector(tv, t)
    t = src.distanceTo(out) / spd
    return out.copy(tp).addScaledVector(tv, min(t, 3.0))
}
val DEMO = XZ(80.0, -1100.0)
/* steering with terrain look-ahead, a soft ceiling and the edge of the battle area */
fun Game.steerDir(e: Enemy, psiT0: Double, gamT0: Double, dt: Double, turn: Double, gMax: Double = 0.75) {
    var psiT = psiT0; var gamT = gamT0
    val look = max(e.v * 2.4, 45.0); val fx = -sin(e.psi); val fz = -cos(e.psi)
    var top = maxOf(maxOf(gridH(e.pos.x, e.pos.z), gridH(e.pos.x + fx * look * 0.5, e.pos.z + fz * look * 0.5)), gridH(e.pos.x + fx * look, e.pos.z + fz * look), SEA_Y)
    top = max(top, obstacleTop(e.pos.x + fx * look * 0.4, e.pos.z + fz * look * 0.4, 4.0))
    if (e.pos.y < top + e.minAlt) gamT = max(gamT, 0.3 + (top + e.minAlt - e.pos.y) * 0.025)
    if (e.pos.y > 760) gamT = min(gamT, -0.25)
    val C = MS.center ?: DEMO
    if (!e.demo && !(e.tok && e.st != "patrol")) {
        val cx = e.pos.x - C.x; val cz = e.pos.z - C.z; val R = MS.radius * 1.15
        if (cx * cx + cz * cz > R * R) psiT = psiTo(-cx, -cz)
    }
    gamT = clamp(gamT, -gMax, gMax)
    val dpsi = clamp(angDiff(psiT, e.psi), -turn * dt, turn * dt)
    e.psi += dpsi
    e.gam += clamp(gamT - e.gam, -turn * 0.8 * dt, turn * 0.8 * dt)
    e.phi = damp(e.phi, clamp((-dpsi / max(dt, 1e-4) / turn) * 1.1, -1.25, 1.25), 4.0, dt)
}
fun Game.steerTo(e: Enemy, tx: Double, ty: Double, tz: Double, dt: Double, turn: Double, gMax: Double = 0.75) {
    val dx = tx - e.pos.x; val dy = ty - e.pos.y; val dz = tz - e.pos.z
    steerDir(e, psiTo(dx, dz), atan2(dy, max(hypot(dx, dz), 1.0)), dt, turn, gMax)
}
fun Game.moveEnemy(e: Enemy, dt: Double) {
    fwd(e.psi, e.gam, e.vel).multiplyScalar(e.v)
    e.pos.addScaledVector(e.vel, dt)
}
fun Game.enemyShoot(e: Enemy, from: Vec3, aim: Vec3, spread: Double, dmg: Double, blob: Int = 0, spd: Double = EBSPD) {
    _v3.copy(aim).sub(from).normalize()
    _v3.x += RR(-spread, spread); _v3.y += RR(-spread, spread); _v3.z += RR(-spread, spread)
    _v3.normalize().multiplyScalar(spd)
    if (blob == 0) _v3.add(e.vel)
    addBullet(from, _v3, 1, dmg, if (blob != 0) 4.0 else 2.3, blob)
    if (game.t - e.sndT > 0.25) { e.sndT = game.t; sfx.egun(from) }
}
/* fighters: attack (lead pursuit), evade (break turn when someone is on their tail), extend (after a pass) */
fun Game.updateFighter(e: Enemy, dt: Double) {
    val kd = e.kd
    fwd(e.psi, e.gam, _v1)
    if (!canFight()) {
        val C = MS.center ?: DEMO; val R = if (e.demo) 260 + (e.ph % 1) * 90 else 420.0
        e.ang += (dt * e.v) / R
        steerTo(e, C.x + cos(e.ang) * R, e.alt, C.z + sin(e.ang) * R, dt, kd.turn * 0.8, 0.4)
        e.v = damp(e.v, kd.v, 1.0, dt)
        moveEnemy(e, dt)
        return
    }
    e.stT -= dt
    _v2.copy(F.pos).sub(e.pos)
    val dist = _v2.length().let { if (it == 0.0) 1.0 else it }
    _v2.divideScalar(dist)
    val ahead = _v1.dot(_v2)
    val pf = fwd(F.psi, F.gam, _v3)
    val onTail = ahead < -0.3 && -pf.dot(_v2) > 0.8 && dist < 400
    val threat = MIS.any { m -> m.team == 0 && m.tgt?.e === e && m.p.distanceTo(e.pos) < 420 }
    if (e.st == "attack") {
        if (e.stT <= 0 && ((onTail && rand() < dt * 0.45 * DF.aggr) || (threat && rand() < dt * 1.2 * DF.aggr))) { e.st = "evade"; e.stT = RR(1.1, 2.2); e.brk = if (rand() < 0.5) -1.0 else 1.0; e.clb = RR(-0.45, 0.6) }
        else if (dist < 60) { e.st = "extend"; e.stT = RR(1.8, 2.8) }
    } else if (e.stT <= 0) { e.st = "attack"; e.stT = RR(3.0, 5.5) / DF.aggr }
    // head-on: break away instead of ramming
    if (e.st == "attack" && dist < 140 && ahead > 0.75 && pf.dot(_v2) < -0.75) { e.st = "evade"; e.stT = RR(1.2, 2.0); e.brk = if (rand() < 0.5) -1.0 else 1.0; e.clb = 0.4 }
    var spd = kd.v
    if (e.st == "attack" && !e.tok) {
        // waiting for a turn to attack: circle the fight at a distance
        val a = e.ph + game.t * 0.25
        steerTo(e, F.pos.x + cos(a) * 480, clamp(F.pos.y + 25 * sin(a * 2), 110.0, 480.0), F.pos.z + sin(a) * 480, dt, kd.turn * 0.8)
        if (dist > 900) spd *= 1.25
    } else if (e.st == "attack") {
        leadPoint(e.pos, F.pos, playerVel(_v3), EBSPD + e.v, e.aim)
        steerTo(e, e.aim.x, e.aim.y, e.aim.z, dt, kd.turn * (0.85 + 0.15 * DF.aggr))
        if (dist > 700) spd *= 1.3
        _v3.copy(e.aim).sub(e.pos).normalize()
        val off = acos(clamp(_v1.dot(_v3), -1.0, 1.0))
        if (e.burst <= 0) { e.fireT -= dt; if (e.fireT <= 0 && off < 0.1 && dist < 520 && PL.inv <= 0) { e.burst = if (e.k == "ace") 7 else 4; e.fireT = RR(1.1, 2.2) / DF.aggr } }
    } else if (e.st == "evade") {
        steerDir(e, e.psi + e.brk * 1.6, e.clb, dt, kd.turn * 1.3)
        spd *= 1.15
    } else {
        steerDir(e, e.psi, 0.12, dt, kd.turn)
        spd *= 1.1
    }
    e.v = damp(e.v, spd, 1.2, dt)
    moveEnemy(e, dt)
    if (e.burst > 0) {
        e.gunT -= dt
        if (e.gunT <= 0) {
            e.gunT = 0.075
            e.burst--
            _v2.copy(e.pos).addScaledVector(fwd(e.psi, e.gam, _v3), e.r)
            enemyShoot(e, _v2, e.aim, 0.04 / DF.acc, (if (e.k == "ace") 3.5 else 2.5) * DF.dmg)
        }
    }
}
/* kites: bob around their anchor, stare at you and spit ink */
fun Game.updateKite(e: Enemy, dt: Double) {
    e.t += dt
    val A = e.anchor!!; val fight = canFight()
    var tx = A.x + cos(e.t * 0.22 + e.ph) * 45; var ty = A.y + sin(e.t * 0.7 + e.ph) * 7; var tz = A.z + sin(e.t * 0.22 + e.ph) * 45
    var dist = 1e9
    if (fight) {
        dist = e.pos.distanceTo(F.pos)
        if (dist < 520) { val k = min(1.0, 90 / max(dist, 1.0)); tx = lerp(tx, F.pos.x, k * 0.6); tz = lerp(tz, F.pos.z, k * 0.6); ty = max(ty, min(F.pos.y, A.y + 60)) }
    }
    _v1.set(tx, ty, tz).sub(e.pos)
    val d = _v1.length().let { if (it == 0.0) 1.0 else it }
    e.vel.copy(_v1).multiplyScalar(min(e.kd.v, d * 0.9) / d)
    e.pos.addScaledVector(e.vel, dt)
    val fx = if (fight) F.pos.x - e.pos.x else -WIND.dir.x; val fz = if (fight) F.pos.z - e.pos.z else -WIND.dir.z
    e.psi += angDiff(psiTo(fx, fz), e.psi) * (1 - exp(-2.5 * dt))
    e.gam = 0.0
    e.phi = sin(e.t * 1.3 + e.ph) * 0.18
    if (fight && dist < 460) {
        e.fireT -= dt
        if (e.fireT <= 0 && PL.inv <= 0) {
            e.fireT = RR(2.4, 4.2) / DF.aggr
            leadPoint(e.pos, F.pos, playerVel(_v3), 140.0, e.aim)
            enemyShoot(e, e.pos, e.aim, 0.05 / DF.acc, 5 * DF.dmg, 1, 140.0)
        }
    }
    val tail = e.model.tail!!
    for (i in tail.indices) { val b = tail[i]; b.position.x = sin(e.t * 3 + i * 0.7) * 0.22 * (i + 1); b.position.z = 0.25 * (i + 1) }
}
/* bombers: run in on the landmark, drop a stick of ink bombs, swing round, repeat */
fun Game.updateBomber(e: Enemy, dt: Double) {
    val kd = e.kd; val L = e.lm ?: MS.lm
    e.stT -= dt
    if (L == null || !canFight()) {
        val C = MS.center ?: DEMO; val R = if (e.demo) 330.0 else 520.0
        e.ang += (dt * e.v) / R
        steerTo(e, C.x + cos(e.ang) * R, e.alt, C.z + sin(e.ang) * R, dt, kd.turn, 0.3)
        moveEnemy(e, dt)
        return
    }
    val dx = L.x - e.pos.x; val dz = L.z - e.pos.z; val hd = hypot(dx, dz); val fx = -sin(e.psi); val fz = -cos(e.psi)
    if (e.st == "drop") {
        steerDir(e, e.psi, 0.0, dt, kd.turn, 0.3)
        e.dropT -= dt
        if (e.dropT <= 0) {
            e.dropT = 0.3
            _v2.copy(e.pos).add(_v3.set(RR(-3.0, 3.0), -2.0, RR(-1.0, 1.0)))
            addBullet(_v2, _v3.copy(e.vel).setY(-3.0), 1, 7 * DF.dmg, 14.0, 2)
            if (game.t - e.sndT > 0.5) { e.sndT = game.t; sfx.whoosh(e.pos) }
        }
        if (e.stT <= 0) { e.st = "leave"; e.stT = RR(9.0, 13.0); e.lx = L.x + fx * 900 + RR(-200.0, 200.0); e.lz = L.z + fz * 900 + RR(-200.0, 200.0) }
    } else if (e.st == "leave") {
        steerTo(e, e.lx, e.alt, e.lz, dt, kd.turn, 0.3)
        if (e.stT <= 0) e.st = "run"
    } else {
        steerTo(e, L.x, e.alt, L.z, dt, kd.turn, 0.3)
        if (hd < 120 && dx * fx + dz * fz > 0) { e.st = "drop"; e.stT = 2.8; e.dropT = 0.0 }
    }
    e.v = damp(e.v, kd.v, 1.0, dt)
    moveEnemy(e, dt)
    // tail gunner
    _v2.copy(F.pos).sub(e.pos)
    val dist = _v2.length()
    if (dist < 400 && fwd(e.psi, e.gam, _v1).dot(_v2) < dist * 0.25 && PL.inv <= 0) {
        if (e.burst <= 0) { e.fireT -= dt; if (e.fireT <= 0) { e.burst = 4; e.fireT = RR(1.3, 2.4) / DF.aggr } }
    }
    if (e.burst > 0) {
        e.gunT -= dt
        if (e.gunT <= 0) {
            e.gunT = 0.11
            e.burst--
            e.g.updateWorldFromParents()
            e.g.localToWorld(_v2.set(0.0, 1.4, 4.2))
            leadPoint(_v2, F.pos, playerVel(_v3), EBSPD, e.aim)
            enemyShoot(e, _v2, e.aim, 0.05 / DF.acc, 2.5 * DF.dmg)
        }
    }
}

/* ---------- the boss ---------- */
fun Game.initBoss(e: Enemy) {
    val U = e.model.squid!!
    e.sc = e.g.scale.x
    val parts = U.pods.map { m -> BossPart(m, m.position.clone(), 3.6, 40 * DF.hpK, 40 * DF.hpK, true, Vec3(), false) }.toMutableList()
    parts.add(BossPart(U.core, U.core.position.clone(), 5.0, 160 * DF.hpK, 160 * DF.hpK, true, Vec3(), true))
    e.parts = parts
    e.hull = listOf(doubleArrayOf(0.0, 0.0, -30.0, 9.5), doubleArrayOf(0.0, 0.0, -16.0, 12.0), doubleArrayOf(0.0, 0.0, 0.0, 11.0), doubleArrayOf(0.0, -1.0, 14.0, 10.5)).map { HullBall(Vec3(it[0], it[1], it[2]), it[3], Vec3()) }
    e.hp = parts.sumOf { it.hp }; e.hpMax = e.hp
    e.podsLeft = 6
    e.misT = 6.0; e.flakT = 4.0; e.spawnT = 12.0; e.boomT = 0.0
    e.ang = 0.0
    e.alt = 250.0
    e.r = 75.0
    MS.boss = e
}
fun Game.bossParts(e: Enemy) {
    e.g.updateMatrixWorld()
    for (p in e.parts!!) p.wp.copy(p.off).applyMatrix4(e.g.matrixWorld)
    for (h in e.hull) h.wp.copy(h.off).applyMatrix4(e.g.matrixWorld)
}
/** returns true when the segment a→b touched the squid (a part or the hull) */
fun Game.hitBoss(e: Enemy, a: Vec3, b: Vec3, dmg: Double, missile: Boolean): Boolean {
    for (pt in e.parts!!) {
        if (!pt.alive) continue
        val r = pt.r * e.sc + (if (missile) 3.0 else 0.8)
        if (segD2(a, b, pt.wp) < r * r) {
            if (pt.core && e.podsLeft > 0) {
                sparks(b, 4, "#cfc8b4")
                if (game.t - e.ricT > 3) { e.ricT = game.t; toast("Vỏ giáp mực cứng quá — phá 6 túi mực tím trước!") }
                return true
            }
            pt.hp -= dmg; e.hp -= dmg
            PL.hitT = 0.12; MS.hits += if (missile) 0 else 1
            sparks(b, 3)
            sfx.hit()
            if (pt.hp <= 0) breakPart(e, pt)
            return true
        }
    }
    for (h in e.hull) {
        val r = h.r * e.sc + (if (missile) 2.0 else 0.0)
        if (segD2(a, b, h.wp) < r * r) { sparks(b, 2, "#8a63b5"); return true }
    }
    return false
}
private val POD_COLS = listOf("#c04cff", "#5b2a86", "#1f1d2b")
fun Game.breakPart(e: Enemy, pt: BossPart) {
    pt.alive = false
    pt.m.visible = false
    explode(pt.wp, 1.7)
    burst(pt.wp, POD_COLS, 40, 18.0, 2.0, 1.8)
    if (pt.core) { killEnemy(e, pt.wp); return }
    e.podsLeft--
    addScore(800, pt.wp, "TÚI MỰC")
    if (e.podsLeft == 0) {
        val sh = e.model.squid!!.shell
        sh.visible = false
        e.g.updateWorldFromParents()
        e.g.localToWorld(_v2.copy(sh.position))
        explode(_v2, 2.2)
        showTip("❤ Tim mực đã lộ!", "Bắn vào trái tim đỏ dưới đầu con mực", "Mực Khổng Lồ nổi điên — bay nhanh hơn, phun mực dày hơn, gọi cả phi cơ giấy than!", 5200.0)
        sfx.alarm(2)
        e.misT = 2.0; e.spawnT = 3.0
    } else toast("💥 Phá túi mực · còn ${e.podsLeft}/6")
}
fun Game.updateBoss(e: Enemy, dt: Double) {
    val U = e.model.squid!!; val ph2 = e.podsLeft == 0; val C = MS.center ?: DEMO
    e.t += dt
    val R = 420.0
    e.ang += ((if (ph2) 16 else 11) / R) * dt
    val ty = e.alt + sin(e.t * 0.3) * 25
    steerTo(e, C.x + cos(e.ang + 0.35) * R, ty, C.z + sin(e.ang + 0.35) * R, dt, 0.35, 0.2)
    e.v = damp(e.v, if (ph2) 16.0 else 11.0, 0.5, dt)
    moveEnemy(e, dt)
    // animation
    for (pr in U.props) pr.rotation.z += dt * (if (ph2) 14 else 9)
    val pulse = 0.6 + 0.4 * sin(e.t * 4)
    podMat.emissiveIntensity = 0.45 + 0.4 * pulse
    coreMat.emissiveIntensity = if (ph2) 0.6 + 0.6 * pulse else 0.3
    if (ph2) U.core.setScale(4.2 + 0.4 * pulse)
    val tw = if (ph2) 2.6 else 1.6
    for (k in 0 until U.NT) {
        val T = U.tent[k]; val sp = if (T.long) 4.4 else 3.3
        for (i in 0 until U.NS) {
            val s = if (T.long && i == U.NS - 1) 2.2 else (if (T.long) 1.9 else 2.1) - i * 0.14
            _p.set(T.x * (1 + i * 0.06) + cos(e.t * 0.8 * tw + i * 0.5 + T.a * 2) * i * 0.32, T.y + sin(e.t * tw + i * 0.55 + T.a * 3) * i * 0.45 - i * (if (T.long) 0.9 else 0.5), 19 + i * sp)
            _m.compose(_p, _q.identity(), _s.set(s, s, s * 1.5))
            U.tm.data.setMatrixAt(k * U.NS + i, _m)
        }
    }
    U.tm.data.version++
    bossParts(e)
    if (!canFight()) return
    val dist = e.pos.distanceTo(F.pos)
    if (dist > 1600) return
    val parts = e.parts!!
    val live = parts.filter { it.alive && !it.core }
    e.misT -= dt * DF.aggr
    if (e.misT <= 0) {
        e.misT = if (ph2) 4.5 else 7.5
        for (k in 0 until 2) {
            val from = if (live.isNotEmpty()) pick(live).wp else parts[6].wp
            _v2.copy(F.pos).sub(from).normalize()
            launchMissile(1, from, _v2, 30.0, MTgt(player = true))
        }
        toast("⚠ Mực Khổng Lồ phóng tên lửa mực!")
    }
    e.flakT -= dt * DF.aggr
    if (e.flakT <= 0 && dist < 600) {
        e.flakT = if (ph2) 2.3 else 3.6
        val from = parts[6].wp
        leadPoint(from, F.pos, playerVel(_v3), 130.0, e.aim)
        for (k in 0 until (if (ph2) 12 else 8)) enemyShoot(e, from, e.aim, 0.13, 7 * DF.dmg, 1, 130.0)
    }
    e.spawnT -= dt
    if (e.spawnT <= 0) {
        e.spawnT = if (ph2) 15.0 else 19.0
        val kites = EN.count { it.alive && it.k == "kite" }; val ftr = EN.count { it.alive && it.k == "fighter" }
        var k = 0
        while (k < 2 && kites + k < 6) { val x = e.pos.x + RR(-60.0, 60.0); val z = e.pos.z + RR(-60.0, 60.0); val y = e.pos.y - 40; spawnEnemy("kite", x, y, z, anchor = Vec3(x, max(gridH(x, z), 0.0) + RR(90.0, 160.0), z)); k++ }
        if (ph2 && ftr < 3) spawnEnemy("fighter", e.pos.x, e.pos.y - 30, e.pos.z, psi = e.psi)
    }
}
private val BOSS_SPRAY = listOf("#ffffff", "#e0f6ff", "#bfe8ff", "#3b2a5a")
fun Game.updateBossDying(e: Enemy, dt: Double) {
    e.dying -= dt
    e.gam = damp(e.gam, -0.3, 0.6, dt)
    e.v = damp(e.v, 6.0, 0.5, dt)
    moveEnemy(e, dt)
    e.pos.y -= dt * 12
    bossParts(e)
    e.boomT -= dt
    if (e.boomT <= 0) {
        e.boomT = RR(0.12, 0.3)
        val h = pick(e.hull)
        _v2.copy(h.wp).add(_v3.set(RR(-1.0, 1.0), RR(-1.0, 1.0), RR(-1.0, 1.0)).multiplyScalar(h.r * e.sc))
        explode(_v2, RR(1.2, 2.2))
    }
    val gnd = max(gridH(e.pos.x, e.pos.z), SEA_Y)
    if (e.pos.y < gnd + 10 || e.dying <= 0) {
        explode(e.pos, 4.0)
        for (k in 0 until 160) emit(PB, _v2.set(e.pos.x + RR(-40.0, 40.0), SEA_Y + 0.5, e.pos.z + RR(-40.0, 40.0)), _v3.set(RR(-8.0, 8.0), RR(10.0, 30.0), RR(-8.0, 8.0)), pick(BOSS_SPRAY), RR(1.0, 2.4), -14.0, 0.5)
        inkSplat(e.pos.x, e.pos.z, 26.0)
        celebrate(e.pos)
        removeEnemy(e)
        MS.boss = null
    }
}
private val CELEB_COLS = listOf("#ff4d6d", "#ffd35a", "#5ad1ff", "#7dff8a", "#f37021", "#ffffff", "#1f5fbf")
fun Game.celebrate(p: Vec3) {
    for (k in 0 until 9)
        FW.shells.add(Shell(Vec3(p.x + RR(-160.0, 160.0), 2.0, p.z + RR(-160.0, 160.0)), Vec3(RR(-3.0, 3.0), RR(60.0, 78.0), RR(-3.0, 3.0)), RR(1.4, 3.2), floor(rand() * 4).toInt(), pick(CELEB_COLS)))
}

/* ---------- per-frame driver for the enemies ---------- */
private val WRECK_FIRE = listOf("#ff8a2a", "#ffd35a")
private val SEA_SPRAY = listOf("#ffffff", "#e0f6ff", "#bfe8ff")
fun Game.updateEnemies(dt: Double) {
    // attack tokens: only the nearest few fighters press the attack at once
    if (canFight()) {
        val f = EN.filter { it.alive && !it.demo && (it.k == "fighter" || it.k == "ace") }.toMutableList()
        f.sortWith { a, b -> sign(a.pos.distanceToSquared(F.pos) - b.pos.distanceToSquared(F.pos) - (if (a.k == "ace") 1e7 else 0.0) + (if (b.k == "ace") 1e7 else 0.0)).toInt() }
        f.forEachIndexed { i, e -> e.tok = i < DF.slots }
    }
    var i = EN.size - 1
    while (i >= 0) {
        if (i >= EN.size) { i--; continue }
        val e = EN[i]
        i--
        if (e.alive) {
            if (e.k == "kite") updateKite(e, dt)
            else if (e.k == "bomber") updateBomber(e, dt)
            else if (e.k == "boss") updateBoss(e, dt)
            else updateFighter(e, dt)
            if (e.k != "kite" && e.k != "boss") {
                val gnd = max(gridH(e.pos.x, e.pos.z), SEA_Y)
                if (e.pos.y < gnd + 1.5 || hitCollider(e.pos.x, e.pos.y, e.pos.z, 1.0) != null) {
                    if (canFight()) { MS.kills++; addScore(Math.round(e.kd.score * 0.5).toInt(), e.pos, "ĐÂM NÚI") }
                    explode(e.pos, e.kd.boom)
                    removeEnemy(e)
                    continue
                }
            }
            if (e.hp < e.hpMax * 0.5 && e.k != "boss") { e.smokeT -= dt; if (e.smokeT <= 0) { e.smokeT = 0.06; emit(PS, e.pos, _v3.set(RR(-1.0, 1.0), RR(0.0, 2.0), RR(-1.0, 1.0)), "#4a4658", RR(1.0, 1.8), 0.8, 1.0) } }
        } else if (e.k == "boss") {
            updateBossDying(e, dt)
            if (!EN.contains(e)) continue
        } else {
            // tumbling wreck trailing fire and smoke
            e.dying -= dt
            e.gam = max(-1.1, e.gam - dt * 0.7)
            e.psi += dt * 0.9 * e.spin
            e.phi += dt * 5 * e.spin
            moveEnemy(e, dt)
            e.pos.y -= dt * 6
            e.smokeT -= dt
            if (e.smokeT <= 0) { e.smokeT = 0.03; emit(PS, e.pos, _v3.set(RR(-1.0, 1.0), RR(0.0, 2.0), RR(-1.0, 1.0)), "#3a3848", RR(1.4, 2.4), 1.0, 0.8); emit(PF, e.pos, _v3.set(RR(-2.0, 2.0), RR(-1.0, 2.0), RR(-2.0, 2.0)), pick(WRECK_FIRE), 0.4, 1.0, 2.0) }
            val gnd = max(gridH(e.pos.x, e.pos.z), SEA_Y)
            if (e.pos.y < gnd + 1 || e.dying <= 0 || hitCollider(e.pos.x, e.pos.y, e.pos.z, 0.5) != null) {
                explode(e.pos, e.kd.boom * 0.8)
                if (gnd <= SEA_Y) for (k in 0 until 40) emit(PB, _v2.set(e.pos.x + RR(-3.0, 3.0), SEA_Y + 0.3, e.pos.z + RR(-3.0, 3.0)), _v3.set(RR(-5.0, 5.0), RR(6.0, 16.0), RR(-5.0, 5.0)), pick(SEA_SPRAY), RR(0.7, 1.4), -16.0, 0.6)
                else inkSplat(e.pos.x, e.pos.z, if (e.k == "bomber") 9.0 else 5.0)
                removeEnemy(e)
                continue
            }
        }
        e.g.position.copy(e.pos)
        if (e.k == "boss") e.g.rotation.set(e.gam * 0.5, e.psi, -e.phi * 0.25, Euler.YXZ)
        else e.g.rotation.set(e.gam, e.psi, -e.phi, Euler.YXZ)
    }
}
