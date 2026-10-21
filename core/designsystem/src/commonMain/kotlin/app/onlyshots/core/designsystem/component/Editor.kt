package app.onlyshots.core.designsystem.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.onlyshots.TestTags
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgIcons
import app.onlyshots.core.designsystem.CgMotion
import app.onlyshots.core.designsystem.CgShape
import app.onlyshots.core.designsystem.CgSize
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgStroke
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.caps
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.core_cancel
import app.onlyshots.core.resources.core_choose_file
import app.onlyshots.core.resources.core_colour_chip
import app.onlyshots.core.resources.core_done
import app.onlyshots.core.resources.core_drop_formats
import app.onlyshots.core.resources.core_drop_to_open
import app.onlyshots.core.resources.core_drop_video
import app.onlyshots.core.resources.core_export_estimating
import app.onlyshots.core.resources.core_export_keep_editing
import app.onlyshots.core.resources.core_export_left
import app.onlyshots.core.resources.core_export_making
import app.onlyshots.core.resources.core_export_more
import app.onlyshots.testTagsAsResourceIds
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/*
 * The editor's controls in the Instrument look: square keys with hairline edges, Mono capitals, hairline grids between
 * cells, a bottom sheet without a scrim (so the picture stays in view while it is open) topped by the signal rule. Keys
 * are 40 dp to look at and 44 dp to touch.
 */

/** The small keys' letters: Mono SemiBold 12, set in capitals. */
private val KeyStyle
    @Composable get() = CgTheme.type.labelStrong.copy(letterSpacing = 0.02.em)

/** A chip in the editor's top bar: a name, an optional status dot, and a caret for the sheet it opens. */
@Composable
fun TopChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dot: Color? = null,
    primary: Boolean = false,
    caret: Boolean = true,
) {
    Row(
        modifier
            .heightIn(min = CgSize.Touch)
            .background(if (primary) CgColors.Brand.Signal else Color.Transparent)
            .border(CgStroke.Hairline, if (primary) CgColors.Brand.Signal else CgColors.Line.Hairline)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = CgSpace.S12),
        horizontalArrangement = Arrangement.spacedBy(CgSpace.S8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) Box(Modifier.size(6.dp).background(dot))
        Text(
            text.caps(),
            style = KeyStyle,
            color = if (primary) CgColors.Text.OnLight else CgColors.Text.Primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 180.dp),
        )
        if (caret) {
            Text(
                "▾",
                style = CgTheme.type.caption,
                color = if (primary) CgColors.Text.OnLight else CgColors.Text.Secondary,
            )
        }
    }
}

/**
 * The wide editor's main action: [text] runs it at once ([onClick]); when there is a second way to do it, the ▾ half
 * opens [menu] (its items close it with the function they are handed). The signal, like the phone's export chip.
 */
@Composable
fun SplitButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    menu: (@Composable (close: () -> Unit) -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    val moreWays = stringResource(Res.string.core_export_more)
    Row(
        modifier.height(IntrinsicSize.Min).heightIn(min = CgSize.Touch).background(CgColors.Brand.Signal),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.fillMaxHeight()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(start = CgSpace.S16, end = if (menu == null) CgSpace.S16 else CgSpace.S12),
            Alignment.Center,
        ) {
            Text(text.caps(), style = KeyStyle, color = CgColors.Text.OnLight, maxLines = 1)
        }
        if (menu != null) {
            Box(Modifier.width(CgStroke.Hairline).fillMaxHeight().background(CgColors.Text.OnLight.copy(alpha = 0.35f)))
            Box(Modifier.fillMaxHeight()) {
                Box(
                    Modifier.fillMaxHeight()
                        .clickable(role = Role.DropdownList) { open = true }
                        .semantics { contentDescription = moreWays }
                        .padding(horizontal = CgSpace.S12),
                    Alignment.Center,
                ) {
                    Text("▾", style = KeyStyle, color = CgColors.Text.OnLight)
                }
                DropdownMenu(
                    expanded = open,
                    onDismissRequest = { open = false },
                    modifier = Modifier.testTagsAsResourceIds(),
                    shape = CgShape.Chip,
                    containerColor = CgColors.Surface.Bar,
                    border = BorderStroke(CgStroke.Hairline, CgColors.Line.Hairline),
                ) {
                    menu { open = false }
                }
            }
        }
    }
}

