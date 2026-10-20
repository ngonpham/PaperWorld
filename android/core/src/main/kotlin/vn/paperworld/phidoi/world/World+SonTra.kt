package vn.paperworld.phidoi.world

import vn.paperworld.phidoi.engine.*
import vn.paperworld.phidoi.world.Geog.KARST
import vn.paperworld.phidoi.world.Geog.R_COCO
import vn.paperworld.phidoi.world.World.PALS
import vn.paperworld.phidoi.world.Geog.S
import vn.paperworld.phidoi.world.Geog.lineDist
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/* =========================================================
   SƠN TRÀ — Linh Ứng, radar, Bàn Cờ, lighthouse, banyan, port
   ========================================================= */

fun World.ladyBuddha() {
    val x = 1305.0; val z = -2318.0; val y = gy(x, z); val g = Group()
    g.position.set(x, y, z)
    g.rotation.y = -PI * 0.8 // faces the sea to the south-east
    scene.add(g)
    val stoneM = toon("#f4f2ec") { emissive.copy(Color.of("#fff6e0")); emissiveIntensity = 0.0 }
    NIGHT.add { gl -> stoneM.emissiveIntensity = gl * 0.45 }
    mk(GEO.cyl16, "#d8d2c4", 0.0, 2.0, 0.0, g, 0.05).scale.set(10.0, 4.0, 10.0)
    for (k in 0 until 14) {
        val a = (k / 14.0) * PI * 2; val p = mk(GEO.sph, "#f7efe6", cos(a) * 6.6, 5.0, sin(a) * 6.6, g, 0.04)
        p.scale.set(1.8, 1.3, 2.6); p.rotation.y = -a
    }
    mk(lathe(0.0 to 0.0, 6.6 to 0.0, 6.2 to 2.5, 5.4 to 8.0, 4.7 to 14.0, 4.3 to 18.5, 3.6 to 21.5, 2.6 to 23.2, 1.6 to 24.4, 0.0 to 24.7, seg = 20), stoneM, 0.0, 5.5, 0.0, g, 0.07)
    mk(GEO.sph, stoneM, 0.0, 32.4, 0.0, g, 0.05).scale.set(2.4, 2.8, 2.4)
    mk(GEO.sph, stoneM, 0.0, 35.4, 0.4, g, 0.04).scale.set(1.2, 1.3, 1.2)
    val halo = mk(GEO.torus, "#e8c25a", 0.0, 32.6, 1.8, g, 0.03); halo.scale.set(3.8, 3.8, 1.5)
    val vase = mk(lathe(0.0 to 0.0, 0.7 to 0.2, 0.9 to 1.0, 0.4 to 1.8, 0.5 to 2.4, 0.0 to 2.5, seg = 10), stoneM, 0.0, 21.2, -4.0, g, 0.03)
    vase.rotation.x = 0.2
    for (s in intArrayOf(-1, 1)) { val arm = mk(GEO.sph, stoneM, s * 3.2, 22.0, -1.8, g, 0.04); arm.scale.set(1.1, 4.0, 1.1); arm.rotation.set(0.9, 0.0, -s * 0.5) }
    colCyl(x, z, 7.5, y, y + 37, "statue")
    // the pagoda: halls with upturned roofs + a tiered tower
    fun hall(hx: Double, hz: Double, ry: Double, w: Double, d: Double) {
        val hy = gy(hx, hz); val hg = Group()
        hg.position.set(hx, hy, hz); hg.rotation.y = ry; scene.add(hg)
        box(w + 4, 1.4, d + 4, "#d8d2c4", 0.0, 0.7, 0.0, hg, 0.04)
        box(w, 6.0, d, "#f0c75e", 0.0, 4.4, 0.0, hg)
        val r1 = mk(GEO.troof, "#c8553d", 0.0, 7.2, 0.0, hg, 0.06); r1.scale.set(w * 1.15, 3.4, d * 1.25)
        val r2 = mk(GEO.troof, "#c8553d", 0.0, 10.4, 0.0, hg, 0.05); r2.scale.set(w * 0.7, 2.6, d * 0.8)
        box(w * 0.66, 1.6, d * 0.62, "#f0c75e", 0.0, 10.0, 0.0, hg, 0.04)
        colBox(hx, hz, (w + 4) / 2, (d + 4) / 2, ry, hy - 2, hy + 13, "temple")
    }
    hall(1262.0, -2340.0, -0.35, 22.0, 12.0)
    hall(1236.0, -2372.0, -0.35, 14.0, 9.0)
    val tx = 1350.0; val tz = -2300.0; val ty = gy(tx, tz)
    for (k in 0 until 7) {
        val s = 6 - k * 0.55
        box(s, 3.0, s, "#f6eedc", tx, ty + 1.5 + k * 3.6, tz, scene, 0.04)
        val r = mk(GEO.troof, "#c8553d", tx, ty + 3 + k * 3.6, tz, scene, 0.04)
        r.scale.set(s * 1.5, 1.3, s * 1.5)
    }
    mk(GEO.cone6, "#e8c25a", tx, ty + 27, tz, scene, 0.03).scale.set(0.6, 3.0, 0.6)
    colCyl(tx, tz, 4.5, ty, ty + 29, "temple")
    keep(1290.0, -2330.0, 70.0)
    disc("linhung", "Sơn Trà", "Chùa Linh Ứng · Tượng Quan Âm", "靈應寺", "Tượng Phật Bà Quan Âm cao 67 m trên bán đảo Sơn Trà, mắt hướng ra biển che chở ngư dân ra khơi.", x, y + 30, z, 150.0)
    LANDMARKS["buddha"] = Anchor(x, y + 18, z)
}

