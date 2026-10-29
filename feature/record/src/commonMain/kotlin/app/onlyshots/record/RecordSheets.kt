package app.onlyshots.record

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import app.onlyshots.StyleEditorRoute
import app.onlyshots.core.designsystem.component.EditorSheet
import app.onlyshots.core.designsystem.component.EditorSheetSlot
import app.onlyshots.core.designsystem.component.SHEET_MAX_WIDTH
import app.onlyshots.core.model.Capabilities
import app.onlyshots.core.model.RecordingSettings
import app.onlyshots.core.model.style.GraphStyle
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.record_data
import app.onlyshots.core.resources.record_settings_title
import app.onlyshots.core.resources.record_style
import app.onlyshots.edit.DataSheetContent
import app.onlyshots.edit.EditSheet
import app.onlyshots.layout.SHEET_HEIGHT_FRACTION
import app.onlyshots.style.StylePicker
import app.onlyshots.style.StylePickerActions
import org.jetbrains.compose.resources.stringResource

/** What the record screen's sheets show and do: the graph's style and data, and the recording settings. */
internal class RecordSheetInputs(
    val state: RecordUiState,
    val settings: RecordingSettings,
    val graphStyle: GraphStyle,
    val customStyles: List<GraphStyle>,
    val actions: RecordActions,
    val onStyleEditor: (StyleEditorRoute) -> Unit,
)

/**
 * The graph's sheet ([graphSheet], style or data) and the recording settings ([settingsOpen]). On a fold ([foldPage],
 * where the right page starts) they fill the right page; elsewhere the graph's sheet rises from the bottom, as wide as
 * every sheet at most, and the settings are a bottom sheet.
 */
@Composable
internal fun RecordSheets(
    inputs: RecordSheetInputs,
    graphSheet: EditSheet?,
    onGraphSheet: (EditSheet?) -> Unit,
    settingsOpen: Boolean,
    onSettingsOpen: (Boolean) -> Unit,
    foldPage: Dp?,
) {
    val area = if (foldPage != null) Modifier.fillMaxSize().padding(start = foldPage) else Modifier.fillMaxSize()
    val size =
        if (foldPage != null) Modifier.fillMaxSize()
        else
            Modifier.widthIn(max = SHEET_MAX_WIDTH)
                .fillMaxHeight(SHEET_HEIGHT_FRACTION)
                .wrapContentHeight(Alignment.Bottom)
    // The graph for this and the next recordings; a recorded video keeps its own copy, edited in the editor.
    Box(area, contentAlignment = Alignment.BottomCenter) {
        EditorSheetSlot(graphSheet, size, scrim = true, onScrimTap = { onGraphSheet(null) }) { sheet ->
            EditorSheet(
                title =
                    stringResource(if (sheet == EditSheet.STYLE) Res.string.record_style else Res.string.record_data),
                onDone = { onGraphSheet(null) },
            ) {
                if (sheet == EditSheet.STYLE) GraphStyleContent(inputs) else GraphDataContent(inputs)
            }
        }
    }
    if (foldPage != null) {
        Box(area, contentAlignment = Alignment.BottomCenter) {
            EditorSheetSlot(Unit.takeIf { settingsOpen }, Modifier.fillMaxSize()) {
                EditorSheet(
                    title = stringResource(Res.string.record_settings_title),
                    onDone = { onSettingsOpen(false) },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    RecordingSettingsContent(inputs.settings, inputs.state.sourceKind, inputs.actions::updateSettings)
                }
            }
        }
    } else if (settingsOpen) {
        RecordingSheet(
            settings = inputs.settings,
            sourceKind = inputs.state.sourceKind,
            onChange = inputs.actions::updateSettings,
            onDismiss = { onSettingsOpen(false) },
        )
    }
}

@Composable
private fun GraphStyleContent(inputs: RecordSheetInputs) {
    val actions = inputs.actions
    StylePicker(
        currentId = inputs.graphStyle.id,
        custom = inputs.customStyles,
        actions =
            StylePickerActions(
                pick = actions::pickStyle,
                create = { base -> inputs.onStyleEditor(StyleEditorRoute(baseId = base, camera = true)) },
                edit = { id -> inputs.onStyleEditor(StyleEditorRoute(editId = id, camera = true)) },
                rename = actions::renameStyle,
                delete = actions::deleteStyle,
            ),
    )
}

/** No finished shot yet, so what can be drawn is what the machine sends. */
@Composable
private fun GraphDataContent(inputs: RecordSheetInputs) {
    DataSheetContent(inputs.settings.overlay, Capabilities.of(inputs.state.sourceKind, hasScale = true).series) { change
        ->
        inputs.actions.updateOverlay(change)
    }
}
