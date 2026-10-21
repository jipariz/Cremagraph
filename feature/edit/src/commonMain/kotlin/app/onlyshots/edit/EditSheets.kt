package app.onlyshots.edit

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import app.onlyshots.TestTags
import app.onlyshots.connect.color
import app.onlyshots.connect.label
import app.onlyshots.connect.labelRes
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgIcons
import app.onlyshots.core.designsystem.CgMotion.tick
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgStroke
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.caps
import app.onlyshots.core.designsystem.component.CgButton
import app.onlyshots.core.designsystem.component.CgButtonKind
import app.onlyshots.core.designsystem.component.CgTextField
import app.onlyshots.core.designsystem.component.CgToggle
import app.onlyshots.core.designsystem.component.HairlineGrid
import app.onlyshots.core.designsystem.component.ListRow
import app.onlyshots.core.designsystem.component.Pill
import app.onlyshots.core.designsystem.component.ProgressLine
import app.onlyshots.core.designsystem.component.SegmentedControl
import app.onlyshots.core.designsystem.component.SeriesChip
import app.onlyshots.core.designsystem.component.SettingRow
import app.onlyshots.core.designsystem.component.SettingsList
import app.onlyshots.core.designsystem.component.ShotRow
import app.onlyshots.core.model.Layout
import app.onlyshots.core.model.OverlaySettings
import app.onlyshots.core.model.Quality
import app.onlyshots.core.model.Series
import app.onlyshots.core.model.Shot
import app.onlyshots.core.model.ShotSummary
import app.onlyshots.core.model.VideoItem
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.edit_cancel
import app.onlyshots.core.resources.edit_data_series
import app.onlyshots.core.resources.edit_data_show
import app.onlyshots.core.resources.edit_file
import app.onlyshots.core.resources.edit_from_machine
import app.onlyshots.core.resources.edit_from_machine_detail
import app.onlyshots.core.resources.edit_graph
import app.onlyshots.core.resources.edit_graph_start
import app.onlyshots.core.resources.edit_keep_open
import app.onlyshots.core.resources.edit_legend
import app.onlyshots.core.resources.edit_machine
import app.onlyshots.core.resources.edit_match_by_clock
import app.onlyshots.core.resources.edit_no_sound
import app.onlyshots.core.resources.edit_not_in_shot
import app.onlyshots.core.resources.edit_number_phases
import app.onlyshots.core.resources.edit_number_time
import app.onlyshots.core.resources.edit_number_weight
import app.onlyshots.core.resources.edit_on_connected_machine
import app.onlyshots.core.resources.edit_phase_lines
import app.onlyshots.core.resources.edit_readings
import app.onlyshots.core.resources.edit_recipe
import app.onlyshots.core.resources.edit_recipe_placeholder
import app.onlyshots.core.resources.edit_shot_file
import app.onlyshots.core.resources.edit_shot_file_detail
import app.onlyshots.core.resources.edit_sound_muted
import app.onlyshots.core.resources.edit_status_by_clock
import app.onlyshots.core.resources.edit_status_lined_up
import app.onlyshots.core.resources.edit_status_not_lined_up
import app.onlyshots.core.resources.edit_status_recorded
import app.onlyshots.core.resources.edit_time_left
import app.onlyshots.core.resources.edit_title
import app.onlyshots.core.resources.edit_use_another_shot
import app.onlyshots.core.resources.edit_with_sound
import app.onlyshots.core.resources.edit_working_out
import app.onlyshots.core.resources.unit_grams
import app.onlyshots.export.ExportManager
import app.onlyshots.label
import app.onlyshots.machineLabel
import app.onlyshots.overlayTitleText
import kotlin.math.roundToInt
import kotlin.time.TimeSource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Which series [shot] actually carries; the rest are shown greyed, since there is nothing to draw for them. */
fun seriesIn(shot: Shot?): Set<Series> {
    if (shot == null) return emptySet()
    val s = shot.samples
    return buildSet {
        if (s.any { it.pressure != null }) add(Series.PRESSURE)
        if (s.any { it.flow != null }) add(Series.FLOW)
        if (s.any { it.weight != null }) add(Series.WEIGHT)
        if (s.any { it.weightFlow != null }) add(Series.WEIGHT_FLOW)
        if (s.any { it.temperature != null }) add(Series.TEMPERATURE)
        if (s.any { it.targetPressure != null || it.targetFlow != null || it.targetTemperature != null }) {
            add(Series.TARGETS)
        }
    }
}

