package app.onlyshots

import kotlin.test.Test
import kotlin.test.assertEquals

class NavigationTest {
    @Test
    fun goDoesNotStackTheSameScreenTwice() {
        val stack = mutableListOf<Any>(StartRoute)
        stack.go(HomeRoute)
        stack.go(HomeRoute)
        assertEquals(listOf<Any>(StartRoute, HomeRoute), stack)
    }

    @Test
    fun backKeepsTheFirstScreen() {
        val stack = mutableListOf<Any>(StartRoute, HomeRoute)
        stack.back()
        stack.back()
        assertEquals(listOf<Any>(StartRoute), stack)
    }

    @Test
    fun backToStopsAtTheScreenAndIgnoresOneNotThere() {
        val stack = mutableListOf<Any>(StartRoute, HomeRoute, RecordRoute(), PrivacyRoute)
        stack.backTo(HomeRoute)
        assertEquals(listOf<Any>(StartRoute, HomeRoute), stack)
        stack.backTo(OpenRoute)
        assertEquals(listOf<Any>(StartRoute, HomeRoute), stack)
    }

    @Test
    fun aRecordingsEditorReplacesTheRecordScreen() {
        val stack = mutableListOf<Any>(StartRoute, HomeRoute, RecordRoute())
        stack.replaceAbove(HomeRoute, EditRoute("v1"))
        assertEquals(listOf<Any>(StartRoute, HomeRoute, EditRoute("v1")), stack)
    }
}
