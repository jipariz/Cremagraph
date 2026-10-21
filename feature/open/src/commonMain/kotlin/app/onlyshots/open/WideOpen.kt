package app.onlyshots.open

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.onlyshots.PRIVACY_URL
import app.onlyshots.SharedKeys
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgMotion
import app.onlyshots.core.designsystem.CgSize
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.component.OnlyShotsMark
import app.onlyshots.core.model.VideoItem
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.UiText
import app.onlyshots.core.resources.app_name
import app.onlyshots.core.resources.illus_shot_tile
import app.onlyshots.core.resources.illus_video_tile
import app.onlyshots.core.resources.open_change
import app.onlyshots.core.resources.open_details_does
import app.onlyshots.core.resources.open_details_does_title
import app.onlyshots.core.resources.open_details_shots
import app.onlyshots.core.resources.open_details_shots_machine
import app.onlyshots.core.resources.open_details_shots_title
import app.onlyshots.core.resources.open_details_videos
import app.onlyshots.core.resources.open_details_videos_title
import app.onlyshots.core.resources.open_drop_to_use
import app.onlyshots.core.resources.open_editor
import app.onlyshots.core.resources.open_hint_add_shot
import app.onlyshots.core.resources.open_hint_add_video
import app.onlyshots.core.resources.open_hint_both
import app.onlyshots.core.resources.open_hint_ready
import app.onlyshots.core.resources.open_preview_caption
import app.onlyshots.core.resources.open_privacy
import app.onlyshots.core.resources.open_recorded
import app.onlyshots.core.resources.open_shot_weight_in
import app.onlyshots.core.resources.open_video
import app.onlyshots.core.resources.open_wide_choose_shot
import app.onlyshots.core.resources.open_wide_choose_video
import app.onlyshots.core.resources.open_wide_intro
import app.onlyshots.core.resources.open_wide_shot_detail
import app.onlyshots.core.resources.open_wide_shot_detail_machine
import app.onlyshots.core.resources.open_wide_shot_title
import app.onlyshots.core.resources.open_wide_video_drop
import app.onlyshots.core.resources.open_wide_video_title
import app.onlyshots.core.resources.start_title
import app.onlyshots.core.resources.text
import app.onlyshots.core.resources.unit_bar
import app.onlyshots.core.resources.unit_grams
import app.onlyshots.core.resources.unit_ml_per_s
import app.onlyshots.core.resources.unit_seconds
import app.onlyshots.machineLabel
import app.onlyshots.shareBounds
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** What the desktop or the web says about itself on its start screen. */
data class OpenHost(
    /** In the header, beside the shield: where it runs. */
    val headerNote: String,
    /** The "videos" column: what comes out, and where. */
    val videosNote: String,
)

/**
 * The desktop's and the web's start, from the "OnlyShots Open Screen" design (artboard "Web & desktop"): a header with
 * where it runs, the title and the two steps beside a preview of what comes out, "open editor", and what it does, reads
 * and makes underneath. Narrow, the preview goes under the steps and the three notes stack.
 */
