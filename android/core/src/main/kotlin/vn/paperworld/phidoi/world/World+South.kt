package vn.paperworld.phidoi.world

import vn.paperworld.phidoi.engine.*
import vn.paperworld.phidoi.world.Geog.RIVERS
import vn.paperworld.phidoi.world.Geog.R_THUBON
import vn.paperworld.phidoi.world.Geog.S
import vn.paperworld.phidoi.world.Geog.linePoint
import vn.paperworld.phidoi.world.World.PALS
import kotlin.math.*

/* =========================================================
   HỘI AN — old town, Chùa Cầu, lanterns, river, Bảy Mẫu, Trà Quế
   ========================================================= */
private object HOIAN {
    val streets = doubleArrayOf(3519.0, 3480.0, 3441.0, 3402.0)
    val cross = doubleArrayOf(540.0, 610.0, 680.0, 750.0, 820.0, 890.0)
    const val x0 = 486.0
    const val x1 = 912.0
}
private val YUP = Vec3(0.0, 1.0, 0.0)

fun World.hoiAn(): Int {
    val streets = HOIAN.streets; val cross = HOIAN.cross; val x0 = HOIAN.x0; val x1 = HOIAN.x1
    val nearCross = { x: Double -> cross.any { c -> abs(x - c) < 5 } }
    val PK = doubleArrayOf(640.0, 3460.0) // Phúc Kiến assembly hall
    var houses = 0
    for (si in streets.indices) {
        val zs = streets[si]
        for (side in (if (si == 0) listOf(-1.0) else listOf(-1.0, 1.0))) {
            var x = x0 + RR(0.0, 3.0)
            while (x < x1) {
                val w = RR(5.2, 7.6)
                val xc = x + w / 2
                x += w + RR(0.2, 0.8)
                if (nearCross(xc)) { x += 6; continue }
                val d = RR(10.0, 15.0); val h = RR(4.4, 7.8); val zc = zs + side * (4.5 + d / 2)
                if (abs(xc - PK[0]) < 16 && abs(zc - PK[1]) < 20) continue
                val y = gy(xc, zc)
                put("hoi", xc, y + h / 2, zc, w, h, d, 0.0, pick(PALS.hoi))
                put("roof", xc, y + h, zc, d * 1.08, RR(2.2, 3.1), w * 1.12, PI / 2, pick(PALS.hoiRoof))
                if (rand() < 0.4) put("wall", xc, y + 3.4, zc - side * (d / 2 + 0.7), w * 0.95, 0.25, 1.6, 0.0, "#7a3e2c") // eave over the shopfront
                colBox(xc, zc, w / 2, d / 2, 0.0, y - 1, y + h + 2.4, "house")
                if (rand() < 0.5) put("lantern", xc + RR(-1.5, 1.5), y + 3.1, zc - side * (d / 2 + 0.9), 0.5, 0.65, 0.5, 0.0, pick(PALS.lantern))
                houses++
            }
        }
        // lantern strings across the street
        var x = x0 + 4
        while (x < x1) {
            val y = gy(x, zs) + 6.8; val n = 4
            LANTERN_LINES.add(doubleArrayOf(x, y + 0.6, zs - 7, x, y + 0.6, zs + 7))
            for (k in 0 until n) {
                val c = pick(PALS.lantern); val ly = y - RR(0.0, 0.6); val lz = zs - 5 + (k * 10.0) / (n - 1)
                put("lantern", x, ly, lz, 0.6, 0.78, 0.6, 0.0, c)
                if (k % 2 == 0) GLOWS.add(Glow(x, ly, lz, c))
            }
            x += 6.5
        }
    }
    // riverfront (Bạch Đằng): big lantern stalls and boats
    run {
        var x = x0
        while (x < x1) { put("stall", x + RR(-2.0, 2.0), gy(x, 3528.0), 3528.0, 1.0, 1.0, 1.0, 0.0, pick(listOf("#e8562a", "#f2c230", "#d6452f"))); x += 11 }
    }
    var s0 = 0.0; var s1 = 0.0
    run {
        var s = 0.0
        while (s < R_THUBON.len) { val p = linePoint(R_THUBON, s); if (p.x < 380) s0 = s; if (p.x < 1050) s1 = s; s += 10 }
    }
    for (k in 0 until 34) {
        val p = linePoint(R_THUBON, RR(s0, s1))
        val side = if (rand() < 0.5) -1.0 else 1.0
        put("hoiboat", p.x - p.tz * side * 44, 0.05, p.z + p.tx * side * 44, 1.0, 1.0, 1.0, atan2(-p.tx, -p.tz) + RR(-0.2, 0.2), pick(listOf("#6b4a2f", "#2f6f9f", "#7a3e2c", "#3a7d44")))
    }
    // An Hội night market across the river
    for (k in 0 until 40) { val x = RR(560.0, 780.0); val z = RR(3642.0, 3664.0); put("stall", x, gy(x, z), z, 0.8, 0.8, 0.8, 0.0, pick(listOf("#e8562a", "#f2c230", "#d6452f", "#1f9a8a"))) }
    run {
        var x = 560.0
        while (x < 780) { val c = pick(PALS.lantern); val y = gy(x, 3654.0) + 4; put("lantern", x, y, 3654.0, 0.5, 0.6, 0.5, 0.0, c); if (x % 8 < 4) GLOWS.add(Glow(x, y, 3654.0, c)); x += 4 }
    }
    // trees + palms in courtyards
    for (k in 0 until 40) { val x = RR(x0, x1); val z = RR(3370.0, 3395.0); if (!kept(x, z)) tree(x, z, RR(2.0, 3.0)) }
    disc("phoco", "Hội An", "Phố cổ Hội An", "會安古鎮", "Thương cảng sầm uất từ thế kỷ 16 — những dãy nhà tường vàng mái ngói rêu phong, Di sản Văn hoá Thế giới UNESCO.", 700.0, 20.0, 3460.0, 200.0)
    disc("hoadang", "Hội An", "Hoa đăng sông Hoài", "", "Mỗi tối, hàng nghìn ngọn đèn hoa đăng được thả trôi trên sông, cầu bình an. Hãy bay thấp lúc trời tối!", 700.0, 8.0, 3578.0, 120.0)
    // web: DISC[DISC.length - 1].night = true — the flag is never read by the web build (Disc has no such field here)
    LANDMARKS["hoian"] = Anchor(700.0, 0.0, 3460.0)
    return houses
}

