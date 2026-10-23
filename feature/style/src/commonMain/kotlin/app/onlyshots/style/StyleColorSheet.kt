package app.onlyshots.style

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgStroke
import app.onlyshots.core.designsystem.component.CgTextField
import app.onlyshots.core.designsystem.component.EditorSheet
import app.onlyshots.core.model.style.GraphStyle
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.style_hex_colour
import app.onlyshots.core.resources.style_opacity
import app.onlyshots.core.resources.style_panels
import app.onlyshots.core.resources.text
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import org.jetbrains.compose.resources.stringResource

/**
 * The canvas's colour sheet: the presets that work on video, then (for a panel's colour, [ColorTarget.panelSwatches])
 * the panel colours under their label, each six to a row; a hue strip, hex, and opacity where the colour has one.
 */
@Composable
internal fun ColorSheet(
    target: ColorTarget,
    s: GraphStyle,
    change: ((GraphStyle) -> GraphStyle) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = target.get(s)
    val alpha = if (target.withOpacity) current.opacity else 1f
    fun set(c: Int) = change { target.set(it, c.withAlpha(alpha)) }
    EditorSheet(target.title.text(), onDone = onDone, modifier = modifier) {
        SwatchGrid(Swatches, current) { set(it.color) }
        if (target.panelSwatches) {
            StyleLabel(stringResource(Res.string.style_panels))
            // Clear keeps a faint wash: white at the colour's own opacity, if that is faint already, else 16 %.
            SwatchGrid(PanelSwatches, current) { sw ->
                if (sw.clear) change { target.set(it, sw.color.withAlpha(min(alpha, CLEAR_OPACITY))) }
                else set(sw.color)
            }
        }
        HueStrip(current) { set(it) }
        // With an opacity, the hex carries it too (#RRGGBBAA), so any exact colour can be typed.
        var text by
            remember(current) { mutableStateOf(if (target.withOpacity) hexWithAlpha(current) else hex(current)) }
        Row(horizontalArrangement = Arrangement.spacedBy(CgSpace.S12), verticalAlignment = Alignment.CenterVertically) {
            StyleLabel(stringResource(Res.string.style_hex_colour), Modifier.width(SLIDER_LABEL))
            CgTextField(
                text,
                onValueChange = { t ->
                    text = t.take(if (target.withOpacity) HEX_WITH_ALPHA_LENGTH else HEX_LENGTH)
                    if (target.withOpacity) parseHexWithAlpha(t)?.let { c -> change { target.set(it, c) } }
                    else parseHex(t)?.let { set(it) }
                },
                label = stringResource(Res.string.style_hex_colour),
                modifier = Modifier.weight(1f),
            )
        }
        if (target.withOpacity) {
            SliderRow(stringResource(Res.string.style_opacity), alpha, 0f..1f, "${(alpha * 100).roundToInt()}%") { v ->
                change { target.set(it, current.withAlpha(step(v, 0.01f).coerceIn(0f, 1f))) }
            }
        }
    }
}

/** [swatches] six to a row, square, the picked one framed in signal; clear drawn hatched. */
@Composable
private fun SwatchGrid(swatches: List<Swatch>, current: Int, onPick: (Swatch) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(CgSpace.S8)) {
        for (row in swatches.chunked(SWATCH_COLUMNS)) {
            Row(horizontalArrangement = Arrangement.spacedBy(CgSpace.S8)) {
                for (sw in row) {
                    val on = sw.matches(current)
                    val name = stringResource(sw.name)
                    Box(
                        Modifier.weight(1f)
                            .aspectRatio(1f)
                            .then(
                                if (sw.clear) Modifier.drawBehind { hatch() } else Modifier.background(Color(sw.color))
                            )
                            .border(
                                if (on) CgStroke.Rule else CgStroke.Hairline,
                                if (on) CgColors.Brand.Bone else CgColors.Line.Hairline,
                            )
                            .semantics {
                                contentDescription = name
                                selected = on
                            }
                            .clickable(role = Role.RadioButton) { onPick(sw) }
                    )
                }
                repeat(SWATCH_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** The clear swatch: bars at 45° on the sheet's ground, as the canvas hatches "no colour". */
private fun DrawScope.hatch() {
    drawRect(CgColors.Surface.Bar)
    val step = 8.dp.toPx()
    val w = 4.dp.toPx() / sqrt(2f) * 2f
    clipRect {
        var x = -size.height
        while (x < size.width) {
            drawLine(CgColors.Line.Hairline, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = w / 2f)
            x += step
        }
    }
}

/** The hues across a strip; a tap or drag takes that hue at the colour's own saturation and brightness. */
@Composable
private fun HueStrip(current: Int, onPick: (Int) -> Unit) {
    val (_, s0, v0) = current.toHsv()
    val (sat, value) = hueStripTone(s0, v0)
    // The caller's pick carries the colour's current opacity: keep the gestures calling the latest one.
    val pickNow by rememberUpdatedState(onPick)
    Canvas(
        Modifier.fillMaxWidth()
            .height(HUE_STRIP_HEIGHT)
            .border(CgStroke.Hairline, CgColors.Line.Hairline)
            .pointerInput(sat, value) {
                fun pick(x: Float) = pickNow(hsv((x / size.width).coerceIn(0f, 1f) * 360f, sat, value))
                detectTapGestures { pick(it.x) }
            }
            .pointerInput(sat, value) {
                detectHorizontalDragGestures { change, _ ->
                    pickNow(hsv((change.position.x / size.width).coerceIn(0f, 1f) * 360f, sat, value))
                }
            }
    ) {
        drawRect(Brush.horizontalGradient((0..6).map { Color(hsv(it * 60f, sat, value)) }))
    }
}

/** A preset colour in the colour sheet. */
private const val SWATCH_COLUMNS = 6

/** "#RRGGBB", and "#RRGGBBAA" where the colour carries its opacity. */
private const val HEX_LENGTH = 7
private const val HEX_WITH_ALPHA_LENGTH = 9

private val HUE_STRIP_HEIGHT = 32.dp
