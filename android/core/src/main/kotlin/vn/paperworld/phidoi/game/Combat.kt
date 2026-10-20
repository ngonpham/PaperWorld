package vn.paperworld.phidoi.game

import vn.paperworld.phidoi.engine.*
import vn.paperworld.phidoi.world.*
import kotlin.math.*

/* =========================================================
   COMBAT — Băng Mực Đen: diều mực, phi cơ giấy than, oanh tạc cơ mực và Mực Khổng Lồ
   (web: the COMBAT section; state objects keep the web's names and fields)
   ========================================================= */
class Diff(val name: String, val dmg: Double, val acc: Double, val aggr: Double, val hpK: Double, val regen: Double, val drop: Double, val slots: Int)
val DIFF = listOf(
    Diff("DỄ", 0.5, 0.6, 0.7, 0.8, 1.5, 1.4, 1),
    Diff("THƯỜNG", 1.0, 1.0, 1.0, 1.0, 0.0, 1.0, 2),
    Diff("KHÓ", 1.45, 1.35, 1.3, 1.25, 0.0, 0.75, 3),
)
const val BSPD = 430.0
const val EBSPD = 250.0
const val BMAX = 900

/** a point on the map (web: { x, z }) */
class XZ(val x: Double, val z: Double)

/** the player's guns, missiles, flares and spare planes (web: PL) */
class PlayerState {
    var hp = 100.0; var hpMax = 100.0; var heat = 0.0; var over = false; var gunT = 0.0; var gunI = 0; var missiles = 6; var flares = 8; var flareCd = 0.0; var misCd = 0.0; var misSide = 1.0
    var target: Enemy? = null; var tpart: BossPart? = null; var manualT = -99.0; var pickT = 0.0; var lock = 0.0; var lives = 3; var inv = 0.0; var hitT = 0.0; val hurtDir = Vec3(); var hurtT = 0.0
    var calmT = 0.0; val aim = Vec3(0.0, 0.0, -1.0); var incoming = 0.0; var repairing = false; var toneT = 0.0; var padT = 0.0; var bumpT = 0.0
}

/** the landmark a mission protects (web: MS.lm) */
class MsLm(val name: String, val x: Double, val y: Double, val z: Double, val r: Double) { var hp = 100.0; var hpMax = 100.0 }

/** the running mission (web: MS) */
class MissionState {
    var def: MissionDef? = null; var state = "idle"; var wave = -1; var t = 0.0; var stT = 0.0; var kills = 0; var shots = 0; var hits = 0; var lost = 0; var score = 0; var combo = 0; var comboT = 0.0
    var center: XZ? = null; var radius = 1600.0; var lm: MsLm? = null; var lmWarnT = 0.0; var supplyT = 30.0; var survival = false; var practice = false; var endT = 0.0; var waves = 0
    var outside = false; var won = false; var reason = ""; var civT = 0.0; var bonus = 0; var pracT = 0.0; var boss: Enemy? = null
}

class Bullet(val p: Vec3, val prev: Vec3, val v: Vec3, val team: Int, val dmg: Double, var life: Double, val blob: Int, val g: Double)
class Civ(val p: Vec3, val r: Double, val name: String)
/** floating score texts (web: FT) */
class FloatText(val p: Vec3, val txt: String, val col: String, var t: Double)
class Flare(val p: Vec3, val v: Vec3, var life: Double)
/** what a missile chases (web: { e, part } | { player: true } | { flare }) */
class MTgt(val e: Enemy? = null, val part: BossPart? = null, val player: Boolean = false, val flare: Flare? = null)
class Missile(val g: Group, val team: Int, val p: Vec3, val dir: Vec3, var spd: Double, val max: Double, val acc: Double, val turn: Double, var life: Double, var tgt: MTgt?) {
    var smokeT = 0.0; var arm = 0.3; val prev = Vec3()
}
class PkInfo(val icon: String, val col: String, val name: String)
val PKI = linkedMapOf("repair" to PkInfo("🔧", "#1f9a8a", "Sửa máy bay"), "missile" to PkInfo("🚀", "#d6452f", "Tên lửa"), "flare" to PkInfo("✦", "#f2c230", "Pháo sáng"))
class Pickup(val g: Group, val kind: String, val chute: Group, val halo: Mesh, val haloMat: BasicMaterial, var life: Double, var landed: Boolean, val ph: Double) { val p get() = g.position }
/** the delivery helicopter (web: DLV) */
class Delivery {
    var heli: Heli? = null; var g: Group? = null; var crate: Group? = null; var active = false; var st = ""; var p = Vec3(); val drop = Vec3(); val dir = Vec3(); val vel = Vec3(); var psi = 0.0; var kind = "repair"; var t = 0.0
}