fun World.chuaCau() {
    val x = 470.0; val z = 3440.0; val y = max(gy(x - 14, z), gy(x + 14, z)); val g = Group(); val wood = "#b8574a"
    g.position.set(x, 0.0, z)
    g.rotation.y = PI / 2 // spans the creek east–west
    scene.add(g)
    for (k in 0 until 9) {
        val t = k / 8.0 - 0.5; val yy = y + 2.4 + (1 - 4 * t * t) * 1.1
        box(3.4, 0.5, 6.4, "#9a6a4a", 0.0, yy, t * 24, g, 0.03)
        for (s in listOf(-1.0, 1.0)) box(0.4, 3.2, 0.4, wood, s * 2.8, yy + 1.8, t * 24, g, 0.02)
    }
    box(5.8, 0.5, 26.0, wood, 0.0, y + 6.4, 0.0, g, 0.04)
    val r = mk(GEO.roof, "#6e3a2a", 0.0, y + 6.6, 0.0, g, 0.06); r.scale.set(8.2, 3.0, 27.0)
    for (s in listOf(-1.0, 1.0)) { val c = mk(GEO.cone6, "#6e3a2a", s * 3.9, y + 6.9, 13.6, g, 0.02); c.scale.set(0.5, 1.4, 0.5); c.rotation.z = -s * 0.9 }
    box(5.0, 4.2, 5.0, "#d8a060", -5.2, y + 4.6, 0.0, g, 0.04) // shrine
    val sr = mk(GEO.troof, "#6e3a2a", -5.2, y + 6.8, 0.0, g, 0.04); sr.scale.set(7.0, 2.0, 7.0)
    for (s in listOf(-1.0, 1.0)) box(1.4, 2.2, 1.4, "#a8a49a", 0.0, y + 1.1, s * 14, g, 0.03)
    colBox(x, z, 14.0, 3.4, 0.0, y + 1.5, y + 9.5, "bridge")
    keep(x, z, 14.0)
    disc("chuacau", "Hội An", "Chùa Cầu · Lai Viễn Kiều", "來遠橋", "Cây cầu gỗ có mái do thương nhân Nhật xây đầu thế kỷ 17, giữa cầu là ngôi miếu nhỏ — biểu tượng của Hội An.", x, y + 6, z, 60.0)
    LANDMARKS["chuacau"] = Anchor(x, y, z)
}

