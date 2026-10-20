package app.onlyshots.overlay

import app.onlyshots.overlay.scene.TextMetrics

/** Fixed-advance metrics with Open Sans' vertical proportions; good enough for layout tests. */
object FakeTextMetrics : TextMetrics {
    override fun width(text: String, size: Float, weight: Int): Float =
        text.length * size * (if (weight >= 600) 0.58f else 0.55f)

    override fun ascent(size: Float): Float = size * 1.069f

    override fun descent(size: Float): Float = size * 0.293f
}
