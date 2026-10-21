package app.onlyshots.core.designsystem.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.onlyshots.TestTags
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgMotion
import app.onlyshots.core.designsystem.CgSize
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgStroke
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.caps
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.core_rule_done
import app.onlyshots.core.resources.core_rule_waiting
import kotlin.math.sqrt
import org.jetbrains.compose.resources.stringResource

/** One shape a video can take, for [FormatPicker]: its name and its width over its height. */
data class FormatOption(val label: String, val ratio: Float)

/**
 * The video's shape, picked from [options]: a row of cells in a hairline frame, each with its outline drawn to scale
 * and its name in Mono; the chosen one is the signal with ink on it. Tall: 64 dp cells, the outline above the name (a
 * book foldable's Home). Compact: 48 dp cells, outline beside the name.
 */
@Composable
fun FormatPicker(
    options: List<FormatOption>,
    selected: Int,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Row(modifier.fillMaxWidth().border(CgStroke.Hairline, CgColors.Line.Hairline)) {
        options.forEachIndexed { i, option ->
            val on = i == selected
            val ink by animateColorAsState(if (on) CgColors.Text.OnLight else CgColors.Text.Primary, CgMotion.short())
            val ground by animateColorAsState(if (on) CgColors.Brand.Signal else Color.Transparent, CgMotion.short())
            val edge by
                animateColorAsState(if (on) CgColors.Text.OnLight else CgColors.Text.Secondary, CgMotion.short())
            if (i > 0) {
                Box(
                    Modifier.width(CgStroke.Hairline)
                        .height(if (compact) 48.dp else 64.dp)
                        .background(CgColors.Line.Hairline)
                )
            }
            val outline = @Composable { FormatOutline(option.ratio, edge, compact) }
            val label = @Composable { Text(option.label.caps(), style = CgTheme.type.labelStrong, color = ink) }
            val body =
                Modifier.weight(1f)
                    .testTag(TestTags.format(option.label))
                    .height(if (compact) 48.dp else 64.dp)
                    .background(ground)
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
    val area = if (small) 150f else 240f
    val h = sqrt(area / ratio)
    val w = h * ratio
    Box(Modifier.size(w.dp, h.dp).border(CgStroke.Hairline, color))
}

/**
 * A reading: a cell with its [label] in small Mono capitals over its value in Mono, in the series' [color], with a
 * small [unit]. [large] is the record panel's 28 sp; otherwise 18 sp.
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
            .background(CgColors.Surface.Base)
            .border(CgStroke.Hairline, CgColors.Line.Divider)
            .padding(horizontal = 10.dp, vertical = CgSpace.S6)
    ) {
        Text(label.caps(), style = CgTheme.type.data, color = CgColors.Text.Secondary, maxLines = 1)
        // The first reading fades in over the dash; after that the number just ticks (ten times a second, a fade on
        // each would blur it).
        AnimatedContent(
            value,
            contentKey = { v -> v.any { it.isDigit() } },
            transitionSpec = { fadeIn(CgMotion.medium()) togetherWith fadeOut(CgMotion.short()) },
            label = "reading",
        ) { shown ->
            val style =
                if (large) CgTheme.type.pivot.copy(letterSpacing = CgTheme.type.cardTitle.letterSpacing)
                else CgTheme.type.cardTitle
            Text(
                buildAnnotatedString {
                    append(shown)
                    if (unit.isNotEmpty())
                        withStyle(SpanStyle(fontSize = CgTheme.type.data.fontSize)) { append(" $unit") }
                },
                style = style,
                color = color,
                maxLines = 1,
            )
        }
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

/**
 * A rule while it runs: its words in Mono capitals, and "done" in green once it has happened or "waiting" until then.
 */
@Composable
fun RuleStatusRow(text: String, done: Boolean, modifier: Modifier = Modifier, mark: @Composable () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().heightIn(min = CgSize.Touch).padding(horizontal = CgSpace.S16),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        mark()
        Text(text.caps(), style = CgTheme.type.labelStrong, modifier = Modifier.weight(1f))
        val ink by
            animateColorAsState(if (done) CgColors.Status.Success else CgColors.Text.Secondary, CgMotion.medium())
        AnimatedContent(
            done,
            transitionSpec = {
                (fadeIn(CgMotion.medium()) + scaleIn(CgMotion.springy(), initialScale = 0.8f)) togetherWith
                    fadeOut(CgMotion.short())
            },
            label = "rule",
        ) { now ->
            Text(
                stringResource(if (now) Res.string.core_rule_done else Res.string.core_rule_waiting).caps(),
                style = CgTheme.type.labelStrong,
                color = ink,
            )
        }
    }
}

/**
 * An amber note about the take (a format that will not record the way the screen is held, and what to do): a hairline
 * frame and Mono capitals in the warning colour over the ink.
 */
@Composable
fun TakeNotice(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .background(CgColors.Surface.Base.copy(alpha = 0.85f))
            .border(CgStroke.Hairline, CgColors.Status.Warning)
            .padding(horizontal = 10.dp, vertical = CgSpace.S6),
        horizontalArrangement = Arrangement.spacedBy(CgSpace.S8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(CgColors.Status.Warning))
        Text(
            text.caps(),
            style = CgTheme.type.labelStrong,
            color = CgColors.Status.Warning,
            modifier = Modifier.weight(1f),
        )
        if (action != null && onAction != null) Pill(action, onClick = onAction, light = true)
    }
}

/** The source a take uses: a square live dot and its name in Mono capitals, on a 44 dp key with a hairline edge. */
@Composable
fun SourceChip(text: String, live: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(CgSize.Touch)
            .background(CgColors.Surface.Base.copy(alpha = 0.85f))
            .border(CgStroke.Hairline, CgColors.Line.Hairline)
            .padding(horizontal = CgSpace.S12),
        horizontalArrangement = Arrangement.spacedBy(CgSpace.S8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val dot by animateColorAsState(if (live) CgColors.Status.Live else CgColors.Text.Disabled, CgMotion.medium())
        Box(Modifier.size(6.dp).background(dot))
        Text(
            text.caps(),
            style = CgTheme.type.labelStrong.copy(letterSpacing = CgTheme.type.data.letterSpacing),
            maxLines = 1,
        )
    }
}
