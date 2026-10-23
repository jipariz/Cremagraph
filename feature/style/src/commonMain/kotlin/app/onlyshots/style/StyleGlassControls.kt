package app.onlyshots.style

import androidx.compose.runtime.Composable
import app.onlyshots.core.model.style.GlassSettings
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.style_glass_contrast
import app.onlyshots.core.resources.style_glass_corner
import app.onlyshots.core.resources.style_glass_curve
import app.onlyshots.core.resources.style_glass_dispersion
import app.onlyshots.core.resources.style_glass_edge
import app.onlyshots.core.resources.style_glass_frost
import app.onlyshots.core.resources.style_glass_refraction
import app.onlyshots.core.resources.style_glass_saturation
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

/**
 * The glass sliders of the Liquid demo app (github.com/fletchmckee/liquid), in its order, with its ranges, steps and
 * value formats. Every change shows in the preview at once (Haze) and in the export (the same shader).
 */
@Composable
internal fun GlassControls(g: GlassSettings, change: ((GlassSettings) -> GlassSettings) -> Unit) {
    fun whole(v: Float) = "${v.roundToInt()}"
    fun twoPlaces(v: Float): String {
        val hundredths = (v * 100).roundToInt()
        return "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}"
    }
    // Steps as in the demo: the range split into that many equal intervals.
    fun step(v: Float, range: ClosedFloatingPointRange<Float>, intervals: Int): Float {
        val size = (range.endInclusive - range.start) / intervals
        return (range.start + ((v - range.start) / size).roundToInt() * size).coerceIn(range)
    }
    @Composable
    fun Demo(
        label: String,
        value: Float,
        range: ClosedFloatingPointRange<Float>,
        intervals: Int,
        text: String,
        set: GlassSettings.(Float) -> GlassSettings,
    ) = SliderRow(label, value, range, text) { v -> change { it.set(step(v, range, intervals)) } }

    Demo(stringResource(Res.string.style_glass_frost), g.frost, 0f..30f, 30, whole(g.frost)) { copy(frost = it) }
    Demo(stringResource(Res.string.style_glass_refraction), g.refraction, 0f..0.5f, 50, twoPlaces(g.refraction)) {
        copy(refraction = it)
    }
    Demo(stringResource(Res.string.style_glass_curve), g.curve, 0f..1f, 100, twoPlaces(g.curve)) { copy(curve = it) }
    Demo(stringResource(Res.string.style_glass_edge), g.edge, 0f..0.25f, 25, twoPlaces(g.edge)) { copy(edge = it) }
    Demo(stringResource(Res.string.style_glass_saturation), g.saturation, 0f..2f, 100, twoPlaces(g.saturation)) {
        copy(saturation = it)
    }
    Demo(stringResource(Res.string.style_glass_corner), g.corner, 0f..50f, 50, "${whole(g.corner)}%") {
        copy(corner = it)
    }
    Demo(stringResource(Res.string.style_glass_dispersion), g.dispersion, 0f..1f, 100, twoPlaces(g.dispersion)) {
        copy(dispersion = it)
    }
    Demo(stringResource(Res.string.style_glass_contrast), g.contrast, 0f..2f, 100, twoPlaces(g.contrast)) {
        copy(contrast = it)
    }
}