/* ---------- PROGRESS (saved on this device; web: SAVE in localStorage "pdgdn-save") ---------- */
class MSave(val stars: Int, val score: Int)
class SaveData { val m = LinkedHashMap<String, MSave>(); var survBest = 0; var survWave = 0 }
/** small "k=v;k=v" encoding: survBest=…;survWave=…;m=m1:3:4500,m2:1:900 */
fun loadSave(st: Store): SaveData {
    val s = SaveData()
    val raw = st.getString("save") ?: return s
    try {
        for (kv in raw.split(";")) {
            val i = kv.indexOf('='); if (i < 0) continue
            val k = kv.substring(0, i); val v = kv.substring(i + 1)
            when (k) {
                "survBest" -> s.survBest = v.toIntOrNull() ?: 0
                "survWave" -> s.survWave = v.toIntOrNull() ?: 0
                "m" -> for (e in v.split(",")) { val a = e.split(":"); if (a.size == 3) s.m[a[0]] = MSave(a[1].toIntOrNull() ?: 0, a[2].toIntOrNull() ?: 0) }
            }
        }
    } catch (_: Exception) {}
    return s
}
fun Game.persist() {
    store.putString("save", "survBest=${SAVE.survBest};survWave=${SAVE.survWave};m=" + SAVE.m.entries.joinToString(",") { "${it.key}:${it.value.stars}:${it.value.score}" })
}

/** the menu selection (web: PICK) */
class Pick(st: Store) {
    var craft = st.getString("craft")?.takeIf { CRAFT.containsKey(it) } ?: "jet"
    var mission = st.getString("mission") ?: "m1"
    var diff = st.getInt("diff", 1).let { if (it in DIFF.indices) it else 1 }
}

fun Game.canFight() = game.started && F.mode != "crash" && (MS.state == "fight" || MS.state == "between")

