package app.onlyshots.uitests

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The installed app on a device, end to end: the real camera, Media3's export and the gallery. Controls are found by
 * their test tags (app.onlyshots.TestTags, as resource ids), never by their words or looks. The tags are repeated here
 * as strings: this module tests the APK and does not link the app's code.
 */
@RunWith(AndroidJUnit4::class)
class AppSmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    // The tests run in their own process (self-instrumenting), so the app's id comes from the build, not the context.
    private val app = checkNotNull(InstrumentationRegistry.getArguments().getString("targetAppId")) { "no targetAppId" }

    @Before
    fun launch() {
        for (permission in PERMISSIONS) device.executeShellCommand("pm grant $app $permission")
        device.setOrientationNatural()
        device.pressHome()
        val intent =
            checkNotNull(instrumentation.context.packageManager.getLaunchIntentForPackage(app)) {
                "$app is not installed"
            }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        instrumentation.context.startActivity(intent)
        await("screen.start", LAUNCH_TIMEOUT_MS)
    }

    @After
    fun closeApp() {
        device.setOrientationNatural()
        device.executeShellCommand("am force-stop $app")
    }

    /** Start → record a shot → the sample shot → record → stop → Edit → back asks → keep editing → export → saved. */
    @Test
    fun recordsTheSampleShotAndExportsIt() {
        tap("start.record")
        await("screen.home")
        tap("home.sample")
        await("screen.record")
        // The camera arms first; then a take of a few seconds.
        Thread.sleep(ARM_MS)
        tap("record.button")
        Thread.sleep(TAKE_MS)
        // The sample shot's replay may already have ended the take (auto-stop); otherwise stop it by hand.
        device.findObject(By.res("record.button"))?.click()
        await("screen.edit", SAVE_TIMEOUT_MS)
        device.pressBack()
        tap("dialog.confirm")
        await("screen.edit")
        tap("edit.export")
        tap("export.save")
        await("export.next", EXPORT_TIMEOUT_MS)
        tap("export.next")
        await("screen.home")
    }

    /** Start → compose a video → Open → back to Start. */
    @Test
    fun opensComposeAVideoAndComesBack() {
        tap("start.compose")
        await("screen.open")
        await("open.pickVideo")
        tap("back")
        await("screen.start")
    }

    /** The record screen turned on its side keeps its controls (the button moves to the trailing edge). */
    @Test
    fun recordScreenTurnsOnItsSide() {
        tap("start.record")
        tap("home.sample")
        await("screen.record")
        device.setOrientationLeft()
        await("record.button")
        device.setOrientationNatural()
        await("record.button")
    }

    private fun await(tag: String, timeoutMs: Long = UI_TIMEOUT_MS) {
        check(device.wait(Until.hasObject(By.res(tag)), timeoutMs)) { "$tag not on screen after $timeoutMs ms" }
    }

    private fun tap(tag: String, timeoutMs: Long = UI_TIMEOUT_MS) {
        val found =
            device.wait(Until.findObject(By.res(tag)), timeoutMs) ?: error("$tag not on screen after $timeoutMs ms")
        found.click()
    }

    private companion object {
        val PERMISSIONS =
            listOf(
                "android.permission.CAMERA",
                "android.permission.RECORD_AUDIO",
                "android.permission.BLUETOOTH_SCAN",
                "android.permission.BLUETOOTH_CONNECT",
                "android.permission.POST_NOTIFICATIONS",
            )
        const val UI_TIMEOUT_MS = 10_000L
        const val LAUNCH_TIMEOUT_MS = 20_000L
        const val ARM_MS = 2_000L
        const val TAKE_MS = 3_000L
        const val SAVE_TIMEOUT_MS = 30_000L
        const val EXPORT_TIMEOUT_MS = 180_000L
    }
}
