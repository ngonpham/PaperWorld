package vn.paperworld.phidoi.game

import vn.paperworld.phidoi.engine.*
import kotlin.math.*

/* =========================================================
   HUD — weapons card (#wpn), mission card (#obj), radio (#radio), brand + nav,
   warnings (web: updateHUD 5937–5997), and the combat overlay (web: drawHud 5999–6177)
   ========================================================= */

/** what updateHUD wrote into the DOM, every 0.07 s */
class HudState {
    var t = 0.0
    var hp = 1.0; var low = false; var heat = 0.0; var hot = false
    var mis = "🚀 6"; var misMax = "/6"; var flr = "✦ 8"; var lives = "✈✈✈"
    var objK = "NHIỆM VỤ"; var objName = "—"; var objText = "—"; var bar = -1.0
    var score = "0"; var combo = ""; var timer = "0:00"
}

/** Number.toLocaleString("vi-VN"): 12345 → "12.345" */
fun fmtVi(n: Int): String {
    val s = abs(n).toString(); val sb = StringBuilder()
    for ((i, ch) in s.withIndex()) { if (i > 0 && (s.length - i) % 3 == 0) sb.append('.'); sb.append(ch) }
    return (if (n < 0) "-" else "") + sb
}

fun Game.updateHUD(dt: Double) {
    val H = ui.hud
    H.t += dt
    if (H.t < 0.07 && dt > 0) return
    H.t = 0.0
    val C = F.c
    // weapons + damage
    val hp = PL.hp / PL.hpMax
    H.hp = clamp(hp, 0.0, 1.0); H.low = hp < 0.3
    H.heat = clamp(PL.heat, 0.0, 1.0); H.hot = PL.over
    H.mis = "🚀 ${PL.missiles}"; H.misMax = "/${C.missiles}"; H.flr = "✦ ${PL.flares}"
    H.lives = if (MS.practice) "✈ ∞" else if (PL.lives > 0) "✈".repeat(PL.lives) else "—"
    // mission panel
    val alive = EN.count { it.alive && !it.demo }
    val K = if (MS.practice) "BIA TẬP · KHÔNG GIỚI HẠN" else if (MS.survival) "SINH TỒN · ĐỢT ${max(1, MS.wave + 1)}" else "ĐỢT ${max(1, MS.wave + 1)}/${MS.waves}"
    var txt = when (MS.state) {
        "brief" -> "CHUẨN BỊ — ĐỊCH SẮP XUẤT HIỆN"
        "between" -> "ĐỢT KẾ TIẾP SAU ${ceil(MS.stT).toInt()}…"
        "win", "done" -> if (MS.won) "✓ HOÀN THÀNH" else "✕ KẾT THÚC"
        "fail" -> "✕ THẤT BẠI"
        else -> "CÒN $alive ĐỊCH"
    }
    var bar = -1.0
    MS.lm?.let { txt += " · ★ ${it.name.uppercase()} ${ceil(it.hp).toInt()}%"; bar = it.hp / it.hpMax }
    MS.boss?.let { b -> if (b.alive) { txt += " · 🦑 ${ceil((b.hp / b.hpMax) * 100).toInt()}%"; bar = b.hp / b.hpMax } }
    H.objK = K; H.objText = txt; H.bar = if (bar < 0) -1.0 else clamp(bar, 0.0, 1.0)
    H.objName = MS.def?.let { it.icon + " " + it.name } ?: "—"
    H.score = fmtVi(MS.score)
    H.combo = if (MS.combo > 1 && MS.comboT > 0) "COMBO ×${min(MS.combo, 5)}" else ""
    H.timer = fmtT(MS.t)
    // warnings
    var w = ""
    if (F.mode != "crash" && MS.state != "done") {
        w = when {
            PL.incoming > 0 -> if (isTouch) "⚠ TÊN LỬA ĐANG BÁM — BẤM ✦" else "⚠ TÊN LỬA ĐANG BÁM — G THẢ PHÁO SÁNG"
            F.mode == "fly" && F.warn >= 2 -> "⚠ KÉO LÊN!"
            F.mode == "fly" && F.stall != 0.0 -> "⚠ THẤT TỐC — CHÚC MŨI"
            MS.outside -> "⚠ RỜI VÙNG TÁC CHIẾN — QUAY LẠI"
            hp < 0.25 -> "⚠ MÁY BAY HƯ NẶNG — TÌM KIỆN SỬA CHỮA"
            else -> ""
        }
    }
    ui.warn = w
    ui.lift = when {
        F.mode == "crash" -> ""
        PL.over -> "SÚNG QUÁ NHIỆT — CHỜ NGUỘI"
        PL.repairing -> "🔧 ĐANG SỬA MÁY BAY & NẠP ĐẠN"
        PL.inv > 0 && MS.state != "brief" -> "✦ MÁY BAY DỰ PHÒNG"
        F.mode == "landed" -> if (C.heli) "SHIFT ĐỂ CẤT CÁNH" else "SHIFT / W ĐỂ CẤT CÁNH"
        LIFT.inCloud > 0.2 -> "☁ TRONG MÂY"
        else -> ""
    }
    sfx.warn(F.mode == "fly" && F.warn >= 2 && PL.incoming <= 0)
}