fun World.radar() {
    val (x, z, h) = findHigh(1000.0, -2740.0, 140.0, 12.0)
    val g = Group()
    g.position.set(x, h - 1, z)
    scene.add(g)
    fun dome(dx: Double, dz: Double, r: Double, base: Double) {
        box(r * 1.4, base, r * 1.4, "#e2e0d8", dx, base / 2, dz, g, 0.05)
        mk(GEO.sph, "#fbfaf6", dx, base + r * 0.8, dz, g, 0.06).scale.set(r, r, r)
        colCyl(x + dx, z + dz, r + 0.5, h - 4, h + base + r * 1.8, "radar")
    }
    dome(0.0, 0.0, 7.0, 8.0)
    dome(17.0, 8.0, 5.0, 6.0)
    dome(-14.0, 10.0, 4.2, 5.0)
    box(0.5, 26.0, 0.5, "#9a98a6", -6.0, 13.0, -12.0, g, 0.02)
    box(3.0, 0.4, 0.4, "#9a98a6", -6.0, 24.0, -12.0, g, 0.02)
    colCyl(x - 6, z - 12, 1.0, h, h + 27, "radar")
    keep(x, z, 40.0)
    disc("radar", "Sơn Trà", "Đỉnh Sơn Trà · Trạm radar", "山茶", "Những “quả bóng” radar trắng trên đỉnh Ốc Sơn cao gần 700 m — nhìn bao trọn vịnh, thành phố và biển Đông.", x, h + 20, z, 140.0)
    LANDMARKS["radar"] = Anchor(x, h, z)
}

