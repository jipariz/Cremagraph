package app.onlyshots.platform

import okio.Path

/** Opens the system share sheet for rendered videos. */
interface PlatformShare {
    /**
     * @param onDone called once the sheet is finished with the files, where the platform says so (iOS). Android gives
     *   no completion, so there the temp files wait for the 24 h sweep.
     */
    fun shareVideos(files: List<Path>, onDone: () -> Unit = {})
}

fun PlatformShare.shareVideo(file: Path) = shareVideos(listOf(file))
