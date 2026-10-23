package app.onlyshots.open

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.onlyshots.SharedKeys
import app.onlyshots.TestTags
import app.onlyshots.clockLength
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgIcons
import app.onlyshots.core.designsystem.CgMotion
import app.onlyshots.core.designsystem.CgSize
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgStroke
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.caps
import app.onlyshots.core.designsystem.component.CgIcon
import app.onlyshots.core.model.VideoItem
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.illus_shot_tile
import app.onlyshots.core.resources.illus_video_tile
import app.onlyshots.core.resources.open_editor
import app.onlyshots.core.resources.open_from_file
import app.onlyshots.core.resources.open_from_machine
import app.onlyshots.core.resources.open_from_machine_detail
import app.onlyshots.core.resources.open_phone_shot_title
import app.onlyshots.core.resources.open_phone_video_title
import app.onlyshots.core.resources.open_shot_file_detail
import app.onlyshots.core.resources.open_video
import app.onlyshots.core.resources.text
import app.onlyshots.core.resources.visualizer_from
import app.onlyshots.core.resources.visualizer_from_detail
import app.onlyshots.layout.FoldPage
import app.onlyshots.layout.FoldPanes
import app.onlyshots.layout.FoldScrim
import app.onlyshots.layout.WindowLayout
import app.onlyshots.shareBounds
import app.onlyshots.testTagsAsResourceIds
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/*
 * The pieces more than one Open layout is built from: the steps' rows and keys, "open editor", the picked video's
 * tile and facts, and the shot sheet's choices.
 */

// The phone app's steps (and the web's on a phone) -----------------------------------------------------------------

/**
 * The two steps in their hairline frame, as the phone app's layouts and the web's phone page show them, in [words].
 * [large] on a big tablet.
 */
@Composable
internal fun AppSteps(
    ui: OpenUi,
    words: StepWords,
    actions: OpenActions,
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    StepFrame(modifier) {
        StepRow(
            number = 1,
            title = stringResource(Res.string.open_phone_video_title),
            detail = ui.videoDetail(words).text(),
            action = pickAction(ui.picked != null).text(),
            done = ui.picked != null,
            next = ui.videoNext,
            highlighted = ui.dragging,
            large = large,
            onClick = actions::pickVideo,
            modifier = Modifier.testTag(TestTags.OpenPickVideo),
        )
        StepDivider()
        StepRow(
            number = 2,
            title = stringResource(Res.string.open_phone_shot_title),
            detail = ui.shotDetail(words).text(),
            action = pickAction(ui.shot != null).text(),
            done = ui.shot != null,
            next = ui.shotNext,
            large = large,
            onClick = actions::pickShot,
            modifier = Modifier.testTag(TestTags.OpenPickShot),
        )
    }
}

/** The steps' hairline frame: the two rows share it, with a hairline between them. */
@Composable
internal fun StepFrame(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().border(CgStroke.Hairline, CgColors.Line.Hairline), content = content)
}

@Composable
internal fun StepDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(CgStroke.Hairline).background(CgColors.Line.Hairline))
}

/**
 * A numbered step on the phones, from the canvas's Open-Phone and Open-Ready: its number in a 36 dp square (signal when
 * it is next, bone with a tick once done), what it is, what was picked (or what to pick), and the key. The next step
 * carries a signal rule down its leading edge; the whole row takes the tap.
 */
