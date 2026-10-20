package vn.paperworld.phidoi.game

import vn.paperworld.phidoi.engine.*
import vn.paperworld.phidoi.world.*
import vn.paperworld.phidoi.world.Geog.coastX
import kotlin.math.*

/* =========================================================
   MAPS — pre-painted region map, rotating minimap, big map (web 6178–6320)
   ========================================================= */
const val MAPN = 960

/** paint the region map once (web: paintMap) — terrain colours + hill shading, coastline ink, roads, place names */
fun Game.mapImage(): Image2D {
    ui.mapImg?.let { return it }
    val px = IntArray(GN * GN)
    for (j in 0 until GN) for (i in 0 until GN) {
        val k = j * GN + i; val h = GH[k].toDouble()
        var r: Double; var g: Double; var b: Double
        if (h < SEA_Y) {
            val d = clamp(-h / 30, 0.0, 1.0); r = lerp(150.0, 52.0, d); g = lerp(226.0, 160.0, d); b = lerp(222.0, 192.0, d)
            if ((GM[k * 4 + 1].toInt() and 255) > 120) { r = 110.0; g = 175.0; b = 160.0 }
        } else {
            r = (GC[k * 3].toInt() and 255).toDouble(); g = (GC[k * 3 + 1].toInt() and 255).toDouble(); b = (GC[k * 3 + 2].toInt() and 255).toDouble()
            val hx = (GH[k + (if (i < GN - 1) 1 else 0)] - GH[k - (if (i > 0) 1 else 0)]) / 40.0
            val hz = (GH[k + (if (j < GN - 1) GN else 0)] - GH[k - (if (j > 0) GN else 0)]) / 40.0
            val sh = clamp(1 + (-hx * 0.7 - hz * 0.7) * 0.9, 0.6, 1.35)
            r *= sh; g *= sh; b *= sh
        }
        fun u8(v: Double) = clamp(v.roundToInt().toDouble(), 0.0, 255.0).toInt()
        px[k] = (255 shl 24) or (u8(r) shl 16) or (u8(g) shl 8) or u8(b)
    }
    val small = plat.image(px, GN, GN)
    val g = plat.bitmap(MAPN, MAPN)
    g.drawImage(small, 0f, 0f, MAPN.toFloat(), MAPN.toFloat())
    // coastline ink
    g.setStroke(rgba(31, 29, 43, 0.55)); g.setLineWidth(1.2f)
    val s = MAPN.toFloat() / (GN - 1)
    g.beginPath()
    for (j in 0 until GN - 1) for (i in 0 until GN - 1) {
        val a = GH[j * GN + i] > 0; val b2 = GH[j * GN + i + 1] > 0; val c = GH[(j + 1) * GN + i] > 0
        if (a != b2) { g.moveTo((i + 0.5f) * s, j * s - s * 0.5f); g.lineTo((i + 0.5f) * s, j * s + s * 0.5f) }
        if (a != c) { g.moveTo(i * s - s * 0.5f, (j + 0.5f) * s); g.lineTo(i * s + s * 0.5f, (j + 0.5f) * s) }
    }
    g.stroke()
    // roads + bridges
    g.setStroke(rgba(60, 58, 80, 0.6)); g.setLineWidth(2f)
    for (r in ROADS) {
        g.beginPath()
        for ((i, p) in r.withIndex()) { val mx = w2mX(p[0]).toFloat(); val mz = w2mZ(p[1]).toFloat(); if (i > 0) g.lineTo(mx, mz) else g.moveTo(mx, mz) }
        g.stroke()
    }
    // place names
    fun lab(x: Double, z: Double, t: String, size: Double = 14.0, col: String = "#1f1d2b") {
        val mx = w2mX(x).toFloat(); val mz = w2mZ(z).toFloat()
        g.setFont(Face.HAND, size.toFloat()); g.setTextAlign(Align.CENTER); g.setTextBaseline(Baseline.ALPHABETIC)
        g.setLineWidth(3f); g.setStroke(rgba(251, 248, 239, 0.85)); g.strokeText(t, mx, mz)
        g.setFill(argb(col)); g.fillText(t, mx, mz)
    }
    lab(-1000.0, -2900.0, "Vịnh Đà Nẵng", 18.0, "#1f6f8b")
    lab(1200.0, -2650.0, "Bán đảo Sơn Trà", 17.0)
    lab(-700.0, -1000.0, "Đà Nẵng", 22.0)
    lab(coastX(-800.0) + 260, -800.0, "Biển Mỹ Khê", 16.0, "#1f6f8b")
    lab(720.0, 1240.0, "Ngũ Hành Sơn", 15.0)
    lab(660.0, 3330.0, "Hội An", 20.0)
    lab(3800.0, 2450.0, "Cù Lao Chàm", 16.0)
    lab(-3450.0, -40.0, "Bà Nà", 16.0)
    lab(-2500.0, -3650.0, "Đèo Hải Vân", 15.0)
    lab(-700.0, 2600.0, "Đồng lúa Quảng Nam", 14.0, "#4a6a2a")
    lab(3000.0, 500.0, "Biển Đông", 22.0, "#1f6f8b")
    lab(-300.0, 3900.0, "Sông Thu Bồn", 13.0, "#1f6f8b")
    lab(-200.0, -60.0, "Sông Hàn", 13.0, "#1f6f8b")
    val img = plat.image(g.pixels(), MAPN, MAPN)
    ui.mapImg = img
    return img
}
private fun w2mX(x: Double) = (x - WX0) / WSIZE * MAPN
private fun w2mZ(z: Double) = (z - WZ0) / WSIZE * MAPN

