package app.onlyshots.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgMotion
import app.onlyshots.core.designsystem.CgShape
import app.onlyshots.core.designsystem.CgSize
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgTheme
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** A switch: a pill track, white when on, with a round knob; 44 dp tall to touch. */
@Composable
fun CgToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    // The knob slides and the colours follow it; both start where they belong, so a still frame shows them settled.
    // As an alignment's bias, so the settled knob sits on exactly the pixels Start and End put it on.
    val knobAt by animateFloatAsState(if (checked) 1f else -1f, CgMotion.settle(), label = "knob")
    val track by animateColorAsState(if (checked) CgColors.Text.Primary else CgColors.Surface.Raised, CgMotion.short())
    val knob by animateColorAsState(if (checked) CgColors.Text.OnLight else CgColors.Text.Primary, CgMotion.short())
    val alpha by animateFloatAsState(if (enabled) 1f else 0.4f, CgMotion.short())
    Box(
        modifier
            .size(width = 52.dp, height = CgSize.Touch)
            .graphicsLayer { this.alpha = alpha }
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(width = 46.dp, height = 26.dp).clip(CgShape.Pill).background(track).padding(3.dp),
            contentAlignment = BiasAlignment(knobAt, 0f),
        ) {
            Box(Modifier.size(20.dp).clip(CircleShape).background(knob))
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
    val alpha by animateFloatAsState(if (enabled) 1f else 0.35f, CgMotion.short())
    val bg by animateColorAsState(if (selected) CgColors.Surface.Raised else Color.Transparent, CgMotion.short())
    val edge by animateColorAsState(if (selected) color else CgColors.Surface.Raised, CgMotion.short())
    Row(
        modifier
            .defaultMinSize(minHeight = CgSize.Touch)
            .clip(CgShape.Chip)
            .background(bg)
            .border(2.dp, edge.copy(alpha = alpha), CgShape.Chip)
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
    /** A test tag per option ([app.onlyshots.TestTags]), for the segments UI tests pick. */
    tags: List<String>? = null,
) {
    // The white pill slides from the old choice to the new one: its edges are read from where the segments were placed
    // (layout, the same frame), and only drawing follows the animated index, so a still frame shows it settled.
    val edges = remember(options.size) { FloatArray(options.size * 2) }
    val at = remember { Animatable(selectedIndex.toFloat()) }
    LaunchedEffect(selectedIndex) { at.animateTo(selectedIndex.toFloat(), CgMotion.settle()) }
    Row(
        modifier.clip(CgShape.Pill).background(CgColors.Surface.Raised).padding(3.dp).drawBehind {
            if (options.isEmpty()) return@drawBehind
            val v = at.value.coerceIn(0f, options.lastIndex.toFloat())
            val a = floor(v).toInt()
            val b = ceil(v).toInt()
            val t = v - a
            val left = lerp(edges[a * 2], edges[b * 2], t)
            val right = lerp(edges[a * 2 + 1], edges[b * 2 + 1], t)
            // Only while it slides: settled, the chosen segment wears the pill itself.
            if (!at.isRunning || right <= left) return@drawBehind
            drawRoundRect(
                CgColors.Text.Primary,
                Offset(left, 0f),
                Size(right - left, size.height),
                CornerRadius(size.height / 2),
            )
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selectedIndex
            val ink by animateColorAsState(if (on) CgColors.Text.OnLight else CgColors.Text.Primary, CgMotion.short())
            Box(
                // Stretched, each takes room by its label's length, so "weight settles" fits beside "fixed".
                (if (fill) Modifier.weight(label.length.coerceAtLeast(MIN_SEGMENT_CHARS).toFloat()) else Modifier)
                    // A minimum, not a height: at 200 % font the label must still fit.
                    .heightIn(min = CgSize.Touch - 6.dp)
                    .then(tags?.getOrNull(i)?.let { Modifier.testTag(it) } ?: Modifier)
                    .onPlaced {
                        val x = it.positionInParent().x
                        if (i * 2 + 1 < edges.size) {
                            edges[i * 2] = x
                            edges[i * 2 + 1] = x + it.size.width
                        }
                    }
                    .clip(CgShape.Pill)
                    .background(if (on && !at.isRunning) CgColors.Text.Primary else Color.Transparent)
                    .selectable(on, role = Role.RadioButton, onClick = { onSelect(i) })
                    .padding(horizontal = if (fill) 4.dp else 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = CgTheme.type.label,
                    color = ink,
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
    // A drag follows the finger exactly; a tap (or a value set from elsewhere) glides the thumb there.
    var dragging by remember { mutableStateOf(false) }
    val shown = remember { Animatable(f) }
    LaunchedEffect(f) { if (dragging) shown.snapTo(f) else shown.animateTo(f, CgMotion.settle()) }
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
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { c, _ ->
                    onValueChange(at(c.position.x, size.width.toFloat()))
                }
            }
    ) {
        val y = size.height / 2f
        val track = 4.dp.toPx()
        val r = 9.dp.toPx()
        val x = r + (size.width - 2 * r) * shown.value
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
