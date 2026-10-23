package app.onlyshots.edit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import app.onlyshots.SharedKeys
import app.onlyshots.TestTags
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgIcons
import app.onlyshots.core.designsystem.CgMotion
import app.onlyshots.core.designsystem.CgSize
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgStroke
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.component.CgIcon
import app.onlyshots.core.designsystem.component.OVER_VIDEO
import app.onlyshots.core.designsystem.component.StatusChip
import app.onlyshots.core.model.VideoFormat
import app.onlyshots.core.model.VideoItem
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.edit_frame_switch
import app.onlyshots.core.resources.edit_reframe_hint
import app.onlyshots.core.resources.text
import app.onlyshots.label
import app.onlyshots.layout.animateSizeOn
import app.onlyshots.layout.fitRatio
import app.onlyshots.overlay.compose.ShotOverlay
import app.onlyshots.overlayTitleText
import app.onlyshots.shareBounds
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import org.jetbrains.compose.resources.stringResource

/**
 * The picture as large as its space allows, in the video's shape, with the graph drawn over it and editable on it.
 * [picture] draws the video; [chrome] says which keys this layout puts on the picture rather than in its panels.
 */
@Composable
internal fun EditPicture(
    ui: EditUiState,
    video: VideoItem,
    player: PlayerState,
    local: EditLocalState,
    actions: EditActions,
    chrome: PictureChrome,
    picture: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The video is Haze's source, so styles that blur what is behind the graph blur the video.
    val haze = rememberHazeState()
    // Each tap that plays or pauses, for the mark that answers it.
    var taps by remember { mutableIntStateOf(0) }
    // While the graph is moved, the keys over the picture step aside, so it can go where they are.
    var graphMoving by remember { mutableStateOf(false) }
    val keysAlpha by animateFloatAsState(if (graphMoving) 0f else 1f, CgMotion.short(), label = "keys")
    BoxWithConstraints(
        modifier.fillMaxWidth().background(CgColors.Surface.Scrim),
        contentAlignment = Alignment.Center,
    ) {
        // The video's own shape, as large as it fits: the width first (a wide format fills it), the height where the
        // width would make it too tall (a tall format fills the height). Another format picked grows or shrinks it
        // animated; the window turned or resized moves it at once.
        val (fitW, fitH) = fitRatio(maxWidth.value, maxHeight.value, video.format.ratio)
        val frame = animateSizeOn(DpSize(fitW.dp, fitH.dp), animateOn = video.format)
        // The frame is the same one the record screen filmed in, and the thumbnail it was picked from: it grows out of
        // either on the way in.
        Box(
            Modifier.shareBounds(SharedKeys.video(video.id), overlay = false)
                .shareBounds(SharedKeys.TAKE_FRAME, overlay = false)
                .size(frame.width, frame.height)
        ) {
            VideoLayer(
                ui = ui,
                video = video,
                reframing = local.reframing,
                haze = haze,
                actions = actions,
                onTap = {
                    player.toggle()
                    taps++
                },
                picture = picture,
            )
            // Framing: the frame's edge in the signal and its thirds, to line the cup up on.
            if (local.reframing) FrameGuides(Modifier.fillMaxSize())
            GraphLayer(
                ui = ui,
                video = video,
                player = player,
                local = local,
                actions = actions,
                toolbar = chrome.toolbar,
                haze = haze,
                onMoving = { graphMoving = it },
            )
            PlayMark(
                taps,
                player.playing,
                idle = !local.graphSelected && !local.reframing,
                Modifier.align(Alignment.Center),
            )
            PictureKeys(ui, video, local, actions, chrome, keysAlpha = { keysAlpha })
        }
    }
}

/**
 * The video in its frame. Tap to play; tap again to put down whatever is open. While reframing, drag and pinch move the
 * picture instead. The reframed picture is drawn inside the haze source, so a blur behind the graph blurs what the
 * export will show.
 */
