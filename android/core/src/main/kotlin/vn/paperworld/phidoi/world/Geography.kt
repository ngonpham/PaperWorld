package vn.paperworld.phidoi.world

import vn.paperworld.phidoi.engine.*
import kotlin.math.*

/* =========================================================
   GEOGRAPHY — Đà Nẵng → Hội An, hand-placed and scaled down
   (x = east, z = south; ~1:5 horizontally, heights exaggerated)
   ========================================================= */
const val WX0 = -4800.0
const val WZ0 = -4400.0
const val WSIZE = 9600.0
const val GS = 20.0
const val GN = 481 // WSIZE / GS + 1
const val SEA_Y = 0.0
const val TILE = 400.0
const val TN = 24

class Seg(val ax: Double, val az: Double, val dx: Double, val dz: Double, val l2: Double, val l: Double, val s: Double)

class Line(val name: String, val w: Double, val pts: List<DoubleArray>) {
    val segs = ArrayList<Seg>()
    var len = 0.0
    val x0: Double; val x1: Double; val z0: Double; val z1: Double
    init {
        var a0 = 1e9; var a1 = -1e9; var b0 = 1e9; var b1 = -1e9
        for (i in 0 until pts.size - 1) {
            val ax = pts[i][0]; val az = pts[i][1]; val bx = pts[i + 1][0]; val bz = pts[i + 1][1]
            val dx = bx - ax; val dz = bz - az; val l2 = dx * dx + dz * dz
            segs.add(Seg(ax, az, dx, dz, l2, sqrt(l2), len))
            len += sqrt(l2)
            a0 = minOf(a0, ax, bx); a1 = maxOf(a1, ax, bx); b0 = minOf(b0, az, bz); b1 = maxOf(b1, az, bz)
        }
        val m = w / 2 + 80
        x0 = a0 - m; x1 = a1 + m; z0 = b0 - m; z1 = b1 + m
    }
}

class LinePt { var x = 0.0; var z = 0.0; var tx = 0.0; var tz = 0.0 }

/** the land: signed distances, heights and the masks the painter needs */
object Geog {
    private fun sn(x: Double, y: Double) = Simplex.sn(x, y)
    private fun fbm(x: Double, y: Double, o: Int) = Simplex.fbm(x, y, o)
    private fun ridged(x: Double, y: Double, o: Int) = Simplex.ridged(x, y, o)

    fun mkLine(name: String, w: Double, vararg p: Pair<Int, Int>) = Line(name, w, p.map { doubleArrayOf(it.first.toDouble(), it.second.toDouble()) })

    /** last lineDist query: distance, arc length, tangent */
    object LQ { var d = 0.0; var s = 0.0; var tx = 0.0; var tz = 0.0 }

    fun lineDist(L: Line, x: Double, z: Double): Double {
        if (x < L.x0 || x > L.x1 || z < L.z0 || z > L.z1) return 1e9
        var best = 1e18
        for (g in L.segs) {
            var t = ((x - g.ax) * g.dx + (z - g.az) * g.dz) / g.l2
            t = if (t < 0) 0.0 else if (t > 1) 1.0 else t
            val ex = g.ax + g.dx * t - x; val ez = g.az + g.dz * t - z; val d = ex * ex + ez * ez
            if (d < best) { best = d; LQ.s = g.s + g.l * t; LQ.tx = g.dx / g.l; LQ.tz = g.dz / g.l }
        }
        LQ.d = sqrt(best)
        return LQ.d
    }
    fun linePoint(L: Line, s0: Double, out: LinePt = LinePt()): LinePt {
        val s = clamp(s0, 0.0, L.len - 0.01)
        for (g in L.segs) if (s <= g.s + g.l) {
            val t = (s - g.s) / g.l
            out.x = g.ax + g.dx * t; out.z = g.az + g.dz * t; out.tx = g.dx / g.l; out.tz = g.dz / g.l
            return out
        }
        return out
    }
    /** centre-line x of a mostly north–south river at a given z (NaN when the river does not cross z) */
    fun lineXatZ(L: Line, z: Double): Double {
        for (g in L.segs) {
            val za = g.az; val zb = g.az + g.dz
            if ((z - za) * (z - zb) <= 0 && g.dz != 0.0) return g.ax + g.dx * ((z - za) / g.dz)
        }
        return Double.NaN
    }

