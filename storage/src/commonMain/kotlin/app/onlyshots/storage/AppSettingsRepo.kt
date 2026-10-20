package app.onlyshots.storage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The app's settings as live state: read once at start-up, written back on every change.
 *
 * ponytail: the write is inline, on whatever thread called [update]. It is a ~200 byte atomic JSON write driven by
 * discrete taps (a stepper, a toggle, "connect"); move it to a background dispatcher if a continuous control ever
 * writes through here.
 */
class AppSettingsRepo(private val store: SettingsStore) {
    private val _state = MutableStateFlow(store.load())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    val value: AppSettings
        get() = _state.value

    fun update(change: (AppSettings) -> AppSettings) {
        _state.value = change(_state.value).also(store::save)
    }
}