fun World.phucKien() {
    val x = 640.0; val z = 3462.0; val y = gy(x, z); val g = Group()
    g.position.set(x, y, z)
    scene.add(g)
    box(22.0, 1.0, 28.0, "#d8d2c4", 0.0, 0.5, 0.0, g, 0.03)
    box(18.0, 6.0, 12.0, "#d35d3a", 0.0, 4.0, 6.0, g)
    mk(GEO.troof, "#3f8f6a", 0.0, 7.0, 6.0, g, 0.06).scale.set(22.0, 3.4, 15.0)
    // ornate gate on the street side
    box(14.0, 7.0, 2.4, "#e8a33a", 0.0, 4.5, -12.0, g)
    for ((s, sc) in listOf(0.0 to 1.0, -4.6 to 0.6, 4.6 to 0.6)) { val r = mk(GEO.troof, "#3f8f6a", s, 8 + sc * 1.4, -12.0, g, 0.04); r.scale.set(6 * sc + 3, 2.2 * sc, 4.4) }
    for (s in listOf(-1.0, 1.0)) box(0.9, 7.0, 0.9, "#c0392b", s * 3, 4.5, -13.4, g, 0.02)
    colBox(x, z, 11.0, 14.0, 0.0, y - 1, y + 12, "temple")
    keep(x, z, 20.0)
    disc("phuckien", "Hội An", "Hội quán Phúc Kiến", "福建會館", "Hội quán của người Hoa gốc Phúc Kiến thờ Thiên Hậu Thánh Mẫu — cổng tam quan rực rỡ sắc đỏ, vàng, xanh ngọc.", x, y + 10, z, 60.0)
}

fun World.bayMau() {
    val cx = 1330.0; val cz = 3770.0
    var k = 0
    while (k < 900 * Q.dens + 200) {
        val a = rand() * 6.28; val r = sqrt(rand()) * 120; val x = cx + cos(a) * r * 1.3; val z = cz + sin(a) * r * 0.8; val y = gy(x, z)
        k++
        if (y < -0.2 || S.riv > 0.5) continue
        put("palm", x, max(y, 0.2) + 0.4, z, RR(3.2, 4.6), RR(2.2, 3.2), RR(3.2, 4.6), rand() * 6, pick(listOf("#4f9f3f", "#5aa845", "#6ab04c", "#3f8f3a")))
    }
    for (i in 0 until 26) {
        val p = linePoint(RIVERS[4], rand() * RIVERS[4].len)
        put("thung", p.x + RR(-3.0, 3.0), 0.05, p.z + RR(-3.0, 3.0), 1.05, 1.0, 1.05, rand() * 6, pick(listOf("#5b4630", "#4a3a28", "#3a7d44")))
    }
    disc("baymau", "Hội An", "Rừng dừa Bảy Mẫu", "", "Rừng dừa nước giữa vùng sông nước Cẩm Thanh — ngồi thúng chai xoay tít giữa những tàu lá dừa.", cx, 8.0, cz, 140.0)
    LANDMARKS["baymau"] = Anchor(cx, 0.0, cz)
}

