package vn.paperworld.phidoi.game

import vn.paperworld.phidoi.engine.*
import kotlin.math.*

/* =========================================================
   MENU — the #intro card (craft / mission / difficulty pickers), start, results (#pResult)
   (web: markup ~300–345, renderMenu/start 6751–6790, CSS #intro + body.touch #intro)
   ========================================================= */

/** web: unlocked(i) — the first mission, or the previous one has stars (`?all` → the stored flag "unlockAll") */
fun Game.unlocked(i: Int) = i == 0 || store.getBool("unlockAll", false) || (SAVE.m[MISSIONS[i - 1].id]?.stars ?: 0) > 0

/** web: renderMenu() — the pickers are drawn live from PICK + SAVE; this keeps the selection valid */
fun Game.renderMenu() {
    val cur = ALLM.indexOfFirst { it.id == PICK.mission }
    if (cur < 0 || (cur < MISSIONS.size && !unlocked(cur))) PICK.mission = "m1"
    // the radio checkbox is what audioInit() reads on the first gesture (web: radioSet(A.wantOn ?? $("radioOn").checked))
    if (!ui.radioPrimed) { ui.radioPrimed = true; if (!audio.radioOn) audio.radioSet(store.getBool("radioOn", true)) }
}

fun Game.start() { if (!READY || game.started) return; startMission(PICK.mission) }
fun Game.restartMission() { MS.def?.let { startMission(it.id) } }
fun Game.resultAgain() { MS.def?.let { startMission(it.id) } }

/** web startMission: audioInit(); on the first start radioSet($("radioOn").checked); switch the radio to M.song */
fun Game.missionAudio(song: Int) {
    audio.init()
    if (!ui.everStarted) { audio.radioSet(store.getBool("radioOn", true)); ui.everStarted = true }
    audio.setSong(song)
}

/* ---------- rich text: runs of (text, face, colour), word-wrapped ---------- */
class Run(val t: String, val face: Face, val col: Int)
/** wrap [runs] at [maxW]; returns the height (lines × [lh]) */
fun Canvas2D.rich(runs: List<Run>, x: Double, y: Double, maxW: Double, size: Double, lh: Double, draw: Boolean): Double {
    var cx = 0.0; var line = 0
    for (r in runs) {
        val words = r.t.split(' ')
        for ((i, wd) in words.withIndex()) {
            val tok = if (i < words.size - 1) "$wd " else wd
            if (tok.isEmpty()) continue
            val ww = textW(wd, r.face, size)
            if (cx > 0 && cx + ww > maxW && wd.isNotEmpty()) { line++; cx = 0.0 }
            if (draw && wd.isNotEmpty()) text(wd, x + cx, y + line * lh + lh / 2, r.face, size, r.col)
            cx += textW(tok, r.face, size)
        }
    }
    return (line + 1) * lh
}
/** "*bold* plain" → runs */
private fun runsOf(s: String, plain: Face, bold: Face, col: Int): List<Run> = s.split('*').mapIndexed { i, t -> Run(t, if (i % 2 == 1) bold else plain, col) }

private val CRAFTS = listOf(Triple("jet", "✈", "NHANH · LINH HOẠT"), Triple("seaplane", "🛩", "BỀN · 4 SÚNG"), Triple("heli", "🚁", "TREO · THÁP PHÁO"))
private val DIFFS = listOf("🪁" to "Dễ", "✈" to "Thường", "🔥" to "Khó")
private const val INTRO_P = "Băng Mực Đen thả diều mực, phi cơ giấy than và cả con Mực Khổng Lồ định bôi đen thành phố biển. Gấp chiếc phản lực giấy, cất cánh từ Mỹ Khê, giữ lấy Cầu Rồng, tượng Quan Âm Sơn Trà, Ngũ Hành Sơn và phố cổ Hội An."
private const val INTRO_KEYS = "*W/S* ngóc / chúc · *A/D* nghiêng · *Q/E* bánh lái · *SHIFT* đốt sau · *X* phanh · *SPACE* / chuột trái bắn · *F* / chuột phải tên lửa · *G* pháo sáng · *TAB* đổi mục tiêu · *V* lộn né đạn · *C* góc máy · *M* bản đồ · *ESC* tạm dừng"

