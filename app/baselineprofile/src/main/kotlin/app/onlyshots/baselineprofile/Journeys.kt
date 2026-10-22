package app.onlyshots.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

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
    waitFor(words(RECORD_A_SHOT))
}

/**
 * The app's main path: record the sample shot and export it. Home searching for machines and scales, the camera armed
 * with the graph drawn live, the replayed shot recorded (it starts by itself once the camera is armed, and the take
 * stops itself after the shot), the editor it lands in, and the export to the gallery, to its end. Each run leaves one
 * video in the gallery's OnlyShots album.
 */
fun MacrobenchmarkScope.recordAndExportTheSampleShot() {
    tap(words(RECORD_A_SHOT))
    tap(words(TRY_A_SAMPLE_SHOT))
    tap(words(EXPORT), timeoutMs = TAKE_TIMEOUT_MS)
    tap(words(SAVE_TO_GALLERY))
    // The export sheet says so with the video's quality and length after it.
    waitFor(
        By.text(Pattern.compile(".*" + Pattern.quote(SAVED) + ".*", Pattern.CASE_INSENSITIVE)),
        timeoutMs = EXPORT_TIMEOUT_MS,
    )
}

/** Into the record screen with the sample shot, which starts filming by itself once the camera is armed. */
fun MacrobenchmarkScope.filmTheSampleShot() {
    tap(words(RECORD_A_SHOT))
    tap(words(TRY_A_SAMPLE_SHOT))
}

/** The sample take filmed to its end, and the editor it lands in. */
fun MacrobenchmarkScope.recordTheSampleShotIntoTheEditor() {
    filmTheSampleShot()
    waitFor(words(EXPORT), timeoutMs = TAKE_TIMEOUT_MS)
}

/**
 * The editor at work: the video played with its graph drawn over it, the timeline scrubbed back and forth, played
 * again. Taps the picture to play and pause, drags along the timeline's strip.
 */
fun MacrobenchmarkScope.playAndScrubInTheEditor() {
    val timeline = device.wait(Until.findObject(By.desc(TIMELINE)), UI_TIMEOUT_MS) ?: error("No timeline on screen")
    val strip = timeline.visibleBounds
    // The picture sits above the timeline: its middle is free of the graph's toolbar.
    val pictureX = device.displayWidth / 2
    val pictureY = strip.top / 2
    device.click(pictureX, pictureY)
    Thread.sleep(PLAY_MS)
    device.click(pictureX, pictureY)
    // The strip's middle band: the filmstrip, away from the trim handles at its ends.
    val y = strip.top + strip.height() * 2 / 3
    val left = strip.left + strip.width() / 6
    val right = strip.right - strip.width() / 6
    repeat(SCRUBS) {
        device.swipe(left, y, right, y, SCRUB_STEPS)
        device.swipe(right, y, left, y, SCRUB_STEPS)
    }
    device.click(pictureX, pictureY)
    Thread.sleep(PLAY_MS)
    device.click(pictureX, pictureY)
}

private val PERMISSIONS =
    listOf(
        "android.permission.CAMERA",
        "android.permission.RECORD_AUDIO",
        "android.permission.BLUETOOTH_SCAN",
        "android.permission.BLUETOOTH_CONNECT",
        "android.permission.POST_NOTIFICATIONS",
    )

/**
 * A control by its words, whatever their case: labels are drawn in capitals (the strings are not), so "Record a shot"
 * shows as "RECORD A SHOT".
 */
private fun words(text: String): BySelector = By.text(Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE))

// The words the journeys look for (core/resources: home_start_record_title, home_try_sample, edit_export,
// edit_phone_save, edit_phone_saved, edit_timeline_description).
private const val RECORD_A_SHOT = "Record a shot"
private const val TRY_A_SAMPLE_SHOT = "no machine? try a sample shot"
private const val EXPORT = "export"
private const val SAVE_TO_GALLERY = "save to gallery"
private const val SAVED = "In your gallery, in the OnlyShots album"
private const val TIMELINE = "timeline: drag the ends to trim, drag the flag to place the shot"

private const val UI_TIMEOUT_MS = 10_000L

/** How long the editor plays each time, and the timeline drags: back and forth, each over this many 5 ms steps. */
private const val PLAY_MS = 6_000L
private const val SCRUBS = 3
private const val SCRUB_STEPS = 200

/** The sample shot runs about 45 s with its pre-roll and tail, then saves. */
private const val TAKE_TIMEOUT_MS = 120_000L

/** A 45 s take rendered with its graph at 1080p, on a slow phone. */
private const val EXPORT_TIMEOUT_MS = 300_000L