    val RIVERS = listOf(
        mkLine("han", 96.0, -30 to -2250, -40 to -1980, -55 to -1700, -35 to -1400, -10 to -1100, -5 to -880, -30 to -640, -90 to -350, -190 to -60, -290 to 250, -410 to 560, -600 to 860, -880 to 1080, -1250 to 1200, -1700 to 1260, -2300 to 1350, -2900 to 1500, -3600 to 1650),
        mkLine("coco", 30.0, -260 to 190, 0 to 380, 250 to 560, 400 to 780, 430 to 1100, 470 to 1500, 520 to 2000, 600 to 2500, 680 to 2950, 760 to 3300, 830 to 3560),
        mkLine("thubon", 112.0, -3600 to 4100, -2400 to 3880, -1700 to 3800, -1000 to 3740, -400 to 3660, 150 to 3600, 600 to 3578, 1000 to 3592, 1300 to 3640, 1550 to 3700, 1750 to 3742, 2050 to 3790),
        mkLine("hoai", 16.0, 468 to 3300, 478 to 3420, 470 to 3560),
        mkLine("baymau", 14.0, 1190 to 3700, 1290 to 3765, 1410 to 3745, 1480 to 3815, 1560 to 3800),
        mkLine("cuda", 34.0, -2500 to -1600, -2300 to -1750, -2100 to -1880, -1950 to -2050),
    )
    val R_HAN = RIVERS[0]; val R_COCO = RIVERS[1]; val R_THUBON = RIVERS[2]