private val A75 = rgba(31, 29, 43, 0.75)
private val A70 = rgba(31, 29, 43, 0.7)

/** text cut to [maxW] with "…" (CSS text-overflow: ellipsis) */
fun Canvas2D.ellipsize(t: String, face: Face, size: Double, maxW: Double, spacing: Double = 0.0): String {
    if (textW(t, face, size, spacing) <= maxW) return t
    var n = t.length
    while (n > 0 && textW(t.substring(0, n) + "…", face, size, spacing) > maxW) n--
    return t.substring(0, n) + "…"
}

/** the HUD cards: #obj, #wpn, and on the desktop layout #radio, #brand, #nav */
fun Game.drawMissionCards(c: Canvas2D, w: Double, h: Double) {
    drawObj(c, w, h)
    drawWpn(c, w, h)
    if (!isTouch) {
        if (w > 760) drawRadio(c, w, h)
        drawBrand(c)
        if (w > 1000) drawNav(c)
    }
}

/* ---------- #obj: top centre; touch: top 8, scale .82 ---------- */
fun Game.drawObj(c: Canvas2D, w: Double, h: Double) {
    val H = ui.hud
    val kW = c.textW(H.objK, Face.UI700, 7.0, 0.18)
    val nW = c.textW(H.objName, Face.HAND, 19.0)
    val tW = c.textW(H.objText, Face.UI700, 9.0, 0.08)
    val sW = c.textW(H.score, Face.HAND, 26.0)
    val cW = if (H.combo.isEmpty()) 0.0 else c.textW(H.combo, Face.UI800, 9.0, 0.1)
    val tmW = c.textW(H.timer, Face.UI700, 8.5, 0.14)
    val rW = maxOf(sW, cW, tmW)
    val lWmin = maxOf(kW, nW, tW)
    val cw = max(300.0, lWmin + 14 + 12 + 1.5 + rW + 28 + 3)
    val hasBar = H.bar >= 0
    val lH = 8.75 + 19 + 3 + 11.3 + (if (hasBar) 10.0 else 0.0)
    val rH = 24.7 + 11.3 + 10.7
    val inner = max(lH, rH)
    val ch = inner + 15 + 3
    val k = if (isTouch) 0.82 else 1.0
    scaled(c, w / 2, if (isTouch) 8.0 else 16.0, k) {
        c.card(-cw / 2, 0.0, cw, ch)
        val top = 1.5 + 7
        val lx = -cw / 2 + 1.5 + 14
        val rx = cw / 2 - 1.5 - 14
        val lw = (rx - rW - 12 - 1.5 - 14) - lx
        var y = top + (inner - lH) / 2
        c.text(H.objK, lx, y + 4.6, Face.UI700, 7.0, Pal.red, spacing = 0.18); y += 8.75
        c.text(H.objName, lx, y + 9.5, Face.HAND, 19.0); y += 19 + 3
        c.text(H.objText, lx, y + 5.8, Face.UI700, 9.0, spacing = 0.08); y += 11.3
        if (hasBar) c.bar(lx, y + 4, lw, 6.0, H.bar, floatArrayOf(0f, 0.4f, 1f), intArrayOf(Pal.red, Pal.gold, argb("#1fb5a8")), 1.2)
        // the right column, dashed rule on its left
        val ry = top + (inner - rH) / 2
        val bx = rx - rW - 12
        c.setStroke(rgba(31, 29, 43, 0.3)); c.setLineWidth(1.5f); c.setLineDash(3f, 3f)
        c.beginPath(); c.moveTo(bx.toFloat(), ry.toFloat()); c.lineTo(bx.toFloat(), (ry + rH).toFloat()); c.stroke(); c.setLineDash(0f, 0f)
        c.text(H.score, rx, ry + 12.6, Face.HAND, 26.0, Pal.ink, Align.RIGHT)
        if (H.combo.isNotEmpty()) c.text(H.combo, rx, ry + 24.7 + 5.8, Face.UI800, 9.0, Pal.orange, Align.RIGHT, spacing = 0.1)
        c.text(H.timer, rx, ry + 36 + 5.4, Face.UI700, 8.5, A70, Align.RIGHT, spacing = 0.14)
    }
}

