package app.onlyshots.record

import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.record_allow_camera
import app.onlyshots.core.resources.record_open_settings
import app.onlyshots.core.resources.text
import app.onlyshots.record.CameraPermission.AllowCamera
import app.onlyshots.record.CameraPermission.OnStart
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CameraPermissionTest {
    @Test
    fun theFirstAnswerArmsWithWhatWasAllowed() {
        val p = CameraPermission().answer(camera = true, mic = false)
        assertTrue(p.camera)
        assertFalse(p.mic)
        assertTrue(p.answered)
        assertEquals(OnStart.Arm, p.onStart { error("not asked with the camera allowed") })
    }

    @Test
    fun aRefusalAtTheScreensOwnRequestStillLetsTheButtonAsk() {
        val p = CameraPermission().answer(camera = false, mic = false)
        assertEquals(AllowCamera.Ask, p.allowCamera)
        assertEquals(Res.string.record_allow_camera.text(), p.allowCameraLabel)
    }

    @Test
    fun aRefusalFromTheButtonTurnsItIntoOpenSettings() {
        val p = CameraPermission().answer(camera = false, mic = false).askingFromButton().answer(false, false)
        assertEquals(AllowCamera.OpenSettings, p.allowCamera)
        assertEquals(Res.string.record_open_settings.text(), p.allowCameraLabel)
        assertFalse(p.askedFromButton)
        // And stays so, whatever is answered after.
        assertEquals(AllowCamera.OpenSettings, p.answer(camera = true, mic = true).allowCamera)
    }

    @Test
    fun theButtonGrantedLeavesItAsking() {
        val p = CameraPermission().answer(false, false).askingFromButton().answer(camera = true, mic = true)
        assertEquals(AllowCamera.Ask, p.allowCamera)
        assertTrue(p.camera)
    }

    @Test
    fun comingBackFromTheSettingsAsksAgainOnlyOnceAnswered() {
        val refused = CameraPermission().answer(false, false)
        assertEquals(OnStart.AskAgain, refused.onStart { true })
        assertEquals(OnStart.Nothing, refused.onStart { false })
        // Before the first answer the screen's own request is under way: asking again would arm twice.
        assertEquals(OnStart.Nothing, CameraPermission().onStart { true })
    }
}