/**
 * A menu row in the design's type, for [SplitButton] and the editor's other menus: Mono 13 capitals over a divider,
 * with a sentence under it when there is a [detail].
 */
@Composable
fun CgMenuItem(text: String, detail: String? = null, onClick: () -> Unit) {
    val divider = CgColors.Line.Divider
    DropdownMenuItem(
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text.caps(), style = CgTheme.type.label.copy(fontSize = 13.sp, letterSpacing = 0.02.em))
                if (detail != null) Text(detail, style = CgTheme.type.caption, color = CgColors.Text.Secondary)
            }
        },
        onClick = onClick,
        modifier =
            Modifier.drawBehind {
                val h = CgStroke.Hairline.toPx()
                drawRect(divider, Offset(0f, size.height - h), Size(size.width, h))
            },
    )
}

/**
 * The wide editor's export, over everything: the window dimmed and closed to clicks, and a card in the middle with a
 * [still] of the video, the [progress] (0..1) and the time [left] while it renders, or [done] (where the video went)
 * once it finished. [detail] says what is being made; [footer] what to keep in mind while it runs.
 */
@Composable
fun ExportOverlay(
    detail: String,
    footer: String,
    progress: Float?,
    left: String?,
    done: String?,
    still: ImageBitmap?,
    onCancel: () -> Unit,
    onKeepEditing: () -> Unit,
    next: String,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    /** The video's width over height, for the still. */
    stillRatio: Float = 9f / 16f,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(CgColors.Surface.Scrim.copy(alpha = 0.85f))
            // Nothing behind it takes a click or a drag while the video is made.
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown().consume() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 440.dp)
                .fillMaxWidth()
                .padding(CgSpace.S24)
                .background(CgColors.Surface.Bar)
                .border(CgStroke.Hairline, CgColors.Line.Hairline)
                .signalRule()
                .padding(CgSpace.S24),
            verticalArrangement = Arrangement.spacedBy(CgSpace.S16),
        ) {
            still?.let {
                Image(
                    it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier.align(Alignment.CenterHorizontally)
                            .height(if (stillRatio > 1f) 120.dp else 180.dp)
                            .aspectRatio(stillRatio)
                            .border(
                                CgStroke.Hairline,
                                if (done != null) CgColors.Status.Success else CgColors.Line.Hairline,
                            ),
                )
            }
            // Making → made: the progress gives way to where the video went, and the card eases to its new height.
            AnimatedContent(
                done != null,
                transitionSpec = {
                    (fadeIn(CgMotion.medium()) + scaleIn(CgMotion.springy(), initialScale = 0.92f)) togetherWith
                        fadeOut(CgMotion.short()) using
                        SizeTransform { _, _ -> CgMotion.sizeSpec }
                },
                label = "export",
            ) { finished ->
                Column(verticalArrangement = Arrangement.spacedBy(CgSpace.S12)) {
                    if (finished) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(28.dp).background(CgColors.Status.Success), Alignment.Center) {
                                Text("✓", style = CgTheme.type.label, color = CgColors.Text.OnLight)
                            }
                            Text(done.orEmpty().caps(), style = CgTheme.type.screenTitle)
                        }
                        Text(detail, style = CgTheme.type.data, color = CgColors.Text.Secondary)
                        Column(
                            Modifier.fillMaxWidth().padding(top = CgSpace.S8),
                            verticalArrangement = Arrangement.spacedBy(CgSpace.S8),
                        ) {
                            CgButton(
                                stringResource(Res.string.core_export_keep_editing),
                                onClick = onKeepEditing,
                                kind = CgButtonKind.Secondary,
                                modifier = Modifier.fillMaxWidth().testTag(TestTags.ExportKeepEditing),
                            )
                            CgButton(
                                next,
                                onClick = onNext,
                                kind = CgButtonKind.Signal,
                                modifier = Modifier.fillMaxWidth().testTag(TestTags.ExportNext),
                            )
                        }
                    } else {
                        Text(stringResource(Res.string.core_export_making).caps(), style = CgTheme.type.pivot)
                        ProgressLine(progress ?: 0f)
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                "${((progress ?: 0f) * 100).toInt()} %",
                                style = CgTheme.type.label.copy(fontSize = 13.sp),
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                left?.let { stringResource(Res.string.core_export_left, it) }
                                    ?: stringResource(Res.string.core_export_estimating),
                                style = CgTheme.type.data.copy(fontSize = 13.sp),
                                color = CgColors.Text.Secondary,
                            )
                        }
                        Text(detail, style = CgTheme.type.data, color = CgColors.Text.Secondary)
                        Text(footer, style = CgTheme.type.bodySmall, color = CgColors.Text.Secondary)
                        CgButton(
                            stringResource(Res.string.core_cancel),
                            onClick = onCancel,
                            kind = CgButtonKind.Secondary,
                            modifier = Modifier.fillMaxWidth().padding(top = CgSpace.S8),
                        )
                    }
                }
            }
        }
    }
}