fun World.banCo() {
    val (x, z, h) = findHigh(600.0, -2560.0, 90.0, 8.0)
    val g = Group()
    g.position.set(x, h - 0.5, z)
    scene.add(g)
    box(14.0, 1.0, 10.0, "#cfc8b8", 0.0, 0.5, 0.0, g, 0.04)
    box(3.2, 1.6, 3.2, "#8a7a5a", 0.0, 1.8, 0.0, g, 0.03)
    box(3.0, 0.1, 3.0, "#e8d8a8", 0.0, 2.65, 0.0, g, 0.0)
    for (s in intArrayOf(-1, 1)) {
        val fig = mk(GEOC.statue, "#b8b0a0", s * 3.2, 1.0, 0.0, g, 0.03)
        fig.scale.set(1.6, 1.4, 1.6)
        mk(GEO.cone, "#b8b0a0", s * 3.2, 4.6, 0.0, g, 0.02).scale.set(0.5, 1.2, 0.5)
    }
    // paragliding launch: windsock + carpet
    box(10.0, 0.15, 14.0, "#3a7d44", -14.0, 0.2, 6.0, g, 0.02)
    box(0.25, 7.0, 0.25, "#4a4858", -20.0, 3.5, -2.0, g, 0.02)
    val sock = mk(GEO.cone, "#f39c12", -20.0, 6.6, -2.0, g, 0.03)
    sock.scale.set(0.7, 3.0, 0.7)
    WINDSOCKS.add(sock)
    colCyl(x, z, 5.0, h - 2, h + 6, "statue")
    keep(x, z, 30.0)
    disc("banco", "Sơn Trà", "Đỉnh Bàn Cờ", "棋盤", "Tượng hai ông tiên ngồi đánh cờ trên đỉnh núi — điểm cất cánh dù lượn nổi tiếng nhìn xuống cả thành phố.", x, h + 10, z, 90.0)
    LANDMARKS["banco"] = Anchor(x, h, z)
}

private val BANYAN_COLS = listOf("#3f7d45", "#4f8f4a", "#2f6b3f")

fun World.banyan() {
    val x = 430.0; val z = -2640.0; val y = gy(x, z)
    for (k in 0 until 9) {
        val a = (k / 9.0) * PI * 2; val r = if (k != 0) RR(2.0, 9.0) else 0.0
        put("trunk", x + cos(a) * r, y + 5, z + sin(a) * r, if (k != 0) 1.0 else 3.5, 10.0 + (if (k != 0) 0.0 else 3.0), if (k != 0) 1.0 else 3.5, 0.0, "#7a5a3a")
    }
    for (k in 0 until 9) put("crown", x + RR(-12.0, 12.0), y + RR(13.0, 19.0), z + RR(-12.0, 12.0), RR(8.0, 12.0), RR(5.0, 8.0), RR(8.0, 12.0), rand() * 6, pick(BANYAN_COLS))
    colCyl(x, z, 16.0, y, y + 24, "tree")
    keep(x, z, 24.0)
    disc("cayda", "Sơn Trà", "Cây Đa ngàn năm", "", "Cây đa cổ thụ hơn 800 năm tuổi trên sườn tây Sơn Trà, rễ phụ buông xuống như một khu rừng nhỏ.", x, y + 15, z, 80.0)
    LANDMARKS["banyan"] = Anchor(x, y, z)
}