/**
 * The data sheet: SERIES, one chip per series in a grid of three meeting on hairlines; then SHOW, whether the graph,
 * the readings, the legend and the phase lines show, in one framed list. The phase lines are offered only where the
 * shot has phases ([hasPhases]).
 */
@Composable
fun DataSheetContent(
    settings: OverlaySettings,
    available: Set<Series>,
    hasPhases: Boolean = true,
    onChange: ((OverlaySettings) -> OverlaySettings) -> Unit,
) {
    SheetSection(stringResource(Res.string.edit_data_series))
    val all = Series.entries
    HairlineGrid(all.size, columns = 3, color = CgColors.Line.Hairline) { i, cell ->
        val series = all[i]
        SeriesChip(
            label = series.label,
            color = series.color,
            selected = series in settings.series,
            enabled = series in available,
            dashed = series == Series.TARGETS,
            framed = false,
            modifier = cell.fillMaxHeight().testTag(TestTags.series(series.name)),
            onClick = {
                onChange { s -> s.copy(series = if (series in s.series) s.series - series else s.series + series) }
            },
        )
    }
    val missing = Series.entries.filter { it !in available }
    if (missing.isNotEmpty() && available.isNotEmpty()) {
        Text(
            stringResource(Res.string.edit_not_in_shot, missing.map { stringResource(it.labelRes) }.joinToString(", ")),
            style = CgTheme.type.caption,
            color = CgColors.Text.Dim,
        )
    }
    val graph = settings.layout != Layout.READINGS_ONLY
    val readings = settings.layout != Layout.GRAPH_ONLY
    fun layoutOf(g: Boolean, r: Boolean) =
        when {
            g && r -> Layout.GRAPH_AND_READINGS
            g -> Layout.GRAPH_ONLY
            else -> Layout.READINGS_ONLY
        }
    SheetSection(stringResource(Res.string.edit_data_show), Modifier.padding(top = CgSpace.S8))
    val graphTitle = stringResource(Res.string.edit_graph)
    val readingsTitle = stringResource(Res.string.edit_readings)
    val legendTitle = stringResource(Res.string.edit_legend)
    val phasesTitle = stringResource(Res.string.edit_phase_lines)
    SettingsList(
        listOfNotNull(
            // The last of the two that is on cannot be turned off: a graph with neither would draw nothing.
            {
                SettingRow(graphTitle) {
                    CgToggle(
                        graph,
                        enabled = !graph || readings,
                        onCheckedChange = { onChange { s -> s.copy(layout = layoutOf(it, readings)) } },
                    )
                }
            },
            {
                SettingRow(readingsTitle) {
                    CgToggle(
                        readings,
                        enabled = !readings || graph,
                        onCheckedChange = { onChange { s -> s.copy(layout = layoutOf(graph, it)) } },
                    )
                }
            },
            // The legend sits in the graph's header: without the graph there is none to hide.
            {
                SettingRow(legendTitle) {
                    CgToggle(
                        settings.legend && graph,
                        enabled = graph,
                        onCheckedChange = { onChange { s -> s.copy(legend = it) } },
                    )
                }
            },
            if (!hasPhases) null
            else {
                {
                    SettingRow(phasesTitle) {
                        CgToggle(
                            settings.phases && graph,
                            enabled = graph,
                            onCheckedChange = { onChange { s -> s.copy(phases = it) } },
                        )
                    }
                }
            },
        )
    )
}

/** A sheet's section name: Mono capitals in the secondary grey, as SERIES and SHOW. */
@Composable
private fun SheetSection(text: String, modifier: Modifier = Modifier) {
    Text(text.caps(), style = CgTheme.type.labelStrong, color = CgColors.Text.Secondary, modifier = modifier)
}

