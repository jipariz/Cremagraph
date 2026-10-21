package app.onlyshots

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

/** The phones' first screen: record a shot live, or compose a video from one already filmed. */
@Serializable data object StartRoute : NavKey

@Serializable data object HomeRoute : NavKey

/** The record screen; with [sample], the replayed sample shot starts as soon as the camera is armed. */
@Serializable data class RecordRoute(val sample: Boolean = false) : NavKey

/**
 * The editor on video [id]. [manual]: opened from a picked video and shot rather than from a recording, so leaving it
 * goes back to where they were picked.
 */
@Serializable data class EditRoute(val id: String, val manual: Boolean = false) : NavKey

/** Every editor's start: pick a video and its shot. */
@Serializable data object OpenRoute : NavKey

/**
 * The graph style editor: a user style [editId] changed in place, or a new one from [baseId]. Its preview is live:
 * [videoId] playing (opened from the editor) or the camera when [camera] (opened from the record screen).
 */
@Serializable
data class StyleEditorRoute(
    val baseId: String? = null,
    val editId: String? = null,
    val videoId: String? = null,
    val camera: Boolean = false,
) : NavKey

/** The privacy policy, inside the app. */
@Serializable data object PrivacyRoute : NavKey

/** The machine shot picker (phones, desktop), opened from the start screen or the editor's shot sheet. */
@Serializable data object MachineShotsRoute : NavKey

/** How a back stack of these routes is saved (rotation, process death): each one by its serializer. */
val RouteSaving = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(StartRoute::class, StartRoute.serializer())
            subclass(HomeRoute::class, HomeRoute.serializer())
            subclass(RecordRoute::class, RecordRoute.serializer())
            subclass(EditRoute::class, EditRoute.serializer())
            subclass(OpenRoute::class, OpenRoute.serializer())
            subclass(StyleEditorRoute::class, StyleEditorRoute.serializer())
            subclass(PrivacyRoute::class, PrivacyRoute.serializer())
            subclass(MachineShotsRoute::class, MachineShotsRoute.serializer())
        }
    }
}
