package vn.paperworld.phidoi.engine

import java.nio.ByteBuffer

/** The slice of OpenGL ES 3.0 the renderer uses. Android binds it to GLES30, the desktop test harness to LWJGL. */
interface GL {
    fun getString(name: Int): String?
    fun getInteger(name: Int): Int
    fun getError(): Int
    fun hasExtension(name: String): Boolean

    fun createShader(type: Int): Int
    fun shaderSource(s: Int, src: String)
    fun compileShader(s: Int)
    fun getShaderCompiled(s: Int): Boolean
    fun getShaderInfoLog(s: Int): String
    fun deleteShader(s: Int)
    fun createProgram(): Int
    fun attachShader(p: Int, s: Int)
    fun bindAttribLocation(p: Int, index: Int, name: String)
    fun linkProgram(p: Int)
    fun getProgramLinked(p: Int): Boolean
    fun getProgramInfoLog(p: Int): String
    fun useProgram(p: Int)
    fun getUniformLocation(p: Int, name: String): Int

    fun uniform1i(l: Int, v: Int)
    fun uniform1f(l: Int, v: Float)
    fun uniform2f(l: Int, a: Float, b: Float)
    fun uniform3f(l: Int, a: Float, b: Float, c: Float)
    fun uniform4f(l: Int, a: Float, b: Float, c: Float, d: Float)
    fun uniformMatrix3fv(l: Int, m: FloatArray)
    fun uniformMatrix4fv(l: Int, m: FloatArray)

    fun genBuffer(): Int
    fun deleteBuffer(b: Int)
    fun bindBuffer(target: Int, b: Int)
    fun bufferData(target: Int, data: ByteBuffer, usage: Int)
    fun bufferData(target: Int, size: Int, usage: Int)
    fun bufferSubData(target: Int, offset: Int, data: ByteBuffer)
    fun genVertexArray(): Int
    fun deleteVertexArray(v: Int)
    fun bindVertexArray(v: Int)
    fun enableVertexAttribArray(i: Int)
    fun disableVertexAttribArray(i: Int)
    fun vertexAttribPointer(i: Int, size: Int, type: Int, normalized: Boolean, stride: Int, offset: Int)
    fun vertexAttribDivisor(i: Int, d: Int)
    fun vertexAttrib4f(i: Int, a: Float, b: Float, c: Float, d: Float)

    fun drawArrays(mode: Int, first: Int, count: Int)
    fun drawElements(mode: Int, count: Int, type: Int, offset: Int)
    fun drawArraysInstanced(mode: Int, first: Int, count: Int, instances: Int)
    fun drawElementsInstanced(mode: Int, count: Int, type: Int, offset: Int, instances: Int)

    fun genTexture(): Int
    fun deleteTexture(t: Int)
    fun bindTexture(target: Int, t: Int)
    fun activeTexture(unit: Int)
    fun texImage2D(target: Int, level: Int, internalFormat: Int, w: Int, h: Int, format: Int, type: Int, data: ByteBuffer?)
    fun texParameteri(target: Int, p: Int, v: Int)
    fun texParameterf(target: Int, p: Int, v: Float)
    fun generateMipmap(target: Int)
    fun pixelStorei(p: Int, v: Int)

    fun genFramebuffer(): Int
    fun deleteFramebuffer(f: Int)
    fun bindFramebuffer(target: Int, f: Int)
    fun framebufferTexture2D(target: Int, attachment: Int, texTarget: Int, t: Int, level: Int)
    fun framebufferRenderbuffer(target: Int, attachment: Int, rbTarget: Int, rb: Int)
    fun checkFramebufferStatus(target: Int): Int
    fun genRenderbuffer(): Int
    fun deleteRenderbuffer(r: Int)
    fun bindRenderbuffer(target: Int, r: Int)
    fun renderbufferStorage(target: Int, format: Int, w: Int, h: Int)
    fun renderbufferStorageMultisample(target: Int, samples: Int, format: Int, w: Int, h: Int)
    fun blitFramebuffer(x0: Int, y0: Int, x1: Int, y1: Int, dx0: Int, dy0: Int, dx1: Int, dy1: Int, mask: Int, filter: Int)
    fun drawBuffersNone()
    fun readBufferNone()

