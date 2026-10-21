package app.onlyshots.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What the profile buys: cold start compiled the way a fresh install from the store is (the profile installed) against
 * not compiled at all. `./gradlew :app:baselineprofile:connectedBenchmarkReleaseAndroidTest` with a phone connected.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class StartupBenchmarks {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test fun withoutCompilation() = startup(CompilationMode.None())

    @Test fun withBaselineProfile() = startup(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun startup(mode: CompilationMode) =
        rule.measureRepeated(
            packageName = targetPackage,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = mode,
            startupMode = StartupMode.COLD,
            iterations = 10,
            setupBlock = { grantPermissions() },
        ) {
            startToStartScreen()
        }
}
