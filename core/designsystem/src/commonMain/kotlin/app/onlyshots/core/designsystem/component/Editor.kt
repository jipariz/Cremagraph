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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
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
import app.onlyshots.TestTags
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgIcons
import app.onlyshots.core.designsystem.CgMotion
import app.onlyshots.core.designsystem.CgRadius
import app.onlyshots.core.designsystem.CgShape
import app.onlyshots.core.designsystem.CgSize
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgTheme
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
 * The web editor's phone controls (web/.../styles.css): rounded chips and pills on the raised surface, list rows, a
 * floating toolbar over the graph and a bottom sheet without a scrim, so the picture stays in view while it is open.
 * Pills are 34 dp to look at and 44 dp to touch.
 */

private val Chip = CgShape.Pill

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
    Box(modifier.heightIn(min = CgSize.Touch).clickable(role = Role.Button, onClick = onClick), Alignment.Center) {
        Row(
            Modifier.clip(Chip)
                .background(if (primary) CgColors.Brand.Orange else CgColors.Surface.Raised)
                .padding(start = CgSpace.S12, end = 10.dp, top = CgSpace.S8, bottom = CgSpace.S8),
            horizontalArrangement = Arrangement.spacedBy(CgSpace.S6),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (dot != null) Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
            Text(
                text,
                style = if (primary) CgTheme.type.labelStrong else CgTheme.type.label,
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
}

/**
 * The wide editor's main action: [text] runs it at once ([onClick]); when there is a second way to do it, the ▾ half
 * opens [menu] (its items close it with the function they are handed). Orange, like the phone's export chip.
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
        modifier.heightIn(min = CgSize.Touch),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.clip(Chip).background(CgColors.Brand.Orange), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text,
                style = CgTheme.type.labelStrong,
                color = CgColors.Text.OnLight,
                maxLines = 1,
                modifier =
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                        .padding(start = CgSpace.S16, end = if (menu == null) CgSpace.S16 else CgSpace.S12)
                        .padding(vertical = CgSpace.S8),
            )
            if (menu != null) {
                Box(Modifier.width(1.dp).height(18.dp).background(CgColors.Text.OnLight.copy(alpha = 0.35f)))
                Box {
                    Text(
                        "▾",
                        style = CgTheme.type.labelStrong,
                        color = CgColors.Text.OnLight,
                        modifier =
                            Modifier.clickable(role = Role.DropdownList) { open = true }
                                .semantics { contentDescription = moreWays }
                                .padding(horizontal = CgSpace.S12, vertical = CgSpace.S8),
                    )
                    DropdownMenu(
                        expanded = open,
                        onDismissRequest = { open = false },
                        modifier = Modifier.testTagsAsResourceIds(),
                    ) {
                        menu { open = false }
                    }
                }
            }
        }
    }
}

/** A menu row in the design's type, for [SplitButton] and the editor's other menus. */
@Composable
fun CgMenuItem(text: String, detail: String? = null, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Column {
                Text(text, style = CgTheme.type.body)
                if (detail != null) Text(detail, style = CgTheme.type.caption, color = CgColors.Text.Dim)
            }
        },
        onClick = onClick,
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
                .clip(CgShape.Dialog)
                .background(CgColors.Surface.Bar)
                .padding(CgSpace.S24),
            verticalArrangement = Arrangement.spacedBy(CgSpace.S12),
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
                            .clip(RoundedCornerShape(CgRadius.Chip)),
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
                        Text("✓ ${done.orEmpty()}", style = CgTheme.type.sheetTitle, color = CgColors.Status.Live)
                        Text(detail, style = CgTheme.type.bodySmall, color = CgColors.Text.Secondary)
                        Row(
                            Modifier.fillMaxWidth().padding(top = CgSpace.S8),
                            horizontalArrangement = Arrangement.spacedBy(CgSpace.S8, Alignment.End),
                        ) {
                            CgButton(
                                stringResource(Res.string.core_export_keep_editing),
                                onClick = onKeepEditing,
                                kind = CgButtonKind.Secondary,
                                modifier = Modifier.testTag(TestTags.ExportKeepEditing),
                            )
                            CgButton(next, onClick = onNext, modifier = Modifier.testTag(TestTags.ExportNext))
                        }
                    } else {
                        Text(stringResource(Res.string.core_export_making), style = CgTheme.type.sheetTitle)
                        ProgressLine(progress ?: 0f)
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                "${((progress ?: 0f) * 100).toInt()} %",
                                style = CgTheme.type.label,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                left?.let { stringResource(Res.string.core_export_left, it) }
                                    ?: stringResource(Res.string.core_export_estimating),
                                style = CgTheme.type.caption,
                                color = CgColors.Text.Dim,
                            )
                        }
                        Text(detail, style = CgTheme.type.bodySmall, color = CgColors.Text.Secondary)
                        Text(footer, style = CgTheme.type.caption, color = CgColors.Text.Dim)
                        Row(
                            Modifier.fillMaxWidth().padding(top = CgSpace.S8),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            CgButton(
                                stringResource(Res.string.core_cancel),
                                onClick = onCancel,
                                kind = CgButtonKind.Secondary,
                            )
                        }
                    }
                }
            }
        }
    }
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
            text,
            style = CgTheme.type.label,
            color = CgColors.Text.Dim,
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
        Box(Modifier.fillMaxWidth().height(1.dp).background(CgColors.Line.Divider))
    }
}

