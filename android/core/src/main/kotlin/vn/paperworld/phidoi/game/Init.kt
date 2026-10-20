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
    probeAndBatch(skip)
    collectStatics(skip)
}

/** find what moves (run the animations a few times), then merge everything else per landmark (World.batchStatics) */
fun Game.probeAndBatch(skip: Set<Object3D>) {
    val roots = batchRoots(skip)
    val snaps = ArrayList<TransformSnap>()
    for (r in roots) r.traverse { snaps.add(TransformSnap(it)) }
    val glow0 = env.glow; val fire0 = DRAGON.fireT
    val far = Vec3(1e6, 0.0, 1e6)
    for (t in doubleArrayOf(0.7, 3.1, 9.4, 17.0)) for (gl in doubleArrayOf(0.0, 1.0)) {
        env.glow = gl
        DRAGON.fireT = 5.0
        updateMisc(0.1, t)
        updateDragon(0.1, t, far)
    }
    val dynamic = HashSet<Object3D>()
    for (s in snaps) if (s.changed()) dynamic.add(s.o)
    for (s in snaps) s.restore()
    env.glow = glow0; DRAGON.fireT = fire0
    val n = batchStatics(roots, dynamic)
    batchInfo = "merged $n meshes, ${dynamic.size} animated objects kept"
}


/** the world is built: models, first spawn, attract mode (web: tail of init) */
fun Game.onReady() {
    buildModels()
    setCraft(store.getString("craft")?.takeIf { CRAFT.containsKey(it) } ?: "jet", true)
    spawnAt(firstSpawn())
    F.g.visible = false
    updateTiles(camera.position, 40)
    updateCells(camera.position)
    READY = true
    afterReady()
}

/* =========================================================
   AUDIO — fill the frame the synth reads (web: updateAudio's reads of game state)
   ========================================================= */
private val audioF = AudioFrame()
fun Game.audioFrame(dt: Double) {
    val f = audioF; val C = F.c
    f.flying = game.started && F.mode != "crash"
    f.v = F.v; f.craftId = C.id; f.heli = C.heli; f.glide = C.glide; f.landed = F.mode == "landed"; f.modeFly = F.mode == "fly"
    f.vy = F.vy; f.thr = F.thr; f.energy = F.energy
    f.afterburner = C.id == "jet" && IN.boost > 0.05 && F.energy > 0.01
    f.camMode = game.camMode
    Geog.sample(camera.position.x, camera.position.z)
    f.shore = smooth(260.0, 20.0, kotlin.math.abs(Geog.S.sd)) * smooth(120.0, 10.0, camera.position.y)
    f.rain = ENV.rain; f.climb = F.vs; f.vario = SET.vario; f.gameT = game.t
    f.cam.copy(camera.position)
    f.bellAt = null
    for (k in listOf("buddha", "thuyson")) { val L = LANDMARKS[k] ?: continue; if (kotlin.math.hypot(F.pos.x - L.x, F.pos.z - L.z) < 260) { f.bellAt = Vec3(L.x, if (L.y != 0.0) L.y else 20.0, L.z); break } }
    f.gullOk = f.shore > 0.2 || BIRDS.list.any { !it.eagle && it.pos.distanceTo(camera.position) < 120 }
    audio.update(dt, f)
}
