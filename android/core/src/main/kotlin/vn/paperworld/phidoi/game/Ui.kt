package vn.paperworld.phidoi.game

import vn.paperworld.phidoi.engine.*
import vn.paperworld.phidoi.world.QP
import kotlin.math.*

/* =========================================================
   UI — the web build's DOM overlay, drawn with Canvas2D (units: CSS px = dp)
   ========================================================= */
object Pal {
    val paper = argb("#f4efe2"); val paper2 = argb("#fbf8ef"); val ink = argb("#1f1d2b"); val red = argb("#d6452f"); val teal = argb("#1f9a8a")
    val gold = argb("#f2c230"); val orange = argb("#f37021"); val blue = argb("#1f5fbf"); val white = argb("#ffffff")
    val inkShadow = rgba(31, 29, 43, 0.85)
}

/** a tappable region recorded while drawing; the newest drawn is on top */
class Hit(val x: Double, val y: Double, val w: Double, val h: Double, val round: Boolean, val onDown: () -> Unit, val onUp: (() -> Unit)? = null, val onMove: ((Double, Double) -> Unit)? = null) {
    fun contains(px: Double, py: Double): Boolean {
        if (round) { val rx = w / 2; val ry = h / 2; val dx = (px - x - rx) / rx; val dy = (py - y - ry) / ry; return dx * dx + dy * dy <= 1.0 }
        return px >= x && px <= x + w && py >= y && py <= y + h
    }
}
class Ptr(val hit: Hit?, var lx: Double, var ly: Double) { var look = false }

class UiState {
    var hits = ArrayList<Hit>(); var next = ArrayList<Hit>()
    val ptrs = HashMap<Int, Ptr>()
    val pressed = HashSet<String>()
    /** stick knob offset (dp) */
    var knobX = 0.0; var knobY = 0.0
    var w = 800.0; var h = 450.0
    var hudT = 0.0
    // instrument readouts (web: updateHUD every 0.07 s)
    var spd = "000"; var spdSub = "TỐC ĐỘ"; var alt = "000"; var vs = "+0.0 m/s"; var vario = 0.0; var hdg = 0.0; var energy = 1.0
    var warn = ""; var lift = ""
    var settingsScroll = 0.0
    var studio = false
    /** wall-clock seconds (CSS animations keep running while the game is paused) */
    var clock = 0.0
    /** the transform + clip applied to hit regions recorded by [hit] (cards drawn scaled) */
    var hitTx = 0.0; var hitTy = 0.0; var hitK = 1.0; var hitClip: DoubleArray? = null
    /** HUD cards, maps, menu (Hud.kt, Maps.kt, Menu.kt) */
    val hud = HudState()
    var mapImg: Image2D? = null
    var introScroll = 0.0; var dragY = Double.NaN
    var everStarted = false; var radioPrimed = false
}

