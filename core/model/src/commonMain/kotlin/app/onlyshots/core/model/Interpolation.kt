package app.onlyshots.core.model

/**
 * Sample at shot time [tUs], linearly interpolated between neighbours.
 *
 * Before the first sample the first one is returned, after the last one the last one (the reading holds). Inside a gap
 * every value is null. Discrete fields (phase, brewing) come from the earlier neighbour. Returns null only for a shot
 * without samples.
 */
fun Shot.sampleAt(tUs: Long): Sample? {
    if (samples.isEmpty()) return null
    if (gaps.any { tUs in it }) return Sample(tUs = tUs, brewing = false)
    val first = samples.first()
    val last = samples.last()
    if (tUs <= first.tUs) return first.copy(tUs = tUs)
    if (tUs >= last.tUs) return last.copy(tUs = tUs)
    val i = samples.indexAtOrBefore(tUs)
    val a = samples[i]
    val b = samples[i + 1]
    if (a.tUs == tUs) return a
    val f = (tUs - a.tUs).toFloat() / (b.tUs - a.tUs).toFloat()
    return Sample(
        tUs = tUs,
        pressure = lerp(a.pressure, b.pressure, f),
        flow = lerp(a.flow, b.flow, f),
        weight = lerp(a.weight, b.weight, f),
        weightFlow = lerp(a.weightFlow, b.weightFlow, f),
        temperature = lerp(a.temperature, b.temperature, f),
        targetPressure = lerp(a.targetPressure, b.targetPressure, f),
        targetFlow = lerp(a.targetFlow, b.targetFlow, f),
        targetTemperature = lerp(a.targetTemperature, b.targetTemperature, f),
        targetWeight = lerp(a.targetWeight, b.targetWeight, f),
        phase = a.phase,
        brewing = a.brewing,
    )
}

/** Sample shown at video time [videoUs] when graph time 0 sits at [shotStartInVideoUs]. */
fun Shot.sampleAtVideoTime(videoUs: Long, shotStartInVideoUs: Long): Sample? = sampleAt(videoUs - shotStartInVideoUs)

/** Index of the last sample with `tUs <= t`; samples must be sorted and non-empty, t >= first. */
internal fun List<Sample>.indexAtOrBefore(t: Long): Int {
    var lo = 0
    var hi = size - 1
    while (lo < hi) {
        val mid = (lo + hi + 1) ushr 1
        if (this[mid].tUs <= t) lo = mid else hi = mid - 1
    }
    return lo
}

private fun lerp(a: Float?, b: Float?, f: Float): Float? =
    when {
        a == null -> null
        b == null -> a
        else -> a + (b - a) * f
    }

/**
 * Fills [Sample.weightFlow] from weight where the source does not report it, as the weight change over the trailing
 * [windowUs], never negative.
 */
fun List<Sample>.withDerivedWeightFlow(windowUs: Long = 1_000_000): List<Sample> {
    var j = 0
    return mapIndexed { i, s ->
        if (s.weightFlow != null || s.weight == null) return@mapIndexed s
        while (j < i && s.tUs - this[j].tUs > windowUs) j++
        var k = j
        while (k < i && this[k].weight == null) k++
        val ref = this[k]
        val dt = s.tUs - ref.tUs
        val wf = if (k == i || dt <= 0) 0f else ((s.weight - ref.weight!!) / (dt / 1_000_000f)).coerceAtLeast(0f)
        s.copy(weightFlow = wf)
    }
}
