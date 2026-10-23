package app.onlyshots.record

import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.UiText
import app.onlyshots.core.resources.record_allow_camera
import app.onlyshots.core.resources.record_open_settings
import app.onlyshots.core.resources.text

/**
 * Where asking for the camera and the microphone stands on the record screen. The microphone is always on when allowed;
 * the editor mutes a take that should be silent.
 */
internal data class CameraPermission(
    val camera: Boolean = false,
    val mic: Boolean = false,
    /**
     * The first answer is in: until then the screen's own first request is under way, and asking again would arm the
     * camera twice.
     */
    val answered: Boolean = false,
    /** The request under way came from "allow camera". */
    val askedFromButton: Boolean = false,
    /**
     * A request from "allow camera" came back refused: the system will not ask again, so the button sends the user to
     * the app's settings instead.
     */
    val refusedFromButton: Boolean = false,
) {
    /** The answer to a request: from the prompt, or read again without one. */
    fun answer(camera: Boolean, mic: Boolean): CameraPermission =
        copy(
            camera = camera,
            mic = mic,
            answered = true,
            askedFromButton = false,
            refusedFromButton = refusedFromButton || (askedFromButton && !camera),
        )

    /** What "allow camera" does now. */
    val allowCamera: AllowCamera
        get() = if (refusedFromButton) AllowCamera.OpenSettings else AllowCamera.Ask

    /** "allow camera" asks: its answer, if a refusal, turns the button into "open settings". */
    fun askingFromButton(): CameraPermission = copy(askedFromButton = true)

    /** The words on the button, as [allowCamera] decides. */
    val allowCameraLabel: UiText
        get() =
            when (allowCamera) {
                AllowCamera.Ask -> Res.string.record_allow_camera.text()
                AllowCamera.OpenSettings -> Res.string.record_open_settings.text()
            }

    /**
     * What coming back to the screen does: re-arm with the camera; back from the system settings with the camera
     * allowed ([allowedNow] checks without asking), read both answers again (no prompt for what is already decided).
     */
    fun onStart(allowedNow: () -> Boolean): OnStart =
        when {
            camera -> OnStart.Arm
            answered && allowedNow() -> OnStart.AskAgain
            else -> OnStart.Nothing
        }

    enum class AllowCamera {
        Ask,
        OpenSettings,
    }

    enum class OnStart {
        Arm,
        AskAgain,
        Nothing,
    }
}