    /** east coast: Mỹ Khê → Non Nước → An Bàng → Cửa Đại */
    fun coastX(z: Double) = if (z < -2250) 520 + (z + 2250) * 1.2 else 520 + 0.17 * (z + 2250) + 22 * sin(z * 0.0021) + 10 * sin(z * 0.0063 + 1)
    fun sdMain(x: Double, z: Double): Double {
        val sE = coastX(z) - x
        val ex = (x + 1000) / 1350; val ez = (z + 2900) / 1000; val sB = (sqrt(ex * ex + ez * ez) - 1) * 1050 // Đà Nẵng bay
        val sN = (z - min(-3800 + max(0.0, x + 1300) * 3, -2300.0)) / 3.16 // Hải Vân cape
        return minOf(sE, sB, sN)
    }
    fun sdEll(x: Double, z: Double, cx: Double, cz: Double, rx: Double, rz: Double, rot: Double): Double {
        val c = cos(rot); val s = sin(rot); val dx = x - cx; val dz = z - cz
        val u = (dx * c - dz * s) / rx; val v = (dx * s + dz * c) / rz
        return (1 - sqrt(u * u + v * v)) * min(rx, rz)
    }
    val SONTRA = arrayOf(doubleArrayOf(1150.0, -2650.0, 920.0, 430.0, -0.05), doubleArrayOf(2020.0, -2720.0, 360.0, 250.0, 0.2), doubleArrayOf(620.0, -2330.0, 300.0, 230.0, 0.0))
    val ST_PEAKS = arrayOf(
        doubleArrayOf(1000.0, -2740.0, 380.0, 188.0), // Ốc Sơn (~690 m)
        doubleArrayOf(600.0, -2560.0, 220.0, 118.0), // Bàn Cờ
        doubleArrayOf(1520.0, -2700.0, 320.0, 138.0),
        doubleArrayOf(2050.0, -2730.0, 230.0, 92.0),
        doubleArrayOf(1250.0, -2440.0, 200.0, 52.0),
    )
    /** Cù Lao Chàm archipelago: x, z, rx, rz, height, rot */
    val ISLANDS = arrayOf(
        doubleArrayOf(3800.0, 2650.0, 520.0, 230.0, 165.0, -0.25), // Hòn Lao
        doubleArrayOf(3150.0, 2900.0, 140.0, 60.0, 45.0, 0.3), // Hòn Dài
        doubleArrayOf(3300.0, 2480.0, 70.0, 55.0, 35.0, 0.0), // Hòn Mồ
        doubleArrayOf(4300.0, 3050.0, 120.0, 80.0, 55.0, 0.5), // Hòn Khô
        doubleArrayOf(4050.0, 3260.0, 90.0, 65.0, 45.0, 0.0), // Hòn Tai
        doubleArrayOf(3550.0, 3160.0, 160.0, 90.0, 70.0, -0.4), // Hòn Lá
    )
    class Karst(val x: Double, val z: Double, val r: Double, val h: Double, val name: String)
    /** Ngũ Hành Sơn */
    val KARST = listOf(
        Karst(760.0, 760.0, 105.0, 92.0, "Thủy Sơn"), Karst(905.0, 700.0, 40.0, 40.0, "Mộc Sơn"), Karst(585.0, 905.0, 48.0, 60.0, "Hỏa Sơn"),
        Karst(640.0, 975.0, 38.0, 44.0, "Âm Hỏa Sơn"), Karst(530.0, 1080.0, 44.0, 48.0, "Kim Sơn"), Karst(840.0, 1010.0, 55.0, 38.0, "Thổ Sơn"),
    )
    fun sdSonTra(x: Double, z: Double): Double {
        if (x < -400 || x > 2600 || z < -3300 || z > -1900) return -500.0
        var s = -1e9
        for (e in SONTRA) s = max(s, sdEll(x, z, e[0], e[1], e[2], e[3], e[4]))
        return s
    }
    fun sdIslands(x: Double, z: Double): Double {
        if (x < 2700 || z < 2100 || z > 3600) return -500.0
        var s = -1e9
        for (e in ISLANDS) s = max(s, sdEll(x, z, e[0], e[1], e[2], e[3], e[5]))
        return s
    }
    fun gauss(x: Double, z: Double, cx: Double, cz: Double, r: Double): Double { val dx = (x - cx) / r; val dz = (z - cz) / r; return exp(-(dx * dx + dz * dz)) }
    /** west mountains: Hải Vân pass to the north-west, Bà Nà hills to the west */
    fun mtnEdge(z: Double) = -2050 - 0.12 * (z + 2000)
    fun mtnMask(x: Double, z: Double) = max(smooth(mtnEdge(z), mtnEdge(z) - 1000, x), smooth(-3250.0, -3650.0, z) * smooth(-900.0, -1400.0, x))
    fun cityMask(x: Double, z: Double) =
        smooth(-1850.0, -1650.0, x + 90 * sn(z * 0.003, 4.1)) * smooth(760.0, 560.0, z + 110 * sn(x * 0.0025, 1.3)) * smooth(-2300.0, -2150.0, z) * (1 - smooth(-250.0, 120.0, sdSonTra(x, z)))
    fun hoianMask(x: Double, z: Double) = smooth(400.0, 450.0, x) * smooth(960.0, 910.0, x) * smooth(3350.0, 3390.0, z) * smooth(3545.0, 3515.0, z)
    fun riceMask(x: Double, z: Double) = smooth(780.0, 1050.0, z) * (1 - smooth(4900.0, 5100.0, z))

    /** fields of the last sample() */
    object S { var h = 0.0; var sd = 0.0; var riv = 0.0; var city = 0.0; var hoian = 0.0; var rice = 0.0; var M = 0.0; var rock = 0.0; var karst = 0.0; var st = 0.0; var isl = 0.0 }