fun World.traQue() {
    val cx = 880.0; val cz = 3255.0
    for (r in 0 until 26)
        for (k in 0 until 7) {
            val x = cx - 70 + k * 21; val z = cz - 52 + r * 4; val y = gy(x, z)
            put("wall", x, y + 0.25, z, 18.0, 0.5, 2.4, 0.0, pick(listOf("#5aa845", "#7fc25a", "#4f9f3f", "#9bd16a", "#6ab04c")))
        }
    for (k in 0 until 18) { val x = cx + RR(-70.0, 70.0); val z = cz + RR(-50.0, 50.0); put("hat", x, gy(x, z) + 0.4, z, 1.2, 1.2, 1.2, rand() * 6, pick(PALS.clothes)) }
    disc("traque", "Hội An", "Làng rau Trà Quế", "", "Làng rau thơm 300 năm tuổi bón bằng rong sông Cổ Cò — húng, quế, tía tô xanh mướt từng luống.", cx, 6.0, cz, 110.0)
}

/* countryside: paddies, hamlets, bamboo, buffaloes and conical hats */
fun World.countryside() {
    var x = -2300.0
    while (x < 1300) {
        var z = 1150.0
        while (z < 4900) {
            val xx = x + RR(-10.0, 10.0); val zz = z + RR(-10.0, 10.0)
            val h = gy(xx, zz)
            z += 26
            if (h < 0.6 || S.rice < 0.5 || S.M > 0.15 || S.hoian > 0.1 || S.riv > 0.1 || S.karst > 0 || kept(xx, zz)) continue
            val hamlet = sn(xx * 0.006 + 4, zz * 0.006 - 2)
            if (hamlet > 0.42) {
                if (rand() < 0.45) {
                    val w = RR(6.0, 10.0); val d = RR(5.0, 8.0); val hh = RR(3.4, 5.0)
                    put("wall", xx, h + hh / 2, zz, w, hh, d, 0.0, pick(PALS.rural))
                    put("roof", xx, h + hh, zz, d * 1.1, 2.2, w * 1.1, PI / 2, pick(PALS.ruralRoof))
                } else if (rand() < 0.5) put("crown", xx, h + 5, zz, RR(1.6, 2.4), RR(4.0, 6.0), RR(1.6, 2.4), rand() * 6, pick(listOf("#8fbf4a", "#7fae3e", "#9cc95a")))
                else tree(xx, zz, RR(2.0, 3.2))
            } else if (rand() < 0.035 * Q.dens) put("hat", xx, h, zz, 1.2, 1.2, 1.2, rand() * 6, pick(PALS.clothes))
            else if (rand() < 0.012) put("buffalo", xx, h, zz, 1.2, 1.2, 1.2, rand() * 6, pick(listOf("#4a4a52", "#5a5560", "#3f3c44")))
            else if (rand() < 0.02) tree(xx, zz, RR(2.2, 3.4))
        }
        x += 26
    }
}

/* =========================================================
   CÙ LAO CHÀM · HẢI VÂN · BÀ NÀ (Cầu Vàng)
   ========================================================= */
fun World.cuLaoCham() {
    lighthouse(4190.0, 2575.0, "Hải đăng Cù Lao Chàm", "hdclc", "Cù Lao Chàm", "", "")
    DISC.removeAt(DISC.size - 1)
    for (k in 0 until 14) {
        val x = 3480 + RR(-60.0, 60.0); val z = 2790 + RR(-20.0, 25.0); val y = gy(x, z)
        if (y < 1) continue
        val w = RR(5.0, 8.0); val d = RR(5.0, 7.0); val hh = RR(3.4, 4.6)
        put("wall", x, y + hh / 2, z, w, hh, d, 0.0, pick(PALS.rural))
        put("roof", x, y + hh, z, d * 1.1, 2.0, w * 1.1, PI / 2, pick(PALS.ruralRoof))
    }
    for (k in 0 until 30) put("boat", 3480 + RR(-90.0, 90.0), 0.1, 2850 + RR(10.0, 80.0), 0.8, 0.8, 0.8, rand() * 6, pick(PALS.boat))
    disc("culaocham", "Cù Lao Chàm", "Cù Lao Chàm", "占不勞", "Cụm đảo ngọc cách Hội An 15 km — khu dự trữ sinh quyển thế giới, rạn san hô và hang yến trên vách đá.", 3800.0, 80.0, 2700.0, 520.0)
    LANDMARKS["culaocham"] = Anchor(3800.0, 0.0, 2700.0)
}

