package vn.paperworld.phidoi.game

import vn.paperworld.phidoi.engine.*
import vn.paperworld.phidoi.world.*
import kotlin.math.*

/* =========================================================
   GAME STATE + INPUT
   ========================================================= */
fun Game.readInput(dt: Double) {
    fun k(a: String, b: String) = if (a in keys || b in keys) 1.0 else 0.0
    var pitch = k("w", "arrowup") - k("s", "arrowdown"); var roll = k("d", "arrowright") - k("a", "arrowleft")
    if (TOUCH.on) { pitch += -TOUCH.y; roll += TOUCH.x }
    if (TILT.on) { pitch += TILT.y; roll += TILT.x }
    pitch += PAD.ly; roll += PAD.lx
    IN.pitch = damp(IN.pitch, clamp(pitch, -1.0, 1.0), if ("w" in keys || "s" in keys || "arrowup" in keys || "arrowdown" in keys) 7.0 else 12.0, dt)
    IN.roll = damp(IN.roll, clamp(roll, -1.0, 1.0), 9.0, dt)
    IN.yaw = k("e", "e") - k("q", "q")
    IN.boost = max(if ("shift" in keys) 1.0 else 0.0, if (PAD.a) 1.0 else 0.0)
    IN.brake = max(if ("x" in keys || "control" in keys) 1.0 else 0.0, PAD.lt)
    IN.fire = (" " in keys || "j" in keys || PAD.rt > 0.35) && game.started && game.panel == null
    val tr = "v" in keys
    IN.trickPressed = tr && !IN.trickHeld && F.mode == "fly" && !F.c.heli
    IN.trickHeld = tr
    IN.flap = false
    IN.flapPressed = false
}
fun Game.cycleCam() { game.camMode = (game.camMode + 1) % CAMS.size; toast("🎥 " + CAMS[game.camMode]); game.cine = null }

/* =========================================================
   CAMERA
   ========================================================= */
