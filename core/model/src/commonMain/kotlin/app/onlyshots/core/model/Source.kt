package app.onlyshots.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class SourceKind {
    GAGGIUINO,
    GAGGIMATE,
    DECENT,
    SCALE,
    /** A shot fetched from visualizer.coffee, or read from its download: never a live source. */
    VISUALIZER,
}

@Serializable
enum class Series {
    PRESSURE,
    FLOW,
    WEIGHT,
    WEIGHT_FLOW,
    TEMPERATURE,
    TARGETS,
}

/** What makes a recording start automatically for a source. */
@Serializable
enum class Trigger {
    BREW_SWITCH,
    PROCESS_START,
    SHOT_START,
    FIRST_DROPS,
}

/**
 * What a connected source can provide. Screens decide what to show from this alone and never branch on the machine
 * itself; unavailable series are explained in the UI, not hidden.
 */
@Serializable
data class Capabilities(
    val series: Set<Series>,
    val phases: Boolean,
    val history: Boolean,
    val autoStartTrigger: Trigger,
) {
    companion object {
        /**
         * Defaults per source. GaggiMate reports weight only with a scale paired. Pressure comes only from machines
         * with a network API; a Bluetooth scale on its own gives weight.
         */
        fun of(kind: SourceKind, hasScale: Boolean = false): Capabilities =
            when (kind) {
                // Gaggiuino release/stm32-blackpill streams no phase index and has no shot history
                // endpoint; the web UI keeps history in the browser.
                SourceKind.GAGGIUINO ->
                    Capabilities(Series.entries.toSet(), phases = false, history = false, Trigger.BREW_SWITCH)
                SourceKind.GAGGIMATE ->
                    Capabilities(
                        series =
                            buildSet {
                                add(Series.PRESSURE)
                                add(Series.FLOW)
                                add(Series.TEMPERATURE)
                                add(Series.TARGETS)
                                if (hasScale) {
                                    add(Series.WEIGHT)
                                    add(Series.WEIGHT_FLOW)
                                }
                            },
                        phases = true,
                        history = true,
                        autoStartTrigger = Trigger.PROCESS_START,
                    )
                SourceKind.DECENT ->
                    Capabilities(Series.entries.toSet(), phases = true, history = true, Trigger.SHOT_START)
                // Read after the fact, whatever machine made it: no live series to promise.
                SourceKind.VISUALIZER -> Capabilities(emptySet(), phases = false, history = false, Trigger.FIRST_DROPS)
                SourceKind.SCALE ->
                    Capabilities(
                        series = setOf(Series.WEIGHT, Series.WEIGHT_FLOW),
                        phases = false,
                        history = false,
                        autoStartTrigger = Trigger.FIRST_DROPS,
                    )
            }
    }
}
