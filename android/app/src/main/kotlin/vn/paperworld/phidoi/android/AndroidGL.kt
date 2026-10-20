package vn.paperworld.phidoi.android

import android.opengl.GLES20
import android.opengl.GLES30
import vn.paperworld.phidoi.engine.GL
import java.nio.ByteBuffer

/** [GL] over android.opengl.GLES30 (call only on the GL thread) */
class AndroidGL : GL {
    private val i1 = IntArray(1)
    private var extensions: String? = null

    override fun getString(name: Int): String? = GLES30.glGetString(name)
    override fun getInteger(name: Int): Int { GLES30.glGetIntegerv(name, i1, 0); return i1[0] }
    override fun getError() = GLES30.glGetError()
    override fun hasExtension(name: String): Boolean {
        val e = extensions ?: (GLES30.glGetString(GLES30.GL_EXTENSIONS) ?: "").also { extensions = it }
        return e.split(' ').contains(name)
    }

    override fun createShader(type: Int) = GLES30.glCreateShader(type)
    override fun shaderSource(s: Int, src: String) = GLES30.glShaderSource(s, src)
    override fun compileShader(s: Int) = GLES30.glCompileShader(s)
    override fun getShaderCompiled(s: Int): Boolean { GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, i1, 0); return i1[0] != 0 }
    override fun getShaderInfoLog(s: Int): String = GLES30.glGetShaderInfoLog(s) ?: ""
    override fun deleteShader(s: Int) = GLES30.glDeleteShader(s)
    override fun createProgram() = GLES30.glCreateProgram()
    override fun attachShader(p: Int, s: Int) = GLES30.glAttachShader(p, s)
    override fun bindAttribLocation(p: Int, index: Int, name: String) = GLES30.glBindAttribLocation(p, index, name)
    override fun linkProgram(p: Int) = GLES30.glLinkProgram(p)
    override fun getProgramLinked(p: Int): Boolean { GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, i1, 0); return i1[0] != 0 }
    override fun getProgramInfoLog(p: Int): String = GLES30.glGetProgramInfoLog(p) ?: ""
    override fun useProgram(p: Int) = GLES30.glUseProgram(p)
    override fun getUniformLocation(p: Int, name: String) = GLES30.glGetUniformLocation(p, name)

    override fun uniform1i(l: Int, v: Int) = GLES30.glUniform1i(l, v)
    override fun uniform1f(l: Int, v: Float) = GLES30.glUniform1f(l, v)
    override fun uniform2f(l: Int, a: Float, b: Float) = GLES30.glUniform2f(l, a, b)
    override fun uniform3f(l: Int, a: Float, b: Float, c: Float) = GLES30.glUniform3f(l, a, b, c)
    override fun uniform4f(l: Int, a: Float, b: Float, c: Float, d: Float) = GLES30.glUniform4f(l, a, b, c, d)
    override fun uniformMatrix3fv(l: Int, m: FloatArray) = GLES30.glUniformMatrix3fv(l, 1, false, m, 0)
    override fun uniformMatrix4fv(l: Int, m: FloatArray) = GLES30.glUniformMatrix4fv(l, 1, false, m, 0)

    override fun genBuffer(): Int { GLES30.glGenBuffers(1, i1, 0); return i1[0] }
    override fun deleteBuffer(b: Int) { i1[0] = b; GLES30.glDeleteBuffers(1, i1, 0) }
    override fun bindBuffer(target: Int, b: Int) = GLES30.glBindBuffer(target, b)
    override fun bufferData(target: Int, data: ByteBuffer, usage: Int) = GLES30.glBufferData(target, data.remaining(), data, usage)
    override fun bufferData(target: Int, size: Int, usage: Int) = GLES30.glBufferData(target, size, null, usage)
    override fun bufferSubData(target: Int, offset: Int, data: ByteBuffer) = GLES30.glBufferSubData(target, offset, data.remaining(), data)
    override fun genVertexArray(): Int { GLES30.glGenVertexArrays(1, i1, 0); return i1[0] }
    override fun deleteVertexArray(v: Int) { i1[0] = v; GLES30.glDeleteVertexArrays(1, i1, 0) }
    override fun bindVertexArray(v: Int) = GLES30.glBindVertexArray(v)
    override fun enableVertexAttribArray(i: Int) = GLES30.glEnableVertexAttribArray(i)
    override fun disableVertexAttribArray(i: Int) = GLES30.glDisableVertexAttribArray(i)
    override fun vertexAttribPointer(i: Int, size: Int, type: Int, normalized: Boolean, stride: Int, offset: Int) = GLES30.glVertexAttribPointer(i, size, type, normalized, stride, offset)
    override fun vertexAttribDivisor(i: Int, d: Int) = GLES30.glVertexAttribDivisor(i, d)
    override fun vertexAttrib4f(i: Int, a: Float, b: Float, c: Float, d: Float) = GLES30.glVertexAttrib4f(i, a, b, c, d)

    override fun drawArrays(mode: Int, first: Int, count: Int) = GLES30.glDrawArrays(mode, first, count)
    override fun drawElements(mode: Int, count: Int, type: Int, offset: Int) = GLES30.glDrawElements(mode, count, type, offset)
    override fun drawArraysInstanced(mode: Int, first: Int, count: Int, instances: Int) = GLES30.glDrawArraysInstanced(mode, first, count, instances)
    override fun drawElementsInstanced(mode: Int, count: Int, type: Int, offset: Int, instances: Int) = GLES30.glDrawElementsInstanced(mode, count, type, offset, instances)

    override fun genTexture(): Int { GLES30.glGenTextures(1, i1, 0); return i1[0] }
    override fun deleteTexture(t: Int) { i1[0] = t; GLES30.glDeleteTextures(1, i1, 0) }
    override fun bindTexture(target: Int, t: Int) = GLES30.glBindTexture(target, t)
    override fun activeTexture(unit: Int) = GLES30.glActiveTexture(unit)
    override fun texImage2D(target: Int, level: Int, internalFormat: Int, w: Int, h: Int, format: Int, type: Int, data: ByteBuffer?) =
        GLES30.glTexImage2D(target, level, internalFormat, w, h, 0, format, type, data)
    override fun texParameteri(target: Int, p: Int, v: Int) = GLES30.glTexParameteri(target, p, v)
    override fun texParameterf(target: Int, p: Int, v: Float) = GLES30.glTexParameterf(target, p, v)
    override fun generateMipmap(target: Int) = GLES30.glGenerateMipmap(target)
    override fun pixelStorei(p: Int, v: Int) = GLES30.glPixelStorei(p, v)

    override fun genFramebuffer(): Int { GLES30.glGenFramebuffers(1, i1, 0); return i1[0] }
    override fun deleteFramebuffer(f: Int) { i1[0] = f; GLES30.glDeleteFramebuffers(1, i1, 0) }
    override fun bindFramebuffer(target: Int, f: Int) = GLES30.glBindFramebuffer(target, f)
    override fun framebufferTexture2D(target: Int, attachment: Int, texTarget: Int, t: Int, level: Int) = GLES30.glFramebufferTexture2D(target, attachment, texTarget, t, level)
    override fun framebufferRenderbuffer(target: Int, attachment: Int, rbTarget: Int, rb: Int) = GLES30.glFramebufferRenderbuffer(target, attachment, rbTarget, rb)
    override fun checkFramebufferStatus(target: Int) = GLES30.glCheckFramebufferStatus(target)
    override fun genRenderbuffer(): Int { GLES30.glGenRenderbuffers(1, i1, 0); return i1[0] }
    override fun deleteRenderbuffer(r: Int) { i1[0] = r; GLES30.glDeleteRenderbuffers(1, i1, 0) }
    override fun bindRenderbuffer(target: Int, r: Int) = GLES30.glBindRenderbuffer(target, r)
    override fun renderbufferStorage(target: Int, format: Int, w: Int, h: Int) = GLES30.glRenderbufferStorage(target, format, w, h)
    override fun renderbufferStorageMultisample(target: Int, samples: Int, format: Int, w: Int, h: Int) = GLES30.glRenderbufferStorageMultisample(target, samples, format, w, h)
    override fun blitFramebuffer(x0: Int, y0: Int, x1: Int, y1: Int, dx0: Int, dy0: Int, dx1: Int, dy1: Int, mask: Int, filter: Int) =
        GLES30.glBlitFramebuffer(x0, y0, x1, y1, dx0, dy0, dx1, dy1, mask, filter)
    override fun drawBuffersNone() { i1[0] = GLES30.GL_NONE; GLES30.glDrawBuffers(1, i1, 0) }
    override fun readBufferNone() = GLES30.glReadBuffer(GLES30.GL_NONE)

    override fun viewport(x: Int, y: Int, w: Int, h: Int) = GLES30.glViewport(x, y, w, h)
    override fun clearColor(r: Float, g: Float, b: Float, a: Float) = GLES30.glClearColor(r, g, b, a)
    override fun clear(mask: Int) = GLES30.glClear(mask)
    override fun enable(cap: Int) = GLES30.glEnable(cap)
    override fun disable(cap: Int) = GLES30.glDisable(cap)
    override fun depthMask(on: Boolean) = GLES30.glDepthMask(on)
    override fun depthFunc(f: Int) = GLES30.glDepthFunc(f)
    override fun cullFace(m: Int) = GLES30.glCullFace(m)
    override fun blendFunc(s: Int, d: Int) = GLES30.glBlendFunc(s, d)
    override fun blendFuncSeparate(sRgb: Int, dRgb: Int, sA: Int, dA: Int) = GLES30.glBlendFuncSeparate(sRgb, dRgb, sA, dA)
    override fun blendEquation(m: Int) = GLES30.glBlendEquation(m)
    override fun colorMask(r: Boolean, g: Boolean, b: Boolean, a: Boolean) = GLES30.glColorMask(r, g, b, a)
    override fun polygonOffset(factor: Float, units: Float) = GLES30.glPolygonOffset(factor, units)
    override fun lineWidth(w: Float) = GLES20.glLineWidth(w)
    override fun readPixels(x: Int, y: Int, w: Int, h: Int, format: Int, type: Int, out: ByteBuffer) = GLES30.glReadPixels(x, y, w, h, format, type, out)
    override fun finish() = GLES30.glFinish()
}