/* ---------- #wpn: right 22 bottom 14 width 210; touch: left 14 top 76, scale .72 ---------- */
fun Game.drawWpn(c: Canvas2D, w: Double, h: Double) {
    val H = ui.hud
    val cw = 210.0; val ch = 62.0
    val (tx, ty, k) = if (isTouch) Triple(14.0, 76.0, 0.72) else Triple(w - 22 - cw, h - 14 - ch, 1.0)
    scaled(c, tx, ty, k) {
        c.card(0.0, 0.0, cw, ch)
        val x = 13.5; val bx = x + 52 + 7; val bw = cw - 13.5 * 2 - 59
        var cy = 9.5 + 4.5
        c.text("ĐỘ BỀN", x, cy, Face.UI700, 6.5, A75, spacing = 0.16)
        if (H.low) c.bar(bx, cy - 4, bw, 8.0, H.hp, floatArrayOf(0f), intArrayOf(Pal.red), fillA = blinkA(0.5))
        else c.bar(bx, cy - 4, bw, 8.0, H.hp, floatArrayOf(0f, 1f), intArrayOf(argb("#1fb5a8"), argb("#8fd26a")))
        cy += 13
        c.text("NHIỆT SÚNG", x, cy, Face.UI700, 6.5, A75, spacing = 0.16)
        c.save(); if (H.hot) c.setAlpha(blinkA(0.3).toFloat())
        c.bar(bx, cy - 4, bw, 8.0, H.heat, floatArrayOf(0f, 0.5f, 1f), intArrayOf(Pal.gold, Pal.orange, Pal.red))
        c.restore()
        // #ammo: three items, space-between
        val ay = 9.5 + 9 + 4 + 9 + 6 + 7
        val w1 = c.textW(H.mis, Face.UI800, 11.0, 0.04); val w1b = c.textW(H.misMax, Face.UI800, 8.0, 0.12)
        val w2 = c.textW(H.flr, Face.UI800, 11.0, 0.04); val w3 = c.textW(H.lives, Face.UI800, 11.0, 1 / 11.0)
        val iw = cw - 27
        c.text(H.mis, x, ay, Face.UI800, 11.0, spacing = 0.04)
        c.text(H.misMax, x + w1 + 2 + 1.5, ay + 1, Face.UI800, 8.0, A70, spacing = 0.12)
        val gap = (iw - (w1 + 2 + w1b) - w2 - w3) / 2
        c.text(H.flr, x + w1 + 2 + w1b + gap, ay, Face.UI800, 11.0, spacing = 0.04)
        c.text(H.lives, x + iw, ay, Face.UI800, 11.0, Pal.ink, Align.RIGHT, spacing = 1 / 11.0)
    }
}

