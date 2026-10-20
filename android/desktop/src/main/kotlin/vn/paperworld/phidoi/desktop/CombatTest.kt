package vn.paperworld.phidoi.desktop

import vn.paperworld.phidoi.engine.*
import vn.paperworld.phidoi.world.*
import vn.paperworld.phidoi.game.*
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.*

/**
 * headless combat checks (desktop mode `combat`): what the web build was tested for.
 * Prints one PASS/FAIL line per check. -Dpd.only=guns,missile,… runs a subset; -Dpd.shots=/abs/dir renders a few screenshots.
 */
fun combatTest() {
    val w = 960; val h = 540
    val ctx = Headless(w, h)
    val gl = LwjglGL()
    val host = GameHost(AwtPlatform, MemStore().also { it.put("quality", System.getProperty("q", "1")) }, NullAudio(), object : HostEvents {}, touch = true, density = 1.0)
    host.onSurfaceCreated(gl)
    host.onSurfaceChanged(w, h)
    val rec = RecordingCanvas(AwtPlatform)
    while (!host.ready) { rec.reset(); host.drawFrame(0.016, rec, w, h) }
    val g = host.g
    val only = System.getProperty("only", "").split(",").filter { it.isNotBlank() }.toSet()
    val shotDir = System.getProperty("shots", "")
    val results = ArrayList<String>()
    fun report(name: String, ok: Boolean, info: String) { val l = (if (ok) "PASS " else "FAIL ") + name + " — " + info; println(l); results.add(l) }
    val DT = 1.0 / 30
    fun step(sec: Double, each: (() -> Boolean)? = null): Double {
        var t = 0.0
        while (t < sec) { if (each != null && each()) break; g.frame(DT, null, null); t += DT }
        return t
    }
    fun shot(name: String) {
        if (shotDir.isEmpty()) return
        rec.reset(); host.drawFrame(DT, rec, w, h); gl.finish()
        val f = File(shotDir, "$name.png"); ImageIO.write(ctx.grab(), "png", f); println("wrote $f")
    }
    /** an empty fight around (x, z): no waves, no demo planes */
    fun arena(craft: String, x: Double, z: Double, y: Double, psi: Double, fight: Boolean = true) {
        g.backToMenu(); g.clearCombat()
        g.keys.clear(); g.TOUCH.on = false; g.TOUCH.x = 0.0; g.TOUCH.y = 0.0
        g.ENV.auto = false; g.ENV.hour = 10.0; g.ENV.target = 10.0
        g.setCraft(craft, true)
        g.game.started = true; g.F.g.visible = true; g.game.camMode = 0
        g.spawnAt(SpawnPt(x, z, y, psi))
        g.resetPlayer()
        g.DF = DIFF[1]
        g.MS.def = null; g.MS.state = if (fight) "fight" else "idle"; g.MS.center = XZ(x, z); g.MS.radius = 1600.0; g.MS.practice = false; g.MS.survival = false
        g.result = null
        g.MS.hits = 0; g.MS.shots = 0; g.MS.kills = 0; g.MS.score = 0; g.MS.combo = 0
        step(0.1)
    }
    fun run(name: String) = only.isEmpty() || name in only

    /* 1. guns converge on the boresight 320 m ahead, then hit a fighter ahead */
    if (run("guns")) {
        arena("jet", 1600.0, -700.0, 300.0, 0.0)
        g.PL.inv = 1e9
        g.keys.add(" ")
        var worst = 0.0; var n = 0
        val seen = HashSet<Bullet>()
        step(0.6) {
            for (b in g.BUL) if (b.team == 0 && seen.add(b)) {
                // distance from the boresight point (this frame) to the bullet's line of flight
                val bp = fwd(g.F.psi, g.F.gam).multiplyScalar(320.0).add(g.F.pos)
                val d = b.v.clone().normalize(); val rel = bp.clone().sub(b.prev); val along = rel.dot(d)
                val miss = rel.addScaledVector(d, -along).length()
                worst = max(worst, miss); n++
            }
            false
        }
        g.keys.remove(" ")
        report("guns converge", n >= 6 && worst < 3.0, "$n rounds from ${g.F.c.guns.size} barrels pass within ${"%.2f".format(worst)} m of the boresight point 320 m ahead")
        step(1.6) // let the old rounds expire
        arena("jet", 1600.0, -700.0, 300.0, 0.0)
        g.PL.inv = 1e9
        // a fighter 260 m ahead flying straight away (extending, slight climb)
        val e = g.spawnEnemy("fighter", g.F.pos.x, g.F.pos.y + 4, g.F.pos.z - 260, psi = 0.0)
        e.st = "extend"; e.stT = 1e9
        val hp0 = e.hp
        g.keys.add(" ")
        val t = step(5.0) { !e.alive }
        g.keys.remove(" ")
        report("guns hit a fighter ahead", !e.alive && g.MS.hits > 0, "fighter ${if (e.alive) "still alive (hp ${"%.1f".format(e.hp)}/$hp0)" else "shot down"} after ${"%.1f".format(t)} s · ${g.MS.hits}/${g.MS.shots} rounds hit (aim assist ${g.SET.assist})")
        // a dogfight: the bot pilot chases a fighter with its full AI, guns only
        arena("jet", 1600.0, -700.0, 300.0, 0.0)
        g.PL.inv = 1e9; g.PL.missiles = 0
        val e2 = g.spawnEnemy("fighter", g.F.pos.x + 60, g.F.pos.y, g.F.pos.z - 400, psi = 0.3)
        val tgt = Vec3(); val fv = Vec3()
        val t2 = step(40.0) { bot(g, tgt, fv); !e2.alive }
        g.TOUCH.on = false; g.keys.clear()
        report("guns win a dogfight (bot pilot)", !e2.alive, "fighter ${if (e2.alive) "still alive (hp ${"%.1f".format(e2.hp)})" else "shot down"} after ${"%.1f".format(t2)} s · ${g.MS.hits}/${g.MS.shots} rounds hit · player ${g.F.mode}")
    }

    /* 2. a locked missile kills a fighter (head-on), and the segment test catches a fast pass */
    if (run("missile")) {
        arena("jet", 1600.0, -700.0, 300.0, 0.0)
        g.PL.inv = 1e9
        val e = g.spawnEnemy("fighter", g.F.pos.x + 20, g.F.pos.y + 10, g.F.pos.z - 800, psi = PI)
        val tl = step(4.0) { g.PL.lock >= 1 }
        val locked = g.PL.lock >= 1 && g.PL.target === e
        val m0 = g.PL.missiles
        g.IN.missileQ = true
        step(0.1)
        val mis = g.MIS.firstOrNull { it.team == 0 }
        val tgtOk = mis?.tgt?.e === e
        val t = step(8.0) { !e.alive && g.MIS.none { it.team == 0 } }
        report("locked missile kills a fighter", locked && tgtOk && !e.alive && g.PL.missiles == m0 - 1, "lock in ${"%.2f".format(tl)} s (target ${if (g.PL.target === e || !e.alive) "ok" else "wrong"}), missile ${if (tgtOk) "homing on it" else "NOT locked"}, fighter ${if (e.alive) "alive hp ${e.hp}" else "killed"} ${"%.1f".format(t)} s after launch")

        // fast pass: one 0.2 s step carries the missile 35 m clean through the fighter (both end points outside its 8.4 m radius)
        arena("jet", 1600.0, -700.0, 300.0, 0.0, fight = false)
        val f2 = g.spawnEnemy("fighter", 1600.0, 300.0, -1100.0, psi = 0.0)
        val from = Vec3(1600.0, 300.0, -1100.0 + 20)
        val m = g.launchMissile(0, from, Vec3(0.0, 0.0, -1.0), 175.0, MTgt(e = f2))
        g.updateMissiles(0.2)
        val endD = m.p.distanceTo(f2.pos); val startD = from.distanceTo(f2.pos); val r = f2.r + 4
        report("missile segment collision (no tunnelling)", !f2.alive && !g.MIS.contains(m), "step 35 m: start ${"%.1f".format(startD)} m / end ${"%.1f".format(endD)} m from the centre (hit radius ${"%.1f".format(r)} m, a point test would ${if (startD > r && endD > r) "miss" else "hit"}) → fighter ${if (f2.alive) "MISSED" else "destroyed"}")
        // and in the real loop: head-on at a high closing speed with 50 ms frames
        arena("jet", 1600.0, -700.0, 300.0, 0.0)
        g.PL.inv = 1e9
        val f3 = g.spawnEnemy("fighter", g.F.pos.x, g.F.pos.y, g.F.pos.z - 600, psi = PI)
        f3.v = 80.0
        val m3 = g.launchMissile(0, g.F.pos.clone().add(Vec3(0.0, 0.0, -10.0)), Vec3(0.0, 0.0, -1.0), 175.0, MTgt(e = f3))
        var maxStep = 0.0
        var tt = 0.0
        while (tt < 6 && g.MIS.contains(m3)) { val p0 = m3.p.clone(); val e0 = f3.pos.clone(); g.frame(0.05, null, null); if (g.MIS.contains(m3)) maxStep = max(maxStep, m3.p.distanceTo(p0) + f3.pos.distanceTo(e0)); tt += 0.05 }
        report("missile vs head-on fighter at 50 ms frames", !f3.alive, "fighter ${if (f3.alive) "survived" else "destroyed"} after ${"%.2f".format(tt)} s (closing ~${"%.0f".format(maxStep / 0.05)} m/s)")
    }

    /* 3. flares decoy an enemy missile */
    if (run("flares")) {
        arena("jet", 1600.0, -700.0, 300.0, 0.0)
        g.PL.inv = 0.0
        val f0 = fwd(g.F.psi, 0.0)
        val from = g.F.pos.clone().addScaledVector(f0, -450.0)
        val m = g.launchMissile(1, from, f0, 30.0, MTgt(player = true))
        val hp0 = g.PL.hp; var drops = 0; var decoyed = false; var minD = 1e9; var warn = 0.0
        step(20.0) {
            if (!g.MIS.contains(m)) return@step true
            g.PL.inv = 0.0
            val d = m.p.distanceTo(g.F.pos); minD = min(minD, d); warn = max(warn, g.PL.incoming)
            if (m.tgt?.player == true && d < 260 && g.PL.flareCd <= 0) { g.IN.flareQ = true; drops++ }
            if (m.tgt?.flare != null) decoyed = true
            false
        }
        report("flares decoy an enemy missile", decoyed && warn > 0 && g.PL.hp == hp0 && !g.MIS.contains(m), "${drops} flare salvo(s), missile ${if (decoyed) "chased a flare" else "kept tracking"}, closest ${"%.0f".format(minD)} m, hp ${g.PL.hp}/$hp0, missile warning peaked at ${"%.2f".format(warn)}")
    }

    /* 4. the boss: the heart is shielded, six ink sacs break one by one, then the heart, then it falls */
    if (run("boss")) {
        arena("heli", -250.0, -2250.0, 300.0, -PI / 2)
        g.PL.inv = 1e9
        val boss = g.spawnEnemy("boss", -250.0 + 420, 250.0, -2250.0, psi = PI)
        step(0.2)
        val parts = boss.parts!!; val core = parts.first { it.core }
        fun shootAt(pt: BossPart, n: Int) {
            for (k in 0 until n) {
                val out = pt.wp.clone().sub(boss.pos).normalize()
                if (pt.core) out.set(0.0, -1.0, 0.0)
                g.addBullet(pt.wp.clone().addScaledVector(out, 60.0), out.clone().multiplyScalar(-BSPD), 0, 1.0, 1.45, 0)
            }
        }
        val hp0 = boss.hp
        var t = 0.0
        while (t < 1.5) { shootAt(core, 2); g.frame(DT, null, null); t += DT }
        val shielded = core.hp == core.max && boss.hp == hp0
        val order = ArrayList<Int>()
        var ok = true
        for (pt in parts.filter { !it.core }) {
            val before = boss.podsLeft
            t = 0.0
            while (pt.alive && t < 10) { shootAt(pt, 3); g.frame(DT, null, null); t += DT }
            order.add(boss.podsLeft)
            if (pt.alive || boss.podsLeft != before - 1) ok = false
        }
        step(0.5)
        if (shotDir.isNotEmpty()) { g.camera.position.set(boss.pos.x - 160, boss.pos.y + 40, boss.pos.z + 120); g.game.started = true }
        t = 0.0
        while (boss.alive && t < 15) { shootAt(core, 3); g.frame(DT, null, null); t += DT }
        val killed = !boss.alive
        val tf = step(12.0) { !g.EN.contains(boss) }
        report("boss damaged part by part", shielded && ok && order == listOf(5, 4, 3, 2, 1, 0) && killed && !g.EN.contains(boss) && g.MS.boss == null,
            "heart ${if (shielded) "shielded while sacs live" else "NOT shielded"} · sacs left after each: $order · heart ${if (killed) "broken" else "holding (hp ${core.hp})"} · wreck ${if (g.EN.contains(boss)) "still falling" else "gone after ${"%.1f".format(tf)} s"} · score ${g.MS.score}")
    }

    /* 5. supply drops repair and rearm */
    if (run("supply")) {
        arena("jet", 1500.0, -700.0, 220.0, -PI / 2, fight = false)
        g.PL.inv = 1e9
        g.PL.hp = 40.0; g.PL.missiles = 1
        g.startDelivery()
        val kind1 = g.DLV.kind; val far = g.DLV.p.distanceTo(g.DLV.drop)
        val tDrop = step(60.0) { g.PKS.isNotEmpty() }
        val pk = g.PKS.firstOrNull()
        var hpAfter = g.PL.hp
        if (pk != null) { g.F.pos.copy(pk.p); step(0.1); hpAfter = g.PL.hp }
        val ok1 = kind1 == "repair" && pk != null && hpAfter == 90.0 && g.PKS.isEmpty()
        step(25.0) { !g.DLV.active }
        g.PL.hp = g.PL.hpMax; g.PL.missiles = 1
        g.startDelivery()
        val kind2 = g.DLV.kind
        step(60.0) { g.PKS.isNotEmpty() }
        val pk2 = g.PKS.firstOrNull()
        if (pk2 != null) { g.F.pos.copy(pk2.p); step(0.1) }
        val ok2 = kind2 == "missile" && pk2 != null && g.PL.missiles == 5
        report("supply drop repairs and rearms", ok1 && ok2, "heli came from ${"%.0f".format(far)} m and dropped a $kind1 crate after ${"%.1f".format(tDrop)} s → hp 40 → $hpAfter; next crate $kind2 → missiles 1 → ${g.PL.missiles}/${g.F.c.missiles}")
    }

    /* 6. a scripted bot wins mission m1 */
    if (run("m1")) {
        g.backToMenu()
        g.PICK.diff = System.getProperty("diff", "1").toInt(); g.PICK.craft = "jet"
        g.startMission("m1")
        val tgt = Vec3(); val f = Vec3(); var kills = 0; var lastCrash = g.F.lastCrash
        var t = 0.0
        while (t < 420 && g.MS.state != "done") {
            bot(g, tgt, f)
            g.frame(DT, null, null); t += DT
            if (g.MS.kills != kills) { kills = g.MS.kills }
            if (g.F.lastCrash != lastCrash) { lastCrash = g.F.lastCrash; println("  crash at ${fmtT(g.MS.t)}: $lastCrash (hp ${"%.0f".format(g.PL.hp)})") }
            if (shotDir.isNotEmpty() && abs(t - 30.0) < DT / 2) shot("m1_fight")
        }
        g.TOUCH.on = false; g.keys.clear()
        val r = g.result
        report("bot wins mission m1", g.MS.won && g.MS.state == "done" && r != null, "state ${g.MS.state} won=${g.MS.won} in ${fmtT(g.MS.t)} (par ${fmtT(170.0)}) · kills ${g.MS.kills} · lost ${g.MS.lost} · acc ${if (g.MS.shots > 0) g.MS.hits * 100 / g.MS.shots else 0}% · score ${g.MS.score}" +
            (if (r != null) " · result “${r.title}” stars ${r.stars.map { if (it) "★" else "☆" }.joinToString("")} · ${r.note}" else ""))
        if (r != null) println("  rows: " + r.rows.joinToString(" | ") { "${it.first}: ${it.second}" })
        val saved = g.store.getString("save")
        val back = loadSave(g.store)
        report("progress saved", back.m["m1"]?.score == g.SAVE.m["m1"]?.score && (g.MS.won == (back.m["m1"] != null)), "store 'save' = $saved")
    }
    println("---")
    for (l in results) println(l)
    println("${results.count { it.startsWith("PASS") }}/${results.size} PASS")
}