@Composable
internal fun StepRow(
    number: Int,
    title: String,
    detail: String,
    action: String,
    done: Boolean,
    next: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    large: Boolean = false,
) {
    // A step lights up as it is done or hovered with a file, rather than switching at once; read while drawing.
    val ground = animateColorAsState(if (highlighted) CgColors.Surface.Raised else Color.Transparent, CgMotion.short())
    val box = animateColorAsState(if (done) CgColors.Brand.Bone else Color.Transparent, CgMotion.medium())
    val edge =
        when {
            done -> CgColors.Brand.Bone
            highlighted -> CgColors.Brand.Signal
            else -> CgColors.Line.Hairline
        }
    val digit =
        when {
            done -> CgColors.Text.OnLight
            next -> CgColors.Text.Primary
            else -> CgColors.Text.Secondary
        }
    Row(
        modifier
            .fillMaxWidth()
            .drawBehind { drawRect(ground.value) }
            .clickable(role = Role.Button, onClick = onClick)
            .drawBehind {
                if (next || highlighted) {
                    drawRect(CgColors.Brand.Signal, size = Size(LeadRule.toPx(), size.height))
                }
            }
            .padding(if (large) CgSpace.S24 else CgSpace.S16),
        horizontalArrangement = Arrangement.spacedBy(if (large) CgSpace.S16 else CgSpace.S12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(if (large) CgSize.Touch else NumberBox)
                .drawBehind { drawRect(box.value) }
                .border(CgStroke.Hairline, edge),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                CgIcon(CgIcons.Check, tint = digit, size = 18.dp)
            } else {
                Text(stepNumber(number), style = CgTheme.type.labelStrong.copy(letterSpacing = 0.em), color = digit)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title.caps(),
                style = (if (large) CgTheme.type.screenTitle else CgTheme.type.label).copy(letterSpacing = 0.em),
            )
            // "choose a video" ticks over to the picked file's name and length.
            AnimatedContent(
                detail,
                transitionSpec = { with(CgMotion) { tick() } },
                label = "detail",
            ) {
                Text(
                    it,
                    style =
                        when {
                            done -> CgTheme.type.data
                            large -> CgTheme.type.body
                            else -> CgTheme.type.bodySmall
                        },
                    color = CgColors.Text.Secondary,
                )
            }
        }
        StepKey(action, signal = next && !done, onClick = null)
    }
}

/** The signal rule down the leading edge of the step that is next. */
private val LeadRule = 3.dp

/** The step's number square on a phone; a big tablet's is a touch target. */
private val NumberBox = 36.dp

// Keys -------------------------------------------------------------------------------------------------------------

/**
 * A step's key: 44 dp, Mono capitals; the signal fill when it is the next thing to do, else a hairline outline. Used by
 * every Open layout. Without [onClick] it only shows the way: the whole step row is what takes the tap.
 */
@Composable
internal fun StepKey(
    text: String,
    signal: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    /** A light runs over it now and then, like a shine on metal: the key to press next. */
    shine: Boolean = false,
) {
    Box(
        modifier
            .heightIn(min = CgSize.Touch)
            .background(if (signal) CgColors.Brand.Signal else Color.Transparent)
            .then(if (shine) Modifier.shine() else Modifier)
            .border(CgStroke.Hairline, if (signal) CgColors.Brand.Signal else CgColors.Line.Hairline)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp),
        Alignment.Center,
    ) {
        Text(
            text.caps(),
            style = CgTheme.type.labelStrong.copy(letterSpacing = 0.em),
            color = if (signal) CgColors.Text.OnLight else CgColors.Text.Primary,
        )
    }
}

/**
 * A soft light that crosses the key at a slant every few seconds and is gone between: a shine on metal. It starts off
 * the left edge and waits off the right, so a still frame (an IDE preview, a screenshot) shows the key plain.
 */
@Composable
private fun Modifier.shine(): Modifier {
    if (LocalInspectionMode.current) return this
    // A wait, a run across, back to the start: nothing draws while it waits, so the screen can go idle.
    val at = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(SHINE_REST_MS.toLong())
            at.animateTo(1f, tween(SHINE_PERIOD_MS - SHINE_REST_MS, easing = LinearEasing))
            at.snapTo(0f)
        }
    }
    // Its own layer, so only the key redraws; the light made once per size and moved.
    return graphicsLayer().clipToBounds().drawWithCache {
        val band = size.height * 2f
        val light =
            Brush.linearGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = 0.35f), Color.Transparent),
                start = Offset(-band / 2f, 0f),
                end = Offset(band / 2f, size.height),
            )
        onDrawWithContent {
            drawContent()
            val x = -band + (size.width + band * 2f) * at.value
            translate(left = x) { drawRect(light, topLeft = Offset(-x, 0f), size = size) }
        }
    }
}

/** A shine every 3.2 s: 2.4 s at rest, then 0.8 s across. */
private const val SHINE_PERIOD_MS = 3_200
private const val SHINE_REST_MS = 2_400