/** an ellipse path (CSS border-radius: 50% on a non-square box) */
fun Canvas2D.ellipse(cx: Double, cy: Double, rx: Double, ry: Double) {
    beginPath()
    for (i in 0..48) { val a = i / 48.0 * PI * 2; lineTo((cx + cos(a) * rx).toFloat(), (cy + sin(a) * ry).toFloat()) }
    closePath()
}

/* ---------- #radio: left 22 bottom 14 width 230 (hidden on touch and below 760 px) ---------- */
fun Game.drawRadio(c: Canvas2D, w: Double, h: Double) {
    val cw = 230.0; val ch = 94.0
    val x = 22.0; val y = h - 14 - ch
    c.card(x, y, cw, ch)
    val ix = x + 11.5; val iy = y + 10.5
    // #freq
    val fx = ix + 30; val fy = iy + 20.5
    c.setFill(Pal.orange); c.ellipse(fx, fy, 30.0, 20.0); c.fill()
    c.setStroke(Pal.ink); c.setLineWidth(1.5f); c.ellipse(fx, fy, 29.25, 19.25); c.stroke()
    c.text("99.1", fx, fy - 3.5, Face.HAND, 19.0, Pal.white, Align.CENTER)
    c.text("MHZ", fx, fy + 8.5, Face.UI700, 6.0, Pal.white, Align.CENTER, spacing = 0.15)
    // station, song, progress
    val sx = ix + 60 + 10; val sw = cw - 3 - 20 - 70
    c.text("PHI ĐỘI FM", sx, iy + 8.5, Face.HAND, 17.0)
    val names = audio.songNames
    val nm = (if (audio.radioOn) "♫ " else "◌ ") + (names.getOrNull(audio.songIndex) ?: "—")
    c.text(c.ellipsize(nm, Face.UI500, 9.5, min(140.0, sw)), sx, iy + 17 + 3 + 6, Face.UI500, 9.5, rgba(31, 29, 43, 0.8))
    c.setFill(Pal.white); c.rrect(sx, iy + 37, sw, 4.0, 2.0); c.fill()
    val pk = clamp(audio.songProgress, 0.0, 1.0)
    if (pk > 0) {
        c.save(); c.rrect(sx, iy + 37, sw, 4.0, 2.0); c.clip()
        c.setFillLinear(sx.toFloat(), 0f, (sx + sw * pk).toFloat(), 0f, floatArrayOf(0f, 0.33f, 0.66f, 1f), intArrayOf(argb("#e8562a"), argb("#f2b705"), argb("#1fb5a8"), argb("#2a6fd9")))
        c.fillRect(sx.toFloat(), (iy + 37).toFloat(), (sw * pk).toFloat(), 4f)
        c.restore()
    }
    c.setStroke(Pal.ink); c.setLineWidth(1f); c.rrect(sx, iy + 37, sw, 4.0, 2.0); c.stroke()
    // .ctrls
    val by = iy + 41 + 8; val bw = (cw - 3 - 20 - 10) / 3
    button(c, if (audio.radioOn) "■ Tắt" else "▶ Bật", ix, by, bw, on = audio.radioOn, size = 8.0) { audio.init(); audio.radioSet(!audio.radioOn) }
    button(c, "Bài kế ♫", ix + bw + 5, by, bw, size = 8.0) { audio.init(); audio.nextSong() }
    button(c, "Góc 🎥", ix + (bw + 5) * 2, by, bw, size = 8.0) { cycleCam() }
}

