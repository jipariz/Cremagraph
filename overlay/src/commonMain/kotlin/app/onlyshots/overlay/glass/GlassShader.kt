package app.onlyshots.overlay.glass

import app.onlyshots.overlay.scene.BackdropPrim

/**
 * Liquid Glass for the exporters: Haze's own optical pass (refraction, dispersion, ambient light, colour grading, tint)
 * and its specular rim, fused into one program so a glass panel in an exported frame is drawn the way Haze draws it in
 * the preview. [sksl] is for Skia (the iOS exporter); [glsl] for OpenGL ES 2 / WebGL 1 (the Android and web exporters).
 *
 * Inputs, in frame pixels with y down: `frame` (the sharp video frame), `content` (the same frame blurred by the
 * backdrop's blur), the uniforms of [uniforms]. Outside the panel the frame comes back unchanged.
 */
object GlassShader {
    /** The fused program in Skia's shading language. */
    val sksl: String by lazy { fused() }

    /** The same program in GLSL ES 1.0: textures for shaders, gl_FragCoord for the coordinate. */
    val glsl: String by lazy { toGlsl(fused()) }

    private fun fused(): String {
        val optical =
            HazeGlassShaders.buildOptical(interactive = false)
                .replace("vec4 main(vec2 coord)", "vec4 opticalMain(vec2 coord)")
        return """
            uniform shader frame;
            uniform float specularIntensity;
            layout(color) uniform vec4 edgeShadow;
            uniform float specularExponent;
            uniform float2 lightPosition;
            $optical

            // Haze's rim pass (GlassShaders.buildRim), on the optical pass's shape.
            float rimSdf(vec2 localCoord) {
              return sdRoundedRect(localCoord, materialSize, cornerRadii);
            }

            vec2 rimGradient(vec2 localCoord) {
              float left = rimSdf(clampMaterial(localCoord - vec2(sampleStep, 0.0)));
              float right = rimSdf(clampMaterial(localCoord + vec2(sampleStep, 0.0)));
              float up = rimSdf(clampMaterial(localCoord - vec2(0.0, sampleStep)));
              float down = rimSdf(clampMaterial(localCoord + vec2(0.0, sampleStep)));
              return vec2(right - left, down - up) * (0.5 / max(sampleStep, 0.0001));
            }

            vec4 rimMain(vec2 coord) {
              vec2 localCoord = materialCoord(coord);
              float sd = rimSdf(localCoord);
              float coverage = shapeCoverage(sd, sampleStep * 0.5);
              if (coverage <= 0.0) return vec4(0.0);
              float edgeWidth = max(edgeSoftness, sampleStep);
              float edge = 1.0 - smootherstep(clamp(-sd / max(edgeWidth, 0.0001), 0.0, 1.0));
              if (edge <= 0.0) return vec4(0.0);
              vec2 gradient = rimGradient(localCoord);
              vec3 normal = normalize(vec3(-gradient.x, -gradient.y, 1.0));
              vec2 lightDirection2D = safeNormalize(lightPosition - localCoord, vec2(0.0, -1.0));
              vec3 lightDirection = normalize(vec3(lightDirection2D, 1.0));
              float specularBase = max(dot(normal, lightDirection), 0.0);
              float specular = specularExponent == 0.0 ? 1.0 : pow(specularBase, specularExponent);
              float specularAlpha = specular * specularIntensity * edge;
              float shadowAlpha = edgeShadow.a * edge;
              float alpha = specularAlpha + shadowAlpha * (1.0 - specularAlpha);
              vec3 color = vec3(specularAlpha) + edgeShadow.rgb * shadowAlpha * (1.0 - specularAlpha);
              return alpha > 0.0 ? vec4(color, alpha) * coverage : vec4(0.0);
            }

            vec4 main(vec2 coord) {
              vec4 base = frame.eval(coord);
              vec4 glass = opticalMain(coord);
              vec4 composed = glass + base * (1.0 - glass.a);
              vec4 rim = rimMain(coord);
              return rim + composed * (1.0 - rim.a);
            }
        """
            .trimIndent()
    }

