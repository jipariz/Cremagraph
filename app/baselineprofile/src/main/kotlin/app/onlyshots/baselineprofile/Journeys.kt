package app.onlyshots.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Until

/*
 * The paths through the app worth compiling ahead of time, as UI Automator steps. They find things by the words on
 * screen (core/resources' strings), so a reworded button is a broken journey: it fails loudly rather than profiling
 * less.
 */

/** The app under test, as the baseline profile plugin passes it. */
val targetPackage: String =
    InstrumentationRegistry.getArguments().getString("targetAppId")
        ?: error("targetAppId is not passed: run this through the baseline profile plugin's tasks")

/** What the journeys would otherwise stop at a system prompt for: the camera, the microphone, the scale, notices. */
fun MacrobenchmarkScope.grantPermissions() {
    for (permission in PERMISSIONS) {
        device.executeShellCommand("pm grant $packageName $permission")
    }
}

/** Waits for [selector] and taps it. */
fun MacrobenchmarkScope.tap(selector: BySelector, timeoutMs: Long = UI_TIMEOUT_MS) {
    val found =
        device.wait(Until.findObject(selector), timeoutMs) ?: error("Not on screen after $timeoutMs ms: $selector")
    found.click()
}

/** Waits until [selector] is on screen. */
fun MacrobenchmarkScope.waitFor(selector: BySelector, timeoutMs: Long = UI_TIMEOUT_MS) {
    check(device.wait(Until.hasObject(selector), timeoutMs)) { "Not on screen after $timeoutMs ms: $selector" }
}

/** Cold start to the start screen, its two ways in on screen. */
fun MacrobenchmarkScope.startToStartScreen() {
    pressHome()
    startActivityAndWait()
    waitFor(By.text(RECORD_A_SHOT))
}

/**
 * The app's main path: record the sample shot and export it. Home searching for machines and scales, the camera armed
 * with the graph drawn live, the replayed shot recorded (it starts by itself once the camera is armed, and the take
 * stops itself after the shot), the editor it lands in, and the export to the gallery, to its end. Each run leaves one
 * video in the gallery's OnlyShots album.
 */
fun MacrobenchmarkScope.recordAndExportTheSampleShot() {
    tap(By.text(RECORD_A_SHOT))
    tap(By.text(TRY_A_SAMPLE_SHOT))
    tap(By.text(EXPORT), timeoutMs = TAKE_TIMEOUT_MS)
    tap(By.text(SAVE_TO_GALLERY))
    // The export sheet says so with the video's quality and length after it.
    waitFor(By.textContains(SAVED), timeoutMs = EXPORT_TIMEOUT_MS)
}

private val PERMISSIONS =
    listOf(
        "android.permission.CAMERA",
        "android.permission.RECORD_AUDIO",
        "android.permission.BLUETOOTH_SCAN",
        "android.permission.BLUETOOTH_CONNECT",
        "android.permission.POST_NOTIFICATIONS",
    )

// The words the journeys look for (core/resources: home_start_record_title, home_try_sample, edit_export,
// edit_phone_save, edit_phone_saved).
private const val RECORD_A_SHOT = "Record a shot"
private const val TRY_A_SAMPLE_SHOT = "no machine? try a sample shot"
private const val EXPORT = "export"
private const val SAVE_TO_GALLERY = "save to gallery"
private const val SAVED = "In your gallery, in the OnlyShots album"

private const val UI_TIMEOUT_MS = 10_000L

/** The sample shot runs about 45 s with its pre-roll and tail, then saves. */
private const val TAKE_TIMEOUT_MS = 120_000L

/** A 45 s take rendered with its graph at 1080p, on a slow phone. */
private const val EXPORT_TIMEOUT_MS = 300_000L
