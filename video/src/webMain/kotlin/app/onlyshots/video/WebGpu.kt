package app.onlyshots.video

import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.DirectContext
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Surface

/** The browser gives no WebGL context: the export cannot run here. The exporter words it for the user. */
internal class NoWebGlException : IllegalStateException("no WebGL in this browser")

/** The browser has no WebCodecs `VideoEncoder`: nothing can be written here. The exporter words it too. */
internal class NoWebCodecsException : IllegalStateException("no VideoEncoder in this browser")

/**
 * A WebGL context of its own for the export, with a Skia [DirectContext] on it. The glass shader and the blur run on
 * the GPU here: Skia's CPU path for runtime shaders is interpreted in wasm and takes seconds a frame.
 *
 * Compose draws its own canvas with another WebGL context, so this one is made current again before every frame
 * ([use]); everything between that and the read-back is synchronous, so Compose cannot draw in between.
 */
internal class WebGpu(width: Int, height: Int) : AutoCloseable {
    private val handle: Int = glCreateContext(skikoGl(), width, height).also { if (it == 0) throw NoWebGlException() }
    val context: DirectContext

    init {
        glMakeCurrent(skikoGl(), handle)
        context = DirectContext.makeGL()
    }

    fun surface(width: Int, height: Int): Surface =
        Surface.makeRenderTarget(context, false, ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.PREMUL))

    /** Makes this context current for [block], which must not suspend. */
    inline fun <T> use(block: () -> T): T {
        makeCurrent()
        return block().also { context.flush() }
    }

    fun makeCurrent() {
        glMakeCurrent(skikoGl(), handle)
    }

    override fun close() {
        makeCurrent()
        context.abandon()
        context.close()
        glDeleteContext(skikoGl(), handle)
    }
}

private fun glCreateContext(gl: JsAny, width: Int, height: Int): Int =
    js(
        """(() => {
            const canvas = document.createElement('canvas');
            canvas.width = width;
            canvas.height = height;
            return gl.createContext(canvas, {
                alpha: 1, depth: 0, stencil: 8, antialias: 0, premultipliedAlpha: 1, preserveDrawingBuffer: 0,
                preferLowPowerToHighPerformance: 0, failIfMajorPerformanceCaveat: 0, enableExtensionsByDefault: 1,
                explicitSwapControl: 0, renderViaOffscreenBackBuffer: 0, majorVersion: 2,
            });
        })()"""
    )

private fun glMakeCurrent(gl: JsAny, handle: Int): Boolean = js("gl.makeContextCurrent(handle)")

private fun glDeleteContext(gl: JsAny, handle: Int): Unit = js("gl.deleteContext(handle)")