/**
 * Where the editor's host sends "from the machine": the phones open their machine shot picker; null where machines
 * cannot be reached (desktop, web). Provided by [EditScreen].
 */
val LocalFromMachine = staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * The editor's shot sheet. With a shot attached, the shot leads: a card with its name, numbers and how it is lined up,
 * and the ways to use another one as keys on one line under it. Without one, those ways are the sheet.
 */
@Composable
fun ShotSheetContent(
    item: VideoItem,
    shots: List<ShotSummary>,
    onPick: (ShotSummary) -> Unit,
    onFile: () -> Unit,
    onMatchByClock: () -> Unit = {},
    onUnlock: () -> Unit = {},
    /** The host can reach machines on the network (the phones): a recent shot from one, picked on its own screen. */
    onFromMachine: (() -> Unit)? = null,
) {
    val shot = item.shot
    if (shot != null) {
        ShotCard(item, shot, onMatchByClock = onMatchByClock, onUnlock = onUnlock)
        // The other ways in, on one line: the words, then a key each.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CgSpace.S8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.edit_use_another_shot).caps(),
                style = CgTheme.type.labelStrong,
                color = CgColors.Text.Secondary,
                modifier = Modifier.weight(1f),
            )
            Pill(stringResource(Res.string.edit_file), onClick = onFile)
            if (onFromMachine != null) Pill(stringResource(Res.string.edit_machine), onClick = onFromMachine)
        }
    } else {
        ListRow(
            stringResource(Res.string.edit_shot_file),
            stringResource(Res.string.edit_shot_file_detail),
            onClick = onFile,
        )
        if (onFromMachine != null) {
            ListRow(
                stringResource(Res.string.edit_from_machine),
                stringResource(Res.string.edit_from_machine_detail),
                onClick = onFromMachine,
            )
        }
    }
    if (shots.isNotEmpty()) {
        Text(
            stringResource(Res.string.edit_on_connected_machine).caps(),
            style = CgTheme.type.labelStrong,
            color = CgColors.Text.Secondary,
        )
        for (summary in shots) {
            ShotRow(
                title = summary.profileName,
                subtitle = clock(summary.durationUs),
                machineColor = machineColor(summary.source),
                selected = item.shot?.id == summary.id,
                onClick = { onPick(summary) },
            )
        }
    }
}

/**
 * The attached shot, as a framed cell: its name and machine beside a bar in the machine's colour, its time, final
 * weight and phases as a row of cells, how it is lined up with the video, and what can be done about that.
 */
@Composable
private fun ShotCard(item: VideoItem, shot: Shot, onMatchByClock: () -> Unit, onUnlock: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(CgSpace.S12)) {
        Column(Modifier.fillMaxWidth().border(CgStroke.Hairline, CgColors.Line.Hairline)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = CgSpace.S16, vertical = CgSpace.S12),
                horizontalArrangement = Arrangement.spacedBy(CgSpace.S12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(4.dp).height(36.dp).background(machineColor(shot.source)))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(shot.profileName.caps(), style = CgTheme.type.cardTitle)
                    Text(
                        machineLabel(shot.source).caps(),
                        style = CgTheme.type.data,
                        color = CgColors.Text.Secondary,
                    )
                }
            }
            Rule()
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                ShotNumber(stringResource(Res.string.edit_number_time), clock(shot.durationUs))
                shot.finalWeight?.let {
                    VerticalRule()
                    ShotNumber(
                        stringResource(Res.string.edit_number_weight),
                        "${(it * 10).roundToInt() / 10f} ${stringResource(Res.string.unit_grams)}",
                    )
                }
                shot.phaseStartsUs.size
                    .takeIf { it > 0 }
                    ?.let {
                        VerticalRule()
                        ShotNumber(stringResource(Res.string.edit_number_phases), "$it")
                    }
            }
            Rule()
            val inStep = item.syncLocked || item.shotStartInVideoUs != null
            val tone = if (inStep) CgColors.Status.Success else CgColors.Status.Warning
            Row(
                Modifier.fillMaxWidth().padding(horizontal = CgSpace.S16, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(CgSpace.S8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(6.dp).background(tone))
                Text(stringResource(shotStatus(item)), style = CgTheme.type.bodySmall, color = tone)
            }
        }
        // The machine or scale may have started the take a little early or late: the start can still be moved.
        // The camera's clock and the machine's agree on where the shot starts: one tap lines it up.
        when {
            item.syncLocked ->
                Pill(stringResource(Res.string.edit_graph_start), onClick = onUnlock, icon = CgIcons.Lock)
            item.shotStartByClock() != null ->
                Pill(stringResource(Res.string.edit_match_by_clock), onClick = onMatchByClock)
        }
    }
}