/** a checkbox with the red accent colour */
fun Canvas2D.checkbox(x: Double, y: Double, s: Double, on: Boolean) {
    if (on) {
        setFill(Pal.red); rrect(x, y, s, s, 2.5); fill()
        setStroke(Pal.white); setLineWidth((s / 7).toFloat())
        beginPath(); moveTo((x + s * 0.22).toFloat(), (y + s * 0.5).toFloat()); lineTo((x + s * 0.42).toFloat(), (y + s * 0.7).toFloat()); lineTo((x + s * 0.78).toFloat(), (y + s * 0.3).toFloat()); stroke()
    } else {
        setFill(Pal.white); rrect(x, y, s, s, 2.5); fill()
        setStroke(argb("#767676")); setLineWidth(1f); rrect(x + 0.5, y + 0.5, s - 1, s - 1, 2.5); stroke()
    }
}

/** one .opt tile */
private fun Game.opt(c: Canvas2D, x: Double, y: Double, w: Double, h: Double, icon: String, label: String, small: String?, on: Boolean, lock: Boolean, act: () -> Unit) {
    val T = isTouch
    c.save()
    c.setFill(Pal.ink); c.rrect(x + 1.5, y + 1.5, w, h, 4.0); c.fill()
    c.setFill(if (on) Pal.gold else Pal.white); c.rrect(x, y, w, h, 4.0); c.fill()
    c.setStroke(Pal.ink); c.setLineWidth(1.5f); c.rrect(x, y, w, h, 4.0); c.stroke()
    val isz = if (T) 15.0 else 20.0
    var cy = y + 1.5 + (if (T) 3.0 else 7.0)
    c.text(icon, x + w / 2, cy + isz * 0.55, Face.UI700, isz, Pal.ink, Align.CENTER); cy += isz * 1.1 + 3
    c.text(label, x + w / 2, cy + 4.8, Face.UI700, 8.0, Pal.ink, Align.CENTER, spacing = 0.04); cy += 9.6 + 3
    if (small != null && small.isNotEmpty()) c.text(small, x + w / 2, cy + 4.5, Face.UI700, 8.5, Pal.orange, Align.CENTER, spacing = 0.06)
    if (lock) {
        c.text("🔒", x + w - 3, y + 2 + 6, Face.UI500, 10.0, Pal.ink, Align.RIGHT)
        // CSS opacity .45 on the whole tile (shadow included) over the paper card
        c.setFill(rgba(251, 248, 239, 0.55)); c.fillRect((x - 1).toFloat(), (y - 1).toFloat(), (w + 3.5).toFloat(), (h + 3.5).toFloat())
    }
    c.restore()
    hit(x, y, w, h) { sfx.beep(660.0, 0.03, 0.04); act() }
}
private fun Game.optH(small: Boolean): Double { val T = isTouch; return 3 + (if (T) 3.0 else 7.0) + (if (T) 15.0 else 20.0) * 1.1 + 3 + 9.6 + (if (small) 12.0 else 0.0) + (if (T) 3.0 else 6.0) }

