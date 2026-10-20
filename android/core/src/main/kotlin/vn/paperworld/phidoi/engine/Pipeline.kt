package vn.paperworld.phidoi.engine

/**
 * Scene → (MSAA) HDR target with a depth texture → post pass (ink edges, paper, torn border) → screen.
 */
class Pipeline(val r: Renderer) {
    private val gl = r.gl
    var width = 0; private set
    var height = 0; private set
    var samples = 0; private set
    var pixelRatio = 1.0
    private var msFbo = 0; private var msColor = 0; private var msDepth = 0
    private var fbo = 0; private var colorTex = 0; private var depthTex = 0
    var hdr = true; private set
    val clear = Color.of("#dcecf2")

    /* post uniforms */
    var time = 0.0; var speed = 0.0; var fade = 0.0; var mist = 0.0; var night = 0.0; var hurt = 0.0
    var edgeN = 500.0; var edgeF = 2000.0
    val mistCol = Color.of("#f4f6f8")
    private val tri = Geometry().also { it.position = floatArrayOf(-1f, -1f, 0f, 3f, -1f, 0f, -1f, 3f, 0f) }
    private val post by lazy { r.program("post", Shaders.POST_VS, Shaders.POST_FS, emptyList()) }

    fun resize(w: Int, h: Int, msaa: Int) {
        if (w == width && h == height && msaa == samples && fbo != 0) return
        release()
        width = w; height = h
        val maxS = gl.getInteger(GLC.MAX_SAMPLES)
        samples = minOf(msaa, maxS)
        for (fmt in intArrayOf(GLC.RGBA16F, GLC.RGBA8)) {
            if (tryBuild(fmt)) { hdr = fmt == GLC.RGBA16F; break }
            release(); width = w; height = h
        }
        gl.bindFramebuffer(GLC.FRAMEBUFFER, 0)
    }

    private fun tryBuild(fmt: Int): Boolean {
        colorTex = gl.genTexture()
        gl.bindTexture(GLC.TEXTURE_2D, colorTex)
        gl.texImage2D(GLC.TEXTURE_2D, 0, fmt, width, height, GLC.RGBA, if (fmt == GLC.RGBA16F) GLC.HALF_FLOAT else GLC.UNSIGNED_BYTE, null)
        for ((p, v) in listOf(GLC.TEXTURE_MIN_FILTER to GLC.LINEAR, GLC.TEXTURE_MAG_FILTER to GLC.LINEAR, GLC.TEXTURE_WRAP_S to GLC.CLAMP_TO_EDGE, GLC.TEXTURE_WRAP_T to GLC.CLAMP_TO_EDGE)) gl.texParameteri(GLC.TEXTURE_2D, p, v)
        depthTex = gl.genTexture()
        gl.bindTexture(GLC.TEXTURE_2D, depthTex)
        gl.texImage2D(GLC.TEXTURE_2D, 0, GLC.DEPTH24_STENCIL8, width, height, GLC.DEPTH_STENCIL, GLC.UNSIGNED_INT_24_8, null)
        for ((p, v) in listOf(GLC.TEXTURE_MIN_FILTER to GLC.NEAREST, GLC.TEXTURE_MAG_FILTER to GLC.NEAREST, GLC.TEXTURE_WRAP_S to GLC.CLAMP_TO_EDGE, GLC.TEXTURE_WRAP_T to GLC.CLAMP_TO_EDGE)) gl.texParameteri(GLC.TEXTURE_2D, p, v)
        fbo = gl.genFramebuffer()
        gl.bindFramebuffer(GLC.FRAMEBUFFER, fbo)
        gl.framebufferTexture2D(GLC.FRAMEBUFFER, GLC.COLOR_ATTACHMENT0, GLC.TEXTURE_2D, colorTex, 0)
        gl.framebufferTexture2D(GLC.FRAMEBUFFER, GLC.DEPTH_STENCIL_ATTACHMENT, GLC.TEXTURE_2D, depthTex, 0)
        if (gl.checkFramebufferStatus(GLC.FRAMEBUFFER) != GLC.FRAMEBUFFER_COMPLETE) return false
        if (samples > 0) {
            msFbo = gl.genFramebuffer()
            gl.bindFramebuffer(GLC.FRAMEBUFFER, msFbo)
            msColor = gl.genRenderbuffer(); gl.bindRenderbuffer(GLC.RENDERBUFFER, msColor)
            gl.renderbufferStorageMultisample(GLC.RENDERBUFFER, samples, fmt, width, height)
            gl.framebufferRenderbuffer(GLC.FRAMEBUFFER, GLC.COLOR_ATTACHMENT0, GLC.RENDERBUFFER, msColor)
            msDepth = gl.genRenderbuffer(); gl.bindRenderbuffer(GLC.RENDERBUFFER, msDepth)
            gl.renderbufferStorageMultisample(GLC.RENDERBUFFER, samples, GLC.DEPTH24_STENCIL8, width, height)
            gl.framebufferRenderbuffer(GLC.FRAMEBUFFER, GLC.DEPTH_STENCIL_ATTACHMENT, GLC.RENDERBUFFER, msDepth)
            if (gl.checkFramebufferStatus(GLC.FRAMEBUFFER) != GLC.FRAMEBUFFER_COMPLETE) {
                gl.deleteFramebuffer(msFbo); gl.deleteRenderbuffer(msColor); gl.deleteRenderbuffer(msDepth); msFbo = 0; msColor = 0; msDepth = 0; samples = 0
            }
        }
        return true
    }

