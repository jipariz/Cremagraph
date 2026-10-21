package app.onlyshots.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * How smooth the two busiest screens are, compiled as a store install is: the record screen while the graph is drawn
 * live over the camera, and the editor playing and scrubbing a take. Frame durations and overruns at P50/P90/P95/P99.
 * `./gradlew :app:baselineprofile:connectedBenchmarkReleaseAndroidTest
 * -Pandroid.testInstrumentationRunnerArguments.class=app.onlyshots.baselineprofile.FrameTimingBenchmarks`.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class FrameTimingBenchmarks {
    @get:Rule val rule = MacrobenchmarkRule()

    /** Filming the sample shot: the camera, the graph and the readings, all live. */
    @Test
    fun recording() =
        rule.measureRepeated(
            packageName = targetPackage,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
            startupMode = StartupMode.COLD,
            iterations = ITERATIONS,
            setupBlock = { grantPermissions() },
        ) {
            startToStartScreen()
            filmTheSampleShot()
            Thread.sleep(RECORDING_MS)
        }

    /** The editor a take lands in: playback with the graph drawn over it, and the timeline scrubbed. */
    @Test
    fun editing() =
        rule.measureRepeated(
            packageName = targetPackage,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
            startupMode = null,
            iterations = ITERATIONS,
            setupBlock = {
                // From scratch: a take left running (the recording test's) would bring back its own screen.
                killProcess()
                grantPermissions()
                startToStartScreen()
                recordTheSampleShotIntoTheEditor()
            },
        ) {
            playAndScrubInTheEditor()
        }
}

/** Each editing run films a whole sample take first, so a few. */
private const val ITERATIONS = 3

/** Long enough for the shot to start and its graph to draw for a while. */
private const val RECORDING_MS = 15_000L