/* ---------- #brand (left 22 top 22) + #nav (top 72, only wider than 1000 px) ---------- */
fun Game.drawBrand(c: Canvas2D) {
    val x = 22.0; val y = 22.0
    // #logo: orange disc, a white paper dart and two blue stripes, all tilted −24°
    c.disc(x + 18, y + 18, 18.0, Pal.orange, 1.5)
    c.save(); c.circle(x + 18, y + 18, 17.0); c.clip()
    c.save(); c.translate((x + 7).toFloat(), (y + 17).toFloat()); c.rotate((-24 * PI / 180).toFloat()); c.scale(1.7f, 1f)
    c.setFill(Pal.white); c.beginPath(); c.moveTo(0f, -7f); c.lineTo(13f, 0f); c.lineTo(0f, 7f); c.closePath(); c.fill()
    c.restore()
    c.save(); c.translate((x + 18).toFloat(), (y + 27.5).toFloat()); c.rotate((-24 * PI / 180).toFloat())
    c.setFill(Pal.blue); c.fillRect(-14f, -1.5f, 28f, 3f); c.fillRect(-14f, -6.5f, 28f, 3f)
    c.restore()
    c.restore()
    c.setStroke(Pal.ink); c.setLineWidth(1.5f); c.circle(x + 18, y + 18, 18.0); c.stroke()
    val tx = x + 36 + 10
    c.text("Phi Đội Giấy", tx, y + 12, Face.BRUSH, 25.0)
    val hw = c.textW("沱灢", Face.HAN, 9.0, 0.1)
    c.text("沱灢", tx, y + 32, Face.HAN, 9.0, Pal.red, spacing = 0.1)
    c.text("ĐÀ NẴNG · PAPER SQUADRON", tx + hw + 4, y + 32, Face.UI700, 7.5, rgba(31, 29, 43, 0.6), spacing = 0.22)
}
fun Game.drawNav(c: Canvas2D) {
    var x = 22.0; val y = 72.0
    x += button(c, "Bản đồ ⌖", x, y) { togglePanel("pMap") } + 6
    x += button(c, "Cài đặt ⚙", x, y) { togglePanel("pSettings") } + 6
    x += button(c, "Phím ?", x, y) { togglePanel("pHelp") } + 6
    button(c, "Dừng ❚❚", x, y) { togglePause() }
}

/* =========================================================
   COMBAT HUD — gun reticle, target brackets, lead pipper, missile lock, threat arrows
   ========================================================= */
class Scr { var x = 0.0; var y = 0.0; var on = false }
private val SC = Scr(); private val SC2 = Scr()
private val _hv = Vec3(); private val _hc = Vec3(); private val _ha = Vec3(); private val _hb = Vec3()
private val INK_C = argb("#1f1d2b"); private val RED_C = argb("#d6452f"); private val GOLD_C = argb("#f2c230"); private val ORANGE_C = argb("#f37021")
private val HALO = rgba(255, 255, 255, 0.78); private val HALO_T = rgba(255, 255, 255, 0.85)

