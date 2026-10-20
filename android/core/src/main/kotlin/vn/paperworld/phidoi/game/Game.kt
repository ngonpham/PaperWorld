package vn.paperworld.phidoi.game

import vn.paperworld.phidoi.engine.*
import vn.paperworld.phidoi.world.*
import kotlin.math.*

/** small persistent key/value store (SharedPreferences on Android, a properties file on the desktop) */
interface Store {
    fun getString(k: String): String?
    fun putString(k: String, v: String)
    fun getBool(k: String, d: Boolean) = getString(k)?.toBooleanStrictOrNull() ?: d
    fun getInt(k: String, d: Int) = getString(k)?.toIntOrNull() ?: d
    fun put(k: String, v: Any) = putString(k, v.toString())
}
class MemStore : Store { private val m = HashMap<String, String>(); override fun getString(k: String) = m[k]; override fun putString(k: String, v: String) { m[k] = v } }

/* =========================================================
   aircraft
   ========================================================= */
class LandSpec(val vs: Double, val v: Double)
class Craft(
    val id: String, val name: String, val icon: String, val vb: Double, val LD: Double, val vs: Double, val vmax: Double, val bankMax: Double,
    val rollRate: Double = 0.0, val nGain: Double = 0.0, val gMax: Double, val cruise: Double = 0.0, val boost: Double = 0.0, val eUse: Double = 0.0, val eGain: Double = 0.0,
    val thrust: Double = 0.0, val yawK: Double, val radius: Double, val land: LandSpec, val tricks: Boolean = false, val water: Boolean = false, val heli: Boolean = false,
    val energy: String, val big: Double, val hp: Double, val guns: List<DoubleArray>, val rate: Double, val dmg: Double, val heat: Double, val missiles: Int, val flares: Int,
    val turret: Boolean = false, val glide: Boolean = false,
)
private fun g3(vararg v: Double) = v
val CRAFT = linkedMapOf(
    "jet" to Craft(
        "jet", "Phản lực giấy", "✈", vb = 44.0, LD = 8.0, vs = 17.0, vmax = 150.0, bankMax = 1.35, rollRate = 3.4, nGain = 3.0, gMax = 7.5, cruise = 1.6, boost = 16.0, eUse = 0.14, eGain = 0.09,
        yawK = 0.4, radius = 2.2, land = LandSpec(4.5, 32.0), tricks = true, energy = "ĐỐT SAU", big = 1.35,
        hp = 100.0, guns = listOf(g3(-0.95, -0.1, -2.2), g3(0.95, -0.1, -2.2)), rate = 14.0, dmg = 1.0, heat = 0.017, missiles = 6, flares = 8,
    ),
    "seaplane" to Craft(
        "seaplane", "Thủy phi cơ", "🛩", vb = 40.0, LD = 8.0, vs = 20.0, vmax = 112.0, bankMax = 1.05, rollRate = 1.7, nGain = 1.9, gMax = 4.5, thrust = 11.0, yawK = 0.3, radius = 4.0,
        land = LandSpec(4.2, 36.0), water = true, tricks = true, energy = "GA", big = 1.7,
        hp = 160.0, guns = listOf(g3(-3.2, 1.75, -1.7), g3(3.2, 1.75, -1.7), g3(-4.4, 1.75, -1.7), g3(4.4, 1.75, -1.7)), rate = 16.0, dmg = 1.15, heat = 0.014, missiles = 10, flares = 6,
    ),
    "heli" to Craft(
        "heli", "Trực thăng Ahamove", "🚁", vb = 30.0, LD = 6.0, vs = 0.0, vmax = 60.0, bankMax = 0.4, gMax = 2.0, yawK = 0.0, radius = 3.5, land = LandSpec(5.5, 16.0), heli = true, energy = "CẦN GA", big = 1.6,
        hp = 140.0, guns = listOf(g3(0.0, -1.5, -2.6)), rate = 11.0, dmg = 1.0, heat = 0.015, missiles = 8, flares = 10, turret = true,
    ),
)

class Settings(st: Store) {
    var assist = st.getBool("assist", true); var invert = st.getBool("invert", false); var crash = st.getBool("crash", true)
    var vario = false; var mapRot = st.getBool("mapRot", true); var tilt = st.getBool("tilt", false)
}

class Trick(val type: String) { var a = 0.0; var g0 = 0.0; var psi = 0.0; var t = 0.0; var dir = 1.0 }
class Hist(val p: Vec3, val psi: Double, val t: Double)