/* ---------- drawing helpers ---------- */
fun Canvas2D.rrect(x: Double, y: Double, w: Double, h: Double, r: Double) {
    val X = x.toFloat(); val Y = y.toFloat(); val W = w.toFloat(); val H = h.toFloat(); val R = min(r, min(w, h) / 2).toFloat()
    beginPath()
    moveTo(X + R, Y); lineTo(X + W - R, Y); arc(X + W - R, Y + R, R, (-PI / 2).toFloat(), 0f)
    lineTo(X + W, Y + H - R); arc(X + W - R, Y + H - R, R, 0f, (PI / 2).toFloat())
    lineTo(X + R, Y + H); arc(X + R, Y + H - R, R, (PI / 2).toFloat(), PI.toFloat())
    lineTo(X, Y + R); arc(X + R, Y + R, R, PI.toFloat(), (PI * 1.5).toFloat())
    closePath()
}
fun Canvas2D.circle(cx: Double, cy: Double, r: Double) { beginPath(); arc(cx.toFloat(), cy.toFloat(), r.toFloat(), 0f, 7f); closePath() }
/** the paper card: fill, ink border, hard ink shadow (web .card) */
fun Canvas2D.card(x: Double, y: Double, w: Double, h: Double, fill: Int = Pal.paper2, shadow: Double = 3.0, r: Double = 4.0, border: Double = 1.5) {
    if (shadow > 0) { setFill(Pal.inkShadow); rrect(x + shadow, y + shadow, w, h, r); fill() }
    setFill(fill); rrect(x, y, w, h, r); fill()
    setStroke(Pal.ink); setLineWidth(border.toFloat()); rrect(x, y, w, h, r); stroke()
}
fun Canvas2D.disc(cx: Double, cy: Double, r: Double, fill: Int, shadow: Double = 2.0, border: Double = 1.5) {
    if (shadow > 0) { setFill(Pal.ink); circle(cx + shadow, cy + shadow, r); fill() }
    setFill(fill); circle(cx, cy, r); fill()
    setStroke(Pal.ink); setLineWidth(border.toFloat()); circle(cx, cy, r); stroke()
}
fun Canvas2D.text(t: String, x: Double, y: Double, face: Face, size: Double, col: Int = Pal.ink, align: Int = Align.LEFT, base: Int = Baseline.MIDDLE, spacing: Double = 0.0) {
    setFont(face, size.toFloat()); setFill(col); setTextAlign(align); setTextBaseline(base)
    if (spacing == 0.0) { fillText(t, x.toFloat(), y.toFloat()); return }
    // letter-spaced (CSS letter-spacing): draw glyph by glyph
    var w = 0.0; for (ch in t) w += measureText(ch.toString()) + size * spacing
    w -= size * spacing
    var px = when (align) { Align.CENTER -> x - w / 2; Align.RIGHT -> x - w; else -> x }
    setTextAlign(Align.LEFT)
    for (ch in t) { val s = ch.toString(); fillText(s, px.toFloat(), y.toFloat()); px += measureText(s) + size * spacing }
}
/** [text] with a stroked halo behind it (the web's text-shadow / strokeText halos), letter-spacing aware */
fun Canvas2D.haloText(t: String, x: Double, y: Double, face: Face, size: Double, col: Int, halo: Int, haloW: Double, align: Int = Align.CENTER, spacing: Double = 0.0) {
    setFont(face, size.toFloat()); setTextBaseline(Baseline.MIDDLE); setStroke(halo); setLineWidth(haloW.toFloat())
    if (spacing == 0.0) { setTextAlign(align); strokeText(t, x.toFloat(), y.toFloat()) }
    else {
        var w = 0.0; for (ch in t) w += measureText(ch.toString()) + size * spacing
        w -= size * spacing
        var px = when (align) { Align.CENTER -> x - w / 2; Align.RIGHT -> x - w; else -> x }
        setTextAlign(Align.LEFT)
        for (ch in t) { val s = ch.toString(); strokeText(s, px.toFloat(), y.toFloat()); px += measureText(s) + size * spacing }
    }
    text(t, x, y, face, size, col, align, Baseline.MIDDLE, spacing)
}
fun Canvas2D.textW(t: String, face: Face, size: Double, spacing: Double = 0.0): Double { setFont(face, size.toFloat()); var w = measureText(t).toDouble(); if (spacing != 0.0) w += size * spacing * (t.length - 1); return w }
/** word-wrapped paragraph; returns the height used */
fun Canvas2D.para(t: String, x: Double, y: Double, maxW: Double, face: Face, size: Double, lh: Double, col: Int = Pal.ink, align: Int = Align.LEFT, draw: Boolean = true): Double {
    setFont(face, size.toFloat())
    val lines = ArrayList<String>()
    var cur = ""
    for (word in t.split(' ')) {
        val tryS = if (cur.isEmpty()) word else "$cur $word"
        if (measureText(tryS) > maxW && cur.isNotEmpty()) { lines.add(cur); cur = word } else cur = tryS
    }
    if (cur.isNotEmpty()) lines.add(cur)
    if (draw) for ((i, l) in lines.withIndex()) text(l, x, y + i * size * lh, face, size, col, align, Baseline.TOP)
    return lines.size * size * lh
}
/** a web .bar: white track, the fill (its gradient spans the filled part, like the CSS <i>), ink border; [fillA] = opacity of the fill */
fun Canvas2D.bar(x: Double, y: Double, w: Double, h: Double, k: Double, stops: FloatArray, cols: IntArray, border: Double = 1.5, fillA: Double = 1.0) {
    setFill(Pal.white); rrect(x, y, w, h, 3.0); fill()
    val fw = w * clamp(k, 0.0, 1.0)
    if (fw > 0) {
        save(); rrect(x, y, w, h, 3.0); clip()
        if (fillA < 1) setAlpha(fillA.toFloat())
        if (cols.size == 1) setFill(cols[0]) else setFillLinear(x.toFloat(), 0f, (x + fw).toFloat(), 0f, stops, cols)
        fillRect(x.toFloat(), y.toFloat(), fw.toFloat(), h.toFloat())
        restore()
    }
    setStroke(Pal.ink); setLineWidth(border.toFloat()); rrect(x, y, w, h, 3.0); stroke()
}

/* ---------- hit regions ---------- */
fun Game.hit(x: Double, y: Double, w: Double, h: Double, round: Boolean = false, onUp: (() -> Unit)? = null, onMove: ((Double, Double) -> Unit)? = null, onDown: () -> Unit) {
    val k = ui.hitK
    var X = x * k + ui.hitTx; var Y = y * k + ui.hitTy; var W = w * k; var H = h * k
    val cl = ui.hitClip
    if (cl != null && !round) {
        val x1 = min(X + W, cl[0] + cl[2]); val y1 = min(Y + H, cl[1] + cl[3])
        X = max(X, cl[0]); Y = max(Y, cl[1]); W = x1 - X; H = y1 - Y
        if (W <= 0 || H <= 0) return
    }
    ui.next.add(Hit(X, Y, W, H, round, onDown, onUp, onMove))
}
/** draw [block] translated + scaled, with the hit regions it records mapped the same way */
inline fun Game.scaled(c: Canvas2D, tx: Double, ty: Double, k: Double, block: () -> Unit) {
    val otx = ui.hitTx; val oty = ui.hitTy; val ok = ui.hitK
    c.save(); c.translate(tx.toFloat(), ty.toFloat()); if (k != 1.0) c.scale(k.toFloat(), k.toFloat())
    ui.hitTx = otx + tx * ok; ui.hitTy = oty + ty * ok; ui.hitK = ok * k
    block()
    c.restore()
    ui.hitTx = otx; ui.hitTy = oty; ui.hitK = ok
}
/** a web .btn: returns its width */
fun Game.button(c: Canvas2D, label: String, x: Double, y: Double, w0: Double = 0.0, on: Boolean = false, red: Boolean = false, size: Double = 9.5, action: () -> Unit): Double {
    val tw = c.textW(label.uppercase(), Face.UI700, size, 0.12)
    val w = if (w0 > 0) w0 else tw + 20; val h = size + 16
    c.card(x, y, w, h, if (red) Pal.red else if (on) Pal.ink else Pal.paper2, 1.5, 3.0)
    c.text(label.uppercase(), x + w / 2, y + h / 2 + 0.5, Face.UI700, size, if (red || on) Pal.paper2 else Pal.ink, Align.CENTER, spacing = 0.12)
    hit(x, y, w, h) { sfx.beep(660.0, 0.03, 0.04); action() }
    return w
}

