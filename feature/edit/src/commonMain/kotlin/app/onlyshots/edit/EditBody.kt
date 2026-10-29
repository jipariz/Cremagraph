package app.onlyshots.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import app.onlyshots.StyleEditorRoute
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.component.EditorSheet
import app.onlyshots.core.designsystem.component.EditorSheetSlot
import app.onlyshots.core.designsystem.component.LocalNativeChrome
import app.onlyshots.core.model.VideoItem
import app.onlyshots.core.model.style.GraphStyle
import app.onlyshots.core.resources.text
import app.onlyshots.layout.LayoutKind
import app.onlyshots.layout.LocalWindowLayout
import app.onlyshots.layout.SHEET_HEIGHT_FRACTION
import app.onlyshots.style.StylePicker
import app.onlyshots.style.StylePickerActions

/**
 * The editor drawn from plain state: [ui], the user's [customStyles], the [player]'s clock, and [actions] to ask for
 * changes. [picture] draws the video into the modifier it is given (the platform's video surface in the app, a still in
 * a screenshot). Phone layout, or on a wide window the picture with its timeline beside the panel; a tabletop's two
 * halves, or a cover screen's word to open the phone.
 */
@Composable
fun EditBody(
    ui: EditUiState,
    customStyles: List<GraphStyle>,
    player: PlayerState,
    actions: EditActions,
    onBack: () -> Unit,
    onStyleEditor: (StyleEditorRoute) -> Unit,
    onExported: () -> Unit,
    onPickShotFile: () -> Unit,
    onSaveToGallery: () -> Unit,
    picture: @Composable (Modifier) -> Unit,
    initiallySelected: Boolean = false,
    initiallyReframing: Boolean = false,
) {
    val local = rememberEditLocalState(initiallySelected, initiallyReframing)
    val chrome = editChrome(LocalWindowLayout.current.kind, ui, player.muted, local.graphSelected, local.reframing)
    val video = ui.item
    // The export straight from a wide window's panel or a tabletop's keys, or from the keyboard.
    val export = { if (video != null && ui.exporting == null) onSaveToGallery() }
    // Cmd/Ctrl+Z, Cmd/Ctrl+Shift+Z and Cmd/Ctrl+E on a keyboard, as any editor.
    val focus = remember { FocusRequester() }
    val keyInput =
        Modifier.focusRequester(focus)
            .focusable()
            .editorKeys(
                actions,
                export,
                blocked = { chrome.exportOverlay },
                onEscape = {
                    when (chrome.escape(local.graphSelected)) {
                        EditEscape.ACKNOWLEDGE -> actions.acknowledgeExport()
                        EditEscape.DESELECT -> local.graphSelected = false
                        EditEscape.NONE -> Unit
                    }
                },
            )
    LaunchedEffect(chrome.leave) { if (chrome.leave) onExported() }
    // Once the layout holding the keys is in place: the phone's until the video loads, then perhaps another.
    LaunchedEffect(chrome.kind) {
        withFrameNanos {}
        focus.requestFocus()
    }
    val share = { actions.saveAndExport(ui.shareQuality, chrome.sound) }
    val exportInPanel =
        LocalNativeChrome.current == null || LocalExportWording.current.share != null || ui.qualities.size > 1
    val slots =
        EditSlots(
            picture = { modifier ->
                if (video != null) {
                    EditPicture(ui, video, player, local, actions, pictureChrome(chrome.kind), picture, modifier)
                }
            },
            timeBand = { if (video != null) EditTimeBand(ui, video, player, local, actions) },
            sheet = { sheet ->
                if (video != null) {
                    SheetBody(
                        sheet = sheet,
                        video = video,
                        ui = ui,
                        customStyles = customStyles,
                        sound = chrome.sound,
                        actions = actions,
                        onStyleEditor = onStyleEditor,
                        onPickShotFile = onPickShotFile,
                        onShare = share,
                        onSaveToGallery = onSaveToGallery,
                        onExported = onExported,
                    )
                }
            },
            // The way out, at the bottom of a wide editor's panel whichever page it shows. Where the bars are native
            // saving is their item: the panel keeps a footer only for what is left to choose there (share, the
            // quality).
            exportBar =
                if (!exportInPanel) null
                else {
                    {
                        if (video != null) {
                            ExportBar(
                                video,
                                galleryQuality = ui.galleryQuality,
                                shareQuality = ui.shareQuality,
                                qualities = ui.qualities,
                                sound = chrome.sound,
                                onSave = export,
                                onShare = share,
                                onQuality = actions::pickQuality,
                            )
                        }
                    }
                },
        )
    EditNativeBar(ui, actions, player, onExport = if (chrome.kind == LayoutKind.Phone) null else export)
    Box(Modifier.fillMaxSize().background(CgColors.Surface.Base).then(keyInput)) {
        val insets = Modifier.fillMaxSize().safeDrawingPadding()
        when {
            chrome.kind == LayoutKind.Tiny -> EditTinyLayout(onBack, insets)
            video == null ->
                EditPhoneLayout(ui, null, player, local, chrome, actions, onBack, onExported, slots, insets)
            chrome.kind == LayoutKind.Wide ->
                EditWideLayout(ui, video, player, local, chrome, actions, onBack, onPickShotFile, onExported, slots)
            chrome.kind == LayoutKind.Stacked ->
                EditStackedLayout(ui, video, player, local, chrome, actions, onBack, export, onExported, slots, insets)
            else -> EditPhoneLayout(ui, video, player, local, chrome, actions, onBack, onExported, slots, insets)
        }
    }
}