private fun Game.scr(v: Vec3, out: Scr): Scr {
    _hv.copy(v).project(camera)
    out.x = (_hv.x * 0.5 + 0.5) * ui.w
    out.y = (0.5 - _hv.y * 0.5) * ui.h
    out.on = _hv.z < 1 && _hv.x > -1 && _hv.x < 1 && _hv.y > -1 && _hv.y < 1
    return out
}
/** screen-space direction (radians) toward a world point, valid even behind the camera */
private fun Game.screenAng(v: Vec3): Double {
    _hc.copy(v).applyMatrix4(camera.matrixWorldInverse)
    if (_hc.z > 0 && abs(_hc.x) + abs(_hc.y) < 1e-3) return PI / 2
    return atan2(-_hc.y, _hc.x)
}
private fun Canvas2D.hstroke(w: Double, col: Int) {
    setLineWidth((w + 2.6).toFloat()); setStroke(HALO); stroke()
    setLineWidth(w.toFloat()); setStroke(col); stroke()
}
fun Canvas2D.label(txt: String, x: Double, y: Double, col: Int = INK_C, size: Double = 11.0, align: Int = Align.CENTER) {
    setFont(Face.UI800, size.toFloat()); setTextAlign(align); setTextBaseline(Baseline.MIDDLE)
    setLineWidth(3.2f); setStroke(HALO_T); strokeText(txt, x.toFloat(), y.toFloat())
    setFill(col); fillText(txt, x.toFloat(), y.toFloat())
}
private val CORNERS = arrayOf(intArrayOf(-1, -1), intArrayOf(1, -1), intArrayOf(1, 1), intArrayOf(-1, 1))
private fun Canvas2D.bracket(x: Double, y: Double, s: Double, col: Int, w: Double) {
    val c = max(4.0, s * 0.45)
    beginPath()
    for ((sx, sy) in CORNERS) {
        moveTo((x + sx * s).toFloat(), (y + sy * (s - c)).toFloat()); lineTo((x + sx * s).toFloat(), (y + sy * s).toFloat()); lineTo((x + sx * (s - c)).toFloat(), (y + sy * s).toFloat())
    }
    hstroke(w, col)
}
private fun Game.edgeArrow(c: Canvas2D, v: Vec3, col: Int, size: Double, txt: String) {
    val W = ui.w; val H = ui.h
    val a = screenAng(v); val cx = W / 2; val cy = H / 2; val rx = W / 2 - 46; val ry = H / 2 - 46
    val x = cx + cos(a) * rx; val y = cy + sin(a) * ry
    val s = size.toFloat()
    c.save()
    c.translate(x.toFloat(), y.toFloat())
    c.rotate(a.toFloat())
    c.beginPath()
    c.moveTo(s, 0f); c.lineTo(-s * 0.7f, s * 0.75f); c.lineTo(-s * 0.35f, 0f); c.lineTo(-s * 0.7f, -s * 0.75f); c.closePath()
    c.setFill(col); c.fill()
    c.setLineWidth(1.6f); c.setStroke(INK_C); c.stroke()
    c.restore()
    if (txt.isNotEmpty()) c.label(txt, x - cos(a) * 22, y - sin(a) * 18, if (col == GOLD_C) INK_C else col, 9.5)
}

