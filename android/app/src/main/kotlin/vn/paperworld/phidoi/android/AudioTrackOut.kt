package vn.paperworld.phidoi.android

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import vn.paperworld.phidoi.game.PcmOut

/** 16-bit stereo streaming AudioTrack (USAGE_GAME, low-latency) at the device's native output rate */
class AudioTrackOut private constructor(private val track: AudioTrack, override val sampleRate: Int) : PcmOut {
    private var prioritySet = false

    override fun write(buf: ShortArray, frames: Int) {
        if (!prioritySet) {
            // first write comes from the synth thread: give it audio priority
            prioritySet = true
            try { Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO) } catch (_: Exception) {}
        }
        var off = 0
        val n = frames * 2
        while (off < n) {
            val w = track.write(buf, off, n - off, AudioTrack.WRITE_BLOCKING)
            if (w <= 0) return
            off += w
        }
    }

    override fun close() {
        try { track.pause(); track.flush(); track.stop() } catch (_: Exception) {}
        track.release()
    }

    companion object {
        /** null when the device refuses to open an output stream */
        fun create(): AudioTrackOut? = try {
            var rate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC)
            if (rate !in 22050..96000) rate = 48000
            val enc = AudioFormat.ENCODING_PCM_16BIT
            val ch = AudioFormat.CHANNEL_OUT_STEREO
            val min = AudioTrack.getMinBufferSize(rate, ch, enc)
            val size = if (min > 0) min * 3 else rate / 10 * 4
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(AudioFormat.Builder().setEncoding(enc).setSampleRate(rate).setChannelMask(ch).build())
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(size)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
            if (track.state != AudioTrack.STATE_INITIALIZED) { track.release(); null }
            else { track.play(); AudioTrackOut(track, rate) }
        } catch (_: Exception) {
            null
        }
    }
}