/* ---------- bullets: tracers, ink blobs and ink bombs ---------- */
fun Game.makeTRC(): InstancedMesh {
    val m = InstancedMesh(Geo.box(1.0, 1.0, 1.0), BasicMaterial("#ffffff"), InstanceData(BMAX, true))
    m.frustumCulled = false
    m.data.count = 0
    combat.add(m)
    return m
}
fun Game.makeBLB(): InstancedMesh {
    val m = InstancedMesh(GEO.ico, BasicMaterial("#ffffff"), InstanceData(300, true))
    m.frustumCulled = false
    m.data.count = 0
    combat.add(m)
    return m
}
private val COL_P = Color.of("#ffd35a"); private val COL_E = Color.of("#c04cff"); private val COL_BLOB = Color.of("#5b2a86"); private val COL_BOMB = Color.of("#1f1d2b")
fun Game.addBullet(p: Vec3, v: Vec3, team: Int, dmg: Double, life: Double, blob: Int = 0): Bullet? {
    if (BUL.size >= BMAX) return null
    val b = Bullet(p.clone(), p.clone(), v.clone(), team, dmg, life, blob, if (blob == 2) 24.0 else 0.0)
    BUL.add(b)
    return b
}
private val _bq = Quat(); private val _bz = Vec3(0.0, 0.0, 1.0); private val _bd = Vec3(); private val _bs = Vec3()
fun Game.drawBullets() {
    var nt = 0; var nb = 0
    for (b in BUL) {
        if (b.blob != 0) {
            if (nb >= 300) continue
            val s = if (b.blob == 2) 1.6 else 1.3
            _m.compose(b.p, _q.setFromEuler(_e.set(b.life * 3, b.life * 2, 0.0)) /* keeps _e's current order, as three's Euler.set does */, _bs.set(s, s, s))
            BLB.data.setMatrixAt(nb, _m)
            BLB.data.setColorAt(nb, if (b.blob == 2) COL_BOMB else COL_BLOB)
            nb++
        } else {
            val sp = b.v.length()
            _bd.copy(b.v).divideScalar(if (sp == 0.0) 1.0 else sp)
            val len = min(sp * 0.02, 9.0); val w = if (b.team != 0) 0.5 else 0.34
            _p.copy(b.p).addScaledVector(_bd, -len / 2)
            _m.compose(_p, _bq.setFromUnitVectors(_bz, _bd), _bs.set(w, w, len))
            TRC.data.setMatrixAt(nt, _m)
            TRC.data.setColorAt(nt, if (b.team != 0) COL_E else COL_P)
            nt++
        }
    }
    TRC.data.count = nt
    BLB.data.count = nb
    TRC.data.version++; TRC.data.colorVersion++
    BLB.data.version++; BLB.data.colorVersion++
}
/** squared distance from point c to the segment a→b */
fun segD2(a: Vec3, b: Vec3, c: Vec3): Double {
    val abx = b.x - a.x; val aby = b.y - a.y; val abz = b.z - a.z; val l2 = abx * abx + aby * aby + abz * abz
    var t = if (l2 > 0) ((c.x - a.x) * abx + (c.y - a.y) * aby + (c.z - a.z) * abz) / l2 else 0.0
    t = if (t < 0) 0.0 else if (t > 1) 1.0 else t
    val dx = a.x + abx * t - c.x; val dy = a.y + aby * t - c.y; val dz = a.z + abz * t - c.z
    return dx * dx + dy * dy + dz * dz
}
/** people who must not be shot: airliners, paragliders, parasails, Ahamove helicopters */
fun Game.civList(): ArrayList<Civ> {
    CIV.clear()
    for (J in JETS) if (J.g.visible) CIV.add(Civ(J.pos, 13.0, "máy bay chở khách"))
    for (H in AIHELI) CIV.add(Civ(H.pos, 6.0, "trực thăng Ahamove"))
    for (p in PILOTS) CIV.add(Civ(p.pos, 6.0, "phi công dù lượn"))
    for (P in PARAS) CIV.add(Civ(P.pos, 6.0, "dù kéo cano"))
    if (DLV.active) CIV.add(Civ(DLV.p, 7.0, "trực thăng tiếp tế Ahamove"))
    return CIV
}
fun Game.sparks(p: Vec3, n: Int = 3, col: String = "#ffd35a") {
    for (k in 0 until n) emit(PF, p, _v3.set(RR(-1.0, 1.0), RR(-0.5, 1.5), RR(-1.0, 1.0)).multiplyScalar(RR(4.0, 12.0)), if (rand() < 0.3) "#ffffff" else col, RR(0.15, 0.35), -10.0, 2.0)
}
private val INK_COLS = listOf("#1f1d2b", "#5b2a86", "#3b2a5a")
fun Game.bulletHits(b: Bullet): Boolean {
    if (b.team == 0) {
        for (i in EN.indices) {
            val e = EN[i]
            if (!e.alive) continue
            if (e.parts != null) { if (hitBoss(e, b.prev, b.p, b.dmg, false)) return true; continue }
            val r = e.r + 0.6
            if (segD2(b.prev, b.p, e.pos) < r * r) { damageEnemy(e, b.dmg, b.p); return true }
        }
        for (c in CIV) if (segD2(b.prev, b.p, c.p) < c.r * c.r) { hitCivilian(c, b.p); return true }
    } else if (game.started && F.mode != "crash") {
        val r = F.c.radius + (if (b.blob == 2) 2.5 else 1.0)
        if (segD2(b.prev, b.p, F.pos) < r * r) {
            hurtPlayer(b.dmg, _v1.copy(b.v).negate())
            sparks(b.p, 4, "#c04cff")
            if (b.blob != 0) burst(b.p, INK_COLS, 10, 6.0, 1.0, 0.8)
            return true
        }
    }
    return false
}
fun Game.updateBullets(dt: Double) {
    civList()
    var i = BUL.size - 1
    while (i >= 0) {
        val b = BUL[i]
        b.prev.copy(b.p)
        if (b.g != 0.0) b.v.y -= b.g * dt
        b.p.addScaledVector(b.v, dt)
        b.life -= dt
        var dead = b.life <= 0
        if (!dead) dead = bulletHits(b)
        if (!dead) {
            val gh = gridH(b.p.x, b.p.z)
            if (b.p.y < max(gh, SEA_Y)) {
                dead = true
                if (b.blob == 2) bombHit(b.p)
                else if (gh < SEA_Y) emit(PB, _v2.set(b.p.x, SEA_Y + 0.2, b.p.z), _v3.set(RR(-1.0, 1.0), RR(3.0, 7.0), RR(-1.0, 1.0)), "#ffffff", 0.6, -14.0, 1.0)
                else if (b.blob != 0) inkSplat(b.p.x, b.p.z, RR(1.5, 2.5))
                else emit(PB, _v2.set(b.p.x, gh + 0.3, b.p.z), _v3.set(RR(-2.0, 2.0), RR(2.0, 5.0), RR(-2.0, 2.0)), "#d8c8a0", 0.5, -12.0, 1.0)
            } else if (hitCollider(b.p.x, b.p.y, b.p.z, 0.0) != null) {
                dead = true
                if (b.blob == 2) bombHit(b.p)
                else sparks(b.p, 2, if (b.team != 0) "#c04cff" else "#ffd35a")
            }
        }
        if (dead) { BUL[i] = BUL[BUL.size - 1]; BUL.removeAt(BUL.size - 1) }
        i--
    }
}

