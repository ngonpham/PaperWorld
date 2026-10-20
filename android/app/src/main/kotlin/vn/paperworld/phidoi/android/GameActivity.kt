package vn.paperworld.phidoi.android

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import vn.paperworld.phidoi.engine.RecordingCanvas
import vn.paperworld.phidoi.game.Audio
import vn.paperworld.phidoi.game.GameHost
import vn.paperworld.phidoi.game.HostEvents
import vn.paperworld.phidoi.game.Store
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class PrefsStore(ctx: Context) : Store {
    private val p = ctx.getSharedPreferences("pdgdn", Context.MODE_PRIVATE)
    override fun getString(k: String): String? = p.getString(k, null)
    override fun putString(k: String, v: String) { p.edit().putString(k, v).apply() }
}

/**
 * Phi Đội Giấy on Android: the game runs on the GL thread (GLSurfaceView renderer), the HUD is recorded there into a
 * [RecordingCanvas] and replayed by [HudView] on the UI thread. Touches are queued to the game thread.
 */
class GameActivity : Activity() {
    private lateinit var glView: GLSurfaceView
    private lateinit var hud: HudView
    private lateinit var host: GameHost
    private lateinit var audio: Audio

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 28) window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        val plat = AndroidPlatform(assets)
        val store = PrefsStore(this)
        audio = Audio { AudioTrackOut.create() }
        val vib = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        val events = object : HostEvents {
            override fun vibrate(ms: Int) {
                if (vib == null) return
                if (Build.VERSION.SDK_INT >= 26) vib.vibrate(VibrationEffect.createOneShot(ms.toLong(), VibrationEffect.DEFAULT_AMPLITUDE)) else @Suppress("DEPRECATION") vib.vibrate(ms.toLong())
            }
            override fun quit() { runOnUiThread { finish() } }
        }
        host = GameHost(plat, store, audio, events, touch = true, density = resources.displayMetrics.density.toDouble())
        glView = GLSurfaceView(this)
        glView.setEGLContextClientVersion(3)
        glView.setEGLConfigChooser(8, 8, 8, 8, 0, 0)
        glView.preserveEGLContextOnPause = true
        hud = HudView(this, host)
        glView.setRenderer(object : GLSurfaceView.Renderer {
            private var last = 0L
            private val gl = AndroidGL()
            override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) { host.onSurfaceCreated(gl) }
            override fun onSurfaceChanged(unused: GL10?, w: Int, h: Int) { host.onSurfaceChanged(w, h) }
            override fun onDrawFrame(unused: GL10?) {
                val now = System.nanoTime()
                val dt = if (last == 0L) 0.016 else (now - last) / 1e9
                last = now
                val rec = hud.back()
                host.drawFrame(dt, rec, hud.width, hud.height)
                hud.publish()
            }
        })
        glView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        val root = FrameLayout(this)
        root.addView(glView)
        root.addView(hud)
        setContentView(root)
        immersive()
    }

    private fun immersive() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        }
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) immersive() }
    override fun onPause() { super.onPause(); glView.onPause(); host.queue { it.pause(true) }; audio.pause(true) }
    override fun onResume() { super.onResume(); glView.onResume(); audio.pause(false) }
    override fun onDestroy() { super.onDestroy(); audio.release() }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { host.queue { it.back() } }
    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        val name = keyName(e.keyCode) ?: return super.dispatchKeyEvent(e)
        val down = e.action == KeyEvent.ACTION_DOWN
        if (e.action == KeyEvent.ACTION_DOWN || e.action == KeyEvent.ACTION_UP) host.queue { it.key(name, down, e.repeatCount > 0) }
        return true
    }
    private fun keyName(c: Int): String? = when (c) {
        in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> ('a' + (c - KeyEvent.KEYCODE_A)).toString()
        KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_BUTTON_R2 -> " "
        KeyEvent.KEYCODE_DPAD_UP -> "arrowup"; KeyEvent.KEYCODE_DPAD_DOWN -> "arrowdown"; KeyEvent.KEYCODE_DPAD_LEFT -> "arrowleft"; KeyEvent.KEYCODE_DPAD_RIGHT -> "arrowright"
        KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT, KeyEvent.KEYCODE_BUTTON_A -> "shift"
        KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> "control"
        KeyEvent.KEYCODE_TAB -> "tab"; KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BUTTON_START -> "enter"; KeyEvent.KEYCODE_ESCAPE -> "escape"
        KeyEvent.KEYCODE_EQUALS, KeyEvent.KEYCODE_PLUS -> "+"; KeyEvent.KEYCODE_MINUS -> "-"
        else -> null
    }
}

/** replays the HUD recorded on the GL thread; forwards touches to the game thread */
@SuppressLint("ViewConstructor")
class HudView(ctx: Context, private val host: GameHost) : View(ctx) {
    private val recs = arrayOf(RecordingCanvas(host.plat), RecordingCanvas(host.plat), RecordingCanvas(host.plat))
    // triple buffer: the GL thread writes [w], the UI thread reads [r], [ready] is the newest complete one
    private var w = 0; private var ready = -1; private var r = -1
    private val lock = Any()
    private val canvas2d = AndroidCanvas(Canvas())

    fun back(): RecordingCanvas { synchronized(lock) { var i = 0; while (i == ready || i == r) i++; w = i }; recs[w].reset(); return recs[w] }
    fun publish() { synchronized(lock) { ready = w }; postInvalidateOnAnimation() }

    override fun onDraw(c: Canvas) {
        synchronized(lock) { if (ready >= 0) { r = ready; ready = -1 } }
        if (r < 0) return
        canvas2d.c = c
        canvas2d.reset()
        recs[r].replay(canvas2d)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val act = e.actionMasked
        when (act) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> { val i = e.actionIndex; val id = e.getPointerId(i); val x = e.getX(i).toDouble(); val y = e.getY(i).toDouble(); host.queue { it.pointer(0, id, x, y) } }
            MotionEvent.ACTION_MOVE -> for (i in 0 until e.pointerCount) { val id = e.getPointerId(i); val x = e.getX(i).toDouble(); val y = e.getY(i).toDouble(); host.queue { it.pointer(1, id, x, y) } }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> { val i = e.actionIndex; val id = e.getPointerId(i); val x = e.getX(i).toDouble(); val y = e.getY(i).toDouble(); host.queue { it.pointer(2, id, x, y) } }
            MotionEvent.ACTION_CANCEL -> for (i in 0 until e.pointerCount) { val id = e.getPointerId(i); host.queue { it.pointer(3, id, 0.0, 0.0) } }
        }
        return true
    }
}