private fun Canvas2D.star(x: Double, y: Double, r: Double, fill: Int) {
    setFill(fill); setStroke(Pal.ink); setLineWidth((r * 0.22).toFloat())
    beginPath()
    for (k in 0 until 10) { val a = k * PI / 5; val rr = if (k % 2 != 0) r * 0.45 else r; lineTo((x + sin(a) * rr).toFloat(), (y - cos(a) * rr).toFloat()) }
    closePath(); fill(); stroke()
}
private fun Canvas2D.dart(x: Double, y: Double, rot: Double, fill: Int, stroke: Int, lw: Double) {
    save(); translate(x.toFloat(), y.toFloat()); rotate(rot.toFloat())
    setFill(fill); setStroke(stroke); setLineWidth(lw.toFloat())
    beginPath(); moveTo(0f, -14f); lineTo(9f, 10f); lineTo(0f, 5f); lineTo(-9f, 10f); closePath(); fill(); stroke()
    restore()
}

/** battle area, landmark, supplies, enemies, incoming missiles; map point = ((x − ox)·s, (z − oz)·s) */
private fun Game.drawMarkers(g: Canvas2D, ox: Double, oz: Double, sc: Double, big: Boolean) {
    fun tx(x: Double) = (x - ox) * sc
    fun ty(z: Double) = (z - oz) * sc
    val ctr = MS.center
    if (ctr != null && game.started) {
        g.setStroke(rgba(214, 69, 47, 0.6)); g.setLineDash(7f, 6f); g.setLineWidth(if (big) 2f else 2.4f)
        g.circle(tx(ctr.x), ty(ctr.z), MS.radius * sc); g.stroke(); g.setLineDash(0f, 0f)
    }
    MS.lm?.let { L ->
        val x = tx(L.x); val y = ty(L.z)
        g.setStroke(rgba(242, 194, 48, 0.95)); g.setLineWidth(2f)
        g.circle(x, y, max(6.0, L.r * sc)); g.stroke()
        g.star(x, y, if (big) 11.0 else 10.0, Pal.gold)
    }
    for (k in PKS) {
        val x = tx(k.p.x).toFloat(); val y = ty(k.p.z).toFloat()
        g.setFill(argb(World.AHA.orange)); g.setStroke(Pal.ink); g.setLineWidth(2f); g.fillRect(x - 6, y - 6, 12f, 12f); g.strokeRect(x - 6, y - 6, 12f, 12f)
    }
    if (DLV.active) { g.setFill(argb(World.AHA.blue)); g.setStroke(Pal.white); g.setLineWidth(2f); g.circle(tx(DLV.p.x), ty(DLV.p.z), 6.0); g.fill(); g.stroke() }
    for (e in EN) {
        if (!e.alive || e.demo) continue
        g.save()
        g.translate(tx(e.pos.x).toFloat(), ty(e.pos.z).toFloat())
        when (e.k) {
            "boss" -> { g.setFill(argb("#6b3fa0")); g.setStroke(Pal.ink); g.setLineWidth(2.5f); g.circle(0.0, 0.0, if (big) 16.0 else 14.0); g.fill(); g.stroke() }
            "kite" -> { g.setFill(Pal.red); g.setStroke(Pal.ink); g.setLineWidth(1.6f); g.beginPath(); g.moveTo(0f, -7f); g.lineTo(5f, 0f); g.lineTo(0f, 7f); g.lineTo(-5f, 0f); g.closePath(); g.fill(); g.stroke() }
            else -> {
                val s = if (e.k == "bomber") 12f else if (e.k == "ace") 11f else 9f
                g.rotate((-e.psi).toFloat())
                g.setFill(if (e.k == "ace") Pal.ink else Pal.red); g.setStroke(if (e === PL.target) Pal.gold else Pal.ink); g.setLineWidth(if (e === PL.target) 3f else 1.8f)
                g.beginPath(); g.moveTo(0f, -s); g.lineTo(s * 0.75f, s * 0.8f); g.lineTo(0f, s * 0.35f); g.lineTo(-s * 0.75f, s * 0.8f); g.closePath(); g.fill(); g.stroke()
            }
        }
        g.restore()
    }
    for (m in MIS) { if (m.team != 1) continue; g.setFill(argb("#ff3b2f")); g.circle(tx(m.p.x), ty(m.p.z), 3.5); g.fill() }
}

