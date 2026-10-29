package app.onlyshots.open

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgStroke
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.caps
import app.onlyshots.core.designsystem.component.BackButton
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.open_header_edit
import app.onlyshots.core.resources.text
import app.onlyshots.layout.FoldPanes
import app.onlyshots.layout.WindowLayout
import org.jetbrains.compose.resources.stringResource

/**
 * The phone app's Open on a tablet or an open foldable (Open-Phone/Open-Ready -M, -B, -F):
 * - on a fold: the header and steps on the left page, the picked video and "open editor" on the right;
 * - landscape: the steps on the left, the picked video in 9:16 and "open editor" in a 300 dp column on the right;
 * - portrait: the steps beside the picked video, "open editor" across the bottom.
 */
@Composable
internal fun OpenWideLayout(
    ui: OpenUi,
    actions: OpenActions,
    onBack: () -> Unit,
    layout: WindowLayout,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(CgColors.Surface.Base).safeDrawingPadding()) {
        when {
            layout.fold -> FoldOpen(ui, actions, onBack, layout)
            layout.landscape -> TabletOpen(ui, actions, onBack, Landscape)
            else -> TabletOpen(ui, actions, onBack, Portrait)
        }
    }
}

/** A fold's two pages: the header and the steps on the left, the picked video and "open editor" on the right. */
@Composable
private fun FoldOpen(ui: OpenUi, actions: OpenActions, onBack: () -> Unit, layout: WindowLayout) {
    FoldPanes(
        layout,
        first = {
            Column(
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = FoldPageGutter, vertical = CgSpace.S24),
                verticalArrangement = Arrangement.spacedBy(CgSpace.S16),
            ) {
                ComposeHeader(onBack, big = false)
                StepsAndProblem(ui, actions)
            }
        },
        second = {
            PreviewAndKey(
                ui,
                actions,
                maxHeight = FoldPreviewMaxHeight,
                modifier =
                    Modifier.fillMaxSize()
                        .drawBehind {
                            drawRect(CgColors.Line.Divider, size = Size(CgStroke.Hairline.toPx(), size.height))
                        }
                        .padding(start = CgSpace.S24, end = CgSpace.S24, top = FoldPreviewTop, bottom = CgSpace.S24),
                spacing = CgSpace.S12,
            )
        },
    )
}

/**
 * How a tablet held one way or the other lays the same parts out: its margins, the gap between the steps and the
 * picture, the picture's size, and whether "open editor" goes under the picture ([keyBeside]) or across the bottom.
 */
private class TabletSpec(
    val padding: PaddingValues,
    val gap: Dp,
    val largeSteps: Boolean,
    val previewWidth: Dp,
    val previewMaxHeight: Dp,
    val keyBeside: Boolean,
)

private val Landscape =
    TabletSpec(
        padding = PaddingValues(horizontal = 64.dp, vertical = 40.dp),
        gap = 64.dp,
        largeSteps = true,
        previewWidth = 300.dp,
        previewMaxHeight = 448.dp,
        keyBeside = true,
    )

private val Portrait =
    TabletSpec(
        padding = PaddingValues(start = 48.dp, end = 48.dp, top = CgSpace.S24, bottom = 40.dp),
        gap = 32.dp,
        largeSteps = false,
        previewWidth = 270.dp,
        previewMaxHeight = 480.dp,
        keyBeside = false,
    )

/** A tablet: the header over the steps beside the picked video, laid out by [spec]. */
@Composable
private fun TabletOpen(ui: OpenUi, actions: OpenActions, onBack: () -> Unit, spec: TabletSpec) {
    Column(
        Modifier.fillMaxSize().padding(spec.padding),
        verticalArrangement = Arrangement.spacedBy(TabletRowGap),
    ) {
        ComposeHeader(onBack, big = true)
        Row(
            Modifier.weight(1f, fill = spec.keyBeside).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spec.gap),
            verticalAlignment = Alignment.Top,
        ) {
            StepsAndProblem(
                ui,
                actions,
                large = spec.largeSteps,
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            )
            if (spec.keyBeside) {
                PreviewAndKey(
                    ui,
                    actions,
                    maxHeight = spec.previewMaxHeight,
                    modifier = Modifier.width(spec.previewWidth).fillMaxHeight(),
                )
            } else {
                PickedPreview(ui, spec.previewMaxHeight, Modifier.width(spec.previewWidth))
            }
        }
        if (!spec.keyBeside) {
            Spacer(Modifier.weight(1f))
            OpenEditorKey(ready = ui.ready, onClick = actions::openEditor, fill = true)
        }
    }
}