@Composable
internal fun WideOpenBody(
    picked: VideoItem?,
    pickedFile: PickedFile?,
    shot: ChosenShot?,
    status: UiText?,
    onPickVideo: () -> Unit,
    onPickShot: () -> Unit,
    onOpenEditor: () -> Unit,
    modifier: Modifier,
    dragging: Boolean,
    fromMachine: Boolean,
    host: OpenHost?,
    stores: StoreLinks?,
    onPrivacy: (() -> Unit)?,
) {
    BoxWithConstraints(modifier.fillMaxSize().background(CgColors.Surface.Base).safeDrawingPadding()) {
        // A phone's browser gets the design's own compact page, with the app's banner.
        if (stores?.phone == true && maxWidth < 600.dp) {
            MobileOpenBody(
                picked,
                pickedFile,
                shot,
                status,
                onPickVideo,
                onPickShot,
                onOpenEditor,
                stores,
                host?.videosNote,
            )
            return@BoxWithConstraints
        }
        val w = maxWidth.value
        val gutter = (w * 0.04f).coerceIn(16f, 48f).dp
        val wide = maxWidth >= 900.dp
        val detailColumns = maxWidth >= 800.dp
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Header(host?.headerNote, gutter, narrow = !detailColumns, onPrivacy = onPrivacy)
            Column(
                Modifier.fillMaxWidth()
                    .wrapContentHeight()
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = 1240.dp)
                    .padding(start = gutter, end = gutter, top = (w * 0.05f).coerceIn(28f, 64f).dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(56.dp),
            ) {
                val steps =
                    @Composable { mod: Modifier ->
                        Steps(
                            picked,
                            pickedFile,
                            shot,
                            status,
                            onPickVideo,
                            onPickShot,
                            onOpenEditor,
                            dragging,
                            fromMachine,
                            titleSize = (w * 0.042f).coerceIn(34f, 52f),
                            modifier = mod,
                        )
                    }
                if (wide) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy((w * 0.05f).coerceIn(32f, 80f).dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        steps(Modifier.weight(1f))
                        Preview(Modifier.widthIn(min = 300.dp))
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        steps(Modifier.fillMaxWidth())
                        Preview(Modifier)
                    }
                }
                // The web offers the phone app: it films the shot live, which the page cannot.
                if (stores?.any == true) AppBand(stores, wide = detailColumns)
                Details(fromMachine, host?.videosNote, columns = detailColumns)
            }
        }
    }
}

/** The name, where it runs, and the privacy link; narrow, the last two go under the name, as the design wraps. */
@Composable
private fun Header(note: String?, gutter: Dp, narrow: Boolean, onPrivacy: (() -> Unit)?) {
    val uri = LocalUriHandler.current
    val brand =
        @Composable {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OnlyShotsMark(splashTarget = true)
                Text(
                    stringResource(Res.string.app_name),
                    style = CgTheme.type.body.copy(fontSize = 22.sp, fontWeight = FontWeight.Normal),
                    color = CgColors.Brand.Cream,
                )
            }
        }
    val meta =
        @Composable {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                if (note != null) {
                    Row(
                        Modifier.weight(1f, fill = false),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Shield()
                        Text(note, style = CgTheme.type.body.copy(fontSize = 14.sp), color = CgColors.Text.Secondary)
                    }
                }
                Text(
                    stringResource(Res.string.open_privacy),
                    style = CgTheme.type.body.copy(fontSize = 14.sp, textDecoration = TextDecoration.Underline),
                    color = CgColors.Text.Secondary,
                    modifier =
                        Modifier.heightIn(min = CgSize.Touch).wrapContentHeight().clickable(role = Role.Button) {
                            onPrivacy?.invoke() ?: uri.openUri(PRIVACY_URL)
                        },
                )
            }
        }
    Column {
        if (narrow) {
            Column(Modifier.fillMaxWidth().padding(horizontal = gutter, vertical = 14.dp)) {
                brand()
                meta()
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = gutter, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                brand()
                meta()
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(CgColors.Line.Divider))
    }
}

