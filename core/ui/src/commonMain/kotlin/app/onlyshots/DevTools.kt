package app.onlyshots

/**
 * Whether this is a debug build: the machine log and the simulated shot are developer tools. Set once by each entry
 * point (Android from its BuildConfig, iOS from the binary, the desktop always), since a library has no build type.
 */
object DevTools {
    var enabled: Boolean = false
}

/** Shorthand for [DevTools.enabled]. */
val devTools: Boolean
    get() = DevTools.enabled