fun Game.fitFov(v: Double): Double {
    val a = camera.aspect; val ref = 1.6
    if (a <= ref) return v
    val h = 2 * atan(tan((v * PI) / 360) * ref)
    return (2 * atan(tan(h / 2) / a) * 180) / PI
}
private val _off = Vec3()
fun Game.updateCamera(dt: Double) {
    val C = F.c; val p = F.g.position; val z = game.zoom * (if (isTouch) 0.85 else 1.0)
    if (!game.dragging) { game.dragYaw = damp(game.dragYaw, 0.0, 1.4, dt); game.dragPitch = damp(game.dragPitch, 0.0, 1.4, dt) }
    fwd(F.psi, F.gam, _cf)
    val big = C.big
    var fov = 66 + clamp((F.v - C.vb) / C.vb, -0.3, 1.5) * 9 + (if (IN.boost > 0.05 && F.energy > 0.02 && C.id == "jet") 6 else 0)
    var roll: Double
    val mode = if (game.started) game.camMode else -1
    val looping = F.trick?.type == "loop"
    if (mode == -1) {
        // attract mode: slow orbit over the city
        val a = game.t * 0.04
        camPos.set(-60 + cos(a) * 520, 190 + sin(a * 0.7) * 40, -1000 + sin(a) * 520)
        camera.position.lerp(camPos, if (game.camSnap) 1.0 else 1 - exp(-2 * dt))
        lookAt.set(80.0, 20.0, -1100.0)
        camera.up.set(0.0, 1.0, 0.0)
        camera.lookAt(lookAt)
        camera.fov = damp(camera.fov, fitFov(55.0), 2.0, dt)
        camera.updateProjectionMatrix()
        game.camSnap = false
        return
    }
    val tgt = PL.target
    if (mode == 2) {
        // cockpit / nose view: full attitude
        F.g.updateWorldFromParents()
        val gq = Quat().setFromRotationMatrix(F.g.matrixWorld)
        _cu.set(0.0, 1.0, 0.0).applyQuaternion(gq)
        if (C.id == "seaplane") _off.set(0.0, 1.9, -1.4) else if (C.heli) _off.set(0.0, 0.9, -1.8) else _off.set(0.0, 1.05, -1.4)
        camPos.copy(_off).applyQuaternion(gq).add(p)
        camera.position.copy(camPos)
        _cf.set(0.0, 0.0, -1.0).applyQuaternion(gq)
        _cf.applyAxisAngle(_cu, game.dragYaw)
        lookAt.copy(camPos).addScaledVector(_cf, 50.0).addScaledVector(_cu, -game.dragPitch * 30)
        camera.up.copy(_cu)
        camera.lookAt(lookAt)
        fov += 8
    } else if (mode == 3 && tgt != null && tgt.alive) {
        // padlock: look past the aircraft at the current target
        val tp = tgt.pos
        _cr.copy(tp).sub(p)
        val dT = _cr.length()
        _cr.divideScalar(if (dT == 0.0) 1.0 else dT)
        val D = 15 * z * big; val H = 4.5 * z * big
        camPos.copy(p).addScaledVector(_cr, -D)
        camPos.y += H
        if (game.camSnap) camera.position.copy(camPos) else camera.position.lerp(camPos, 1 - exp(-6 * dt))
        val minY = max(surfaceAt(camera.position.x, camera.position.z), obstacleTop(camera.position.x, camera.position.z, 2.0)) + 1.6
        if (camera.position.y < minY) camera.position.y = minY
        lookAt.copy(p).addScaledVector(_cr, min(dT * 0.5, 90.0))
        camera.up.set(0.0, 1.0, 0.0)
        camera.lookAt(lookAt)
        camYaw = atan2(-_cr.x, -_cr.z) // keep the chase cam in step for a smooth hand-back
    } else {
        // chase cams
        val D = (if (mode == 1) 7.5 else if (mode == 4) 30.0 else 12.5) * z * big; val H = (if (mode == 1) 2.2 else if (mode == 4) 10.0 else 3.6) * z * big
        val targetYaw = F.psi + game.dragYaw
        if (game.camSnap) { camYaw = targetYaw; camPitch = F.gam * 0.55 }
        camYaw += angDiff(targetYaw, camYaw) * (1 - exp(-(if (F.c.glide) 2.2 else 3.6) * dt))
        val gFollow = if (looping) F.gam else F.gam * 0.55
        camPitch = damp(camPitch, gFollow, if (looping) 8.0 else 3.0, dt)
        val cpch = cos(camPitch); val spch = sin(camPitch)
        if (looping) {
            F.g.updateWorldFromParents()
            _cu.set(0.0, 1.0, 0.0).applyQuaternion(Quat().setFromRotationMatrix(F.g.matrixWorld))
            camPos.copy(p).addScaledVector(_cf, -D).addScaledVector(_cu, H)
        } else {
            camPos.set(p.x + sin(camYaw) * D * cpch, p.y + H - spch * D + game.dragPitch * 16 * z, p.z + cos(camYaw) * D * cpch)
        }
        if (game.camSnap) camera.position.copy(camPos) else camera.position.lerp(camPos, 1 - exp(-(if (looping) 10 else 7) * dt))
        val minY = max(surfaceAt(camera.position.x, camera.position.z), obstacleTop(camera.position.x, camera.position.z, 2.0)) + 1.6
        if (camera.position.y < minY) camera.position.y = minY
        if (looping) camera.up.copy(_cu)
        else {
            roll = F.rollVis * (if (mode == 1) 0.45 else 0.3)
            _cr.set(-cos(camYaw), 0.0, sin(camYaw))
            camera.up.set(0.0, 1.0, 0.0).multiplyScalar(cos(roll)).addScaledVector(_cr, -sin(roll))
        }
        lookAt.copy(p).addScaledVector(_cf, 6 * big).add(_v1.set(0.0, (if (mode == 4) 2 else 1) * big, 0.0))
        camera.lookAt(lookAt)
    }
    game.camSnap = false
    // shake: crash, stall buffet, turbulence, speed
    val sh = game.shake + (if (F.stall > 0) 0.25 else 0.0) + min(0.25, abs(F.lift) * 0.02) + max(0.0, (F.v - 60) / 300)
    game.shake = max(0.0, game.shake - dt * 1.4)
    if (sh > 0.01) camera.position.add(_v1.set(RR(-1.0, 1.0), RR(-1.0, 1.0), RR(-1.0, 1.0)).multiplyScalar(sh * 0.35))
    camera.fov = damp(camera.fov, fitFov(fov), 3.0, dt)
    camera.near = clamp((camera.position.y - max(surfaceAt(camera.position.x, camera.position.z), obstacleTop(camera.position.x, camera.position.z, 2.0))) * 0.02, 0.4, 4.0)
    camera.updateProjectionMatrix()
    // inside a cloud? → white mist
    var mist = 0.0
    for (c in CLOUDS) {
        val dx = camera.position.x - c.g.position.x; val dy = camera.position.y - c.g.position.y; val dz = camera.position.z - c.g.position.z
        if (abs(dx) > c.s * 2.5 || abs(dz) > c.s * 2.5 || abs(dy) > c.s) continue
        val e = (dx * dx) / (c.s * c.s * 2.6) + (dz * dz) / (c.s * c.s * 1.8) + (dy * dy) / (c.s * c.s * 0.45)
        mist = max(mist, smooth(1.15, 0.45, e))
    }
    game.mist = damp(game.mist, mist, 4.0, dt)
}

/* =========================================================
   RAIN
   ========================================================= */