/** A 44 dp round button with an icon, on the raised surface: for a bar, where [CgRoundButton] would be see-through. */
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
            .clip(CircleShape)
            .background(CgColors.Surface.Raised)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        Alignment.Center,
    ) {
        Image(
            icon,
            contentDescription = null,
            colorFilter = ColorFilter.tint(CgColors.Text.Primary),
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun RuleLine(icon: ImageVector, color: Color, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(CgSpace.S6), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(RULE_GLYPH))
        Text(
            text,
            style = CgTheme.type.label,
            color = CgColors.Text.Secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val RULE_GLYPH = 12.dp

/** A small rounded action: the timeline's tools, the sheets' buttons. [light] is the white one that confirms. */
@Composable
fun Pill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = CgColors.Text.Primary,
    enabled: Boolean = true,
    light: Boolean = false,
    minWidth: Dp = 0.dp,
    /** Stretch the pill itself across [modifier]'s width, for the wide cancel and confirm buttons of a sheet. */
    fill: Boolean = false,
    /** Drawn before the text, in the text's colour. */
    icon: ImageVector? = null,
) {
    val alpha by animateFloatAsState(if (enabled) 1f else 0.4f, CgMotion.short(), label = "alpha")
    val ground by
        animateColorAsState(
            if (light) CgColors.Text.Primary else CgColors.Surface.Raised,
            CgMotion.short(),
            label = "bg",
        )
    val fg by animateColorAsState(if (light) CgColors.Text.OnLight else color, CgMotion.short(), label = "fg")
    Box(
        modifier
            .heightIn(min = CgSize.Touch)
            .graphicsLayer { this.alpha = alpha }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        Alignment.Center,
    ) {
        Box(
            Modifier.clip(Chip)
                .background(ground)
                .heightIn(min = 34.dp)
                .widthIn(min = minWidth)
                .then(if (fill) Modifier.fillMaxWidth() else Modifier)
                .padding(horizontal = CgSpace.S12, vertical = CgSpace.S8),
            Alignment.Center,
        ) {
            if (icon == null) {
                Text(text, style = CgTheme.type.label, color = fg)
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
                    Text(text, style = CgTheme.type.label, color = fg)
                }
            }
        }
    }
}

/**
 * A row in a sheet that leads somewhere: what it is, a line about it, and a chevron, or [trailing] in its place (a
 * choice that goes with the row, tapped on its own).
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
            .clip(CgShape.Field)
            .background(CgColors.Surface.Raised)
            .border(1.dp, CgColors.Line.Hairline, CgShape.Field)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = CgSpace.S16, vertical = CgSpace.S14),
        horizontalArrangement = Arrangement.spacedBy(CgSpace.S12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = CgTheme.type.body)
            Text(detail, style = CgTheme.type.caption, color = CgColors.Text.Dim)
        }
        if (trailing != null) trailing() else Text("›", style = CgTheme.type.cardTitle, color = CgColors.Text.Dim)
    }
}

/** The toolbar that floats over a selected graph. */
@Composable
fun CardBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(CgColors.Surface.Raised.copy(alpha = 0.95f))
            .padding(CgSpace.S4),
        horizontalArrangement = Arrangement.spacedBy(CgSpace.S4),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun CardBarButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .heightIn(min = 36.dp)
            .clip(CgShape.Chip)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        Alignment.Center,
    ) {
        Text(text, style = CgTheme.type.label)
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
            .clip(CgShape.Sheet)
            .background(CgColors.Surface.Bar)
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
            .padding(start = CgSpace.S16, end = CgSpace.S16, top = 10.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(CgSpace.S12),
    ) {
        SheetHeader(title, onDone)
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
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
 * The top of every bottom sheet: a 40 × 4 handle, the title, and a "done" when [onDone] is set. [EditorSheet] and
 * [CgSheet] both start with it, so the two kinds of sheet look the same.
 */
@Composable
fun SheetHeader(title: String, onDone: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(CgSpace.S12)) {
        Box(
            Modifier.align(Alignment.CenterHorizontally)
                .size(40.dp, 4.dp)
                .clip(CircleShape)
                .background(CgColors.Text.Dim)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = CgTheme.type.sheetTitle, modifier = Modifier.weight(1f).semantics { heading() })
            if (onDone != null) {
                Box(
                    Modifier.testTag(TestTags.SheetDone)
                        .heightIn(min = CgSize.Touch)
                        .clickable(role = Role.Button, onClick = onDone)
                        .padding(4.dp),
                    Alignment.Center,
                ) {
                    Text(
                        stringResource(Res.string.core_done),
                        style = CgTheme.type.bodySmall,
                        color = CgColors.Text.Primary,
                    )
                }
            }
        }
    }
}