/* =========================================================
   entry points from the host
   ========================================================= */
fun Game.drawUi(c: Canvas2D, w: Double, h: Double, density: Double, dt: Double) {
    ui.w = w; ui.h = h
    ui.clock += dt
    ui.next = ArrayList()
    ui.hitTx = 0.0; ui.hitTy = 0.0; ui.hitK = 1.0; ui.hitClip = null
    c.save()
    c.scale(density.toFloat(), density.toFloat())
    if (game.started) {
        if (!ui.studio) {
            drawCombatHud(c, w, h, dt)
            drawInstruments(c, w, h)
            drawMissionCards(c, w, h)
            drawMinimapCard(c, w, h, dt)
            drawWarnings(c, w, h)
            if (isTouch) drawTouch(c, w, h)
        }
        drawMessages(c, w, h)
        drawPanels(c, w, h)
    } else {
        drawIntro(c, w, h)
        drawMessages(c, w, h)
    }
    if (h > w && isTouch) drawRotate(c, w, h)
    c.restore()
    ui.hits = ui.next
}

fun Game.pointer(action: Int, id: Int, x: Double, y: Double) {
    when (action) {
        0 -> {
            audio.init()
            val hit = ui.hits.lastOrNull { it.contains(x, y) }
            val p = Ptr(hit, x, y)
            ui.ptrs[id] = p
            if (hit != null) { hit.onDown(); hit.onMove?.invoke(x, y) }
            else if (game.started && game.panel == null) { p.look = true; game.dragging = true }
        }
        1 -> {
            val p = ui.ptrs[id] ?: return
            if (p.hit != null) p.hit.onMove?.invoke(x, y)
            else if (p.look) {
                val dx = x - p.lx; val dy = y - p.ly
                game.dragYaw -= dx * 0.006
                game.dragPitch = clamp(game.dragPitch + dy * 0.004, -0.6, 1.2)
            }
            p.lx = x; p.ly = y
        }
        2, 3 -> {
            val p = ui.ptrs.remove(id) ?: return
            p.hit?.onUp?.invoke()
            if (p.look && ui.ptrs.values.none { it.look }) game.dragging = false
        }
    }
}

private val HOLD = setOf("w", "a", "s", "d", "q", "e", "x", "j", "arrowup", "arrowdown", "arrowleft", "arrowright", "shift", " ")
fun Game.key(name: String, down: Boolean, repeat: Boolean) {
    if (!down) { keys.remove(name); return }
    if (repeat && name !in HOLD) return
    keys.add(name)
    audio.init()
    if (!game.started) { if (name == "enter") start(); return }
    if (game.panel == "pResult") { if (name == "enter") resultAgain(); return }
    when (name) {
        "escape", "p" -> { if (game.panel != null && game.panel != "pPause") closePanels() else togglePause() }
        "f" -> IN.missileQ = true
        "g" -> IN.flareQ = true
        "tab", "t" -> IN.targetQ = true
        "c" -> cycleCam()
        "m" -> togglePanel("pMap")
        "h" -> ui.studio = !ui.studio
        "r" -> if (F.mode == "crash" && MS.state != "fail" && MS.state != "done") respawn()
        "b" -> audio.radioSet(!audio.radioOn)
        "n" -> audio.nextSong()
        "+", "=" -> game.zoom = clamp(game.zoom - 0.12, 0.5, 2.6)
        "-", "_" -> game.zoom = clamp(game.zoom + 0.12, 0.5, 2.6)
    }
}
fun Game.backKey() {
    when {
        !game.started -> hostQuit()
        game.panel == "pResult" -> backToMenu()
        game.panel != null && game.panel != "pPause" -> closePanels()
        else -> togglePause()
    }
}
fun Game.appPaused(on: Boolean) { if (on && game.started && !game.paused && game.panel == null) togglePause(true); keys.clear(); ui.ptrs.clear(); TOUCH.on = false; TOUCH.x = 0.0; TOUCH.y = 0.0; game.dragging = false }

/* =========================================================
   PANELS (web: closePanels / togglePanel / togglePause)
   ========================================================= */
fun Game.closePanels() { game.panel = null }
fun Game.togglePanel(id: String) {
    if (!game.started || game.panel == "pResult" || game.panel == "pPause") return
    val was = game.panel == id
    closePanels()
    if (was) return
    game.panel = id
    game.panelT = game.t
    if (id == "pMap") onBigMapOpen()
}
/* =========================================================
   INSTRUMENTS — airspeed, attitude, vario, altitude, heading tape (web: updateHUD + drawADI)
   ========================================================= */
