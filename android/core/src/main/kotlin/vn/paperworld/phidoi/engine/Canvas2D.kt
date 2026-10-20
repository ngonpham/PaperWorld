package vn.paperworld.phidoi.engine

/** the typefaces of the web game */
enum class Face { UI500, UI700, UI800, HAND, BRUSH, HAN }

object Align { const val LEFT = 0; const val CENTER = 1; const val RIGHT = 2 }
object Baseline { const val ALPHABETIC = 0; const val MIDDLE = 1; const val TOP = 2 }

/** an image the 2D layer can draw (platform bitmap behind it) */
interface Image2D { val width: Int; val height: Int }

/**
 * The slice of the HTML canvas 2D API the game uses. Colours are ARGB ints.
 * Implemented over android.graphics.Canvas on Android and java.awt.Graphics2D on the desktop harness.
 */
interface Canvas2D {
    fun save()
    fun restore()
    fun translate(x: Float, y: Float)
    fun rotate(a: Float)
    fun scale(sx: Float, sy: Float)
    fun setAlpha(a: Float)
    fun setFill(argb: Int)
    fun setStroke(argb: Int)
    fun setLineWidth(w: Float)
    fun setLineDash(on: Float, off: Float)
    fun setFillLinear(x0: Float, y0: Float, x1: Float, y1: Float, stops: FloatArray, colors: IntArray)
    fun setFillRadial(cx: Float, cy: Float, r: Float, stops: FloatArray, colors: IntArray)
    fun beginPath()
    fun moveTo(x: Float, y: Float)
    fun lineTo(x: Float, y: Float)
    fun arc(x: Float, y: Float, r: Float, a0: Float, a1: Float)
    fun rect(x: Float, y: Float, w: Float, h: Float)
    fun closePath()
    fun fill()
    fun stroke()
    fun clip()
    fun fillRect(x: Float, y: Float, w: Float, h: Float)
    fun strokeRect(x: Float, y: Float, w: Float, h: Float)
    fun clearRect(x: Float, y: Float, w: Float, h: Float)
    fun setFont(face: Face, size: Float)
    fun setTextAlign(a: Int)
    fun setTextBaseline(b: Int)
    fun fillText(t: String, x: Float, y: Float)
    fun strokeText(t: String, x: Float, y: Float)
    fun measureText(t: String): Float
    fun drawImage(img: Image2D, x: Float, y: Float, w: Float, h: Float)
}

/** a canvas backed by pixels, for painting textures */
interface BitmapCanvas : Canvas2D {
    val width: Int
    val height: Int
    fun pixels(): IntArray
}

/** what the platform provides to core code */
interface Platform2D {
    fun bitmap(w: Int, h: Int): BitmapCanvas
    fun image(pixels: IntArray, w: Int, h: Int): Image2D
    /** text measuring without a canvas (layout) */
    fun measure(face: Face, size: Float, t: String): Float
}

fun argb(hex: String, a: Double = 1.0): Int { val h = Color.parseHex(hex); return ((clamp(a, 0.0, 1.0) * 255).toInt() shl 24) or h }
fun rgba(r: Int, g: Int, b: Int, a: Double) = ((clamp(a, 0.0, 1.0) * 255).toInt() shl 24) or (r shl 16) or (g shl 8) or b

/**
 * Records draw calls on the game thread and replays them on the UI thread (Android) or into an image (desktop).
 * Two instances are double-buffered by the host.
 */
class RecordingCanvas(private val measurer: Platform2D) : Canvas2D {
    private var ops = IntArray(4096); private var on = 0
    private var fs = FloatArray(16384); private var fn = 0
    private val objs = ArrayList<Any>(256)
    private var curFace = Face.UI500; private var curSize = 12f

    fun reset() { on = 0; fn = 0; objs.clear() }
    private fun op(o: Int) { if (on == ops.size) ops = ops.copyOf(ops.size * 2); ops[on++] = o }
    private fun f(v: Float) { if (fn == fs.size) fs = fs.copyOf(fs.size * 2); fs[fn++] = v }
    private fun ib(v: Int) = f(java.lang.Float.intBitsToFloat(v))

