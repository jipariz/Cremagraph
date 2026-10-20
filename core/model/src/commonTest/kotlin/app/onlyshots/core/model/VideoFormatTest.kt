package app.onlyshots.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VideoFormatTest {
    @Test
    fun everyFormatHasItsSizeAtBothQualities() {
        val sizes = VideoFormat.entries.associateWith { Quality.HD_1080.size(it) to Quality.UHD_4K.size(it) }
        assertEquals(Size(1080, 1920) to Size(2160, 3840), sizes[VideoFormat.PORTRAIT_9_16])
        assertEquals(Size(1080, 1350) to Size(2160, 2700), sizes[VideoFormat.FEED_4_5])
        assertEquals(Size(1080, 1080) to Size(2160, 2160), sizes[VideoFormat.SQUARE_1_1])
        assertEquals(Size(1920, 1080) to Size(3840, 2160), sizes[VideoFormat.LANDSCAPE_16_9])
    }

    @Test
    fun portraitKeepsTheNumbersItAlwaysHad() {
        assertEquals(NormRect.REF_H, VideoFormat.PORTRAIT_9_16.refH)
        assertEquals(NormRect.Default, NormRect.default(VideoFormat.PORTRAIT_9_16))
        assertEquals(NormRect.MIN_W, NormRect.minW(VideoFormat.PORTRAIT_9_16))
        assertEquals(NormRect.MIN_H, NormRect.minH(VideoFormat.PORTRAIT_9_16))
    }

    @Test
    fun theDefaultGraphFitsEveryFormatAtItsPortraitSize() {
        for (f in VideoFormat.entries) {
            val r = NormRect.default(f)
            assertTrue(r.x >= 0f && r.y >= 0f && r.right <= 1f && r.bottom <= 1f, "$f: $r")
            // 170 reference px tall, against the short side, in every format.
            assertEquals(170f, r.h * f.refH, 0.01f, "$f")
        }
        // 16:9: no wider than in portrait, at the bottom left.
        val wide = NormRect.default(VideoFormat.LANDSCAPE_16_9)
        assertEquals(362f, wide.w * VideoFormat.LANDSCAPE_16_9.refW, 0.01f)
        assertEquals(14f, wide.x * VideoFormat.LANDSCAPE_16_9.refW, 0.01f)
    }

    @Test
    fun aRefittedGraphKeepsItsSizeAgainstTheShortSideAndStaysInside() {
        val r = NormRect.Default
        val square = r.refit(VideoFormat.PORTRAIT_9_16, VideoFormat.SQUARE_1_1)
        assertEquals(r.h * VideoFormat.PORTRAIT_9_16.refH, square.h * VideoFormat.SQUARE_1_1.refH, 0.01f)
        assertTrue(square.bottom <= 1f && square.y >= 0f)
        val wide = r.refit(VideoFormat.PORTRAIT_9_16, VideoFormat.LANDSCAPE_16_9)
        assertEquals(170f, wide.h * VideoFormat.LANDSCAPE_16_9.refH, 0.01f)
        assertTrue(wide.right <= 1f && wide.bottom <= 1f)
        assertEquals(r, r.refit(VideoFormat.PORTRAIT_9_16, VideoFormat.PORTRAIT_9_16))
    }

    @Test
    fun aPortraitPictureInASquareFrameShowsItsMiddle() {
        val portrait = 9f / 16f
        // The square's top edge is the picture's 21.875 %: (1 − 9/16) / 2 off each end.
        val (x, y) = sourcePoint(0.5f, 0f, portrait, VideoFormat.SQUARE_1_1, Reframe())
        assertEquals(0.5f, x, 1e-5f)
        assertEquals(0.21875f, y, 1e-5f)
        // Unchanged where the picture already has the frame's shape.
        val (sx, sy) = sourcePoint(0.2f, 0.7f, portrait, VideoFormat.PORTRAIT_9_16, Reframe())
        assertEquals(0.2f, sx, 1e-5f)
        assertEquals(0.7f, sy, 1e-5f)
    }

    @Test
    fun aLandscapePictureInAPortraitFrameShowsItsMiddleThird() {
        val (x, _) = sourcePoint(0f, 0.5f, 16f / 9f, VideoFormat.PORTRAIT_9_16, Reframe())
        // (9/16) / (16/9) of the width shows, about 31.6 %, centred.
        assertEquals(0.5f - 0.5f * (9f / 16f) / (16f / 9f), x, 1e-5f)
    }

    @Test
    fun theReframeIsUndoneFirst() {
        val r = Reframe(zoom = 2f, x = 0.25f, y = 0.5f)
        // The output's middle is the reframe's centre, then the cover crop.
        val (x, y) = sourcePoint(0.5f, 0.5f, 9f / 16f, VideoFormat.SQUARE_1_1, r)
        assertEquals(0.25f, x, 1e-5f)
        assertEquals(0.5f, y, 1e-5f)
    }

    @Test
    fun snappingKnowsWhereEachFormatIsCovered() {
        val free = NormRect(0.05f, 0.125f, 0.5f, 0.2f)
        val portrait = OverlaySnap.snap(free, 0.05f, 0.05f)
        assertEquals(OverlaySnap.Place.TOP_BELOW_HEADER, portrait.vertical?.place)
        val square = OverlaySnap.snap(free.copy(y = 0.04f), 0.05f, 0.05f, VideoFormat.SQUARE_1_1)
        assertEquals(OverlaySnap.Place.TOP, square.vertical?.place)
        assertEquals(14f / 390f, square.rect.y, 1e-5f)
    }

    @Test
    fun recordingSettingsRefitTheGraphWithTheFormat() {
        val s = RecordingSettings().withFormat(VideoFormat.LANDSCAPE_16_9)
        assertEquals(VideoFormat.LANDSCAPE_16_9, s.format)
        assertEquals(NormRect.Default.refit(VideoFormat.PORTRAIT_9_16, VideoFormat.LANDSCAPE_16_9), s.overlay.rect)
    }

    @Test
    fun aPortraitPictureInALandscapeFrameCanBeMovedUpAndDownAllTheWay() {
        val portrait = 9f / 16f
        val wide = VideoFormat.LANDSCAPE_16_9
        // The picture is about 3.16 frames tall here: the centre can go from its top band to its bottom band.
        val top = Reframe(1f, 0.5f, -5f).clamped(portrait, wide)
        val reach = wide.ratio / portrait
        assertEquals(0.5f - reach / 2f + 0.5f, top.y, 1e-5f)
        // The frame's top edge is then the picture's top edge.
        assertEquals(0f, sourcePoint(0.5f, 0f, portrait, wide, top).second, 1e-5f)
        // Across, it fills the frame exactly: no room until it is zoomed.
        assertEquals(0.5f, Reframe(1f, 0.9f, 0.5f).clamped(portrait, wide).x, 1e-5f)
        assertEquals(0.75f, Reframe(2f, 0.9f, 0.5f).clamped(portrait, wide).x, 1e-5f)
        // Without a known shape: the old limits.
        assertEquals(Reframe(), Reframe(1f, 0.5f, -5f).clamped())
    }
}