fun Game.updateInstruments(dt: Double) {
    ui.hudT += dt
    if (ui.hudT < 0.07 && dt > 0) return
    ui.hudT = 0.0
    val C = F.c
    ui.spd = (F.v * 3.6).roundToInt().toString().padStart(3, '0')
    ui.spdSub = if (F.mode == "landed") "ĐÃ ĐÁP" else if (F.mode == "water") "TRÊN MẶT NƯỚC" else if (F.stall > 0) "THẤT TỐC" else if (C.heli) (if (F.v < 3) "TREO" else "TRỰC THĂNG") else "G ${"%.1f".format(F.n)}"
    ui.alt = max(0, (F.pos.y * 3).roundToInt()).toString().padStart(3, '0')
    val vs = F.vs * 3
    ui.vs = (if (vs >= 0) "+" else "−") + "%.1f".format(abs(vs)) + " m/s"
    ui.vario = clamp(vs / 15, -1.0, 1.0)
    ui.hdg = ((((-F.psi * 180) / PI) % 360) + 360) % 360
    ui.energy = clamp(F.energy, 0.0, 1.0)
}
private val TAPE_L = listOf("B", "ĐB", "Đ", "ĐN", "N", "TN", "T", "TB")
fun Game.drawADI(c: Canvas2D, cx: Double, cy: Double, r: Double) {
    val k = r / 70.0 // the web ADI canvas is 140 px drawn at 70 px
    c.save()
    c.translate(cx.toFloat(), cy.toFloat())
    c.scale((k).toFloat(), (k).toFloat())
    val W = 140f
    c.save()
    c.circle(0.0, 0.0, 68.0); c.clip()
    c.rotate((-F.rollVis).toFloat())
    val py = (clamp(F.gam, -1.2, 1.2) * 90).toFloat()
    c.setFill(argb("#9fd8f0")); c.fillRect(-W, -W * 2 + py, W * 2, W * 2)
    c.setFill(argb("#e9d5a5")); c.fillRect(-W, py, W * 2, W * 2)
    c.setStroke(Pal.ink); c.setLineWidth(2f)
    c.beginPath(); c.moveTo(-W, py); c.lineTo(W, py); c.stroke()
    c.setLineWidth(1.2f)
    for (kk in -3..3) { if (kk == 0) continue; val y = py - kk * 22; val w = if (kk % 2 != 0) 12f else 22f; c.beginPath(); c.moveTo(-w, y); c.lineTo(w, y); c.stroke() }
    c.restore()
    c.setStroke(Pal.red); c.setLineWidth(4f)
    c.beginPath(); c.moveTo(-34f, 0f); c.lineTo(-12f, 0f); c.lineTo(0f, 9f); c.lineTo(12f, 0f); c.lineTo(34f, 0f); c.stroke()
    c.setFill(Pal.red); c.circle(0.0, 0.0, 3.5); c.fill()
    // bank scale
    c.setStroke(Pal.ink); c.setLineWidth(1.5f)
    for (a in intArrayOf(-60, -30, -15, 0, 15, 30, 60)) { val rr = if (a != 0) 58f else 54f; val ra = ((a - 90) * PI / 180); c.beginPath(); c.moveTo((cos(ra) * 64).toFloat(), (sin(ra) * 64).toFloat()); c.lineTo((cos(ra) * rr).toFloat(), (sin(ra) * rr).toFloat()); c.stroke() }
    c.save(); c.rotate((-F.rollVis).toFloat())
    c.setFill(Pal.ink); c.beginPath(); c.moveTo(0f, -56f); c.lineTo(-6f, -46f); c.lineTo(6f, -46f); c.closePath(); c.fill()
    c.restore()
    if (F.stall > 0) { c.setFill(rgba(214, 69, 47, 0.25)); c.circle(0.0, 0.0, 68.0); c.fill() }
    c.restore()
    c.setStroke(Pal.ink); c.setLineWidth(1.5f); c.circle(cx, cy, r); c.stroke()
}
fun Game.drawInstruments(c: Canvas2D, w: Double, h: Double) {
    // card: gauge 70 · ADI 70 · vario 12 · gauge 70 · heading 150, gaps 10, padding 8/12
    val cw = 12 + 70 + 10 + 70 + 10 + 12 + 10 + 70 + 10 + 150 + 12.0; val ch = 70 + 16.0
    c.save()
    if (isTouch) { c.translate(14f, 6f); c.scale(0.62f, 0.62f) } else c.translate(((w - cw) / 2).toFloat(), (h - 14 - ch).toFloat())
    c.card(0.0, 0.0, cw, ch)
    var x = 12.0; val cy = ch / 2
    fun gauge(big: String, unit: String, small: String) {
        c.text(big, x + 35, cy - 8, Face.HAND, 30.0, Pal.ink, Align.CENTER)
        c.text(unit, x + 35, cy + 10, Face.UI700, 6.5, rgba(31, 29, 43, 0.75), Align.CENTER, spacing = 0.2)
        c.text(small, x + 35, cy + 22, Face.UI700, 9.0, Pal.ink, Align.CENTER, spacing = 0.04)
    }
    gauge(ui.spd, "KM/H", ui.spdSub); x += 80
    drawADI(c, x + 35, cy, 35.0); x += 80
    // vario
    c.setFill(Pal.white); c.rrect(x, 8.0, 12.0, 70.0, 3.0); c.fill()
    val k = ui.vario
    c.setFill(if (k >= 0) argb("#1fb5a8") else Pal.red)
    if (k >= 0) c.fillRect(x.toFloat(), (8 + 35 - k * 35).toFloat(), 12f, (k * 35).toFloat()) else c.fillRect(x.toFloat(), 43f, 12f, (-k * 35).toFloat())
    c.setStroke(Pal.ink); c.setLineWidth(1f); c.beginPath(); c.moveTo(x.toFloat(), 43f); c.lineTo((x + 12).toFloat(), 43f); c.stroke()
    c.setLineWidth(1.5f); c.rrect(x, 8.0, 12.0, 70.0, 3.0); c.stroke()
    x += 22
    gauge(ui.alt, "MÉT", ui.vs); x += 80
    // heading
    c.text(F.c.icon + " " + F.c.name, x + 75, 18.0, Face.HAND, 15.0, Pal.ink, Align.CENTER)
    val ty = 27.0
    c.setFill(Pal.white); c.rrect(x, ty, 150.0, 26.0, 3.0); c.fill()
    c.save(); c.rrect(x, ty, 150.0, 26.0, 3.0); c.clip()
    val off = 75 - 480 - (ui.hdg / 45) * 60.5
    for (r in 0 until 3) for (i in 0 until 8) {
        val sx = x + off + (r * 8 + i) * 60.5
        if (sx < x - 70 || sx > x + 220) continue
        val minor = i % 2 == 1
        c.text(TAPE_L[i], sx + 30, ty + 13, Face.UI800, if (minor) 8.0 else 10.0, if (i == 0) Pal.red else if (minor) rgba(31, 29, 43, 0.55) else Pal.ink, Align.CENTER)
        c.setStroke(Pal.ink); c.setLineWidth(1f); c.beginPath(); c.moveTo((sx + 60).toFloat(), ty.toFloat()); c.lineTo((sx + 60).toFloat(), (ty + 6).toFloat()); c.stroke()
    }
    c.restore()
    c.setStroke(Pal.ink); c.setLineWidth(1.5f); c.rrect(x, ty, 150.0, 26.0, 3.0); c.stroke()
    c.setFill(Pal.red); c.beginPath(); c.moveTo((x + 70).toFloat(), (ty - 1).toFloat()); c.lineTo((x + 80).toFloat(), (ty - 1).toFloat()); c.lineTo((x + 75).toFloat(), (ty + 6).toFloat()); c.closePath(); c.fill()
    c.bar(x, ty + 30, 150.0, 7.0, ui.energy, floatArrayOf(0f, 1f), intArrayOf(Pal.orange, Pal.gold))
    c.text(F.c.energy, x + 75, ty + 46, Face.UI700, 6.5, rgba(31, 29, 43, 0.7), Align.CENTER, spacing = 0.18)
    c.restore()
}