/** The 2 dp signal rule across the top of a sheet or a card that stands over the screen. */
fun Modifier.signalRule(): Modifier = drawWithContent {
    drawContent()
    drawRect(CgColors.Brand.Signal, Offset.Zero, Size(size.width, CgStroke.Rule.toPx()))
}

/**
 * The record screen's recording rules, read-only: how a recording starts ([start], [startDetail]) and how it stops, on
 * one line of a fixed [RulesStripHeight]. The settings themselves open from a separate button.
 *
 * Fixed, because the camera preview sits right above it: a strip that grew or shrank would resize the preview, and
 * resizing it rebinds the camera, which ends the recording in progress.
 */
@Composable
fun RulesStrip(start: String, startDetail: String?, stop: String, modifier: Modifier = Modifier) {
    StripFrame(modifier) {
        Box(Modifier.weight(1f)) {
            RuleLine(CgIcons.Play, CgColors.Text.Secondary, start + startDetail?.let { " · $it" }.orEmpty())
        }
        RuleLine(CgIcons.Stop, CgColors.Text.Secondary, stop)
    }
}

/** The same strip with one line of text in it, for when there are no rules to show. */
@Composable
fun RulesStripHint(text: String, modifier: Modifier = Modifier) {
    StripFrame(modifier) {
        Text(
            text.caps(),
            style = KeyStyle,
            color = CgColors.Text.Secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Height of [RulesStrip] and [RulesStripHint], divider included. */
val RulesStripHeight = 37.dp

@Composable
private fun StripFrame(modifier: Modifier, content: @Composable RowScope.() -> Unit) {
    Column(modifier.fillMaxWidth().height(RulesStripHeight)) {
        Row(
            Modifier.fillMaxWidth().weight(1f).semantics(mergeDescendants = true) {}.padding(horizontal = CgSpace.S16),
            horizontalArrangement = Arrangement.spacedBy(CgSpace.S12),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
        Box(Modifier.fillMaxWidth().height(CgStroke.Hairline).background(CgColors.Line.Hairline))
    }
}

/** A 44 dp square key with an icon and a hairline edge: for a bar, where [CgRoundButton] would be see-through. */
@Composable
fun IconCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .size(CgSize.Touch)
            .alpha(if (enabled) 1f else 0.4f)
            .background(CgColors.Surface.Base)
            .border(CgStroke.Hairline, CgColors.Line.Hairline)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        Alignment.Center,
    ) {
        Image(
            icon,
            contentDescription = null,
            colorFilter = ColorFilter.tint(CgColors.Text.Primary),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun RuleLine(icon: ImageVector, color: Color, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(CgSpace.S6), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(RULE_GLYPH))
        Text(
            text.caps(),
            style = KeyStyle,
            color = CgColors.Text.Secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val RULE_GLYPH = 12.dp

/** How opaque the ink ground of a control over the video is, as the camera's keys have it. */
const val OVER_VIDEO = 0.85f

/**
 * A small square key: the timeline's tools, the sheets' buttons. A hairline outline with Mono capitals; [light] is the
 * bone one that confirms.
 */
@Composable
fun Pill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = CgColors.Text.Primary,
    enabled: Boolean = true,
    light: Boolean = false,
    minWidth: Dp = 0.dp,
    /** Stretch the key itself across [modifier]'s width, for the wide cancel and confirm buttons of a sheet. */
    fill: Boolean = false,
    /** Drawn before the text, in the text's colour. */
    icon: ImageVector? = null,
    /** Over the picture: on an ink ground, so it reads over any frame of the video. */
    overVideo: Boolean = false,
) {
    val alpha by animateFloatAsState(if (enabled) 1f else 0.4f, CgMotion.short(), label = "alpha")
    val rest = if (overVideo) CgColors.Surface.Base.copy(alpha = OVER_VIDEO) else Color.Transparent
    val ground by animateColorAsState(if (light) CgColors.Brand.Bone else rest, CgMotion.short(), label = "bg")
    val edge = if (light) CgColors.Brand.Bone else CgColors.Line.Hairline
    val fg by animateColorAsState(if (light) CgColors.Text.OnLight else color, CgMotion.short(), label = "fg")
    Box(
        modifier
            .heightIn(min = CgSize.Touch)
            .graphicsLayer { this.alpha = alpha }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        Alignment.Center,
    ) {
        Box(
            Modifier.background(ground)
                .border(CgStroke.Hairline, edge)
                .heightIn(min = 40.dp)
                .widthIn(min = minWidth)
                .then(if (fill) Modifier.fillMaxWidth() else Modifier)
                .padding(horizontal = CgSpace.S12, vertical = CgSpace.S8),
            Alignment.Center,
        ) {
            if (icon == null) {
                Text(text.caps(), style = KeyStyle, color = fg)
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(CgSpace.S6),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        icon,
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(fg),
                        modifier = Modifier.size(14.dp),
                    )
                    Text(text.caps(), style = KeyStyle, color = fg)
                }
            }
        }
    }
}

/**
 * A row in a sheet that leads somewhere: what it is in Mono capitals, a sentence about it, and a chevron, or [trailing]
 * in its place (a choice that goes with the row, tapped on its own). A cell with a hairline edge.
 */
@Composable
fun ListRow(
    title: String,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
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
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title.caps(), style = CgTheme.type.label)
            Text(detail, style = CgTheme.type.caption, color = CgColors.Text.Secondary)
        }
        if (trailing != null) {
            trailing()
        } else {
            Text("›", style = CgTheme.type.cardTitle, color = CgColors.Text.Secondary)
        }
    }
}

