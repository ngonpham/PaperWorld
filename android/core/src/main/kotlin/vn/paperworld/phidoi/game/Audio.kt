package vn.paperworld.phidoi.game

import java.util.Random
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.*
import vn.paperworld.phidoi.engine.Mulberry32
import vn.paperworld.phidoi.engine.Vec3
import vn.paperworld.phidoi.engine.clamp

/**
 * AUDIO — Sóng Biển FM (generative ngũ cung), wind, vario, sfx.
 *
 * A port of the web build's WebAudio graph to a small software synth:
 * master gain 0.85 → compressor (−16 dB, 3:1, 30 dB knee, makeup) → out;
 * a Freeverb-style stereo reverb calibrated to the energy of the 2.4 s noise-IR convolver (revIn 0.4);
 * music bus (gain → 6 kHz low-pass → master + reverb); sfx bus (0.9 → master + reverb);
 * looping filtered-noise beds (wind, hiss, waves, rain, 19 Hz-AM paper flutter), the seaplane engine
 * (two detuned saws → 700 Hz low-pass) and the variometer sink tone straight into master.
 *
 * Threads: [init] starts an audio thread that owns every voice and writes blocks to the [PcmOut]
 * (which blocks). Everything the game calls ([sfx], [radioSet], [nextSong], [update]) only posts commands
 * or parameter targets. For offline rendering (tests) use [initOffline] + [render] instead of [init].
 *
 * [onSongChange] receives the bare song name (the web build toasts "♫ " + name).
 */
class Audio(private val outFactory: () -> PcmOut?) : AudioApi {

    // ------------------------------------------------------------------ songs (same data as the web build)
    private class SongDef(val n: String, val root: Int, val sc: IntArray, val bpm: Int, val lead: String, val seed: Int)
    private class Note(val m: Int, val len: Int)
    private class SongData(val d: SongDef, val form: Array<Array<Note?>>, val prog: IntArray) {
        val bpm get() = d.bpm
        val lead get() = d.lead
        fun deg(x: Int) = deg(d, x)
    }

    private companion object {
        const val BLOCK = 256
        const val POOL = 96
        const val MAXV = 64
        const val FADE = 128
        const val TAB = 2048
        val SIN = FloatArray(TAB + 1) { sin(2.0 * PI * it / TAB).toFloat() }

        val BAC = intArrayOf(0, 2, 5, 7, 9)
        val OAN = intArrayOf(0, 3, 5, 7, 10)
        val XUAN = intArrayOf(0, 2, 5, 7, 10)
        val SONGS = arrayOf(
            SongDef("Cất cánh Mỹ Khê", 62, BAC, 116, "tranh", 11),
            SongDef("Rồng vàng sông Hàn", 57, XUAN, 122, "sao", 23),
            SongDef("Gió lộng Sơn Trà", 60, BAC, 110, "bau", 37),
            SongDef("Đêm hoa đăng", 55, OAN, 98, "bau", 41),
            SongDef("Mực Khổng Lồ", 58, OAN, 128, "tranh", 53),
            SongDef("Phi đội giấy", 64, BAC, 118, "sao", 67),
        )
        val ARP = intArrayOf(0, 2, 4, 2, 5, 4, 2, 4)
        val SONGDATA = Array(SONGS.size) { makeSong(SONGS[it]) }

        fun mtof(m: Double) = 440.0 * 2.0.pow((m - 69.0) / 12.0)
        fun mtof(m: Int) = mtof(m.toDouble())
        fun deg(s: SongDef, d: Int) = s.root + s.sc[((d % 5) + 5) % 5] + 12 * Math.floorDiv(d, 5)

        fun makeSong(s: SongDef): SongData {
            val r = Mulberry32(s.seed)
            fun phrase(): Array<Note?> {
                val out = arrayOfNulls<Note>(16)
                var d = floor(r.next() * 5).toInt() + 5
                for (st in 0 until 16) {
                    val on = if (st % 4 == 0) r.next() < 0.85 else if (st % 2 == 0) r.next() < 0.45 else r.next() < 0.12
                    if (!on) continue
                    d = (d + floor(r.next() * 5).toInt() - 2).coerceIn(3, 12)
                    out[st] = Note(deg(s, d), if (st % 4 == 0 && r.next() < 0.5) 3 else 1)
                }
                return out
            }
            val a1 = phrase(); val a2 = phrase(); val b1 = phrase(); val b2 = phrase()
            val form = arrayOf(a1, a2, a1, b1, b2, a1, a2, b2)
            val prog = intArrayOf(0, 3, 2, 0, 0, 3, 4, 0).map { deg(s, it) - 24 }.toIntArray()
            return SongData(s, form, prog)
        }

        // waveforms / filter types
        const val SINE = 0; const val TRI = 1; const val SAW = 2; const val SQR = 3; const val NOISE = 4
        const val NONE = 0; const val LP = 1; const val HP = 2; const val BP = 3

        fun sinTab(p: Double): Double {
            val x = p * TAB
            val i = x.toInt()
            val a = SIN[i]
            return a + (SIN[i + 1] - a) * (x - i)
        }

        fun polyBlep(t: Double, dt: Double): Double = when {
            t < dt -> { val x = t / dt; x + x - x * x - 1.0 }
            t > 1.0 - dt -> { val x = (t - 1.0) / dt; x * x + x + x + 1.0 }
            else -> 0.0
        }

        /** band-limited (PolyBLEP) sawtooth, WebAudio phase: 0 at p=0 rising, jump at p=0.5 */
        fun saw(p: Double, dt: Double): Double {
            var q = p + 0.5; if (q >= 1.0) q -= 1.0
            return 2.0 * q - 1.0 - polyBlep(q, dt)
        }
    }

    // ------------------------------------------------------------------ game-thread side
    @Volatile private var synth: Synth? = null
    private var out: PcmOut? = null
    private var thread: Thread? = null
    @Volatile private var running = false
    @Volatile private var paused = false
    private val pauseLock = Object()
    private val cmds = ConcurrentLinkedQueue<(Synth) -> Unit>()
    private val songMsgs = ConcurrentLinkedQueue<String>()
    private var wantOn: Boolean? = null
    @Volatile private var on = false
    @Volatile private var song = 0
    @Volatile private var bar = 0
    private var frame = AudioFrame()
    private val grnd = Random()
    private var varioT = 0.0
    private var bellT = 0.0
    private var gullT = 0.0
    private var warnT = 0.0
    private var hitT = 0.0
    private var hurtT = 0.0
    private val paramLock = Any()
    private val tgtG = DoubleArray(NP)
    private val tgtShared = DoubleArray(NP)
    private var tgtDirty = false