/** the player's aircraft state (web: F) */
class Flight {
    var c: Craft = CRAFT["jet"]!!
    val pos = Vec3(); var v = 30.0; var psi = 0.0; var gam = 0.0; var phi = 0.0; var n = 1.0; var energy = 1.0; var thr = 0.6
    var mode = "fly"; var vs = 0.0; var agl = 50.0; var ground = 0.0; var overWater = false
    var stall = 0.0; var warn = 0.0; var trick: Trick? = null; var loopA = 0.0; var crashT = 0.0; val hist = ArrayList<Hist>(); var histT = 0.0
    var skips = 0; var skipT = 0.0; var tricks = 0; var flapK = 0.0; var tuck = 0.0; var prevY = 0.0
    val g = Group(); var model: CraftModel? = null; var rollVis = 0.0; val hv = Vec3(); var vy = 0.0; var landY = 0.0; val offVis = Vec3(); var lift = 0.0; var stillT = 0.0; var crashes = 0
    var liftSm = 0.0; var lastCrash = ""; var beachT = -1e9
}

/** an aircraft model + the parts the animation drives (web: g.userData) */
class CraftModel(val g: Group) {
    var flame: Mesh? = null; var flameMat: BasicMaterial? = null
    var prop: Object3D? = null; var disc: Mesh? = null; var discMat: BasicMaterial? = null
    var rotor: Object3D? = null; var tail: Object3D? = null
    var tips = arrayOf(Vec3(), Vec3())
}

class GameState {
    var started = false; var t = 0.0; var camMode = 0; var zoom = 1.0; var dragYaw = 0.0; var dragPitch = 0.0; var dragging = false; var studio = false
    var shake = 0.0; var hurt = 0.0; var fade = 1.0; var camSnap = true; var regionId: String? = null; val tipShown = HashSet<String>(); var lapT = 0.0
    var panel: String? = null; var mist = 0.0; var paused = false; var slow = 0.0; var titleT = -99.0; var tileBurst = 0; var panelT = 0.0
    var cine: Any? = null; var regionT = 0.0
}
class Input {
    var pitch = 0.0; var roll = 0.0; var yaw = 0.0; var boost = 0.0; var brake = 0.0; var fire = false; var flap = false; var flapPressed = false
    var trickPressed = false; var trickHeld = false; var loopKey = false; var missileQ = false; var flareQ = false; var targetQ = false
}
class TouchStick { var x = 0.0; var y = 0.0; var on = false }
class Tilt { var on = false; var x = 0.0; var y = 0.0 }
class Pad { var x = 0.0; var y = 0.0; var lx = 0.0; var ly = 0.0; var rt = 0.0; var lt = 0.0; var a = false }

/** UI messages the HUD layer draws (web: DOM toast / tip / title) */
class Messages {
    var toast = ""; var toastT = -99.0
    var tipK = ""; var tipH = ""; var tipP = ""; var tipT = -99.0; var tipMs = 4600.0
    var tHan = ""; var tBig = ""; var tSub = ""; var titleT = -99.0
}
class Region(val id: String, val name: String, val han: String, val sub: String)

class Trail(val n: Int) {
    val geo = Geometry(); val pos = FloatArray(n * 3); val col = FloatArray(n * 4)
    val mat = LineMaterial("#ffffff").also { it.vertexColors = true; it.transparent = true; it.depthWrite = false }
    val l = Lines(geo, mat, true)
    init { geo.position = pos; geo.color = col; geo.colorSize = 4; geo.dynamic = true; l.frustumCulled = false }
}

/**
 * Phi Đội Giấy: the world plus everything that moves, flies and fights in it.
 * Mirrors the module scope of the web build; the systems live in the other files of this package as extension functions.
 */
class Game(plat: Platform2D, Q: Quality, val store: Store, val audio: AudioApi) : World(plat, Q) {
    val sfx get() = audio.sfx
    val camera = Camera(62.0, 16.0 / 9, 0.5, 16000.0)
    var aspect = 16.0 / 9
    var isTouch = true
    var READY = false
    /** the render pipeline's near plane / post uniforms are read from here */
    var postNear = 0.5

    val game = GameState()
    val keys = HashSet<String>()
    val IN = Input()
    val TOUCH = TouchStick()
    val TILT = Tilt()
    val PAD = Pad()
    val SET = Settings(store)
    val CAMS = listOf("BÁM ĐUÔI", "GẦN", "BUỒNG LÁI", "KHOÁ MỤC TIÊU", "TOÀN CẢNH")
    val msg = Messages()
    val ui = UiState()
    /** platform hooks (vibration, quit) — set by the host */
    var events: HostEvents = object : HostEvents {}
    init { audio.onSongChange = { toast("♫ " + it) } }

    /* scratch vectors shared by the systems (web: _v1, _v2, _v3) */
    val _v1 = Vec3(); val _v2 = Vec3(); val _v3 = Vec3()
    val _m = Mat4(); val _q = Quat(); val _e = Euler(); val _s = Vec3(); val _p = Vec3()
    val YUP = Vec3(0.0, 1.0, 0.0)

