package app.onlyshots.home

import app.onlyshots.layout.WindowLayout

/** How a wide Start is laid out. */
enum class StartWideShape {
    /** The headline on the left, the choices in a panel on the right (a page each on a book foldable). */
    Beside,

    /** One column, the two choices side by side under the headline: an upright tablet. */
    Upright,
}

/** A wide window on its side, or a book foldable's two pages, puts the choices beside the headline. */
fun startWideShape(layout: WindowLayout): StartWideShape =
    if (layout.fold || layout.landscape) StartWideShape.Beside else StartWideShape.Upright