@Composable
private fun VideoLayer(
    ui: EditUiState,
    video: VideoItem,
    reframing: Boolean,
    haze: HazeState,
    actions: EditActions,
    onTap: () -> Unit,
    picture: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetOpen by rememberUpdatedState(ui.sheet != null)
    val tap by rememberUpdatedState(onTap)
    val reframe by rememberUpdatedState(video.reframe)
    Box(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .hazeSource(haze)
            .pointerInput(Unit) { detectTapGestures { if (sheetOpen) actions.closeSheet() else tap() } }
            .then(
                if (reframing) Modifier.reframeGestures({ reframe }) { push, change -> actions.reframe(push, change) }
                else Modifier
            )
    ) {
        // The whole picture around the frame, moved and zoomed by the reframe, as the exporters draw it.
        Box(Modifier.fillMaxSize().reframed(video.reframe)) { picture(Modifier.coverOf(ui.sourceAspect)) }
    }
}

/** The graph over the picture, and what moves and selects it; it steps aside while the picture is framed. */
@Composable
private fun GraphLayer(
    ui: EditUiState,
    video: VideoItem,
    player: PlayerState,
    local: EditLocalState,
    actions: EditActions,
    toolbar: Boolean,
    haze: HazeState,
    onMoving: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        !local.reframing,
        modifier.fillMaxSize(),
        enter = fadeIn(CgMotion.medium()),
        exit = fadeOut(CgMotion.short()),
    ) {
        val shot = video.shot ?: return@AnimatedVisibility
        val style = ui.style ?: return@AnimatedVisibility
        ShotOverlay(
            shot = shot,
            videoTimeUs = { player.positionUs },
            shotStartUs = video.shotStartInVideoUs ?: 0L,
            settings = video.overlay,
            style = style,
            title = overlayTitleText(shot, video.overlay),
            recipe = video.overlay.recipe,
            fonts = CgTheme.overlayFonts,
            modifier = Modifier.fillMaxSize(),
            hazeState = haze,
            backdropLuma = { ui.luma?.at(player.positionUs) },
        )
        OverlayEditLayer(
            rect = video.overlay.rect,
            format = video.format,
            onChange = { rect, push -> actions.overlay(push) { it.copy(rect = rect) } },
            onTap = { local.graphSelected = !local.graphSelected },
            selected = local.graphSelected,
            toolbar =
                if (!toolbar) null
                else {
                    {
                        GraphToolbar(
                            opacity = video.overlay.opacity,
                            onStyle = { actions.openSheet(EditSheet.STYLE) },
                            onData = { actions.openSheet(EditSheet.DATA) },
                            onOpacity = { o -> actions.overlay { it.withOpacity(o) } },
                        )
                    }
                },
            onDragging = onMoving,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * The keys and words on the picture: what the editor is busy with, the formats and the hint while framing, and the
 * reframe switch. They fade by [keysAlpha] while the graph is moved, read in drawing only.
 */
@Composable
private fun BoxScope.PictureKeys(
    ui: EditUiState,
    video: VideoItem,
    local: EditLocalState,
    actions: EditActions,
    chrome: PictureChrome,
    keysAlpha: () -> Float,
) {
    var busy by remember { mutableStateOf(ui.busy) }
    if (ui.busy != null) busy = ui.busy
    AnimatedVisibility(
        ui.busy != null,
        Modifier.align(Alignment.TopStart).padding(CgSpace.S12).graphicsLayer { alpha = keysAlpha() },
        enter = fadeIn(CgMotion.medium()),
        exit = fadeOut(CgMotion.medium()),
    ) {
        busy?.let { StatusChip(it.text(), recording = false) }
    }
    if (chrome.formatStrip) {
        AnimatedVisibility(
            local.reframing,
            Modifier.align(Alignment.TopCenter).padding(CgSpace.S12).clipToBounds(),
            enter = slideInVertically(CgMotion.offsetSpec) { -it },
            exit = slideOutVertically(CgMotion.offsetSpec) { -it },
        ) {
            FormatStrip(video.format, onPick = actions::setFormat)
        }
    }
    val hintOnTop = chrome.hint == HintPlace.TOP_START
    AnimatedVisibility(
        local.reframing,
        Modifier.align(if (hintOnTop) Alignment.TopStart else Alignment.BottomCenter)
            .padding(CgSpace.S8)
            .clipToBounds(),
        enter = slideInVertically(CgMotion.offsetSpec) { if (hintOnTop) -it else it },
        exit = slideOutVertically(CgMotion.offsetSpec) { if (hintOnTop) -it else it },
    ) {
        StatusChip(stringResource(Res.string.edit_reframe_hint), recording = false)
    }
    // Top right, so it never covers the graph's toolbar; while framing, "done" is under the picture instead.
    if (chrome.frameSwitch && !local.reframing) {
        FrameToggle(
            reframing = false,
            onReframing = local::reframe,
            offLabel = Res.string.edit_frame_switch,
            icon = true,
            overVideo = true,
            modifier =
                Modifier.align(Alignment.TopEnd)
                    .padding(horizontal = CgSpace.S8)
                    .graphicsLayer { alpha = keysAlpha() }
                    .testTag(TestTags.EditFrame),
        )
    }
}

/**
 * The video's shape, while framing: the four formats as one strip on an ink ground over the picture, like the camera's
 * lenses, the current one filled bone.
 */
@Composable
private fun FormatStrip(format: VideoFormat, onPick: (VideoFormat) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(CgColors.Surface.Base.copy(alpha = OVER_VIDEO))
            .border(CgStroke.Hairline, CgColors.Line.Hairline)
            .padding(CgStroke.Hairline),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (f in VideoFormat.entries) {
            val on = f == format
            Box(
                Modifier.testTag(TestTags.format(f.label))
                    .heightIn(min = CgSize.Touch)
                    .widthIn(min = FORMAT_KEY_MIN_WIDTH)
                    .background(if (on) CgColors.Brand.Bone else Color.Transparent)
                    .selectable(on, role = Role.RadioButton, onClick = { onPick(f) })
                    .padding(horizontal = CgSpace.S12),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    f.label,
                    style = CgTheme.type.labelStrong,
                    color = if (on) CgColors.Text.OnLight else CgColors.Text.Primary,
                )
            }
        }
    }
}