/* ---------- ink stains on the ground ---------- */
private val SPLAT_COLS = listOf("#1f1d2b", "#3b2a5a", "#5b2a86")
fun Game.inkSplat(x: Double, z: Double, s: Double) {
    val gh = heightAt(x, z); val top = obstacleTop(x, z, 0.0)
    burst(_v2.set(x, max(gh, SEA_Y) + 1, z), SPLAT_COLS, Math.round(6 + s * 2).toInt(), 5 + s, 3.0, 1.0)
    if (gh < SEA_Y - 0.3 && top < SEA_Y) return
    val m = Mesh(inkGeo, inkMat)
    m.position.set(x, max(gh, top) + 0.35, z)
    m.scale.set(s, 1.0, s * RR(0.6, 1.0))
    m.rotation.y = rand() * 6.28
    m.renderOrder = 1.0
    combat.add(m)
    INKD.add(m)
    if (INKD.size > 90) combat.remove(INKD.removeAt(0))
}
fun Game.bombHit(p: Vec3) {
    inkSplat(p.x, p.z, RR(7.0, 11.0))
    emit(PF, p, _v3.set(0.0, 6.0, 0.0), "#c04cff", 0.5, 0.0, 2.0)
    sfx.explode(p, 0.6)
    val L = MS.lm
    if (L == null || L.hp <= 0 || MS.state == "win") return
    if (hypot(p.x - L.x, p.z - L.z) < L.r) {
        L.hp = max(0.0, L.hp - 5 * DF.dmg)
        if (game.t - MS.lmWarnT > 2.5) { MS.lmWarnT = game.t; toast("⚠ ${L.name} bị bôi mực! Còn ${ceil(L.hp).toInt()}%"); sfx.alarm(1) }
    }
}

/* ---------- explosions ---------- */
private val FIRE_COLS = listOf("#ffd35a", "#ff8a2a", "#ff5a20", "#ffe9a0")
private val SMOKE_COLS = listOf("#6d6a7a", "#4a4658", "#8a8796", "#3a3848")
private val INKY_COLS = listOf("#1f1d2b", "#3b2a5a", "#6b3fa0", "#fbf8ef")
private val PAPER_COLS = listOf("#fbf8ef", "#f37021", "#1f5fbf")
fun Game.explode(p: Vec3, s: Double = 1.0, inky: Boolean = true) {
    val n = Math.round(22 * s).toInt()
    for (k in 0 until n) emit(PF, p, _v3.set(RR(-1.0, 1.0), RR(-1.0, 1.0), RR(-1.0, 1.0)).normalize().multiplyScalar(RR(4.0, 16.0) * s), pick(FIRE_COLS), RR(0.35, 0.8), 2.0, 2.4)
    var k = 0
    while (k < n * 0.8) { emit(PS, p, _v3.set(RR(-1.0, 1.0), RR(-0.6, 1.0), RR(-1.0, 1.0)).normalize().multiplyScalar(RR(1.5, 6.0) * s), pick(SMOKE_COLS), RR(1.2, 2.6), 1.6, 1.3); k++ }
    burst(p, if (inky) INKY_COLS else PAPER_COLS, Math.round(20 * s).toInt(), 13 * s, 4.0, 1.5)
    sfx.explode(p, s)
    val d = camera.position.distanceTo(p)
    game.shake = max(game.shake, clamp(1 - d / 260, 0.0, 1.0) * 0.55 * s)
}

/* ---------- damage, kills, score ---------- */
fun Game.addScore(pts: Int, p: Vec3, label: String = "") {
    MS.score = max(0, MS.score + pts)
    FT.add(FloatText(p.clone(), (if (pts >= 0) "+" else "") + pts + (if (label.isNotEmpty()) " $label" else ""), if (pts >= 0) "#f37021" else "#d6452f", 1.6))
}
fun Game.damageEnemy(e: Enemy, d: Double, at: Vec3) {
    e.hp -= d
    PL.hitT = 0.12
    MS.hits++
    sparks(at, 3)
    sfx.hit()
    if (e.hp <= 0) killEnemy(e, at)
}
private val KITE_COLS = listOf("#2d2a3e", "#6b3fa0", "#d6452f", "#c9a46a")
private val PK_KINDS = listOf("repair", "missile", "flare")
fun Game.killEnemy(e: Enemy, at: Vec3) {
    if (!e.alive) return
    e.alive = false
    e.dying = if (e.k == "boss") 7.0 else if (e.k == "kite") 0.0 else RR(2.5, 4.0)
    MS.kills++
    MS.combo = if (MS.comboT > 0) MS.combo + 1 else 1
    MS.comboT = 4.5
    val mult = min(MS.combo, 5); val low = F.mode == "fly" && F.agl < 25
    addScore(Math.round(e.kd.score * mult * (if (low) 1.2 else 1.0)).toInt(), e.pos, if (mult > 1) "×$mult" else if (low) "BAY SÁT!" else "")
    explode(e.pos, e.kd.boom)
    if (e.k == "kite") { burst(e.pos, KITE_COLS, 30, 10.0, 2.0, 1.6); removeEnemy(e) }
    if (e.k == "boss") { showTitle(Region("", "Mực Khổng Lồ đã gục!", "勝", "Bầu trời Đà Nẵng lại trong xanh")); sfx.fanfare() }
    if (e.k == "ace") toast("★ Hạ gục quái kiệt Quạ Đen!")
    if (PL.target === e) { PL.target = null; PL.lock = 0.0 }
    if (e.k != "boss" && rand() < 0.12 * DF.drop && !MS.practice) spawnPickup(pick(PK_KINDS), e.pos.x, e.pos.y + 10, e.pos.z)
    val left = EN.count { it.alive && !it.demo }
    val d = MS.def
    if (left == 0 && !MS.survival && !MS.practice && d != null && MS.wave >= (d.waves?.size ?: 0) - 1) game.slow = 1.2
}
fun Game.hitCivilian(c: Civ, at: Vec3) {
    sparks(at, 4, "#ffffff")
    if (game.t - MS.civT < 1) return
    MS.civT = game.t
    addScore(-150, at, "BẮN NHẦM!")
    toast("⚠ Bắn nhầm " + c.name + "! −150")
    sfx.alarm(1)
}
private val DOWN_MSG = listOf("Cánh giấy bốc cháy!", "Trúng đạn mực — rơi rồi!", "Thủng cánh, mất lái!")
fun Game.hurtPlayer(d: Double, fromDir: Vec3) {
    if (PL.inv > 0 || F.mode == "crash" || !game.started || MS.state == "win" || MS.state == "done") return
    PL.hp -= d
    PL.calmT = 0.0
    game.hurt = min(1.0, game.hurt + d / 30)
    game.shake = max(game.shake, min(0.6, d / 40))
    PL.hurtDir.copy(fromDir).normalize()
    PL.hurtT = 1.0
    sfx.hurt()
    if (PL.hp <= 0) {
        PL.hp = 0.0
        explode(F.pos, 1.3, false)
        crash(pick(DOWN_MSG), true)
    }
}
/** lose a spare plane; out of spares → the mission fails */
fun Game.playerDown(msg: String) {
    if (MS.def == null || MS.state == "win" || MS.state == "done" || MS.state == "fail") return
    MS.lost++
    if (MS.practice) { showTip("✂ Rơi rồi!", msg, "Bia tập không giới hạn máy bay — đang gấp chiếc mới…", 2200.0); return }
    PL.lives--
    if (PL.lives < 0) { PL.lives = 0; failMission("Hết máy bay dự phòng"); showTip("✂ Rơi chiếc cuối cùng", msg, "Băng Mực Đen đã làm chủ bầu trời…", 3000.0) }
    else showTip("✂ Bị hạ!", msg, "Đang gấp máy bay dự phòng… còn ${PL.lives} chiếc", 2400.0)
}