class Rain(g: Game) {
    val N = 1400
    val geo = Geometry(); val pos = FloatArray(N * 6); val off = FloatArray(N * 3)
    val mat = LineMaterial("#c9d6ea").also { it.transparent = true; it.opacity = 0.0; it.depthWrite = false }
    val mesh = Lines(geo, mat, false)
    init {
        for (i in 0 until N) { off[i * 3] = g.RR(-60.0, 60.0).toFloat(); off[i * 3 + 1] = g.RR(-40.0, 40.0).toFloat(); off[i * 3 + 2] = g.RR(-60.0, 60.0).toFloat() }
        geo.position = pos; geo.dynamic = true
        mesh.frustumCulled = false
        g.scene.add(mesh)
    }
}

/* =========================================================
   WORLD UPDATE
   ========================================================= */
fun Game.updateWorld(dt: Double, r: Renderer?, pipe: Pipeline?) {
    val t = game.t
    if (ENV.auto) ENV.hour = (ENV.hour + dt * 0.1) % 24
    else { var d = ENV.target - ENV.hour; if (abs(d) > 12) d -= sign(d) * 24; ENV.hour = (ENV.hour + d * (1 - exp(-2 * dt)) + 24) % 24 }
    ENV.rain = damp(ENV.rain, if (ENV.rainOn) 1.0 else 0.0, 1.0, dt)
    applyEnv()
    val cp = camera.position
    sky.position.copy(cp)
    for (rg in ranges) rg.position.set(cp.x * 0.85, 0.0, cp.z * 0.85)
    skyMat.time = t
    seaMat.time = t
    sea.position.set(Math.round(cp.x / 50) * 50.0, SEA_Y, Math.round(cp.z / 50) * 50.0)
    // fog opens up with altitude
    val alt = max(0.0, cp.y)
    val far = Q.view * (1 + smooth(40.0, 600.0, alt) * 0.7) * (1 - ENV.rain * 0.5)
    fogFar = far; fogNear = far * 0.18
    pipe?.let { it.edgeN = far * 0.08; it.edgeF = far * 0.32; it.night = env.glow }
    camera.far = 16000.0
    // shadows follow the aircraft, wider when higher
    val tgt = if (game.started) F.pos else lookAt
    val e = clamp(70 + F.agl * 0.6, 70.0, 240.0)
    if (abs(e - shadowE) > 8) shadowE = e
    r?.let { it.shadowExtent = shadowE; it.shadowTarget.copy(tgt) }
    RAIN.mat.opacity = ENV.rain * 0.5
    RAIN.mesh.visible = ENV.rain > 0.02
    if (RAIN.mesh.visible) {
        val o = RAIN.off; val P = RAIN.pos
        for (i in 0 until RAIN.N) {
            val x = cp.x + (((((o[i * 3] - cp.x) % 120) + 180) % 120) - 60); val y = cp.y + (((((o[i * 3 + 1] - t * 55 - cp.y) % 80) + 120) % 80) - 40); val z = cp.z + (((((o[i * 3 + 2] - cp.z) % 120) + 180) % 120) - 60)
            val b = i * 6
            P[b] = x.toFloat(); P[b + 1] = y.toFloat(); P[b + 2] = z.toFloat(); P[b + 3] = (x + WIND.v.x * 0.06).toFloat(); P[b + 4] = (y + 1.6).toFloat(); P[b + 5] = (z + WIND.v.z * 0.06).toFloat()
        }
        RAIN.geo.posVersion++
    }
    for (c in CLOUDS) {
        c.g.position.x = c.x + WIND.v.x * ((t * 0.6) % 600) - WIND.v.x * 180
        c.g.position.z = c.z + WIND.v.z * ((t * 0.6) % 600) - WIND.v.z * 180
        c.thermal?.let { th -> c.g.position.x = th.x + WIND.v.x * (c.y / 4.5) * 0.6; c.g.position.z = th.z + WIND.v.z * (c.y / 4.5) * 0.6 }
        c.g.visible = !(ENV.rain < 0.3 && c.haivan && env.glow > 0.9)
    }
    // the land breathes: thermal clouds swell by day, shrink by night
    val dayK = smooth(8.0, 12.0, ENV.hour) * smooth(18.5, 15.5, ENV.hour)
    for (T in THERMALS) T.cloud!!.g.setScale(0.45 + 0.55 * dayK + ENV.rain * 0.3)
    game.fade = damp(game.fade, 0.0, 2.2, dt)
    game.hurt = damp(game.hurt, 0.0, 2.5, dt)
}

/* =========================================================
   REGION TITLES + FIRST-FLIGHT TIPS
   ========================================================= */
