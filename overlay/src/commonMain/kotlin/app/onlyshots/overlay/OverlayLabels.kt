package app.onlyshots.overlay

import app.onlyshots.core.model.Series

/**
 * Every word the overlay draws, in the user's language: series and reading names, units, "phase 2", "target 9.0 bar".
 * This module has no resources of its own; `:overlay:compose` resolves these from `:core:resources` once
 * (`rememberOverlayLabels`, `overlayLabels`), and the preview and every exporter hand the same values in, so an export
 * still matches its preview.
 */
data class OverlayLabels(
    val time: String,
    val weight: String,
    val pressure: String,
    val flow: String,
    val weightFlow: String,
    val temperature: String,
    val target: String,
    /** A phase line's label, with `%1$s` for its number. */
    val phase: String,
    /** A reading's target, with `%1$s` for the value and `%2$s` for the unit. */
    val targetValue: String,
    val seconds: String,
    val grams: String,
    val bar: String,
    val mlPerS: String,
    val gPerS: String,
    val celsius: String,
    /** A scale, as the graph's title names the source of a scale-only shot ("profile · scale"). */
    val scale: String,
) {
    fun series(s: Series): String =
        when (s) {
            Series.PRESSURE -> pressure
            Series.FLOW -> flow
            Series.WEIGHT -> weight
            Series.WEIGHT_FLOW -> weightFlow
            Series.TEMPERATURE -> temperature
            Series.TARGETS -> target
        }

    fun reading(r: Reading): String =
        when (r) {
            Reading.TIME -> time
            Reading.WEIGHT -> weight
            Reading.PRESSURE -> pressure
            Reading.FLOW -> flow
            Reading.WEIGHT_FLOW -> weightFlow
            Reading.TEMPERATURE -> temperature
        }

    fun phase(number: Int): String = phase.replace("%1\$s", number.toString())

    fun targetValue(value: String, unit: String): String = targetValue.replace("%1\$s", value).replace("%2\$s", unit)
}
