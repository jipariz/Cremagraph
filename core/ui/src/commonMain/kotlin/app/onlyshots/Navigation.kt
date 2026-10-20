package app.onlyshots

import kotlinx.coroutines.flow.MutableStateFlow

/** Opens [key], unless it is already on top (a second tap on the same button does not stack it twice). */
fun <T> MutableList<T>.go(key: T) {
    if (lastOrNull() != key) add(key)
}

/** Back one screen; the first one stays (leaving it is the system's back, not the app's). */
fun <T> MutableList<T>.back() {
    if (size > 1) removeAt(lastIndex)
}

/** Back until [key] is on top; nothing when it is not in the stack. */
fun <T> MutableList<T>.backTo(key: T) {
    if (key !in this) return
    while (last() != key) removeAt(lastIndex)
}

/** Back to [key], then on to [next]: a screen that replaces everything above [key]. */
fun <T> MutableList<T>.replaceAbove(key: T, next: T) {
    backTo(key)
    add(next)
}

/**
 * What a screen hands back to the one under it: the style the style editor saved (its id), the shot the machine shot
 * picker loaded (as JSON). The screen that opened them is on top again when they go back, and takes the value (then
 * clears it, through the `on…Used` callbacks the screens take).
 */
class NavResults {
    val savedStyle = MutableStateFlow<String?>(null)
    val pickedShot = MutableStateFlow<String?>(null)
}