/* =========================================================
   MESSAGES — toast, tip, region title, warnings
   ========================================================= */
private fun fadeK(age: Double, dur: Double, fadeIn: Double, fadeOut: Double) = if (age < 0 || age > dur + fadeOut) 0.0 else min(clamp(age / fadeIn, 0.0, 1.0), clamp((dur + fadeOut - age) / fadeOut, 0.0, 1.0))
fun Game.drawMessages(c: Canvas2D, w: Double, h: Double) {
    val t = game.t
    // region title
    val ta = fadeK(t - msg.titleT, 4.6, 0.7, 0.7)
    if (ta > 0) {
        c.save(); c.setAlpha(ta.toFloat())
        val y = h * 0.27
        if (msg.tHan.isNotEmpty()) c.text(msg.tHan, w / 2, y - 34, Face.HAN, 15.0, Pal.red, Align.CENTER, spacing = 0.4)
        val big = clamp(w * 0.042, 30.0, 58.0)
        c.setFont(Face.BRUSH, big.toFloat()); c.setTextAlign(Align.CENTER); c.setTextBaseline(Baseline.MIDDLE)
        c.setStroke(rgba(255, 255, 255, 0.75)); c.setLineWidth(4f); c.strokeText(msg.tBig, (w / 2).toFloat(), y.toFloat())
        c.text(msg.tBig, w / 2, y, Face.BRUSH, big, Pal.ink, Align.CENTER)
        c.setStroke(rgba(255, 255, 255, 0.9)); c.setLineWidth(3f); c.setFont(Face.UI500, 12f); c.strokeText(msg.tSub, (w / 2).toFloat(), (y + big * 0.62 + 6).toFloat())
        c.text(msg.tSub, w / 2, y + big * 0.62 + 6, Face.UI500, 12.0, Pal.ink, Align.CENTER)
        c.restore()
    }
    // tip card
    val tipA = fadeK(t - msg.tipT, msg.tipMs / 1000, 0.6, 0.6)
    if (tipA > 0) {
        val sc = if (isTouch) 0.78 else 1.0
        val maxW = min(540.0, w * 0.76)
        val hw = c.textW(msg.tipH, Face.HAND, 21.0); val pw = min(c.textW(msg.tipP, Face.UI500, 9.5), maxW - 32)
        val cw = min(maxW, max(max(hw, pw) + 32, 160.0))
        val ph = c.para(msg.tipP, 0.0, 0.0, cw - 32, Face.UI500, 9.5, 1.4, draw = false)
        val ch = 6 + 10 + 24 + ph + 7
        c.save(); c.setAlpha(tipA.toFloat())
        c.translate((w / 2).toFloat(), (if (isTouch) 62.0 else h - 150 - ch * sc).toFloat()); c.scale(sc.toFloat(), sc.toFloat()); c.rotate(-0.007f)
        c.card(-cw / 2, 0.0, cw, ch)
        c.text(msg.tipK.uppercase(), 0.0, 12.0, Face.UI700, 7.5, Pal.red, Align.CENTER, spacing = 0.18)
        c.text(msg.tipH, 0.0, 30.0, Face.HAND, 21.0, Pal.ink, Align.CENTER)
        c.para(msg.tipP, 0.0, 42.0, cw - 32, Face.UI500, 9.5, 1.4, rgba(31, 29, 43, 0.78), Align.CENTER)
        c.restore()
    }
    // toast
    val toA = fadeK(t - msg.toastT, 1.9, 0.4, 0.4)
    if (toA > 0 && msg.toast.isNotEmpty()) {
        val s = msg.toast.uppercase(); val tw = c.textW(s, Face.UI700, 9.5, 0.14) + 28
        val y = if (isTouch) 150.0 else h - 236
        c.save(); c.setAlpha(toA.toFloat())
        c.card(w / 2 - tw / 2, y, tw, 26.0)
        c.text(s, w / 2, y + 13.5, Face.UI700, 9.5, Pal.ink, Align.CENTER, spacing = 0.14)
        c.restore()
    }
}
fun Game.drawWarnings(c: Canvas2D, w: Double, h: Double) {
    if (ui.warn.isNotEmpty()) {
        // #warn.show: animation blink 0.5s steps(2) — full, then 35 %
        c.save(); c.setAlpha(blinkA(0.5).toFloat())
        c.haloText(ui.warn, w / 2, h * 0.34, Face.UI800, 15.0, Pal.red, Pal.white, 4.0, spacing = 0.25)
        c.restore()
    }
    if (ui.lift.isNotEmpty()) c.haloText(ui.lift, w / 2, h * 0.40, Face.UI700, 10.0, Pal.teal, Pal.white, 3.0, spacing = 0.2)
}