fun World.haiVan() {
    val hi = findHigh(-2100.0, -3420.0, 120.0, 15.0); val x = hi[0]; val z = hi[1]; val h = hi[2]
    val g = Group()
    g.position.set(x, h - 0.5, z)
    g.rotation.y = 0.3
    scene.add(g)
    box(22.0, 9.0, 9.0, "#9a9488", 0.0, 4.5, 0.0, g)
    // web: rotation.x = π/2, rotation.z = π/2, order "ZXY" (= Rz·Rx) — baked into the geometry
    mk(Geo.cylinder(3.0, 3.0, 9.4, 14, 1, false, 0.0, PI).rotateX(PI / 2).rotateZ(PI / 2).addONormal(), "#3a3640", 0.0, 3.8, 0.0, g, 0.03)
    box(9.0, 4.0, 6.0, "#c8a46a", 0.0, 11.0, 0.0, g)
    mk(GEO.troof, "#7a3e2c", 0.0, 13.0, 0.0, g, 0.05).scale.set(13.0, 3.0, 9.0)
    colBox(x, z, 11.0, 4.5, 0.3, h - 2, h + 16, "gate")
    keep(x, z, 20.0)
    disc("haivan", "Hải Vân", "Đèo Hải Vân · Hải Vân Quan", "海雲關", "“Thiên hạ đệ nhất hùng quan” — cửa ải trên đỉnh đèo mây biển, ranh giới giữa Đà Nẵng và Huế.", x, h + 20, z, 160.0)
    LANDMARKS["haivan"] = Anchor(x, h, z)
}

