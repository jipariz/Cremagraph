package app.onlyshots.machine

import app.onlyshots.core.model.Sample
import app.onlyshots.core.model.SourceKind
import app.onlyshots.core.model.oneDecimal
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.UiText
import app.onlyshots.core.resources.raw
import app.onlyshots.core.resources.source_scale
import app.onlyshots.core.resources.text
import app.onlyshots.sources.BrewEvent
import app.onlyshots.sources.ble.BleReading
import app.onlyshots.sources.machine.NetworkMachines

/**
 * One line of the machine log: what the decoder made of a frame, in app terms and units. The lines are a developer's
 * log, so they stay in English.
 */
object MachineLog {
    fun line(sample: Sample, shotStartUs: Long?): String = buildString {
        if (sample.brewing && shotStartUs != null) append("${((sample.tUs - shotStartUs) / 1_000_000f).oneDecimal()} s")
        else append("idle")
        sample.pressure?.let { append(" · ${it.oneDecimal()} bar") }
        sample.flow?.let { append(" · ${it.oneDecimal()} ml/s") }
        sample.weight?.let { append(" · ${it.oneDecimal()} g") }
        sample.weightFlow?.let { append(" · ${it.oneDecimal()} g/s") }
        sample.temperature?.let { append(" · ${it.oneDecimal()} °C") }
        val targets =
            listOfNotNull(
                sample.targetPressure?.takeIf { it > 0f }?.let { "${it.oneDecimal()} bar" },
                sample.targetFlow?.takeIf { it > 0f }?.let { "${it.oneDecimal()} ml/s" },
                sample.targetTemperature?.let { "${it.oneDecimal()} °C" },
            )
        if (targets.isNotEmpty()) append(" · target ${targets.joinToString(", ")}")
    }

    fun line(event: BrewEvent, shotStartUs: Long?): String =
        when (event) {
            is BrewEvent.Started -> "brew started"
            is BrewEvent.Ended ->
                "brew ended at ${shotStartUs?.let { ((event.atUs - it) / 1_000_000f).oneDecimal() } ?: "?"} s"
        }

    fun line(r: BleReading, from: String): String = buildString {
        append(from)
        r.weightG?.let { append(" · ${it.oneDecimal()} g") }
        r.weightFlowGps?.let { append(" · ${it.oneDecimal()} g/s") }
        r.batteryPercent?.let { append(" · battery $it %") }
    }

    /** The live sources this version can connect to: every network machine, then a scale. */
    val kinds = NetworkMachines.all.map { it.kind } + SourceKind.SCALE

    /** The source's name: a machine's own (a proper noun), or "scale" in the user's language. */
    fun label(kind: SourceKind): UiText =
        if (kind == SourceKind.SCALE) Res.string.source_scale.text()
        else (NetworkMachines.of(kind)?.name ?: kind.name.lowercase()).raw()

    /** What the address field starts with: the machine's own name on the network. */
    fun defaultAddress(kind: SourceKind): String = NetworkMachines.of(kind)?.hostnames?.firstOrNull().orEmpty()

    /** "host" or "host:port"; the default port is [defaultPort]. */
    fun parseAddress(text: String, defaultPort: Int): Pair<String, Int>? {
        val t = text.trim().removePrefix("ws://").removePrefix("http://").substringBefore('/')
        if (t.isEmpty()) return null
        val host = t.substringBeforeLast(':', t)
        val port = if (':' in t) t.substringAfterLast(':').toIntOrNull() ?: return null else defaultPort
        return if (host.isEmpty() || port !in 1..65535) null else host to port
    }
}
