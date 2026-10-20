package app.onlyshots.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgShape
import app.onlyshots.core.designsystem.CgSize
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.core_rule_done
import app.onlyshots.core.resources.core_rule_waiting
import kotlin.math.sqrt
import org.jetbrains.compose.resources.stringResource

/** One shape a video can take, for [FormatPicker]: its name and its width over its height. */
data class FormatOption(val label: String, val ratio: Float)

/**
 * The video's shape, picked from [options]: each a button with its outline drawn to scale and its name, the chosen one
 * white. Tall: 72 dp tiles, the outline above the name (a book foldable's Home). Compact: 48 dp pills, side by side.
 */
@Composable
fun FormatPicker(
    options: List<FormatOption>,
    selected: Int,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CgSpace.S8)) {
        options.forEachIndexed { i, option ->
            val on = i == selected
            val ink = if (on) CgColors.Text.OnLight else CgColors.Text.Primary
            val shape = if (compact) CgShape.Pill else RoundedCornerShape(CgRadiusTile)
            val outline = @Composable { FormatOutline(option.ratio, if (on) ink else CgColors.Text.Secondary, compact) }
            val label =
                @Composable { Text(option.label, style = CgTheme.type.labelStrong.copy(fontSize = 13.sp), color = ink) }
            val body =
                Modifier.weight(1f)
                    .height(if (compact) 48.dp else 72.dp)
                    .clip(shape)
                    .background(if (on) CgColors.Text.Primary else CgColors.Surface.Raised)
                    .clickable(role = Role.Button) { onPick(i) }
                    .semantics { this.selected = on }
            if (compact) {
                Row(body, Arrangement.spacedBy(CgSpace.S8, Alignment.CenterHorizontally), Alignment.CenterVertically) {
                    outline()
                    label()
                }
            } else {
                Column(
                    body,
                    Arrangement.spacedBy(CgSpace.S6, Alignment.CenterVertically),
                    Alignment.CenterHorizontally,
                ) {
                    outline()
                    label()
                }
            }
        }
    }
}

/** The format's outline, the same area for every shape, so a wide one reads as wide and a tall one as tall. */
@Composable
private fun FormatOutline(ratio: Float, color: Color, small: Boolean) {
    val area = if (small) 150f else 370f
    val h = sqrt(area / ratio)
    val w = h * ratio
    Box(Modifier.size(w.dp, h.dp).border(2.dp, color, RoundedCornerShape(if (small) 2.dp else 3.dp)))
}

private val CgRadiusTile = 12.dp

/**
 * A reading: its [label] over its value, large and light, in the series' [color], with a small [unit]. [large] is the
 * record panel's 40 sp; otherwise Home's 24 sp.
 */
@Composable
fun ReadingTile(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    color: Color = CgColors.Text.Primary,
    large: Boolean = false,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(CgRadiusTile))
            .background(CgColors.Surface.Raised)
            .padding(horizontal = if (large) CgSpace.S14 else CgSpace.S12, vertical = if (large) CgSpace.S12 else 10.dp)
    ) {
        Text(label, style = CgTheme.type.caption, color = CgColors.Text.Secondary, maxLines = 1)
        Text(
            buildAnnotatedString {
                append(value)
                if (unit.isNotEmpty()) withStyle(SpanStyle(fontSize = if (large) 14.sp else 12.sp)) { append(" $unit") }
            },
            style = CgTheme.type.valueLarge.copy(fontSize = if (large) 40.sp else 24.sp),
            color = color,
            maxLines = 1,
        )
    }
}

/** A recording rule with its switch: "Start with the shot", and what that means under it. */
@Composable
fun RuleRow(
    title: String,
    detail: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingRow(title, modifier.padding(vertical = CgSpace.S4), hint = detail) {
        CgToggle(checked = checked, onCheckedChange = onToggle)
    }
}

/** A rule while it runs: its words, and "done" in green once it has happened or "waiting" until then. */
@Composable
fun RuleStatusRow(text: String, done: Boolean, modifier: Modifier = Modifier, mark: @Composable () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().heightIn(min = CgSize.Touch).padding(horizontal = CgSpace.S14),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        mark()
        Text(text, style = CgTheme.type.label, modifier = Modifier.weight(1f))
        Text(
            stringResource(if (done) Res.string.core_rule_done else Res.string.core_rule_waiting),
            style = CgTheme.type.caption,
            color = if (done) CgColors.Status.Live else CgColors.Text.Secondary,
        )
    }
}

/** An amber note about the take: a format that will not record the way the screen is held, and what to do. */
@Composable
fun TakeNotice(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CgRadiusTile))
            .background(NoticeGround)
            .padding(
                horizontal = CgSpace.S12,
                vertical = 10.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(CgSpace.S8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(CgColors.Status.Highlight))
        Text(text, style = CgTheme.type.caption, color = NoticeInk, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) Pill(action, onClick = onAction, light = true)
    }
}

private val NoticeGround = Color(0xFF2A2010)
private val NoticeInk = Color(0xFFF2DDB0)

/** The source a take uses: a live dot and its name, in a raised pill. */
@Composable
fun SourceChip(text: String, live: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier.height(36.dp).clip(CgShape.Pill).background(CgColors.Surface.Raised).padding(horizontal = CgSpace.S14),
        horizontalArrangement = Arrangement.spacedBy(CgSpace.S8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (live) CgColors.Status.Live else CgColors.Text.Dim))
        Text(text, style = CgTheme.type.label, maxLines = 1)
    }
}
