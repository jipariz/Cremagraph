package app.onlyshots.core.designsystem.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgIcons
import app.onlyshots.core.designsystem.CgMotion
import app.onlyshots.core.designsystem.CgSize
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgStroke
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.caps
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.core_library_count
import app.onlyshots.core.resources.core_scale_connect
import app.onlyshots.core.resources.core_scale_connected
import app.onlyshots.core.resources.core_scale_connecting
import com.valentinilk.shimmer.ShimmerBounds
import com.valentinilk.shimmer.defaultShimmerTheme
import com.valentinilk.shimmer.rememberShimmer
import com.valentinilk.shimmer.shimmer
import kotlin.math.PI
import kotlin.math.cos
import org.jetbrains.compose.resources.stringResource

/*
 * Figma "Home" (47:142 and its states), in the Instrument look: the rows, cells and the record action the start
 * screen is built from. Square cells with hairline edges on ink; names and states in Mono capitals.
 */

private val TileSize = 44.dp

/**
 * The app mark, 32 dp: the Signal tile, the trace cut into a signal square. With [splashTarget] it is where the
 * splash's mark lands (the first screen's header): it says where it is and waits, unseen, until then.
 */
@Composable
fun OnlyShotsMark(modifier: Modifier = Modifier, splashTarget: Boolean = false) {
    val target = LocalSplashTarget.current.takeIf { splashTarget }
    Image(
        CgIcons.OnlyShots,
        contentDescription = null,
        modifier =
            modifier
                .size(32.dp)
                .then(
                    if (target == null) Modifier
                    else
                        Modifier.onGloballyPositioned { target.bounds = it.boundsInRoot() }
                            .graphicsLayer { alpha = if (target.hidden) 0f else 1f }
                ),
    )
}

/** "library · 12" in the Home header. */
@Composable
fun LibraryChip(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.heightIn(min = CgSize.Touch).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(Res.string.core_library_count, count.toString()).caps(),
            style = CgTheme.type.labelStrong,
            modifier =
                Modifier.border(CgStroke.Hairline, CgColors.Line.Hairline)
                    .padding(horizontal = CgSpace.S12, vertical = CgSpace.S8),
        )
    }
}

/** A section title in capitals with an optional note or link on the right ("searching…", "connect a scale ›"). */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    trailingColor: Color = CgColors.Text.Primary,
    onTrailing: (() -> Unit)? = null,
    /** The note is a search still running: it breathes, with a dot sending out a ping before it. */
    searching: Boolean = false,
) {
    // A link keeps its 44 dp target, which would make the header taller than the 14 + 18 + 4 of the design: the
    // padding gives back what the target adds, so a header looks the same with or without a link.
    val link = onTrailing != null && trailing != null
    Row(
        modifier.fillMaxWidth().padding(top = if (link) 1.dp else CgSpace.S14, bottom = if (link) 0.dp else CgSpace.S4),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title.caps(),
            style = CgTheme.type.labelStrong,
            color = CgColors.Text.Secondary,
            modifier = Modifier.semantics { heading() },
        )
        if (trailing != null) {
            val target = onTrailing?.let {
                Modifier.heightIn(min = CgSize.Touch).clickable(role = Role.Button, onClick = it)
            }
            val ink by animateColorAsState(trailingColor, CgMotion.medium())
            Box(target ?: Modifier, contentAlignment = Alignment.CenterEnd) {
                // "searching…" gives way to "2 found" with a fade, not a jump.
                Crossfade(searching, animationSpec = CgMotion.medium(), label = "searching") { busy ->
                    if (busy) SearchingNote(trailing.caps(), ink)
                    else Text(trailing.caps(), style = CgTheme.type.labelStrong, color = ink)
                }
            }
        }
    }
}

/**
 * "searching…" while a search runs: a dot sends out a ring that fades as it grows, and the word breathes with it. One
 * 1.4 s beat, so the two never drift apart. Still in an IDE preview.
 */