fun World.tienSaPort() {
    for (pz in doubleArrayOf(-2395.0, -2330.0)) {
        val x0 = 260.0; val x1 = 130.0
        box(x0 - x1, 1.4, 14.0, "#c8c4ba", (x0 + x1) / 2, 2.0, pz, scene, 0.04)
        colBox((x0 + x1) / 2, pz, (x0 - x1) / 2, 7.0, 0.0, -3.0, 2.7, "pier")
    }
    // cruise ship
    val g = Group(); val sx = 190.0; val sz = -2362.0
    g.position.set(sx, 0.0, sz)
    g.rotation.y = PI / 2
    scene.add(g)
    val hull = mk(hullGeo, "#f7f5ef", 0.0, 3.0, 0.0, g, 0.07); hull.scale.set(12.0, 8.0, 78.0)
    box(10.0, 0.4, 70.0, "#2a5d8f", 0.0, 0.9, 6.0, g, 0.0)
    for (k in 0 until 4) box(9.6 - k * 1.2, 2.4, 54.0 - k * 8, "#fbfaf6", 0.0, 8.2 + k * 2.4, 8.0 + k * 2, g, 0.05)
    box(3.0, 5.0, 4.0, "#d6452f", 0.0, 19.0, 14.0, g, 0.04)
    colBox(sx, sz, 39.0, 6.5, PI / 2, -2.0, 21.0, "ship")
    for (cx in doubleArrayOf(210.0, 240.0)) {
        val cy = 2.7; val cg = Group()
        cg.position.set(cx, cy, -2410.0); scene.add(cg)
        for (s in intArrayOf(-1, 1)) for (t in intArrayOf(-1, 1)) box(1.0, 26.0, 1.0, "#d6452f", s * 5.0, 13.0, t * 4.0, cg, 0.03)
        box(12.0, 2.0, 10.0, "#d6452f", 0.0, 26.0, 0.0, cg, 0.03)
        box(1.6, 1.6, 34.0, "#d6452f", 0.0, 24.0, 10.0, cg, 0.03)
        colBox(cx, -2410.0, 6.0, 6.0, 0.0, 0.0, 28.0, "crane")
    }
    keep(200.0, -2360.0, 90.0)
    helipad(205.0, 2.7, -2395.0, 6.5)
    disc("cangtiensa", "Sơn Trà", "Cảng Tiên Sa", "仙沙", "Cảng nước sâu dưới chân Sơn Trà, nơi những con tàu du lịch khổng lồ cập bến đưa du khách vào Đà Nẵng.", 190.0, 20.0, -2362.0, 120.0)
}

/* red-shanked douc langurs — the "queen of primates" of Sơn Trà */
fun World.doucs() {
    val doucMat = toon("#ffffff") { vertexColors = true }
    val spots = listOf(doubleArrayOf(1150.0, -2380.0), doubleArrayOf(880.0, -2440.0), doubleArrayOf(1480.0, -2480.0), doubleArrayOf(700.0, -2420.0), doubleArrayOf(1700.0, -2600.0))
    for (sp in spots) {
        val sx = sp[0]; val sz = sp[1]
        for (k in 0 until 3) {
            val x = sx + RR(-25.0, 25.0); val z = sz + RR(-25.0, 25.0); val y = gy(x, z) + RR(5.0, 8.0); val g = Group()
            g.position.set(x, y, z)
            g.rotation.y = rand() * 6
            scene.add(g)
            put("crown", x, y - 1.6, z, 4.0, 3.0, 4.0, 0.0, "#4f8f4a")
            put("trunk", x, y - 4.5, z, 0.8, 7.0, 0.8, 0.0, "#6b4a2f")
            val parts = arrayListOf(
                P(GEO.box, 0.0, 0.6, 0.0, 0.8, 1.1, 0.7, 0.0, 0.0, 0.0, "#7a7a82"), // grey body
                P(GEO.sph, 0.0, 1.45, -0.15, 0.38, 0.42, 0.36, 0.0, 0.0, 0.0, "#f0b860"), // golden face
                P(GEO.sph, 0.0, 1.7, 0.0, 0.42, 0.25, 0.42, 0.0, 0.0, 0.0, "#1f1d2b"),
                P(GEO.box, 0.0, -0.6, 0.45, 0.14, 2.2, 0.14, 0.3, 0.0, 0.0, "#f4f2ea"), // white tail
            )
            for (s in intArrayOf(-1, 1)) {
                parts.add(P(GEO.box, s * 0.28, -0.2, 0.0, 0.22, 0.7, 0.22, 0.0, 0.0, 0.0, "#b8322a")) // maroon "trousers"
                parts.add(P(GEO.box, s * 0.5, 0.75, -0.1, 0.18, 0.6, 0.18, 0.0, 0.0, 0.0, "#f4f2ea")) // white forearms
            }
            mk(mergeGeo(parts), doucMat, 0.0, 0.0, 0.0, g, 0.03)
            DOUCS.add(World.Douc(g, y, rand() * 6))
        }
    }
    disc("vooc", "Sơn Trà", "Voọc chà vá chân nâu", "", "“Nữ hoàng linh trưởng” mặt vàng, tay trắng, chân nâu đỏ — đàn voọc quý hiếm sống trong rừng Sơn Trà. Bay thật thấp mới thấy!", 1150.0, gy(1150.0, -2380.0) + 6, -2380.0, 45.0)
    DISC_MULTI[DISC[DISC.size - 1]] = spots.map { doubleArrayOf(it[0], it[1]) }
}

