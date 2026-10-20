package vn.paperworld.phidoi.world

import vn.paperworld.phidoi.engine.*
import vn.paperworld.phidoi.world.Geog.S
import vn.paperworld.phidoi.world.World.AHA
import vn.paperworld.phidoi.world.World.Companion.pt
import vn.paperworld.phidoi.world.World.PALS
import kotlin.math.*

/* =========================================================
   LANDMARKS — Đà Nẵng city & the Hàn river bridges
   ========================================================= */

fun World.bridgeDeck(name: String, z: Double, deckY: Double, half: Double, ramp: Double, w: Double, col: String = "#d9d6cf", split: Double = 0.0): Bridge {
    val x0 = Geog.lineXatZ(Geog.R_HAN, z); val g = Group()
    scene.add(g)
    val xa = x0 - half - ramp; val xb = x0 + half + ramp
    val ga = max(gy(xa, z), 1.0) + 0.4; val gb = max(gy(xb, z), 1.0) + 0.4
    val parts = if (split != 0.0) listOf(-(w / 4 + split / 2), w / 4 + split / 2) else listOf(0.0)
    val ww = if (split != 0.0) w / 2 - split / 2 else w
    val ends = listOf(Triple(x0 - half, ga, -1.0), Triple(x0 + half, gb, 1.0))
    for (oz in parts) {
        box(half * 2, 1.6, ww, col, x0, deckY - 0.8, z + oz, g, 0.06)
        for ((xs, gx, s) in ends) {
            val dy = deckY - gx
            val m = box(hypot(ramp, dy), 1.6, ww, col, xs + (s * ramp) / 2, (deckY + gx) / 2 - 0.8, z + oz, g, 0.06)
            m.rotation.z = -s * atan2(dy, ramp)
        }
        box(half * 2, 1.0, 0.3, "#8a8a96", x0, deckY + 0.5, z + oz - ww / 2 + 0.15, g, 0.03)
        box(half * 2, 1.0, 0.3, "#8a8a96", x0, deckY + 0.5, z + oz + ww / 2 - 0.15, g, 0.03)
    }
    colBox(x0, z, half, w / 2, 0.0, deckY - 1.7, deckY + 1.2, "bridge")
    for ((xs, gx, s) in ends)
        for (k in 0 until 4) { val t = (k + 0.5) / 4; val y = deckY + (gx - deckY) * t; colBox(xs + s * ramp * t, z, ramp / 8 + 1, w / 2, 0.0, -2.0, y + 1, "bridge") }
    // piers
    for (k in -1..1) {
        if (k == 0 && split != 0.0) continue
        val px = x0 + k * half * 0.55
        mk(GEO.cyl16, "#bdb9b0", px, (deckY - 1.6) / 2 - 1, z, g, 0.05).scale.set(1.6, deckY + 0.4, 1.6)
        colCyl(px, z, 2.0, -5.0, deckY - 1.6, "bridge")
    }
    val B = Bridge(name, z, x0, deckY, half, w, g)
    BRIDGES.add(B)
    keep(x0, z, half + ramp + 20)
    ROADS.add(listOf(pt(xa - 60, z), pt(xa, z), pt(xb, z), pt(xb + 60, z)))
    return B
}

/** line segments from a flat [x0,y0,z0, x1,y1,z1, ...] list */
private fun World.segLines(P: List<Double>, col: String) {
    val a = FloatArray(P.size) { P[it].toFloat() }
    scene.add(Lines(Geometry.of(a, null, null, null), LineMaterial(col), strip = false))
}

