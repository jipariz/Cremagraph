package app.onlyshots.edit

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.onlyshots.TestTags
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgIcons
import app.onlyshots.core.designsystem.CgMotion
import app.onlyshots.core.designsystem.CgMotion.cut
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgStroke
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.caps
import app.onlyshots.core.designsystem.component.FormatPicker
import app.onlyshots.core.designsystem.component.Pill
import app.onlyshots.core.designsystem.component.SegmentedControl
import app.onlyshots.core.designsystem.component.topRule
import app.onlyshots.core.model.VideoItem
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.edit_change
import app.onlyshots.core.resources.edit_done
import app.onlyshots.core.resources.edit_frame
import app.onlyshots.core.resources.edit_graph
import app.onlyshots.core.resources.edit_shot
import app.onlyshots.core.resources.edit_tap_graph_hint
import app.onlyshots.core.resources.edit_zoom_and_crop
import app.onlyshots.core.resources.text
import app.onlyshots.layout.LocalWindowLayout
import app.onlyshots.layout.WidePanes
import app.onlyshots.layout.belowPanelHeightDp
import app.onlyshots.layout.placePanel
import app.onlyshots.layout.sidePanelWidthDp
import app.onlyshots.machineLabel
import org.jetbrains.compose.resources.stringResource

/**
 * As the web editor's desk: the picture with its timeline, and everything else in a panel beside it (or under it, on an
 * upright window with a wide video). The panel follows the selection: the graph's settings while the graph is selected,
 * else the video's. The export is a card over the whole window.
 */
@Composable
internal fun EditWideLayout(
    ui: EditUiState,
    video: VideoItem,
    player: PlayerState,
    local: EditLocalState,
    chrome: EditChrome,
    actions: EditActions,
    onBack: () -> Unit,
    onPickShotFile: () -> Unit,
    onExported: () -> Unit,
    slots: EditSlots,
    modifier: Modifier = Modifier,
) {
    val layout = LocalWindowLayout.current
    Box(modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            // The panel beside the picture, or under it across the window where that leaves the video more room (a
            // wide or square video on an upright tablet); picking another format moves it there animated.
            val ratio = video.format.ratio
            WidePanes(
                layout,
                placement =
                    placePanel(maxWidth.value, maxHeight.value, ratio, WIDE_CHROME_DP, belowPanelDp = BELOW_PANEL_DP),
                belowHeight =
                    belowPanelHeightDp(maxWidth.value, maxHeight.value, ratio, WIDE_CHROME_DP, BELOW_PANEL_DP).dp,
                panelWidth = sidePanelWidthDp(maxWidth.value).dp,
                // The panel meets the picture at a hairline, as on the desk, rather than across a gap.
                gap = false,
                animateOn = video.format,
                main = { PictureColumn(ui, video, player, actions, onBack, slots) },
                panel = {
                    // Selecting the graph swaps the video's panel for the graph's: a cut, the selection already shows
                    // what changed.
                    AnimatedContent(
                        chrome.graphPanel,
                        modifier = Modifier.fillMaxSize().startRule(),
                        transitionSpec = { cut() },
                        label = "panel",
                    ) { graph ->
                        if (graph) {
                            GraphPanel(ui, video, actions, onDone = { local.graphSelected = false }, slots)
                        } else {
                            VideoPanel(
                                ui = ui,
                                video = video,
                                actions = actions,
                                reframing = local.reframing,
                                onReframing = local::reframe,
                                onPickShotFile = onPickShotFile,
                                footer = slots.exportBar,
                            )
                        }
                    }
                },
            )
        }
        // The export fades in over the editor, and out once its result is seen.
        EditExportOverlay(chrome.exportOverlay, ui, video, actions, onExported, fullScreen = false)
    }
}

/** What always takes height on a wide window besides the picture: the top bar and the timeline band. */
private const val WIDE_CHROME_DP = 236f

/** The least the panel under the picture gets on an upright window: its pages scroll, the export stays pinned. */
private const val BELOW_PANEL_DP = 360f

/**
 * The top bar over the picture and its timeline: the picture as large as the column's width allows, the timeline right
 * under it, and what height is left over above and below the two rather than between them.
 */
@Composable
private fun PictureColumn(
    ui: EditUiState,
    video: VideoItem,
    player: PlayerState,
    actions: EditActions,
    onBack: () -> Unit,
    slots: EditSlots,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        WideTopBar(video, player, onBack = onBack)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                slots.picture(Modifier.weight(1f, fill = false))
                slots.timeBand()
            }
            MessageBanner(ui.message, actions::clearMessage, Modifier.align(Alignment.TopCenter))
        }
    }
}

/**
 * The panel while the graph is selected: the graph's pages the phone shows as sheets (style, data, text), one at a
 * time, picked at its top, with the graph's opacity beside "done", which puts the graph down. The page stays where it
 * was while something else (an export) borrows the sheet.
 */
@Composable
private fun GraphPanel(
    ui: EditUiState,
    video: VideoItem,
    actions: EditActions,
    onDone: () -> Unit,
    slots: EditSlots,
    modifier: Modifier = Modifier,
) {
    val page = graphPage(ui.sheet, ui.graphPage)
    EditPanel(
        page,
        title = stringResource(Res.string.edit_graph),
        onPage = actions::openSheet,
        onDone = onDone,
        tools = {
            Pill(
                "${opacityPercent(video.overlay.opacity)}%",
                icon = CgIcons.Contrast,
                onClick = { actions.overlay { it.withOpacity(nextOpacity(it.opacity)) } },
            )
        },
        footer = slots.exportBar,
        modifier = modifier,
    ) {
        AnimatedContent(page, transitionSpec = { cut() }, label = "page") { shown ->
            Column(verticalArrangement = Arrangement.spacedBy(CgSpace.S12)) { slots.sheet(shown) }
        }
    }
}