    /* ---------- life: particles, wind, thermals, clouds (module-scope objects in web order) ---------- */
    val puffTex = makePuffTex(this)
    val PB = Particles(900, 0.7, false, null).also { scene.add(it.m) }
    val PF = Particles(1600, 2.8, true, puffTex).also { scene.add(it.m) } // fire + fireworks
    val PS = Particles(900, 5.0, false, puffTex).also { scene.add(it.m) } // smoke
    val WIND = Wind()
    val THERMALS = ArrayList<Thermal>()
    val CLOUDS = ArrayList<Cloud>()
    val cloudMat = toon("#ffffff") { emissive.copy(Color.of("#ffffff")); emissiveIntensity = 0.18 }
    init { NIGHT.add { g -> cloudMat.emissiveIntensity = 0.18 * (1 - g); cloudMat.color.setRGB(1 - g * 0.55, 1 - g * 0.5, 1 - g * 0.35) } }
    val LIFT = Lift()
    val TP = ThermalFx(this)
    val WS = WindFx(this)

    /* ---------- moving things ---------- */
    val CARS = Cars()
    val JETS = ArrayList<Jet>()
    val BIRDS = Birds()
    val PILOTS = ArrayList<Pilot>()
    val PARAS = ArrayList<Para>()
    val AIHELI = ArrayList<AiHeli>()
    val FW = Fireworks()
    val HOADANG = HoaDang()
    val SQUID = makeSquid()
    var HALO: Points? = null
    var haloMat: PointsMaterial? = null
    val SPINNERS = ArrayList<Spinner>()

    /* ---------- combat state (Combat*.kt / Missions.kt) ---------- */
    // COMBAT-STATE-BEGIN
    /** progress saved on this device (web: SAVE) */
    val SAVE = loadSave(store)
    /** the menu selection: craft, mission, difficulty (web: PICK) */
    val PICK = Pick(store)
    var DF = DIFF[1]
    val combat = Group().also { scene.add(it) }
    val PL = PlayerState()
    val MS = MissionState()
    val TRC = makeTRC()
    val BLB = makeBLB()
    val BUL = ArrayList<Bullet>()
    val CIV = ArrayList<Civ>()
    val INKD = ArrayList<Mesh>()
    val inkGeo = Geo.circle(1.0, 14).rotateX(-PI / 2)
    val inkMat = BasicMaterial("#2a1f3d").also { it.transparent = true; it.opacity = 0.72; it.depthWrite = false; it.polygonOffset = true; it.polyFactor = -6f; it.polyUnits = -6f }
    val dsCache = HashMap<String, ToonMaterial>()
    val eyeMat = toon("#ff3b2f") { emissive.copy(Color.of("#ff2a1a")); emissiveIntensity = 0.9 }
    val engMat = toon("#b04cff") { emissive.copy(Color.of("#b04cff")); emissiveIntensity = 0.7 }
    val flameMat = BasicMaterial("#ffb347").also { it.transparent = true; it.opacity = 0.85; it.blending = Blend.ADDITIVE; it.depthWrite = false }
    val podMat = toon("#c04cff") { emissive.copy(Color.of("#b04cff")); emissiveIntensity = 0.65 }
    val coreMat = toon("#ff3b2f") { emissive.copy(Color.of("#ff2a1a")); emissiveIntensity = 0.4 }
    val EK = makeEK()
    val EN = ArrayList<Enemy>()
    val MIS = ArrayList<Missile>()
    val FLR = ArrayList<Flare>()
    val PKS = ArrayList<Pickup>()
    val DLV = Delivery()
    val FT = ArrayList<FloatText>()
    val MISSIONS = makeMissions()
    val SURV = makeSurv()
    val PRAC = makePrac()
    val ALLM = MISSIONS + SURV + PRAC
    /** the results panel once a mission ends (web: the #pResult DOM) */
    var result: MissionResult? = null
    /** the pause panel's subtitle (web: #pauseSub) */
    var pauseSub = ""
    // COMBAT-STATE-END

    /* ---------- rain + loop bookkeeping ---------- */
    val RAIN = Rain(this)
    var shadowE = 80.0
    var regT = 0.0; var regPending: String? = null; var regPendT = 0.0
    var cellT = 0.0
    var batchInfo = ""

    /* ---------- the player's aircraft ---------- */
    val F = Flight().also { scene.add(it.g) }
    val MODELS = LinkedHashMap<String, CraftModel>()
    val TRAIL_N = 70
    val trails = List(2) { Trail(TRAIL_N).also { t -> scene.add(t.l) } }
    val blobMat = BasicMaterial("#1f1d2b").also { it.transparent = true; it.opacity = 0.3; it.depthWrite = false; it.polygonOffset = true; it.polyFactor = -4f }
    val blob = Mesh(Geo.circle(1.0, 24).rotateX(-PI / 2), blobMat).also { scene.add(it) }
    var liftSm = 0.0; var vsSm = 0.0