/** The parts every layout places, made once by [EditBody] so each layout only says where they go. */
internal class EditSlots(
    /** The picture with the graph on it, in the modifier's space. */
    val picture: @Composable (Modifier) -> Unit,
    /** The timeline and its keys. */
    val timeBand: @Composable () -> Unit,
    /** What a sheet holds: a bottom sheet on a phone, the side panel on a wide window. */
    val sheet: @Composable (EditSheet) -> Unit,
    /** The export, pinned under a wide window's panel; null where the native bar's item is all there is to it. */
    val exportBar: (@Composable () -> Unit)?,
)

/** What each sheet holds. */
@Composable
private fun SheetBody(
    sheet: EditSheet,
    video: VideoItem,
    ui: EditUiState,
    customStyles: List<GraphStyle>,
    sound: Boolean,
    actions: EditActions,
    onStyleEditor: (StyleEditorRoute) -> Unit,
    onPickShotFile: () -> Unit,
    onShare: () -> Unit,
    onSaveToGallery: () -> Unit,
    onExported: () -> Unit,
) {
    when (sheet) {
        EditSheet.STYLE ->
            StylePicker(
                currentId = ui.style?.id ?: video.overlay.styleId,
                custom = customStyles,
                actions =
                    StylePickerActions(
                        pick = actions::pickStyle,
                        create = { base -> onStyleEditor(StyleEditorRoute(baseId = base, videoId = video.id)) },
                        edit = { sid -> onStyleEditor(StyleEditorRoute(editId = sid, videoId = video.id)) },
                        rename = { sid, name -> actions.renameStyle(sid, name) },
                        delete = actions::deleteStyle,
                    ),
            )
        // The graph's words go with its data: what it shows and what it says, in one place.
        EditSheet.DATA -> {
            DataSheetContent(
                video.overlay,
                seriesIn(video.shot),
                hasPhases = video.shot?.phaseStartsUs?.isNotEmpty() == true,
            ) { change ->
                actions.overlay(change = change)
            }
            GraphTextFields(video) { push, change -> actions.overlay(push, change) }
        }
        EditSheet.SHOT ->
            ShotSheetContent(
                video,
                ui.shots,
                onPick = actions::pickShot,
                onFile = onPickShotFile,
                onMatchByClock = actions::matchByClock,
                onUnlock = actions::unlockShotStart,
                onFromMachine = LocalFromMachine.current,
                onVisualizer = if (ui.hasVisualizer) actions::openVisualizer else null,
                visualizer = ui.visualizer,
                onFetchVisualizer = actions::fetchFromVisualizer,
            )
        EditSheet.EXPORT ->
            ExportSheetContent(
                item = video,
                galleryQuality = ui.galleryQuality,
                shareQuality = ui.shareQuality,
                qualities = ui.qualities,
                sound = sound,
                exporting = ui.exporting,
                leftMs = ui.exportLeftMs,
                savedToGallery = ui.savedToGallery,
                onShare = onShare,
                onSaveToGallery = onSaveToGallery,
                onNextShot = onExported,
                onCancel = actions::cancelExport,
                onQuality = actions::pickQuality,
            )
    }
}

/**
 * The bottom sheet over the picture (a phone's, a tabletop's), rising at most [SHEET_HEIGHT_FRACTION] up the box. With
 * [scrim], the rest dims and a tap on it puts the sheet down. Neither while an export runs: it is seen out or
 * cancelled.
 */
@Composable
internal fun BoxScope.EditBottomSheet(
    sheet: EditSheet?,
    ui: EditUiState,
    actions: EditActions,
    scrim: Boolean,
    content: @Composable (EditSheet) -> Unit,
) {
    val close = actions::closeSheet.takeIf { ui.exporting == null }
    EditorSheetSlot(
        sheet,
        Modifier.align(Alignment.BottomCenter).fillMaxHeight(SHEET_HEIGHT_FRACTION).wrapContentHeight(Alignment.Bottom),
        scrim = scrim,
        onScrimTap = close,
    ) { shown ->
        EditorSheet(title = sheetTitle(shown, ui).text(), onDone = close) { content(shown) }
    }
}

private fun Modifier.editorKeys(
    actions: EditActions,
    onExport: () -> Unit,
    blocked: () -> Boolean,
    onEscape: () -> Unit,
): Modifier = onPreviewKeyEvent { e ->
    if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) onEscape()
    // Under the export's overlay the editor takes no keys at all.
    if (blocked()) return@onPreviewKeyEvent true
    val command = e.type == KeyEventType.KeyDown && (e.isMetaPressed || e.isCtrlPressed)
    when {
        command && e.key == Key.Z -> if (e.isShiftPressed) actions.redo() else actions.undo()
        command && e.key == Key.E -> onExport()
        else -> return@onPreviewKeyEvent false
    }
    true
}
