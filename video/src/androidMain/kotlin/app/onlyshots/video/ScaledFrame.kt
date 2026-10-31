package app.onlyshots.video

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build

/**
 * A frame no wider than [maxWidthPx], scaled by the decoder rather than after the fact.
 *
 * `getFrameAtTime` always hands back the video's native resolution: an ARGB_8888 bitmap of about 8 MB at 1080p and
 * 33 MB at 4K, whatever the caller does with it next. Everything here wants a small picture — a 108 px filmstrip
 * cell, twelve luma readings — so the size goes to the decoder, as iOS already does with
 * `AVAssetImageGenerator.maximumSize` and the web with `cgThumbnail`'s `maxWidth`.
 *
 * `getScaledFrameAtTime` arrived in API 27 and the app's floor is 26, so one version of Android still decodes whole
 * and leaves the shrinking to the caller. The box is as tall as 16:9 of its width, so a portrait clip is bounded by
 * its width like a landscape one; the frame comes back with its aspect kept, and callers read its real dimensions.
 */
internal fun MediaMetadataRetriever.scaledFrameAt(atUs: Long, option: Int, maxWidthPx: Int): Bitmap? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        getScaledFrameAtTime(atUs, option, maxWidthPx, maxWidthPx * 16 / 9)
    } else {
        getFrameAtTime(atUs, option)
    }
