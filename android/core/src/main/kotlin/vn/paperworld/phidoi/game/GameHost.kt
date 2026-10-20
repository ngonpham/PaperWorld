package vn.paperworld.phidoi.game

import vn.paperworld.phidoi.engine.*
import vn.paperworld.phidoi.world.*
import java.util.concurrent.ConcurrentLinkedQueue

/** what the platform offers the game besides drawing */
interface HostEvents {
    fun vibrate(ms: Int) {}
    fun quit() {}
}

/**
 * Owns the game on the GL thread: builds the world step by step (with a loading screen), then runs
 * simulation + rendering + HUD each frame. Platform input is queued from any thread with [queue].
 */
class GameHost(val plat: Platform2D, val store: Store, val audio: AudioApi, val events: HostEvents, val touch: Boolean, val density: Double) {
    val qi = store.getInt("quality", if (touch) 1 else 2).coerceIn(0, QP.size - 1)
    val Q = QP[qi]
    lateinit var g: Game
    private var gl: GL? = null
    private var r: Renderer? = null
    private var pipe: Pipeline? = null
    var width = 1; var height = 1
    val drawCalls get() = r?.drawCalls ?: 0
    private val q = ConcurrentLinkedQueue<(GameHost) -> Unit>()
    private var step = 0
    var loading = "đang gấp bản đồ…"
    val ready get() = step >= STEPS.size
    /** pixel ratio of the 3D view relative to the screen (auto-resolution, web: PR) */
    var pr = 1.0
    private var prMax = 1.0
    private var arT = 0.0; private var arN = 0

    fun queue(f: (GameHost) -> Unit) { q.add(f) }

    fun onSurfaceCreated(gl: GL) {
        // a new EGL context: every GPU object is gone
        this.gl = gl
        val rr = Renderer(gl)
        r = rr
        pipe = Pipeline(rr)
        rr.shadowSize = Q.shadow
        if (::g.isInitialized) g.scene.traverse { forgetGpu(it) }
    }
    private fun forgetGpu(o: Object3D) {
        when (o) {
            is Mesh -> { o.geometry.gpu = null; (o as? InstancedMesh)?.data?.gpu = null; forgetMat(o.material); o.materials?.forEach { forgetMat(it) } }
            is Points -> { o.geometry.gpu = null; o.material.map?.gpu = 0 }
            is Lines -> o.geometry.gpu = null
        }
    }
    private fun forgetMat(m: Material) { when (m) { is ToonMaterial -> m.map?.gpu = 0; is BasicMaterial -> m.map?.gpu = 0; is SeaMaterial -> m.map.gpu = 0; else -> {} } }

    fun onSurfaceChanged(w: Int, h: Int) {
        width = maxOf(1, w); height = maxOf(1, h)
        prMax = minOf(1.0, Q.pr / maxOf(1.0, density) * 1.6).coerceAtLeast(0.6)
        if (pr > prMax || pr <= 0) pr = prMax
        resizePipe()
    }
    private fun resizePipe() {
        val p = pipe ?: return
        p.resize((width * pr).toInt(), (height * pr).toInt(), Q.msaa)
        p.pixelRatio = pr * density
        if (::g.isInitialized) { g.aspect = width.toDouble() / height; g.camera.aspect = g.aspect; g.camera.updateProjectionMatrix() }
    }