/* ---------- #minimap: right 22 top 22, 160 px; touch: right 12 top 10, 110 px (canvas 320) ---------- */
fun Game.drawMinimapCard(c: Canvas2D, w: Double, h: Double, dt: Double) {
    val S = if (isTouch) 110.0 else 160.0
    val x0 = w - (if (isTouch) 12.0 else 22.0) - S; val y0 = if (isTouch) 10.0 else 22.0
    val cx = x0 + S / 2; val cy = y0 + S / 2
    c.setFill(Pal.ink); c.circle(cx + 2, cy + 2, S / 2); c.fill()
    c.setFill(Pal.paper2); c.circle(cx, cy, S / 2); c.fill()
    val W = 320.0
    val img = mapImage()
    c.save()
    c.translate((x0 + 1.5).toFloat(), (y0 + 1.5).toFloat()); c.scale(((S - 3) / W).toFloat(), ((S - 3) / W).toFloat())
    val range = 700 + clamp(F.pos.y * 2, 0.0, 1300.0); val sc = W / (range * 2)
    val rot = if (SET.mapRot) F.psi else 0.0
    c.save()
    c.circle(W / 2, W / 2, W / 2); c.clip()
    c.setFill(argb("#3fa9b8")); c.fillRect(0f, 0f, W.toFloat(), W.toFloat())
    c.translate((W / 2).toFloat(), (W / 2).toFloat())
    c.rotate(rot.toFloat())
    val k = WSIZE / MAPN
    val mx = w2mX(F.pos.x); val mz = w2mZ(F.pos.z)
    c.drawImage(img, (-mx * sc * k).toFloat(), (-mz * sc * k).toFloat(), (MAPN * sc * k).toFloat(), (MAPN * sc * k).toFloat())
    drawMarkers(c, F.pos.x, F.pos.z, sc, false)
    c.restore()
    // the player arrow
    c.save(); c.translate((W / 2).toFloat(), (W / 2).toFloat())
    c.dart(0.0, 0.0, if (SET.mapRot) 0.0 else -F.psi, argb(World.AHA.blue), Pal.white, 2.4)
    c.restore()
    // north + wind
    c.save()
    c.translate((W / 2).toFloat(), (W / 2).toFloat())
    c.rotate(rot.toFloat())
    c.text("B", 0.0, -W / 2 + 20, Face.UI800, 22.0, Pal.ink, Align.CENTER)
    c.rotate(atan2(WIND.dir.x, -WIND.dir.z).toFloat())
    c.setStroke(Pal.teal); c.setLineWidth(3f)
    val r = (W / 2).toFloat()
    c.beginPath(); c.moveTo(0f, r - 46); c.lineTo(0f, r - 18); c.moveTo(-6f, r - 40); c.lineTo(0f, r - 48); c.lineTo(6f, r - 40); c.stroke()
    c.restore()
    c.restore()
    c.setStroke(Pal.ink); c.setLineWidth(1.5f); c.circle(cx, cy, S / 2 - 0.75); c.stroke()
}

/** web: togglePanel("pMap") → drawBigMap(); the map art is painted on first use */
fun Game.onBigMapOpen() { mapImage() }

/* ---------- #pMap: "Bản đồ tác chiến", map min(78vh, 86vw) (touch 80vh) ---------- */
fun Game.drawBigMapPanel(c: Canvas2D, w: Double, h: Double) {
    val pad = if (isTouch) 14.0 else 20.0
    val head = if (isTouch) 30.0 else 52.0
    val sub = "▲ máy bay địch · ★ mục tiêu cần bảo vệ · ■ kiện hàng Ahamove · vòng nét đứt: vùng tác chiến"
    // the web panel scrolls when it is taller than 90vh; here the map shrinks to fit instead
    val M = minOf(if (isTouch) h * 0.8 else h * 0.78, w * 0.86, h - 12 - head - pad * 2 - 3)
    val subW = if (isTouch) 0.0 else c.textW(sub, Face.UI500, 10.5)
    val pw = min(w * 0.96, max(M, max(subW, c.textW("Bản đồ tác chiến", Face.HAND, if (isTouch) 22.0 else 30.0) + 90)) + pad * 2 + 3)
    val ph = head + M + pad * 2 + 3
    val (x0, y0) = panelFrame(c, w, h, pw, ph, "Bản đồ tác chiến", sub, true)
    val mx = x0 - pad + (pw - M) / 2
    val img = mapImage()
    c.setFill(argb("#9fe0e0")); c.rrect(mx, y0, M, M, 3.0); c.fill()
    c.save()
    c.rrect(mx, y0, M, M, 3.0); c.clip()
    c.translate(mx.toFloat(), y0.toFloat()); c.scale((M / MAPN).toFloat(), (M / MAPN).toFloat())
    c.drawImage(img, 0f, 0f, MAPN.toFloat(), MAPN.toFloat())
    drawMarkers(c, WX0, WZ0, MAPN / WSIZE, true)
    c.dart(w2mX(F.pos.x), w2mZ(F.pos.z), -F.psi, argb(World.AHA.blue), Pal.white, 2.0)
    c.restore()
    c.setStroke(Pal.ink); c.setLineWidth(1.5f); c.rrect(mx, y0, M, M, 3.0); c.stroke()
}