/* ---------- missiles (both sides) ---------- */
fun Game.mkMissile(team: Int): Group {
    val g = Group()
    val b = mk(GEO.cyl, if (team != 0) "#2d2a3e" else "#fbf8ef", 0.0, 0.0, 0.0, g, 0.03)
    b.scale.set(0.55, 3.2, 0.55)
    b.rotation.x = PI / 2
    val tip = mk(GEO.cone6, if (team != 0) "#d6452f" else "#f37021", 0.0, 0.0, -2.1, g, 0.02)
    tip.scale.set(0.3, 1.0, 0.3)
    tip.rotation.x = -PI / 2
    for (k in 0 until 4) { val f = box(0.06, 0.8, 0.7, if (team != 0) "#6b3fa0" else "#1f5fbf", sin((k * PI) / 2) * 0.35, cos((k * PI) / 2) * 0.35, 1.3, g, 0.0); f.rotation.z = (k * PI) / 2 }
    val fl = Mesh(GEO.cone6, flameMat)
    fl.scale.set(0.35, 1.8, 0.35)
    fl.rotation.x = PI / 2
    fl.position.z = 2.5
    g.add(fl)
    g.setScale(if (team != 0) 1.4 else 1.0)
    g.useQuat = true
    combat.add(g)
    return g
}
fun Game.launchMissile(team: Int, from: Vec3, dir: Vec3, speed0: Double, tgt: MTgt?): Missile {
    val m = Missile(mkMissile(team), team, from.clone(), dir.clone().normalize(), speed0, if (team != 0) 100.0 else 175.0, if (team != 0) 55.0 else 120.0, if (team != 0) 1.45 else 3.1, if (team != 0) 9.0 else 6.0, tgt)
    MIS.add(m)
    sfx.missile(from)
    return m
}
fun Game.tgtPos(tg: MTgt?): Vec3? {
    if (tg == null) return null
    if (tg.player) return if (game.started && F.mode != "crash") F.pos else null
    tg.flare?.let { return if (it.life > 0) it.p else null }
    val e = tg.e ?: return null
    if (!e.alive) return null
    val pt = tg.part
    return if (pt != null) (if (pt.alive) pt.wp else null) else e.pos
}
private val _md = Vec3(); private val _ma = Vec3(); private val _bzn = Vec3(0.0, 0.0, -1.0)
fun Game.updateMissiles(dt: Double) {
    PL.incoming = 0.0
    var i = MIS.size - 1
    while (i >= 0) {
        val m = MIS[i]
        m.life -= dt
        m.arm -= dt
        m.spd = min(m.max, m.spd + m.acc * dt)
        val tp = tgtPos(m.tgt)
        if (tp != null) {
            val tg = m.tgt!!
            val d = m.p.distanceTo(tp)
            _md.copy(tp)
            if (tg.player) _md.addScaledVector(playerVel(_v3), min(1.5, d / m.spd) * 0.7)
            else if (tg.e != null && tg.part == null) _md.addScaledVector(tg.e.vel, min(1.2, d / m.spd) * 0.8)
            _md.sub(m.p).normalize()
            val ang = acos(clamp(m.dir.dot(_md), -1.0, 1.0))
            if (ang > (if (m.team != 0) 1.9 else 2.2)) m.tgt = null // overshot: lost the lock
            else if (ang > 1e-4) {
                _ma.crossVectors(m.dir, _md)
                if (_ma.lengthSq() > 1e-10) m.dir.applyAxisAngle(_ma.normalize(), min(ang, m.turn * dt))
            }
            val t2 = m.tgt
            if (t2 != null && t2.player && d < 1200) PL.incoming = max(PL.incoming, 1 - d / 1200 + 0.2)
        }
        m.prev.copy(m.p)
        m.p.addScaledVector(m.dir, m.spd * dt)
        m.g.position.copy(m.p)
        m.g.quaternion.setFromUnitVectors(_bzn, m.dir)
        m.smokeT -= dt
        if (m.smokeT <= 0) {
            m.smokeT = 0.03
            emit(PS, _v2.copy(m.p).addScaledVector(m.dir, -2.6), _v3.set(RR(-0.6, 0.6), RR(0.0, 0.8), RR(-0.6, 0.6)), if (m.team != 0) "#6d6a7a" else "#ece8dc", RR(0.9, 1.6), 0.5, 1.6)
        }
        var boom = false
        val mt = m.tgt
        if (m.team == 0) {
            for (j in EN.indices) {
                val e = EN[j]
                if (!e.alive) continue
                val parts = e.parts
                if (parts != null) {
                    if (hitBoss(e, m.prev, m.p, 0.0, true)) {
                        val tpart = mt?.part
                        val pt = if (tpart != null && tpart.alive) tpart else parts.find { it.alive && !it.core && it.wp.distanceTo(m.p) < it.r * e.sc + 6 }
                        if (pt != null && !(pt.core && e.podsLeft > 0)) { pt.hp -= 35; e.hp -= 35; if (pt.hp <= 0) breakPart(e, pt) }
                        boom = true
                        break
                    }
                } else if (segD2(m.prev, m.p, e.pos) < (e.r + 4).pow(2)) { damageEnemy(e, 40.0, m.p); boom = true; break }
            }
        } else if (mt?.flare != null) {
            if (segD2(m.prev, m.p, mt.flare.p) < 81) boom = true
        } else if (game.started && F.mode != "crash" && m.arm <= 0 && segD2(m.prev, m.p, F.pos) < (F.c.radius + 3.5).pow(2)) {
            hurtPlayer(32 * DF.dmg, _v3.copy(m.dir).negate())
            boom = true
        }
        if (!boom && (m.p.y < max(gridH(m.p.x, m.p.z), SEA_Y) || hitCollider(m.p.x, m.p.y, m.p.z, 0.0) != null)) boom = true
        if (!boom && m.life <= 0) boom = true
        if (boom) {
            explode(m.p, if (m.team != 0) 0.9 else 0.8, m.team == 1)
            combat.remove(m.g)
            MIS.removeAt(i)
        }
        i--
    }
}