fun World.dragonBridge() {
    val z = -1020.0; val B = bridgeDeck("Cầu Rồng", z, 12.5, 74.0, 52.0, 16.0, "#d8d4ca", 3.2); val x0 = B.x0
    val scaleMat = toon("#f2b71e") { emissive.copy(Color.of("#ffb020")); emissiveIntensity = 0.0 }
    val finMat = toon("#e8762a") { emissive.copy(Color.of("#ff5a20")); emissiveIntensity = 0.0 }
    DRAGON.mats.add(scaleMat); DRAGON.mats.add(finMat)
    val pts = ArrayList<Vec3>()
    val L0 = x0 - 82; val L1 = x0 + 74
    for (i in 0..80) {
        val t = i / 80.0; val x = L0 + (L1 - L0) * t
        var y = 12.9 + 13 * abs(sin(PI * 2.5 * t)).pow(0.85)
        if (t < 0.06) y = 12.9 + (0.06 - t) * 140 // tail flick
        if (t > 0.94) y = lerp(y, 20.5, smooth(0.94, 1.0, t))
        pts.add(Vec3(x, y, z + sin(t * 9) * 0.4))
    }
    val curve = CatmullRom3(pts)
    val body = mk(curve.tube(220, 2.1, 10).addONormal(), scaleMat, 0.0, 0.0, 0.0, scene, 0.07)
    body.castShadow = true
    // spines + belly fins
    val spines = ArrayList<Geo.Part>()
    for (i in 1 until 60) {
        val t = i / 60.0; val p = curve.getPointAt(t); val tg = curve.getTangentAt(t)
        spines.add(P(GEO.cone6, p.x, p.y + 2.3, p.z, 0.55, 1.6, 0.9, 0.0, 0.0, -atan2(tg.y, tg.x) * 0.8))
        if (p.y > 14.5) colCyl(p.x, p.z, 2.6, p.y - 2.6, p.y + 2.8, "dragon")
    }
    mk(mergeGeo(spines), finMat, 0.0, 0.0, 0.0, scene, 0.03)
    // head (faces east, toward the sea)
    val hp = curve.getPointAt(1.0); val head = Group()
    head.position.copy(hp)
    scene.add(head)
    mk(geoBox(5.0, 3.6, 3.8), scaleMat, 1.5, 0.6, 0.0, head, 0.07)
    val snout = mk(GEO.cone6, scaleMat, 5.2, 0.4, 0.0, head, 0.06)
    snout.scale.set(1.6, 3.6, 1.4); snout.rotation.z = -PI / 2
    val jaw = mk(geoBox(4.2, 0.8, 2.6), finMat, 4.6, -1.6, 0.0, head, 0.05)
    jaw.rotation.z = -0.35
    for (s in doubleArrayOf(-1.0, 1.0)) {
        val horn = mk(GEO.cone6, "#f8e7b0", -0.6, 3.4, s * 1.2, head, 0.04)
        horn.scale.set(0.45, 3.4, 0.45); horn.rotation.set(s * 0.25, 0.0, 0.9)
        mk(GEO.sph, "#ffffff", 3.1, 1.6, s * 1.6, head, 0.03).setScale(0.62)
        mk(GEO.sph, "#1f1d2b", 3.5, 1.65, s * 1.75, head, 0.0).setScale(0.3)
        val wh = mk(geoBox(5.0, 0.15, 0.15), "#f8e7b0", 6.5, -0.6, s * 1.4, head, 0.02)
        wh.rotation.set(0.0, s * 0.5, -0.4)
        for (k in 0 until 3) { val m = mk(GEO.cone6, finMat, -1.6 - k * 1.1, 1.4, s * 1.8, head, 0.03); m.scale.set(0.6, 2.4, 0.4); m.rotation.set(s * 1.1, 0.0, 1.2) }
    }
    colCyl(hp.x + 2, hp.z, 4.0, hp.y - 3, hp.y + 4, "dragon")
    DRAGON.head.set(hp.x + 7.5, hp.y - 0.2, hp.z)
    DRAGON.headG = head
    LANDMARKS["dragon"] = Anchor(hp.x, hp.y, hp.z).also { it.x0 = x0 }
    disc("rong", "Sông Hàn", "Cầu Rồng", "龍橋", "Con rồng thép vàng vươn mình qua sông Hàn, đầu hướng ra biển. Tối cuối tuần rồng phun lửa và phun nước.", x0, 20.0, z, 140.0)
}

fun World.hanBridge() {
    val z = -1380.0; val B = bridgeDeck("Cầu Sông Hàn", z, 12.0, 62.0, 46.0, 13.0, "#d6d4cf"); val x0 = B.x0
    for (s in doubleArrayOf(-1.0, 1.0)) {
        val px = x0 + s * 28
        for (oz in doubleArrayOf(-6.0, 6.0)) { box(1.4, 34.0, 1.4, "#ecebe5", px, 17.0, z + oz, scene, 0.05); colCyl(px, z + oz, 1.6, 0.0, 34.0, "bridge") }
        box(1.6, 1.4, 13.4, "#ecebe5", px, 33.0, z, scene, 0.05)
        box(1.4, 1.2, 13.4, "#ecebe5", px, 20.0, z, scene, 0.05)
    }
    val P = ArrayList<Double>()
    for (s in doubleArrayOf(-1.0, 1.0))
        for (oz in doubleArrayOf(-6.0, 6.0))
            for (k in 1..7) {
                val px = x0 + s * 28
                P.addAll(listOf(px, 32.0, z + oz, px + (if (k % 2 != 0) 1 else -1) * k * 3.8, 12.4, z + oz))
            }
    segLines(P, "#3a3848")
    for (k in -6..6) {
        put("slamp", x0 + k * 10, 12.6, z - 6.6, 0.35, 0.35, 0.35, 0.0, if (k % 2 != 0) "#5ad1ff" else "#ff5ab4")
        put("slamp", x0 + k * 10, 12.6, z + 6.6, 0.35, 0.35, 0.35, 0.0, if (k % 2 != 0) "#ff5ab4" else "#5ad1ff")
    }
    disc("songhan", "Sông Hàn", "Cầu Sông Hàn", "", "Cây cầu quay đầu tiên của Việt Nam — nửa đêm hai nhịp cầu xoay ngang để tàu thuyền qua sông.", x0, 14.0, z, 110.0)
    LANDMARKS["songhan"] = Anchor(x0, 0.0, z)
}

