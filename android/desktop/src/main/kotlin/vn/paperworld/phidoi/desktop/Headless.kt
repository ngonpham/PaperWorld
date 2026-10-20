package vn.paperworld.phidoi.desktop

import org.lwjgl.BufferUtils
import org.lwjgl.PointerBuffer
import org.lwjgl.egl.EGL
import org.lwjgl.egl.EGL10.*
import org.lwjgl.egl.EGL14.EGL_OPENGL_ES_API
import org.lwjgl.egl.EGL14.eglBindAPI
import org.lwjgl.opengles.GLES
import org.lwjgl.opengles.GLES30
import org.lwjgl.system.Configuration
import org.lwjgl.system.JNI
import org.lwjgl.system.MemoryUtil
import java.awt.image.BufferedImage

const val CHROME = "/opt/pw-browsers/chromium-1194/chrome-linux"

/** an offscreen OpenGL ES 3 context through ANGLE (Vulkan → SwiftShader), no GPU or display needed */
class Headless(val width: Int, val height: Int) {
    init {
        Configuration.EGL_LIBRARY_NAME.set("$CHROME/libEGL.so")
        Configuration.OPENGLES_LIBRARY_NAME.set("$CHROME/libGLESv2.so")
        val attrs = PointerBuffer.allocateDirect(7)
        attrs.put(0x3203).put(0x3450).put(0x3209).put(0x3487).put(EGL_NONE.toLong()).flip()
        val fn = EGL.getFunctionProvider()!!.getFunctionAddress("eglGetPlatformDisplay")
        val display = JNI.callPPP(0x3202, 0L, MemoryUtil.memAddress(attrs), fn)
        val maj = BufferUtils.createIntBuffer(1); val min = BufferUtils.createIntBuffer(1)
        check(eglInitialize(display, maj, min)) { "eglInitialize failed" }
        EGL.createDisplayCapabilities(display, maj[0], min[0])
        val cfg = BufferUtils.createIntBuffer(32)
        cfg.put(intArrayOf(0x3040, 0x40, EGL_SURFACE_TYPE, EGL_PBUFFER_BIT, EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8, EGL_ALPHA_SIZE, 8, EGL_DEPTH_SIZE, 24, EGL_STENCIL_SIZE, 8, EGL_NONE)).flip()
        val configs = PointerBuffer.allocateDirect(1); val n = BufferUtils.createIntBuffer(1)
        check(eglChooseConfig(display, cfg, configs, n) && n[0] > 0) { "no EGL config" }
        val surf = eglCreatePbufferSurface(display, configs[0], intArrayOf(EGL_WIDTH, width, EGL_HEIGHT, height, EGL_NONE))
        eglBindAPI(EGL_OPENGL_ES_API)
        val ctx = eglCreateContext(display, configs[0], EGL_NO_CONTEXT, intArrayOf(0x3098, 3, EGL_NONE))
        check(eglMakeCurrent(display, surf, surf, ctx)) { "eglMakeCurrent failed" }
        GLES.createCapabilities()
    }

    fun grab(): BufferedImage {
        val px = BufferUtils.createByteBuffer(width * height * 4)
        GLES30.glReadPixels(0, 0, width, height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, px)
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val row = IntArray(width)
        for (y in 0 until height) {
            for (x in 0 until width) { val i = ((height - 1 - y) * width + x) * 4; row[x] = ((px[i].toInt() and 255) shl 16) or ((px[i + 1].toInt() and 255) shl 8) or (px[i + 2].toInt() and 255) }
            img.setRGB(0, y, width, 1, row, 0, width)
        }
        return img
    }
}
