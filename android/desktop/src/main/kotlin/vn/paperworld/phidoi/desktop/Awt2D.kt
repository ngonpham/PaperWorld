package vn.paperworld.phidoi.desktop

import java.awt.Color
import vn.paperworld.phidoi.engine.*
import java.awt.*
import java.awt.geom.AffineTransform
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.io.File
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

object AwtFonts {
    private val dir = File(System.getProperty("fontDir", "app/src/main/assets/fonts"))
    private val base = HashMap<Face, Font>()
    fun font(face: Face, size: Float): Font {
        val f = base.getOrPut(face) {
            val file = when (face) {
                Face.UI500 -> "BeVietnamPro-Medium.ttf"; Face.UI700 -> "BeVietnamPro-Bold.ttf"; Face.UI800 -> "BeVietnamPro-ExtraBold.ttf"
                Face.HAND -> "PatrickHand-Regular.ttf"; Face.BRUSH -> "Pacifico-Regular.ttf"; Face.HAN -> ""
            }
            if (file.isEmpty()) Font(Font.SERIF, Font.BOLD, 12) else Font.createFont(Font.TRUETYPE_FONT, File(dir, file))
        }
        return f.deriveFont(size)
    }
}

class AwtImage(val img: BufferedImage) : Image2D { override val width get() = img.width; override val height get() = img.height }

open class AwtCanvas(protected var g: Graphics2D) : Canvas2D {
    private class St(val tf: AffineTransform, val alpha: Float, val fill: Paint, val stroke: Color, val lw: Float, val dash: FloatArray?, val font: Font, val align: Int, val base: Int, val clip: Shape?)
    private val stack = ArrayList<St>()
    private var alpha = 1f
    private var fillPaint: Paint = Color.BLACK
    private var strokeCol: Color = Color.BLACK
    private var lw = 1f
    private var dash: FloatArray? = null
    private var font: Font = AwtFonts.font(Face.UI500, 12f)
    private var align = Align.LEFT
    private var baseline = Baseline.ALPHABETIC
    private var path = Path2D.Float()
    private var hasPoint = false

