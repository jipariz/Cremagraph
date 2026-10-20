package app.onlyshots.layout

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A foldable iPhone's fold: whether it is half open, and where it crosses the window, in points. */
data class IosFold(
    val partiallyOpen: Boolean,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    /** The fold runs top to bottom (a book held open), not across (tabletop). */
    val isVertical: Boolean
        get() = bottom - top > right - left
}

/**
 * The fold as the host app sees it. Kotlin's UIKit bindings predate iOS 27.1's hinge API, so the Swift host watches it
 * (`UIHingeInteraction` for the status, the view's division region for where it is) and reports here.
 */
object IosHinge {
    val fold: StateFlow<IosFold?>
        field = MutableStateFlow<IosFold?>(null)

    fun update(partiallyOpen: Boolean, left: Float, top: Float, right: Float, bottom: Float) {
        fold.value = IosFold(partiallyOpen, left, top, right, bottom)
    }

    /** No fold: a phone without one, a closed one on its outer display, or an iOS before 27.1. */
    fun clear() {
        fold.value = null
    }
}
