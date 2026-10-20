package app.onlyshots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import app.onlyshots.camera.CameraController
import app.onlyshots.camera.CameraPreview
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.model.Shot
import app.onlyshots.core.model.SidecarCodec
import app.onlyshots.edit.EditScreen
import app.onlyshots.home.HomeScreen
import app.onlyshots.home.MachineShotsScreen
import app.onlyshots.home.StartScreen
import app.onlyshots.layout.ProvideWindowLayout
import app.onlyshots.open.OpenScreen
import app.onlyshots.privacy.PrivacyScreen
import app.onlyshots.record.RecordScreen
import app.onlyshots.sources.SourceHolder
import app.onlyshots.style.LiveCamera
import app.onlyshots.style.StyleEditorScreen
import org.koin.compose.koinInject

/**
 * The phones' app. Start chooses the way in. Recording live: connect (Home), record, edit, and after the export back to
 * record for the next shot; back from record returns to Home, back from edit (the edits already saved) to record.
 * Editing a video by hand: pick it and its shot file, edit, and back to the picking screen, which lists it again. The
 * desktop and the web run the editor alone (`EditorApp` in `:editor`).
 */
@Composable
fun OnlyShotsApp() {
    CgTheme {
        val backStack = rememberNavBackStack(RouteSaving, StartRoute)
        // The machine connects while the app is in the foreground and lets go when it is not:
        // cancelling the holder's child scope is the only way to stop the 5 s retry loop.
        val machine: SourceHolder = koinInject()
        LifecycleEventEffect(Lifecycle.Event.ON_START) { machine.resume() }
        LifecycleEventEffect(Lifecycle.Event.ON_STOP) { machine.pause() }
        ProvideWindowLayout { SharedMotion { Graph(backStack) } }
    }
}

@Composable
private fun Graph(nav: NavBackStack<NavKey>) {
    val results = remember { NavResults() }
    NavDisplay(
        backStack = nav,
        onBack = { nav.back() },
        // Each screen keeps its own saved state and view models (the editor's is keyed by its video), as before.
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
                entry<StartRoute> {
                    StartScreen(
                        onRecord = { nav.go(HomeRoute) },
                        onEdit = { nav.go(OpenRoute) },
                        onPrivacy = { nav.go(PrivacyRoute) },
                    )
                }
                entry<OpenRoute> {
                    OpenScreen(
                        onOpen = { id -> nav.go(EditRoute(id, manual = true)) },
                        onBack = { nav.back() },
                        onFromMachine = { nav.go(MachineShotsRoute) },
                        pickedShot = results.pickedShot,
                        onPickedShotUsed = { results.pickedShot.value = null },
                    )
                }
                entry<HomeRoute> {
                    HomeScreen(
                        onOpenCamera = { nav.go(RecordRoute()) },
                        onTrySample = { nav.go(RecordRoute(sample = true)) },
                        onBack = { nav.back() },
                        onPrivacy = { nav.go(PrivacyRoute) },
                    )
                }
                entry<PrivacyRoute>(metadata = ModalMotion) { PrivacyScreen(onBack = { nav.back() }) }
                entry<RecordRoute> { route ->
                    RecordScreen(
                        playSample = route.sample,
                        onBack = { nav.backTo(HomeRoute) },
                        // The editor replaces the record screen, so leaving it starts a fresh session.
                        onSaved = { id -> nav.replaceAbove(HomeRoute, EditRoute(id)) },
                        onStyleEditor = { nav.go(it) },
                        savedStyle = results.savedStyle,
                        onSavedStyleUsed = { results.savedStyle.value = null },
                    )
                }
                entry<EditRoute> { route ->
                    val id = route.id
                    // A recording goes on to the next take; a video opened by hand goes back to where it was picked.
                    val next = {
                        if (route.manual) nav.backTo(OpenRoute) else nav.replaceAbove(HomeRoute, RecordRoute())
                    }
                    EditScreen(
                        id = id,
                        onBack = { next() },
                        onExported = { next() },
                        askBeforeLeaving = !route.manual,
                        onFromMachine = { nav.go(MachineShotsRoute) },
                        pickedShot = results.pickedShot,
                        onPickedShotUsed = { results.pickedShot.value = null },
                        onStyleEditor = { nav.go(it.copy(videoId = id)) },
                        savedStyle = results.savedStyle,
                        onSavedStyleUsed = { results.savedStyle.value = null },
                    )
                }
                entry<MachineShotsRoute>(metadata = ModalMotion) {
                    MachineShotsScreen(
                        onPicked = { shot ->
                            results.pickedShot.value = SidecarCodec.json.encodeToString(Shot.serializer(), shot)
                            nav.back()
                        },
                        onBack = { nav.back() },
                    )
                }
                entry<StyleEditorRoute>(metadata = FadeMotion) { route ->
                    StyleEditorScreen(
                        baseId = route.baseId,
                        editId = route.editId,
                        videoId = route.videoId,
                        camera = if (route.camera) liveCamera() else null,
                        onClose = { saved ->
                            if (saved != null) results.savedStyle.value = saved
                            nav.back()
                        },
                    )
                }
            },
    )
}

/** The record screen's camera, lent to the style editor opened from it. */
@Composable
private fun liveCamera(): LiveCamera {
    val camera: CameraController = koinInject()
    return LiveCamera(sampleLuma = camera::sampleLuma) { modifier, drawFrames ->
        CameraPreview(camera, modifier, drawFrames = drawFrames)
    }
}