/* =========================================================
   TOUCH CONTROLS — stick, fire, missile, flares, boost, brake, camera, target, menu
   ========================================================= */
fun Game.drawTouch(c: Canvas2D, w: Double, h: Double) {
    if (game.panel != null) return
    // stick
    val R = 75.0; val sx = 26.0 + R; val sy = h - 26 - R
    c.setFill(rgba(251, 248, 239, 0.55)); c.circle(sx, sy, R); c.fill()
    c.setStroke(Pal.ink); c.setLineWidth(1.5f); c.circle(sx, sy, R); c.stroke()
    c.setStroke(rgba(31, 29, 43, 0.25)); c.setLineWidth(1f)
    c.beginPath(); c.moveTo(sx.toFloat(), (sy - R + 12).toFloat()); c.lineTo(sx.toFloat(), (sy + R - 12).toFloat()); c.moveTo((sx - R + 12).toFloat(), sy.toFloat()); c.lineTo((sx + R - 12).toFloat(), sy.toFloat()); c.stroke()
    c.disc(sx + ui.knobX, sy + ui.knobY, 30.0, Pal.gold)
    fun moveStick(px: Double, py: Double) {
        val r = 55.0
        var dx = px - sx; var dy = py - sy
        val l = hypot(dx, dy)
        if (l > r) { dx *= r / l; dy *= r / l }
        ui.knobX = dx; ui.knobY = dy
        fun f(v: Double) = sign(v) * max(0.0, abs(v) - 0.08) / 0.92
        TOUCH.x = f(dx / r); TOUCH.y = f(dy / r); TOUCH.on = true
    }
    hit(sx - R, sy - R, R * 2, R * 2, true, onUp = { ui.knobX = 0.0; ui.knobY = 0.0; TOUCH.x = 0.0; TOUCH.y = 0.0; TOUCH.on = false }, onMove = ::moveStick) {}
    // buttons
    fun tb(cx: Double, cy: Double, r: Double, label: String, fill: Int, keyName: String? = null, big: Boolean = false, act: (() -> Unit)? = null) {
        val id = keyName ?: label
        val on = id in ui.pressed
        val o = if (on) 1.0 else 0.0
        c.disc(cx + o, cy + o, r, if (on) blend(fill) else fill, if (on) 0.0 else 2.0)
        val col = if (fill == Pal.red) Pal.white else Pal.ink
        val lines = label.split("\n")
        val isSym = lines.size == 1 && label.length <= 2
        val fs = if (isSym) (if (r < 26) 17.0 else 20.0) else if (big) 12.0 else 9.0
        for ((i, l) in lines.withIndex()) c.text(l, cx + o, cy + o + (i - (lines.size - 1) / 2.0) * fs * 1.1, if (isSym) Face.UI700 else Face.UI800, fs, col, Align.CENTER, spacing = if (isSym) 0.0 else 0.04)
        hit(cx - r, cy - r, r * 2, r * 2, true, onUp = { ui.pressed.remove(id); if (keyName != null) keys.remove(keyName) }) {
            ui.pressed.add(id); hostVibrate(8)
            if (keyName != null) keys.add(keyName)
            if (keyName == "f") IN.missileQ = true
            if (keyName == "g") IN.flareQ = true
            act?.invoke()
        }
    }
    // #tbtns: 3×2 grid of 58 px, gap 9, right 20, bottom 22
    val gx = w - 20 - (58 * 3 + 9 * 2); val gy = h - 22 - (58 * 2 + 9)
    fun cell(i: Int, j: Int) = Pair(gx + i * 67 + 29, gy + j * 67 + 29)
    cell(0, 0).let { (x, y) -> tb(x, y, 29.0, "🎥", Pal.paper2, act = { cycleCam() }) }
    cell(1, 0).let { (x, y) -> tb(x, y, 29.0, "🎯", Pal.paper2, act = { IN.targetQ = true }) }
    cell(2, 0).let { (x, y) -> tb(x, y, 29.0, "PHANH", Pal.paper2, "x") }
    cell(0, 1).let { (x, y) -> tb(x, y, 29.0, "✦", Pal.paper2, "g") }
    cell(1, 1).let { (x, y) -> tb(x, y, 29.0, "🚀", Pal.red, "f") }
    cell(2, 1).let { (x, y) -> tb(x, y, 29.0, "ĐỐT\nSAU", Pal.gold, "shift") }
    // #tfire
    tb(w - 228 - 46, h - 30 - 46, 46.0, "BẮN", Pal.red, " ", big = true)
    // #tmenu: map / pause / settings, bottom centre
    val my = h - 14 - 23
    tb(w / 2 - 56, my, 23.0, "⌖", Pal.paper2, act = { togglePanel("pMap") })
    tb(w / 2, my, 23.0, "❚❚", Pal.paper2, act = { togglePause() })
    tb(w / 2 + 56, my, 23.0, "⚙", Pal.paper2, act = { togglePanel("pSettings") })
}
private fun blend(c: Int): Int { val r = ((c shr 16) and 255) * 92 / 100; val g = ((c shr 8) and 255) * 92 / 100; val b = (c and 255) * 92 / 100; return (c and -0x1000000) or (r shl 16) or (g shl 8) or b }