/** The title, the two steps, and "open editor" with what it is waiting for. */
@Composable
private fun Steps(
    picked: VideoItem?,
    pickedFile: PickedFile?,
    shot: ChosenShot?,
    status: UiText?,
    onPickVideo: () -> Unit,
    onPickShot: () -> Unit,
    onOpenEditor: () -> Unit,
    dragging: Boolean,
    fromMachine: Boolean,
    titleSize: Float,
    modifier: Modifier,
) {
    val opening = status?.takeIf { it == Opening }?.text()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(28.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                stringResource(Res.string.start_title),
                style =
                    CgTheme.type.body.copy(
                        fontSize = titleSize.sp,
                        lineHeight = (titleSize * 1.08f).sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = (-0.01).em,
                    ),
                color = CgColors.Text.Primary,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(Res.string.open_wide_intro),
                style = CgTheme.type.body.copy(fontSize = 18.sp, lineHeight = 28.sp),
                color = CgColors.Text.Secondary,
                modifier = Modifier.widthIn(max = 560.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StepSwap(picked != null) { done ->
                if (!done || picked == null) {
                    EmptyStep(
                        number = 1,
                        title = stringResource(Res.string.open_wide_video_title),
                        detail =
                            opening
                                ?: stringResource(
                                    if (dragging) Res.string.open_drop_to_use else Res.string.open_wide_video_drop
                                ),
                        action = stringResource(Res.string.open_wide_choose_video),
                        next = true,
                        highlighted = dragging,
                        onClick = onPickVideo,
                    )
                } else {
                    PickedStep(
                        name = pickedFile?.name ?: stringResource(Res.string.open_video),
                        detail = videoLine(picked, pickedFile),
                        tile = { VideoTile(pickedFile?.thumb, picked.id) },
                        onChange = onPickVideo,
                        highlighted = dragging,
                    )
                }
            }
            StepSwap(shot != null) { done ->
                if (!done || shot == null) {
                    EmptyStep(
                        number = 2,
                        title = stringResource(Res.string.open_wide_shot_title),
                        detail =
                            stringResource(
                                if (fromMachine) Res.string.open_wide_shot_detail_machine
                                else Res.string.open_wide_shot_detail
                            ),
                        action = stringResource(Res.string.open_wide_choose_shot),
                        next = picked != null,
                        highlighted = false,
                        onClick = onPickShot,
                    )
                } else {
                    PickedStep(
                        name = shot.label,
                        detail = shotLine(shot),
                        tile = { ShotTile() },
                        onChange = onPickShot,
                        highlighted = false,
                    )
                }
            }
            status
                ?.takeIf { it != Opening }
                ?.let { Text(it.text(), style = CgTheme.type.body, color = CgColors.Status.Highlight) }
        }
        val ready = picked != null && shot != null
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        animateColorAsState(if (ready) CgColors.Brand.Orange else CgColors.Surface.Raised).value
                    )
                    .clickable(enabled = ready, role = Role.Button, onClick = onOpenEditor)
                    .padding(horizontal = 36.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val fg by animateColorAsState(if (ready) CgColors.Text.OnLight else CgColors.Text.Dim)
                Text(
                    stringResource(Res.string.open_editor),
                    style = CgTheme.type.body.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                    color = fg,
                )
                Arrow(fg)
            }
            Text(
                stringResource(ctaHint(picked != null, shot != null)),
                style = CgTheme.type.body.copy(fontSize = 14.sp),
                color = CgColors.Text.Dim,
            )
        }
    }
}

/** A step not done yet: a dashed card, its number, what goes in it, and the button that picks it. */
@Composable
private fun EmptyStep(
    number: Int,
    title: String,
    detail: String,
    action: String,
    next: Boolean,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    val radius = 16.dp
    val dash = if (next && number == 2) CgColors.Brand.Orange else CgColors.Text.Dim
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(radius))
            .background(if (highlighted) CgColors.Surface.Raised else CgColors.Surface.Bar)
            .drawBehind { dashedBorder(if (highlighted) CgColors.Text.Primary else dash, radius.toPx()) }
            .padding(horizontal = 18.dp, vertical = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(CgColors.Surface.Raised), Alignment.Center) {
            Text("$number", style = CgTheme.type.body.copy(fontSize = 17.sp), color = CgColors.Text.Primary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = CgTheme.type.body.copy(fontSize = 18.sp), color = CgColors.Text.Primary)
            Text(detail, style = CgTheme.type.body.copy(fontSize = 14.sp), color = CgColors.Text.Dim)
        }
        PillButton(action, light = next, onClick = onClick)
    }
}

/** A step done: what was picked, with a small picture of it, and "change". */
@Composable
internal fun PickedStep(
    name: String,
    detail: String,
    tile: @Composable () -> Unit,
    onChange: () -> Unit,
    highlighted: Boolean,
) {
    val card = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth()
            .clip(card)
            .background(if (highlighted) CgColors.Surface.Raised else CgColors.Surface.Bar)
            .border(1.dp, if (highlighted) CgColors.Text.Primary else CgColors.Line.Hairline, card)
            .padding(start = 14.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tile()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                name,
                style = CgTheme.type.body.copy(fontSize = 17.sp),
                color = CgColors.Text.Primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(detail, style = CgTheme.type.body.copy(fontSize = 14.sp), color = CgColors.Text.Dim)
        }
        PillButton(stringResource(Res.string.open_change), light = false, outlined = true, onClick = onChange)
    }
}