    private fun release() {
        if (fbo != 0) gl.deleteFramebuffer(fbo); if (colorTex != 0) gl.deleteTexture(colorTex); if (depthTex != 0) gl.deleteTexture(depthTex)
        if (msFbo != 0) gl.deleteFramebuffer(msFbo); if (msColor != 0) gl.deleteRenderbuffer(msColor); if (msDepth != 0) gl.deleteRenderbuffer(msDepth)
        fbo = 0; colorTex = 0; depthTex = 0; msFbo = 0; msColor = 0; msDepth = 0
    }

    /** draw [scene] through the post pass into [screenFbo] of size [screenW]×[screenH] */
    fun render(scene: Object3D, cam: Camera, screenFbo: Int, screenW: Int, screenH: Int) {
        val target = if (msFbo != 0) msFbo else fbo
        gl.bindFramebuffer(GLC.FRAMEBUFFER, target)
        r.currentFbo = target; r.viewportW = width; r.viewportH = height; r.drawingBufferHeight = height
        gl.viewport(0, 0, width, height)
        gl.depthMask(true)
        gl.clearColor(clear.r.toFloat(), clear.g.toFloat(), clear.b.toFloat(), 1f)
        gl.clear(GLC.COLOR_BUFFER_BIT or GLC.DEPTH_BUFFER_BIT or GLC.STENCIL_BUFFER_BIT)
        r.render(scene, cam)
        if (msFbo != 0) {
            gl.bindFramebuffer(GLC.READ_FRAMEBUFFER, msFbo)
            gl.bindFramebuffer(GLC.DRAW_FRAMEBUFFER, fbo)
            gl.blitFramebuffer(0, 0, width, height, 0, 0, width, height, GLC.COLOR_BUFFER_BIT, GLC.NEAREST)
            gl.blitFramebuffer(0, 0, width, height, 0, 0, width, height, GLC.DEPTH_BUFFER_BIT or GLC.STENCIL_BUFFER_BIT, GLC.NEAREST)
        }
        gl.bindFramebuffer(GLC.FRAMEBUFFER, screenFbo)
        gl.viewport(0, 0, screenW, screenH)
        gl.disable(GLC.DEPTH_TEST); gl.disable(GLC.BLEND); gl.disable(GLC.CULL_FACE); gl.disable(GLC.POLYGON_OFFSET_FILL)
        val p = post
        gl.useProgram(p.id)
        gl.activeTexture(GLC.TEXTURE0); gl.bindTexture(GLC.TEXTURE_2D, colorTex); p.i("tColor", 0)
        gl.activeTexture(GLC.TEXTURE0 + 2); gl.bindTexture(GLC.TEXTURE_2D, depthTex); p.i("tDepth", 2)
        p.v2("uRes", width.toDouble(), height.toDouble())
        p.f("uNear", cam.near); p.f("uFar", cam.far); p.f("uTime", time); p.f("uPR", pixelRatio); p.f("uSpeed", speed); p.f("uFade", fade)
        p.f("uNight", night); p.f("uMist", mist); p.f("uHurt", hurt); p.f("uEdgeN", edgeN); p.f("uEdgeF", edgeF)
        p.v3("uPaper", 0.957, 0.937, 0.886); p.v3("uInk", 0.12, 0.11, 0.17); p.col("uMistCol", mistCol)
        r.drawFullscreen(tri)
        gl.enable(GLC.DEPTH_TEST)
    }
}
