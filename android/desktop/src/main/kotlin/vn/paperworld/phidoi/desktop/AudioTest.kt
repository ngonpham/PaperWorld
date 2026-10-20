package vn.paperworld.phidoi.desktop

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.sin
import vn.paperworld.phidoi.engine.Vec3
import vn.paperworld.phidoi.game.Audio
import vn.paperworld.phidoi.game.AudioFrame
import vn.paperworld.phidoi.game.PcmOut

/** a PcmOut that collects 16-bit stereo PCM and writes a .wav on close (never blocks) */
class WavOut(private val file: File, override val sampleRate: Int) : PcmOut {
    private val data = ByteArrayOutputStream()
    var frames = 0L; private set
    override fun write(buf: ShortArray, frames: Int) {
        for (i in 0 until frames * 2) { val s = buf[i].toInt(); data.write(s and 0xff); data.write((s shr 8) and 0xff) }
        this.frames += frames
    }
    override fun close() {
        val d = data.toByteArray()
        RandomAccessFile(file, "rw").use { f ->
            f.setLength(0)
            fun i32(v: Int) { f.write(v and 0xff); f.write((v shr 8) and 0xff); f.write((v shr 16) and 0xff); f.write((v shr 24) and 0xff) }
            fun i16(v: Int) { f.write(v and 0xff); f.write((v shr 8) and 0xff) }
            f.writeBytes("RIFF"); i32(36 + d.size); f.writeBytes("WAVE")
            f.writeBytes("fmt "); i32(16); i16(1); i16(2); i32(sampleRate); i32(sampleRate * 4); i16(4); i16(16)
            f.writeBytes("data"); i32(d.size); f.write(d)
        }
    }
}

/**
 * Offline audio render: `-Pargs=audio -Dpd.out=/abs/audio.wav [-Dpd.sr=44100]`.
 * Writes the full mix (radio + flight beds + a dense run of sfx) to `out`, plus `out-sfx.wav` (every sfx alone in a
 * 2 s slot, radio off, grounded) with `out.events.txt` (expected onset times), `out-music.wav` (radio only) and
 * `out-flight.wav` (beds only: jet, afterburner, seaplane, helicopter, landed).
 */
fun audioTest() {
    val out = File(System.getProperty("out", "audio.wav")).absoluteFile
    val sr = System.getProperty("sr", "44100").toInt()
    val base = out.path.removeSuffix(".wav")
    val mainTimeline: (Audio, (Double, () -> Unit) -> Unit) -> Unit = { a, at ->
        val s = a.sfx
        at(1.0) { s.fold() }
        at(1.5) { s.whoosh(Vec3(100.0, 0.0, 0.0)) }
        for (i in 0 until 10) at(2.0 + i * 0.08) { s.gun() }
        at(3.0) { s.missile(null) }
        at(3.5) { s.egun(Vec3(0.0, 0.0, 200.0)) }
        at(4.0) { s.explode(Vec3(300.0, 0.0, 0.0), 1.0) }
        at(5.0) { s.boom(Vec3(500.0, 0.0, 0.0)) }
        at(7.0) { s.chime(5) }
        at(7.5) { s.ring(3) }
        at(8.0) { s.fanfare() }
        at(9.0) { s.alarm(3) }
        at(10.0) { s.warn(true) }
        at(10.2) { s.warn(true) }
        at(11.0) { s.splash(1.0) }
        at(12.0) { s.bell(Vec3(50.0, 20.0, 0.0)) }
        at(13.0) { s.gull() }
        at(14.0) { s.hit() }
        at(14.3) { s.hurt() }
        at(16.0) { s.flare() }
        at(17.0) { s.crumple() }
        at(18.0) { s.fail() }
        at(19.0) { s.stamp() }
        at(19.6) { s.nest() }
        at(20.0) { a.nextSong() }
        at(22.0) { s.explode(Vec3(30.0, 0.0, 0.0), 2.0) }
        at(23.0) { s.beep(1000.0, 0.06, 0.07) }
    }
    val main = WavOut(out, sr).let { w -> runScenario(w, radio = true, flight = true, seconds = 25.0, timeline = mainTimeline).also { w.close() } }
    val events = StringBuilder()
    // each effect with a fresh synth in its own 2 s slot, fired 0.6 s in
    val sfxWav = WavOut(File("$base-sfx.wav"), sr)
    var sfxOnly: RunResult? = null
    for ((i, e) in SFX.withIndex()) {
        val slot0 = sfxWav.frames.toDouble() / sr
        val r = runScenario(sfxWav, radio = false, flight = false, seconds = 2.0) { a, at -> at(0.6) { e.second(a) } }
        sfxOnly = sfxOnly?.plus(r) ?: r
        events.append("%.4f %s\n".format(slot0 + 0.6 + if (e.first == "boom") 200 / 343.0 else if (e.first == "explode") 100 / 900.0 else 0.0, e.first))
    }
    sfxWav.close()
    File("$base.events.txt").writeText(events.toString())
    val music = WavOut(File("$base-music.wav"), sr).let { w -> runScenario(w, radio = true, flight = false, seconds = 20.0) { _, _ -> }.also { w.close() } }
    val flight = WavOut(File("$base-flight.wav"), sr).let { w -> runScenario(w, radio = false, flight = true, seconds = 25.0) { _, _ -> }.also { w.close() } }
    // warm (post-JIT) speed of the full mix
    val warm = runScenario(WavOut(File("$base-warm.wav"), sr), radio = true, flight = true, seconds = 25.0, timeline = mainTimeline)
    for ((n, r) in listOf("main" to main, "sfx" to sfxOnly!!, "music" to music, "flight" to flight, "warm" to warm))
        println("%-6s %5.1f s audio in %6.0f ms  realtime x%.1f  maxVoices=%d nan=%d clip=%d songs=%s"
            .format(n, r.seconds, r.ms, r.seconds * 1000 / r.ms, r.maxVoices, r.nan, r.clip, r.songs))
    realtimeSmoke(sr)
    println("wrote $out")
}

