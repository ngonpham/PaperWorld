package vn.paperworld.phidoi.world

import vn.paperworld.phidoi.world.Geog.R_COCO
import vn.paperworld.phidoi.world.World.PALS
import vn.paperworld.phidoi.world.Geog.S
import vn.paperworld.phidoi.world.Geog.coastX
import vn.paperworld.phidoi.world.Geog.lineDist
import vn.paperworld.phidoi.world.Geog.sdIslands
import vn.paperworld.phidoi.world.World.Companion.pt
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max

/* =========================================================
   COAST — Mỹ Khê → Non Nước → An Bàng → Cửa Đại
   ========================================================= */

private val THUNG_COLS = listOf("#5b4630", "#4a3a28", "#2f6f9f", "#3a7d44", "#5b4630")

fun World.coast() {
    val road = ArrayList<DoubleArray>()
    run {
        var z = -2140.0
        while (z <= 3520) {
            val x = coastX(z) - 86
            road.add(pt(x, z))
            z += 20
        }
    }
    ribbon(road, 14.0)
    ROADS.add(road)
    val CARS = ArrayList<Double>()
    run {
        var z = -2140.0
        while (z <= 3520) {
            val xc = coastX(z)
            gy(xc - 20, z)
            val busy = if (z < 600) 1.0 else if (z > 2900) 0.8 else 0.35
            // umbrellas, beds and swimmers on the sand
            val cluster = if (sn(z * 0.012, 3.3) > -0.25) busy else busy * 0.15
            if (rand() < 0.28 * cluster * Q.dens) {
                val x = xc - RR(16.0, 40.0); val y = gy(x, z)
                if (S.sd > 6 && S.sd < 46 && y > 0.5 && !kept(x, z)) {
                    put("umb", x, y, z, 1.0, 1.0, 1.0, rand() * 6, pick(PALS.umb))
                    put("pole", x, y, z, 1.0, 1.0, 1.0, 0.0, "#f4f2ea")
                    if (rand() < 0.7) {
                        put("bed", x + 1.6, y, z + 0.8, 1.0, 1.0, 1.0, RR(-0.3, 0.3) + PI / 2, "#ffffff")
                        put("bed", x + 1.6, y, z - 0.9, 1.0, 1.0, 1.0, RR(-0.3, 0.3) + PI / 2, "#ffffff")
                    }
                }
            }
            if (rand() < 0.25 * cluster * Q.dens) {
                val x = xc + RR(-34.0, 18.0); val y = max(gy(x, z), 0.0)
                put("person", x, y - (if (y <= 0.01) 0.75 else 0.0), z, 1.2, 1.2, 1.2, rand() * 6, pick(PALS.clothes))
            }
            // casuarina belt south of the city, palms by the sand
            if (z > 650 && rand() < 0.55 * Q.dens) {
                val x = xc - RR(100.0, 200.0); gy(x, z)
                if (S.sd > 92 && S.city < 0.3 && !kept(x, z) && S.karst < 0.05) casuarina(x, z, RR(0.8, 1.25))
            }
            if (rand() < 0.07 * Q.dens) {
                val x = xc - RR(48.0, 74.0); gy(x, z)
                if (S.sd > 44 && S.sd < 80 && !kept(x, z)) palm(x, z, RR(0.85, 1.2))
            }
            // streetlights
            if (floor(z / 3.5 + 0.5).toLong() % 9 == 0L) {
                val x = xc - 78; val y = gy(x, z)
                put("pole", x, y + 1.5, z, 1.0, 1.6, 1.0, 0.0, "#4a4858")
                put("slamp", x, y + 6.5, z, 0.5, 0.4, 0.5, 0.0, "#fff1c4")
            }
            if (rand() < 0.04) CARS.add(z)
            z += 3.5
        }
    }
    COAST_CARS.addAll(CARS)
    // basket boats (thúng chai) — clusters at the fishing beaches
    val spots = listOf(doubleArrayOf(-1750.0, 30.0), doubleArrayOf(-1320.0, 22.0), doubleArrayOf(-420.0, 18.0), doubleArrayOf(880.0, 26.0), doubleArrayOf(1600.0, 18.0), doubleArrayOf(2600.0, 22.0), doubleArrayOf(3350.0, 24.0))
    for ((zc, n) in spots.map { it[0] to it[1] }) {
        var k = 0
        while (k < n * Q.dens + 6) {
            val z = zc + RR(-60.0, 60.0); val xc = coastX(z); val onWater = rand() < 0.35
            val x = if (onWater) xc + RR(20.0, 120.0) else xc - RR(4.0, 16.0)
            val y = if (onWater) 0.05 else gy(x, z)
            put("thung", x, y, z, RR(0.95, 1.2), 1.0, RR(0.95, 1.2), rand() * 6, pick(THUNG_COLS))
            k++
        }
    }
    disc("thungchai", "Biển", "Thúng chai Mỹ Khê", "", "Những chiếc thuyền thúng tròn đan bằng tre của ngư dân, phơi trên cát mỗi sớm sau chuyến đánh cá đêm.", coastX(-1750.0) - 8, 6.0, -1750.0, 80.0)
    // fishing boats anchored offshore + at Thọ Quang
    run {
        var k = 0
        while (k < 120 * Q.dens + 30) {
            k++
            val z = RR(-2100.0, 3800.0); val x = coastX(z) + RR(120.0, 900.0)
            if (sdIslands(x, z) > -40) continue
            put("boat", x, 0.1, z, 1.0, 1.0, 1.0, RR(-0.4, 0.4) + (if (rand() < 0.5) 0.0 else PI), pick(PALS.boat))
            BOAT_LIGHTS.add(doubleArrayOf(x, z))
        }
    }
    for (k in 0 until 40) put("boat", RR(150.0, 420.0), 0.1, RR(-2280.0, -2150.0), 0.9, 0.9, 0.9, PI / 2 + RR(-0.3, 0.3), pick(PALS.boat))
    disc("mykhe", "Biển", "Bãi biển Mỹ Khê", "美溪", "Một trong những bãi biển đẹp nhất hành tinh — cát trắng mịn, sóng êm, dài hút tầm mắt từ chân Sơn Trà.", coastX(-800.0), 10.0, -800.0, 200.0)
    disc("cuadai", "Hội An", "Biển An Bàng · Cửa Đại", "", "Bãi biển hoang sơ của Hội An, nơi sông Thu Bồn đổ ra biển — hàng dừa nghiêng và quán chòi lá.", coastX(3300.0) - 20, 10.0, 3300.0, 220.0)
    // lifeguard towers
    run {
        var z = -1900.0
        while (z < 500) {
            val x = coastX(z) - 30; val y = gy(x, z)
            put("ltower", x, y, z, 1.0, 1.0, 1.0, PI / 2, "#e8562a")
            z += 260
        }
    }
    // south coast resorts
    var z = 650.0
    while (z < 3000) {
        val zr = z
        z += 120
        if (rand() < 0.45) continue
        val xc = coastX(zr) - RR(150.0, 220.0)
        gy(xc, zr)
        if (S.karst > 0 || kept(xc, zr) || lineDist(R_COCO, xc, zr) < 40) continue
        for (k in 0 until 4) {
            val x = xc + RR(-30.0, 30.0); val zz = zr + RR(-35.0, 35.0); val y = gy(x, zz); val w = RR(10.0, 18.0); val d = RR(8.0, 14.0); val h = RR(6.0, 15.0)
            if (S.karst > 0 || kept(x, zz)) continue
            put("bld", x, y + h / 2, zz, w, h, d, 0.0, pick(PALS.city))
            put("roof", x, y + h, zz, w * 1.04, RR(2.2, 3.4), d * 1.04, 0.0, pick(PALS.roofs))
            colBox(x, zz, w / 2, d / 2, 0.0, y - 1, y + h, "bld")
        }
        put("pool", xc + 40, gy(xc + 40, zr) + 0.3, zr, 14.0, 0.5, 8.0, 0.0, "#2fb3bf")
        for (k in 0 until 6) palm(xc + RR(20.0, 60.0), zr + RR(-30.0, 30.0), RR(0.8, 1.1))
    }
}
