package vn.paperworld.phidoi.game

import vn.paperworld.phidoi.engine.*
import vn.paperworld.phidoi.world.*

/* =========================================================
   BUILD THE WORLD, step by step (web: init) — same order as the web build so the seeded random matches
   ========================================================= */
fun Game.buildCity() { airport(); dragonBridge(); hanBridge(); thuanPhuoc(); tranThiLy(); tienSon(); sunWheel(); cathedral(); cityIcons() }
fun Game.buildSonTra() { ladyBuddha(); radar(); banCo(); banyan(); tienSaPort(); doucs(); tienSaLighthouse() }
fun Game.buildMarble() { marbleMountains() }
fun Game.buildHoiAn() { chuaCau(); phucKien(); hoiAn(); bayMau(); traQue() }
fun Game.buildIslands() { cuLaoCham(); haiVan(); baNa() }
fun Game.buildCityBlocks() { cityBlocks() }
fun Game.buildCoast() { coast() }
fun Game.buildNature() {
    countryside(); forests()
    for (r in ROADS.dropLast(1)) if (r.size == 4) ribbon(r.map { World.pt(it[0], it[1]) }, 12.0)
}
fun Game.finishWorld() {
    buildInstances()
    setupThermals(); setupCars(); setupJets(); setupBirds(); setupPilots(); setupHoaDang(); setupSpinners(); setupHalos()
    setupGameplay()
    val skip = HashSet<Object3D>()
    skip.add(F.g); skip.add(sky); skip.add(sea); skip.add(blob); skip.add(RAIN.mesh); skip.addAll(ranges)
    skipStatics(skip)
    for (J in JETS) skip.add(J.g)
    for (p in PILOTS) skip.add(p.g)
    for (c in CABLE.cars) skip.add(c.g)
    for (P in PARAS) { skip.add(P.boat); skip.add(P.sail); skip.add(P.rope) }
    for (H in AIHELI) skip.add(H.g)
    for (c in CLOUDS) skip.add(c.g)
    collectStatics(skip)
}