    fun sample(x: Double, z: Double): Double {
        val sm = sdMain(x, z); val ss = sdSonTra(x, z); val si = sdIslands(x, z)
        val hvR = smooth(-1500.0, -2300.0, x) * smooth(-2300.0, -3100.0, z)
        val rock = maxOf(smooth(-220.0, 80.0, ss), smooth(-220.0, 80.0, si), hvR)
        var sd = maxOf(sm, ss, si)
        sd += (3 + 30 * rock) * fbm(x * 0.0042 + 3.3, z * 0.0042 - 1.7, 3) + 4 * rock * sn(x * 0.02, z * 0.02)
        val M = mtnMask(x, z)
        S.sd = sd; S.rock = rock; S.M = M; S.st = ss; S.isl = si; S.karst = 0.0; S.riv = 0.0
        S.city = 0.0; S.hoian = 0.0; S.rice = 0.0
        var h: Double
        if (sd < 0) {
            h = max(-36.0, -0.9 + sd * (0.032 + 0.05 * rock) + 1.2 * sn(x * 0.006, z * 0.006))
        } else {
            h = 0.15 + 2.4 * smooth(0.0, 70.0, sd) + (0.8 * sn(x * 0.008, z * 0.008) + 0.4 * sn(x * 0.03, z * 0.03)) * smooth(60.0, 260.0, sd)
            val cf = smooth(-10.0, 70 + 110 * (1 - rock), sd)
            if (M > 0.001) {
                val hm = M * (60 + 360 * ridged(x * 0.00072 + 5.1, z * 0.00072 - 2.3, 4)) + 270 * gauss(x, z, -3450.0, -250.0, 560.0) + 190 * gauss(x, z, -2500.0, -3420.0, 520.0) + 120 * gauss(x, z, -3000.0, 1800.0, 700.0)
                h = max(h, h + hm * M * cf)
            }
            if (ss > -60) {
                var hs = 0.0
                for (p in ST_PEAKS) hs += p[3] * gauss(x, z, p[0], p[1], p[2])
                hs = hs * smooth(-20.0, 170.0, ss) + 34 * ridged(x * 0.003 + 1.7, z * 0.003, 3) * smooth(0.0, 220.0, ss)
                h = max(h, hs * cf + 1)
            }
            if (si > -60) {
                var hi = 0.0
                for (e in ISLANDS) hi = max(hi, e[4] * smooth(-10.0, min(e[2], e[3]) * 0.9, sdEll(x, z, e[0], e[1], e[2], e[3], e[5])))
                hi += 14 * ridged(x * 0.006, z * 0.006, 2) * smooth(0.0, 60.0, si)
                h = max(h, hi * cf + 1)
            }
            // flatten the towns and the paddies
            val ci = cityMask(x, z) * smooth(25.0, 90.0, sd)
            if (ci > 0) { h = lerp(h, 2.6 + 0.25 * sn(x * 0.01, z * 0.01), ci); S.city = ci }
            val ho = hoianMask(x, z)
            if (ho > 0) { h = lerp(h, 2.3, ho); S.hoian = ho }
            val ri = riceMask(x, z) * (1 - M) * smooth(130.0, 260.0, sd) * (1 - ho)
            if (ri > 0) { h = lerp(h, 1.6 + 0.3 * sn(x * 0.004, z * 0.004), ri * 0.85); S.rice = ri }
            // Ngũ Hành Sơn karsts
            if (x > 420 && x < 1000 && z > 600 && z < 1120) {
                for (i in KARST.indices) {
                    val k = KARST[i]; val d = hypot(x - k.x, z - k.z)
                    if (d > k.r * 1.3) continue
                    val n = fbm(x * 0.03 + i * 7, z * 0.03, 2) * 14
                    val pr = smooth(k.r * 1.02, k.r * 0.62, d + n)
                    if (pr <= 0) continue
                    val hk = 2.5 + k.h * pr.pow(0.38) * (0.78 + 0.34 * ridged(x * 0.03 + i, z * 0.03, 2)) + 6 * pr * sn(x * 0.08, z * 0.08)
                    if (hk > h) { h = hk; S.karst = max(S.karst, pr) }
                }
            }
        }
        // rivers
        for (L in RIVERS) {
            val d = lineDist(L, x, z)
            if (d > L.w / 2 + 60) continue
            val half = L.w / 2; val bed = -3.4 - min(2.0, L.w * 0.02)
            val t = smooth(half - 5, half + 30, d)
            if (h > bed) h = bed + (h - bed) * t
            S.riv = max(S.riv, 1 - smooth(half - 3, half + 5, d))
        }
        S.h = h
        return h
    }