    fun viewport(x: Int, y: Int, w: Int, h: Int)
    fun clearColor(r: Float, g: Float, b: Float, a: Float)
    fun clear(mask: Int)
    fun enable(cap: Int)
    fun disable(cap: Int)
    fun depthMask(on: Boolean)
    fun depthFunc(f: Int)
    fun cullFace(m: Int)
    fun blendFunc(s: Int, d: Int)
    fun blendFuncSeparate(sRgb: Int, dRgb: Int, sA: Int, dA: Int)
    fun blendEquation(m: Int)
    fun colorMask(r: Boolean, g: Boolean, b: Boolean, a: Boolean)
    fun polygonOffset(factor: Float, units: Float)
    fun lineWidth(w: Float)
    fun readPixels(x: Int, y: Int, w: Int, h: Int, format: Int, type: Int, out: ByteBuffer)
    fun finish()
}

/** GLES constants (identical values on every platform) */
object GLC {
    const val NO_ERROR = 0
    const val DEPTH_BUFFER_BIT = 0x0100
    const val STENCIL_BUFFER_BIT = 0x0400
    const val COLOR_BUFFER_BIT = 0x4000
    const val POINTS = 0x0000
    const val LINES = 0x0001
    const val LINE_STRIP = 0x0003
    const val TRIANGLES = 0x0004
    const val ZERO = 0
    const val ONE = 1
    const val SRC_ALPHA = 0x0302
    const val ONE_MINUS_SRC_ALPHA = 0x0303
    const val FUNC_ADD = 0x8006
    const val ARRAY_BUFFER = 0x8892
    const val ELEMENT_ARRAY_BUFFER = 0x8893
    const val STATIC_DRAW = 0x88E4
    const val DYNAMIC_DRAW = 0x88E8
    const val CULL_FACE = 0x0B44
    const val BLEND = 0x0BE2
    const val DEPTH_TEST = 0x0B71
    const val POLYGON_OFFSET_FILL = 0x8037
    const val FRONT = 0x0404
    const val BACK = 0x0405
    const val LEQUAL = 0x0203
    const val LESS = 0x0201
    const val ALWAYS = 0x0207
    const val BYTE = 0x1400
    const val UNSIGNED_BYTE = 0x1401
    const val UNSIGNED_SHORT = 0x1403
    const val UNSIGNED_INT = 0x1405
    const val FLOAT = 0x1406
    const val HALF_FLOAT = 0x140B
    const val UNSIGNED_INT_24_8 = 0x84FA
    const val DEPTH_COMPONENT = 0x1902
    const val RED = 0x1903
    const val RGB = 0x1907
    const val RGBA = 0x1908
    const val LUMINANCE = 0x1909
    const val R8 = 0x8229
    const val RGBA8 = 0x8058
    const val SRGB8_ALPHA8 = 0x8C43
    const val RGBA16F = 0x881A
    const val DEPTH_COMPONENT16 = 0x81A5
    const val DEPTH_COMPONENT24 = 0x81A6
    const val DEPTH24_STENCIL8 = 0x88F0
    const val DEPTH_STENCIL = 0x84F9
    const val VERTEX_SHADER = 0x8B31
    const val FRAGMENT_SHADER = 0x8B30
    const val TEXTURE_2D = 0x0DE1
    const val TEXTURE0 = 0x84C0
    const val TEXTURE_MAG_FILTER = 0x2800
    const val TEXTURE_MIN_FILTER = 0x2801
    const val TEXTURE_WRAP_S = 0x2802
    const val TEXTURE_WRAP_T = 0x2803
    const val TEXTURE_COMPARE_MODE = 0x884C
    const val TEXTURE_COMPARE_FUNC = 0x884D
    const val COMPARE_REF_TO_TEXTURE = 0x884E
    const val TEXTURE_MAX_ANISOTROPY_EXT = 0x84FE
    const val MAX_TEXTURE_MAX_ANISOTROPY_EXT = 0x84FF
    const val NEAREST = 0x2600
    const val LINEAR = 0x2601
    const val LINEAR_MIPMAP_LINEAR = 0x2703
    const val REPEAT = 0x2901
    const val CLAMP_TO_EDGE = 0x812F
    const val UNPACK_ALIGNMENT = 0x0CF5
    const val PACK_ALIGNMENT = 0x0D05
    const val FRAMEBUFFER = 0x8D40
    const val READ_FRAMEBUFFER = 0x8CA8
    const val DRAW_FRAMEBUFFER = 0x8CA9
    const val RENDERBUFFER = 0x8D41
    const val COLOR_ATTACHMENT0 = 0x8CE0
    const val DEPTH_ATTACHMENT = 0x8D00
    const val DEPTH_STENCIL_ATTACHMENT = 0x821A
    const val FRAMEBUFFER_COMPLETE = 0x8CD5
    const val MAX_SAMPLES = 0x8D57
    const val MAX_TEXTURE_SIZE = 0x0D33
    const val VERSION = 0x1F02
    const val RENDERER = 0x1F01
    const val EXTENSIONS = 0x1F03
}