/**
 * The toolbar that floats over a selected graph: its keys side by side in a frame of the signal, with hairlines between
 * them.
 */
@Composable
fun CardBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .height(IntrinsicSize.Min)
            .background(CgColors.Line.Hairline)
            .border(CgStroke.Hairline, CgColors.Brand.Signal)
            .padding(CgStroke.Hairline),
        horizontalArrangement = Arrangement.spacedBy(CgStroke.Hairline),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun CardBarButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxHeight()
            .heightIn(min = 40.dp)
            .background(CgColors.Surface.Base)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = CgSpace.S12),
        Alignment.Center,
    ) {
        Text(text.caps(), style = KeyStyle)
    }
}

/**
 * The editor's bottom sheet: no scrim, so the picture stays in view; a title, a "done", and contents that scroll inside
 * at most [maxHeightFraction] of the screen. A plain layout rather than a dialog window, so it can be drawn by the
 * screenshot tests like anything else. Drag it down to close it, as [CgSheet]: by its top, or from the contents once
 * they are scrolled to the top. Without [onDone] it stays put.
 */
@Composable
fun EditorSheet(
    title: String,
    onDone: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    // How far down the sheet is pulled, in px; 0 at rest, so it draws as it always did.
    var drag by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableIntStateOf(0) }
    val done by rememberUpdatedState(onDone)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    fun pull(dy: Float) {
        if (done != null) drag = (drag + dy).coerceAtLeast(0f)
    }
    fun settle(velocity: Float) {
        val close = done ?: return
        if (drag <= 0f) return
        val flick = velocity > with(density) { DISMISS_VELOCITY.toPx() }
        scope.launch {
            if (flick || drag > height * DISMISS_FRACTION) {
                // Off the screen first, so the sheet leaves rather than vanishing where the finger let go.
                animate(drag, height.toFloat(), animationSpec = tween(DISMISS_MS)) { v, _ -> drag = v }
                close()
            } else {
                animate(drag, 0f, animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow)) { v, _
                    ->
                    drag = v
                }
            }
        }
    }
    // The contents scroll first; pulled down past their top, the sheet follows the finger, and pushed up again it goes
    // back before they scroll.
    val nested = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y >= 0f || drag <= 0f) return Offset.Zero
                val taken = maxOf(available.y, -drag)
                drag += taken
                return Offset(0f, taken)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y <= 0f || done == null) return Offset.Zero
                pull(available.y)
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (drag <= 0f) return Velocity.Zero
                settle(available.y)
                return available
            }
        }
    }
    Column(
        modifier
            .graphicsLayer { translationY = drag }
            .onSizeChanged { height = it.height }
            // A sheet, not a wall: on a tablet or the desktop it keeps Material's sheet width and stays centred.
            .widthIn(max = SHEET_MAX_WIDTH)
            .fillMaxWidth()
            // Rows that come and go inside (a switch that shows its settings) grow the sheet rather than jump it.
            .animateContentSize(CgMotion.sizeSpec)
            .background(CgColors.Surface.Bar)
            .signalRule()
            // Touches on the sheet stay on the sheet: without this they fall through to the timeline under it.
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown().consume() } }
            // After it, so it sees the touch first: the top and the gaps between controls drag the sheet.
            .draggable(
                rememberDraggableState { pull(it) },
                Orientation.Vertical,
                enabled = onDone != null,
                onDragStopped = { settle(it) },
            )
            .nestedScroll(nested)
            .navigationBarsPadding()
            .padding(bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(CgSpace.S16),
    ) {
        SheetHeader(title, onDone)
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = CgSpace.S16),
            verticalArrangement = Arrangement.spacedBy(CgSpace.S12),
            content = content,
        )
    }
}

