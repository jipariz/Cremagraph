package app.onlyshots.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import app.onlyshots.TestTags
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgIcons
import app.onlyshots.core.designsystem.CgSize
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgStroke
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.caps
import app.onlyshots.core.designsystem.component.BluetoothBlue
import app.onlyshots.core.designsystem.component.ChoiceCard
import app.onlyshots.core.designsystem.component.IconCircleButton
import app.onlyshots.core.designsystem.component.OnlyShotsMark
import app.onlyshots.core.designsystem.headline
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.app_name
import app.onlyshots.core.resources.home_about
import app.onlyshots.core.resources.home_start_edit_detail
import app.onlyshots.core.resources.home_start_edit_title
import app.onlyshots.core.resources.home_start_graph_series
import app.onlyshots.core.resources.home_start_question
import app.onlyshots.core.resources.home_start_record_detail
import app.onlyshots.core.resources.home_start_record_title
import app.onlyshots.core.resources.start_title
import org.jetbrains.compose.resources.stringResource

/*
 * The pieces every Start layout is made of: the header, the graph card, the headline and the two choices.
 */

/** The mark (where the splash lands) and the name; the help key at the end when [onHelp] is given. */
@Composable
internal fun StartHeader(onHelp: (() -> Unit)?, big: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().height(if (big) CgSize.Touch else PHONE_HEADER_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(if (big) CgSpace.S12 else PHONE_HEADER_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OnlyShotsMark(splashTarget = true)
        Text(
            stringResource(Res.string.app_name).caps(),
            style =
                (if (big) CgTheme.type.screenTitle else CgTheme.type.label).copy(
                    letterSpacing = CgTheme.type.labelStrong.letterSpacing
                ),
            modifier = Modifier.weight(1f),
        )
        if (onHelp != null) HelpKey(onHelp)
    }
}

@Composable
internal fun HelpKey(onHelp: () -> Unit, modifier: Modifier = Modifier) {
    IconCircleButton(
        CgIcons.Help,
        stringResource(Res.string.home_about),
        onClick = onHelp,
        modifier = modifier.testTag(TestTags.StartHelp),
    )
}

/**
 * "FILM THE SHOT. KEEP THE DATA.": the headline, one sentence a line as the link banner sets it; the large Mono one in
 * a wide window.
 */
@Composable
internal fun Headline(big: Boolean, modifier: Modifier = Modifier) {
    Text(
        stringResource(Res.string.start_title).headline(),
        modifier = modifier,
        style =
            if (big) {
                CgTheme.type.valueLarge.copy(
                    letterSpacing = CgTheme.type.pivot.letterSpacing,
                    lineHeight = CgTheme.type.valueLarge.fontSize * 1.1f,
                )
            } else {
                CgTheme.type.pivot
            },
    )
}

/** The question and the two ways in, as one grid of cells meeting on a hairline: stacked, or [sideBySide]. */
@Composable
internal fun Choices(onRecord: () -> Unit, onEdit: () -> Unit, sideBySide: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(CgSpace.S12)) {
        Text(
            stringResource(Res.string.home_start_question).caps(),
            style = CgTheme.type.labelStrong,
            color = CgColors.Text.Secondary,
        )
        val record: @Composable (Modifier) -> Unit = { m ->
            ChoiceCard(
                icon = CgIcons.Record,
                tileColor = CgColors.Status.Record,
                title = stringResource(Res.string.home_start_record_title),
                detail = stringResource(Res.string.home_start_record_detail),
                onClick = onRecord,
                signal = true,
                modifier = m.testTag(TestTags.StartRecord),
            )
        }
        val compose: @Composable (Modifier) -> Unit = { m ->
            ChoiceCard(
                icon = CgIcons.Film,
                tileColor = CgColors.Surface.Raised,
                title = stringResource(Res.string.home_start_edit_title),
                detail = stringResource(Res.string.home_start_edit_detail),
                onClick = onEdit,
                modifier = m.testTag(TestTags.StartCompose),
            )
        }
        if (sideBySide) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                record(Modifier.weight(1f).fillMaxHeight())
                compose(Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column {
                record(Modifier)
                compose(Modifier)
            }
        }
    }
}

