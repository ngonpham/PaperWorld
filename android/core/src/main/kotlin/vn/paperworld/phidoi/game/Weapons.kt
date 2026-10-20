package vn.paperworld.phidoi.game

import vn.paperworld.phidoi.engine.*
import vn.paperworld.phidoi.world.*
import kotlin.math.*

/* =========================================================
   YOUR WEAPONS — guns with heat, aim assist and convergence, the helicopter's turret,
   missiles with lock, flares, targeting, repairs
   ========================================================= */
fun Game.aimDir(out: Vec3): Vec3 = if (F.c.turret) out.copy(PL.aim) else fwd(F.psi, F.gam, out)

private class TgtScore(val e: Enemy, val s: Double)
/** auto-targeting every 0.4 s, or the next target on TAB / 🎯 (manual = true) */
fun Game.pickTarget(manual: Boolean) {
    aimDir(_v1)
    val list = ArrayList<TgtScore>()
    for (e in EN) {
        if (!e.alive || e.demo) continue
        _v2.copy(e.pos).sub(F.pos)
        val d = _v2.length()
        if (d > 3000) continue
        val ang = acos(clamp(_v2.dot(_v1) / (if (d == 0.0) 1.0 else d), -1.0, 1.0))
        list.add(TgtScore(e, ang * 700 + d * (if (e.k == "boss") 0.5 else 1.0)))
    }
    if (list.isEmpty()) { PL.target = null; PL.lock = 0.0; return }
    list.sortBy { it.s }
    if (manual) {
        val i = list.indexOfFirst { it.e === PL.target }
        PL.target = list[(i + 1) % list.size].e
        PL.manualT = game.t
        PL.lock = 0.0
        sfx.beep(900.0, 0.04, 0.05)
        return
    }
    val cur = list.find { it.e === PL.target }
    if (cur == null || (game.t - PL.manualT > 5 && list[0].s < cur.s - 260)) { if (list[0].e !== PL.target) PL.lock = 0.0; PL.target = list[0].e }
}
/** which squid part the missile seeker looks at */
fun Game.bossAimPart(e: Enemy): BossPart? {
    aimDir(_v1)
    var best: BossPart? = null; var bs = 1e9
    for (p in e.parts!!) {
        if (!p.alive || (p.core && e.podsLeft > 0)) continue
        _v2.copy(p.wp).sub(F.pos)
        val d = _v2.length(); val a = acos(clamp(_v2.dot(_v1) / d, -1.0, 1.0))
        if (a < bs) { bs = a; best = p }
    }
    return best
}
private val _mz = Vec3(); private val _tip = Vec3(); private val _ma = Vec3()
private fun Game.aimPoint(T: Enemy): Vec3 = if (T.parts != null) (PL.tpart?.wp ?: T.pos) else T.pos
fun Game.shootGun() {
    val C = F.c; val gp = C.guns[PL.gunI++ % C.guns.size]
    _tip.set(gp[0], gp[1], gp[2]).applyMatrix4(F.g.matrixWorld)
    aimDir(_mz)
    // guns are harmonised: every barrel converges on the boresight 320 m ahead
    _mz.multiplyScalar(320.0).add(F.pos).sub(_tip).normalize()
    val T = PL.target
    if (T != null && T.alive && (SET.assist || C.turret)) {
        val tp = aimPoint(T)
        leadPoint(_tip, tp, T.vel, BSPD + F.v, _v2)
        _v2.sub(_tip)
        val d = _v2.length()
        _v2.divideScalar(d)
        if (d < 800 && _mz.dot(_v2) > cos(if (C.turret) 0.6 else 0.13)) _mz.lerp(_v2, if (C.turret) 0.85 else 0.92).normalize()
    }
    val sp = 0.0028
    _mz.x += RR(-sp, sp); _mz.y += RR(-sp, sp); _mz.z += RR(-sp, sp)
    _mz.normalize()
    // aim the muzzle so that muzzle velocity + the aircraft's own velocity flies along _mz
    playerVel(_v3)
    _v1.copy(_v3).addScaledVector(_mz, -_v3.dot(_mz))
    val along = sqrt(max(BSPD * BSPD - _v1.lengthSq(), BSPD * BSPD * 0.5))
    _v3.copy(_mz).multiplyScalar(along + max(0.0, playerVel(_v1).dot(_mz)))
    addBullet(_tip, _v3, 0, C.dmg, 1.45, 0)
    emit(PF, _tip, _v1.copy(_mz).multiplyScalar(18.0), "#ffe9a0", 0.08, 0.0, 1.0)
    MS.shots++
    sfx.gun()
}
fun Game.fireMissile() {
    if (F.mode == "crash" || F.mode == "landed" || PL.misCd > 0) return
    if (PL.missiles <= 0) { toast("Hết tên lửa — chờ trực thăng Ahamove tiếp tế 📦"); return }
    PL.missiles--
    PL.misCd = 0.45
    PL.misSide = -PL.misSide
    F.g.updateWorldFromParents()
    _tip.set(PL.misSide * (if (F.c.id == "seaplane") 2.6 else 1.4), if (F.c.id == "seaplane") 1.2 else -0.7, -0.5).applyMatrix4(F.g.matrixWorld)
    aimDir(_v1)
    val T = PL.target; val locked = PL.lock >= 1 && T != null && T.alive
    val tgt = if (locked) MTgt(e = T, part = if (T!!.parts != null) PL.tpart else null) else null
    launchMissile(0, _tip, _v1, max(F.v, 20.0) + 10, tgt)
    if (!locked) toast("Phóng không khoá — tên lửa bay thẳng")
    PL.lock = if (locked) 0.55 else 0.0
}
private val BUMP_COLS = listOf("#3b2a5a", "#6b4a8a", "#fbf8ef")
fun Game.updateWeapons(dt: Double) {
    val C = F.c; val alive = game.started && F.mode != "crash" && MS.state != "done"
    PL.inv = max(0.0, PL.inv - dt)
    PL.hitT = max(0.0, PL.hitT - dt)
    PL.hurtT = max(0.0, PL.hurtT - dt * 1.4)
    PL.flareCd = max(0.0, PL.flareCd - dt)
    PL.misCd = max(0.0, PL.misCd - dt)
    PL.calmT += dt
    F.model?.let { it.g.visible = PL.inv <= 0 || F.mode == "crash" || sin(game.t * 30) > 0 }
    if (!alive) { PL.lock = 0.0; return }
    // turret (helicopter): slews toward the target's lead point inside its arc
    if (C.turret) {
        fwd(F.psi, -0.12, _v1)
        val T = PL.target
        if (T != null && T.alive) {
            val tp = aimPoint(T)
            leadPoint(F.pos, tp, T.vel, BSPD, _v2)
            _v2.sub(F.pos).normalize()
            val hz = hypot(_v2.x, _v2.z); val el = atan2(_v2.y, hz); val az = abs(angDiff(psiTo(_v2.x, _v2.z), F.psi))
            if (az < 1.1 && el < 0.5 && el > -1.2) _v1.copy(_v2)
        }
        val ang = acos(clamp(PL.aim.dot(_v1), -1.0, 1.0))
        if (ang > 1e-4) { _ma.crossVectors(PL.aim, _v1); if (_ma.lengthSq() > 1e-10) PL.aim.applyAxisAngle(_ma.normalize(), min(ang, 3 * dt)) }
    }
    // guns + heat
    val firing = IN.fire && !PL.over && (F.mode == "fly" || F.mode == "water")
    if (firing) {
        PL.gunT -= dt
        while (PL.gunT <= 0) {
            PL.gunT += 1 / C.rate
            shootGun()
            PL.heat += C.heat
            if (PL.heat >= 1) { PL.heat = 1.0; PL.over = true; sfx.beep(330.0, 0.25, 0.08); break }
        }
    } else PL.gunT = max(0.0, PL.gunT - dt)
    PL.heat = max(0.0, PL.heat - dt * (if (PL.over) 0.5 else if (firing) 0.12 else 0.38))
    if (PL.over && PL.heat < 0.3) PL.over = false
    // targeting + missile lock
    if (IN.targetQ) { IN.targetQ = false; pickTarget(true) }
    PL.pickT -= dt
    if (PL.pickT <= 0) { PL.pickT = 0.4; pickTarget(false) }
    val T = PL.target
    if (T != null && !T.alive) PL.target = null
    if (T != null && T.parts != null) PL.tpart = bossAimPart(T)
    if (T != null && T.alive && PL.missiles > 0 && F.mode == "fly") {
        val tp = aimPoint(T)
        _v2.copy(tp).sub(F.pos)
        val d = _v2.length(); val ang = acos(clamp(_v2.dot(aimDir(_v1)) / d, -1.0, 1.0))
        if (d < 1300 && ang < 0.3) PL.lock = min(1.0, PL.lock + dt / 0.85)
        else PL.lock = max(0.0, PL.lock - dt * 1.5)
    } else PL.lock = max(0.0, PL.lock - dt * 2)
    if (IN.missileQ) { IN.missileQ = false; fireMissile() }
    if (IN.flareQ) { IN.flareQ = false; dropFlares() }
    // lock + missile-warning tones
    PL.toneT -= dt
    if (PL.toneT <= 0) {
        if (PL.incoming > 0) { PL.toneT = 0.16 - PL.incoming * 0.08; sfx.beep(if (PL.incoming > 0.7) 1900.0 else 1500.0, 0.05, 0.06) }
        else if (PL.lock >= 1) { PL.toneT = 0.09; sfx.beep(1600.0, 0.05, 0.035) }
        else if (PL.lock > 0.05) { PL.toneT = 0.24; sfx.beep(1000.0, 0.04, 0.03) }
        else PL.toneT = 0.05
    }
    // repairs: landing on an Ahamove pad, floating the seaplane, or a calm patch on easy
    PL.repairing = false
    if (F.mode == "landed" || F.mode == "water") {
        val pad = World.AHA.pads.find { hypot(it.x - F.pos.x, it.z - F.pos.z) < it.r + 3 }
        val k = if (pad != null) 18.0 else if (F.mode == "water") 7.0 else 4.0
        if (PL.hp < PL.hpMax || (pad != null && (PL.missiles < C.missiles || PL.flares < C.flares))) {
            PL.repairing = true
            PL.hp = min(PL.hpMax, PL.hp + k * dt)
            if (pad != null) { PL.padT += dt; if (PL.padT > 0.8) { PL.padT = 0.0; PL.missiles = min(C.missiles, PL.missiles + 1); PL.flares = min(C.flares, PL.flares + 1) } }
        }
    }
    if (DF.regen != 0.0 && PL.calmT > 5) PL.hp = min(PL.hpMax, PL.hp + DF.regen * dt)
    // collisions with enemy aircraft
    if (F.mode == "fly") {
        for (e in EN) {
            if (!e.alive || e.demo) continue
            if (e.parts != null) {
                // the squid's rubbery hide bounces you off — it hurts, but it isn't the end
                for (h in e.hull) {
                    val R = h.r * e.sc + F.c.radius
                    if (h.wp.distanceTo(F.pos) < R) {
                        _v1.copy(F.pos).sub(h.wp).normalize()
                        F.pos.copy(h.wp).addScaledVector(_v1, R + 1)
                        F.psi = psiTo(_v1.x, _v1.z)
                        F.gam = clamp(asin(clamp(_v1.y, -1.0, 1.0)), -0.6, 0.6)
                        F.v = max(F.c.vs * 1.3, F.v * 0.6)
                        if (F.c.heli) F.hv.copy(_v1).setY(0.0).multiplyScalar(18.0)
                        hurtPlayer(20.0, _v1.clone().negate())
                        burst(F.pos, BUMP_COLS, 24, 9.0)
                        if (game.t - PL.bumpT > 3) { PL.bumpT = game.t; toast("Bật văng khỏi thân mực! −20 độ bền") }
                        break
                    }
                }
                continue
            }
            if (e.pos.distanceTo(F.pos) < e.r * 0.8 + F.c.radius) { killEnemy(e, e.pos); if (PL.inv <= 0) crash("Va chạm trên không!", true); return }
        }
    }
}