/**
 * "Open editor →": the signal key once the video and the shot are picked, a hairline outline with grey letters before.
 * [fill] stretches it over the width with the arrow at the far end (the phones); otherwise it is as wide as its words.
 */
@Composable
internal fun OpenEditorKey(ready: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, fill: Boolean = false) {
    // Read while drawing: the change of colour redraws the key, it does not recompose it.
    val ground = animateColorAsState(if (ready) CgColors.Brand.Signal else Color.Transparent, CgMotion.short())
    val ink by animateColorAsState(if (ready) CgColors.Text.OnLight else CgColors.Text.Disabled, CgMotion.short())
    Row(
        modifier
            .testTag(TestTags.OpenEditor)
            .then(if (fill) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = OpenEditorHeight)
            .drawBehind { drawRect(ground.value) }
            .border(CgStroke.Hairline, if (ready) CgColors.Brand.Signal else CgColors.Line.Hairline)
            .clickable(enabled = ready, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (fill) 20.dp else 24.dp),
        horizontalArrangement = if (fill) Arrangement.SpaceBetween else Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(Res.string.open_editor).caps(), style = CgTheme.type.label, color = ink)
        CgIcon(CgIcons.ArrowRight, tint = ink, size = 18.dp)
    }
}

/** "Open editor" stands taller than the steps' keys: it is the screen's one way on. */
private val OpenEditorHeight = 56.dp

/**
 * Why the last pick did not take (a video it cannot read, a shot file it cannot parse): Mono, in the warning colour.
 */
@Composable
internal fun StatusLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = CgTheme.type.labelStrong.copy(letterSpacing = 0.em),
        color = CgColors.Status.Warning,
        modifier = modifier,
    )
}

// The picked video and shot ---------------------------------------------------------------------------------------

/**
 * The video's tile: a frame of it, cropped to fill, in a hairline frame; until that is decoded (or where it cannot be),
 * the design's drawing of a cup under the spout with the graph's strip along the bottom. With [videoId], the editor's
 * picture grows out of it.
 */
