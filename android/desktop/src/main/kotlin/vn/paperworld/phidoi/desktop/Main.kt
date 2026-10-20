package vn.paperworld.phidoi.desktop

import vn.paperworld.phidoi.engine.*
import java.io.File
import javax.imageio.ImageIO

fun main(args: Array<String>) {
    val mode = args.firstOrNull() ?: "engine"
    when (mode) {
        "engine" -> engineTest()
        else -> error("unknown mode $mode")
    }
}

/** smoke test: sky, sea, toon boxes with ink outlines, instanced spheres, post pass */
fun engineTest() {
    val w = 960; val h = 540
    val ctx = Headless(w, h)
    val gl = LwjglGL()
    println("GL: ${gl.getString(GLC.VERSION)}")
    val r = Renderer(gl)
    val pipe = Pipeline(r)
    pipe.resize(w, h, 4)
    println("hdr=${pipe.hdr} samples=${pipe.samples}")
    val scene = Group()
    val cam = Camera(55.0, w.toDouble() / h, 0.5, 16000.0)
    cam.position.set(30.0, 18.0, 40.0); cam.lookAt(Vec3(0.0, 4.0, 0.0))
    r.sunDir.set(-0.7, 0.55, 0.25).normalize(); r.sunColor.set("#fff6e4"); r.sunIntensity = 2.7
    r.hemiSky.set("#ffffff"); r.hemiGround.set("#b4c8d8"); r.hemiIntensity = System.getProperty("hemi", "1.38").toDouble()
    r.fogColor.set("#e6f4f6"); r.fogNear = 600.0; r.fogFar = 3000.0
    r.shadowTarget.set(0.0, 0.0, 0.0)
    val sky = SkyMaterial()
    sky.zen.set("#5fb0ee"); sky.hor.set("#e6f4f6"); sky.sun.set("#fff6e4"); sky.sunDir.copy(r.sunDir); sky.cloudLit.set("#ffffff"); sky.cloudShade.set("#c7d3ea")
    val skyMesh = Mesh(Geo.sphere(7000.0, 32, 16), sky); skyMesh.renderOrder = -1.0; skyMesh.frustumCulled = false
    scene.add(skyMesh)
    val n = 64
    val tex = Texture(n, n).also { t -> t.srgb = false; t.repeat = false; t.mipmaps = false; t.bytes = ByteArray(n * n * 4) { i -> when (i % 4) { 0 -> ((40 - 10) / 80.0 * 255).toInt().toByte(); 3 -> 255.toByte(); else -> 0 } } }
    val sea = SeaMaterial(tex); sea.map0x = -400.0; sea.map0z = -400.0; sea.mapS = 800.0; sea.gridN = n
    sea.sunDir.copy(r.sunDir); sea.sunCol.set("#fff6e4")
    val seaMesh = Mesh(Geo.plane().rotateX(-PI / 2), sea); seaMesh.scale.set(4000.0, 1.0, 4000.0); seaMesh.frustumCulled = false
    scene.add(seaMesh)
    val ground = Mesh(Geo.box(40.0, 2.0, 30.0), ToonMaterial("#f2e2b4")); ground.position.y = 0.5; ground.receiveShadow = true
    scene.add(ground)
    fun outlined(m: Mesh, th: Double): Mesh { m.geometry.addONormal(); m.add(Mesh(m.geometry, OutlineMaterial(th))); return m }
    for ((i, c) in listOf("#f37021", "#1f5fbf", "#ffd166", "#7ee0c3").withIndex()) {
        val b = outlined(Mesh(Geo.box(4.0, 6.0 + i * 3, 4.0), ToonMaterial(c)), 0.06)
        b.position.set(-12.0 + i * 8, 1.5 + (6.0 + i * 3) / 2, -4.0); b.castShadow = true; b.receiveShadow = true
        scene.add(b)
    }
    val ico = Geo.icosahedron(1.0, 1).addONormal()
    val data = InstanceData(30, true)
    val m4 = Mat4(); val q = Quat(); val s = Vec3()
    for (i in 0 until 30) { m4.compose(Vec3(-15.0 + (i % 10) * 3.3, 3.5, 6.0 + (i / 10) * 3.0), q.identity(), s.set(1.2, 1.2, 1.2)); data.setMatrixAt(i, m4); data.setColorAt(i, Color.of(listOf("#3f7d45", "#5f9e50", "#6aa858")[i % 3])) }
    data.computeBoundingSphere(ico)
    val im = InstancedMesh(ico, ToonMaterial(), data); im.castShadow = true; im.receiveShadow = true
    scene.add(im, InstancedMesh(ico, OutlineMaterial(0.075), data))
    pipe.time = 3.0
    val t0 = System.nanoTime()
    pipe.render(scene, cam, 0, w, h)
    gl.finish()
    println("frame ms=${(System.nanoTime() - t0) / 1e6} draws=${r.drawCalls} err=${gl.getError()}")
    val out = System.getProperty("out", "engine.png")
    ImageIO.write(ctx.grab(), "png", File(out))
    println("wrote $out")
}