fun World.thuanPhuoc() {
    val z = -1870.0; val B = bridgeDeck("Cầu Thuận Phước", z, 21.0, 92.0, 95.0, 14.0, "#d9d7d0"); val x0 = B.x0
    val towerH = 58.0
    for (s in doubleArrayOf(-1.0, 1.0)) {
        val px = x0 + s * 70
        for (oz in doubleArrayOf(-7.0, 7.0)) { box(2.2, towerH, 2.2, "#e4e2da", px, towerH / 2, z + oz, scene, 0.05); colCyl(px, z + oz, 2.0, 0.0, towerH, "bridge") }
        box(2.4, 2.0, 16.0, "#e4e2da", px, towerH - 1, z, scene, 0.05)
        box(2.4, 2.0, 16.0, "#e4e2da", px, 30.0, z, scene, 0.05)
    }
    val P = ArrayList<Double>()
    for (oz in doubleArrayOf(-7.0, 7.0)) {
        var prev: DoubleArray? = null
        for (i in 0..60) {
            val t = i / 60.0; val x = x0 - 170 + t * 340
            val ax = abs(x - x0)
            val y = if (ax < 70) 24 + (towerH - 24) * (ax / 70).pow(2)
            else towerH - (towerH - max(gy(x, z), 1.0) - 2) * ((ax - 70) / 100)
            if (prev != null) P.addAll(listOf(prev[0], prev[1], z + oz, x, y, z + oz))
            if (ax < 68 && i % 2 == 0) P.addAll(listOf(x, y, z + oz, x, 21.2, z + oz))
            prev = doubleArrayOf(x, y)
        }
    }
    segLines(P, "#2f2d3c")
    val cols = listOf("#ff5a5a", "#ffd35a", "#5affb4", "#5ab4ff", "#c45aff")
    for (k in -9..9) put("slamp", x0 + k * 10, 21.6, z, 0.4, 0.4, 0.4, 0.0, cols[(k + 9) % 5])
    disc("thuanphuoc", "Sông Hàn", "Cầu Thuận Phước", "順福橋", "Cầu treo dây võng dài nhất Việt Nam nơi cửa sông Hàn đổ ra vịnh — nhìn thẳng sang bán đảo Sơn Trà.", x0, 30.0, z, 140.0)
    LANDMARKS["thuanphuoc"] = Anchor(x0, 0.0, z)
}

fun World.tranThiLy() {
    val z = -700.0; val B = bridgeDeck("Cầu Trần Thị Lý", z, 12.0, 62.0, 46.0, 14.0, "#dcdad2"); val x0 = B.x0
    val g = Group()
    g.position.set(x0 + 22, 0.0, z)
    scene.add(g)
    val pyl = mk(geoBox(3.0, 62.0, 3.0), "#f4f2ea", 0.0, 31.0, 0.0, g, 0.06)
    pyl.rotation.z = 0.32
    val top = Vec3(-sin(0.32) * 62 + 0, cos(0.32) * 62, 0.0).add(g.position)
    val P = ArrayList<Double>()
    for (k in 0 until 12) {
        val tx = x0 - 55 + k * 8.5; val f = 0.35 + 0.6 * (k / 12.0); val px = g.position.x - sin(0.32) * 62 * f; val py = cos(0.32) * 62 * f
        for (oz in doubleArrayOf(-0.6, 0.6)) P.addAll(listOf(px, py, z + oz, tx, 12.3, z + oz * 9))
    }
    segLines(P, "#3a3848")
    for (k in 0 until 10) { val f = k / 10.0; colCyl(g.position.x - sin(0.32) * 62 * f, z, 2.4, cos(0.32) * 62 * f - 4, cos(0.32) * 62 * f + 4, "bridge") }
    disc("tranthily", "Sông Hàn", "Cầu Trần Thị Lý", "", "Trụ tháp nghiêng hình cánh buồm trắng, dây văng xoè như mái chèo — sáng đèn đổi màu mỗi tối.", x0, 30.0, z, 110.0)
    // web: top is the pylon tip (Vector3); Anchor keeps its height
    LANDMARKS["tranthily"] = Anchor(x0, 0.0, z).also { it.top = top.y }
}

