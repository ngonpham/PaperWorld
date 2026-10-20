package vn.paperworld.phidoi.game

import vn.paperworld.phidoi.engine.Vec3

/** where the synthesized 16-bit stereo PCM goes (Android AudioTrack, a WAV file on the desktop) */
interface PcmOut {
    val sampleRate: Int
    /** blocking write of [frames] interleaved stereo frames */
    fun write(buf: ShortArray, frames: Int)
    fun close() {}
}

/** what the game tells the audio engine every frame (mirrors the reads in the web build's updateAudio) */
class AudioFrame {
    /** game.started && F.mode != "crash" */
    var flying = false
    var v = 0.0
    var craftId = "jet"
    var heli = false
    var glide = false
    /** F.mode == "landed" / "fly" */
    var landed = false
    var modeFly = false
    var vy = 0.0
    var thr = 0.0
    var energy = 0.0
    /** jet afterburner: C.id == "jet" && IN.boost > 0.05 && F.energy > 0.01 */
    var afterburner = false
    var camMode = 0
    /** smooth(260, 20, |S.sd|) * smooth(120, 10, camera.y) at the camera */
    var shore = 0.0
    var rain = 0.0
    /** F.vs */
    var climb = 0.0
    var vario = false
    var gameT = 0.0
    val cam = Vec3()
    /** a temple bell within 260 m of the player (Lady Buddha / Thủy Sơn), else null */
    var bellAt: Vec3? = null
    /** a gull call may play (shore > 0.2 or a seagull within 120 m of the camera) */
    var gullOk = false
}

/** sound effects, same names and arguments as the web build's `sfx` object */
interface Sfx {
    fun fold()
    fun whoosh(p: Vec3?)
    fun crumple()
    fun thud(v: Double = 0.3)
    fun splash(v: Double = 1.0)
    fun chime(n: Int = 3)
    fun ring(i: Int)
    fun stamp()
    fun nest()
    fun fanfare()
    fun boom(p: Vec3)
    fun bell(p: Vec3)
    fun gull()
    fun beep(f: Double, len: Double = 0.06, v: Double = 0.07)
    fun gun()
    fun egun(p: Vec3?)
    fun missile(p: Vec3?)
    fun explode(p: Vec3, s: Double = 1.0)
    fun hit()
    fun hurt()
    fun flare()
    fun alarm(n: Int = 2)
    fun fail()
    fun warn(on: Boolean)
}

/** what the game uses of the audio engine */
interface AudioApi {
    val sfx: Sfx
    /** true once the radio is playing */
    val radioOn: Boolean
    val songIndex: Int
    val songNames: List<String>
    /** position in the current song 0..1 (the radio progress bar) */
    val songProgress: Double
    /** called (on the game thread, from update) when the song changes while the radio is on: show a toast */
    var onSongChange: ((String) -> Unit)?
    /** start the engine (first user gesture) */
    fun init()
    fun radioSet(on: Boolean)
    fun nextSong()
    /** switch the radio to song [i] from its start, without the song toast (web startMission: A.song = M.song; A.bar = 0; A.step = 0) */
    fun setSong(i: Int) {}
    /** per frame, game thread */
    fun update(dt: Double, f: AudioFrame)
    fun pause(paused: Boolean) {}
    fun release() {}
}
