package vn.paperworld.phidoi.world

/** build every landmark in the web build's order (the seeded random depends on it) — desktop world test */
fun World.buildLandmarks() {
    airport(); dragonBridge(); hanBridge(); thuanPhuoc(); tranThiLy(); tienSon(); sunWheel(); cathedral(); cityIcons()
    ladyBuddha(); radar(); banCo(); banyan(); tienSaPort(); doucs(); tienSaLighthouse()
    marbleMountains()
    chuaCau(); phucKien(); hoiAn(); bayMau(); traQue()
    cuLaoCham(); haiVan(); baNa()
    cityBlocks()
    coast()
    countryside(); forests()
    for (r in ROADS.dropLast(1)) if (r.size == 4) ribbon(r.map { World.pt(it[0], it[1]) }, 12.0)
}