    override val radioOn get() = on
    override val songIndex get() = song
    override val songNames: List<String> = SONGS.map { it.n }
    override val songProgress: Double get() { val n = SONGDATA[song].form.size * 4; return (bar % n).toDouble() / n }
    override var onSongChange: ((String) -> Unit)? = null

    /** debug: voices alive in the last block, the most seen at once, NaN samples, clipped samples */
    val activeVoices get() = synth?.liveCount ?: 0
    val maxVoicesSeen get() = synth?.maxLive ?: 0
    val nanCount get() = synth?.nanCount ?: 0L
    val clipCount get() = synth?.clipCount ?: 0L

    private val ready get() = synth != null


    override fun init() {
        if (synth != null) { pause(false); return }
        val o = outFactory() ?: return
        out = o
        synth = Synth(o.sampleRate)
        radioSet(wantOn ?: true)
        running = true
        thread = Thread({ loop(o) }, "phidoi-audio").apply { priority = Thread.MAX_PRIORITY; isDaemon = true; start() }
    }

    /** set up the synth without the realtime thread; drive it with [render] (desktop tests) */
    fun initOffline(): Boolean {
        if (synth != null) return true
        val o = outFactory() ?: return false
        out = o
        synth = Synth(o.sampleRate)
        radioSet(wantOn ?: true)
        return true
    }

    /** synthesize [frames] frames on the calling thread and write them to the PcmOut (offline mode only) */
    fun render(frames: Int) {
        val s = synth ?: return
        val o = out ?: return
        var left = frames
        while (left > 0) {
            val n = min(left, BLOCK)
            s.block(n)
            o.write(s.pcm, n)
            left -= n
        }
    }

    private fun loop(o: PcmOut) {
        val s = synth ?: return
        try {
            while (running) {
                if (paused) {
                    synchronized(pauseLock) { while (paused && running) pauseLock.wait(200) }
                    continue
                }
                s.block(BLOCK)
                o.write(s.pcm, BLOCK)
            }
        } catch (_: InterruptedException) {
        } finally {
            try { o.close() } catch (_: Exception) {}
        }
    }

    override fun pause(paused: Boolean) {
        this.paused = paused
        if (!paused) synchronized(pauseLock) { pauseLock.notifyAll() }
    }

    override fun release() {
        running = false
        pause(false)
        val t = thread
        if (t != null) { t.interrupt(); try { t.join(500) } catch (_: InterruptedException) {} }
        else try { out?.close() } catch (_: Exception) {}
        thread = null
        synth = null
        out = null
        paused = false
        cmds.clear()
    }

    private fun post(c: (Synth) -> Unit) { if (ready) cmds.add(c) }

    override fun radioSet(on: Boolean) {
        wantOn = on
        if (!ready) return
        this.on = on
        post { it.musT = if (on) 0.75 else 0.0 }
    }

    override fun nextSong() {
        if (!ready) { song = (song + 1) % SONGS.size; bar = 0; return }
        post { it.nextSongA() }
    }

    override fun setSong(i: Int) {
        if (!ready || i !in SONGS.indices || song == i) return
        song = i; bar = 0
        post { it.setSongA(i) }
    }

    private fun distGain(p: Vec3?) = if (p == null) 1.0 else clamp(1 - frame.cam.distanceTo(p) / 900, 0.0, 1.0)

    override fun update(dt: Double, f: AudioFrame) {
        frame = f
        while (true) { val s = songMsgs.poll() ?: break; onSongChange?.invoke(s) }
        if (!ready) return
        val flying = f.flying
        val v = if (flying) f.v else 0.0
        val heli = f.heli
        val landed = f.landed
        val t = tgtG
        t[P_WIND_F] = 300 + v * 18
        t[P_WIND_G] = if (flying) clamp(0.02 + v * 0.0028, 0.0, 0.32) * (if (f.camMode == 2) 1.4 else 1.0) else 0.01
        t[P_FLUT_LG] = if (flying && heli) (if (landed) 0.08 else 0.2) else 0.0
        t[P_LFO_F] = if (heli) (if (landed) 7.0 else 11 + f.vy * 0.15) else 19.0
        t[P_FLUT_F] = if (heli) 380.0 else 1300.0
        val ab = f.afterburner
        t[P_ENG_G] = if (flying && f.craftId == "seaplane") 0.04 + f.thr * 0.07
            else if (flying && heli) 0.035 + f.energy * 0.03
            else if (flying && f.craftId == "jet") 0.03 + (if (ab) 0.05 else 0.0)
            else 0.0
        for (i in 0..1) t[P_ENG_F0 + i] = if (f.craftId == "jet") 80 + f.v * 0.45 + (if (ab) 30 else 0) + i * 2.1 else 55 + f.thr * 70 + i * 1.6
        t[P_HISS_G] = if (flying && ab) 0.09 else if (flying) clamp((v - 35) * 0.002, 0.0, 0.12) else 0.0
        t[P_WAVES_G] = f.shore * (0.1 + 0.06 * sin(f.gameT * 0.6))
        t[P_RAIN_G] = f.rain * 0.1
        val climb = f.climb
        t[P_SINK_G] = if (f.vario && flying && f.modeFly && climb < -3.2 && f.glide) 0.035 else 0.0
        t[P_SINK_F] = 260 + climb * 8
        synchronized(paramLock) { System.arraycopy(t, 0, tgtShared, 0, NP); tgtDirty = true }
        // variometer: rising beeps
        if (f.vario && flying && f.modeFly && climb > 0.3) {
            varioT -= dt
            if (varioT <= 0) { varioT = 0.55 / (1 + climb * 0.45); sfx.beep(560 + climb * 85, 0.07 + 0.03 / (1 + climb), 0.055) }
        }
        // temple bells + gulls
        bellT -= dt; gullT -= dt
        if (bellT <= 0) {
            val b = f.bellAt
            if (b != null) { sfx.bell(b); bellT = 16.0 }
            if (bellT <= 0) bellT = 3.0
        }
        if (gullT <= 0) { gullT = 4 + 5 * grnd.nextDouble(); if (f.gullOk) sfx.gull() }
    }