@Composable
internal fun VideoTile(thumb: ImageBitmap?, modifier: Modifier = Modifier, videoId: String? = null) {
    val shared = if (videoId != null) Modifier.shareBounds(SharedKeys.video(videoId)) else Modifier
    // The frame fades in over the drawing once it is decoded.
    Crossfade(
        thumb,
        shared.then(modifier).border(CgStroke.Hairline, CgColors.Line.Hairline),
        CgMotion.medium(),
        label = "thumb",
    ) { frame ->
        if (frame != null) {
            Image(frame, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Image(
                painterResource(Res.drawable.illus_video_tile),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** The shot's tile: two curves, pressure and flow, in a hairline square. */
@Composable
internal fun ShotTile(modifier: Modifier = Modifier) {
    Box(
        modifier.size(64.dp).background(CgColors.Surface.Raised).border(CgStroke.Hairline, CgColors.Line.Hairline),
        Alignment.Center,
    ) {
        Image(painterResource(Res.drawable.illus_shot_tile), contentDescription = null, Modifier.size(44.dp, 30.dp))
    }
}

/** The video's length on its frame, bottom left. */
@Composable
internal fun LengthTag(picked: VideoItem, modifier: Modifier = Modifier) {
    Text(
        clockLength(picked.trimmedDurationUs),
        style = CgTheme.type.labelStrong.copy(letterSpacing = 0.em),
        modifier =
            modifier
                .padding(6.dp)
                .background(CgColors.Surface.Base.copy(alpha = 0.85f))
                .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

/** "VIDEO", the file's name and its size, beside or under the picked video's frame. */
@Composable
internal fun VideoFacts(file: PickedFile?, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(CgSpace.S4)) {
        Text(
            stringResource(Res.string.open_video).caps(),
            style = CgTheme.type.caption,
            color = CgColors.Text.Secondary,
        )
        Text(
            videoName(file).text(),
            style = CgTheme.type.label.copy(letterSpacing = 0.em),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        file?.sizeText()?.let { Text(it, style = CgTheme.type.caption, color = CgColors.Text.Secondary) }
    }
}

/** A dashed hairline inside the bounds, square, as the canvas draws a step still to do. */
internal fun DrawScope.dashedBorder(color: Color) {
    val w = CgStroke.Hairline.toPx()
    drawRect(
        color,
        topLeft = Offset(w / 2, w / 2),
        size = Size(size.width - w, size.height - w),
        style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
    )
}

// The shot sheet ---------------------------------------------------------------------------------------------------

/** What the shot sheet offers: a file, the machine where the host can reach one, and a shot shared on Visualizer. */
@Composable
fun ShotSourceChoices(
    onFile: () -> Unit,
    onMachine: (() -> Unit)?,
    onVisualizer: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        SourceChoice(
            title = stringResource(Res.string.open_from_file),
            detail = stringResource(Res.string.open_shot_file_detail),
            onClick = onFile,
            modifier = Modifier.testTag(TestTags.OpenFromFile),
        ) {
            CgIcon(CgIcons.File, size = 18.dp)
        }
        if (onMachine != null) {
            SourceChoice(
                title = stringResource(Res.string.open_from_machine),
                detail = stringResource(Res.string.open_from_machine_detail),
                onClick = onMachine,
                // The cells share their hairline, as the canvas stacks them.
                modifier = Modifier.offset(y = -CgStroke.Hairline).testTag(TestTags.OpenFromMachine),
            ) {
                // The machine's colour bar, as its rows wear it.
                Box(Modifier.size(width = CgSpace.S4, height = CgSpace.S24).background(CgColors.Machine.Gaggiuino))
            }
        }
        if (onVisualizer != null) {
            SourceChoice(
                title = stringResource(Res.string.visualizer_from),
                detail = stringResource(Res.string.visualizer_from_detail),
                onClick = onVisualizer,
                modifier =
                    Modifier.offset(y = -CgStroke.Hairline * (if (onMachine != null) 2 else 1))
                        .testTag(TestTags.ShotFromVisualizer),
            ) {
                Box(Modifier.size(width = CgSpace.S4, height = CgSpace.S24).background(CgColors.Machine.Visualizer))
            }
        }
    }
}

/**
 * One way to get the shot, in the shot sheet: a hairline cell with a 44 dp framed [tile], the way in Mono capitals,
 * what it takes, and a chevron.
 */
@Composable
private fun SourceChoice(
    title: String,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tile: @Composable () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .border(CgStroke.Hairline, CgColors.Line.Hairline)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(CgSpace.S16),
        horizontalArrangement = Arrangement.spacedBy(CgSpace.S12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(CgSize.Touch).border(CgStroke.Hairline, CgColors.Line.Hairline),
            contentAlignment = Alignment.Center,
        ) {
            tile()
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title.caps(), style = CgTheme.type.label)
            Text(detail, style = CgTheme.type.bodySmall, color = CgColors.Text.Secondary)
        }
        CgIcon(CgIcons.ChevronRight, tint = CgColors.Text.Secondary, size = 18.dp)
    }
}

/**
 * A sheet on a fold (Open-Source-F): the right page, under a signal rule, with its header and DONE key, clear of the
 * window's edges; the left page dimmed under a scrim that closes it, as does back.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun FoldPageSheet(
    title: String,
    layout: WindowLayout,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    NavigationBackHandler(rememberNavigationEventState(NavigationEventInfo.None)) { onDismiss() }
    FoldPanes(
        layout,
        modifier.testTagsAsResourceIds(),
        first = { FoldScrim(onDismiss) },
        second = {
            FoldPage(
                title,
                onDismiss,
                Modifier.background(CgColors.Surface.Bar),
                // The right page meets the window's top, end and bottom; the header sits a step below the rule.
                contentPadding =
                    WindowInsets.safeDrawing
                        .only(WindowInsetsSides.Top + WindowInsetsSides.End + WindowInsetsSides.Bottom)
                        .add(WindowInsets(top = CgSpace.S24))
                        .asPaddingValues(),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(top = CgSpace.S12).padding(horizontal = CgSpace.S16),
                    verticalArrangement = Arrangement.spacedBy(CgSpace.S12),
                    content = content,
                )
            }
        },
    )
}
