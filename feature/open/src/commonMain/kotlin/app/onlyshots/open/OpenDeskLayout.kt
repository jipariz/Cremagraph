package app.onlyshots.open

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.onlyshots.PRIVACY_URL
import app.onlyshots.TestTags
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgIcons
import app.onlyshots.core.designsystem.CgMotion
import app.onlyshots.core.designsystem.CgSize
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgStroke
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.caps
import app.onlyshots.core.designsystem.component.CgIcon
import app.onlyshots.core.designsystem.component.OnlyShotsMark
import app.onlyshots.core.designsystem.headline
import app.onlyshots.core.model.NormRect
import app.onlyshots.core.model.OverlaySettings
import app.onlyshots.core.model.sample.SampleShots
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.app_name
import app.onlyshots.core.resources.open_change
import app.onlyshots.core.resources.open_details_does
import app.onlyshots.core.resources.open_details_does_title
import app.onlyshots.core.resources.open_details_shots
import app.onlyshots.core.resources.open_details_shots_machine
import app.onlyshots.core.resources.open_details_shots_title
import app.onlyshots.core.resources.open_details_videos
import app.onlyshots.core.resources.open_details_videos_title
import app.onlyshots.core.resources.open_preview_caption
import app.onlyshots.core.resources.open_privacy
import app.onlyshots.core.resources.open_wide_choose_shot
import app.onlyshots.core.resources.open_wide_choose_video
import app.onlyshots.core.resources.open_wide_intro
import app.onlyshots.core.resources.open_wide_shot_title
import app.onlyshots.core.resources.open_wide_video_title
import app.onlyshots.core.resources.photo_shot
import app.onlyshots.core.resources.start_title
import app.onlyshots.core.resources.text
import app.onlyshots.overlay.GraphStyles
import app.onlyshots.overlay.compose.ShotOverlay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The desktop's and the web's start, from the canvas's "Open-Desk" artboard: on a faint grid, a header with where it
 * runs, the title and the two steps as cells beside a preview of what comes out, "open editor", and what it does, reads
 * and makes in a hairline grid underneath. Narrow, the preview goes under the steps, and the steps and notes stack
 * ([deskMetrics]).
 */
@Composable
internal fun OpenDeskLayout(
    ui: OpenUi,
    actions: OpenActions,
    entry: OpenEntry.Desk,
    modifier: Modifier = Modifier,
) {
    // Measured for fitting the page to its width, not for choosing a layout.
    BoxWithConstraints(modifier.fillMaxSize().background(CgColors.Surface.Base).safeDrawingPadding()) {
        val metrics = deskMetrics(maxWidth.value)
        val gutter = metrics.gutterDp.dp
        Column(Modifier.fillMaxSize().drawBehind { grid() }.verticalScroll(rememberScrollState())) {
            Header(entry.host?.headerNote, gutter, narrow = !metrics.twoUp, onPrivacy = entry.onPrivacy)
            Column(
                Modifier.fillMaxWidth()
                    .wrapContentHeight()
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = PageMaxWidth)
                    .padding(start = gutter, end = gutter, top = metrics.topDp.dp, bottom = SectionGap),
                verticalArrangement = Arrangement.spacedBy(SectionGap),
            ) {
                if (metrics.previewBeside) {
                    Row(horizontalArrangement = Arrangement.spacedBy(metrics.previewGapDp.dp)) {
                        DeskSteps(ui, actions, twoUp = metrics.twoUp, modifier = Modifier.weight(1f))
                        Preview(Modifier.width(PreviewWidth))
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        DeskSteps(ui, actions, twoUp = metrics.twoUp, modifier = Modifier.fillMaxWidth())
                        Preview(Modifier.width(PreviewWidth))
                    }
                }
                // The web offers the phone app: it films the shot live, which the page cannot.
                entry.stores?.takeIf { it.any }?.let { AppBand(it, wide = metrics.twoUp) }
                Details(ui.fromMachine, entry.host?.videosNote, columns = metrics.twoUp)
            }
        }
    }
}