/**
 * The graph's pages, one at a time, picked at its top, with the [tools] the phone's toolbar over the graph holds;
 * "done" puts the graph down.
 */
@Composable
private fun EditPanel(
    page: EditSheet,
    title: String,
    onPage: (EditSheet) -> Unit,
    onDone: () -> Unit,
    /** The graph's quick tools beside "done": how much of its background shows. */
    tools: @Composable () -> Unit,
    /** Pinned under the pages: the export. */
    footer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxSize().topRule()) {
        Row(
            Modifier.fillMaxWidth()
                .bottomRule()
                .padding(start = CgSpace.S16, end = CgSpace.S12, top = CgSpace.S12, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CgSpace.S6),
        ) {
            Text(title.caps(), style = CgTheme.type.sheetTitle, modifier = Modifier.weight(1f))
            tools()
            Pill(
                stringResource(Res.string.edit_done),
                onClick = onDone,
                light = true,
                modifier = Modifier.testTag(TestTags.SheetDone),
            )
        }
        SegmentedControl(
            GraphPages.map { stringResource(it.label) },
            GraphPages.indexOf(page),
            onSelect = { onPage(GraphPages[it]) },
            fill = true,
            modifier = Modifier.fillMaxWidth(),
            tags = listOf(TestTags.ToolbarStyle, TestTags.ToolbarData),
        )
        Column(
            Modifier.fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CgSpace.S16, vertical = CgSpace.S12),
            verticalArrangement = Arrangement.spacedBy(CgSpace.S12),
        ) {
            content()
        }
        PanelFooter(footer)
    }
}

/**
 * The panel while nothing is selected: the video's settings. Its shot (one line and "change", which opens the file and
 * the machine's shots in place), its frame (the format, and zooming and cropping the picture in it), the graph's style
 * (changed by tapping the graph), and the [footer] export pinned at the bottom.
 */
@Composable
private fun VideoPanel(
    ui: EditUiState,
    video: VideoItem,
    actions: EditActions,
    reframing: Boolean,
    onReframing: (Boolean) -> Unit,
    onPickShotFile: () -> Unit,
    footer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    var changingShot by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).animateContentSize(CgMotion.sizeSpec)) {
            val shot = video.shot
            // Picking another shot: the section is marked with the signal along its edge while it is open.
            PanelSection(marked = shot == null || changingShot) {
                PanelLabel(stringResource(Res.string.edit_shot))
                if (shot != null && !changingShot) {
                    ShotSummaryRow(
                        color = machineColor(shot.source),
                        title = "${shot.profileName} · ${machineLabel(shot.source)}",
                        subtitle = shotDetail(video)?.text().orEmpty(),
                    ) {
                        Pill(stringResource(Res.string.edit_change), onClick = { changingShot = true })
                    }
                    ShotStartPill(video, onUnlock = actions::unlockShotStart, onMatchByClock = actions::matchByClock)
                } else {
                    ShotSheetContent(
                        video,
                        ui.shots,
                        onPick = {
                            actions.pickShot(it)
                            changingShot = false
                        },
                        onFile = onPickShotFile,
                        onMatchByClock = actions::matchByClock,
                        onUnlock = actions::unlockShotStart,
                        onFromMachine = LocalFromMachine.current,
                    )
                    if (shot != null) Pill(stringResource(Res.string.edit_done), onClick = { changingShot = false })
                }
            }
            PanelSection {
                PanelLabel(stringResource(Res.string.edit_frame))
                FormatPicker(
                    FORMAT_OPTIONS,
                    formatIndex(video.format),
                    onPick = { actions.setFormat(formatAt(it)) },
                    compact = true,
                )
                FrameToggle(
                    reframing,
                    onReframing,
                    offLabel = Res.string.edit_zoom_and_crop,
                    fill = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (shot != null) {
                PanelSection(gap = CgSpace.S4) {
                    PanelLabel(stringResource(Res.string.edit_graph))
                    Text((ui.style?.name ?: video.overlay.styleId).caps(), style = CgTheme.type.label)
                    Text(
                        stringResource(Res.string.edit_tap_graph_hint),
                        style = CgTheme.type.caption,
                        color = CgColors.Text.Secondary,
                    )
                }
            }
        }
        PanelFooter(footer)
    }
}

/**
 * One part of the desk panel (the shot, the frame, the graph): padded, over a hairline. [marked] draws the signal along
 * its leading edge, for the part that is open for a change.
 */
@Composable
private fun PanelSection(
    modifier: Modifier = Modifier,
    marked: Boolean = false,
    gap: Dp = 10.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .bottomRule()
            .then(if (marked) Modifier.leadingSignal() else Modifier)
            .padding(CgSpace.S16),
        verticalArrangement = Arrangement.spacedBy(gap),
        content = content,
    )
}

/** The export pinned at the bottom of the desk panel, under a hairline. */
@Composable
private fun PanelFooter(footer: @Composable () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().topRule().padding(horizontal = CgSpace.S16, vertical = CgSpace.S12)) { footer() }
}

@Composable
private fun PanelLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.caps(), style = CgTheme.type.labelStrong, color = CgColors.Text.Secondary, modifier = modifier)
}

/** A hairline along the leading edge: where the desk panel meets the picture. */
private fun Modifier.startRule(): Modifier = drawBehind {
    drawRect(CgColors.Line.Hairline, Offset.Zero, Size(CgStroke.Hairline.toPx(), size.height))
}

/** The signal along the leading edge: the desk panel's part that is open for a change. */
private fun Modifier.leadingSignal(): Modifier = drawBehind {
    drawRect(CgColors.Brand.Signal, Offset.Zero, Size(CgStroke.Rule.toPx(), size.height))
}
