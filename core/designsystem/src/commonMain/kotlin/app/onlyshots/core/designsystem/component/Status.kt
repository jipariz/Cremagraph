package app.onlyshots.core.designsystem.component

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgShape
import app.onlyshots.core.designsystem.CgSize
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgTheme

/** Figma "Status chip" over the camera view. Recording turns it red. */
@Composable
fun StatusChip(text: String, recording: Boolean, modifier: Modifier = Modifier, dot: Color? = null) {
    Row(
        modifier
            // Opaque, not a tint: over a dark frame a translucent chip is unreadable.
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .clip(CgShape.Pill)
            .background(if (recording) CgColors.Status.RecordingChip else CgColors.Surface.Bar)
            .padding(horizontal = CgSpace.S12, vertical = CgSpace.S6),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CgSpace.S8),
    ) {
        if (dot != null) Box(Modifier.size(8.dp).background(dot, CircleShape))
        Text(text, style = CgTheme.type.label)
    }
}

enum class BannerKind {
    Success,
    Warning,
    Info,
}

/**
 * Figma "Banner": one full-width line of copy on the raised surface, with a dot in the kind's colour, as a [StatusPill]
 * says its state. A field of saturated colour across the screen outweighed everything under it.
 */
@Composable
fun Banner(
    text: String,
    kind: BannerKind,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    val dot =
        when (kind) {
            BannerKind.Success -> CgColors.Status.Live
            BannerKind.Warning -> CgColors.Status.Highlight
            BannerKind.Info -> CgColors.Status.Info
        }
    Row(
        modifier
            .fillMaxWidth()
            .background(CgColors.Surface.Raised)
            .drawBehind {
                val h = 1.dp.toPx()
                drawRect(CgColors.Line.Hairline, Offset(0f, size.height - h), Size(size.width, h))
            }
            .padding(horizontal = CgSpace.S16, vertical = CgSpace.S12),
        horizontalArrangement = Arrangement.spacedBy(CgSpace.S12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(dot, CircleShape))
        Text(
            text,
            style = CgTheme.type.bodySmall,
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (action != null) {
            Box(
                Modifier.defaultMinSize(minWidth = CgSize.Touch, minHeight = CgSize.Touch)
                    .clickable(role = Role.Button, onClick = onAction),
                contentAlignment = Alignment.Center,
            ) {
                Text(action, style = CgTheme.type.labelStrong, modifier = Modifier.padding(CgSpace.S12))
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
        modifier.fillMaxWidth().padding(horizontal = CgSpace.S16),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CgSpace.S12),
    ) {
        BackButton(onClick = onBack)
        Text(title, style = CgTheme.type.screenTitle, modifier = Modifier.weight(1f).semantics { heading() })
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
                    color = if (i == selectedIndex) CgColors.Text.Primary else CgColors.Text.Dim,
                    modifier = Modifier.padding(vertical = CgSpace.S4),
                )
            }
        }
    }
}