/** The page's faint 24 dp grid, as the canvas draws the desk's ground. */
private fun DrawScope.grid() {
    val step = 24.dp.toPx()
    val line = CgColors.Text.Primary.copy(alpha = 0.035f)
    var x = 0f
    while (x < size.width) {
        drawLine(line, Offset(x, 0f), Offset(x, size.height), 1f)
        x += step
    }
    var y = 0f
    while (y < size.height) {
        drawLine(line, Offset(0f, y), Offset(size.width, y), 1f)
        y += step
    }
}

/** The name, where it runs, and the privacy link; narrow, the last two go under the name. */
@Composable
private fun Header(
    note: String?,
    gutter: Dp,
    narrow: Boolean,
    onPrivacy: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        if (narrow) {
            Column(Modifier.fillMaxWidth().padding(horizontal = gutter, vertical = 14.dp)) {
                Brand()
                HeaderMeta(note, onPrivacy)
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = gutter, vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(CgSpace.S16),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Brand()
                Box(Modifier.weight(1f))
                HeaderMeta(note, onPrivacy)
            }
        }
        StepDivider()
    }
}

@Composable
private fun Brand(modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        OnlyShotsMark(splashTarget = true)
        Text(
            stringResource(Res.string.app_name).caps(),
            style = CgTheme.type.sheetTitle.copy(letterSpacing = 0.08.em),
            color = CgColors.Text.Primary,
        )
    }
}

/** Where it runs, beside the shield, and "privacy": the app's own screen, or the web page. */
@Composable
private fun HeaderMeta(note: String?, onPrivacy: (() -> Unit)?, modifier: Modifier = Modifier) {
    val uri = LocalUriHandler.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
        if (note != null) {
            Row(
                Modifier.weight(1f, fill = false),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CgIcon(CgIcons.Shield, tint = CgColors.Text.Secondary, size = 16.dp)
                Text(note, style = CgTheme.type.caption, color = CgColors.Text.Secondary)
            }
        }
        Text(
            stringResource(Res.string.open_privacy),
            style = CgTheme.type.labelStrong.copy(letterSpacing = 0.em, textDecoration = TextDecoration.Underline),
            color = CgColors.Text.Primary,
            modifier =
                Modifier.heightIn(min = CgSize.Touch).wrapContentHeight().clickable(role = Role.Button) {
                    onPrivacy?.invoke() ?: uri.openUri(PRIVACY_URL)
                },
        )
    }
}

/** The title, the two steps (side by side when [twoUp]), and "open editor" with what it is waiting for. */
@Composable
private fun DeskSteps(ui: OpenUi, actions: OpenActions, twoUp: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(
            stringResource(Res.string.start_title).headline(),
            style = CgTheme.type.valueLarge.copy(letterSpacing = (-0.02).em, lineHeight = 48.sp),
            color = CgColors.Text.Primary,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(Res.string.open_wide_intro),
            style = CgTheme.type.body.copy(lineHeight = 24.sp),
            color = CgColors.Text.Secondary,
            modifier = Modifier.widthIn(max = IntroMaxWidth),
        )
        if (twoUp) {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(CgSpace.S16)) {
                DeskVideoStep(ui, actions, Modifier.weight(1f).fillMaxHeight())
                DeskShotStep(ui, actions, Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(CgSpace.S12)) {
                DeskVideoStep(ui, actions, Modifier.fillMaxWidth())
                DeskShotStep(ui, actions, Modifier.fillMaxWidth())
            }
        }
        ui.problem?.let { StatusLine(it.text()) }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            OpenEditorKey(ready = ui.ready, onClick = actions::openEditor)
            Text(ui.ctaHint().text(), style = CgTheme.type.bodySmall, color = CgColors.Text.Secondary)
        }
    }
}