    /* ---------- world building, one step per frame (web: init) ---------- */
    private val STEPS: List<Pair<String, (Game) -> Unit>> = listOf(
        "đang gấp bản đồ…" to { w -> w.buildGrid() },
        "đang trải địa hình…" to { w -> w.buildTiles() },
        "đang dựng cầu sông Hàn…" to { w -> w.buildCity() },
        "đang dựng bán đảo Sơn Trà…" to { w -> w.buildSonTra() },
        "đang gấp Ngũ Hành Sơn…" to { w -> w.buildMarble() },
        "đang thắp đèn lồng Hội An…" to { w -> w.buildHoiAn() },
        "đang vẽ Cù Lao Chàm, Hải Vân, Bà Nà…" to { w -> w.buildIslands() },
        "đang xây thành phố…" to { w -> w.buildCityBlocks() },
        "đang trải bãi biển…" to { w -> w.buildCoast() },
        "đang trồng rừng, cấy lúa…" to { w -> w.buildNature() },
        "đang gấp mây…" to { w -> w.finishWorld() },
    )
    private fun initStep() {
        if (step == 0 && !::g.isInitialized) { g = Game(plat, Q, store, audio); g.isTouch = touch; g.aspect = width.toDouble() / height; g.camera.aspect = g.aspect; return }
        val (label, f) = STEPS[step]
        loading = label
        f(g)
        step++
        if (step < STEPS.size) loading = STEPS[step].first
        if (ready) g.onReady()
    }

    /* ---------- the frame ---------- */
    private var lastHudW = 0
    fun drawFrame(dt: Double, hud: Canvas2D, hudW: Int, hudH: Int) {
        while (true) { val f = q.poll() ?: break; f(this) }
        val rr = r ?: return; val p = pipe ?: return
        if (!ready) {
            initStep()
            drawLoading(hud, hudW, hudH)
            gl?.let { it.bindFramebuffer(GLC.FRAMEBUFFER, 0); it.viewport(0, 0, width, height); it.clearColor(0.86f, 0.93f, 0.95f, 1f); it.clear(GLC.COLOR_BUFFER_BIT) }
            return
        }
        autoRes(dt)
        g.frame(dt, rr, p)
        for (geo in g.disposeQ) rr.dispose(geo)
        g.disposeQ.clear()
        p.render(g.scene, g.camera, 0, width, height)
        g.drawUi(hud, hudW.toDouble() / density, hudH.toDouble() / density, density, dt)
        g.audioFrame(dt)
    }
    private fun autoRes(dt: Double) {
        if (!g.game.started) return
        arT += dt; arN++
        if (arT < 2.5) return
        val fps = arN / arT; arT = 0.0; arN = 0
        var np = pr
        if (fps < 40 && pr > 0.55) np = maxOf(0.55, pr - 0.15)
        else if (fps > 57 && pr < prMax) np = minOf(prMax, pr + 0.08)
        if (np != pr) { pr = np; resizePipe() }
    }
    private fun drawLoading(c: Canvas2D, w: Int, h: Int) {
        val W = w.toFloat(); val H = h.toFloat(); val d = density.toFloat()
        c.setFill(argb("#f6efe0")); c.fillRect(0f, 0f, W, H)
        c.setTextAlign(Align.CENTER); c.setTextBaseline(Baseline.MIDDLE)
        c.setFill(argb("#1f1d2b")); c.setFont(Face.BRUSH, 34f * d); c.fillText("Phi Đội Giấy", W / 2, H * 0.42f)
        c.setFont(Face.UI700, 13f * d); c.setFill(argb("#f37021")); c.fillText("ĐÀ NẴNG", W / 2, H * 0.42f + 34f * d)
        c.setFont(Face.HAND, 18f * d); c.setFill(argb("#3a3848")); c.fillText(loading, W / 2, H * 0.62f)
        val k = (step.toFloat() + 1) / (STEPS.size + 1)
        c.setFill(argb("#e6dcc6")); c.fillRect(W * 0.3f, H * 0.68f, W * 0.4f, 4f * d)
        c.setFill(argb("#f37021")); c.fillRect(W * 0.3f, H * 0.68f, W * 0.4f * k, 4f * d)
    }

    /* ---------- input (game thread) ---------- */
    fun pointer(action: Int, id: Int, x: Double, y: Double) { if (ready) g.pointer(action, id, x / density, y / density) }
    fun key(name: String, down: Boolean, repeat: Boolean) { if (ready) g.key(name, down, repeat) }
    fun back() { if (ready) g.backKey() else events.quit() }
    fun pause(on: Boolean) { if (ready) g.appPaused(on) }
}
