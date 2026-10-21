package app.onlyshots.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import app.onlyshots.EditRoute
import app.onlyshots.FadeMotion
import app.onlyshots.LocalSharedTransitionScope
import app.onlyshots.MachineShotsRoute
import app.onlyshots.ModalMotion
import app.onlyshots.NavResults
import app.onlyshots.OpenRoute
import app.onlyshots.PrivacyRoute
import app.onlyshots.RouteSaving
import app.onlyshots.ScreenMotion
import app.onlyshots.SharedMotion
import app.onlyshots.StyleEditorRoute
import app.onlyshots.back
import app.onlyshots.backTo
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.model.Shot
import app.onlyshots.core.model.SidecarCodec
import app.onlyshots.edit.EditScreen
import app.onlyshots.edit.ExportWording
import app.onlyshots.edit.LocalExportWording
import app.onlyshots.go
import app.onlyshots.layout.ProvideWindowLayout
import app.onlyshots.open.OpenHost
import app.onlyshots.open.OpenScreen
import app.onlyshots.open.StoreLinks
import app.onlyshots.privacy.PrivacyScreen
import app.onlyshots.rememberNavMotionDecorator
import app.onlyshots.style.StyleEditorScreen

/** The host's machine shot picker: it hands the shot it loaded to `onPicked`, or goes back. */
typealias MachineShots = @Composable (onPicked: (Shot) -> Unit, onBack: () -> Unit) -> Unit

/**
 * The editor on its own, as the desktop and the web run it: pick a video and its shot (or reopen a video), edit it,
 * style its graph, export, back to the start. No live machine, no scale, no camera; leaving a video loses nothing, it
 * stays in the list. [machineShots], where the host can reach machines on the network (the desktop, not the web): a
 * shot can come straight from the machine, on the start screen and in the editor's shot sheet.
 */
@Composable
fun EditorApp(
    exportWording: ExportWording = ExportWording.Desktop,
    machineShots: MachineShots? = null,
    /** What this editor says about itself on the start screen: where it runs, what its videos come out as. */
    host: OpenHost? = null,
    /** The phone app's store links, which the web offers on its start screen. */
    stores: StoreLinks? = null,
    /** Given the back stack, so a host can tie it to its own history: the web's browser back button. */
    history: (@Composable (NavBackStack<NavKey>) -> Unit)? = null,
) {
    CgTheme {
        CompositionLocalProvider(LocalExportWording provides exportWording) {
            EditorNav(machineShots, host, stores, history)
        }
    }
}

@Composable
private fun EditorNav(
    machineShots: MachineShots?,
    host: OpenHost?,
    stores: StoreLinks?,
    history: (@Composable (NavBackStack<NavKey>) -> Unit)?,
) {
    ProvideWindowLayout { SharedMotion { EditorGraph(machineShots, host, stores, history) } }
}

@Composable
private fun EditorGraph(
    machineShots: MachineShots?,
    host: OpenHost?,
    stores: StoreLinks?,
    history: (@Composable (NavBackStack<NavKey>) -> Unit)?,
) {
    val nav = rememberNavBackStack(RouteSaving, OpenRoute)
    val results = remember { NavResults() }
    history?.invoke(nav)
    val fromMachine = machineShots?.let { { nav.go(MachineShotsRoute) } }
    NavDisplay(
        backStack = nav,
        onBack = { nav.back() },
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
                rememberNavMotionDecorator(),
            ),
        sharedTransitionScope = LocalSharedTransitionScope.current,
        transitionSpec = ScreenMotion.forward(),
        popTransitionSpec = ScreenMotion.back(),
        predictivePopTransitionSpec = ScreenMotion.predictiveBack(),
        entryProvider =
            entryProvider {
                entry<OpenRoute> {
                    OpenScreen(
                        onOpen = { id -> nav.go(EditRoute(id, manual = true)) },
                        onFromMachine = fromMachine,
                        pickedShot = results.pickedShot,
                        onPickedShotUsed = { results.pickedShot.value = null },
                        host = host,
                        stores = stores,
                        onPrivacy = { nav.go(PrivacyRoute) },
                    )
                }
                entry<EditRoute> { route ->
                    val id = route.id
                    val back = { nav.backTo(OpenRoute) }
                    EditScreen(
                        id = id,
                        onBack = { back() },
                        onExported = { back() },
                        askBeforeLeaving = false,
                        onFromMachine = fromMachine,
                        pickedShot = results.pickedShot,
                        onPickedShotUsed = { results.pickedShot.value = null },
                        onStyleEditor = { nav.go(it.copy(videoId = id)) },
                        savedStyle = results.savedStyle,
                        onSavedStyleUsed = { results.savedStyle.value = null },
                    )
                }
                if (machineShots != null) {
                    entry<MachineShotsRoute>(metadata = ModalMotion) {
                        machineShots(
                            { shot ->
                                results.pickedShot.value = SidecarCodec.json.encodeToString(Shot.serializer(), shot)
                                nav.back()
                            },
                            { nav.back() },
                        )
                    }
                }
                entry<PrivacyRoute>(metadata = ModalMotion) { PrivacyScreen(onBack = { nav.back() }) }
                entry<StyleEditorRoute>(metadata = FadeMotion) { route ->
                    StyleEditorScreen(
                        baseId = route.baseId,
                        editId = route.editId,
                        videoId = route.videoId,
                        onClose = { saved ->
                            if (saved != null) results.savedStyle.value = saved
                            nav.back()
                        },
                    )
                }
            },
    )
}