fun World.baNa() {
    // find a moderately steep shoulder on the east face of Bà Nà for the bridge
    val pk = findHigh(-3450.0, -250.0, 260.0, 20.0)
    var best: DoubleArray? = null // [x, z, h, a, sc]
    var a0 = -0.7
    while (a0 <= 0.7) {
        val dx = cos(a0); val dz = sin(a0)
        var r = 60.0
        while (r < 700) {
            val x = pk[0] + dx * r; val z = pk[1] + dz * r; val h = gy(x, z)
            if (h < pk[2] * 0.7) { val s = slope(x, z); val sc = abs(s - 0.75); if (best == null || sc < best[4]) best = doubleArrayOf(x, z, h, a0, sc); break }
            r += 10
        }
        a0 += 0.1
    }
    val x = best!![0]; val z = best[1]; val h = best[2]; val a = best[3]
    val g = Group()
    g.position.set(x, 0.0, z)
    g.rotation.y = -a // bridge runs along the contour, bulging away from the peak
    scene.add(g)
    val deckY = h + 26; val pts = ArrayList<Vec3>()
    for (i in 0..40) { val t = i / 40.0; val ang = (t - 0.5) * 2.0; val R = 48.0; pts.add(Vec3(cos(ang) * R - R + 14, deckY + sin(t * PI) * 2.5, sin(ang) * R)) }
    val curve = CatmullRom3(pts)
    val gold = toon("#f2c230") { emissive.copy(Color.of("#ffcf5a")); emissiveIntensity = 0.1 }
    NIGHT.add { gl -> gold.emissiveIntensity = 0.1 + gl * 0.9 }
    val deck = mk(curve.tube(80, 3.0, 6).addONormal(), gold, 0.0, 0.0, 0.0, g, 0.06)
    deck.scale.y = 0.45
    deck.position.y = deckY * 0.55
    for (i in 0..40 step 2) {
        val p = curve.getPointAt(i / 40.0)
        for (s in listOf(-1.0, 1.0)) box(0.3, 1.8, 0.3, "#d4a017", p.x + s * 2.4, deckY + 1.6, p.z, g, 0.0)
    }
    // the two giant mossy stone hands cupping the deck from below
    val stone = "#8f8a7e"; val moss = "#7f8a6e"
    for (t in listOf(0.32, 0.68)) {
        val p = curve.getPointAt(t); val hg = Group()
        hg.position.set(p.x - 3, 0.0, p.z)
        g.add(hg)
        val wp = Vec3(p.x - 3, 0.0, p.z).applyAxisAngle(YUP, -a).add(Vec3(x, 0.0, z))
        val base = gy(wp.x, wp.z) - 6; val armH = deckY - 7 - base
        mk(lathe(listOf(doubleArrayOf(0.0, 0.0), doubleArrayOf(7.0, 0.0), doubleArrayOf(6.0, armH * 0.4), doubleArrayOf(4.6, armH * 0.85), doubleArrayOf(4.2, armH), doubleArrayOf(0.0, armH)), 12), moss, 0.0, base, 0.0, hg, 0.08)
        mk(geoBox(13.0, 4.5, 10.0), stone, 0.0, deckY - 5.2, 0.0, hg, 0.07) // palm under the walkway
        for (side in listOf(-1.0, 1.0)) {
            // fingers curl up on either side of the deck
            for (f in 0 until (if (side < 0) 4 else 1)) {
                val fg = mk(geoBox(2.2, 9.0, 2.2), stone, side * 5.6, deckY + 0.6, -3.6 + f * 2.4, hg, 0.05)
                fg.rotation.z = side * 0.28
                val tip = mk(geoBox(2.0, 3.2, 2.0), moss, side * 4.4, deckY + 5.6, -3.6 + f * 2.4, hg, 0.04)
                tip.rotation.z = side * 0.9
            }
        }
        colCyl(wp.x, wp.z, 7.5, base, deckY - 3, "hand")
        for (side in listOf(-1.0, 1.0)) { val fp = Vec3(p.x - 3 + side * 5.6, 0.0, p.z).applyAxisAngle(YUP, -a).add(Vec3(x, 0.0, z)); colCyl(fp.x, fp.z, 2.6, deckY - 3, deckY + 6, "hand") }
    }
    for (i in 0..20) { val p = curve.getPointAt(i / 20.0).clone().applyAxisAngle(YUP, -a).add(Vec3(x, 0.0, z)); colCyl(p.x, p.z, 3.0, deckY - 1.4, deckY + 2.4, "bridge") }
    keep(x, z, 60.0)
    disc("cauvang", "Bà Nà", "Cầu Vàng · Bà Nà", "", "Dải lụa vàng vắt qua đôi bàn tay đá khổng lồ trên đỉnh Bà Nà, giữa mây ngàn — thử bay luồn dưới cầu!", x, deckY, z, 150.0)
    val cw = curve.getPointAt(0.5).clone().applyAxisAngle(YUP, -a).add(Vec3(x, 0.0, z))
    LANDMARKS["cauvang"] = Anchor(cw.x, deckY, cw.z).also { it.dx = cos(a); it.dz = sin(a) }
    // cable car from the foothills
    val ax = -2350.0; val az = -120.0; val ay = gy(ax, az) + 6; val bx = x + 30; val bz = z + 6; val by = gy(bx, bz) + 8
    CABLE.a.set(ax, ay + 10, az); CABLE.b.set(bx, by + 10, bz)
    for ((px, py, pz) in listOf(Triple(ax, ay, az), Triple(bx, by, bz))) { box(12.0, 10.0, 10.0, "#e8e2d4", px, py - 1, pz, scene); colBox(px, pz, 6.0, 5.0, 0.0, py - 8, py + 4, "station") }
    val lp = floatArrayOf(
        CABLE.a.x.toFloat(), CABLE.a.y.toFloat(), CABLE.a.z.toFloat(), CABLE.b.x.toFloat(), CABLE.b.y.toFloat(), CABLE.b.z.toFloat(),
        CABLE.a.x.toFloat(), CABLE.a.y.toFloat(), (CABLE.a.z + 3).toFloat(), CABLE.b.x.toFloat(), CABLE.b.y.toFloat(), (CABLE.b.z + 3).toFloat(),
    )
    scene.add(Lines(Geometry.of(lp, null, null, null), LineMaterial(Color.of("#2f2d3c")), strip = false))
    val span = CABLE.a.distanceTo(CABLE.b)
    val nCars = floor(span / 70).toInt()
    for (k in 0 until nCars) {
        val c = Group()
        box(2.4, 2.4, 2.4, pick(listOf("#e8562a", "#f2c230", "#1f9a8a", "#e0418f")), 0.0, -3.0, 0.0, c, 0.04)
        box(0.2, 3.0, 0.2, "#3a3848", 0.0, -1.2, 0.0, c, 0.0)
        scene.add(c)
        CABLE.cars.add(World.CableCar(c, k.toDouble() / nCars, k % 2))
    }
    // pylons along the line
    for (k in 1 until 8) {
        val t = k / 8.0; val px = lerp(ax, bx, t); val pz = lerp(az, bz, t); val py = gy(px, pz); val ty = lerp(CABLE.a.y, CABLE.b.y, t)
        box(1.4, ty - py, 1.4, "#9a98a6", px, (ty + py) / 2, pz + 1.5, scene, 0.03)
        colCyl(px, pz + 1.5, 1.4, py, ty, "pylon")
    }
}

