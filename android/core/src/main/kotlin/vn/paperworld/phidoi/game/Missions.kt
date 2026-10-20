package vn.paperworld.phidoi.game

import vn.paperworld.phidoi.engine.*
import vn.paperworld.phidoi.world.*
import vn.paperworld.phidoi.world.Geog.coastX
import kotlin.math.*

/* =========================================================
   MISSIONS — six sorties over Đà Nẵng, survival, and a practice range
   ========================================================= */
class MissionDef(
    val id: String, val icon: String, val name: String, val short: String, val han: String, val hour: Double, val radius: Double, val par: Double, val song: Int, val desc: String,
    val center: (() -> XZ)? = null, val start: () -> SpawnPt, val waves: List<() -> Unit>? = null, val lm: (() -> MsLm)? = null,
    val survival: Boolean = false, val practice: Boolean = false,
)
/** what the results panel shows (web: #pResult — rHan, rTitle, rStars, rStats, rNote, nextBtn) */
class MissionResult(val han: String, val title: String, val stars: List<Boolean>, val rows: List<Pair<String, String>>, val note: String, val next: MissionDef?)

// spawn helpers · angles: 0 = east (the sea), π/2 = south, π = west, −π/2 = north
fun Game.sqFighters(k: String, n: Int, ang: Double, dist: Double, alt: Double) {
    val c = MS.center!!
    for (i in 0 until n) {
        val a = ang + (i - (n - 1) / 2.0) * 0.1; val x = c.x + cos(a) * (dist + i * 30); val z = c.z + sin(a) * (dist + i * 30)
        spawnEnemy(k, x, max(alt + RR(-20.0, 30.0), gridH(x, z) + 80), z, psi = psiTo(c.x - x, c.z - z))
    }
}
fun Game.sqBombers(n: Int, ang: Double, dist: Double, alt: Double) {
    val L = MS.lm!!
    for (i in 0 until n) {
        val a = ang + (i - (n - 1) / 2.0) * 0.09; val x = L.x + cos(a) * (dist + i * 60); val z = L.z + sin(a) * (dist + i * 60); val y = max(L.y + alt, 90.0)
        spawnEnemy("bomber", x, y, z, psi = psiTo(L.x - x, L.z - z), st = "run", alt = y + RR(-10.0, 10.0), lm = L)
    }
}
/** kites at [x, z, height above ground or sea] */
fun Game.kites(list: List<DoubleArray>) {
    for (k in list) {
        val x = k[0]; val z = k[1]; val h = k[2]
        val y = max(gridH(x, z), SEA_Y) + h
        spawnEnemy("kite", x, y, z, anchor = Vec3(x, y, z))
    }
}
private fun Game.LM(k: String) = LANDMARKS[k]!!
private fun kxz(x: Double, z: Double, h: Double) = doubleArrayOf(x, z, h)

