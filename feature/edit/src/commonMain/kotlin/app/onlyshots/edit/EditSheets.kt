package app.onlyshots.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import app.onlyshots.connect.color
import app.onlyshots.connect.label
import app.onlyshots.connect.labelRes
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgIcons
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.component.CgButton
import app.onlyshots.core.designsystem.component.CgButtonKind
import app.onlyshots.core.designsystem.component.CgTextField
import app.onlyshots.core.designsystem.component.CgToggle
import app.onlyshots.core.designsystem.component.ListRow
import app.onlyshots.core.designsystem.component.Pill
import app.onlyshots.core.designsystem.component.ProgressLine
import app.onlyshots.core.designsystem.component.SeriesChip
import app.onlyshots.core.designsystem.component.SettingRow
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
 * The data sheet: one chip per series, then whether the graph, the readings, the legend and the phase lines show. The
 * phase lines are offered only where the shot has phases ([hasPhases]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DataSheetContent(
    settings: OverlaySettings,
    available: Set<Series>,
    hasPhases: Boolean = true,
    onChange: ((OverlaySettings) -> OverlaySettings) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(CgSpace.S8),
        verticalArrangement = Arrangement.spacedBy(CgSpace.S8),
    ) {
        for (series in Series.entries) {
            SeriesChip(
                label = series.label,
                color = series.color,
                selected = series in settings.series,
                enabled = series in available,
                onClick = {
                    onChange { s -> s.copy(series = if (series in s.series) s.series - series else s.series + series) }
                },
            )
        }
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
    // The last of the two that is on cannot be turned off: a graph with neither would draw nothing.
    SettingRow(stringResource(Res.string.edit_graph)) {
        CgToggle(
            graph,
            enabled = !graph || readings,
            onCheckedChange = { onChange { s -> s.copy(layout = layoutOf(it, readings)) } },
        )
    }
    SettingRow(stringResource(Res.string.edit_readings)) {
        CgToggle(
            readings,
            enabled = !readings || graph,
            onCheckedChange = { onChange { s -> s.copy(layout = layoutOf(graph, it)) } },
        )
    }
    // The legend sits in the graph's header: without the graph there is none to hide.
    SettingRow(stringResource(Res.string.edit_legend)) {
        CgToggle(settings.legend && graph, enabled = graph, onCheckedChange = { onChange { s -> s.copy(legend = it) } })
    }
    if (hasPhases) {
        SettingRow(stringResource(Res.string.edit_phase_lines)) {
            CgToggle(
                settings.phases && graph,
                enabled = graph,
                onCheckedChange = { onChange { s -> s.copy(phases = it) } },
            )
        }
    }
}

/**
 * Where the editor's host sends "from the machine": the phones open their machine shot picker; null where machines
 * cannot be reached (desktop, web). Provided by [EditScreen].
 */
val LocalFromMachine = staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * The editor's shot sheet. With a shot attached, the shot leads: a card with its name, numbers and how it is lined up,
 * and the ways to use another one as small pills under it. Without one, those ways are the sheet.
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
        Text(
            stringResource(Res.string.edit_use_another_shot),
            style = CgTheme.type.labelStrong,
            color = CgColors.Text.Secondary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(CgSpace.S8)) {
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
            stringResource(Res.string.edit_on_connected_machine),
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
 * The attached shot: its name over a stripe in the machine's colour, its time, final weight and phases, how it is lined
 * up with the video, and what can be done about that.
 */
@Composable
private fun ShotCard(item: VideoItem, shot: Shot, onMatchByClock: () -> Unit, onUnlock: () -> Unit) {
    val card = RoundedCornerShape(16.dp)
    // Outlined rather than filled, so the pills inside (on the raised surface) still read as buttons.
    Row(Modifier.fillMaxWidth().clip(card).border(1.dp, CgColors.Line.Hairline, card).height(IntrinsicSize.Min)) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(machineColor(shot.source)))
        Column(Modifier.padding(CgSpace.S16), verticalArrangement = Arrangement.spacedBy(CgSpace.S12)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(shot.profileName, style = CgTheme.type.cardTitle)
                Text(machineLabel(shot.source), style = CgTheme.type.caption, color = CgColors.Text.Dim)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(CgSpace.S24)) {
                ShotNumber(stringResource(Res.string.edit_number_time), clock(shot.durationUs))
                shot.finalWeight?.let {
                    ShotNumber(
                        stringResource(Res.string.edit_number_weight),
                        "${(it * 10).roundToInt() / 10f} ${stringResource(Res.string.unit_grams)}",
                    )
                }
                shot.phaseStartsUs.size
                    .takeIf { it > 0 }
                    ?.let { ShotNumber(stringResource(Res.string.edit_number_phases), "$it") }
            }
            Text(
                stringResource(shotStatus(item)),
                style = CgTheme.type.caption,
                color =
                    if (item.syncLocked || item.shotStartInVideoUs != null) CgColors.Status.Live
                    else CgColors.Status.Highlight,
            )
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
}

@Composable
private fun ShotNumber(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = CgTheme.type.caption, color = CgColors.Text.Dim)
        Text(value, style = CgTheme.type.body)
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
fun TextSheetContent(item: VideoItem, onChange: (push: Boolean, (OverlaySettings) -> OverlaySettings) -> Unit) {
    var lastTyped by remember { mutableStateOf<TimeSource.Monotonic.ValueTimeMark?>(null) }
    fun push(): Boolean {
        val push = lastTyped.let { it == null || it.elapsedNow().inWholeMilliseconds > TYPING_STEP_MS }
        lastTyped = TimeSource.Monotonic.markNow()
        return push
    }
    val title = stringResource(Res.string.edit_title)
    val recipe = stringResource(Res.string.edit_recipe)
    val default = item.shot?.let { overlayTitleText(it, item.overlay.copy(title = null)) } ?: title
    Text(title, style = CgTheme.type.labelStrong, color = CgColors.Text.Secondary)
    CgTextField(
        value = item.overlay.title.orEmpty(),
        onValueChange = { text -> onChange(push()) { it.copy(title = text.ifEmpty { null }) } },
        label = title,
        placeholder = default,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(recipe, style = CgTheme.type.labelStrong, color = CgColors.Text.Secondary)
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
    quality: Quality,
    /** Whether the export has sound: the video has it and the editor did not mute it. */
    sound: Boolean,
    exporting: Float?,
    leftMs: Long?,
    savedToGallery: Boolean,
    onShare: () -> Unit,
    onSaveToGallery: () -> Unit,
    onNextShot: () -> Unit,
    onCancel: () -> Unit,
) {
    val wording = LocalExportWording.current
    if (savedToGallery) {
        Text(
            "${stringResource(wording.saved)} · ${quality.label} · ${clock(item.trimmedDurationUs)}",
            style = CgTheme.type.bodySmall,
            color = CgColors.Text.Secondary,
        )
        Pill(
            stringResource(wording.next),
            onClick = onNextShot,
            modifier = Modifier.fillMaxWidth(),
            light = true,
            fill = true,
        )
        return
    }
    if (exporting != null) {
        ProgressLine(exporting)
        Row(Modifier.fillMaxWidth()) {
            Text("${(exporting * 100).toInt()} %", style = CgTheme.type.label, modifier = Modifier.weight(1f))
            Text(
                leftMs?.let { stringResource(Res.string.edit_time_left, clock(it * 1000)) }
                    ?: stringResource(Res.string.edit_working_out),
                style = CgTheme.type.caption,
                color = CgColors.Text.Dim,
            )
        }
        Text(stringResource(Res.string.edit_keep_open), style = CgTheme.type.caption, color = CgColors.Text.Dim)
        Pill(
            stringResource(Res.string.edit_cancel),
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
            fill = true,
        )
        return
    }
    val what = exportSummary(item, quality, sound, wording)
    ListRow(stringResource(wording.save), "$what · ${stringResource(wording.saveWhere)}", onClick = onSaveToGallery)
    wording.share?.let { share ->
        val where = wording.shareWhere?.let { " · ${stringResource(it)}" }.orEmpty()
        ListRow(stringResource(share), "$what$where", onClick = onShare)
    }
    Text(stringResource(wording.footer), style = CgTheme.type.caption, color = CgColors.Text.Dim)
}

/** What an export will make, in one line: "1080p · 9:16 · 0:47 · with sound". */
@Composable
fun exportSummary(item: VideoItem, quality: Quality, sound: Boolean, wording: ExportWording): String =
    "${quality.label} · ${item.format.label} · ${clock(item.trimmedDurationUs)} · " +
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
    quality: Quality,
    sound: Boolean,
    onSave: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val wording = LocalExportWording.current
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
                modifier = Modifier.weight(1f).heightIn(min = 52.dp).fillMaxHeight(),
            )
            wording.share?.let {
                CgButton(
                    stringResource(it),
                    onClick = onShare,
                    kind = CgButtonKind.Secondary,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp).fillMaxHeight(),
                )
            }
        }
        Text(
            "${exportSummary(item, quality, sound, wording)} · ${stringResource(wording.saveWhere)}",
            style = CgTheme.type.caption,
            color = CgColors.Text.Dim,
        )
    }
}