    override val sfx: Sfx = object : Sfx {
        override fun fold() = post { it.fold() }
        override fun whoosh(p: Vec3?) { if (!ready) return; val k = distGain(p); if (k <= 0) return; post { it.noiseBurst(it.now, BP, 300.0, 1800.0, 0.6, 0.3 * k, 1.5) } }
        override fun crumple() = post { it.crumple() }
        override fun thud(v: Double) = post { it.thud(it.now, v) }
        override fun splash(v: Double) = post { it.noiseBurst(it.now, LP, 3000.0, 300.0, 0.7, 0.35 * v, 0.7) }
        override fun chime(n: Int) = post { it.chime(n) }
        override fun ring(i: Int) = post { it.ring(i) }
        override fun stamp() = post { it.stamp() }
        override fun nest() = post { it.nest() }
        override fun fanfare() = post { it.fanfare() }
        override fun boom(p: Vec3) {
            if (!ready) return
            val d = frame.cam.distanceTo(p); val k = clamp(1 - d / 2200, 0.0, 1.0)
            if (k <= 0) return
            post { it.boom(it.now + d / 343, k) }
        }
        override fun bell(p: Vec3) { if (!ready) return; val k = distGain(p) * 0.8 + 0.2; post { it.bell(k) } }
        override fun gull() = post { it.gull() }
        override fun beep(f: Double, len: Double, v: Double) = post { it.beep(it.now, f, len, v) }
        override fun gun() = post { it.gun() }
        override fun egun(p: Vec3?) {
            if (!ready) return
            val k = distGain(p) * 0.7; if (k <= 0.03) return
            post { s -> for (i in 0 until 3) s.noiseBurst(s.now + i * 0.075, BP, 1500.0, 450.0, 0.05, 0.11 * k, 1.4) }
        }
        override fun missile(p: Vec3?) { if (!ready) return; val k = distGain(p); if (k <= 0) return; post { it.noiseBurst(it.now, BP, 300.0, 2600.0, 1.1, 0.4 * k, 0.9) } }
        override fun explode(p: Vec3, s: Double) {
            if (!ready) return
            val d = frame.cam.distanceTo(p); val k = clamp(1 - d / 1700, 0.0, 1.0)
            if (k <= 0) return
            post { it.explode(it.now + d / 900, k, s) }
        }
        override fun hit() {
            if (!ready || frame.gameT - hitT < 0.05) return
            hitT = frame.gameT
            post { s -> val t = s.now; val v = s.osc(TRI, 1900.0, t, false, 0.05); s.env(v, t, 0.001, 0.05, 0.01, 0.02) }
        }
        override fun hurt() {
            if (!ready || frame.gameT - hurtT < 0.08) return
            hurtT = frame.gameT
            post { s -> s.noiseBurst(s.now, LP, 1500.0, 200.0, 0.25, 0.3, 0.8); s.thud(s.now, 0.15) }
        }
        override fun flare() = post { s -> for (i in 0 until 4) s.noiseBurst(s.now + i * 0.07, HP, 5000.0, 2500.0, 0.12, 0.12, 0.7) }
        override fun alarm(n: Int) = post { it.alarm(n) }
        override fun fail() = post { it.fail() }
        override fun warn(on: Boolean) {
            if (!ready || !on) return
            if (frame.gameT - warnT > 0.5) {
                warnT = frame.gameT
                post { s -> s.beep(s.now, 880.0, 0.12, 0.06); s.beep(s.now + 0.17, 660.0, 0.12, 0.06) }
            }
        }
    }

    // ------------------------------------------------------------------ DSP building blocks

    /** an AudioParam: setValueAtTime / linear & exponential ramps / setTargetAtTime, evaluated sample by sample */
    private class Param {
        private val type = IntArray(CAP)
        private val t = DoubleArray(CAP)
        private val v = DoubleArray(CAP)
        private val tau = DoubleArray(CAP)
        private var n = 0
        private var def = 0.0
        private var idx = 0
        private var last = -1
        var cur = 0.0; private set
        private var pos = 0.0
        private var mode = CONST
        private var inc = 0.0
        private var targ = 0.0
        private var k = 0.0
        private var prevT = 0.0
        private var prevV = 0.0

        fun reset(d: Double) { n = 0; def = d; cur = d; idx = 0; last = -1; mode = CONST }

        /** times and tau in samples */
        fun add(ty: Int, time: Double, value: Double, ta: Double = 0.0) {
            if (n == CAP) return
            var i = n
            while (i > 0 && t[i - 1] > time) { type[i] = type[i - 1]; t[i] = t[i - 1]; v[i] = v[i - 1]; tau[i] = tau[i - 1]; i-- }
            type[i] = ty; t[i] = time; v[i] = value; tau[i] = ta
            n++
        }

        fun begin(p: Double) { pos = p; idx = 0; last = -1; cur = def; prevT = p; prevV = def; mode = CONST; advance() }

        private fun advance() {
            while (idx < n && t[idx] <= pos) {
                when (type[idx]) {
                    TGT -> { prevT = t[idx]; prevV = cur; targ = v[idx]; k = exp(-1.0 / max(1e-9, tau[idx])) }
                    else -> { cur = v[idx]; prevT = t[idx]; prevV = cur }
                }
                last = idx; idx++
            }
            mode = CONST
            if (idx < n && (type[idx] == LIN || type[idx] == EXP)) {
                val t1 = t[idx]; val v1 = v[idx]; val span = t1 - prevT
                if (span <= 0) { cur = v1; return }
                val fr = ((pos - prevT) / span).coerceIn(0.0, 1.0)
                if (type[idx] == LIN) { cur = prevV + (v1 - prevV) * fr; inc = (v1 - prevV) / span; mode = LINM }
                else if ((prevV > 0 && v1 > 0) || (prevV < 0 && v1 < 0)) { val r = v1 / prevV; cur = prevV * r.pow(fr); inc = r.pow(1.0 / span); mode = EXPM }
                else cur = prevV
            } else if (last >= 0 && type[last] == TGT) mode = TGTM
        }

        /** value at the current sample, then step one sample */
        fun next(): Double {
            if (idx < n && t[idx] <= pos) advance()
            val o = cur
            when (mode) {
                LINM -> cur += inc
                EXPM -> cur *= inc
                TGTM -> cur = targ + (cur - targ) * k
            }
            pos += 1.0
            return o
        }

        companion object {
            const val CAP = 6
            const val SET = 0; const val LIN = 1; const val EXP = 2; const val TGT = 3
            const val CONST = 0; const val LINM = 1; const val EXPM = 2; const val TGTM = 3
        }
    }