fun Game.makeMissions(): List<MissionDef> = listOf(
    MissionDef(
        "m1", "🏖", "Bình minh Mỹ Khê", "Mỹ Khê", "美溪", 6.4, 1500.0, 170.0, 0,
        "Diều mực giăng kín bờ biển Mỹ Khê lúc bình minh. Làm quen súng và tên lửa: bắn rơi hết diều, rồi đón đầu hai phi cơ giấy than từ ngoài khơi.",
        center = { XZ(coastX(-800.0) + 120, -800.0) },
        start = { SpawnPt(coastX(350.0) + 40, 350.0, 75.0, psiTo(0.08, -1.0)) },
        waves = listOf(
            { kites((0 until 6).map { i -> val z = -60.0 - i * 220; kxz(coastX(z) + RR(40.0, 220.0), z, RR(30.0, 70.0)) }) },
            { kites((0 until 4).map { i -> val z = -1250.0 - i * 170; kxz(coastX(z) + RR(-160.0, 120.0), z, RR(40.0, 90.0)) }); sqFighters("fighter", 2, -0.25, 1200.0, 150.0) },
        ),
    ),
    MissionDef(
        "m2", "🐉", "Giữ lấy Cầu Rồng", "Cầu Rồng", "龍橋", 9.6, 1700.0, 250.0, 1,
        "Oanh tạc cơ mực nhắm vào Cầu Rồng. Hạ phi cơ hộ tống, chặn đường oanh tạc cơ trước khi chúng thả mực xuống sông Hàn.",
        lm = { MsLm("Cầu Rồng", LM("dragon").x0, 14.0, LM("dragon").z, 110.0) },
        start = { SpawnPt(coastX(-1100.0) + 60, -1100.0, 120.0, psiTo(-1.0, 0.05)) },
        waves = listOf(
            { sqFighters("fighter", 3, -1.9, 1100.0, 170.0) },
            { sqBombers(2, 0.15, 1400.0, 120.0); sqFighters("fighter", 2, 0.3, 1300.0, 190.0) },
            { sqBombers(2, -0.6, 1400.0, 130.0); sqBombers(1, 0.7, 1500.0, 115.0); sqFighters("fighter", 3, 0.1, 1300.0, 210.0) },
        ),
    ),
    MissionDef(
        "m3", "⛰", "Mây trên Sơn Trà", "Sơn Trà", "山茶", 12.4, 1500.0, 270.0, 2,
        "Phi đội giấy than lượn quanh bán đảo Sơn Trà và tượng Quan Âm. Không chiến giữa núi rừng — và chạm trán quái kiệt “Quạ Đen”.",
        center = { XZ(1000.0, -2600.0) },
        start = { SpawnPt(LM("buddha").x - 250, LM("buddha").z + 650, 170.0, psiTo(0.45, -1.0)) },
        waves = listOf(
            { sqFighters("fighter", 4, -1.2, 1000.0, 270.0) },
            {
                sqFighters("fighter", 3, 0.2, 1100.0, 300.0)
                kites(listOf(kxz(LM("radar").x + 70, LM("radar").z + 30, 60.0), kxz(LM("radar").x - 90, LM("radar").z - 40, 70.0), kxz(LM("banco").x + 40, LM("banco").z, 55.0), kxz(LM("banyan").x, LM("banyan").z + 30, 50.0)))
            },
            { sqFighters("ace", 1, -0.4, 1100.0, 330.0); sqFighters("fighter", 2, -0.7, 1150.0, 300.0) },
        ),
    ),
    MissionDef(
        "m4", "🗻", "Hẻm đá Ngũ Hành", "Ngũ Hành", "五行山", 16.7, 1400.0, 260.0, 3,
        "Diều mực trốn giữa năm ngọn núi đá vôi. Bay thấp luồn lách hạ hết, rồi giữ làng đá Non Nước khỏi oanh tạc cơ lúc hoàng hôn.",
        lm = { MsLm("Làng đá Non Nước", 690.0, 6.0, 1140.0, 120.0) },
        center = { XZ(720.0, 880.0) },
        start = { SpawnPt(760.0, 240.0, 95.0, psiTo(0.0, 1.0)) },
        waves = listOf(
            { kites(Geog.KARST.map { k -> val a = rand() * 6.28; kxz(k.x + cos(a) * k.r * 1.45, k.z + sin(a) * k.r * 1.45, k.h * 0.55 + RR(8.0, 22.0)) } + listOf(kxz(700.0, 860.0, 30.0), kxz(610.0, 1180.0, 25.0))) },
            { sqBombers(2, PI, 1300.0, 110.0); sqFighters("fighter", 2, PI * 0.92, 1200.0, 160.0) },
            { sqBombers(3, 0.1, 1300.0, 120.0); sqFighters("fighter", 3, 0.3, 1200.0, 180.0) },
        ),
    ),
    MissionDef(
        "m5", "🏮", "Đêm hoa đăng Hội An", "Hội An", "會安", 20.6, 1600.0, 290.0, 3,
        "Đêm rằm phố Hội, Băng Mực Đen kéo oanh tạc cơ từ biển vào. Giữ cho mái ngói, đèn lồng và Chùa Cầu không vấy mực.",
        lm = { MsLm("Phố cổ Hội An", 700.0, 8.0, 3460.0, 230.0) },
        center = { XZ(900.0, 3350.0) },
        start = { SpawnPt(300.0, 3580.0, 80.0, psiTo(1.0, -0.1)) },
        waves = listOf(
            { kites(listOf(kxz(560.0, 3560.0, 25.0), kxz(700.0, 3600.0, 30.0), kxz(840.0, 3580.0, 28.0), kxz(1000.0, 3600.0, 30.0), kxz(620.0, 3380.0, 40.0))); sqFighters("fighter", 2, -0.2, 1200.0, 160.0) },
            { sqBombers(3, 0.05, 1500.0, 120.0); sqFighters("fighter", 2, 0.2, 1400.0, 170.0) },
            { sqBombers(2, -0.5, 1500.0, 120.0); sqBombers(2, 0.6, 1500.0, 130.0); sqFighters("fighter", 3, 0.0, 1400.0, 180.0) },
        ),
    ),
    MissionDef(
        "m6", "🦑", "Mực Khổng Lồ", "Mực Khổng Lồ", "巨墨", 17.4, 1700.0, 360.0, 4,
        "Khí cầu Mực Khổng Lồ trồi lên giữa vịnh Đà Nẵng. Phá sáu túi mực tím hai bên thân để lộ trái tim mực, rồi kết liễu nó trước khi thành phố chìm trong bóng tối.",
        center = { XZ(-250.0, -2250.0) },
        start = { SpawnPt(coastX(-1500.0) + 40, -1500.0, 160.0, psiTo(-0.7, -1.0)) },
        waves = listOf(
            { sqFighters("fighter", 3, PI * 1.15, 900.0, 210.0) },
            {
                val c = MS.center!!
                spawnEnemy("boss", c.x + 420, 250.0, c.z, psi = PI)
                showTip("🦑 Mực Khổng Lồ!", "Khí cầu mực đang trồi lên giữa vịnh", "Bắn 6 túi mực tím phát sáng hai bên thân — tên lửa sẽ tự khoá vào túi gần tâm ngắm nhất.", 6000.0)
            },
        ),
    ),
)
fun Game.makeSurv() = MissionDef(
    "surv", "♾", "Sinh tồn", "Sinh tồn", "∞", 12.5, 1900.0, 0.0, 5,
    "Giữ Ahamove Sky Hub bên sông Hàn trước từng đợt tấn công không dứt — càng về sau càng đông, có cả Quạ Đen và Mực Khổng Lồ. Trụ được bao nhiêu đợt?",
    lm = { MsLm("Ahamove Sky Hub", LM("hub").x, LM("hub").y, LM("hub").z, 100.0) },
    center = { XZ(0.0, -1050.0) },
    start = { SpawnPt(coastX(-500.0) + 60, -500.0, 130.0, psiTo(-1.0, -0.2)) },
    survival = true,
)
fun Game.makePrac() = MissionDef(
    "prac", "🎯", "Bia tập", "Bia tập", "練", 9.5, 1500.0, 0.0, 5,
    "Không giới hạn máy bay, không thể thua. Diều mực và phi cơ tập luôn được thả lại — luyện ngắm dẫn bắn, khoá tên lửa và né đạn trên biển Mỹ Khê.",
    center = { XZ(coastX(-700.0) + 200, -700.0) },
    start = { SpawnPt(coastX(200.0) + 60, 200.0, 90.0, psiTo(0.1, -1.0)) },
    practice = true,
)

