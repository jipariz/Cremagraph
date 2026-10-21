package app.onlyshots.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgSize
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgStroke
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.caps

/**
 * The pivot turned on its side, for windows wide enough to keep it visible all the time: square items in Mono capitals,
 * the chosen one marked by a signal rule on its leading edge.
 */
@Composable
fun CgNavRail(items: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxHeight()
            .background(CgColors.Surface.Bar)
            .safeDrawingPadding()
            .padding(vertical = CgSpace.S16, horizontal = CgSpace.S12),
        verticalArrangement = Arrangement.spacedBy(CgSpace.S8),
    ) {
        items.forEachIndexed { i, label ->
            val on = i == selectedIndex
            Text(
                label.caps(),
                style = CgTheme.type.label,
                color = if (on) CgColors.Text.Primary else CgColors.Text.Secondary,
                modifier =
                    Modifier.defaultMinSize(minWidth = CgSize.Touch, minHeight = CgSize.Touch)
                        .drawBehind {
                            if (on) drawRect(CgColors.Brand.Signal, size = Size(CgStroke.Rule.toPx(), size.height))
                        }
                        .selectable(selected = on, role = Role.Tab) { onSelect(i) }
                        .padding(start = CgSpace.S12, top = CgSpace.S12, bottom = CgSpace.S12),
            )
        }
    }
}
