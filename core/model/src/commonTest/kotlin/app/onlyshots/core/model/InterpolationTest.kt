package app.onlyshots.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InterpolationTest {
    private val shot =
        shotOf(
            s(0.0, pressure = 0f, weight = 0f, phase = 1),
            s(1.0, pressure = 2f, weight = null, phase = 1),
            s(2.0, pressure = 6f, weight = 4f, phase = 2),
        )

    @Test
    fun interpolatesLinearlyBetweenSamples() {
        assertEquals(1f, shot.sampleAt(500_000)!!.pressure!!, 1e-4f)
        assertEquals(4f, shot.sampleAt(1_500_000)!!.pressure!!, 1e-4f)
    }

    @Test
    fun exactSampleTimeReturnsThatSample() {
        assertEquals(2f, shot.sampleAt(1_000_000)!!.pressure)
    }

    @Test
    fun discreteFieldsComeFromEarlierNeighbour() {
        assertEquals(1, shot.sampleAt(1_999_999)!!.phase)
        assertEquals(2, shot.sampleAt(2_000_000)!!.phase)
    }

    @Test
    fun missingNeighbourValueHoldsTheKnownOne() {
        // weight is null at 1 s: between 0 s and 1 s it holds 0, between 1 s and 2 s it is unknown.
        assertEquals(0f, shot.sampleAt(400_000)!!.weight)
        assertNull(shot.sampleAt(1_500_000)!!.weight)
    }

    @Test
    fun clampsOutsideTheShot() {
        assertEquals(0f, shot.sampleAt(-3_000_000)!!.pressure)
        assertEquals(6f, shot.sampleAt(9_000_000)!!.pressure)
        assertEquals(9_000_000, shot.sampleAt(9_000_000)!!.tUs)
    }

    @Test
    fun gapHasNoValues() {
        val gapped = shot.copy(gaps = listOf(TimeRange(1_200_000, 1_800_000)))
        assertNull(gapped.sampleAt(1_500_000)!!.pressure)
        assertEquals(6f, gapped.sampleAt(2_000_000)!!.pressure)
    }

    @Test
    fun videoTimeIsOffsetByShotStart() {
        assertEquals(
            4f,
            shot.sampleAtVideoTime(videoUs = 4_500_000, shotStartInVideoUs = 3_000_000)!!.pressure!!,
            1e-4f,
        )
    }

    @Test
    fun emptyShotHasNoSample() {
        assertNull(shotOf().sampleAt(0))
    }

    @Test
    fun derivesWeightFlowOverTrailingWindow() {
        val samples = (0..20).map { i -> Sample(tUs = i * 100_000L, weight = i * 0.2f) } // 2 g/s
        val derived = samples.withDerivedWeightFlow(windowUs = 1_000_000)
        assertEquals(0f, derived.first().weightFlow)
        assertEquals(2f, derived.last().weightFlow!!, 1e-3f)
    }

    @Test
    fun derivedWeightFlowNeverNegativeAndKeepsSourceValues() {
        val samples =
            listOf(
                    Sample(tUs = 0, weight = 5f),
                    Sample(tUs = 500_000, weight = 4f),
                    Sample(tUs = 1_000_000, weight = 4f, weightFlow = 1.5f),
                )
                .withDerivedWeightFlow()
        assertEquals(0f, samples[1].weightFlow)
        assertEquals(1.5f, samples[2].weightFlow)
    }
}
