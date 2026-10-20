package app.onlyshots.core.model

import kotlin.time.Instant

internal fun shotOf(vararg samples: Sample, gaps: List<TimeRange> = emptyList()): Shot =
    Shot(
        id = "s1",
        source = SourceKind.GAGGIUINO,
        profileName = "Zer0",
        startedAt = Instant.parse("2026-10-11T08:14:00Z"),
        durationUs = samples.lastOrNull()?.tUs ?: 0L,
        finalWeight = samples.lastOrNull()?.weight,
        samples = samples.toList(),
        phaseStartsUs = listOf(0L),
        gaps = gaps,
    )

internal fun s(seconds: Double, pressure: Float? = null, weight: Float? = null, phase: Int? = null): Sample =
    Sample(tUs = (seconds * 1_000_000).toLong(), pressure = pressure, weight = weight, phase = phase)