/** the threaded path: a PcmOut that blocks like a sound card, game-thread calls while it runs, then release */
private fun realtimeSmoke(sr: Int) {
    val written = java.util.concurrent.atomic.AtomicLong()
    val t0 = System.nanoTime()
    val pace = object : PcmOut {
        override val sampleRate = sr
        override fun write(buf: ShortArray, frames: Int) {
            val due = t0 + (written.addAndGet(frames.toLong()) - sr / 10) * 1_000_000_000L / sr
            val wait = due - System.nanoTime()
            if (wait > 0) Thread.sleep(wait / 1_000_000, (wait % 1_000_000).toInt())
        }
    }
    val a = Audio { pace }
    val toasts = ArrayList<String>()
    a.onSongChange = { toasts.add(it) }
    a.init()
    val f = AudioFrame().apply { flying = true; v = 50.0; modeFly = true }
    for (i in 0 until 90) {
        f.gameT = i / 60.0
        a.update(1 / 60.0, f)
        if (i == 10) a.sfx.chime()
        if (i == 20) { a.sfx.gun(); a.sfx.explode(Vec3(10.0, 0.0, 0.0)) }
        if (i == 30) a.nextSong()
        if (i == 60) a.pause(true)
        if (i == 70) a.pause(false)
        Thread.sleep(16)
    }
    val frames = written.get()
    a.release()
    val after = written.get()
    Thread.sleep(50)
    println("realtime: ${"%.2f".format(frames.toDouble() / sr)} s written in ${"%.2f".format((System.nanoTime() - t0) / 1e9)} s, " +
        "song=${a.songIndex} toasts=$toasts nan=${a.nanCount} stoppedAfterRelease=${written.get() == after}")
}