/** A sheet let go this far down its height, or flicked down this fast, closes; otherwise it springs back. */
private const val DISMISS_FRACTION = 0.3f
private val DISMISS_VELOCITY = 1000.dp
private const val DISMISS_MS = 200

/**
 * Where an [EditorSheet] shows: none while [sheet] is null; one slides up from the bottom when it is set and back down
 * when it is cleared (the last one stays drawn while it leaves); a switch from one sheet to another cross-fades in
 * place as the height eases. Shown at once on the first frame, so a screenshot finds it open.
 */
@Composable
fun <T : Any> EditorSheetSlot(
    sheet: T?,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    AnimatedContent(
        sheet,
        modifier,
        transitionSpec = {
            when {
                initialState == null ->
                    slideInVertically(CgMotion.long()) { it } + fadeIn(CgMotion.medium()) togetherWith
                        ExitTransition.None
                targetState == null ->
                    EnterTransition.None togetherWith
                        slideOutVertically(tween(CgMotion.MEDIUM, easing = CgMotion.EmphasizedAccelerate)) { it } +
                            fadeOut(CgMotion.medium())
                else -> fadeIn(CgMotion.medium()) togetherWith fadeOut(CgMotion.short())
            } using SizeTransform(clip = false) { _, _ -> CgMotion.sizeSpec }
        },
        contentAlignment = Alignment.BottomCenter,
        label = "sheet",
    ) { shown ->
        if (shown != null) content(shown)
    }
}

/**
 * The top of every bottom sheet: the title in Mono capitals and a "done" key when [onDone] is set, over a hairline.
 * [EditorSheet] and [CgSheet] both start with it, so the two kinds of sheet look the same.
 */
