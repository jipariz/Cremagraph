package app.onlyshots.edit

import app.onlyshots.core.model.OverlaySettings
import app.onlyshots.core.model.Quality
import app.onlyshots.core.model.Reframe
import app.onlyshots.core.model.ShotSummary
import app.onlyshots.core.model.VideoFormat
import app.onlyshots.export.ExportManager

/**
 * What the editor's body can ask for. [EditViewModel] does it for real; a screenshot preview passes a stand-in, so the
 * whole screen renders from plain state.
 */
interface EditActions {
    fun openSheet(sheet: EditSheet)

    fun closeSheet()

    /** Loads the machine's recent shots into the state, for a shot menu. */
    fun loadShots()

    /** The finished export was seen: its banner goes. */
    fun acknowledgeExport()

    fun clearMessage()

    fun undo()

    fun redo()

    fun pickStyle(id: String)

    fun renameStyle(id: String, name: String)

    fun deleteStyle(id: String)

    fun overlay(push: Boolean = true, change: (OverlaySettings) -> OverlaySettings)

    fun pickShot(summary: ShotSummary)

    fun findStart()

    /** Puts the shot start where the shot's clock and the video's agree. */
    fun matchByClock()

    /** The video's shape: the graph refitted to it and the crop kept where it was. */
    fun setFormat(format: VideoFormat)

    /** Zooms and moves the picture; [push] false while a gesture is in flight. */
    fun reframe(push: Boolean, change: (Reframe) -> Reframe)

    fun trim(inUs: Long, outUs: Long, push: Boolean)

    fun setShotStart(videoUs: Long, push: Boolean)

    /** Lets a recording's shot start be moved by hand, for when the machine or scale started the take late or early. */
    fun unlockShotStart()

    fun saveAndExport(
        quality: Quality,
        withSound: Boolean,
        to: ExportManager.Destination = ExportManager.Destination.SHARE,
    )

    fun cancelExport()

    /** The export screen's ask, answered: [allow] to be told outside the app when an export is done, else not now. */
    fun answerExportNotifications(allow: Boolean) = Unit

    /** The video just saved, in the gallery's own app. */
    fun openSavedVideo() = Unit

    /** The share sheet for the video just saved. */
    fun shareSavedVideo() = Unit

    /** The quality exports [to] that destination make from now on, remembered for the next videos. */
    fun pickQuality(to: ExportManager.Destination, quality: Quality)

    /** Does nothing: for previews. */
    object None : EditActions {
        override fun openSheet(sheet: EditSheet) = Unit

        override fun closeSheet() = Unit

        override fun loadShots() = Unit

        override fun acknowledgeExport() = Unit

        override fun clearMessage() = Unit

        override fun undo() = Unit

        override fun redo() = Unit

        override fun pickStyle(id: String) = Unit

        override fun renameStyle(id: String, name: String) = Unit

        override fun deleteStyle(id: String) = Unit

        override fun overlay(push: Boolean, change: (OverlaySettings) -> OverlaySettings) = Unit

        override fun pickShot(summary: ShotSummary) = Unit

        override fun findStart() = Unit

        override fun matchByClock() = Unit

        override fun setFormat(format: VideoFormat) = Unit

        override fun reframe(push: Boolean, change: (Reframe) -> Reframe) = Unit

        override fun trim(inUs: Long, outUs: Long, push: Boolean) = Unit

        override fun setShotStart(videoUs: Long, push: Boolean) = Unit

        override fun unlockShotStart() = Unit

        override fun saveAndExport(quality: Quality, withSound: Boolean, to: ExportManager.Destination) = Unit

        override fun cancelExport() = Unit

        override fun pickQuality(to: ExportManager.Destination, quality: Quality) = Unit
    }
}
