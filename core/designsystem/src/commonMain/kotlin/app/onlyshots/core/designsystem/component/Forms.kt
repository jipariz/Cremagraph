package app.onlyshots.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.onlyshots.TestTags
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgShape
import app.onlyshots.core.designsystem.CgSize
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.testTagsAsResourceIds

/**
 * The one text field: a rounded box on the raised surface, no outline, an orange cursor, [placeholder] in dim text
 * while it is empty. 44 dp tall at least.
 */
@Composable
fun CgTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    label: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    BasicTextField(
        value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = CgTheme.type.body.copy(color = CgColors.Text.Primary),
        cursorBrush = SolidColor(CgColors.Brand.Orange),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        modifier = modifier.semantics { if (label != null) contentDescription = label },
        decorationBox = { field ->
            Box(
                Modifier.fillMaxWidth()
                    .heightIn(min = CgSize.Touch)
                    .clip(CgShape.Field)
                    .background(CgColors.Surface.Raised)
                    .padding(horizontal = CgSpace.S12, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty() && placeholder != null) {
                    Text(placeholder, style = CgTheme.type.body, color = CgColors.Text.Dim)
                }
                field()
            }
        },
    )
}

/**
 * The one dialog: a rounded card in the sheets' colour, a title, the [text] or [content] under it, and its two choices
 * at the bottom right: [dismiss] plain, [confirm] orange. Tapping outside is [onDismiss].
 */
@Composable
fun CgDialog(
    title: String,
    onDismiss: () -> Unit,
    confirm: Pair<String, () -> Unit>,
    modifier: Modifier = Modifier,
    dismiss: Pair<String, () -> Unit>? = null,
    text: String? = null,
    confirmEnabled: Boolean = true,
    content: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        modifier = modifier.testTagsAsResourceIds(),
        containerColor = CgColors.Surface.Bar,
        shape = CgShape.Dialog,
        text = { CgDialogCard(title, confirm, dismiss, text, confirmEnabled, content) },
    )
}

/** [CgDialog]'s contents without the dialog window, so the screenshot tests can draw it. */
@Composable
fun CgDialogCard(
    title: String,
    confirm: Pair<String, () -> Unit>,
    dismiss: Pair<String, () -> Unit>? = null,
    text: String? = null,
    confirmEnabled: Boolean = true,
    content: (@Composable () -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CgSpace.S16)) {
        Text(title, style = CgTheme.type.sheetTitle, modifier = Modifier.semantics { heading() })
        if (text != null) Text(text, style = CgTheme.type.body, color = CgColors.Text.Secondary)
        content?.invoke()
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CgSpace.S8, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            dismiss?.let { (label, onClick) ->
                Pill(label, onClick, minWidth = 72.dp, modifier = Modifier.testTag(TestTags.DialogDismiss))
            }
            Pill(
                confirm.first,
                confirm.second,
                light = true,
                enabled = confirmEnabled,
                modifier = Modifier.widthIn(min = 72.dp).testTag(TestTags.DialogConfirm),
            )
        }
    }
}