    /** RBJ biquad with the WebAudio conventions (Q in dB for low/high-pass, linear for band-pass) */
    private class Biquad {
        var b0 = 1.0; var b1 = 0.0; var b2 = 0.0; var a1 = 0.0; var a2 = 0.0
        var z1 = 0.0; var z2 = 0.0
        var lastF = -1.0
        fun reset() { z1 = 0.0; z2 = 0.0; lastF = -1.0 }
        fun set(type: Int, f0: Double, q: Double, sr: Double) {
            lastF = f0
            val f = f0.coerceIn(1.0, sr * 0.4999)
            val w0 = 2 * PI * f / sr
            val cw = cos(w0); val sw = sin(w0)
            val alpha = if (type == BP) sw / (2 * max(q, 1e-4)) else sw / (2 * 10.0.pow(q / 20))
            val a0 = 1 + alpha
            when (type) {
                LP -> { b0 = (1 - cw) / 2; b1 = 1 - cw; b2 = b0 }
                HP -> { b0 = (1 + cw) / 2; b1 = -(1 + cw); b2 = b0 }
                else -> { b0 = alpha; b1 = 0.0; b2 = -alpha }
            }
            b0 /= a0; b1 /= a0; b2 /= a0
            a1 = -2 * cw / a0; a2 = (1 - alpha) / a0
        }
        fun run(x: Double): Double { val y = b0 * x + z1; z1 = b1 * x - a1 * y + z2; z2 = b2 * x - a2 * y; return y }
    }

    /** one source (oscillator or noise) → optional biquad → gain, into the music or sfx bus */
    private class Voice {
        var active = false
        var begun = false
        var src = SINE
        var phase = 0.0
        var detune = 1.0
        val freq = Param()
        var vib = false
        var vibInc = 0.0
        var vibPh = 0.0
        val vibAmp = Param()
        var nPos = 0
        var fType = NONE
        val fFreq = Param()
        var fQ = 1.0
        val bq = Biquad()
        var fCnt = 0
        val gain = Param()
        var mus = false
        var start = 0L
        var end = 0L
        var fade = 0
    }

    /**
     * Freeverb-style stereo network (8 damped combs + 4 all-passes per channel, 23-sample spread) tuned to the
     * decay of the web build's (1 - t/2.4 s)^3 noise IR, with its input gain calibrated so the energy gain matches
     * that IR after the browser's convolver normalization (−58 dB calibration, 44.1 kHz reference).
     */
    private class Reverb(sr: Int) {
        private val combs = Array(16) { i -> FloatArray(((CT[i % 8] + (if (i >= 8) 23 else 0)) * sr / 44100.0).toInt().coerceAtLeast(1)) }
        private val ci = IntArray(16)
        private val cs = DoubleArray(16)
        private val fb = DoubleArray(16) { 10.0.pow(-3.0 * combs[it].size / (RT60 * sr)) }
        private val aps = Array(8) { i -> FloatArray(((AT[i % 4] + (if (i >= 4) 23 else 0)) * sr / 44100.0).toInt().coerceAtLeast(1)) }
        private val ai = IntArray(8)
        var l = 0.0; var r = 0.0
        private var gain = 1.0
        private var dn = 1e-18

        init {
            var e = 0.0
            process(1.0)
            e += l * l
            for (i in 1 until sr * 3) { process(0.0); e += l * l }
            val target = 10.0.pow(-58.0 / 10) * 2.4 * 44100.0 * 44100.0 / sr
            gain = sqrt(target / e)
            clear()
        }

        private fun clear() {
            for (c in combs) c.fill(0f)
            for (a in aps) a.fill(0f)
            cs.fill(0.0)
        }

        fun process(x: Double) {
            dn = -dn
            val inp = x * gain + dn
            var ol = 0.0; var or = 0.0
            for (c in 0 until 8) {
                ol += comb(c, inp)
                or += comb(c + 8, inp)
            }
            for (a in 0 until 4) {
                ol = allpass(a, ol)
                or = allpass(a + 4, or)
            }
            l = ol; r = or
        }

        private fun comb(c: Int, inp: Double): Double {
            val buf = combs[c]; val i = ci[c]
            val y = buf[i].toDouble()
            cs[c] = y * (1 - DAMP) + cs[c] * DAMP
            buf[i] = (inp + cs[c] * fb[c]).toFloat()
            ci[c] = if (i + 1 >= buf.size) 0 else i + 1
            return y
        }

        private fun allpass(a: Int, inp: Double): Double {
            val buf = aps[a]; val i = ai[a]
            val b = buf[i].toDouble()
            buf[i] = (inp + b * 0.5).toFloat()
            ai[a] = if (i + 1 >= buf.size) 0 else i + 1
            return b - inp
        }

        companion object {
            val CT = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
            val AT = intArrayOf(556, 441, 341, 225)
            const val RT60 = 2.2
            const val DAMP = 0.15
        }
    }

    /**
     * DynamicsCompressorNode: threshold −16 dB, knee 30 dB, ratio 3, attack 3 ms, release 250 ms, 6 ms look-ahead,
     * Chromium's exponential knee curve and automatic makeup gain ((1 / fullRangeGain)^0.6).
     */
    private class Comp(sr: Int) {
        private val linT = db2lin(TH)
        private val kneeLin = db2lin(TH + KNEE)
        private val slope = 1.0 / RATIO
        private val k: Double
        private val kneeDb: Double
        private val yKneeDb: Double
        private val makeup: Double
        private val n = (0.006 * sr).roundToInt().coerceAtLeast(1)
        private val rl = DoubleArray(n)
        private val rr = DoubleArray(n)
        private var ri = 0
        private val atk = 1 - exp(-1.0 / (0.003 * sr))
        private val rel = 1 - exp(-1.0 / (0.25 * sr))
        private var g = 1.0
        private var want = 1.0
        private var peak = 0.0
        private var cnt = 0
        var l = 0.0; var r = 0.0

        init {
            var minK = 0.1; var maxK = 10000.0; var kk = 5.0
            for (i in 0 until 15) {
                val s = slopeAt(kneeLin, kk)
                if (s < slope) maxK = kk else minK = kk
                kk = sqrt(minK * maxK)
            }
            k = kk
            kneeDb = TH + KNEE
            yKneeDb = lin2db(knee(kneeLin, k))
            makeup = (1.0 / sat(1.0)).pow(0.6)
        }