fun Game.updateRegion(dt: Double) {
    regT += dt
    if (!game.started || regT < 0.5) return
    regT = 0.0
    val r = regionAt(F.pos.x, F.pos.z)
    if (r.id != game.regionId) {
        if (regPending != r.id) { regPending = r.id; regPendT = 0.0 }
        else { regPendT += 0.5; if (regPendT >= 1) { game.regionId = r.id; if (game.t - game.titleT > 9) showTitle(r) } }
    } else regPending = null
    game.lapT += 0.5
    val T = isTouch
    val tips = listOf(
        listOf("5", "🎯 Ngắm bắn", if (T) "Giữ nút BẮN" else "SPACE hoặc giữ chuột trái", "Đặt vòng ngắm lên vòng tròn đỏ trước mặt địch — đạn cần thời gian để bay tới!"),
        listOf("22", "🚀 Tên lửa", if (T) "Nút 🚀" else "F hoặc nhấp chuột phải", "Giữ địch trong khung ngắm tới khi hình thoi chuyển đỏ “KHOÁ ●”, rồi phóng. Không khoá thì tên lửa bay thẳng."),
        listOf("45", "✦ Pháo sáng", if (T) "Nút ✦" else "Phím G", "Nghe tiếng bíp dồn dập và thấy ⚠ TÊN LỬA? Thả pháo sáng rồi bẻ lái thật gắt."),
        listOf("70", "📦 Tiếp tế Ahamove", "Trực thăng cam – xanh thả kiện hàng có dù", "Bay xuyên qua để 🔧 sửa máy bay, 🚀 nạp tên lửa, ✦ thêm pháo sáng. Đáp xuống bãi chữ H cũng được sửa & nạp đạn!"),
        listOf("100", "🔥 Súng nóng", "Bắn từng loạt ngắn", "Thanh NHIỆT SÚNG đầy thì súng kẹt vài giây. " + (if (T) "Nút 🎯 đổi mục tiêu." else "TAB đổi mục tiêu · V lộn xoắn né đạn.")),
        listOf("135", "🎥 Khoá mục tiêu", if (T) "Nút 🎥" else "Phím C", "Góc máy “Khoá mục tiêu” luôn giữ kẻ địch trong khung hình khi quần nhau."),
    )
    for (tp in tips) if (game.lapT > tp[0].toDouble() && tp[0] !in game.tipShown) { game.tipShown.add(tp[0]); showTip(tp[1], tp[2], tp[3], 6000.0); break }
}

/* =========================================================
   LOOP
   ========================================================= */
/** one step of the simulation (everything but drawing) */
fun Game.simStep(dt: Double, r: Renderer? = null, pipe: Pipeline? = null) {
    game.t += dt
    readInput(dt)
    if (game.started) updateFlight(dt, game.t) else updateWind(game.t)
    val pp = if (game.started) F.pos else camera.position
    if (READY) {
        updateCars(dt, pp)
        updateJets(dt, game.t)
        updateBirds(dt, game.t, pp)
        updatePilots(dt, game.t)
        updateDragon(dt, game.t, pp)
        updateFireworks(dt, game.t)
        updateHoaDang(dt, game.t)
        updateMisc(dt, game.t)
        updateThermalFx(pp, game.t)
        updateWindFx(camera.position, game.t, dt)
        updateCombat(dt)
        stepParticles(PB, dt, false)
        stepParticles(PF, dt, true)
        stepParticles(PS, dt, false)
    }
    updateCamera(dt)
    updateWorld(dt, r, pipe)
    updateTiles(if (game.started) F.pos else camera.position, if (game.tileBurst > 0) 6 else if (game.started) 2 else 6)
    if (game.tileBurst > 0) game.tileBurst--
    cellT += dt
    if (cellT > 0.25) { cellT = 0.0; updateCells(camera.position); updateStatics(camera.position, fogFar) }
    camera.updateCameraMatrices()
    if (game.started) { updateHUD(dt); updateRegion(dt) }
}
/** one displayed frame: simulate (unless frozen), then hand the post-process its uniforms */
fun Game.frame(rawDt: Double, r: Renderer?, pipe: Pipeline?) {
    val frozen = game.paused || (game.started && game.panel != null)
    var dt = if (frozen) 0.0 else clamp(rawDt, 0.0, 0.05)
    if (game.slow > 0) { game.slow -= dt; dt *= 0.35 }
    simStep(dt, r, pipe)
    r?.let { applyTo(it) }
    pipe?.let { p ->
        p.time = game.t
        p.speed = if (game.started && F.mode == "fly") clamp((F.v - F.c.vb * 1.6) / F.c.vb, 0.0, 1.0) * 0.7 + (if (IN.boost > 0.05 && F.energy > 0.02 && F.c.id == "jet") 0.35 else 0.0) else 0.0
        p.fade = game.fade
        p.mist = game.mist
        p.hurt = game.hurt
    }
    IN.flapPressed = false
}