fun World.tienSon() {
    val z = -320.0
    bridgeDeck("Cầu Tiên Sơn", z, 11.0, 64.0, 40.0, 14.0, "#d4d2ca")
}

fun World.sunWheel() {
    val x = -255.0; val z = -430.0; val y0 = gy(x, z); val R = 23.0; val cy = y0 + R + 6; val g = Group(); val wheel = Group()
    g.position.set(x, cy, z)
    scene.add(g)
    for (s in doubleArrayOf(-1.0, 1.0))
        for (oz in doubleArrayOf(-1.0, 1.0)) {
            val leg = box(1.2, R + 9, 1.2, "#e8e6e0", s * 3, -(R + 6) / 2 + 1, oz * 9, g, 0.05)
            leg.rotation.x = oz * 0.36
        }
    box(7.0, 1.2, 1.2, "#e8e6e0", 0.0, 0.0, 0.0, g, 0.05)
    g.add(wheel)
    val rimMat = tintedGlow(0.1, 2.0)
    val rim = Mesh(Geo.torus(R, 0.55, 6, 72).addONormal(), toon("#f0eee8"))
    outlined(rim, 0.05)
    rim.rotation.y = PI / 2
    wheel.add(rim)
    val rim2 = outlined(Mesh(rim.geometry, rim.material), 0.05) // rim.clone()
    rim2.rotation.y = PI / 2
    rim2.setScale(0.94)
    wheel.add(rim2)
    val lights = InstancedMesh(GEO.sph, rimMat, 72, true)
    val lc = Color(); val m4 = Mat4(); val p = Vec3(); val q = Quat(); val sc = Vec3()
    for (k in 0 until 72) {
        val a = (k / 72.0) * PI * 2
        m4.compose(p.set(0.0, sin(a) * R, cos(a) * R), q.identity(), sc.set(0.45, 0.45, 0.45))
        lights.data.setMatrixAt(k, m4)
        lights.data.setColorAt(k, lc.setHSL(k / 72.0, 0.85, 0.6))
    }
    lights.data.computeBoundingSphere(GEO.sph)
    wheel.add(lights)
    for (k in 0 until 18) {
        val a = (k / 18.0) * PI * 2; val sp = box(0.25, R * 2, 0.25, "#d0cec8", 0.0, 0.0, 0.0, wheel, 0.0)
        sp.rotation.x = a
    }
    val gond = ArrayList<Mesh>()
    for (k in 0 until 32) {
        val a = (k / 32.0) * PI * 2; val c = mk(geoBox(1.6, 2.0, 2.0), pick(listOf("#e8562a", "#2a9fd6", "#f2c230", "#1f9a8a", "#e0418f")), 0.0, sin(a) * (R + 1.4), cos(a) * (R + 1.4), wheel, 0.04)
        gond.add(c)
    }
    ANIM.add { _, t ->
        wheel.rotation.x = t * 0.05
        for (c in gond) c.rotation.x = -wheel.rotation.x
        rimMat.emissive.setHSL((t * 0.05) % 1, 0.6, 0.6)
    }
    colBox(x, z, 4.0, R + 2, 0.0, cy - R - 2, cy + R + 2, "wheel")
    colCyl(x, z, 4.0, 0.0, cy, "wheel")
    keep(x, z, 50.0); keep(x + 95, z - 55, 55.0) // Asia Park grounds
    disc("sunwheel", "Sông Hàn", "Vòng quay Mặt Trời", "", "Sun Wheel cao 115 m bên bờ tây sông Hàn — một trong những vòng quay lớn nhất thế giới, rực đèn về đêm.", x, cy, z, 110.0)
    LANDMARKS["sunwheel"] = Anchor(x, cy, z)
}

