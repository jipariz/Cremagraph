package app.onlyshots.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgShape
import app.onlyshots.core.designsystem.CgSize
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgTheme
import kotlin.math.roundToInt

/** A switch: a pill track, white when on, with a round knob; 44 dp tall to touch. */
@Composable
fun CgToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .size(width = 52.dp, height = CgSize.Touch)
            .alpha(if (enabled) 1f else 0.4f)
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(width = 46.dp, height = 26.dp)
                .clip(CgShape.Pill)
                .background(if (checked) CgColors.Text.Primary else CgColors.Surface.Raised)
                .padding(3.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                Modifier.size(20.dp)
                    .clip(CircleShape)
                    .background(if (checked) CgColors.Text.OnLight else CgColors.Text.Primary)
            )
        }
    }
}

/**
 * Figma "Series chip". A series the source cannot provide stays visible but disabled, so the reason can be explained
 * next to it.
 */
@Composable
fun SeriesChip(
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val alpha = if (enabled) 1f else 0.35f
    Row(
        modifier
            .defaultMinSize(minHeight = CgSize.Touch)
            .clip(CgShape.Chip)
            .background(if (selected) CgColors.Surface.Raised else Color.Transparent)
            .border(2.dp, (if (selected) color else CgColors.Surface.Raised).copy(alpha = alpha), CgShape.Chip)
            .toggleable(selected, enabled = enabled, role = Role.Checkbox, onValueChange = { onClick() })
            .padding(horizontal = CgSpace.S12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CgSpace.S8),
    ) {
        Box(Modifier.size(width = 12.dp, height = 3.dp).clip(CgShape.Pill).background(color.copy(alpha = alpha)))
        Text(label, style = CgTheme.type.label, color = CgColors.Text.Primary.copy(alpha = alpha))
    }
}

/**
 * The one segmented choice: a rounded track on the raised surface, the chosen option a white pill. [fill] stretches the
 * segments over the whole width; otherwise each is as wide as its label. 44 dp tall to touch.
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    fill: Boolean = false,
) {
    Row(
        modifier.clip(CgShape.Pill).background(CgColors.Surface.Raised).padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selectedIndex
            Box(
                // Stretched, each takes room by its label's length, so "weight settles" fits beside "fixed".
                (if (fill) Modifier.weight(label.length.coerceAtLeast(MIN_SEGMENT_CHARS).toFloat()) else Modifier)
                    // A minimum, not a height: at 200 % font the label must still fit.
                    .heightIn(min = CgSize.Touch - 6.dp)
                    .clip(CgShape.Pill)
                    .background(if (on) CgColors.Text.Primary else Color.Transparent)
                    .selectable(on, role = Role.RadioButton, onClick = { onSelect(i) })
                    .padding(horizontal = if (fill) 4.dp else 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = CgTheme.type.label,
                    color = if (on) CgColors.Text.OnLight else CgColors.Text.Primary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Figma "Stepper": − value + on one rounded track, with 44 dp round keys. */
@Composable
fun CgStepper(
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
    minusEnabled: Boolean = true,
    plusEnabled: Boolean = true,
) {
    Row(
        modifier.clip(CgShape.Pill).background(CgColors.Surface.Raised),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperKey("−", minusEnabled, onMinus)
        Text(
            value,
            style = CgTheme.type.body,
            modifier = Modifier.widthIn(min = 56.dp).padding(horizontal = CgSpace.S4),
            textAlign = TextAlign.Center,
        )
        StepperKey("+", plusEnabled, onPlus)
    }
}

@Composable
private fun StepperKey(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(CgSize.Touch)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = CgTheme.type.cardTitle, color = if (enabled) CgColors.Text.Primary else CgColors.Text.Dim)
    }
}

/**
 * Figma's slider: a 4 dp rounded track, the filled part white, an 18 dp round thumb; 44 dp tall to touch. Tap or drag
 * anywhere on it. With [steps], the value snaps to that many points between the ends, as Material's slider does.
 */
@Composable
fun CgSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    label: String? = null,
) {
    val span = range.endInclusive - range.start
    fun snapped(v: Float): Float {
        val c = v.coerceIn(range)
        if (steps <= 0 || span <= 0f) return c
        val step = span / (steps + 1)
        return range.start + ((c - range.start) / step).roundToInt() * step
    }
    val f = if (span > 0f) ((value - range.start) / span).coerceIn(0f, 1f) else 0f
    fun at(x: Float, width: Float) = snapped(range.start + (x / width).coerceIn(0f, 1f) * span)
    Canvas(
        // As wide as it is allowed, like Material's slider: a width or weight from the caller still decides.
        modifier
            .fillMaxWidth()
            .height(CgSize.Touch)
            .semantics {
                if (label != null) contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(value, range, steps)
                setProgress { v ->
                    onValueChange(snapped(v))
                    true
                }
            }
            .pointerInput(range, steps) { detectTapGestures { onValueChange(at(it.x, size.width.toFloat())) } }
            .pointerInput(range, steps) {
                detectHorizontalDragGestures { c, _ -> onValueChange(at(c.position.x, size.width.toFloat())) }
            }
    ) {
        val y = size.height / 2f
        val track = 4.dp.toPx()
        val r = 9.dp.toPx()
        val x = r + (size.width - 2 * r) * f
        drawRoundRect(
            CgColors.Surface.Raised,
            Offset(r, y - track / 2),
            Size(size.width - 2 * r, track),
            CornerRadius(track / 2),
        )
        drawRoundRect(CgColors.Text.Primary, Offset(r, y - track / 2), Size(x - r, track), CornerRadius(track / 2))
        drawCircle(CgColors.Text.Primary, r, Offset(x, y))
    }
}

/**
 * A setting: its name (and a line about it, if [hint]) on the left, its control on the right; 44 dp tall at least.
 * Every settings list in the app is built from these.
 */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    control: @Composable () -> Unit,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = CgSize.Touch),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CgSpace.S12),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = CgTheme.type.body)
            if (hint != null) Text(hint, style = CgTheme.type.caption, color = CgColors.Text.Secondary)
        }
        control()
    }
}

/** A filled segment is never narrower than a label of this many characters. */
private const val MIN_SEGMENT_CHARS = 6