/** the Tiên Sa lighthouse on the eastern cape (the inline block of the web init()) */
fun World.tienSaLighthouse() {
    var x = 2150.0
    while (gy(x + 12, -2705.0) > 7 && x < 2600) x += 12
    lighthouse(x - 6, -2705.0, "Hải đăng Tiên Sa", "haidang", "Sơn Trà", "Ngọn hải đăng trắng nơi mũi đông bán đảo, dẫn đường tàu thuyền vào vịnh Đà Nẵng hơn một thế kỷ.", "")
    LANDMARKS["lighthouse"] = Anchor(x - 6, 0.0, -2705.0)
}

/* =========================================================
   NGŨ HÀNH SƠN — five marble mountains, Tam Thai, Non Nước
   ========================================================= */

private val STONE_COLS = listOf("#f4f2ec", "#e2ddd2", "#cfc9bd", "#bfb8aa", "#e8e2d4")

fun World.marbleMountains() {
    val names = listOf(
        arrayOf("thuyson", "Thủy Sơn · Chùa Tam Thai", "水山", "Ngọn lớn nhất trong năm ngọn núi đá vôi, ẩn chùa Tam Thai, động Huyền Không và đài Vọng Giang nhìn ra sông Cổ Cò."),
        arrayOf("mocson", "Mộc Sơn", "木山", "Ngọn núi “Gỗ” — vách đá như hình người nằm nghỉ, nằm ngay cạnh Thủy Sơn."),
        arrayOf("hoason", "Hỏa Sơn", "火山", "Ngọn núi “Lửa” với hai đỉnh Dương Hỏa Sơn và Âm Hỏa Sơn, chùa Linh Sơn ẩn trong vách đá."),
        null,
        arrayOf("kimson", "Kim Sơn", "金山", "Ngọn núi “Kim loại” soi bóng xuống sông Cổ Cò, có động Quan Âm linh thiêng."),
        arrayOf("thoson", "Thổ Sơn", "土山", "Ngọn núi “Đất” thấp và rộng nhất — dấu tích đền tháp Chăm Pa xưa."),
    )
    for (i in KARST.indices) {
        val k = KARST[i]; val n = names.getOrNull(i) ?: continue
        val (x, z, h) = findHigh(k.x, k.z, k.r * 0.5, 6.0)
        disc(n[0], "Ngũ Hành Sơn", n[1], n[2], n[3], x, h + 10, z, k.r + 60)
        keep(k.x, k.z, k.r + 35)
        LANDMARKS[n[0]] = Anchor(x, h, z) // web also stores r: k[2] (unread)
    }
    // trees clinging to the karsts
    var x = 440.0
    while (x < 1000) {
        var z = 620.0
        while (z < 1120) {
            val xx = x + RR(-3.0, 3.0); val zz = z + RR(-3.0, 3.0)
            z += 7
            gy(xx, zz)
            if (S.karst < 0.3 || rand() > 0.55 * Q.dens + 0.2) continue
            val sl = slope(xx, zz)
            if (sl > 1.6) { if (rand() < 0.25) put("rock", xx, gy(xx, zz), zz, RR(1.5, 3.0), RR(1.0, 2.0), RR(1.5, 3.0), rand() * 6, "#d6d0c4"); continue }
            tree(xx, zz, RR(1.8, 3.2), PALS.karst, false)
        }
        x += 7
    }
    // Tam Thai pagoda on the saddle of Thủy Sơn
    val T = LANDMARKS["thuyson"]!!
    val px = T.x - 18; val pz = T.z + 14; val py = gy(px, pz); val g = Group()
    g.position.set(px, py, pz)
    g.rotation.y = 0.4
    scene.add(g)
    box(16.0, 5.0, 9.0, "#f0c75e", 0.0, 2.5, 0.0, g)
    mk(GEO.troof, "#b84a32", 0.0, 5.0, 0.0, g, 0.05).scale.set(19.0, 3.0, 12.0)
    mk(GEO.troof, "#b84a32", 0.0, 7.6, 0.0, g, 0.04).scale.set(11.0, 2.2, 7.0)
    box(9.0, 1.4, 5.5, "#f0c75e", 0.0, 7.2, 0.0, g, 0.03)
    colBox(px, pz, 9.0, 5.0, 0.4, py - 2, py + 10, "temple")
    val vx = T.x + 22; val vz = T.z - 26; val vy = gy(vx, vz)
    for (s in intArrayOf(-1, 1)) for (t in intArrayOf(-1, 1)) box(0.5, 4.0, 0.5, "#8a3a2a", vx + s * 2.2, vy + 2, vz + t * 2.2, scene, 0.02)
    mk(GEO.troof, "#b84a32", vx, vy + 4, vz, scene, 0.04).scale.set(7.0, 2.4, 7.0)
    // Huyền Không cave mouth (west face)
    val cx = T.x - 70; val cz = T.z + 8; val cy = gy(cx - 8, cz)
    val arch = mk(Geo.cylinder(5.0, 5.0, 3.0, 16, 1, false, 0.0, PI).addONormal(), "#2a2630", cx - 4, cy + 1, cz, scene, 0.04)
    arch.rotation.set(PI / 2, 0.0, PI / 2)
    // tiered tower (Xá Lợi)
    val tx = T.x + 6; val tz = T.z + 40; val ty = gy(tx, tz)
    for (k in 0 until 7) {
        val s = 4.6 - k * 0.45
        box(s, 2.6, s, "#f6eedc", tx, ty + 1.3 + k * 3.1, tz, scene, 0.03)
        mk(GEO.troof, "#b84a32", tx, ty + 2.6 + k * 3.1, tz, scene, 0.03).scale.set(s * 1.5, 1.1, s * 1.5)
    }
    colCyl(tx, tz, 3.6, ty, ty + 23, "temple")
    // Non Nước stone-carving village at the foot
    run {
        var k = 0
        while (k < 160 * Q.dens + 40) {
            k++
            val sx = RR(560.0, 820.0); val sz = RR(1090.0, 1180.0)
            gy(sx, sz)
            if (S.karst > 0.05 || lineDist(R_COCO, sx, sz) < 25) continue
            val s = if (rand() < 0.15) RR(2.2, 4.0) else RR(0.8, 1.6)
            put("statue", sx, gy(sx, sz), sz, s, s, s, rand() * 6, pick(STONE_COLS))
        }
    }
    for (k in 0 until 26) {
        val hx = RR(570.0, 820.0); val hz = RR(1150.0, 1215.0); val y = gy(hx, hz); val w = RR(8.0, 14.0); val d = RR(7.0, 10.0); val h = RR(4.0, 7.0)
        if (S.karst > 0.05) continue
        put("wall", hx, y + h / 2, hz, w, h, d, 0.0, pick(PALS.rural))
        put("roof", hx, y + h, hz, d * 1.1, 2.6, w * 1.08, PI / 2, pick(PALS.ruralRoof))
        colBox(hx, hz, w / 2, d / 2, 0.0, y, y + h + 2.6, "bld")
    }
    disc("nonnuoc", "Ngũ Hành Sơn", "Làng đá Non Nước", "", "Làng nghề chạm khắc đá mỹ nghệ hơn 400 năm dưới chân Ngũ Hành Sơn — sân nhà nào cũng đầy tượng Phật, tượng sư tử.", 690.0, 12.0, 1140.0, 110.0)
    LANDMARKS["nonnuoc"] = Anchor(690.0, 0.0, 1140.0)
}