        private fun knee(x: Double, k: Double) = if (x < linT) x else linT + (1 - exp(-k * (x - linT))) / k
        private fun slopeAt(x: Double, k: Double): Double {
            if (x < linT) return 1.0
            val x2 = x * 1.001
            return (lin2db(knee(x2, k)) - lin2db(knee(x, k))) / (lin2db(x2) - lin2db(x))
        }
        private fun sat(x: Double) = if (x < kneeLin) knee(x, k) else db2lin(yKneeDb + slope * (lin2db(x) - kneeDb))

        fun process(inL: Double, inR: Double) {
            val a = max(abs(inL), abs(inR))
            if (a > peak) peak = a
            if (++cnt >= 8) { cnt = 0; want = if (peak <= linT) 1.0 else sat(peak) / peak; peak = 0.0 }
            g += (want - g) * (if (want < g) atk else rel)
            val dl = rl[ri]; val dr = rr[ri]
            rl[ri] = inL; rr[ri] = inR
            ri = if (ri + 1 >= n) 0 else ri + 1
            val m = g * makeup
            l = dl * m; r = dr * m
        }

        companion object {
            const val TH = -16.0
            const val KNEE = 30.0
            const val RATIO = 3.0
            fun db2lin(d: Double) = 10.0.pow(d / 20)
            fun lin2db(x: Double) = 20 * log10(max(x, 1e-12))
        }
    }