private val FORMAT_KEY_MIN_WIDTH = 64.dp

/** The frame being framed: a signal hairline round it and bone hairlines on its thirds. */
@Composable
private fun FrameGuides(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = CgStroke.Hairline.toPx()
        val thirds = CgColors.Text.Primary.copy(alpha = 0.25f)
        for (i in 1..2) {
            drawRect(thirds, Offset(size.width * i / 3f - w / 2, 0f), Size(w, size.height))
            drawRect(thirds, Offset(0f, size.height * i / 3f - w / 2), Size(size.width, w))
        }
        drawRect(CgColors.Brand.Signal, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), style = Stroke(w))
    }
}

/**
 * The play or pause key in the middle of the picture, shown only when a tap on the picture plays or pauses it: it holds
 * a moment and then cuts out, so the tap is seen to. Never while something else is being worked on ([idle] false: the
 * graph picked, the picture framed).
 */
@Composable
private fun PlayMark(taps: Int, playing: Boolean, idle: Boolean, modifier: Modifier = Modifier) {
    val shown = remember { Animatable(0f) }
    LaunchedEffect(taps) {
        if (taps > 0 && idle) {
            shown.snapTo(1f)
            shown.animateTo(0f, tween(CgMotion.SHORT, delayMillis = PLAY_MARK_HOLD_MS, easing = CgMotion.Linear))
        } else shown.snapTo(0f)
    }
    Box(
        modifier
            .size(PLAY_MARK_SIZE)
            .graphicsLayer { alpha = shown.value }
            .background(CgColors.Surface.Base.copy(alpha = 0.6f))
            .border(CgStroke.Hairline, CgColors.Brand.Bone),
        contentAlignment = Alignment.Center,
    ) {
        CgIcon(if (playing) CgIcons.Play else CgIcons.Pause, size = PLAY_MARK_ICON)
    }
}

private const val PLAY_MARK_HOLD_MS = 300
private val PLAY_MARK_SIZE = 56.dp
private val PLAY_MARK_ICON = 24.dp
