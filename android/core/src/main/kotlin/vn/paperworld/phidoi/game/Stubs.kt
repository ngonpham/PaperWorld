package vn.paperworld.phidoi.game

import vn.paperworld.phidoi.engine.*

/* TEMPORARY: stand-ins for the combat/mission/HUD systems until they are ported */
class StubTarget { var alive = false; val pos = Vec3() }
class StubPL { var hp = 100.0; var hpMax = 100.0; var heat = 0.0; var over = false; var inv = 0.0; var lock = 0.0; var calmT = 0.0; var target: StubTarget? = null }
class StubMS { var def: Any? = null; var state = "idle" }
private val pl = StubPL()
private val ms = StubMS()
val Game.PL get() = pl
val Game.MS get() = ms
fun Game.hurtPlayer(d: Double, fromDir: Vec3) {}
fun Game.playerDown(msg: String) {}
fun Game.updateCombat(dt: Double) {}
fun Game.updateHUD(dt: Double) {}