    override fun save() = op(0)
    override fun restore() = op(1)
    override fun translate(x: Float, y: Float) { op(2); f(x); f(y) }
    override fun rotate(a: Float) { op(3); f(a) }
    override fun scale(sx: Float, sy: Float) { op(4); f(sx); f(sy) }
    override fun setAlpha(a: Float) { op(5); f(a) }
    override fun setFill(argb: Int) { op(6); ib(argb) }
    override fun setStroke(argb: Int) { op(7); ib(argb) }
    override fun setLineWidth(w: Float) { op(8); f(w) }
    override fun setLineDash(on: Float, off: Float) { op(9); f(on); f(off) }
    override fun setFillLinear(x0: Float, y0: Float, x1: Float, y1: Float, stops: FloatArray, colors: IntArray) { op(10); f(x0); f(y0); f(x1); f(y1); objs.add(stops.copyOf()); objs.add(colors.copyOf()) }
    override fun setFillRadial(cx: Float, cy: Float, r: Float, stops: FloatArray, colors: IntArray) { op(11); f(cx); f(cy); f(r); objs.add(stops.copyOf()); objs.add(colors.copyOf()) }
    override fun beginPath() = op(12)
    override fun moveTo(x: Float, y: Float) { op(13); f(x); f(y) }
    override fun lineTo(x: Float, y: Float) { op(14); f(x); f(y) }
    override fun arc(x: Float, y: Float, r: Float, a0: Float, a1: Float) { op(15); f(x); f(y); f(r); f(a0); f(a1) }
    override fun rect(x: Float, y: Float, w: Float, h: Float) { op(16); f(x); f(y); f(w); f(h) }
    override fun closePath() = op(17)
    override fun fill() = op(18)
    override fun stroke() = op(19)
    override fun clip() = op(20)
    override fun fillRect(x: Float, y: Float, w: Float, h: Float) { op(21); f(x); f(y); f(w); f(h) }
    override fun strokeRect(x: Float, y: Float, w: Float, h: Float) { op(22); f(x); f(y); f(w); f(h) }
    override fun clearRect(x: Float, y: Float, w: Float, h: Float) { op(23); f(x); f(y); f(w); f(h) }
    override fun setFont(face: Face, size: Float) { curFace = face; curSize = size; op(24); ib(face.ordinal); f(size) }
    override fun setTextAlign(a: Int) { op(25); ib(a) }
    override fun setTextBaseline(b: Int) { op(26); ib(b) }
    override fun fillText(t: String, x: Float, y: Float) { op(27); objs.add(t); f(x); f(y) }
    override fun strokeText(t: String, x: Float, y: Float) { op(28); objs.add(t); f(x); f(y) }
    override fun measureText(t: String) = measurer.measure(curFace, curSize, t)
    override fun drawImage(img: Image2D, x: Float, y: Float, w: Float, h: Float) { op(29); objs.add(img); f(x); f(y); f(w); f(h) }

    fun replay(c: Canvas2D) {
        var fi = 0; var oi = 0
        fun F() = fs[fi++]
        fun I() = java.lang.Float.floatToRawIntBits(fs[fi++])
        for (k in 0 until on) {
            when (ops[k]) {
                0 -> c.save(); 1 -> c.restore()
                2 -> c.translate(F(), F()); 3 -> c.rotate(F()); 4 -> c.scale(F(), F())
                5 -> c.setAlpha(F()); 6 -> c.setFill(I()); 7 -> c.setStroke(I()); 8 -> c.setLineWidth(F()); 9 -> c.setLineDash(F(), F())
                10 -> { val a = F(); val b = F(); val d = F(); val e = F(); c.setFillLinear(a, b, d, e, objs[oi++] as FloatArray, objs[oi++] as IntArray) }
                11 -> { val a = F(); val b = F(); val r = F(); c.setFillRadial(a, b, r, objs[oi++] as FloatArray, objs[oi++] as IntArray) }
                12 -> c.beginPath(); 13 -> c.moveTo(F(), F()); 14 -> c.lineTo(F(), F())
                15 -> c.arc(F(), F(), F(), F(), F()); 16 -> c.rect(F(), F(), F(), F()); 17 -> c.closePath()
                18 -> c.fill(); 19 -> c.stroke(); 20 -> c.clip()
                21 -> c.fillRect(F(), F(), F(), F()); 22 -> c.strokeRect(F(), F(), F(), F()); 23 -> c.clearRect(F(), F(), F(), F())
                24 -> { val face = Face.entries[I()]; c.setFont(face, F()) }
                25 -> c.setTextAlign(I()); 26 -> c.setTextBaseline(I())
                27 -> { val t = objs[oi++] as String; c.fillText(t, F(), F()) }
                28 -> { val t = objs[oi++] as String; c.strokeText(t, F(), F()) }
                29 -> { val img = objs[oi++] as Image2D; c.drawImage(img, F(), F(), F(), F()) }
            }
        }
    }
}

/** paint a texture with the platform canvas */
fun paintTexture(p: Platform2D, w: Int, h: Int, repeat: Boolean = true, draw: (Canvas2D, Int, Int) -> Unit): Texture {
    val c = p.bitmap(w, h)
    draw(c, w, h)
    val t = Texture(w, h)
    t.pixels = c.pixels()
    t.repeat = repeat
    return t
}
