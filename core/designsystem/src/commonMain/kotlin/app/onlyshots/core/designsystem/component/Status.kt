package app.onlyshots.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgMotion
import app.onlyshots.core.designsystem.CgSize
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgStroke
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.caps

/**
 * Figma "Status chip" over the camera view: a compact ink cell with a hairline edge, a square dot and Mono capitals; it
 * says, it is not a key, so it is only as tall as its line. Recording turns it the record red with white letters, a
 * size up.
 */
@Composable
fun StatusChip(text: String, recording: Boolean, modifier: Modifier = Modifier, dot: Color? = null) {
    val ground by
        animateColorAsState(
            if (recording) CgColors.Status.Record else CgColors.Surface.Base.copy(alpha = 0.85f),
            CgMotion.medium(),
        )
    val edge by
        animateColorAsState(if (recording) CgColors.Status.Record else CgColors.Line.Hairline, CgMotion.medium())
    Row(
        modifier
            // Opaque, not a tint: over a dark frame a translucent chip is unreadable.
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .background(ground)
            .border(CgStroke.Hairline, edge)
            .padding(horizontal = 10.dp, vertical = CgSpace.S6),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CgSpace.S8),
    ) {
        if (dot != null) Box(Modifier.size(6.dp).background(dot))
        Text(
            text.caps(),
            style = CgTheme.type.labelStrong.copy(letterSpacing = 0.sp, fontSize = if (recording) 13.sp else 12.sp),
            color = if (recording) Color.White else CgColors.Text.Primary,
        )
    }
}

enum class BannerKind {
    Success,
    Warning,
    Info,
}

/**
 * Figma "Banner": one line of Mono capitals in a cell edged in the kind's colour, the words in that colour too, over
 * the ink at 90 %, and an optional key outlined the same way. A field of saturated colour across the screen outweighed
 * everything under it.
 */
@Composable
fun Banner(
    text: String,
    kind: BannerKind,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    val tone =
        when (kind) {
            BannerKind.Success -> CgColors.Status.Success
            BannerKind.Warning -> CgColors.Status.Warning
            BannerKind.Info -> CgColors.Text.Primary
        }
    Row(
        modifier
            .fillMaxWidth()
            .background(CgColors.Surface.Base.copy(alpha = 0.9f))
            .border(CgStroke.Hairline, if (kind == BannerKind.Info) CgColors.Line.Hairline else tone)
            .padding(horizontal = CgSpace.S12, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(CgSpace.S12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text.caps(),
            style = CgTheme.type.labelStrong.copy(letterSpacing = 0.sp),
            color = tone,
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (action != null) {
            Box(
                Modifier.defaultMinSize(minWidth = CgSize.Touch, minHeight = CgSize.Touch)
                    .border(CgStroke.Hairline, if (kind == BannerKind.Info) CgColors.Line.Hairline else tone)
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = CgSpace.S16),
                contentAlignment = Alignment.Center,
            ) {
                Text(action.caps(), style = CgTheme.type.labelStrong.copy(letterSpacing = 0.sp), color = tone)
            }
        }
    }
}

/** Figma "Header back": one screen title with a back target in front of it. */
@Composable
fun HeaderBack(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val h = CgStroke.Hairline.toPx()
                drawRect(CgColors.Line.Hairline, Offset(0f, size.height - h), Size(size.width, h))
            }
            .padding(start = CgSpace.S16, end = CgSpace.S16, bottom = CgSpace.S12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CgSpace.S12),
    ) {
        BackButton(onClick = onBack)
        Text(title.caps(), style = CgTheme.type.sheetTitle, modifier = Modifier.weight(1f).semantics { heading() })
        action?.invoke()
    }
}

/** Figma "Header pivot": Metro pivot titles; the current one white, the others dim. */
@Composable
fun HeaderPivot(items: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.horizontalScroll(rememberScrollState()).padding(horizontal = CgSpace.S16),
        horizontalArrangement = Arrangement.spacedBy(CgSpace.S16),
    ) {
        items.forEachIndexed { i, label ->
            Box(
                Modifier.defaultMinSize(minHeight = CgSize.Touch)
                    .semantics { if (i == selectedIndex) heading() }
                    .clickable(role = Role.Tab) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = CgTheme.type.pivot,
                    color =
                        animateColorAsState(
                                if (i == selectedIndex) CgColors.Text.Primary else CgColors.Text.Disabled,
                                CgMotion.short(),
                            )
                            .value,
                    modifier = Modifier.padding(vertical = CgSpace.S4),
                )
            }
        }
    }
}