fun World.cathedral() {
    val x = -330.0; val z = -1240.0; val y = gy(x, z); val g = Group(); val pink = "#f2a5b3"
    g.position.set(x, y, z)
    scene.add(g)
    box(12.0, 11.0, 26.0, pink, 0.0, 5.5, 2.0, g)
    val r = mk(GEO.roof, "#c9707f", 0.0, 11.0, 2.0, g, 0.05); r.scale.set(13.0, 6.0, 27.0)
    box(7.0, 22.0, 7.0, pink, 0.0, 11.0, -12.0, g)
    val sp = mk(GEO.pyr, "#c9707f", 0.0, 22.0 + 6, -12.0, g, 0.05); sp.scale.set(5.0, 12.0, 5.0)
    box(0.25, 3.0, 0.25, "#3a3848", 0.0, 35.0, -12.0, g, 0.0)
    val rooster = box(1.4, 1.1, 0.25, "#d4a017", 0.0, 36.6, -12.0, g, 0.02)
    ANIM.add { _, t -> rooster.rotation.y = sin(t * 0.3) * 1.2 }
    for (s in doubleArrayOf(-1.0, 1.0)) { box(3.0, 15.0, 3.0, pink, s * 5.5, 7.5, -12.0, g); val p = mk(GEO.pyr, "#c9707f", s * 5.5, 17.0, -12.0, g, 0.04); p.scale.set(2.4, 5.0, 2.4) }
    colBox(x, z + 2, 6.0, 13.0, 0.0, y, y + 17, "bld")
    colCyl(x, z - 12, 4.0, y, y + 34, "bld")
    keep(x, z, 28.0)
    disc("nhatho", "Trung tâm", "Nhà thờ Con Gà", "", "Nhà thờ Chính toà Đà Nẵng màu hồng, xây năm 1923 — trên đỉnh tháp có con gà trống chỉ hướng gió.", x, y + 30, z, 90.0)
}

/* Ahamove: Sky Hub tower with a rooftop helipad, plus pads at Mỹ Khê and Hội An */
fun World.ahamoveHub() {
    val x = 235.0; val z = -1260.0; val y = gy(x, z); val H = 50.0; val W = 20.0; val g = Group()
    g.position.set(x, y, z)
    scene.add(g)
    mk(geoBox(W, H, W), bldMat, 0.0, H / 2, 0.0, g, 0.07)
    for (k in 0 until 4) box(W + 0.8, 2.6, W + 0.8, if (k % 2 != 0) AHA.blue else AHA.orange, 0.0, 8.0 + k * 11, 0.0, g, 0.05)
    box(W + 1.6, 1.4, W + 1.6, AHA.orange, 0.0, H + 0.7, 0.0, g, 0.06)
    // web: map and emissiveMap are two identical ahaTex canvases; here the emissive reads the map
    val sign = toon("#ffffff") { map = ahaTex(AHA.orange, "#ffffff", "ahamove"); emissive.copy(Color.of("#ffffff")); emissiveMap = true; emissiveIntensity = 0.15 }
    NIGHT.add { gl -> sign.emissiveIntensity = 0.15 + gl * 0.9 }
    for (k in 0 until 4) {
        val s = Mesh(Geo.plane(16.0, 4.0), sign); val a = (k * PI) / 2
        s.position.set(sin(a) * (W / 2 + 0.95), H - 4.5, cos(a) * (W / 2 + 0.95))
        s.rotation.y = a
        g.add(s)
    }
    colBox(x, z, W / 2 + 0.8, W / 2 + 0.8, 0.0, y - 2, y + H + 1.4, "bld")
    val pad = helipad(x, y + H + 1.4, z, 8.5)
    pad.hub = true
    for (k in 0 until 8) { val a = (k / 8.0) * PI * 2; put("slamp", x + cos(a) * 9.4, y + H + 1.9, z + sin(a) * 9.4, 0.35, 0.35, 0.35, 0.0, if (k % 2 != 0) AHA.orange else "#5ab4ff") }
    keep(x, z, 34.0)
    disc("ahahub", "Trung tâm", "Ahamove Sky Hub", "", "Toà nhà cam – xanh bên sông Hàn với sân đỗ trực thăng giao hàng trên nóc. Thử đáp trực thăng Ahamove xuống chữ H!", x, y + H, z, 90.0)
    LANDMARKS["hub"] = Anchor(x, y + H + 1.4, z)
    // ground pads by the sea and in Hội An
    val mx = Geog.coastX(-300.0) - 120; val mz = -300.0
    helipad(mx, gy(mx, mz) + 0.1, mz, 9.0)
    keep(mx, mz, 22.0)
    helipad(960.0, gy(960.0, 3330.0) + 0.1, 3330.0, 9.0)
    keep(960.0, 3330.0, 22.0)
}

