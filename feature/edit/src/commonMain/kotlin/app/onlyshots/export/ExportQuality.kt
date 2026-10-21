package app.onlyshots.export

import app.onlyshots.core.model.Quality

/**
 * The quality an export to [to] starts on, out of the ones this video allows ([available], never empty): the one last
 * picked for that destination when the video allows it, else the destination's own default. The gallery keeps the
 * user's copy, so the best there is; a share goes to apps that squeeze it to about 1080p anyway, where a 4K file is
 * many times the size to send.
 */
fun exportQuality(to: ExportManager.Destination, available: List<Quality>, lastPicked: Quality?): Quality {
    if (lastPicked != null && lastPicked in available) return lastPicked
    return when (to) {
        ExportManager.Destination.GALLERY -> available.maxBy { it.shortSide }
        ExportManager.Destination.SHARE ->
            available.firstOrNull { it == Quality.HD_1080 } ?: available.minBy { it.shortSide }
    }
}
