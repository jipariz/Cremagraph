package app.onlyshots.camera

import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * A camera for the desktop development host: the controls answer, the clock runs, nothing is recorded. Enough to drive
 * the Record screen under Compose Hot Reload.
 */
class DesktopCameraController(private val dir: File) : CameraController {
    override val state: StateFlow<CameraState>
        field = MutableStateFlow(CameraState(previewReady = true))

    private var startedNs = 0L
    private var triggerUs: Long? = null

    override fun setZoom(zoom: Float) = state.update { it.copy(zoom = zoom.coerceIn(it.minZoom, it.maxZoom)) }

    override fun focusAt(x: Float, y: Float) = state.update { it.copy(focusLocked = true) }

    override fun unlockFocus() = state.update { it.copy(focusLocked = false) }

    override fun setMic(on: Boolean) = state.update { it.copy(micOn = on) }

    override fun setFourK(on: Boolean) = Unit

    override suspend fun arm(preRollSeconds: Int) {
        startedNs = System.nanoTime()
        triggerUs = null
    }

    override fun disarm() = Unit

    override fun trigger(): Long = ((System.nanoTime() - startedNs) / 1_000).also { triggerUs = it }

    override suspend fun stop(): Recording {
        val durationUs = (System.nanoTime() - startedNs) / 1_000
        return Recording(File(dir, "desktop.mp4").path, durationUs, hasAudio = false, triggerVideoUs = triggerUs)
    }

    override fun freeBytes(): Long = dir.apply { mkdirs() }.usableSpace
}