/** the Ahamove delivery helicopter's parts (web: g.userData.rotor / tail / disc / tips) */
class Heli(val g: Group, val rotor: Group, val tail: Group, val disc: Mesh, val discMat: BasicMaterial, val tips: List<Vec3>)

/* Ahamove delivery helicopter (forward = −z) */
private fun World.ahaSide() = ahaTex("#ffffff", AHA.orange, "ahamove", 512, 128)

fun World.makeHeli(parcel: Boolean = false): Heli {
    val g = Group(); val O = AHA.orange; val B = AHA.blue
    mk(GEO.sph, O, 0.0, 0.0, -0.3, g, 0.06).scale.set(1.6, 1.45, 2.6)
    mk(GEO.sph, "#9fd8f0", 0.0, 0.28, -2.0, g, 0.04).scale.set(1.25, 1.05, 1.3)
    box(3.26, 0.4, 3.6, B, 0.0, -0.35, -0.2, g, 0.03)
    box(0.55, 0.55, 5.6, O, 0.0, 0.35, 3.7, g, 0.04)
    box(0.16, 1.9, 1.2, B, 0.0, 1.15, 6.2, g, 0.03)
    box(2.2, 0.12, 0.8, B, 0.0, 0.45, 5.6, g, 0.03)
    val tr = Group()
    tr.position.set(0.32, 1.1, 6.25)
    g.add(tr)
    box(0.06, 2.0, 0.24, "#2b2f3a", 0.0, 0.0, 0.0, tr, 0.0)
    mk(GEO.cyl, "#2b2f3a", 0.0, 1.55, -0.3, g, 0.02).scale.set(0.35, 0.6, 0.35)
    val rotor = Group()
    rotor.position.set(0.0, 1.95, -0.3)
    g.add(rotor)
    box(11.5, 0.08, 0.45, "#2b2f3a", 0.0, 0.0, 0.0, rotor, 0.02)
    box(0.45, 0.08, 11.5, "#2b2f3a", 0.0, 0.0, 0.0, rotor, 0.02)
    val discMat = BasicMaterial("#ffffff").also { it.transparent = true; it.opacity = 0.12; it.depthWrite = false }
    val disc = Mesh(Geo.circle(5.8, 28).rotateX(-PI / 2), discMat)
    disc.position.y = 1.95
    disc.position.z = -0.3
    g.add(disc)
    for (s in doubleArrayOf(-1.0, 1.0)) {
        box(0.16, 0.16, 4.6, "#2b2f3a", s * 1.25, -1.75, -0.3, g, 0.02)
        for (zz in doubleArrayOf(-1.5, 0.9)) box(0.1, 0.9, 0.1, "#2b2f3a", s * 1.05, -1.3, zz, g, 0.0)
        val t = Mesh(Geo.plane(2.7, 0.68), toon("#ffffff") { map = ahaSide() })
        t.position.set(s * 1.63, 0.15, -0.2)
        t.rotation.y = s * PI / 2
        g.add(t)
    }
    if (parcel) {
        box(1.4, 1.1, 1.4, O, 0.0, -4.2, -0.3, g, 0.04)
        box(1.45, 0.25, 0.3, B, 0.0, -3.8, -0.3, g, 0.0)
        val rope = Lines(Geometry.of(floatArrayOf(0f, -1.6f, -0.3f, 0f, -3.6f, -0.3f), null, null, null), LineMaterial("#3a3848"), strip = true)
        g.add(rope)
    }
    g.traverse { it.castShadow = true }
    val h = Heli(g, rotor, tr, disc, discMat, listOf(Vec3(-5.6, 1.95, -0.3), Vec3(5.6, 1.95, -0.3)))
    g.userData = h
    return h
}