/* ---------- flares ---------- */
fun Game.dropFlares() {
    if (F.mode == "crash" || PL.flareCd > 0) return
    if (PL.flares <= 0) { toast("Hết pháo sáng!"); return }
    PL.flares--
    PL.flareCd = 1.0
    val pv = playerVel(_v3)
    val made = ArrayList<Flare>()
    for (k in 0 until 4) {
        val f = Flare(F.pos.clone(), Vec3(RR(-14.0, 14.0), RR(-6.0, 4.0), RR(-14.0, 14.0)).addScaledVector(pv, 0.35), 3.4)
        FLR.add(f)
        made.add(f)
    }
    for (m in MIS) { val t = m.tgt; if (m.team == 1 && t != null && t.player && m.p.distanceTo(F.pos) < 950 && rand() < 0.88 / sqrt(DF.acc)) m.tgt = MTgt(flare = pick(made)) }
    sfx.flare()
}
fun Game.updateFlares(dt: Double) {
    var i = FLR.size - 1
    while (i >= 0) {
        val f = FLR[i]
        f.life -= dt
        f.v.multiplyScalar(1 - dt * 0.9)
        f.v.y -= 7 * dt
        f.p.addScaledVector(f.v, dt)
        emit(PF, f.p, _v3.set(RR(-1.0, 1.0), RR(-1.0, 1.0), RR(-1.0, 1.0)), if (rand() < 0.5) "#ffffff" else "#ffe9a0", 0.35, 0.0, 2.0)
        if (rand() < 0.4) emit(PS, f.p, _v3.set(0.0, 0.5, 0.0), "#e9e6dc", 1.4, 0.3, 1.0)
        if (f.life <= 0) FLR.removeAt(i)
        i--
    }
}