@Composable
internal fun PillButton(text: String, light: Boolean, onClick: () -> Unit, outlined: Boolean = false) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier.heightIn(min = 44.dp)
            .clip(shape)
            .background(
                when {
                    outlined -> Color.Transparent
                    light -> CgColors.Brand.Cream
                    else -> CgColors.Surface.Raised
                }
            )
            .then(if (outlined) Modifier.border(1.dp, CgColors.Line.Hairline, shape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = if (outlined) 16.dp else 20.dp),
        Alignment.Center,
    ) {
        Text(
            text,
            style =
                CgTheme.type.body.copy(
                    fontSize = 15.sp,
                    fontWeight = if (outlined) FontWeight.Normal else FontWeight.SemiBold,
                ),
            color = if (light && !outlined) CgColors.Text.OnLight else CgColors.Brand.Cream,
        )
    }
}

/** What comes out: a phone-shaped frame with the cup under the spout and the graph over it. */
@Composable
private fun Preview(modifier: Modifier) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(252.dp, 448.dp)
                .clip(RoundedCornerShape(22.dp))
                .border(1.dp, CgColors.Line.Hairline, RoundedCornerShape(22.dp))
        ) {
            Canvas(Modifier.fillMaxSize()) { scene() }
            Column(
                Modifier.align(Alignment.BottomCenter)
                    .padding(12.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CgColors.Surface.Base.copy(alpha = 0.72f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    val small = CgTheme.type.body.copy(fontSize = 12.sp)
                    Text("9.0 ${stringResource(Res.string.unit_bar)}", style = small, color = CgColors.Series.Pressure)
                    Text("2.1 ${stringResource(Res.string.unit_ml_per_s)}", style = small, color = CgColors.Series.Flow)
                    Text("36.4 ${stringResource(Res.string.unit_grams)}", style = small, color = CgColors.Text.Primary)
                    Text("0:28", style = small, color = CgColors.Text.Secondary)
                }
                Canvas(Modifier.fillMaxWidth().height(80.dp)) { graph() }
            }
        }
        Text(
            stringResource(Res.string.open_preview_caption),
            style = CgTheme.type.body.copy(fontSize = 13.sp),
            color = CgColors.Text.Dim,
        )
    }
}

/** What it does, which shots it reads, which videos it takes. */
@Composable
private fun Details(fromMachine: Boolean, videosNote: String?, columns: Boolean) {
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
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(CgColors.Line.Divider))
        Spacer(Modifier.height(32.dp))
        val note =
            @Composable { title: String, text: String, mod: Modifier ->
                Column(mod, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        title.uppercase(),
                        style =
                            CgTheme.type.body.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.06.em,
                            ),
                        color = CgColors.Text.Secondary,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text,
                        style = CgTheme.type.body.copy(fontSize = 15.sp, lineHeight = 24.sp),
                        color = CgColors.Text.Secondary,
                    )
                }
            }
        if (columns) {
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                for ((title, text) in notes) note(title, text, Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                for ((title, text) in notes) note(title, text, Modifier)
            }
        }
    }
}

// Small drawings from the design -----------------------------------------------------------------

/**
 * The video's tile: a frame of it, cropped to fill; until that is decoded (or where it cannot be), the design's drawing
 * of a cup under the spout with the graph's strip along the bottom.
 */
