package app.onlyshots.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.onlyshots.TestTags
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgStroke
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.caps
import app.onlyshots.testTagsAsResourceIds

/**
 * The one text field: a square box on ink with a hairline edge that turns signal while it has focus, Mono letters, a
 * signal cursor, [placeholder] in dim text while it is empty. 52 dp tall at least.
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
    var focused by remember { mutableStateOf(false) }
    val text = CgTheme.type.label.copy(fontWeight = FontWeight.Normal, letterSpacing = 0.em)
    BasicTextField(
        value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = text.copy(color = CgColors.Text.Primary),
        cursorBrush = SolidColor(CgColors.Brand.Signal),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        modifier =
            modifier
                .onFocusChanged { focused = it.isFocused }
                .semantics { if (label != null) contentDescription = label },
        decorationBox = { field ->
            Box(
                Modifier.fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .background(CgColors.Surface.Base)
                    .border(CgStroke.Hairline, if (focused) CgColors.Brand.Signal else CgColors.Line.Hairline)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty() && placeholder != null) Text(placeholder, style = text, color = CgColors.Text.Dim)
                field()
            }
        },
    )
}

/** Which of a dialog's two choices is the one that loses something: it is drawn in signal letters, never filled. */
enum class DialogRisk {
    Confirm,
    Dismiss,
}

/**
 * The one dialog: a square card in the sheets' colour under a signal rule, a title in Mono capitals, the [text] or
 * [content] under it, and its two choices as cells across the bottom: [dismiss] plain on the left, [confirm] bone on
 * the right. The [risk]y one, if any, is drawn in signal letters instead and always sits on the right, so the safe one
 * keeps the left (keep editing | leave); a dialog whose confirm is the risk (a delete) wears the record red rule.
 * Tapping outside is [onDismiss].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CgDialog(
    title: String,
    onDismiss: () -> Unit,
    confirm: Pair<String, () -> Unit>,
    modifier: Modifier = Modifier,
    dismiss: Pair<String, () -> Unit>? = null,
    text: String? = null,
    confirmEnabled: Boolean = true,
    risk: DialogRisk? = null,
    content: (@Composable () -> Unit)? = null,
) {
    BasicAlertDialog(onDismissRequest = onDismiss, modifier = modifier.testTagsAsResourceIds()) {
        CgDialogCard(title, confirm, dismiss, text, confirmEnabled, risk, content)
    }
}

/** [CgDialog]'s contents without the dialog window, so the screenshot tests can draw it. */
@Composable
fun CgDialogCard(
    title: String,
    confirm: Pair<String, () -> Unit>,
    dismiss: Pair<String, () -> Unit>? = null,
    text: String? = null,
    confirmEnabled: Boolean = true,
    risk: DialogRisk? = null,
    content: (@Composable () -> Unit)? = null,
) {
    Column(
        Modifier.widthIn(max = 480.dp)
            .background(CgColors.Surface.Bar)
            .border(CgStroke.Hairline, CgColors.Line.Hairline)
            .drawBehind {
                val rule = if (risk == DialogRisk.Confirm) CgColors.Status.Record else CgColors.Line.Hairline
                drawRect(rule, Offset.Zero, Size(size.width, CgStroke.Rule.toPx()))
            }
    ) {
        Column(
            Modifier.padding(CgSpace.S16).padding(top = CgStroke.Rule),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title.caps(), style = CgTheme.type.cardTitle, modifier = Modifier.semantics { heading() })
            if (text != null) {
                Text(
                    text,
                    style = CgTheme.type.body.copy(fontSize = 14.sp, lineHeight = 21.sp),
                    color = CgColors.Text.Secondary,
                )
            }
            content?.invoke()
        }
        Box(Modifier.fillMaxWidth().height(CgStroke.Hairline).background(CgColors.Line.Hairline))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            val dismissCell: (@Composable RowScope.() -> Unit)? = dismiss?.let { (label, onClick) ->
                {
                    DialogChoice(
                        label,
                        onClick,
                        filled = false,
                        risky = risk == DialogRisk.Dismiss,
                        modifier = Modifier.weight(1f).testTag(TestTags.DialogDismiss),
                    )
                }
            }
            val confirmCell: @Composable RowScope.() -> Unit = {
                DialogChoice(
                    confirm.first,
                    confirm.second,
                    filled = risk != DialogRisk.Confirm,
                    risky = risk == DialogRisk.Confirm,
                    enabled = confirmEnabled,
                    modifier = Modifier.weight(1f).testTag(TestTags.DialogConfirm),
                )
            }
            val divider: @Composable () -> Unit = {
                Box(Modifier.width(CgStroke.Hairline).fillMaxHeight().background(CgColors.Line.Hairline))
            }
            // The risky choice on the right; the safe one, on the left.
            val cells =
                if (risk == DialogRisk.Dismiss) listOfNotNull(confirmCell, dismissCell)
                else listOfNotNull(dismissCell, confirmCell)
            cells.forEachIndexed { i, cell ->
                if (i > 0) divider()
                cell()
            }
        }
    }
}

/** One of a dialog's choices: a 52 dp cell across half the card. */
@Composable
private fun DialogChoice(
    label: String,
    onClick: () -> Unit,
    filled: Boolean,
    risky: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val bg = if (filled && enabled) CgColors.Brand.Bone else Color.Transparent
    val ink =
        when {
            !enabled -> CgColors.Text.Disabled
            risky -> CgColors.Brand.Signal
            filled -> CgColors.Text.OnLight
            else -> CgColors.Text.Primary
        }
    Box(
        modifier
            .heightIn(min = 52.dp)
            .background(bg)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = CgSpace.S8),
        contentAlignment = Alignment.Center,
    ) {
        Text(label.caps(), style = CgTheme.type.labelStrong, color = ink, textAlign = TextAlign.Center)
    }
}
