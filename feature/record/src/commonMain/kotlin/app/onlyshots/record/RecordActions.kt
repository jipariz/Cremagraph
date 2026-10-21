package app.onlyshots.record

import app.onlyshots.core.model.OverlaySettings
import app.onlyshots.core.model.RecordingSettings

/**
 * What the record screen's body can ask for. [RecordViewModel] does it for real; a screenshot preview passes [None], so
 * the whole screen renders from plain state.
 */
interface RecordActions {
    fun recordPressed()

    fun simulateShot()

    fun setZoom(zoom: Float)

    fun focusAt(x: Float, y: Float)

    fun unlockFocus()

    fun pickStyle(id: String)

    fun renameStyle(id: String, name: String)

    fun deleteStyle(id: String)

    fun updateOverlay(change: (OverlaySettings) -> OverlaySettings)

    fun updateSettings(change: (RecordingSettings) -> RecordingSettings)

    /** Does nothing: for previews. */
    object None : RecordActions {
        override fun recordPressed() = Unit

        override fun simulateShot() = Unit

        override fun setZoom(zoom: Float) = Unit

        override fun focusAt(x: Float, y: Float) = Unit

        override fun unlockFocus() = Unit

        override fun pickStyle(id: String) = Unit

        override fun renameStyle(id: String, name: String) = Unit

        override fun deleteStyle(id: String) = Unit

        override fun updateOverlay(change: (OverlaySettings) -> OverlaySettings) = Unit

        override fun updateSettings(change: (RecordingSettings) -> RecordingSettings) = Unit
    }
}
