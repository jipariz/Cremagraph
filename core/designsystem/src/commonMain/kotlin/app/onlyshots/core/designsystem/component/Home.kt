package app.onlyshots.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgIcons
import app.onlyshots.core.designsystem.CgSize
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgTheme
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
 * Figma "Home" (47:142 and its states): the rows, cards and the record action the start screen is built from. Card
 * corners are 12, tiles 44 with corners of 10, pills 10, the record action 28 — the Home frames are rounder than the
 * square controls elsewhere, and these sizes come straight from them.
 */

private val Card = RoundedCornerShape(12.dp)
private val Tile = RoundedCornerShape(10.dp)
private val Pill = RoundedCornerShape(10.dp)
private val TileSize = 44.dp

/** The app mark, 32 dp: the Viewfinder symbol in cream and orange, for the dark screens it sits on. */
@Composable
fun OnlyShotsMark(modifier: Modifier = Modifier) {
    Image(CgIcons.OnlyShots, contentDescription = null, modifier = modifier.size(32.dp))
}

/** "library · 12" in the Home header. */
@Composable
fun LibraryChip(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.heightIn(min = CgSize.Touch).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(Res.string.core_library_count, count.toString()),
            style = CgTheme.type.label,
            modifier =
                Modifier.clip(RoundedCornerShape(18.dp))
                    .background(CgColors.Surface.Raised)
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
    trailingColor: Color = CgColors.Text.Dim,
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
            title.uppercase(),
            style = CgTheme.type.labelStrong,
            color = CgColors.Text.Secondary,
            modifier = Modifier.semantics { heading() },
        )
        if (trailing != null) {
            val target = onTrailing?.let {
                Modifier.heightIn(min = CgSize.Touch).clickable(role = Role.Button, onClick = it)
            }
            Box(target ?: Modifier, contentAlignment = Alignment.CenterEnd) {
                if (searching) SearchingNote(trailing, trailingColor)
                else Text(trailing, style = CgTheme.type.label, color = trailingColor)
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
    val beat by
        rememberInfiniteTransition(label = "searching")
            .animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1_400, easing = LinearEasing)),
                label = "beat",
            )
    val t = if (still) 0f else beat
    Row(horizontalArrangement = Arrangement.spacedBy(CgSpace.S6), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(14.dp).drawBehind {
                val dot = 2.5.dp.toPx()
                drawCircle(color, radius = dot + (size.minDimension / 2 - dot) * t, alpha = 0.5f * (1f - t))
                drawCircle(color, radius = dot)
            }
        )
        // Brightest as the ring leaves the dot, dimmest as it fades out.
        Text(
            text,
            style = CgTheme.type.label,
            color = color,
            modifier = Modifier.alpha(0.55f + 0.45f * (0.5f + 0.5f * cos(2f * PI.toFloat() * t))),
        )
    }
}

/** A dot and a word: "ready", "connected", "busy", "offline". */
@Composable
fun StatusPill(text: String, dot: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(Pill).background(CgColors.Surface.Base).padding(horizontal = CgSpace.S8, vertical = CgSpace.S4),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
        Text(text, style = CgTheme.type.caption, color = CgColors.Text.Secondary)
    }
}

/**
 * A machine or a connected scale: a coloured tile, the name, where it is, an optional live line, and its status. The
 * chosen one is raised with a white outline; the others keep a hairline, so they stand off the page.
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
    Row(
        modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .clip(Card)
            .background(if (selected) CgColors.Surface.Raised else CgColors.Surface.Bar)
            .border(if (selected) 2.dp else 1.dp, if (selected) CgColors.Text.Primary else CgColors.Line.Hairline, Card)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(start = CgSpace.S12, end = CgSpace.S14, top = CgSpace.S12, bottom = CgSpace.S12),
        horizontalArrangement = Arrangement.spacedBy(CgSpace.S12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(TileSize).clip(Tile).background(tileColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(IconKind.Machine)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = CgTheme.type.body)
            Text(detail, style = CgTheme.type.caption, color = CgColors.Text.Dim)
            if (live != null) Text(live, style = CgTheme.type.caption, color = CgColors.Status.Live)
        }
        StatusPill(status, statusDot)
    }
}

/** One of the start screen's ways in: a coloured icon tile, a title, what it does, and a chevron. */
@Composable
fun ChoiceCard(
    icon: ImageVector,
    tileColor: Color,
    title: String,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(Card)
            .background(CgColors.Surface.Bar)
            .border(1.dp, CgColors.Line.Hairline, Card)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(CgSpace.S16),
        horizontalArrangement = Arrangement.spacedBy(CgSpace.S14),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp).clip(Tile).background(tileColor), contentAlignment = Alignment.Center) {
            Image(
                icon,
                contentDescription = null,
                colorFilter = ColorFilter.tint(Color.White),
                modifier = Modifier.size(28.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CgSpace.S4)) {
            Text(title, style = CgTheme.type.cardTitle)
            Text(detail, style = CgTheme.type.caption, color = CgColors.Text.Secondary)
        }
        Text("›", style = CgTheme.type.cardTitle, color = CgColors.Text.Secondary)
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
            .clip(Card)
            .background(CgColors.Surface.Bar)
            .padding(start = CgSpace.S12, top = CgSpace.S12, bottom = CgSpace.S12),
        horizontalArrangement = Arrangement.spacedBy(CgSpace.S12),
    ) {
        Box(Modifier.size(TileSize).clip(Tile).background(CgColors.Surface.Raised))
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
    Box(Modifier.width(width).height(10.dp).clip(RoundedCornerShape(5.dp)).background(CgColors.Surface.Raised))
}