/** A hairline across a framed cell. */
@Composable
private fun Rule() {
    Box(Modifier.fillMaxWidth().height(CgStroke.Hairline).background(CgColors.Line.Hairline))
}

/** A hairline between two cells of a row. */
@Composable
private fun VerticalRule() {
    Box(Modifier.width(CgStroke.Hairline).fillMaxHeight().background(CgColors.Line.Hairline))
}

@Composable
private fun RowScope.ShotNumber(label: String, value: String) {
    Column(
        Modifier.weight(1f).padding(horizontal = CgSpace.S16, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label.caps(), style = CgTheme.type.caption, color = CgColors.Text.Secondary)
        Text(value.caps(), style = CgTheme.type.valueReading)
    }
}

/** How the shot is lined up with the video, as the shot sheet and the wide panel say it. */
internal fun shotStatus(video: VideoItem): StringResource =
    when {
        video.syncLocked -> Res.string.edit_status_recorded
        video.shotStartByClock() != null && video.shotStartInVideoUs == video.shotStartByClock() ->
            Res.string.edit_status_by_clock
        video.shotStartInVideoUs != null -> Res.string.edit_status_lined_up
        else -> Res.string.edit_status_not_lined_up
    }

/**
 * The graph's words: its title (empty for "profile · machine", shown as the placeholder) and a recipe line under it.
 * Typing is one undo step per pause rather than one per letter.
 */
@Composable
fun GraphTextFields(item: VideoItem, onChange: (push: Boolean, (OverlaySettings) -> OverlaySettings) -> Unit) {
    var lastTyped by remember { mutableStateOf<TimeSource.Monotonic.ValueTimeMark?>(null) }
    fun push(): Boolean {
        val push = lastTyped.let { it == null || it.elapsedNow().inWholeMilliseconds > TYPING_STEP_MS }
        lastTyped = TimeSource.Monotonic.markNow()
        return push
    }
    val title = stringResource(Res.string.edit_title)
    val recipe = stringResource(Res.string.edit_recipe)
    val default = item.shot?.let { overlayTitleText(it, item.overlay.copy(title = null)) } ?: title
    Text(title.caps(), style = CgTheme.type.labelStrong, color = CgColors.Text.Secondary)
    CgTextField(
        value = item.overlay.title.orEmpty(),
        onValueChange = { text -> onChange(push()) { it.copy(title = text.ifEmpty { null }) } },
        label = title,
        placeholder = default,
        modifier = Modifier.fillMaxWidth().testTag(TestTags.TextTitleField),
    )
    Text(recipe.caps(), style = CgTheme.type.labelStrong, color = CgColors.Text.Secondary)
    CgTextField(
        value = item.overlay.recipe.orEmpty(),
        onValueChange = { text -> onChange(push()) { it.copy(recipe = text.ifEmpty { null }) } },
        label = recipe,
        placeholder = stringResource(Res.string.edit_recipe_placeholder),
        modifier = Modifier.fillMaxWidth(),
    )
}

private const val TYPING_STEP_MS = 1_000

