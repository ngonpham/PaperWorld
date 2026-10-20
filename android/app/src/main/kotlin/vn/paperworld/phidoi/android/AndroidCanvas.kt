package vn.paperworld.phidoi.android

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import vn.paperworld.phidoi.engine.*
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

object AndroidFonts {
    private val faces = HashMap<Face, Typeface>()
    lateinit var assets: AssetManager
    fun face(f: Face): Typeface = faces.getOrPut(f) {
        val file = when (f) {
            Face.UI500 -> "BeVietnamPro-Medium.ttf"; Face.UI700 -> "BeVietnamPro-Bold.ttf"; Face.UI800 -> "BeVietnamPro-ExtraBold.ttf"
            Face.HAND -> "PatrickHand-Regular.ttf"; Face.BRUSH -> "Pacifico-Regular.ttf"; Face.HAN -> ""
        }
        if (file.isEmpty()) Typeface.create(Typeface.SERIF, Typeface.BOLD) else Typeface.createFromAsset(assets, "fonts/$file")
    }
}

class AndroidImage(val bmp: Bitmap) : Image2D { override val width get() = bmp.width; override val height get() = bmp.height }

/** [Canvas2D] over android.graphics.Canvas — the HUD view replays recordings into it, textures paint through it */
open class AndroidCanvas(var c: Canvas) : Canvas2D {
    private class St(val alpha: Float, val fill: Int, val fillShader: Shader?, val stroke: Int, val lw: Float, val dash: DashPathEffect?, val face: Face, val size: Float, val align: Int, val base: Int)
    private val stack = ArrayList<St>()
    private var alpha = 1f
    private var fill = 0xff000000.toInt()
    private var fillShader: Shader? = null
    private var stroke = 0xff000000.toInt()
    private var lw = 1f
    private var dash: DashPathEffect? = null
    private var face = Face.UI500
    private var size = 12f
    private var align = Align.LEFT
    private var baseline = Baseline.ALPHABETIC
    private val path = Path()
    private var hasPoint = false
    private val fp = Paint(Paint.ANTI_ALIAS_FLAG).also { it.style = Paint.Style.FILL; it.isFilterBitmap = true }
    private val sp = Paint(Paint.ANTI_ALIAS_FLAG).also { it.style = Paint.Style.STROKE; it.strokeCap = Paint.Cap.ROUND; it.strokeJoin = Paint.Join.ROUND }
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).also { it.isSubpixelText = true }
    private val clearP = Paint().also { it.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
    private val rf = RectF()

    fun reset() { stack.clear(); alpha = 1f; fill = 0xff000000.toInt(); fillShader = null; stroke = fill; lw = 1f; dash = null; align = Align.LEFT; baseline = Baseline.ALPHABETIC; path.reset(); hasPoint = false }

    private fun a(argb: Int): Int { val al = ((argb ushr 24) * alpha.coerceIn(0f, 1f)).toInt(); return (al shl 24) or (argb and 0xffffff) }
    private fun fillPaint(): Paint {
        fp.shader = fillShader
        if (fillShader != null) { fp.color = 0xff000000.toInt(); fp.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt() } else fp.color = a(fill)
        return fp
    }
    private fun strokePaint(): Paint { sp.color = a(stroke); sp.strokeWidth = lw; sp.pathEffect = dash; return sp }
    private fun textPaint(fillMode: Boolean): Paint {
        tp.typeface = AndroidFonts.face(face); tp.textSize = size
        tp.textAlign = when (align) { Align.CENTER -> Paint.Align.CENTER; Align.RIGHT -> Paint.Align.RIGHT; else -> Paint.Align.LEFT }
        if (fillMode) { tp.style = Paint.Style.FILL; tp.shader = fillShader; tp.color = if (fillShader != null) a(0xff000000.toInt()) else a(fill) }
        else { tp.style = Paint.Style.STROKE; tp.shader = null; tp.color = a(stroke); tp.strokeWidth = lw; tp.strokeJoin = Paint.Join.ROUND }
        return tp
    }
    private fun dy(p: Paint): Float {
        val fm = p.fontMetrics
        return when (baseline) { Baseline.MIDDLE -> -(fm.ascent + fm.descent) / 2f; Baseline.TOP -> -fm.ascent; else -> 0f }
    }

    override fun save() { stack.add(St(alpha, fill, fillShader, stroke, lw, dash, face, size, align, baseline)); c.save() }
    override fun restore() {
        val s = stack.removeLastOrNull() ?: return
        alpha = s.alpha; fill = s.fill; fillShader = s.fillShader; stroke = s.stroke; lw = s.lw; dash = s.dash; face = s.face; size = s.size; align = s.align; baseline = s.base
        c.restore()
    }
    override fun translate(x: Float, y: Float) = c.translate(x, y)
    override fun rotate(a: Float) = c.rotate(Math.toDegrees(a.toDouble()).toFloat())
    override fun scale(sx: Float, sy: Float) = c.scale(sx, sy)
    override fun setAlpha(a: Float) { alpha = a }
    override fun setFill(argb: Int) { fill = argb; fillShader = null }
    override fun setStroke(argb: Int) { stroke = argb }
    override fun setLineWidth(w: Float) { lw = w }
    override fun setLineDash(on: Float, off: Float) { dash = if (on <= 0f) null else DashPathEffect(floatArrayOf(on, off), 0f) }
    override fun setFillLinear(x0: Float, y0: Float, x1: Float, y1: Float, stops: FloatArray, colors: IntArray) {
        fillShader = if (x0 == x1 && y0 == y1) null.also { fill = colors[0] } else LinearGradient(x0, y0, x1, y1, colors, stops, Shader.TileMode.CLAMP)
    }
    override fun setFillRadial(cx: Float, cy: Float, r: Float, stops: FloatArray, colors: IntArray) {
        fillShader = RadialGradient(cx, cy, maxOf(r, 0.001f), colors, stops, Shader.TileMode.CLAMP)
    }
    override fun beginPath() { path.reset(); hasPoint = false }
    override fun moveTo(x: Float, y: Float) { path.moveTo(x, y); hasPoint = true }
    override fun lineTo(x: Float, y: Float) { if (hasPoint) path.lineTo(x, y) else path.moveTo(x, y); hasPoint = true }
    override fun arc(x: Float, y: Float, r: Float, a0: Float, a1: Float) {
        val sweep = minOf(a1 - a0, (Math.PI * 2).toFloat())
        val n = maxOf(2, ceil(abs(sweep) / 0.12f * maxOf(1f, r / 40f)).toInt())
        for (i in 0..n) { val a = a0 + sweep * i / n; lineTo(x + cos(a) * r, y + sin(a) * r) }
    }
    override fun rect(x: Float, y: Float, w: Float, h: Float) { path.addRect(x, y, x + w, y + h, Path.Direction.CW); hasPoint = true }
    override fun closePath() { path.close() }
    override fun fill() { c.drawPath(path, fillPaint()) }
    override fun stroke() { c.drawPath(path, strokePaint()) }
    override fun clip() { c.clipPath(path) }
    override fun fillRect(x: Float, y: Float, w: Float, h: Float) { c.drawRect(x, y, x + w, y + h, fillPaint()) }
    override fun strokeRect(x: Float, y: Float, w: Float, h: Float) { c.drawRect(x, y, x + w, y + h, strokePaint()) }
    override fun clearRect(x: Float, y: Float, w: Float, h: Float) { c.drawRect(x, y, x + w, y + h, clearP) }
    override fun setFont(face: Face, size: Float) { this.face = face; this.size = size }
    override fun setTextAlign(a: Int) { align = a }
    override fun setTextBaseline(b: Int) { baseline = b }
    override fun fillText(t: String, x: Float, y: Float) { val p = textPaint(true); c.drawText(t, x, y + dy(p), p) }
    override fun strokeText(t: String, x: Float, y: Float) { val p = textPaint(false); c.drawText(t, x, y + dy(p), p) }
    override fun measureText(t: String): Float { tp.typeface = AndroidFonts.face(face); tp.textSize = size; return tp.measureText(t) }
    override fun drawImage(img: Image2D, x: Float, y: Float, w: Float, h: Float) {
        fp.shader = null; fp.color = 0xffffffff.toInt(); fp.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        rf.set(x, y, x + w, y + h)
        c.drawBitmap((img as AndroidImage).bmp, null, rf, fp)
    }
}

class AndroidBitmapCanvas(val bmp: Bitmap) : AndroidCanvas(Canvas(bmp)), BitmapCanvas {
    override val width get() = bmp.width
    override val height get() = bmp.height
    override fun pixels(): IntArray { val out = IntArray(width * height); bmp.getPixels(out, 0, width, 0, 0, width, height); return out }
}

class AndroidPlatform(assets: AssetManager) : Platform2D {
    init { AndroidFonts.assets = assets }
    private val mp = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun bitmap(w: Int, h: Int): BitmapCanvas = AndroidBitmapCanvas(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888))
    override fun image(pixels: IntArray, w: Int, h: Int): Image2D = AndroidImage(Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888))
    @Synchronized override fun measure(face: Face, size: Float, t: String): Float { mp.typeface = AndroidFonts.face(face); mp.textSize = size; return mp.measureText(t) }
}
