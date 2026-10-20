package app.onlyshots.overlay.glass

import app.onlyshots.core.model.style.GlassSettings

/**
 * Haze's glass parameters for one panel, lengths in frame pixels: what the preview hands to Haze's `GlassStyle` and the
 * exporters to the same shader, so both draw the same glass.
 */
data class HazeGlassParams(
    val refractionStrength: Float,
    /** The curved rim's height as a share of the shorter side. */
    val refractionHeight: Float,
    val refractionDisplacement: Float,
    val specularIntensity: Float,
    val specularExponent: Float,
    val fresnelExponent: Float,
    val edgeShadow: Float,
    val edgeSoftness: Float,
    val ambient: Float,
    /** Where the light is, -1..1 across the panel each way. */
    val lightX: Float,
    val lightY: Float,
    val dispersion: Float,
    /** Haze's contrast and white point: 0 leaves the picture alone. */
    val contrast: Float,
    val whitePoint: Float,
    val saturation: Float,
    val normalBlend: Float,
)

/**
 * The Liquid demo's controls ([GlassSettings]) as Haze's glass for a panel [width] × [height] px. The lens follows
 * Liquid's: [GlassSettings.refraction] is how far in from the edge it reaches and [GlassSettings.curve] how far it
 * displaces, both shares of the shorter side; the lit rim is [GlassSettings.edge] of it. What the demo doesn't expose
 * is fixed to Liquid's look: light from the top left, the rim lit only when there is one, no edge shadow, no white
 * point shift.
 */
fun GlassSettings.toHaze(width: Float, height: Float): HazeGlassParams {
    val shortest = minOf(width, height).coerceAtLeast(0f)
    val rim = edge.coerceIn(0f, 0.25f) * shortest
    return HazeGlassParams(
        refractionStrength = if (curve > 0f && refraction > 0f) 1f else 0f,
        refractionHeight = refraction.coerceIn(0f, 0.5f),
        refractionDisplacement = (curve.coerceIn(0f, 1f) * shortest).coerceAtMost(MAX_DISPLACEMENT),
        specularIntensity = if (rim > 0f) 0.8f else 0f,
        specularExponent = 16f,
        fresnelExponent = 3f,
        edgeShadow = 0f,
        edgeSoftness = rim,
        ambient = if (rim > 0f) 0.5f else 0f,
        lightX = -1f,
        lightY = -1f,
        dispersion = dispersion.coerceIn(0f, 1f),
        contrast = (contrast - 1f).coerceIn(-1f, 1f),
        whitePoint = 0f,
        saturation = saturation.coerceIn(0f, 2f),
        normalBlend = 0f,
    )
}

/** Haze caps the displacement at this many pixels. */
const val MAX_DISPLACEMENT = 256f