    // ------------------------------------------------------------------ the synth (audio thread)
    private inner class Synth(val sr: Int) {
        val srD = sr.toDouble()
        val rnd = Random()
        val noise = FloatArray(sr * 2).also { for (i in it.indices) it[i] = rnd.nextFloat() * 2f - 1f }
        val pcm = ShortArray(BLOCK * 2)
        var pos = 0L
        var now = 0.0
        private val pool = Array(POOL) { Voice() }
        private val musBuf = DoubleArray(BLOCK)
        private val sfxBuf = DoubleArray(BLOCK)
        var liveCount = 0
        var maxLive = 0
        var nanCount = 0L
        var clipCount = 0L

        // scheduler
        private var step = 0
        private var next = 0.1
        private var prevLead = 0

        // beds
        private val tgt = DoubleArray(NP)
        private val cur = DoubleArray(NP)
        private val coef = DoubleArray(NP)
        var musT = 0.0
        private var musG = 0.0
        private val musC = 1 - exp(-1.0 / (0.4 * sr))
        private val musLp = Biquad().also { it.set(LP, 6000.0, 1.0, srD) }
        private val windBq = Biquad()
        private val hissBq = Biquad().also { it.set(HP, 3000.0, 0.5, srD) }
        private val wavesBq = Biquad().also { it.set(LP, 520.0, 0.4, srD) }
        private val rainBq = Biquad().also { it.set(BP, 2400.0, 0.4, srD) }
        private val flutBq = Biquad()
        private val engBq = Biquad().also { it.set(LP, 700.0, 1.0, srD) }
        private var bedIdx = 0
        private var lfoPh = 0.0
        private var sinkPh = 0.0
        private val engPh = DoubleArray(2)
        private val rev = Reverb(sr)
        private val comp = Comp(sr)

        init {
            cur[P_WIND_F] = 500.0; cur[P_LFO_F] = 19.0; cur[P_FLUT_F] = 1300.0
            cur[P_ENG_F0] = 60.0; cur[P_ENG_F1] = 61.5; cur[P_SINK_F] = 280.0
            System.arraycopy(cur, 0, tgt, 0, NP)
            for (i in 0 until NP) coef[i] = 1 - exp(-1.0 / (TAU[i] * sr))
            windBq.set(BP, cur[P_WIND_F], 0.7, srD)
            flutBq.set(BP, cur[P_FLUT_F], 1.2, srD)
            next = 0.1
        }

        // ---------------- block
        fun block(frames: Int) {
            now = pos / srD
            while (true) { val c = cmds.poll() ?: break; c(this) }
            synchronized(paramLock) { if (tgtDirty) { System.arraycopy(tgtShared, 0, tgt, 0, NP); tgtDirty = false } }
            schedule()
            java.util.Arrays.fill(musBuf, 0, frames, 0.0)
            java.util.Arrays.fill(sfxBuf, 0, frames, 0.0)
            var live = 0
            for (v in pool) if (v.active) { live++; renderVoice(v, frames) }
            liveCount = live
            if (live > maxLive) maxLive = live
            mix(frames)
            pos += frames
        }

        private fun renderVoice(v: Voice, frames: Int) {
            val a = max(0L, v.start - pos).toInt()
            if (a >= frames) return
            if (!v.begun) {
                v.begun = true
                val p0 = (pos + a).toDouble()
                v.freq.begin(p0); v.vibAmp.begin(p0); v.fFreq.begin(p0); v.gain.begin(p0)
                v.bq.reset(); v.fCnt = 0
            }
            val b = min(frames.toLong(), v.end - pos).toInt()
            val buf = if (v.mus) musBuf else sfxBuf
            val inv = 1.0 / srD
            val filt = v.fType != NONE
            val src = v.src
            for (i in a until b) {
                var x: Double
                if (src == NOISE) {
                    x = if (v.nPos < noise.size) noise[v.nPos++].toDouble() else 0.0
                } else {
                    var f = v.freq.next() * v.detune
                    if (v.vib) {
                        f += v.vibAmp.next() * sinTab(v.vibPh)
                        v.vibPh += v.vibInc; if (v.vibPh >= 1.0) v.vibPh -= 1.0
                    }
                    var dt = f * inv
                    if (dt < 0) dt = 0.0 else if (dt > 0.5) dt = 0.5
                    val p = v.phase
                    x = when (src) {
                        SINE -> sinTab(p)
                        TRI -> if (p < 0.25) 4 * p else if (p < 0.75) 2 - 4 * p else 4 * p - 4
                        SAW -> saw(p, dt)
                        else -> {
                            var q = p + 0.5; if (q >= 1.0) q -= 1.0
                            (if (p < 0.5) 1.0 else -1.0) + polyBlep(p, dt) - polyBlep(q, dt)
                        }
                    }
                    var np = p + dt; if (np >= 1.0) np -= 1.0
                    v.phase = np
                }
                if (filt) {
                    val ff = v.fFreq.next()
                    if (--v.fCnt <= 0) { v.fCnt = 32; if (ff != v.bq.lastF) v.bq.set(v.fType, ff, v.fQ, srD) }
                    x = v.bq.run(x)
                }
                x *= v.gain.next()
                if (v.fade > 0) {
                    x *= v.fade.toDouble() / FADE
                    if (--v.fade == 0) { v.active = false; buf[i] += x; return }
                }
                buf[i] += x
            }
            if (pos + frames >= v.end) v.active = false
        }

        private fun mix(frames: Int) {
            val c = cur; val tg = tgt; val k = coef
            // slow parameters: once per block
            val blk = frames.toDouble()
            for (i in SLOW_FIRST until NP) c[i] += (tg[i] - c[i]) * (1 - exp(-blk / (TAU[i] * srD)))
            if (abs(c[P_WIND_F] - windBq.lastF) > 0.5) windBq.set(BP, c[P_WIND_F], 0.7, srD)
            if (abs(c[P_FLUT_F] - flutBq.lastF) > 0.5) flutBq.set(BP, c[P_FLUT_F], 1.2, srD)
            val windOn = max(c[P_WIND_G], tg[P_WIND_G]) > 1e-6
            val hissOn = max(c[P_HISS_G], tg[P_HISS_G]) > 1e-6
            val wavesOn = max(abs(c[P_WAVES_G]), abs(tg[P_WAVES_G])) > 1e-6
            val rainOn = max(c[P_RAIN_G], tg[P_RAIN_G]) > 1e-6
            val flutOn = max(c[P_FLUT_LG], tg[P_FLUT_LG]) > 1e-6
            val engOn = max(c[P_ENG_G], tg[P_ENG_G]) > 1e-6
            val sinkOn = max(c[P_SINK_G], tg[P_SINK_G]) > 1e-6
            val lfoInc = c[P_LFO_F] / srD
            val sinkInc = c[P_SINK_F] / srD
            val e0 = c[P_ENG_F0] / srD; val e1 = c[P_ENG_F1] / srD
            val nz = noise
            for (i in 0 until frames) {
                for (j in 0 until SLOW_FIRST) c[j] += (tg[j] - c[j]) * k[j]
                val nb = nz[bedIdx].toDouble()
                if (++bedIdx >= nz.size) bedIdx = 0
                var bed = 0.0
                if (windOn) bed += windBq.run(nb) * c[P_WIND_G]
                if (hissOn) bed += hissBq.run(nb) * c[P_HISS_G]
                if (wavesOn) bed += wavesBq.run(nb) * c[P_WAVES_G]
                if (rainOn) bed += rainBq.run(nb) * c[P_RAIN_G]
                if (flutOn) bed += flutBq.run(nb) * c[P_FLUT_LG] * sinTab(lfoPh)
                lfoPh += lfoInc; if (lfoPh >= 1.0) lfoPh -= 1.0
                if (engOn) {
                    val s = saw(engPh[0], e0) + saw(engPh[1], e1)
                    bed += engBq.run(s) * c[P_ENG_G]
                }
                engPh[0] += e0; if (engPh[0] >= 1.0) engPh[0] -= 1.0
                engPh[1] += e1; if (engPh[1] >= 1.0) engPh[1] -= 1.0
                if (sinkOn) bed += sinTab(sinkPh) * c[P_SINK_G]
                sinkPh += sinkInc; if (sinkPh >= 1.0) sinkPh -= 1.0
                // buses
                musG += (musT - musG) * musC
                val m = musLp.run(musBuf[i] * musG)
                val s = sfxBuf[i] * 0.9
                rev.process((m + s) * 0.4)
                val dry = m + s + bed
                comp.process((dry + rev.l) * 0.85, (dry + rev.r) * 0.85)
                pcm[i * 2] = toPcm(comp.l)
                pcm[i * 2 + 1] = toPcm(comp.r)
            }
        }

        private fun toPcm(x: Double): Short {
            if (x.isNaN()) { nanCount++; return 0 }
            if (x > 1.0 || x < -1.0) clipCount++
            return (x * 32767.0).roundToInt().coerceIn(-32768, 32767).toShort()
        }

        // ---------------- voices
        private fun alloc(t: Double): Voice {
            var free: Voice? = null
            var live = 0
            var oldest: Voice? = null
            for (v in pool) {
                if (!v.active) { if (free == null) free = v }
                else if (v.fade == 0) { live++; if (oldest == null || v.start < oldest.start) oldest = v }
            }
            if (live >= MAXV && oldest != null) {
                if (oldest.start >= pos || !oldest.begun) oldest.active = false else oldest.fade = FADE
                if (free == null && !oldest.active) free = oldest
            }
            if (free == null) {
                var o = pool[0]
                for (v in pool) if (v.start < o.start) o = v
                free = o
            }
            val v = free
            v.active = true; v.begun = false; v.fade = 0
            v.phase = 0.0; v.detune = 1.0; v.vib = false; v.vibPh = 0.0; v.vibInc = 0.0
            v.freq.reset(440.0); v.vibAmp.reset(0.0); v.fFreq.reset(350.0); v.gain.reset(1.0)
            v.fType = NONE; v.fQ = 1.0; v.nPos = 0
            v.start = ceil(t * srD).toLong()
            v.end = Long.MAX_VALUE
            return v
        }

        private fun stopAt(v: Voice, t: Double) { v.end = min(v.end, ceil(t * srD).toLong()) }

        /** OscillatorNode → GainNode → bus, start(t), stop(t + dur + 2) */
        fun osc(type: Int, f: Double, t: Double, mus: Boolean, dur: Double = 1.0): Voice {
            val v = alloc(t)
            v.src = type; v.mus = mus
            v.freq.add(Param.SET, t * srD, f)
            stopAt(v, t + dur + 2)
            return v
        }

        /** AudioBufferSourceNode (the shared 2 s noise buffer) → filter → gain → bus */
        private fun noiseSrc(t: Double, offset: Double, stop: Double, mus: Boolean, fType: Int, f: Double, q: Double): Voice {
            val v = alloc(t)
            v.src = NOISE; v.mus = mus
            v.nPos = (offset * srD).toInt()
            v.fType = fType; v.fQ = q
            v.fFreq.reset(f)
            stopAt(v, stop)
            return v
        }

        private fun filter(v: Voice, type: Int, f: Double, q: Double = 1.0) { v.fType = type; v.fFreq.reset(f); v.fQ = q }

        fun env(v: Voice, t: Double, a: Double, peak: Double, dur: Double, rel: Double) {
            v.gain.add(Param.SET, t * srD, 0.0001)
            v.gain.add(Param.LIN, (t + a) * srD, peak)
            v.gain.add(Param.TGT, (t + a + dur) * srD, 0.0001, rel * srD)
            stopAt(v, t + a + dur + rel * 6)
        }

        private fun setF(v: Voice, t: Double, f: Double) = v.freq.add(Param.SET, t * srD, f)
        private fun linF(v: Voice, t: Double, f: Double) = v.freq.add(Param.LIN, t * srD, f)
        private fun expF(v: Voice, t: Double, f: Double) = v.freq.add(Param.EXP, t * srD, f)
        private fun tgtF(v: Voice, t: Double, f: Double, tau: Double) = v.freq.add(Param.TGT, t * srD, f, tau * srD)
        private fun vibrato(v: Voice, rate: Double) { v.vib = true; v.vibInc = rate / srD; v.vibPh = 0.0 }

        // ---------------- instruments (INSTR)
        private fun tranh(t: Double, m: Int, vel: Double = 0.5) {
            val o = osc(TRI, mtof(m), t, true, 1.4)
            if (rnd.nextDouble() < 0.25) { setF(o, t, mtof(m - 1)); linF(o, t + 0.09, mtof(m)) }
            env(o, t, 0.004, 0.16 * vel, 0.02, 0.35)
            val h = osc(SINE, mtof(m + 12), t, true, 0.6)
            env(h, t, 0.003, 0.05 * vel, 0.01, 0.15)
        }

        private fun bau(t: Double, m: Int, dur: Double, vel: Double = 0.5) {
            val o = osc(SINE, mtof(if (prevLead != 0) prevLead else m), t, true, dur + 0.6)
            tgtF(o, t, mtof(m), 0.04)
            vibrato(o, 5.4)
            o.vibAmp.add(Param.SET, t * srD, 0.0)
            o.vibAmp.add(Param.LIN, (t + dur * 0.8) * srD, mtof(m) * 0.012)
            if (rnd.nextDouble() < 0.3) tgtF(o, t + dur * 0.6, mtof(m + 2), 0.08)
            env(o, t, 0.05, 0.2 * vel, dur * 0.85, 0.25)
            val t2 = osc(TRI, mtof(m) * 2, t, true, dur)
            tgtF(t2, t, mtof(m) * 2, 0.04)
            env(t2, t, 0.05, 0.025 * vel, dur * 0.7, 0.2)
            prevLead = m
        }

        private fun sao(t: Double, m: Int, dur: Double, vel: Double = 0.5) {
            val o = osc(SINE, mtof(m), t, true, dur + 0.6)
            vibrato(o, 5.0)
            o.vibAmp.reset(mtof(m) * 0.006)
            env(o, t, 0.06, 0.15 * vel, dur * 0.8, 0.18)
            val n = noiseSrc(t, 0.0, t + dur + 1, true, BP, mtof(m) * 2, 3.0)
            env(n, t, 0.04, 0.04 * vel, dur * 0.6, 0.1)
        }

        private fun bass(t: Double, m: Int, dur: Double) {
            val g = osc(SINE, mtof(m), t, true, dur + 0.5)
            env(g, t, 0.01, 0.24, dur * 0.7, 0.2)
            val h = osc(TRI, mtof(m), t, true, dur)
            env(h, t, 0.01, 0.05, dur * 0.4, 0.1)
        }

        private fun mo(t: Double, vel: Double = 1.0) {
            val n = noiseSrc(t, 0.0, t + 0.2, true, BP, 1150.0, 9.0)
            env(n, t, 0.001, 0.5 * vel, 0.005, 0.03)
        }

        private fun hat(t: Double, vel: Double = 1.0) {
            val n = noiseSrc(t, 0.0, t + 0.15, true, HP, 7000.0, 1.0)
            env(n, t, 0.001, 0.05 * vel, 0.005, 0.025)
        }

        private fun kick(t: Double) {
            val o = osc(SINE, 120.0, t, true, 0.4)
            expF(o, t + 0.18, 45.0)
            env(o, t, 0.002, 0.35, 0.03, 0.08)
        }

        private fun pad(t: Double, m: Int, dur: Double) {
            for (d in intArrayOf(0, 7, 12)) {
                val o = osc(SAW, mtof(m + d), t, true, dur)
                o.detune = 2.0.pow((rnd.nextDouble() - 0.5) * 14 / 1200)
                filter(o, LP, 900.0)
                env(o, t, dur * 0.3, 0.018, dur * 0.4, dur * 0.3)
            }
        }

        private fun schedule() {
            val sd = SONGDATA[song]
            val sp = 60.0 / sd.bpm / 4
            while (next < now + 0.12) {
                val t = next; val s = step % 16; val b = bar % sd.form.size
                if (on) {
                    val ph = sd.form[b]; val root = sd.prog[b]
                    if (s == 0) { bass(t, root, sp * 7); pad(t, root + 12, sp * 16); if (sd.bpm > 80) kick(t) }
                    if (s == 8) { bass(t, root + 7, sp * 6); if (sd.bpm > 105) kick(t) }
                    if (s % 4 == 2) mo(t, 0.6 + (if (s == 10) 0.4 else 0.0))
                    if (sd.bpm > 85 && s % 2 == 1) hat(t, 0.6)
                    if (s % 2 == 0) tranh(t, sd.deg(ARP[(s / 2) % 8] + 5), 0.45)
                    val n = ph[s]
                    if (n != null) {
                        val dur = n.len * sp * 1.9
                        when (sd.lead) {
                            "bau" -> bau(t, n.m, dur, 0.7)
                            "sao" -> sao(t, n.m + 12, dur, 0.7)
                            else -> tranh(t, n.m + 12, 0.85)
                        }
                    }
                }
                next += sp
                step++
                if (step % 16 == 0) { bar++; if (bar >= sd.form.size * 4) nextSongA() }
            }
        }

        fun setSongA(i: Int) { song = i; bar = 0; step = 0 }

        fun nextSongA() {
            song = (song + 1) % SONGS.size; bar = 0; step = 0
            if (on) songMsgs.add(SONGS[song].n)
        }

        // ---------------- sound effects
        fun noiseBurst(t: Double, type: Int, f0: Double, f1: Double, dur: Double, peak: Double, q: Double = 1.0, mus: Boolean = false) {
            val v = noiseSrc(t, rnd.nextDouble(), t + dur + 0.5, mus, type, f0, q)
            v.fFreq.add(Param.SET, t * srD, f0)
            v.fFreq.add(Param.EXP, (t + dur) * srD, max(20.0, f1))
            env(v, t, 0.005, peak, dur * 0.4, dur * 0.3)
        }

        fun fold() { val t = now; noiseBurst(t, BP, 2400.0, 900.0, 0.18, 0.25, 2.0); noiseBurst(t + 0.08, BP, 3200.0, 1400.0, 0.12, 0.18, 2.0) }

        fun crumple() {
            val t = now
            for (i in 0 until 9) noiseBurst(t + i * 0.045 + rnd.nextDouble() * 0.03, BP, 2600 + rnd.nextDouble() * 2000, 900.0, 0.07, 0.3, 3.0)
            thud(t, 0.5)
        }

        fun thud(t: Double, vel: Double) {
            val o = osc(SINE, 140.0, t, false, 0.4)
            expF(o, t + 0.25, 40.0)
            env(o, t, 0.005, vel, 0.05, 0.1)
        }

        fun chime(n: Int) {
            val t = now
            for (i in 0 until n) {
                val o = osc(SINE, mtof(74 + BAC[i % 5] + 12 * (i / 5)), t + i * 0.07, false, 0.6)
                env(o, t + i * 0.07, 0.004, 0.08, 0.05, 0.25)
            }
        }

        fun ring(i: Int) {
            val t = now
            val ii = max(0, i)
            val m = 72 + BAC[ii % 5] + 12 * ((ii / 5) % 2)
            for ((d, dt) in RING) {
                val o = osc(TRI, mtof(m + d), t + dt, false, 0.8)
                env(o, t + dt, 0.003, 0.12, 0.05, 0.3)
            }
        }

        fun stamp() {
            val t = now
            thud(t, 0.35)
            for (j in 0 until 3) {
                val o = osc(SINE, STAMP_F[j], t + 0.05, false, 2.5)
                env(o, t + 0.05, 0.002, 0.09 * STAMP_K[j], 0.05, 0.9)
            }
        }

        fun nest() {
            val t = now
            for ((i, d) in NEST.withIndex()) { val o = osc(SINE, mtof(81 + d), t + i * 0.05, false, 0.5); env(o, t + i * 0.05, 0.003, 0.07, 0.03, 0.2) }
        }

        fun fanfare() {
            val t = now
            for ((i, d) in FANFARE.withIndex()) { val o = osc(TRI, mtof(67 + d), t + i * 0.11, false, 0.8); env(o, t + i * 0.11, 0.005, 0.12, 0.08, 0.3) }
        }

        fun boom(t: Double, k: Double) {
            noiseBurst(t, LP, 500.0, 60.0, 1.2, 0.55 * k, 0.8)
            for (i in 0 until 6) noiseBurst(t + 0.3 + rnd.nextDouble() * 0.6, HP, 4000.0, 3000.0, 0.05, 0.1 * k)
        }

        fun bell(k: Double) {
            val t = now
            for (j in 0 until 5) { val o = osc(SINE, BELL_F[j], t, false, 5.0); env(o, t, 0.01, 0.1 * BELL_A[j] * k, 0.1, 1.6) }
        }

        fun gull() {
            val t = now
            for (i in 0 until 3) {
                val ti = t + i * 0.22
                val o = osc(SAW, 1500.0, ti, false, 0.4)
                setF(o, ti, 1700.0); expF(o, ti + 0.18, 900.0)
                filter(o, BP, 1500.0, 4.0)
                env(o, ti, 0.01, 0.035, 0.08, 0.05)
            }
        }

        fun beep(t: Double, f: Double, len: Double, vel: Double) { val o = osc(SINE, f, t, false, len + 0.1); env(o, t, 0.003, vel, len, 0.01) }

        fun gun() {
            val t = now
            noiseBurst(t, BP, 2600.0, 700.0, 0.06, 0.13, 1.1)
            val o = osc(SQR, 120.0, t, false, 0.1)
            expF(o, t + 0.06, 48.0)
            env(o, t, 0.002, 0.045, 0.02, 0.03)
        }

        fun explode(t: Double, k: Double, s: Double) {
            noiseBurst(t, LP, 1000.0, 50.0, 0.8 + s * 0.3, 0.5 * k * min(1.4, s), 0.7)
            val o = osc(SINE, 95.0, t, false, 0.7)
            expF(o, t + 0.5, 30.0)
            env(o, t, 0.005, 0.35 * k, 0.1, 0.25)
        }

        fun alarm(n: Int) {
            val t = now
            for (i in 0 until n) {
                val ti = t + i * 0.32
                val o = osc(SAW, 520.0, ti, false, 0.3)
                linF(o, ti + 0.25, 780.0)
                filter(o, LP, 1800.0)
                env(o, ti, 0.01, 0.05, 0.2, 0.05)
            }
        }

        fun fail() {
            val t = now
            for ((i, d) in FAIL.withIndex()) { val o = osc(TRI, mtof(62 + d), t + i * 0.22, false, 0.8); env(o, t + i * 0.22, 0.005, 0.11, 0.12, 0.3) }
        }
    }
}