fun World.cityIcons() {
    ahamoveHub()
    // Administration Centre ("corn cob") and riverside towers
    val ax = -250.0; val az = -1150.0; val ay = gy(ax, az)
    val t = mk(Geo.cylinder(8.0, 10.0, 66.0, 10).addONormal(), bldMat, ax, ay + 33, az, scene, 0.06)
    t.castShadow = true
    mk(Geo.cylinder(4.0, 8.0, 8.0, 10).addONormal(), "#cfe0ea", ax, ay + 70, az, scene, 0.05)
    colCyl(ax, az, 10.0, ay, ay + 74, "bld")
    keep(ax, az, 24.0)
    val nx = -140.0; val nz = -1525.0; val ny = gy(nx, nz)
    put("bld", nx, ny + 27, nz, 18.0, 54.0, 14.0, 0.0, "#d8e6ec")
    put("bld", nx, ny + 56, nz, 10.0, 4.0, 8.0, 0.0, "#f2f2ee")
    colBox(nx, nz, 9.0, 7.0, 0.0, ny, ny + 58, "bld")
    keep(nx, nz, 18.0)
    // Cá chép hoá rồng + Cầu Tình Yêu (east bank, north of the Dragon Bridge)
    val cx = Geog.lineXatZ(Geog.R_HAN, -1100.0) + 62; val cz = -1100.0; val cy = gy(cx, cz)
    val g = Group()
    g.position.set(cx, max(cy, 0.8), cz)
    scene.add(g)
    mk(GEO.cyl16, "#d8d4ca", 0.0, 1.0, 0.0, g, 0.04).scale.set(5.0, 2.0, 5.0)
    val carp = mk(Geo.sphere(1.0, 12, 8).addONormal(), "#f7f5ef", 0.0, 7.5, 0.0, g, 0.05)
    carp.scale.set(1.8, 5.0, 1.5); carp.rotation.z = 0.35
    val tail = mk(GEO.cone6, "#f7f5ef", -1.6, 2.6, 0.0, g, 0.04); tail.scale.set(2.2, 2.4, 0.6); tail.rotation.z = 2.4
    val headD = mk(GEO.cone6, "#f2c230", 1.7, 12.6, 0.0, g, 0.04); headD.scale.set(1.2, 2.6, 1.0); headD.rotation.z = -0.5
    colCyl(cx, cz, 4.0, 0.0, 15.0, "statue")
    for (k in 0 until 14) put("lantern", cx - 10 + k * 1.6, 2.2, cz + 9, 0.45, 0.45, 0.45, 0.0, "#ff3a5a")
    for (k in 0 until 9) box(1.6, 0.3, 3.0, "#a0785a", cx - 14 + k * 2, 1.1, cz + 9, scene, 0.02)
    keep(cx, cz, 18.0)
    disc("cachep", "Sông Hàn", "Cá chép hoá rồng", "鯉魚化龍", "Tượng cá chép vượt vũ môn hoá rồng phun nước bên Cầu Tình Yêu, nơi các cặp đôi treo khoá tình yêu.", cx, 10.0, cz, 70.0)
}

/* Đà Nẵng airport: runway + terminal + paper airliners */
fun World.airport() {
    val x = AIRPORT.x; val z0 = AIRPORT.z0; val z1 = AIRPORT.z1
    val runway = Mesh(Geo.box(18.0, 0.3, z1 - z0), toon("#6a6878") { polygonOffset = true; polyFactor = -1f })
    runway.position.set(x, 2.75, (z0 + z1) / 2)
    runway.receiveShadow = true
    scene.add(runway)
    var z = z0 + 30
    while (z < z1 - 30) { box(0.8, 0.12, 9.0, "#f4f2ea", x, 2.95, z, scene, 0.0); z += 24 }
    for (zz in doubleArrayOf(z0 + 8, z1 - 8)) for (k in -3..3) box(1.2, 0.12, 8.0, "#f4f2ea", x + k * 2.2, 2.95, zz, scene, 0.0)
    val tw = Mesh(Geo.box(10.0, 0.25, z1 - z0 - 80), toon("#77758a"))
    tw.position.set(x + 34, 2.7, (z0 + z1) / 2)
    scene.add(tw)
    val ty = 2.6
    box(40.0, 9.0, 90.0, "#e8eef2", x + 95, ty + 4.5, -120.0, scene)
    val wroof = mk(Geo.cylinder(30.0, 30.0, 94.0, 16, 1, false, -0.6, 1.2).addONormal(), "#cfd8de", x + 95, ty + 9 - 26, -120.0, scene, 0.05)
    wroof.rotation.x = PI / 2; wroof.rotation.y = PI / 2
    box(3.0, 26.0, 3.0, "#e8eef2", x + 70, 13.0, -40.0, scene)
    box(7.0, 4.0, 7.0, "#9fc6d6", x + 70, 27.0, -40.0, scene)
    colBox(x + 95, -120.0, 20.0, 45.0, 0.0, 0.0, 16.0, "bld")
    colCyl(x + 70, -40.0, 4.0, 0.0, 30.0, "bld")
    keep(x + 40, (z0 + z1) / 2, 0.0)
    disc("sanbay", "Trung tâm", "Sân bay Đà Nẵng", "", "Sân bay ngay giữa lòng thành phố — máy bay cất cánh bay thẳng ra vịnh Đà Nẵng.", x, 20.0, -100.0, 160.0)
    LANDMARKS["airport"] = Anchor(x).also { it.z0 = z0; it.z1 = z1 }
}