/* =========================================================
   NATURE scatter — Sơn Trà forest, mountains, islands, rocks
   ========================================================= */
private inline fun World.zone(x0: Double, x1: Double, z0: Double, z1: Double, step: Double, test: (Double, Double, Double) -> Boolean, place: (Double, Double, Double) -> Unit) {
    var x = x0
    while (x < x1) {
        var z = z0
        while (z < z1) {
            val xx = x + RR(-step * 0.45, step * 0.45); val zz = z + RR(-step * 0.45, step * 0.45)
            val h = gy(xx, zz)
            z += step
            if (h < 1 || kept(xx, zz) || S.riv > 0.05) continue
            if (test(xx, zz, h)) place(xx, zz, h)
        }
        x += step
    }
}

fun World.forests() {
    val sd = Q.dens
    // Sơn Trà: dense
    zone(-100.0, 2500.0, -3250.0, -1950.0, 17 / sqrt(sd), { x, z, h -> S.st > 12 && h > 3 && slope(x, z) < 1.8 }, { x, z, h -> tree(x, z, RR(2.4, 4.2) * (1 - h / 600), PALS.forest, rand() < 0.5) })
    // islands
    zone(2900.0, 4500.0, 2300.0, 3450.0, 18 / sqrt(sd), { _, _, h -> S.isl > 10 && h > 3 }, { x, z, _ -> tree(x, z, RR(2.4, 4.0), PALS.forest, false) })
    // western mountains (Hải Vân, Bà Nà, Trường Sơn foothills)
    zone(-4800.0, -1200.0, -4400.0, 5200.0, 46 / sqrt(sd), { x, z, h -> S.M > 0.25 && h > 12 && S.sd > 30 && slope(x, z) < 1.4 }, { x, z, _ -> tree(x, z, RR(4.5, 7.5), PALS.mtn, false) })
    // rocks on the rocky shores
    zone(-100.0, 4600.0, -3300.0, 3500.0, 14.0, { _, _, _ -> S.rock > 0.6 && S.sd > -12 && S.sd < 14 && rand() < 0.4 }, { x, z, h -> put("rock", x, h - 0.4, z, RR(2.0, 5.0), RR(1.4, 3.5), RR(2.0, 5.0), rand() * 6, pick(listOf("#b8b2a6", "#a8a296", "#c8c2b4"))) })
    // bay shore (Nguyễn Tất Thành) casuarinas + Liên Chiểu
    var x = -2200.0
    while (x < -100) {
        val ex = (x + 1000) / 1350; val zc = -2900 + 1000 * sqrt(max(0.0, 1 - ex * ex))
        for (k in 0 until 2) { val z = zc + RR(70.0, 130.0); gy(x, z); if (S.sd > 60 && !kept(x, z) && rand() < 0.5 * sd) casuarina(x, z, RR(0.8, 1.1)) }
        val zb = zc + RR(10.0, 40.0); val yb = gy(x, zb)
        if (yb > 0.4 && rand() < 0.06) put("person", x, yb, zb, 1.2, 1.2, 1.2, rand() * 6, pick(PALS.clothes))
        x += 9
    }
}