/** A 6 dp progress line, orange on the divider colour. */
@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier) {
    // The export reports in steps; the line glides between them instead of jumping.
    val shown by animateFloatAsState(fraction.coerceIn(0f, 1f), CgMotion.settle(), label = "progress")
    Box(modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(CgColors.Line.Divider)) {
        Box(Modifier.fillMaxWidth(shown).fillMaxHeight().background(CgColors.Brand.Orange))
    }
}

/** A colour and its name in a rounded chip; tap to change it. */
@Composable
fun ColorChip(color: Color, name: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(Res.string.core_colour_chip, name)
    Row(
        modifier
            .heightIn(min = CgSize.Touch)
            .clip(CgShape.Chip)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.clip(CgShape.Chip)
                .background(CgColors.Surface.Raised)
                .padding(start = 6.dp, end = 10.dp, top = 5.dp, bottom = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(CgSpace.S8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(22.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.dp, CgColors.Line.Divider, CircleShape)
            )
            Text(name, style = CgTheme.type.label)
        }
    }
}

/** Material's bottom-sheet width: no sheet spans a whole tablet or desktop window. */
val SHEET_MAX_WIDTH = 640.dp

/**
 * Where a computer opens a video: a dashed card to drop a file on, with [onPick] as a button inside it (and on the card
 * itself) for those who would rather choose. [active] while a file is dragged over the window: the outline turns solid
 * white and the card says so. [busy] replaces the invitation while a video is being read.
 */
@Composable
fun DropZone(onPick: () -> Unit, modifier: Modifier = Modifier, active: Boolean = false, busy: String? = null) {
    val outline = if (active) CgColors.Text.Primary else CgColors.Line.Divider
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = 240.dp)
            .clip(DropShape)
            .background(if (active) CgColors.Surface.Raised else CgColors.Surface.Bar)
            .drawBehind {
                val stroke = (if (active) 2.dp else 1.5.dp).toPx()
                drawRoundRect(
                    color = outline,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(DropRadius.toPx()),
                    style =
                        Stroke(
                            width = stroke,
                            pathEffect = if (active) null else PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
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
            colorFilter = ColorFilter.tint(if (active) CgColors.Text.Primary else CgColors.Text.Secondary),
        )
        Text(
            busy ?: stringResource(if (active) Res.string.core_drop_to_open else Res.string.core_drop_video),
            style = CgTheme.type.sheetTitle,
            color = CgColors.Text.Primary,
        )
        if (busy == null) {
            Text(stringResource(Res.string.core_drop_formats), style = CgTheme.type.caption, color = CgColors.Text.Dim)
            CgButton(
                stringResource(Res.string.core_choose_file),
                onClick = onPick,
                modifier = Modifier.padding(top = CgSpace.S4),
            )
        }
    }
}

private val DropRadius = 16.dp
private val DropShape = RoundedCornerShape(DropRadius)