/** Step 1 on the desk: an empty cell to drop or choose a video on, ticking over to the picked video. */
@Composable
private fun DeskVideoStep(ui: OpenUi, actions: OpenActions, modifier: Modifier = Modifier) {
    val title = stringResource(Res.string.open_wide_video_title)
    StepSwap(ui.picked != null, modifier) { done ->
        val picked = ui.picked
        if (!done || picked == null) {
            DeskEmptyStep(
                number = 1,
                title = title,
                detail = ui.emptyVideoDetail(StepWords.Desk).text(),
                action = stringResource(Res.string.open_wide_choose_video),
                next = true,
                highlighted = ui.dragging,
                onClick = actions::pickVideo,
                tag = TestTags.OpenPickVideo,
            )
        } else {
            DeskPickedStep(
                number = 1,
                title = title,
                name = videoName(ui.pickedFile).text(),
                detail = videoLine(picked, ui.pickedFile).text(),
                tile = { VideoTile(ui.pickedFile?.thumb, Modifier.size(64.dp, 112.dp), videoId = picked.id) },
                onChange = actions::pickVideo,
                highlighted = ui.dragging,
                tag = TestTags.OpenPickVideo,
            )
        }
    }
}

/** Step 2 on the desk: an empty cell to choose the shot, ticking over to the picked shot. */
@Composable
private fun DeskShotStep(ui: OpenUi, actions: OpenActions, modifier: Modifier = Modifier) {
    val title = stringResource(Res.string.open_wide_shot_title)
    StepSwap(ui.shot != null, modifier) { done ->
        val shot = ui.shot
        if (!done || shot == null) {
            DeskEmptyStep(
                number = 2,
                title = title,
                detail = ui.shotPrompt(StepWords.Desk).text(),
                action = stringResource(Res.string.open_wide_choose_shot),
                next = ui.picked != null,
                highlighted = false,
                onClick = actions::pickShot,
                tag = TestTags.OpenPickShot,
            )
        } else {
            DeskPickedStep(
                number = 2,
                title = title,
                name = shot.label,
                detail = shotLine(shot).text(),
                tile = { ShotTile() },
                onChange = actions::pickShot,
                highlighted = false,
                tag = TestTags.OpenPickShot,
            )
        }
    }
}

/** A step not done yet, on the desk: a dashed cell (signal when it is next), its number and name, and its key. */
@Composable
private fun DeskEmptyStep(
    number: Int,
    title: String,
    detail: String,
    action: String,
    next: Boolean,
    highlighted: Boolean,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
) {
    val accent =
        if (highlighted) CgColors.Brand.Signal else if (next) CgColors.Text.Primary else CgColors.Text.Secondary
    Column(
        modifier
            .fillMaxSize()
            .background(if (highlighted) CgColors.Surface.Raised else Color.Transparent)
            .then(
                if (highlighted) Modifier.border(CgStroke.Rule, CgColors.Brand.Signal)
                else Modifier.drawBehind { dashedBorder(CgColors.Line.Hairline) }
            )
            .padding(CgSpace.S16),
        // The words at the top, the key in the bottom corner: side by side, both cells' keys line up.
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.padding(bottom = CgSpace.S8), verticalArrangement = Arrangement.spacedBy(CgSpace.S8)) {
            Text("${stepNumber(number)} · ${title.caps()}", style = CgTheme.type.data, color = accent)
            Text(detail, style = CgTheme.type.body, color = CgColors.Text.Secondary)
        }
        StepKey(action, signal = next, onClick = onClick, modifier = Modifier.testTag(tag), shine = next)
    }
}

/** A step done, on the desk: a small picture of what was picked, its name and line, and "change". */
@Composable
private fun DeskPickedStep(
    number: Int,
    title: String,
    name: String,
    detail: String,
    tile: @Composable () -> Unit,
    onChange: () -> Unit,
    highlighted: Boolean,
    tag: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxSize()
            .background(if (highlighted) CgColors.Surface.Raised else Color.Transparent)
            .border(
                if (highlighted) CgStroke.Rule else CgStroke.Hairline,
                if (highlighted) CgColors.Brand.Signal else CgColors.Line.Hairline,
            )
            .padding(CgSpace.S16),
        horizontalArrangement = Arrangement.spacedBy(CgSpace.S16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tile()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CgSpace.S4)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(CgSpace.S6),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${stepNumber(number)} · ${title.caps()}",
                    style = CgTheme.type.data,
                    color = CgColors.Status.Success,
                )
                CgIcon(CgIcons.Check, tint = CgColors.Status.Success, size = 14.dp)
            }
            Text(
                name,
                style = CgTheme.type.label.copy(letterSpacing = 0.em),
                color = CgColors.Text.Primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(detail, style = CgTheme.type.data, color = CgColors.Text.Secondary)
        }
        StepKey(
            stringResource(Res.string.open_change),
            signal = false,
            onClick = onChange,
            modifier = Modifier.testTag(tag),
        )
    }
}

