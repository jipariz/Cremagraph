package app.onlyshots.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Writes the app's baseline profile: `./gradlew :app:androidApp:generateBaselineProfile` with a phone connected (API
 * 33+, or 28+ rooted). Startup goes into the startup profile too, which also lays out the dex files for it.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test
    fun startup() =
        rule.collect(packageName = targetPackage, includeInStartupProfile = true) {
            grantPermissions()
            startToStartScreen()
        }

    @Test
    fun journeys() =
        rule.collect(packageName = targetPackage, maxIterations = JOURNEY_ITERATIONS) {
            grantPermissions()
            startToStartScreen()
            recordAndExportTheSampleShot()
        }
}

/** Each run records and exports a whole sample take, so fewer than the default 15. */
private const val JOURNEY_ITERATIONS = 3