    init {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
    }
    private fun col(argb: Int) = Color((argb shr 16) and 255, (argb shr 8) and 255, argb and 255, (argb ushr 24) and 255)
    private fun comp() { g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha.coerceIn(0f, 1f)) }

    override fun save() { stack.add(St(g.transform, alpha, fillPaint, strokeCol, lw, dash, font, align, baseline, g.clip)) }
    override fun restore() {
        val s = stack.removeLastOrNull() ?: return
        g.transform = s.tf; alpha = s.alpha; fillPaint = s.fill; strokeCol = s.stroke; lw = s.lw; dash = s.dash; font = s.font; align = s.align; baseline = s.base; g.clip = s.clip
    }
    override fun translate(x: Float, y: Float) = g.translate(x.toDouble(), y.toDouble())
    override fun rotate(a: Float) = g.rotate(a.toDouble())
    override fun scale(sx: Float, sy: Float) = g.scale(sx.toDouble(), sy.toDouble())
    override fun setAlpha(a: Float) { alpha = a }
    override fun setFill(argb: Int) { fillPaint = col(argb) }
    override fun setStroke(argb: Int) { strokeCol = col(argb) }
    override fun setLineWidth(w: Float) { lw = w }
    override fun setLineDash(on: Float, off: Float) { dash = if (on <= 0f) null else floatArrayOf(on, off) }
    override fun setFillLinear(x0: Float, y0: Float, x1: Float, y1: Float, stops: FloatArray, colors: IntArray) {
        fillPaint = if (x0 == x1 && y0 == y1) col(colors[0]) else LinearGradientPaint(x0, y0, x1, y1, fixStops(stops), colors.map { col(it) }.toTypedArray())
    }
    override fun setFillRadial(cx: Float, cy: Float, r: Float, stops: FloatArray, colors: IntArray) {
        fillPaint = RadialGradientPaint(cx, cy, r, fixStops(stops), colors.map { col(it) }.toTypedArray())
    }
    private fun fixStops(s: FloatArray): FloatArray { val o = s.copyOf(); for (i in 1 until o.size) if (o[i] <= o[i - 1]) o[i] = o[i - 1] + 1e-4f; return o }
    override fun beginPath() { path = Path2D.Float(); hasPoint = false }
    override fun moveTo(x: Float, y: Float) { path.moveTo(x, y); hasPoint = true }
    override fun lineTo(x: Float, y: Float) { if (hasPoint) path.lineTo(x, y) else path.moveTo(x, y); hasPoint = true }
    override fun arc(x: Float, y: Float, r: Float, a0: Float, a1: Float) {
        val sweep = minOf(a1 - a0, (Math.PI * 2).toFloat())
        val n = maxOf(2, ceil(abs(sweep) / 0.12f * maxOf(1f, r / 40f)).toInt())
        for (i in 0..n) { val a = a0 + sweep * i / n; lineTo(x + cos(a) * r, y + sin(a) * r) }
    }
    override fun rect(x: Float, y: Float, w: Float, h: Float) { path.moveTo(x, y); path.lineTo(x + w, y); path.lineTo(x + w, y + h); path.lineTo(x, y + h); path.closePath(); hasPoint = true }
    override fun closePath() { path.closePath() }
    override fun fill() { comp(); g.paint = fillPaint; g.fill(path) }
    override fun stroke() {
        comp(); g.paint = strokeCol
        g.stroke = BasicStroke(lw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, dash, 0f)
        g.draw(path)
    }
    override fun clip() { g.clip(path) }
    override fun fillRect(x: Float, y: Float, w: Float, h: Float) { comp(); g.paint = fillPaint; g.fill(java.awt.geom.Rectangle2D.Float(x, y, w, h)) }
    override fun strokeRect(x: Float, y: Float, w: Float, h: Float) { comp(); g.paint = strokeCol; g.stroke = BasicStroke(lw); g.draw(java.awt.geom.Rectangle2D.Float(x, y, w, h)) }
    override fun clearRect(x: Float, y: Float, w: Float, h: Float) { val c = g.composite; g.composite = AlphaComposite.Clear; g.fill(java.awt.geom.Rectangle2D.Float(x, y, w, h)); g.composite = c }
    override fun setFont(face: Face, size: Float) { font = AwtFonts.font(face, size) }
    override fun setTextAlign(a: Int) { align = a }
    override fun setTextBaseline(b: Int) { baseline = b }
    private fun textPos(t: String, x: Float, y: Float): Pair<Float, Float> {
        val fm = g.getFontMetrics(font)
        val w = fm.getStringBounds(t, g).width.toFloat()
        val dx = when (align) { Align.CENTER -> -w / 2; Align.RIGHT -> -w; else -> 0f }
        val dy = when (baseline) { Baseline.MIDDLE -> (fm.ascent - fm.descent) / 2f; Baseline.TOP -> fm.ascent.toFloat(); else -> 0f }
        return Pair(x + dx, y + dy)
    }
    override fun fillText(t: String, x: Float, y: Float) { comp(); g.font = font; g.paint = fillPaint; val (px, py) = textPos(t, x, y); g.drawString(t, px, py) }
    override fun strokeText(t: String, x: Float, y: Float) {
        comp(); g.font = font
        val (px, py) = textPos(t, x, y)
        val shape = font.createGlyphVector(g.fontRenderContext, t).getOutline(px, py)
        g.paint = strokeCol; g.stroke = BasicStroke(lw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND); g.draw(shape)
    }
    override fun measureText(t: String) = g.getFontMetrics(font).getStringBounds(t, g).width.toFloat()
    override fun drawImage(img: Image2D, x: Float, y: Float, w: Float, h: Float) {
        comp()
        val bi = (img as AwtImage).img
        val at = AffineTransform(); at.translate(x.toDouble(), y.toDouble()); at.scale(w.toDouble() / bi.width, h.toDouble() / bi.height)
        g.drawImage(bi, at, null)
    }
}

object AwtPlatform : Platform2D {
    private val scratch = BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB).createGraphics()
    override fun bitmap(w: Int, h: Int): BitmapCanvas = AwtBitmap2(w, h)
    override fun image(pixels: IntArray, w: Int, h: Int): Image2D { val bi = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB); bi.setRGB(0, 0, w, h, pixels, 0, w); return AwtImage(bi) }
    override fun measure(face: Face, size: Float, t: String) = scratch.getFontMetrics(AwtFonts.font(face, size)).getStringBounds(t, scratch).width.toFloat()
}

/** pixel-backed canvas without poking into JDK internals */
class AwtBitmap2 private constructor(val image: BufferedImage) : AwtCanvas(image.createGraphics()), BitmapCanvas {
    constructor(w: Int, h: Int) : this(BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB))
    override val width get() = image.width
    override val height get() = image.height
    override fun pixels(): IntArray { val out = IntArray(width * height); image.getRGB(0, 0, width, height, out, 0, width); return out }
}