@Composable
private fun SearchingNote(text: String, color: Color) {
    val still = LocalInspectionMode.current
    // Read only where it is drawn: the beat changes every frame, the composition need not.
    val beat =
        rememberInfiniteTransition(label = "searching")
            .animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1_400, easing = LinearEasing)),
                label = "beat",
            )
    fun t() = if (still) 0f else beat.value
    Row(horizontalArrangement = Arrangement.spacedBy(CgSpace.S6), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(14.dp).drawBehind {
                val dot = 2.5.dp.toPx()
                val t = t()
                val ping = dot + (size.minDimension / 2 - dot) * t
                drawRect(
                    color,
                    topLeft = center - Offset(ping, ping),
                    size = Size(ping * 2, ping * 2),
                    alpha = 0.5f * (1f - t),
                    style = Stroke(1.dp.toPx()),
                )
                drawRect(color, topLeft = center - Offset(dot, dot), size = Size(dot * 2, dot * 2))
            }
        )
        // Brightest as the ring leaves the dot, dimmest as it fades out.
        Text(
            text,
            style = CgTheme.type.labelStrong,
            color = color,
            modifier = Modifier.graphicsLayer { alpha = 0.55f + 0.45f * (0.5f + 0.5f * cos(2f * PI.toFloat() * t())) },
        )
    }
}

/** A square dot and a word in Mono capitals, both in the state's colour: "ready", "connected", "busy", "offline". */
@Composable
fun StatusPill(text: String, dot: Color, modifier: Modifier = Modifier) {
    val dotColor by animateColorAsState(dot, CgMotion.medium())
    Row(
        modifier.padding(vertical = CgSpace.S4),
        horizontalArrangement = Arrangement.spacedBy(CgSpace.S6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(dotColor))
        AnimatedContent(
            text,
            transitionSpec = { fadeIn(CgMotion.medium()) togetherWith fadeOut(CgMotion.short()) using SizeTransform() },
            label = "status",
        ) {
            Text(it.caps(), style = CgTheme.type.labelStrong.copy(letterSpacing = 0.sp), color = dotColor)
        }
    }
}

/**
 * A machine or a connected scale: a cell on the bar colour with the machine's colour as a 4 dp bar, the name in Mono
 * capitals, where it is in Mono, an optional live line, and its status. The chosen one has the signal for its edge; the
 * others a hairline.
 */
@Composable
fun MachineRow(
    name: String,
    detail: String,
    tileColor: Color,
    status: String,
    statusDot: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    live: String? = null,
    enabled: Boolean = true,
) {
    val alpha by animateFloatAsState(if (enabled) 1f else 0.5f, CgMotion.short())
    val edge by animateColorAsState(if (selected) CgColors.Brand.Signal else CgColors.Line.Hairline, CgMotion.short())
    Row(
        modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha }
            .background(CgColors.Surface.Bar)
            .border(CgStroke.Hairline, edge)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = CgSpace.S16, vertical = CgSpace.S14),
        horizontalArrangement = Arrangement.spacedBy(CgSpace.S12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 4.dp, height = 36.dp).background(tileColor))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name.caps(), style = CgTheme.type.cardTitle)
            Text(detail, style = CgTheme.type.data, color = CgColors.Text.Secondary)
            // The live line opens under the name when readings start, rather than pushing the row taller at once.
            AnimatedVisibility(
                live != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                var last by remember { mutableStateOf(live.orEmpty()) }
                if (live != null) last = live
                Text(last, style = CgTheme.type.data, color = CgColors.Status.Live)
            }
        }
        StatusPill(status, statusDot)
    }
}

/**
 * One of the start screen's ways in: a 44 dp framed icon, a title in Mono capitals, what it does, and a chevron. With
 * [signal] it is the screen's main way in, filled with the signal and drawn in ink; otherwise a bar-coloured cell with
 * a hairline edge. [tileColor] is kept for callers; the Instrument cells carry no coloured tile.
 */