@Composable
internal fun VideoTile(thumb: ImageBitmap? = null, videoId: String? = null) {
    val shape = RoundedCornerShape(10.dp)
    // The editor's picture grows out of this tile when the editor opens on the video.
    val shared = if (videoId != null) Modifier.shareBounds(SharedKeys.video(videoId), clip = shape) else Modifier
    // The frame fades in over the drawing once it is decoded.
    Crossfade(thumb, shared.size(54.dp, 72.dp).clip(shape), CgMotion.medium(), label = "thumb") { frame ->
        if (frame != null) {
            Image(frame, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Image(
                painterResource(Res.drawable.illus_video_tile),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** The shot's tile: two curves, pressure and flow. */
@Composable
internal fun ShotTile() {
    Box(Modifier.size(54.dp).clip(RoundedCornerShape(10.dp)).background(CgColors.Surface.Raised), Alignment.Center) {
        Image(painterResource(Res.drawable.illus_shot_tile), contentDescription = null, Modifier.size(40.dp, 28.dp))
    }
}

@Composable
internal fun Arrow(color: Color) {
    Canvas(Modifier.size(18.dp)) {
        val s = size.width / 18f
        val stroke = Stroke(1.8f * s, cap = StrokeCap.Round)
        drawLine(color, Offset(3.5f * s, 9 * s), Offset(14.5f * s, 9 * s), 1.8f * s, StrokeCap.Round)
        drawPath(
            Path().apply {
                moveTo(10 * s, 4.5f * s)
                lineTo(14.5f * s, 9 * s)
                lineTo(10 * s, 13.5f * s)
            },
            color,
            style = stroke,
        )
    }
}

/** "Nothing is uploaded": a shield with a green tick. */
@Composable
private fun Shield() {
    Canvas(Modifier.size(16.dp)) {
        val s = size.width / 16f
        drawPath(
            Path().apply {
                moveTo(8 * s, 1.5f * s)
                lineTo(2.5f * s, 3.5f * s)
                lineTo(2.5f * s, 7.5f * s)
                cubicTo(2.5f * s, 10.7f * s, 4.8f * s, 13.4f * s, 8 * s, 14.5f * s)
                cubicTo(11.2f * s, 13.4f * s, 13.5f * s, 10.7f * s, 13.5f * s, 7.5f * s)
                lineTo(13.5f * s, 3.5f * s)
                close()
            },
            CgColors.Text.Secondary,
            style = Stroke(1.4f * s),
        )
        drawPath(
            Path().apply {
                moveTo(5.5f * s, 8 * s)
                lineTo(7.3f * s, 9.8f * s)
                lineTo(10.8f * s, 6.3f * s)
            },
            CgColors.Status.Live,
            style = Stroke(1.4f * s, cap = StrokeCap.Round),
        )
    }
}

/** The preview's scene, from the design's 252 × 448 drawing. */
private fun DrawScope.scene() {
    val s = size.width / 252f
    drawRect(Color(0xFF231A17))
    drawRect(Color(0xFF1A1412), size = Size(size.width, 120 * s))
    drawRoundRect(Color(0xFF3A2F2B), Offset(96 * s, 40 * s), Size(60 * s, 70 * s), CornerRadius(6 * s))
    drawLine(Color(0xFF8A5A3C), Offset(124 * s, 110 * s), Offset(124 * s, 230 * s), 5 * s, StrokeCap.Round)
    drawLine(Color(0xFF6E4630), Offset(131 * s, 110 * s), Offset(131 * s, 206 * s), 3 * s, StrokeCap.Round)
    drawPath(
        Path().apply {
            moveTo(78 * s, 228 * s)
            lineTo(174 * s, 228 * s)
            lineTo(166 * s, 294 * s)
            quadraticTo(164 * s, 306 * s, 152 * s, 306 * s)
            lineTo(100 * s, 306 * s)
            quadraticTo(88 * s, 306 * s, 86 * s, 294 * s)
            close()
        },
        Color(0xFF4A3A33),
    )
    drawArc(
        Color(0xFF4A3A33),
        startAngle = -90f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(172 * s, 244 * s),
        size = Size(24 * s, 24 * s),
        style = Stroke(7 * s),
    )
    drawOval(Color(0xFF6E4630), Offset(80 * s, 225 * s), Size(92 * s, 14 * s))
    drawRoundRect(Color(0xFF2E2421), Offset(40 * s, 316 * s), Size(172 * s, 10 * s), CornerRadius(5 * s))
}

/** The preview's graph: a faint grid, pressure, flow and weight. */
private fun DrawScope.graph() {
    val sx = size.width / 200f
    val sy = size.height / 80f
    for (y in listOf(20f, 40f, 60f)) {
        drawLine(Color.White.copy(alpha = 0.08f), Offset(0f, y * sy), Offset(size.width, y * sy), 1f)
    }
    drawPath(
        Path().apply {
            moveTo(0f, 78 * sy)
            cubicTo(20 * sx, 78 * sy, 25 * sx, 16 * sy, 45 * sx, 14 * sy)
            lineTo(130 * sx, 14 * sy)
            cubicTo(150 * sx, 14 * sy, 165 * sx, 34 * sy, 200 * sx, 42 * sy)
        },
        CgColors.Series.Pressure,
        style = Stroke(2.dp.toPx()),
    )
    drawPath(
        Path().apply {
            moveTo(0f, 79 * sy)
            cubicTo(30 * sx, 79 * sy, 40 * sx, 53 * sy, 60 * sx, 48 * sy)
            cubicTo(80 * sx, 43 * sy, 120 * sx, 36 * sy, 200 * sx, 34 * sy)
        },
        CgColors.Series.Flow,
        style = Stroke(2.dp.toPx()),
    )
    drawPath(
        Path().apply {
            moveTo(0f, 79 * sy)
            lineTo(40 * sx, 79 * sy)
            cubicTo(80 * sx, 77 * sy, 120 * sx, 41 * sy, 200 * sx, 13 * sy)
        },
        CgColors.Series.Weight,
        style = Stroke(2.dp.toPx()),
    )
}

/** A dashed 1.5 dp border inside the bounds, rounded by [radius] px. */
internal fun DrawScope.dashedBorder(color: Color, radius: Float) {
    val w = 1.5.dp.toPx()
    drawRoundRect(
        color,
        topLeft = Offset(w / 2, w / 2),
        size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(radius - w / 2),
        style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
    )
}

/** "0:42 · 1080 × 1920 · recorded 2026-10-20 08:14". */
@Composable
internal fun videoLine(item: VideoItem, file: PickedFile?): String =
    listOfNotNull(
            length(item.durationUs),
            file?.takeIf { it.widthPx > 0 && it.heightPx > 0 }?.let { "${it.widthPx} × ${it.heightPx}" },
            item.recordedAt?.let { stringResource(Res.string.open_recorded, it.toString().take(16).replace('T', ' ')) },
        )
        .joinToString(" · ")

/** "Gaggiuino · 36.4 g in 28 s · 2026-10-20 08:14". */
@Composable
internal fun shotLine(chosen: ChosenShot): String {
    val shot = chosen.shot
    val time = "${shot.durationUs / 1_000_000} ${stringResource(Res.string.unit_seconds)}"
    val grams = stringResource(Res.string.unit_grams)
    return listOf(
            machineLabel(shot.source),
            shot.finalWeight?.let {
                stringResource(Res.string.open_shot_weight_in, "${(it * 10).roundToInt() / 10f} $grams", time)
            } ?: time,
            shot.startedAt.toString().take(16).replace('T', ' '),
        )
        .joinToString(" · ")
}

private fun length(us: Long): String {
    val seconds = us / 1_000_000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

/** What "open editor" is waiting for, said beside it. */
internal fun ctaHint(video: Boolean, shot: Boolean): StringResource =
    when {
        video && shot -> Res.string.open_hint_ready
        video -> Res.string.open_hint_add_shot
        shot -> Res.string.open_hint_add_video
        else -> Res.string.open_hint_both
    }

/** An empty step and the same step picked: the one fades into the other, the column easing to its new height. */
@Composable
internal fun StepSwap(done: Boolean, content: @Composable (done: Boolean) -> Unit) {
    AnimatedContent(
        done,
        transitionSpec = {
            (fadeIn(CgMotion.medium()) + scaleIn(CgMotion.springy(), initialScale = 0.96f)) togetherWith
                fadeOut(CgMotion.short()) using
                SizeTransform { _, _ -> CgMotion.sizeSpec }
        },
        label = "step",
    ) {
        content(it)
    }
}