    /**
     * SkSL → GLSL ES 1.0. The two are close: shaders become textures sampled at pixel/size (y flipped, as GL textures
     * start at the bottom), SkSL's float2 and colour uniforms become plain ones, the int-coded float uniforms compare
     * with float literals, and SkSL's built-in sRGB conversions are written out.
     */
    internal fun toGlsl(sksl: String): String {
        var s = sksl
        s = s.replace("uniform shader content;", "uniform sampler2D content;")
        s = s.replace("uniform shader frame;", "uniform sampler2D frame;")
        s = s.replace("content.eval(", "sampleContent(").replace("frame.eval(", "sampleFrame(")
        s = s.replace("layout(color) uniform", "uniform")
        s = Regex("\\bfloat2\\b").replace(s, "vec2")
        s = Regex("\\bhalf4\\b").replace(s, "vec4")
        s =
            Regex("\\b(surfaceProfile|chromaticAberrationMode)\\s*(==|!=)\\s*(\\d+)(?![\\d.])").replace(s) {
                "${it.groupValues[1]} ${it.groupValues[2]} ${it.groupValues[3]}.0"
            }
        val helpers =
            """
            vec4 sampleContent(vec2 p) { return texture2D(content, vec2(p.x, sampleSize.y - p.y) / sampleSize); }
            vec4 sampleFrame(vec2 p) { return texture2D(frame, vec2(p.x, sampleSize.y - p.y) / sampleSize); }
            vec3 toLinearSrgb(vec3 c) {
              return mix(c / 12.92, pow((c + 0.055) / 1.055, vec3(2.4)), step(vec3(0.04045), c));
            }
            vec3 fromLinearSrgb(vec3 c) {
              return mix(c * 12.92, 1.055 * pow(max(c, vec3(0.0)), vec3(1.0 / 2.4)) - 0.055, step(vec3(0.0031308), c));
            }
            """
                .trimIndent()
        // GLSL wants a function declared before its first use: the helpers go after the uniforms, before any code.
        val firstFunction = s.indexOf("vec2 materialCoord(")
        s = s.substring(0, firstFunction) + helpers + "\n" + s.substring(firstFunction)
        s = s.replace("vec4 main(vec2 coord) {", "vec4 fusedMain(vec2 coord) {")
        return "precision highp float;\n" +
            s +
            "\nvoid main() {\n  gl_FragColor = fusedMain(vec2(gl_FragCoord.x, sampleSize.y - gl_FragCoord.y));\n}\n"
    }

    /** Pixels between the samples of the shader's gradients: Haze uses two device pixels. */
    const val SAMPLE_STEP = 2f

    /**
     * The uniforms for drawing [prim] (a glass backdrop) into a [frameWidth] × [frameHeight] frame: the values Haze
     * derives from the same settings (GlassRenderParams), through the same [toHaze] mapping as the preview.
     */
    fun uniforms(prim: BackdropPrim, frameWidth: Float, frameHeight: Float): GlassUniforms {
        val w = prim.box.width
        val h = prim.box.height
        val g = requireNotNull(prim.glass) { "not a glass backdrop" }.toHaze(w, h)
        val shortest = minOf(w, h).coerceAtLeast(0f)
        val radius = prim.radius.coerceIn(0f, shortest / 2f)
        val floats =
            linkedMapOf(
                "sampleSize" to floatArrayOf(frameWidth, frameHeight),
                "materialOrigin" to floatArrayOf(prim.box.left, prim.box.top),
                "materialSize" to floatArrayOf(w, h),
                "sampleStep" to floatArrayOf(SAMPLE_STEP),
                "refractionStrength" to floatArrayOf(g.refractionStrength),
                "refractionFoldStrength" to floatArrayOf(0f),
                "ambientResponse" to floatArrayOf(g.ambient),
                "edgeSoftness" to floatArrayOf(g.edgeSoftness),
                "refractionHeight" to floatArrayOf((shortest * g.refractionHeight).coerceIn(0f, shortest)),
                // The surface profile: refraction across the lens, following its height.
                "edgeRefractionWidth" to floatArrayOf(-1f),
                "chromaticAberrationStrength" to floatArrayOf(g.dispersion),
                "cornerRadii" to floatArrayOf(radius, radius, radius, radius),
                "surfaceProfile" to floatArrayOf(0f),
                "chromaticAberrationMode" to floatArrayOf(0f),
                "contrast" to floatArrayOf(g.contrast),
                "whitePoint" to floatArrayOf(g.whitePoint),
                "chromaMultiplier" to floatArrayOf(g.saturation),
                "refractionScale" to floatArrayOf(g.refractionDisplacement),
                "contentNormalBlend" to floatArrayOf(g.normalBlend),
                "fresnelExponent" to floatArrayOf(g.fresnelExponent),
                "specularIntensity" to floatArrayOf(g.specularIntensity),
                "specularExponent" to floatArrayOf(g.specularExponent),
                "lightPosition" to floatArrayOf(w / 2f * (1f + g.lightX), h / 2f * (1f + g.lightY)),
            )
        val t = prim.tint
        val colors =
            linkedMapOf(
                "tintColor" to floatArrayOf(ch(t, 16), ch(t, 8), ch(t, 0), ch(t, 24)),
                "edgeShadow" to floatArrayOf(0f, 0f, 0f, g.edgeShadow),
            )
        return GlassUniforms(floats, colors)
    }

    private fun ch(argb: Int, shift: Int) = ((argb ushr shift) and 0xFF) / 255f
}

/** Named float uniforms (scalars and vectors) and colour uniforms (unpremultiplied RGBA) for [GlassShader]. */
class GlassUniforms(val floats: Map<String, FloatArray>, val colors: Map<String, FloatArray>)
