package app.onlyshots.edit

import androidx.compose.runtime.Composable
import app.onlyshots.TestTags
import app.onlyshots.core.designsystem.component.BarIcon
import app.onlyshots.core.designsystem.component.BarPriority
import app.onlyshots.core.designsystem.component.NativeBarItem
import app.onlyshots.core.designsystem.component.NativeItems
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.edit_export
import app.onlyshots.core.resources.edit_muted
import app.onlyshots.core.resources.edit_redo
import app.onlyshots.core.resources.edit_sound
import app.onlyshots.core.resources.edit_undo
import org.jetbrains.compose.resources.stringResource

/**
 * Where the bars are native (iPhone Duo): export (prominent), undo and redo together, and the sound last, so it is the
 * first to go to the overflow. The keys they replace on the screen step aside. [onExport] is the wide editor's save;
 * null opens the phone's export sheet.
 */
@Composable
internal fun EditNativeBar(ui: EditUiState, actions: EditActions, player: PlayerState, onExport: (() -> Unit)?) {
    val video = ui.item
    // On a wide editor the item saves straight away, as the panel's button did: it is called what that button was.
    val export = stringResource(if (onExport != null) LocalExportWording.current.save else Res.string.edit_export)
    val undo = stringResource(Res.string.edit_undo)
    val redo = stringResource(Res.string.edit_redo)
    val sound = stringResource(if (player.muted) Res.string.edit_muted else Res.string.edit_sound)
    NativeItems(
        buildList {
            if (video != null) {
                add(
                    NativeBarItem(
                        TestTags.EditExport,
                        export,
                        BarIcon.Export,
                        prominent = true,
                        enabled = ui.exporting == null,
                        priority = BarPriority.High,
                    )
                )
            }
            add(NativeBarItem(TestTags.EditUndo, undo, BarIcon.Undo, enabled = ui.canUndo, group = 1))
            add(NativeBarItem(TestTags.EditRedo, redo, BarIcon.Redo, enabled = ui.canRedo, group = 1))
            if (video?.hasAudio == true) {
                val icon = if (player.muted) BarIcon.SoundOff else BarIcon.SoundOn
                add(NativeBarItem(TestTags.EditSound, sound, icon, bottom = true, priority = BarPriority.Low))
            }
        }
    ) { id ->
        when (id) {
            TestTags.EditExport -> onExport?.invoke() ?: actions.openSheet(EditSheet.EXPORT)
            TestTags.EditUndo -> actions.undo()
            TestTags.EditRedo -> actions.redo()
            TestTags.EditSound -> player.muted = !player.muted
        }
    }
}
