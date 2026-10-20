package vn.paperworld.phidoi.engine

import kotlin.math.floor
import kotlin.math.sqrt

/** mulberry32, bit-for-bit the same generator as the web game */
class Mulberry32(seed: Int) {
    private var a = seed
    fun next(): Double {
        a += 0x6d2b79f5
        var t = (a xor (a ushr 15)) * (1 or a)
        t = (t + ((t xor (t ushr 7)) * (61 or t))) xor t
        return ((t xor (t ushr 14)).toLong() and 0xffffffffL) / 4294967296.0
    }
}

/** the seeded random used while building the world, plus the usual helpers */
object Rnd {
    var gen = Mulberry32(20261003)
    fun rand() = gen.next()
    fun RR(a: Double, b: Double) = a + (b - a) * rand()
    fun RR(a: Int, b: Int) = RR(a.toDouble(), b.toDouble())
    fun <T> pick(a: List<T>): T = a[floor(rand() * a.size).toInt()]
    fun <T> pick(a: Array<T>): T = a[floor(rand() * a.size).toInt()]
}

/** 2D simplex noise, fbm and ridged — same permutation table as the web build */
object Simplex {
    private val PERM = IntArray(512)
    private val GRX = intArrayOf(1, -1, 1, -1, 1, -1, 0, 0)
    private val GRY = intArrayOf(1, 1, -1, -1, 0, 0, 1, -1)
    private val F2 = 0.5 * (sqrt(3.0) - 1)
    private val G2 = (3 - sqrt(3.0)) / 6

    init {
        val r = Mulberry32(1337)
        val p = IntArray(256) { it }
        for (i in 255 downTo 1) { val j = floor(r.next() * (i + 1)).toInt(); val t = p[i]; p[i] = p[j]; p[j] = t }
        for (i in 0 until 512) PERM[i] = p[i and 255]
    }

    fun sn(x: Double, y: Double): Double {
        val s = (x + y) * F2
        val i = floor(x + s).toInt(); val j = floor(y + s).toInt()
        val t = (i + j) * G2
        val x0 = x - i + t; val y0 = y - j + t
        val i1 = if (x0 > y0) 1 else 0; val j1 = 1 - i1
        val x1 = x0 - i1 + G2; val y1 = y0 - j1 + G2; val x2 = x0 - 1 + 2 * G2; val y2 = y0 - 1 + 2 * G2
        val ii = i and 255; val jj = j and 255
        var n = 0.0
        var q = 0.5 - x0 * x0 - y0 * y0
        if (q > 0) { val g = PERM[ii + PERM[jj]] and 7; q *= q; n += q * q * (GRX[g] * x0 + GRY[g] * y0) }
        q = 0.5 - x1 * x1 - y1 * y1
        if (q > 0) { val g = PERM[ii + i1 + PERM[jj + j1]] and 7; q *= q; n += q * q * (GRX[g] * x1 + GRY[g] * y1) }
        q = 0.5 - x2 * x2 - y2 * y2
        if (q > 0) { val g = PERM[ii + 1 + PERM[jj + 1]] and 7; q *= q; n += q * q * (GRX[g] * x2 + GRY[g] * y2) }
        return 70 * n
    }

    fun fbm(x: Double, y: Double, o: Int): Double {
        var v = 0.0; var a = 0.5; var f = 1.0; var s = 0.0
        for (i in 0 until o) { v += a * sn(x * f, y * f); s += a; f *= 2.03; a *= 0.5 }
        return v / s
    }

    fun ridged(x: Double, y: Double, o: Int): Double {
        var v = 0.0; var a = 0.5; var f = 1.0
        for (i in 0 until o) { val n = 1 - kotlin.math.abs(sn(x * f, y * f)); v += a * n * n; f *= 2.1; a *= 0.5 }
        return v
    }
}