// bed parameter slots (fast = smoothed per sample, slow = per block), with their setTargetAtTime constants
private const val P_WIND_G = 0
private const val P_HISS_G = 1
private const val P_WAVES_G = 2
private const val P_RAIN_G = 3
private const val P_FLUT_LG = 4
private const val P_ENG_G = 5
private const val P_SINK_G = 6
private const val SLOW_FIRST = 7
private const val P_WIND_F = 7
private const val P_LFO_F = 8
private const val P_FLUT_F = 9
private const val P_ENG_F0 = 10
private const val P_ENG_F1 = 11
private const val P_SINK_F = 12
private const val NP = 13
private val TAU = doubleArrayOf(0.15, 0.15, 0.3, 0.3, 0.1, 0.15, 0.1, 0.1, 0.2, 0.2, 0.2, 0.2, 0.2)

private val RING = listOf(0 to 0.0, 7 to 0.06, 12 to 0.12)
private val STAMP_F = doubleArrayOf(880.0, 1320.0, 2211.0)
private val STAMP_K = doubleArrayOf(1.0, 0.5, 0.3)
private val NEST = intArrayOf(0, 4, 7, 12, 16, 19, 24)
private val FANFARE = intArrayOf(0, 2, 5, 7, 9, 12, 9, 12)
private val FAIL = intArrayOf(7, 5, 3, 0, -5)
private val BELL_F = doubleArrayOf(196.0, 392.0, 523.0, 784.0, 1046.0)
private val BELL_A = doubleArrayOf(1.0, 0.5, 0.35, 0.2, 0.12)
