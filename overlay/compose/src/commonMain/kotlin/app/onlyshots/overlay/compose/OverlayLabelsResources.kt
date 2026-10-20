package app.onlyshots.overlay.compose

import androidx.compose.runtime.Composable
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.overlay_phase
import app.onlyshots.core.resources.overlay_target_value
import app.onlyshots.core.resources.reading_flow
import app.onlyshots.core.resources.reading_pressure
import app.onlyshots.core.resources.reading_target
import app.onlyshots.core.resources.reading_temperature
import app.onlyshots.core.resources.reading_time
import app.onlyshots.core.resources.reading_weight
import app.onlyshots.core.resources.reading_weight_flow
import app.onlyshots.core.resources.source_scale
import app.onlyshots.core.resources.unit_bar
import app.onlyshots.core.resources.unit_celsius
import app.onlyshots.core.resources.unit_g_per_s
import app.onlyshots.core.resources.unit_grams
import app.onlyshots.core.resources.unit_ml_per_s
import app.onlyshots.core.resources.unit_seconds
import app.onlyshots.overlay.OverlayLabels
import app.onlyshots.overlay.Reading
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** The resources behind [OverlayLabels], in its fields' order. */
private val labelResources: List<StringResource> =
    listOf(
        Res.string.reading_time,
        Res.string.reading_weight,
        Res.string.reading_pressure,
        Res.string.reading_flow,
        Res.string.reading_weight_flow,
        Res.string.reading_temperature,
        Res.string.reading_target,
        Res.string.overlay_phase,
        Res.string.overlay_target_value,
        Res.string.unit_seconds,
        Res.string.unit_grams,
        Res.string.unit_bar,
        Res.string.unit_ml_per_s,
        Res.string.unit_g_per_s,
        Res.string.unit_celsius,
        Res.string.source_scale,
    )

private fun labelsOf(s: List<String>) =
    OverlayLabels(
        time = s[0],
        weight = s[1],
        pressure = s[2],
        flow = s[3],
        weightFlow = s[4],
        temperature = s[5],
        target = s[6],
        phase = s[7],
        targetValue = s[8],
        seconds = s[9],
        grams = s[10],
        bar = s[11],
        mlPerS = s[12],
        gPerS = s[13],
        celsius = s[14],
        scale = s[15],
    )

/** The overlay's words for the screens (record, edit, previews). The templates keep their `%1$s` for the overlay. */
@Composable fun rememberOverlayLabels(): OverlayLabels = labelsOf(labelResources.map { stringResource(it) })

/** The same words outside composition, for an export: resolve once before the frame loop. */
suspend fun overlayLabels(): OverlayLabels = labelsOf(labelResources.map { getString(it) })

/** A reading's name as the overlay labels it, for the screens that talk about it (the style editor). */
@Composable fun Reading.label(): String = rememberOverlayLabels().reading(this)