@Composable
fun SheetHeader(title: String, onDone: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = CgSize.Touch + CgSpace.S16)
                .padding(start = CgSpace.S16, end = CgSpace.S8, top = CgSpace.S8, bottom = CgSpace.S8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title.caps(),
                style = CgTheme.type.sheetTitle,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (onDone != null) {
                Box(
                    Modifier.testTag(TestTags.SheetDone)
                        .heightIn(min = CgSize.Touch)
                        .border(CgStroke.Hairline, CgColors.Line.Hairline)
                        .clickable(role = Role.Button, onClick = onDone)
                        .padding(horizontal = CgSpace.S16),
                    Alignment.Center,
                ) {
                    Text(stringResource(Res.string.core_done).caps(), style = KeyStyle, color = CgColors.Text.Primary)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(CgStroke.Hairline).background(CgColors.Line.Hairline))
    }
}

/** A 12 dp progress bar: a hairline frame with the done part filled with the signal, 2 dp inside it. */
@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier) {
    // The export reports in steps; the bar glides between them instead of jumping.
    val shown by animateFloatAsState(fraction.coerceIn(0f, 1f), CgMotion.settle(), label = "progress")
    Box(modifier.fillMaxWidth().height(12.dp).border(CgStroke.Hairline, CgColors.Line.Hairline).padding(3.dp)) {
        Box(Modifier.fillMaxWidth(shown).fillMaxHeight().background(CgColors.Brand.Signal))
    }
}

/** A colour and its name in a square chip with a hairline edge; tap to change it. */
@Composable
fun ColorChip(color: Color, name: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(Res.string.core_colour_chip, name)
    Row(
        modifier
            .heightIn(min = CgSize.Touch)
            .border(CgStroke.Hairline, CgColors.Line.Hairline)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(start = CgSpace.S8, end = CgSpace.S12),
        horizontalArrangement = Arrangement.spacedBy(CgSpace.S8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp).background(color).border(CgStroke.Hairline, CgColors.Line.Hairline))
        Text(name.caps(), style = KeyStyle)
    }
}

/** Material's bottom-sheet width: no sheet spans a whole tablet or desktop window. */
val SHEET_MAX_WIDTH = 640.dp

/**
 * Where a computer opens a video: a dashed square to drop a file on, with [onPick] as a button inside it (and on the
 * square itself) for those who would rather choose. [active] while a file is dragged over the window: the outline turns
 * the signal, solid, and the square says so. [busy] replaces the invitation while a video is being read.
 */
@Composable
fun DropZone(onPick: () -> Unit, modifier: Modifier = Modifier, active: Boolean = false, busy: String? = null) {
    val outline = if (active) CgColors.Brand.Signal else CgColors.Line.Hairline
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = 240.dp)
            .background(if (active) CgColors.Surface.Raised else CgColors.Surface.Bar)
            .drawBehind {
                val stroke = (if (active) CgStroke.Rule else CgStroke.Hairline).toPx()
                drawRect(
                    color = outline,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    style =
                        Stroke(
                            width = stroke,
                            pathEffect = if (active) null else PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                        ),
                )
            }
            .clickable(enabled = busy == null, role = Role.Button, onClick = onPick)
            .padding(CgSpace.S24),
        verticalArrangement = Arrangement.spacedBy(CgSpace.S12, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            CgIcons.Film,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            colorFilter = ColorFilter.tint(if (active) CgColors.Brand.Signal else CgColors.Text.Secondary),
        )
        Text(
            (busy ?: stringResource(if (active) Res.string.core_drop_to_open else Res.string.core_drop_video)).caps(),
            style = CgTheme.type.sheetTitle,
            color = CgColors.Text.Primary,
        )
        if (busy == null) {
            Text(
                stringResource(Res.string.core_drop_formats),
                style = CgTheme.type.caption,
                color = CgColors.Text.Secondary,
            )
            CgButton(
                stringResource(Res.string.core_choose_file),
                onClick = onPick,
                modifier = Modifier.padding(top = CgSpace.S4),
            )
        }
    }
}
