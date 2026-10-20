package vn.paperworld.phidoi.game

import vn.paperworld.phidoi.engine.*

/* TEMPORARY: UI pieces that depend on combat/missions (HUD port replaces these) */
fun Game.drawCombatHud(c: Canvas2D, w: Double, h: Double, dt: Double) {}
fun Game.drawMissionCards(c: Canvas2D, w: Double, h: Double) {}
fun Game.drawMinimapCard(c: Canvas2D, w: Double, h: Double, dt: Double) {}
fun Game.onBigMapOpen() {}
fun Game.drawBigMapPanel(c: Canvas2D, w: Double, h: Double) {}
fun Game.drawResult(c: Canvas2D, w: Double, h: Double) {}
fun Game.pauseSub() = ""
fun Game.restartMission() {}
fun Game.resultAgain() {}
fun Game.backToMenu() { game.started = false; game.panel = null; game.paused = false; F.g.visible = false }
fun Game.start() { game.started = true; F.g.visible = true; game.camSnap = true; game.fade = 1.0 }
fun Game.drawIntro(c: Canvas2D, w: Double, h: Double) {
    val cw = 300.0; val ch = 120.0; val x = (w - cw) / 2; val y = (h - ch) / 2
    c.card(x, y, cw, ch)
    c.text("Phi Đội Giấy.", x + 20, y + 34, Face.HAND, 34.0)
    button(c, "CẤT CÁNH · XUẤT KÍCH ↗", x + 20, y + 70, cw - 40, red = true) { start() }
}
fun Game.hostQuit() = events.quit()
fun Game.hostVibrate(ms: Int) = events.vibrate(ms)
