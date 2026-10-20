package vn.paperworld.phidoi.game

import vn.paperworld.phidoi.engine.Vec3

/** silent audio (tests, headless runs) */
class NullAudio : AudioApi {
    override val sfx = object : Sfx {
        override fun fold() {}; override fun whoosh(p: Vec3?) {}; override fun crumple() {}; override fun thud(v: Double) {}; override fun splash(v: Double) {}
        override fun chime(n: Int) {}; override fun ring(i: Int) {}; override fun stamp() {}; override fun nest() {}; override fun fanfare() {}; override fun boom(p: Vec3) {}
        override fun bell(p: Vec3) {}; override fun gull() {}; override fun beep(f: Double, len: Double, v: Double) {}; override fun gun() {}; override fun egun(p: Vec3?) {}
        override fun missile(p: Vec3?) {}; override fun explode(p: Vec3, s: Double) {}; override fun hit() {}; override fun hurt() {}; override fun flare() {}
        override fun alarm(n: Int) {}; override fun fail() {}; override fun warn(on: Boolean) {}
    }
    override val radioOn = false
    override val songIndex = 0
    override val songNames = listOf("—")
    override val songProgress = 0.0
    override var onSongChange: ((String) -> Unit)? = null
    override fun init() {}
    override fun radioSet(on: Boolean) {}
    override fun nextSong() {}
    override fun update(dt: Double, f: AudioFrame) {}
}