/* city blocks */
fun World.inAirport(x: Double, z: Double) = x > AIRPORT.x - 120 && x < AIRPORT.x + 150 && z > AIRPORT.z0 - 60 && z < AIRPORT.z1 + 60

private val LOTS_1 = listOf(doubleArrayOf(0.0, 0.0, 58.0, 58.0))
private val LOTS_2 = listOf(doubleArrayOf(-15.0, 0.0, 28.0, 58.0), doubleArrayOf(15.0, 0.0, 28.0, 58.0))
private val LOTS_4 = listOf(doubleArrayOf(-15.0, -15.0, 28.0, 28.0), doubleArrayOf(15.0, -15.0, 28.0, 28.0), doubleArrayOf(-15.0, 15.0, 28.0, 28.0), doubleArrayOf(15.0, 15.0, 28.0, 28.0))

fun World.cityBlocks() {
    val BS = 70.0
    var bz = -2230.0
    while (bz < 720) {
        var bx = -1820.0
        while (bx < 720) {
            val cx = bx + BS / 2; val cz = bz + BS / 2
            bx += BS
            gy(cx, cz)
            if (S.city < 0.6 || S.sd < 95 || inAirport(cx, cz) || kept(cx, cz)) continue
            val rd = Geog.lineDist(Geog.R_HAN, cx, cz)
            if (rd < 82) continue
            val riverP = smooth(300.0, 90.0, rd); val beachP = smooth(320.0, 110.0, S.sd); val coreP = smooth(900.0, 200.0, hypot(cx + 60, cz + 1100))
            if (rand() < 0.07) { // park
                for (k in 0 until 8) tree(cx + RR(-28.0, 28.0), cz + RR(-28.0, 28.0), RR(2.6, 4.0), PALS.forest)
                continue
            }
            val lots = if (rand() < 0.3) LOTS_1 else if (rand() < 0.5) LOTS_2 else LOTS_4
            for (lot in lots) {
                val ox = lot[0]; val oz = lot[1]; val lw = lot[2]; val ld = lot[3]
                val x = cx + ox; val z = cz + oz; val y = gy(x, z)
                if (Geog.lineDist(Geog.R_HAN, x, z) < 70 || S.sd < 90 || S.karst > 0 || kept(x, z)) continue
                val tall = rand() < 0.04 + 0.42 * max(riverP, beachP) * (0.4 + 0.6 * coreP)
                var w = lw - RR(4.0, 9.0); var d = ld - RR(4.0, 9.0); var h = RR(6.0, 13.0) + 10 * coreP * rand()
                if (tall) { h = RR(26.0, 64.0); w = min(w, RR(14.0, 22.0)); d = min(d, RR(12.0, 20.0)) }
                if (lw > 50 && !tall) h = RR(10.0, 18.0)
                val wallC = pick(if (tall) PALS.tall else PALS.city)
                put("bld", x, y + h / 2, z, w, h, d, 0.0, wallC)
                colBox(x, z, w / 2, d / 2, 0.0, y - 2, y + h, "bld")
                if (tall) {
                    // a coloured crown band and a set-back top, like a painted toy tower
                    val bandC = pick(PALS.band.filter { it != wallC })
                    put("wall", x, y + h - 1.2, z, w + 0.6, 2.4, d + 0.6, 0.0, bandC)
                    if (rand() < 0.6) { put("bld", x, y + h + 2, z, w * 0.6, 4.0, d * 0.6, 0.0, pick(PALS.band)); colBox(x, z, w * 0.3, d * 0.3, 0.0, y, y + h + 4, "bld") }
                } else if (h < 18 && rand() < 0.6) {
                    // pitched orange or blue roof, like the houses in Paper World
                    val rh = RR(2.4, 4.2)
                    put("roof", x, y + h, z, w * 1.04, rh, d * 1.04, if (rand() < 0.5) 0.0 else PI / 2, pick(PALS.roofs))
                    colBox(x, z, w / 2, d / 2, 0.0, y, y + h + rh, "bld")
                } else {
                    put("wall", x, y + h + 0.4, z, w + 0.4, 0.8, d + 0.4, 0.0, pick(PALS.band))
                    if (rand() < 0.4) put("wall", x + RR(-w / 4, w / 4), y + h + 1.4, z + RR(-d / 4, d / 4), 2.4, 1.6, 2.4, 0.0, "#cfd2d8")
                }
            }
            if (rand() < 0.5) tree(cx + 33, cz + 33, RR(2.2, 3.2), PALS.forest)
        }
        bz += BS
    }
}
