package app.onlyshots.export

import app.onlyshots.core.model.Quality
import app.onlyshots.export.ExportManager.Destination
import kotlin.test.Test
import kotlin.test.assertEquals

class ExportQualityTest {
    private val both = listOf(Quality.HD_1080, Quality.UHD_4K)
    private val hdOnly = listOf(Quality.HD_1080)

    @Test
    fun theGalleryGetsTheBestAndAShare1080p() {
        assertEquals(Quality.UHD_4K, exportQuality(Destination.GALLERY, both, lastPicked = null))
        assertEquals(Quality.HD_1080, exportQuality(Destination.SHARE, both, lastPicked = null))
    }

    @Test
    fun theLastPickWins() {
        assertEquals(Quality.HD_1080, exportQuality(Destination.GALLERY, both, lastPicked = Quality.HD_1080))
        assertEquals(Quality.UHD_4K, exportQuality(Destination.SHARE, both, lastPicked = Quality.UHD_4K))
    }

    @Test
    fun aPickTheVideoCannotMakeFallsBack() {
        // 4K picked for a 4K take; the next video is 1080p.
        assertEquals(Quality.HD_1080, exportQuality(Destination.GALLERY, hdOnly, lastPicked = Quality.UHD_4K))
        assertEquals(Quality.HD_1080, exportQuality(Destination.SHARE, hdOnly, lastPicked = Quality.UHD_4K))
    }
}