/** the #intro card body; returns its height. With [draw] = false it only measures. */
private fun Game.introBody(c: Canvas2D, x: Double, y0: Double, iw: Double, draw: Boolean): Double {
    val T = isTouch
    var y = y0; var m = 0.0
    fun gap(top: Double) { y += max(m, top); m = 0.0 }
    // .kick
    if (!T) {
        if (draw) c.text("MỘT ĐÀ NẴNG MÀU NƯỚC · GẤP BẰNG GIẤY", x, y + 4.7, Face.UI700, 7.5, Pal.red, spacing = 0.18)
        y += 9.4; m = 8.0
    }
    // h2 + em
    gap(0.0)
    if (!T) {
        if (draw) {
            c.text("Phi Đội Giấy.", x, y + 20.5, Face.HAND, 40.0)
            c.save(); c.translate((x + iw / 2).toFloat(), (y + 39.2 + 6 + 11.3).toFloat()); c.rotate((-2 * PI / 180).toFloat())
            c.text("Không chiến trên bầu trời Đà Nẵng", -iw / 2, 0.0, Face.BRUSH, 23.0, Pal.orange)
            c.restore()
        }
        y += 39.2 + 6 + 22.5
    } else {
        if (draw) {
            val tw = c.textW("Phi Đội Giấy.", Face.HAND, 26.0)
            c.text("Phi Đội Giấy.", x, y + 19.5, Face.HAND, 26.0, base = Baseline.ALPHABETIC)
            val ew = c.textW("Không chiến trên bầu trời Đà Nẵng", Face.BRUSH, 17.0)
            c.save(); c.translate((x + tw + 8 + ew / 2).toFloat(), (y + 15.0).toFloat()); c.rotate((-2 * PI / 180).toFloat())
            c.text("Không chiến trên bầu trời Đà Nẵng", -ew / 2, 4.5, Face.BRUSH, 17.0, Pal.orange, base = Baseline.ALPHABETIC)
            c.restore()
        }
        y += 25.5
    }
    // p
    if (!T) { gap(10.0); y += c.para(INTRO_P, x, y, iw, Face.UI500, 11.0, 1.55, rgba(31, 29, 43, 0.88), draw = draw); m = 8.0 }
    // pickers
    fun pick(title: String, cols: Int, n: Int, small: Boolean, cell: (Int, Double, Double, Double, Double) -> Unit) {
        gap(if (T) 5.0 else 6.0)
        if (draw) c.text(title, x, y + 5.3, Face.UI700, 8.5, rgba(31, 29, 43, 0.75), spacing = 0.18)
        y += 10.6 + 6
        val cwid = (iw - 6 * (cols - 1)) / cols; val rh = optH(small)
        for (i in 0 until n) {
            val col = i % cols; val row = i / cols
            if (draw) cell(i, x + col * (cwid + 6), y + row * (rh + 6), cwid, rh)
        }
        val rows = (n + cols - 1) / cols
        y += rows * rh + (rows - 1) * 6
        m = if (T) 5.0 else 8.0
    }
    pick("CHỌN PHI CƠ", 3, 3, true) { i, cx, cy, cw, ch ->
        val (id, icon, sm) = CRAFTS[i]
        opt(c, cx, cy, cw, ch, icon, CRAFT[id]!!.name, sm, PICK.craft == id, false) { PICK.craft = id; store.putString("craft", id); renderMenu() }
    }
    pick("CHỌN NHIỆM VỤ", 4, ALLM.size, true) { i, cx, cy, cw, ch ->
        val M = ALLM[i]
        val lock = i < MISSIONS.size && !unlocked(i); val sv = SAVE.m[M.id]
        val st = if (M.survival) (if (SAVE.survBest != 0) "★ " + SAVE.survBest else "") else if (M.practice) "" else if (sv != null) "★".repeat(sv.stars) + "☆".repeat(3 - sv.stars) else ""
        opt(c, cx, cy, cw, ch, M.icon, M.short, st, M.id == PICK.mission, lock) {
            if (lock) toast("🔒 Hoàn thành nhiệm vụ trước để mở khoá")
            else { PICK.mission = M.id; store.putString("mission", M.id); renderMenu() }
        }
    }
    // #mDesc
    if (!T) {
        gap(2.0)
        val M = ALLM.find { it.id == PICK.mission } ?: ALLM[0]; val sv = SAVE.m[M.id]
        val runs = arrayListOf(Run(M.desc, Face.UI500, Pal.ink))
        if (sv != null && sv.score != 0) runs.add(Run(" · Kỷ lục ${sv.score}", Face.UI500, rgba(31, 29, 43, 0.7)))
        val tw = iw - 3 - 16
        val th = c.rich(runs, 0.0, 0.0, tw, 10.5, 15.2, false)
        val bh = max(30.0, 12 + 16.8 + th)
        if (draw) {
            c.setFill(rgba(243, 112, 33, 0.07)); c.fillRect(x.toFloat(), y.toFloat(), iw.toFloat(), bh.toFloat())
            c.setFill(Pal.orange); c.fillRect(x.toFloat(), y.toFloat(), 3f, bh.toFloat())
            c.text(M.icon + " " + M.name, x + 11, y + 6 + 8.4, Face.HAND, 16.0)
            c.rich(runs, x + 11, y + 6 + 16.8, tw, 10.5, 15.2, true)
        }
        y += bh; m = 8.0
    }
    pick("ĐỘ KHÓ", 3, 3, false) { i, cx, cy, cw, ch ->
        val (icon, nm) = DIFFS[i]
        opt(c, cx, cy, cw, ch, icon, nm, null, PICK.diff == i, false) { PICK.diff = i; store.put("diff", i); renderMenu() }
    }
    // the two checkboxes
    fun label(txt: String, on: Boolean, toggle: () -> Unit) {
        gap(0.0)
        val fs = if (T) 10.0 else 10.5
        val th = c.para(txt, 0.0, 0.0, iw - 22, Face.UI500, fs, 1.3, draw = false)
        val rh = max(14.0, th)
        if (draw) {
            c.checkbox(x, y + rh / 2 - 7, 14.0, on)
            c.para(txt, x + 22, y + (rh - th) / 2, iw - 22, Face.UI500, fs, 1.3)
            hit(x, y - 2, iw, rh + 4) { sfx.beep(660.0, 0.03, 0.04); toggle() }
        }
        y += rh; m = if (T) 3.0 else 6.0
    }
    label("Hỗ trợ bay & ngắm (tự cân cánh, tránh địa hình, đạn hút mục tiêu)", SET.assist) { SET.assist = !SET.assist; store.put("assist", SET.assist) }
    label("Bật nhạc “Phi Đội FM” khi cất cánh", store.getBool("radioOn", true)) { store.put("radioOn", !store.getBool("radioOn", true)) }
    // #go
    gap(4.0)
    val gh = if (T) 43.0 else 49.0
    if (draw) {
        c.setFill(Pal.ink); c.rrect(x + 2.5, y + 2.5, iw, gh, 4.0); c.fill()
        c.setFill(Pal.red); c.rrect(x, y, iw, gh, 4.0); c.fill()
        c.setStroke(Pal.ink); c.setLineWidth(1.5f); c.rrect(x, y, iw, gh, 4.0); c.stroke()
        val px = if (T) 14.0 else 16.0
        c.text("CẤT CÁNH · XUẤT KÍCH", x + 1.5 + px, y + gh / 2, Face.UI800, 11.0, Pal.white, spacing = 0.2)
        c.text("↗", x + iw - 1.5 - px, y + gh / 2, Face.UI800, 16.0, Pal.white, Align.RIGHT)
        hit(x, y, iw, gh) { sfx.beep(660.0, 0.03, 0.04); start() }
    }
    y += gh
    // .keys
    if (!T) { gap(10.0); y += c.rich(runsOf(INTRO_KEYS, Face.UI500, Face.UI800, rgba(31, 29, 43, 0.75)), x, y, iw, 8.5, 8.5 * 1.85, draw) }
    y += m
    return y - y0
}

