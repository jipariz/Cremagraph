package app.onlyshots.style

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.onlyshots.TestTags
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgStroke
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.caps
import app.onlyshots.core.designsystem.component.CgButton
import app.onlyshots.core.designsystem.component.CgButtonKind
import app.onlyshots.core.designsystem.component.CgTextField
import app.onlyshots.core.designsystem.component.CgToggle
import app.onlyshots.core.designsystem.component.EditorSheet
import app.onlyshots.core.model.SourceKind
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.UiText
import app.onlyshots.core.resources.raw
import app.onlyshots.core.resources.style_cancel
import app.onlyshots.core.resources.style_name
import app.onlyshots.core.resources.style_save
import app.onlyshots.core.resources.style_save_style
import app.onlyshots.core.resources.style_scale_only
import app.onlyshots.core.resources.style_use_by_default_for
import app.onlyshots.core.resources.text
import app.onlyshots.edit.machineColor
import org.jetbrains.compose.resources.stringResource

/** Figma 13 · G: name, where the style is the default, save. */
@Composable
fun SaveSheet(
    name: String,
    defaults: Set<SourceKind>,
    onSave: (String, Set<SourceKind>) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf(name) }
    var picked by remember { mutableStateOf(defaults) }
    EditorSheet(stringResource(Res.string.style_save_style), onDone = null, modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(CgSpace.S6)) {
            StyleLabel(stringResource(Res.string.style_name))
            CgTextField(
                text,
                onValueChange = { text = typedName(it) },
                label = stringResource(Res.string.style_name),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        StyleLabel(stringResource(Res.string.style_use_by_default_for))
        Column(Modifier.fillMaxWidth().border(CgStroke.Hairline, CgColors.Line.Hairline)) {
            DefaultSources.forEachIndexed { i, (kind, label) ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(CgStroke.Hairline).background(CgColors.Line.Divider))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = CgSpace.S12, vertical = CgSpace.S6),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CgSpace.S12),
                ) {
                    Box(Modifier.size(SOURCE_MARK_WIDTH, SOURCE_MARK_HEIGHT).background(machineColor(kind)))
                    Text(label.text().caps(), style = CgTheme.type.labelStrong, modifier = Modifier.weight(1f))
                    CgToggle(
                        kind in picked,
                        onCheckedChange = { on -> picked = if (on) picked + kind else picked - kind },
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(CgSpace.S8)) {
            CgButton(
                stringResource(Res.string.style_cancel),
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                kind = CgButtonKind.Secondary,
            )
            CgButton(
                stringResource(Res.string.style_save),
                onClick = { onSave(text, picked) },
                modifier = Modifier.weight(1f).testTag(TestTags.StyleSaveConfirm),
                kind = CgButtonKind.Signal,
                enabled = text.isNotBlank(),
            )
        }
    }
}

/** The sources a style can be the default for: the machines by their names, and "scale only". */
private val DefaultSources: List<Pair<SourceKind, UiText>> =
    listOf(
        SourceKind.GAGGIUINO to "Gaggiuino".raw(),
        SourceKind.GAGGIMATE to "GaggiMate".raw(),
        SourceKind.DECENT to "Decent".raw(),
        SourceKind.SCALE to Res.string.style_scale_only.text(),
    )

/** The source's colour mark beside its name. */
private val SOURCE_MARK_WIDTH = 4.dp
private val SOURCE_MARK_HEIGHT = 20.dp