fun Game.drawCombatHud(c: Canvas2D, w: Double, h: Double, dt: Double) {
    if (!game.started || ui.studio || MS.state == "done") return
    val cam = camera.position
    // the landmark to defend
    MS.lm?.let { L ->
        val p = _ha.set(L.x, L.y + 30, L.z)
        scr(p, SC)
        if (SC.on) {
            c.circle(SC.x, SC.y, 9.0); c.setFill(GOLD_C); c.fill(); c.setLineWidth(1.6f); c.setStroke(INK_C); c.stroke()
            c.label("★", SC.x, SC.y + 0.5, INK_C, 10.0)
            val bw = 46.0
            c.setFill(rgba(255, 255, 255, 0.85)); c.fillRect((SC.x - bw / 2 - 1).toFloat(), (SC.y + 13).toFloat(), (bw + 2).toFloat(), 6f)
            c.setFill(if (L.hp > 50) argb("#1fb5a8") else if (L.hp > 25) GOLD_C else RED_C); c.fillRect((SC.x - bw / 2).toFloat(), (SC.y + 14).toFloat(), (bw * L.hp / 100).toFloat(), 4f)
            c.label(L.name, SC.x, SC.y + 27, INK_C, 9.0)
        } else edgeArrow(c, p, GOLD_C, 11.0, "★ " + ceil(L.hp).toInt() + "%")
    }
    // supply crates
    for (k in PKS) {
        scr(k.p, SC)
        val d = k.p.distanceTo(cam)
        if (SC.on) { if (d < 1400) { c.circle(SC.x, SC.y, 13.0); c.hstroke(2.0, ORANGE_C); c.label(PKI[k.kind]!!.icon + " " + (d * 3).roundToInt() + "m", SC.x, SC.y - 22, ORANGE_C, 10.0) } }
        else if (d < 1600) edgeArrow(c, k.p, ORANGE_C, 9.0, "📦")
    }
    // enemies
    val T = PL.target
    for (e in EN) {
        if (!e.alive || e.demo) continue
        val d = e.pos.distanceTo(cam); val isT = e === T
        scr(e.pos, SC)
        if (SC.on && d < 3200) {
            val s = clamp((e.r * 300) / max(d, 1.0), if (isT) 12.0 else 8.0, if (e.k == "boss") 120.0 else 60.0)
            c.bracket(SC.x, SC.y, s, RED_C, if (isT) 2.4 else 1.5)
            if (e.hp < e.hpMax && e.k != "boss") {
                c.setFill(rgba(255, 255, 255, 0.85)); c.fillRect((SC.x - s).toFloat(), (SC.y + s + 4).toFloat(), (s * 2).toFloat(), 4f)
                c.setFill(RED_C); c.fillRect((SC.x - s).toFloat(), (SC.y + s + 4).toFloat(), (s * 2 * max(0.0, e.hp) / e.hpMax).toFloat(), 4f)
            }
            if (isT) c.label("${e.kd.name.uppercase()} · ${(d * 3).roundToInt()} m", SC.x, SC.y - s - 10, RED_C, 10.0)
            e.parts?.let { parts ->
                for (pt in parts) {
                    if (!pt.alive || (pt.core && e.podsLeft > 0)) continue
                    scr(pt.wp, SC2)
                    if (!SC2.on) continue
                    val r = clamp((pt.r * e.sc * 300) / max(pt.wp.distanceTo(cam), 1.0), 5.0, 26.0)
                    c.circle(SC2.x, SC2.y, r); c.hstroke(if (pt === PL.tpart) 2.4 else 1.4, if (pt.core) RED_C else argb("#8e44ad"))
                }
            }
        } else if (isT || d < 900) edgeArrow(c, e.pos, RED_C, if (isT) 12.0 else 8.0, if (isT) "${(d * 3).roundToInt()}m" else "")
    }
    if (F.mode == "crash") { drawFloaters(c); return }
    // gun reticle (boresight, or the helicopter's turret)
    aimDir(_hb)
    _ha.copy(F.pos).addScaledVector(_hb, 350.0)
    scr(_ha, SC)
    val rx = if (SC.on) SC.x else w / 2; val ry = if (SC.on) SC.y else h / 2; val rc = if (PL.over) RED_C else INK_C
    c.beginPath()
    for (k in 0 until 4) {
        val a0 = k * (PI / 2) + 0.28; val a1 = (k + 1) * (PI / 2) - 0.28
        c.moveTo((rx + cos(a0) * 15).toFloat(), (ry + sin(a0) * 15).toFloat()); c.arc(rx.toFloat(), ry.toFloat(), 15f, a0.toFloat(), a1.toFloat())
    }
    c.hstroke(1.8, rc)
    c.circle(rx, ry, 1.8); c.setFill(rc); c.fill()
    if (PL.over) c.label("QUÁ NHIỆT", rx, ry + 28, RED_C, 9.5)
    if (PL.hitT > 0) {
        c.beginPath()
        for ((sx, sy) in CORNERS) { c.moveTo((rx + sx * 7).toFloat(), (ry + sy * 7).toFloat()); c.lineTo((rx + sx * 13).toFloat(), (ry + sy * 13).toFloat()) }
        c.hstroke(2.2, ORANGE_C)
    }
    // lead pipper + missile seeker on the current target
    if (T != null && T.alive) {
        val tp = if (T.parts != null) (PL.tpart?.wp ?: T.pos) else T.pos
        val d = tp.distanceTo(F.pos)
        if (d < 1000) {
            leadPoint(F.pos, tp, T.vel, BSPD + F.v, _ha)
            scr(_ha, SC2)
            if (SC2.on) {
                c.circle(SC2.x, SC2.y, 7.0); c.hstroke(1.6, RED_C)
                c.circle(SC2.x, SC2.y, 2.0); c.setFill(RED_C); c.fill()
                if (hypot(SC2.x - rx, SC2.y - ry) > 22) {
                    c.setLineDash(3f, 5f); c.beginPath(); c.moveTo(rx.toFloat(), ry.toFloat()); c.lineTo(SC2.x.toFloat(), SC2.y.toFloat())
                    c.setLineWidth(1f); c.setStroke(rgba(214, 69, 47, 0.55)); c.stroke(); c.setLineDash(0f, 0f)
                }
            }
        }
        scr(tp, SC2)
        if (PL.lock > 0.02 && SC2.on) {
            val sz = lerp(70.0, 16.0, PL.lock); val rot = (1 - PL.lock) * PI
            c.save()
            c.translate(SC2.x.toFloat(), SC2.y.toFloat())
            c.rotate((PI / 4 + rot).toFloat())
            c.beginPath(); c.rect(-sz.toFloat(), -sz.toFloat(), (sz * 2).toFloat(), (sz * 2).toFloat())
            c.hstroke(if (PL.lock >= 1) 2.6 else 1.6, if (PL.lock >= 1) RED_C else ORANGE_C)
            c.restore()
            if (PL.lock >= 1) c.label("KHOÁ ●", SC2.x, SC2.y + sz + 18, RED_C, 10.5)
        }
    }
    // incoming missiles: red wedges around the reticle
    for (m in MIS) {
        if (m.team != 1 || m.tgt?.player != true) continue
        val a = screenAng(m.p); val R = 46f
        c.save(); c.translate(rx.toFloat(), ry.toFloat()); c.rotate(a.toFloat())
        c.beginPath(); c.moveTo(R + 12, 0f); c.lineTo(R, -7f); c.lineTo(R, 7f); c.closePath()
        c.setFill(if (sin(game.t * 20) > 0) RED_C else argb("#ff9a8a")); c.fill()
        c.restore()
    }
    // where the last hit came from
    if (PL.hurtT > 0) {
        _ha.copy(F.pos).addScaledVector(PL.hurtDir, 60.0)
        val a = screenAng(_ha); val R = min(w, h) * 0.3
        c.beginPath(); c.arc((w / 2).toFloat(), (h / 2).toFloat(), R.toFloat(), (a - 0.35).toFloat(), (a + 0.35).toFloat())
        c.setLineWidth(7f); c.setStroke(rgba(214, 69, 47, clamp(PL.hurtT * 0.7, 0.0, 1.0))); c.stroke()
    }
    if (MS.outside) MS.center?.let { edgeArrow(c, _ha.set(it.x, F.pos.y, it.z), GOLD_C, 12.0, "VÙNG TÁC CHIẾN") }
    drawFloaters(c)
}
private fun Game.drawFloaters(c: Canvas2D) {
    for (f in FT) {
        scr(f.p, SC)
        if (!SC.on) continue
        val u = 1 - f.t / 1.6
        c.save()
        c.setAlpha(clamp(f.t * 2, 0.0, 1.0).toFloat())
        c.setFont(Face.HAND, 22f); c.setTextAlign(Align.CENTER); c.setTextBaseline(Baseline.MIDDLE)
        val y = (SC.y - 24 - u * 40).toFloat()
        c.setLineWidth(4f); c.setStroke(rgba(255, 255, 255, 0.9)); c.strokeText(f.txt, SC.x.toFloat(), y)
        c.setFill(argb(f.col)); c.fillText(f.txt, SC.x.toFloat(), y)
        c.restore()
    }
}
