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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.draw.drawBehind
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
import app.onlyshots.core.designsystem.CgSize
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgStroke
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.caps
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * A switch: a 56 × 32 square track with a square knob. On, track and edge are the signal and the knob ink; off, a
 * hairline track with a hairline knob. 44 dp tall to touch.
 */
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
    val track by animateColorAsState(if (checked) CgColors.Brand.Signal else Color.Transparent, CgMotion.short())
    val edge by animateColorAsState(if (checked) CgColors.Brand.Signal else CgColors.Line.Hairline, CgMotion.short())
    val knob by animateColorAsState(if (checked) CgColors.Text.OnLight else CgColors.Line.Hairline, CgMotion.short())
    val alpha by animateFloatAsState(if (enabled) 1f else 0.4f, CgMotion.short())
    Box(
        modifier
            .size(width = 56.dp, height = CgSize.Touch)
            .graphicsLayer { this.alpha = alpha }
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(width = 56.dp, height = 32.dp)
                .background(track)
                .border(CgStroke.Hairline, edge)
                .padding(4.dp),
            contentAlignment = BiasAlignment(knobAt, 0f),
        ) {
            Box(Modifier.size(24.dp).background(knob))
        }
    }
}

/**
 * Figma "Series chip": a cell with the series' colour as a short bar and its name in Mono capitals. Chosen, the cell is
 * raised; a series the source cannot provide stays visible but greyed, so the reason can be explained next to it.
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
    val bg by animateColorAsState(if (selected && enabled) CgColors.Surface.Raised else CgColors.Surface.Base)
    val ink =
        when {
            !enabled -> CgColors.Text.Disabled
            selected -> CgColors.Text.Primary
            else -> CgColors.Text.Secondary
        }
    Row(
        modifier
            .defaultMinSize(minHeight = 48.dp)
            .background(bg)
            .border(CgStroke.Hairline, CgColors.Line.Hairline)
            .toggleable(selected, enabled = enabled, role = Role.Checkbox, onValueChange = { onClick() })
            .padding(horizontal = CgSpace.S12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CgSpace.S8),
    ) {
        Box(Modifier.size(width = 12.dp, height = 3.dp).background(if (enabled) color else CgColors.Line.Hairline))
        Text(
            label.caps(),
            style = CgTheme.type.labelStrong.copy(letterSpacing = CgTheme.type.data.letterSpacing),
            color = ink,
        )
    }
}

/**
 * The one segmented choice: a row of cells inside a hairline frame, with hairlines between them; the chosen cell is
 * filled bone with ink letters. [fill] stretches the cells over the whole width; otherwise each is as wide as its
 * label. 48 dp tall, never less than 44.
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
    // The bone fill slides from the old choice to the new one: its edges are read from where the cells were placed
    // (layout, the same frame), and only drawing follows the animated index, so a still frame shows it settled.
    val edges = remember(options.size) { FloatArray(options.size * 2) }
    val at = remember { Animatable(selectedIndex.toFloat()) }
    LaunchedEffect(selectedIndex) { at.animateTo(selectedIndex.toFloat(), CgMotion.settle()) }
    Row(
        modifier.border(CgStroke.Hairline, CgColors.Line.Hairline).drawBehind {
            if (options.isEmpty()) return@drawBehind
            val v = at.value.coerceIn(0f, options.lastIndex.toFloat())
            val a = floor(v).toInt()
            val b = ceil(v).toInt()
            val t = v - a
            val left = lerp(edges[a * 2], edges[b * 2], t)
            val right = lerp(edges[a * 2 + 1], edges[b * 2 + 1], t)
            // Only while it slides: settled, the chosen cell wears the fill itself.
            if (!at.isRunning || right <= left) return@drawBehind
            drawRect(CgColors.Brand.Bone, Offset(left, 0f), Size(right - left, size.height))
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selectedIndex
            val ink by animateColorAsState(if (on) CgColors.Text.OnLight else CgColors.Text.Primary, CgMotion.short())
            if (i > 0) Box(Modifier.width(CgStroke.Hairline).heightIn(min = 48.dp).background(CgColors.Line.Hairline))
            Box(
                // Stretched, each takes room by its label's length, so "weight settles" fits beside "fixed".
                (if (fill) Modifier.weight(label.length.coerceAtLeast(MIN_SEGMENT_CHARS).toFloat()) else Modifier)
                    // A minimum, not a height: at 200 % font the label must still fit.
                    .heightIn(min = 48.dp)
                    .then(tags?.getOrNull(i)?.let { Modifier.testTag(it) } ?: Modifier)
                    .onPlaced {
                        val x = it.positionInParent().x
                        if (i * 2 + 1 < edges.size) {
                            edges[i * 2] = x
                            edges[i * 2 + 1] = x + it.size.width
                        }
                    }
                    .background(if (on && !at.isRunning) CgColors.Brand.Bone else Color.Transparent)
                    .selectable(on, role = Role.RadioButton, onClick = { onSelect(i) })
                    .padding(horizontal = if (fill) 4.dp else 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label.caps(), style = CgTheme.type.labelStrong, color = ink, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Figma "Stepper": − value + as three cells in a hairline frame, the keys 44 dp square. */
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
        modifier.height(CgSize.Touch).border(CgStroke.Hairline, CgColors.Line.Hairline),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperKey("−", minusEnabled, onMinus)
        Box(Modifier.width(CgStroke.Hairline).fillMaxHeight().background(CgColors.Line.Hairline))
        Text(
            value.caps(),
            style = CgTheme.type.label,
            modifier = Modifier.widthIn(min = 56.dp).padding(horizontal = CgSpace.S4),
            textAlign = TextAlign.Center,
        )
        Box(Modifier.width(CgStroke.Hairline).fillMaxHeight().background(CgColors.Line.Hairline))
        StepperKey("+", plusEnabled, onPlus)
    }
}

@Composable
private fun StepperKey(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(CgSize.Touch).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            symbol,
            style = CgTheme.type.cardTitle,
            color = if (enabled) CgColors.Text.Primary else CgColors.Text.Disabled,
        )
    }
}

/**
 * Figma's slider: a 2 dp hairline track, the filled part the signal, a 12 × 16 bone thumb; 44 dp tall to touch. Tap or
 * drag anywhere on it. With [steps], the value snaps to that many points between the ends, as Material's slider does.
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
        val track = CgStroke.Rule.toPx()
        val half = 6.dp.toPx()
        val thumbH = 16.dp.toPx()
        val x = half + (size.width - 2 * half) * shown.value
        drawRect(CgColors.Line.Hairline, Offset(half, y - track / 2), Size(size.width - 2 * half, track))
        drawRect(CgColors.Brand.Signal, Offset(half, y - track / 2), Size(x - half, track))
        drawRect(CgColors.Brand.Bone, Offset(x - half, y - thumbH / 2), Size(2 * half, thumbH))
    }
}

/**
 * A setting: its name in Mono capitals (and a sentence about it, if [hint]) on the left, its control on the right; 44
 * dp tall at least. Every settings list in the app is built from these.
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
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CgSpace.S4)) {
            Text(title.caps(), style = CgTheme.type.label)
            if (hint != null) Text(hint, style = CgTheme.type.bodySmall, color = CgColors.Text.Secondary)
        }
        control()
    }
}

/** A filled segment is never narrower than a label of this many characters. */
private const val MIN_SEGMENT_CHARS = 6