@Composable
fun ChoiceCard(
    icon: ImageVector,
    tileColor: Color,
    title: String,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    signal: Boolean = false,
) {
    val ink = if (signal) CgColors.Text.OnLight else CgColors.Text.Primary
    Row(
        modifier
            .fillMaxWidth()
            .background(if (signal) CgColors.Brand.Signal else CgColors.Surface.Bar)
            .border(CgStroke.Hairline, if (signal) CgColors.Brand.Signal else CgColors.Line.Hairline)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(CgSpace.S16),
        horizontalArrangement = Arrangement.spacedBy(CgSpace.S16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(TileSize)
                .border(if (signal) CgStroke.Rule else CgStroke.Hairline, if (signal) ink else CgColors.Line.Hairline),
            contentAlignment = Alignment.Center,
        ) {
            CgIcon(icon, tint = ink, size = 20.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CgSpace.S4)) {
            Text(title.caps(), style = CgTheme.type.screenTitle.copy(letterSpacing = 0.sp), color = ink)
            Text(
                detail,
                style = CgTheme.type.bodySmall,
                color = if (signal) CgColors.Text.OnLight else CgColors.Text.Secondary,
            )
        }
        CgIcon(
            CgIcons.ChevronRight,
            tint = if (signal) CgColors.Text.OnLight else CgColors.Text.Secondary,
            size = 20.dp,
        )
    }
}

/**
 * Where the next search result will land, while the search runs; a light sweeps across it (compose-shimmer). An IDE
 * preview draws it still; the screenshot tests catch the sweep's first frame, before the light arrives, every run.
 */
@Composable
fun SkeletonRow(modifier: Modifier = Modifier) {
    val shimmer = rememberShimmer(ShimmerBounds.View, SkeletonShimmer)
    Row(
        modifier
            .fillMaxWidth()
            .then(if (LocalInspectionMode.current) Modifier.alpha(0.6f) else Modifier.shimmer(shimmer))
            .background(CgColors.Surface.Bar)
            .border(CgStroke.Hairline, CgColors.Line.Hairline)
            .padding(horizontal = CgSpace.S16, vertical = CgSpace.S14),
        horizontalArrangement = Arrangement.spacedBy(CgSpace.S12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 4.dp, height = 36.dp).background(CgColors.Line.Hairline))
        Column(verticalArrangement = Arrangement.spacedBy(CgSpace.S6)) {
            Bar(140.dp)
            Bar(200.dp)
        }
    }
}

/** The row rests at 60 % and the light brings it to full, rather than the library's 25 % that hides it on dark. */
private val SkeletonShimmer =
    defaultShimmerTheme.copy(
        shaderColors = listOf(Color.White.copy(alpha = 0.6f), Color.White, Color.White.copy(alpha = 0.6f))
    )

@Composable
private fun Bar(width: Dp) {
    Box(Modifier.width(width).height(10.dp).background(CgColors.Surface.Raised))
}

/** "add by address": a dashed hairline cell with the signal's plus for what the search cannot see. */
@Composable
fun AddRow(title: String, onClick: () -> Unit, modifier: Modifier = Modifier, subtitle: String? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = CgSize.Touch)
            .drawBehind {
                val stroke = CgStroke.Hairline.toPx()
                drawRect(
                    color = CgColors.Line.Hairline,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))),
                )
            }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = CgSpace.S16, vertical = CgSpace.S12),
        horizontalArrangement = Arrangement.spacedBy(CgSpace.S12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CgIcon(CgIcons.Plus, tint = CgColors.Brand.Signal, size = 24.dp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title.caps(), style = CgTheme.type.label)
            if (subtitle != null) Text(subtitle, style = CgTheme.type.bodySmall, color = CgColors.Text.Secondary)
        }
    }
}