/** the #intro card: right 4vw, centred vertically (touch / narrow: centred); scaled down, then scrolled, when taller than the screen */
fun Game.drawIntro(c: Canvas2D, w: Double, h: Double) {
    val T = isTouch
    val cw = if (T) min(600.0, w * 0.94) else min(470.0, w * 0.92)
    val padT = if (T) 10.0 else 18.0; val padX = if (T) 14.0 else 20.0; val padB = if (T) 10.0 else 14.0
    val iw = cw - 3 - padX * 2
    val H = introBody(c, 0.0, 0.0, iw, false) + padT + padB + 3
    val maxH = h * (if (T) 0.94 else 0.95)
    val k = clamp(maxH / H, if (T) 0.6 else 0.8, 1.0)
    val viewH = min(H, maxH / k)
    val scrollMax = H - viewH
    ui.introScroll = clamp(ui.introScroll, 0.0, max(0.0, scrollMax))
    val sx = if (T || w <= 760) (w - cw * k) / 2 else w - 0.04 * w - cw * k
    val sy = (h - viewH * k) / 2
    scaled(c, sx, sy, k) {
        c.card(0.0, 0.0, cw, viewH)
        if (scrollMax > 0) hit(0.0, 0.0, cw, viewH, onUp = { ui.dragY = Double.NaN }, onMove = { _, py ->
            if (ui.dragY.isNaN()) ui.dragY = py
            else { ui.introScroll = clamp(ui.introScroll - (py - ui.dragY) / k, 0.0, scrollMax); ui.dragY = py }
        }) { ui.dragY = Double.NaN }
        else hit(0.0, 0.0, cw, viewH) {}
        c.save(); c.rrect(0.75, 0.75, cw - 1.5, viewH - 1.5, 3.5); c.clip()
        ui.hitClip = doubleArrayOf(sx, sy, cw * k, viewH * k)
        introBody(c, 1.5 + padX, 1.5 + padT - ui.introScroll, iw, true)
        ui.hitClip = null
        c.restore()
        if (scrollMax > 0) {
            val th = viewH * viewH / H; val ty = (viewH - th) * ui.introScroll / scrollMax
            c.setFill(rgba(31, 29, 43, 0.3)); c.rrect(cw - 6, ty + 3, 3.0, th - 6, 1.5); c.fill()
        }
    }
}