    /* ---------- camera ---------- */
    val camPos = Vec3(); val lookAt = Vec3(); val _cu = Vec3(); val _cf = Vec3(); val _cr = Vec3()
    var camYaw = 0.0; var camPitch = 0.0

    /* ---------- regions for the location titles ---------- */
    val REG = listOf(
        Region("sontra", "Bán đảo Sơn Trà", "山茶", "Lá phổi xanh của Đà Nẵng — rừng nguyên sinh, voọc chà vá và gió biển lồng lộng"),
        Region("bay", "Vịnh Đà Nẵng", "沱灢灣", "Vịnh biển ôm trọn thành phố giữa Sơn Trà và đèo Hải Vân"),
        Region("han", "Sông Hàn", "", "Dòng sông của những cây cầu — sáng đèn lung linh mỗi tối"),
        Region("city", "Thành phố Đà Nẵng", "沱灢", "Thành phố đáng sống bên sông, bên biển"),
        Region("mykhe", "Biển Mỹ Khê", "美溪", "Cát trắng, sóng xanh và những chiếc thúng chai"),
        Region("nguhanh", "Ngũ Hành Sơn", "五行山", "Năm ngọn núi đá vôi Kim – Mộc – Thủy – Hỏa – Thổ"),
        Region("coast", "Biển Non Nước", "", "Dải cát dài với rừng phi lao chạy tít về Hội An"),
        Region("rice", "Đồng lúa Quảng Nam", "廣南", "Ruộng lúa xanh, lũy tre làng, nón lá và đàn trâu"),
        Region("hoian", "Phố cổ Hội An", "會安", "Thương cảng xưa bên sông Hoài — đèn lồng thắp sáng mỗi đêm"),
        Region("anbang", "Biển An Bàng – Cửa Đại", "", "Nơi sông Thu Bồn gặp biển"),
        Region("culao", "Cù Lao Chàm", "占不勞", "Đảo ngọc giữa biển xanh, khu dự trữ sinh quyển thế giới"),
        Region("sea", "Biển Đông", "", "Thuyền câu mực thắp đèn sáng rực mỗi đêm"),
        Region("bana", "Núi Bà Nà", "", "Đỉnh núi mây phủ, Cầu Vàng và đường cáp treo dài ngoạn mục"),
        Region("haivan", "Đèo Hải Vân", "海雲", "Mây biển quấn quanh đỉnh đèo hùng vĩ nhất Việt Nam"),
        Region("mtn", "Dãy Trường Sơn", "長山", "Rừng núi điệp trùng phía tây thành phố"),
    )
    val RG = REG.associateBy { it.id }

    /* ---------- messages ---------- */
    fun toast(t: String) { msg.toast = t; msg.toastT = game.t }
    fun showTitle(r: Region) { msg.tHan = r.han; msg.tBig = r.name; msg.tSub = r.sub; msg.titleT = game.t }
    fun showTip(k: String, h: String, p: String, ms: Double = 4600.0) { msg.tipK = k; msg.tipH = h; msg.tipP = p; msg.tipT = game.t; msg.tipMs = ms }

    fun regionAt(x: Double, z: Double): Region {
        val S = Geog.S
        Geog.sample(x, z)
        if (S.st > -40 && z < -1900) return RG["sontra"]!!
        if (S.isl > -400) return RG["culao"]!!
        if (hypot(x + 3450, z + 250) < 900) return RG["bana"]!!
        if (x < -1600 && z < -2300) return RG["haivan"]!!
        if (S.M > 0.35) return RG["mtn"]!!
        if (S.sd < 0 && z < -1880 && x < 300) return RG["bay"]!!
        if (S.sd < -300) return RG["sea"]!!
        if (x > 400 && x < 1060 && z > 560 && z < 1220) return RG["nguhanh"]!!
        if (x > 380 && x < 1000 && z > 3330 && z < 3700) return RG["hoian"]!!
        if (z > 2900 && S.sd < 500) return RG["anbang"]!!
        if (S.sd < 260 && z < 650) return RG["mykhe"]!!
        if (Geog.lineDist(Geog.R_HAN, x, z) < 140 && z < 300) return RG["han"]!!
        if (S.city > 0.4) return RG["city"]!!
        if (S.sd < 400 && z >= 650) return RG["coast"]!!
        return RG["rice"]!!
    }
}

fun psiTo(dx: Double, dz: Double) = atan2(-dx, -dz)
fun fwd(psi: Double, gam: Double, out: Vec3 = Vec3()) = out.set(-sin(psi) * cos(gam), sin(gam), -cos(psi) * cos(gam))
fun fmtT(s: Double) = "${floor(s / 60).toInt()}:${floor(s % 60).toInt().toString().padStart(2, '0')}"
