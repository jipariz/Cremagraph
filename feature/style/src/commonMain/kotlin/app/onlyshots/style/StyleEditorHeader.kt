package app.onlyshots.style

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.onlyshots.TestTags
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgIcons
import app.onlyshots.core.designsystem.CgSize
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgStroke
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.caps
import app.onlyshots.core.designsystem.component.BackButton
import app.onlyshots.core.designsystem.component.BarIcon
import app.onlyshots.core.designsystem.component.BarPriority
import app.onlyshots.core.designsystem.component.CgIcon
import app.onlyshots.core.designsystem.component.LocalNativeChrome
import app.onlyshots.core.designsystem.component.NativeBarItem
import app.onlyshots.core.designsystem.component.NativeItems
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.style_name
import app.onlyshots.core.resources.style_rename
import app.onlyshots.core.resources.style_save
import org.jetbrains.compose.resources.stringResource

/**
 * The style's name, renamed in place: tap it to edit, the keyboard's done (or leaving the field) keeps it. A new style
 * or copy ([nameFirst]) opens with its name ready to type over.
 */
@Composable
internal fun StyleEditorHeader(
    name: String,
    onRename: (String) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    nameFirst: Boolean = false,
) {
    var editing by remember { mutableStateOf(nameFirst) }
    // The whole name selected, so typing replaces "Aero copy" at once.
    // Keyed on the name: a new style's name is put into words a moment after the editor opens.
    var text by remember(name) { mutableStateOf(TextFieldValue(name, selection = TextRange(0, name.length))) }
    val focus = remember { FocusRequester() }
    val nameLabel = stringResource(Res.string.style_name)
    val keyboard = LocalSoftwareKeyboardController.current
    fun commit() {
        renamed(text.text, name)?.let(onRename)
        editing = false
        keyboard?.hide()
    }
    Row(
        modifier.fillMaxWidth().drawBehind { drawBottomHairline() }.padding(CgSpace.S8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackButton(onClick = onBack, modifier = Modifier.padding(end = CgSpace.S8))
        if (editing) {
            var focused by remember { mutableStateOf(false) }
            BasicTextField(
                text,
                onValueChange = { text = it.copy(text = typedName(it.text)) },
                singleLine = true,
                textStyle = CgTheme.type.label.copy(color = CgColors.Text.Primary),
                cursorBrush = SolidColor(CgColors.Brand.Signal),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commit() }),
                modifier =
                    Modifier.weight(1f)
                        .focusRequester(focus)
                        .onFocusChanged {
                            if (focused && !it.isFocused) commit()
                            focused = it.isFocused
                        }
                        .semantics { contentDescription = nameLabel },
            )
            LaunchedEffect(Unit) { focus.requestFocus() }
        } else {
            Row(
                Modifier.weight(1f)
                    .heightIn(min = CgSize.Touch)
                    .drawBehind { dashedRule(CgColors.Line.Hairline) }
                    .clickable(role = Role.Button, onClickLabel = stringResource(Res.string.style_rename)) {
                        text = TextFieldValue(name, selection = TextRange(0, name.length))
                        editing = true
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    name.caps(),
                    style = CgTheme.type.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier.weight(1f, fill = false).padding(horizontal = CgSpace.S4).semantics { heading() },
                )
                CgIcon(CgIcons.Edit, tint = CgColors.Text.Dim, size = EDIT_ICON)
            }
        }
        // Where the bars are native (iPhone Duo) save is their prominent item, not a key here.
        val saveTitle = stringResource(Res.string.style_save)
        NativeItems(
            listOf(
                NativeBarItem(
                    TestTags.StyleEditorSave,
                    saveTitle,
                    BarIcon.Save,
                    prominent = true,
                    priority = BarPriority.High,
                )
            )
        ) {
            if (editing) commit()
            onSave()
        }
        if (LocalNativeChrome.current != null) return@Row
        Box(
            Modifier.padding(start = CgSpace.S8)
                .testTag(TestTags.StyleEditorSave)
                .heightIn(min = CgSize.Touch)
                .background(CgColors.Brand.Signal)
                .clickable(role = Role.Button) {
                    if (editing) commit()
                    onSave()
                }
                .padding(horizontal = CgSpace.S16),
            Alignment.Center,
        ) {
            Text(
                stringResource(Res.string.style_save).caps(),
                style = CgTheme.type.labelStrong,
                color = CgColors.Text.OnLight,
            )
        }
    }
}

/** The dashed rule under the style's name, which says it can be typed over. */
private fun DrawScope.dashedRule(color: Color) {
    val y = size.height - CgStroke.Hairline.toPx() / 2
    drawLine(
        color,
        Offset(0f, y),
        Offset(size.width, y),
        CgStroke.Hairline.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
    )
}

/** The header: a 44 dp key with 8 dp around it. */
internal val HEADER_HEIGHT = CgSize.Touch + CgSpace.S8 * 2

private val EDIT_ICON = 14.dp