/** every effect once, for the isolated sfx render */
private val SFX: List<Pair<String, (Audio) -> Unit>> = listOf(
    "fold" to { a -> a.sfx.fold() },
    "whoosh" to { a -> a.sfx.whoosh(Vec3(100.0, 0.0, 0.0)) },
    "crumple" to { a -> a.sfx.crumple() },
    "thud" to { a -> a.sfx.thud() },
    "splash" to { a -> a.sfx.splash() },
    "chime" to { a -> a.sfx.chime(5) },
    "ring" to { a -> a.sfx.ring(3) },
    "stamp" to { a -> a.sfx.stamp() },
    "nest" to { a -> a.sfx.nest() },
    "fanfare" to { a -> a.sfx.fanfare() },
    "boom" to { a -> a.sfx.boom(Vec3(200.0, 30.0, 0.0)) },
    "bell" to { a -> a.sfx.bell(Vec3(50.0, 20.0, 0.0)) },
    "gull" to { a -> a.sfx.gull() },
    "beep" to { a -> a.sfx.beep(1000.0) },
    "gun" to { a -> a.sfx.gun() },
    "egun" to { a -> a.sfx.egun(Vec3(0.0, 30.0, 200.0)) },
    "missile" to { a -> a.sfx.missile(null) },
    "explode" to { a -> a.sfx.explode(Vec3(100.0, 30.0, 0.0)) },
    "hit" to { a -> a.sfx.hit() },
    "hurt" to { a -> a.sfx.hurt() },
    "flare" to { a -> a.sfx.flare() },
    "alarm" to { a -> a.sfx.alarm() },
    "fail" to { a -> a.sfx.fail() },
    "warn" to { a -> a.sfx.warn(true) },
)

private class RunResult(val seconds: Double, val ms: Double, val maxVoices: Int, val nan: Long, val clip: Long, val songs: List<String>) {
    operator fun plus(o: RunResult) = RunResult(seconds + o.seconds, ms + o.ms, maxOf(maxVoices, o.maxVoices), nan + o.nan, clip + o.clip, songs + o.songs)
}

/** drive a fresh synth for [seconds] of simulated 60 fps game time, rendering into [wav] (left open) */
private fun runScenario(wav: WavOut, radio: Boolean, flight: Boolean, seconds: Double,
                        timeline: (Audio, (Double, () -> Unit) -> Unit) -> Unit): RunResult {
    val sr = wav.sampleRate
    val f0 = wav.frames
    val a = Audio { wav }
    val songs = ArrayList<String>()
    a.onSongChange = { songs.add(it) }
    a.radioSet(radio)
    check(a.initOffline())
    val f = AudioFrame()
    val dt = 1.0 / 60
    var t = 0.0
    var frac = 0.0
    var renderNs = 0L
    val tl = ArrayList<Pair<Double, () -> Unit>>()
    timeline(a) { time, fn -> tl.add(time to fn) }
    tl.sortBy { it.first }
    var next = 0
    while (t < seconds) {
        // simulated game state
        f.gameT = t
        f.cam.set(0.0, 30.0, 0.0)
        f.flying = flight && t < 24.0
        f.vario = true
        f.bellAt = null; f.gullOk = false; f.shore = 0.0; f.rain = 0.0
        f.afterburner = false; f.heli = false; f.glide = false; f.landed = false; f.modeFly = true
        f.craftId = "jet"; f.v = 0.0; f.climb = 0.0; f.vy = 0.0; f.thr = 0.0; f.energy = 0.0
        if (flight) when {
            t < 8.0 -> { f.v = 60 + 35 * sin(t * 0.7); f.climb = 3 * sin(t * 0.9); f.afterburner = t > 5.0; f.energy = 1.0; f.thr = 0.8 }
            t < 15.0 -> {
                f.craftId = "seaplane"; f.v = 30 + 10 * sin(t); f.thr = 0.5 + 0.5 * sin(t * 1.3); f.climb = -4 + 6 * sin(t * 0.6); f.glide = true
                f.shore = if (t > 10) 0.7 else 0.0; f.gullOk = t > 10; if (t > 11.5 && t < 12.5) f.bellAt = Vec3(80.0, 20.0, 0.0)
            }
            else -> {
                f.craftId = "heli"; f.heli = true; f.v = 15 + 10 * sin(t); f.vy = 4 * sin(t * 0.8); f.energy = 0.6; f.climb = f.vy
                f.rain = if (t > 17) 0.6 else 0.0; f.landed = t > 21.0; f.modeFly = !f.landed
            }
        }
        f.camMode = if (t in 6.0..7.0) 2 else 0
        while (next < tl.size && tl[next].first <= t + 1e-9) { tl[next].second(); next++ }
        a.update(dt, f)
        frac += sr * dt
        val n = frac.toInt(); frac -= n
        val t0 = System.nanoTime()
        a.render(n)
        renderNs += System.nanoTime() - t0
        t += dt
    }
    a.update(dt, f)
    return RunResult((wav.frames - f0).toDouble() / sr, renderNs / 1e6, a.maxVoicesSeen, a.nanCount, a.clipCount, songs)
}