/** An empty step and the same step picked: the one ticks over to the other, the column moving to its new height. */
@Composable
private fun StepSwap(done: Boolean, modifier: Modifier = Modifier, content: @Composable (done: Boolean) -> Unit) {
    AnimatedContent(done, modifier, transitionSpec = { with(CgMotion) { tick() } }, label = "step") { content(it) }
}

/** What comes out: a 9:16 frame with the cup under the spout and the graph's card over it. */
@Composable
private fun Preview(modifier: Modifier = Modifier) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CgSpace.S12),
    ) {
        Box(
            Modifier.size(PreviewWidth, PreviewHeight).border(CgStroke.Hairline, CgColors.Line.Hairline).clipToBounds()
        ) {
            Image(
                painterResource(Res.drawable.photo_shot),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // The graph as the app draws it and the export writes it: Instrument, the sample shot, near its end.
            val style = GraphStyles.byId(PREVIEW_STYLE) ?: GraphStyles.all.first()
            ShotOverlay(
                shot = SampleShots.zer0,
                videoTimeUs = PREVIEW_AT_US,
                shotStartUs = 0L,
                settings = OverlaySettings(styleId = style.id, rect = PREVIEW_RECT),
                style = style,
                title = PREVIEW_TITLE,
                fonts = CgTheme.overlayFonts,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            stringResource(Res.string.open_preview_caption),
            style = CgTheme.type.caption,
            color = CgColors.Text.Secondary,
        )
    }
}

/** The preview's graph: its style, the moment of the sample shot it shows, and its title (proper nouns). */
private const val PREVIEW_STYLE = "instrument"
private const val PREVIEW_AT_US = 30_000_000L
private const val PREVIEW_TITLE = "Zer0 · Gaggiuino"
/** Up over the group head, so the cup and the stream under it stay in view. */
private val PREVIEW_RECT = NormRect.Default.copy(y = 60f / NormRect.REF_H)

/** What it does, which shots it reads, which videos it takes: three cells in a hairline grid. */
@Composable
private fun Details(fromMachine: Boolean, videosNote: String?, columns: Boolean, modifier: Modifier = Modifier) {
    val notes =
        listOf(
            stringResource(Res.string.open_details_does_title) to stringResource(Res.string.open_details_does),
            stringResource(Res.string.open_details_shots_title) to
                stringResource(
                    if (fromMachine) Res.string.open_details_shots_machine else Res.string.open_details_shots
                ),
            stringResource(Res.string.open_details_videos_title) to
                (stringResource(Res.string.open_details_videos) + (videosNote?.let { " $it" } ?: "")),
        )
    val frame = modifier.fillMaxWidth().background(CgColors.Line.Hairline).padding(CgStroke.Hairline)
    if (columns) {
        Row(frame.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(CgStroke.Hairline)) {
            for ((title, text) in notes) DetailNote(title, text, Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        Column(frame, verticalArrangement = Arrangement.spacedBy(CgStroke.Hairline)) {
            for ((title, text) in notes) DetailNote(title, text, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun DetailNote(title: String, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier.background(CgColors.Surface.Base).padding(CgSpace.S16),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            title.caps(),
            style = CgTheme.type.labelStrong.copy(letterSpacing = 0.em),
            color = CgColors.Text.Secondary,
            modifier = Modifier.semantics { heading() },
        )
        Text(text, style = CgTheme.type.bodySmall.copy(lineHeight = 20.sp), color = CgColors.Text.Primary)
    }
}

/** The page's content stops widening here (1440 less its gutters), centred on a wider window. */
private val PageMaxWidth = 1312.dp

/** Between the page's sections, and under the last. */
private val SectionGap = 40.dp

private val IntroMaxWidth = 640.dp

/** What comes out, in 9:16. */
private val PreviewWidth = 300.dp
private val PreviewHeight = 533.dp
