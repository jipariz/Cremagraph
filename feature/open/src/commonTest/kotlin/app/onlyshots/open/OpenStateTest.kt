package app.onlyshots.open

import app.onlyshots.layout.bookLayout
import app.onlyshots.layout.phoneLayout
import app.onlyshots.layout.stackedLayout
import app.onlyshots.layout.tinyLayout
import app.onlyshots.layout.wideLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class OpenStateTest {
    private val phoneBrowser = StoreLinks(onPlay = {}, onAppStore = null, phone = true)
    private val computerBrowser = StoreLinks(onPlay = {}, onAppStore = {}, phone = false)

    @Test
    fun aPhonesBrowserGetsTheCompactPageOnAPhoneSizedWindow() {
        val desk = OpenEntry.Desk(stores = phoneBrowser)
        assertSame(phoneBrowser, desk.phoneStores(phoneLayout(390f, 844f)))
        assertSame(phoneBrowser, desk.phoneStores(tinyLayout(300f, 360f)))
        // A tablet's browser, or a phone's held wide, gets the desk page with its band.
        assertNull(desk.phoneStores(wideLayout(820f, 1180f)))
    }

    @Test
    fun aComputersBrowserAndTheDesktopAlwaysGetTheDeskPage() {
        assertNull(OpenEntry.Desk(stores = computerBrowser).phoneStores(phoneLayout(390f, 844f)))
        assertNull(OpenEntry.Desk().phoneStores(phoneLayout(390f, 844f)))
    }

    @Test
    fun theShotSheetIsAFoldsPageOnlyInThePhoneAppOnAFold() {
        val app = OpenEntry.PhoneApp(onBack = {})
        assertTrue(shotSheetIsFoldPage(app, bookLayout(840f, 900f)))
        assertFalse(shotSheetIsFoldPage(app, wideLayout(840f, 900f)))
        assertFalse(shotSheetIsFoldPage(app, stackedLayout(840f, 900f)))
        assertFalse(shotSheetIsFoldPage(OpenEntry.Desk(), bookLayout(840f, 900f)))
    }

    @Test
    fun theDeskGuttersGrowWithTheWindowBetweenTheirBounds() {
        assertEquals(16f, deskMetrics(320f).gutterDp)
        assertEquals(44f, deskMetrics(1000f).gutterDp, 0.001f)
        assertEquals(64f, deskMetrics(1920f).gutterDp)
        assertEquals(24f, deskMetrics(320f).topDp)
        assertEquals(33f, deskMetrics(1000f).topDp, 0.001f)
        assertEquals(48f, deskMetrics(1920f).topDp)
        assertEquals(32f, deskMetrics(320f).previewGapDp)
        assertEquals(64f, deskMetrics(1920f).previewGapDp)
    }

    @Test
    fun theStepsGoSideBySideBeforeThePreviewMovesBesideThem() {
        val narrow = deskMetrics(TWO_UP_DP - 1f)
        assertFalse(narrow.twoUp)
        assertFalse(narrow.previewBeside)
        val medium = deskMetrics(TWO_UP_DP)
        assertTrue(medium.twoUp)
        assertFalse(medium.previewBeside)
        val wide = deskMetrics(PREVIEW_BESIDE_DP)
        assertTrue(wide.twoUp)
        assertTrue(wide.previewBeside)
    }

    @Test
    fun aPhoneOffersPlayUnlessOnlyTheAppStoreHasTheApp() {
        val play = {}
        val appStore = {}
        assertEquals(PhoneStore(Store.Play, play, banner = true), StoreLinks(play, appStore, phone = true).phoneStore())
        assertEquals(PhoneStore(Store.AppStore, appStore, banner = true), StoreLinks(null, appStore, true).phoneStore())
        assertEquals(PhoneStore(Store.Play, null, banner = false), StoreLinks(null, null, phone = true).phoneStore())
    }

    @Test
    fun aClosedBannerStaysClosed() {
        val store = StoreLinks(onPlay = {}, onAppStore = null, phone = true, bannerDismissed = true).phoneStore()
        assertFalse(store.banner)
    }
}
