package app.onlyshots.style

import app.onlyshots.core.model.OverlaySettings
import app.onlyshots.core.model.SourceKind
import app.onlyshots.core.model.style.GraphStyle
import app.onlyshots.overlay.GraphStyles
import app.onlyshots.storage.AppSettingsRepo
import kotlin.random.Random
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The user's own graph styles and which source each is the default for, kept in the app settings. Built-in styles are
 * never edited in place: a change to one is saved as a copy.
 */
class StyleLibrary(private val settings: AppSettingsRepo) {
    val custom: List<GraphStyle>
        get() = settings.value.customStyles

    val defaults: Map<SourceKind, String>
        get() = settings.value.defaultStyles

    /** The user's styles as they change, for pickers to follow. */
    val customStyles: Flow<List<GraphStyle>> = settings.state.map { it.customStyles }.distinctUntilChanged()

    /** The style [overlay] draws with for [source], user styles and defaults included. */
    fun resolve(overlay: OverlaySettings, source: SourceKind?): GraphStyle =
        GraphStyles.resolve(overlay, source, custom, defaults)

    fun byId(id: String): GraphStyle? = custom.firstOrNull { it.id == id } ?: GraphStyles.byId(id)

    /**
     * A new, unsaved style starting from [base]: a fresh id and [name] (put into words by the caller, as [newStyleName]
     * says it: the base's name with "copy", or "my style").
     */
    fun copyOf(base: GraphStyle, name: String): GraphStyle =
        base.copy(id = "u-${Random.nextLong().toULong().toString(36)}", name = name, builtIn = false)

    /** Saves [style] (added, or replacing the one with its id) and makes it the default for exactly [defaultFor]. */
    fun save(style: GraphStyle, defaultFor: Set<SourceKind>) {
        settings.update { s ->
            val list = s.customStyles.toMutableList()
            val at = list.indexOfFirst { it.id == style.id }
            if (at >= 0) list[at] = style else list += style
            val defaults = s.defaultStyles.filterValues { it != style.id } + defaultFor.associateWith { style.id }
            s.copy(customStyles = list, defaultStyles = defaults)
        }
    }

    fun rename(id: String, name: String) {
        settings.update { s ->
            s.copy(customStyles = s.customStyles.map { if (it.id == id) it.copy(name = name) else it })
        }
    }

    /** Removes a user style; sources that defaulted to it go back to their built-in default. */
    fun delete(id: String) {
        settings.update { s ->
            s.copy(
                customStyles = s.customStyles.filterNot { it.id == id },
                defaultStyles = s.defaultStyles.filterValues { it != id },
            )
        }
    }

    /** The sources [id] is currently the default for. */
    fun defaultsOf(id: String): Set<SourceKind> = defaults.filterValues { it == id }.keys

    /**
     * [overlay] switched to the style [id]. A user style is copied into the overlay too, so a video keeps the look it
     * was made with whatever happens to the style later.
     */
    fun pick(overlay: OverlaySettings, id: String): OverlaySettings =
        overlay.copy(styleId = id, style = custom.firstOrNull { it.id == id })
}