    /* terrain palette (sRGB 0..255) */
    object PAL {
        val sand = intArrayOf(242, 226, 180); val wet = intArrayOf(222, 203, 150); val dune = intArrayOf(170, 198, 110); val city = intArrayOf(221, 211, 194); val park = intArrayOf(150, 196, 112)
        val forest = arrayOf(intArrayOf(79, 143, 74), intArrayOf(95, 158, 80), intArrayOf(63, 125, 69), intArrayOf(106, 168, 88))
        val cliff = intArrayOf(158, 150, 138); val lime = intArrayOf(214, 212, 204); val limeG = intArrayOf(186, 186, 178)
        val hoian = intArrayOf(214, 190, 140); val mud = intArrayOf(186, 171, 132); val seabed = intArrayOf(210, 196, 150)
        val rice = arrayOf(
            intArrayOf(168, 216, 106), intArrayOf(189, 226, 124), intArrayOf(140, 201, 90), intArrayOf(176, 214, 112), intArrayOf(152, 206, 98),
            intArrayOf(201, 224, 138), intArrayOf(160, 210, 118), intArrayOf(196, 210, 112), intArrayOf(214, 206, 118), intArrayOf(150, 204, 170),
        )
    }

    /** ground colour; uses the S fields of the last sample(x, z) */
    fun colorOf(x: Double, z: Double, h: Double, slope: Double, out: DoubleArray): DoubleArray {
        val n = sn(x * 0.013, z * 0.013); val n2 = hash2(floor(x / 37), floor(z / 41))
        val c: IntArray = when {
            h < 0.5 -> if (S.riv > 0.3) PAL.mud else if (h < -0.4) PAL.seabed else PAL.wet
            S.riv > 0.2 -> PAL.mud
            S.karst > 0.15 -> if (slope > 1.1) (if (n2 < 0.5) PAL.lime else PAL.limeG) else if (slope > 0.7) (if (n2 < 0.4) PAL.limeG else PAL.forest[3]) else PAL.forest[(n2 * 4).toInt()]
            S.sd < 48 && S.rock < 0.5 && h < 4 -> PAL.sand
            S.hoian > 0.5 -> if (n2 < 0.25) PAL.park else PAL.hoian
            S.city > 0.5 -> if (n2 < 0.12) PAL.park else PAL.city
            slope > 1.5 && h > 8 && n2 < 0.6 -> PAL.cliff
            S.sd < 140 && S.rock < 0.5 && h < 6 -> PAL.dune
            S.rice > 0.4 && h < 6 -> {
                // paddy patchwork in tilted fields
                val u = x * 0.94 + z * 0.34; val v = -x * 0.34 + z * 0.94
                PAL.rice[(hash2(floor(u / 46), floor(v / 34)) * PAL.rice.size).toInt()]
            }
            S.M < 0.12 && S.st < 0 && S.isl < 0 && h < 9 -> {
                val u = x * 0.9 + z * 0.43; val v = -x * 0.43 + z * 0.9
                if (hash2(floor(u / 60), floor(v / 44)) < 0.12) PAL.forest[3] else PAL.rice[(hash2(floor(u / 52), floor(v / 38)) * PAL.rice.size).toInt()]
            }
            else -> PAL.forest[((n * 0.5 + 0.5) * 3.99).toInt()]
        }
        var r = c[0].toDouble(); var g = c[1].toDouble(); var b = c[2].toDouble()
        if (h > 240) { val t = smooth(240.0, 520.0, h); r = lerp(r, 150.0, t * 0.6); g = lerp(g, 166.0, t * 0.6); b = lerp(b, 170.0, t * 0.6) }
        val j = 1 + (hash2(x * 0.37, z * 0.71) - 0.5) * 0.08 + n * 0.04
        out[0] = clamp(r * j, 0.0, 255.0); out[1] = clamp(g * j, 0.0, 255.0); out[2] = clamp(b * j, 0.0, 255.0)
        return out
    }
}