/** The way into Bluetooth before anything is chosen: a hairline cell with a framed Bluetooth mark and a chevron. */
@Composable
fun ConnectScaleCard(title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(CgColors.Surface.Bar)
            .border(CgStroke.Hairline, CgColors.Line.Hairline)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = CgSpace.S16, vertical = CgSpace.S12),
        horizontalArrangement = Arrangement.spacedBy(CgSpace.S12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(TileSize).border(CgStroke.Hairline, CgColors.Line.Hairline),
            contentAlignment = Alignment.Center,
        ) {
            CgIcon(CgIcons.Bluetooth, tint = CgColors.Brand.Signal, size = 20.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title.caps(), style = CgTheme.type.label)
            Text(subtitle, style = CgTheme.type.bodySmall, color = CgColors.Text.Secondary)
        }
        CgIcon(CgIcons.ChevronRight, tint = CgColors.Text.Secondary, size = 20.dp)
    }
}

/** What a found scale's connect pill says. */
enum class ScaleAction {
    Connect,
    Connecting,
    Connected,
}

/**
 * A scale found while scanning, as a cell on ink with a hairline edge (stacked, the edges meet as one grid): signal
 * strength in four square bars, name in Mono capitals, what it measures, and its connect key.
 */
@Composable
fun ScaleDeviceRow(
    name: String,
    detail: String,
    bars: Int,
    action: ScaleAction,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(CgColors.Surface.Base)
            .border(CgStroke.Hairline, CgColors.Line.Hairline)
            .clickable(enabled = action == ScaleAction.Connect, role = Role.Button, onClick = onAction)
            .padding(horizontal = CgSpace.S16, vertical = CgSpace.S12),
        horizontalArrangement = Arrangement.spacedBy(CgSpace.S12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.size(width = 24.dp, height = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            for (i in 0 until 4) {
                Box(
                    Modifier.width(4.dp)
                        .height((4 + 4 * i).dp)
                        .background(if (i < bars) CgColors.Text.Primary else CgColors.Line.Hairline)
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name.caps(), style = CgTheme.type.label.copy(letterSpacing = 0.sp))
            Text(detail, style = CgTheme.type.bodySmall, color = CgColors.Text.Secondary)
        }
        val (label, bg, fg) =
            when (action) {
                ScaleAction.Connect ->
                    Triple(stringResource(Res.string.core_scale_connect), CgColors.Brand.Bone, CgColors.Text.OnLight)
                ScaleAction.Connecting ->
                    Triple(
                        stringResource(Res.string.core_scale_connecting),
                        CgColors.Surface.Base,
                        CgColors.Text.Secondary,
                    )
                ScaleAction.Connected ->
                    Triple(stringResource(Res.string.core_scale_connected), CgColors.Surface.Base, CgColors.Status.Live)
            }
        Box(
            Modifier.height(36.dp)
                .background(bg)
                .border(CgStroke.Hairline, if (bg == CgColors.Surface.Base) CgColors.Line.Hairline else bg)
                .padding(horizontal = CgSpace.S14),
            contentAlignment = Alignment.Center,
        ) {
            Text(label.caps(), style = CgTheme.type.labelStrong.copy(letterSpacing = 0.sp), color = fg)
        }
    }
}

/**
 * The main action at the bottom of Home: a 56 dp signal bar naming what will be used, an ink dot before it and an arrow
 * after, with how recording starts under it. Disabled, a hairline bar with grey letters.
 */
@Composable
fun RecordAction(
    text: String,
    caption: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ground by animateColorAsState(if (enabled) CgColors.Brand.Signal else Color.Transparent, CgMotion.medium())
    val ink by animateColorAsState(if (enabled) CgColors.Text.OnLight else CgColors.Text.Disabled, CgMotion.medium())
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(CgSpace.S8)) {
        Row(
            Modifier.fillMaxWidth()
                .height(56.dp)
                .background(ground)
                .border(CgStroke.Hairline, if (enabled) Color.Transparent else CgColors.Line.Hairline)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(CgSpace.S12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The one round thing: the record dot.
            Box(Modifier.size(10.dp).background(ink, CircleShape))
            Text(text.caps(), style = CgTheme.type.label, color = ink, modifier = Modifier.weight(1f))
            CgIcon(CgIcons.ArrowRight, tint = ink, size = 18.dp)
        }
        Text(
            caption,
            style = CgTheme.type.bodySmall,
            color = CgColors.Text.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