/** The web editor's export sheet: the options, then the render's progress. */
@Composable
fun ExportSheetContent(
    item: VideoItem,
    /** What each way out makes, out of [qualities]; a choice between them shows when there are two. */
    galleryQuality: Quality,
    shareQuality: Quality,
    qualities: List<Quality>,
    /** Whether the export has sound: the video has it and the editor did not mute it. */
    sound: Boolean,
    exporting: Float?,
    leftMs: Long?,
    savedToGallery: Boolean,
    onShare: () -> Unit,
    onSaveToGallery: () -> Unit,
    onNextShot: () -> Unit,
    onCancel: () -> Unit,
    onQuality: (ExportManager.Destination, Quality) -> Unit = { _, _ -> },
) {
    val wording = LocalExportWording.current
    val choose = qualities.size > 1
    // Options → progress → saved: each step ticks over to the next, and the sheet moves to its height.
    val step =
        when {
            savedToGallery -> ExportStep.SAVED
            exporting != null -> ExportStep.MAKING
            else -> ExportStep.OPTIONS
        }
    AnimatedContent(
        step,
        transitionSpec = { tick() },
        label = "export",
    ) { shown ->
        Column(verticalArrangement = Arrangement.spacedBy(CgSpace.S12)) {
            when (shown) {
                ExportStep.SAVED -> {
                    Text(
                        "${stringResource(wording.saved)} · ${galleryQuality.label} · ${clock(item.trimmedDurationUs)}",
                        style = CgTheme.type.bodySmall,
                        color = CgColors.Text.Secondary,
                    )
                    CgButton(
                        stringResource(wording.next),
                        onClick = onNextShot,
                        kind = CgButtonKind.Signal,
                        modifier = Modifier.fillMaxWidth().testTag(TestTags.ExportNext),
                    )
                }
                ExportStep.MAKING -> {
                    val done = exporting ?: 1f
                    ProgressLine(done)
                    Row(Modifier.fillMaxWidth()) {
                        Text("${(done * 100).toInt()}%", style = CgTheme.type.label, modifier = Modifier.weight(1f))
                        Text(
                            leftMs?.let { stringResource(Res.string.edit_time_left, clock(it * 1000)) }
                                ?: stringResource(Res.string.edit_working_out),
                            style = CgTheme.type.caption,
                            color = CgColors.Text.Dim,
                        )
                    }
                    Text(
                        stringResource(Res.string.edit_keep_open),
                        style = CgTheme.type.caption,
                        color = CgColors.Text.Dim,
                    )
                    CgButton(
                        stringResource(Res.string.edit_cancel),
                        onClick = onCancel,
                        kind = CgButtonKind.Secondary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                ExportStep.OPTIONS -> {
                    // With a choice beside each row, the quality is said there, not again in the line.
                    ExportChoice(
                        stringResource(wording.save),
                        stringResource(wording.saveWhere),
                        exportSummary(item, galleryQuality.takeUnless { choose }, sound, wording),
                        onClick = onSaveToGallery,
                        signal = true,
                        modifier = Modifier.testTag(TestTags.ExportSave),
                        trailing =
                            if (choose) {
                                {
                                    QualityChoice(qualities, galleryQuality) {
                                        onQuality(ExportManager.Destination.GALLERY, it)
                                    }
                                }
                            } else null,
                    )
                    wording.share?.let { share ->
                        ExportChoice(
                            stringResource(share),
                            wording.shareWhere?.let { stringResource(it) },
                            exportSummary(item, shareQuality.takeUnless { choose }, sound, wording),
                            onClick = onShare,
                            modifier = Modifier.testTag(TestTags.ExportShare),
                            trailing =
                                if (choose) {
                                    {
                                        QualityChoice(qualities, shareQuality) {
                                            onQuality(ExportManager.Destination.SHARE, it)
                                        }
                                    }
                                } else null,
                        )
                    }
                    Text(stringResource(wording.footer), style = CgTheme.type.caption, color = CgColors.Text.Dim)
                }
            }
        }
    }
}

/**
 * One way out of the export sheet, as a framed block: its name and where it goes on a key across the top (the signal
 * for the main one), and what it will make with the [trailing] quality choice under it.
 */
@Composable
private fun ExportChoice(
    title: String,
    where: String?,
    summary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    signal: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val edge = if (signal) CgColors.Brand.Signal else CgColors.Line.Hairline
    val ink = if (signal) CgColors.Text.OnLight else CgColors.Text.Primary
    Column(Modifier.fillMaxWidth().border(CgStroke.Hairline, edge)) {
        Column(
            modifier
                .fillMaxWidth()
                .background(if (signal) CgColors.Brand.Signal else CgColors.Surface.Bar)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(CgSpace.S16),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(title.caps(), style = CgTheme.type.label, color = ink)
            if (where != null) {
                Text(where, style = CgTheme.type.caption, color = if (signal) ink else CgColors.Text.Secondary)
            }
        }
        if (!signal) Box(Modifier.fillMaxWidth().height(CgStroke.Hairline).background(edge))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = CgSpace.S16, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(CgSpace.S12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(summary, style = CgTheme.type.caption, color = CgColors.Text.Secondary, modifier = Modifier.weight(1f))
            trailing?.invoke()
        }
    }
}

private enum class ExportStep {
    OPTIONS,
    MAKING,
    SAVED,
}

/**
 * What an export will make, in one line: "1080p · 9:16 · 0:47 · with sound". Without [quality] when a choice beside it
 * says that.
 */
@Composable
fun exportSummary(item: VideoItem, quality: Quality?, sound: Boolean, wording: ExportWording): String =
    "${quality?.let { "${it.label} · " }.orEmpty()}${item.format.label} · ${clock(item.trimmedDurationUs)} · " +
        stringResource(
            when {
                !wording.sound || !item.hasAudio -> Res.string.edit_no_sound
                sound -> Res.string.edit_with_sound
                else -> Res.string.edit_sound_muted
            }
        )

/**
 * The export at the bottom of a wide editor's panel: the main way out as the big button ("save to Photos", "export"),
 * the other one beside it where the platform has one (share, show the file), and what it will make under them.
 */
@Composable
fun ExportBar(
    item: VideoItem,
    galleryQuality: Quality,
    shareQuality: Quality,
    qualities: List<Quality>,
    sound: Boolean,
    onSave: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
    onQuality: (ExportManager.Destination, Quality) -> Unit = { _, _ -> },
) {
    val wording = LocalExportWording.current
    val choose = qualities.size > 1
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(CgSpace.S8)) {
        Row(
            Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(CgSpace.S8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Two ways out of equal weight: the same size, the second one dark. At least 52 dp, never cut: a label that
            // wraps in a narrow panel makes both taller (the row's intrinsic height) instead of losing its second line.
            CgButton(
                stringResource(wording.save),
                onClick = onSave,
                kind = CgButtonKind.Signal,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp).fillMaxHeight().testTag(TestTags.ExportSave),
            )
            wording.share?.let {
                CgButton(
                    stringResource(it),
                    onClick = onShare,
                    kind = CgButtonKind.Secondary,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp).fillMaxHeight().testTag(TestTags.ExportShare),
                )
            }
        }
        // Each way out's quality under its button, when there is a choice.
        if (choose) {
            Row(horizontalArrangement = Arrangement.spacedBy(CgSpace.S8)) {
                QualityChoice(qualities, galleryQuality, Modifier.weight(1f), fill = true) {
                    onQuality(ExportManager.Destination.GALLERY, it)
                }
                if (wording.share != null) {
                    QualityChoice(qualities, shareQuality, Modifier.weight(1f), fill = true) {
                        onQuality(ExportManager.Destination.SHARE, it)
                    }
                }
            }
        }
        Text(
            "${exportSummary(item, galleryQuality.takeUnless { choose }, sound, wording)} · " +
                stringResource(wording.saveWhere),
            style = CgTheme.type.caption,
            color = CgColors.Text.Dim,
        )
    }
}

/** 1080p or 4K for one way out of the export. */
@Composable
private fun QualityChoice(
    qualities: List<Quality>,
    selected: Quality,
    modifier: Modifier = Modifier,
    fill: Boolean = false,
    onSelect: (Quality) -> Unit,
) {
    SegmentedControl(
        options = qualities.map { it.label },
        selectedIndex = qualities.indexOf(selected).coerceAtLeast(0),
        onSelect = { onSelect(qualities[it]) },
        modifier = modifier,
        fill = fill,
    )
}
