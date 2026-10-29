package app.onlyshots.style

import app.onlyshots.core.model.style.GraphStyle
import app.onlyshots.core.model.style.Reading
import app.onlyshots.core.resources.raw
import app.onlyshots.overlay.GraphStyles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

class StyleEditorRulesTest {
    @Test
    fun stepFrameStaysOnTheFrames() {
        assertEquals(3, stepFrame(2, 1, 12))
        assertEquals(0, stepFrame(0, -1, 12))
        assertEquals(11, stepFrame(11, 3, 12))
        // No frames yet: always the first.
        assertEquals(0, stepFrame(5, 0, 0))
        // A frame index from a longer list, clamped to the shorter one now loaded.
        assertEquals(2, stepFrame(7, 0, 3))
    }

    @Test
    fun dragStepsAFramePerStepAgainstTheFinger() {
        assertEquals(FrameDrag(0, 10f), dragFrames(0f, 10f, 24f))
        // Right goes back, carrying what is left over.
        assertEquals(FrameDrag(-1, 6f), dragFrames(10f, 20f, 24f))
        // Left goes on, several at once on a fast flick.
        assertEquals(FrameDrag(2, -2f), dragFrames(0f, -50f, 24f))
        // Exactly a step is not yet a step, as before.
        assertEquals(FrameDrag(0, 24f), dragFrames(0f, 24f, 24f))
    }

    @Test
    fun captionSaysWhatThePictureIs() {
        assertEquals(PreviewCaption.LIVE, previewCaption(live = true, frameCount = 12))
        assertEquals(PreviewCaption.DRAG, previewCaption(live = false, frameCount = 2))
        assertEquals(PreviewCaption.RECORD, previewCaption(live = false, frameCount = 1))
        assertEquals(PreviewCaption.RECORD, previewCaption(live = false, frameCount = 0))
        assertTrue(canDragFrames(live = false, frameCount = 2))
        assertFalse(canDragFrames(live = true, frameCount = 12))
        assertFalse(canDragFrames(live = false, frameCount = 1))
    }

    @Test
    fun namesAreCutAndTrimmed() {
        assertEquals(STYLE_NAME_MAX, typedName("x".repeat(40)).length)
        assertEquals("Aero", typedName("Aero"))
        assertEquals("Mine", renamed("  Mine ", "Aero"))
        assertNull(renamed("   ", "Aero"))
        assertNull(renamed(" Aero ", "Aero"))
    }

    @Test
    fun saveWinsOverAColour() {
        val target = ColorTarget("grid".raw(), { 0 }, { s, _ -> s })
        assertEquals(StyleSheet.Save, styleSheet(saving = true, color = target))
        assertEquals(StyleSheet.Color(target), styleSheet(saving = false, color = target))
        assertNull(styleSheet(saving = false, color = null))
    }

    @Test
    fun panelSwatchesFollowTheOpacityUnlessSaidOtherwise() {
        assertTrue(ColorTarget("".raw(), { 0 }, { s, _ -> s }, withOpacity = true).panelSwatches)
        assertFalse(ColorTarget("".raw(), { 0 }, { s, _ -> s }).panelSwatches)
        assertFalse(
            ColorTarget("".raw(), { 0 }, { s, _ -> s }, withOpacity = true, panelSwatches = false).panelSwatches
        )
    }

    @Test
    fun hueStripLiftsGreyAndDarkColours() {
        assertEquals(0.6f to 0.9f, hueStripTone(0.05f, 0.1f))
        assertEquals(0.8f to 0.5f, hueStripTone(0.8f, 0.5f))
    }

    @Test
    fun valuesModeNamesThePresets() {
        val s: GraphStyle = GraphStyles.Aero
        assertEquals(ValuesMode.SERIES, valuesMode(s.withSeriesValues()))
        assertEquals(ValuesMode.WHITE, valuesMode(s.withWhiteValues()))
        assertEquals(ValuesMode.CUSTOM, valuesMode(s.withWhiteValues().withValueColor(Reading.entries.first(), RED)))
    }

    @Test
    fun panelIsWideOnlyOnAnExpandedWindow() {
        assertNull(stylePanelWidthDp(839f))
        assertEquals(STYLE_PANEL_WIDTH_DP, stylePanelWidthDp(840f))
        assertEquals(420f, stylePanelWidthDp(1280f))
    }

    @Test
    fun lumaAveragesEverySeventhPixel() {
        assertEquals(SCENE_LUMA, averageLuma(IntArray(0)))
        assertEquals(1f, averageLuma(IntArray(14) { WHITE_ARGB }), 1e-4f)
        // Only pixels 0 and 7 are read: one white, one black.
        val pixels = IntArray(14) { if (it == 0) WHITE_ARGB else BLACK_ARGB }
        assertEquals(0.5f, averageLuma(pixels), 1e-4f)
    }

    @Test
    fun cameraLumaSmoothsHalfWayAndSkipsEmptySamples() = runTest {
        assertEquals(0.4f, smoothLuma(null, 0.4f))
        assertEquals(0.6f, smoothLuma(0.4f, 0.8f))
        val samples = mutableListOf(0.4f, null, 0.8f, 0.0f)
        val seen = cameraLuma { samples.removeFirst() }.take(3).toList()
        assertEquals(listOf(0.4f, 0.6f, 0.3f), seen)
    }

    private companion object {
        const val WHITE_ARGB = 0xFFFFFFFF.toInt()
        const val BLACK_ARGB = 0xFF000000.toInt()
        const val RED = 0xFFFF0000.toInt()
    }
}
