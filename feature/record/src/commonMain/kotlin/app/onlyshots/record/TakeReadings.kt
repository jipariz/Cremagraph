package app.onlyshots.record

import app.onlyshots.core.model.RecordingSettings
import app.onlyshots.core.model.Sample
import app.onlyshots.core.model.SourceKind
import app.onlyshots.core.model.VideoFormat
import app.onlyshots.core.model.oneDecimal
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.UiText
import app.onlyshots.core.resources.overlay_target_value
import app.onlyshots.core.resources.raw
import app.onlyshots.core.resources.reading_flow
import app.onlyshots.core.resources.reading_pressure
import app.onlyshots.core.resources.reading_temperature
import app.onlyshots.core.resources.reading_weight
import app.onlyshots.core.resources.record_no_sound
import app.onlyshots.core.resources.record_started_before
import app.onlyshots.core.resources.record_started_by_hand
import app.onlyshots.core.resources.record_started_with_shot
import app.onlyshots.core.resources.record_turn_sideways_body
import app.onlyshots.core.resources.record_turn_sideways_title
import app.onlyshots.core.resources.record_turn_upright_body
import app.onlyshots.core.resources.record_turn_upright_title
import app.onlyshots.core.resources.record_with_sound
import app.onlyshots.core.resources.text
import app.onlyshots.core.resources.unit_bar
import app.onlyshots.core.resources.unit_celsius
import app.onlyshots.core.resources.unit_g_per_s
import app.onlyshots.core.resources.unit_grams
import app.onlyshots.core.resources.unit_ml_per_s
import org.jetbrains.compose.resources.StringResource

/** Which quantity a reading tile shows, so the screen can colour it as the graph does. */
enum class Quantity {
    Pressure,
    Flow,
    Weight,
    WeightFlow,
    Temperature,
}

/** One reading tile: its label (with a target where there is one), the value now ("–" before any) and its unit. */
data class TakeReading(val label: UiText, val value: String, val unit: StringResource, val quantity: Quantity)

/**
 * The record panel's tiles: a machine's pressure, flow, weight (with its target) and temperature; a scale's weight and
 * how fast it climbs. [sample] is the newest reading: the shot's while recording, the source's before.
 */
fun takeReadings(sample: Sample?, kind: SourceKind): List<TakeReading> {
    fun v(x: Float?) = x?.oneDecimal() ?: "–"
    val weight = Res.string.reading_weight.text()
    val flow = Res.string.reading_flow.text()
    if (kind == SourceKind.SCALE) {
        return listOf(
            TakeReading(weight, v(sample?.weight), Res.string.unit_grams, Quantity.Weight),
            TakeReading(flow, v(sample?.weightFlow), Res.string.unit_g_per_s, Quantity.WeightFlow),
        )
    }
    val weightLabel =
        sample?.targetWeight?.let {
            UiText.Joined(
                listOf(weight, Res.string.overlay_target_value.text(it.oneDecimal(), Res.string.unit_grams.text()))
            )
        } ?: weight
    return listOf(
        TakeReading(Res.string.reading_pressure.text(), v(sample?.pressure), Res.string.unit_bar, Quantity.Pressure),
        TakeReading(flow, v(sample?.flow), Res.string.unit_ml_per_s, Quantity.Flow),
        TakeReading(weightLabel, v(sample?.weight), Res.string.unit_grams, Quantity.Weight),
        TakeReading(
            Res.string.reading_temperature.text(),
            v(sample?.temperature),
            Res.string.unit_celsius,
            Quantity.Temperature,
        ),
    )
}

/** The line under the clock: the quality the camera films at, and whether there is sound. */
fun takeQuality(fourK: Boolean, sound: Boolean): UiText =
    UiText.Joined(
        listOf(
            (if (fourK) "4K" else "1080p").raw(),
            (if (sound) Res.string.record_with_sound else Res.string.record_no_sound).text(),
        )
    )

/**
 * Where the screen stands the other way from the format: the format that fits as it stands (16:9 on a sideways screen,
 * 9:16 on an upright one), offered in its place.
 */
fun formatThatFits(windowWidth: Int, windowHeight: Int): VideoFormat =
    if (windowWidth > windowHeight) VideoFormat.LANDSCAPE_16_9 else VideoFormat.PORTRAIT_9_16

/** "Stand the phone upright" or "Turn it on its side", for the paused camera. */
fun turnTitle(format: VideoFormat): StringResource =
    if (format.landscape) Res.string.record_turn_sideways_title else Res.string.record_turn_upright_title

/** Why the camera waits, under [turnTitle]. */
fun turnBody(format: VideoFormat): UiText =
    (if (format.landscape) Res.string.record_turn_sideways_body else Res.string.record_turn_upright_body).text(
        format.label
    )

/** How a rule reads in the panel while recording: whether the take began with the shot, and when it will stop. */
fun startRuleLine(settings: RecordingSettings): UiText =
    if (!settings.autoStart) Res.string.record_started_by_hand.text()
    else if (settings.preRollSeconds == 0) Res.string.record_started_with_shot.text()
    else Res.string.record_started_before.text(settings.preRollSeconds.toString())
