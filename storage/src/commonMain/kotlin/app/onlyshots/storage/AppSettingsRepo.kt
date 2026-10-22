package app.onlyshots.storage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The app's settings as live state: read once at start-up, written back on every change.
 *
 * ponytail: the write is inline, on whatever thread called [update]. It is a ~200 byte atomic JSON write driven by
 * discrete taps (a stepper, a toggle, "connect"); move it to a background dispatcher if a continuous control ever
 * writes through here.
 */
class AppSettingsRepo(private val store: SettingsStore) {
    val state: StateFlow<AppSettings>
        field = MutableStateFlow(store.load())

    val value: AppSettings
        get() = state.value

    fun update(change: (AppSettings) -> AppSettings) {
        state.value = change(state.value).also(store::save)
    }
}
