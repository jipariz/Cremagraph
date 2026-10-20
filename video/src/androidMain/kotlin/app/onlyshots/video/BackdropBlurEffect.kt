package app.onlyshots.video

import android.content.Context
import android.opengl.GLES20
import androidx.annotation.OptIn
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import app.onlyshots.overlay.glass.GlassShader
import app.onlyshots.overlay.scene.BackdropPrim

/**
 * Blurs the video inside the overlay's backdrop rects — a blur or glass panel, Metro's tiles, Aero — before the graph
 * is drawn over it, so the export shows what Haze shows in the preview. [backdrops] gives the rects of one frame, in
 * output pixels, from the same scene builder as the overlay.
 *
 * Per frame: the frame is drawn at a quarter size, blurred in two passes (a separable Gaussian), and laid back over
 * the full frame only inside the rounded rects, with a pixel of soft edge. Frames without backdrops are copied as they
 * are.
 */
@OptIn(UnstableApi::class)
internal class BackdropBlurEffect(private val backdrops: (presentationTimeUs: Long) -> List<BackdropPrim>) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = Program(backdrops, useHdr)

    private class Program(private val backdrops: (Long) -> List<BackdropPrim>, useHdr: Boolean) :
        BaseGlShaderProgram(useHdr, 1) {
        private val copy = GlProgram(VERTEX, COPY_FRAGMENT)
        private val blur = GlProgram(VERTEX, BLUR_FRAGMENT)
        private val composite = GlProgram(VERTEX, COMPOSITE_FRAGMENT)
        private val useHighPrecision = useHdr
        private var width = 0
        private var height = 0
        private var smallW = 0
        private var smallH = 0
        private var texA = -1
        private var texB = -1
        private var fboA = -1
        private var fboB = -1

        /** Liquid Glass (Haze's shader, see GlassShader), compiled the first time a frame has a glass backdrop. */
        private var glass: GlProgram? = null

        /** Full-size frames to draw glass panels from and into, made the first time they are needed. */
        private var texC = -1
        private var texD = -1
        private var fboC = -1
        private var fboD = -1

        init {
            for (p in listOf(copy, blur, composite)) {
                p.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), 4)
            }
        }

        override fun configure(inputWidth: Int, inputHeight: Int): Size {
            if (inputWidth != width || inputHeight != height) {
                releaseTargets()
                width = inputWidth
                height = inputHeight
                smallW = (inputWidth / DOWNSCALE).coerceAtLeast(1)
                smallH = (inputHeight / DOWNSCALE).coerceAtLeast(1)
                texA = GlUtil.createTexture(smallW, smallH, useHighPrecision)
                texB = GlUtil.createTexture(smallW, smallH, useHighPrecision)
                fboA = GlUtil.createFboForTexture(texA)
                fboB = GlUtil.createFboForTexture(texB)
            }
            return Size(inputWidth, inputHeight)
        }

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            val rects = backdrops(presentationTimeUs).take(MAX_RECTS)
            val out = IntArray(1)
            GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, out, 0)
            if (rects.isEmpty()) {
                draw(copy) { setSamplerTexIdUniform("uTexture", inputTexId, 0) }
                return
            }
            // One blur strength per frame: the backdrops of a style share it.
            val sigma = (rects.maxOf { it.blur } * SIGMA_PER_RADIUS / DOWNSCALE).coerceAtLeast(0.5f)

            GlUtil.focusFramebufferUsingCurrentContext(fboA, smallW, smallH)
            draw(copy) { setSamplerTexIdUniform("uTexture", inputTexId, 0) }
            GlUtil.focusFramebufferUsingCurrentContext(fboB, smallW, smallH)
            draw(blur) {
                setSamplerTexIdUniform("uTexture", texA, 0)
                setFloatsUniform("uStep", floatArrayOf(1f / smallW, 0f))
                setFloatUniform("uSigma", sigma)
            }
            GlUtil.focusFramebufferUsingCurrentContext(fboA, smallW, smallH)
            draw(blur) {
                setSamplerTexIdUniform("uTexture", texB, 0)
                setFloatsUniform("uStep", floatArrayOf(0f, 1f / smallH))
                setFloatUniform("uSigma", sigma)
            }

            val glassRects = rects.filter { it.glass != null }
            val blurRects = rects.filter { it.glass == null }
            if (glassRects.isEmpty()) {
                composite(out[0], inputTexId, blurRects)
                return
            }
            // Blur panels first (into a full-size frame), then each glass panel over the result, the last one
            // straight into the output.
            ensureFullSize()
            var source = inputTexId
            if (blurRects.isNotEmpty()) {
                composite(fboC, inputTexId, blurRects)
                source = texC
            }
            val program = glass ?: GlProgram(VERTEX, GlassShader.glsl).also {
                it.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), 4)
                glass = it
            }
            glassRects.forEachIndexed { i, prim ->
                val last = i == glassRects.lastIndex
                val (targetFbo, targetTex) =
                    when {
                        last -> out[0] to -1
                        source == texD -> fboC to texC
                        else -> fboD to texD
                    }
                GlUtil.focusFramebufferUsingCurrentContext(targetFbo, width, height)
                val u = GlassShader.uniforms(prim, width.toFloat(), height.toFloat())
                draw(program) {
                    setSamplerTexIdUniform("frame", source, 0)
                    // Glass without frost bends the sharp picture, not the quarter-size blur.
                    setSamplerTexIdUniform("content", if (prim.blur < SHARP_BELOW) source else texA, 1)
                    for ((name, v) in u.floats) setFloatsUniformIfPresent(name, v)
                    for ((name, v) in u.colors) setFloatsUniformIfPresent(name, v)
                }
                source = targetTex
            }
        }

        /** The frame with the blur panels' rects swapped for the blurred picture, into [fbo]. */
        private fun composite(fbo: Int, inputTexId: Int, rects: List<BackdropPrim>) {
            GlUtil.focusFramebufferUsingCurrentContext(fbo, width, height)
            draw(composite) {
                setSamplerTexIdUniform("uTexture", inputTexId, 0)
                setSamplerTexIdUniform("uBlurred", texA, 1)
                setFloatsUniform("uSize", floatArrayOf(width.toFloat(), height.toFloat()))
                setIntUniform("uCount", rects.size)
                for (i in 0 until MAX_RECTS) {
                    val r = rects.getOrNull(i)
                    // Scene y runs down from the top; GL's from the bottom.
                    setFloatsUniform(
                        "uRect$i",
                        if (r == null) floatArrayOf(0f, 0f, 0f, 0f)
                        else floatArrayOf(r.box.left, height - r.box.bottom, r.box.right, height - r.box.top),
                    )
                    setFloatUniform("uRadius$i", r?.radius ?: 0f)
                }
            }
        }

        private fun ensureFullSize() {
            if (texC != -1) return
            texC = GlUtil.createTexture(width, height, useHighPrecision)
            texD = GlUtil.createTexture(width, height, useHighPrecision)
            fboC = GlUtil.createFboForTexture(texC)
            fboD = GlUtil.createFboForTexture(texD)
        }

        private inline fun draw(program: GlProgram, uniforms: GlProgram.() -> Unit) {
            program.use()
            program.uniforms()
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GlUtil.checkGlError()
        }

        private fun releaseTargets() {
            if (fboA != -1) GlUtil.deleteFbo(fboA)
            if (fboB != -1) GlUtil.deleteFbo(fboB)
            if (texA != -1) GlUtil.deleteTexture(texA)
            if (texB != -1) GlUtil.deleteTexture(texB)
            fboA = -1
            fboB = -1
            texA = -1
            texB = -1
            for (f in listOf(fboC, fboD)) if (f != -1) GlUtil.deleteFbo(f)
            for (t in listOf(texC, texD)) if (t != -1) GlUtil.deleteTexture(t)
            fboC = -1
            fboD = -1
            texC = -1
            texD = -1
        }

        override fun release() {
            super.release()
            releaseTargets()
            copy.delete()
            blur.delete()
            composite.delete()
            glass?.delete()
        }
    }

    private companion object {
        const val DOWNSCALE = 4
        const val MAX_RECTS = 8

        /** Haze's blur radius and a Gaussian sigma are not the same number; this keeps the two looking alike. */
        const val SIGMA_PER_RADIUS = 0.6f

        /** A glass blur under this many pixels is no frost: the glass samples the sharp frame. */
        const val SHARP_BELOW = 1f

        const val VERTEX =
            """
            attribute vec4 aFramePosition;
            varying vec2 vTexCoords;
            void main() {
              gl_Position = aFramePosition;
              vTexCoords = (aFramePosition.xy + 1.0) * 0.5;
            }
            """

        const val COPY_FRAGMENT =
            """
            precision mediump float;
            uniform sampler2D uTexture;
            varying vec2 vTexCoords;
            void main() { gl_FragColor = texture2D(uTexture, vTexCoords); }
            """

        /** 33 taps along [uStep], spaced so they span three sigmas whatever the sigma. */
        const val BLUR_FRAGMENT =
            """
            precision mediump float;
            uniform sampler2D uTexture;
            uniform vec2 uStep;
            uniform float uSigma;
            varying vec2 vTexCoords;
            void main() {
              float spacing = max(1.0, uSigma * 3.0 / 16.0);
              vec4 sum = vec4(0.0);
              float total = 0.0;
              for (int i = -16; i <= 16; i++) {
                float x = float(i) * spacing;
                float w = exp(-(x * x) / (2.0 * uSigma * uSigma));
                sum += texture2D(uTexture, vTexCoords + uStep * x) * w;
                total += w;
              }
              gl_FragColor = sum / total;
            }
            """

        const val COMPOSITE_FRAGMENT =
            """
            precision highp float;
            uniform sampler2D uTexture;
            uniform sampler2D uBlurred;
            uniform vec2 uSize;
            uniform int uCount;
            uniform vec4 uRect0; uniform vec4 uRect1; uniform vec4 uRect2; uniform vec4 uRect3;
            uniform vec4 uRect4; uniform vec4 uRect5; uniform vec4 uRect6; uniform vec4 uRect7;
            uniform float uRadius0; uniform float uRadius1; uniform float uRadius2; uniform float uRadius3;
            uniform float uRadius4; uniform float uRadius5; uniform float uRadius6; uniform float uRadius7;
            varying vec2 vTexCoords;

            // 1 inside the rounded rect, 0 outside, with a one-pixel soft edge.
            float inside(vec2 p, vec4 r, float radius) {
              vec2 c = (r.xy + r.zw) * 0.5;
              vec2 hs = (r.zw - r.xy) * 0.5;
              float rr = min(radius, min(hs.x, hs.y));
              vec2 q = abs(p - c) - hs + rr;
              float d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - rr;
              return clamp(0.5 - d, 0.0, 1.0);
            }

            void main() {
              vec2 p = vTexCoords * uSize;
              float m = 0.0;
              if (uCount > 0) m = max(m, inside(p, uRect0, uRadius0));
              if (uCount > 1) m = max(m, inside(p, uRect1, uRadius1));
              if (uCount > 2) m = max(m, inside(p, uRect2, uRadius2));
              if (uCount > 3) m = max(m, inside(p, uRect3, uRadius3));
              if (uCount > 4) m = max(m, inside(p, uRect4, uRadius4));
              if (uCount > 5) m = max(m, inside(p, uRect5, uRadius5));
              if (uCount > 6) m = max(m, inside(p, uRect6, uRadius6));
              if (uCount > 7) m = max(m, inside(p, uRect7, uRadius7));
              vec4 sharp = texture2D(uTexture, vTexCoords);
              vec4 soft = texture2D(uBlurred, vTexCoords);
              gl_FragColor = mix(sharp, soft, m);
            }
            """
    }
}