/* =========================================================
   RESULTS — #pResult: 勝/敗, title, stars, stats table, note, Nhiệm vụ kế ↗ / Chơi lại ↺ / Về menu ☰
   ========================================================= */
fun Game.drawResult(c: Canvas2D, w: Double, h: Double) {
    val r = result ?: return
    val T = isTouch
    val pw = min(460.0, w * 0.94); val padY = if (T) 12.0 else 18.0; val padX = if (T) 14.0 else 20.0
    val iw = pw - 3 - padX * 2
    val rowH = 23.0
    val noteH = if (r.note.isEmpty()) 0.0 else c.para(r.note, 0.0, 0.0, iw, Face.UI500, 10.0, 1.3, draw = false)
    val ph = 3 + padY * 2 + 20 + 40.8 + 2 + 37.5 + 6 + 6 + r.rows.size * rowH + 8 + noteH + 12 + 25.5
    val k = min(1.0, (h - 12) / ph)
    scaled(c, (w - pw * k) / 2, max(6.0, (h - ph * k) / 2), k) {
        c.card(0.0, 0.0, pw, ph)
        val cx = pw / 2; val x = 1.5 + padX
        var y = 1.5 + padY
        c.text(r.han, cx, y + 10, Face.HAN, 15.0, Pal.red, Align.CENTER, spacing = 0.4); y += 20
        c.text(r.title, cx, y + 20.4, Face.BRUSH, 34.0, Pal.ink, Align.CENTER); y += 40.8 + 2
        // ★★★ (font-size 30, letter-spacing 6px; lit stars are gold with an ink drop shadow)
        val sw = c.textW("★", Face.UI500, 30.0); val tot = sw * 3 + 6 * 3
        for ((i, on) in r.stars.withIndex()) {
            val sx = cx - tot / 2 + i * (sw + 6)
            if (on) c.text("★", sx + 1.5, y + 18.75 + 1.5, Face.UI500, 30.0, Pal.ink)
            c.text("★", sx, y + 18.75, Face.UI500, 30.0, if (on) Pal.gold else argb("#cfc8b4"))
        }
        y += 37.5 + 6 + 6
        for ((a, b) in r.rows) {
            c.text(a, x, y + rowH / 2 - 0.5, Face.UI500, 11.0)
            c.text(b, x + iw, y + rowH / 2 - 0.5, Face.UI800, 11.0, Pal.ink, Align.RIGHT)
            c.setStroke(rgba(31, 29, 43, 0.2)); c.setLineWidth(1f); c.setLineDash(3f, 3f)
            c.beginPath(); c.moveTo(x.toFloat(), (y + rowH - 0.5).toFloat()); c.lineTo((x + iw).toFloat(), (y + rowH - 0.5).toFloat()); c.stroke(); c.setLineDash(0f, 0f)
            y += rowH
        }
        y += 8
        if (noteH > 0) { c.para(r.note, cx, y, iw, Face.UI500, 10.0, 1.3, rgba(31, 29, 43, 0.75), Align.CENTER); y += noteH }
        y += 12
        val btns = ArrayList<Triple<String, Boolean, () -> Unit>>()
        r.next?.let { nx -> btns.add(Triple("Nhiệm vụ kế ↗", true) { startMission(nx.id) }) }
        btns.add(Triple("Chơi lại ↺", false) { resultAgain() })
        btns.add(Triple("Về menu ☰", false) { backToMenu() })
        val widths = btns.map { c.textW(it.first.uppercase(), Face.UI700, 9.5, 0.12) + 20 }
        var bx = cx - (widths.sum() + 8 * (btns.size - 1)) / 2
        for ((i, b) in btns.withIndex()) { button(c, b.first, bx, y, widths[i], red = b.second, action = b.third); bx += widths[i] + 8 }
    }
}