/** a simple pursuit pilot: steer at the target's lead point, shoot when lined up, missiles on lock */
private fun bot(g: Game, tgt: Vec3, f: Vec3) {
    val F = g.F
    g.TOUCH.on = true
    if (F.mode != "fly") { g.TOUCH.x = 0.0; g.TOUCH.y = 0.0; g.keys.remove(" "); if (F.mode == "landed") g.keys.add("shift") else g.keys.remove("shift"); return }
    g.keys.remove("shift")
    val T = g.PL.target
    val c = g.MS.center!!
    var shoot = false
    if (T != null && T.alive) {
        val tp = if (T.parts != null) (g.PL.tpart?.wp ?: T.pos) else T.pos
        leadPoint(F.pos, tp, T.vel, BSPD + F.v, tgt)
        val d = tgt.distanceTo(F.pos)
        fwd(F.psi, F.gam, f)
        val off = acos(clamp(tgt.clone().sub(F.pos).normalize().dot(f), -1.0, 1.0))
        shoot = off < 0.09 && d < 600
        // too close and not lined up: extend away before turning back in
        if (d < 110 && off > 0.6) tgt.set(F.pos.x + f.x * 300, max(F.pos.y, T.pos.y + 40), F.pos.z + f.z * 300)
        if (g.PL.lock >= 1 && T.k != "kite" && g.PL.missiles > 0) g.IN.missileQ = true
    } else tgt.set(c.x, 170.0, c.z)
    val dx = tgt.x - F.pos.x; val dy = tgt.y - F.pos.y; val dz = tgt.z - F.pos.z
    val psiT = psiTo(dx, dz)
    var gamT = clamp(atan2(dy, max(hypot(dx, dz), 1.0)), -0.45, 0.5)
    if (F.agl < 45) gamT = max(gamT, 0.25)
    if (F.warn > 0) gamT = max(gamT, 0.4)
    val dpsi = angDiff(psiT, F.psi)
    val roll = clamp(-dpsi * 2.5, -1.0, 1.0)
    val pitch = clamp((gamT - F.gam) * 4 + abs(roll) * 0.15, -1.0, 1.0)
    g.TOUCH.x = roll; g.TOUCH.y = -pitch
    if (shoot) g.keys.add(" ") else g.keys.remove(" ")
}
