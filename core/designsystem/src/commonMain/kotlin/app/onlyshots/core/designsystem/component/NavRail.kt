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
import androidx.compose.ui.semantics.Role
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgSize
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgTheme

/** The pivot turned on its side, for windows wide enough to keep it visible all the time. */
@Composable
fun CgNavRail(items: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxHeight()
            .background(CgColors.Surface.Bar)
            .safeDrawingPadding()
            .padding(vertical = CgSpace.S16, horizontal = CgSpace.S12),
        verticalArrangement = Arrangement.spacedBy(CgSpace.S16),
    ) {
        items.forEachIndexed { i, label ->
            Text(
                label,
                style = CgTheme.type.cardTitle,
                color = if (i == selectedIndex) CgColors.Text.Primary else CgColors.Text.Dim,
                modifier =
                    Modifier.defaultMinSize(minWidth = CgSize.Touch, minHeight = CgSize.Touch).selectable(
                        selected = i == selectedIndex,
                        role = Role.Tab,
                    ) {
                        onSelect(i)
                    },
            )
        }
    }
}