/** The steps and, under them, why the last pick did not take. */
@Composable
private fun StepsAndProblem(
    ui: OpenUi,
    actions: OpenActions,
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(CgSpace.S16)) {
        AppSteps(ui, StepWords.PhoneApp, actions, large = large)
        ui.problem?.let { StatusLine(it.text()) }
    }
}

/** The picked video at the top, "open editor" at the bottom of the column. */
@Composable
private fun PreviewAndKey(
    ui: OpenUi,
    actions: OpenActions,
    maxHeight: Dp,
    modifier: Modifier = Modifier,
    spacing: Dp = CgSpace.S16,
) {
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(spacing),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PickedPreview(ui, maxHeight, Modifier.weight(1f, fill = false))
        Spacer(Modifier.weight(1f))
        OpenEditorKey(ready = ui.ready, onClick = actions::openEditor, fill = true)
    }
}

/** The page's back key and "compose a video" over a hairline; Mono 28 on a tablet, 20 on a fold's page. */
@Composable
private fun ComposeHeader(onBack: () -> Unit, big: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(
                    CgColors.Line.Hairline,
                    Offset(0f, size.height - CgStroke.Hairline.toPx()),
                    Size(size.width, CgStroke.Hairline.toPx()),
                )
            }
            .padding(bottom = if (big) CgSpace.S16 else CgSpace.S12),
        horizontalArrangement = Arrangement.spacedBy(if (big) CgSpace.S16 else CgSpace.S12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackButton(onBack)
        Text(
            stringResource(Res.string.open_header_edit).caps(),
            style =
                if (big) CgTheme.type.pivot.copy(letterSpacing = 0.04.em, lineHeight = TextUnit.Unspecified)
                else CgTheme.type.screenTitle,
            modifier = Modifier.semantics { heading() },
        )
    }
}

/**
 * The picked video in 9:16, at most [maxHeight] tall and as wide as it is let, with its length on it and its name and
 * size under it; before one is picked, a dashed frame where it will be.
 */
@Composable
private fun PickedPreview(ui: OpenUi, maxHeight: Dp, modifier: Modifier = Modifier) {
    val picked = ui.picked
    Column(modifier, verticalArrangement = Arrangement.spacedBy(CgSpace.S12)) {
        Box(
            Modifier.weight(1f, fill = false)
                .heightIn(max = maxHeight)
                .aspectRatio(9f / 16f, matchHeightConstraintsFirst = true)
                .then(
                    if (picked == null) Modifier.drawBehind { dashedBorder(CgColors.Line.Hairline) }
                    else Modifier.border(CgStroke.Hairline, CgColors.Line.Hairline)
                )
        ) {
            if (picked != null) {
                VideoTile(ui.pickedFile?.thumb, Modifier.fillMaxSize())
                LengthTag(picked, Modifier.align(Alignment.BottomStart))
            }
        }
        if (picked != null) VideoFacts(ui.pickedFile)
    }
}

/** A fold's page keeps a narrower margin than a tablet's. */
private val FoldPageGutter = 20.dp

/**
 * On a fold, the picked video's top sits level with the steps on the other page, below their header: the header's
 * height plus the page's top margin, measured on the canvas.
 */
private val FoldPreviewTop = 105.dp

private val FoldPreviewMaxHeight = 384.dp

/** Between a tablet's header and what is under it. */
private val TabletRowGap = 32.dp
