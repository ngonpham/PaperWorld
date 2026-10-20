package vn.paperworld.phidoi.game

import vn.paperworld.phidoi.engine.*

/* TEMPORARY: stand-ins for the HUD / menu systems until they are ported */
fun Game.updateHUD(dt: Double) {}
/** web: renderMenu() — repaint the mission / craft / difficulty pickers from PICK + SAVE (UI port) */
fun Game.renderMenu() {}
/** web startMission: audioInit(); on the first start radioSet($("radioOn").checked); switch the radio to M.song (audio/UI port) */
fun Game.missionAudio(song: Int) {}