/* ---------- Ahamove supply drops ---------- */
fun Game.mkCrate(kind: String, parent: Object3D?): Group {
    val g = Group()
    box(2.4, 2.4, 2.4, World.AHA.orange, 0.0, 0.0, 0.0, g, 0.05)
    box(2.46, 0.6, 2.46, PKI[kind]!!.col, 0.0, 0.2, 0.0, g, 0.02)
    box(0.5, 2.46, 2.46, World.AHA.blue, 0.0, 0.0, 0.0, g, 0.0)
    parent?.add(g)
    return g
}
fun Game.spawnPickup(kind: String, x: Double, y: Double, z: Double) {
    val g = Group()
    mkCrate(kind, g)
    val chute = Group()
    val c = mk(GEO.hemi, "#fbf8ef", 0.0, 6.5, 0.0, chute, 0.05)
    c.scale.set(4.2, 2.4, 4.2)
    val c2 = mk(GEO.hemi, World.AHA.orange, 0.0, 6.52, 0.0, chute, 0.0)
    c2.scale.set(2.2, 2.45, 4.25)
    val P = FloatList()
    for (ab in arrayOf(doubleArrayOf(1.2, 1.2), doubleArrayOf(-1.2, 1.2), doubleArrayOf(1.2, -1.2), doubleArrayOf(-1.2, -1.2))) { val a = ab[0]; val b = ab[1]; P.add3(a, 1.2, b); P.add3(a * 3.4, 6.5, b * 3.4) }
    val lg = Geometry(); lg.position = P.toArray()
    chute.add(Lines(lg, LineMaterial("#3a3848"), false))
    g.add(chute)
    val hm = BasicMaterial(PKI[kind]!!.col).also { it.transparent = true; it.opacity = 0.22; it.depthWrite = false; it.blending = Blend.ADDITIVE }
    val halo = Mesh(Geo.sphere(4.5, 14, 10), hm)
    g.add(halo)
    g.position.set(x, y, z)
    combat.add(g)
    PKS.add(Pickup(g, kind, chute, halo, hm, 45.0, false, rand() * 6))
}
fun Game.updatePickups(dt: Double) {
    var i = PKS.size - 1
    while (i >= 0) {
        val k = PKS[i]
        k.life -= dt
        val gnd = maxOf(gridH(k.p.x, k.p.z), SEA_Y, obstacleTop(k.p.x, k.p.z, 0.0))
        if (!k.landed) {
            k.p.y -= 3.2 * dt
            k.p.x += WIND.v.x * 0.3 * dt
            k.p.z += WIND.v.z * 0.3 * dt
            k.g.rotation.z = sin(game.t * 1.3 + k.ph) * 0.12
            k.g.rotation.y += dt * 0.4
            if (k.p.y < gnd + 1.3) { k.landed = true; k.chute.visible = false; k.p.y = gnd + 1.3 }
        } else k.p.y = gnd + 1.3 + (if (gnd <= SEA_Y + 0.01) sin(game.t * 2 + k.ph) * 0.2 else 0.0)
        k.haloMat.opacity = 0.16 + 0.1 * sin(game.t * 5 + k.ph)
        k.g.visible = k.life > 6 || sin(game.t * 12) > 0
        var got = false
        if (game.started && F.mode != "crash" && F.pos.distanceTo(k.p) < (if (F.c.heli) 12 else 10)) {
            got = true
            val C = F.c; val info = PKI[k.kind]!!
            if (k.kind == "repair") PL.hp = min(PL.hpMax, PL.hp + PL.hpMax * 0.5)
            if (k.kind == "missile") PL.missiles = min(C.missiles, PL.missiles + ceil(C.missiles * 0.6).toInt())
            if (k.kind == "flare") PL.flares = min(C.flares, PL.flares + 5)
            burst(k.p, listOf(World.AHA.orange, World.AHA.blue, "#ffffff", info.col), 40, 12.0)
            sfx.nest()
            toast("📦 Ahamove giao tận cánh: ${info.icon} ${info.name}")
            addScore(50, k.p, "")
        }
        if (got || k.life <= 0) { combat.remove(k.g); PKS.removeAt(i); disposeQ.add(k.halo.geometry); (k.chute.children.firstOrNull { it is Lines } as Lines?)?.let { disposeQ.add(it.geometry) } }
        i--
    }
}
/* the delivery helicopter: flies in, drops the crate ahead of you, flies home */
fun Game.setupDelivery() {
    val h = makeHeli(false)
    DLV.heli = h
    val g = h.g
    DLV.g = g
    val crate = mkCrate("repair", g)
    DLV.crate = crate
    crate.position.y = -4.4
    val rope = Lines(Geometry.of(floatArrayOf(0f, -1.6f, -0.3f, 0f, -3.3f, 0f), null, null, null), LineMaterial("#3a3848"), strip = true)
    crate.add(rope)
    rope.position.y = 4.4
    g.visible = false
    DLV.p = g.position
    combat.add(g)
}
fun Game.startDelivery() {
    val C = F.c
    val g = DLV.g ?: return
    val kind = if (PL.hp < PL.hpMax * 0.6) "repair" else if (PL.missiles < C.missiles / 2.0) "missile" else if (PL.flares < 3) "flare" else pick(PK_KINDS)
    DLV.kind = kind
    val old = DLV.crate!!
    combat.remove(old)
    g.remove(old)
    val crate = mkCrate(kind, g)
    DLV.crate = crate
    crate.position.y = -4.4
    for (ch in old.children.toList()) if (ch is Lines) crate.add(ch)
    fwd(F.psi, 0.0, _v1)
    DLV.drop.copy(F.pos).addScaledVector(_v1, 450.0).add(_v2.set(RR(-90.0, 90.0), 0.0, RR(-90.0, 90.0)))
    DLV.drop.y = maxOf(F.pos.y + 40, gridH(DLV.drop.x, DLV.drop.z) + 100, 90.0)
    val H = LANDMARKS["hub"]
    if (H != null && hypot(H.x - DLV.drop.x, H.z - DLV.drop.z) < 3000) DLV.dir.set(H.x - DLV.drop.x, 0.0, H.z - DLV.drop.z).normalize()
    else DLV.dir.set(-_v1.z, 0.0, _v1.x).multiplyScalar(if (rand() < 0.5) 1.0 else -1.0)
    DLV.p.copy(DLV.drop).addScaledVector(DLV.dir, 1100.0)
    DLV.p.y = DLV.drop.y + 30
    DLV.vel.set(0.0, 0.0, 0.0)
    DLV.psi = psiTo(-DLV.dir.x, -DLV.dir.z)
    DLV.st = "in"
    DLV.active = true
    g.visible = true
    crate.visible = true
    val info = PKI[kind]!!
    toast("📦 Trực thăng Ahamove đang chở ${info.icon} ${info.name} tới!")
    sfx.chime(5)
}
fun Game.updateDelivery(dt: Double) {
    if (!DLV.active) return
    val h = DLV.heli!!
    h.rotor.rotation.y += dt * 26
    h.tail.rotation.x += dt * 40
    val tx: Double; val tz: Double; val ty: Double
    if (DLV.st == "in") { tx = DLV.drop.x; tz = DLV.drop.z; ty = DLV.drop.y }
    else { tx = DLV.p.x + DLV.dir.x * 200; tz = DLV.p.z + DLV.dir.z * 200; ty = DLV.p.y + 10 }
    val dx = tx - DLV.p.x; val dz = tz - DLV.p.z; val d = hypot(dx, dz); val spd = if (DLV.st == "in") min(55.0, d * 0.6 + 8) else 50.0
    val dd = if (d == 0.0) 1.0 else d
    DLV.vel.x = damp(DLV.vel.x, (dx / dd) * spd, 1.4, dt)
    DLV.vel.z = damp(DLV.vel.z, (dz / dd) * spd, 1.4, dt)
    DLV.vel.y = damp(DLV.vel.y, clamp((max(ty, gridH(DLV.p.x, DLV.p.z) + 70) - DLV.p.y) * 0.5, -8.0, 8.0), 1.5, dt)
    DLV.p.addScaledVector(DLV.vel, dt)
    val sp = hypot(DLV.vel.x, DLV.vel.z)
    if (sp > 2) DLV.psi += angDiff(atan2(-DLV.vel.x, -DLV.vel.z), DLV.psi) * (1 - exp(-1.5 * dt))
    h.g.rotation.set(-clamp(sp / 55, 0.0, 1.0) * 0.22, DLV.psi, 0.0, Euler.YXZ)
    if (DLV.st == "in" && d < 18) {
        DLV.st = "out"
        DLV.t = 20.0
        DLV.crate!!.visible = false
        spawnPickup(DLV.kind, DLV.p.x, DLV.p.y - 5, DLV.p.z)
    }
    if (DLV.st == "out") { DLV.t -= dt; if (DLV.t <= 0) { DLV.active = false; h.g.visible = false } }
}

/* ---------- per-frame driver for everything that fights ---------- */
fun Game.updateCombat(dt: Double) {
    updateEnemies(dt)
    if (game.started) updateWeapons(dt)
    updateBullets(dt)
    updateMissiles(dt)
    updateFlares(dt)
    updatePickups(dt)
    updateDelivery(dt)
    drawBullets()
    if (game.started) updateMission(dt)
    var i = FT.size - 1
    while (i >= 0) { FT[i].t -= dt; if (FT[i].t <= 0) FT.removeAt(i); i-- }
}

/** the collectStatics skip set: the combat group holds everything that moves and fights (web init: new Set([..., combat, ...])) */
fun Game.skipStatics(skip: HashSet<Object3D>) { skip.add(combat) }
fun Game.setupGameplay() { setupDelivery() }
