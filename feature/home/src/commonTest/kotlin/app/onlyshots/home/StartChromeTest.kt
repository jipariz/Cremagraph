package app.onlyshots.home

import app.onlyshots.layout.bookLayout
import app.onlyshots.layout.wideLayout
import kotlin.test.Test
import kotlin.test.assertEquals

class StartChromeTest {
    @Test
    fun aWideWindowOnItsSideOrAFoldPutsTheChoicesBesideTheHeadline() {
        assertEquals(StartWideShape.Beside, startWideShape(wideLayout(1280f, 800f)))
        assertEquals(StartWideShape.Upright, startWideShape(wideLayout(800f, 1280f)))
        assertEquals(StartWideShape.Beside, startWideShape(bookLayout(840f, 900f)))
    }
}