fun Game.drawRotate(c: Canvas2D, w: Double, h: Double) {
    c.setFill(Pal.paper); c.fillRect(0f, 0f, w.toFloat(), h.toFloat())
    c.text("Xoay ngang điện thoại", w / 2, h / 2 - 14, Face.HAND, 40.0, Pal.ink, Align.CENTER)
    c.text("PHI ĐỘI GIẤY CHƠI Ở CHẾ ĐỘ NGANG", w / 2, h / 2 + 24, Face.UI700, 12.0, Pal.ink, Align.CENTER, spacing = 0.2)
    hit(0.0, 0.0, w, h) {}
}

/* =========================================================
   PANELS — settings, help, pause (web: #pSettings, #pHelp, #pPause)
   ========================================================= */
fun Game.drawPanels(c: Canvas2D, w: Double, h: Double) {
    val p = game.panel ?: return
    // dim + swallow taps behind the panel
    c.setFill(rgba(31, 29, 43, 0.18)); c.fillRect(0f, 0f, w.toFloat(), h.toFloat())
    hit(0.0, 0.0, w, h) {}
    when (p) {
        "pSettings" -> drawSettings(c, w, h)
        "pHelp" -> drawHelp(c, w, h)
        "pPause" -> drawPause(c, w, h)
        "pMap" -> drawBigMapPanel(c, w, h)
        "pResult" -> drawResult(c, w, h)
    }
}
fun Game.panelFrame(c: Canvas2D, w: Double, h: Double, pw: Double, ph: Double, title: String, sub: String?, close: Boolean): Pair<Double, Double> {
    val x = (w - pw) / 2; val y = max(6.0, (h - ph) / 2)
    c.card(x, y, pw, ph)
    val pad = if (isTouch) 14.0 else 20.0
    c.text(title, x + pad, y + pad + 12, Face.HAND, if (isTouch) 22.0 else 30.0, Pal.ink)
    if (close) button(c, "Đóng ✕", x + pw - 12 - 70, y + 12, 70.0) { closePanels() }
    if (sub != null && !isTouch) c.text(sub, x + pad, y + pad + 36, Face.UI500, 10.5, rgba(31, 29, 43, 0.75))
    return Pair(x + pad, y + pad + (if (isTouch) 30.0 else 52.0))
}
private fun Game.settingRow(c: Canvas2D, x: Double, y: Double, w: Double, label: String, small: String?, on: Boolean, toggle: () -> Unit): Double {
    val rh = if (small != null && !isTouch) 34.0 else 28.0
    c.text(label, x, y + (if (small != null && !isTouch) 10.0 else rh / 2), Face.UI500, 11.0)
    if (small != null && !isTouch) c.text(small, x, y + 24, Face.UI500, 9.0, rgba(31, 29, 43, 0.65))
    // checkbox
    val bx = x + w - 18; val by = y + rh / 2 - 8
    c.setFill(if (on) Pal.red else Pal.white); c.rrect(bx, by, 16.0, 16.0, 3.0); c.fill()
    c.setStroke(Pal.ink); c.setLineWidth(1.5f); c.rrect(bx, by, 16.0, 16.0, 3.0); c.stroke()
    if (on) { c.setStroke(Pal.white); c.setLineWidth(2f); c.beginPath(); c.moveTo((bx + 3.5).toFloat(), (by + 8).toFloat()); c.lineTo((bx + 7).toFloat(), (by + 11.5).toFloat()); c.lineTo((bx + 12.5).toFloat(), (by + 4.5).toFloat()); c.stroke() }
    c.setStroke(rgba(31, 29, 43, 0.2)); c.setLineWidth(1f); c.setLineDash(3f, 3f); c.beginPath(); c.moveTo(x.toFloat(), (y + rh).toFloat()); c.lineTo((x + w).toFloat(), (y + rh).toFloat()); c.stroke(); c.setLineDash(0f, 0f)
    hit(x, y, w, rh) { sfx.beep(660.0, 0.03, 0.04); toggle() }
    return rh
}
fun Game.drawSettings(c: Canvas2D, w: Double, h: Double) {
    val pw = min(420.0, w * 0.94); val ph = if (isTouch) 236.0 else 300.0
    val (x, y0) = panelFrame(c, w, h, pw, ph, "Cài đặt", "Được ghi nhớ trên thiết bị này", true)
    val iw = pw - (x - (w - pw) / 2) * 2
    var y = y0
    y += settingRow(c, x, y, iw, "Hỗ trợ bay & ngắm", "Tự cân cánh, chống thất tốc, tránh địa hình, đạn hút về mục tiêu", SET.assist) { SET.assist = !SET.assist; store.put("assist", SET.assist) }
    y += settingRow(c, x, y, iw, "Đảo trục ngóc/chúc", "Như cần lái máy bay thật: kéo về (S) để ngóc mũi", SET.invert) { SET.invert = !SET.invert; store.put("invert", SET.invert) }
    y += settingRow(c, x, y, iw, "Va chạm thật", "Tắt để nảy nhẹ thay vì nhàu giấy khi đụng địa hình", SET.crash) { SET.crash = !SET.crash; store.put("crash", SET.crash) }
    y += settingRow(c, x, y, iw, "Bản đồ nhỏ xoay theo hướng bay", null, SET.mapRot) { SET.mapRot = !SET.mapRot; store.put("mapRot", SET.mapRot) }
    y += settingRow(c, x, y, iw, "Nhạc “Phi Đội FM”", null, audio.radioOn) { audio.radioSet(!audio.radioOn) }
    // quality
    c.text("Chất lượng đồ hoạ", x, y + 14, Face.UI500, 11.0)
    c.text(Q.name, x + 120, y + 14, Face.UI700, 9.0, rgba(31, 29, 43, 0.65))
    button(c, "Đổi ⚙", x + iw - 64, y + 2, 64.0) {
        val next = (QP.indexOf(Q) + 1) % QP.size
        store.put("quality", next)
        toast("Chất lượng · " + QP[next].name + " — mở lại game để áp dụng")
    }
}
private val HELP = listOf(
    "Cần trái" to "Ngóc / chúc mũi & nghiêng cánh để rẽ",
    "BẮN" to "Giữ để bắn — giữ lâu súng sẽ nóng, quá nhiệt phải chờ nguội",
    "🚀" to "Phóng tên lửa — giữ mục tiêu trong khung ngắm đến khi KHOÁ",
    "✦" to "Thả pháo sáng — đánh lạc hướng tên lửa đang đuổi theo bạn",
    "🎯 · 🎥" to "Đổi mục tiêu · đổi góc máy (bám đuôi, gần, buồng lái, khoá mục tiêu, toàn cảnh)",
    "ĐỐT SAU · PHANH" to "Tăng tốc (tốn nhiên liệu, tự hồi) · phanh gió",
    "Kéo màn hình" to "Nhìn quanh",
    "Trực thăng" to "Cần trái tiến · lùi · xoay · ĐỐT SAU lên · PHANH xuống · tháp pháo tự ngắm",
    "📦 Kiện hàng" to "Trực thăng Ahamove thả tiếp tế: 🔧 sửa máy bay · 🚀 tên lửa · ✦ pháo sáng — bay xuyên qua để nhận",
    "Bàn phím / tay cầm" to "W/S A/D Q/E · SHIFT · X · SPACE · F · G · TAB · V · C · M · ESC",
)
fun Game.drawHelp(c: Canvas2D, w: Double, h: Double) {
    val pw = min(560.0, w * 0.94); val ph = min(h - 12, 360.0)
    val (x, y0) = panelFrame(c, w, h, pw, ph, "Sổ tay phi công", "Bám đuôi địch, giữ khung ngắm trên vòng tròn dẫn bắn, khoá tên lửa rồi phóng. Đừng quên bảo vệ địa danh!", true)
    val iw = pw - (x - (w - pw) / 2) * 2
    var y = y0
    for ((k, v) in HELP) {
        c.text(k, x, y + 4, Face.UI800, 11.0, Pal.ink, base = Baseline.TOP)
        val hh = c.para(v, x + iw * 0.34, y + 4, iw * 0.66, Face.UI500, 11.0, 1.3)
        y += max(hh, 14.0) + 8
        c.setStroke(rgba(31, 29, 43, 0.2)); c.setLineWidth(1f); c.setLineDash(3f, 3f); c.beginPath(); c.moveTo(x.toFloat(), (y - 2).toFloat()); c.lineTo((x + iw).toFloat(), (y - 2).toFloat()); c.stroke(); c.setLineDash(0f, 0f)
    }
}
fun Game.drawPause(c: Canvas2D, w: Double, h: Double) {
    val pw = min(360.0, w * 0.92); val ph = 150.0
    val x = (w - pw) / 2; val y = (h - ph) / 2
    c.card(x, y, pw, ph)
    c.text("Tạm dừng", w / 2, y + 34, Face.HAND, 30.0, Pal.ink, Align.CENTER)
    c.text(pauseSub, w / 2, y + 62, Face.UI500, 10.5, rgba(31, 29, 43, 0.75), Align.CENTER)
    val bw = 100.0; val gap = 8.0; val bx = w / 2 - (bw * 3 + gap * 2) / 2
    button(c, "Tiếp tục ▶", bx, y + 92, bw, red = true) { togglePause(false) }
    button(c, "Chơi lại ↺", bx + bw + gap, y + 92, bw) { restartMission() }
    button(c, "Về menu ☰", bx + (bw + gap) * 2, y + 92, bw) { backToMenu() }
}

/** CSS `@keyframes blink { 50% { opacity: .35 } }` with steps(2) over [period] seconds */
fun Game.blinkA(period: Double) = if ((ui.clock % period) < period / 2) 1.0 else 0.35
fun Game.hostQuit() = events.quit()
fun Game.hostVibrate(ms: Int) = events.vibrate(ms)