/** "add by address": a dashed card for what the search cannot see. */
@Composable
fun AddRow(title: String, onClick: () -> Unit, modifier: Modifier = Modifier, subtitle: String? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(Card)
            .drawBehind {
                val stroke = 1.5.dp.toPx()
                drawRoundRect(
                    color = CgColors.Line.Divider,
                    topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))),
                )
            }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = CgSpace.S12, end = CgSpace.S14, top = CgSpace.S12, bottom = CgSpace.S12),
        horizontalArrangement = Arrangement.spacedBy(CgSpace.S12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(TileSize).border(1.dp, CgColors.Text.Dim, Tile),
            contentAlignment = Alignment.Center,
        ) {
            Icon(IconKind.Plus)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = CgTheme.type.bodySmall)
            if (subtitle != null) Text(subtitle, style = CgTheme.type.caption, color = CgColors.Text.Dim)
        }
    }
}

/** The way into Bluetooth before anything is chosen: blue outline, a Bluetooth mark, and a chevron. */
@Composable
fun ConnectScaleCard(title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(Card)
            .border(1.5.dp, CgColors.Series.Pressure, Card)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(CgSpace.S14),
        horizontalArrangement = Arrangement.spacedBy(CgSpace.S12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(TileSize).clip(CircleShape).background(CgColors.Status.Info),
            contentAlignment = Alignment.Center,
        ) {
            Text("ᛒ", style = CgTheme.type.cardTitle)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = CgTheme.type.body)
            Text(subtitle, style = CgTheme.type.caption, color = CgColors.Text.Dim)
        }
        Text("›", style = CgTheme.type.cardTitle, color = CgColors.Text.Secondary)
    }
}

/** What a found scale's connect pill says. */
enum class ScaleAction {
    Connect,
    Connecting,
    Connected,
}

/** A scale found while scanning: signal strength in four bars, name, what it measures, and its connect pill. */
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
            .clip(Card)
            .background(CgColors.Surface.Raised)
            .border(1.dp, CgColors.Line.Hairline, Card)
            .clickable(enabled = action == ScaleAction.Connect, role = Role.Button, onClick = onAction)
            .padding(CgSpace.S12),
        horizontalArrangement = Arrangement.spacedBy(CgSpace.S12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
            for (i in 0 until 4) {
                Box(
                    Modifier.width(4.dp)
                        .height((6 + 4 * i).dp)
                        .background(if (i < bars) CgColors.Text.Primary else CgColors.Line.Divider)
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = CgTheme.type.body)
            Text(detail, style = CgTheme.type.caption, color = CgColors.Text.Dim)
        }
        val (label, bg, fg) =
            when (action) {
                ScaleAction.Connect ->
                    Triple(stringResource(Res.string.core_scale_connect), CgColors.Text.Primary, CgColors.Text.OnLight)
                ScaleAction.Connecting ->
                    Triple(
                        stringResource(Res.string.core_scale_connecting),
                        CgColors.Surface.Base,
                        CgColors.Text.Secondary,
                    )
                ScaleAction.Connected ->
                    Triple(stringResource(Res.string.core_scale_connected), CgColors.Surface.Base, CgColors.Status.Live)
            }
        Text(
            label,
            style = CgTheme.type.labelStrong,
            color = fg,
            modifier =
                Modifier.clip(RoundedCornerShape(16.dp))
                    .background(bg)
                    .padding(horizontal = CgSpace.S14, vertical = CgSpace.S8),
        )
    }
}

/**
 * The main action at the bottom of Home, a white pill like every screen's main action, naming what will be used, with
 * how recording starts under it. Its dot is the one touch of record red: red is for recording itself.
 */
@Composable
fun RecordAction(
    text: String,
    caption: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(CgSpace.S6)) {
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(if (enabled) CgColors.Text.Primary else CgColors.Surface.Raised)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(vertical = CgSpace.S16),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(14.dp)
                    .clip(CircleShape)
                    .background(if (enabled) CgColors.Status.Record else CgColors.Text.Dim)
            )
            Text(text, style = CgTheme.type.body, color = if (enabled) CgColors.Text.OnLight else CgColors.Text.Dim)
        }
        Text(
            caption,
            style = CgTheme.type.caption,
            color = CgColors.Text.Dim,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private enum class IconKind {
    Machine,
    Plus,
}

@Composable
private fun Icon(kind: IconKind) {
    Image(
        if (kind == IconKind.Machine) CgIcons.Machine else CgIcons.Plus,
        contentDescription = null,
        colorFilter = ColorFilter.tint(CgColors.Text.Primary),
        modifier = Modifier.size(24.dp),
    )
}
