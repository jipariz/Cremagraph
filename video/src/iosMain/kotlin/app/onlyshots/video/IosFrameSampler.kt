@file:OptIn(ExperimentalForeignApi::class)

package app.onlyshots.video

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.get
import kotlinx.cinterop.reinterpret
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path
import platform.AVFoundation.AVAssetImageGenerator
import platform.AVFoundation.AVURLAsset
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextGetData
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSURL

class IosFrameSampler : FrameSampler {
    override suspend fun lumaAt(file: Path, atUs: Long, points: List<Pair<Float, Float>>): List<Float>? =
        withContext(Dispatchers.Default) {
            val asset = AVURLAsset.URLAssetWithURL(NSURL.fileURLWithPath(file.toString()), null)
            val generator = AVAssetImageGenerator(asset)
            generator.appliesPreferredTrackTransform = true
            generator.requestedTimeToleranceBefore = CMTimeMake(0, 1)
            generator.requestedTimeToleranceAfter = CMTimeMake(0, 1)
            val image = generator.copyCGImageAtTime(CMTimeMake(atUs, 1_000_000), null, null) ?: return@withContext null
            val w = CGImageGetWidth(image)
            val h = CGImageGetHeight(image)
            val space = CGColorSpaceCreateDeviceRGB()
            val ctx =
                CGBitmapContextCreate(
                    null,
                    w,
                    h,
                    8uL,
                    w * 4uL,
                    space,
                    CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value,
                )
            CGContextDrawImage(ctx, CGRectMake(0.0, 0.0, w.toDouble(), h.toDouble()), image)
            val px = CGBitmapContextGetData(ctx)!!.reinterpret<UByteVar>()
            val result = points.map { (x, y) ->
                val ix = (x * w.toFloat()).toInt().coerceIn(0, w.toInt() - 1)
                // Bitmap memory is top row first.
                val iy = (y * h.toFloat()).toInt().coerceIn(0, h.toInt() - 1)
                val o = (iy * w.toInt() + ix) * 4
                (0.2126f * px[o].toInt() + 0.7152f * px[o + 1].toInt() + 0.0722f * px[o + 2].toInt()) / 255f
            }
            CGContextRelease(ctx)
            CGColorSpaceRelease(space)
            CGImageRelease(image)
            result
        }
}