fun Game.survivalWave(n: Int): String {
    val c = MS.center!!; val L = MS.lm; val side = rand() * 6.28
    if (n % 10 == 0) { spawnEnemy("boss", c.x + cos(side) * 420, 250.0, c.z + sin(side) * 420, psi = 0.0); sqFighters("fighter", 2, side + 0.4, 1100.0, 220.0); return "Mực Khổng Lồ" }
    val nk = if (n <= 3) 3 + n else if (n % 2 != 0) 2 else 0
    if (nk != 0) kites((0 until nk).map { val a = rand() * 6.28; val r = RR(250.0, 900.0); val x = c.x + cos(a) * r; val z = c.z + sin(a) * r; kxz(x, z, RR(40.0, 110.0)) })
    sqFighters("fighter", min(1 + floor(n * 0.7).toInt(), 8), side, 1200.0, RR(150.0, 260.0))
    val nb = min(n / 3, 4)
    if (nb != 0 && L != null) sqBombers(nb, side + PI * RR(0.5, 1.5), 1400.0, 120.0)
    if (n % 5 == 0) sqFighters("ace", 1, side + 0.6, 1200.0, 300.0)
    return ""
}
fun Game.practiceTick(dt: Double) {
    MS.pracT = MS.pracT - dt
    if (MS.pracT > 0) return
    MS.pracT = 3.0
    val c = MS.center!!; val k = EN.count { it.alive && it.k == "kite" }; val f = EN.count { it.alive && it.k == "fighter" }
    if (k < 7) kites((0 until 7 - k).map { val z = c.z + RR(-700.0, 700.0); kxz(coastX(z) + RR(0.0, 400.0), z, RR(30.0, 110.0)) })
    if (f < 2) sqFighters("fighter", 1, RR(-0.8, 0.8), 1100.0, 160.0)
}
fun Game.nextWave() {
    MS.wave++
    MS.state = "fight"
    if (MS.practice) { practiceTick(1.0); return }
    var extra = ""
    if (MS.survival) extra = survivalWave(MS.wave + 1)
    else MS.def!!.waves!![MS.wave]()
    val n = if (MS.survival) "" else "/${MS.waves}"
    val lm = MS.lm
    showTip("⚔ Đợt " + (MS.wave + 1) + n, extra.ifEmpty { waveLabel() }, if (lm != null) "Bảo vệ ${lm.name}!" else "Bám đuôi, dẫn bắn, khoá tên lửa!", 3600.0)
    sfx.alarm(2)
}
fun Game.waveLabel(): String {
    val c = linkedMapOf("kite" to 0, "fighter" to 0, "bomber" to 0, "ace" to 0, "boss" to 0)
    for (e in EN) if (e.alive && !e.demo) c[e.k] = c[e.k]!! + 1
    val parts = ArrayList<String>()
    if (c["boss"]!! > 0) parts.add("🦑 Mực Khổng Lồ")
    if (c["ace"]!! > 0) parts.add("☠ Quạ Đen")
    if (c["bomber"]!! > 0) parts.add("${c["bomber"]} oanh tạc cơ")
    if (c["fighter"]!! > 0) parts.add("${c["fighter"]} phi cơ giấy than")
    if (c["kite"]!! > 0) parts.add("${c["kite"]} diều mực")
    return parts.joinToString(" · ").ifEmpty { "Địch đang tới" }
}
fun Game.winMission() {
    MS.state = "win"
    MS.won = true
    MS.endT = 3.2
    val M = MS.def!!; val lm = MS.lm
    val bonus = 1000 + (if (lm != null) Math.round(lm.hp * 15).toInt() else 0) + PL.lives * 300 + max(0, Math.round((M.par - MS.t) * 10).toInt())
    MS.bonus = bonus
    MS.score += bonus
    sfx.fanfare()
    showTitle(Region("", "Nhiệm vụ hoàn thành!", "勝", M.icon + " " + M.name + " · thưởng +" + bonus))
    celebrate(if (lm != null) Vec3(lm.x, 0.0, lm.z) else F.pos)
}
fun Game.failMission(reason: String) {
    if (MS.state == "fail" || MS.state == "done" || MS.state == "win") return
    MS.state = "fail"
    MS.won = false
    MS.reason = reason
    MS.endT = 3.4
    sfx.fail()
    showTitle(Region("", if (MS.survival) "Hết đường lui!" else "Nhiệm vụ thất bại", "敗", reason))
}
fun Game.updateMission(dt: Double) {
    val def = MS.def ?: return
    if (MS.state == "brief" || MS.state == "fight" || MS.state == "between") MS.t += dt
    MS.comboT -= dt
    if (MS.comboT <= 0) MS.combo = 0
    val alive = EN.count { it.alive && !it.demo }; val bossDying = EN.any { it.k == "boss" && !it.alive }
    if (MS.state == "brief") { MS.stT -= dt; if (MS.stT <= 0) nextWave() }
    else if (MS.state == "fight") {
        if (MS.practice) practiceTick(dt)
        else if (alive == 0 && !bossDying) {
            if (!MS.survival && MS.wave >= def.waves!!.size - 1) winMission()
            else {
                MS.state = "between"
                MS.stT = 4.5
                if (MS.survival) { val b = 300 * (MS.wave + 1); MS.score += b; toast("✓ Trụ vững đợt ${MS.wave + 1} · +$b") }
                else toast("✓ Sạch bóng địch · đợt ${MS.wave + 2}/${MS.waves} sắp tới")
                sfx.chime(5)
            }
        }
    } else if (MS.state == "between") { MS.stT -= dt; if (MS.stT <= 0) nextWave() }
    else if (MS.state == "win" || MS.state == "fail") { MS.endT -= dt; if (MS.endT <= 0) { MS.state = "done"; showResult() } }
    val lm = MS.lm
    if (lm != null && lm.hp <= 0 && (MS.state == "fight" || MS.state == "between")) failMission("${lm.name} đã chìm trong mực!")
    // the edge of the battle area
    val c = MS.center!!
    val dx = F.pos.x - c.x; val dz = F.pos.z - c.z
    MS.outside = game.started && hypot(dx, dz) > MS.radius * 1.3
    // Ahamove supplies
    if (!MS.practice && (MS.state == "fight" || MS.state == "between") && !DLV.active && F.mode == "fly") {
        MS.supplyT -= dt
        if (MS.supplyT <= 0) {
            val C = F.c; val need = PL.hp < PL.hpMax * 0.75 || PL.missiles < C.missiles / 2.0 || PL.flares < 3
            if (need || rand() < 0.3) { startDelivery(); MS.supplyT = RR(35.0, 50.0) / DF.drop }
            else MS.supplyT = 10.0
        }
    }
}
fun Game.starsFor(): List<Boolean> {
    val M = MS.def!!; val lm = MS.lm
    return listOf(MS.won, MS.won && MS.lost == 0, MS.won && (if (lm != null) lm.hp >= 70 else MS.t <= M.par))
}
fun Game.showResult() {
    val M = MS.def!!; val acc = if (MS.shots != 0) Math.round((MS.hits.toDouble() / MS.shots) * 100).toInt() else 0
    var note = ""; var best = 0; var newBest = false
    val rows = ArrayList<Pair<String, String>>()
    rows.add("Hạ gục" to "${MS.kills}"); rows.add("Độ chính xác" to "$acc%"); rows.add("Thời gian" to fmtT(MS.t))
    val lm = MS.lm
    if (lm != null) rows.add(lm.name + " còn" to "${ceil(lm.hp).toInt()}%")
    rows.add("Máy bay mất" to "${MS.lost}")
    if (MS.won && MS.bonus != 0) rows.add("Thưởng hoàn thành" to "+${MS.bonus}")
    rows.add("Tổng điểm" to "${MS.score}")
    var st = listOf(false, false, false)
    if (MS.survival) {
        val w = max(0, MS.wave + (if (MS.state == "between") 1 else 0))
        rows.add(0, "Trụ được" to "$w đợt")
        if (MS.score > SAVE.survBest) { SAVE.survBest = MS.score; newBest = true }
        SAVE.survWave = max(SAVE.survWave, w)
        best = SAVE.survBest
        st = listOf(w >= 3, w >= 6, w >= 10)
        note = "Kỷ lục: $best điểm · ${SAVE.survWave} đợt" + (if (newBest) " · KỶ LỤC MỚI!" else "")
    } else if (!MS.practice) {
        st = starsFor()
        val prev = SAVE.m[M.id] ?: MSave(0, 0)
        val ns = st.count { it }
        if (MS.won) {
            if (MS.score > prev.score) newBest = true
            SAVE.m[M.id] = MSave(max(prev.stars, ns), max(prev.score, MS.score))
        }
        best = (SAVE.m[M.id] ?: prev).score
        note = (if (MS.won) "★ Hoàn thành · ★ Không mất máy bay · ★ " + (if (lm != null) "Địa danh còn ≥ 70%" else "Nhanh hơn " + fmtT(M.par)) else MS.reason) + (if (best != 0) " · Kỷ lục $best" else "") + (if (newBest) " · KỶ LỤC MỚI!" else "")
    }
    persist()
    val idx = MISSIONS.indexOf(M)
    val nx = if (idx >= 0 && idx < MISSIONS.size - 1 && MS.won) MISSIONS[idx + 1] else null
    result = MissionResult(
        if (MS.won || MS.survival) "勝" else "敗",
        if (MS.survival) "Sinh tồn · " + max(0, MS.wave) + " đợt" else if (MS.won) "Hoàn thành!" else "Thất bại",
        st, rows, note, nx,
    )
    closePanels()
    game.panel = "pResult"
    renderMenu()
}
fun Game.clearCombat() {
    for (e in EN.toList()) removeEnemy(e)
    for (m in MIS) combat.remove(m.g)
    MIS.clear()
    BUL.clear()
    FLR.clear()
    for (k in PKS) combat.remove(k.g)
    PKS.clear()
    for (d in INKD) combat.remove(d)
    INKD.clear()
    FT.clear()
    DLV.active = false
    DLV.g?.visible = false
    MS.boss = null
    PL.target = null
    PL.lock = 0.0
    drawBullets()
}
fun Game.resetPlayer() {
    val C = F.c
    PL.hpMax = C.hp; PL.hp = C.hp; PL.heat = 0.0; PL.over = false; PL.missiles = C.missiles; PL.flares = C.flares
    PL.lives = if (MS.practice) 99 else 3; PL.inv = 2.0; PL.target = null; PL.lock = 0.0; PL.hurtT = 0.0; PL.calmT = 0.0
    PL.aim.copy(fwd(F.psi, -0.12, _v1))
}
/** the attract mode's little air show over the city */
fun Game.spawnDemo() {
    for (k in 0 until 3) spawnEnemy("fighter", DEMO.x + RR(-200.0, 200.0), RR(120.0, 170.0), DEMO.z + RR(-200.0, 200.0), demo = true, ang = k * 2.1)
    spawnEnemy("bomber", DEMO.x, 190.0, DEMO.z, demo = true)
    for (k in 0 until 3) { val x = DEMO.x + RR(-300.0, 300.0); val z = DEMO.z + RR(-300.0, 300.0); val y = max(gridH(x, z), 0.0) + RR(50.0, 90.0); spawnEnemy("kite", x, y, z, anchor = Vec3(x, y, z), demo = true) }
}
private val SURV_HOURS = listOf(9.5, 12.5, 16.8, 18.6, 21.5)
fun Game.startMission(id: String) {
    val M = ALLM.find { it.id == id } ?: MISSIONS[0]
    clearCombat()
    closePanels()
    DF = DIFF.getOrNull(PICK.diff) ?: DIFF[1]
    MS.def = M; MS.state = "brief"; MS.wave = -1; MS.t = 0.0; MS.stT = 4.0; MS.kills = 0; MS.shots = 0; MS.hits = 0; MS.lost = 0; MS.score = 0; MS.combo = 0; MS.comboT = 0.0
    MS.survival = M.survival; MS.practice = M.practice; MS.endT = 0.0; MS.supplyT = 30.0; MS.lmWarnT = 0.0; MS.outside = false; MS.won = false; MS.reason = ""; MS.bonus = 0; MS.pracT = 0.0
    MS.waves = M.waves?.size ?: 0
    MS.center = M.center?.invoke()
    MS.radius = M.radius
    MS.lm = M.lm?.invoke()
    if (MS.center == null) MS.center = XZ(MS.lm!!.x, MS.lm!!.z)
    ENV.auto = false
    val h = if (M.survival) pick(SURV_HOURS) else M.hour
    ENV.hour = h; ENV.target = h
    ENV.rainOn = false
    ENV.rain = 0.0
    missionAudio(M.song)
    setCraft(PICK.craft, true)
    spawnAt(M.start())
    resetPlayer()
    result = null
    game.started = true
    game.paused = false
    game.slow = 0.0
    F.g.visible = true
    game.fade = 1.0
    game.lapT = 0.0
    game.tipShown.clear()
    game.titleT = game.t
    game.regionId = regionAt(F.pos.x, F.pos.z).id
    showTitle(Region("", M.name, M.han, M.desc))
    store.putString("mission", M.id)
}
fun Game.backToMenu() {
    clearCombat()
    closePanels()
    MS.def = null
    MS.state = "idle"
    MS.center = null
    MS.lm = null
    game.started = false
    game.paused = false
    game.slow = 0.0
    F.g.visible = false
    F.mode = "fly"
    game.camSnap = true
    renderMenu()
    spawnDemo()
}
/** pause / resume (web: togglePause; the panel text is [pauseSub]) */
fun Game.togglePause(force: Boolean? = null) {
    if (!game.started || MS.state == "done") return
    val on = force ?: !game.paused
    closePanels()
    game.paused = on
    if (on) {
        val d = MS.def
        pauseSub = (if (d != null) d.icon + " " + d.name + " · " else "") + fmtT(MS.t) + " · " + MS.score + " điểm"
        game.panel = "pPause"
    }
}
/** web init: spawnAt(MISSIONS[0].start()) */
fun Game.firstSpawn() = MISSIONS[0].start()
/** web init tail: renderMenu(); spawnDemo() */
fun Game.afterReady() { renderMenu(); spawnDemo() }