/**
 * What OnlyShots makes, drawn small: a shot's graph in a bar-coloured cell, which series it plots and how long it ran
 * over a grid, pressure in the signal, flow in bone, weight in blue. Decoration only.
 */
@Composable
internal fun GraphCard(modifier: Modifier, big: Boolean) {
    Column(
        modifier
            .fillMaxWidth()
            .background(CgColors.Surface.Bar)
            .border(CgStroke.Hairline, CgColors.Line.Hairline)
            .padding(if (big) CgSpace.S16 else CgSpace.S12),
        verticalArrangement = Arrangement.spacedBy(if (big) CgSpace.S12 else CgSpace.S8),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                stringResource(Res.string.home_start_graph_series),
                style = CgTheme.type.data,
                color = CgColors.Text.Secondary,
            )
            Text("0:28", style = CgTheme.type.data, color = CgColors.Text.Secondary)
        }
        val trace = if (big) 2.dp else 1.5.dp
        // Built once per size, not on every draw.
        Spacer(
            Modifier.fillMaxWidth()
                .weight(1f)
                .clearAndSetSemantics {}
                .drawWithCache {
                    // The card draws it in a 300 × 120 box, stretched to fit.
                    val sx = size.width / 300f
                    val sy = size.height / 120f
                    val hair = CgStroke.Hairline.toPx()
                    val stroke = Stroke(trace.toPx())
                    val signal =
                        Path().apply {
                            moveTo(0f, 118f * sy)
                            cubicTo(20f * sx, 118f * sy, 30f * sx, 30f * sy, 50f * sx, 24f * sy)
                            lineTo(180f * sx, 24f * sy)
                            cubicTo(220f * sx, 26f * sy, 260f * sx, 40f * sy, 300f * sx, 48f * sy)
                        }
                    val primary =
                        Path().apply {
                            moveTo(0f, 118f * sy)
                            cubicTo(40f * sx, 116f * sy, 60f * sx, 90f * sy, 90f * sx, 72f * sy)
                            cubicTo(140f * sx, 60f * sy, 200f * sx, 56f * sy, 300f * sx, 54f * sy)
                        }
                    val blue =
                        Path().apply {
                            moveTo(0f, 118f * sy)
                            lineTo(40f * sx, 118f * sy)
                            cubicTo(120f * sx, 110f * sy, 200f * sx, 60f * sy, 300f * sx, 14f * sy)
                        }
                    onDrawBehind {
                        for (y in GRID_ROWS) {
                            drawLine(CgColors.Line.Divider, Offset(0f, y * sy), Offset(size.width, y * sy), hair)
                        }
                        for (x in GRID_COLUMNS) {
                            drawLine(CgColors.Line.Divider, Offset(x * sx, 0f), Offset(x * sx, size.height), hair)
                        }
                        drawPath(signal, CgColors.Brand.Signal, style = stroke)
                        drawPath(primary, CgColors.Text.Primary, style = stroke)
                        drawPath(blue, BluetoothBlue, style = stroke)
                    }
                }
        )
    }
}

/** The card's grid, in its 300 × 120 box. */
private val GRID_ROWS = floatArrayOf(24f, 60f, 96f)
private val GRID_COLUMNS = floatArrayOf(75f, 150f, 225f)

/** The phone's header: as tall as the canvas draws it, the mark and the name a little closer. */
private val PHONE_HEADER_HEIGHT = 60.dp
private val PHONE_HEADER_GAP = 10.dp

/** The instrument's faint ground: a 24 dp grid of bone lines at 4 %. */
internal fun DrawScope.drawGrid() {
    val step = GRID_STEP.toPx()
    val line = CgColors.Text.Primary.copy(alpha = 0.04f)
    var x = 0f
    while (x < size.width) {
        drawLine(line, Offset(x, 0f), Offset(x, size.height), CgStroke.Hairline.toPx())
        x += step
    }
    var y = 0f
    while (y < size.height) {
        drawLine(line, Offset(0f, y), Offset(size.width, y), CgStroke.Hairline.toPx())
        y += step
    }
}

private val GRID_STEP = 24.dp
