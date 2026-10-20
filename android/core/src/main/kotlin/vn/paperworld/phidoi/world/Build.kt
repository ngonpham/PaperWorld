package vn.paperworld.phidoi.world

/** build every landmark in the web build's order (the seeded random depends on it) */
fun World.buildLandmarks() {
    chuaCau(); phucKien(); hoiAn(); bayMau(); traQue()
    cuLaoCham(); haiVan(); baNa()
    countryside(); forests()
}
